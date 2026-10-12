package de.ovgu.imiq.cricket

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import java.io.ByteArrayOutputStream

fun obj(vararg values: Pair<String,Any?>) = JSONObject().apply { values.forEach { put(it.first,it.second ?: JSONObject.NULL) } }
fun JSONObject.objects(key:String):List<JSONObject> = optJSONArray(key).objects()
fun JSONArray?.objects():List<JSONObject> = if(this==null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
fun JSONObject.copyJson() = JSONObject(toString())
fun JSONObject.number(key:String):Double? = if(isNull(key)) null else optDouble(key).takeIf { it.isFinite() }
fun now() = Instant.now().toString()
fun week() = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()
const val PASSPORT = "imiq.experimental.cognitive-passport.v1"
const val COMPANION = "imiq.experimental.companion.v1"
const val SETTINGS = "cricket.settings.v1"
const val ONBOARDING = "imiq.experimental.onboarding-step.v1"
val modes = listOf("walk","bike","pt","car")
val goalKeys = listOf("env","health_activity","cost","time","comfort_physical","reliable","flex","safety_crime","health_infection","crowding","safety_accident")
val needKeys = listOf("pro_env","physical","cost","speed","comfort","reliable","autonomy","safety_crime","health_infection","privacy","safety_accident")
fun modelMode(value:String) = when(value) { "foot"->"walk";"car_driver"->"car";"pt_bus_tram"->"pt";else->value }

private class StoreDatabase(context:Context,name:String):SQLiteOpenHelper(context,name,null,1) {
 init {setWriteAheadLoggingEnabled(true)}
 override fun onCreate(db:SQLiteDatabase) {
  db.execSQL("CREATE TABLE kv (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
  db.execSQL("CREATE TABLE searches (id TEXT PRIMARY KEY, created TEXT NOT NULL, value TEXT NOT NULL)")
 }
 override fun onUpgrade(db:SQLiteDatabase,old:Int,new:Int) = Unit
}

/** One connection pool per file, shared by the UI, background reads and GPS.
 * Separate helpers otherwise compete for SQLite's writer lock even with WAL.
 */
private object StoreDatabases {
 private data class Entry(val helper:StoreDatabase,var references:Int)
 private val entries=mutableMapOf<String,Entry>()
 @Synchronized fun acquire(context:Context,name:String):Pair<String,StoreDatabase> {
  val app=context.applicationContext
  val key=app.getDatabasePath(name).canonicalPath
  val entry=entries.getOrPut(key){Entry(StoreDatabase(app,name),0)}
  entry.references++
  return key to entry.helper
 }
 @Synchronized fun release(key:String) {
  val entry=entries.getValue(key)
  if(--entry.references==0){entry.helper.close();entries.remove(key)}
 }
}

class Store(private val context:Context,val databaseName:String="cricket-native-v1.db"):java.io.Closeable {
 private val shared=StoreDatabases.acquire(context,databaseName)
 private val closed=java.util.concurrent.atomic.AtomicBoolean(false)
 val readableDatabase:SQLiteDatabase get(){check(!closed.get()) {"Store is closed"};return shared.second.readableDatabase}
 val writableDatabase:SQLiteDatabase get(){check(!closed.get()) {"Store is closed"};return shared.second.writableDatabase}
 override fun close(){if(closed.compareAndSet(false,true))StoreDatabases.release(shared.first)}
 // Android CursorWindow limits each row, even when SQLite successfully stores it.
 // Read bytes in bounded chunks; decoding once preserves Unicode across boundaries.
 private fun <T> snapshot(block:(SQLiteDatabase)->T):T {
  val db=readableDatabase
  if(db.inTransaction())return block(db)
  db.beginTransactionNonExclusive()
  try {val result=block(db);db.setTransactionSuccessful();return result}finally{db.endTransaction()}
 }
 private fun largeText(table:String,column:String,key:String):String?=snapshot {db->
  require(table in listOf("kv","searches")&&column in listOf("key","id"))
  val bytes=db.rawQuery("SELECT length(CAST(value AS BLOB)) FROM $table WHERE $column=?",arrayOf(key)).use {if(it.moveToFirst())it.getLong(0)else return@snapshot null}
  require(bytes<=Int.MAX_VALUE) {"Stored record exceeds supported size"}
  val result=ByteArrayOutputStream(minOf(bytes,262144L).toInt())
  var offset=0L
  while(offset<bytes){
   val chunk=db.rawQuery("SELECT substr(CAST(value AS BLOB),?,262144) FROM $table WHERE $column=?",arrayOf((offset+1).toString(),key)).use {check(it.moveToFirst()) {"Stored record disappeared"};it.getBlob(0)}
   check(chunk.isNotEmpty()) {"Incomplete stored record"}
   result.write(chunk);offset+=chunk.size
  }
  result.toByteArray().toString(Charsets.UTF_8)
 }
 fun get(key:String):String? {
  // Small UI settings need one read, without taking a write transaction lock.
  val first=readableDatabase.rawQuery("SELECT substr(CAST(value AS BLOB),1,262145) FROM kv WHERE key=?",arrayOf(key)).use {if(it.moveToFirst())it.getBlob(0)else return null}
  return if(first.size<=262144)first.toString(Charsets.UTF_8)else largeText("kv","key",key)
 }
 fun json(key:String):JSONObject? = runCatching { get(key)?.let(::JSONObject) }.getOrNull()
 fun put(key:String,value:Any) { writableDatabase.execSQL("INSERT OR REPLACE INTO kv VALUES (?,?)",arrayOf(key,value.toString())) }
 fun remove(key:String) { writableDatabase.execSQL("DELETE FROM kv WHERE key=?",arrayOf(key)) }
 fun storage():JSONObject = snapshot {db->val keys=db.rawQuery("SELECT key FROM kv",null).use {c->buildList {while(c.moveToNext())add(c.getString(0))}};JSONObject().apply {keys.forEach {key->put(key,this@Store.get(key))}}}
 fun passport():JSONObject? = json(PASSPORT)?.takeIf { validPassport(it) }
 fun confirmedSnapshot(record:JSONObject):JSONObject? {
  record.optJSONObject("confirmed_snapshot")?.let { return it }
  val reference=record.optJSONObject("passport_reference")?:return null
  val values=storage()
  return values.keys().asSequence().filter { it==PASSPORT||it.startsWith("$PASSPORT.archive.") }.mapNotNull { key->json(key) }.firstOrNull { item->
   val lineage=item.optJSONObject("response")?.optJSONObject("cognitive_passport")?.optJSONObject("lineage")
   lineage?.optString("passport_id")==reference.optString("passport_id")&&lineage?.optInt("revision")==reference.optInt("revision")&&item.optString("fingerprint")==reference.optString("fingerprint")
  }
 }
 fun history():List<JSONObject> = snapshot {db->val ids=db.rawQuery("SELECT id FROM searches ORDER BY created DESC",null).use {c->buildList {while(c.moveToNext())add(c.getString(0))}};ids.mapNotNull(::record)}
 fun historyPreviews(limit:Int):List<JSONObject> {
  require(limit>0)
  // Project in SQLite: no multi-megabyte result or forecast reaches CursorWindow/JVM.
  val query="""SELECT json_object('history_preview',json('true'),'search_id',id,
   'created_at',created,'status',json_extract(value,'$.status'),'places',json_extract(value,'$.places'),
   'choices',json((SELECT json_group_array(json_object('mode_key',json_extract(c.value,'$.mode_key'),
    'chosen_at',json_extract(c.value,'$.chosen_at'),'choice_event_id',json_extract(c.value,'$.choice_event_id'),
    'routing_rank',json_extract(c.value,'$.routing_rank'),'route_id',json_extract(c.value,'$.route_id')))
    FROM json_each(searches.value,'$.choices') c)),
   'journeys',json((SELECT json_group_array(json_object('journey_id',json_extract(j.value,'$.journey_id'),
    'status',json_extract(j.value,'$.status'),'capture_mode',json_extract(j.value,'$.capture_mode'),
    'tracking',json_object('distance_m',json_extract(j.value,'$.tracking.distance_m'),
     'elapsed_seconds',json_extract(j.value,'$.tracking.elapsed_seconds'))))
    FROM json_each(searches.value,'$.journeys') j)))
   FROM searches ORDER BY CASE WHEN json_array_length(value,'$.journeys')>0 THEN 0
    WHEN json_array_length(value,'$.choices')>0 THEN 1 ELSE 2 END,created DESC LIMIT ?"""
  return readableDatabase.rawQuery(query,arrayOf(limit.toString())).use {c->buildList {while(c.moveToNext())add(JSONObject(c.getString(0)))}}
 }
 /** A bounded SQL projection: no trajectories, GPS, weather arrays or notes. */
 internal fun companionEvidence(subject:String):List<JSONObject> {
  val query="""SELECT json_object('search_id',s.id,
   'passport',json_extract(s.value,'$.request.cognitive_passport'),
   'evidence_classification',json_extract(s.value,'$.evidence_classification'),
   'journey',json_object('journey_id',json_extract(j.value,'$.journey_id'),
    'status',json_extract(j.value,'$.status'),'selection',json_extract(j.value,'$.selection'),
    'evidence_classification',COALESCE(json_extract(j.value,'$.evidence_classification'),json_extract(s.value,'$.evidence_classification')),
    'experience',json_object('event_id',json_extract(j.value,'$.experience.event_id'),
     'confirmed_at',json_extract(j.value,'$.experience.confirmed_at'),
     'completion',json_extract(j.value,'$.experience.completion'),
     'actual_modes',json_extract(j.value,'$.experience.actual_modes'),
     'affect_scale_version',json_extract(j.value,'$.experience.affect_scale_version'),
     'mode_valences_raw_minus3_to_3',json_extract(j.value,'$.experience.mode_valences_raw_minus3_to_3'),
     'comfort_observation',json_extract(j.value,'$.experience.comfort_observation'),
     'need_satisfaction_raw_1_to_7',json_extract(j.value,'$.experience.need_satisfaction_raw_1_to_7'))),
   'hotco',CASE WHEN json_type(j.value,'$.plan.context_at_selection.snapshot.hotco')='object' THEN
    json_object('node_activations',json_object('needs',json_object('final',json_extract(j.value,'$.plan.context_at_selection.snapshot.hotco.node_activations.needs.final'))),
     'process_diagnostics',json_object('support_dynamics',json_extract(j.value,'$.plan.context_at_selection.snapshot.hotco.process_diagnostics.support_dynamics'))) ELSE NULL END)
   FROM searches s,json_each(s.value,'$.journeys') j
   WHERE json_type(j.value,'$.experience')='object'
    AND json_extract(s.value,'$.request.cognitive_passport.agent_id')=?
   ORDER BY json_extract(j.value,'$.experience.confirmed_at') DESC,s.id,json_extract(j.value,'$.journey_id') LIMIT 60"""
  return readableDatabase.rawQuery(query,arrayOf(subject)).use {c->buildList {while(c.moveToNext())add(JSONObject(c.getString(0)))}}
 }
 internal fun companionLearnedHeader(root:String):JSONObject? {
  val tip=get("$LEARNED.tip.$root")?:return null
  return readableDatabase.rawQuery("SELECT json_object('schema_version',json_extract(value,'$.schema_version'),'original_passport_id',json_extract(value,'$.original_passport_id'),'revision',json_extract(value,'$.revision'),'evidence_cutoff',json_extract(value,'$.evidence_cutoff'),'activation',json_extract(value,'$.activation')) FROM kv WHERE key=?",arrayOf("$LEARNED.version.$tip")).use {if(it.moveToFirst())JSONObject(it.getString(0))else null}
 }
 // Research uses per-record snapshots: do not retain every large trajectory or
 // hold one writer transaction across the entire history while GPS is active.
 internal fun forEachResearchRecord(block:(JSONObject)->Unit) {
  val ids=readableDatabase.rawQuery("SELECT id FROM searches ORDER BY created DESC",null).use {c->buildList {while(c.moveToNext())add(c.getString(0))}}
  ids.forEach {id->streamedRecord(id,true)?.let(block)}
 }
 // Stop at the first pending experience instead of parsing every search.
 // Run on Dispatchers.IO: individual records can still exceed several MB.
 fun firstPendingJourney():Pair<JSONObject,JSONObject>? = snapshot {db->
  // Store writes JSONObject.toString(): a pending status always has this token.
  // False positives are checked below; unrelated large records stay in SQLite.
  val ids=db.rawQuery("SELECT id FROM searches WHERE instr(value,?)>0 ORDER BY created DESC",arrayOf("\"awaiting_confirmation\"")).use {c->buildList {while(c.moveToNext())add(c.getString(0))}}
  ids.firstNotNullOfOrNull {id->record(id)?.let {record->record.objects("journeys").lastOrNull {it.optString("status")=="awaiting_confirmation"}?.let {record to it}}}
 }
 private fun streamedRecord(id:String,research:Boolean=false,ui:Boolean=false,history:Boolean=false,reflection:Boolean=false):JSONObject?=snapshot {db->
  val exists=db.rawQuery("SELECT 1 FROM searches WHERE id=?",arrayOf(id)).use {it.moveToFirst()}
  if(!exists)return@snapshot null
  val stream=object:java.io.InputStream() {
   var chunk=ByteArray(0);var index=0;var offset=1L;var ended=false
   override fun read():Int {if(index==chunk.size){if(ended)return -1;chunk=db.rawQuery("SELECT substr(CAST(value AS BLOB),?,262144) FROM searches WHERE id=?",arrayOf(offset.toString(),id)).use {check(it.moveToFirst());it.getBlob(0)};index=0;offset+=chunk.size;if(chunk.isEmpty()){ended=true;return -1}};return chunk[index++].toInt() and 255}
   override fun read(buffer:ByteArray,start:Int,length:Int):Int {if(length==0)return 0;val first=read();if(first<0)return -1;buffer[start]=first.toByte();val count=minOf(length-1,chunk.size-index);chunk.copyInto(buffer,start+1,index,index+count);index+=count;return count+1}
  }
  stream.reader(Charsets.UTF_8).use {readStreamingRecord(it,research,ui,history,reflection)}
 }
 internal fun researchRecord(id:String):JSONObject? = streamedRecord(id,true)
 fun recordForUi(id:String):JSONObject? = streamedRecord(id,ui=true)
 fun recordForReflection(id:String):JSONObject? = streamedRecord(id,reflection=true)
 fun saveExplanation(id:String,index:Int,language:String,response:JSONObject) {
  val db=writableDatabase;db.beginTransaction()
  try {
   val record=recordForReflection(id)?:error("Journey not saved")
   require(response.getJSONObject("identity").getInt("choice_index")==index)
   require(record.objects("choices").getOrNull(index)?.optString("chosen_at")==response.getJSONObject("identity").optString("chosen_at")) {"Saved choice changed"}
   val entries=record.optJSONArray("explanations")?:JSONArray()
   val previous=entries.objects().lastOrNull{it.optInt("choice_index")==index&&it.optString("language")==language}?.optJSONObject("response")
   if(previous!=null&&canonical(previous)==canonical(response)){db.setTransactionSuccessful();return}
   entries.put(obj("choice_index" to index,"language" to language,"response" to response,"created_at" to now()))
   // SQLite updates the original document; large archives never enter the JVM.
   db.execSQL("UPDATE searches SET value=json_set(value,'$.explanations',json(?)) WHERE id=?",arrayOf(entries.toString(),id))
   db.setTransactionSuccessful()
  }finally{db.endTransaction()}
 }
 fun record(id:String):JSONObject? = streamedRecord(id)
 fun saveRecord(record:JSONObject) { require(validRecord(record));writableDatabase.execSQL("INSERT OR REPLACE INTO searches VALUES (?,?,?)",arrayOf(record.getString("search_id"),record.getString("created_at"),record.toString())) }
 fun mutateRecord(id:String,block:(JSONObject)->Unit) {
  val db=writableDatabase;db.beginTransaction();try {val record=record(id)?:error("Journey not found");block(record);saveRecord(record);db.setTransactionSuccessful()}finally{db.endTransaction()}
 }
 fun export():JSONObject {
  val captured=snapshot {Triple(storage().apply {remove(ACTIVE_TRIP)},history(),now())}
  // Index and check the captured values outside the transaction: GPS writes can resume.
  val (values,records,at)=captured
  validateLearnedStorage(values)
  return obj("schema" to "cricket-backup-v1","exported_at" to at,"storage" to values,"simulations" to JSONArray(records),"longitudinal" to longitudinalIndex(values,records))
 }
 fun importBackup(backup:JSONObject,replace:Boolean=true) {
  require(json(ACTIVE_TRIP)==null) { "Finish the active journey before importing" }
  require(backup.optString("schema")=="cricket-backup-v1") { "Unsupported backup" }
  val values=backup.getJSONObject("storage"); val records=backup.getJSONArray("simulations").objects()
  validateLearnedStorage(values)
  val index=longitudinalIndex(values,records)
  if(backup.has("longitudinal"))require(canonical(backup.getJSONObject("longitudinal"))==canonical(index)) {"Inconsistent D-Day evidence index"}
  require(!values.has(ACTIVE_TRIP)) { "Backups cannot activate a tracking service" }
  require(records.size==backup.getJSONArray("simulations").length()&&records.all(::validRecord)) { "Invalid journey history" }
  val contract=JourneyContract(context.assets.open("journey-v1.schema.json").bufferedReader().use {JSONObject(it.readText())})
  records.forEach {record->record.objects("journeys").forEach {contract.validate(record,it);require(it.optString("status") !in liveTripStates) { "Finish tracked journeys before importing a backup" }}}
  if(values.has(PASSPORT))require(validPassport(JSONObject(values.getString(PASSPORT)))) { "Invalid Passport" }
  require(values.keys().asSequence().all { allowedKey(it) && values.opt(it) is String }) { "Invalid backup keys" }
  val db=writableDatabase;db.beginTransaction()
  try {
   records.forEach { r->record(r.getString("search_id"))?.let { require(canonical(it)==canonical(r)) { "Conflicting journey ID" } } }
   val previous=passport()
   if(replace) {
    val preserved=storage();db.execSQL("DELETE FROM kv")
    preserved.keys().asSequence().filter { it.startsWith("$PASSPORT.archive.")||it.startsWith("$LEARNED.version.") }.forEach { key->
     if(values.has(key))require(canonical(JSONObject(values.getString(key)))==canonical(JSONObject(preserved.getString(key)))) { "Conflicting Passport archive" }
     put(key,preserved.getString(key))
    }
   }
   values.keys().forEach { key->put(key,values.getString(key)) }
   validateLearnedStorage(storage())
   if(previous!=null&&previous.toString()!=get(PASSPORT))archive(previous)
   records.forEach(::saveRecord)
   db.setTransactionSuccessful()
  } finally { db.endTransaction() }
 }
 fun archive(item:JSONObject) {
  val id=item.optJSONObject("response")?.optJSONObject("cognitive_passport")?.optJSONObject("lineage")?.optString("passport_id")
  if(id.isNullOrEmpty())return
  val db=writableDatabase;val ownTransaction=!db.inTransaction()
  if(ownTransaction)db.beginTransaction()
  try {
   val key="$PASSPORT.archive.$id";val previous=json(key)
   require(previous==null||canonical(previous.getJSONObject("response").getJSONObject("cognitive_passport").copyJson())==canonical(item.getJSONObject("response").getJSONObject("cognitive_passport").copyJson())) {"Conflicting immutable Passport archive"}
   if(previous==null)put(key,item)
   if(ownTransaction)db.setTransactionSuccessful()
  }finally {if(ownTransaction)db.endTransaction()}
 }
 fun commitDailyPassport(expected:JSONObject,next:JSONObject,plan:JSONObject) {
  require(validPassport(next)) {"Invalid daily Passport"}
  val db=writableDatabase;db.beginTransaction()
  try {
   // Compare the same JSON round-trip: Android can serialize 1.0 as 1.
   require(canonical(passport())==canonical(expected.copyJson())) {"Your Passport changed. Confirm today's goals again."}
   archive(expected);put(PASSPORT,next)
   put(dailyKey(plan.getString("localDate"),"saved"),plan)
   remove(dailyKey(plan.getString("localDate"),"draft"))
   db.setTransactionSuccessful()
  }finally{db.endTransaction()}
 }
 fun reset() { require(json(ACTIVE_TRIP)==null) { "Finish the active journey first" };val db=writableDatabase;db.beginTransaction();try { db.execSQL("DELETE FROM searches");db.execSQL("DELETE FROM kv");db.setTransactionSuccessful() } finally { db.endTransaction() } }
 internal fun commitLearned(jobKey:String,request:JSONObject,result:JSONObject) {
  validateLearnedResponse(request,result)
  val root=result.getString("original_passport_id")
  val expected=request.optJSONObject("previous_version")?.getString("passport_id")
  val db=writableDatabase;db.beginTransaction()
  try {
   val job=json(jobKey)?:error("Missing durable learning job")
   require(canonical(job.getJSONObject("request"))==canonical(request.copyJson())) {"Frozen learning request changed"}
   if(job.optString("status")=="complete"){
    require(canonical(job.getJSONObject("result"))==canonical(result.copyJson())) {"Conflicting immutable learning result"}
   }else{
    require(get("$LEARNED.tip.$root")==expected) {"Concurrent learned Passport update; frozen job needs review"}
    result.objects("new_versions").forEach {v->
     val key="$LEARNED.version.${v.getString("passport_id")}";val old=json(key)
     require(old==null||canonical(old)==canonical(v.copyJson())) {"Conflicting immutable learned version"}
     if(old==null)put(key,v)
    }
    result.optString("tip_passport_id").takeUnless {it.isEmpty()||it=="null"}?.let {put("$LEARNED.tip.$root",it)}
    job.put("status","complete").put("recorded_at",now()).put("result",result)
    put(jobKey,job)
   }
   db.setTransactionSuccessful()
  }finally{db.endTransaction()}
 }
 /** Reversible shadow review pointer; append tip and active Passport remain independent. */
 internal fun selectLearned(root:String,id:String) {
  val db=writableDatabase;db.beginTransaction()
  try {
   if(id!=root){val version=json("$LEARNED.version.$id")?:error("Unknown learned Passport");validateLearnedVersion(version);require(version.getString("original_passport_id")==root)}
   else require(json("$PASSPORT.archive.$root")!=null||passport()?.getJSONObject("response")?.getJSONObject("cognitive_passport")?.getJSONObject("lineage")?.getString("passport_id")==root)
   val key="$LEARNED.review.$root";val history=json("$LEARNED.selections.$root")?:obj("events" to JSONArray())
   history.getJSONArray("events").put(obj("at" to now(),"before" to get(key),"after" to id,"activation_applied" to false))
   put(key,id);put("$LEARNED.selections.$root",history);db.setTransactionSuccessful()
  }finally{db.endTransaction()}
 }
 fun clearHistory() { require(json(ACTIVE_TRIP)==null) { "Finish the active journey first" };writableDatabase.execSQL("DELETE FROM searches") }
}
fun allowedKey(key:String) = key.startsWith("imiq.experimental.") || key in listOf("d-day.evidence-exclusions.v1","cricket.settings.v1","cricket.recent-places.v1","cricket.chosen-journey.v1","cricket.passport-renewal.v1","cricket.connection.v1",ACTIVE_TRIP)
fun validPassport(item:JSONObject):Boolean {
 val p=item.optJSONObject("response")?.optJSONObject("cognitive_passport")?:return false
 val profile=p.optJSONObject("profile")?:return false
 return item.optInt("version")==1&&item.opt("fingerprint") is String&&p.optString("schema_version")=="2.0"&&p.optJSONObject("lineage")?.opt("passport_id") is String&&profile.optJSONObject("needs")!=null&&modes.all { profile.optJSONObject("beliefs")?.optJSONObject(it)!=null&&profile.optJSONObject("availability")?.opt(it) is Boolean }
}
fun validRecord(record:JSONObject):Boolean {
 val id=record.optString("search_id");val result=record.optJSONObject("result");val choices=record.optJSONArray("choices")?:return false
 return (!record.has("journeys")||(record.optJSONArray("journeys")!=null&&record.objects("journeys").size==record.getJSONArray("journeys").length()&&record.objects("journeys").map {it.optString("journey_id")}.distinct().size==record.getJSONArray("journeys").length()))&&record.optInt("version")==1&&id.isNotEmpty()&&record.optString("created_at").isNotEmpty()&&record.optString("status") in listOf("pending","complete","failed")&&record.optJSONObject("request")?.optString("search_id")==id&&
  (record.optString("status")!="complete"||(result?.optString("search_id")==id&&result.optString("schema_version")=="routed-contextual-deliberation-v1"&&result.optJSONObject("routing")?.optJSONArray("routes")!=null&&result.optJSONArray("route_audit")!=null))&&
  choices.objects().size==choices.length()&&choices.objects().all { choice->result?.optJSONObject("routing")?.objects("routes")?.any { it.optInt("rank")==choice.optInt("routing_rank")&&it.optString("mode_key")==choice.optString("mode_key")&&it.optBoolean("available") }==true }
}
fun canonical(value:Any?):String = when(value) {
 is JSONObject->value.keys().asSequence().sorted().joinToString(prefix="{",postfix="}") { JSONObject.quote(it)+":"+canonical(value.opt(it)) }
 is JSONArray->(0 until value.length()).joinToString(prefix="[",postfix="]") { canonical(value.opt(it)) }
 is String->JSONObject.quote(value)
 else->value?.toString()?:"null"
}

class Api(private val store:Store) {
 fun base():String = normalizeBase(store.get("cricket.connection.v1")?:"http://localhost:8077")
 fun normalizeBase(text:String):String {
  val url=URI(text.trim());require(url.scheme in listOf("https","http")&&!url.host.isNullOrEmpty()&&url.userInfo==null&&url.query==null&&url.fragment==null) { "Enter a valid backend URL" };return url.toString().trimEnd('/')
 }
 suspend fun call(path:String,payload:JSONObject?=null,timeout:Int=20000):JSONObject = withContext(Dispatchers.IO) {
  val conn=URL(base()+path).openConnection() as HttpURLConnection
  try {
   conn.connectTimeout=15000;conn.readTimeout=timeout;conn.setRequestProperty("Accept","application/json")
   if(payload!=null){conn.requestMethod="POST";conn.doOutput=true;conn.setRequestProperty("Content-Type","application/json");conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }}
   val status=conn.responseCode;val text=(if(status in 200..299)conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
   ensureActive()
   val data=runCatching { JSONObject(text) }.getOrNull()
   if(status !in 200..299)throw IllegalStateException(data?.optString("message")?.takeIf { it.isNotEmpty() }?:"Server error $status")
   data?:throw IllegalStateException("Invalid server response")
  } finally { conn.disconnect() }
 }
}
