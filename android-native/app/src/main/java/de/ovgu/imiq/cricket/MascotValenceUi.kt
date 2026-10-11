package de.ovgu.imiq.cricket

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.*

/** Vector portraits of the established mini robot, spider, spirit and puppy.
 * Only the head is drawn. Expression follows the saved mode valence, never HOTCO rank.
 */
@Composable internal fun MascotValenceFace(appearance:String,rating:Int?,animated:Boolean,modifier:Modifier=Modifier,interactionSeconds:Float?=null) {
 val lifecycle=LocalLifecycleOwner.current.lifecycle
 var seconds by remember {mutableFloatStateOf(0f)}
 LaunchedEffect(animated,lifecycle,interactionSeconds!=null) {
  seconds=0f
  if(animated&&interactionSeconds==null)lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
   var start=0L
   while(true)withFrameNanos {now->if(start==0L)start=now;seconds=((now-start)/1_000_000_000.0).toFloat()}
  }
 }
 val expressionSeconds=interactionSeconds?:seconds
 val emotion by animateFloatAsState((rating?:0).coerceIn(-3,3).toFloat(),tween(if(animated)350 else 0),label="Mascot expression")
 Canvas(modifier) {
  val factor=minOf(size.width,size.height)/64f
  translate((size.width-64*factor)/2,(size.height-64*factor)/2) {
   scale(factor,pivot=Offset.Zero) {
    val tone=when {rating==null->MutedText;rating<0->Color(0xFFC8A8E4);rating>0->Mint;else->Gold}
    val wave=if(animated&&rating!=null)sin(expressionSeconds*(if(emotion>0)1.8f else 1.1f))else 0f
    val tilt=if(rating==null)0f else wave*(if(emotion<0)1.1f else .8f)*abs(emotion)/3
    rotate(tilt,pivot=Offset(32f,28f)) {
     translate(top=if(emotion>0)-wave*.7f else 0f) {
      val robot=appearance !in listOf("naturalist","explorer","creature")
      val dark=Color(0xFF121D26)
      val outline=Stroke(1.8f,cap=StrokeCap.Round)
      when(appearance) {
       "naturalist"->{
        // Floppy ears and a cream muzzle belong to the naturalist's black puppy.
        drawOval(Color(0xFF16141E),Offset(4f,10f),Size(19f,31f))
        drawOval(Color(0xFF16141E),Offset(41f,10f),Size(19f,31f))
        drawOval(Brush.radialGradient(listOf(Color(0xFF514550),Color(0xFF29242F)),Offset(27f,17f),31f),Offset(13f,6f),Size(38f,42f))
        drawOval(Color(0xFFEBD3A9),Offset(17f,27f),Size(30f,20f))
        drawOval(Color(0xFFAF8B61),Offset(18f,15f),Size(9f,4f))
        drawOval(Color(0xFFAF8B61),Offset(37f,15f),Size(9f,4f))
        drawOval(dark,Offset(28f,29f),Size(8f,5f))
        drawLine(dark,Offset(32f,33f),Offset(32f,36f),1.1f)
       }
       "explorer"->{
        drawCircle(dark,23f,Offset(32f,27f))
        drawCircle(Brush.radialGradient(listOf(Color(0xFFAA7850),Color(0xFF55372F)),Offset(22f,15f),37f),21.5f,Offset(32f,27f))
        drawOval(Color(0xFFD5AB79).copy(alpha=.55f),Offset(18f,10f),Size(9f,4f))
       }
       "creature"->{
        val head=Path().apply {moveTo(32f,2f);cubicTo(42f,3f,48f,9f,49f,16f);cubicTo(58f,22f,54f,38f,46f,44f);cubicTo(39f,50f,22f,50f,15f,42f);cubicTo(7f,38f,6f,23f,13f,17f);cubicTo(17f,8f,20f,6f,25f,7f);quadraticTo(28f,1f,32f,2f);close()}
        drawPath(head,Mint.copy(alpha=.17f),style=Stroke(5f))
        drawPath(head,Brush.radialGradient(listOf(Color(0xFFB6F2D4),Color(0xFF35B793)),Offset(24f,14f),35f))
        drawOval(Color(0xFFFFFBE7),Offset(15f,11f),Size(34f,34f))
        drawCircle(Pale.copy(alpha=.7f),2.5f,Offset(31f,7f))
       }
       else->{
        // The miniature robot's head: ivory shell, cyan ear pods and dark screen.
        drawCircle(dark,9f,Offset(11f,28f));drawCircle(dark,9f,Offset(53f,28f))
        drawCircle(Color(0xFF93E6E0),6.5f,Offset(11f,28f));drawCircle(Color(0xFF93E6E0),6.5f,Offset(53f,28f))
        drawRoundRect(dark,Offset(12f,7f),Size(40f,40f),CornerRadius(18f))
        drawRoundRect(Brush.linearGradient(listOf(Color(0xFFFFFBE9),Color(0xFFB0E4E0))),Offset(14f,9f),Size(36f,36f),CornerRadius(16f))
        drawRoundRect(dark,Offset(17f,15f),Size(30f,25f),CornerRadius(11f))
        drawOval(Color(0xFF82E8E8),Offset(21f,1f),Size(22f,5f),style=Stroke(1.5f))
        drawLine(Color(0xFF82E8E8),Offset(32f,6f),Offset(32f,9f),1.5f)
       }
      }
      val eyeColor=if(robot)Color(0xFF81F1F2)else dark
      val blinkPhase=expressionSeconds%4.6f
      val blink=if(animated&&blinkPhase in 3.9f..4.12f)abs(blinkPhase-4.01f)/.11f else 1f
      val happy=emotion>=2.5f&&(!animated||expressionSeconds%3.4f<1.5f)
      for(x in listOf(23f,41f)) {
       if(appearance=="explorer")drawOval(Color(0xFFFFF4DE),Offset(x-5f,19f),Size(10f,13f))
       if(happy) {
        val eye=Path().apply {moveTo(x-3.4f,26f);quadraticTo(x,20f,x+3.4f,26f)}
        drawPath(eye,eyeColor,style=Stroke(2.2f,cap=StrokeCap.Round))
       }else {
        val h=(if(robot)8f else 8.5f)*blink.coerceAtLeast(.08f)
        drawOval(eyeColor,Offset(x-2.7f,25f-h/2),Size(5.4f,h))
        if(!robot&&blink>.8f)drawCircle(Pale,1.1f,Offset(x-.8f,22.5f))
       }
      }
      if(emotion<0) {
       // Increasing concern: raised inner brows, downturned mouth and quieter motion.
       val worry=-emotion/3
       drawLine(if(robot)eyeColor else dark,Offset(19f,18f),Offset(27f,18f-3*worry),1.4f,StrokeCap.Round)
       drawLine(if(robot)eyeColor else dark,Offset(37f,18f-3*worry),Offset(45f,18f),1.4f,StrokeCap.Round)
      }
      val mouthY=if(appearance=="naturalist")38f else 34f
      val mouth=Path().apply {moveTo(26f,mouthY);quadraticTo(32f,mouthY+emotion*2.5f,38f,mouthY)}
      drawPath(mouth,if(robot)eyeColor else dark,style=Stroke(if(robot)1.6f else 1.8f,cap=StrokeCap.Round))
      if(emotion>0&&!robot) {
       drawOval(Color(0xFFF3A4AD).copy(alpha=.4f+.1f*emotion),Offset(15f,30f),Size(7f,4f))
       drawOval(Color(0xFFF3A4AD).copy(alpha=.4f+.1f*emotion),Offset(42f,30f),Size(7f,4f))
      }
      if(rating==null)drawCircle(Panel.copy(alpha=.3f),22f,Offset(32f,27f))
     }
    }
    for(step in -3..3) {
     val point=Offset(8f+(step+3)*8f,58f)
     drawCircle(if(step==rating)tone else Edge.copy(alpha=.4f),if(step==rating)3.2f else 1.7f,point)
     if(step==rating)drawCircle(Pale,4.3f,point,style=Stroke(.8f))
    }
   }
  }
 }
}
