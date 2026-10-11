package de.ovgu.imiq.cricket

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class SimulatedGoalSupportTest {
 @Test fun unchartedDestinationAccessRetainsItsUnknownTimeInsteadOfBeingCountedAsWalking() {
  val route=obj("mode_key" to "car","summary" to obj("destination_access_time_unknown" to true,"destination_access_offset_m" to 62.0),"legs" to JSONArray(listOf(obj("mode" to "walk","duration_seconds" to 340))))
  val card=RouteCard(route,null,null)
  assertEquals(62.0,destinationAccessGap(card)!!,0.0)
  assertEquals(340.0,carWalkingSeconds(card)!!,0.0)
  route.getJSONObject("summary").remove("destination_access_time_unknown")
  assertNull(destinationAccessGap(card))
 }
 @Test fun baselineFallbackIsSavedEvidenceAndDoesNotBecomeARouteCandidate() {
  val original=card("car")
  val fallback=original.copy(candidate=null,basicSimulation=original.candidate!!.getJSONObject("hotco"))
  assertNotNull(simulatedGoalContributions(fallback))
  assertNull(fallback.candidate)
 }
 @Test fun walkingDurationSumsAccessAndEgressOnly() {
  val route=obj("mode_key" to "car","legs" to JSONArray(listOf(obj("mode" to "walk","duration_seconds" to 60),obj("mode" to "car","duration_seconds" to 600),obj("mode" to "walk","duration_seconds" to 120))))
  assertEquals(180.0,carWalkingSeconds(RouteCard(route,null,null))!!,0.0)
  route.getJSONArray("legs").getJSONObject(0).remove("duration_seconds")
  assertNull(carWalkingSeconds(RouteCard(route,null,null)))
 }
 private fun card(mode:String="bike"):RouteCard {
  // Deliberately different from needKeys and modes order.
  val columns=listOf("action:pt",*needKeys.reversed().toTypedArray(),"action:bike","action:car","action:walk")
  val topology=JSONArray(columns.map {JSONArray(columns.map {0.0})})
  val row=topology.getJSONArray(columns.indexOf("action:bike"))
  mapOf("comfort" to -0.8,"cost" to 0.3,"privacy" to 0.7,"pro_env" to 0.6,"autonomy" to 0.4,"speed" to -0.9,"reliable" to -0.6,"physical" to 1.0).forEach {(n,w)->row.put(columns.indexOf(n),w)}
  val final=obj(*needKeys.map {it to if(it=="physical")0.001 else .5}.toTypedArray())
  return RouteCard(obj("rank" to 1,"mode_key" to mode),null,obj("hotco" to obj(
   "simulation_evidence" to obj("state_columns" to JSONArray(columns),"topology" to topology),
   "node_activations" to obj("needs" to obj("final" to final)))))
 }
 @Test fun topThreeRankSignedSimulatedInputsInsteadOfNeedsOrBeliefsAlone() {
  val card=card();val before=card.candidate.toString()
  val links=simulatedGoalContributions(card)!!
  assertEquals(listOf("privacy","pro_env","autonomy"),strongestGoalSupports(links).map {it.need})
  assertEquals(listOf("speed","comfort","reliable"),strongestGoalObstacles(links).map {it.need})
  assertEquals(.001,links.single {it.need=="physical"}.input,0.0)
  assertEquals(-.45,links.single {it.need=="speed"}.input,0.0)
  assertEquals(before,card.candidate.toString())
 }
 @Test fun selectedModeUsesItsOwnActionConnectionsAndCanonicalFootAlias() {
  assertTrue(strongestGoalSupports(simulatedGoalContributions(card("foot"))).isEmpty())
  assertTrue(strongestGoalObstacles(simulatedGoalContributions(card("car"))).isEmpty())
  assertNull(simulatedGoalContributions(card("unknown")))
 }
 @Test fun missingIncompleteAndInvalidSimulationNeverFallsBackToInitialRatings() {
  val missing=card().copy(candidate=null)
  assertNull(simulatedGoalContributions(missing))
  val partial=card();partial.candidate!!.getJSONObject("hotco").getJSONObject("node_activations").getJSONObject("needs").getJSONObject("final").remove("comfort")
  assertNull(simulatedGoalContributions(partial))
  val invalid=card();invalid.candidate!!.getJSONObject("hotco").getJSONObject("node_activations").getJSONObject("needs").getJSONObject("final").put("comfort",2.0)
  assertNull(simulatedGoalContributions(invalid))
 }
 @Test fun groupsRemainSmallerThanThreeWhenEvidenceHasFewerSignedLinks() {
  val links=listOf(SimulatedGoalContribution("comfort",-.2,.5,-.4),SimulatedGoalContribution("speed",0.0,0.0,1.0),SimulatedGoalContribution("privacy",.3,.5,.6))
  assertEquals(listOf("privacy"),strongestGoalSupports(links).map {it.need})
  assertEquals(listOf("comfort"),strongestGoalObstacles(links).map {it.need})
  assertTrue(strongestGoalSupports(null).isEmpty())
 }
}
