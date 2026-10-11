package de.ovgu.imiq.cricket

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun GoalsScreen(model:CricketModel) {
 val points=model.plan().getJSONObject("points")
 WeeklyPrioritiesEditor(goalKeys.associateWith {points.optInt(it)},model.de,
  onAdjust=model::adjustGoal,onSave={if(model.dailyGoalsVisible)model.confirmDailyGoals()else model.saveGoals()},onBack={if(model.dailyGoalsVisible)model.closeDailyGoals()else model.goStep("travel-kit")},
  appearance=model.companion().optString("appearance","robot"),animated=model.settings().optString("motion")=="full",saving=model.busy)
}

/** Independent ordinal importance responses with the existing companion feedback. */
@Composable internal fun WeeklyPrioritiesEditor(points:Map<String,Int>,de:Boolean,onAdjust:(String,Int)->Unit,onSave:()->Unit,onBack:()->Unit,appearance:String="robot",animated:Boolean=true,saving:Boolean=false) {
 fun text(en:String,german:String)=if(de)german else en
 val rated=goalKeys.count {(points[it]?:0) in 1..7}
 val remaining=goalKeys.size-rated
 var explanation by remember {mutableStateOf<String?>(null)}
 var activeKey by rememberSaveable {mutableStateOf(goalKeys.maxByOrNull {points[it]?:0}?:goalKeys.first())}
 var interaction by remember {mutableIntStateOf(0)}
 var interactionTime by remember {mutableLongStateOf(0L)}
 var direction by remember {mutableIntStateOf(0)}
 fun adjust(key:String,delta:Int){activeKey=key;direction=delta;interactionTime=android.os.SystemClock.elapsedRealtime();interaction++;onAdjust(key,delta)}
 Column(Modifier.fillMaxSize().padding(horizontal=14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
   IconButton(onBack,Modifier.size(48.dp).semantics {contentDescription=text("Back","Zurück")}){
    Canvas(Modifier.size(22.dp)){
     val stroke=2.dp.toPx();drawLine(Pale,Offset(size.width*.8f,size.height*.5f),Offset(size.width*.2f,size.height*.5f),stroke)
     drawLine(Pale,Offset(size.width*.2f,size.height*.5f),Offset(size.width*.5f,size.height*.2f),stroke)
     drawLine(Pale,Offset(size.width*.2f,size.height*.5f),Offset(size.width*.5f,size.height*.8f),stroke)
    }
   }
   Text(text("What matters today?","Was zählt heute?"),Modifier.weight(1f),style=MaterialTheme.typography.titleLarge,color=Pale)
  }
  Text(text("For today’s journeys, how important is each goal to you? Rate each separately: 1 = not important; 7 = very important.","Wie wichtig ist dir jedes Ziel für deine heutigen Wege? Bewerte jedes einzeln: 1 = nicht wichtig; 7 = sehr wichtig."),style=MaterialTheme.typography.bodySmall,color=MutedText)
  Surface(color=PanelRaised,shape=RoundedCornerShape(16.dp)) {
   Column(Modifier.padding(horizontal=14.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
     Text(if(remaining==0)text("All priorities rated","Alle Prioritäten bewertet")else text("$remaining left to rate","Noch $remaining zu bewerten"),Modifier.weight(1f).semantics {liveRegion=LiveRegionMode.Polite},style=MaterialTheme.typography.titleSmall,color=Mint)
     Text("$rated / ${goalKeys.size}",style=MaterialTheme.typography.labelLarge,color=Gold)
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){(1..7).forEach {Text("$it",style=MaterialTheme.typography.labelSmall,color=Gold)}}
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
     Text(text("Not important","Nicht wichtig"),style=MaterialTheme.typography.bodySmall,color=MutedText)
     Text(text("Very important","Sehr wichtig"),style=MaterialTheme.typography.bodySmall,color=MutedText)
    }
   }
  }
  LazyColumn(Modifier.weight(1f).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(6.dp),contentPadding=PaddingValues(bottom=4.dp)) {
   items(goalKeys,key={it}){key->
    val title=priorityTitle(key,de);val value=points[key]?:0
    val active=key==activeKey
    val phase=priorityReactionPhase(if(active)interaction else 0,interactionTime,animated)
    val reaction=if(active)direction else 0
    val reacting=phase<1f&&reaction!=0
    val accent=if(reacting&&reaction<0)androidx.compose.ui.graphics.Color(0xFFD7A0FF)else if(reacting)Gold else Mint
    val borderColor by animateColorAsState(if(active)accent else Edge.copy(alpha=.25f),tween(if(animated)180 else 0),label="Active priority")
    Surface(color=Panel,shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,borderColor)) {
     Column{
     Row(Modifier.fillMaxWidth().heightIn(min=64.dp).padding(start=10.dp,end=4.dp,top=4.dp,bottom=0.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
      Box(Modifier.size(36.dp),contentAlignment=Alignment.Center){
       if(active){
        Box(Modifier.fillMaxSize().semantics(mergeDescendants=true) {contentDescription=text("Your companion at $title","Dein Begleiter bei $title")}){
         PriorityReactionAvatar(appearance,reaction,phase,de,Modifier.fillMaxSize())
         GoalIcon(key,Modifier.size(14.dp).align(Alignment.BottomEnd))
        }
       }else GoalIcon(key,Modifier.size(26.dp))
      }
      Column(Modifier.weight(1f).heightIn(min=48.dp).clickable(role=Role.Button,onClickLabel=text("About $title","Über $title")){explanation=key}.padding(vertical=6.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
       Text(title,style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Medium,color=Pale)
       Text(text("More info","Mehr Infos"),style=MaterialTheme.typography.bodySmall.copy(fontSize=8.sp,lineHeight=14.sp),color=MutedText)
      }
      PriorityPointButton(false,value>1,text("Decrease rating for $title","Bewertung für $title senken")){adjust(key,-1)}
      Text(if(value==0)"—"else "$value",Modifier.widthIn(min=24.dp).semantics {contentDescription=if(value==0)text("$title: Not rated","$title: Noch nicht bewertet")else text("$title: rating $value of 7","$title: Bewertung $value von 7")},style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,color=if(value>0)Gold else MutedText,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
      PriorityPointButton(true,value<7,text("Increase rating for $title","Bewertung für $title erhöhen")){adjust(key,1)}
     }
     PriorityPetBar(appearance,value,reaction,phase,animated,title,de,Modifier.padding(horizontal=12.dp))
     }
    }
   }
  }
  Column(Modifier.fillMaxWidth().padding(bottom=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
   Text(if(remaining==0)text("Several goals can be equally important. These ratings describe importance today.","Mehrere Ziele können gleich wichtig sein. Die Bewertungen beschreiben die heutige Wichtigkeit.")else text("Rate every priority to continue.","Bewerte jede Priorität, um fortzufahren."),style=MaterialTheme.typography.bodySmall,color=MutedText)
   Button(onSave,Modifier.fillMaxWidth().heightIn(min=52.dp),enabled=remaining==0&&!saving,shape=RoundedCornerShape(14.dp)) {Text(if(saving)text("Saving…","Wird gespeichert…")else text("Save priorities","Prioritäten speichern"))}
  }
 }
 explanation?.let {key->val copy=goalCopy(key,de)
  AlertDialog(onDismissRequest={explanation=null},icon={GoalIcon(key,Modifier.size(36.dp))},title={Text(copy.getString("title"),style=MaterialTheme.typography.titleMedium)},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
   Text(copy.getString("hint"))
   Text(text("How important is this goal for your journeys today? 1 = not important; 7 = very important. Rate its importance, not how well a journey satisfies it. Ratings do not have to add up to a fixed total.","Wie wichtig ist dir dieses Ziel für deine heutigen Wege? 1 = nicht wichtig; 7 = sehr wichtig. Bewerte die Wichtigkeit, nicht wie gut eine Reise das Ziel erfüllt. Die Bewertungen müssen keine feste Summe ergeben."),style=MaterialTheme.typography.bodySmall,color=MutedText)
  }},confirmButton={TextButton({explanation=null}){Text(text("Got it","Verstanden"))}})
 }
}

private fun priorityTitle(key:String,de:Boolean):String {
 val titles=when(key){
  "env"->"Lower emissions" to "Weniger Emissionen"
  "health_activity"->"Move more" to "Mehr Bewegung"
  "cost"->"Save money" to "Geld sparen"
  "time"->"Save time" to "Zeit sparen"
  "comfort_physical"->"Physical comfort" to "Körperlicher Komfort"
  "reliable"->"Reliability" to "Zuverlässigkeit"
  "flex"->"Flexibility" to "Flexibilität"
  "safety_crime"->"Personal safety" to "Persönliche Sicherheit"
  "health_infection"->"Avoid infection" to "Ansteckung vermeiden"
  "crowding"->"Space & privacy" to "Platz & Privatsphäre"
  "safety_accident"->"Traffic safety" to "Verkehrssicherheit"
  else->key to key
 }
 return if(de)titles.second else titles.first
}

@Composable private fun PriorityPointButton(add:Boolean,enabled:Boolean,label:String,onClick:()->Unit) {
 FilledTonalIconButton(onClick,Modifier.size(48.dp).semantics {contentDescription=label},enabled=enabled) {
  Canvas(Modifier.size(18.dp)){
   val color=if(enabled)Mint else MutedText.copy(alpha=.5f);val stroke=2.dp.toPx()
   drawLine(color,Offset(0f,size.height/2),Offset(size.width,size.height/2),stroke)
   if(add)drawLine(color,Offset(size.width/2,0f),Offset(size.width/2,size.height),stroke)
  }
 }
}
