package de.ovgu.imiq.cricket

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NeedImportanceRatingsDeviceTest {
 @Test fun ordinalRatingsReachTheLivePassportWithoutSumNormalization()=runBlocking {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val name="importance-api-test-${java.util.UUID.randomUUID()}.db"
  val store=Store(context,name)
  try {
   val api=Api(store)
   val ratings=obj(*goalKeys.mapIndexed {i,key->key to (i%7+1)}.toTypedArray())
   val start=api.call("/api/dyconet/adaptive-passport/start",obj("schema_version" to "adaptive_cognitive_passport_onboarding_1.0",
    "agent_id" to "synthetic-device-importance","responses" to obj("needs" to needImportanceRatings(ratings),"valences" to obj(*modes.map {it to 0}.toTypedArray()))))
   val completed=api.call("/api/dyconet/adaptive-passport/complete",obj("schema_version" to start.getString("schema_version"),"start" to start,
    "answers" to obj(*start.objects("questions").map {it.getString("cell_id") to 4}.toTypedArray())))
   val response=api.call("/api/dyconet/adaptive-passport/bootstrap",obj("schema_version" to "adaptive_hotco_bootstrap_1.0",
    "completed_passport" to completed,"availability" to obj(*modes.map {it to true}.toTypedArray())))
   val passport=response.getJSONObject("cognitive_passport")
   val needs=passport.getJSONObject("profile").getJSONObject("needs")
   val routing=passport.getJSONObject("routing_parameters").getJSONObject("need_weights")
   for(i in goalKeys.indices){
    val expected=(ratings.getInt(goalKeys[i])-1)/6.0
    // Passport serialization retains ten decimal places for recurring sixths.
    assertEquals(expected,needs.getDouble(needKeys[i]),1e-9)
    assertEquals(expected,routing.getDouble(needKeys[i]),1e-9)
   }
   assertTrue(needKeys.sumOf {needs.getDouble(it)}>1.0)
   assertTrue(passport.getJSONObject("observed_measurements").has("needs_raw_1_to_7"))
   assertFalse(passport.getJSONObject("observed_measurements").has("needs_allocation"))
  }finally{store.close();context.deleteDatabase(name)}
 }
 @Test fun previousBudgetsAreNotInventedOrdinalResponses(){
  val legacy=obj("budget" to 10,"points" to obj(*goalKeys.map {it to if(it=="env")10 else 0}.toTypedArray()))
  val fresh=importancePlan(legacy,"2026-10-05")
  assertEquals(NEED_IMPORTANCE_SCALE,fresh.getString("scale"))
  assertTrue(goalKeys.all {fresh.getJSONObject("points").getInt(it)==0})
  assertEquals(10,legacy.getJSONObject("points").getInt("env"))
  assertThrows(IllegalArgumentException::class.java){needImportanceRatings(fresh.getJSONObject("points"))}
  for(rating in 1..7){
   val values=obj(*goalKeys.map {it to rating}.toTypedArray())
   val canonical=needImportanceRatings(values)
   assertTrue(needKeys.all {canonical.getInt(it)==rating})
   val plan=fresh.copyJson().put("points",values)
   assertSame(plan,importancePlan(plan,"2026-10-05"))
  }
 }
}
