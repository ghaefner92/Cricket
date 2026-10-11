package de.ovgu.imiq.cricket

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MapPlacesSettings(model:CricketModel,preferences:MapPoiPreferences,parkingForTransit:Boolean=false,onDismiss:()->Unit) {
 var draft by remember {mutableStateOf(preferences)}
 ModalBottomSheet(onDismissRequest=onDismiss,containerColor=Panel,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
   Heading(model.text("Places on your map","Orte auf deiner Karte"))
   fun label(en:String,de:String)=model.text(en,de)
   @Composable fun toggle(en:String,de:String,value:Boolean,onChange:(Boolean)->Unit){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(label(en,de),Modifier.weight(1f));Switch(value,onCheckedChange=onChange)}}
   toggle(if(parkingForTransit)"Parking in public transport view"else"Parking",if(parkingForTransit)"Parkplätze in ÖPNV-Ansicht"else"Parkplätze",draft.parking){draft=draft.copy(parking=it)}
   toggle("Public transport stops","Haltestellen",draft.stops){draft=draft.copy(stops=it)}
   toggle("Include my GPS position","GPS-Position einbeziehen",draft.gps){draft=draft.copy(gps=it)}
   if(draft.parking)toggle("Include restricted parking","Eingeschränkte Parkplätze zeigen",draft.restricted){draft=draft.copy(restricted=it)}
   Text(label("Search radius","Suchradius")+" · ${draft.radius} m",color=Mint)
   Slider(draft.radius.toFloat(),onValueChange={draft=draft.copy(radius=((it/50).roundToInt()*50).coerceIn(50,2000))},valueRange=50f..2000f,steps=38)
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(100,200,500,1000).forEach {r->FilterChip(draft.radius==r,{draft=draft.copy(radius=r)},label={Text("$r m")})}}
   Text(label("Around origin, destination and an available GPS position. Linked trip boarding and arrival platforms remain visible.","Um Start, Ziel und eine verfügbare GPS-Position. Eindeutig zugeordnete Ein- und Ausstiegsbahnsteige bleiben sichtbar."),style=MaterialTheme.typography.bodySmall,color=MutedText)
   PixelButton(label("Apply","Übernehmen"),{val settings=model.settings().copyJson();val points=settings.optJSONObject("mapPoints")?.copyJson()?:JSONObject();val savedParking=points.optBoolean("parking",true);val changed=draft.json();changed.keys().forEach {key->points.put(key,changed.get(key))};if(parkingForTransit){points.put("parking",savedParking);points.put("ptParking",draft.parking)};settings.put("mapPoints",points);model.store.put("cricket.settings.v1",settings);model.revision++;onDismiss()},Modifier.fillMaxWidth())
  }
 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MobilityPlaceSheet(model:CricketModel,poi:MobilityPoi,origin:Place?,destination:Place?,canPlan:Boolean,onDismiss:()->Unit) {
 var response by remember(poi.id){mutableStateOf<JSONObject?>(null)}
 var failure by remember(poi.id){mutableStateOf<String?>(null)}
 var loading by remember(poi.id){mutableStateOf(false)}
 var reload by remember(poi.id){mutableIntStateOf(0)}
 var offsetMinutes by remember(poi.id){mutableIntStateOf(0)}
 var time by remember {mutableLongStateOf(System.currentTimeMillis())}
 LaunchedEffect(poi.id){while(true){kotlinx.coroutines.delay(15000);time=System.currentTimeMillis()}}
 val stopId=poi.gtfsIds.firstOrNull().orEmpty()
 LaunchedEffect(poi.id,origin,destination,reload,offsetMinutes){
  response=null;failure=null
  if(poi.tags.optBoolean("display_only")||poi.kind!="stop"||stopId.isBlank()||origin==null||destination==null)return@LaunchedEffect
  loading=true
  try {
   val settings=model.settings();val passport=model.passport()?.optJSONObject("response")?.optJSONObject("cognitive_passport")
   val availability=settings.optJSONObject("journeyAvailability")?:passport?.optJSONObject("profile")?.optJSONObject("availability")?:obj("walk" to true,"bike" to false,"car" to false,"pt" to true)
   val departure=java.time.Instant.now().plusSeconds(offsetMinutes*60L).toString()
   response=model.api.call("/api/dyconet/transit/stop-itineraries",obj("boarding_platform_id" to poi.id,"start" to origin.coordinates(),"stop" to destination.coordinates(),"datetime" to departure,"availability" to availability,"max_walk_m" to settings.optInt("maxWalk",1500)),90000)
  }catch(e:CancellationException){throw e}catch(e:Exception){failure=model.text("I couldn't retrieve connections from this stop. Check your walking limit or try again.","Verbindungen ab dieser Haltestelle konnten nicht geladen werden. Prüfe dein Fußweglimit oder versuche es erneut.")}
  finally{loading=false}
 }
 ModalBottomSheet(onDismissRequest=onDismiss,containerColor=Panel,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
  Column(Modifier.fillMaxWidth().fillMaxHeight(.88f).padding(horizontal=16.dp)) {
   Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Heading(poi.name.ifBlank {model.text(if(poi.kind=="parking")"Parking"else"Stop",if(poi.kind=="parking")"Parkplatz"else"Haltestelle")});Text(model.text(if(poi.kind=="parking")"Parking details"else"Connections from this stop",if(poi.kind=="parking")"Parkinformationen"else"Verbindungen ab dieser Haltestelle"),style=MaterialTheme.typography.bodySmall,color=MutedText)};TextButton(onDismiss){Text(model.text("Close","Schließen"))}}
   LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(top=12.dp,bottom=28.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
    if(poi.kind=="parking") {
     item {PixelCard {
      Copy(model.text("Access: ","Zugang: ")+when(poi.access){"yes","permissive"->model.text("Public","Öffentlich");"unknown"->model.text("Not specified","Nicht angegeben");else->model.text("Restricted","Eingeschränkt")})
      Copy(model.text("Fee: ","Gebühr: ")+when(poi.tags.optString("fee")){"yes"->model.text("Paid","Gebührenpflichtig");"no"->model.text("Mapped as free","Als kostenlos kartiert");else->model.text("Not specified","Nicht angegeben")})
      Copy(model.text("Free spaces and your vehicle's location are unconfirmed.","Freie Plätze und der Autostandort sind nicht bestätigt."),MutedText)
     }}
     if(canPlan&&poi.selectable) {
      if(origin!=null&&poiDistance(GeoPoint(origin.lat,origin.lon),poi.point)<=2000)item {PixelButton(model.text("Use at origin · recalculate","Am Start nutzen · neu berechnen"),{onDismiss();model.changeParking(true,poi.id)},Modifier.fillMaxWidth())}
      if(destination!=null&&poiDistance(GeoPoint(destination.lat,destination.lon),poi.point)<=2000)item {PixelButton(model.text("Use at destination · recalculate","Am Ziel nutzen · neu berechnen"),{onDismiss();model.changeParking(false,poi.id)},Modifier.fillMaxWidth())}
     }
     item {Disclosure(model.text("Location and source","Ort und Quelle")){Copy(model.text("OSM parking location; an area center may differ from its entrance. Only mapped paths count in the journey.","OSM-Parkposition; Flächenmitte und Eingang können abweichen. Nur kartierte Wege zählen zur Reise."));Copy("© OpenStreetMap · ODbL")}}
    }else {
     item {PixelCard {Copy(model.text("To: ","Nach: ")+(destination?.label?:model.text("Choose a destination first","Wähle zuerst ein Ziel")));Copy(model.text("Walking access from your origin is included.","Der Fußweg von deinem Startort ist enthalten."),MutedText)}}
     item {Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(0,30,60).forEach {mins->FilterChip(offsetMinutes==mins,{offsetMinutes=mins},label={Text(if(mins==0)model.text("Now","Jetzt")else "+$mins min")})};TextButton({reload++}){Text(model.text("Refresh","Aktualisieren"))}}}
     item {val reference=poi.tags.optString("ref:IFOPT").substringAfterLast("::","");Copy(model.text("Passenger waiting area · OSM platform", "Wartebereich · OSM-Bahnsteig")+(if(reference.isNotBlank())" · $reference"else""),MutedText)}
     if(stopId.isBlank())item {Copy(model.text("This waiting area has no unambiguous timetable link yet. Nearby vehicle stop points are not treated as this platform.","Dieser Wartebereich hat noch keine eindeutige Fahrplanverknüpfung. Nahe Fahrzeug-Haltepunkte gelten nicht als dieser Bahnsteig."),Gold)}
     if(loading)item {Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){CircularProgressIndicator(Modifier.size(22.dp));Copy(model.text("Finding connections…","Verbindungen suchen…"))}}
     failure?.let {message->item {Copy(message,Gold);TextButton({reload++}){Text(model.text("Try again","Erneut versuchen"))}}}
     val itineraries=response?.objects("routes").orEmpty()
     if(response?.optBoolean("incomplete")==true)item {Copy(model.text("Some services could not be checked. Refresh to try again.","Einige Verbindungen konnten nicht geprüft werden. Versuche es mit Aktualisieren erneut."),Gold)}
     if(response!=null&&itineraries.isEmpty())item {Copy(model.text("No matching departures from this exact stop for your destination and walking limit.","Keine passende Abfahrt an genau dieser Haltestelle für dein Ziel und Fußweglimit."))}
     items(itineraries){route->
      val card=RouteCard(route,null,null);val board=route.objects("legs").firstOrNull {it.optString("mode")=="pt"}?.optJSONObject("transit");val transit=route.optJSONObject("transit");val summary=route.optJSONObject("summary")
      PixelCard {
       Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){TransitVehicleIcon(transitServices(card).firstOrNull()?.vehicle?:"pt",Modifier.size(22.dp));Heading(transitServices(card).joinToString(" → "){it.line?:model.text("Transit","ÖPNV")})}
       Copy(clock(board?.number("start_time_ms"))+" → "+clock(transit?.number("end_time_ms")),Mint)
       Copy(model.text("Towards ","Richtung ")+board?.optJSONObject("to")?.optString("name").orEmpty(),MutedText)
       Copy(model.text("Leave ","Start ")+clock(transit?.number("start_time_ms"))+" · "+routeDurationLabel(card,time,model.de)+" · ${summary?.optInt("transfers",0)} "+model.text("transfers","Umstiege"))
       val walk=route.objects("legs").filter {it.optString("mode")=="walk"}.sumOf {it.number("duration_seconds")?:0.0}
       if(walk>0)Copy(model.text("Walk ","Zu Fuß ")+"${countdownMinutes(walk)} min",MutedText)
       Copy(if(transit?.optString("realtime_status")=="REALTIME")model.text("Live updates","Echtzeitdaten")else model.text("Scheduled departure","Fahrplanabfahrt"),MutedText)
       Disclosure(model.text("Stops and journey steps","Haltestellen und Reiseabschnitte")){route.objects("legs").forEach {leg->val t=leg.optJSONObject("transit");Copy((if(leg.optString("mode")=="walk")model.text("Walk","Zu Fuß")else t?.optString("line").orEmpty())+" · "+t?.optJSONObject("from")?.optString("name").orEmpty()+" → "+t?.optJSONObject("to")?.optString("name").orEmpty())}}
       if(canPlan)PixelButton(if(card.expired(time))model.text("Departure passed","Abfahrt vorbei")else model.text("Plan this itinerary","Diese Verbindung planen"),{onDismiss();model.planStopItinerary(poi,route)},Modifier.fillMaxWidth(),enabled=!card.expired(time)&&!model.busy)
      }
     }
     item {Copy("GTFS · MVB · GTFS.de · CC BY-SA 4.0",MutedText)}
    }
   }
  }
 }
}
