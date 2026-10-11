package de.ovgu.imiq.cricket

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

/** Read the passport used for this search, including historical searches. */
@Composable fun routePassport(model:CricketModel):JSONObject? {
 val searchId=model.historyId
 val cached=model.routeProfileSnapshot?.takeIf {it.first==searchId}?.second
 val store=model.store
 var profile by remember(searchId){mutableStateOf(cached)}
 LaunchedEffect(searchId,cached){
  if(cached!=null){profile=cached;return@LaunchedEffect}
  try {
   profile=withContext(Dispatchers.IO){if(searchId!=null)store.record(searchId)?.optJSONObject("request")?.optJSONObject("cognitive_passport")else store.passport()?.optJSONObject("response")?.optJSONObject("cognitive_passport")}
   if(searchId!=null&&model.historyId==searchId)profile?.let {model.routeProfileSnapshot=searchId to it}
  }catch(e:CancellationException){throw e}catch(e:Exception){model.error=e.message}
 }
 return if(searchId!=null&&cached!=null)cached else profile
}

private fun feelingName(model:CricketModel,rating:Int?):String=when(rating) {
 -3->model.text("Very unpleasant","Sehr unangenehm")
 -2->model.text("Unpleasant","Unangenehm")
 -1->model.text("Slightly unpleasant","Etwas unangenehm")
 0->model.text("Neutral","Neutral")
 1->model.text("Slightly pleasant","Etwas angenehm")
 2->model.text("Pleasant","Angenehm")
 3->model.text("Very pleasant","Sehr angenehm")
 else->model.text("Not recorded","Nicht erfasst")
}

/** A seven-step face and indicator; missing values have no selected step. */
@Composable private fun ValenceSprite(rating:Int?,modifier:Modifier=Modifier) {
 Canvas(modifier) {
  scale(minOf(size.width,size.height)/64f,pivot=Offset.Zero) {
   val tone=when {rating==null->Edge;rating<0->Color(0xFFB99AE4);rating>0->Mint;else->Gold}
   drawCircle(Ink,24f,Offset(32f,27f))
   drawCircle(tone,21f,Offset(32f,27f))
   drawCircle(Pale.copy(alpha=.5f),3f,Offset(19f,14f))
   drawCircle(Ink,2.7f,Offset(23f,24f));drawCircle(Ink,2.7f,Offset(41f,24f))
   val mouth=Path().apply {moveTo(22f,35f);quadraticTo(32f,35f+(rating?:0)*3.5f,42f,35f)}
   drawPath(mouth,Ink,style=Stroke(2.5f,cap=StrokeCap.Round))
   for(step in -3..3) {
    val p=Offset(8f+(step+3)*8f,58f)
    drawCircle(if(step==rating)tone else Edge.copy(alpha=.4f),if(step==rating)3.6f else 2f,p)
    if(step==rating)drawCircle(Pale,4.7f,p,style=Stroke(1f))
   }
  }
 }
}

/** One compact row, available for every mode; text is disclosed on tap. */
@Composable fun RouteProfileIcons(model:CricketModel,card:RouteCard,passport:JSONObject?=routePassport(model),compact:Boolean=false,modifier:Modifier=Modifier,showGoals:Boolean=true) {
 val profile=passport?.optJSONObject("profile")
 val needs=profile?.optJSONObject("needs")
 val beliefs=profile?.optJSONObject("beliefs")?.optJSONObject(card.mode)
 val links=needKeys.filter {(needs?.number(it)?:0.0)>0&&(beliefs?.number(it)?:0.0)>0}
  .sortedByDescending {(needs?.number(it)?:0.0)*(beliefs?.number(it)?:0.0)}
 val normalized=profile?.optJSONObject("valences")?.number(card.mode)?.takeIf {it in -1.0..1.0}
 val rating=normalized?.let {(it*3).roundToInt().coerceIn(-3,3)}
 val animated=model.settings().optString("motion")=="full"
 var expanded by remember(card.id,passport?.toString()) {mutableStateOf<String?>(null)}
 val modeLabel=modeName(card.mode,model.de)
 val feeling=feelingName(model,rating)
 val iconSize=if(compact)40.dp else 48.dp
 Row(modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(if(compact)2.dp else 6.dp)) {
  Surface(color=Panel,shape=CircleShape,border=BorderStroke(1.dp,Edge.copy(alpha=.55f))) {
   IconButton({expanded="valence"},Modifier.size(iconSize).semantics {contentDescription=model.text("Your declared usual feeling about ","Dein angegebenes übliches Gefühl zu ")+modeLabel+": $feeling"}) {
    if(compact)MascotValenceFace(model.companion().optString("appearance","robot"),rating,animated,Modifier.size(36.dp))
    else ValenceSprite(rating,Modifier.size(36.dp))
   }
  }
  if(showGoals) {
  VerticalDivider(Modifier.height(24.dp),color=MutedText.copy(alpha=.35f))
  Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(if(compact)0.dp else 4.dp)) {
   links.forEach {need->
    val goal=goalKeys[needKeys.indexOf(need)]
    val label=goalCopy(goal,model.de).getString("title")
    IconButton({expanded=need},Modifier.size(iconSize).semantics {contentDescription=label+model.text(" · Positive connection in your profile"," · Positive Verbindung in deinem Profil")}) {
     ContextSprite(goal,animated,Modifier.size(if(compact)22.dp else 28.dp))
    }
   }
   if(links.isEmpty())IconButton({expanded="noGoals"},Modifier.size(iconSize).semantics {contentDescription=model.text("Goal connections","Zielverbindungen")}) {PixelIcon("passport",Modifier.size(if(compact)22.dp else 26.dp))}
  }
  }
 }
 expanded?.let {key->
  val valence=key=="valence"
  val noGoals=key=="noGoals"
  val goal=if(!valence&&!noGoals)goalKeys[needKeys.indexOf(key)]else null
  val title=if(valence)model.text("Declared feeling · ","Angegebenes Gefühl · ")+modeLabel else if(noGoals)model.text("Your goals","Deine Ziele")else goalCopy(goal!!,model.de).getString("title")
  AlertDialog(onDismissRequest={expanded=null},icon={if(valence)ValenceSprite(rating,Modifier.size(48.dp))else ContextSprite(goal?:"passport",animated,Modifier.size(32.dp))},title={Text(title)},text={
   Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
    if(valence) {
     Text(if(rating==null)feeling else "${if(rating>0)"+"else ""}$rating · $feeling",color=if(rating==null)MutedText else Mint)
     Text(model.text("This is your declared usual feeling about this mode. The model considers it alongside your goals; it does not measure how this particular journey will feel.","Dies ist dein angegebenes übliches Gefühl zu diesem Verkehrsmittel. Das Modell berücksichtigt es neben deinen Zielen; es misst nicht, wie sich diese konkrete Reise anfühlen wird."))
     Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(2.dp)) {
      for(step in -3..3)Column(Modifier.weight(1f).semantics {contentDescription="${if(step>0)"+"else ""}$step · ${feelingName(model,step)}"},horizontalAlignment=Alignment.CenterHorizontally) {
       Surface(shape=CircleShape,color=if(step==rating)Gold.copy(alpha=.2f)else Color.Transparent) {ValenceSprite(step,Modifier.size(30.dp))}
       Text("${if(step>0)"+"else ""}$step",style=MaterialTheme.typography.labelSmall,color=if(step==rating)Gold else MutedText)
      }
     }
     Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Text(model.text("Unpleasant","Unangenehm"),style=MaterialTheme.typography.labelSmall);Text(model.text("Pleasant","Angenehm"),style=MaterialTheme.typography.labelSmall)}
    }else if(noGoals) {
     Text(model.text("I haven't found a positive connection between this mode and your current priorities in the profile saved for this search.","Im für diese Suche gespeicherten Profil habe ich keine positive Verbindung zwischen diesem Verkehrsmittel und deinen aktuellen Prioritäten gefunden."))
    }else {
     Text(model.text("In your profile, ","In deinem Profil ist ")+modeLabel+model.text(" has a positive goal–mode connection. This represents an expectation that using the mode helps meet this goal, not observed satisfaction on this route."," eine positive Ziel-Modus-Verbindung gespeichert. Sie beschreibt die Erwartung, dass die Nutzung beim Erfüllen dieses Ziels hilft, keine beobachtete Zielerfüllung auf dieser Route."))
     val reported=passport?.optJSONObject("observed_measurements")?.optJSONObject("beliefs_raw_1_to_7")?.has("${card.mode}__$key")==true
     Text(if(reported)model.text("This connection comes from your answer.","Diese Verbindung stammt aus deiner Antwort.")else model.text("This connection is an initial estimate in your profile.","Diese Verbindung ist eine anfängliche Einschätzung in deinem Profil."),style=MaterialTheme.typography.bodySmall,color=MutedText)
    }
   }
  },confirmButton={TextButton({expanded=null}){Text(model.text("Close","Schließen"))}})
 }
}
