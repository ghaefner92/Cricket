package de.ovgu.imiq.cricket
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
class TransitMapColorTest {
 @Test fun mixedItineraryKeepsVehicleTypeAndDistinctBusColour(){
  val result=JSONObject("""{"routing":{"routes":[{"rank":1,"mode_key":"pt","available":true,"legs":[{"mode":"pt","geometry":{"type":"LineString","coordinates":[[11,52],[11.001,52.001]]},"transit":{"source_mode":"TRAM","geometry_quality":"GTFS_SHAPE"}},{"mode":"pt","geometry":{"type":"LineString","coordinates":[[11.001,52.001],[11.002,52.002]]},"transit":{"source_mode":"BUS","geometry_quality":"APPROXIMATE_NO_SHAPES"}}]}]}}""")
  val original=result.toString();val segments=mapRoutes(result).single().segments
  assertEquals(listOf("TRAM","BUS"),segments.map {it.transitVehicle})
  assertTrue(segments.first().drawable);assertTrue(segments.last().approximate)
  assertNotEquals(mapSegmentColor(segments.first()),mapSegmentColor(segments.last()))
  assertEquals(original,result.toString())
 }
}
