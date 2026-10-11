package de.ovgu.imiq.cricket

import org.json.JSONObject

internal const val ACTIVE_PLAYBACK="cricket.active-route-playback.v1"
/** UI progress only. Never converted to travelled time, fixes or observed experience. */
internal object JourneyPlaybackClock {
 fun create(search:String,index:Int,now:Long,boot:Int?)=obj("search_id" to search,"choice_index" to index,"running" to true,"elapsed_seconds" to 0.0,"anchor_ms" to now,"boot_count" to boot,"duration_seconds" to null)
 fun elapsed(state:JSONObject,now:Long,boot:Int?):Double {
  val base=state.optDouble("elapsed_seconds",0.0).coerceAtLeast(0.0)
  val sameBoot=!state.isNull("boot_count")&&boot!=null&&state.optInt("boot_count")==boot
  val delta=if(state.optBoolean("running")&&sameBoot)((now-state.optLong("anchor_ms",now))/1000.0).coerceAtLeast(0.0)else 0.0
  val duration=state.number("duration_seconds")?.takeIf {it>0}
  return (base+delta).let {if(duration!=null)it.coerceAtMost(duration)else it}
 }
 fun setRunning(state:JSONObject,running:Boolean,now:Long,boot:Int?) {state.put("elapsed_seconds",elapsed(state,now,boot)).put("anchor_ms",now).put("boot_count",boot?:JSONObject.NULL).put("running",running)}
 fun progress(state:JSONObject,now:Long,boot:Int?):Double=state.number("duration_seconds")?.takeIf {it>0}?.let {(elapsed(state,now,boot)/it).coerceIn(0.0,1.0)}?:0.0
}
