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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID

internal const val STARTUP_DURATION_MS=12000L

class CricketModel(app:Application):AndroidViewModel(app) {
 private val database=Store(app)
 var revision by mutableStateOf(0)
 val store:Store get(){revision;return database}
 val api=Api(database)
 val trips=TripRepository(app,database)
 var tripSearchId by mutableStateOf<String?>(null)
 var tripId by mutableStateOf<String?>(null)
 var tripStartingSearchId by mutableStateOf<String?>(null)
 init {
  viewModelScope.launch {TripUpdates.revision.collect {revision++}}
  viewModelScope.launch {TripUpdates.errors.collect {failure->
   error=failure.message
   if(failure.searchId!=null&&tripStartingSearchId==failure.searchId){tripStartingSearchId=null;routeView="alternatives"}
  }}
 }
 var ready by mutableStateOf(false)
 var startupVisible by mutableStateOf(true)
 fun skipStartupWelcome(){if(ready)startupVisible=false}
 private var startupStartedAt:Long?=null
 fun beginStartupWelcome(){if(startupStartedAt==null)startupStartedAt=android.os.SystemClock.elapsedRealtime()}
 fun startupElapsedMs():Long = startupStartedAt?.let {(android.os.SystemClock.elapsedRealtime()-it).coerceAtLeast(0L)}?:0L
 var migrationError by mutableStateOf<String?>(null)
 var pendingImport by mutableStateOf<JSONObject?>(null)
 var tab by mutableStateOf("home")
 var step by mutableStateOf("identity")
 var locale by mutableStateOf(store.get("imiq.experimental.language")?:"en")
 var busy by mutableStateOf(false)
 var routeSearching by mutableStateOf(false)
 var error by mutableStateOf<String?>(null)
 var notice by mutableStateOf<String?>(null)
 var dailyGoalsVisible by mutableStateOf(false)
 private var searchAfterDailyGoals=false
 var pendingPassport by mutableStateOf<JSONObject?>(null)
 private var pendingFingerprint:String?=null
 var result by mutableStateOf<JSONObject?>(null)
 var origin by mutableStateOf<Place?>(null)
 var planningLocationAttempted by mutableStateOf(false)
 var planningPosition by mutableStateOf<JSONObject?>(null)
 var destination by mutableStateOf<Place?>(null)
 var selectedId by mutableStateOf<String?>(null)
 var chosenId by mutableStateOf<String?>(null)
 var routeView by mutableStateOf("search")
 var activeMode by mutableStateOf("walk")
 var historyId by mutableStateOf<String?>(null)
 var routeProfileSnapshot by mutableStateOf<Pair<String,JSONObject>?>(null)
 var reflection by mutableStateOf<JSONObject?>(null)
 var reflectionBusy by mutableStateOf(false)
 var reflectionError by mutableStateOf<String?>(null)
 var companionReflection by mutableStateOf<JSONObject?>(null)
 var companionReflectionBusy by mutableStateOf(false)
 var companionReflectionFailed by mutableStateOf(false)
 private var companionNarration:Job?=null
 private var companionNarrationVersion=0
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
 fun refresh(){revision++;ready=true;step=store.get(ONBOARDING)?.takeIf { it in listOf("identity","travel-kit","goals","valences","beliefs","passport") }?:"identity";ProspectiveLearning.resume(getApplication());LearnedPassportLearning.resume(getApplication())}
 fun companion()=store.json(COMPANION)?:store.json("imiq.experimental.companion-draft.v1")?:obj("name" to "","appearance" to "robot","palette" to "mint","version" to 1)
 fun passport()=store.passport()
 fun profile()=passport()?.optJSONObject("response")?.optJSONObject("cognitive_passport")
 fun settings():JSONObject {
  val value=store.json(SETTINGS)?:obj("version" to 1,"motion" to "full","textSize" to "normal","maxWalk" to 1500)
  if(value.optInt("walkingDefaultVersion")<2) {
   if(!value.has("maxWalk")||value.optInt("maxWalk")==500)value.put("maxWalk",1500)
   value.put("walkingDefaultVersion",2);store.put(SETTINGS,value)
  }
  return value
 }
 fun availability()=store.json("imiq.experimental.availability.v1")?.optJSONObject("answers")?:obj(*modes.map { it to null }.toTypedArray())
 fun valences()=store.json("imiq.experimental.valences-draft.v1")?.optJSONObject("answers")?:store.json("imiq.experimental.valences.v1")?.optJSONObject("answers")?:obj(*modes.map { it to null }.toTypedArray())
 fun plan():JSONObject=proposedDailyPlan(store.json(dailyKey(today(),"draft"))?:store.json(dailyKey(today(),"saved")),passport(),today())
 fun dailyGoalsCurrent():Boolean=profile()?.optJSONObject("daily_goals")?.let {it.optString("local_date")==today()&&it.optString("timezone")==java.time.ZoneId.systemDefault().id}==true
 fun openDailyGoals(){dailyGoalsVisible=true;error=null}
 fun closeDailyGoals(){dailyGoalsVisible=false;searchAfterDailyGoals=false}
 fun confirmDailyGoals(editedPlan:JSONObject?=null,expectedPlan:JSONObject?=null,onSaved:()->Unit={})=run {
  val current=passport()?:error("Confirm your Passport first")
  val base=plan()
  require(expectedPlan==null||canonical(base)==canonical(expectedPlan)) {"Your daily goals changed. Open the goal again."}
  val draft=(editedPlan?:base).copyJson().put("confirmedAt",now()).put("timezone",java.time.ZoneId.systemDefault().id)
  val payload=dailyGoalPayload(draft);val expected=canonical(base.getJSONObject("points"))
  val response=api.call("/api/dyconet/daily-passport/confirm",obj("schema_version" to "daily-passport-confirm-v1","previous_passport" to current.getJSONObject("response").getJSONObject("cognitive_passport"),"daily_goals" to payload))
  require(today()==draft.getString("localDate")&&canonical(plan().getJSONObject("points"))==expected) {"Your daily goals changed. Confirm them again."}
  val next=current.copyJson().put("response",response).put("confirmedAt",draft.getString("confirmedAt")).put("createdAt",draft.getString("confirmedAt"))
   .put("goalRatings",draft.getJSONObject("points")).put("needsSource",NEED_IMPORTANCE_SCALE).put("conversion","rating_minus1_div6_v1")
   .put("fingerprint",canonical(obj("dailyGoals" to payload,"parent" to current.getJSONObject("response").getJSONObject("cognitive_passport").getJSONObject("lineage").getString("passport_id"))))
  val source=current.optJSONObject("sourceSnapshot")?.copyJson()?:JSONObject();source.put("plan",draft);next.put("sourceSnapshot",source)
  withContext(Dispatchers.IO){database.commitDailyPassport(current,next,draft)}
  dailyGoalsVisible=false;revision++;onSaved()
  if(searchAfterDailyGoals){searchAfterDailyGoals=false;viewModelScope.launch {while(busy)delay(20);search()}}
 }

 fun goStep(next:String){store.put(ONBOARDING,next);step=next;error=null;pendingPassport=null;revision++}
 fun setLanguage(next:String){locale=next;store.put("imiq.experimental.language",next)}
 fun saveCompanion(name:String,appearance:String){require(name.trim().isNotEmpty());val item=obj("version" to 1,"name" to name.trim().take(24),"appearance" to appearance,"palette" to "mint");store.put(COMPANION,item);store.put("imiq.experimental.companion-draft.v1",item);revision++}
 fun saveAvailability(value:JSONObject){store.put("imiq.experimental.availability.v1",obj("version" to 1,"answers" to value));revision++}
 fun mobilityHabits()=store.json("cricket.mobility-habits.v1")?.optJSONObject("answers")?:obj(*modes.map {it to null}.toTypedArray())
 fun saveMobilityHabit(mode:String,habitual:Boolean){require(mode in modes);store.put("cricket.mobility-habits.v1",obj("version" to 1,"updated_at" to now(),"answers" to mobilityHabits().copyJson().put(mode,habitual)));revision++}
 fun confirmMobilityOptions(){
  require(modes.all {mobilityHabits().opt(it) is Boolean})
  // Unknown access stays included unless the user explicitly excludes it in route options.
  val available=availability().copyJson();modes.forEach {if(available.opt(it) !is Boolean)available.put(it,true)}
  require(modes.any {available.optBoolean(it)})
  saveAvailability(available);goStep("goals")
 }
 fun adjustGoal(key:String,delta:Int){require(key in goalKeys&&delta in listOf(-1,1));val p=plan();val points=p.getJSONObject("points");val next=points.optInt(key)+delta;if(next in 1..7){points.put(key,next);store.put(dailyKey(today(),"draft"),p);revision++}}
 fun saveGoals(){val p=plan();needImportanceRatings(p.getJSONObject("points"));p.put("confirmedAt",now()).put("timezone",java.time.ZoneId.systemDefault().id);store.put(dailyKey(today(),"saved"),p);store.remove(dailyKey(today(),"draft"));store.remove("imiq.experimental.adaptive-profile.v1");goStep("valences")}
 fun rateFeeling(mode:String,value:Int,index:Int){val answers=valences();answers.put(mode,value.coerceIn(-3,3));store.put("imiq.experimental.valences-draft.v1",obj("version" to 1,"index" to index,"answers" to answers));revision++}
 fun saveFeelings(){val answers=valences();require(modes.all { answers.opt(it) is Int&&answers.getInt(it) in -3..3 });store.put("imiq.experimental.valences.v1",obj("version" to 1,"index" to 0,"answers" to answers));goStep("beliefs");prepareBeliefs()}
 fun onboardingInputs():JSONObject {
  val needs=needImportanceRatings(plan().getJSONObject("points"))
  val feelings=valences();require(modes.all { !feelings.isNull(it) })
  val agent=store.get("imiq.experimental.agent-id.v1")?:"exp-${UUID.randomUUID()}".also { store.put("imiq.experimental.agent-id.v1",it) }
  return obj("schema_version" to "adaptive_cognitive_passport_onboarding_1.0","agent_id" to agent,"responses" to obj("needs" to needs,"valences" to feelings))
 }
 fun fingerprint()=canonical(obj("localDate" to today(),"payload" to onboardingInputs()))
 fun run(routeSearch:Boolean=false,block:suspend ()->Unit){if(busy)return;error=null;busy=true;routeSearching=routeSearch;val version=++operationVersion;operation=viewModelScope.launch { try{block()}catch(e:CancellationException){throw e}catch(e:Exception){if(version==operationVersion)error=e.message?:text("Connection failed","Verbindung fehlgeschlagen")}finally{if(version==operationVersion){busy=false;routeSearching=false}} }}
 fun cancel(){operationVersion++;operation?.cancel();busy=false;routeSearching=false;notice=text("Cancelled","Abgebrochen")}
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
  val value=api.call("/api/dyconet/adaptive-passport/bootstrap",obj("schema_version" to "adaptive_hotco_bootstrap_1.0","completed_passport" to completed.getJSONObject("response"),"availability" to available,"daily_goals" to dailyGoalPayload(plan())))
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
  val source=obj("plan" to p,"valences" to feelings,"availability" to available,"habitual_mobility" to (store.json("cricket.mobility-habits.v1")?:JSONObject.NULL),"toleranceState" to obj("answers" to JSONObject()),"inputs" to obj("weekStart" to week()))
  val saved=obj("version" to 1,"fingerprint" to fingerprint(),"confirmedAt" to now(),"createdAt" to now(),"weekStart" to week(),"goalRatings" to p.getJSONObject("points"),"needsSource" to NEED_IMPORTANCE_SCALE,"conversion" to "rating_minus1_div6_v1","availability" to available,"sourceSnapshot" to source,"response" to response)
  require(validPassport(saved));store.passport()?.let(store::archive);store.put(PASSPORT,saved);store.remove("cricket.passport-renewal.v1");pendingPassport=null;goStep("passport");tab="home"
  }catch(e:Exception){error=e.message}
 }
 fun beginRenewal(){
  if(store.json("cricket.passport-renewal.v1")!=null)return
  val keys=store.storage();val answers=JSONObject()
  // Keep the current companion/kit prefilled, but restore them if the draft is cancelled.
  listOf(COMPANION,"imiq.experimental.companion-draft.v1","imiq.experimental.availability.v1","cricket.mobility-habits.v1").forEach {key->answers.put(key,keys.opt(key)?:JSONObject.NULL)}
  keys.keys().asSequence().filter { (it.startsWith("imiq.experimental.weekly-goals.v1.")||it.startsWith("imiq.experimental.daily-goals.v1."))||it in listOf(ONBOARDING,"imiq.experimental.valences.v1","imiq.experimental.valences-draft.v1","imiq.experimental.adaptive-beliefs.v1","imiq.experimental.adaptive-profile.v1") }.toList().forEach { key->answers.put(key,keys.getString(key));store.remove(key) }
  store.put("cricket.passport-renewal.v1",obj("version" to 1,"answers" to answers));tab="passport";goStep("identity")
 }
 fun cancelRenewal(){val answers=store.json("cricket.passport-renewal.v1")?.optJSONObject("answers")?:return;val current=store.storage();current.keys().asSequence().filter { (it.startsWith("imiq.experimental.weekly-goals.v1.")||it.startsWith("imiq.experimental.daily-goals.v1."))||it in listOf(ONBOARDING,"imiq.experimental.valences.v1","imiq.experimental.valences-draft.v1","imiq.experimental.adaptive-beliefs.v1","imiq.experimental.adaptive-profile.v1") }.toList().forEach(store::remove);answers.keys().forEach { if(answers.isNull(it))store.remove(it)else store.put(it,answers.getString(it)) };store.remove("cricket.passport-renewal.v1");goStep("passport");tab="home"}
 var originParkingId by mutableStateOf<String?>(null)
 var destinationParkingId by mutableStateOf<String?>(null)
 private var parkingReselect=false
 var boardingStopId by mutableStateOf<String?>(null)
 var boardingTripId by mutableStateOf<String?>(null)
 var boardingDepartureMs:Double?=null
 private var boardingPlanTime:String?=null
 private var transitReselect=false
 fun planStopItinerary(stop:MobilityPoi,route:JSONObject){
  if(busy)return
  val board=route.objects("legs").firstOrNull {it.optString("mode")=="pt"}?.optJSONObject("transit")?:return
  val stopId=board.optJSONObject("from")?.optJSONObject("stop")?.optString("gtfsId")?:return
  if(stopId !in stop.gtfsIds)return
  boardingStopId=stopId;boardingTripId=board.optString("gtfs_trip_id");boardingDepartureMs=board.number("scheduled_start_time_ms")
  // The plan window belongs to the timetable; context remains captured now.
  boardingPlanTime=java.time.Instant.ofEpochMilli(maxOf(System.currentTimeMillis(),(route.optJSONObject("transit")?.number("start_time_ms")?.toLong()?:System.currentTimeMillis())-30000)).toString()
  transitReselect=true;search()
 }
 fun changeParking(start:Boolean,id:String?){if(busy)return;if(start)originParkingId=id else destinationParkingId=id;parkingReselect=true;search()}
 fun setPlace(start:Boolean,place:Place?){if(start){origin=place;originParkingId=null}else{destination=place;destinationParkingId=null};boardingStopId=null;boardingTripId=null;boardingDepartureMs=null;boardingPlanTime=null;result=null;selectedId=null;chosenId=null;reflection=null;routeView="search"}
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
 fun search(){if(passport()!=null&&!dailyGoalsCurrent()){searchAfterDailyGoals=true;openDailyGoals();return};searchInternal()}
 private fun searchInternal()=run(routeSearch=true) {
  val active=passport()?:error("Confirm your Passport first");val p=active.getJSONObject("response").getJSONObject("cognitive_passport");val start=origin?:error("Select an origin suggestion");val stop=destination?:error("Select a destination suggestion")
  val available=settings().optJSONObject("journeyAvailability")?:p.getJSONObject("profile").getJSONObject("availability")
  require(modes.any { available.optBoolean(it) }) { "Enable at least one transport mode" }
  val id="route-${UUID.randomUUID()}";val request=obj("search_id" to id,"datetime" to now(),"cognitive_passport" to p,"journey_availability" to available,"start" to start.coordinates(),"stop" to stop.coordinates(),"max_walk_m" to settings().optInt("maxWalk",1500),"tolerance_profile" to JSONObject(),"record_trajectories" to true,"context_policy" to "observations_only","contextual_query" to obj("query_orion" to true,"entity_families" to JSONArray(listOf("weather")),"weather_radius_meters" to 5000))
  val pois=withContext(Dispatchers.IO){MagdeburgMobility.load(getApplication())}
  val parkingSupported=nearestPublicParking(pois,GeoPoint(start.lat,start.lon))!=null&&nearestPublicParking(pois,GeoPoint(stop.lat,stop.lon))!=null
  if(available.optBoolean("car")&&parkingSupported)request.put("car_parking",obj("policy" to "nearest-public-osm-magdeburg-v1","origin_id" to originParkingId,"destination_id" to destinationParkingId))
  val keepCar=parkingReselect;parkingReselect=false
  val keepTransit=transitReselect;transitReselect=false
  if(boardingStopId!=null)request.put("boarding_stop_id",boardingStopId).put("boarding_trip_id",boardingTripId).put("boarding_departure_ms",boardingDepartureMs).put("boarding_plan_datetime",boardingPlanTime)
  val lineage=p.getJSONObject("lineage");val record=obj("version" to 1,"app_version" to BuildConfig.VERSION_NAME,"comfort_pilot_required" to true,"affect_pilot_required" to true,"daily_goals_reference" to p.optJSONObject("daily_goals")?.copyJson(),"recommendation_rule" to "unanimous-clear-mode-v1","search_id" to id,"created_at" to now(),"status" to "pending","request" to request,"places" to obj("origin" to start.json(),"destination" to stop.json()),"passport_reference" to obj("passport_id" to lineage.getString("passport_id"),"revision" to lineage.getInt("revision"),"fingerprint" to active.getString("fingerprint"),"confirmed_at" to active.optString("confirmedAt")),"confirmed_snapshot" to active,"choices" to JSONArray())
  withContext(Dispatchers.IO){database.saveRecord(record)};routeProfileSnapshot=id to p;historyId=id;result=null;chosenId=null;reflection=null
  try {
   val response=api.call("/api/dyconet/routed-contextual-deliberation",request,180000)
   require(response.optString("schema_version")=="routed-contextual-deliberation-v1"&&response.optString("search_id")==id&&response.optJSONObject("routing")?.optJSONArray("routes")!=null&&response.optJSONArray("route_audit")!=null) { "Invalid route response" }
   require(modes.all { response.optJSONObject("journey_availability")?.opt(it)==available.opt(it) }) { "Availability changed in response" }
   val recommendation=withContext(Dispatchers.Default){recommendation(response)}
   withContext(Dispatchers.IO){record.put("status","complete").put("completed_at",now()).put("result",response).put("recommendation",recommendation.json()).put("d_day_forecast",obj("status" to "queued"));database.saveRecord(record)}
   result=response;activeMode=if(keepTransit)"pt"else if(keepCar)"car"else recommendation.winner?:cards(response).firstOrNull()?.mode?:"walk";selectedId=if(keepTransit)cards(response).firstOrNull {it.mode=="pt"&&it.route.optBoolean("available")}?.id else if(keepCar)cards(response).firstOrNull {it.mode=="car"}?.id else recommendation.primary?.id;routeView="alternatives"
   if(keepTransit&&selectedId==null)error=text("That departure can no longer be planned. Open the stop to refresh its itineraries.","Diese Abfahrt kann nicht mehr geplant werden. Öffne die Haltestelle, um Verbindungen zu aktualisieren.")
   ProspectiveLearning.enqueue(getApplication(),id)
   val recent=JSONArray();(listOf(start,stop)+recents()).distinctBy { it.lat to it.lon }.take(6).forEach { recent.put(it.json()) };withContext(Dispatchers.IO){database.put("cricket.recent-places.v1",recent)};revision++
  }catch(e:Exception){withContext(NonCancellable+Dispatchers.IO){record.put("status","failed").put("completed_at",now()).put("failure",if(e is CancellationException)"cancelled" else e.message);database.saveRecord(record)};throw e}
 }
 fun recents():List<Place> { val raw=store.get("cricket.recent-places.v1")?:return emptyList();return runCatching { val parsed=JSONArray(raw);parsed.objects().mapNotNull(Place::fromJson) }.getOrDefault(emptyList()) }
 fun choose(card:RouteCard,onSelected:((String,Int)->Unit)?=null)=run {
  if(store.json(ACTIVE_PLAYBACK)!=null){openPlayback();return@run}
  require(card.route.optBoolean("available")&&!card.expired()) {"Departure has passed; search again"}
  val id=historyId?:error("Journey not found")
  val selection=withContext(Dispatchers.IO){
   val active=trips.active()
   if(active!=null)return@withContext Triple(active.first.getString("search_id"),-1,active.second.getString("journey_id"))
   val choice=obj("chosen_at" to now(),"route_id" to card.audit?.opt("route_id"),"routing_rank" to card.route.getInt("rank"),"mode_key" to card.route.getString("mode_key"),"summary" to card.route.optJSONObject("summary"),"choice_event_id" to "choice-${UUID.randomUUID()}")
   if(card.route.optString("provider")=="otp")listOf("provider","provider_itinerary_id","transit","legs").forEach {choice.put(it,card.route.opt(it))}
   var index=-1
   database.mutateRecord(id){record->
    require(record.optString("status")=="complete"&&record.optJSONObject("result")?.optJSONObject("routing")?.objects("routes")?.any {it.optInt("rank")==card.route.optInt("rank")&&it.optString("mode_key")==card.route.optString("mode_key")&&it.optBoolean("available")}==true)
    index=record.getJSONArray("choices").length();record.getJSONArray("choices").put(choice)
   }
   Triple(id,index,null)
  }
  if(selection.third!=null){openTrip(selection.first,selection.third);return@run}
  chosenId=card.id;selectedId=card.id;activeMode=card.mode;revision++
  if(onSelected==null)routeView="preview" else onSelected(selection.first,selection.second)
 }
 fun loadReflection(force:Boolean=false){
  narration?.cancel();val version=++narrationVersion;reflection=null;reflectionError=null;reflectionBusy=true
  val id=historyId
  if(id==null){reflectionBusy=false;return}
  val language=locale
  narration=viewModelScope.launch { try {
   val record=withContext(Dispatchers.IO){store.recordForReflection(id)}?:error("Journey not saved");val index=record.getJSONArray("choices").length()-1
   val existing=if(force)null else record.objects("explanations").lastOrNull { it.optInt("choice_index")==index&&it.optString("language")==language }?.optJSONObject("response")
   val retryNarration=existing?.optJSONObject("narration")?.optString("status")=="TEMPLATE_FALLBACK"
   val value=if(existing==null||retryNarration)api.call("/api/dyconet/choice-explanation",obj("record" to record,"choice_index" to index,"confirmed_snapshot" to (store.confirmedSnapshot(record)?:error("The original Passport for this journey is unavailable")),"language" to language),30000)else existing
   require(validReflection(value,record,index,language,getApplication<Application>().assets.open("choiceWordings.json").bufferedReader().use { JSONObject(it.readText()) })) { "Unverified explanation" }
   if(existing==null||retryNarration)withContext(Dispatchers.IO){store.saveExplanation(id,index,language,value)}
   if(version==narrationVersion)reflection=value
  }catch(e:CancellationException){throw e}catch(e:Exception){if(version==narrationVersion)reflectionError=e.message}finally{if(version==narrationVersion)reflectionBusy=false} }
 }
 fun loadCompanionReflection(force:Boolean=false) {
  companionNarration?.cancel();val version=++companionNarrationVersion
  companionReflection=null;companionReflectionFailed=false;companionReflectionBusy=true
  val language=locale
  companionNarration=viewModelScope.launch {try {
   val request=withContext(Dispatchers.IO){companionRequest(store,language)}
   val key="$COMPANION_REFLECTION.$language"
   val cached=if(force)null else withContext(Dispatchers.IO){store.json(key)}?.takeIf {validCompanionReflection(it,request)}
   val response=cached?:api.call("/api/dyconet/companion-reflection",request,30000)
   require(validCompanionReflection(response,request)) {"Unverified companion reflection"}
   if(version==companionNarrationVersion){
    if(cached==null)withContext(Dispatchers.IO){store.put(key,response)}
    companionReflection=response
   }
  }catch(e:CancellationException){throw e}catch(e:Exception){if(version==companionNarrationVersion)companionReflectionFailed=true}
   finally {if(version==companionNarrationVersion)companionReflectionBusy=false}
  }
 }
 fun openHistory(record:JSONObject){if(record.optString("status")!="complete")return;result=record.getJSONObject("result");historyId=record.getString("search_id");routeProfileSnapshot=record.optJSONObject("request")?.optJSONObject("cognitive_passport")?.let {record.getString("search_id") to it};origin=Place.fromJson(record.getJSONObject("places").getJSONObject("origin"));destination=Place.fromJson(record.getJSONObject("places").getJSONObject("destination"));val choice=record.objects("choices").lastOrNull();chosenId=choice?.let { "${it.optInt("routing_rank")}-${it.optString("mode_key")}" };selectedId=chosenId;activeMode=cards(result).find { it.id==chosenId }?.mode?:cards(result).firstOrNull()?.mode?:"walk";tab="journey";routeView=if(choice==null)"alternatives" else "preview"}
 fun saveSettings(value:JSONObject){val max=value.optInt("maxWalk",1500);require(max in 0..10000);store.put(SETTINGS,value);revision++}
 fun openTrip(searchId:String,journeyId:String?=null){tripSearchId=searchId;tripId=journeyId;tab="journey";routeView="trip"}
 var pendingGpsStart:Pair<String,Int>?=null
 var noGpsJourney by mutableStateOf<Pair<String,Int>?>(null)
 fun startWithoutGps(searchId:String,index:Int){
  runCatching {
   require(trips.active()==null){text("Finish the active journey first","Beende zuerst die aktive Reise")}
   val existing=store.json(ACTIVE_PLAYBACK)
   if(existing==null)store.put(ACTIVE_PLAYBACK,JourneyPlaybackClock.create(searchId,index,android.os.SystemClock.elapsedRealtime(),trips.boot()))
   TrackingService.action(getApplication(),TrackingService.PLAYBACK)
   openPlayback()
  }.onFailure {error=it.message}
 }
 fun openPlayback(){val p=store.json(ACTIVE_PLAYBACK)?:return;noGpsJourney=p.getString("search_id") to p.getInt("choice_index");tab="journey";routeView="without_gps";tripStartingSearchId=null;error=null}
 fun cancelPlayback(){TrackingService.action(getApplication(),TrackingService.CANCEL);noGpsJourney=null;routeView="alternatives"}
 fun finishPlayback(){val p=store.json(ACTIVE_PLAYBACK)?:return;TrackingService.action(getApplication(),TrackingService.FINISH);noGpsJourney=null;manualTrip(p.getString("search_id"),p.getInt("choice_index"))}
 fun openingTracking(searchId:String){tripStartingSearchId=searchId;openTrip(searchId)}
 var experienceSaved by mutableStateOf(false)
 fun returnAfterExperience() {
  narration?.cancel();narrationVersion++
  tripSearchId=null;tripId=null;historyId=null
  result=null;selectedId=null;chosenId=null;reflection=null;reflectionBusy=false;reflectionError=null
  notice=null;error=null;tab="journey";routeView="search"
  experienceSaved=true;revision++
 }
 fun clearJourneyHistory(){runCatching {store.clearHistory();revision++;result=null;historyId=null;tripSearchId=null;tripId=null;notice=text("History cleared","Verlauf gelöscht")}.onFailure {error=it.message}}
 fun resetLocalData(){runCatching {store.reset();refresh();tab="home";result=null;historyId=null;tripSearchId=null;tripId=null}.onFailure {error=it.message}}
 fun manualTrip(searchId:String,index:Int){runCatching {val trip=trips.start(searchId,index,false);openTrip(searchId,trip.getString("journey_id"))}.onFailure {error=it.message}}
 fun validateTrip(record:JSONObject,trip:JSONObject)=run {
  val fingerprint=canonical(trip);val minimized=record.copyJson();minimized.remove("journeys");minimized.remove("explanations")
  val receipt=api.call("/api/dyconet/journeys/validate",obj("record" to minimized,"journey" to trip),30000)
  require(receipt.optString("schema_version")=="cricket-journey-validation-v1"&&receipt.optString("journey_id")==trip.getString("journey_id")&&receipt.optString("status")=="valid"&&receipt.opt("learning_applied")==false&&receipt.opt("stored_on_server")==false&&receipt.optString("evidence_id").startsWith("journey-evidence-"))
  store.put("imiq.experimental.journey-validation.v1.${trip.getString("journey_id")}",obj("fingerprint" to fingerprint,"receipt" to receipt));revision++;notice=text("Journey contract validated","Reisevertrag validiert")
 }
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
