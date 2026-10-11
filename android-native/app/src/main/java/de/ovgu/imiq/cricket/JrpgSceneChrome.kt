package de.ovgu.imiq.cricket

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.sin

/** Shared gentle scene clock; no work while the activity is in the background. */
@Composable internal fun jrpgSceneTime(animated:Boolean):Float {
 val lifecycle=LocalLifecycleOwner.current.lifecycle
 var seconds by remember {mutableFloatStateOf(0f)}
 LaunchedEffect(animated,lifecycle){
  seconds=0f
  if(animated)lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){var start=0L;while(true)withFrameNanos {now->if(start==0L)start=now;seconds=((now-start)/1_000_000_000.0).toFloat()}}
 }
 return seconds
}

/** Code-drawn pixel ornaments extend Cricket's existing icon system. */
@Composable internal fun Modifier.jrpgSceneFrame(animated:Boolean):Modifier {
 val seconds=jrpgSceneTime(animated)
 return drawWithContent {
  drawContent()
  val u=1.dp.toPx();val pad=3*u
  drawRoundRect(Ink.copy(alpha=.6f),Offset(pad,pad),Size(size.width-2*pad,size.height-2*pad),CornerRadius(12*u),style=Stroke(4*u))
  drawRoundRect(Gold.copy(alpha=.75f),Offset(pad,pad),Size(size.width-2*pad,size.height-2*pad),CornerRadius(12*u),style=Stroke(u))
  listOf(Offset(0f,0f),Offset(size.width,0f),Offset(0f,size.height),Offset(size.width,size.height)).forEachIndexed {i,corner->
   translate(corner.x,corner.y){scale(if(i%2==0)1f else -1f,if(i<2)1f else -1f,pivot=Offset.Zero){
    drawRect(Ink,Offset(2*u,7*u),Size(6*u,24*u));drawRect(Ink,Offset(7*u,2*u),Size(24*u,6*u))
    drawRect(Gold,Offset(4*u,9*u),Size(2*u,18*u));drawRect(Gold,Offset(9*u,4*u),Size(18*u,2*u))
    drawRect(Pale,Offset(7*u,7*u),Size(5*u,5*u));drawRect(Mint,Offset(9*u,9*u),Size(3*u,3*u))
    drawRect(Gold.copy(alpha=.7f),Offset(14*u,9*u),Size(5*u,2*u));drawRect(Gold.copy(alpha=.7f),Offset(9*u,14*u),Size(2*u,5*u))
   }}
  }
  val x=if(animated)20*u+(size.width-40*u)*((seconds*.075f)%1f)else size.width*.35f
  val alpha=if(animated).45f+.1f*sin(seconds*.9f)else .5f
  drawRect(Pale.copy(alpha=alpha),Offset(x,2*u),Size(9*u,u))
 }
}

@Composable internal fun JrpgSceneArrow(previous:Boolean,label:String,animated:Boolean,onClick:()->Unit,modifier:Modifier=Modifier) {
 val interaction=remember {MutableInteractionSource()}
 val pressed by interaction.collectIsPressedAsState()
 val scale by animateFloatAsState(if(pressed).92f else 1f,label="JRPG arrow press",animationSpec=androidx.compose.animation.core.tween(if(animated)140 else 0))
 val shape=RoundedCornerShape(16.dp)
 IconButton(onClick,modifier.size(48.dp).semantics {contentDescription=label},interactionSource=interaction) {
  Box(Modifier.size(42.dp).graphicsLayer {scaleX=scale;scaleY=scale;shadowElevation=if(pressed)0f else 3.dp.toPx();this.shape=shape;clip=true}
   .background(Brush.verticalGradient(listOf(if(pressed)Gold.copy(alpha=.9f)else Panel.copy(alpha=.96f),Ink.copy(alpha=.96f))),shape)
   .border(1.dp,if(pressed)Pale else Gold.copy(alpha=.75f),shape),contentAlignment=Alignment.Center) {
   PixelIcon(if(previous)"previous"else"next",Modifier.size(25.dp))
   Canvas(Modifier.fillMaxSize()){val u=1.dp.toPx();drawRect(Mint.copy(alpha=.65f),Offset(8*u,5*u),Size(10*u,u));drawRect(Gold.copy(alpha=.5f),Offset(size.width-8*u,size.height-15*u),Size(u,7*u))}
  }
 }
}
