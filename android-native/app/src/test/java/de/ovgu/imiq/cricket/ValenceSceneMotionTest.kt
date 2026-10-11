package de.ovgu.imiq.cricket

import org.junit.Assert.*
import org.junit.Test

class ValenceSceneMotionTest {
 @Test fun allRoutesStayInsideTheirSceneAcrossSeveralLoops() {
  for(mode in listOf("walk","bike","pt","car"))for(step in 0..1440) {
   val p=valenceWorldPose(mode,step/10f,true)
   assertTrue(p.x in 0f..1f && p.ground in 0f..1f)
   assertTrue(p.depth in .4f..1f && p.visibility in 0f..1f)
  }
 }
 @Test fun towerRouteClimbsAndChangesDepthWithHiddenRearPassages() {
  val start=valenceWorldPose("walk",0f,true)
  val upper=valenceWorldPose("walk",16f,true)
  assertTrue(upper.ground<start.ground)
  assertTrue(upper.depth<start.depth)
  assertEquals(0f,valenceWorldPose("walk",4.6f,true).visibility,0f)
  assertEquals(1f,start.visibility,0f)
  var hidden=0;var longest=0
  for(step in 0..10000) {
   hidden=if(valenceWorldPose("walk",step/100f,true).visibility<.2f)hidden+1 else 0
   longest=maxOf(longest,hidden)
  }
  assertTrue("Rear turns must reappear within one second",longest<100)
 }
 @Test fun reducedMotionHasAVisibleStablePoseRegardlessOfClock() {
  for(mode in listOf("walk","bike","pt","car")) {
   val p=valenceWorldPose(mode,0f,false)
   assertEquals(p,valenceWorldPose(mode,123f,false))
   assertEquals(1f,p.visibility,0f)
  }
 }
}
