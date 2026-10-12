package de.ovgu.imiq.cricket

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch
import kotlinx.coroutines.TimeoutCancellationException
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.*
import org.json.JSONObject
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.camera.CameraUpdateFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

internal fun locationAccessBlocked(hasPermission:Boolean,servicesEnabled:Boolean)=!hasPermission||!servicesEnabled

private data class ProjectedRoutePath(val route:MapRoute,val path:Path,val offsets:List<Offset>,val mode:String,val dashed:Boolean,val vehicle:String)

@Composable fun NativeJourneyMap(model:CricketModel,tripRecord:JSONObject?=null,trackedTrip:JSONObject?=null,compact:Boolean=false,selectedOnly:Boolean=false,planning:Boolean=false,planningLocating:Boolean=false,onMapPick:((GeoPoint)->Unit)?=null,onPlanningLocate:(()->Unit)?=null,pendingPoint:GeoPoint?=null,playbackFraction:Double?=null) {
 val context=LocalContext.current
 val preferences=remember(model.revision){model.settings()}
 var poiSettingsSheet by remember {mutableStateOf(false)}
 val appearance=remember(model.revision){model.companion().optString("appearance","robot")}
 val locationScope=rememberCoroutineScope()
 val tracking=trackedTrip!=null
 // Search evidence is immutable; GPS/status updates must not rebuild geometry
 // or restart the alignment request for the same saved search.
 val routeKey:Any?=if(planning)"planning"else tripRecord?.optString("search_id")?:model.result
 val result=remember(routeKey){planningMapResult(planning,model.result,tripRecord?.optJSONObject("result"))}
 val apiBase=remember(model.revision){model.api.base()}
 val origin=tripRecord?.optJSONObject("places")?.optJSONObject("origin")?.let(Place::fromJson)?:model.origin
 val destination=tripRecord?.optJSONObject("places")?.optJSONObject("destination")?.let(Place::fromJson)?:model.destination
 var tramAlignments by remember(result){mutableStateOf<Map<String,JSONObject>>(emptyMap())}
 LaunchedEffect(result,apiBase) {
  val legs=tramAlignmentRequest(result)
  if(legs.length()>0)try {
   val response=model.api.call("/api/dyconet/transit/tram-alignment",obj("legs" to legs),30000)
   tramAlignments=response.objects("alignments").associate {"${it.getString("route_id")}/${it.getInt("leg_index")}" to it}
  }catch(e:CancellationException){throw e}catch(_:Exception){}
 }
 val routes=remember(routeKey,tramAlignments){mapRoutes(result,tramAlignments)}
 val selection=trackedTrip?.optJSONObject("selection")
 val selected=if(planning)null else if(tracking)"${selection?.optInt("routing_rank")}-${selection?.optString("mode_key")}"else model.selectedId
 val ptMap=!planning&&(routes.find {it.id==selected}?.mode?:model.activeMode)=="pt"
 val mapPoints=preferences.optJSONObject("mapPoints")
 val poiPreferences=MapPoiPreferences.fromJson(mapPoints).copy(parking=mapParkingVisible(mapPoints,if(ptMap)"pt"else"other",planning))
 var allPois by remember {mutableStateOf<List<MobilityPoi>>(emptyList())}
 LaunchedEffect(context,apiBase){
  val osm=withContext(Dispatchers.IO){MagdeburgMobility.load(context)}
  allPois=osm.filter {it.kind!="stop"||it.waitingPlace}
  try {val catalog=model.api.call("/api/dyconet/transit/stops");allPois=withContext(Dispatchers.Default){platformPois(osm,catalog)}}
  catch(e:CancellationException){throw e}catch(_:Exception){}
 }
 var inspectedPoi by remember(routeKey){mutableStateOf<MobilityPoi?>(null)}
 val shown=remember(routes,tracking,selectedOnly,selected){if(tracking||selectedOnly)routes.filter {it.id==selected}else routes.groupBy {it.mode}.values.mapNotNull {group->group.find {it.id==selected}?:group.firstOrNull()}}
 val routeMotion=preferences.optString("motion")=="full"
 val routePhase=remember {mutableFloatStateOf(0f)}
 val mapLifecycle=LocalLifecycleOwner.current.lifecycle
 var locationBlocked by remember {mutableStateOf(!hasLocationAccess(context)||!locationServicesEnabled(context))}
 LaunchedEffect(mapLifecycle,planningLocating) {
  mapLifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
   while(true){
    val allowed=listOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION).any {context.checkSelfPermission(it)==PackageManager.PERMISSION_GRANTED}
    val enabled=(context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager).isLocationEnabled
    locationBlocked=locationAccessBlocked(allowed,enabled)
    delay(1000)
   }
  }
 }

 LaunchedEffect(routeKey,selected,routeMotion,mapLifecycle) {
  routePhase.floatValue=0f
  if(routeMotion&&selected!=null)mapLifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
   var start=0L;var lastPhase=0L
   while(true)withFrameNanos {now->
    if(start==0L)start=now
    if(now-lastPhase>=33_333_333L){routePhase.floatValue=(((now-start)/1e9*38)%64).toFloat();lastPhase=now}
   }
  }
 }
 val fixes=trackedTrip?.optJSONObject("tracking")?.objects("points").orEmpty()
 val latest=fixes.lastOrNull()
 var mapFix by remember(routeKey){mutableStateOf<JSONObject?>(null)}
 val position=if(planning)model.planningPosition else if((mapFix?.optLong("monotonic_ms")?:-1L)>(latest?.optLong("monotonic_ms")?:-1L))mapFix else latest
 var locationBusy by remember {mutableStateOf(false)}
 var locationError by remember {mutableStateOf<String?>(null)}
 var follow by remember(routeKey) {mutableStateOf(planning)}
 fun locateMap() {
  locationBusy=true;locationError=null
  locationScope.launch {
   try {
    val fix=currentMapLocation(context)
    mapFix=obj("lat" to fix.latitude,"lon" to fix.longitude,"accuracy_m" to fix.accuracy.toDouble(),"monotonic_ms" to fix.elapsedRealtimeNanos/1000000)
    follow=true
   }catch(e:TimeoutCancellationException){locationError=model.text("I couldn't find your position yet. Try outdoors or near a window, then tap again.","Ich konnte deinen Standort noch nicht finden. Versuche es draußen oder am Fenster und tippe erneut.")}
   catch(e:CancellationException){throw e}
   catch(e:Exception){locationError=model.text("I couldn't access your position. Check that location services and location permission are enabled.","Ich konnte deinen Standort nicht abrufen. Prüfe, ob Standortdienste und Standortberechtigung aktiviert sind.")}
   finally{locationBusy=false}
  }
 }
 val locationActions=rememberAndroidLocationAccess(model,onReady={locationBlocked=false;follow=true;if(planning)onPlanningLocate?.invoke()else if(mapFix==null)locateMap()},onUnavailable={locationBusy=false;locationBlocked=!hasLocationAccess(context)||!locationServicesEnabled(context)})
 // Keep the display current even after a manual pan. Camera following is independent.
 LaunchedEffect(mapLifecycle,planning,locationBlocked,routeKey) {
  if(!planning&&!locationBlocked)mapLifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
   try {mapLocations(context).collect {fix->
    mapFix=obj("lat" to fix.latitude,"lon" to fix.longitude,"accuracy_m" to fix.accuracy.toDouble(),"monotonic_ms" to fix.elapsedRealtimeNanos/1000000)
   }}catch(e:CancellationException){throw e}catch(_:Exception){}
  }
 }
 val focus=shown.find {it.id==selected}
 val markerMode=(focus?.mode?:if(tracking)modelMode(selection?.optString("mode_key").orEmpty())else model.activeMode).takeIf {it in listOf("walk","bike","car","pt")}?:"walk"
 val journeyId=trackedTrip?.optString("journey_id")
 val motion=remember(journeyId,markerMode,routeKey,focus){JourneyAvatarMotion(markerMode,focus)}
 val inferredMode=remember(motion,journeyId,fixes.size,latest?.optLong("monotonic_ms")){motion.update(fixes)}
 val movingMode=inferredMode
 // A transit journey starts on foot; the route's overall mode still labels its
 // icon and geometry. Tracking later infers boarding from the existing GPS logic.
 val playback=playbackFraction?.let {routePlaybackPosition(focus,it)}
 val originAvatarMode=if(playback!=null)playback.second else if(tracking)inferredMode else focus?.segments?.firstOrNull()?.mode?:if(markerMode=="pt")"walk"else markerMode
 val spriteAnimated=preferences.optString("motion")=="full"&&(!tracking||trackedTrip?.optString("status")=="recording")
 var avatarPosition by remember(journeyId){mutableStateOf<GeoPoint?>(null)}
 val recording=trackedTrip?.optString("status")=="recording"
 LaunchedEffect(journeyId,latest?.optLong("monotonic_ms"),recording,playback) {
  val target=playback?.first?:routeAvatarTarget(focus,latest?.let {GeoPoint(it.getDouble("lat"),it.getDouble("lon"))},recording)?:return@LaunchedEffect
  val start=avatarPosition
  if(start==null||!spriteAnimated)avatarPosition=target
  else animate(0f,1f,animationSpec=tween(1000,easing=LinearEasing)){fraction,_->avatarPosition=GeoPoint(start.lat+(target.lat-start.lat)*fraction,start.lon+(target.lon-start.lon)*fraction)}
 }
 val points=remember(focus,shown,origin,destination){(focus?.segments?.flatMap {it.points}?:shown.flatMap {r->r.segments.flatMap {it.points}})+listOfNotNull(origin?.let {GeoPoint(it.lat,it.lon)},destination?.let {GeoPoint(it.lat,it.lon)})}
 var nativeMap by remember{mutableStateOf<MapLibreMap?>(null)}
 var cameraRevision by remember{mutableIntStateOf(0)}
 var mapStyle by remember{mutableStateOf(JourneyMapStyle.fromKey(model.store.get("cricket.map-style.v1")))}
 var styleSheet by remember{mutableStateOf(false)}
 var size by remember{mutableStateOf(IntSize.Zero)}
 var retry by remember{mutableIntStateOf(0)}
 var mapFailure by remember{mutableStateOf(false)}
 var mapLoading by remember{mutableStateOf(true)}
 LaunchedEffect(mapLoading,mapStyle,retry){if(mapLoading){delay(25000);if(mapLoading){mapLoading=false;mapFailure=true}}}
 val density=LocalDensity.current
 LaunchedEffect(nativeMap,routeKey,selected,size,if(planning&&origin?.source!="map_selection")origin?.let {GeoPoint(it.lat,it.lon)} else null,if(planning&&destination?.source!="map_selection")destination?.let {GeoPoint(it.lat,it.lon)} else null){
  val map=nativeMap?:return@LaunchedEffect
  if(size.width==0||size.height==0)return@LaunchedEffect
  val locations=points.map {LatLng(it.lat,it.lon)}.distinct()
  when {
   locations.size>1->{val bounds=LatLngBounds.Builder().includes(locations).build();map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds,with(density){40.dp.roundToPx()}));if(map.cameraPosition.zoom>17)map.moveCamera(CameraUpdateFactory.zoomTo(17.0))}
   locations.size==1->map.moveCamera(CameraUpdateFactory.newLatLngZoom(locations.first(),15.0))
   else->map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(52.13,11.63),13.0))
  }
 }
 LaunchedEffect(nativeMap,position?.optLong("monotonic_ms"),follow,size,routeKey,selected){
  val map=nativeMap?:return@LaunchedEffect
  if((follow||planning&&origin==null&&destination==null)&&position!=null&&size.width>0&&size.height>0)map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(position.getDouble("lat"),position.getDouble("lon")),maxOf(map.cameraPosition.zoom,16.0)))
 }
 val projection=remember(nativeMap,cameraRevision){nativeMap?.projection}
 fun screen(point:GeoPoint):Offset {val p=projection?.toScreenLocation(LatLng(point.lat,point.lon));return if(p==null)Offset(-10000f,-10000f)else Offset(p.x,p.y)}
 val routeCards=remember(result){cards(result)}
 val selectedCard=remember(routeCards,selected){routeCards.find {it.id==selected}}
 var routeStops by remember(routeKey,selected){mutableStateOf<JSONObject?>(null)}
 LaunchedEffect(routeKey,selected,apiBase){
  routeStops=null
  val requests=org.json.JSONArray()
  selectedCard?.route?.objects("legs")?.forEachIndexed {i,l->val t=l.optJSONObject("transit");if(l.optString("mode")=="pt"&&t!=null)requests.put(obj("leg_index" to i,"trip_id" to t.optString("gtfs_trip_id"),"from_id" to t.optJSONObject("from")?.optJSONObject("stop")?.optString("gtfsId"),"to_id" to t.optJSONObject("to")?.optJSONObject("stop")?.optString("gtfsId")))}
  if(requests.length()>0)try{routeStops=model.api.call("/api/dyconet/transit/route-stops",obj("legs" to requests),30000)}catch(e:CancellationException){throw e}catch(_:Exception){}
 }
 val activeStops=remember(result,selected,allPois,routeStops){itineraryStopPois(selectedCard,allPois,routeStops)}
 val gpsCenter=position?.let {GeoPoint(it.getDouble("lat"),it.getDouble("lon"))}
 val originStops=remember(allPois,origin,poiPreferences.stops,planning){if(planning)nearestOriginPlatforms(allPois,origin?.let {GeoPoint(it.lat,it.lon)},poiPreferences.stops)else emptyList()}
 val nearbyPois=remember(allPois,poiPreferences,origin,destination,gpsCenter,activeStops,originStops){
  (activeStops+originStops+visibleMobility(allPois,poiPreferences,origin?.let {GeoPoint(it.lat,it.lon)},destination?.let {GeoPoint(it.lat,it.lon)},gpsCenter)).distinctBy {it.id}
 }
 val projectedPois=remember(nearbyPois,nativeMap,cameraRevision,size){nearbyPois.map {it to screen(it.point)}.filter {(_,p)->p.x in -30f..size.width+30f&&p.y in -30f..size.height+30f}}
 val parking=selectedCard?.route?.optJSONObject("summary")?.optJSONObject("parking_assumption")
 val parkingIds=listOfNotNull(parking?.optJSONObject("origin")?.optString("id"),parking?.optJSONObject("destination")?.optString("id"))+activeStops.map {it.id}+originStops.map {it.id}
 val poiClusters=remember(projectedPois,parkingIds,cameraRevision,density){clusterMobility(projectedPois,parkingIds,if((nativeMap?.cameraPosition?.zoom?:0.0)>=17.5)0f else with(density){48.dp.toPx()})}
 val poiPaint=remember {android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {typeface=android.graphics.Typeface.DEFAULT_BOLD;textAlign=android.graphics.Paint.Align.CENTER}}
 val poiLabels=remember(poiClusters,parkingIds,cameraRevision,size,density){
  val occupied=mutableListOf<android.graphics.RectF>()
  poiClusters.sortedByDescending {it.members.first().id in parkingIds}.mapNotNull {cluster->
   val poi=cluster.members.first();val p=cluster.position
   val chosen=poi.id in parkingIds
   val name=cluster.members.map {it.name.ifBlank {it.tags.optString("ref")}}.firstOrNull {it.isNotBlank()}.orEmpty().take(38)
   val text=name+(poi.tags.number("origin_nearby_metres")?.let {" · ${kotlin.math.round(it).toInt()} m "+model.text("straight-line","Luftlinie")}.orEmpty())+when(poi.tags.optString("route_role")){"transfer"->" · "+poi.tags.optString("lines");"final"->model.text(" · Final stop"," · Ausstieg");else->""}
   if(text.isBlank()||!chosen&&(poi.kind!="stop"||(nativeMap?.cameraPosition?.zoom?:0.0)<13))return@mapNotNull null
   val font=with(density){10.sp.toPx()};val r=with(density){(if(chosen)10 else 7).dp.toPx()}
   poiPaint.textSize=font;val half=poiPaint.measureText(text)/2;val y=p.y+r+with(density){12.dp.toPx()}
   val bounds=android.graphics.RectF(p.x-half-4,y-font-4,p.x+half+4,y+4)
   if(!chosen&&occupied.any {android.graphics.RectF.intersects(it,bounds)})return@mapNotNull null
   occupied.add(bounds);Triple(text,p.x,y)
  }
 }
 // Avatar animation and the elapsed clock must not reproject every route point.
 // Rebuild geometry only when the camera, route, selection or fixes change.
 val projectedRoutes=remember(nativeMap,cameraRevision,shown,selected,size){
  shown.sortedBy {it.id==selected}.flatMap {route->route.segments.filter {it.drawable}.map {segment->
   val offsets=segment.points.map(::screen)
   val path=Path().apply {offsets.forEachIndexed {i,p->if(i==0)moveTo(p.x,p.y)else lineTo(p.x,p.y)}}
   ProjectedRoutePath(route,path,offsets,segment.mode,segment.approximate||route.supplemental||route.mode=="pt"&&segment.mode=="walk",segment.transitVehicle)
  }}
 }
 val projectedFixes=remember(nativeMap,cameraRevision,fixes,size){
  fixes.groupBy {it.optInt("segment_index")}.values.map {segment->Path().apply {segment.forEachIndexed {i,fix->val p=screen(GeoPoint(fix.getDouble("lat"),fix.getDouble("lon")));if(i==0)moveTo(p.x,p.y)else lineTo(p.x,p.y)}}}
 }
 fun inspectCluster(cluster:PoiCluster) {
   if(cluster.members.size==1)inspectedPoi=cluster.members.first()
   else {follow=false;val lat=cluster.members.map {it.point.lat}.average();val lon=cluster.members.map {it.point.lon}.average();nativeMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat,lon),minOf(19.0,maxOf(17.5,(nativeMap?.cameraPosition?.zoom?:14.0)+1.5))))}
 }
 fun selectRoute(point:LatLng):Boolean {
  val tap=screen(GeoPoint(point.latitude,point.longitude))
  poiClusters.minByOrNull {(tap-it.position).getDistance()}?.takeIf {(tap-it.position).getDistance()<=with(density){18.dp.toPx()}}?.let {cluster->
   inspectCluster(cluster)
   return true
  }
  if(tracking||selectedOnly)return false
  var nearest:MapRoute?=null;var distance=with(density){20.dp.toPx()}.toDouble()
  projectedRoutes.forEach {segment->segment.offsets.zipWithNext().forEach { (p,q)->val delta=q-p;val denominator=delta.x*delta.x+delta.y*delta.y;val t=if(denominator==0f)0f else (((tap.x-p.x)*delta.x+(tap.y-p.y)*delta.y)/denominator).coerceIn(0f,1f);val d=(tap-(p+delta*t)).getDistance().toDouble();if(d<distance){distance=d;nearest=segment.route} }}
  nearest?.let {model.selectedId=it.id;model.activeMode=it.mode}
  return nearest!=null
 }
 Column(Modifier.fillMaxSize().padding(if(compact)0.dp else 12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
  Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().onSizeChanged {size=it}) {
   VectorMapSurface(mapStyle,retry,onReady={nativeMap=it;if(planning)it.uiSettings.setCompassMargins(0,with(density){178.dp.roundToPx()},with(density){10.dp.roundToPx()},0)},onCameraChanged={cameraRevision++},onGesture={follow=false},onTap={point->if(planning){follow=false;onMapPick?.invoke(GeoPoint(point.latitude,point.longitude));true}else selectRoute(point)},onLoading={mapLoading=it},onFailure={mapFailure=it},modifier=Modifier.fillMaxSize())
   Canvas(Modifier.fillMaxSize()) {
    projectedRoutes.forEach {segment->
     val chosen=segment.route.id==selected
     val color=mapSegmentColor(MapSegment(emptyList(),segment.mode,false,transitVehicle=segment.vehicle))
     val effect=if(segment.dashed)PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(),6.dp.toPx()),0f)else null
     fun stroke(width:Float,pathEffect:PathEffect?=effect)=Stroke(width.dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round,pathEffect=pathEffect)
     if(chosen) {
      drawPath(segment.path,color.copy(alpha=.10f),style=stroke(15f))
      drawPath(segment.path,color.copy(alpha=.24f),style=stroke(9f))
      drawPath(segment.path,Ink.copy(alpha=.65f),style=stroke(5.5f))
      drawPath(segment.path,color,style=stroke(4f))
      if(!routeMotion)drawPath(segment.path,Color.White.copy(alpha=.60f),style=stroke(1.5f))
     }else {
      // Fade casing too: an opaque dark outline would still clutter the map.
      drawPath(segment.path,Ink.copy(alpha=.10f),style=stroke(3.5f))
      drawPath(segment.path,color.copy(alpha=if(selected!=null).19f else .40f),style=stroke(2f))
     }
    }
    projectedFixes.forEach {path->drawPath(path,Color.White,style=Stroke(12f));drawPath(path,Color(0xFF188AE8),style=Stroke(7f))}
   }
   if(routeMotion)Canvas(Modifier.fillMaxSize()) {
    val phase=-routePhase.floatValue.dp.toPx()
    projectedRoutes.filter {it.route.id==selected}.forEach {segment->
     val pattern=if(segment.dashed)floatArrayOf(10.dp.toPx(),6.dp.toPx())else floatArrayOf(18.dp.toPx(),46.dp.toPx())
     drawPath(segment.path,Color.White.copy(alpha=.90f),style=Stroke(1.5.dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round,pathEffect=PathEffect.dashPathEffect(pattern,phase)))
    }
   }
   Canvas(Modifier.fillMaxSize()) {
    poiClusters.forEach {cluster->
     val poi=cluster.members.first();val p=cluster.position
     val chosen=poi.id in parkingIds
     val intermediate=poi.tags.optString("route_role")=="intermediate"
     val transfer=poi.tags.optString("route_role")=="transfer"
     val opacity=if(intermediate).42f else 1f
     val r=(if(chosen)10 else 7).dp.toPx()
     val color=if(poi.kind=="stop")Color(0xFF248056)else if(cluster.members.any {it.selectable})Color(0xFF276CC0)else Color(0xFF78727D)
     if(poi.kind=="stop"){drawCircle((if(chosen&&!intermediate)Gold else Color.White).copy(alpha=opacity),r+2.dp.toPx(),p);drawCircle(color.copy(alpha=opacity),r,p)}
     else {drawRoundRect(if(chosen)Gold else Color.White,Offset(p.x-r-1.dp.toPx(),p.y-r-1.dp.toPx()),androidx.compose.ui.geometry.Size(2*r+2.dp.toPx(),2*r+2.dp.toPx()),androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()));drawRoundRect(color,Offset(p.x-r,p.y-r),androidx.compose.ui.geometry.Size(2*r,2*r),androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))}
     if(transfer){
      val u=2.dp.toPx()
      for(sign in listOf(-1,1)){val y=p.y+sign*2.5f*u;drawLine(Pale,Offset(p.x-3*u,y),Offset(p.x+3*u,y),u,StrokeCap.Square)
       val tip=p.x+sign*3*u;drawLine(Pale,Offset(tip-sign*2*u,y-2*u),Offset(tip,y),u,StrokeCap.Square);drawLine(Pale,Offset(tip-sign*2*u,y+2*u),Offset(tip,y),u,StrokeCap.Square)}
     }
     drawIntoCanvas {canvas->
      poiPaint.color=android.graphics.Color.WHITE;poiPaint.alpha=(255*opacity).toInt();poiPaint.textSize=(if(chosen)15 else 11).sp.toPx()
      if(!transfer)canvas.nativeCanvas.drawText(if(poi.kind=="parking")"P"else"H",p.x,p.y-(poiPaint.ascent()+poiPaint.descent())/2,poiPaint)
      poiPaint.alpha=255
      if(cluster.members.size>1){poiPaint.textSize=7.sp.toPx();val count=cluster.members.size.toString();val x=p.x+r+4.dp.toPx();val y=p.y+r+4.dp.toPx();drawCircle(Ink,6.dp.toPx(),Offset(x,y));canvas.nativeCanvas.drawText(count,x,y-(poiPaint.ascent()+poiPaint.descent())/2,poiPaint)}
     }
    }
    drawIntoCanvas {canvas->poiLabels.forEach {(text,x,y)->
     poiPaint.textSize=10.sp.toPx();poiPaint.style=android.graphics.Paint.Style.STROKE;poiPaint.strokeWidth=3.dp.toPx();poiPaint.color=android.graphics.Color.WHITE;canvas.nativeCanvas.drawText(text,x,y,poiPaint)
     poiPaint.style=android.graphics.Paint.Style.FILL;poiPaint.color=android.graphics.Color.rgb(40,35,45);canvas.nativeCanvas.drawText(text,x,y,poiPaint)
    }}
    listOfNotNull(origin,destination).forEach {place->val p=screen(GeoPoint(place.lat,place.lon));drawCircle(Color.White,6.dp.toPx(),p);drawCircle(Ink,3.dp.toPx(),p)}
    position?.let {fix->val point=GeoPoint(fix.getDouble("lat"),fix.getDouble("lon"));val p=screen(point);val metres=fix.number("accuracy_m")?:0.0;val edge=screen(GeoPoint(point.lat,point.lon+metres/(111320*cos(point.lat*PI/180).coerceAtLeast(.01))));val radius=(edge-p).getDistance();drawCircle(Color(0xFF188AE8).copy(alpha=.15f),maxOf(radius,10f),p);drawCircle(Color.White,15f,p);drawCircle(Color(0xFF188AE8),11f,p)}
   }
   poiClusters.forEach {cluster->
    val half=with(density){14.dp.toPx()}
    val poi=cluster.members.first()
    Box(Modifier.offset {IntOffset((cluster.position.x-half).roundToInt(),(cluster.position.y-half).roundToInt())}.size(28.dp).clickable {if(planning&&cluster.members.size==1&&poi.kind!="stop")onMapPick?.invoke(poi.point)else inspectCluster(cluster)}.semantics {
     contentDescription=if(cluster.members.size>1)(if(poi.kind=="parking")"P"else"H")+" · ${cluster.members.size} · "+model.text("Zoom to explore","Zum Erkunden vergrößern")else (if(poi.kind=="parking")"P"else"H")+" · "+poi.name.ifBlank {poi.tags.optString("ref",model.text("Mapped place","Kartierter Ort"))}
    },contentAlignment=Alignment.Center) {if(poi.tags.optString("route_role")=="final")ContextSprite("finish",false,Modifier.size(24.dp))}
   }
   listOfNotNull(origin?.let {it to "A"},destination?.let {it to "B"}).forEach { (place,label)->
    val point=screen(GeoPoint(place.lat,place.lon))
    val width=with(LocalDensity.current){44.dp.toPx()};val height=with(LocalDensity.current){48.dp.toPx()}
    Box(Modifier.offset {IntOffset((point.x-width/2).roundToInt(),(point.y-height).roundToInt())}.size(44.dp,48.dp).semantics {contentDescription=if(label=="A")model.text("A · Origin","A · Start")else model.text("B · Destination","B · Ziel")}) {
     if(label=="A"&&(tracking||playbackFraction!=null)&&avatarPosition!=null)PixelIcon("location",Modifier.align(Alignment.BottomCenter).size(20.dp))
     else if(label=="A"&&!planning)CompanionSprite(appearance,Modifier.align(Alignment.BottomCenter).size(40.dp),animated=spriteAnimated,transport=originAvatarMode)
     else if(label=="A")PixelIcon("location",Modifier.align(Alignment.BottomCenter).size(30.dp))
     else ContextSprite("finish",preferences.optString("motion")=="full",Modifier.align(Alignment.BottomCenter).size(38.dp))
     Surface(Modifier.align(Alignment.TopStart),color=Ink,shape=MaterialTheme.shapes.small) {Text(label,color=if(label=="A")Mint else Gold,fontSize=7.sp,modifier=Modifier.padding(horizontal=3.dp,vertical=1.dp))}
    }
   }
   pendingPoint?.let {p->val point=screen(p);Box(Modifier.offset {IntOffset((point.x-12.dp.toPx()).roundToInt(),(point.y-30.dp.toPx()).roundToInt())}.size(24.dp,30.dp)){PixelIcon("location",Modifier.fillMaxSize())}}
   if(planning)position?.let {fix->val point=screen(GeoPoint(fix.getDouble("lat"),fix.getDouble("lon")));Box(Modifier.offset {IntOffset((point.x-24.dp.toPx()).roundToInt(),(point.y-48.dp.toPx()).roundToInt())}.size(48.dp).semantics {contentDescription=model.text("Your location avatar","Dein Standort-Avatar")}){CompanionSprite(appearance,Modifier.fillMaxSize(),animated=spriteAnimated)}}
   if(tracking||playbackFraction!=null)avatarPosition?.let {p->
    val point=screen(p)
    val width=with(LocalDensity.current){52.dp.toPx()};val height=with(LocalDensity.current){52.dp.toPx()}
    Box(Modifier.offset {IntOffset((point.x-width/2).roundToInt(),(point.y-height).roundToInt())}.size(52.dp).semantics {contentDescription=model.text("Journey avatar on selected route","Reiseavatar auf gewählter Route")},contentAlignment=Alignment.BottomCenter) {
     CompanionSprite(appearance,Modifier.size(48.dp),animated=spriteAnimated,transport=if(playbackFraction!=null)originAvatarMode else movingMode)
    }
   }
   Surface(Modifier.align(Alignment.BottomStart).padding(10.dp),color=Panel,shape=MaterialTheme.shapes.large,shadowElevation=4.dp) {
    Column {
     IconButton({styleSheet=true},Modifier.size(44.dp).semantics {contentDescription=model.text("Map views","Kartenansichten")+" · "+mapStyle.title(model)}) {MapLayersIcon(Modifier.size(22.dp))}
     IconButton({poiSettingsSheet=true},Modifier.size(44.dp).semantics {contentDescription=model.text("Map settings · Places and radius","Karteneinstellungen · Orte und Radius")}) {PixelIcon("settings",Modifier.size(22.dp))}
     IconButton(onClick={follow=true;locationActions.request()},modifier=Modifier.size(44.dp).semantics {contentDescription=if(!hasLocationAccess(context))model.text("My position · Allow location access","Mein Standort · Standortzugriff erlauben")else if(locationBlocked)model.text("My position · Enable location","Mein Standort · Standort aktivieren")else model.text("My position · Center map","Mein Standort · Karte zentrieren")}) {
      if((locationBusy||planningLocating)&&!locationBlocked)CircularProgressIndicator(Modifier.size(22.dp),strokeWidth=2.dp)else Box(Modifier.size(24.dp)) {
       PixelIcon("location",Modifier.fillMaxSize())
       if(locationBlocked)Canvas(Modifier.fillMaxSize()){val a=Offset(size.width*.15f,size.height*.85f);val b=Offset(size.width*.85f,size.height*.15f);drawLine(Ink,a,b,5.dp.toPx(),StrokeCap.Round);drawLine(Color(0xFFFF5252),a,b,3.dp.toPx(),StrokeCap.Round)}
      }
     }
    }
   }
   if(mapStyle==JourneyMapStyle.Mobility)MobilityMapLegend(model,Modifier.align(Alignment.TopStart).padding(start=10.dp,top=if(planning)174.dp else 10.dp))
   if(focus?.segments?.any {!it.drawable}==true)Surface(Modifier.align(Alignment.TopCenter).padding(top=66.dp),color=Panel,shape=MaterialTheme.shapes.small) {
    Text(model.text("Tram path unavailable","Tramverlauf nicht verfügbar"),color=Gold,fontSize=10.sp,modifier=Modifier.padding(8.dp))
   }
   Surface(Modifier.align(Alignment.BottomEnd).padding(end=4.dp,bottom=2.dp),color=Panel.copy(alpha=.9f)){
    Text("© OpenStreetMap · OpenMapTiles",color=Pale,fontFamily=FontFamily.SansSerif,fontSize=10.sp,lineHeight=12.sp,modifier=Modifier.padding(horizontal=4.dp,vertical=2.dp))
   }
   if(mapFailure)Surface(Modifier.align(Alignment.Center).padding(20.dp),color=Panel,shape=MaterialTheme.shapes.large){Column(Modifier.padding(14.dp)){Text(model.text("I couldn't load the map. Check your internet connection.","Ich konnte die Karte nicht laden. Prüfe deine Internetverbindung."));TextButton({retry++}){Text(model.text("Retry","Erneut versuchen"))}}}
   else if(mapLoading)Surface(Modifier.align(Alignment.TopCenter).padding(top=if(planning)174.dp else 66.dp),color=Panel,shape=MaterialTheme.shapes.medium){Row(Modifier.padding(8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp);Text(model.text("Loading map…","Karte laden…"),style=MaterialTheme.typography.labelSmall)}}
  }
  if(styleSheet)MapStyleSheet(model,mapStyle,onSelect={mapStyle=it;model.store.put("cricket.map-style.v1",it.key);styleSheet=false},onDismiss={styleSheet=false})
  if(poiSettingsSheet)MapPlacesSettings(model,poiPreferences,parkingForTransit=ptMap){poiSettingsSheet=false}
  inspectedPoi?.let {poi->MobilityPlaceSheet(model,poi,origin,destination,!planning&&!tracking&&!selectedOnly&&!model.busy&&!poi.tags.optBoolean("display_only")){inspectedPoi=null}}
  locationError?.let {message->AlertDialog(onDismissRequest={locationError=null},title={Text(model.text("My position","Mein Standort"))},text={Text(message)},confirmButton={TextButton({locationError=null}){Text(model.text("Close","Schließen"))}})}
  if(!planning&&shown.isEmpty())Copy(model.text("No path geometry returned. Origin and destination remain visible.","Keine Weggeometrie empfangen. Start und Ziel bleiben sichtbar."))
  if(!compact&&shown.any { it.supplemental||it.segments.any {s->s.approximate} })Copy(model.text("Dashed paths: approximate or supplemental geometry.","Gestrichelte Wege: ungefähre oder ergänzende Geometrie."),Gold)
  if(!tracking&&!compact){shown.map {it.mode}.distinct().chunked(2).forEach {pair->Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){pair.forEach {mode->PixelButton(modeName(mode,model.de),{val r=shown.first {it.mode==mode};model.selectedId=r.id;model.activeMode=mode},Modifier.weight(1f),primary=shown.find {it.id==selected}?.mode==mode)}}}
  PixelButton(model.text("View route details","Routendetails ansehen"),{selected?.let {id->cards(model.result).find {it.id==id}?.let {model.activeMode=it.mode} };model.routeView="alternatives"},Modifier.fillMaxWidth())}
 }
}
