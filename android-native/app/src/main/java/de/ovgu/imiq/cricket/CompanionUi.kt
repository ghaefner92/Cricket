package de.ovgu.imiq.cricket

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.FilterQuality
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

internal const val COMPANION_REFLECTION="cricket.companion-reflection.v1"

@Composable internal fun CompanionFaceIcon(appearance:String) {
 val safe=appearance.takeIf {it in listOf("robot","explorer","creature","naturalist")}?:"robot"
 val meta=introMetadata("startup_"+safe);val image=art(meta.getString("file"))
 Canvas(Modifier.size(30.dp).clearAndSetSemantics {}) {
  val frame=meta.getJSONArray("frames").getJSONArray(0)
  val fraction=when(safe){"robot"->.64f;"naturalist"->.48f;"explorer"->.55f;else->.6f}
  val height=(frame.getInt(3)*fraction).roundToInt()
  val ratio=minOf(size.width/frame.getInt(2),size.height/height)
  val w=(frame.getInt(2)*ratio).roundToInt();val h=(height*ratio).roundToInt()
  drawImage(image,IntOffset(frame.getInt(0),frame.getInt(1)),IntSize(frame.getInt(2),height),IntOffset(((size.width-w)/2).roundToInt(),((size.height-h)/2).roundToInt()),IntSize(w,h),filterQuality=FilterQuality.High)
 }
}

internal fun companionRequest(store:Store,language:String):JSONObject {
 val wrapper=store.passport()?:error("Profile unavailable")
 val passport=wrapper.getJSONObject("response").getJSONObject("cognitive_passport")
 val root=originalPassport(store,wrapper).getJSONObject("lineage").getString("passport_id")
 val registry=store.json("d-day.evidence-exclusions.v1")
 return obj("schema_version" to "cricket-companion-reflection-request-v1","language" to language,
  "passport" to passport.copyJson(),"records" to JSONArray(store.companionEvidence(passport.getString("agent_id"))),
  "history_reviewed" to (registry!=null),"evidence_exclusions" to registry,"learned" to store.companionLearnedHeader(root))
}

/** Validate the exact reviewed wording and source before presenting a voice. */
internal fun validCompanionReflection(value:JSONObject,request:JSONObject):Boolean=runCatching {
 require(value.getString("schema_version")=="cricket-companion-reflection-response-v1"&&value.getString("voice_version")=="companion-pattern-voice-1.1")
 require(value.getString("request_id")==learnedDigest(request)&&value.getString("language")==request.getString("language"))
 require(!value.getBoolean("active_passport_modified")&&!value.getBoolean("hotco_modified"))
 val withoutId=value.copyJson().apply {remove("reflection_id")}
 require(value.getString("reflection_id")==learnedDigest(withoutId))
 val catalog=value.getJSONObject("source_catalog");val selection=value.getJSONObject("selection")
 require(selection.getString("tone")=="reflective"&&selection.getString("question")=="none")
 val opening=selection.getInt("opening");require(opening in 0..1)
 val rows=catalog.objects("statements");val variants=selection.getJSONObject("variants")
 require(variants.keys().asSequence().toSet()==rows.map {it.getString("id")}.toSet())
 val expected=listOf(catalog.getJSONArray("openings").getString(opening))+rows.map {
  val options=it.getJSONArray("options");val index=variants.getInt(it.getString("id"));require(index in 0 until options.length());options.getString(index)
 }
 val paragraphs=value.getJSONArray("paragraphs")
 require((0 until paragraphs.length()).map {paragraphs.getString(it)}==expected)
 require(expected.all {it.length in 1..1500&&!Regex("(?i)\\b(hotco|passport|backend|model|modell)\\b").containsMatchIn(it)})
 val voice=value.getJSONObject("narration")
 require(voice.getString("provider")=="groq"&&voice.getString("model")=="openai/gpt-oss-120b")
 require(if(voice.getBoolean("generated_by_llm"))voice.getString("status")=="AVAILABLE"&&voice.isNull("reason")else voice.getString("status")=="TEMPLATE_FALLBACK"&&voice.getString("reason") in listOf("NOT_CONFIGURED","PROVIDER_OR_VALIDATION_FAILURE"))
 true
}.getOrDefault(false)

@Composable internal fun CompanionScreen(model:CricketModel) {
 val companion=model.companion();val animated=model.settings().optString("motion")=="full"
 LaunchedEffect(model.locale){model.loadCompanionReflection()}
 val response=model.companionReflection
 val paragraphs=response?.optJSONArray("paragraphs")?.let {a->(0 until a.length()).map {a.getString(it)}}.orEmpty()
 // Reuse the existing dialogue reveal and talking portrait, with no model panels.
 val total=paragraphs.sumOf {it.codePointCount(0,it.length)}
 var revealed by remember(response,animated){mutableIntStateOf(if(animated)0 else total)}
 LaunchedEffect(response,animated){if(animated)while(revealed<total){delay(16);revealed=(revealed+3).coerceAtMost(total)}}
 ScreenColumn {
  PixelCard {
   Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
    CompanionSprite(companion.optString("appearance","robot"),Modifier.size(156.dp),animated=animated,talking=revealed<total,portrait=true)
    Heading(companion.optString("name").ifBlank {"Companion"})
   }
   when {
    model.companionReflectionBusy->{Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){CircularProgressIndicator(Modifier.size(20.dp),color=Mint);Copy(model.text("Let me take a moment with what you have shared…","Ich nehme mir einen Moment für das, was du mir erzählt hast…"))}}
    model.companionReflectionFailed->{Copy(model.text("I couldn’t put my thoughts into words just now. Shall we try again?","Ich konnte meine Gedanken gerade nicht in Worte fassen. Versuchen wir es noch einmal?"));PixelButton(model.text("Try again","Erneut versuchen"),{model.loadCompanionReflection(true)})}
    else->{var offset=0;paragraphs.forEach {paragraph->
     val length=paragraph.codePointCount(0,paragraph.length);val count=(revealed-offset).coerceIn(0,length);offset+=length
     if(count>0)Text(paragraph.substring(0,paragraph.offsetByCodePoints(0,count)),Modifier.clearAndSetSemantics {text=AnnotatedString(paragraph)},color=Pale,fontFamily=PixelFont,style=MaterialTheme.typography.bodyLarge)
    }
     if(revealed<total)TextButton({revealed=total}){Text(model.text("Show text","Text anzeigen"))}
     if(response!=null){
      if(response.getJSONObject("narration").getBoolean("generated_by_llm").not())Text(model.text("Live conversation unavailable · saved reflection","Live-Gespräch nicht verfügbar · gespeicherte Reflexion"),color=MutedText,fontFamily=PixelFont,style=MaterialTheme.typography.bodySmall)
      TextButton({model.loadCompanionReflection(true)}){Text(model.text("Reflect again","Erneut betrachten"))}
     }
    }
   }
  }
 }
}
