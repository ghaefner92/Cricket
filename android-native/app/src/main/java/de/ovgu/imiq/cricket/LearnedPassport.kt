package de.ovgu.imiq.cricket

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.Inflater

internal const val LEARNED="imiq.experimental.learned-passport.v1"
internal const val LEARNED_SCHEMA="d-day-learned-passport-v1"

internal fun learnedDigest(value:Any?):String {
 fun hex(bytes:ByteArray):String {
  val digits="0123456789abcdef";val chars=CharArray(bytes.size*2)
  bytes.forEachIndexed {i,b->val n=b.toInt() and 255;chars[2*i]=digits[n shr 4];chars[2*i+1]=digits[n and 15]}
  return String(chars)
 }
 fun encoded(v:Any?):String=when(v){
  null,JSONObject.NULL->"null"
  is JSONObject->v.keys().asSequence().sortedBy {hex(it.toByteArray(Charsets.UTF_8))}.joinToString(",","{","}"){encoded(it)+":"+encoded(v.opt(it))}
  is JSONArray->(0 until v.length()).joinToString(",","[","]"){encoded(v.opt(it))}
  is String->"s"+hex(v.toByteArray(Charsets.UTF_8))
  is Boolean->v.toString()
  is Number->{
   if(v is Long)require(v in -9007199254740991L..9007199254740991L)
   if(v is java.math.BigInteger)require(v.abs()<=java.math.BigInteger.valueOf(9007199254740991L))
   val n=v.toDouble();require(n.isFinite());"n"+hex(ByteBuffer.allocate(8).putDouble(if(n==0.0)0.0 else n).array())
  }
  else->error("Unsupported immutable learned value")
 }
 return hex(MessageDigest.getInstance("SHA-256").digest(("d-day-typed-utf8-binary64-v1|"+encoded(value)).toByteArray(Charsets.US_ASCII)))
}

/** Durable shadow jobs. Active Passport and HOTCO are never selected here. */
object LearnedPassportLearning {
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
 private val gate=Mutex()
 fun resume(context:Context,databaseName:String="cricket-native-v1.db")=scope.launch {gate.withLock {
  Store(context.applicationContext,databaseName).use {store->
   prepareLearnedBackfill(store)
   val jobs=store.storage()
   val keys=jobs.keys().asSequence().filter {it.startsWith("$LEARNED.job.")}.toList()
    .sortedWith(compareBy<String> {Instant.parse(JSONObject(jobs.getString(it)).getString("as_of"))}.thenBy {it})
   val blockedRoots=mutableSetOf<String>()
   for(key in keys){
    val job=store.json(key)?:continue
    if(job.optString("status") !in listOf("queued","pending","failed"))continue
    val record=store.record(job.getString("search_id"))
    var root:String?=null
    try {
     require(record!=null) {"Source Experience unavailable; restore evidence before retrying"}
     root=originalPassport(store,record.getJSONObject("confirmed_snapshot")).getJSONObject("lineage").getString("passport_id")
     if(root in blockedRoots)continue
     val request=job.optJSONObject("request")?:learnedRequest(store,record,job).also {
      job.put("status","pending").put("request",it).put("original_passport_id",root);store.put(key,job)
     }
     val response=Api(store).call("/api/dyconet/d-day/learned-passport/update",request,180000)
     store.commitLearned(key,request,response)
     TripUpdates.changed()
    }catch(e:Exception){
     if(e is CancellationException)throw e
     if(root!=null)blockedRoots.add(root)
     job.put("status","failed").put("failed_at",now()).put("failure",e.message?.take(300)?:"Learned Passport failed")
     store.put(key,job)
    }
   }
  }
 }}
}

private fun prepareLearnedBackfill(store:Store) {
 if(store.json("d-day.evidence-exclusions.v1")==null)return
 val values=store.storage();val roots=mutableSetOf<String>()
 values.keys().asSequence().filter {it.startsWith("$LEARNED.job.")}.forEach {key->
  val job=JSONObject(values.getString(key))
  if(job.optString("status") in listOf("queued","pending","failed")){
   val record=store.record(job.getString("search_id"))?:return@forEach
   val root=runCatching {originalPassport(store,record.getJSONObject("confirmed_snapshot")).getJSONObject("lineage").getString("passport_id")}.getOrNull()
   if(root!=null&&store.get("$LEARNED.tip.$root")==null)roots.add(root)
  }
 }
 if(roots.isEmpty())return
 store.forEachResearchRecord {record->
  val root=runCatching {originalPassport(store,record.getJSONObject("confirmed_snapshot")).getJSONObject("lineage").getString("passport_id")}.getOrNull()
  if(root in roots)record.objects("journeys").filter {it.optJSONObject("experience")!=null}.forEach {queueLearnedPassport(store,record,it)}
 }
}

internal fun queueLearnedPassport(store:Store,record:JSONObject,journey:JSONObject) {
 val e=journey.getJSONObject("experience");val event=e.getString("event_id");val key="$LEARNED.job.$event"
 val old=store.json(key)
 if(old!=null){require(old.getString("journey_id")==journey.getString("journey_id")&&old.getString("as_of")==e.getString("confirmed_at"));return}
 store.put(key,obj("schema_version" to "d-day-learned-job-v1","status" to "queued",
  "search_id" to record.getString("search_id"),"journey_id" to journey.getString("journey_id"),
  "event_id" to event,"as_of" to e.getString("confirmed_at")))
}

internal fun learnedRequest(store:Store,record:JSONObject,job:JSONObject):JSONObject {
 val evidence=prospectiveRequest(store,record)
 val original=evidence.getJSONObject("initial_passport");val root=original.getJSONObject("lineage").getString("passport_id")
 val tip=store.get("$LEARNED.tip.$root")
 val registry=store.json("d-day.evidence-exclusions.v1")
 // A previous recorded stationary test must not silently enter retrospective learning.
 if(tip==null&&registry==null){
  require(evidence.objects("records").flatMap {it.objects("journeys")}.filter {
   !Instant.parse(it.getJSONObject("experience").getString("confirmed_at")).isAfter(Instant.parse(job.getString("as_of")))
  }.all {
   it.getJSONObject("experience").getString("event_id")==job.getString("event_id")
  }) {"Configure the evidence exclusion registry before learning from existing history"}
 }
 return obj("schema_version" to "d-day-learned-passport-request-v1","as_of" to job.getString("as_of"),
  "initial_passport" to original.copyJson(),"records" to JSONArray(evidence.objects("records").map {
   it.copyJson().put("original_passport_id",root).put("subject_id",original.getString("agent_id"))
  }))
  .apply {
   registry?.let {put("evidence_exclusions",it.copyJson())}
   tip?.let {put("previous_version",store.json("$LEARNED.version.$it")?:error("Learned tip archive unavailable"))}
  }
}

internal fun validateLearnedVersion(v:JSONObject) {
 require(v.getString("schema_version")==LEARNED_SCHEMA&&v.getString("passport_id")=="learned-${v.getString("content_digest")}")
 require(v.getString("digest_encoding")=="d-day-typed-utf8-binary64-v1")
 require(learnedDigest(v.copyJson().apply {remove("passport_id");remove("content_digest")})==v.getString("content_digest")) {"Immutable learned Passport changed"}
 require(v.getInt("revision")>0&&v.getString("subject_id").isNotEmpty()&&v.getString("original_passport_id").isNotEmpty())
 Instant.parse(v.getString("evidence_cutoff"))
 val activation=v.getJSONObject("activation")
 require(!activation.getBoolean("applied")&&!activation.getBoolean("hotco_modified")&&!activation.getBoolean("state_carryover_applied"))
 val config=v.getJSONObject("configuration")
 require(config.getString("weather_model_version")=="d-day-weather-intensity-v2"&&config.getDouble("diffusion")==0.0&&
  config.getString("update_strategy")=="original_prior_plus_unique_full_history")
 val ledger=v.objects("evidence_ledger")
 require(ledger.size==v.getJSONArray("evidence_ledger").length())
 require(ledger.map {"${it.getString("target")}|${it.getString("event_id")}|${it.getString("mode")}"}.distinct().size==ledger.size)
 ledger.forEach {require(it.getString("target") in listOf("comfort","affect")&&it.getString("mode") in modes)}
 val targets=v.getJSONObject("targets");require(targets.keys().asSequence().toSet()==setOf("comfort","affect"))
 for(target in listOf("comfort","affect")){
  val fitted=targets.getJSONObject(target)
  fitted.keys().forEach {mode->
   require(mode in modes);val models=fitted.getJSONObject(mode)
   require(models.keys().asSequence().toSet()==setOf("basic","contextual"))
   for(name in listOf("basic","contextual")){
    val model=models.getJSONObject(name);val ids=model.getJSONArray("event_ids")
    val expected=ledger.filter {it.getString("target")==target&&it.getString("mode")==mode}.map {it.getString("event_id")}
    require((0 until ids.length()).map {ids.getString(it)}==expected)
    require(model.getBoolean("contextual")== (name=="contextual"))
    val samples=model.getJSONObject("samples");val shape=samples.getJSONArray("shape")
    require(samples.getString("codec")=="zlib-base64-float64-le-v1"&&shape.length()==3&&shape.getInt(0)==4&&
     shape.getInt(1) in 128..4096&&shape.getInt(2)==if(name=="contextual")18 else 1)
    require(samples.getString("data").isNotEmpty()&&samples.getString("digest").length==64)
    validateLearnedSamples(samples)
   }
  }
 }
}

internal fun validateLearnedSamples(samples:JSONObject) {
 val shape=samples.getJSONArray("shape")
 require(shape.length()==3&&shape.getInt(0)==4&&shape.getInt(1) in 128..4096&&shape.getInt(2) in listOf(1,18))
 val expected=shape.getInt(0)*shape.getInt(1)*shape.getInt(2)*8
 val inflater=Inflater();val bytes=ByteArray(expected+1)
 try {
  inflater.setInput(Base64.getDecoder().decode(samples.getString("data")))
  var length=0
  while(!inflater.finished()&&length<bytes.size){val n=inflater.inflate(bytes,length,bytes.size-length);require(n>0) {"Incomplete archived posterior"};length+=n}
  require(length==expected&&inflater.finished()&&inflater.remaining==0) {"Invalid archived posterior size"}
 }finally{inflater.end()}
 val raw=bytes.copyOf(expected);val encoded=Base64.getEncoder().encodeToString(raw)
 val digest=learnedDigest(obj("bytes_base64" to encoded))
 require(digest==samples.getString("digest")) {"Archived posterior digest mismatch"}
 val buffer=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
 while(buffer.hasRemaining())require(buffer.double.isFinite()) {"Invalid archived posterior values"}
}

internal fun validateLearnedResponse(request:JSONObject,result:JSONObject) {
 require(result.getString("schema_version")=="d-day-learned-passport-result-v1")
 require(result.getString("request_digest")==learnedDigest(request)) {"Frozen learning request/result mismatch"}
 require(!result.getBoolean("learning_applied_to_active_passport")&&!result.getBoolean("hotco_modified")&&!result.getBoolean("stored_on_server"))
 val root=request.getJSONObject("initial_passport").getJSONObject("lineage").getString("passport_id")
 require(result.getString("original_passport_id")==root)
 val previous=request.optJSONObject("previous_version")
 var parent=previous?.getString("passport_id")?:root;var revision=previous?.getInt("revision")?:0
 val priorId=previous?.getString("passport_id")
 require(result.optString("previous_passport_id").takeUnless {it.isEmpty()||it=="null"}==priorId)
 val versions=result.objects("new_versions");require(versions.size==result.getJSONArray("new_versions").length())
 var ledger=previous?.objects("evidence_ledger")?:emptyList()
 versions.forEach {v->
  validateLearnedVersion(v)
  require(v.getString("original_passport_id")==root&&v.getString("parent_passport_id")==parent&&v.getInt("revision")==revision+1)
  require(!Instant.parse(v.getString("evidence_cutoff")).isAfter(Instant.parse(request.getString("as_of"))))
  val next=v.objects("evidence_ledger")
  require(next.size>ledger.size&&canonical(JSONArray(next.take(ledger.size)))==canonical(JSONArray(ledger)))
  parent=v.getString("passport_id");revision=v.getInt("revision");ledger=next
 }
 val tip=result.optString("tip_passport_id").takeUnless {it.isEmpty()||it=="null"}
 require(tip==if(versions.isEmpty())priorId else parent)
 require(result.getString("status")==if(versions.isEmpty())"unchanged" else "updated")
}

internal fun validateLearnedStorage(values:JSONObject) {
 val versions=mutableMapOf<String,JSONObject>()
 values.keys().asSequence().filter {it.startsWith("$LEARNED.version.")}.forEach {key->
  val v=JSONObject(values.getString(key));validateLearnedVersion(v)
  require(key=="$LEARNED.version.${v.getString("passport_id")}");versions[v.getString("passport_id")]=v
 }
 versions.values.forEach {v->
  val parent=v.getString("parent_passport_id");val root=v.getString("original_passport_id")
  val wrapper=JSONObject(values.optString("$PASSPORT.archive.$root",values.optString(PASSPORT,"{}")))
  val original=wrapper.optJSONObject("response")?.optJSONObject("cognitive_passport")?:error("Original learned Passport unavailable")
  require(original.getJSONObject("lineage").getString("passport_id")==root&&original.getString("agent_id")==v.getString("subject_id")&&learnedDigest(original)==v.getString("original_passport_digest"))
  if(parent==root)require(v.getInt("revision")==1)else {
   val p=versions[parent]?:error("Missing learned Passport ancestor")
   require(p.getString("original_passport_id")==root&&v.getInt("revision")==p.getInt("revision")+1)
   require(canonical(JSONArray(v.objects("evidence_ledger").take(p.objects("evidence_ledger").size)))==canonical(p.getJSONArray("evidence_ledger")))
  }
 }
 values.keys().asSequence().filter {it.startsWith("$LEARNED.tip.")||it.startsWith("$LEARNED.review.")}.forEach {key->
  val root=key.substringAfter(if(key.startsWith("$LEARNED.tip."))"$LEARNED.tip." else "$LEARNED.review.")
  if(key.startsWith("$LEARNED.review.")&&values.getString(key)==root){
   require(values.has("$PASSPORT.archive.$root")||values.optString(PASSPORT).let {runCatching {JSONObject(it).getJSONObject("response").getJSONObject("cognitive_passport").getJSONObject("lineage").getString("passport_id")==root}.getOrDefault(false)})
  }else {
   val v=versions[values.getString(key)]?:error("Missing selected learned Passport")
   require(v.getString("original_passport_id")==root)
  }
 }
 values.keys().asSequence().filter {it.startsWith("$LEARNED.job.")}.forEach {key->
  val job=JSONObject(values.getString(key))
  require(job.getString("schema_version")=="d-day-learned-job-v1"&&key=="$LEARNED.job.${job.getString("event_id")}")
  require(job.getString("status") in listOf("queued","pending","failed","complete"));Instant.parse(job.getString("as_of"))
  if(job.getString("status")=="complete"){
   val initial=job.getJSONObject("request").getJSONObject("initial_passport")
   val root=initial.getJSONObject("lineage").getString("passport_id")
   val wrapper=JSONObject(values.optString("$PASSPORT.archive.$root",values.optString(PASSPORT,"{}")))
   require(canonical(initial)==canonical(wrapper.getJSONObject("response").getJSONObject("cognitive_passport")))
   validateLearnedResponse(job.getJSONObject("request"),job.getJSONObject("result"))
   job.getJSONObject("result").objects("new_versions").forEach {v->require(canonical(v)==canonical(versions[v.getString("passport_id")]))}
  }
 }
}
