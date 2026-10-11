package de.ovgu.imiq.cricket

import org.junit.Assert.*
import org.junit.Test
import java.io.File


class DailyGoalSphereTest {
 @Test fun everyAvatarHasItsOwnTransparentThreeStatePortraitAtlas() {
  for(kind in listOf("naturalist","explorer","robot","creature")){
   assertEquals(kind,petPortraitKind(kind))
   val bytes=File("src/main/assets/art/jrpg/pet-$kind-face-3d-v1.png").readBytes()
   val header=java.nio.ByteBuffer.wrap(bytes)
   assertEquals(0x89504e47.toInt(),header.getInt(0))
   val width=header.getInt(16);val height=header.getInt(20)
   assertEquals(0,width%3);assertEquals(0,height%3)
   assertTrue(width/3>=384&&height/3>=384)
   assertEquals(6,bytes[25].toInt()) // RGBA PNG
  }
  assertEquals("robot",petPortraitKind("unknown"))
 }
 @Test fun tiedImportanceSharesRankWithoutRankingMissingResponses() {
  val ranks=goalPriorityRanks(mapOf("comfort" to 7,"time" to 7,"cost" to 5,"flex" to 5,"unknown" to 0,"invalid" to 9))
  assertEquals(GoalPriorityRank(1,true),ranks["comfort"])
  assertEquals(ranks["comfort"],ranks["time"])
  assertEquals(GoalPriorityRank(3,true),ranks["cost"])
  assertFalse(ranks.containsKey("unknown"));assertFalse(ranks.containsKey("invalid"))
  assertEquals(GoalPriorityRank(1,false),goalPriorityRanks(mapOf("time" to 1))["time"])
 }
 @Test fun editingOneGoalPreservesOtherRatingsAndTheOriginal() {
  val base=importancePlan(null,"2026-10-11").put("localDate","2026-10-11")
  goalKeys.forEach {base.getJSONObject("points").put(it,3)}
  val original=canonical(base)
  val edited=editedDailyGoal(base,"comfort_physical",7)
  assertEquals(original,canonical(base))
  goalKeys.forEach {assertEquals(if(it=="comfort_physical")7 else 3,edited.getJSONObject("points").getInt(it))}
  assertEquals("2026-10-11",edited.getString("localDate"))
 }
 @Test fun unratedAndLegacyAllocationCannotBecomeImplicitRatings() {
  assertNull(goalSalience(0));assertNull(goalSalience(8))
  assertEquals(0f,goalSalience(1)!!,0f);assertEquals(1f,goalSalience(7)!!,0f)
  val base=importancePlan(null,"2026-10-11")
  assertThrows(IllegalArgumentException::class.java){needImportanceRatings(editedDailyGoal(base,"time",4).getJSONObject("points"))}
  assertEquals(0,base.getJSONObject("points").getInt("time"))
  assertThrows(IllegalArgumentException::class.java){editedDailyGoal(base.put("scale","allocation"),"time",4)}
 }
 @Test fun rotationExposesEveryGoalAndProjectionRemainsBounded() {
  for(i in goalKeys.indices){
   val visible=(0..24).map {goalSpherePoint(i,goalKeys.size,it*.26f,.12f)}
   assertTrue(visible.any {it.depth>0f});assertTrue(visible.any {it.depth<0f})
   visible.forEach {assertTrue(it.x.isFinite()&&it.y.isFinite());assertTrue(kotlin.math.abs(it.x)<1.5f&&kotlin.math.abs(it.y)<1.5f)}
  }
 }
}
