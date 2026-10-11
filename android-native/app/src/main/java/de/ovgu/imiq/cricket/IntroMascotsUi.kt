package de.ovgu.imiq.cricket

import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import org.json.JSONObject
import kotlin.math.*

private fun DrawScope.mascotFrame(image:ImageBitmap,meta:JSONObject,frame:Int,center:Offset,width:Float,opacity:Float=1f){
 val r=meta.getJSONArray("frames").getJSONArray(frame)
 val height=width*r.getInt(3)/r.getInt(2)
 drawImage(image,IntOffset(r.getInt(0),r.getInt(1)),IntSize(r.getInt(2),r.getInt(3)),
  IntOffset((center.x-width/2).roundToInt(),(center.y-height/2).roundToInt()),IntSize(width.roundToInt().coerceAtLeast(1),height.roundToInt().coerceAtLeast(1)),alpha=opacity,filterQuality=FilterQuality.Medium)
}

/** A bounded authored pose loop with an eased, short dissolve between cells. */
private fun DrawScope.mascotPose(image:ImageBitmap,meta:JSONObject,time:Float,animated:Boolean,center:Offset,width:Float,opacity:Float=1f,settle:Boolean=true){
 val phase=if(animated)time.coerceAtLeast(0f)*2.4f else 0f
 val resting=settle&&phase>=12f
 val index=if(resting)8+(phase.toInt()-12)%4 else phase.toInt()%12
 val previous=if(resting&&index==8)11 else (index+11)%12
 val blend=if(animated)introEase((phase%1f)/.24f)else 1f
 if(blend<1f)mascotFrame(image,meta,previous,center,width,opacity*(1-blend))
 mascotFrame(image,meta,index,center,width,opacity*blend)
}

@Composable internal fun IntroCompanionMascots(t:Float,animated:Boolean,letters:IntroTitleMetrics,launchBounds:Rect,modifier:Modifier){
 val spider=art("companion-intro-spider-v1.png");val spiderMeta=introMetadata("spider")
 val drone=art("companion-intro-drone-v1.png");val droneMeta=introMetadata("drone")
 val spirit=art("companion-intro-spirit-v1.png");val spiritMeta=introMetadata("spirit")
 val equationPaint=remember {Paint(Paint.ANTI_ALIAS_FLAG).apply{typeface=Typeface.MONOSPACE}}
 Canvas(modifier.clearAndSetSemantics {contentDescription="Explorer's spider hangs from the O; the robot's pet calculates on the N; a magical spirit circles the start button"}){
  val titleY=size.height*.43f
  val titleWidth=if(letters.width>0)letters.width else size.width*.78f
  val titleLeft=(size.width-titleWidth)/2
  val o=titleLeft+if(letters.width>0)letters.o else titleWidth*.165f
  val n=titleLeft+if(letters.width>0)letters.last else titleWidth*.945f
  // The spider climbs from the explorer, then hangs on a gently swaying silk thread.
  val spiderStart=2.6f
  if(!animated||t>=spiderStart){
   val p=if(animated)introEase((t-spiderStart)/3.2f)else 1f
   val anchor=Offset(o,titleY-9.dp.toPx())
   val sway=if(animated&&p>=1f)sin((t-5.8f)*1.1f)*4.dp.toPx()else 0f
   val end=Offset(o+sway,titleY+32.dp.toPx())
   val start=Offset(10.dp.toPx(),size.height*.72f)
   val position=Offset(start.x+(end.x-start.x)*p,start.y+(end.y-start.y)*p)
   if(p>.72f){
    val silk=Path().apply{moveTo(anchor.x,anchor.y);quadraticTo(anchor.x+sway*.25f,(anchor.y+position.y)/2,position.x,position.y)}
    drawPath(silk,Pale.copy(alpha=introEase((p-.72f)/.28f)*.8f),style=Stroke(1.dp.toPx()))
    if(p>=1f){ // Small fan of web strands attaches to the letter without covering its glyph.
     for(j in -1..1)drawLine(Pale.copy(alpha=.45f),Offset(anchor.x+j*7.dp.toPx(),anchor.y),Offset(anchor.x,anchor.y+9.dp.toPx()),.6.dp.toPx())
    }
   }
   mascotPose(spider,spiderMeta,t-spiderStart,animated,position,34.dp.toPx(),introEase((t-spiderStart)/.4f).let{if(animated)it else 1f})
  }
  // The tiny hovering pet takes the place previously occupied by the second puppy.
  val robotStart=2.1f
  if(!animated||t>=robotStart){
   val p=if(animated)introEase((t-robotStart)/3.3f)else 1f
   val target=Offset(n,titleY-42.dp.toPx())
   val start=Offset(size.width-26.dp.toPx(),size.height*.16f)
   val hover=if(animated)sin(t*1.3f)*1.5.dp.toPx()else 0f
   val position=Offset(start.x+(target.x-start.x)*p,start.y+(target.y-start.y)*p+hover)
   mascotPose(drone,droneMeta,t-robotStart,animated,position,54.dp.toPx())
   if(p>=1f){
    val alpha=if(animated)introEase((t-5.4f)/.8f)else 1f
    // Classic HOTCO activation rule, equivalent to its sign-dependent update.
    // a′ is the next cycle; n⁺=max(net,0), n⁻=max(-net,0), bounds [-1,1].
    // This historical presentation is decorative; the online HOTCO-CT solver
    // continues using its existing continuous field and node-specific bounds.
    val equation="a′ⱼ=(1−d)aⱼ+(1−aⱼ)nⱼ⁺−(aⱼ+1)nⱼ⁻"
    equationPaint.textSize=9.sp.toPx()
    val padding=10.dp.toPx()
    val w=minOf(equationPaint.measureText(equation)+padding*2,size.width-24.dp.toPx());val h=26.dp.toPx()
    if(equationPaint.measureText(equation)>w-padding*2)equationPaint.textSize*=(w-padding*2)/equationPaint.measureText(equation)
    val x=(n-w*.74f).coerceIn(12.dp.toPx(),size.width-w-12.dp.toPx())
    val y=position.y-49.dp.toPx()
    val cut=4.dp.toPx()
    val screen=Path().apply{moveTo(x+cut,y);lineTo(x+w-cut,y);lineTo(x+w,y+cut);lineTo(x+w,y+h-cut);lineTo(x+w-cut,y+h);lineTo(x+cut,y+h);lineTo(x,y+h-cut);lineTo(x,y+cut);close()}
    drawPath(screen,Color(0xFF071D2A).copy(alpha=.97f*alpha))
    clipPath(screen){
     var scan=y+2.dp.toPx()
     while(scan<y+h){drawLine(Mint.copy(alpha=.055f*alpha),Offset(x,scan),Offset(x+w,scan),.5.dp.toPx());scan+=3.dp.toPx()}
    }
    drawPath(screen,Mint.copy(alpha=.14f*alpha),style=Stroke(4.dp.toPx()))
    drawPath(screen,Mint.copy(alpha=.8f*alpha),style=Stroke(.8.dp.toPx()))
    drawLine(Mint.copy(alpha=alpha),Offset(x+cut,y),Offset(x+cut+10.dp.toPx(),y),1.6.dp.toPx())
    drawLine(Mint.copy(alpha=alpha),Offset(x+w-cut-10.dp.toPx(),y+h),Offset(x+w-cut,y+h),1.6.dp.toPx())
    drawLine(Mint.copy(alpha=.55f*alpha),Offset(position.x,y+h),Offset(position.x,position.y-20.dp.toPx()),.8.dp.toPx())
    equationPaint.color=Mint.copy(alpha=alpha).toArgb()
    val native=drawContext.canvas.nativeCanvas
    val reveal=if(animated)((t-5.4f)/1.4f).coerceIn(0f,1f)else 1f
    val baseline=y+h/2-(equationPaint.ascent()+equationPaint.descent())/2
    native.drawText(equation.take((equation.length*reveal).toInt()),x+padding,baseline,equationPaint)
   }
  }
  // Geometry comes from the real button, so the trail never covers its label.
  if(launchBounds.width>0&&(!animated||t>=4.5f)){
   val radiusX=launchBounds.width/2+22.dp.toPx();val radiusY=launchBounds.height/2+20.dp.toPx()
   val center=launchBounds.center
   val arrival=if(animated)introEase((t-4.5f)/2.4f)else 1f
   val theta=if(animated)((t-6.9f).coerceAtLeast(0f)*.68f)else 0f
   fun orbit(angle:Float)=Offset(center.x+radiusX*cos(angle),center.y+radiusY*sin(angle))
   val target=orbit(theta)
   val origin=Offset(size.width-20.dp.toPx(),size.height*.73f)
   val position=if(arrival<1f)Offset(origin.x+(target.x-origin.x)*arrival,origin.y+(target.y-origin.y)*arrival)else target
   if(animated&&arrival>=1f){
    for(i in 20 downTo 1){
     val dot=orbit(theta-i*.045f)
     val fade=(1f-i/21f)*.55f
     drawCircle(Mint.copy(alpha=fade*.2f),8.dp.toPx(),dot)
     drawCircle(Mint.copy(alpha=fade),2.dp.toPx(),dot)
    }
   }
   drawCircle(Brush.radialGradient(listOf(Mint.copy(alpha=.12f),Color.Transparent),position,27.dp.toPx()),27.dp.toPx(),position)
   mascotPose(spirit,spiritMeta,t-4.5f,animated,position,38.dp.toPx())
  }
 }
}

@Composable internal fun AnimatedUniversityBrand(t:Float,animated:Boolean,modifier:Modifier){
 val context=LocalContext.current
 val logo=remember{context.assets.open("branding/companion-ovgu-plate-v3.png").use{BitmapFactory.decodeStream(it).asImageBitmap()}}
 val portrait=art("companion-intro-portrait-v2.png");val meta=introMetadata("portrait")
 Canvas(modifier.semantics{contentDescription="Otto-von-Guericke-Universität Magdeburg · Intelligenter Mobilitätsraum"}){
  val ratio=minOf(size.width/2051f,size.height/443f)
  val width=2051f*ratio;val height=443f*ratio
  val x=(size.width-width)/2;val y=(size.height-height)/2
  // One immutable plate keeps all four purple corners aligned beneath the portrait.
  drawImage(logo,IntOffset(88,171),IntSize(2051,443),IntOffset(x.roundToInt(),y.roundToInt()),IntSize(width.roundToInt(),height.roundToInt()),filterQuality=FilterQuality.Medium)
  mascotPose(portrait,meta,t*.62f,animated,Offset(x+268*ratio,y+height*.51f),height*1.27f,settle=false)
 }
}
