package de.ovgu.imiq.cricket

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class RainPresentationTest {
 @Test fun latestKnownPublishedTimeSelectsValueWithoutCertifyingFreshnessAndUvIsHidden() {
  val older=rain(value=1.0).put("timestamp","2026-10-11T10:00:00Z")
  val latest=rain(value=2.0).put("timestamp","2026-10-11T11:00:00Z")
  assertSame(latest,displayedRain(listOf(older,latest)))
  assertEquals("11.10.2026 13:00",rainKnownTime(latest,false))
  assertNull(rainReportInstant(latest))
  assertEquals(listOf(latest),weatherDisplayReadings(listOf(latest,JSONObject().put("variable","uvIndex"))))
 }
 private fun rain(id:String="Sensor:Weather:Winfred",field:String="rainIntensity",unit:String="mm/h",value:Double=0.0)=
  JSONObject().put("source_entity_id",id).put("variable","rain").put("status","OBSERVED").put("unit",unit).put("numeric_value",value)
   .put("source_metadata",JSONObject().put("source_attribute",field))
 @Test fun onlyWinfredIntensityIsDisplayedAndZeroIsValid() {
  val correct=rain()
  assertSame(correct,displayedRain(listOf(rain("Sensor:Weather:Walter"),rain(field="cumulativeRainfall"),rain(unit="mm"),correct)))
  assertNull(displayedRain(listOf(rain(value=-1.0),rain(value=451.0))))
  assertEquals(listOf(correct),weatherDisplayReadings(listOf(rain("Sensor:Weather:Walter"),correct,JSONObject().put("variable","cumulativeRainfall"))))
 }
 @Test fun queryTimeAndPublishedTimeNeverBecomeReportAge() {
  val reading=rain().put("timestamp","2026-10-11T12:00:00Z")
  reading.getJSONObject("source_metadata").put("provider_response_received_at","2026-10-11T12:00:00Z")
  assertEquals("Report age unknown",rainReportAge(reading,false,Instant.parse("2026-10-11T12:01:00Z")))
 }
 @Test fun explicitReportAgeIsShownAndFutureReportIsUnknown() {
  val reading=rain()
  reading.getJSONObject("source_metadata").put("last_report_at","2026-10-11T12:00:00Z").put("last_report_time_provenance","EXPLICIT_ATTRIBUTE_RECEIVED_AT")
  assertEquals("Reported 45 min ago",rainReportAge(reading,false,Instant.parse("2026-10-11T12:45:00Z")))
  assertEquals("Report age unknown",rainReportAge(reading,false,Instant.parse("2026-10-11T11:45:00Z")))
 }
}
