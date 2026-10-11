package de.ovgu.imiq.cricket

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

internal fun petPortraitKind(appearance:String)=appearance.takeIf {it in listOf("naturalist","explorer","robot","creature")}?:"robot"

/** Touch expressions are decorative; never questionnaire answers or inferred feelings. */
@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun TouchPetPortrait(model:CricketModel,modifier:Modifier=Modifier) {
 val kind=petPortraitKind(model.companion().optString("appearance","robot"))
 val image=art("pet-$kind-face-3d-v1.png")
 val animated=model.settings().optString("motion")=="full"
 val lifecycle=LocalLifecycleOwner.current.lifecycle
 var reaction by remember(kind) {mutableIntStateOf(0)}
 var gesture by remember(kind) {mutableIntStateOf(0)}
 var frame by remember(kind) {mutableIntStateOf(0)}
 LaunchedEffect(kind,reaction,gesture,animated,lifecycle) {
  frame=0
  lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
   if(reaction==0) {
    if(animated)while(true){delay(3400);frame=1;delay(140);frame=2;delay(140);frame=0}
   }else {
    if(animated)for(step in listOf(0,1,2,1,2,1,0)){frame=step;delay(150)}
    else {frame=1;delay(1100)}
    reaction=0;frame=0
   }
  }
 }
 val name=when(kind){"naturalist"->model.text("Puppy","Hündchen");"explorer"->model.text("Spider","Spinne");"creature"->model.text("Spirit","Geist");else->model.text("Mini drone","Minidrohne")}
 Canvas(modifier.semantics {
  contentDescription=model.text("$name · Tap to laugh · Hold for a curious expression","$name · Tippen zum Lachen · Halten für einen fragenden Blick")
  stateDescription=when(reaction){1->model.text("Laughing","Lachend");2->model.text("Curious","Neugierig");else->model.text("Resting","Ruhend")}
 }.combinedClickable(onClickLabel=model.text("Laugh","Lachen"),onLongClickLabel=model.text("Curious expression","Fragender Blick"),onClick={reaction=1;gesture++},onLongClick={reaction=2;gesture++})) {
  val w=image.width/3;val h=image.height/3
  drawImage(image,IntOffset(frame*w,reaction*h),IntSize(w,h),IntOffset.Zero,IntSize(size.width.roundToInt(),size.height.roundToInt()),filterQuality=FilterQuality.High)
 }
}
