package de.ovgu.imiq.cricket

import org.junit.Assert.*
import org.junit.Test

class LocationStartAndPlaybackTest {
 @Test fun permissionIsRequiredEvenWhenTheSystemSwitchIsOn(){assertEquals(LocationStartStep.Permission,locationStartStep(false,true));assertEquals(LocationStartStep.Permission,locationStartStep(false,false))}
 @Test fun permissionAloneMustStillAskAndroidToEnableLocation(){assertEquals(LocationStartStep.Services,locationStartStep(true,false));assertEquals(LocationStartStep.Ready,locationStartStep(true,true))}
 private fun route(vararg segments:MapSegment)=MapRoute("r","pt",segments.toList(),false)
 private fun segment(a:Double,b:Double,mode:String="walk",drawable:Boolean=true)=MapSegment(listOf(GeoPoint(52.0,a),GeoPoint(52.0,b)),mode,false,drawable)
 @Test fun playbackInterpolatesThePlannedPathAndClampsEndpoints(){val r=route(segment(11.0,12.0));assertEquals(11.0,routePlaybackPosition(r,-1.0)!!.first.lon,1e-8);assertEquals(11.5,routePlaybackPosition(r,.5)!!.first.lon,1e-8);assertEquals(12.0,routePlaybackPosition(r,2.0)!!.first.lon,1e-8)}
 @Test fun disjointLegsNeverInventAConnectingGeometry(){val r=route(segment(11.0,12.0),segment(14.0,15.0,"pt"));val p=routePlaybackPosition(r,.6)!!;assertEquals(14.2,p.first.lon,1e-8);assertEquals("pt",p.second)}
 @Test fun unavailableGeometryAndInvalidProgressProduceNoPosition(){assertNull(routePlaybackPosition(null,.5));assertNull(routePlaybackPosition(route(segment(11.0,12.0,drawable=false)),.5));assertNull(routePlaybackPosition(route(segment(11.0,12.0)),Double.NaN))}
 @Test fun zeroLengthGeometryRemainsAtItsKnownPoint(){val r=route(segment(11.0,11.0));assertEquals(GeoPoint(52.0,11.0),routePlaybackPosition(r,.5)!!.first)}
}
