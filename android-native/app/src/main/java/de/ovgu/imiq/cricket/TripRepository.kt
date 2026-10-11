package de.ovgu.imiq.cricket

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.update

data class TrackingFailure(val searchId:String?,val message:String)
object TripUpdates {
 val revision=MutableStateFlow(0L)
 val errors=MutableSharedFlow<TrackingFailure>(extraBufferCapacity=4)
 fun changed(){revision.update {it+1}}
}

class TripRepository(context:Context,private val store:Store=Store(context)) {
 private val context=context.applicationContext
 val contract=JourneyContract(context.assets.open("journey-v1.schema.json").bufferedReader().use {JSONObject(it.readText())})
 private fun activePointer(searchId:String,trip:JSONObject)=obj("search_id" to searchId,"journey_id" to trip.getString("journey_id"),"status" to trip.getString("status"),"distance_m" to trip.getJSONObject("tracking").opt("distance_m"))
 fun boot():Int?=runCatching {Settings.Global.getInt(context.contentResolver,Settings.Global.BOOT_COUNT)}.getOrNull()
 fun active():Pair<JSONObject,JSONObject>? {
  val pointer=store.json(ACTIVE_TRIP)?:return null
  val record=store.record(pointer.optString("search_id"))?:return null
  val trip=record.objects("journeys").find {it.optString("journey_id")==pointer.optString("journey_id")&&it.optString("status") in liveTripStates}?:return null
  return record to trip
 }
 fun start(searchId:String,index:Int,tracked:Boolean):JSONObject {
  val db=store.writableDatabase;db.beginTransaction()
  return try {
   require(active()==null) { "Finish or cancel the active journey first" }
   val record=store.record(searchId)?:error("Journey not found")
   if(tracked){val choice=record.getJSONArray("choices").getJSONObject(index);require(cards(record.optJSONObject("result")).find {it.id=="${choice.optInt("routing_rank")}-${choice.optString("mode_key")}"}?.expired()!=true) { "Departure has passed; search again" }}
   require(record.objects("journeys").none {it.optInt("choice_index")==index}) { "An experience already exists for this choice" }
   val trip=TrackingEngine.create(record,index,tracked,now(),SystemClock.elapsedRealtime(),boot())
   contract.validate(record,trip)
   val journeys=record.optJSONArray("journeys")?:JSONArray();journeys.put(trip);record.put("journeys",journeys);store.saveRecord(record)
   if(tracked)store.put(ACTIVE_TRIP,activePointer(searchId,trip))
   db.setTransactionSuccessful();trip
  }finally{db.endTransaction()}
  .also {TripUpdates.changed()}
 }
 fun change(searchId:String,tripId:String,block:(JSONObject)->Unit):JSONObject {
  val db=store.writableDatabase;db.beginTransaction()
  return try {
   val record=store.record(searchId)?:error("Journey not found");val trip=record.objects("journeys").single {it.getString("journey_id")==tripId}
   block(trip);contract.validate(record,trip);store.saveRecord(record)
   if(store.json(ACTIVE_TRIP)?.optString("journey_id")==tripId){if(trip.optString("status") !in liveTripStates)store.remove(ACTIVE_TRIP)else store.put(ACTIVE_TRIP,activePointer(searchId,trip))}
   db.setTransactionSuccessful();trip
  }finally{db.endTransaction()}
  .also {TripUpdates.changed()}
 }
 fun changeActive(block:(JSONObject)->Unit):JSONObject? {
  val db=store.writableDatabase;db.beginTransaction()
  return try {
   val pair=active()
   if(pair==null){db.setTransactionSuccessful();null}else{
    val (record,trip)=pair;block(trip);contract.validate(record,trip);store.saveRecord(record)
    if(trip.optString("status") !in liveTripStates)store.remove(ACTIVE_TRIP)else store.put(ACTIVE_TRIP,activePointer(record.getString("search_id"),trip))
    db.setTransactionSuccessful();trip
   }
  }finally{db.endTransaction()}
  .also {if(it!=null)TripUpdates.changed()}
 }
 fun recover() {
  if(TrackingService.running)return
  val pair=active()
  if(pair==null){if(store.json(ACTIVE_TRIP)!=null)store.remove(ACTIVE_TRIP);return}
  if(pair.second.optString("status")!="recording"){
   if(pair.second.optJSONObject("context_capture")?.objects("samples")?.any {it.optString("status")=="pending"}==true)changeActive {JourneyContextCapture.interruptPending(it)}
   return
  }
  changeActive {TrackingEngine.pause(it,now(),true)}
 }
 fun confirmExperience(searchId:String,journeyId:String,completion:String,actual:List<String>,ratings:JSONObject,changes:String,reason:String?,at:String,satisfaction:JSONObject,valences:JSONObject,comfortObservation:JSONObject?=null):JSONObject {
  val db=store.writableDatabase;db.beginTransaction()
  return try {
   val record=store.record(searchId)?:error("Journey not found")
   val trip=record.objects("journeys").single {it.getString("journey_id")==journeyId}
   if(trip.optString("status")=="confirmed"){
    val candidate=trip.copyJson().put("status","awaiting_confirmation")
    TrackingEngine.confirm(candidate,completion,actual,ratings,changes,reason,trip.getJSONObject("experience").getString("confirmed_at"),satisfaction,valences,comfortObservation)
    if(!trip.getJSONObject("experience").has("mode_valences_normalized_minus1_to_1"))candidate.getJSONObject("experience").remove("mode_valences_normalized_minus1_to_1")
    if(!trip.getJSONObject("experience").has("affect_scale_version"))candidate.getJSONObject("experience").remove("affect_scale_version")
    require(canonical(candidate.getJSONObject("experience"))==canonical(trip.getJSONObject("experience"))) {"This experience was already saved with different answers"}
   }else{
    TrackingEngine.confirm(trip,completion,actual,ratings,changes,reason,at,satisfaction,valences,comfortObservation)
    contract.validate(record,trip)
    store.passport()?.let {current->
     val next=passportWithExperience(current,record,trip)
     require(validPassport(next));store.archive(current);store.archive(next);store.put(PASSPORT,next)
     val transitions=record.optJSONArray("passport_transitions")?:JSONArray()
     transitions.put(passportTransition(current,next,trip));record.put("passport_transitions",transitions)
     record.put("last_evidence_passport",next.getJSONObject("response").getJSONObject("cognitive_passport").getJSONObject("lineage").copyJson())
    }
    appendProspectiveEvaluation(record,trip)
    store.saveRecord(record)
   }
   queueLearnedPassport(store,record,trip)
   db.setTransactionSuccessful();trip
  }finally{db.endTransaction()}
  .also {TripUpdates.changed();LearnedPassportLearning.resume(context,store.databaseName)}
 }
 fun validateAll() {store.history().forEach {record->record.objects("journeys").forEach {contract.validate(record,it)}}}
}
