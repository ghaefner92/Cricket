package de.ovgu.imiq.cricket

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeeklyNeedAllocationDeviceTest {
 @Test fun exactAllocationReachesTheLivePassport()=runBlocking {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val name="allocation-api-test-${java.util.UUID.randomUUID()}.db"
  val store=Store(context,name)
  try {
   val api=Api(store)
   val points=obj(*goalKeys.map {it to when(it){"env"->1;"health_activity"->2;"cost"->3;"time"->4;else->0}}.toTypedArray())
   val allocation=weeklyNeedAllocation(points)
   val start=api.call("/api/dyconet/adaptive-passport/start",obj("schema_version" to "adaptive_cognitive_passport_onboarding_1.0",
    "agent_id" to "synthetic-device-allocation","responses" to obj("needs_allocation" to allocation,"valences" to obj(*modes.map {it to 0}.toTypedArray()))))
   val answers=obj(*start.objects("questions").map {it.getString("cell_id") to 4}.toTypedArray())
   val completed=api.call("/api/dyconet/adaptive-passport/complete",obj("schema_version" to start.getString("schema_version"),"start" to start,"answers" to answers))
   val response=api.call("/api/dyconet/adaptive-passport/bootstrap",obj("schema_version" to "adaptive_hotco_bootstrap_1.0",
    "completed_passport" to completed,"availability" to obj(*modes.map {it to true}.toTypedArray())))
   val passport=response.getJSONObject("cognitive_passport")
   val needs=passport.getJSONObject("profile").getJSONObject("needs")
   val routing=passport.getJSONObject("routing_parameters").getJSONObject("need_weights")
   for(index in goalKeys.indices){
    val expected=points.getInt(goalKeys[index])/10.0
    assertEquals(expected,needs.getDouble(needKeys[index]),1e-12)
    assertEquals(expected,routing.getDouble(needKeys[index]),1e-12)
   }
   assertEquals(1.0,needKeys.sumOf {needs.getDouble(it)},1e-12)
   assertTrue(passport.getJSONObject("observed_measurements").has("needs_allocation"))
   assertFalse(passport.getJSONObject("observed_measurements").has("needs_raw_1_to_7"))
  }finally{store.close();context.deleteDatabase(name)}
 }
 @Test fun everyPointIsPreservedOnEveryCanonicalNeed(){
  for(key in goalKeys)for(value in 0..10){
   val other=goalKeys.first {it!=key}
   val points=obj(*goalKeys.map {it to when(it){key->value;other->10-value;else->0}}.toTypedArray())
   val result=weeklyNeedAllocation(points)
   assertEquals("weekly_need_allocation_1.0",result.getString("schema_version"))
   assertEquals(10,result.getInt("budget"))
   val allocation=result.getJSONObject("points")
   assertEquals(value,allocation.getInt(needKeys[goalKeys.indexOf(key)]))
   assertEquals(10,needKeys.sumOf {allocation.getInt(it)})
   assertEquals(11,allocation.length())
  }
 }
 @Test fun incompleteOrInvalidBudgetsAreRejected(){
  for(sum in listOf(0,9,11)){
   val points=obj(*goalKeys.map {it to if(it=="env")sum else 0}.toTypedArray())
   assertThrows(IllegalArgumentException::class.java){weeklyNeedAllocation(points)}
  }
 }
}
