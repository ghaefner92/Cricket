package de.ovgu.imiq.cricket

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class HistoryPreviewTest {
 @Test fun previewKeepsJourneyActionsAndMetricsWithoutSimulationOrGpsPayload() {
  val record=obj("search_id" to "s","status" to "complete","result" to obj("large" to true),"choices" to JSONArray().put(obj("mode_key" to "pt")),"journeys" to JSONArray().put(obj("journey_id" to "j","status" to "confirmed","capture_mode" to "gps","tracking" to obj("distance_m" to 100,"elapsed_seconds" to 60,"points" to JSONArray().put(obj("lat" to 52))))))
  val original=record.toString();val preview=historyPreview(record)
  assertFalse(preview.has("result"));assertEquals(1,preview.objects("choices").size)
  val trip=preview.objects("journeys").single()
  assertEquals("j",trip.getString("journey_id"));assertEquals(100,trip.getJSONObject("tracking").getInt("distance_m"))
  assertFalse(trip.getJSONObject("tracking").has("points"));assertEquals(original,record.toString())
 }
}
