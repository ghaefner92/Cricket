package de.ovgu.imiq.cricket

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant
import kotlin.math.*

const val JOURNEY_SCHEMA="cricket-journey-v1"
const val ACTIVE_TRIP="cricket.active-trip.v1"
val liveTripStates=setOf("recording","paused")
fun choiceEventId(record:JSONObject,index:Int):String {
 val choice=record.getJSONArray("choices").getJSONObject(index)
 return choice.optString("choice_event_id").takeIf { it.isNotEmpty() }?:"choice-"+MessageDigest.getInstance("SHA-256").digest("${record.getString("search_id")}|$index|${choice.getString("chosen_at")}".toByteArray()).joinToString("") { "%02x".format(it) }
}
fun jsonNull(value:Any?)=value==null||value===JSONObject.NULL
fun instant(value:Any?):Instant?=if(jsonNull(value))null else Instant.parse(value as String)
fun distanceMeters(a:JSONObject,b:JSONObject):Double {
 val lat1=Math.toRadians(a.getDouble("lat"));val lat2=Math.toRadians(b.getDouble("lat"));val delta=Math.toRadians(b.getDouble("lon")-a.getDouble("lon"))
 val h=sin((lat2-lat1)/2).pow(2)+cos(lat1)*cos(lat2)*sin(delta/2).pow(2)
 return 6371008.8*2*asin(sqrt(h.coerceIn(0.0,1.0)))
}

/** Small strict interpreter for the keywords used by the shared v1 schema. */
class JourneyContract(private val root:JSONObject) {
 fun validate(record:JSONObject,journey:JSONObject) {
  require(validRecord(record)&&record.optString("status")=="complete") { "Expected completed search" }
  schema(root,journey,"journey")
  JourneyContextCapture.validate(journey)
  require(journey.getString("search_id")==record.getString("search_id")) { "Journey/search mismatch" }
  val index=journey.getInt("choice_index");val choice=record.getJSONArray("choices").getJSONObject(index)
  require(journey.getString("choice_event_id")==choiceEventId(record,index)) { "Journey/choice mismatch" }
  val selection=journey.getJSONObject("selection")
  listOf("chosen_at","mode_key","route_id","routing_rank","provider","provider_itinerary_id").forEach { key->require(canonical(selection.opt(key))==canonical(choice.opt(key))) { "Selection mismatch: $key" } }
  require(canonical(journey.getJSONObject("passport_reference"))==canonical(record.getJSONObject("passport_reference"))) { "Passport mismatch" }
  val route=record.getJSONObject("result").getJSONObject("routing").objects("routes").single { it.optInt("rank")==selection.getInt("routing_rank")&&it.optString("mode_key")==selection.getString("mode_key") }
  val plan=journey.getJSONObject("plan")
  require(canonical(plan.getJSONObject("route"))==canonical(route)&&route.optBoolean("available")) { "Itinerary mismatch" }
  require(canonical(plan.opt("recommendation"))==canonical(record.opt("recommendation"))) { "Recommendation mismatch" }
  val alternatives=record.getJSONObject("result").getJSONObject("routing").objects("routes").map { obj("routing_rank" to it.getInt("rank"),"mode_key" to it.getString("mode_key"),"available" to it.optBoolean("available")) }
  require(canonical(plan.getJSONArray("alternatives"))==canonical(JSONArray(alternatives))) { "Alternatives mismatch" }
  val context=plan.getJSONObject("context_at_selection")
  require(context.getString("queried_at")==record.getJSONObject("request").getString("datetime")) { "Context time mismatch" }
  val expected=cards(record.optJSONObject("result")).find {it.id=="${selection.getInt("routing_rank")}-${selection.getString("mode_key")}"}?.candidate
  require(canonical(context.opt("snapshot"))==canonical(expected)) { "Context snapshot mismatch" }
  val created=Instant.parse(journey.getString("created_at"));val chosen=Instant.parse(selection.getString("chosen_at"));require(created>=chosen)
  Instant.parse(context.getString("queried_at"))
  val start=instant(journey.opt("started_at"));val end=instant(journey.opt("ended_at"));if(start!=null)require(start>=created);if(end!=null)require(end>=created&&(start==null||end>=start))
  val status=journey.getString("status");val manual=journey.getString("capture_mode")=="manual";val tracking=journey.getJSONObject("tracking")
  if(status in liveTripStates)require(!manual&&start!=null&&end==null)
  if(status !in liveTripStates)require(end!=null)
  val points=tracking.objects("points")
  require(tracking.getInt("gap_count")==tracking.getInt("segment_index")) { "Gap/segment mismatch" }
  if(manual)require(start==null&&points.isEmpty()&&tracking.isNull("distance_m")&&tracking.isNull("elapsed_seconds")&&tracking.getJSONArray("events").length()==0&&tracking.isNull("started_monotonic_ms")&&tracking.isNull("boot_count")&&tracking.getInt("gap_count")==0&&tracking.getInt("rejected_fixes")==0)
  else require(start!=null&&tracking.number("started_monotonic_ms")!=null)
  var distance=0.0;var previous:JSONObject?=null
  points.forEach { point->
   val stamp=Instant.parse(point.getString("recorded_at"));val received=Instant.parse(point.getString("received_at"))
   require(start!=null&&stamp>=start&&received>=stamp&&java.time.Duration.between(stamp,received).seconds<=30&&(end==null||received<=end)) { "Invalid fix time" }
   require(!point.getBoolean("mock")) { "Mock fixes cannot be experience evidence" }
   require(point.getInt("segment_index")<=tracking.getInt("segment_index"))
   previous?.let { old->
    require(point.getInt("segment_index")>=old.getInt("segment_index"))
    if(point.getInt("segment_index")==old.getInt("segment_index")){
     val seconds=(point.getLong("monotonic_ms")-old.getLong("monotonic_ms"))/1000.0
     require(seconds>0&&seconds<=120) { "Invalid fix interval" };val step=distanceMeters(old,point);require(step/seconds<=70) { "Implausible fix speed" };distance+=step
    }
   };previous=point
  }
  if(points.size<2)require(tracking.isNull("distance_m"))else require(abs((tracking.number("distance_m")?:error("Missing observed distance"))-distance)<=0.01)
  var lastEvent=created
  tracking.objects("events").forEach { val at=Instant.parse(it.getString("at"));require(at>=lastEvent&&(end==null||at<=end));lastEvent=at }
  if(!manual)require(tracking.objects("events").firstOrNull()?.optString("kind")=="start")
  if(!manual&&end!=null)require(tracking.number("elapsed_seconds")!=null) else require(tracking.isNull("elapsed_seconds"))
  val experience=journey.optJSONObject("experience")
  require((status=="confirmed")== (experience!=null)) { "Confirmation state mismatch" }
  if(experience!=null){
   require(experience.getString("event_id")=="experience-${journey.getString("journey_id")}") { "Experience identity mismatch" }
   require(Instant.parse(experience.getString("confirmed_at"))>=end!!)
   val actual=experience.getJSONArray("actual_modes");val completion=experience.getString("completion")
   val satisfaction=experience.optJSONObject("need_satisfaction_raw_1_to_7")?:JSONObject()
   val emotions=experience.optJSONObject("mode_valences_raw_minus3_to_3")?:JSONObject()
   require(emotions.keys().asSequence().all {key->(0 until actual.length()).any {actual.getString(it)==key}}) {"Emotion must refer to an actual mode"}
   val normalized=experience.optJSONObject("mode_valences_normalized_minus1_to_1")
   if(record.optBoolean("affect_pilot_required")&&completion!="not_taken")require(normalized!=null&&emotions.keys().asSequence().toSet()==(0 until actual.length()).map {actual.getString(it)}.toSet()&&experience.optString("affect_scale_version")=="mode-affect-ordinal-minus3-to3-v1") {"Affective experience is required for each actual mode"}
   normalized?.let {values->
    require(values.keys().asSequence().toSet()==emotions.keys().asSequence().toSet()) {"Valence normalization keys mismatch"}
    require(values.keys().asSequence().all {abs(values.getDouble(it)-emotions.getInt(it)/3.0)<1e-9}) {"Valence normalization must be raw/3"}
   }
   if(record.optBoolean("comfort_pilot_required")&&completion!="not_taken")require(experience.optJSONObject("comfort_observation")!=null) {"Physical comfort is required in this pilot"}
   experience.optJSONObject("comfort_observation")?.let {o->
    require(completion!="not_taken"&&(0 until actual.length()).any {actual.getString(it)==o.getString("mode")}) {"Comfort must refer to an actual mode"}
    val answered=Instant.parse(o.getString("answered_at"));require(answered>=end&&answered<=Instant.parse(experience.getString("confirmed_at"))) {"Comfort answer outside experience time"}
    require((o.getString("mode_selection")=="single_actual_mode")== (actual.length()==1)) {"Incorrect mode attribution method"}
   }
   if(completion=="not_taken")require(satisfaction.length()==0&&emotions.length()==0)
   if(completion=="not_taken")require(actual.length()==0&&experience.getJSONObject("ratings").length()==0&&manual)
   else require(actual.length()>0)
   if(completion=="as_planned")require((0 until actual.length()).map {actual.getString(it)}.toSet()==setOf(modelMode(selection.getString("mode_key"))))
  }
 }
 private fun schema(def:JSONObject,value:Any?,path:String) {
  if(def.has("\$ref")){schema(root.getJSONObject("\$defs").getJSONObject(def.getString("\$ref").substringAfterLast('/')),value,path);return}
  if(def.has("type")){
   val type=def.get("type");val types=if(type is JSONArray)(0 until type.length()).map {type.getString(it)}else listOf(type as String)
   require(types.any { when(it){"null"->jsonNull(value);"object"->value is JSONObject;"array"->value is JSONArray;"string"->value is String;"boolean"->value is Boolean;"number"->value is Number&&value.toDouble().isFinite();"integer"->value is Number&&value.toDouble().isFinite()&&value.toDouble()%1.0==0.0;else->false} }) { "$path: invalid type" }
  }
  if(def.has("const"))require(canonical(value)==canonical(def.get("const"))) { "$path: invalid constant" }
  if(def.has("enum"))require((0 until def.getJSONArray("enum").length()).any {canonical(value)==canonical(def.getJSONArray("enum").get(it))}) { "$path: invalid option" }
  when(value){
   is JSONObject->{
    val properties=def.optJSONObject("properties")?:JSONObject()
    def.optJSONArray("required")?.let {required->(0 until required.length()).forEach {require(value.has(required.getString(it))) { "$path: missing ${required.getString(it)}" } }}
    value.keys().forEach {key->if(properties.has(key))schema(properties.getJSONObject(key),value.get(key),"$path.$key")else require(def.opt("additionalProperties")!=false) { "$path: unknown $key" } }
   }
   is JSONArray->{require(value.length()<=def.optInt("maxItems",Int.MAX_VALUE));if(def.optBoolean("uniqueItems"))require((0 until value.length()).map {canonical(value.get(it))}.distinct().size==value.length());def.optJSONObject("items")?.let {items->(0 until value.length()).forEach {schema(items,value.get(it),"$path[$it]")} } }
   is String->require(value.codePointCount(0,value.length) in def.optInt("minLength",0)..def.optInt("maxLength",Int.MAX_VALUE))
   is Number->require(value.toDouble()>=def.optDouble("minimum",Double.NEGATIVE_INFINITY)&&value.toDouble()<=def.optDouble("maximum",Double.POSITIVE_INFINITY))
  }
 }
}
