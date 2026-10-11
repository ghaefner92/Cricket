package de.ovgu.imiq.cricket
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
class MobilityPoiTest {
 @Test fun vehicleStopsAreNeverPassengerMarkersEvenWhenTaggedBusStop() {
  val rail=poi("rail","stop").copy(tags=JSONObject().put("public_transport","stop_position").put("highway","bus_stop"))
  val platform=poi("platform","stop").copy(tags=JSONObject().put("public_transport","platform"))
  assertFalse(rail.waitingPlace);assertTrue(platform.waitingPlace)
  assertEquals(listOf("platform"),platformPois(listOf(rail,platform),JSONObject().put("stops",org.json.JSONArray())).map {it.id})
 }
 private fun poi(id:String,kind:String="parking",lat:Double=52.0,access:String="yes")=MobilityPoi(id,kind,GeoPoint(lat,11.0),id,JSONObject().put("access",access),"node")
 @Test fun modeAndBothEndpointRadiiDetermineVisibleSymbols() {
  val values=listOf(poi("a"),poi("b",lat=52.03),poi("far",lat=53.0),poi("stop","stop"))
  assertEquals(listOf("a","b"),nearbyMobility(values,"car",GeoPoint(52.0,11.0),GeoPoint(52.03,11.0)).map {it.id})
  assertEquals(listOf("stop"),nearbyMobility(values,"pt",GeoPoint(52.0,11.0),null).map {it.id})
  assertTrue(nearbyMobility(values,"bike",GeoPoint(52.0,11.0),null).isEmpty())
 }
 @Test fun automaticSuggestionRequiresPublicAccessAndTwoKilometres() {
  val values=listOf(poi("private",access="private"),poi("unknown",access="unknown"),poi("public",lat=52.001),poi("far",lat=53.0))
  assertEquals("public",nearestPublicParking(values,GeoPoint(52.0,11.0))?.id)
  assertNull(nearestPublicParking(values,GeoPoint(50.0,11.0)))
  assertTrue(values[1].selectable);assertFalse(values[0].selectable)
 }
 @Test fun clustersRetainEveryMappedPlaceAndKeepSelectedParkingIndividual() {
  val points=listOf(poi("a") to androidx.compose.ui.geometry.Offset(1f,1f),poi("b") to androidx.compose.ui.geometry.Offset(2f,2f),poi("selected") to androidx.compose.ui.geometry.Offset(3f,3f))
  val groups=clusterMobility(points,listOf("selected"),48f)
  assertEquals(2,groups.size)
  assertEquals(listOf("selected"),groups.last().members.map {it.id})
  assertEquals(setOf("a","b","selected"),groups.flatMap {it.members}.map {it.id}.toSet())
  assertEquals(3,clusterMobility(points,emptyList(),0f).size)
 }
 @Test fun defaultRadiusIsTwoHundredMetresAndGpsCanBeDisabled() {
  val preferences=MapPoiPreferences.fromJson(null)
  assertEquals(200,preferences.radius)
  val values=listOf(poi("near",lat=52.001),poi("outside",lat=52.003),poi("gps",lat=53.0),poi("private",access="private"))
  assertEquals(setOf("near","gps"),visibleMobility(values,preferences,GeoPoint(52.0,11.0),null,GeoPoint(53.0,11.0)).map {it.id}.toSet())
  assertEquals(listOf("near"),visibleMobility(values,preferences.copy(gps=false),GeoPoint(52.0,11.0),null,GeoPoint(53.0,11.0)).map {it.id})
  assertTrue(visibleMobility(values,preferences.copy(parking=false),GeoPoint(52.0,11.0),null,null).isEmpty())
  assertEquals(preferences,MapPoiPreferences.fromJson(preferences.json()))
 }
 @Test fun clustersNeverMergeParkingWithTransitStops() {
  val points=listOf(poi("P") to androidx.compose.ui.geometry.Offset(1f,1f),poi("H","stop") to androidx.compose.ui.geometry.Offset(2f,2f))
  assertEquals(2,clusterMobility(points,emptyList(),48f).size)
 }
}
