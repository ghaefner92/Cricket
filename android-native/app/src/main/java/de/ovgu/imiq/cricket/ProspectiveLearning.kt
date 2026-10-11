package de.ovgu.imiq.cricket

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import kotlin.math.abs
import kotlin.math.ln

/** Research jobs never participate in the route UI busy state or active HOTCO inputs. */
object ProspectiveLearning {
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
 private val gate=Mutex()
 fun enqueue(context:Context,id:String,databaseName:String="cricket-native-v1.db") = scope.launch {gate.withLock {Store(context.applicationContext,databaseName).use {store->
  try {
   val header=store.researchRecord(id)?:return@use
   if(header.optString("status")!="complete"||header.optJSONObject("d_day_forecast")?.optString("status") in listOf("complete","failed"))return@use
   val record=store.record(id)?:return@use
   val existing=record.optJSONObject("d_day_forecast")?.optJSONObject("request")
   val request=existing?:prospectiveRequest(store,record)
   if(existing==null)store.mutateRecord(id){it.put("d_day_forecast",obj("status" to "pending","as_of" to request.getString("as_of"),"request" to request))}
   val response=Api(store).call("/api/dyconet/d-day/prospective/forecast",request,180000)
   require(response.optString("schema_version")=="d-day-prospective-forecast-v1"&&response.getString("search_id")==id&&response.getString("as_of")==request.getString("as_of"))
   require(!response.getBoolean("learning_applied_to_active_passport")&&!response.getBoolean("hotco_modified"))
   val received=now()
   store.mutateRecord(id){current->
    val frozen=current.getJSONObject("d_day_forecast")
    require(canonical(frozen.getJSONObject("request"))==canonical(request))
    frozen.put("status","complete").put("recorded_at",received).put("result",response)
    current.objects("journeys").filter {it.optJSONObject("experience")!=null}.forEach {appendProspectiveEvaluation(current,it)}
   }
   TripUpdates.changed()
  }catch(e:Exception){
   runCatching {store.mutateRecord(id){it.put("d_day_forecast",(it.optJSONObject("d_day_forecast")?:JSONObject()).put("status","failed").put("failed_at",now()).put("failure",e.message?.take(300)?:"Forecast failed"))}}
  }
 }}}
 fun resume(context:Context) {scope.launch {Store(context.applicationContext).use {store->store.forEachResearchRecord {if(it.optJSONObject("d_day_forecast")?.optString("status") in listOf("queued","pending"))enqueue(context,it.getString("search_id"))}}}}
}

internal fun originalPassport(store:Store,wrapper:JSONObject):JSONObject {
 var p=wrapper.getJSONObject("response").getJSONObject("cognitive_passport")
 val seen=mutableSetOf<String>()
 while(true){
  val lineage=p.getJSONObject("lineage");require(seen.add(lineage.getString("passport_id"))) {"Cyclic Passport lineage"}
  val parent=lineage.optString("parent_passport_id").takeIf {it.isNotEmpty()&&it!="null"}?:return p
  p=(store.json("$PASSPORT.archive.$parent")?:error("Original Passport archive unavailable")).getJSONObject("response").getJSONObject("cognitive_passport")
 }
}

internal fun prospectiveRequest(store:Store,record:JSONObject):JSONObject {
 val original=originalPassport(store,record.getJSONObject("confirmed_snapshot"))
 val rootId=original.getJSONObject("lineage").getString("passport_id")
 val prior=JSONArray();val excluded=JSONArray()
 store.forEachResearchRecord {old->
  if(old.objects("journeys").none {it.optJSONObject("experience")!=null})return@forEachResearchRecord
  val root=runCatching {originalPassport(store,old.getJSONObject("confirmed_snapshot"))}.getOrNull()
  if(root?.getJSONObject("lineage")?.getString("passport_id")!=rootId){excluded.put(old.getString("search_id"));return@forEachResearchRecord}
  prior.put(obj("completed_at" to old.opt("completed_at"),"journeys" to JSONArray(old.objects("journeys").filter {it.optJSONObject("experience")!=null}.map {j->
   val plan=j.getJSONObject("plan");val candidate=plan.getJSONObject("context_at_selection").optJSONObject("snapshot")
   obj("journey_id" to j.getString("journey_id"),"choice_event_id" to j.getString("choice_event_id"),"selection" to j.getJSONObject("selection").copyJson(),"experience" to j.getJSONObject("experience").copyJson(),
    "plan" to obj("route" to obj("summary" to plan.getJSONObject("route").optJSONObject("summary")),"context_at_selection" to obj("snapshot" to obj("route_context" to candidate?.optJSONObject("route_context")))))
    .apply {if(j.has("status"))put("status",j.getString("status"));
     val classification=j.opt("evidence_classification")?:old.opt("evidence_classification")
     if(classification!=null&&classification!=JSONObject.NULL)put("evidence_classification",classification)}
  })))
 }
 val candidates=cards(record.getJSONObject("result")).filter {it.route.optBoolean("available")}.map {card->obj("prediction_id" to card.id,"mode" to card.mode,
  "route" to obj("summary" to card.route.optJSONObject("summary")),"snapshot" to obj("route_context" to card.candidate?.optJSONObject("route_context")))}
 return obj("schema_version" to "d-day-prospective-request-v1","search_id" to record.getString("search_id"),"as_of" to record.getString("completed_at"),
  "initial_passport" to original.copyJson(),"records" to prior,"candidates" to JSONArray(candidates),"excluded_record_ids" to excluded,
  "weather_model_version" to "d-day-weather-intensity-v2")
  .apply {store.json("d-day.evidence-exclusions.v1")?.let {put("evidence_exclusions",it.copyJson())}}
}

internal fun prospectiveEvaluation(record:JSONObject,journey:JSONObject):JSONObject {
 val e=journey.getJSONObject("experience");val f=record.optJSONObject("d_day_forecast")
 val evaluation=obj("schema_version" to "d-day-prospective-evaluation-v1","event_id" to e.getString("event_id"),"journey_id" to journey.getString("journey_id"),
  "choice_event_id" to journey.getString("choice_event_id"),"evaluated_at" to now(),"learning_applied" to false,"scores" to JSONArray())
 fun skip(reason:String)=evaluation.put("status","unscored").put("reason",reason)
 val classification=journey.opt("evidence_classification")?:record.opt("evidence_classification")
 if(classification!=null&&classification!=JSONObject.NULL){require(classification in listOf("real_experience","test","synthetic","stationary_test"));if(classification!="real_experience")return skip("explicit_$classification")}
 if(journey.optString("status")=="cancelled")return skip("trip_cancelled")
 if(e.getString("completion")=="not_taken")return skip("trip_not_taken")
 if(e.getString("completion")=="changed")return skip("changed_route_context_not_attributable")
 if(f?.optString("status")!="complete")return skip("forecast_not_available")
 val selection=journey.getJSONObject("selection")
 if(!Instant.parse(f.getString("recorded_at")).isBefore(Instant.parse(selection.getString("chosen_at"))))return skip("forecast_recorded_after_choice")
 val result=f.getJSONObject("result");val id="${selection.getInt("routing_rank")}-${selection.getString("mode_key")}"
 evaluation.put("forecast_request_digest",result.getString("request_digest")).put("prediction_id",id).put("as_of",result.getString("as_of"))
 val scores=evaluation.getJSONArray("scores")
 for(target in listOf("comfort","affect")){
  val observation=e.optJSONObject("comfort_observation")
  val mode=if(target=="comfort")observation?.optString("mode")else modelMode(selection.getString("mode_key"))
  val raw=if(target=="comfort")observation?.number("rating")?.toInt()else e.optJSONObject("mode_valences_raw_minus3_to_3")?.number(mode?:"")?.toInt()
  if(raw==null||mode==null)continue
  val prediction=result.getJSONObject("targets").getJSONObject(target).objects("predictions").find {it.getString("prediction_id")==id&&it.getString("mode")==mode}?:continue
  val y=if(target=="comfort")raw-1 else raw+3
  val normalized=if(target=="comfort")(raw-4)/3.0 else raw/3.0
  val models=prediction.getJSONObject("models")
  for(name in listOf("static","basic","contextual")){
   val model=models.getJSONObject(name)
   if(!model.optBoolean("prediction_usable_for_research"))continue
   val p=model.getJSONArray("category_probabilities");require(p.length()==7)
   val values=(0..6).map {p.getDouble(it)};require(values.all {it.isFinite()&&it>=0}&&abs(values.sum()-1)<1e-6)
   var cumulative=0.0;var rps=0.0
   for(k in 0..5){cumulative+=values[k];rps+=(cumulative-(if(y<=k)1.0 else 0.0)).let {it*it}}
   val mean=values.mapIndexed {i,v->v*(i-3)/3.0}.sum()
   val score=obj("target" to target,"mode" to mode,"model" to name,"raw_response" to raw,"normalized_response" to normalized,
    "log_score" to ln(values[y].coerceAtLeast(1e-15)),"ranked_probability_score" to rps/6,"normalized_absolute_error" to abs(mean-normalized),"predicted_normalized_mean" to mean)
   result.getJSONObject("targets").getJSONObject(target).optJSONObject("weather_contract")?.let {score.put("weather_model_version",it.getString("weather_model_version"))}
   scores.put(score)
  }
 }
 return evaluation.put("status",if(scores.length()>0)"scored"else"unscored").apply {if(scores.length()==0)put("reason","no_eligible_prediction")}
}

internal fun appendProspectiveEvaluation(record:JSONObject,journey:JSONObject) {
 if(!record.has("d_day_forecast"))return // Historic records remain unchanged.
 val evaluations=record.optJSONArray("d_day_evaluations")?:JSONArray()
 val event=journey.getJSONObject("experience").getString("event_id")
 val existing=evaluations.objects().find {it.getString("event_id")==event}
 // A forecast pending at confirmation may finish later; classify it explicitly.
 if(existing!=null&&existing.optString("reason")!="forecast_not_available")return
 val next=JSONArray(evaluations.objects().filter {it.getString("event_id")!=event});next.put(prospectiveEvaluation(record,journey));record.put("d_day_evaluations",next)
}

internal fun validateProspectiveRecord(record:JSONObject) {
 val f=record.optJSONObject("d_day_forecast")
 if(f==null){require(record.objects("d_day_evaluations").isEmpty()) {"Orphan research evaluation"};return}
 require(f.getString("status") in listOf("queued","pending","complete","failed"))
 if(f.getString("status")=="complete"){
  val r=f.getJSONObject("result");val request=f.getJSONObject("request")
  require(r.getString("schema_version")=="d-day-prospective-forecast-v1"&&r.getString("search_id")==record.getString("search_id")&&request.getString("search_id")==record.getString("search_id")&&r.getString("as_of")==request.getString("as_of"))
  require(!r.getBoolean("learning_applied_to_active_passport")&&!r.getBoolean("hotco_modified"))
  require(!Instant.parse(f.getString("recorded_at")).isBefore(Instant.parse(r.getString("as_of"))))
 }
 val seen=mutableSetOf<String>()
 record.objects("d_day_evaluations").forEach {stored->
  require(seen.add(stored.getString("event_id"))) {"Duplicate research evaluation"}
  val j=record.objects("journeys").single {it.optJSONObject("experience")?.optString("event_id")==stored.getString("event_id")}
  val expected=prospectiveEvaluation(record,j).apply {remove("evaluated_at")}
  require(canonical(stored.copyJson().apply {remove("evaluated_at")})==canonical(expected.copyJson())) {"Research evaluation inconsistent with frozen forecast"}
 }
}
