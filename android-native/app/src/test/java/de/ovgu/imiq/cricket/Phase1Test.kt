package de.ovgu.imiq.cricket

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class Phase1Test {
 private val cases=JSONArray(File("../../validation/fixtures/journey_cases.json").readText())
 private val contract=JourneyContract(JSONObject(File("../../contracts/journey-v1.schema.json").readText()))
 private fun record()=cases.getJSONObject(0).getJSONObject("record").copyJson()
 private fun fix(index:Int)=cases.getJSONObject(2).getJSONObject("journey").getJSONObject("tracking").getJSONArray("points").getJSONObject(index).copyJson()
 @Test fun sharedContractCases(){
  for(i in 0 until cases.length()){
   val case=cases.getJSONObject(i);val result=runCatching {contract.validate(case.getJSONObject("record"),case.getJSONObject("journey"))}
   assertEquals("${case.getString("name")}: ${result.exceptionOrNull()}",case.getBoolean("valid"),result.isSuccess)
  }
 }
 @Test fun trackedLifecycleAndSingleConfirmation(){
  val record=record();val trip=TrackingEngine.create(record,0,true,"2026-10-08T10:01:00Z",1000,1)
  contract.validate(record,trip);assertTrue(TrackingEngine.fix(trip,fix(0)));assertTrue(TrackingEngine.fix(trip,fix(1)))
  contract.validate(record,trip)
  TrackingEngine.finish(trip,"2026-10-08T10:03:00Z",121000,1);contract.validate(record,trip)
  assertEquals(120.0,trip.getJSONObject("tracking").getDouble("elapsed_seconds"),0.001)
  assertTrue(trip.getJSONObject("tracking").getDouble("distance_m")>110)
  TrackingEngine.confirm(trip,"as_planned",listOf("walk"),obj("comfort" to 6),"",null,"2026-10-08T10:04:00Z");contract.validate(record,trip)
  assertTrue(runCatching {TrackingEngine.confirm(trip,"as_planned",listOf("walk"),JSONObject(),"",null,"2026-10-08T10:05:00Z")}.isFailure)
  assertFalse(trip.getJSONObject("experience").getBoolean("learning_applied"))
 }
 @Test fun pauseAndGapNeverJoinCoordinates(){
  val record=record();val trip=TrackingEngine.create(record,0,true,"2026-10-08T10:01:00Z",1000,1)
  TrackingEngine.fix(trip,fix(0));TrackingEngine.pause(trip,"2026-10-08T10:01:20Z");contract.validate(record,trip)
  TrackingEngine.resume(trip,"2026-10-08T10:02:00Z");TrackingEngine.fix(trip,fix(1));contract.validate(record,trip)
  assertEquals(0.0,trip.getJSONObject("tracking").getDouble("distance_m"),0.0)
  assertEquals(1,trip.getJSONObject("tracking").getInt("gap_count"))
 }
 @Test fun poorStaleMockDuplicateAndTeleportFixesAreRejected(){
  val trip=TrackingEngine.create(record(),0,true,"2026-10-08T10:01:00Z",1000,1)
  assertFalse(TrackingEngine.fix(trip,fix(0).put("accuracy_m",100)))
  assertFalse(TrackingEngine.fix(trip,fix(0).put("mock",true)))
  assertFalse(TrackingEngine.fix(trip,fix(0).put("recorded_at","2026-10-08T10:00:00Z")))
  assertTrue(TrackingEngine.fix(trip,fix(0)))
  assertFalse(TrackingEngine.fix(trip,fix(0)))
  assertFalse(TrackingEngine.fix(trip,fix(1).put("lat",55.0)))
  assertEquals(5,trip.getJSONObject("tracking").getInt("rejected_fixes"))
  assertTrue(trip.getJSONObject("tracking").isNull("distance_m"))
 }
 @Test fun manualConfirmationDoesNotInventMetrics(){
  val record=record();val trip=TrackingEngine.create(record,0,false,"2026-10-08T10:01:00Z",1000,1)
  TrackingEngine.confirm(trip,"not_taken",emptyList(),JSONObject(),"",null,"2026-10-08T10:02:00Z");contract.validate(record,trip)
  assertTrue(trip.getJSONObject("tracking").isNull("elapsed_seconds"));assertTrue(trip.getJSONObject("tracking").isNull("distance_m"))
 }
 @Test fun routingAliasesStartAndConfirmBothCaptureModes(){
  for(raw in listOf("foot","walk","bike","pt","pt_bus_tram","car","car_driver"))for(tracked in listOf(false,true)) {
   val record=record()
   record.getJSONObject("result").getJSONObject("routing").getJSONArray("routes").getJSONObject(0).put("mode_key",raw)
   record.getJSONArray("choices").getJSONObject(0).put("mode_key",raw)
   val trip=TrackingEngine.create(record,0,tracked,"2026-10-08T10:01:00Z",1000,1)
   contract.validate(record,trip)
   assertEquals(raw,trip.getJSONObject("selection").getString("mode_key"))
   if(tracked)TrackingEngine.finish(trip,"2026-10-08T10:03:00Z",121000,1)
   TrackingEngine.confirm(trip,"as_planned",listOf(modelMode(raw)),JSONObject(),"",null,"2026-10-08T10:04:00Z")
   contract.validate(record,trip)
   assertEquals(modelMode(raw),trip.getJSONObject("experience").getJSONArray("actual_modes").getString(0))
   trip.getJSONObject("experience").put("actual_modes",JSONArray(listOf(if(modelMode(raw)=="car")"walk"else"car")))
   assertTrue(runCatching {contract.validate(record,trip)}.isFailure)
  }
 }
 @Test fun processInterruptionCanResumeWithoutJoiningGap(){
  val record=record();val trip=TrackingEngine.create(record,0,true,"2026-10-08T10:01:00Z",1000,1)
  TrackingEngine.fix(trip,fix(0));TrackingEngine.pause(trip,"2026-10-08T10:01:20Z",true)
  val restored=JSONObject(trip.toString());TrackingEngine.resume(restored,"2026-10-08T10:02:00Z");TrackingEngine.fix(restored,fix(1));contract.validate(record,restored)
  assertEquals("interrupted",restored.getJSONObject("tracking").getJSONArray("events").getJSONObject(1).getString("kind"))
  assertEquals(0.0,restored.getJSONObject("tracking").getDouble("distance_m"),0.0)
 }
 @Test fun cancellationIsNotAnExperience(){
  val record=record();val trip=TrackingEngine.create(record,0,true,"2026-10-08T10:01:00Z",1000,1)
  TrackingEngine.finish(trip,"2026-10-08T10:03:00Z",121000,1,true);contract.validate(record,trip)
  assertEquals("cancelled",trip.getString("status"));assertTrue(trip.isNull("experience"))
 }
}
