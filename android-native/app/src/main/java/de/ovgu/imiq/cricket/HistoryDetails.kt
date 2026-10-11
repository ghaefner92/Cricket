package de.ovgu.imiq.cricket

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable fun HistoryDetails(model:CricketModel,record:JSONObject) {
 val context=LocalContext.current
 val wording=remember {context.assets.open("choiceWordings.json").bufferedReader().use {JSONObject(it.readText())}}
 if(record.objects("journeys").isNotEmpty())Disclosure(model.text("Actual journeys and experiences","Tatsächliche Reisen und Erfahrungen")) {
  record.objects("journeys").forEach {trip->
   Copy(tripStatus(model,trip.getString("status"))+" · "+trip.getString("capture_mode"))
   Copy("${metric(trip.getJSONObject("tracking").number("distance_m")?.div(1000))} km · ${metric(trip.getJSONObject("tracking").number("elapsed_seconds")?.div(60))} min")
   PixelButton(model.text("View experience","Erfahrung ansehen"),{model.openTrip(record.getString("search_id"),trip.getString("journey_id"))})
  }
 }
 Disclosure(model.text("Saved simulation","Gespeicherte Simulation")) {
  val full by produceState<JSONObject?>(null,record.getString("search_id"),model.revision) {
   try {value=(if(record.optBoolean("history_preview"))withContext(Dispatchers.IO){model.store.record(record.getString("search_id"))}else record)?:error("Saved journey unavailable")}
   catch(e:CancellationException){throw e}catch(e:Exception){model.error=e.message;value=JSONObject().put("load_failed",true)}
  }
  val record=full
  if(record==null){Copy(model.text("Loading saved simulation…","Gespeicherte Simulation laden…"));return@Disclosure}
  if(record.optBoolean("load_failed")){Copy(model.text("The saved simulation could not be loaded.","Die gespeicherte Simulation konnte nicht geladen werden."));return@Disclosure}
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
