package de.ovgu.imiq.cricket

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

@Composable internal fun RoutePlayButton(label:String,enabled:Boolean=true,onClick:()->Unit) {
 FilledIconButton(onClick,Modifier.size(44.dp).semantics {contentDescription=label},enabled=enabled) {
  Canvas(Modifier.size(18.dp)) {
   val play=Path().apply {moveTo(size.width*.2f,size.height*.08f);lineTo(size.width*.9f,size.height*.5f);lineTo(size.width*.2f,size.height*.92f);close()}
   drawPath(play,if(enabled)Ink else MutedText)
  }
 }
}

@Composable internal fun RoutePauseButton(label:String,onClick:()->Unit) {
 FilledIconButton(onClick,Modifier.size(44.dp).semantics {contentDescription=label}) {
  Canvas(Modifier.size(18.dp)) {
   drawLine(Ink,Offset(size.width*.3f,size.height*.15f),Offset(size.width*.3f,size.height*.85f),4.dp.toPx())
   drawLine(Ink,Offset(size.width*.7f,size.height*.15f),Offset(size.width*.7f,size.height*.85f),4.dp.toPx())
  }
 }
}

@Composable internal fun RouteStopButton(label:String,cancelLabel:String,onCancel:(()->Unit)?,onClick:()->Unit) {
 val red=Color(0xFFFF657B)
 Box(Modifier.size(44.dp).clip(CircleShape).background(red.copy(alpha=.15f))
  .combinedClickable(role=Role.Button,onClick=onClick,onLongClick=onCancel,onLongClickLabel=cancelLabel)
  .semantics {contentDescription=label},contentAlignment=Alignment.Center) {
  Canvas(Modifier.size(18.dp)){drawRect(red)}
 }
}

@Composable internal fun TransitVehicleIcon(vehicle:String,modifier:Modifier=Modifier) {
 val resource=when(vehicle){"bus"->R.drawable.ic_transit_bus;"tram"->R.drawable.ic_transit_tram;"train"->R.drawable.ic_transit_train;else->null}
 if(resource!=null)Icon(painterResource(resource),null,modifier,tint=Gold)else PixelIcon("pt",modifier)
}

private fun vehicleName(vehicle:String,de:Boolean)=when(vehicle){"tram"->"Tram";"bus"->"Bus";"train"->if(de)"Bahn"else"Train";else->if(de)"ÖPNV"else"Transit"}

/** Keep short summary values complete even at large system font sizes. */
@Composable private fun SummaryFitText(text:String,size:Float,color:Color,weight:FontWeight=FontWeight.Normal,modifier:Modifier=Modifier) {
 val measurer=rememberTextMeasurer()
 val density=LocalDensity.current
 val style=LocalTextStyle.current.copy(fontSize=size.sp,fontWeight=weight,lineHeight=(size+2).sp)
 BoxWithConstraints(modifier) {
  val available=with(density){maxWidth.toPx()}
  val measured=measurer.measure(text,style=style,maxLines=1,softWrap=false).size.width
  val fitted=(if(measured>available-2&&measured>0)size*(available-2).coerceAtLeast(0f)/measured*.96f else size).coerceAtLeast(7f)
  Text(text,style=style.copy(fontSize=fitted.sp,lineHeight=(fitted+2).sp),color=color,softWrap=false,maxLines=1)
 }
}

@Composable internal fun DepartureDelayBadge(model:CricketModel,transit:JSONObject?) {
 val seconds=liveDepartureDelay(transit)?:return
 val line=transit?.optString("line").orEmpty()
 val description=model.text("Live departure delay","Echtzeit-Abfahrtsverspätung")+" · "+line+" · "+departureDelayLabel(seconds)
 SummaryFitText(departureDelayLabel(seconds),10f,Color(0xFFFF657B),FontWeight.Bold,Modifier.semantics {contentDescription=description})
}

@Composable private fun SummaryIdentity(model:CricketModel,card:RouteCard,time:Long,recommended:Boolean,modifier:Modifier,statusLabel:String?=null,departureOnly:Boolean=false) {
 val services=transitServices(card)
 Row(modifier,verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
  if(!departureOnly||card.mode!="pt")if(card.mode=="pt")TransitVehicleIcon(services.firstOrNull()?.vehicle?:"pt",Modifier.size(22.dp))else PixelIcon(card.mode,Modifier.size(22.dp))
  Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(1.dp)) {
   val identity=if(card.mode=="pt"&&services.isNotEmpty())vehicleName(services.first().vehicle,model.de)+" "+services.take(2).joinToString(" → ") {it.line?:vehicleName(it.vehicle,model.de)}+(if(services.size>2)" +${services.size-2}"else "")else modeName(card.mode,model.de)
   if(!departureOnly||card.mode!="pt")SummaryFitText(identity,12f,if(card.mode=="pt")Gold else Pale,FontWeight.SemiBold)
   val timing=transitTiming(card,time)
   val subtitle=statusLabel?:when {
    card.expired(time)->model.text("Departure passed · search again","Abfahrt vorbei · neu suchen")
    originAccessGap(card)!=null->model.text("Start access · ${metric(originAccessGap(card),0)} m gap; time unknown","Startzugang · ${metric(originAccessGap(card),0)} m Abstand; Zeit unbekannt")
    destinationAccessGap(card)!=null->model.text("Access · ${metric(destinationAccessGap(card),0)} m gap; time unknown","Zugang · ${metric(destinationAccessGap(card),0)} m Abstand; Zeit unbekannt")
    timing!=null&&timing.beforeDepartureSeconds>0->model.text("Leave in ${countdownMinutes(timing.beforeDepartureSeconds)} min","Start in ${countdownMinutes(timing.beforeDepartureSeconds)} min")
    card.mode=="pt"&&timing==null->model.text("Departure unknown","Startzeit unbekannt")
    recommended->model.text("Recommended","Empfohlen")
    else->model.text("Leaving now","Start jetzt")
   }
   if(card.mode=="pt")SummaryFitText(subtitle,if(departureOnly)12f else 9f,if(departureOnly)Pale else if(recommended)Gold else MutedText)
   else Text(subtitle,fontSize=9.sp,lineHeight=11.sp,color=if(recommended)Gold else MutedText,maxLines=2)
   if(card.route.optJSONObject("summary")?.optJSONObject("parking_assumption")!=null)Text(model.text("P · proposed parking","P · vorgeschlagenes Parken"),fontSize=9.sp,lineHeight=11.sp,color=Gold)
   DepartureDelayBadge(model,largestDepartureDelay(card.route))
  }
 }
}

@Composable private fun SummaryMetrics(model:CricketModel,card:RouteCard,time:Long,modifier:Modifier,elapsed:String?=null,startedAt:Long?=null) {
 val summary=card.route.optJSONObject("summary")
 val timing=transitTiming(card,time)
 val duration=timing?.let {countdownMinutes(it.fromNowSeconds).toString()}?:summary?.number("duration_seconds")?.takeIf {it>=0}?.let {countdownMinutes(it).toString()}
 val metres=summary?.number("distance_meters")?.takeIf {it>=0}
 val distance=if(metres==null)"—"else if(metres<1000)"${metric(metres,0)} m"else "${metric(metres/1000,1)} km"
 val accessOnly=summary?.optBoolean("destination_access_time_unknown")==true
 val minimumArrival=carArrivalLowerBoundMs(card,startedAt?:time)
 val labels=listOf(if(elapsed!=null)model.text("Elapsed","Vergangen")else if(minimumArrival!=null)model.text("Known time + access","Bekannte Zeit + Zugang")else if(originAccessGap(card)!=null)model.text("Mapped time","Kartierte Zeit")else if(accessOnly)model.text("Time to access","Zeit bis Zugang")else model.text(if(timing!=null)"From now"else"Travel time",if(timing!=null)"Ab jetzt"else"Fahrzeit"),model.text("Distance","Distanz"),if(minimumArrival!=null)model.text("Arrival no earlier","Ankunft frühestens")else if(accessOnly)model.text("At access ≈","Am Zugang ≈")else model.text("Arrival ≈","Ankunft ≈"))
 val values=listOf(elapsed?:duration?.let {"$it min"+(if(minimumArrival!=null)" +"else"")}?:"—",distance,clock(minimumArrival?:summaryArrivalMs(card,startedAt?:time)))
 Row(modifier,verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
  values.forEachIndexed {index,value->
   Column(Modifier.weight(1f).semantics {contentDescription="${labels[index]}: $value"}) {
    SummaryFitText(labels[index],10f,MutedText)
    SummaryFitText(value,14f,if(index==2)Mint else Pale,FontWeight.SemiBold)
    if(index==0)carWalkingSeconds(card)?.let {seconds->
     val walking=model.text("Walk ${countdownMinutes(seconds)} min","Zu Fuß ${countdownMinutes(seconds)} Min.")
     SummaryFitText(walking,9f,MutedText)
    }
   }
  }
 }
}

/** At most 15% of screen height. Width, rather than height, chooses the layout. */
@Composable internal fun RouteSummaryPanel(model:CricketModel,card:RouteCard,time:Long,recommended:Boolean,onDetails:()->Unit,elapsed:String?=null,controls:Boolean=false,statusLabel:String?=null,startedAt:Long?=null,passportSnapshot:JSONObject?=null,onCancel:(()->Unit)?=null,simplified:Boolean=false,action:@Composable ()->Unit) {
 val height=minOf(LocalConfiguration.current.screenHeightDp*.15f,if(simplified)88f else 112f).dp
 val fontScale=LocalDensity.current.fontScale
 val passport=passportSnapshot?:routePassport(model)
 MaterialTheme(colorScheme=MaterialTheme.colorScheme,shapes=MaterialTheme.shapes,typography=Typography()) {
  val details:@Composable ()->Unit={
   if(controls)RouteStopButton(model.text("Stop journey · Experience","Reise beenden · Erfahrung"),model.text("Cancel journey","Reise abbrechen"),onCancel,onDetails)
   else IconButton(onDetails,Modifier.size(40.dp).semantics {contentDescription=model.text("Route details","Routendetails")}) {
    Canvas(Modifier.size(20.dp)) {
     drawCircle(MutedText,style=androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
     drawCircle(Pale,1.2.dp.toPx(),Offset(size.width*.5f,size.height*.3f))
     drawLine(Pale,Offset(size.width*.5f,size.height*.46f),Offset(size.width*.5f,size.height*.74f),1.8.dp.toPx())
    }
   }
  }
  BoxWithConstraints(Modifier.fillMaxWidth()) {
   val goalWidth=(if(fontScale>1.15f)110 else 100).dp
   if(maxWidth>=640.dp)Row(Modifier.fillMaxWidth().height(height).padding(horizontal=10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
    SummaryIdentity(model,card,time,recommended,Modifier.width(140.dp),statusLabel,departureOnly=simplified)
    if(!simplified)RouteGoalIndicators(model,card,passport,Modifier.width(goalWidth),showLabel=height>=64.dp)
    SummaryMetrics(model,card,time,Modifier.weight(1f),elapsed,startedAt)
    if(!simplified)RouteProfileIcons(model,card,passport=passport,compact=true,modifier=Modifier.width(40.dp),showGoals=false);details();action()
   }else Column(Modifier.fillMaxWidth().height(height).padding(horizontal=10.dp,vertical=2.dp)) {
    Row(Modifier.weight(1.3f).fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically) {
     SummaryIdentity(model,card,time,recommended,Modifier.weight(1f),statusLabel,departureOnly=simplified)
     if(!simplified)RouteGoalIndicators(model,card,passport,Modifier.width(goalWidth),showLabel=height>=104.dp);details();action()
    }
    Row(Modifier.weight(1f).fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
     SummaryMetrics(model,card,time,Modifier.weight(1f),elapsed,startedAt)
     if(!simplified)RouteProfileIcons(model,card,passport=passport,compact=true,modifier=Modifier.width(40.dp),showGoals=false)
    }
   }
  }
 }
}
