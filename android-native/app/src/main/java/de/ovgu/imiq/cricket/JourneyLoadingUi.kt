package de.ovgu.imiq.cricket

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.*

/** Indeterminate waiting scene. Motion never represents completed work. */
@Composable fun JourneyLoadingScreen(model:CricketModel) {
 val animated=model.settings().optString("motion")=="full"
 var phase by remember {mutableFloatStateOf(0f)}
 var message by remember {mutableIntStateOf(0)}
 var waited by remember {mutableIntStateOf(0)}
 LaunchedEffect(animated){phase=0f;if(animated){var start=0L;while(true)withFrameNanos {t->if(start==0L)start=t;phase=((t-start)/1_000_000_000.0).toFloat()}}}
 LaunchedEffect(Unit){while(true){delay(6500);message=(message+1)%3;waited+=6500}}
 val messages=listOf(
  model.text("Take a little pause — I'll bring the options to you.","Mach eine kleine Pause – ich bringe dir die Möglichkeiten."),
  model.text("Your priorities matter. I'll help you compare the journey options.","Deine Prioritäten zählen. Ich helfe dir, die Reisemöglichkeiten zu vergleichen."),
  model.text("A little patience, and we'll find your next step together.","Ein wenig Geduld – wir finden gemeinsam deinen nächsten Schritt.")
 )
 Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Ink,Panel,Ink))).verticalScroll(rememberScrollState()).padding(horizontal=24.dp,vertical=20.dp),horizontalAlignment=Alignment.CenterHorizontally) {
  Text(model.text("Searching…","Ich suche…"),style=MaterialTheme.typography.labelLarge,color=Mint)
  Spacer(Modifier.height(38.dp))
  Box(Modifier.size(240.dp),contentAlignment=Alignment.Center) {
   Canvas(Modifier.fillMaxSize()) {
    val radius=size.minDimension*.39f
    drawCircle(Mint.copy(alpha=.09f),radius)
    drawCircle(Gold.copy(alpha=.18f),radius,style=Stroke(2.dp.toPx()))
    for(i in 0..5){
     val angle=i*PI/3+if(animated)phase*.28 else 0.0
     val p=Offset(center.x+(cos(angle)*radius).toFloat(),center.y+(sin(angle)*radius).toFloat())
     drawCircle(if(i%2==0)Gold.copy(alpha=.65f)else Mint.copy(alpha=.65f),if(i%2==0)3.dp.toPx()else 2.dp.toPx(),p)
    }
   }
   CompanionSprite(model.companion().optString("appearance","robot"),Modifier.size(158.dp),animated=animated,expression="reflective")
   Surface(Modifier.align(Alignment.TopEnd).padding(top=10.dp,end=8.dp),color=Panel,shape=MaterialTheme.shapes.large) {
    Canvas(Modifier.size(width=66.dp,height=38.dp)) {
     for(i in 0..2){val pulse=if(animated).55f+.45f*((sin(phase*2.8f-i*.9f)+1f)/2f)else .8f;drawCircle(Mint.copy(alpha=pulse),3.5.dp.toPx(),Offset(size.width*(.28f+i*.22f),size.height/2))}
    }
   }
  }
  Spacer(Modifier.height(26.dp))
  Text(model.text("I'm looking for the option that best fits your needs.","Ich suche die Möglichkeit, die am besten zu deinen Bedürfnissen passt."),style=MaterialTheme.typography.titleLarge,color=Gold,textAlign=TextAlign.Center,modifier=Modifier.widthIn(max=360.dp))
  Spacer(Modifier.height(18.dp))
  Text(messages[message],style=MaterialTheme.typography.bodyMedium,color=Pale,textAlign=TextAlign.Center,modifier=Modifier.widthIn(max=340.dp).heightIn(min=64.dp))
  if(waited>=30000)Text(model.text("Thanks for waiting. I need a little more time to finish your search.","Danke fürs Warten. Ich brauche noch etwas Zeit, um deine Suche abzuschließen."),style=MaterialTheme.typography.bodySmall,color=MutedText,textAlign=TextAlign.Center)
  Spacer(Modifier.height(28.dp))
  TextButton({model.cancel()}) {Text(model.text("Cancel search","Suche abbrechen"),color=MutedText)}
 }
}
