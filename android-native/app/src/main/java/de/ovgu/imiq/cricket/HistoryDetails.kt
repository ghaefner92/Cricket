package de.ovgu.imiq.cricket

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable fun HistoryDetails(model:CricketModel,record:JSONObject) {
 val context=LocalContext.current
 val wording=remember {context.assets.open("choiceWordings.json").bufferedReader().use {JSONObject(it.readText())}}
 Disclosure(model.text("Saved simulation","Gespeicherte Simulation")) {
  Copy(model.text("Passport revision: ","Passport-Version: ")+record.optJSONObject("passport_reference")?.optInt("revision"))
  val available=record.optJSONObject("request")?.optJSONObject("journey_availability")?:record.optJSONObject("request")?.optJSONObject("cognitive_passport")?.optJSONObject("profile")?.optJSONObject("availability")
  Copy(model.text("Available in this simulation: ","Verfügbar in dieser Simulation: ")+modes.filter {available?.optBoolean(it)==true}.map {modeName(it,model.de)}.joinToString(" · "))
  val recommendation=record.optJSONObject("recommendation")
  if(recommendation?.optString("state")=="clear")Copy(model.text("Recommended mode: ","Empfohlener Modus: ")+modeName(recommendation.optString("winner"),model.de))else Copy(model.text("No clear recommendation","Keine eindeutige Empfehlung"))
  cards(record.optJSONObject("result")).forEach { card->Disclosure(modeName(card.mode,model.de)+" · "+model.text("Saved weather","Gespeichertes Wetter")){WeatherUi(model,card)} }
  record.objects("explanations").filter {it.optString("language")==model.locale}.groupBy {it.optInt("choice_index")}.values.forEach {entries->
   val entry=entries.last();val response=entry.optJSONObject("response")
   Disclosure(model.text("Saved explanation","Gespeicherte Erklärung")+" · "+(entry.optInt("choice_index")+1)) {
    if(response!=null&&validReflection(response,record,entry.optInt("choice_index"),model.locale,wording))dialogueRows(response,wording).forEach {row->Copy(row.text)}
    else Copy(model.text("This saved explanation could not be verified.","Diese gespeicherte Erklärung konnte nicht verifiziert werden."))
   }
  }
 }
}
