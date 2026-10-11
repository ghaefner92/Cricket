package de.ovgu.imiq.cricket

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

/** Pure state transitions, independent of Android sensors and UI. */
object TrackingEngine {
 fun create(record:JSONObject,index:Int,tracked:Boolean,at:String,elapsedMs:Long,boot:Int?):JSONObject {
  require(record.optString("status")=="complete")
  val choice=record.getJSONArray("choices").getJSONObject(index)
  val route=record.getJSONObject("result").getJSONObject("routing").objects("routes").single {it.optInt("rank")==choice.getInt("routing_rank")&&it.optString("mode_key")==choice.getString("mode_key")&&it.optBoolean("available")}
  val card=cards(record.optJSONObject("result")).find {it.id=="${choice.getInt("routing_rank")}-${choice.getString("mode_key")}"}
  val journey=obj("schema_version" to JOURNEY_SCHEMA,"journey_id" to "trip-${UUID.randomUUID()}","search_id" to record.getString("search_id"),"choice_index" to index,"choice_event_id" to choiceEventId(record,index),"created_at" to at,"started_at" to if(tracked)at else null,"ended_at" to if(tracked)null else at,"status" to if(tracked)"recording" else "awaiting_confirmation","capture_mode" to if(tracked)"tracked"else"manual",
   "selection" to obj(*listOf("chosen_at","mode_key","route_id","routing_rank","provider","provider_itinerary_id").map {it to choice.opt(it)}.toTypedArray()),"passport_reference" to record.getJSONObject("passport_reference").copyJson(),
   "plan" to obj("route" to route.copyJson(),"recommendation" to record.optJSONObject("recommendation")?.copyJson(),"alternatives" to JSONArray(record.getJSONObject("result").getJSONObject("routing").objects("routes").map {obj("routing_rank" to it.getInt("rank"),"mode_key" to it.getString("mode_key"),"available" to it.optBoolean("available"))}),"context_at_selection" to obj("queried_at" to record.getJSONObject("request").getString("datetime"),"snapshot" to card?.candidate?.copyJson(),"temporal_reference" to "search_query")),
   "tracking" to obj("points" to JSONArray(),"events" to JSONArray(),"rejected_fixes" to 0,"distance_m" to null,"elapsed_seconds" to null,"gap_count" to 0,"segment_index" to 0,"approximate" to true,"started_monotonic_ms" to if(tracked)elapsedMs else null,"boot_count" to if(tracked)boot else null),"experience" to null)
  if(tracked)event(journey,"start",at,"")
  return journey
 }
 fun event(journey:JSONObject,kind:String,at:String,detail:String) {
  val events=journey.getJSONObject("tracking").getJSONArray("events")
  require(events.length()<2000) { "Event limit reached; finish this journey" }
  events.put(obj("at" to at,"kind" to kind,"detail" to detail.take(300)))
 }
 fun breakSegment(journey:JSONObject,kind:String,at:String,detail:String="") {
  val t=journey.getJSONObject("tracking");t.put("segment_index",t.getInt("segment_index")+1).put("gap_count",t.getInt("gap_count")+1);event(journey,kind,at,detail)
 }
 fun pause(journey:JSONObject,at:String,interrupted:Boolean=false) {
  if(interrupted)JourneyContextCapture.interruptPending(journey)
  require(journey.getString("status") in liveTripStates)
  if(journey.getString("status")=="recording") {journey.put("status","paused");breakSegment(journey,if(interrupted)"interrupted"else"pause",at)}
 }
 fun resume(journey:JSONObject,at:String) {require(journey.getString("status")=="paused");journey.put("status","recording");event(journey,"resume",at,"")}
 fun fix(journey:JSONObject,point:JSONObject):Boolean {
  require(journey.getString("status")=="recording")
  val t=journey.getJSONObject("tracking");val points=t.getJSONArray("points");val old=points.optJSONObject(points.length()-1)
  val received=Instant.parse(point.getString("received_at"));val stamp=Instant.parse(point.getString("recorded_at"));val start=Instant.parse(journey.getString("started_at"))
  val lat=point.optDouble("lat");val lon=point.optDouble("lon");val accuracy=point.optDouble("accuracy_m")
  fun reject():Boolean {t.put("rejected_fixes",t.getInt("rejected_fixes")+1);return false}
  if(!lat.isFinite()||!lon.isFinite()||kotlin.math.abs(lat)>90||kotlin.math.abs(lon)>180||!accuracy.isFinite()||accuracy !in 0.0..50.0||point.optBoolean("mock")||stamp<start||received<stamp||java.time.Duration.between(stamp,received).seconds>30)return reject()
  require(points.length()<20000) { "Point limit reached; finish this journey" }
  var step=0.0
  if(old!=null&&old.getInt("segment_index")==t.getInt("segment_index")){
   val seconds=(point.getLong("monotonic_ms")-old.getLong("monotonic_ms"))/1000.0
   if(seconds<=0)return reject()
   if(seconds>120)breakSegment(journey,"gap",point.getString("received_at"),"More than 120 seconds between retained positions")
   else {
    step=distanceMeters(old,point)
    if(step/seconds>70)return reject()
    // Small movements below the uncertainty floor do not accumulate stationary jitter.
    if(step<maxOf(5.0,minOf(old.getDouble("accuracy_m"),accuracy)*0.5)){
     // Retain an accurate stationary fix once per sampling interval so waiting
     // periods can collect ambient context without accumulating GPS jitter.
     if(seconds<JourneyContextCapture.INTERVAL_SECONDS)return false
     step=0.0
    }
   }
  }
  point.put("segment_index",t.getInt("segment_index"));points.put(point)
  if(points.length()>=2)t.put("distance_m",(t.number("distance_m")?:0.0)+step)
  return true
 }
 fun finish(journey:JSONObject,at:String,elapsedMs:Long,boot:Int?,cancelled:Boolean=false) {
  JourneyContextCapture.interruptPending(journey)
  require(journey.getString("status") in liveTripStates)
  val t=journey.getJSONObject("tracking")
  val start=t.getLong("started_monotonic_ms")
  val seconds=if(!t.isNull("boot_count")&&t.optInt("boot_count")==boot&&elapsedMs>=start)(elapsedMs-start)/1000.0 else java.time.Duration.between(Instant.parse(journey.getString("started_at")),Instant.parse(at)).toMillis()/1000.0
  t.put("elapsed_seconds",seconds.coerceAtLeast(0.0));journey.put("ended_at",at).put("status",if(cancelled)"cancelled"else"awaiting_confirmation");event(journey,if(cancelled)"cancel"else"finish",at,"")
 }
 fun confirm(journey:JSONObject,completion:String,actual:List<String>,ratings:JSONObject,changes:String,reason:String?,at:String,satisfaction:JSONObject=JSONObject(),valences:JSONObject=JSONObject(),comfortObservation:JSONObject?=null) {
  require(journey.getString("status")=="awaiting_confirmation")
  journey.put("experience",obj("schema_version" to "cricket-experience-v1","event_id" to "experience-${journey.getString("journey_id")}","confirmed_at" to at,"completion" to completion,"actual_modes" to JSONArray(actual),"changes" to changes.trim(),"ratings" to ratings,"choice_reason" to reason?.trim()?.takeIf {it.isNotEmpty()},"learning_applied" to false)).put("status","confirmed")
  if(satisfaction.length()>0)journey.getJSONObject("experience").put("need_satisfaction_raw_1_to_7",satisfaction)
  if(comfortObservation!=null)journey.getJSONObject("experience").put("comfort_observation",comfortObservation.copyJson())
  if(valences.length()>0)journey.getJSONObject("experience").put("mode_valences_raw_minus3_to_3",valences.copyJson()).put("mode_valences_normalized_minus1_to_1",normalizedExperienceValences(valences)).put("affect_scale_version","mode-affect-ordinal-minus3-to3-v1")
 }
}
internal fun normalizedExperienceValences(raw:JSONObject):JSONObject {
 require(raw.keys().asSequence().all {it in modes&&raw.opt(it) is Int&&raw.getInt(it) in -3..3}) {"Expected ordinal mode valences from -3 to +3"}
 return obj(*raw.keys().asSequence().map {it to raw.getInt(it)/3.0}.toList().toTypedArray())
}
