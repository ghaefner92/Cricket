package de.ovgu.imiq.cricket

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.*
import org.json.JSONObject

private val companionKinds=listOf("robot","explorer","creature","naturalist")

@Composable fun CompanionSelector(model:CricketModel,appearance:String,animated:Boolean,onSelect:(String)->Unit) {
 val selected=companionKinds.indexOf(appearance).coerceAtLeast(0)
 val currentSelect by rememberUpdatedState(onSelect)
 fun change(delta:Int){currentSelect(companionKinds[(selected+delta+companionKinds.size)%companionKinds.size])}
 val name=when(appearance) {"naturalist"->model.text("Naturalist","Naturfreundin");"explorer"->model.text("Explorer","Entdecker");"creature"->model.text("Creature","Wesen");else->model.text("Robot","Roboter")}
 val place=when(appearance) {"naturalist"->model.text("Harz forest","Harzwald");"explorer"->model.text("Castle passages","Burggewölbe");"creature"->model.text("Moonlit cemetery","Friedhof im Mondlicht");else->model.text("Neon city","Neonstadt")}
 Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)) {
  Box(Modifier.fillMaxWidth().aspectRatio(4f/3f).clip(RoundedCornerShape(14.dp)).jrpgSceneFrame(animated)
   .semantics {stateDescription="$name · ${selected+1}/4"}
   .pointerInput(selected) {
    var distance=0f
    detectHorizontalDragGestures(onDragStart={distance=0f},onDragCancel={distance=0f},onDragEnd={if(abs(distance)>48.dp.toPx())change(if(distance<0)1 else -1);distance=0f}) {change,amount->change.consume();distance+=amount}
   }) {
   Crossfade(companionKinds[selected],animationSpec=tween(if(animated)420 else 0),label="companion scene") {kind->MeetCompanionScene(kind,animated)}
   JrpgSceneArrow(true,model.text("Previous companion","Vorheriger Begleiter"),animated,{change(-1)},Modifier.align(Alignment.CenterStart).padding(start=4.dp))
   JrpgSceneArrow(false,model.text("Next companion","Nächster Begleiter"),animated,{change(1)},Modifier.align(Alignment.CenterEnd).padding(end=4.dp))
  }
  Text(name,style=MaterialTheme.typography.titleSmall,color=Gold)
  Text(place,style=MaterialTheme.typography.bodySmall,color=MutedText)
  Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
   companionKinds.forEachIndexed {index,_->Box(Modifier.size(if(index==selected)10.dp else 7.dp).clip(CircleShape).background(if(index==selected)Gold else Edge.copy(alpha=.5f)))}
  }
 }
}

@Composable private fun MeetCompanionScene(kind:String,animated:Boolean) {
 val background=art("meet-$kind-background-v1.png")
 val atlas=art("meet-$kind-action-v1.png")
 val context=LocalContext.current
 val metadata=remember(kind){context.assets.open("art/jrpg/meet-atlas.json").bufferedReader().use {JSONObject(it.readText()).getJSONObject(kind)}}
 val lifecycle=LocalLifecycleOwner.current.lifecycle
 var seconds by remember(kind){mutableFloatStateOf(0f)}
 LaunchedEffect(kind,animated,lifecycle) {
  seconds=0f
  if(animated)lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){var start=0L;while(true)withFrameNanos {now->if(start==0L)start=now;seconds=((now-start)/1_000_000_000.0).toFloat()}}
 }
 Box(Modifier.fillMaxSize()) {
  Image(background,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
  Canvas(Modifier.fillMaxSize()) {
   val t=seconds
   val unit=size.width/640f
   scale(unit,pivot=Offset.Zero) {
    val h=size.height/unit
    when(kind) {
     "naturalist"->{
      val glow=.09f+if(animated).035f*sin(t*.8f)else 0f
      drawCircle(Brush.radialGradient(listOf(Gold.copy(alpha=glow),Color.Transparent),Offset(524f,h*.14f),150f),150f,Offset(524f,h*.14f))
      repeat(7){i->val x=(50f+i*91f+if(animated)t*7f else 0f)%640f;val y=(i*43f+if(animated)t*10f else 0f)%(h*.8f);rotate(if(animated)sin(t*.7f+i)*20f else 0f,Offset(x,y)){drawRect(if(i%2==0)Mint.copy(alpha=.65f)else Gold.copy(alpha=.55f),Offset(x,y),Size(4f,7f));drawRect(Ink.copy(alpha=.3f),Offset(x+2,y+1),Size(1f,5f))}}
     }
     "robot"->{
      val x=if(animated)((t*24f)%760f)-60f else 90f;val y=h*.3f
      drawRoundRect(Ink,Offset(x,y),Size(28f,10f),CornerRadius(3f));drawRect(Mint,Offset(x+5,y+2),Size(16f,3f));drawLine(Gold.copy(alpha=.65f),Offset(x-8,y+8),Offset(x,y+8),2f)
      repeat(5){i->val glow=if(animated).18f+.1f*sin(t*.6f+i)else .22f;drawRect(Color(0xFF81DCF6).copy(alpha=glow),Offset(45f+i*125f,h*.2f+i%2*13f),Size(7f,15f))}
     }
     "explorer"->{
      listOf(92f,548f).forEachIndexed {i,x->
       val y=35f+if(animated)25f*(sin(t*.65f+i*2)+1f)else 25f
       drawLine(Pale.copy(alpha=.6f),Offset(x,0f),Offset(x,y),1f)
       drawOval(Ink,Offset(x-4,y-3),Size(8f,10f));drawCircle(Gold,1f,Offset(x-1,y))
       repeat(4){leg->val step=if(animated)sin(t*1.8f+leg)*1.5f else 0f;val ly=y+leg*2f-2;drawLine(Ink,Offset(x-2,ly),Offset(x-8,ly-3+step),1.5f);drawLine(Ink,Offset(x+2,ly),Offset(x+8,ly-3-step),1.5f)}
      }
     }
     else->{
      repeat(3){i->val y=h*(.68f+i*.07f);val shift=if(animated)sin(t*.25f+i)*32f else 0f;drawOval(Pale.copy(alpha=.045f),Offset(-90f+shift,y),Size(750f,25f))}
      repeat(7){i->val x=45f+i*88f;val y=h*(.32f+.25f*((i*7%9)/9f))+if(animated)sin(t*.65f+i)*6f else 0f;val glow=if(animated).4f+.16f*sin(t*.9f+i)else .5f;drawRect(Mint.copy(alpha=glow),Offset(x,y),Size(3f,3f))}
     }
    }
   }
   val frame=if(animated)(((t%8f-1f).coerceAtLeast(0f)*3f).toInt()).coerceIn(0,15)else 0
   val bounds=metadata.getJSONArray("frames").getJSONArray(frame)
   val x=bounds.getInt(0);val y=bounds.getInt(1);val w=bounds.getInt(2);val h=bounds.getInt(3)
   val side=size.height*.86f
   val feet=size.height*.94f
   drawOval(Ink.copy(alpha=.24f),Offset(size.width/2-side*.22f,feet-4.dp.toPx()),Size(side*.44f,8.dp.toPx()))
   val ratio=side/metadata.getDouble("reference_width").toFloat()
   drawImage(atlas,IntOffset(x,y),IntSize(w,h),IntOffset(((size.width-w*ratio)/2).roundToInt(),(feet-metadata.getJSONArray("anchor_y_pixels").getInt(frame)*ratio).roundToInt()),IntSize((w*ratio).roundToInt(),(h*ratio).roundToInt()),filterQuality=FilterQuality.Medium)
  }
 }
}
