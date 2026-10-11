package de.ovgu.imiq.cricket

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import org.json.JSONObject
import kotlin.math.abs

/** Three strongest positive and negative inputs from the saved route simulation. */
@Composable internal fun RouteGoalIndicators(model:CricketModel,card:RouteCard,passport:JSONObject?,modifier:Modifier=Modifier,showLabel:Boolean=true) {
 val links=remember(card.candidate,card.basicSimulation,card.mode){simulatedGoalContributions(card)}
 val supports=strongestGoalSupports(links)
 val obstacles=strongestGoalObstacles(links)
 val animated=model.settings().optString("motion")=="full"
 val lifecycle=LocalLifecycleOwner.current.lifecycle
 var seconds by remember {mutableFloatStateOf(0f)}
 var expanded by remember(card.id) {mutableStateOf(false)}
 LaunchedEffect(animated,lifecycle,supports.isNotEmpty()||obstacles.isNotEmpty()) {
  seconds=0f
  if(animated&&(supports.isNotEmpty()||obstacles.isNotEmpty()))lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
   var start=0L
   while(true)withFrameNanos {now->if(start==0L)start=now;seconds=((now-start)/1e9).toFloat()}
  }
 }
 val titles=goalKeys.map {goalCopy(it,model.de).getString("title")}
 fun title(need:String)=titles[needKeys.indexOf(need)]
 val basic=card.candidate==null&&card.basicSimulation!=null
 val label=if(basic)model.text("Basic simulated goals","Basis-Zielsimulation")else model.text("Simulated goals","Simulierte Ziele")
 val unavailable=model.text("Not simulated","Nicht simuliert")
 val empty=model.text("No contribution","Kein Beitrag")
 val description=when {
  links==null->"$label: $unavailable"
  supports.isEmpty()&&obstacles.isEmpty()->"$label: $empty"
  else->"$label: "+model.text("Supports: ","Unterstützt: ")+supports.joinToString(", "){title(it.need)}+"; "+model.text("Opposes: ","Wirkt entgegen: ")+obstacles.joinToString(", "){title(it.need)}
 }
 Column(modifier.clickable(role=Role.Button,onClickLabel=model.text("View simulated goal support","Simulierte Zielbeiträge ansehen")){expanded=true}
  .semantics {contentDescription=description},verticalArrangement=Arrangement.spacedBy(1.dp),horizontalAlignment=Alignment.CenterHorizontally) {
  if(showLabel)Text(label,fontSize=8.sp,lineHeight=10.sp,color=MutedText,maxLines=1)
  if(supports.isEmpty()&&obstacles.isEmpty())Text(if(links==null)unavailable else empty,fontSize=9.sp,lineHeight=11.sp,color=MutedText,maxLines=2)
  else listOf(supports,obstacles).forEach {group->Row(Modifier.height(22.dp),horizontalArrangement=Arrangement.spacedBy(3.dp)) {
   if(group.isEmpty())Text("—",fontSize=10.sp,lineHeight=12.sp,color=MutedText)
   group.forEachIndexed {index,link->
    val positive=link.input>0
    Box(Modifier.size(22.dp).semantics {contentDescription=title(link.need)+if(positive)model.text(" · Simulated support"," · Simulierter Beitrag")else model.text(" · Simulated obstacle"," · Simuliertes Hindernis")},contentAlignment=Alignment.CenterStart) {
     ContextSprite(goalKeys[needKeys.indexOf(link.need)],false,Modifier.size(18.dp))
     Canvas(Modifier.align(Alignment.TopEnd).width(7.dp).height(18.dp)) {
      val phase=if(animated)(seconds*.85f+index*.12f)%1f else .5f
      val color=(if(positive)Color(0xFF53EB89)else Color(0xFFFF657B)).copy(alpha=if(animated).6f+.4f*(1-phase)else 1f)
      val tip=Offset(size.width*.5f,size.height*(if(positive).3f-phase*.2f else .7f+phase*.2f))
      val base=tip+Offset(0f,size.height*(if(positive).4f else -.4f))
      val wing=size.height*(if(positive).17f else -.17f)
      drawLine(color,base,tip,2.dp.toPx(),StrokeCap.Round)
      drawLine(color,tip,tip+Offset(-size.width*.32f,wing),2.dp.toPx(),StrokeCap.Round)
      drawLine(color,tip,tip+Offset(size.width*.32f,wing),2.dp.toPx(),StrokeCap.Round)
     }
    }
   }
  }}
 }
 if(expanded)AlertDialog(onDismissRequest={expanded=false},title={Text(model.text("Simulated goal support · ","Simulierte Zielbeiträge · ")+modeName(card.mode,model.de))},text={
  Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
   if(basic)Text(model.text("Basic simulation from your Passport. Route context is unavailable; pedestrian access is not evaluated in these goal indicators.","Basissimulation aus deinem Passport. Routenkontext fehlt; Fußwege werden in diesen Zielbeiträgen nicht bewertet."))
   Text(model.text("Green ↑: up to three strongest supports. Red ↓: up to three strongest obstacles. These are goal inputs at the end of this option’s simulation, not measured satisfaction.","Grün ↑: bis zu drei stärkste Beiträge. Rot ↓: bis zu drei stärkste Hindernisse. Dies sind Zielbeiträge am Ende der Simulation dieser Option, keine gemessene Zufriedenheit."))
   if(links==null)Text(model.text("This saved route has no simulation evidence for goal support.","Für diese gespeicherte Route fehlen Simulationsdaten zu Zielbeiträgen."))
   else {
    Text(model.text("All simulated connections","Alle simulierten Verbindungen"))
    links.sortedByDescending {abs(it.input)}.forEach {link->Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
     ContextSprite(goalKeys[needKeys.indexOf(link.need)],false,Modifier.size(26.dp))
     Column(Modifier.weight(1f)) {
      Text(title(link.need),color=if(link.input>0)Mint else if(link.input<0)MaterialTheme.colorScheme.error else MutedText)
      Text((if(link.input>0)model.text("Supports","Unterstützt")else if(link.input<0)model.text("Opposes","Wirkt entgegen")else model.text("No contribution","Kein Beitrag"))+" · "+"%.4f".format(java.util.Locale.US,link.input),style=MaterialTheme.typography.bodySmall)
      val source=passport?.optJSONObject("measurement_provenance")?.optJSONObject("beliefs")?.optJSONObject(card.mode)?.optJSONObject(link.need)?.optString("source")
      if(source in listOf("observed","estimated"))Text(if(source=="observed")model.text("Connection from your answer","Verbindung aus deiner Antwort")else model.text("Estimated connection","Geschätzte Verbindung"),style=MaterialTheme.typography.bodySmall,color=MutedText)
     }
    }}
   }
  }
 },confirmButton={TextButton({expanded=false}){Text(model.text("Close","Schließen"))}})
}
