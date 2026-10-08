package de.ovgu.imiq.cricket

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID
import kotlin.math.roundToInt

class CricketModel(app:Application):AndroidViewModel(app) {
 private val database=Store(app)
 var revision by mutableStateOf(0)
 val store:Store get(){revision;return database}
 val api=Api(database)
 var ready by mutableStateOf(false)
 var migrationError by mutableStateOf<String?>(null)
 var pendingImport by mutableStateOf<JSONObject?>(null)
 var tab by mutableStateOf("home")
 var step by mutableStateOf("identity")
 var locale by mutableStateOf(store.get("imiq.experimental.language")?:"en")
 var busy by mutableStateOf(false)
 var error by mutableStateOf<String?>(null)
 var notice by mutableStateOf<String?>(null)
 var pendingPassport by mutableStateOf<JSONObject?>(null)
 private var pendingFingerprint:String?=null
 var result by mutableStateOf<JSONObject?>(null)
 var origin by mutableStateOf<Place?>(null)
 var destination by mutableStateOf<Place?>(null)
 var selectedId by mutableStateOf<String?>(null)
 var chosenId by mutableStateOf<String?>(null)
 var routeView by mutableStateOf("search")
 var activeMode by mutableStateOf("walk")
 var historyId by mutableStateOf<String?>(null)
 var reflection by mutableStateOf<JSONObject?>(null)
 var reflectionBusy by mutableStateOf(false)
 var reflectionError by mutableStateOf<String?>(null)
 var beliefStart by mutableStateOf<JSONObject?>(null)
 var beliefIndex by mutableStateOf(0)
 var beliefAnswers by mutableStateOf(JSONObject())
 private var operation:Job?=null
 private var operationVersion=0
 private var narration:Job?=null
 private var narrationVersion=0
 private val geocodingGate=Mutex()
 private var nextGeocodingStart=0L
 private val geocodingCache=linkedMapOf<String,Pair<Long,List<Place>>>()
 val de get()=locale=="de"
 fun text(en:String,de:String)=if(this.de)de else en
 fun refresh(){revision++;ready=true;step=store.get(ONBOARDING)?.takeIf { it in listOf("identity","goals","valences","beliefs","passport") }?:"identity"}
 fun companion()=store.json(COMPANION)?:store.json("imiq.experimental.companion-draft.v1")?:obj("name" to "","appearance" to "robot","palette" to "mint","version" to 1)
 fun passport()=store.passport()
 fun profile()=passport()?.optJSONObject("response")?.optJSONObject("cognitive_passport")
 fun settings()=store.json(SETTINGS)?:obj("version" to 1,"motion" to "full","textSize" to "normal","maxWalk" to 500)
 fun availability()=store.json("imiq.experimental.availability.v1")?.optJSONObject("answers")?:obj(*modes.map { it to null }.toTypedArray())
 fun valences()=store.json("imiq.experimental.valences-draft.v1")?.optJSONObject("answers")?:store.json("imiq.experimental.valences.v1")?.optJSONObject("answers")?:obj(*modes.map { it to null }.toTypedArray())
 fun plan():JSONObject=store.json("imiq.experimental.weekly-goals.v1.${week()}.draft")?:store.json("imiq.experimental.weekly-goals.v1.${week()}.saved")?:obj("version" to 1,"weekStart" to week(),"budget" to 10,"points" to obj(*goalKeys.map { it to 0 }.toTypedArray()))
 fun goStep(next:String){store.put(ONBOARDING,next);step=next;error=null;pendingPassport=null;revision++}
 fun setLanguage(next:String){locale=next;store.put("imiq.experimental.language",next)}
 fun saveCompanion(name:String,appearance:String){require(name.trim().isNotEmpty());val item=obj("version" to 1,"name" to name.trim().take(24),"appearance" to appearance,"palette" to "mint");store.put(COMPANION,item);store.put("imiq.experimental.companion-draft.v1",item);revision++}
 fun saveAvailability(value:JSONObject){store.put("imiq.experimental.availability.v1",obj("version" to 1,"answers" to value));revision++}
 fun adjustGoal(key:String,delta:Int){val p=plan();val points=p.getJSONObject("points");val sum=goalKeys.sumOf { points.optInt(it) };val next=points.optInt(key)+delta;if(next in 0..10&&(delta<0||sum<10)){points.put(key,next);store.put("imiq.experimental.weekly-goals.v1.${week()}.draft",p);revision++}}
 fun saveGoals(){val p=plan();require(goalKeys.sumOf { p.getJSONObject("points").optInt(it) }==10);store.put("imiq.experimental.weekly-goals.v1.${week()}.saved",p);store.remove("imiq.experimental.adaptive-profile.v1");goStep("valences")}
 fun rateFeeling(mode:String,value:Int,index:Int){val answers=valences();answers.put(mode,value.coerceIn(-3,3));store.put("imiq.experimental.valences-draft.v1",obj("version" to 1,"index" to index,"answers" to answers));revision++}
 fun saveFeelings(){val answers=valences();require(modes.all { answers.opt(it) is Int&&answers.getInt(it) in -3..3 });store.put("imiq.experimental.valences.v1",obj("version" to 1,"index" to 0,"answers" to answers));goStep("beliefs");prepareBeliefs()}
 fun onboardingInputs():JSONObject {
  val points=plan().getJSONObject("points");require(goalKeys.sumOf { points.optInt(it) }==10)
  val feelings=valences();require(modes.all { !feelings.isNull(it) })
  val agent=store.get("imiq.experimental.agent-id.v1")?:"exp-${UUID.randomUUID()}".also { store.put("imiq.experimental.agent-id.v1",it) }
  return obj("schema_version" to "adaptive_cognitive_passport_onboarding_1.0","agent_id" to agent,"responses" to obj("needs" to obj(*goalKeys.mapIndexed { index,key->needKeys[index] to 1+(6.0*points.optInt(key)/10).roundToInt() }.toTypedArray()),"valences" to feelings))
 }
 fun fingerprint()=canonical(obj("weekStart" to week(),"payload" to onboardingInputs()))
 fun run(block:suspend ()->Unit){if(busy)return;error=null;busy=true;val version=++operationVersion;operation=viewModelScope.launch { try{block()}catch(e:CancellationException){throw e}catch(e:Exception){if(version==operationVersion)error=e.message?:text("Connection failed","Verbindung fehlgeschlagen")}finally{if(version==operationVersion)busy=false} }}
 fun cancel(){operationVersion++;operation?.cancel();busy=false;notice=text("Cancelled","Abgebrochen")}
 fun prepareBeliefs(force:Boolean=false)=run {
  val inputs=onboardingInputs();val initialFingerprint=fingerprint();val cached=store.json("imiq.experimental.adaptive-beliefs.v1")
  if(!force&&cached?.optString("fingerprint")==fingerprint()){beliefStart=cached.getJSONObject("start");beliefAnswers=cached.getJSONObject("answers");beliefIndex=cached.optInt("index")}else{
   val start=api.call("/api/dyconet/adaptive-passport/start",inputs)
   require(initialFingerprint==fingerprint()) { "Your answers changed. Review your goals and feelings." }
   require(start.optString("stage")=="questions"&&start.objects("questions").size==4&&start.objects("questions").map {it.optString("cell_id")}.distinct().size==4&&start.optString("agent_id")==inputs.getString("agent_id")&&start.objects("questions").all {it.optString("hotco_mode") in modes&&it.optString("model_need") in needKeys&&it.optString("cell_id")=="${it.optString("hotco_mode")}__${it.optString("model_need")}"&&it.optJSONObject("response_scale")?.optInt("minimum")==1&&it.optJSONObject("response_scale")?.optInt("maximum")==7}) { "Invalid calibration response" }
   beliefStart=start;beliefAnswers=JSONObject();beliefIndex=0;persistBeliefs()
  }
 }
 fun persistBeliefs(){val start=beliefStart?:return;store.put("imiq.experimental.adaptive-beliefs.v1",obj("fingerprint" to fingerprint(),"start" to start,"answers" to beliefAnswers,"index" to beliefIndex));revision++}
 fun rateBelief(cell:String,rating:Int){beliefAnswers=beliefAnswers.copyJson().put(cell,rating.coerceIn(1,7));persistBeliefs()}
 fun beliefNext(){if(beliefIndex<3){beliefIndex++;persistBeliefs()}else run {
  val start=beliefStart?:error("Missing questions");require(start.objects("questions").all { beliefAnswers.optInt(it.getString("cell_id")) in 1..7 })
  val initialFingerprint=fingerprint()
  val completed=api.call("/api/dyconet/adaptive-passport/complete",obj("schema_version" to start.getString("schema_version"),"start" to start,"answers" to beliefAnswers))
  require(initialFingerprint==fingerprint()) { "Your answers changed. Review your goals and feelings." }
  require(completed.optString("stage")=="complete"&&completed.optBoolean("ready_for_hotco")) { "Invalid calibration completion" }
  store.put("imiq.experimental.adaptive-profile.v1",obj("version" to 1,"fingerprint" to fingerprint(),"response" to completed));goStep("passport");createPassportInternal()
 }}
 private suspend fun createPassportInternal(){
  val completed=store.json("imiq.experimental.adaptive-profile.v1")?:error("Complete your four connections first")
  require(completed.optString("fingerprint")==fingerprint()) { "Your answers changed. Review your connections." }
  val available=availability();require(modes.all { available.opt(it) is Boolean }&&modes.any { available.optBoolean(it) })
  val initialFingerprint=fingerprint()
  val value=api.call("/api/dyconet/adaptive-passport/bootstrap",obj("schema_version" to "adaptive_hotco_bootstrap_1.0","completed_passport" to completed.getJSONObject("response"),"availability" to available))
  require(initialFingerprint==fingerprint()&&canonical(available)==canonical(availability())) { "Your answers changed. Create the Passport again." }
  val p=value.optJSONObject("cognitive_passport")?:error("Invalid Passport response")
  require(p.optString("schema_version")=="2.0"&&p.optJSONObject("profile")?.optJSONObject("beliefs")!=null)
  pendingPassport=value
  pendingFingerprint=initialFingerprint
 }
 fun createPassport()=run { createPassportInternal() }
 fun confirmPassport(){
  try {
  val response=pendingPassport?:return;val p=plan();val available=availability();val feelings=obj("answers" to valences())
  require(pendingFingerprint==fingerprint()) { "Your answers changed. Create the Passport again." }
  val source=obj("plan" to p,"valences" to feelings,"availability" to available,"toleranceState" to obj("answers" to JSONObject()),"inputs" to obj("weekStart" to week()))
  val saved=obj("version" to 1,"fingerprint" to fingerprint(),"confirmedAt" to now(),"createdAt" to now(),"weekStart" to week(),"goalPoints" to p.getJSONObject("points"),"needsSource" to "weekly_goal_allocation","conversion" to "points_div10_round_to_1_7_v1","availability" to available,"sourceSnapshot" to source,"response" to response)
  require(validPassport(saved));store.passport()?.let(store::archive);store.put(PASSPORT,saved);store.remove("cricket.passport-renewal.v1");pendingPassport=null;goStep("passport");tab="home"
  }catch(e:Exception){error=e.message}
 }
 fun beginRenewal(){if(store.json("cricket.passport-renewal.v1")!=null)return;val keys=store.storage();val answers=JSONObject();keys.keys().asSequence().filter { it.startsWith("imiq.experimental.weekly-goals.v1.")||it in listOf(ONBOARDING,"imiq.experimental.valences.v1","imiq.experimental.valences-draft.v1","imiq.experimental.adaptive-beliefs.v1","imiq.experimental.adaptive-profile.v1") }.toList().forEach { key->answers.put(key,keys.getString(key));store.remove(key) };store.put("cricket.passport-renewal.v1",obj("version" to 1,"answers" to answers));tab="passport";goStep("goals")}
 fun cancelRenewal(){val answers=store.json("cricket.passport-renewal.v1")?.optJSONObject("answers")?:return;val current=store.storage();current.keys().asSequence().filter { it.startsWith("imiq.experimental.weekly-goals.v1.")||it in listOf(ONBOARDING,"imiq.experimental.valences.v1","imiq.experimental.valences-draft.v1","imiq.experimental.adaptive-beliefs.v1","imiq.experimental.adaptive-profile.v1") }.toList().forEach(store::remove);answers.keys().forEach { if(!answers.isNull(it))store.put(it,answers.getString(it)) };store.remove("cricket.passport-renewal.v1");goStep("passport");tab="home"}
 fun setPlace(start:Boolean,place:Place?){if(start)origin=place else destination=place;result=null;selectedId=null;chosenId=null;reflection=null;routeView="search"}
 suspend fun findPlaces(query:String,bias:Place?):List<Place>{
  require(query.trim().length in 3..240)
  val language=locale
  val key="${api.base()}:$language:${bias?.lat}:${bias?.lon}:${query.trim().lowercase()}"
  geocodingCache[key]?.takeIf {System.currentTimeMillis()-it.first<300000}?.let {return it.second}
  geocodingGate.withLock {delay((nextGeocodingStart-System.currentTimeMillis()).coerceAtLeast(0));nextGeocodingStart=System.currentTimeMillis()+1000}
  val suffix="?q=${URLEncoder.encode(query.trim(),"UTF-8")}&lang=$language&lat=${bias?.lat?:52.13}&lon=${bias?.lon?:11.62}"
  val data=api.call("/api/dyconet/geocoding$suffix")
  val requested=Regex("\\s(\\d{1,4}[a-z]?(?:[-/]\\d{1,4}[a-z]?)?)(?=\\s|$)",RegexOption.IGNORE_CASE).findAll(query.substringBefore(',')).lastOrNull()?.groupValues?.get(1)?.lowercase()
  val places=data.objects("features").mapNotNull(::placeFromFeature).filter {requested==null||it.house.lowercase()==requested}
  geocodingCache[key]=System.currentTimeMillis() to places
  if(geocodingCache.size>50)geocodingCache.remove(geocodingCache.keys.first())
  return places
 }
 fun search()=run {
  val active=passport()?:error("Confirm your Passport first");val p=active.getJSONObject("response").getJSONObject("cognitive_passport");val start=origin?:error("Select an origin suggestion");val stop=destination?:error("Select a destination suggestion")
  val available=settings().optJSONObject("journeyAvailability")?:p.getJSONObject("profile").getJSONObject("availability")
  require(modes.any { available.optBoolean(it) }) { "Enable at least one transport mode" }
  val id="route-${UUID.randomUUID()}";val request=obj("search_id" to id,"datetime" to now(),"cognitive_passport" to p,"journey_availability" to available,"start" to start.coordinates(),"stop" to stop.coordinates(),"max_walk_m" to settings().optInt("maxWalk",500),"tolerance_profile" to JSONObject(),"contextual_query" to obj("query_orion" to true,"entity_families" to JSONArray(listOf("weather")),"weather_radius_meters" to 5000))
  val lineage=p.getJSONObject("lineage");val record=obj("version" to 1,"app_version" to "0.3.0","recommendation_rule" to "unanimous-clear-mode-v1","search_id" to id,"created_at" to now(),"status" to "pending","request" to request,"places" to obj("origin" to start.json(),"destination" to stop.json()),"passport_reference" to obj("passport_id" to lineage.getString("passport_id"),"revision" to lineage.getInt("revision"),"fingerprint" to active.getString("fingerprint"),"confirmed_at" to active.optString("confirmedAt")),"confirmed_snapshot" to active,"choices" to JSONArray())
  store.saveRecord(record);historyId=id;result=null;chosenId=null;reflection=null
  try {
   val response=api.call("/api/dyconet/routed-contextual-deliberation",request,180000)
   require(response.optString("schema_version")=="routed-contextual-deliberation-v1"&&response.optString("search_id")==id&&response.optJSONObject("routing")?.optJSONArray("routes")!=null&&response.optJSONArray("route_audit")!=null) { "Invalid route response" }
   require(modes.all { response.optJSONObject("journey_availability")?.opt(it)==available.opt(it) }) { "Availability changed in response" }
   val recommendation=recommendation(response);record.put("status","complete").put("completed_at",now()).put("result",response).put("recommendation",recommendation.json());store.saveRecord(record)
   result=response;activeMode=recommendation.winner?:cards(response).firstOrNull()?.mode?:"walk";selectedId=recommendation.primary?.id;routeView="alternatives"
   val recent=JSONArray();(listOf(start,stop)+recents()).distinctBy { it.lat to it.lon }.take(6).forEach { recent.put(it.json()) };store.put("cricket.recent-places.v1",recent);revision++
  }catch(e:Exception){record.put("status","failed").put("completed_at",now()).put("failure",if(e is CancellationException)"cancelled" else e.message);store.saveRecord(record);throw e}
 }
 fun recents():List<Place> { val raw=store.get("cricket.recent-places.v1")?:return emptyList();return runCatching { val parsed=JSONArray(raw);parsed.objects().mapNotNull(Place::fromJson) }.getOrDefault(emptyList()) }
 fun choose(card:RouteCard){
  if(!card.route.optBoolean("available")||card.expired())return
  val id=historyId?:return;val record=store.record(id)?:return
  require(record.optString("status")=="complete"&&record.optJSONObject("result")?.optJSONObject("routing")?.objects("routes")?.any { it.optInt("rank")==card.route.optInt("rank")&&it.optString("mode_key")==card.route.optString("mode_key")&&it.optBoolean("available") }==true)
  val choice=obj("chosen_at" to now(),"route_id" to card.audit?.opt("route_id"),"routing_rank" to card.route.getInt("rank"),"mode_key" to card.route.getString("mode_key"),"summary" to card.route.optJSONObject("summary"))
  if(card.route.optString("provider")=="otp")listOf("provider","provider_itinerary_id","transit","legs").forEach { choice.put(it,card.route.opt(it)) }
  record.getJSONArray("choices").put(choice);store.saveRecord(record);chosenId=card.id;selectedId=card.id;activeMode=card.mode;routeView="reflection";revision++;loadReflection()
 }
 fun loadReflection(force:Boolean=false){
  narration?.cancel();val version=++narrationVersion;reflection=null;reflectionError=null;reflectionBusy=true
  val id=historyId
  if(id==null){reflectionBusy=false;return}
  val language=locale
  narration=viewModelScope.launch { try {
   val record=store.record(id)?:error("Journey not saved");val index=record.getJSONArray("choices").length()-1
   val existing=if(force)null else record.objects("explanations").lastOrNull { it.optInt("choice_index")==index&&it.optString("language")==language }?.optJSONObject("response")
   val value=existing?:api.call("/api/dyconet/choice-explanation",obj("record" to record,"choice_index" to index,"confirmed_snapshot" to (store.confirmedSnapshot(record)?:error("The original Passport for this journey is unavailable")),"language" to language),30000)
   require(validReflection(value,record,index,language,getApplication<Application>().assets.open("choiceWordings.json").bufferedReader().use { JSONObject(it.readText()) })) { "Unverified explanation" }
   if(existing==null){val entries=record.optJSONArray("explanations")?:JSONArray();entries.put(obj("choice_index" to index,"language" to language,"response" to value,"created_at" to now()));record.put("explanations",entries);store.saveRecord(record)}
   if(version==narrationVersion)reflection=value
  }catch(e:CancellationException){throw e}catch(e:Exception){if(version==narrationVersion)reflectionError=e.message}finally{if(version==narrationVersion)reflectionBusy=false} }
 }
 fun openHistory(record:JSONObject){if(record.optString("status")!="complete")return;result=record.getJSONObject("result");historyId=record.getString("search_id");origin=Place.fromJson(record.getJSONObject("places").getJSONObject("origin"));destination=Place.fromJson(record.getJSONObject("places").getJSONObject("destination"));val choice=record.objects("choices").lastOrNull();chosenId=choice?.let { "${it.optInt("routing_rank")}-${it.optString("mode_key")}" };selectedId=chosenId;activeMode=cards(result).find { it.id==chosenId }?.mode?:cards(result).firstOrNull()?.mode?:"walk";tab="journey";routeView=if(choice==null)"alternatives" else "reflection";if(choice!=null)loadReflection()}
 fun saveSettings(value:JSONObject){val max=value.optInt("maxWalk",500);require(max in 0..10000);store.put(SETTINGS,value);revision++}
}

data class Place(val lat:Double,val lon:Double,val label:String,val id:String,val house:String="",val street:String="",val detail:String="",val source:String="geocoding",val accuracy:Float?=null) {
 fun coordinates()=obj("lat" to lat,"lon" to lon)
 fun json()=obj("lat" to lat,"lon" to lon,"label" to label,"id" to id,"houseNumber" to house,"street" to street,"detail" to detail,"source" to source,"accuracy_meters" to accuracy)
 companion object { fun fromJson(value:JSONObject):Place? { val lat=value.number("lat")?:return null;val lon=value.number("lon")?:return null;if(kotlin.math.abs(lat)>90||kotlin.math.abs(lon)>180)return null;return Place(lat,lon,value.optString("label"),value.optString("id"),value.optString("houseNumber"),value.optString("street"),value.optString("detail"),value.optString("source","geocoding"),value.number("accuracy_meters")?.toFloat()) } }
}
fun placeFromFeature(feature:JSONObject):Place? {
 val geometry=feature.optJSONObject("geometry")?:return null;if(geometry.optString("type")!="Point")return null
 val c=geometry.optJSONArray("coordinates")?:return null;val p=feature.optJSONObject("properties")?:return null
 val lon=c.optDouble(0);val lat=c.optDouble(1);if(!lat.isFinite()||!lon.isFinite()||kotlin.math.abs(lat)>90||kotlin.math.abs(lon)>180)return null
 val street=p.optString("street");val house=p.optString("housenumber");val city=p.optString("city");val name=p.optString("name");val address=listOf(street,house).filter { it.isNotEmpty() }.joinToString(" ")
 val label=listOf(name,address,city).filter { it.isNotEmpty() }.distinct().joinToString(", ");if(label.isEmpty())return null
 return Place(lat,lon,label,p.optString("osm_type")+p.optString("osm_id"),house,street,listOf(p.optString("postcode"),city).filter { it.isNotEmpty() }.joinToString(" "))
}
