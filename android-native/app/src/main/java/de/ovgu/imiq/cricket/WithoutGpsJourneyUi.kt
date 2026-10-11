package de.ovgu.imiq.cricket

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.*
import org.json.JSONObject

/** Geometry preview only: no synthetic fixes, trip evidence or automatic completion. */
internal fun routePlaybackPosition(route:MapRoute?,fraction:Double):Pair<GeoPoint,String>? {
 if(route==null||!fraction.isFinite())return null
 val segments=route.segments.filter {it.drawable&&it.points.isNotEmpty()}
 val edges=segments.flatMap {s->s.points.zipWithNext().map {Triple(it.first,it.second,s.mode)}}
 if(edges.isEmpty())return segments.firstOrNull()?.let {it.points.first() to it.mode}
 val lengths=edges.map {e->val dy=e.second.lat-e.first.lat;val dx=(e.second.lon-e.first.lon)*kotlin.math.cos(Math.toRadians((e.first.lat+e.second.lat)/2));kotlin.math.hypot(dx,dy)}
 val total=lengths.sum()
 if(total<=0)return edges.first().let {it.first to it.third}
 var remaining=total*fraction.coerceIn(0.0,1.0)
 edges.forEachIndexed {i,e->val length=lengths[i];if(length>0&&(remaining<=length||i==edges.lastIndex)){val f=(remaining/length).coerceIn(0.0,1.0);return GeoPoint(e.first.lat+(e.second.lat-e.first.lat)*f,e.first.lon+(e.second.lon-e.first.lon)*f) to e.third};remaining-=length}
 return edges.last().let {it.second to it.third}
}

@Composable internal fun WithoutGpsJourneyScreen(model:CricketModel) {
 val selection=model.noGpsJourney?:return
 val lifecycle=LocalLifecycleOwner.current.lifecycle
 var record by remember(selection){mutableStateOf<JSONObject?>(null)}
 LaunchedEffect(selection){record=withContext(Dispatchers.IO){model.store.recordForUi(selection.first)}}
 val saved=record
 var tick by remember {mutableLongStateOf(SystemClock.elapsedRealtime())}
 LaunchedEffect(lifecycle){lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED){while(true){tick=SystemClock.elapsedRealtime();delay(250)}}}
 BackHandler {model.tab="home"}
 if(saved==null){CircularProgressIndicator();return}
 val choice=saved.objects("choices").getOrNull(selection.second)?:return
 val card=cards(saved.optJSONObject("result")).find {it.id=="${choice.optInt("routing_rank")}-${choice.optString("mode_key")}"}
 LaunchedEffect(selection,card?.id){if(card!=null){model.selectedId=card.id;model.activeMode=card.mode}}
 val state=remember(model.revision,selection){model.store.json(ACTIVE_PLAYBACK)}?:return
 val duration=card?.route?.optJSONObject("summary")?.number("duration_seconds")?.takeIf {it.isFinite()&&it>0}
 LaunchedEffect(selection,duration){if(duration!=null&&state.number("duration_seconds")!=duration){model.store.json(ACTIVE_PLAYBACK)?.let {p->if(p.optString("search_id")==selection.first&&p.optInt("choice_index")==selection.second){p.put("duration_seconds",duration);model.store.put(ACTIVE_PLAYBACK,p);TripUpdates.changed()}}}}
 val elapsed=JourneyPlaybackClock.elapsed(state,tick,model.trips.boot())
 val fraction=JourneyPlaybackClock.progress(state,tick,model.trips.boot())
 val running=state.optBoolean("running")&&fraction<1
 fun action(value:String){TrackingService.action(model.getApplication(),value)}
 Column(Modifier.fillMaxSize()) {
  Box(Modifier.weight(1f)){NativeJourneyMap(model,tripRecord=saved,selectedOnly=true,compact=true,playbackFraction=fraction)}
  Surface(color=Panel){
   if(card!=null)RouteSummaryPanel(model,card,System.currentTimeMillis(),false,onDetails={model.finishPlayback()},elapsed="%02d:%02d".format(elapsed.toInt()/60,elapsed.toInt()%60),controls=true,statusLabel=model.text("Route playback · No GPS","Routenwiedergabe · Ohne GPS"),onCancel={model.cancelPlayback()}) {
    if(running&&fraction<1)RoutePauseButton(model.text("Pause journey","Reise pausieren")){action(TrackingService.PAUSE)}
    else RoutePlayButton(model.text("Resume journey","Reise fortsetzen"),enabled=fraction<1){action(TrackingService.RESUME)}
   }
  }
 }
}
