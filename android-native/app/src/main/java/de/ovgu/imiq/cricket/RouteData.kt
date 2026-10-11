package de.ovgu.imiq.cricket

import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class RouteCard(val route:JSONObject,val audit:JSONObject?,val candidate:JSONObject?,val basicSimulation:JSONObject?=null) {
 val id get()="${route.optInt("rank")}-${route.optString("mode_key")}"
 val mode get()=modelMode(route.optString("mode_key"))
 fun expired(time:Long=System.currentTimeMillis()):Boolean {val transit=route.optJSONObject("transit");return transit?.number("start_time_ms")?.let { it<time-30000 }==true&&route.objects("legs").any { it.optString("mode")=="pt" }}
}
fun cards(result:JSONObject?):List<RouteCard> {
 if(result==null)return emptyList()
 return result.optJSONObject("routing")?.objects("routes")?.map { route->
  val audit=result.objects("route_audit").find { it.optInt("routing_rank")==route.optInt("rank")&&it.optString("mode_key")==route.optString("mode_key") }
  val candidate=result.optJSONObject("contextual_deliberation")?.objects("candidate_results")?.find { it.optString("route_id")==audit?.optString("route_id") }
  val basic=if(candidate==null&&route.optBoolean("available"))result.optJSONObject("contextual_deliberation")?.optJSONObject("baseline")else null
  RouteCard(route,audit,candidate,basic)
 }?:emptyList()
}
fun boarding(card:RouteCard)=card.route.objects("legs").firstOrNull { it.optString("mode")=="pt" }?.optJSONObject("transit")?.number("start_time_ms")
/** Presentation countdown; the immutable OTP itinerary duration is not rewritten. */
data class TransitTiming(val beforeDepartureSeconds:Double,val fromNowSeconds:Double,val arrivalMs:Double)
fun transitTiming(card:RouteCard,time:Long=System.currentTimeMillis()):TransitTiming? {
 if(card.mode!="pt"||card.expired(time))return null
 val transit=card.route.optJSONObject("transit")?:return null
 val start=transit.number("start_time_ms")?:return null
 val end=transit.number("end_time_ms")?:return null
 if(!start.isFinite()||!end.isFinite()||start<=0||end<start||end<=time)return null
 return TransitTiming(((start-time)/1000).coerceAtLeast(0.0),(end-time)/1000,end)
}
fun countdownMinutes(seconds:Double)=kotlin.math.ceil(seconds.coerceAtLeast(0.0)/60).toInt()
fun carWalkingSeconds(card:RouteCard):Double? = if(card.mode!="car")null else card.route.objects("legs").filter {modelMode(it.optString("mode"))=="walk"}.takeIf {it.isNotEmpty()}?.let {legs->
 val durations=legs.map {it.number("duration_seconds")?.takeIf {value->value>0}?:return null}
 durations.sum()
}
fun routeDurationLabel(card:RouteCard,time:Long,de:Boolean):String {
 val timing=transitTiming(card,time)
 if(timing!=null)return "${countdownMinutes(timing.fromNowSeconds)} min "+if(de)"ab jetzt"else"from now"
 val duration=metric(card.route.optJSONObject("summary")?.number("duration_seconds")?.div(60),0)
 if(originAccessGap(card)!=null)return "$duration min "+if(de)"kartierter Weg"else"mapped route"
 if(card.route.optJSONObject("summary")?.optBoolean("destination_access_time_unknown")==true)return "$duration min "+if(de)"bis Zugang"else"to access"
 return "$duration min"+if(card.mode=="pt")if(de)" Reisezeit"else" journey"else""
}
fun destinationAccessGap(card:RouteCard):Double? = card.route.optJSONObject("summary")?.takeIf {it.optBoolean("destination_access_time_unknown")}?.number("destination_access_offset_m")?.takeIf {it>0}
fun originAccessGap(card:RouteCard):Double? = card.route.optJSONObject("summary")?.takeIf {it.optBoolean("origin_access_time_unknown")}?.number("origin_access_offset_m")?.takeIf {it>0}
fun transitDepartureLabel(card:RouteCard,time:Long,de:Boolean):String {
 val timing=transitTiming(card,time)?:return (if(de)"Start "else"Leave ")+clock(card.route.optJSONObject("transit")?.number("start_time_ms"))
 val leave=if(timing.beforeDepartureSeconds<=0)if(de)"Jetzt starten"else"Leave now"else
  if(de)"Start in ${countdownMinutes(timing.beforeDepartureSeconds)} min"else"Leave in ${countdownMinutes(timing.beforeDepartureSeconds)} min"
 return leave+" · "+(if(de)"Ankunft "else"Arrive ")+clock(timing.arrivalMs)
}
/** Walking access to the first vehicle, excluding transfer and destination walks. */
fun boardingAccess(card:RouteCard):Pair<Double,Double>? {
 val legs=card.route.objects("legs");val index=legs.indexOfFirst {it.optString("mode")=="pt"}
 if(index<0)return null
 var metres=0.0;var seconds=0.0
 for(leg in legs.take(index)) {
  if(leg.optString("mode")!="walk")return null
  val m=leg.number("distance_meters")?:return null;val s=leg.number("duration_seconds")?:return null
  if(!m.isFinite()||!s.isFinite()||m<0||s<0)return null
  metres+=m;seconds+=s
 }
 // An omitted access leg is unknown when the provider explicitly reports an access gap.
 if(originAccessGap(card)!=null)return null
 return metres to seconds
}
fun sortedCards(values:List<RouteCard>,time:Long=System.currentTimeMillis())=values.sortedWith(compareBy<RouteCard> { it.expired(time) }.thenBy { !it.route.optBoolean("available") }.thenBy { it.route.optJSONObject("transit")?.number("end_time_ms")?:Double.MAX_VALUE }.thenBy { boardingAccess(it)?.first?:Double.MAX_VALUE }.thenBy { boarding(it)?:Double.MAX_VALUE }.thenBy { it.route.optInt("rank") })
fun transitConnectionOptions(values:List<RouteCard>,time:Long)=sortedCards(values.filter {it.route.optBoolean("available")&&!it.expired(time)},time).distinctBy {it.route.optString("provider_itinerary_id").takeIf {id->id.isNotBlank()}?:it.id}.take(3)
fun departurePattern(card:RouteCard)=card.route.objects("legs").filter {it.optString("mode")=="pt"}.joinToString(" → ") { leg->val transit=leg.optJSONObject("transit");listOf(transit?.optString("line")?.ifEmpty {transit.optString("source_mode")}.orEmpty(),transit?.optJSONObject("from")?.optString("name").orEmpty(),transit?.optJSONObject("to")?.optString("name").orEmpty()).joinToString(" · ") }
data class Recommendation(val state:String,val winner:String?,val primary:RouteCard?) {
 fun json()=obj("state" to state,"winner" to winner,"primary_route_id" to primary?.audit?.opt("route_id"))
}
fun recommendation(result:JSONObject):Recommendation {
 val available=cards(result).filter { it.route.optBoolean("available") }
 val runs=available.map { it.candidate?.optJSONObject("hotco") };val winners=runs.map { it?.optString("winner") }
 val clear=runs.isNotEmpty()&&runs.all { it?.optString("ambiguity_state")=="CLEAR"&&it.optJSONObject("final_action_activations")?.number(it.optString("winner"))!=null }
 val agreed=winners.firstOrNull()?.takeIf { it.isNotBlank()&&winners.all { w->w==it } }
 val matching=available.filter { it.mode==agreed }
 val primary=if(winners.firstOrNull()=="pt")sortedCards(matching).firstOrNull()else matching.firstOrNull()
 val state=when {
  available.isEmpty()->"incomplete"
  available.map { it.mode }.distinct().size==1->"only"
  available.any { it.candidate==null }->"incomplete"
  runs.any { it?.optString("ambiguity_state") in listOf("UNSETTLED","UNKNOWN") }->"uncertain"
  runs.any { it?.optString("ambiguity_state")=="SENSITIVE" }->"sensitive"
  runs.any { it?.optString("ambiguity_state")=="NEAR_TIE" }->"similar"
  agreed==null||matching.isEmpty()->"disagreement"
  clear->"clear"
  else->"uncertain"
 }
 return Recommendation(state,agreed?.takeIf { matching.isNotEmpty() },primary?:available.firstOrNull())
}
fun recommendationHeading(r:Recommendation,de:Boolean):String = if(de)when(r.state) {
 "clear"->"✨ Ich habe einen Favoriten für dich"
 "only"->"🧭 Ich habe eine verfügbare Möglichkeit gefunden"
 "similar"->"⚖️ Ich sehe ähnlich passende Möglichkeiten"
 "sensitive"->"🌿 Mein Favorit kann sich noch ändern"
 "disagreement"->"🧭 Mein Favorit hängt vom Reiseverlauf ab"
 "incomplete"->"🧭 Mir fehlen noch Informationen"
 else->"🌿 Ich kann dir noch keinen stabilen Favoriten nennen"
}else when(r.state) {
 "clear"->"✨ I have a favourite for you"
 "only"->"🧭 I found one available option"
 "similar"->"⚖️ I see options that fit you similarly"
 "sensitive"->"🌿 My favourite could still change"
 "disagreement"->"🧭 My favourite depends on the journey"
 "incomplete"->"🧭 I still need more information"
 else->"🌿 I can't name a stable favourite yet"
}
@androidx.compose.runtime.Composable
fun recommendationMessage(r:Recommendation,de:Boolean):String {
 val mode=r.winner?.let {modeName(it,de)}
 return if(de)when(r.state) {
  "clear"->"Ich würde dir $mode empfehlen: Diese Möglichkeit passt am besten zu deinem Profil und bleibt in meinen abschließenden Vergleichen vorne. Die Entscheidung liegt bei dir."
  "only"->"Ich habe für diese Suche nur eine verfügbare Verkehrsart gefunden. Damit kann ich keine Alternativen vergleichen."
  "similar"->"Ich sehe am Ende nur einen kleinen Abstand oder anhaltend nahe Möglichkeiten. Lass uns Fahrzeit und Reisebedingungen vergleichen – was ist dir heute wichtig?"
  "sensitive"->"${if(mode!=null)"Ich tendiere zu $mode. " else "Ich sehe noch keinen verlässlichen Favoriten. "}Kleine Änderungen deiner angenommenen Überzeugungen oder der Berechnung machen meine Empfehlung weniger eindeutig."
  "disagreement"->"Ich komme je nach Reisebedingungen zu unterschiedlichen Favoriten. Lass uns die Möglichkeiten genauer vergleichen."
  "incomplete"->"Mir fehlen vollständige Reisebewertungen. Ich zeige dir die verfügbaren Wege, damit du sie vergleichen kannst."
  else->"${if(mode!=null)"Ich tendiere momentan zu $mode, aber " else ""}Meine abschließenden Vergleiche ergeben noch keine stabile Empfehlung. Lass uns die Wege gemeinsam ansehen."
 }else when(r.state) {
  "clear"->"I'd suggest $mode: it fits your profile best and stays ahead in my final comparisons. The choice is yours."
  "only"->"I found only one available travel mode for this search, so I can't compare alternatives."
  "similar"->"I see only a small final difference or options that stay close near the end. Let's compare travel time and journey conditions — what matters to you today?"
  "sensitive"->"${if(mode!=null)"I'm leaning towards $mode. " else "I don't have a reliable favourite yet. "}Small changes in your assumed beliefs or the calculation make my recommendation less clear."
  "disagreement"->"I favour different options under different journey conditions. Let's look at what each journey offers you."
  "incomplete"->"I'm missing complete journey assessments. I'll show you the available routes so you can compare them."
  else->"${if(mode!=null)"I'm leaning towards $mode, but " else ""}I haven't reached a stable recommendation in my final comparisons. Let's look at the routes together."
 }
}
fun clock(value:Double?):String=if(value==null)"—"else DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Europe/Berlin")).format(Instant.ofEpochMilli(value.toLong()))
fun metric(value:Double?,digits:Int=1):String=if(value==null)"—"else String.format(Locale.getDefault(),"%.${digits}f",value)
fun observations(card:RouteCard?):List<JSONObject> {
 val context=card?.candidate?.optJSONObject("route_context")?:return emptyList()
 val raw=if(context.has("raw_observations"))context.objects("raw_observations")else context.objects("segments").flatMap { it.objects("raw_observations") }
 return raw.distinctBy { listOf(it.optString("source_entity_id"),it.optString("variable"),it.opt("raw_value"),it.opt("numeric_value"),it.optString("timestamp"),it.optString("unit")) }
}
data class GeoPoint(val lat:Double,val lon:Double)
data class MapSegment(val points:List<GeoPoint>,val mode:String,val approximate:Boolean,val drawable:Boolean=true,val referenceNetwork:Boolean=false,val transitVehicle:String="")
data class MapRoute(val id:String,val mode:String,val segments:List<MapSegment>,val supplemental:Boolean)
fun tramAlignmentRequest(result:JSONObject?):org.json.JSONArray = org.json.JSONArray().apply {
 cards(result).forEach {card->card.route.objects("legs").forEachIndexed {index,leg->val t=leg.optJSONObject("transit")
  val from=t?.optJSONObject("from");val to=t?.optJSONObject("to");val geometry=leg.optJSONObject("geometry")
  if(t?.optString("source_mode")=="TRAM"&&t.optString("geometry_quality") !in listOf("OSM_REFERENCE_LINE","OSM_REFERENCE_NETWORK","GTFS_SHAPE")&&from!=null&&to!=null&&geometry!=null)put(obj("route_id" to card.id,"leg_index" to index,"line" to t.optString("line"),"from" to from,"to" to to,"geometry" to geometry))
 }}
}
fun mapRoutes(result:JSONObject?,tramAlignments:Map<String,JSONObject> = emptyMap()):List<MapRoute> = cards(result).filter { it.route.opt("available")!=false }.mapNotNull { card->
 val candidate=result?.objects("candidate_routes")?.find { it.optString("route_id")==card.audit?.optString("route_id") }
 val legs=candidate?.objects("legs")?.takeIf { it.isNotEmpty() }?:card.route.objects("legs")
 val segments=legs.mapIndexedNotNull {index,leg->
  val replacement=tramAlignments["${card.id}/$index"]
  val array=if(replacement==null)leg.optJSONArray("geometry")else null
  val points=if(array!=null)array.objects().mapNotNull { point->val lat=point.number("latitude");val lon=point.number("longitude");if(lat!=null&&lon!=null)GeoPoint(lat,lon)else null }else{
   val geometry=replacement?.optJSONObject("geometry")?:leg.optJSONObject("geometry");val c=geometry?.optJSONArray("coordinates")
   if(geometry?.optString("type")!="LineString"||c==null)emptyList()else(0 until c.length()).mapNotNull { i->val p=c.optJSONArray(i);val lon=p?.optDouble(0);val lat=p?.optDouble(1);if(lon!=null&&lat!=null&&lon.isFinite()&&lat.isFinite())GeoPoint(lat,lon)else null }
  }
  val transit=leg.optJSONObject("transit");val quality=if(replacement!=null)replacement.optJSONObject("reference_alignment")?.optString("source","OSM_REFERENCE_LINE")?:"OSM_REFERENCE_LINE"else transit?.optString("geometry_quality")
  val drawable=transit?.optString("source_mode")!="TRAM"||quality in listOf("OSM_REFERENCE_LINE","OSM_REFERENCE_NETWORK","GTFS_SHAPE")
  if(points.size<2||points.any { kotlin.math.abs(it.lat)>90||kotlin.math.abs(it.lon)>180 })null else MapSegment(points,modelMode(leg.optString("mode")),quality in listOf("APPROXIMATE_NO_SHAPES","OSM_REFERENCE_LINE","OSM_REFERENCE_NETWORK"),drawable,quality=="OSM_REFERENCE_NETWORK",transit?.optString("source_mode").orEmpty())
 }
 if(segments.isEmpty())null else MapRoute(card.id,card.mode,segments,card.audit?.optJSONObject("geometry_evidence")?.opt("route_identity_verified")==false)
}
fun validReflection(value:JSONObject,record:JSONObject,index:Int,language:String,wording:JSONObject):Boolean {
 val choice=record.optJSONArray("choices")?.optJSONObject(index)?:return false
 val identity=value.optJSONObject("identity")?:return false
 val template=value.optJSONObject("explanation")?:return false
 if(value.optString("schema_version")!="cricket-choice-explanation-response-v1"||identity.optString("search_id")!=record.optString("search_id")||identity.optInt("choice_index",-1)!=index||identity.opt("route_id")!=choice.opt("route_id")||identity.optString("mode_key")!=choice.optString("mode_key")||identity.optString("chosen_at")!=choice.optString("chosen_at")||identity.optString("choice_event_id").isEmpty()||value.optString("evidence_id").isEmpty()||template.optString("schema_version")!="cricket-choice-template-v1"||template.optString("language")!=language||template.opt("generated_by_llm")!=false||template.optString("source_evidence_id")!=value.optString("evidence_id")||template.optString("choice_event_id")!=identity.optString("choice_event_id")||template.optString("template_id").isEmpty())return false
 val statements=template.optJSONArray("statements")?:return false
 if(statements.objects().size!=statements.length()||statements.objects().any { listOf("id","rule","section","text").any { key->it.opt(key) !is String }||it.optJSONArray("evidence_refs")==null })return false
 val voice=value.optJSONObject("narration")?:return !value.has("narration")
 val catalog=if(voice.optString("wording_version")==wording.optString("version"))wording else wording.optJSONObject("legacy")?.optJSONObject(voice.optString("wording_version"))?:return false
 if(voice.optString("schema_version")!="cricket-choice-voice-v1"||voice.optString("language")!=language||voice.optString("source_template_id")!=template.optString("template_id")||voice.optString("source_evidence_id")!=template.optString("source_evidence_id")||voice.optString("choice_event_id")!=template.optString("choice_event_id")||voice.optString("provider")!="groq"||voice.optString("model")!="openai/gpt-oss-120b"||voice.optString("narration_mode")!="verified_phrase_selection"||voice.optString("prompt_version")!=catalog.optString("prompt_version","choice-voice-selector-1.0")||voice.optString("narration_id").isEmpty())return false
 if(voice.optString("status")=="TEMPLATE_FALLBACK")return voice.opt("generated_by_llm")==false&&voice.isNull("selection")&&voice.optString("reason") in listOf("NOT_CONFIGURED","PROVIDER_OR_VALIDATION_FAILURE")
 if(voice.optString("status")!="AVAILABLE"||voice.opt("generated_by_llm")!=true||!voice.isNull("reason"))return false
 val selection=voice.optJSONObject("selection")?:return false;val variants=selection.optJSONObject("variants")?:return false
 if(catalog.optJSONObject("openings")?.optJSONArray(language)?.opt(selection.optInt("opening",-1)) !is String||selection.optString("emphasis") !in listOf("affinities","tensions")||variants.length()!=statements.length())return false
 if(catalog.has("questions")) {
  val tone=voiceTone(template);if(selection.optString("tone")!=tone)return false
  val allowed=catalog.optJSONObject("tones")?.optJSONArray(tone)?:return false
  if((0 until allowed.length()).none { allowed.optInt(it)==selection.optInt("opening") })return false
  if(voiceQuestions(template,catalog).none { it.optString("id")==selection.optString("question") })return false
 }
 return statements.objects().all { row->val variant=variants.opt(row.optString("id"));val pair=catalog.optJSONObject("replacements")?.optJSONObject(language)?.optJSONArray(row.optString("rule"));variant is Int&&(variant==0||variant==1&&pair!=null&&row.optString("text").startsWith(pair.optString(0))) }
}
fun voiceTone(template:JSONObject):String {val rules=template.objects("statements").map { it.optString("rule") }.toSet();return when { rules.any { it in listOf("GOALS_UNKNOWN","NO_CONTEXT_SIMULATION") }->"uncertain";rules.any { it in listOf("GOALS_OPPOSITION","DIFFERENT_CLEAR","CHOICE_BELOW_AMBIGUOUS_PAIR") }->"reflective";else->"warm" }}
fun voiceQuestions(template:JSONObject,catalog:JSONObject):List<JSONObject> { val rules=template.objects("statements").map { it.optString("rule") }.toSet();return catalog.optJSONObject("questions")?.objects(template.optString("language"))?.filter { q->val requires=q.optJSONArray("requires");requires!=null&&(0 until requires.length()).all { requires.optString(it) in rules } }?:emptyList() }
data class DialogueRow(val section:String,val rule:String,val text:String)
fun dialogueRows(value:JSONObject?,wording:JSONObject):List<DialogueRow> {
 val template=value?.optJSONObject("explanation")?:return emptyList();val voice=value.optJSONObject("narration");val lang=template.optString("language")
 val catalog=if(voice?.optString("wording_version")==wording.optString("version"))wording else wording.optJSONObject("legacy")?.optJSONObject(voice?.optString("wording_version")?:"")
 val modern=catalog?.has("questions")==true;val generated=voice?.optBoolean("generated_by_llm")==true;val selection=voice?.optJSONObject("selection")
 val variants=selection?.optJSONObject("variants")
 val statements=template.objects("statements").map { row->val pair=catalog?.optJSONObject("replacements")?.optJSONObject(lang)?.optJSONArray(row.optString("rule"));val variant=if(generated)variants?.optInt(row.optString("id"),0)else if(modern)1 else 0;val text=row.optString("text");DialogueRow(row.optString("section"),row.optString("rule"),if(variant==1&&pair!=null&&text.startsWith(pair.optString(0)))pair.optString(1)+text.removePrefix(pair.optString(0))else text) }
 val openingIndex=if(generated)selection?.optInt("opening") else catalog?.optJSONObject("tones")?.optJSONArray(voiceTone(template))?.optInt(0)
 val opening=openingIndex?.let { catalog?.optJSONObject("openings")?.optJSONArray(lang)?.optString(it) }.orEmpty()
 val emphasis=selection?.optString("emphasis");val order=listOf("selection")+(if(emphasis=="tensions")listOf("tensions","affinities")else listOf("affinities","tensions"))+listOf("balance","comparison","observations","context","evolution","uncertainty","model_detail")
 val rows=mutableListOf<DialogueRow>();if(opening.isNotEmpty())rows+=DialogueRow("opening","OPENING",opening)
 order.forEach { section->rows+=statements.filter { it.section==section&&it.rule!="OBSERVED_rain" } };rows+=statements.filter { it.section !in order&&it.rule!="OBSERVED_rain" }
 if(modern){val questions=voiceQuestions(template,catalog!!);val question=if(generated)questions.find { it.optString("id")==selection?.optString("question") }else listOf("mixed","tension","support","priorities").firstNotNullOfOrNull { id->questions.find { it.optString("id")==id } };question?.let { rows+=DialogueRow("reflection","REFLECTION_QUESTION",it.optString("text")) }}
 return rows
}
