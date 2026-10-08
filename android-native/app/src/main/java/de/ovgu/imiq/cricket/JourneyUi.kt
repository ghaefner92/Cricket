package de.ovgu.imiq.cricket

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.json.JSONObject

@Composable fun JourneyScreen(model:CricketModel,onLocate:()->Unit) {
 val result=model.result
 Column(Modifier.fillMaxSize()) {
  Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
   val views=if(result==null)listOf("search")else if(model.chosenId!=null)listOf("search","alternatives","map","reflection")else listOf("search","alternatives","map")
   views.forEach { view->PixelButton(when(view){"search"->model.text("Search","Suche");"alternatives"->model.text("Routes","Wege");"map"->model.text("Map","Karte");else->model.text("Choice","Wahl")},{model.routeView=view},Modifier.weight(1f),primary=model.routeView==view) }
  }
  if(model.busy)Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){CircularProgressIndicator(Modifier.size(24.dp));Text(model.text("Searching…","Suche läuft…"),Modifier.weight(1f));TextButton({model.cancel()}){Text(model.text("Cancel","Abbrechen"))}}
  when(model.routeView){
   "map"->NativeJourneyMap(model)
   "alternatives"->AlternativesScreen(model)
   "reflection"->ReflectionScreen(model)
   else->SearchScreen(model,onLocate)
  }
 }
}
@Composable fun SearchScreen(model:CricketModel,onLocate:()->Unit) {
 val keyboard=LocalSoftwareKeyboardController.current
 ScreenColumn {
  Heading(model.text("Your next journey","Deine nächste Reise"))
  PixelCard {
   AddressField(model,model.origin,model.text("A · Origin","A · Start"),{model.setPlace(true,it)})
   AddressField(model,model.destination,model.text("B · Destination","B · Ziel"),{model.setPlace(false,it)})
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){PixelButton(model.text("My location","Mein Standort"),onLocate,Modifier.weight(1f),enabled=!model.busy);PixelButton(model.text("Swap","Tauschen"),{val start=model.origin;val stop=model.destination;model.setPlace(true,stop);model.setPlace(false,start)},Modifier.weight(1f),enabled=!model.busy)}
   model.origin?.accuracy?.let { Copy(model.text("Location accuracy: about ${it.toInt()} m","Standortgenauigkeit: etwa ${it.toInt()} m")) }
   PixelButton(model.text("Find routes →","Wege suchen →"),{keyboard?.hide();model.search()},Modifier.fillMaxWidth(),enabled=model.origin!=null&&model.destination!=null&&!model.busy,primary=true)
  }
  Disclosure(model.text("Journey preferences","Reisepräferenzen")){Copy("${model.settings().optInt("maxWalk",500)} m · "+model.text("walking limit to public transport","Gehstrecke zum ÖPNV"));Copy(model.text("Adjust walking distance and available transport in Menu.","Gehstrecke und verfügbare Verkehrsmittel im Menü anpassen."));PixelButton(model.text("Open Menu","Menü öffnen"),{model.tab="settings"})}
  val recent=model.recents();if(recent.isNotEmpty())PixelCard {Heading(model.text("Recent places","Letzte Orte"));recent.forEach { place->Copy(place.label);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){PixelButton(model.text("Origin","Start"),{model.setPlace(true,place)},Modifier.weight(1f));PixelButton(model.text("Destination","Ziel"),{model.setPlace(false,place)},Modifier.weight(1f))}};PixelButton(model.text("Clear recent places","Letzte Orte löschen"),{model.store.remove("cricket.recent-places.v1");model.revision++})}
 }
}
@Composable fun AddressField(model:CricketModel,selected:Place?,label:String,onChoose:(Place?)->Unit) {
 var query by remember { mutableStateOf(selected?.label.orEmpty()) }
 var results by remember { mutableStateOf(emptyList<Place>()) }
 var loading by remember { mutableStateOf(false) }
 var failure by remember { mutableStateOf<String?>(null) }
 var searched by remember {mutableStateOf(false)}
 var searchRevision by remember {mutableIntStateOf(0)}
 val keyboard=LocalSoftwareKeyboardController.current
 LaunchedEffect(selected){if(selected!=null){query=selected.label;results=emptyList();searched=false}}
 LaunchedEffect(query,model.locale,searchRevision){
  results=emptyList();failure=null;searched=false;loading=false
  if(query.trim().length<3||query==selected?.label)return@LaunchedEffect
  delay(650);loading=true
  try {results=model.findPlaces(query,model.origin);searched=true}catch(e:CancellationException){throw e}catch(e:Exception){failure=model.text("Address search is unavailable. Check the backend connection.","Ortssuche nicht erreichbar. Backend-Verbindung prüfen.")}finally{loading=false}
 }
 Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
  OutlinedTextField(query,{query=it;onChoose(null)},Modifier.fillMaxWidth(),label={Text(label)},singleLine=true,enabled=!model.busy,trailingIcon={TextButton({searchRevision++},enabled=query.trim().length>=3&&!loading){Text(if(loading)"…"else"⌕")}})
  if(loading)Copy(model.text("Loading suggestions…","Vorschläge werden geladen…"),Gold)
  failure?.let {Copy(it,MaterialTheme.colorScheme.error)}
  if(searched&&results.isEmpty())Copy(model.text("No matching address. Add the city or check the house number.","Keine passende Adresse. Stadt ergänzen oder Hausnummer prüfen."))
  results.forEach { place->PixelButton(place.label+if(place.house.isNotEmpty())" ✓"else"",{onChoose(place);query=place.label;results=emptyList();keyboard?.hide()},Modifier.fillMaxWidth()) }
  if(selected!=null)Copy(if(selected.source=="geolocation")model.text("Current location selected","Aktueller Standort gewählt")else if(selected.house.isNotEmpty())model.text("Address with house number selected","Adresse mit Hausnummer gewählt")else model.text("Place selected; exact house number not confirmed","Ort gewählt; genaue Hausnummer nicht bestätigt"),Mint)
 }
}
@Composable fun AlternativesScreen(model:CricketModel) {
 val result=model.result?:return
 var time by remember {mutableLongStateOf(System.currentTimeMillis())}
 LaunchedEffect(result){while(true){delay(15000);time=System.currentTimeMillis()}}
 val all=cards(result);val groups=all.groupBy { it.mode };val recommendation=recommendation(result)
 var selectedDepartures by remember(result){mutableStateOf(mapOf<String,String>())}
 val candidates=groups[model.activeMode].orEmpty();val available=candidates.filter { it.route.optBoolean("available") }.ifEmpty { candidates }
 val sorted=if(model.activeMode=="pt")sortedCards(available)else available
 val current=sorted.find { it.id==model.selectedId }?:sorted.find { it.id==selectedDepartures[model.activeMode] }?:sorted.firstOrNull()
 Column(Modifier.fillMaxSize().padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
  if(groups.isNotEmpty())groups.keys.toList().chunked(2).forEach { row->Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){row.forEach { mode->PixelButton(modeName(mode,model.de),{model.activeMode=mode},Modifier.weight(1f),primary=model.activeMode==mode)}}}
  LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(14.dp)) {
   item {PixelCard {Heading(when(recommendation.state){"clear"->model.text("An option that fits your profile","Eine Möglichkeit, die zu dir passt");"similar"->model.text("Some options are closely matched","Einige Möglichkeiten liegen nah beieinander");"disagreement"->model.text("No shared favourite","Kein gemeinsamer Favorit");else->model.text("Explore available journeys","Verfügbare Wege entdecken")});Copy(if(recommendation.state=="clear")model.text("Across the available context checks, the model favours ${modeName(recommendation.winner!!,false)}. You can still compare freely.","In den verfügbaren Kontextvergleichen bevorzugt das Modell ${modeName(recommendation.winner,true)}. Du kannst frei vergleichen.")else model.text("The model does not establish a clear favourite here. Compare the journeys and decide what matters for this trip.","Das Modell zeigt hier keinen eindeutigen Favoriten. Vergleiche die Wege und entscheide, was dir auf dieser Reise wichtig ist."))}}
   item {ProviderNotices(model,result)}
   if(current!=null)item {RouteCardUi(model,current,recommendation,time)}
   else item {PixelCard {Copy(model.text("No alternatives found. Try another destination or refresh the search.","Keine Alternativen gefunden. Anderes Ziel wählen oder Suche erneuern."));PixelButton(model.text("Change journey","Reise ändern"),{model.routeView="search"})}}
   if(model.activeMode=="pt"&&sorted.size>1)item {PixelCard {Disclosure(model.text("More connections and departures","Weitere Verbindungen und Abfahrten")){sorted.groupBy(::departurePattern).forEach { (pattern,departures)->Heading(pattern);departures.forEach { card->val transit=card.route.optJSONObject("transit");val summary=card.route.optJSONObject("summary");PixelButton(model.text("Leave ","Start ")+clock(transit?.number("start_time_ms"))+" · "+model.text("Board ","Einstieg ")+clock(boarding(card))+" → "+clock(transit?.number("end_time_ms"))+"\n${metric(summary?.number("duration_seconds")?.div(60),0)} min · ${summary?.optInt("transfers")} "+model.text("transfers","Umstiege")+(if(card.expired(time))" · "+model.text("Expired","Vorbei")else""),{selectedDepartures=selectedDepartures+(card.mode to card.id);model.selectedId=card.id},Modifier.fillMaxWidth(),primary=current?.id==card.id)}}}}}
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
 val route=card.route;val summary=route.optJSONObject("summary");val highlight=recommendation.state=="clear"&&recommendation.winner==card.mode
 val passport=model.historyId?.let {model.store.record(it)?.optJSONObject("request")?.optJSONObject("cognitive_passport")}?:model.profile()
 PixelCard(highlight=highlight) {
  Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){PixelIcon(card.mode);Heading(modeName(card.mode,model.de))}
  if(highlight)Copy(model.text("Fits your profile in this simulation","Passt in dieser Simulation zu deinem Profil"),Mint)
  if(model.chosenId==card.id)Copy(model.text("✓ Chosen and saved","✓ Gewählt und gespeichert"),Mint)
  Heading("${metric(summary?.number("duration_seconds")?.div(60))} min · ${metric(summary?.number("distance_meters")?.div(1000),2)} km")
  if(route.optString("provider")=="otp")TransitDetails(model,card,time)
  if(route.optString("provider")=="graphhopper")Copy(model.text("Independently calculated route; time and distance are estimates.","Unabhängig berechneter Weg; Zeit und Strecke sind Schätzungen."))
  if(!route.optBoolean("available"))Copy(model.text("Unavailable with your declared travel kit.","Mit deinen angegebenen Verkehrsmitteln nicht verfügbar."))
  else {
   TravelScene(model.companion().optString("appearance","robot"),card.mode,model.settings().optString("motion")=="full")
   val needs=passport?.optJSONObject("profile")?.optJSONObject("needs");val beliefs=passport?.optJSONObject("profile")?.optJSONObject("beliefs")?.optJSONObject(card.mode)
   val links=needKeys.filter { (needs?.number(it)?:0.0)>0&&(beliefs?.number(it)?:0.0)>0 }.sortedByDescending { needs?.number(it) }.take(3)
   if(links.isNotEmpty()){Heading(model.text("Connections in your profile","Verbindungen in deinem Profil"));links.forEach { Copy("◆ "+needName(it,model.de),Mint) }}
   WeatherUi(model,card)
   PixelButton(model.text("Choose this journey","Diesen Weg wählen"),{model.choose(card)},Modifier.fillMaxWidth(),enabled=!card.expired(time)&&!model.busy,primary=true)
   if(mapRoutes(model.result).any { it.id==card.id })PixelButton(model.text("View on map","Auf Karte ansehen"),{model.selectedId=card.id;model.routeView="map"},Modifier.fillMaxWidth())
   Disclosure(model.text("Why this assessment?","Warum diese Einordnung?")) {
    Copy(model.text("The model uses your weekly priorities, goal–mode connections, feelings and available transport. Profile links describe your beliefs; they are not isolated causes of the recommendation.","Das Modell nutzt Wochenprioritäten, Ziel-Modus-Verbindungen, Gefühle und verfügbare Verkehrsmittel. Die Profilverbindungen beschreiben deine Überzeugungen; sie sind keine isolierten Ursachen der Empfehlung."))
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
 Copy(model.text("Leave by ","Start spätestens ")+clock(transit?.number("start_time_ms"))+" · "+model.text("Board ","Einstieg ")+clock(boarding?.number("start_time_ms"))+"\n"+boarding?.optJSONObject("from")?.optString("name").orEmpty(),Gold)
 if(card.expired(time))Copy(model.text("This departure has passed. Refresh your search.","Diese Startzeit ist vorbei. Suche aktualisieren."),MaterialTheme.colorScheme.error)
 Copy("${clock(transit?.number("start_time_ms"))} → ${clock(transit?.number("end_time_ms"))}\n${summary?.optInt("transfers")} "+model.text("transfers","Umstiege")+" · ${metric(summary?.number("walk_distance_meters"),0)} m · ${metric(summary?.number("waiting_seconds")?.div(60))} min "+model.text("waiting","Wartezeit"))
 Disclosure(model.text("Journey legs","Reiseabschnitte")) {
  route.objects("legs").forEach { leg->val details=leg.optJSONObject("transit");val mode=modelMode(leg.optString("mode"));Row(verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(10.dp)){PixelIcon(if(mode=="walk")"walk"else"pt");Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){Heading((if(mode=="walk")model.text("Walk","Zu Fuß")else details?.optString("source_mode").orEmpty())+" "+details?.optString("line").orEmpty());Copy("${details?.optJSONObject("from")?.optString("name")?:"—"} → ${details?.optJSONObject("to")?.optString("name")?:"—"}");Copy("${clock(details?.number("start_time_ms"))} → ${clock(details?.number("end_time_ms"))} · ${metric(leg.number("duration_seconds")?.div(60))} min");if(mode=="pt"){val delay=details?.number("departure_delay_seconds");Copy((if(details?.optBoolean("realtime")==true)"Live"else model.text("Scheduled","Fahrplan"))+" · "+if(delay==null)model.text("No live prediction","Keine Echtzeitprognose")else "${metric(delay/60)} min",Mint)}}}
  }
  Copy(model.text("Dashed transit paths can be approximate. Scheduled and live times are distinguished.","Gestrichelte ÖPNV-Verläufe können ungefähr sein. Fahrplan und Echtzeit werden unterschieden."))
 }
 Disclosure(model.text("Schedule and source","Fahrplan und Quelle")){route.objects("legs").filter { it.optString("mode")=="pt" }.forEach { leg->val t=leg.optJSONObject("transit");Copy("${t?.optString("line")} · ${model.text("Scheduled departure","Geplante Abfahrt")}: ${clock(t?.number("scheduled_start_time_ms"))}") };Copy("GTFS.de · MVB · OpenStreetMap contributors · CC BY-SA 4.0")}
}
@Composable fun WeatherUi(model:CricketModel,card:RouteCard?) {
 val variables=listOf("temperature" to model.text("Temperature","Temperatur"),"humidity" to model.text("Humidity","Feuchtigkeit"),"rain" to model.text("Rain","Regen"),"windSpeed" to model.text("Wind","Wind"),"windGust" to model.text("Gusts","Böen"),"uvIndex" to "UV","lightIntensity" to model.text("Light","Licht"))
 val all=observations(card).filter { it.optString("status")=="OBSERVED"&&it.number("numeric_value")!=null&&it.optJSONObject("source_metadata")?.opt("normalization_eligible")!=false&&it.optString("unit").isNotEmpty() }
 Heading(model.text("Weather at search time","Wetter zur Suchzeit"))
 variables.chunked(2).forEach { pair->Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){pair.forEach { (key,label)->Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){val readings=all.filter { it.optString("variable")==key };val units=readings.map { it.optString("unit") }.distinct();Text(label,style=MaterialTheme.typography.bodySmall,color=Edge);if(readings.isEmpty()||units.size!=1)Text(model.text("Unknown","Unbekannt"),style=MaterialTheme.typography.bodySmall)else{val values=readings.mapNotNull { it.number("numeric_value") };Text((if(values.min()==values.max())metric(values.first())else "${metric(values.min())}–${metric(values.max())}")+" "+units.first(),color=Mint)}}}}
  }
 if(all.isEmpty())Copy(when(card?.candidate?.optJSONObject("source_status")?.optString("weather")) {
  "UNAVAILABLE"->model.text("Orion weather is unavailable.","Orion-Wetter ist nicht erreichbar.")
  "EMPTY"->model.text("Orion returned no weather readings here.","Orion hat hier keine Wettermessungen geliefert.")
  "MALFORMED","OBSERVED"->model.text("Weather readings could not be used.","Wettermessungen konnten nicht verwendet werden.")
  else->model.text("Weather data are unknown for this journey.","Wetterdaten sind für diese Reise unbekannt.")
 })
 else Copy("Orion · "+(model.historyId?.let {model.store.record(it)?.optJSONObject("request")?.optString("datetime")}.orEmpty()),Edge)
}
@Composable fun ReflectionScreen(model:CricketModel) {
 val context=LocalContext.current;val wording=remember {context.assets.open("choiceWordings.json").bufferedReader().use {JSONObject(it.readText())}}
 val allRows=dialogueRows(model.reflection,wording);var page by remember(model.reflection){mutableIntStateOf(0)};var details by remember(model.reflection){mutableStateOf(false)};var readAll by remember(model.reflection){mutableStateOf(false)}
 val visible=if(details)allRows else allRows.filter { it.section!="model_detail" }
 val pages=buildList<List<DialogueRow>>{var rows=mutableListOf<DialogueRow>();var count=0;visible.forEach { row->if(rows.isNotEmpty()&&count+row.text.length>850){add(rows);rows=mutableListOf();count=0};rows.add(row);count+=row.text.length};if(rows.isNotEmpty())add(rows)}
 val rows=if(readAll)visible else pages.getOrNull(page).orEmpty();var revealed by remember(rows){mutableIntStateOf(0)}
 val animation=model.settings().optString("motion")=="full"
 val textLength=rows.sumOf { it.text.codePointCount(0,it.text.length) }
 LaunchedEffect(rows,animation){revealed=if(animation)0 else textLength;if(animation)while(revealed<textLength){delay(16);revealed=(revealed+3).coerceAtMost(textLength)}}
 val card=cards(model.result).find {it.id==model.chosenId}
 ScreenColumn {
  PixelCard {Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){CompanionSprite(model.companion().optString("appearance","robot"),Modifier.size(104.dp),animated=animation,talking=revealed<textLength,portrait=true);Column(Modifier.weight(1f)){Heading(model.companion().optString("name"));Copy(model.text("✓ Choice saved","✓ Wahl gespeichert"),Mint);if(card!=null)Copy(modeName(card.mode,model.de))}}
   if(card!=null)Disclosure(model.text("Journey weather","Reisewetter")){WeatherUi(model,card)}
   when {model.reflectionBusy->{CircularProgressIndicator();Copy(model.text("Let me look at your choice…","Ich schaue mir deine Wahl an…"))};model.reflectionError!=null->{Copy(model.text("Your choice is saved. I could not load the explanation just now.","Deine Wahl ist gespeichert. Die Erklärung konnte gerade nicht geladen werden."));PixelButton(model.text("Try again","Erneut versuchen"),{model.loadReflection(true)})};else->{var offset=0;rows.forEach { row->val length=row.text.codePointCount(0,row.text.length);val chars=(revealed-offset).coerceIn(0,length);val fragment=row.text.substring(0,row.text.offsetByCodePoints(0,chars));offset+=length;if(chars>0)Text(fragment,modifier=Modifier.clearAndSetSemantics { text=AnnotatedString(row.text) },color=when(row.section){"affinities"->Mint;"tensions"->Gold;else->Pale})};if(revealed<textLength)PixelButton(model.text("Show text","Text anzeigen"),{revealed=textLength});if(!readAll&&page<pages.lastIndex)PixelButton(model.text("Continue →","Weiter →"),{page++},Modifier.fillMaxWidth(),primary=true);if(!readAll&&page>0)PixelButton(model.text("← Back","← Zurück"),{page--});if(pages.size>1){Copy("${page+1}/${pages.size}",Edge);PixelButton(if(readAll)model.text("Read in pages","Seitenweise lesen")else model.text("Read all","Alles lesen"),{readAll=!readAll;page=0})};if(allRows.any {it.section=="model_detail"})PixelButton(if(details)model.text("Brief explanation","Kurze Erklärung")else model.text("Simulation details","Simulationsdetails"),{details=!details;page=0});Copy(model.text("Verified wording based on your saved journey.","Verifizierte Formulierungen auf Basis deiner gespeicherten Reise."),Edge);PixelButton(model.text("Refresh story","Neu erzählen"),{model.loadReflection(true)})}}
  }
  PixelButton(model.text("Keep comparing","Weiter vergleichen"),{model.routeView="alternatives"},Modifier.fillMaxWidth())
 }
}
