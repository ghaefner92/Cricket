package de.ovgu.imiq.cricket

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import kotlin.math.*

/** One finite clock per interaction. Resting rows do not run animation loops. */
@Composable internal fun priorityReactionPhase(event:Int,eventTime:Long,animated:Boolean):Float {
 val phase=remember {Animatable(1f)}
 LaunchedEffect(event,animated){
  val age=(android.os.SystemClock.elapsedRealtime()-eventTime).coerceIn(0L,900L)
  if(event>0&&animated&&age<900){phase.snapTo(age/900f);phase.animateTo(1f,tween((900-age).toInt()))}
  else phase.snapTo(1f)
 }
 return phase.value
}

/** The established avatar with a nod/thumb or a head shake/crossed arms. */
@Composable internal fun PriorityReactionAvatar(appearance:String,direction:Int,phase:Float,de:Boolean,modifier:Modifier) {
 val safe=appearance.takeIf {it in listOf("robot","explorer","creature","naturalist")}?:"robot"
 val image=art("$safe-sheet-v2.png")
 val reacting=phase<1f&&direction!=0
 val state=if(direction>0){if(de)"Zustimmung"else"Approval"}else{if(de)"Verneinung, verschränkte Arme"else"Negation, arms crossed"}
 Canvas(modifier.semantics {if(reacting)stateDescription=state}){
  val side=min(size.width,size.height)
  val wave=if(reacting)sin(phase*PI*2).toFloat()else 0f
  val row=if(reacting&&direction>0)1 else if(reacting)3 else 0
  val col=if(reacting&&direction>0)0 else if(reacting)2 else 0
  val cell=image.width/6;val h=image.height/4
  translate((size.width-side)/2,(size.height-side)/2){
   if(reacting){
    val pulse=sin(phase*PI).toFloat().coerceAtLeast(0f)
    val tone=if(direction>0)Gold else Color(0xFFD7A0FF)
    drawCircle(tone.copy(alpha=.18f*pulse),side*.46f,Offset(side/2,side/2))
    drawCircle(tone.copy(alpha=.7f*pulse),side*.44f,Offset(side/2,side/2),style=Stroke(1.dp.toPx()))
   }
   // Approval nods; negation shakes instead of using a celebration pose.
   translate(left=if(reacting&&direction<0)wave*side*.025f else 0f,top=if(direction>0)abs(wave)*side*.025f else 0f){
    drawImage(image,IntOffset(col*cell,row*h),IntSize(cell,h),IntOffset.Zero,IntSize(side.roundToInt(),side.roundToInt()),filterQuality=FilterQuality.Medium)
    if(reacting){
     scale(side/64f,pivot=Offset.Zero){
      val sleeve=when(safe){"naturalist"->Color(0xFF9A304A);"explorer"->Color(0xFF328C90);"creature"->Color(0xFFAE69CA);else->Mint}
      val hand=when(safe){"naturalist","explorer"->Color(0xFFFFCA8D);"creature"->Color(0xFFC9E783);else->Mint}
      fun arm(a:Offset,b:Offset){drawLine(Ink,a,b,8f,StrokeCap.Round);drawLine(sleeve,a,b,5.5f,StrokeCap.Round)}
      if(direction<0){
       // Cover the old chest gesture with two complete, visibly crossed forearms.
       val x=if(safe=="naturalist")39f else 32f
       arm(Offset(x-10f,37f),Offset(x+8f,43f))
       drawCircle(hand,3f,Offset(x+8f,43f))
       arm(Offset(x+10f,36f),Offset(x-8f,44f))
       drawCircle(hand,3f,Offset(x-8f,44f))
      }else{
       val x=if(safe=="naturalist")53f else 52f
       arm(Offset(x-8f,41f),Offset(x,37f))
       drawRoundRect(Ink,Offset(x-3f,29f),Size(6f,11f),CornerRadius(2f))
       drawRoundRect(hand,Offset(x-2f,30f),Size(4f,9f),CornerRadius(1f))
       drawLine(Ink,Offset(x-2f,34f),Offset(x-3f,27f),4f,StrokeCap.Round)
       drawLine(hand,Offset(x-2f,34f),Offset(x-3f,27f),2.5f,StrokeCap.Round)
      }
     }
    }
   }
  }
 }
}

/** Each pet follows the same interpolated endpoint used to draw its goal bar. */
@Composable internal fun PriorityPetBar(appearance:String,value:Int,direction:Int,phase:Float,animated:Boolean,title:String,de:Boolean,modifier:Modifier=Modifier) {
 val pet=when(appearance){"naturalist"->"dogs";"explorer"->"spider";"creature"->"spirit";else->"drone"}
 val meta=introMetadata(pet);val image=art(meta.getString("file"))
 val progress by animateFloatAsState(((value-1)/6f).coerceIn(0f,1f),tween(if(animated)650 else 0),label="Priority bar")
 Canvas(modifier.fillMaxWidth().height(26.dp).semantics {
  contentDescription=if(value==0){if(de)"$title: Noch nicht bewertet"else"$title: Not rated"}else if(de)"$title: $value von 7"else"$title: $value of 7"
  if(value in 1..7)progressBarRangeInfo=ProgressBarRangeInfo(value.toFloat(),1f..7f,5)
 }){
  val reacting=phase<1f&&direction!=0
  val pulse=if(reacting)sin(phase*PI).toFloat().coerceAtLeast(0f)else 0f
  val tone=if(reacting&&direction<0)Color(0xFFD7A0FF)else Mint
  val radius=12.dp.toPx();val start=radius;val end=(size.width-radius).coerceAtLeast(start)
  val x=start+(end-start)*progress;val y=size.height*.66f
  drawLine(Edge.copy(alpha=.4f),Offset(start,y),Offset(end,y),3.dp.toPx(),StrokeCap.Round)
  if(progress>0f){
   drawLine(tone.copy(alpha=.2f*pulse),Offset(start,y),Offset(x,y),9.dp.toPx(),StrokeCap.Round)
   drawLine(Brush.linearGradient(listOf(tone,if(reacting&&direction>0)Gold else tone),Offset(start,y),Offset(x,y)),Offset(start,y),Offset(x,y),3.dp.toPx(),StrokeCap.Round)
  }
  val center=Offset(x,size.height/2-if(reacting)sin(phase*PI).toFloat()*2.dp.toPx()else 0f)
  if(reacting){
   drawCircle(tone.copy(alpha=.16f*pulse),radius*1.15f,center)
   repeat(4){i->
    val angle=i*PI/2+phase*PI;val distance=radius*(.65f+phase*.6f)
    val p=center+Offset(cos(angle).toFloat()*distance,sin(angle).toFloat()*distance)
    drawLine(if(direction>0)Gold.copy(alpha=pulse)else tone.copy(alpha=pulse),p-Offset(1.dp.toPx(),0f),p+Offset(1.dp.toPx(),0f),1.dp.toPx())
    drawLine(Pale.copy(alpha=pulse),p-Offset(0f,1.dp.toPx()),p+Offset(0f,1.dp.toPx()),1.dp.toPx())
   }
  }
  val frame=if(reacting)(phase*10).toInt().coerceIn(0,meta.getJSONArray("frames").length()-1)else 0
  val rect=meta.getJSONArray("frames").getJSONArray(frame)
  val width=22.dp.toPx();val height=width*rect.getInt(3)/rect.getInt(2)
  rotate(if(reacting)direction*360f*phase else 0f,center){
   drawImage(image,IntOffset(rect.getInt(0),rect.getInt(1)),IntSize(rect.getInt(2),rect.getInt(3)),IntOffset((center.x-width/2).roundToInt(),(center.y-height/2).roundToInt()),IntSize(width.roundToInt(),height.roundToInt()),filterQuality=FilterQuality.Medium)
  }
 }
}
