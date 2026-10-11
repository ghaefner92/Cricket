package de.ovgu.imiq.cricket

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import kotlin.math.*

private val HeartRed=Color(0xFFFF5978)
private val DisgustPurple=Color(0xFFB49AE8)

@Composable private fun valenceReactionPhase(event:Int,animated:Boolean):Float {
 val phase=remember {Animatable(1f)}
 LaunchedEffect(event,animated) {
  if(event>0&&animated){phase.snapTo(0f);phase.animateTo(1f,tween(2400,easing=LinearEasing))}else phase.snapTo(1f)
 }
 return phase.value
}

/** The reported valence is the only expression input: no preference or recommendation scoring. */
internal fun valenceFeeling(rating:Int?,de:Boolean):String = when(rating) {
 -3->if(de)"Sehr unangenehm"else"Very unpleasant"
 -2->if(de)"Unangenehm"else"Unpleasant"
 -1->if(de)"Etwas unangenehm"else"Slightly unpleasant"
 0->if(de)"Neutral"else"Neutral"
 1->if(de)"Etwas angenehm"else"Slightly pleasant"
 2->if(de)"Angenehm"else"Pleasant"
 3->if(de)"Sehr angenehm"else"Very pleasant"
 else->if(de)"Noch nicht bewertet"else"Not rated yet"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun ValenceEditor(appearance:String,mode:String,rating:Int?,animated:Boolean,de:Boolean,onPrevious:()->Unit={},onNext:()->Unit={},prompt:String?=null,showModeNavigation:Boolean=true,onChange:(Int)->Unit) {
 key(mode) {
  var event by remember {mutableIntStateOf(0)}
  val phase=valenceReactionPhase(event,animated)
  fun choose(value:Int) {
   val next=value.coerceIn(-3,3)
   if(next!=rating){event++;onChange(next)}
  }
  val tone=when {rating==null->MutedText;rating<0->DisgustPurple;rating>0->HeartRed;else->Gold}
  ValenceWorld(appearance,mode,rating,animated,phase,de,onPrevious,onNext,showModeNavigation)
  Copy(prompt?:if(de)"Denke an die Nutzung dieses Verkehrsmittels auf deinen üblichen Wegen. Wie angenehm oder unangenehm fühlt sie sich insgesamt an? 0 bedeutet neutral, nicht unbeantwortet."else"Think about using this mode on your usual journeys. Overall, how pleasant or unpleasant does it feel to you? 0 means neutral, not unanswered.")
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
   IconButton({choose((rating?:0)-1)},Modifier.size(48.dp).semantics {contentDescription=if(de)"Eine Stufe Richtung unangenehm"else"One step towards unpleasant"},enabled=rating!=-3) {
    ValenceHeart(false,rating,phase,Modifier.size(44.dp),animated)
   }
   Slider((rating?:0).toFloat(),{choose(it.roundToInt())},Modifier.weight(1f).semantics(mergeDescendants=true) {
    contentDescription=if(de)"Angenehm–unangenehm-Skala: minus 3 sehr unangenehm, 0 neutral, plus 3 sehr angenehm"else"Pleasant–unpleasant scale: minus 3 very unpleasant, 0 neutral, plus 3 very pleasant"
    stateDescription=valenceFeeling(rating,de)
   },valueRange=-3f..3f,steps=5,thumb={
    Canvas(Modifier.size(24.dp,32.dp)) {
     val gem=Path().apply {moveTo(size.width/2,0f);lineTo(size.width,size.height/2);lineTo(size.width/2,size.height);lineTo(0f,size.height/2);close()}
     drawPath(gem,Brush.linearGradient(listOf(Pale,tone,tone.copy(alpha=.8f))))
     drawPath(gem,Gold,style=Stroke(1.dp.toPx()))
     drawLine(Pale.copy(alpha=.65f),Offset(size.width/2,3.dp.toPx()),Offset(size.width/2,size.height-3.dp.toPx()),1.dp.toPx())
    }
   },track={state->
    Canvas(Modifier.fillMaxWidth().height(16.dp)) {
     val y=size.height/2;val inset=2.dp.toPx();val width=(size.width-inset*2).coerceAtLeast(1f)
     drawRoundRect(Ink,cornerRadius=CornerRadius(3.dp.toPx()))
     drawRoundRect(Gold.copy(alpha=.6f),cornerRadius=CornerRadius(3.dp.toPx()),style=Stroke(1.dp.toPx()))
     val fraction=(state.value+3)/6f
     drawLine(tone.copy(alpha=.6f),Offset(inset,y),Offset(inset+width*fraction,y),4.dp.toPx(),StrokeCap.Round)
     repeat(7){i->drawRect(if(i==3)Gold else Pale.copy(alpha=.7f),Offset(inset+width*i/6-1.dp.toPx(),y-1.dp.toPx()),Size(2.dp.toPx(),2.dp.toPx()))}
    }
   })
   IconButton({choose((rating?:0)+1)},Modifier.size(48.dp).semantics {contentDescription=if(de)"Eine Stufe Richtung angenehm"else"One step towards pleasant"},enabled=rating!=3) {
    ValenceHeart(true,rating,phase,Modifier.size(44.dp),animated)
   }
  }
  Row(Modifier.fillMaxWidth().padding(horizontal=48.dp),horizontalArrangement=Arrangement.SpaceBetween) {
   (-3..3).forEach {value->Text(if(value>0)"+$value"else"$value",style=MaterialTheme.typography.bodySmall,color=if(value==rating)tone else MutedText)}
  }
  Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally) {
   Text(if(rating==null)"—"else if(rating>0)"+$rating"else"$rating",color=tone,style=MaterialTheme.typography.titleMedium)
   Text(valenceFeeling(rating,de),color=tone,style=MaterialTheme.typography.bodySmall)
  }
  TextButton({choose(0)},Modifier.fillMaxWidth().semantics {contentDescription=if(de)"Neutral wählen · 0"else"Choose neutral · 0"}){Text(if(de)"Neutral wählen · 0"else"Choose neutral · 0")}
 }
}

@Composable private fun ValenceHeart(positive:Boolean,rating:Int?,phase:Float,modifier:Modifier,animated:Boolean) {
 val intensity=(if(positive)(rating?:0).coerceAtLeast(0)else -(rating?:0).coerceAtMost(0))/3f
 val amount by animateFloatAsState(intensity,tween(if(animated)500 else 0),label="Heart intensity")
 Canvas(modifier) {
  val active=phase<1&&intensity>0
  val beat=if(active&&positive)sin(phase*PI*6).toFloat()*sin(phase*PI).toFloat()*.09f*amount else 0f
  val side=min(size.width,size.height)*(if(positive).66f+amount*.24f+beat else .82f-amount*.24f)
  val center=Offset(size.width/2,size.height/2)
  val color=if(positive)lerp(MutedText,HeartRed,amount)else lerp(MutedText,DisgustPurple,amount)
  if(active)drawCircle(color.copy(alpha=.15f*sin(phase*PI).toFloat()),size.width*.49f,center)
  translate(center.x-side/2,center.y-side/2) {
   scale(side/40f,pivot=Offset.Zero) {
    val heart=Path().apply {moveTo(20f,36f);cubicTo(12f,29f,2f,22f,2f,13f);cubicTo(2f,2f,14f,0f,20f,9f);cubicTo(26f,0f,38f,2f,38f,13f);cubicTo(38f,22f,28f,29f,20f,36f);close()}
    if(positive){drawPath(heart,color);drawOval(Pale.copy(alpha=.5f),Offset(8f,7f),Size(7f,4f))}
    else {
     clipPath(heart) {
      drawRect(color)
      val crack=Path().apply {moveTo(23f,-1f);lineTo(18f,12f);lineTo(24f,19f);lineTo(16f,26f);lineTo(20f,41f)}
      drawPath(crack,Panel,style=Stroke(2.5f+amount*3f,cap=StrokeCap.Round,join=StrokeJoin.Miter))
     }
    }
   }
  }
 }
}
