package de.ovgu.imiq.cricket

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RouteSummaryDataTest {
 @Test fun unsupportedTramGeometryIsHiddenWhileWalkingAndSourceRemainIntact() {
  val walk=obj("mode" to "walk","geometry" to obj("type" to "LineString","coordinates" to JSONArray("[[11,52],[11.001,52]]")))
  val tram=obj("mode" to "pt","geometry" to obj("type" to "LineString","coordinates" to JSONArray("[[11.001,52],[11.002,52.001]]")),"transit" to obj("source_mode" to "TRAM","line" to "6","geometry_quality" to "APPROXIMATE_NO_SHAPES","from" to obj("lon" to 11.001,"lat" to 52),"to" to obj("lon" to 11.002,"lat" to 52.001)))
  val route=obj("rank" to 1,"mode_key" to "pt","available" to true,"legs" to JSONArray().put(walk).put(tram))
  val result=obj("routing" to obj("routes" to JSONArray().put(route)));val before=result.toString()
  assertEquals(listOf(true,false),mapRoutes(result).single().segments.map {it.drawable})
  val patch=obj("geometry" to obj("type" to "LineString","coordinates" to JSONArray("[[11.001,52],[11.002,52],[11.002,52.001]]")))
  assertEquals(listOf(true,true),mapRoutes(result,mapOf("1-pt/1" to patch)).single().segments.map {it.drawable})
  assertEquals(3,mapRoutes(result,mapOf("1-pt/1" to patch)).single().segments.last().points.size)
  patch.put("reference_alignment",obj("source" to "OSM_REFERENCE_NETWORK"))
  val reference=mapRoutes(result,mapOf("1-pt/1" to patch)).single().segments.last()
  assertTrue(reference.drawable);assertTrue(reference.approximate);assertTrue(reference.referenceNetwork)
  assertFalse(mapRoutes(result,mapOf("1-pt/1" to patch)).single().segments.first().referenceNetwork)
  assertEquals(before,result.toString())
 }
 private val now=1_791_500_000_000L
 private fun card(mode:String,seconds:Double=600.0)=RouteCard(obj("rank" to 1,"mode_key" to mode,"summary" to obj("duration_seconds" to seconds,"distance_meters" to 1200)),null,null)
 private fun transit():RouteCard=card("pt").also {
  it.route.put("transit",obj("start_time_ms" to now+480_000,"end_time_ms" to now+1_980_000))
  it.route.put("legs",JSONArray().put(obj("mode" to "walk")).put(obj("mode" to "pt","transit" to obj("source_mode" to "TRAM","line" to "6"))))
 }
 @Test fun activeModesEstimateArrivalFromNowWithoutChangingRoute() {
  for(mode in listOf("foot","walk","bike","car","car_driver")) {
   val card=card(mode);val before=card.route.toString()
   assertEquals(now+600_000.0,summaryArrivalMs(card,now)!!,0.0)
   assertEquals(now+660_000.0,summaryArrivalMs(card,now+60_000)!!,0.0)
   assertEquals(before,card.route.toString())
  }
 }
 @Test fun unknownOriginAccessKeepsMappedTimeButCannotEstimateArrival() {
  val card=card("car").also {it.route.getJSONObject("summary").put("origin_access_time_unknown",true).put("origin_access_offset_m",62.0)}
  assertNull(summaryArrivalMs(card,now))
  assertEquals(now+600_000.0,carArrivalLowerBoundMs(card,now)!!,0.0)
  assertEquals(62.0,originAccessGap(card)!!,0.0)
  assertEquals("10 min mapped route",routeDurationLabel(card,now,false))
 }
 @Test fun transitUsesProviderArrivalAndCountsInitialWaitOnce() {
  val card=transit();val before=card.route.toString()
  assertEquals(now+1_980_000.0,summaryArrivalMs(card,now)!!,0.0)
  assertEquals(now+1_980_000.0,summaryArrivalMs(card,now+60_000)!!,0.0)
  assertEquals(33,countdownMinutes(transitTiming(card,now)!!.fromNowSeconds))
  assertEquals(before,card.route.toString())
 }
 @Test fun invalidMissingAndExpiredTimesStayUnknown() {
  assertNull(summaryArrivalMs(card("walk",-1.0),now))
  assertNull(summaryArrivalMs(card("car").also {it.route.getJSONObject("summary").remove("duration_seconds")},now))
  assertNull(carArrivalLowerBoundMs(card("car"),now))
  assertNull(carArrivalLowerBoundMs(card("car",-1.0).also {it.route.getJSONObject("summary").put("origin_access_time_unknown",true).put("origin_access_offset_m",62)},now))
  assertNull(summaryArrivalMs(card("pt"),now))
  val expired=transit()
  assertNull(summaryArrivalMs(expired,now+600_000))
  val invalid=transit().also {it.route.getJSONObject("transit").put("end_time_ms",now-1)}
  assertNull(summaryArrivalMs(invalid,now))
 }
 @Test fun transitSymbolsFollowLegsAndTransferOrderWithNoInventedVehicle() {
  val card=transit()
  card.route.getJSONArray("legs").put(obj("mode" to "walk"))
   .put(obj("mode" to "pt","transit" to obj("source_mode" to "BUS","line" to "48")))
   .put(obj("mode" to "pt","transit" to obj("source_mode" to "BUS","line" to "48")))
   .put(obj("mode" to "pt","transit" to JSONObject()))
  assertEquals(listOf(TransitService("tram","6"),TransitService("bus","48"),TransitService("pt",null)),transitServices(card))
  assertTrue(transitServices(card("walk")).isEmpty())
 }
}
