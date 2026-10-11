package de.ovgu.imiq.cricket

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.Duration
import java.util.UUID

/** Ambient readings linked to retained fixes; never fills gaps or assigns actual mode. */
object JourneyContextCapture {
 const val INTERVAL_SECONDS=60L
 const val LIMIT=240
 fun begin(trip:JSONObject,point:JSONObject):JSONObject? {
  if(trip.optString("status")!="recording"||trip.optString("capture_mode")!="tracked")return null
  val capture=trip.optJSONObject("context_capture")?:obj("schema_version" to "d-day-trip-context-v1","sampling_interval_seconds" to INTERVAL_SECONDS,"sample_limit" to LIMIT,"samples" to JSONArray()).also {trip.put("context_capture",it)}
  val samples=capture.getJSONArray("samples")
  if(samples.length()>=LIMIT||samples.objects().any {it.optString("status")=="pending"})return null
  val retained=trip.getJSONObject("tracking").objects("points").lastOrNull()?:return null
  if(retained.getLong("monotonic_ms")!=point.getLong("monotonic_ms"))return null
  val last=samples.optJSONObject(samples.length()-1)
  if(last!=null&&last.getInt("segment_index")==point.getInt("segment_index")&&Duration.between(Instant.parse(last.getString("requested_at")),Instant.parse(point.getString("received_at"))).seconds<INTERVAL_SECONDS)return null
  val sample=obj("sample_id" to "context-${UUID.randomUUID()}","point_monotonic_ms" to point.getLong("monotonic_ms"),"segment_index" to point.getInt("segment_index"),"requested_at" to point.getString("received_at"),"received_at" to null,"status" to "pending","result" to null,"failure_reason" to null)
  samples.put(sample)
  return sample.copyJson()
 }
 fun complete(trip:JSONObject,id:String,result:JSONObject?,at:String,failure:String?=null):Boolean {
  val sample=trip.optJSONObject("context_capture")?.objects("samples")?.find {it.optString("sample_id")==id}?:return false
  if(sample.optString("status")!="pending"||trip.optString("status") !in liveTripStates)return false
  val received=Instant.parse(at)
  val valid=result!=null&&runCatching {
   val point=trip.getJSONObject("tracking").objects("points").single {it.getLong("monotonic_ms")==sample.getLong("point_monotonic_ms")}
   val location=result.getJSONObject("query_location")
   result.getString("schema_version")=="d-day-context-sample-v1"&&result.opt("stored_on_server")==false&&result.opt("personal_exposure_measured")==false&&
    Instant.parse(result.getString("queried_at"))<=received&&received>=Instant.parse(sample.getString("requested_at"))&&
    location.getDouble("lat")==point.getDouble("lat")&&location.getDouble("lon")==point.getDouble("lon")&&
    result.getJSONObject("context").getString("queried_at")==result.getString("queried_at")&&
    result.getJSONObject("audit").getString("schema_version")==
     (if(result.getJSONObject("context").optString("weather_model_version")=="d-day-weather-intensity-v2")"d-day-context-audit-v2"else "d-day-context-audit-v1")&&result.optJSONArray("raw_observations")!=null
  }.getOrDefault(false)
  sample.put("received_at",at).put("status",if(valid)"complete"else"failed").put("result",if(valid)result.copyJson() else JSONObject.NULL).put("failure_reason",if(valid)JSONObject.NULL else (failure?:"invalid_response_or_clock_skew"))
  return true
 }
 fun interruptPending(trip:JSONObject) {
  trip.optJSONObject("context_capture")?.objects("samples")?.filter {it.optString("status")=="pending"}?.forEach {it.put("status","interrupted").put("failure_reason","capture_stopped_before_response")}
 }
 fun validate(trip:JSONObject) {
  val capture=trip.optJSONObject("context_capture")?:return
  require(trip.getString("capture_mode")=="tracked") {"Manual trip cannot contain ambient capture"}
  val points=trip.getJSONObject("tracking").objects("points").associateBy {it.getLong("monotonic_ms")}
  var previous:Instant?=null
  val ids=mutableSetOf<String>()
  capture.objects("samples").forEach {s->
   require(ids.add(s.getString("sample_id"))) {"Duplicate context sample"}
   val point=points[s.getLong("point_monotonic_ms")]?:error("Context sample without retained fix")
   val at=Instant.parse(s.getString("requested_at"))
   require(s.getInt("segment_index")==point.getInt("segment_index")&&at==Instant.parse(point.getString("received_at"))&&(previous==null||at>previous)) {"Invalid context sample chronology"}
   previous=at
   val endedAt=instant(trip.opt("ended_at"));require(endedAt==null||at<=endedAt)
   var recording=true
   trip.getJSONObject("tracking").objects("events").takeWhile {Instant.parse(it.getString("at"))<=at}.forEach {event->
    when(event.getString("kind")){"pause","interrupted"->recording=false;"resume"->recording=true}
   }
   require(recording) {"Context requested while paused"}
   if(s.getString("status")=="complete"){
    val received=Instant.parse(s.getString("received_at"));val ended=instant(trip.opt("ended_at"))
    require(received>=at&&(ended==null||received<=ended)) {"Context response outside journey"}
    val result=s.getJSONObject("result")
    val location=result.getJSONObject("query_location")
    require(location.getDouble("lat")==point.getDouble("lat")&&location.getDouble("lon")==point.getDouble("lon"))
    require(result.getString("schema_version")=="d-day-context-sample-v1"&&result.opt("stored_on_server")==false&&result.opt("personal_exposure_measured")==false&&Instant.parse(result.getString("queried_at"))<=received)
    require(result.getJSONObject("context").getString("queried_at")==result.getString("queried_at"))
   }else require(s.isNull("result"))
   if(!trip.isNull("ended_at"))require(s.getString("status")!="pending")
  }
 }
}
