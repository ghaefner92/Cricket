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
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import org.json.JSONObject
import kotlin.math.*

internal data class IntroTitleMetrics(val width:Float,val first:Float,val last:Float,val o:Float=0f,val m:Float=0f)
internal fun introEase(x:Float):Float {val p=x.coerceIn(0f,1f);return p*p*p*(p*(p*6f-15f)+10f)}
internal const val INTRO_ESCAPE=1.75f
internal const val INTRO_ARRIVAL=5.15f
/** Add premultiplied poses in an isolated layer: their shared pixels stay opaque. */
internal fun DrawScope.introBlend(bounds:Rect,blend:Float,draw:(Boolean,Float,BlendMode)->Unit){
 if(blend>=1f){draw(true,1f,BlendMode.SrcOver);return}
 drawContext.canvas.saveLayer(bounds,androidx.compose.ui.graphics.Paint())
 draw(false,1f-blend,BlendMode.SrcOver)
 draw(true,blend,BlendMode.Plus)
 drawContext.canvas.restore()
}

/** Shared sprite geometry keeps the pets attached to their own companion at release. */
internal fun DrawScope.introAnchor(index:Int,t:Float,animated:Boolean,point:Offset=Offset(.5f,.48f)):Offset {
 val side=minOf(size.width*.64f,270.dp.toPx(),size.height*.36f)
 val left=index%2==0;val top=index<2
 val enter=if(animated)introEase(t/1.35f)else 1f
 val x=if(left)-side*.46f else size.width-side*.54f
 val y=if(top)side*.04f else size.height-side*1.32f
 val shift=(1-enter)*side*.8f
 val angle=(if(left)43f else -43f)*PI.toFloat()/180f
 val dx=(point.x-.5f)*side;val dy=(point.y-.5f)*side
 val breath=if(animated)sin(t*.85f+index*.45f)*1.1.dp.toPx()else 0f
 return Offset(x+side/2+(if(left)-shift else shift)+dx*cos(angle)-dy*sin(angle),y+side/2+breath+dx*sin(angle)+dy*cos(angle))
}

@Composable fun StartupWelcomeScreen(model:CricketModel,onRetryMigration:()->Unit) {
 val animated=model.settings().optString("motion")=="full"
 val lifecycle=LocalLifecycleOwner.current.lifecycle
 var seconds by remember {mutableFloatStateOf(model.startupElapsedMs()/1000f)}
 var letters by remember {mutableStateOf(IntroTitleMetrics(0f,0f,0f))}
 var rootPosition by remember {mutableStateOf(Offset.Zero)}
 var launchBounds by remember {mutableStateOf(Rect.Zero)}
 LaunchedEffect(model){model.beginStartupWelcome()}
 LaunchedEffect(model,lifecycle,animated){if(animated)lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){while(true)withFrameNanos {seconds=model.startupElapsedMs()/1000f}}}
 // Read the clock only inside drawing, keeping text/layout out of each animation frame.
 val timeline=remember(animated){{if(animated)seconds*.75f else 9f}}
 BoxWithConstraints(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Ink,Panel,Ink))).safeDrawingPadding().clipToBounds().onGloballyPositioned {rootPosition=it.positionInRoot()}) {
  val titleY=maxHeight*.43f
  val titleFont=if(maxWidth<350.dp)32.sp else 38.sp
  val compact=maxHeight<620.dp
  Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}){
   val center=Offset(size.width*.5f,size.height*.43f);val radius=size.width*.78f
   drawCircle(Brush.radialGradient(listOf(Color(0xFF6D466D).copy(alpha=.16f),Color.Transparent),center,radius),radius,center)
  }
  IntroCornerCast(timeline,animated,Modifier.fillMaxSize())
  Box(Modifier.fillMaxWidth().offset(y=titleY-22.dp).height(44.dp),contentAlignment=Alignment.Center){
   SmoothCompanionTitle(titleFont){layout->
    val first=layout.getBoundingBox(0);val last=layout.getBoundingBox(8)
    val o=layout.getBoundingBox(1);val m=layout.getBoundingBox(2)
    letters=IntroTitleMetrics(layout.size.width.toFloat(),(first.left+first.right)/2,(last.left+last.right)/2,(o.left+o.right)/2,(m.left+m.right)/2)
   }
  }
  IntroDogsAndMushroom(timeline,animated,letters,Modifier.fillMaxSize())
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
  IntroCompanionMascots(timeline,animated,letters,if(model.ready)launchBounds else Rect.Zero,Modifier.fillMaxSize())
  AnimatedUniversityBrand(timeline,animated,Modifier.align(Alignment.BottomCenter).padding(bottom=14.dp).widthIn(max=340.dp).width(maxWidth*.88f).height(if(compact)64.dp else 76.dp))
 }
}

@Composable internal fun introMetadata(key:String):JSONObject {
 val context=LocalContext.current
 return remember(key){context.assets.open("art/jrpg/companion-intro-atlas.json").bufferedReader().use {JSONObject(it.readText()).getJSONObject(key)}}
}

/** Full sprites are drawn on the screen canvas; only the screen edge clips a body. */
@Composable private fun IntroCornerCast(time:()->Float,animated:Boolean,modifier:Modifier) {
 val kinds=listOf("naturalist","robot","explorer","creature")
 val metadata=kinds.map {introMetadata("startup_"+it)}
 val sheets=metadata.map {art(it.getString("file"))}
 fun pose(time:Float):Int {
  val at=time.coerceAtLeast(0f)
  return if(!animated)24 else when {
   at<INTRO_ESCAPE->(at*5f).toInt()%8
   at<INTRO_ARRIVAL->intArrayOf(8,9,10,11,12,13,12,11,10,9)[((at-INTRO_ESCAPE)*7f).toInt()%10]
   at<INTRO_ARRIVAL+1f->16+((at-INTRO_ARRIVAL)*8f).toInt().coerceAtMost(7)
   else->24+((at-INTRO_ARRIVAL-1f)*8f).toInt()%8
  }
 }
 Canvas(modifier.clearAndSetSemantics {contentDescription="Four companions lean in from the screen edges"}){
  val t=time()
  val side=minOf(size.width*.64f,270.dp.toPx(),size.height*.36f)
  val enter=if(animated)introEase(t/1.35f)else 1f
  kinds.forEachIndexed {index,_->
   val left=index%2==0
   val top=index<2
   val x=if(left)-side*.46f else size.width-side*.54f
   val y=if(top)side*.04f else size.height-side*1.32f
   val shift=(1-enter)*side*.8f
   val breath=if(animated)sin(t*.85f+index*.45f)*1.1.dp.toPx()else 0f
   val frame=pose(t)
   val previous=pose(t-.125f)
   val meta=metadata[index]
   // Brief frame dissolves soften a pose boundary without changing the authored pixels.
   val currentAlpha=if(animated&&previous!=frame){
    var before=t-.125f;var after=t
    repeat(7){val middle=(before+after)/2;if(pose(middle)==frame)after=middle else before=middle}
    introEase((t-after)/.125f)
   }else 1f
   translate(left=x+if(left)-shift else shift,top=y+breath){
    rotate(if(left)43f else -43f,Offset(side/2,side/2)){
     fun draw(frameIndex:Int,opacity:Float,mode:BlendMode){
      val bounds=meta.getJSONArray("frames").getJSONArray(frameIndex)
      val offset=meta.getJSONArray("offsets").getJSONArray(frameIndex)
      val refWidth=meta.getDouble("reference_width").toFloat()
      val refHeight=meta.getDouble("reference_height").toFloat()
      val ratio=side*.92f/maxOf(refWidth,refHeight)
      drawImage(sheets[index],IntOffset(bounds.getInt(0),bounds.getInt(1)),IntSize(bounds.getInt(2),bounds.getInt(3)),
       IntOffset(((side-refWidth*ratio)/2+offset.getDouble(0)*ratio).roundToInt(),((side-refHeight*ratio)/2+offset.getDouble(1)*ratio).roundToInt()),IntSize((bounds.getInt(2)*ratio).roundToInt(),(bounds.getInt(3)*ratio).roundToInt()),alpha=opacity*enter,filterQuality=FilterQuality.High,blendMode=mode)
     }
     introBlend(Rect(Offset.Zero,Size(side,side)),currentAlpha){current,alpha,mode->draw(if(current)frame else previous,alpha,mode)}
    }
   }
  }
 }
}

@Composable private fun IntroDogsAndMushroom(time:()->Float,animated:Boolean,letters:IntroTitleMetrics,modifier:Modifier) {
 val dogs=art("companion-intro-dogs-v1.png");val dogMeta=introMetadata("dogs")
 val mushroom=art("companion-intro-amanita-v1.png");val mushroomMeta=introMetadata("amanita")
 Canvas(modifier.clearAndSetSemantics {contentDescription="A puppy brings a glowing mushroom to Companion"}){
  val t=time()
  val titleY=size.height*.43f
  val titleWidth=if(letters.width>0)letters.width else size.width*.78f
  val titleLeft=(size.width-titleWidth)/2
  val c=titleLeft+if(letters.width>0)letters.first else titleWidth*.055f
  val n=titleLeft+if(letters.width>0)letters.last else titleWidth*.945f
  val m=titleLeft+if(letters.width>0)letters.m else titleWidth*.275f
  val dogSide=(size.width*.19f).coerceAtMost(82.dp.toPx())*1.5f
  val floor=titleY-18.dp.toPx()
  val release=6.45f
  fun prop(center:Offset,side:Float,angle:Float=0f){
   val phase=if(animated)t*8f else 0f
   val frame=phase.toInt()%8;val previous=(frame+7)%8
   val blend=if(animated)introEase(phase%1f)else 1f
   rotate(angle,center){introBlend(Rect(center-Offset(side,side),center+Offset(side,side)),blend){current,alpha,mode->
    val r=mushroomMeta.getJSONArray("frames").getJSONArray(if(current)frame else previous)
    drawImage(mushroom,IntOffset(r.getInt(0),r.getInt(1)),IntSize(r.getInt(2),r.getInt(3)),IntOffset((center.x-side/2).roundToInt(),(center.y-side/2).roundToInt()),IntSize(side.roundToInt(),side.roundToInt()),alpha=alpha,filterQuality=FilterQuality.High,blendMode=mode)
   }}
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
   val startTime=INTRO_ESCAPE
   val escapeTime=startTime
   if(t>=escapeTime){
    val start=introAnchor(0,INTRO_ESCAPE,animated,Offset(.27f,.5f))+Offset(0f,dogSide*.45f)
    val target=Offset(if(i==0)c else n,floor)
    val duration=INTRO_ARRIVAL-INTRO_ESCAPE
    val p=((t-startTime)/duration).coerceIn(0f,1f)
    val eased=introEase(p)
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
    val previous=dogPose(t-1f/9f)
    val blend=if(animated&&previous!=frame){
     var before=t-1f/9f;var after=t
     repeat(6){val middle=(before+after)/2;if(dogPose(middle)==frame)after=middle else before=middle}
     introEase((t-after)/(1f/9f))
    }else 1f
    val ratio=dogSide/dogMeta.getDouble("reference_width").toFloat()
    val breathe=if(animated&&p>=1f)sin(t*1.1f+i)*.65.dp.toPx()else 0f
    val drawDog:DrawScope.()->Unit={
     fun draw(frameIndex:Int,opacity:Float,mode:BlendMode){
      val r=dogMeta.getJSONArray("frames").getJSONArray(frameIndex)
      val w=r.getInt(2)*ratio;val h=r.getInt(3)*ratio
      drawImage(dogs,IntOffset(r.getInt(0),r.getInt(1)),IntSize(r.getInt(2),r.getInt(3)),IntOffset((position.x-w/2).roundToInt(),(position.y-h+breathe).roundToInt()),IntSize(w.roundToInt(),h.roundToInt()),alpha=opacity*(if(animated)introEase((t-INTRO_ESCAPE)/.25f)else 1f),filterQuality=FilterQuality.High,blendMode=mode)
     }
     introBlend(Rect(position-Offset(dogSide,dogSide*1.5f),position+Offset(dogSide,dogSide*.5f)),blend){current,alpha,mode->draw(if(current)frame else previous,alpha,mode)}
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

@Composable private fun SmoothCompanionTitle(fontSize:TextUnit,onLayout:(TextLayoutResult)->Unit) {
 var firstLetter by remember {mutableStateOf(Rect.Zero)}
 var baseline by remember {mutableFloatStateOf(0f)}
 val title=remember {buildAnnotatedString{withStyle(SpanStyle(color=Color.Transparent)){append("C")};append("OMPANION")}}
 Box(contentAlignment=Alignment.Center){
  Text(title,fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Bold,fontSize=fontSize,maxLines=1,softWrap=false,textAlign=TextAlign.Center,onTextLayout={firstLetter=it.getBoundingBox(0);baseline=it.firstBaseline;onLayout(it)},style=TextStyle(brush=Brush.verticalGradient(listOf(Color(0xFFB2FFE3),Mint,Color(0xFF28B9A0))),shadow=Shadow(Mint.copy(alpha=.55f),Offset.Zero,9f)))
  Canvas(Modifier.matchParentSize().clearAndSetSemantics {}){
   if(firstLetter.width>0f){
    val cap=fontSize.toPx()*.74f
    val stroke=cap*.16f
    val rect=Rect(firstLetter.left+stroke/2,baseline-cap+stroke/2,firstLetter.right-stroke/2,baseline-stroke/2)
    drawArc(Brush.verticalGradient(listOf(Color(0xFFB2FFE3),Mint,Color(0xFF28B9A0))),45f,270f,false,rect.topLeft,rect.size,style=Stroke(stroke,cap=StrokeCap.Round))
    val dot=Offset(rect.center.x+rect.width/2*cos(-PI.toFloat()/4),rect.center.y+rect.height/2*sin(-PI.toFloat()/4))
    drawCircle(Gold,stroke*.47f,dot)
   }
  }
 }
}
