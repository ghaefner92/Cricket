package de.ovgu.imiq.cricket

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.json.JSONObject
import kotlin.math.*

@Composable internal fun DailyGoalsSphere(model:CricketModel) {
 val plan=model.plan();val points=plan.getJSONObject("points")
 val ranks=goalPriorityRanks(goalKeys.associateWith {points.optInt(it)})


 var selected by remember {mutableStateOf<String?>(null)}
 var editingBase by remember {mutableStateOf<JSONObject?>(null)}
 var rating by remember {mutableIntStateOf(0)}
 var saved by remember {mutableStateOf(false)}
 var aiming by remember {mutableStateOf(false)}
 var pointer by remember {mutableStateOf<Offset?>(null)}
 var target by remember {mutableStateOf<String?>(null)}
 val lifecycle=LocalLifecycleOwner.current.lifecycle
 DisposableEffect(lifecycle,model.busy){
  val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_STOP){aiming=false;pointer=null;target=null}}
  lifecycle.addObserver(observer)
  onDispose {lifecycle.removeObserver(observer);aiming=false;pointer=null;target=null}
 }
 fun openGoal(key:String){if(!model.busy){selected=key;editingBase=model.plan().copyJson();rating=editingBase!!.getJSONObject("points").optInt(key);saved=false;model.error=null}}
 Heading(model.text("Daily Mobility Goals","Tägliche Mobilitätsziele"))
 Text(model.text("Drag to choose a goal","Ziehen, um ein Ziel auszuwählen"),style=MaterialTheme.typography.bodySmall,color=MutedText)
 BoxWithConstraints(Modifier.fillMaxWidth().height(382.dp)) {
  val rx=minOf(maxWidth.value/2-35,142f).dp
  val ry=155.dp
  val density=LocalDensity.current
  val radiusX=with(density){rx.toPx()};val radiusY=with(density){ry.toPx()};val petHalf=with(density){116.dp.toPx()}
  fun pointAt(position:Offset){
   pointer=position-Offset(petHalf,petHalf)
   target=aimedGoalIndex(pointer!!.x,pointer!!.y,radiusX,radiusY,goalKeys.size)?.let {goalKeys[it]}
  }
  TouchOriginalPet(model,Modifier.align(Alignment.Center).size(232.dp).zIndex(0f)
   .pointerInput(radiusX,radiusY,model.busy){
    if(!model.busy)detectDragGestures(
     onDragStart={aiming=true;pointAt(it)},
     onDrag={change,_->change.consume();pointAt(change.position)},
     onDragEnd={val chosen=target;aiming=false;pointer=null;target=null;if(chosen!=null)openGoal(chosen)},
     onDragCancel={aiming=false;pointer=null;target=null})
   },aim=if(aiming)pointer else null,selector=true,aimedKey=target)
  if(aiming&&target!=null)Surface(Modifier.align(Alignment.Center).offset(y=103.dp).zIndex(4f),shape=MaterialTheme.shapes.small,color=Panel,border=BorderStroke(1.dp,Mint)){
   Text(shortGoalName(target!!,model.de),Modifier.widthIn(max=210.dp).padding(horizontal=10.dp,vertical=6.dp),color=Mint,style=MaterialTheme.typography.labelLarge)
  }
  goalKeys.forEachIndexed {index,key->
   val angle=-PI/2+index*2*PI/goalKeys.size
   val px=cos(angle).toFloat();val py=sin(angle).toFloat()
   val importance=goalSalience(points.optInt(key))
   val glow=importance?:0f
   val name=goalCopy(key,model.de).getString("title")
   val rank=ranks[key]
   val active=aiming&&target==key
   val sizeScale by animateFloatAsState(if(active)1.3f else 1f,label="goal aim scale")
   val description=name+model.text(if(importance==null)" · Not rated"else " · Importance ${points.optInt(key)} of 7",if(importance==null)" · Unbewertet"else " · Wichtigkeit ${points.optInt(key)} von 7")+(rank?.let {model.text(" · Priority ${it.position}"+if(it.tied)" · Tied"else ""," · Rang ${it.position}"+if(it.tied)" · Gleichrangig"else "")}?:"")
   Box(Modifier.align(Alignment.Center).offset {IntOffset((px*rx.toPx()).roundToInt(),(py*ry.toPx()).roundToInt())}.size(52.dp).graphicsLayer {scaleX=sizeScale;scaleY=sizeScale;alpha=if(aiming&&!active).65f else 1f;compositingStrategy=CompositingStrategy.ModulateAlpha}.zIndex(if(active)3f else 2f)) {
   Surface(onClick={openGoal(key)},enabled=!model.busy,
    modifier=Modifier.fillMaxSize().semantics {contentDescription=description},
    shape=CircleShape,color=if(active)Mint.copy(alpha=.25f)else Panel.copy(alpha=.96f),border=BorderStroke((if(active)3f else 1+glow*2).dp,if(active)Mint else (if(importance==null)MutedText else Gold).copy(alpha=.3f+glow*.65f))) {
    Box(contentAlignment=Alignment.Center){GoalInventorySprite(key,Modifier.size((32+glow*12).dp))}
   }
   rank?.let {Text("${it.position}${if(it.tied)"="else ""}",modifier=Modifier.align(Alignment.TopEnd).offset(x=12.dp,y=(-4).dp).background(Panel,CircleShape).padding(horizontal=5.dp,vertical=2.dp),color=Gold,style=MaterialTheme.typography.labelSmall)}
   }
  }
 }
 Text(model.text("Larger, brighter icons mean greater importance.","Größere, hellere Symbole bedeuten höhere Wichtigkeit."),style=MaterialTheme.typography.bodySmall,color=MutedText)
 Text(model.text("1 = highest priority · = shared rank","1 = höchste Priorität · = gleicher Rang"),style=MaterialTheme.typography.bodySmall,color=Gold)
 Text(model.text("Release to edit · centre to cancel","Loslassen zum Bearbeiten · Mitte zum Abbrechen"),style=MaterialTheme.typography.bodySmall,color=MutedText)
 if(saved)Text(model.text("Saved to your Passport","In deinem Passport gespeichert"),color=Mint,style=MaterialTheme.typography.bodySmall)
 if(!model.dailyGoalsCurrent()) {
  Text(model.text("Review and confirm your goals for today.","Prüfe und bestätige deine Ziele für heute."),style=MaterialTheme.typography.bodySmall,color=Gold)
  PixelButton(model.text("Review today’s goals","Heutige Ziele prüfen"),{model.openDailyGoals()},Modifier.fillMaxWidth(),enabled=!model.busy)
 }
 selected?.let {key->
  val base=editingBase!!
  val complete=goalKeys.all {if(it==key)rating in 1..7 else base.getJSONObject("points").optInt(it) in 1..7}
  AlertDialog(onDismissRequest={if(!model.busy)selected=null},containerColor=Panel,
   icon={GoalInventorySprite(key,Modifier.size(48.dp))},
   title={Heading(goalCopy(key,model.de).getString("title"))},
   text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
    Text(model.text("How important is this goal for today’s journeys?","Wie wichtig ist dieses Ziel für deine heutigen Wege?"))
    Text(if(rating in 1..7)model.text("Importance: $rating / 7","Wichtigkeit: $rating / 7")else model.text("Choose your importance","Wichtigkeit auswählen"),color=Gold)
    Column(Modifier.pointerInput(model.busy){if(model.busy)awaitPointerEventScope {while(true)awaitPointerEvent().changes.forEach {it.consume()}}}){
     RatingControl(rating.takeIf {it in 1..7},{if(!model.busy)rating=it},model.text("1 · Not important","1 · Nicht wichtig"),model.text("7 · Very important","7 · Sehr wichtig"))
    }
    Text(model.text("Saving confirms today’s goals in a new Passport revision.","Speichern bestätigt die heutigen Ziele in einer neuen Passport-Version."),style=MaterialTheme.typography.bodySmall,color=MutedText)
    if(!complete)Text(model.text("Rate the remaining goals in today’s review before saving.","Bewerte die übrigen Ziele in der Tagesübersicht vor dem Speichern."),color=Gold)
    model.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
    if(model.busy)CircularProgressIndicator(Modifier.size(24.dp))
   }},
   confirmButton={PixelButton(model.text("Save","Speichern"),{
    val edited=editedDailyGoal(base,key,rating)
    model.confirmDailyGoals(edited,base){selected=null;saved=true}
   },enabled=complete&&!model.busy,primary=true)},
   dismissButton={TextButton({selected=null},enabled=!model.busy){Text(model.text("Cancel","Abbrechen"))}})
 }
}
