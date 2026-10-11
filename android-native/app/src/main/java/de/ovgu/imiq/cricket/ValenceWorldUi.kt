package de.ovgu.imiq.cricket

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import kotlin.math.*
import org.json.JSONObject

@Composable internal fun ValenceWorld(appearance:String,mode:String,rating:Int?,animated:Boolean,phase:Float,de:Boolean,onPrevious:()->Unit,onNext:()->Unit,showModeNavigation:Boolean=true) {
 val safe=appearance.takeIf {it in listOf("robot","naturalist","explorer","creature")}?:"robot"
 val background=art("valence-$mode-background-v1.png")
 val sheet=art("valence-$safe-emotions-v1.png")
 val context=LocalContext.current
 val atlas=remember(safe){context.assets.open("art/jrpg/valence-emotions-atlas.json").bufferedReader().use {JSONObject(it.readText()).getJSONObject(safe)}}
 val bikeClips=remember(atlas) {
  val frames=atlas.getJSONArray("bike_visible_regions")
  List(6) {column->Path().apply {
   val regions=frames.getJSONArray(column)
   for(i in 0 until regions.length()) {
    val r=regions.getJSONArray(i)
    addRect(Rect(r.getInt(0).toFloat(),r.getInt(1).toFloat(),r.getInt(2).toFloat(),r.getInt(3).toFloat()))
   }
  }}
 }
 val seconds=jrpgSceneTime(animated)
 val emotion by animateFloatAsState((rating?:0)/3f,tween(if(animated)550 else 0),label="World emotion intensity")
 var info by remember {mutableStateOf(false)}
 val feeling=valenceFeeling(rating,de)
 val pet=when(safe){"robot"->"drone";"explorer"->"spider";"creature"->"spirit";else->"puppy"}
 Box(Modifier.fillMaxWidth().aspectRatio(3f/2f).clip(RoundedCornerShape(14.dp)).jrpgSceneFrame(animated)
  .pointerInput(mode){var distance=0f;detectHorizontalDragGestures(onDragStart={distance=0f},onDragEnd={if(abs(distance)>48.dp.toPx()){if(distance<0)onNext()else onPrevious()};distance=0f},onDragCancel={distance=0f}){event,delta->event.consume();distance+=delta}}
  .semantics {contentDescription=valenceLandmarkTitle(mode)}) {
  Image(background,null,Modifier.matchParentSize(),contentScale=ContentScale.FillBounds)
  Canvas(Modifier.matchParentSize().semantics {
   contentDescription="Avatar with transport $mode: $feeling\nCompanion $pet: $feeling"
   stateDescription=if(animated)"Animated"else"Reduced motion"
  }) {
   val t=seconds;val pose=valenceWorldPose(mode,t,animated)
   val intense=abs(emotion)
   val boost=if(animated&&phase<1)sin(phase*PI).toFloat()else 0f
   val clock=if(animated)t else 0f
   if(animated)drawValenceEnvironment(mode,clock)
   val row=modes.indexOf(mode).coerceAtLeast(0)
   val state=if(rating==null||rating==0)1 else if(rating<0)0 else 2
   val gait=if(animated)((clock*2.5f)%2f)else 0f
   val base=state*2+gait.toInt()
   val reference=atlas.getJSONArray("reference").getJSONArray(row)
   val characterWidth=size.width*(if(mode=="walk").13f else if(mode=="bike").30f else .44f)*pose.depth*(1f+boost*.035f)
   val ratio=characterWidth/reference.getInt(0)
   val x=size.width*pose.x
   val ground=size.height*pose.ground
   val wobble=if(animated)sin(clock*if(emotion<0)5f else 2.4f)*intense*(1f+boost)else 0f
   val y=ground-if(emotion>0)abs(wobble)*1.8.dp.toPx()else 0f
   val tone=if(emotion<0)Color(0xFFD5B2F0)else Color(0xFFFF6C94)
   if(rating!=null&&rating!=0)drawOval(tone.copy(alpha=.12f*intense),Offset(x-characterWidth*.55f,ground-3.dp.toPx()),Size(characterWidth*1.1f,6.dp.toPx()))
   fun poseFrame(column:Int,alpha:Float) {
    val rect=atlas.getJSONArray("rows").getJSONArray(row).getJSONArray(column)
    val w=rect.getInt(2)*ratio;val h=rect.getInt(3)*ratio
    val left=(x-w/2).roundToInt();val top=(y-h).roundToInt()
    val exclusions=atlas.getJSONArray("edge_exclusions").getJSONArray(row).getJSONArray(column)
    val excluded=Path().apply {
     for(i in 0 until exclusions.length()) {
      val r=exclusions.getJSONArray(i)
      addRect(Rect(left+r.getInt(0)*ratio,top+r.getInt(1)*ratio,left+r.getInt(2)*ratio,top+r.getInt(3)*ratio))
     }
    }
    rotate(wobble*(if(emotion<0)2.5f else 1.1f),Offset(x,y)) {
     scale(if(pose.facingRight)1f else -1f,1f,Offset(x,y)) {
      if(mode=="bike")translate(left.toFloat(),top.toFloat()) {
       scale(ratio,pivot=Offset.Zero) {
        clipPath(bikeClips[column]) {
         drawImage(sheet,IntOffset(rect.getInt(0),rect.getInt(1)),IntSize(rect.getInt(2),rect.getInt(3)),IntOffset.Zero,IntSize(rect.getInt(2),rect.getInt(3)),alpha=alpha*pose.visibility,filterQuality=FilterQuality.Medium)
        }
       }
      }else clipPath(excluded,ClipOp.Difference) {drawImage(sheet,IntOffset(rect.getInt(0),rect.getInt(1)),IntSize(rect.getInt(2),rect.getInt(3)),IntOffset(left,top),IntSize(w.roundToInt().coerceAtLeast(1),h.roundToInt().coerceAtLeast(1)),alpha=alpha*pose.visibility,filterQuality=FilterQuality.Medium)}
     }
    }
   }
   // Hold each authored pose opaque; continuous motion supplies intermediate frames.
   poseFrame(base,1f)
   if(rating!=null&&rating!=0&&pose.visibility>.2f) {
    val headY=y-reference.getInt(1)*ratio*.78f
    repeat(if(abs(rating)==3)5 else 3){i->
     val p=if(animated)((clock*.45f+i*.29f)%1f)else .45f+i*.1f
     val center=Offset(x+(i-2)*7.dp.toPx()+sin(clock+i)*intense*2.dp.toPx(),headY-p*(16+intense*16).dp.toPx())
     val alpha=(1-p)*(.4f+.6f*intense)*pose.visibility
     val radius=(3+intense*3+boost).dp.toPx()
     drawAnimeHeart(center,radius,tone.copy(alpha=alpha),broken=rating<0)
     if(rating<0) {
      val smoke=Offset(x+(i-2)*4.dp.toPx(),headY-4.dp.toPx()-p*24.dp.toPx())
      drawCircle(Color(0xFFE3DCEB).copy(alpha=alpha*.65f),radius*(.45f+p*.7f),smoke)
      if(abs(rating)>=2)drawLine(Color(0xFFCA7CBE).copy(alpha=alpha),center+Offset(8.dp.toPx(),0f),center+Offset(8.dp.toPx(),6.dp.toPx()),1.2.dp.toPx())
     }
    }
   }
  }
  if(showModeNavigation){
   JrpgSceneArrow(true,if(de)"Vorheriges Verkehrsmittel"else"Previous mode",animated,onPrevious,Modifier.align(Alignment.CenterStart).padding(start=4.dp))
   JrpgSceneArrow(false,if(de)"Nächstes Verkehrsmittel"else"Next mode",animated,onNext,Modifier.align(Alignment.CenterEnd).padding(end=4.dp))
  }
  IconButton({info=true},Modifier.align(Alignment.TopEnd).padding(6.dp).size(48.dp).clip(CircleShape).background(Ink.copy(alpha=.85f)).semantics {contentDescription=if(de)"Über diese Szene"else"About this scene"}){PixelIcon("info",Modifier.size(28.dp))}
 }
 Text(valenceLandmarkTitle(mode),style=MaterialTheme.typography.bodySmall,color=Gold)
 if(info)ValenceLandmarkDialog(mode,de){info=false}
}

private fun DrawScope.drawAnimeHeart(center:Offset,radius:Float,color:Color,broken:Boolean) {
 translate(center.x-radius,center.y-radius) {
  scale(radius/20f,pivot=Offset.Zero) {
   val heart=Path().apply {moveTo(20f,36f);cubicTo(12f,29f,2f,22f,2f,13f);cubicTo(2f,2f,14f,0f,20f,9f);cubicTo(26f,0f,38f,2f,38f,13f);cubicTo(38f,22f,28f,29f,20f,36f);close()}
   drawPath(heart,color)
   if(broken)clipPath(heart){val crack=Path().apply {moveTo(23f,0f);lineTo(17f,13f);lineTo(23f,20f);lineTo(16f,27f);lineTo(20f,39f)};drawPath(crack,Ink.copy(alpha=color.alpha),style=Stroke(3.5f))}
   else drawOval(Pale.copy(alpha=color.alpha*.65f),Offset(8f,7f),Size(6f,4f))
  }
 }
}
