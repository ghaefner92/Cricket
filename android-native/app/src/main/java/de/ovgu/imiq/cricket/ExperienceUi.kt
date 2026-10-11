package de.ovgu.imiq.cricket

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONObject

/** Reuses Passport artwork, while asking only about the completed journey. */
@Composable internal fun ExperienceEmotions(model:CricketModel,actual:List<String>,answers:JSONObject,onAnswer:(String,Int)->Unit) {
 var selected by remember {mutableStateOf(actual.first())}
 val mode=selected.takeIf {it in actual}?:actual.first()
 fun move(delta:Int){selected=actual[(actual.indexOf(mode)+delta+actual.size)%actual.size]}
 PixelCard {
  Heading(model.text("Your feeling during this journey","Dein Gefühl während dieser Reise"))
  Text(model.text("For each used mode, rate your overall feeling during its part of the journey. −3 = very unpleasant; 0 = neutral; +3 = very pleasant.","Bewerte für jedes genutzte Verkehrsmittel dein Gefühl während seines Reiseabschnitts insgesamt. −3 = sehr unangenehm; 0 = neutral; +3 = sehr angenehm."),style=MaterialTheme.typography.bodySmall,color=MutedText)
  if(actual.size>1)Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
   actual.forEach {key->OutlinedButton({selected=key},Modifier.fillMaxWidth()) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
     PixelIcon(key,Modifier.size(24.dp))
     Text(modeName(key,model.de),Modifier.weight(1f),color=if(key==mode)Gold else Pale)
     Text(if(answers.has(key))"✓"else"—",color=if(answers.has(key))Mint else MutedText)
    }
   }}
  }
  Heading(modeName(mode,model.de))
  ValenceEditor(model.companion().optString("appearance","robot"),mode,
   answers.number(mode)?.toInt(),model.settings().optString("motion")=="full",model.de,
   onPrevious={move(-1)},onNext={move(1)},
   prompt=model.text("During the part of this journey travelled by this mode, how pleasant or unpleasant did using it feel overall?","Wie angenehm oder unangenehm fühlte sich die Nutzung dieses Verkehrsmittels während seines Abschnitts dieser Reise insgesamt an?"),
   showModeNavigation=actual.size>1){onAnswer(mode,it)}
  if(actual.size>1)PixelButton(model.text("Next mode →","Nächstes Verkehrsmittel →"),{move(1)},Modifier.fillMaxWidth())
 }
}
