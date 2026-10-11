package de.ovgu.imiq.cricket

import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.dp
import kotlin.math.*

/** Animated foreground details preserve the authored landmarks and their alignment. */
internal fun DrawScope.drawValenceEnvironment(mode:String,time:Float) {
 val w=size.width;val h=size.height
 // Slow clouds and migrating birds above the buildings, independent of heart input.
 repeat(3) {i->
  val x=w*((i*.37f+time*.009f)%1.22f-.11f)
  val y=h*(.105f+(i%2)*.075f)
  val cloud=Color(0xFFFFF5DE).copy(alpha=.32f)
  drawOval(cloud,Offset(x,y),Size(w*.105f,h*.023f))
  drawOval(cloud,Offset(x+w*.028f,y-h*.015f),Size(w*.042f,h*.04f))
 }
 repeat(2) {i->
  val x=w*((time*.024f+i*.15f)%1.3f-.15f)
  val y=h*(.2f+i*.045f)+sin(time*.7f+i)*h*.015f
  val wing=(.004f+.007f*abs(sin(time*3f+i)))*h
  val bird=Color(0xFF394654).copy(alpha=.65f)
  drawLine(bird,Offset(x-w*.009f,y-wing),Offset(x,y),.8.dp.toPx(),StrokeCap.Round)
  drawLine(bird,Offset(x,y),Offset(x+w*.009f,y-wing),.8.dp.toPx(),StrokeCap.Round)
 }
 when(mode) {
  "bike"->repeat(22) {i->
   val y=h*(.64f+(i%6)*.025f)
   val x=w*((i*.117f+time*.016f)%1f)
   val wave=Path().apply {
    moveTo(x,y)
    cubicTo(x+w*.012f,y+sin(time*1.5f+i)*h*.003f,x+w*.025f,y-h*.004f,x+w*.04f,y)
   }
   drawPath(wave,Color(0xFFFFE7AB).copy(alpha=.22f+.15f*sin(time*.8f+i)),style=Stroke(1.dp.toPx()))
  }
  "pt"->repeat(7) {i->
   val x=w*(.19f+i*.085f);val y=h*(.34f+(i%2)*.105f)
   val alpha=(.12f+.18f*sin(time*.7f+i)).coerceAtLeast(0f)
   val glint=Path().apply {moveTo(x,y);lineTo(x+w*.018f,y);lineTo(x-w*.004f,y+h*.065f);lineTo(x-w*.022f,y+h*.065f);close()}
   drawPath(glint,Color(0xFFFFEAB8).copy(alpha=alpha))
  }
  "car"->repeat(4) {i->
   val x=w*(.53f+i*.029f);val y=h*.60f
   val flag=Path().apply {moveTo(x,y);lineTo(x+w*.012f,y+sin(time*1.4f+i)*h*.006f);lineTo(x+w*.009f,y+h*.022f);lineTo(x,y+h*.019f);close()}
   drawPath(flag,Color(0xFFF2B45B).copy(alpha=.6f))
  }
 }
 // Leaves drift near the park trees and the library's street trees.
 if(mode=="walk"||mode=="pt")repeat(7) {i->
  val p=(time*.065f+i*.151f)%1f
  val side=if(i%2==0).08f else .85f
  val x=w*(side+.035f*sin(time*.7f+i));val y=h*(.42f+p*.48f)
  rotate(time*28f+i*37f,Offset(x,y)) {
   drawOval(Color(if(mode=="walk")0xFFB7D16B else 0xFFD3B96B).copy(alpha=.62f*sin(p*PI).toFloat()),Offset(x,y),Size(3.dp.toPx(),1.5.dp.toPx()))
  }
 }
}
