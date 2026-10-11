package de.ovgu.imiq.cricket

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlin.math.*

private val travelKitModes=listOf("walk","bike","pt","car")

@Composable fun TravelKitScreen(model:CricketModel) {
 var index by rememberSaveable {mutableIntStateOf(0)}
 var infoMode by rememberSaveable {mutableStateOf<String?>(null)}
 val mode=travelKitModes[index]
 val modeLabel=modeName(mode,model.de)
 val available=model.availability()
 val habits=model.mobilityHabits()
 val animated=model.settings().optString("motion")=="full"
 val appearance=model.companion().optString("appearance","robot")
 val answered=travelKitModes.count {habits.opt(it) is Boolean}
 val hasOptions=travelKitModes.any {available.optBoolean(it,true)}
 val complete=answered==4&&hasOptions
 fun change(delta:Int){index=(index+delta+travelKitModes.size)%travelKitModes.size}
 Column(Modifier.fillMaxSize().padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
  Heading(model.text("Your everyday mobility","Deine Mobilität im Alltag"))
  Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
   Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){
     travelKitModes.forEachIndexed {i,key->
      val isSelected=i==index
      val label=modeName(key,model.de)
      val answer=habits.opt(key) as? Boolean
      val answerLabel=if(answer==null)model.text("Unanswered","Unbeantwortet")else if(answer)model.text("Used habitually","Regelmäßig genutzt")else model.text("Not used habitually","Nicht regelmäßig genutzt")
      Column(horizontalAlignment=Alignment.CenterHorizontally){
       IconButton({index=i},Modifier.size(48.dp).clip(CircleShape).background(if(isSelected)Gold.copy(alpha=.13f)else Ink.copy(alpha=.3f))
        .border(1.dp,if(isSelected)Gold else Edge.copy(alpha=.35f),CircleShape)
        .semantics {contentDescription=label;selected=isSelected;stateDescription=answerLabel}){PixelIcon(key,Modifier.size(26.dp))}
       Text(if(answer==null)"·"else if(answer)"✓"else"−",color=if(answer==true)Mint else MutedText,style=MaterialTheme.typography.bodySmall)
      }
     }
    }
    Box(Modifier.fillMaxWidth().aspectRatio(3f/2f).clip(RoundedCornerShape(14.dp)).jrpgSceneFrame(animated)
     .semantics {stateDescription="$modeLabel · ${index+1}/4"}
     .pointerInput(index){var distance=0f;detectHorizontalDragGestures(onDragStart={distance=0f},onDragCancel={distance=0f},onDragEnd={if(abs(distance)>48.dp.toPx())change(if(distance<0)1 else -1);distance=0f}){event,delta->event.consume();distance+=delta}}) {
     Crossfade(mode,animationSpec=tween(if(animated)420 else 0),label="travel kit scene"){selectedMode->TravelKitScene(appearance,selectedMode,animated)}
     JrpgSceneArrow(true,model.text("Previous mode","Vorheriges Verkehrsmittel"),animated,{change(-1)},Modifier.align(Alignment.CenterStart).padding(start=4.dp))
     JrpgSceneArrow(false,model.text("Next mode","Nächstes Verkehrsmittel"),animated,{change(1)},Modifier.align(Alignment.CenterEnd).padding(end=4.dp))
     IconButton({infoMode=mode},Modifier.align(Alignment.TopEnd).padding(6.dp).size(48.dp).clip(CircleShape).background(Ink.copy(alpha=.85f)).semantics {contentDescription=model.text("About this scene","Über diese Szene")}){PixelIcon("info",Modifier.size(28.dp))}
    }
    Text(modeName(mode,model.de),color=Gold,style=MaterialTheme.typography.titleSmall)
    Copy(model.text("Do you usually use this mode for your everyday journeys?","Nutzt du dieses Verkehrsmittel normalerweise für deine Alltagswege?"))
    Text(model.text("Answer for each mode, including walking. This describes your usual use; route availability is set below.","Antworte für jedes Verkehrsmittel, auch fürs Gehen. Hier geht es um deine übliche Nutzung; verfügbare Optionen legst du unten fest."),color=MutedText,style=MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
     listOf(true,false).forEach {value->PixelButton(if(value)model.text("Usually","Regelmäßig")else model.text("Not usually","Nicht regelmäßig"),{model.saveMobilityHabit(mode,value)},Modifier.weight(1f),primary=habits.opt(mode)==value)}
    }
    Text(model.text("$answered of 4 answered","$answered von 4 beantwortet"),style=MaterialTheme.typography.bodySmall,color=MutedText)
    SpriteDisclosure(model.text("Options for route suggestions","Optionen für Routenvorschläge"),"preferences",animate=animated){
     Copy(model.text("Enable modes you can use for route suggestions, including modes you do not usually use. Availability does not express liking or importance.","Aktiviere nutzbare Verkehrsmittel für Routenvorschläge, auch wenn du sie nicht regelmäßig nutzt. Verfügbarkeit beschreibt weder Gefallen noch Wichtigkeit."))
     travelKitModes.forEach {key->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
      PixelIcon(key);Text(modeName(key,model.de),Modifier.weight(1f));Switch(available.optBoolean(key,true),{enabled->model.saveAvailability(model.availability().copyJson().put(key,enabled))})
     }}
    }
    if(!hasOptions)Copy(model.text("Include at least one option for route suggestions to continue.","Aktiviere mindestens eine Option für Routenvorschläge, um fortzufahren."))
   }
  }
  Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
   PixelButton(model.text("← Back","← Zurück"),{model.goStep("identity")},Modifier.weight(1f))
   PixelButton(if(complete)model.text("Next: goals →","Weiter: Ziele →")else model.text("Next mode →","Nächster Modus →"),{
    if(complete)model.confirmMobilityOptions()else if(answered<4) {
     index=(1..4).map {(index+it)%4}.first {habits.opt(travelKitModes[it]) !is Boolean}
    }
   },Modifier.weight(2f),enabled=complete||answered<4&&habits.opt(mode) is Boolean,primary=true)
  }
 }
 infoMode?.let {SceneLandmarkDialog(model,it){infoMode=null}}
}

@Composable private fun TravelKitScene(appearance:String,mode:String,animated:Boolean) {
 val background=art("travel-kit-$mode-background-v1.png")
 val time=jrpgSceneTime(animated)
 Box(Modifier.fillMaxSize()) {
  // Keep the landmark and the authored ground line visible on compact phones.
  Image(background,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
  Canvas(Modifier.fillMaxSize()){
   val t=time
   when(mode){
    "car"->{repeat(6){i->val x=size.width*(.58f+.012f*sin(i*2f));val y=size.height*(.4f+i*.035f)+if(animated)sin(t*1.2f+i)*2.dp.toPx()else 0f;drawRect(Pale.copy(alpha=.2f),Offset(x,y),Size(2.dp.toPx(),3.dp.toPx()))}}
    "bike"->{repeat(7){i->val x=size.width*(.08f+i*.14f);val y=size.height*.69f+if(animated)sin(t*.7f+i)*1.dp.toPx()else 0f;drawLine(Gold.copy(alpha=.25f),Offset(x,y),Offset(x+size.width*.025f,y),1.dp.toPx())}}
    "walk"->{repeat(3){i->val x=size.width*(.15f+i*.35f)+if(animated)sin(t*.3f+i)*6.dp.toPx()else 0f;drawRect(Pale.copy(alpha=.2f),Offset(x,size.height*(.13f+i*.035f)),Size(12.dp.toPx(),1.dp.toPx()))}}
    else->{repeat(5){i->val x=size.width*(.08f+i*.2f);val y=size.height*.32f+if(animated)sin(t*.8f+i)*1.dp.toPx()else 0f;drawRect(Gold.copy(alpha=.3f),Offset(x,y),Size(1.dp.toPx(),1.dp.toPx()))}}
   }
  }
  BoxWithConstraints(Modifier.fillMaxSize()) {
   val w=maxWidth*when(mode){"pt"->.78f;"car"->.64f;"bike"->.50f;else->.38f}
   val h=maxHeight*when(mode){"pt","car"->.40f;"bike"->.50f;else->.57f}
   val duration=when(mode){"car"->12f;"pt"->14f;"bike"->16f;else->20f}
   val phase=if(animated)(time/duration+.5f)%1f else .5f
   val x=((maxWidth.value+w.value)*phase-w.value).dp
   val ground=when(mode){"car"->.85f;"pt"->.875f;"bike"->.875f;else->.95f}
   TransportSprite(appearance,mode,animated,Modifier.align(Alignment.BottomStart).offset(x=x).padding(bottom=maxHeight*(1f-ground)).size(w,h),seconds=time)
  }
 }
}
