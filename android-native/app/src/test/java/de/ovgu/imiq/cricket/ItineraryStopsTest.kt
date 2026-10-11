package de.ovgu.imiq.cricket
import org.junit.Test
import org.junit.Assert.*
import org.json.JSONObject
class ItineraryStopsTest {
 @Test fun transferWinsSharedEndpointAndSequenceAddsOnlyConfirmedIntermediateStops(){
  val route=JSONObject("""{"mode_key":"pt","rank":3,"legs":[{"mode":"pt","transit":{"line":"1","from":{"lat":52,"lon":11,"stop":{"gtfsId":"a"}},"to":{"lat":52.01,"lon":11,"stop":{"gtfsId":"b"}}}},{"mode":"pt","transit":{"line":"9","from":{"lat":52.01,"lon":11,"stop":{"gtfsId":"b"}},"to":{"lat":52.03,"lon":11,"stop":{"gtfsId":"d"}}}}]}""")
  val details=JSONObject("""{"legs":[{"leg_index":1,"stops":[{"gtfsId":"b","lat":52.01,"lon":11},{"gtfsId":"c","lat":52.02,"lon":11},{"gtfsId":"d","lat":52.03,"lon":11}]}]}""")
  val card=RouteCard(route,null,null);val before=route.toString()
  val stops=itineraryStopPois(card,emptyList(),details)
  assertEquals(4,stops.size)
  assertEquals("boarding",stops.first().tags.getString("route_role"))
  assertEquals("transfer",stops[1].tags.getString("route_role"))
  assertEquals("1 ↔ 9",stops[1].tags.getString("lines"))
  assertEquals("intermediate",stops[2].tags.getString("route_role"))
  assertEquals("final",stops[3].tags.getString("route_role"))
  assertTrue(stops.all {it.tags.getBoolean("display_only")})
  assertEquals(before,route.toString())
  assertEquals(3,itineraryStopPois(card,emptyList(),null).size)
 }
}
