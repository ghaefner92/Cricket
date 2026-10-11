package de.ovgu.imiq.cricket

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.time.Instant

/** The chosen journey owns its map; a later search cannot replace its route. */
@Composable fun TrackingJourneyScreen(model:CricketModel,record:JSONObject,trip:JSONObject) {
 val context=LocalContext.current;val status=trip.getString("status");val paused=status=="paused"
 val tracking=trip.getJSONObject("tracking")
 var cancel by remember {mutableStateOf(false)}
 var pendingCommand by remember(trip.getString("journey_id")){mutableStateOf<String?>(null)}
 LaunchedEffect(status){pendingCommand=null}
 LaunchedEffect(pendingCommand){
  if(pendingCommand!=null){delay(15000);if(pendingCommand!=null){pendingCommand=null;model.error=model.text("The journey control is taking longer than expected. Check its status before trying again.","Die Reisesteuerung dauert länger als erwartet. Prüfe den Status, bevor du es erneut versuchst.")}}
 }
 var tick by remember {mutableLongStateOf(SystemClock.elapsedRealtime())}
 LaunchedEffect(status){while(true){tick=SystemClock.elapsedRealtime();delay(1000)}}
 val elapsed=tracking.number("elapsed_seconds")?:tracking.number("started_monotonic_ms")?.takeIf {tracking.optInt("boot_count",-1)==model.trips.boot()}?.let {((tick-it)/1000).coerceAtLeast(0.0)}
 val selection=trip.getJSONObject("selection")
 val card=remember(trip.getString("journey_id")){cards(record.optJSONObject("result")).find {it.id=="${selection.optInt("routing_rank")}-${selection.optString("mode_key")}"}}
 val startedAt=runCatching {Instant.parse(trip.getString("started_at")).toEpochMilli()}.getOrNull()
 val seconds=elapsed?.toLong()
 val counter=seconds?.let {"%02d:%02d".format(it/60,it%60)}?:"—"
 fun action(value:String){
  if(pendingCommand!=null)return
  pendingCommand=value
  runCatching {TrackingService.action(context,value)}.onFailure {pendingCommand=null;model.error=it.message}
 }
 BackHandler {cancel=true}
 Column(Modifier.fillMaxSize()) {
  Box(Modifier.weight(1f).fillMaxWidth()){NativeJourneyMap(model,record,trip)}
  Surface(color=Panel,tonalElevation=4.dp) {
   if(card!=null)RouteSummaryPanel(model,card,System.currentTimeMillis(),false,onDetails={action(TrackingService.FINISH)},elapsed=counter,controls=true,statusLabel=if(paused)model.text("Paused · GPS off","Pausiert · GPS aus")else model.text("Journey in progress","Reise läuft"),startedAt=startedAt,passportSnapshot=record.optJSONObject("request")?.optJSONObject("cognitive_passport"),onCancel={cancel=true}) {
     if(pendingCommand!=null)CircularProgressIndicator(Modifier.size(32.dp),color=Mint,strokeWidth=2.dp)
     else if(paused)RoutePlayButton(model.text("Resume journey","Reise fortsetzen")){action(TrackingService.RESUME)}
     else RoutePauseButton(model.text("Pause journey","Reise pausieren")){action(TrackingService.PAUSE)}
   }
  }
 }
 if(cancel)AlertDialog(onDismissRequest={cancel=false},title={Text(model.text("Cancel this journey?","Diese Reise abbrechen?"))},text={Text(model.text("Recorded points stay in history. Experience will show the cancellation; it will not count as a completed journey.","Aufgezeichnete Punkte bleiben im Verlauf. Erfahrung zeigt den Abbruch; die Reise zählt nicht als abgeschlossen."))},confirmButton={TextButton({action(TrackingService.CANCEL);cancel=false}){Text(model.text("Cancel journey","Reise abbrechen"))}},dismissButton={TextButton({cancel=false}){Text(model.text("Keep journey","Reise behalten"))}})
}
