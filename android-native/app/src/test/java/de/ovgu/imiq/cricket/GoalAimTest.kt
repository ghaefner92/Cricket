package de.ovgu.imiq.cricket

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class GoalAimTest {
 @Test fun allElevenDirectionsSelectTheirGoal(){
  for(i in 0 until 11){val angle=-PI/2+i*2*PI/11
   assertEquals(i,aimedGoalIndex((cos(angle)*142).toFloat(),(sin(angle)*155).toFloat(),142f,155f,11))
  }
 }
 @Test fun centreOutsideAndInvalidCoordinatesCancel(){
  for(p in listOf(0f to 0f,20f to 30f,500f to 0f,Float.NaN to 0f))
   assertNull(aimedGoalIndex(p.first,p.second,142f,155f,11))
  assertNull(aimedGoalIndex(0f,-155f,0f,155f,11))
 }
 @Test fun upperSectorWrapsWithoutChangingGoal(){
  assertEquals(0,aimedGoalIndex(-1f,-155f,142f,155f,11))
  assertEquals(0,aimedGoalIndex(1f,-155f,142f,155f,11))
  assertEquals("Eco-mode",shortGoalName("env"))
  assertEquals("Physical Activity",shortGoalName("health_activity"))
  assertEquals("Öko-Modus",shortGoalName("env",true))
  assertEquals("Körperliche Aktivität",shortGoalName("health_activity",true))
  assertEquals("Comfort",shortGoalName("comfort_physical"))
  assertEquals("Komfort",shortGoalName("comfort_physical",true))
 }
}
