package de.ovgu.imiq.cricket

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.math.*

private fun mercator(point:GeoPoint):Pair<Double,Double>{val latitude=point.lat.coerceIn(-85.0511,85.0511)*PI/180;return (point.lon+180)/360 to (1-ln(tan(latitude)+1/cos(latitude))/PI)/2}
private data class Tile(val z:Int,val x:Int,val y:Int)
private val tileGate=Semaphore(4)

@Composable fun NativeJourneyMap(model:CricketModel) {
 val routes=remember(model.result){mapRoutes(model.result)}
 val selected=model.selectedId
 val shown=routes.groupBy {it.mode}.values.mapNotNull {group->group.find {it.id==selected}?:group.firstOrNull()}
 val focus=shown.find {it.id==selected}
 val points=(focus?.segments?.flatMap {it.points}?:shown.flatMap {r->r.segments.flatMap {it.points}})+listOfNotNull(model.origin?.let {GeoPoint(it.lat,it.lon)},model.destination?.let {GeoPoint(it.lat,it.lon)})
 var centerX by remember{mutableDoubleStateOf((11.63+180)/360)}
 var centerY by remember{mutableDoubleStateOf(mercator(GeoPoint(52.13,11.63)).second)}
 var zoom by remember{mutableFloatStateOf(13f)}
 var size by remember{mutableStateOf(IntSize.Zero)}
 var fitRevision by remember{mutableIntStateOf(0)}
 var retry by remember{mutableIntStateOf(0)}
 var tileFailure by remember{mutableStateOf(false)}
 val images=remember{mutableStateMapOf<Tile,ImageBitmap>()}
 val unit=with(LocalDensity.current){256.dp.toPx()}
 LaunchedEffect(model.result,selected,size,fitRevision){
  if(points.isNotEmpty()&&size.width>0&&size.height>0){val bounds=points.map(::mercator);val minX=bounds.minOf {it.first};val maxX=bounds.maxOf {it.first};val minY=bounds.minOf {it.second};val maxY=bounds.maxOf {it.second};centerX=(minX+maxX)/2;centerY=(minY+maxY)/2;val zx=log2((size.width-80).coerceAtLeast(40)/(unit*(maxX-minX).coerceAtLeast(0.0000001)));val zy=log2((size.height-80).coerceAtLeast(40)/(unit*(maxY-minY).coerceAtLeast(0.0000001)));zoom=min(zx,zy).toFloat().coerceIn(2f,16f)}
 }
 val tileZoom=floor(zoom).toInt();val count=1 shl tileZoom;val tileSize=unit*2.0.pow((zoom-tileZoom).toDouble()).toFloat();val worldSize=tileSize*count
 val left=centerX*worldSize-size.width/2;val top=centerY*worldSize-size.height/2
 val tiles=if(size.width==0||size.height==0)emptyList()else buildList {
  val firstX=floor(left/tileSize).toInt();val lastX=floor((left+size.width)/tileSize).toInt();val firstY=floor(top/tileSize).toInt().coerceAtLeast(0);val lastY=floor((top+size.height)/tileSize).toInt().coerceAtMost(count-1)
  for(y in firstY..lastY)for(x in firstX..lastX)add(Tile(tileZoom,((x%count)+count)%count,y))
 }.distinct()
 LaunchedEffect(tiles,retry,model.api.base()){
  tileFailure=false
  coroutineScope {tiles.filter { it !in images }.map { tile->async {tileGate.withPermit {
   try{val result=model.api.call("/api/dyconet/map-tiles/${tile.z}/${tile.x}/${tile.y}",timeout=60000);val bytes=Base64.decode(result.getString("data_url").substringAfter(','),Base64.DEFAULT);val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size)?:error("Invalid map image");images[tile]=ImageBitmapFrom(bitmap)}catch(e:CancellationException){throw e}catch(e:Exception){tileFailure=true}
  }} }.awaitAll() }
  if(images.size>80)images.keys.filter {it !in tiles}.take(images.size-60).forEach {images.remove(it)}
 }
 fun screen(point:GeoPoint):Offset {val p=mercator(point);return Offset(((p.first-centerX)*worldSize+size.width/2).toFloat(),((p.second-centerY)*worldSize+size.height/2).toFloat())}
 Column(Modifier.fillMaxSize().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){PixelButton("+",{zoom=(zoom+1).coerceAtMost(19f)});PixelButton("−",{zoom=(zoom-1).coerceAtLeast(2f)});PixelButton(model.text("Fit journey","Reise anzeigen"),{fitRevision++},Modifier.weight(1f))}
  Box(Modifier.weight(1f).fillMaxWidth()) {
   Canvas(Modifier.fillMaxSize().onSizeChanged {size=it}
    .pointerInput(Unit){detectTransformGestures {centroid,pan,magnification,_->val old=unit*2.0.pow(zoom.toDouble());val next=(zoom+log2(magnification)).coerceIn(2f,19f);val new=unit*2.0.pow(next.toDouble());centerX+=(centroid.x-size.width/2)*(1/old-1/new)-pan.x/new;centerY=((centerY+(centroid.y-size.height/2)*(1/old-1/new)-pan.y/new)).coerceIn(0.0,1.0);zoom=next}}
    .pointerInput(shown,zoom,centerX,centerY,size){detectTapGestures {tap->
     var nearest:MapRoute?=null;var distance=40.0
     shown.forEach {route->route.segments.forEach {segment->segment.points.zipWithNext().forEach { (a,b)->val p=screen(a);val q=screen(b);val delta=q-p;val denominator=delta.x*delta.x+delta.y*delta.y;val t=if(denominator==0f)0f else (((tap.x-p.x)*delta.x+(tap.y-p.y)*delta.y)/denominator).coerceIn(0f,1f);val d=(tap-(p+delta*t)).getDistance().toDouble();if(d<distance){distance=d;nearest=route} } }}
     nearest?.let {model.selectedId=it.id;model.activeMode=it.mode}
    }}
   ) {
    drawRect(Color(0xFFDBE4DF))
    tiles.forEach {tile->images[tile]?.let {image->val px=(tile.x*tileSize-left).toFloat();val py=(tile.y*tileSize-top).toFloat();drawImage(image,dstOffset=IntOffset(px.roundToInt(),py.roundToInt()),dstSize=IntSize(tileSize.roundToInt()+1,tileSize.roundToInt()+1),filterQuality=FilterQuality.Low)}}
    shown.sortedBy {it.id==selected}.forEach {route->route.segments.forEach {segment->val path=Path();segment.points.forEachIndexed { i,p->val s=screen(p);if(i==0)path.moveTo(s.x,s.y)else path.lineTo(s.x,s.y) };val chosen=route.id==selected;val dashed=segment.approximate||route.supplemental||route.mode=="pt"&&segment.mode=="walk";val effect=if(dashed)PathEffect.dashPathEffect(floatArrayOf(12f,10f))else null;drawPath(path,Ink,style=Stroke(if(chosen)16f else 11f,pathEffect=effect));drawPath(path,modeColor(segment.mode).copy(alpha=if(selected!=null&&!chosen)0.5f else 1f),style=Stroke(if(chosen)10f else 6f,pathEffect=effect))}}
    listOfNotNull(model.origin?.let {it to Mint},model.destination?.let {it to Gold}).forEach { (place,c)->val p=screen(GeoPoint(place.lat,place.lon));drawCircle(Ink,18f,p);drawCircle(c,12f,p)}
   }
   if(tileFailure)Column(Modifier.align(Alignment.TopCenter).padding(10.dp)){Surface(color=Panel){Column(Modifier.padding(10.dp)){Text(model.text("Map images could not load","Kartenbilder konnten nicht laden"));TextButton({retry++}){Text(model.text("Retry","Erneut versuchen"))}}}}
   Surface(Modifier.align(Alignment.BottomEnd),color=Color(0xDFFFFFFF)){Text("© OpenStreetMap contributors",color=Color(0xFF14213B),style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(4.dp))}
  }
  if(shown.isEmpty())Copy(model.text("No path geometry returned. Origin and destination remain visible.","Keine Weggeometrie empfangen. Start und Ziel bleiben sichtbar."))
  if(shown.any { it.supplemental||it.segments.any {s->s.approximate} })Copy(model.text("Dashed paths: approximate or supplemental geometry.","Gestrichelte Wege: ungefähre oder ergänzende Geometrie."),Gold)
  shown.map {it.mode}.distinct().chunked(2).forEach {pair->Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){pair.forEach {mode->PixelButton(modeName(mode,model.de),{val r=shown.first {it.mode==mode};model.selectedId=r.id;model.activeMode=mode},Modifier.weight(1f),primary=shown.find {it.id==selected}?.mode==mode)}}}
  PixelButton(model.text("View route details","Routendetails ansehen"),{selected?.let {id->cards(model.result).find {it.id==id}?.let {model.activeMode=it.mode} };model.routeView="alternatives"},Modifier.fillMaxWidth())
 }
}
private fun ImageBitmapFrom(bitmap:android.graphics.Bitmap):ImageBitmap=bitmap.asImageBitmap()
