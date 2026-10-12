package de.ovgu.imiq.cricket

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real SQLite projection + immutable payload preservation; no fabricated user evidence. */
@RunWith(AndroidJUnit4::class)
class HistoryRecoveryDeviceTest {
 private val context=InstrumentationRegistry.getInstrumentation().targetContext
 @Test fun sqliteHistoryAndExplanationPreserveOriginalPayload(){
  val name="history-recovery-isolated.db"
  try {Store(context,name).use{store->
   val record=obj("version" to 1,"search_id" to "s","created_at" to "2026-10-12T09:00:00Z","status" to "complete",
    "request" to obj("search_id" to "s"),"places" to obj("origin" to obj("label" to "A")),
    "result" to obj("search_id" to "s","schema_version" to "routed-contextual-deliberation-v1","routing" to obj("routes" to JSONArray().put(obj("rank" to 0,"mode_key" to "walk","available" to true))),"route_audit" to JSONArray(),
     "contextual_deliberation" to obj("trajectory_archive" to obj("large" to "x".repeat(3_000_000)),"baseline" to obj("winner" to "walk"))),
    "choices" to JSONArray().put(obj("routing_rank" to 0,"mode_key" to "walk","chosen_at" to "choice-time")),
    "journeys" to JSONArray().put(obj("journey_id" to "j","status" to "confirmed","capture_mode" to "manual","tracking" to obj("distance_m" to 12,"elapsed_seconds" to 60,"points" to JSONArray()))))
   store.saveRecord(record)
   val previews=store.historyPreviews(10);assertEquals(1,previews.size)
   assertFalse(previews.single().has("result"));assertEquals(12,previews.single().objects("journeys").single().getJSONObject("tracking").getInt("distance_m"))
   val reflection=store.recordForReflection("s")!!
   assertFalse(reflection.getJSONObject("result").getJSONObject("contextual_deliberation").has("trajectory_archive"))
   val response=obj("identity" to obj("choice_index" to 0,"chosen_at" to "choice-time"),"marker" to "synthetic")
   store.saveExplanation("s",0,"en",response)
   store.saveExplanation("s",0,"en",response)
   val saved=store.record("s")!!
   assertEquals(canonical(record.getJSONObject("result")),canonical(saved.getJSONObject("result")))
   assertEquals(canonical(record.getJSONArray("journeys")),canonical(saved.getJSONArray("journeys")))
   assertEquals(1,saved.objects("explanations").size)
  }}finally{context.deleteDatabase(name)}
 }
 @Test fun realHistoryReadAndNarrationRequestAreBounded(){
  Store(context).use{store->
   val report=JSONObject()
   store.readableDatabase.rawQuery("SELECT count(*),sum(length(CAST(value AS BLOB))) FROM searches",null).use{c->c.moveToFirst();report.put("searches",c.getInt(0)).put("bytes",c.getLong(1))}
   val start=android.os.SystemClock.elapsedRealtime()
   val previews=store.historyPreviews(50)
   report.put("history_load_ms",android.os.SystemClock.elapsedRealtime()-start).put("loaded",previews.size)
   report.put("choices",previews.sumOf{it.objects("choices").size}).put("journeys",previews.sumOf{it.objects("journeys").size})
   val eligible=previews.firstOrNull{it.optString("status")=="complete"&&it.objects("choices").isNotEmpty()}
   if(eligible!=null){
    val record=store.recordForReflection(eligible.getString("search_id"))!!
    val request=obj("record" to record,"choice_index" to record.objects("choices").lastIndex,"confirmed_snapshot" to store.confirmedSnapshot(record),"language" to "en")
    report.put("explanation_request_bytes",request.toString().toByteArray().size)
    context.cacheDir.resolve("history-repair-choice-request.json").writeText(request.toString())
    val response=runBlocking{Api(store).call("/api/dyconet/choice-explanation",request,90000)}
    val index=request.getInt("choice_index")
    val wording=JSONObject(context.assets.open("choiceWordings.json").bufferedReader().use{it.readText()})
    assertTrue("Returned explanation must match saved choice",validReflection(response,record,index,"en",wording))
    report.put("narration",response.optJSONObject("narration")?.copyJson()).put("verified",true)
    context.cacheDir.resolve("history-repair-choice-response.json").writeText(response.toString())
   }
   context.cacheDir.resolve("history-repair-report.json").writeText(report.toString(2))
  }
 }
}
