package de.ovgu.imiq.cricket

import org.junit.Assert.*
import org.junit.Test

class RouteAvatarAnchorTest {
 private val route=MapRoute("1-foot","walk",listOf(MapSegment(listOf(GeoPoint(52.13,11.62),GeoPoint(52.13,11.64)),"walk",false)),false)
 @Test fun remoteRealPositionCannotMoveAvatarOffChosenRoute() {
  assertNull(routeAvatarTarget(route,GeoPoint(52.15,11.66),true))
 }
 @Test fun nearbyGpsIsProjectedOntoRouteInsteadOfReplacingIt() {
  val anchor=routeAvatarTarget(route,GeoPoint(52.1302,11.63),true)!!
  assertEquals(52.13,anchor.lat,1e-8)
  assertEquals(11.63,anchor.lon,1e-8)
 }
 @Test fun pauseAndStopDoNotSupplyANewAvatarTarget() {
  assertNull(routeAvatarTarget(route,GeoPoint(52.13,11.63),false))
  assertNull(routeAvatarTarget(route,null,true))
  assertNull(routeAvatarTarget(null,GeoPoint(52.13,11.63),true))
 }
 @Test fun SeparateLegsDoNotInventAConnectingPath() {
  val split=MapRoute("2-pt","pt",listOf(MapSegment(listOf(GeoPoint(52.13,11.62)),"walk",true),MapSegment(listOf(GeoPoint(52.13,11.66)),"pt",true)),false)
  assertNull(routeAvatarTarget(split,GeoPoint(52.13,11.64),true))
 }
}
