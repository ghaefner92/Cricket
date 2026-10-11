package de.ovgu.imiq.cricket

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DailyPassportDeviceTest {
 @Test fun liveDailyConfirmationDeliberationAndExperienceAreLinkedAndAtomic()=runBlocking {
  val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
  val name="daily-cycle-${UUID.randomUUID()}.db";val store=Store(context,name)
  try {
   val api=Api(store)
   val plan=proposedDailyPlan(null,null,today()).put("points",obj(*goalKeys.map {it to 4}.toTypedArray())).put("confirmedAt",now())
   val start=api.call("/api/dyconet/adaptive-passport/start",obj("schema_version" to "adaptive_cognitive_passport_onboarding_1.0","agent_id" to "synthetic-daily-device",
    "responses" to obj("needs" to needImportanceRatings(plan.getJSONObject("points")),"valences" to obj(*modes.map {it to 0}.toTypedArray()))))
   val completed=api.call("/api/dyconet/adaptive-passport/complete",obj("schema_version" to start.getString("schema_version"),"start" to start,"answers" to obj(*start.objects("questions").map {it.getString("cell_id") to 4}.toTypedArray())))
   val initial=api.call("/api/dyconet/adaptive-passport/bootstrap",obj("schema_version" to "adaptive_hotco_bootstrap_1.0","completed_passport" to completed,"availability" to obj(*modes.map {it to true}.toTypedArray()),"daily_goals" to dailyGoalPayload(plan)))
   val wrapper=obj("version" to 1,"fingerprint" to "synthetic-daily","confirmedAt" to now(),"response" to initial,"sourceSnapshot" to obj("plan" to plan))
   assertTrue(validPassport(wrapper));store.put(PASSPORT,wrapper)
   val revisedPlan=plan.copyJson().put("points",obj(*goalKeys.map {it to 7}.toTypedArray())).put("confirmedAt",now())
   val revised=api.call("/api/dyconet/daily-passport/confirm",obj("schema_version" to "daily-passport-confirm-v1","previous_passport" to initial.getJSONObject("cognitive_passport"),"daily_goals" to dailyGoalPayload(revisedPlan)))
   val revisedWrapper=wrapper.copyJson().put("response",revised).put("sourceSnapshot",obj("plan" to revisedPlan))
   store.commitDailyPassport(wrapper,revisedWrapper,revisedPlan)
   assertEquals(canonical(initial.getJSONObject("cognitive_passport").getJSONObject("profile").getJSONObject("beliefs")),canonical(revised.getJSONObject("cognitive_passport").getJSONObject("profile").getJSONObject("beliefs")))
   assertThrows(IllegalArgumentException::class.java){store.commitDailyPassport(wrapper,revisedWrapper,revisedPlan)}
   val candidate=obj("route_id" to "daily-route","origin" to obj("latitude" to 52.13,"longitude" to 11.62),"destination" to obj("latitude" to 52.14,"longitude" to 11.64),
    "summary" to obj("duration_seconds" to 600,"distance_meters" to 1800),"legs" to JSONArray(listOf(obj("mode" to "walk","duration_seconds" to 600,"distance_meters" to 1800))))
   val simulation=api.call("/api/dyconet/contextual-deliberation",obj("search_id" to "daily-device-search","timestamp" to now(),"cognitive_passport" to revised.getJSONObject("cognitive_passport"),"candidate_routes" to JSONArray(listOf(candidate)),"record_trajectories" to true,"context_policy" to "observations_only"),120000)
   val evidence=simulation.getJSONObject("baseline").getJSONObject("simulation_evidence")
   assertEquals(2001,simulation.getJSONObject("trajectory_archive").getJSONArray(evidence.getString("trajectory_id")).length())
   val record=JSONArray(instrumentation.context.assets.open("journey_cases.json").bufferedReader().use {it.readText()}).getJSONObject(0).getJSONObject("record").copyJson()
   record.put("search_id","daily-device-search").put("journeys",JSONArray()).put("confirmed_snapshot",revisedWrapper).put("daily_goals_reference",revised.getJSONObject("cognitive_passport").getJSONObject("daily_goals"))
   record.getJSONObject("request").put("search_id","daily-device-search").put("cognitive_passport",revised.getJSONObject("cognitive_passport")).put("context_policy","observations_only")
   record.getJSONObject("result").put("search_id","daily-device-search").put("contextual_deliberation",simulation)
   val lineage=revised.getJSONObject("cognitive_passport").getJSONObject("lineage")
   record.put("passport_reference",obj("passport_id" to lineage.getString("passport_id"),"revision" to lineage.getInt("revision"),"fingerprint" to "synthetic-daily","confirmed_at" to now()))
   record.getJSONArray("choices").put(record.getJSONArray("choices").getJSONObject(0).copyJson().put("choice_event_id","choice-second-daily"))
   record.put("comfort_pilot_required",true).put("affect_pilot_required",true)
   record.put("completed_at",now())
   record.put("d_day_forecast",obj("status" to "queued"));store.saveRecord(record)
   ProspectiveLearning.enqueue(context,"daily-device-search",store.databaseName).join()
   val frozen=store.record("daily-device-search")!!.getJSONObject("d_day_forecast")
   assertEquals(frozen.optString("failure"),"complete",frozen.getString("status"))
   assertFalse(frozen.getJSONObject("request").toString().contains("trajectory_archive"))
   record.put("d_day_forecast",frozen.copyJson())
   kotlinx.coroutines.delay(10)
   record.objects("choices").forEach {it.put("chosen_at",now())}
   store.saveRecord(record)
   val repository=TripRepository(context,store);val trip=repository.start("daily-device-search",1,false)
   val satisfaction=obj("comfort" to 6,"pro_env" to 7);val emotions=obj("walk" to 2)
   assertThrows(IllegalArgumentException::class.java){repository.confirmExperience("daily-device-search",trip.getString("journey_id"),"as_planned",listOf("walk"),JSONObject(),"",null,now(),satisfaction,emotions)}
   val comfort=comfortObservation("walk",6,"en",now(),false)
   assertThrows(IllegalArgumentException::class.java){repository.confirmExperience("daily-device-search",trip.getString("journey_id"),"as_planned",listOf("walk"),JSONObject(),"",null,now(),satisfaction,JSONObject(),comfort)}
   repository.confirmExperience("daily-device-search",trip.getString("journey_id"),"as_planned",listOf("walk"),JSONObject(),"",null,now(),satisfaction,emotions,comfort)
   val saved=store.passport()!!;val savedPassport=saved.getJSONObject("response").getJSONObject("cognitive_passport")
   assertEquals(lineage.getString("passport_id"),savedPassport.getJSONObject("lineage").getString("parent_passport_id"))
   assertEquals(canonical(revised.getJSONObject("cognitive_passport").getJSONObject("profile").copyJson()),canonical(savedPassport.getJSONObject("profile")))
   assertEquals(1,savedPassport.getJSONArray("evidence_history").length())
   repository.confirmExperience("daily-device-search",trip.getString("journey_id"),"as_planned",listOf("walk"),JSONObject(),"",null,now(),satisfaction,emotions,comfort)
   assertEquals(canonical(saved),canonical(store.passport()))
   assertThrows(IllegalArgumentException::class.java){repository.confirmExperience("daily-device-search",trip.getString("journey_id"),"as_planned",listOf("walk"),JSONObject(),"",null,now(),obj("comfort" to 1),emotions)}
   val persisted=store.record("daily-device-search")!!;repository.contract.validate(persisted,persisted.objects("journeys").single())
   val evaluation=persisted.objects("d_day_evaluations").single()
   assertEquals("scored",evaluation.getString("status"));assertEquals(6,evaluation.getJSONArray("scores").length())
   val scoredEvent=evaluation.getString("event_id")
   assertEquals(persisted.objects("journeys").single().getJSONObject("experience").getString("event_id"),scoredEvent)
   val frozenBefore=canonical(persisted.getJSONObject("d_day_forecast"))
   ProspectiveLearning.enqueue(context,"daily-device-search",store.databaseName).join()
   assertEquals(frozenBefore,canonical(store.record("daily-device-search")!!.getJSONObject("d_day_forecast")))
   val affectExperience=persisted.objects("journeys").single().getJSONObject("experience")
   assertEquals(2,affectExperience.getJSONObject("mode_valences_raw_minus3_to_3").getInt("walk"))
   assertEquals(2.0/3,affectExperience.getJSONObject("mode_valences_normalized_minus1_to_1").getDouble("walk"),1e-9)
   assertEquals("choice-second-daily",persisted.objects("journeys").single().getString("choice_event_id"))
   assertEquals(2001,persisted.getJSONObject("result").getJSONObject("contextual_deliberation").getJSONObject("trajectory_archive").getJSONArray(evidence.getString("trajectory_id")).length())
   val receipt=api.call("/api/dyconet/journeys/validate",obj("record" to persisted.copyJson().apply {remove("journeys")},"journey" to persisted.objects("journeys").single()))
   assertEquals("valid",receipt.getString("status"));assertFalse(receipt.getBoolean("learning_applied"))
   kotlinx.coroutines.delay(5)
   val nextSearch=persisted.copyJson().put("search_id","synthetic-next-forecast").put("completed_at",now()).put("confirmed_snapshot",saved)
   val nextForecast=api.call("/api/dyconet/d-day/prospective/forecast",prospectiveRequest(store,nextSearch),180000)
   for(target in listOf("comfort","affect"))assertEquals(1,nextForecast.getJSONObject("targets").getJSONObject(target).objects("predictions").first {it.getString("mode")=="walk"}.getJSONObject("models").getJSONObject("basic").getInt("observation_count"))
   assertEquals(canonical(saved),canonical(store.passport()))
   val backup=store.export()
   assertEquals(1,backup.getJSONObject("longitudinal").getJSONArray("experience_events").length())
   assertEquals(1,persisted.getJSONArray("passport_transitions").length())
   val validation=api.call("/api/dyconet/d-day/validate",backup,120000)
   assertEquals("valid",validation.getString("status"));assertEquals(1,validation.getInt("experience_events"))
   assertFalse(validation.getBoolean("learning_applied"));assertFalse(validation.getBoolean("state_carryover_applied"))
   val observation=obj("event_id" to "experience-${trip.getString("journey_id")}","subject_id" to "synthetic-daily-device","journey_id" to trip.getString("journey_id"),"choice_event_id" to "choice-second-daily",
    "mode" to "walk","rating" to 6,"question_version" to "comfort-physical-bipolar-v1","scale" to "helps-hinders-1-to-7","attribution" to "actual_mode","confirmed_at" to persisted.objects("journeys").single().getJSONObject("experience").getString("confirmed_at"),"context" to obj("queried_at" to now(),"measurements" to JSONArray()))
   val pilot=api.call("/api/dyconet/d-day/comfort-pilot/analyze",obj("schema_version" to "d-day-comfort-pilot-request-v1","subject_id" to "synthetic-daily-device","initial_passport" to initial.getJSONObject("cognitive_passport"),
    "observations" to JSONArray(listOf(observation.apply {getJSONObject("context").put("queried_at",getString("confirmed_at"))})),
    "prediction_contexts" to JSONArray(listOf(obj("prediction_id" to "device-pilot","mode" to "walk","as_of" to now(),"context" to obj("queried_at" to observation.getString("confirmed_at"),"measurements" to JSONArray())))),"draws" to 512),120000)
   assertFalse(pilot.getBoolean("learning_applied_to_active_passport"));assertFalse(pilot.getBoolean("hotco_modified"))
   assertEquals(1,pilot.getJSONArray("predictions").getJSONObject(0).getJSONObject("models").getJSONObject("basic").getInt("observation_count"))
   val affectObservation=observation.copyJson().apply {remove("rating")}.put("rating_raw_minus3_to_3",2).put("valence_normalized",2.0/3)
    .put("question_version","mode-affect-ordinal-minus3-to3-v1").put("scale","minus3-to-3")
   val affectPilot=api.call("/api/dyconet/d-day/affect-pilot/analyze",obj("schema_version" to "d-day-affect-pilot-request-v1","subject_id" to "synthetic-daily-device","initial_passport" to initial.getJSONObject("cognitive_passport"),
    "observations" to JSONArray(listOf(affectObservation)),"prediction_contexts" to JSONArray(listOf(obj("prediction_id" to "device-affect","mode" to "walk","as_of" to now(),"context" to observation.getJSONObject("context")))),"draws" to 512),120000)
   assertFalse(affectPilot.getBoolean("learning_applied_to_active_passport"));assertFalse(affectPilot.getBoolean("hotco_modified"))
   assertEquals(1,affectPilot.getJSONArray("predictions").getJSONObject(0).getJSONObject("models").getJSONObject("basic").getInt("observation_count"))
   assertEquals(canonical(saved),canonical(store.passport()))
   context.cacheDir.resolve("affect-pilot-synthetic-backup.json").writeText(backup.toString())
   val bad=backup.copyJson();bad.getJSONObject("longitudinal").getJSONArray("experience_events").getJSONObject(0).put("choice_event_id","forged")
   val badName="daily-bad-${UUID.randomUUID()}.db"
   try {Store(context,badName).use {restore->assertThrows(IllegalArgumentException::class.java){restore.importBackup(bad)};assertNull(restore.passport())}}finally{context.deleteDatabase(badName)}
   val tomorrow=java.time.LocalDate.parse(today()).plusDays(1).toString()
   val proposal=proposedDailyPlan(null,saved,tomorrow)
   assertFalse(proposal.has("confirmedAt"));assertEquals(7,proposal.getJSONObject("points").getInt("env"))
   val restoreName="daily-restore-${UUID.randomUUID()}.db"
   try {Store(context,restoreName).use {restore->restore.importBackup(store.export());assertEquals(canonical(saved),canonical(restore.passport()));assertNotNull(restore.record("daily-device-search"))}}finally{context.deleteDatabase(restoreName)}
  }finally{store.close();context.deleteDatabase(name)}
 }
}
