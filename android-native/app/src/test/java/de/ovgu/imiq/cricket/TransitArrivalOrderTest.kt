package de.ovgu.imiq.cricket
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class TransitArrivalOrderTest {
 @Test fun parkingIsOptionalAndIndependentInTransitView(){
  val prefs=obj("parking" to true)
  assertFalse(mapParkingVisible(prefs,"pt",false))
  assertTrue(mapParkingVisible(prefs,"car",false))
  assertTrue(mapParkingVisible(prefs,"pt",true))
  prefs.put("ptParking",true)
  assertTrue(mapParkingVisible(prefs,"pt",false))
  prefs.put("parking",false)
  assertTrue(mapParkingVisible(prefs,"pt",false))
  assertFalse(mapParkingVisible(prefs,"car",false))
 }
 private val now=1_800_000_000_000L
 private fun card(rank:Int,line:String,start:Long,board:Long,end:Long,access:Double=300.0)=RouteCard(obj(
  "rank" to rank,"mode_key" to "pt","available" to true,
  "transit" to obj("start_time_ms" to start,"end_time_ms" to end),
  "legs" to JSONArray(listOf(
   obj("mode" to "walk","distance_meters" to access,"duration_seconds" to 240),
   obj("mode" to "pt","transit" to obj("line" to line,"start_time_ms" to board,"from" to obj("name" to "A"),"to" to obj("name" to "B"))),
   obj("mode" to "walk","distance_meters" to 9000,"duration_seconds" to 900)
  ))),null,null)
 @Test fun earlierArrivalWinsEvenWithLaterBoardingAndLongerAccess(){
  val slow=card(1,"1",now+60000,now+120000,now+3600000,100.0)
  val fast=card(2,"73",now+120000,now+300000,now+1800000,1200.0)
  val before=fast.route.toString()
  assertEquals(listOf(fast,slow),sortedCards(listOf(slow,fast),now))
  assertEquals(before,fast.route.toString())
  assertEquals(1200.0,boardingAccess(fast)!!.first,0.0)
 }
 @Test fun expiredRouteCannotBecomeFirstEvenWithEarlierArrival(){
  val expired=card(1,"73",now-60000,now+60000,now+120000)
  val usable=card(2,"1",now+60000,now+120000,now+600000)
  assertEquals(usable,sortedCards(listOf(expired,usable),now).first())
  assertEquals(listOf(usable),transitConnectionOptions(listOf(expired,usable),now))
 }
 @Test fun differentDeparturesOnSameLinesStillFillThreeCards(){
  val first=card(1,"6",now+60000,now+120000,now+600000)
  val later=card(2,"6",now+60000,now+180000,now+900000)
  val second=card(3,"1",now+60000,now+180000,now+1200000)
  assertEquals(listOf(first,later,second),transitConnectionOptions(listOf(later,second,first),now))
 }
 @Test fun sameProviderItineraryIsNotDuplicatedUnderAnotherRank(){
  val first=card(1,"6",now+60000,now+120000,now+600000)
  val duplicate=card(2,"6",now+60000,now+120000,now+600000)
  first.route.put("provider_itinerary_id","same-trip");duplicate.route.put("provider_itinerary_id","same-trip")
  assertEquals(1,transitConnectionOptions(listOf(first,duplicate),now).size)
 }
 @Test fun nearbyPlatformsRespectToggleAndExcludeVehicleTrackPositions(){
  val origin=GeoPoint(52.13,11.63)
  val track=MobilityPoi("track","stop",origin,"Track",obj("public_transport" to "stop_position"),"node")
  val platform=MobilityPoi("platform","stop",GeoPoint(52.131,11.63),"Platform",obj("public_transport" to "platform"),"node")
  assertEquals(listOf("platform"),nearestOriginPlatforms(listOf(track,platform),origin,true).map {it.id})
  assertTrue(nearestOriginPlatforms(listOf(platform),origin,false).isEmpty())
  assertTrue(nearestOriginPlatforms(listOf(platform),null,true).isEmpty())
  assertFalse(platform.tags.has("origin_nearby_metres"))
 }
}
