package de.ovgu.imiq.cricket

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import org.json.JSONObject
import kotlin.math.*

internal data class IntroTitleMetrics(val width:Float,val first:Float,val last:Float,val o:Float=0f,val m:Float=0f)
internal fun introEase(x:Float):Float {val p=x.coerceIn(0f,1f);return p*p*p*(p*(p*6f-15f)+10f)}

@Composable fun StartupWelcomeScreen(model:CricketModel,onRetryMigration:()->Unit) {
 val animated=model.settings().optString("motion")=="full"
 val lifecycle=LocalLifecycleOwner.current.lifecycle
 var seconds by remember {mutableFloatStateOf(model.startupElapsedMs()/1000f)}
 var letters by remember {mutableStateOf(IntroTitleMetrics(0f,0f,0f))}
 var rootPosition by remember {mutableStateOf(Offset.Zero)}
 var launchBounds by remember {mutableStateOf(Rect.Zero)}
 LaunchedEffect(model){model.beginStartupWelcome()}
 LaunchedEffect(model,lifecycle){lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){while(true)withFrameNanos {seconds=model.startupElapsedMs()/1000f}}}
 val t=if(animated)seconds*.75f else 9f
 BoxWithConstraints(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Ink,Panel,Ink))).safeDrawingPadding().clipToBounds().onGloballyPositioned {rootPosition=it.positionInRoot()}) {
  val titleY=maxHeight*.43f
  val titleFont=if(maxWidth<350.dp)22.sp else 26.sp
  val compact=maxHeight<620.dp
  Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}){
   val center=Offset(size.width*.5f,size.height*.43f);val radius=size.width*.78f
   drawCircle(Brush.radialGradient(listOf(Color(0xFF6D466D).copy(alpha=.16f),Color.Transparent),center,radius),radius,center)
  }
  IntroCornerCast(t,animated,Modifier.fillMaxSize())
  Box(Modifier.fillMaxWidth().offset(y=titleY-22.dp).height(44.dp),contentAlignment=Alignment.Center){
   ArcadeCompanionTitle(t,animated,titleFont){layout->
    val first=layout.getBoundingBox(0);val last=layout.getBoundingBox(8)
    val o=layout.getBoundingBox(1);val m=layout.getBoundingBox(2)
    letters=IntroTitleMetrics(layout.size.width.toFloat(),(first.left+first.right)/2,(last.left+last.right)/2,(o.left+o.right)/2,(m.left+m.right)/2)
   }
  }
  IntroDogsAndMushroom(t,animated,letters,Modifier.fillMaxSize())
  Column(Modifier.align(Alignment.TopCenter).offset(y=titleY+52.dp).padding(horizontal=22.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)){
   Text(model.text("Every journey, together.","Jede Reise, gemeinsam."),color=Pale,style=MaterialTheme.typography.bodyMedium,textAlign=TextAlign.Center)
   Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
    if(!model.ready&&animated)CircularProgressIndicator(Modifier.size(18.dp),color=Mint,strokeWidth=2.dp)else PixelIcon(if(model.ready)"sparkle"else"time",Modifier.size(18.dp))
    Text(if(model.ready)model.text("Ready when you are","Bereit, wenn du es bist")else model.text("Loading…","Wird geladen…"),color=Mint,style=MaterialTheme.typography.bodySmall)
   }
   if(model.ready){
    Spacer(Modifier.height(24.dp))
    PixelButton(model.text("Let's go →","Los geht's →"),{model.skipStartupWelcome()},Modifier.onGloballyPositioned {launchBounds=Rect(it.positionInRoot()-rootPosition,it.size.toSize())},primary=true)
   }
   else if(model.migrationError!=null){
    Text(model.migrationError!!,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall,maxLines=2)
    TextButton(onRetryMigration){Text(model.text("Retry import","Import wiederholen"))}
   }
  }
  IntroCompanionMascots(t,animated,letters,if(model.ready)launchBounds else Rect.Zero,Modifier.fillMaxSize())
  AnimatedUniversityBrand(t,animated,Modifier.align(Alignment.BottomCenter).padding(bottom=14.dp).widthIn(max=340.dp).width(maxWidth*.88f).height(if(compact)64.dp else 76.dp))
 }
}

@Composable internal fun introMetadata(key:String):JSONObject {
 val context=LocalContext.current
 return remember(key){context.assets.open("art/jrpg/companion-intro-atlas.json").bufferedReader().use {JSONObject(it.readText()).getJSONObject(key)}}
}

/** Full sprites are drawn on the screen canvas; only the screen edge clips a body. */
@Composable private fun IntroCornerCast(t:Float,animated:Boolean,modifier:Modifier) {
 val kinds=listOf("naturalist","robot","explorer","creature")
 val metadata=kinds.map {introMetadata(it)}
 val sheets=metadata.map {art(it.getString("file"))}
 fun pose(kind:String,time:Float):Int {
  val at=time.coerceAtLeast(0f)
  return if(!animated){if(kind=="naturalist")23 else 14}else if(kind=="naturalist")when {
   at<1.9f->(at*3f).toInt().coerceIn(0,5)
   at<3.1f->(6+(at-1.9f)*5f).toInt().coerceIn(6,11)
   at<5.1f->(12+(at-3.1f)*3f).toInt().coerceIn(12,17)
   at<8.5f->18+intArrayOf(0,1,2,3,4,5,4,3,2,1)[((at-5.1f)*2.3f).toInt()%10]
   else->intArrayOf(18,19,18,18,18,18)[((at-8.5f)*.7f).toInt()%6]
  }else {
   val sequence=if(at<8.5f)intArrayOf(5,6,7,8,9,10,11,10,9,8,7,6)else intArrayOf(13,14,15,14)
   if(at<1.1f)(at*3f).toInt().coerceIn(0,3)else sequence[((at-1.1f)*if(at<8.5f)2.4f else .75f).toInt()%sequence.size]
  }
 }
 Canvas(modifier.clearAndSetSemantics {contentDescription="Four companions lean in from the screen edges"}){
  val side=minOf(size.width*.64f,270.dp.toPx(),size.height*.36f)
  val enter=if(animated)introEase(t/1.35f)else 1f
  kinds.forEachIndexed {index,kind->
   val left=index%2==0
   val top=index<2
   val x=if(left)-side*.46f else size.width-side*.54f
   val y=if(top)side*.04f else size.height-side*1.32f
   val shift=(1-enter)*side*.8f
   val breath=if(animated)sin(t*.85f+index*.45f)*1.1.dp.toPx()else 0f
   val frame=pose(kind,t)
   val previous=pose(kind,t-.1f)
   val meta=metadata[index]
   // Brief frame dissolves soften a pose boundary without changing the authored pixels.
   val currentAlpha=if(animated&&previous!=frame){
    var before=t-.1f;var after=t
    repeat(7){val middle=(before+after)/2;if(pose(kind,middle)==frame)after=middle else before=middle}
    introEase((t-after)/.1f)
   }else 1f
   translate(left=x+if(left)-shift else shift,top=y+breath){
    rotate(if(left)43f else -43f,Offset(side/2,side/2)){
     fun draw(frameIndex:Int,opacity:Float){
      val bounds=meta.getJSONArray("frames").getJSONArray(frameIndex)
      drawImage(sheets[index],IntOffset(bounds.getInt(0),bounds.getInt(1)),IntSize(bounds.getInt(2),bounds.getInt(3)),
       IntOffset.Zero,IntSize(side.roundToInt(),side.roundToInt()),alpha=opacity*enter,filterQuality=FilterQuality.High)
     }
     if(currentAlpha<1f)draw(previous,1f-currentAlpha)
     draw(frame,currentAlpha)
    }
   }
  }
 }
}

@Composable private fun IntroDogsAndMushroom(t:Float,animated:Boolean,letters:IntroTitleMetrics,modifier:Modifier) {
 val dogs=art("companion-intro-dogs-v1.png");val dogMeta=introMetadata("dogs")
 val mushroom=art("companion-intro-amanita-v1.png");val mushroomMeta=introMetadata("amanita")
 Canvas(modifier.clearAndSetSemantics {contentDescription="A puppy brings a glowing mushroom to Companion"}){
  val titleY=size.height*.43f
  val titleWidth=if(letters.width>0)letters.width else size.width*.78f
  val titleLeft=(size.width-titleWidth)/2
  val c=titleLeft+if(letters.width>0)letters.first else titleWidth*.055f
  val n=titleLeft+if(letters.width>0)letters.last else titleWidth*.945f
  val m=titleLeft+if(letters.width>0)letters.m else titleWidth*.275f
  val dogSide=(size.width*.19f).coerceAtMost(82.dp.toPx())
  val floor=titleY-18.dp.toPx()
  val release=6.45f
  fun prop(center:Offset,side:Float,angle:Float=0f){
   val frame=if(animated)(t*3f).toInt()%8 else 0
   val r=mushroomMeta.getJSONArray("frames").getJSONArray(frame)
   rotate(angle,center){drawImage(mushroom,IntOffset(r.getInt(0),r.getInt(1)),IntSize(r.getInt(2),r.getInt(3)),IntOffset((center.x-side/2).roundToInt(),(center.y-side/2).roundToInt()),IntSize(side.roundToInt(),side.roundToInt()),filterQuality=FilterQuality.Medium)}
  }
  // The hand and backpack use the same rotated full-sprite geometry as the naturalist.
  val side=minOf(size.width*.64f,270.dp.toPx(),size.height*.36f)
  val enter=if(animated)introEase(t/1.35f)else 1f
  val angle=43f*PI.toFloat()/180f
  fun naturalistPoint(x:Float,y:Float):Offset {
   val dx=(x-.5f)*side;val dy=(y-.5f)*side
   return Offset(side*.04f-(1-enter)*side*.8f+dx*cos(angle)-dy*sin(angle),side*.54f+dx*sin(angle)+dy*cos(angle))
  }
  val hand=naturalistPoint(.94f,.47f)
  if(t<1.75f){prop(hand,28.dp.toPx())}
  repeat(1){i->
   val startTime=if(i==0)1.75f else 2.05f
   val escapeTime=startTime-.6f
   if(t>=escapeTime){
    val start=Offset(hand.x-dogSide*.3f,hand.y+dogSide*.45f)
    val target=Offset(if(i==0)c else n,floor)
    val duration=if(i==0)2.7f else 3.1f
    val p=((t-startTime)/duration).coerceIn(0f,1f)
    val eased=if(p<.8f)p else .8f+.2f*introEase((p-.8f)/.2f)
    val position=if(t<startTime){
     val jump=introEase((t-escapeTime)/.6f)
     val backpack=naturalistPoint(.27f,.5f)
     Offset(backpack.x+(start.x-backpack.x)*jump,backpack.y+(start.y-backpack.y)*jump-sin(jump*PI).toFloat()*18.dp.toPx())
    }else Offset(start.x+(target.x-start.x)*eased,start.y+(target.y-start.y)*eased-sin(p*PI).toFloat()*28.dp.toPx())
    val arrival=startTime+duration
    fun dogPose(at:Float):Int {
     val time=at.coerceAtLeast(escapeTime)
     return if(time>=8.5f){(if(i==0)14 else 30)+((time-8.5f)*1.1f).toInt()%2}else if(time<startTime){16+((time-escapeTime)*9f).toInt()%8}else if(time<arrival){i*16+((time-startTime)*9f).toInt()%8}else if(i==0){
      if(time<release)(8+((time-arrival)*2.5f).toInt()).coerceIn(8,12)else (13+((time-release)*4f).toInt()).coerceIn(13,15)
     }else (24+((time-arrival)*4f).toInt()).coerceIn(24,31)
    }
    val frame=dogPose(t)
    val previous=dogPose(t-.065f)
    val blend=if(animated&&previous!=frame){
     var before=t-.065f;var after=t
     repeat(6){val middle=(before+after)/2;if(dogPose(middle)==frame)after=middle else before=middle}
     introEase((t-after)/.065f)
    }else 1f
    val ratio=dogSide/dogMeta.getDouble("reference_width").toFloat()
    val breathe=if(animated&&p>=1f)sin(t*1.1f+i)*.65.dp.toPx()else 0f
    val drawDog:DrawScope.()->Unit={
     fun draw(frameIndex:Int,opacity:Float){
      val r=dogMeta.getJSONArray("frames").getJSONArray(frameIndex)
      val w=r.getInt(2)*ratio;val h=r.getInt(3)*ratio
      drawImage(dogs,IntOffset(r.getInt(0),r.getInt(1)),IntSize(r.getInt(2),r.getInt(3)),IntOffset((position.x-w/2).roundToInt(),(position.y-h+breathe).roundToInt()),IntSize(w.roundToInt(),h.roundToInt()),alpha=opacity,filterQuality=FilterQuality.Medium)
     }
     if(blend<1f)draw(previous,1f-blend)
     draw(frame,blend)
    }
    if(i==1&&p>.92f)scale(cos(introEase((p-.92f)/.08f)*PI).toFloat(),1f,pivot=position){drawDog()}else drawDog()
   }
  }
  if(t>=release){
   val drop=((t-release)/.75f).coerceIn(0f,1f)
   // The small prop rests on the cap of the measured M, rather than below the C.
   val end=Offset(m,titleY-21.dp.toPx())
   val origin=Offset(c+dogSide*.27f,floor-dogSide*.36f)
   val pos=Offset(origin.x+(end.x-origin.x)*drop,origin.y+(end.y-origin.y)*drop*drop)
   drawCircle(Brush.radialGradient(listOf(Gold.copy(alpha=.14f),Color.Transparent),pos,44.dp.toPx()),44.dp.toPx(),pos)
   prop(pos,27.dp.toPx(),if(drop<1f)drop*28f else 0f)
  }
 }
}

@Composable private fun ArcadeCompanionTitle(seconds:Float,animated:Boolean,fontSize:TextUnit,onLayout:(TextLayoutResult)->Unit) {
 val sinceLanding=seconds-7.2f
 val impact=if(animated)exp(-(sinceLanding/.3f).pow(2))else 0f
 val settled=.14f*introEase(sinceLanding/.7f)
 val glow=(if(animated).61f+.08f*sin(seconds*.85f)else .75f)+settled+impact*.35f
 Box(contentAlignment=Alignment.Center){
  Text("COMPANION",modifier=Modifier.offset(y=3.dp).clearAndSetSemantics {},fontFamily=PixelFont,fontSize=fontSize,maxLines=1,softWrap=false,color=Color(0xFF126B87),style=TextStyle(shadow=Shadow(Mint.copy(alpha=glow.coerceIn(0f,1f)),Offset.Zero,22f+impact*25f)))
  Text("COMPANION",fontFamily=PixelFont,fontSize=fontSize,maxLines=1,softWrap=false,textAlign=TextAlign.Center,onTextLayout=onLayout,style=TextStyle(brush=Brush.verticalGradient(listOf(lerp(Color(0xFFFFF5BA),Color.White,impact),lerp(Gold,Pale,impact),lerp(Color(0xFFFFA957),Gold,impact))),shadow=Shadow(Gold.copy(alpha=glow.coerceIn(0f,1f)),Offset.Zero,12f+impact*30f)))
 }
}
