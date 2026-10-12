package de.ovgu.imiq.cricket

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.json.JSONObject

@Composable fun JourneyScreen(model:CricketModel,onLocate:()->Unit,onTrackStart:(String,Int)->Unit) {
 if(model.routeView=="without_gps"){WithoutGpsJourneyScreen(model);return}
 if(model.routeView=="trip"){TripScreen(model,onTrackStart);return}
 if(model.result!=null&&model.routeView in listOf("alternatives","map","preview")){RouteExplorerScreen(model,onTrackStart);return}
 val result=model.result
 Column(Modifier.fillMaxSize()) {
  if(model.routeView!="search")Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
   RouteIcon("search",model.text("Search","Suche"),model.routeView=="search"){model.routeView="search"}
   if(result!=null)RouteIcon("map",model.text("Map and routes","Karte und Wege")){model.routeView="alternatives"}
   if(model.chosenId!=null)RouteIcon("home","Companion"){model.tab="passport"}
  }
  if(model.busy)Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){CircularProgressIndicator(Modifier.size(24.dp));Text(model.text("Searching…","Suche läuft…"),Modifier.weight(1f));TextButton({model.cancel()}){Text(model.text("Cancel","Abbrechen"))}}
  when(model.routeView){
   "trip"->TripScreen(model,onTrackStart)
   "map"->NativeJourneyMap(model)
   "alternatives"->AlternativesScreen(model)
   "reflection"->ReflectionScreen(model)
   else->SearchScreen(model,onLocate)
  }
 }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SearchScreen(model:CricketModel,onLocate:()->Unit) {
 val keyboard=LocalSoftwareKeyboardController.current
 val focus=LocalFocusManager.current
 val originDraft=rememberSaveable {mutableStateOf(model.origin?.label.orEmpty())}
 val destinationDraft=rememberSaveable {mutableStateOf(model.destination?.label.orEmpty())}
 var targetOrigin by rememberSaveable {mutableStateOf(model.origin==null)}
 var editor by rememberSaveable {mutableStateOf<Boolean?>(null)}
 var details by rememberSaveable {mutableStateOf<String?>(null)}
 fun closeEditor(){keyboard?.hide();focus.clearFocus();editor=null}
 Box(Modifier.fillMaxSize()) {
  TravelPlanningMap(model,originDraft,targetOrigin,{closeEditor();targetOrigin=it},0,Modifier.fillMaxSize(),showSelector=false)
  Surface(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(8.dp),color=Panel.copy(alpha=.97f),shape=MaterialTheme.shapes.large,shadowElevation=4.dp) {
   CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp) {
   Column(Modifier.padding(horizontal=4.dp,vertical=2.dp)) {
    Row(verticalAlignment=Alignment.CenterVertically) {
     Column {
      IconButton({targetOrigin=true;closeEditor()},enabled=!model.busy,modifier=Modifier.size(40.dp).semantics {contentDescription=model.text("Set origin on map","Start auf Karte setzen")}) {TravelActionSprite("origin",Modifier.size(22.dp),highlighted=targetOrigin)}
      IconButton({closeEditor();val a=model.origin;val b=model.destination;model.setPlace(true,b);model.setPlace(false,a);originDraft.value=b?.label.orEmpty();destinationDraft.value=a?.label.orEmpty()},enabled=!model.busy,modifier=Modifier.size(40.dp).semantics {contentDescription=model.text("Swap origin and destination","Start und Ziel tauschen")}) {TravelActionSprite("swap",Modifier.size(22.dp))}
      IconButton({targetOrigin=false;closeEditor()},enabled=!model.busy,modifier=Modifier.size(40.dp).semantics {contentDescription=model.text("Set destination on map","Ziel auf Karte setzen")}) {TravelActionSprite("destination",Modifier.size(22.dp),highlighted=!targetOrigin)}
     }
     Column(Modifier.weight(1f)) {
      listOf(true,false).forEach {start->
       val place=if(start)model.origin else model.destination
       val draft=if(start)originDraft else destinationDraft
       Row(verticalAlignment=Alignment.CenterVertically) {
        TextButton({targetOrigin=start;editor=start},enabled=!model.busy,modifier=Modifier.weight(1f).heightIn(min=40.dp)) {
         Text(place?.label?:draft.value.takeIf {it.isNotBlank()}?:model.text(if(start)"Choose origin"else"Choose destination",if(start)"Start wählen"else"Ziel wählen"),maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium,color=Pale,modifier=Modifier.fillMaxWidth())
        }
       }
       if(start)Spacer(Modifier.height(40.dp))
      }
     }
     Column {
      FilledTonalIconButton({closeEditor();model.search()},enabled=model.origin!=null&&model.destination!=null&&!model.busy,modifier=Modifier.size(40.dp).semantics {contentDescription=model.text("Find routes","Wege suchen")}) {
       TravelActionSprite("search",Modifier.size(22.dp))
      }
      IconButton({closeEditor();details="preferences"},enabled=!model.busy,modifier=Modifier.size(40.dp).semantics {contentDescription=model.text("Journey preferences","Reisepräferenzen")}) {TravelActionSprite("preferences",Modifier.size(22.dp))}
      IconButton({closeEditor();details="recent"},enabled=!model.busy,modifier=Modifier.size(40.dp).semantics {contentDescription=model.text("Recent places","Letzte Orte")}) {TravelActionSprite("recent",Modifier.size(22.dp))}
     }
    }

   }
   }
  }
 }
 editor?.let {start->
  ModalBottomSheet(onDismissRequest={closeEditor()},containerColor=Panel,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
   Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
    Row(verticalAlignment=Alignment.CenterVertically){Text(model.text(if(start)"Origin"else"Destination",if(start)"Start"else"Ziel"),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);TextButton({closeEditor()}){Text(model.text("Map","Karte"))}}
    AddressField(model,if(start)model.origin else model.destination,model.text(if(start)"A · Origin"else"B · Destination",if(start)"A · Start"else"B · Ziel"),{place->model.setPlace(start,place);if(place!=null){closeEditor();if(start)targetOrigin=false}},if(start)originDraft else destinationDraft,showSelectionHint=false,autoFocus=true)
   }
  }
 }
 details?.let {kind->ModalBottomSheet(onDismissRequest={details=null},containerColor=Panel,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
   Text(model.text(if(kind=="recent")"Recent places"else"Journey preferences",if(kind=="recent")"Letzte Orte"else"Reisepräferenzen"),style=MaterialTheme.typography.titleMedium)
   if(kind=="recent") {
    val recent=model.recents()
    if(recent.isEmpty())Copy(model.text("No recent places yet.","Noch keine letzten Orte."))
    recent.forEach {place->Text(place.label,style=MaterialTheme.typography.bodyMedium);Row {TextButton({model.setPlace(true,place);details=null;targetOrigin=false},Modifier.weight(1f)){Text(model.text("Origin","Start"))};TextButton({model.setPlace(false,place);details=null},Modifier.weight(1f)){Text(model.text("Destination","Ziel"))}}}
    if(recent.isNotEmpty())TextButton({model.store.remove("cricket.recent-places.v1");model.revision++}){Text(model.text("Clear recent places","Letzte Orte löschen"))}
   }else {
    Copy("${model.settings().optInt("maxWalk",1500)} m · "+model.text("walking limit to public transport","Gehstrecke zum ÖPNV"))
    val availability=model.settings().optJSONObject("journeyAvailability")?:model.profile()?.optJSONObject("profile")?.optJSONObject("availability")?:model.availability()
    Row(horizontalArrangement=Arrangement.spacedBy(14.dp)){modes.filter {availability.optBoolean(it)}.forEach {PixelIcon(it,Modifier.size(24.dp))}}
    TextButton({details=null;model.tab="settings"}){Text(model.text("Edit preferences","Präferenzen bearbeiten"))}
   }
  }
 }}
}
@Composable fun AddressField(model:CricketModel,selected:Place?,label:String,onChoose:(Place?)->Unit,draft:MutableState<String>?=null,onFocused:()->Unit={},showSelectionHint:Boolean=true,autoFocus:Boolean=false) {
 val fieldFocus=remember {FocusRequester()}
 val queryState=draft?:remember {mutableStateOf(selected?.label.orEmpty())}
 var query by queryState
 var results by remember { mutableStateOf(emptyList<Place>()) }
 var loading by remember { mutableStateOf(false) }
 var failure by remember { mutableStateOf<String?>(null) }
 var searched by remember {mutableStateOf(false)}
 var searchRevision by remember {mutableIntStateOf(0)}
 val keyboard=LocalSoftwareKeyboardController.current
 LaunchedEffect(autoFocus){if(autoFocus){fieldFocus.requestFocus();keyboard?.show()}}
 LaunchedEffect(selected){if(selected!=null){query=selected.label;results=emptyList();searched=false}}
 LaunchedEffect(query,model.locale,searchRevision){
  results=emptyList();failure=null;searched=false;loading=false
  if(query.trim().length<3||query==selected?.label)return@LaunchedEffect
  delay(650);loading=true
  try {results=model.findPlaces(query,model.origin);searched=true}catch(e:CancellationException){throw e}catch(e:Exception){failure=model.text("Address search is unavailable. Check the backend connection.","Ortssuche nicht erreichbar. Backend-Verbindung prüfen.")}finally{loading=false}
 }
 Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
  OutlinedTextField(query,{onFocused();query=it;onChoose(null)},Modifier.fillMaxWidth().focusRequester(fieldFocus).onFocusChanged {if(it.isFocused)onFocused()},label={Text(label)},singleLine=true,enabled=!model.busy,trailingIcon={Row(verticalAlignment=Alignment.CenterVertically){
   if(query.isNotEmpty())IconButton(onClick={query="";onChoose(null);results=emptyList();failure=null;searched=false;loading=false},enabled=!model.busy,modifier=Modifier.semantics {contentDescription=model.text("Clear address","Adresse löschen")}){Text("×",color=MutedText)}
   IconButton(onClick={searchRevision++},enabled=query.trim().length>=3&&!loading&&!model.busy,modifier=Modifier.semantics {contentDescription=model.text("Search address","Adresse suchen")}){if(loading)CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp)else PixelIcon("search",Modifier.size(18.dp))}
  }})
  if(loading)Copy(model.text("Loading suggestions…","Vorschläge werden geladen…"),Gold)
  failure?.let {Copy(it,MaterialTheme.colorScheme.error)}
  if(searched&&results.isEmpty())Copy(model.text("No matching address. Add the city or check the house number.","Keine passende Adresse. Stadt ergänzen oder Hausnummer prüfen."))
  results.forEach { place->PixelButton(place.label+if(place.house.isNotEmpty())" ✓"else"",{onChoose(place);query=place.label;results=emptyList();keyboard?.hide()},Modifier.fillMaxWidth()) }
  if(showSelectionHint&&selected!=null)Copy(if(selected.source=="map_selection")model.text("Map point selected; nearby address is approximate","Kartenpunkt gewählt; Adresse in der Nähe ist ungefähr")else if(selected.source=="geolocation")model.text("Current location selected","Aktueller Standort gewählt")else if(selected.house.isNotEmpty())model.text("Address with house number selected","Adresse mit Hausnummer gewählt")else model.text("Place selected; exact house number not confirmed","Ort gewählt; genaue Hausnummer nicht bestätigt"),Mint)
 }
}
@Composable fun AlternativesScreen(model:CricketModel) {
 val result=model.result?:return
 var time by remember {mutableLongStateOf(System.currentTimeMillis())}
 LaunchedEffect(result){while(true){delay(15000);time=System.currentTimeMillis()}}
 val all=cards(result);val groups=all.groupBy { it.mode };val recommendation=recommendation(result)
 var selectedDepartures by remember(result){mutableStateOf(mapOf<String,String>())}
 val candidates=groups[model.activeMode].orEmpty();val available=candidates.filter { it.route.optBoolean("available") }.ifEmpty { candidates }
 val sorted=if(model.activeMode=="pt")sortedCards(available,time)else available
 val current=sorted.find { it.id==model.selectedId }?:sorted.find { it.id==selectedDepartures[model.activeMode] }?:sorted.firstOrNull()
 Column(Modifier.fillMaxSize().padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
  LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(14.dp)) {
   item {PixelCard {Heading(recommendationHeading(recommendation,model.de));Copy(recommendationMessage(recommendation,model.de))}}
   item {ProviderNotices(model,result)}
   if(current!=null)item {RouteCardUi(model,current,recommendation,time)}
   else item {PixelCard {Copy(model.text("No alternatives found. Try another destination or refresh the search.","Keine Alternativen gefunden. Anderes Ziel wählen oder Suche erneuern."));PixelButton(model.text("Change journey","Reise ändern"),{model.routeView="search"})}}
   if(model.activeMode=="pt"&&sorted.size>1)item {PixelCard {Disclosure(model.text("More connections and departures","Weitere Verbindungen und Abfahrten")){sorted.groupBy(::departurePattern).forEach { (pattern,departures)->Heading(pattern);departures.forEach { card->val transit=card.route.optJSONObject("transit");val summary=card.route.optJSONObject("summary");PixelButton(model.text("Leave ","Start ")+clock(transit?.number("start_time_ms"))+" · "+model.text("Board ","Einstieg ")+clock(boarding(card))+" → "+clock(transit?.number("end_time_ms"))+"\n${routeDurationLabel(card,time,model.de)} · ${summary?.optInt("transfers")} "+model.text("transfers","Umstiege")+(if(card.expired(time))" · "+model.text("Expired","Vorbei")else""),{selectedDepartures=selectedDepartures+(card.mode to card.id);model.selectedId=card.id},Modifier.fillMaxWidth(),primary=current?.id==card.id)}}}}}
   item {Spacer(Modifier.height(12.dp))}
  }
 }
}
@Composable fun ProviderNotices(model:CricketModel,result:JSONObject) {
 val audit=result.optJSONObject("routing")?.optJSONObject("provider_audit")
 if(audit?.optJSONObject("imiq-routing")?.optString("status")=="UNAVAILABLE")Copy(model.text("The usual route service is unavailable. Alternative providers supply the available journeys.","Der übliche Routendienst ist nicht erreichbar. Alternative Anbieter liefern die verfügbaren Wege."),Gold)
 if(audit?.optJSONObject("otp")?.optJSONObject("later_departures")?.optString("status")=="UNAVAILABLE")Copy(model.text("Later departures could not be loaded.","Weitere Abfahrten konnten nicht geladen werden."),Gold)
 val availability=result.optJSONObject("journey_availability")
 modes.filter { availability?.optBoolean(it)==true&&cards(result).none { c->c.mode==it&&c.route.optBoolean("available") } }.forEach { mode->Copy(model.text("No matching ${modeName(mode,false).lowercase()} route was returned.","Für ${modeName(mode,true)} wurde kein passender Weg zurückgegeben.")) }
 val otp=audit?.optJSONObject("otp")
 if(cards(result).none {it.mode=="pt"&&it.route.optBoolean("available")}) {
  val rejected=otp?.objects("rejected_itineraries").orEmpty()
  val walking=rejected.filter {it.optString("reason")=="walking_limit_exceeded"}.mapNotNull {it.number("walk_distance_meters")}
  when {
   availability?.opt("pt")==false->Copy(model.text("Public transport is disabled for this search. Enable it in Menu.","ÖPNV ist für diese Suche deaktiviert. Im Menü aktivieren."))
   otp?.optString("status")=="DISABLED"->Copy(model.text("Public transport is not configured in the backend.","ÖPNV ist im Backend nicht eingerichtet."))
   otp?.optString("status")=="UNAVAILABLE"->Copy(model.text("Public transport is unavailable right now. Other routes remain visible.","ÖPNV ist gerade nicht erreichbar. Andere Wege bleiben sichtbar."),Gold)
   walking.isNotEmpty()->{val limit=(kotlin.math.ceil(walking.min()/50)*50).toInt();Copy(model.text("The returned transit options exceed your walking limit; they need at least ${kotlin.math.ceil(walking.min()).toInt()} m of walking.","Die gefundenen Verbindungen überschreiten dein Gehlimit; sie benötigen mindestens ${kotlin.math.ceil(walking.min()).toInt()} m zu Fuß."));if(limit in 0..10000)PixelButton(model.text("Retry with $limit m walking","Mit $limit m Gehstrecke wiederholen"),{model.saveSettings(model.settings().put("maxWalk",limit));model.search()})}
   rejected.any {it.optString("reason")=="walking_unavailable"}->Copy(model.text("These transit options require walking. Walking is disabled in Menu.","Diese ÖPNV-Verbindungen benötigen Fußwege. Gehen ist im Menü deaktiviert."))
   otp?.optString("status")=="NO_MATCHING_ITINERARIES"->Copy(model.text("No matching transit itinerary for these places and departure time.","Keine passende ÖPNV-Verbindung für diese Orte und Abfahrtszeit."))
  }
 }
}
@Composable fun RouteCardUi(model:CricketModel,card:RouteCard,recommendation:Recommendation,time:Long) {
 val route=card.route;val summary=route.optJSONObject("summary");val highlight=recommendation.state=="clear"&&recommendation.primary?.id==card.id&&!card.expired(time)
 val passport=routePassport(model)
 PixelCard(modifier=if(highlight)Modifier.recommendationGlow(model.settings().optString("motion")=="full")else Modifier,highlight=highlight) {
  Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){PixelIcon(card.mode);Heading(modeName(card.mode,model.de))}
  if(highlight)Copy(model.text("My favourite for your profile","Mein Favorit für dein Profil"),Mint)
  RouteProfileIcons(model,card,passport,compact=true)
  RouteGoalIndicators(model,card,passport,Modifier.fillMaxWidth())
  if(model.chosenId==card.id)Copy(model.text("✓ Chosen and saved","✓ Gewählt und gespeichert"),Mint)
  Heading("${routeDurationLabel(card,time,model.de)} · ${metric(summary?.number("distance_meters")?.div(1000),2)} km")
  carWalkingSeconds(card)?.let {Copy(model.text("Includes ${countdownMinutes(it)} min walking","Davon ${countdownMinutes(it)} Min. zu Fuß"),Mint)}
  originAccessGap(card)?.let {gap->Copy(model.text("The mapped start is ${metric(gap,0)} m from your selected origin (geographic distance). The initial path and its time are unknown and excluded from the totals; arrival time is unknown.","Der kartierte Start liegt ${metric(gap,0)} m vom gewählten Startort entfernt (geografischer Abstand). Anfangsweg und Dauer sind unbekannt und nicht in den Summen enthalten; die Ankunftszeit ist unbekannt."),Gold)}
  card.route.optJSONObject("summary")?.optJSONObject("parking_assumption")?.let {parking->
   listOf("origin","destination").forEach {role->val p=parking.optJSONObject(role);val name=p?.optString("name")?.takeIf {it.isNotBlank()}?:model.text("Mapped parking","Kartierter Parkplatz");Copy((if(role=="origin")model.text("Parking at origin: ","Parken am Start: ")else model.text("Parking at destination: ","Parken am Ziel: "))+name+" · "+metric(p?.number("geographic_distance_m"),0)+" m",Gold)}
   Copy(model.text("Parking is proposed from OpenStreetMap, not your known vehicle location or confirmed free spaces. Walking access is included in mapped totals. Tap a P on the map to change parking and recalculate.","Parken wird anhand von OpenStreetMap vorgeschlagen. Autostandort und freie Plätze sind nicht bestätigt. Fußwege sind in den kartierten Summen enthalten. Tippe auf ein P, um Parken zu ändern und neu zu berechnen."))
  }
  destinationAccessGap(card)?.let {gap->Copy(model.text("The mapped access is ${metric(gap,0)} m from your selected destination (geographic distance). The final path and its time are unknown; they are not included in the totals.","Der kartierte Zugang liegt ${metric(gap,0)} m vom gewählten Ziel entfernt (geografischer Abstand). Letzter Weg und Dauer sind unbekannt und nicht in den Summen enthalten."),Gold)}
  DepartureDelayBadge(model,largestDepartureDelay(route))
  if(route.optString("provider")=="otp")TransitDetails(model,card,time)
  if(route.optString("provider")=="graphhopper")Copy(model.text("Independently calculated route; time and distance are estimates.","Unabhängig berechneter Weg; Zeit und Strecke sind Schätzungen."))
  if(!route.optBoolean("available"))Copy(model.text("Unavailable with your declared travel kit.","Mit deinen angegebenen Verkehrsmitteln nicht verfügbar."))
  else {
   TravelScene(model.companion().optString("appearance","robot"),card.mode,model.settings().optString("motion")=="full")
   PixelButton(model.text("Choose this journey","Diesen Weg wählen"),{model.choose(card)},Modifier.fillMaxWidth(),enabled=!card.expired(time)&&!model.busy,primary=true)
   if(mapRoutes(model.result).any { it.id==card.id })PixelButton(model.text("View on map","Auf Karte ansehen"),{model.selectedId=card.id;model.routeView="map"},Modifier.fillMaxWidth())
   Disclosure(model.text("Why this assessment?","Warum diese Einordnung?")) {
    Copy(model.text("The model combines saved goal importance, goal–mode expectations, declared usual feelings and available modes. Its recommendation is experimental; it does not establish how you will feel or what you will achieve.","Das Modell verbindet gespeicherte Zielwichtigkeit, Ziel-Modus-Erwartungen, angegebene übliche Gefühle und verfügbare Verkehrsmittel. Die Empfehlung ist experimentell; sie belegt weder dein künftiges Gefühl noch die Zielerfüllung."))
    Copy(if(highlight)model.text("This mode leads every available context assessment. Different journeys of the same mode are not ranked against each other by this rule.","Dieser Modus führt in allen verfügbaren Kontextauswertungen. Verschiedene Wege desselben Modus werden durch diese Regel nicht gegeneinander bewertet.")else model.text("Ties, differing results or missing assessments do not establish a recommendation.","Gleichstände, unterschiedliche Ergebnisse oder fehlende Auswertungen ergeben keine Empfehlung."))
    if(card.audit?.optJSONObject("geometry_evidence")?.opt("route_identity_verified")==false)Copy(model.text("Environmental context belongs to a supplemental path whose identity with the original route is unverified.","Der Umweltkontext gehört zu einem ergänzenden Weg, dessen Identität mit der ursprünglichen Route nicht bestätigt ist."),Gold)
    Disclosure(model.text("Simulation details","Simulationsdetails")) {
     modes.forEach { mode->val baseline=model.result?.optJSONObject("contextual_deliberation")?.optJSONObject("baseline")?.optJSONObject("final_action_activations")?.number(mode);val activation=card.candidate?.optJSONObject("hotco")?.optJSONObject("final_action_activations")?.number(mode);Copy("${modeName(mode,model.de)}: ${metric(baseline,3)} → ${metric(activation,3)}") }
     Copy(model.text("Model activations are not measured choice probabilities. Missing data remain unknown.","Modellaktivierungen sind keine gemessenen Wahlwahrscheinlichkeiten. Fehlende Daten bleiben unbekannt."))
     observations(card).forEach { observation->Copy("${observation.optString("variable")}: ${metric(observation.number("numeric_value"))} ${observation.optString("unit")}\n${observation.optString("source_entity_id")} · ${observation.optString("timestamp")}") }
    }
   }
  }
 }
}
@Composable fun TransitDetails(model:CricketModel,card:RouteCard,time:Long) {
 val route=card.route;val summary=route.optJSONObject("summary");val transit=route.optJSONObject("transit");val boarding=route.objects("legs").find { it.optString("mode")=="pt" }?.optJSONObject("transit")
 val timing=transitTiming(card,time)
 if(timing!=null)Copy(model.text("${countdownMinutes(timing.beforeDepartureSeconds)} min until leaving · ${metric(summary?.number("duration_seconds")?.div(60),0)} min journey","${countdownMinutes(timing.beforeDepartureSeconds)} min bis zum Start · ${metric(summary?.number("duration_seconds")?.div(60),0)} min Reisezeit"),Mint)
 else if(!card.expired(time))Copy(model.text("Time from now is unavailable: departure or arrival timing is missing.","Zeit ab jetzt nicht verfügbar: Start- oder Ankunftszeit fehlt."),MutedText)
 Copy(model.text("Leave by ","Start spätestens ")+clock(transit?.number("start_time_ms"))+" · "+model.text("Board ","Einstieg ")+clock(boarding?.number("start_time_ms"))+"\n"+boarding?.optJSONObject("from")?.optString("name").orEmpty(),Gold)
 if(card.expired(time))Copy(model.text("This departure has passed. Refresh your search.","Diese Startzeit ist vorbei. Suche aktualisieren."),MaterialTheme.colorScheme.error)
 Copy("${clock(transit?.number("start_time_ms"))} → ${clock(transit?.number("end_time_ms"))}\n${summary?.optInt("transfers")} "+model.text("transfers","Umstiege")+" · ${metric(summary?.number("walk_distance_meters"),0)} m · ${metric(summary?.number("waiting_seconds")?.div(60))} min "+model.text("waiting within the journey","Wartezeit innerhalb der Reise"))
 Copy(model.text("Journey time includes walking to and from stops and waits between legs.","Die Reisezeit enthält Fußwege zu und von Haltestellen sowie Wartezeiten zwischen Abschnitten."),MutedText)
 Disclosure(model.text("Journey legs","Reiseabschnitte")) {
  route.objects("legs").forEach { leg->
   val details=leg.optJSONObject("transit");val mode=modelMode(leg.optString("mode"))
   Row(verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
    PixelIcon(if(mode=="walk")"walk"else"pt")
    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
     Heading((if(mode=="walk")model.text("Walk","Zu Fuß")else details?.optString("source_mode").orEmpty())+" "+details?.optString("line").orEmpty())
     if(mode=="pt")DepartureDelayBadge(model,details)
     Copy("${details?.optJSONObject("from")?.optString("name")?:"—"} → ${details?.optJSONObject("to")?.optString("name")?:"—"}")
     Copy("${clock(details?.number("start_time_ms"))} → ${clock(details?.number("end_time_ms"))} · ${metric(leg.number("duration_seconds")?.div(60))} min")
     if(mode=="pt") {
      val realtime=details?.optBoolean("realtime")==true
      val delay=if(realtime)details?.number("departure_delay_seconds") else null
      Copy((if(realtime)"Live"else model.text("Scheduled","Fahrplan"))+" · "+when {
       delay==null->model.text("No live prediction","Keine Echtzeitprognose")
       delay==0.0->model.text("Predicted on time","Laut Prognose pünktlich")
       delay<0->"${metric(-delay/60)} min "+model.text("early","früher")
       else->model.text("Delayed","Verspätet")
      },Mint)
     }
    }
   }
  }
  Copy(model.text("Dashed transit paths can be approximate. Scheduled and live times are distinguished.","Gestrichelte ÖPNV-Verläufe können ungefähr sein. Fahrplan und Echtzeit werden unterschieden."))
 }
 Disclosure(model.text("Schedule and source","Fahrplan und Quelle")){route.objects("legs").filter { it.optString("mode")=="pt" }.forEach { leg->val t=leg.optJSONObject("transit");Copy("${t?.optString("line")} · ${model.text("Scheduled departure","Geplante Abfahrt")}: ${clock(t?.number("scheduled_start_time_ms"))}") };Copy("GTFS.de · MVB · OpenStreetMap contributors · CC BY-SA 4.0")}
}
@Composable fun WeatherUi(model:CricketModel,card:RouteCard?) {
 val variables=listOf("temperature" to model.text("Temperature","Temperatur"),"humidity" to model.text("Humidity","Feuchtigkeit"),"rain" to model.text("Rain","Regen"),"windSpeed" to model.text("Wind","Wind"),"windGust" to model.text("Gusts","Böen"),"lightIntensity" to model.text("Light","Licht"))
 val all=weatherDisplayReadings(observations(card)).filter { it.optString("status")=="OBSERVED"&&it.number("numeric_value")!=null&&it.optJSONObject("source_metadata")?.opt("normalization_eligible")!=false&&it.optString("unit").isNotEmpty() }
 Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){ContextSprite("weather",model.settings().optString("motion")=="full");Heading(model.text("Orion · Journey weather","Orion · Reisewetter"))}
 variables.chunked(2).forEach { pair->Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){pair.forEach { (key,label)->Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){val readings=all.filter { it.optString("variable")==key };val units=readings.map { it.optString("unit") }.distinct();Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){ContextSprite(key,model.settings().optString("motion")=="full"&&readings.isNotEmpty(),Modifier.size(24.dp));Text(label,style=MaterialTheme.typography.bodySmall,color=MutedText)};if(readings.isEmpty()||units.size!=1)Text(model.text("Unknown","Unbekannt"),style=MaterialTheme.typography.bodySmall)else{val values=readings.mapNotNull { it.number("numeric_value") };Text((if(values.min()==values.max())metric(values.first())else "${metric(values.min())}–${metric(values.max())}")+" "+units.first(),color=Mint)}}}}
  }
 displayedRain(all)?.let {rain->rainKnownTime(rain,model.de).takeIf {it.isNotEmpty()}?.let {Copy(it,MutedText)}}
 if(all.isEmpty())Copy(when(card?.candidate?.optJSONObject("source_status")?.optString("weather")) {
  "UNAVAILABLE"->model.text("Orion weather is unavailable.","Orion-Wetter ist nicht erreichbar.")
  "EMPTY"->model.text("Orion returned no weather readings here.","Orion hat hier keine Wettermessungen geliefert.")
  "MALFORMED","OBSERVED"->model.text("Weather readings could not be used.","Wettermessungen konnten nicht verwendet werden.")
  else->model.text("Weather data are unknown for this journey.","Wetterdaten sind für diese Reise unbekannt.")
 })
 else Copy("Orion · "+(model.historyId?.let {model.store.record(it)?.optJSONObject("request")?.optString("datetime")}.orEmpty()),MutedText)
}
@Composable fun ReflectionScreen(model:CricketModel) = CompanionScreen(model)
