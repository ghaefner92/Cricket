package de.ovgu.imiq.cricket

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

enum class JourneyMapStyle(val key:String) {
 Clear("clear"),Night("night"),Mobility("mobility");
 fun title(model:CricketModel)=when(this){Clear->model.text("Light","Hell");Night->model.text("Night","Nacht");Mobility->model.text("Mobility","Mobilität")}
 fun description(model:CricketModel)=when(this) {
  Clear->model.text("Soft colours and clear street labels.","Sanfte Farben und gut lesbare Straßennamen.")
  Night->model.text("A dark map with readable streets and landmarks.","Dunkle Karte mit gut lesbaren Straßen und Orientierungspunkten.")
  Mobility->model.text("Highlights mapped footpaths, cycleways, railways and stops.","Hebt kartierte Fußwege, Radwege, Bahnlinien und Haltestellen hervor.")
 }
 companion object {fun fromKey(key:String?)=entries.firstOrNull {it.key==key}?:Clear}
}

/** One native map instance: changing a style never resets the camera or GPS state. */
@Composable internal fun VectorMapSurface(
 style:JourneyMapStyle,retry:Int,onReady:(MapLibreMap)->Unit,onCameraChanged:()->Unit,
 onGesture:()->Unit,onTap:(LatLng)->Boolean,onLoading:(Boolean)->Unit,onFailure:(Boolean)->Unit,
 modifier:Modifier=Modifier
) {
 val context=LocalContext.current
 val lifecycle=LocalLifecycleOwner.current.lifecycle
 val ready by rememberUpdatedState(onReady)
 val cameraChanged by rememberUpdatedState(onCameraChanged)
 val gesture by rememberUpdatedState(onGesture)
 val tap by rememberUpdatedState(onTap)
 val loading by rememberUpdatedState(onLoading)
 val failure by rememberUpdatedState(onFailure)
 val view=remember(context) {
  MapLibre.getInstance(context.applicationContext)
  MapView(context,MapLibreMapOptions.createFromAttributes(context).textureMode(true)).apply {onCreate(null)}
 }
 var map by remember(view){mutableStateOf<MapLibreMap?>(null)}
 DisposableEffect(view,lifecycle) {
  var started=false;var resumed=false;var destroyed=false
  fun sync() {
   val shouldStart=lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
   val shouldResume=lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
   if(resumed&&!shouldResume){view.onPause();resumed=false}
   if(started&&!shouldStart){view.onStop();started=false}
   if(!started&&shouldStart){view.onStart();started=true}
   if(!resumed&&shouldResume){view.onResume();resumed=true}
  }
  val failed=MapView.OnDidFailLoadingMapListener {if(!destroyed){loading(false);failure(true)}}
  val rendered=MapView.OnDidFinishRenderingMapListener {fully->if(!destroyed&&fully){loading(false);failure(false)}}
  view.addOnDidFailLoadingMapListener(failed)
  view.addOnDidFinishRenderingMapListener(rendered)
  val observer=LifecycleEventObserver {_,_->if(!destroyed)sync()}
  lifecycle.addObserver(observer);sync()
  view.getMapAsync {native->
   if(!destroyed) {
    native.uiSettings.apply {isLogoEnabled=false;isAttributionEnabled=true;isCompassEnabled=true;isTiltGesturesEnabled=false;isScrollGesturesEnabled=true;isZoomGesturesEnabled=true;isDoubleTapGesturesEnabled=true}
    val pixels=context.resources.displayMetrics.density
    native.uiSettings.setCompassMargins(0,(112*pixels).toInt(),(10*pixels).toInt(),0)
    native.setMinZoomPreference(2.0);native.setMaxZoomPreference(19.0)
    native.addOnCameraMoveListener {cameraChanged()}
    native.addOnCameraIdleListener {cameraChanged()}
    native.addOnCameraMoveStartedListener {reason->if(reason==MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE)gesture()}
    native.addOnMapClickListener {tap(it)}
    map=native;ready(native)
   }
  }
  onDispose {
   destroyed=true;lifecycle.removeObserver(observer)
   view.removeOnDidFailLoadingMapListener(failed);view.removeOnDidFinishRenderingMapListener(rendered)
   if(resumed)view.onPause()
   if(started)view.onStop()
   view.onDestroy()
  }
 }
 LaunchedEffect(map,style,retry) {
  val native=map?:return@LaunchedEffect
  loading(true);failure(false)
  val json=withContext(Dispatchers.IO){context.assets.open("maps/companion-${style.key}.json").bufferedReader().use {it.readText()}}
  native.setStyle(Style.Builder().fromJson(json)){cameraChanged()}
 }
 AndroidView(factory={view},modifier=modifier)
}

@Composable internal fun MapLayersIcon(modifier:Modifier=Modifier) {
 Canvas(modifier) {
  val w=size.width;val h=size.height
  for(i in 2 downTo 0) {
   val y=h*(.29f+i*.18f)
   val p=Path().apply {moveTo(w*.12f,y);lineTo(w*.5f,y-h*.21f);lineTo(w*.88f,y);lineTo(w*.5f,y+h*.21f);close()}
   drawPath(p,Panel);drawPath(p,if(i==0)Mint else Pale.copy(alpha=.8f),style=Stroke(w*.065f))
  }
 }
}

@Composable private fun MapStylePreview(style:JourneyMapStyle) {
 Canvas(Modifier.size(70.dp,54.dp)) {
  val dark=style==JourneyMapStyle.Night
  val w=size.width;val h=size.height
  drawRoundRect(if(dark)Color(0xFF19151F)else Color(0xFFF5F1EA),cornerRadius=androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()))
  drawCircle(if(dark)Color(0xFF293C33)else Color(0xFFD9E4CE),w*.2f,Offset(w*.8f,h*.2f))
  drawLine(if(dark)Color(0xFF43677A)else Color(0xFFBBCFD5),Offset(w*.07f,0f),Offset(w*.32f,h),w*.09f)
  drawLine(if(dark)Color(0xFF51485B)else Color.White,Offset(0f,h*.7f),Offset(w,h*.35f),w*.09f)
  drawLine(if(dark)Color(0xFF51485B)else Color.White,Offset(w*.53f,0f),Offset(w*.73f,h),w*.07f)
  if(style==JourneyMapStyle.Mobility) {
   drawLine(Color(0xFF168269),Offset(0f,h*.8f),Offset(w,h*.47f),2.dp.toPx())
   drawLine(Color(0xFF297BAC),Offset(w*.63f,0f),Offset(w*.83f,h),2.dp.toPx())
   drawCircle(Color(0xFF80649C),3.dp.toPx(),Offset(w*.62f,h*.58f))
  }
 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun MapStyleSheet(model:CricketModel,style:JourneyMapStyle,onSelect:(JourneyMapStyle)->Unit,onDismiss:()->Unit) {
 ModalBottomSheet(onDismissRequest=onDismiss,containerColor=Panel,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
  Column(Modifier.fillMaxWidth().padding(horizontal=18.dp).padding(bottom=28.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
   Text(model.text("Your map","Deine Karte"),style=MaterialTheme.typography.titleLarge,color=Pale)
   JourneyMapStyle.entries.forEach {option->
    val selected=style==option
    Surface(shape=MaterialTheme.shapes.large,color=if(selected)PanelRaised else Panel,border=BorderStroke(1.dp,if(selected)Mint else Edge.copy(alpha=.5f))) {
     Row(Modifier.fillMaxWidth().clickable {onSelect(option)}.padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
      MapStylePreview(option)
      Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
       Text(option.title(model),color=if(selected)Mint else Pale,style=MaterialTheme.typography.titleSmall)
       Text(option.description(model),color=MutedText,style=MaterialTheme.typography.bodySmall)
      }
      RadioButton(selected=selected,onClick={onSelect(option)})
     }
    }
   }
   Text(model.text("Your route and GPS tracking stay the same in every view.","Deine Route und GPS-Aufzeichnung bleiben in jeder Ansicht gleich."),style=MaterialTheme.typography.bodySmall,color=MutedText)
  }
 }
}

@Composable internal fun MobilityMapLegend(model:CricketModel,modifier:Modifier=Modifier) {
 Surface(modifier,color=Panel.copy(alpha=.95f),shape=MaterialTheme.shapes.medium) {
  Row(Modifier.padding(horizontal=9.dp,vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(9.dp),verticalAlignment=Alignment.CenterVertically) {
   listOf("walk" to Color(0xFF297BAC),"bike" to Color(0xFF168269),"pt" to Color(0xFF80649C)).forEach {(mode,color)->
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(3.dp)) {
     Canvas(Modifier.size(8.dp)){drawCircle(color)}
     Text(when(mode){"walk"->model.text("Paths","Wege");"bike"->model.text("Cycle","Rad");else->model.text("Rail/stops","Bahn/Stopps")},style=MaterialTheme.typography.labelSmall,color=Pale)
    }
   }
  }
 }
}
