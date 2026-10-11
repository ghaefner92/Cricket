package de.ovgu.imiq.cricket

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

/** Small artwork, full touch targets and spoken labels. */
@Composable fun RouteIcon(kind:String,label:String,active:Boolean=false,enabled:Boolean=true,recommended:Boolean=false,animated:Boolean=false,onClick:()->Unit) {
 val mode=kind in listOf("walk","bike","car","pt")
 val upper=if(recommended)Color(0xFF665336)else if(active)Color(0xFF355448)else PanelRaised
 Surface(modifier=if(recommended)Modifier.recommendationGlow(animated,circular=mode)else Modifier,color=if(active)Mint.copy(alpha=.18f)else Panel,shape=if(mode)CircleShape else MaterialTheme.shapes.medium,shadowElevation=if(mode)4.dp else 0.dp,border=if(mode)BorderStroke(1.dp,if(recommended)Gold.copy(alpha=.7f)else if(active)Mint.copy(alpha=.7f)else Edge.copy(alpha=.6f))else null) {
  Box(if(mode)Modifier.background(Brush.verticalGradient(listOf(upper,Panel,Ink)))else Modifier) {
   IconButton(onClick,Modifier.size(48.dp).semantics {contentDescription=label;selected=active},enabled=enabled) {PixelIcon(kind,Modifier.size(24.dp))}
   if(recommended)PixelIcon("sparkle",Modifier.align(Alignment.TopEnd).padding(5.dp).size(10.dp))
  }
 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun RouteExplorerScreen(model:CricketModel,onTrackStart:(String,Int)->Unit) {
 val result=model.result?:return
 val preview=model.routeView=="preview"
 val store=model.store
 val historyId=model.historyId
 val previewData by produceState<Pair<JSONObject?,Pair<JSONObject,JSONObject>?>?>(null,preview,historyId,model.revision){
  if(!preview){value=null;return@produceState}
  try {value=withContext(Dispatchers.IO){historyId?.let(store::recordForUi) to model.trips.active()}}
  catch(e:CancellationException){throw e}catch(e:Exception){model.error=e.message}
 }
 var details by remember(result,preview) {mutableStateOf(false)}
 var time by remember {mutableLongStateOf(System.currentTimeMillis())}
 LaunchedEffect(result){while(true){time=System.currentTimeMillis();delay(15000)}}
 val all=remember(result){cards(result)}
 val assessment=remember(result){recommendation(result)}
 val animated=model.settings().optString("motion")=="full"
 val favourite=assessment.primary?.takeIf {assessment.state=="clear"&&!it.expired(time)}
 val chosen=all.find {it.id==model.chosenId}
 val current=if(preview)chosen else all.find {it.id==model.selectedId}?:all.firstOrNull {it.mode==model.activeMode}?:all.firstOrNull()
 LaunchedEffect(current?.id){if(current!=null){model.selectedId=current.id;model.activeMode=current.mode}}
 BackHandler {if(details)details=false else if(preview)model.routeView="alternatives" else model.routeView="search"}
 Column(Modifier.fillMaxSize()) {
  Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=10.dp,vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
   RouteIcon("home",model.text("Home","Start")){model.tab="home"}
   RouteIcon("search",model.text("Change journey","Reise ändern")){model.routeView="search"}
   if(preview)RouteIcon("travel",model.text("Compare routes","Wege vergleichen")){model.routeView="alternatives"}
   else all.map {it.mode}.distinct().forEach {mode->RouteIcon(mode,modeName(mode,model.de)+(if(favourite?.mode==mode)model.text(" · My recommendation"," · Meine Empfehlung")else ""),current?.mode==mode,recommended=favourite?.mode==mode,animated=animated){
    val candidates=all.filter {it.mode==mode};val sorted=if(mode=="pt")sortedCards(candidates,time)else candidates
    val next=sorted.firstOrNull {it.route.optBoolean("available") }?:sorted.firstOrNull()
    if(next!=null){model.selectedId=next.id;model.activeMode=mode}
   }}
   if(preview)RouteIcon("passport",model.text("My thoughts on your choice","Meine Gedanken zu deiner Wahl")){model.routeView="reflection";model.loadReflection()}
  }
  JourneyWeatherStrip(model,all)
  Box(Modifier.weight(1f).fillMaxWidth()) {NativeJourneyMap(model,compact=true,selectedOnly=preview)}
  Surface(modifier=if(current!=null&&current.id==favourite?.id)Modifier.recommendationGlow(animated)else Modifier,color=Panel,tonalElevation=4.dp) {
   Column {
    if(!preview&&current?.mode=="pt")TransitOptionStrip(model,all.filter {it.mode=="pt"},current,time)
    if(current!=null)RouteSummaryPanel(model,current,time,current.id==favourite?.id,onDetails={details=true},simplified=true) {
     if(!preview)RoutePlayButton(model.text("Start journey","Reise starten"),enabled=current.route.optBoolean("available")&&!current.expired(time)&&!model.busy){
      model.choose(current,onSelected=onTrackStart)
     }
     else {
      val record=previewData?.first
      val index=record?.objects("choices")?.lastIndex?:-1
      val trip=record?.objects("journeys")?.lastOrNull {it.optInt("choice_index")==index}
      val active=previewData?.second
      if(trip!=null)RoutePlayButton(model.text("Open saved journey","Gespeicherte Reise öffnen")){model.openTrip(record!!.getString("search_id"),trip.getString("journey_id"))}
      else if(active!=null)RoutePlayButton(model.text("Manage active journey","Aktive Reise verwalten")){model.openTrip(active.first.getString("search_id"),active.second.getString("journey_id"))}
      else {
       var startOptions by remember(record,index){mutableStateOf(false)}
       Box {
        RoutePlayButton(model.text("Journey start options","Reise starten"),enabled=record!=null&&index>=0){startOptions=true}
        DropdownMenu(startOptions,onDismissRequest={startOptions=false}) {
         DropdownMenuItem(text={Text(model.text("Start journey with GPS","Reise mit GPS starten"))},enabled=!current.expired(time),onClick={startOptions=false;if(record!=null)onTrackStart(record.getString("search_id"),index)})
         DropdownMenuItem(text={Text(model.text("Confirm without GPS","Ohne GPS bestätigen"))},onClick={startOptions=false;if(record!=null)model.manualTrip(record.getString("search_id"),index)})
        }
       }
      }
     }
    }else Text(model.text("No routes available","Keine Wege verfügbar"),Modifier.padding(12.dp))
   }
  }
 }
 if(details)ModalBottomSheet(onDismissRequest={details=false},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=Ink) {
  Box(Modifier.fillMaxWidth().fillMaxHeight(.88f)) {AlternativesScreen(model)}
 }
}

/** Arrival ordering is a timetable comparison, independent of HOTCO's choice of mode. */
@Composable fun TransitOptionStrip(model:CricketModel,values:List<RouteCard>,current:RouteCard,time:Long) {
 val options=transitConnectionOptions(values,time)
 if(options.isEmpty())return
 val animated=model.settings().optString("motion")=="full"
 Column(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
  Text(model.text("Connections","Verbindungen"),style=MaterialTheme.typography.labelSmall,color=MutedText)
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
   options.forEachIndexed {i,card->
    val services=transitServices(card)
    val line=services.joinToString(" → ") {(when(it.vehicle){"tram"->"Tram";"bus"->"Bus";"train"->model.text("Train","Bahn");else->"Transit"})+" "+it.line.orEmpty()}
    val arrival=clock(card.route.optJSONObject("transit")?.number("end_time_ms"))
    val walk=boardingAccess(card)?.second?.let {"${countdownMinutes(it)} min "+model.text("walk","zu Fuß")}?:model.text("Walk unknown","Fußweg unbekannt")
    val active=current.id==card.id
    val accent=listOf(Color(0xFF9FE4CE),Color(0xFFC5B8FF),Color(0xFFFFBC88))[i]
    val rank=model.text(listOf("First","Second","Third")[i],listOf("Erste","Zweite","Dritte")[i])
    Surface(Modifier.weight(1f).heightIn(min=72.dp).clickable {model.selectedId=card.id;model.activeMode="pt"}.semantics(mergeDescendants=true) {selected=active;contentDescription="$rank · $line · ${model.text("Arrival","Ankunft")} $arrival · $walk"},shape=RoundedCornerShape(8.dp),color=Panel,border=BorderStroke(if(active)2.dp else 1.dp,accent.copy(alpha=if(active)1f else .55f))) {
     Column(Modifier.background(Brush.verticalGradient(listOf(accent.copy(alpha=if(active).20f else .09f),Panel))).padding(6.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
      Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(3.dp)) {
       TransitRankSprite(i+1,animated,Modifier.size(18.dp))
       Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(2.dp)) {
        services.forEachIndexed {index,service->
         if(index>0)Text("›",fontFamily=FontFamily.SansSerif,fontSize=10.sp,color=accent)
         TransitVehicleIcon(service.vehicle,Modifier.size(12.dp))
         Text(service.line?:"—",fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Bold,fontSize=11.sp,lineHeight=13.sp,color=accent,maxLines=1)
        }
       }
      }
      Text(model.text("Arrival ","Ankunft ")+arrival,fontFamily=FontFamily.SansSerif,fontSize=11.sp,lineHeight=14.sp,color=Pale,maxLines=2)
      Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(3.dp)) {
       PixelIcon("walk",Modifier.size(12.dp))
       val minutes=boardingAccess(card)?.second?.let {"${countdownMinutes(it)} min"}?:"—"
       Text(minutes,fontFamily=FontFamily.SansSerif,fontSize=10.sp,lineHeight=12.sp,color=MutedText,maxLines=1)
      }
     }
    }
   }
  }
 }
}

/** Shared search snapshot, independent of the selected transport mode. */
@Composable fun JourneyWeatherStrip(model:CricketModel,routes:List<RouteCard>,snapshot:org.json.JSONObject?=null,grid:Boolean=false,compactHome:Boolean=false) {
 var expanded by remember(model.result,snapshot) {mutableStateOf<String?>(null)}
 var weatherDetails by remember {mutableStateOf(false)}
 val readings=weatherDisplayReadings(snapshot?.objects("raw_observations")?:routes.flatMap {observations(it)}).filter {
  it.optString("status")=="OBSERVED"&&it.number("numeric_value")!=null&&
  it.optJSONObject("source_metadata")?.opt("normalization_eligible")!=false&&
  !it.isNull("unit")&&it.optString("unit").trim().lowercase() !in listOf("","null","none")
 }.distinctBy {listOf(it.optString("source_entity_id"),it.optString("variable"),it.optString("timestamp"),it.opt("numeric_value"),it.optString("unit"))}
 val variables=listOf("temperature" to model.text("Temperature","Temperatur"),"humidity" to model.text("Humidity","Feuchtigkeit"),"rain" to model.text("Rain","Regen"),"windSpeed" to model.text("Wind","Wind"),"windGust" to model.text("Gusts","Böen"),"lightIntensity" to model.text("Light","Licht"))
 val light=readings.filter {it.optString("variable")=="lightIntensity"}.mapNotNull {it.number("numeric_value")}
 val dark=light.isNotEmpty()&&light.all {it==0.0}
 fun iconFor(key:String)=if(key=="lightIntensity"&&dark)"night"else key
 fun valueFor(key:String):String {
  val values=readings.filter {it.optString("variable")==key}
  val units=values.map {o->
   val unit=o.optString("unit").trim()
   if(key=="temperature"&&(unit.lowercase() in listOf("c","°c","celsius","cel")||unit=="\uFFFDC"&&o.optJSONObject("source_metadata")?.optString("unit_provenance")=="VALIDATED_DEVICE_CONTRACT"))"°C"else unit
  }.distinct()
  val numbers=values.mapNotNull {it.number("numeric_value")}
  if(numbers.isEmpty()||units.size!=1)return "—"
  val digits=if(key in listOf("humidity","uvIndex","lightIntensity"))0 else 1
  val number=if(numbers.min()==numbers.max())metric(numbers.first(),digits)else "${metric(numbers.min(),digits)}–${metric(numbers.max(),digits)}"
  return number+if(key=="uvIndex"&&units.first()=="index")""else " "+units.first()
 }
 val cell:@Composable (Pair<String,String>)->Unit={ (key,label)->
  val value=valueFor(key)
  TextButton(onClick={expanded=key},contentPadding=PaddingValues(horizontal=if(compactHome)4.dp else 6.dp,vertical=4.dp),modifier=Modifier.heightIn(min=48.dp).semantics {contentDescription="$label: ${if(value=="—")model.text("Unknown","Unbekannt")else value}. ${model.text("Show meaning","Bedeutung anzeigen")}"}) {
   if(compactHome)Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(2.dp)) {
    ContextSprite(iconFor(key),model.settings().optString("motion")=="full"&&value!="—",Modifier.size(24.dp))
    Text(value,style=MaterialTheme.typography.bodyMedium,color=if(value=="—")MutedText else Mint)
    Text(label,style=MaterialTheme.typography.labelSmall,color=MutedText)
   }else {
   ContextSprite(iconFor(key),model.settings().optString("motion")=="full"&&value!="—",Modifier.size(if(grid)28.dp else 18.dp))
   Spacer(Modifier.width(if(grid)8.dp else 4.dp))
   Column(horizontalAlignment=Alignment.Start){
    if(grid)Text(label,style=MaterialTheme.typography.labelSmall,color=MutedText)
    Text(value,style=if(grid)MaterialTheme.typography.bodyMedium else MaterialTheme.typography.labelSmall,fontSize=if(grid)androidx.compose.ui.unit.TextUnit.Unspecified else 10.sp,color=if(value=="—")MutedText else Mint,maxLines=1)
    if(key=="rain"&&value!="—") {
     rainKnownTime(displayedRain(readings),model.de).takeIf {it.isNotEmpty()}?.let {stamp->Text(stamp,style=MaterialTheme.typography.labelSmall,color=MutedText)}
    }
   }
   }
  }
 }
 Surface(color=if(grid)Panel else Panel.copy(alpha=.75f)) {
  if(grid)Column(Modifier.fillMaxWidth()) {
   BoxWithConstraints {
    val columns=if(compactHome&&maxWidth>=280.dp&&model.settings().optString("textSize")!="large")3 else 2
    Column {variables.chunked(columns).forEach {row->Row(Modifier.fillMaxWidth()){row.forEach {variable->Box(Modifier.weight(1f)){cell(variable)}};repeat(columns-row.size){Spacer(Modifier.weight(1f))}}}}
   }
   if(compactHome)rainKnownTime(displayedRain(readings),model.de).takeIf {valueFor("rain")!="—"&&it.isNotEmpty()}?.let {Text(model.text("Rain · ","Regen · ")+it,style=MaterialTheme.typography.bodySmall,color=MutedText,modifier=Modifier.padding(top=4.dp))}
  }else Column {
   Row(Modifier.fillMaxWidth().padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically) {
    variables.filter {it.first in listOf("temperature","rain","windSpeed")}.forEach {variable->Box(Modifier.weight(1f)){cell(variable)}}
    IconButton({weatherDetails=!weatherDetails},Modifier.size(40.dp).semantics {contentDescription=model.text(if(weatherDetails)"Hide weather details"else"More weather details",if(weatherDetails)"Wetterdetails ausblenden"else"Weitere Wetterdetails")}) {
     Text(if(weatherDetails)"−"else"+",color=Mint,fontFamily=FontFamily.SansSerif,fontSize=22.sp)
    }
   }
   if(weatherDetails)Row(Modifier.fillMaxWidth().padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically) {
    variables.filter {it.first !in listOf("temperature","rain","windSpeed")}.forEach {variable->Box(Modifier.weight(1f)){cell(variable)}}
   }
  }
 }
 expanded?.let {key->
  val label=variables.first {it.first==key}.second
  val explanation=when(key) {
   "temperature"->model.text("Air temperature, measured in degrees Celsius.","Lufttemperatur, gemessen in Grad Celsius.")
   "humidity"->model.text("Relative humidity: how much moisture the air contains compared with its capacity at this temperature.","Relative Luftfeuchtigkeit: Feuchtigkeit der Luft im Verhältnis zur möglichen Menge bei dieser Temperatur.")
   "rain"->model.text("Winfred rain intensity in mm/h, based on a ten-minute window. The time shown is the last known sensor timestamp.","Winfred-Regenintensität in mm/h, bezogen auf ein Zehn-Minuten-Fenster. Angezeigt wird der letzte bekannte Sensorzeitstempel.")
   "windSpeed"->model.text("Wind speed in metres per second.","Windgeschwindigkeit in Metern pro Sekunde.")
   "windGust"->model.text("Peak wind gusts: brief increases in wind speed, measured in metres per second.","Windböen: kurzzeitige Spitzen der Windgeschwindigkeit, gemessen in Metern pro Sekunde.")
   "uvIndex"->model.text("UV index: an index of ultraviolet radiation intensity, without a physical unit.","UV-Index: ein Index der Stärke ultravioletter Strahlung, ohne physikalische Einheit.")
   else->model.text("Ambient illuminance in lux: how much light reaches the sensor.","Beleuchtungsstärke in Lux: wie viel Licht den Sensor erreicht.")
  }
  val value=valueFor(key)
  val queried=snapshot?.optString("queried_at")?:model.result?.optJSONObject("contextual_deliberation")?.optString("timestamp").orEmpty()
  AlertDialog(onDismissRequest={expanded=null},icon={ContextSprite(iconFor(key),model.settings().optString("motion")=="full")},title={Text(label)},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
   Text(if(value=="—")model.text("No usable reading was returned for this search.","Für diese Suche wurde kein verwendbarer Messwert geliefert.")else value,color=Mint)
   Text(explanation)
   if(key=="rain"&&value!="—") {
    val rain=displayedRain(readings)
    rainKnownTime(rain,model.de).takeIf {it.isNotEmpty()}?.let {Text(it,style=MaterialTheme.typography.bodySmall)}
    rain?.optJSONObject("source_metadata")?.number("reporting_interval_minutes")?.let {interval->
     Text(model.text("Configured reporting interval: ","Eingestelltes Meldeintervall: ")+metric(interval,0)+model.text(" min. Querying again does not trigger a new measurement."," Min. Eine erneute Abfrage löst keine neue Messung aus."),style=MaterialTheme.typography.bodySmall)
    }
   }
   if(key=="lightIntensity"&&dark)Text(model.text("The sensor reports 0 lux. The moon represents darkness at the sensor.","Der Sensor meldet 0 Lux. Der Mond steht für Dunkelheit am Sensor."),style=MaterialTheme.typography.bodySmall)
   if(value.contains("–"))Text(model.text("The range covers the readings available for this journey.","Der Bereich umfasst die verfügbaren Messwerte für diese Reise."),style=MaterialTheme.typography.bodySmall)
   if(queried.isNotBlank())Text(model.text("Conditions queried: ","Bedingungen abgefragt: ")+queried,style=MaterialTheme.typography.bodySmall,color=MutedText)
  }},confirmButton={TextButton({expanded=null}){Text(model.text("Close","Schließen"))}})
 }
}
