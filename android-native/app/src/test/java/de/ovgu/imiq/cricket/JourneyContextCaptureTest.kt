package de.ovgu.imiq.cricket

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class JourneyContextCaptureTest {
 @Test fun sharedAndroidBackendContractCases(){
  val cases=JSONArray(File("../../validation/fixtures/context_capture_cases.json").readText())
  val contract=JourneyContract(JSONObject(File("../../contracts/journey-v1.schema.json").readText()))
  cases.objects().forEach {c->assertEquals(c.getString("name"),c.getBoolean("valid"),runCatching {contract.validate(c.getJSONObject("record"),c.getJSONObject("journey"))}.isSuccess)}
 }
 private val case=JSONArray(File("../../validation/fixtures/journey_cases.json").readText()).getJSONObject(2)
 private fun trip()=case.getJSONObject("journey").copyJson().put("status","recording").put("ended_at",JSONObject.NULL).also {it.getJSONObject("tracking").put("elapsed_seconds",JSONObject.NULL)}
 @Test fun samplesUseRetainedFixesAndOnlyOneRequestIsInFlight(){
  val t=trip();val points=t.getJSONObject("tracking").objects("points")
  assertNotNull(JourneyContextCapture.begin(t,points.last()))
  assertNull(JourneyContextCapture.begin(t,points.last()))
  val s=t.getJSONObject("context_capture").objects("samples").single()
  assertTrue(JourneyContextCapture.complete(t,s.getString("sample_id"),null,"2026-10-08T10:02:12Z","provider_or_network_unavailable"))
  val next=points.last().copyJson().put("monotonic_ms",131000).put("recorded_at","2026-10-08T10:03:10Z").put("received_at","2026-10-08T10:03:10Z")
  t.getJSONObject("tracking").getJSONArray("points").put(next)
  assertNotNull(JourneyContextCapture.begin(t,next))
 }
 @Test fun pauseDoesNotStartQueriesAndFinishClosesPendingRequests(){
  val t=trip();val point=t.getJSONObject("tracking").objects("points").last()
  val s=JourneyContextCapture.begin(t,point)!!
  TrackingEngine.pause(t,"2026-10-08T10:02:20Z")
  assertNull(JourneyContextCapture.begin(t,point))
  TrackingEngine.finish(t,"2026-10-08T10:03:00Z",121000,1)
  assertEquals("interrupted",t.getJSONObject("context_capture").objects("samples").single().getString("status"))
  assertFalse(JourneyContextCapture.complete(t,s.getString("sample_id"),null,"2026-10-08T10:03:05Z"))
 }
 @Test fun manualTripsDoNotInventCaptureAndIdentityIsChecked(){
  val t=trip().put("capture_mode","manual")
  assertNull(JourneyContextCapture.begin(t,t.getJSONObject("tracking").objects("points").last()))
  t.put("capture_mode","tracked");JourneyContextCapture.begin(t,t.getJSONObject("tracking").objects("points").last())
  t.getJSONObject("context_capture").objects("samples").single().put("point_monotonic_ms",999)
  assertTrue(runCatching {JourneyContextCapture.validate(t)}.isFailure)
 }
 @Test fun stationaryFixesAllowSamplingWithoutAccumulatingJitter(){
  val t=trip();val old=t.getJSONObject("tracking").objects("points").last()
  val before=t.getJSONObject("tracking").getDouble("distance_m")
  val p=old.copyJson().put("monotonic_ms",old.getLong("monotonic_ms")+60000).put("recorded_at","2026-10-08T10:03:10Z").put("received_at","2026-10-08T10:03:10Z")
  assertTrue(TrackingEngine.fix(t,p))
  assertEquals(before,t.getJSONObject("tracking").getDouble("distance_m"),1e-9)
  assertNotNull(JourneyContextCapture.begin(t,p))
 }
 @Test fun newAuditVersionRequiresMatchingContextAndLegacyRemainsAccepted(){
  for(version in listOf("d-day-context-audit-v1","d-day-context-audit-v2")) {
   val t=trip();val point=t.getJSONObject("tracking").objects("points").last()
   val s=JourneyContextCapture.begin(t,point)!!;val at=point.getString("received_at")
   val context=obj("queried_at" to at)
   if(version.endsWith("v2"))context.put("weather_model_version","d-day-weather-intensity-v2")
   val result=obj("schema_version" to "d-day-context-sample-v1","stored_on_server" to false,"personal_exposure_measured" to false,
    "queried_at" to at,"query_location" to obj("lat" to point.getDouble("lat"),"lon" to point.getDouble("lon")),
    "context" to context,"audit" to obj("schema_version" to version),"raw_observations" to JSONArray())
   assertTrue(JourneyContextCapture.complete(t,s.getString("sample_id"),result,at))
   assertEquals("complete",t.getJSONObject("context_capture").objects("samples").single().getString("status"))
  }
  val t=trip();val point=t.getJSONObject("tracking").objects("points").last()
  val s=JourneyContextCapture.begin(t,point)!!;val at=point.getString("received_at")
  val result=obj("schema_version" to "d-day-context-sample-v1","stored_on_server" to false,"personal_exposure_measured" to false,
   "queried_at" to at,"query_location" to obj("lat" to point.getDouble("lat"),"lon" to point.getDouble("lon")),
   "context" to obj("queried_at" to at),"audit" to obj("schema_version" to "d-day-context-audit-v2"),"raw_observations" to JSONArray())
  JourneyContextCapture.complete(t,s.getString("sample_id"),result,at)
  assertEquals("failed",t.getJSONObject("context_capture").objects("samples").single().getString("status"))
 }
}
