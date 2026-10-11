package de.ovgu.imiq.cricket

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONObject

internal fun comfortObservation(mode:String,score:Int,language:String,at:String,multiple:Boolean):JSONObject {
 require(mode in modes&&score in 1..7&&language in listOf("en","de"))
 return obj("question_version" to "comfort-physical-bipolar-v1","scale" to "helps-hinders-1-to-7",
  "mode" to mode,"rating" to score,"attribution" to "actual_mode","language" to language,
  "answered_at" to at,"mode_selection" to if(multiple)"user_selected_actual_mode"else"single_actual_mode")
}

@Composable internal fun ComfortPilotQuestion(model:CricketModel,actual:List<String>,answer:JSONObject?,onAnswer:(JSONObject?)->Unit) {
 val selected=if(actual.size==1)actual.single()else answer?.optString("mode")?.takeIf {it in actual}
 var chosen by remember(actual) {mutableStateOf(selected)}
 val mode=if(actual.size==1)actual.single()else chosen?.takeIf {it in actual}
 val score=answer?.takeIf {it.optString("mode")==mode}?.optInt("rating")
 Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
  GoalIcon("comfort_physical",Modifier.size(36.dp))
  Column {Heading(model.text("Physical comfort","Körperlicher Komfort"));Text(model.text("Required · 1–7","Erforderlich · 1–7"),style=MaterialTheme.typography.bodySmall,color=MutedText)}
 }
 if(actual.size>1){
  Copy(model.text("Choose one used mode. Rate only the part of the journey travelled by that mode. This answer does not describe the other modes.","Wähle ein genutztes Verkehrsmittel. Bewerte nur den damit zurückgelegten Teil der Reise. Die Antwort beschreibt keine anderen Verkehrsmittel."))
  Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){actual.forEach {key->PixelButton(modeName(key,model.de),{chosen=key;onAnswer(null)},Modifier.weight(1f),primary=mode==key)}}
 }
 Copy(if(mode==null)model.text("Choose an actual mode first.","Wähle zuerst ein genutztes Verkehrsmittel.")else
  model.text("During this journey, how much did ${modeName(mode,false).lowercase()} help or hinder meeting your need for physical comfort?",
   "Wie sehr hat ${modeName(mode,true)} auf dieser Reise geholfen oder erschwert, dein Bedürfnis nach körperlichem Komfort zu erfüllen?"))
 if(mode!=null){
  fun choose(rating:Int){onAnswer(comfortObservation(mode,rating,if(model.de)"de"else"en",now(),actual.size>1))}
  RatingControl(score,::choose,model.text("1 · Hinders a lot","1 · Erschwert stark"),model.text("7 · Helps a lot","7 · Hilft stark"))
  Text(if(score==null)model.text("Not rated yet","Noch nicht bewertet")else "$score / 7",style=MaterialTheme.typography.titleMedium,color=if(score==null)MutedText else Gold)
  TextButton({choose(4)},Modifier.fillMaxWidth()){Text(model.text("Choose neutral · 4","Neutral wählen · 4"))}
 }
 score?.let {Copy(model.text(listOf("Hindered a lot","Hindered moderately","Hindered a little","Neither helped nor hindered","Helped a little","Helped moderately","Helped a lot")[it-1],
  listOf("Stark erschwert","Mäßig erschwert","Etwas erschwert","Weder geholfen noch erschwert","Etwas geholfen","Mäßig geholfen","Stark geholfen")[it-1]))}
 Text(model.text("Physical comfort means how comfortable your body felt while travelling. 4 = neither helped nor hindered; it does not mean unknown.","Körperlicher Komfort beschreibt, wie angenehm sich dein Körper unterwegs fühlte. 4 = weder geholfen noch erschwert; es bedeutet nicht unbekannt."),style=MaterialTheme.typography.bodySmall,color=MutedText)
}
