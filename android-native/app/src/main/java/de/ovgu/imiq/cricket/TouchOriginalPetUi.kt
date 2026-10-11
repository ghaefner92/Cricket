package de.ovgu.imiq.cricket

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.atan2
import kotlin.math.PI

@Composable internal fun TouchOriginalPet(model:CricketModel,modifier:Modifier=Modifier,aim:Offset?=null,selector:Boolean=false,aimedKey:String?=null){
 OriginalPet(model.companion().optString("appearance","robot"),model.settings().optString("motion")=="full",model.de,modifier,aim,selector,aimedKey)
}

/** Draw only unchanged pixels from the established body atlas. No generated face, mesh or reticle. */
@Composable internal fun OriginalPet(appearance:String,animated:Boolean,de:Boolean,modifier:Modifier=Modifier,aim:Offset?=null,selector:Boolean=false,aimedKey:String?=null){
 val pet=originalPetKey(appearance);val metadata=introMetadata(pet);val image=art(metadata.getString("file"))
 val cells=remember(pet){metadata.getJSONArray("frames").let {a->List(a.length()){i->a.getJSONArray(i).let {c->IntArray(4){c.getInt(it)}}}}}
 val bounds by produceState<PetSpriteBounds?>(null,pet,image){
  value=withContext(Dispatchers.Default){
   val bitmap=image.asAndroidBitmap();val pixels=IntArray(bitmap.width*bitmap.height)
   bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
   originalPetBounds(pixels,bitmap.width,bitmap.height,cells)
  }
 }
 val lifecycle=LocalLifecycleOwner.current.lifecycle;val root=LocalView.current
 var visible by remember {mutableStateOf(true)}
 var reaction by remember(pet){mutableIntStateOf(0)}
 var interaction by remember(pet){mutableIntStateOf(0)}
 var seconds by remember(pet){mutableFloatStateOf(0f)}
 fun react(value:Int){reaction=value;interaction++}
 LaunchedEffect(aimedKey,selector){if(selector)react(if(aimedKey!=null)1 else 0)}
 LaunchedEffect(pet,animated,visible,interaction,lifecycle){
  seconds=0f
  if(visible)lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){
   val started=android.os.SystemClock.elapsedRealtime()
   while(animated||reaction!=0){
    delay(if(animated)100 else 1400)
    seconds=(android.os.SystemClock.elapsedRealtime()-started)/1000f
    if(reaction!=0&&seconds>=1.4f){reaction=0;seconds=0f;interaction++;break}
   }
  }
 }
 fun text(en:String,german:String)=if(de)german else en
 val name=when(pet){"dogs"->text("Puppy","Hündchen");"spider"->text("Spider","Spinne");"spirit"->text("Spirit","Geist");else->text("Mini drone","Minidrohne")}
 Canvas(modifier.onGloballyPositioned {c->val b=c.boundsInWindow();visible=b.height>0&&b.bottom>0&&b.top<root.height&&b.right>0&&b.left<root.width}
  .semantics {
   contentDescription=text("$name · Original full-body sprite","$name · Originalsprite mit ganzem Körper")
   stateDescription=when(reaction){1->text("Happy","Fröhlich");2->text("Curious","Neugierig");else->text("Resting","Ruhend")}
   if(!selector){
    onClick(text("Happy reaction","Fröhliche Reaktion")){react(1);true}
    onLongClick(text("Curious reaction","Neugierige Reaktion")){react(2);true}
   }
  }.pointerInput(pet,selector){if(!selector)detectTapGestures(onTap={react(1)},onLongPress={react(2)})}){
  val pose=originalPetPose(pet,reaction,seconds,animated);val cell=cells[pose.frame]
  val b=bounds?:PetSpriteBounds(0,0,cell[2],cell[3])
  val right=minOf(b.right,cell[2]);val bottom=minOf(b.bottom,cell[3])
  val w=right-b.left;val h=bottom-b.top
  val fit=minOf(size.width/w,size.height/h)*.9f
  val targetW=(w*fit).roundToInt();val targetH=(h*fit).roundToInt()
  val centre=Offset(size.width/2,size.height/2)
  val aimRotation=if(aim!=null&&aim.getDistance()>size.width*.12f)(atan2(aim.x.toDouble(),-aim.y.toDouble())*180/PI).toFloat()else 0f
  rotate(if(aim!=null)aimRotation else pose.tilt,centre){
   drawImage(image,IntOffset(cell[0]+b.left,cell[1]+b.top),IntSize(w,h),
    IntOffset(((size.width-targetW)/2).roundToInt(),((size.height-targetH)/2-pose.lift*size.height).roundToInt()),IntSize(targetW,targetH),filterQuality=FilterQuality.Medium)
  }
 }
}
