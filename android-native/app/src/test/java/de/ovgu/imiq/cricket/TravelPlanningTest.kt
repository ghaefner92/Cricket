package de.ovgu.imiq.cricket

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class TravelPlanningTest {
 @Test fun unavailableIconSeparatesAndroidPermissionFromEnabledServices(){
  assertTrue(locationAccessBlocked(false,true))
  assertTrue(locationAccessBlocked(true,false))
  assertTrue(locationAccessBlocked(false,false))
  assertFalse(locationAccessBlocked(true,true))
 }

 @Test fun addressLabelsNeverMoveTheChosenPointOrCertifyAHouseNumber(){
  val point=GeoPoint(52.130123456,11.630987654)
  val place=selectedMapPlace(point,"Bahnhofstraße 69, Magdeburg",false)
  assertEquals(point.lat,place.lat,0.0);assertEquals(point.lon,place.lon,0.0)
  assertTrue(place.label.startsWith("Map point · near "))
  assertEquals("",place.house);assertEquals("map_selection",place.source)
  assertEquals(place,Place.fromJson(place.json()))
 }
 @Test fun reverseLookupFailureStillAllowsAnExactCoordinateSelection(){
  val point=GeoPoint(52.130123456,11.630987654)
  val place=selectedMapPlace(point,null,true)
  assertEquals("52.13012, 11.63099",place.label)
  assertEquals(point.lat,place.lat,0.0);assertEquals(point.lon,place.lon,0.0)
  assertThrows(IllegalArgumentException::class.java){selectedMapPlace(GeoPoint(Double.NaN,11.6),null,false)}
  assertThrows(IllegalArgumentException::class.java){selectedMapPlace(GeoPoint(91.0,11.6),null,false)}
 }
 @Test fun planningNeverDisplaysOldSearchOrHistoricalGeometry(){
  val current=JSONObject("{\"previous_search\":true}");val saved=JSONObject("{\"saved\":true}")
  assertNull(planningMapResult(true,current,saved))
  assertTrue(mapRoutes(planningMapResult(true,current,saved)).isEmpty())
  assertSame(current,planningMapResult(false,current))
  assertSame(saved,planningMapResult(false,current,saved))
  assertTrue(current.getBoolean("previous_search"));assertTrue(saved.getBoolean("saved"))
 }
}
