package de.ovgu.imiq.cricket

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class JourneyContextDeviceTest {
 private val instrumentation=InstrumentationRegistry.getInstrumentation()
 private val context=instrumentation.targetContext
 private fun cases()=JSONArray(instrumentation.context.assets.open("context_capture_cases.json").bufferedReader().use {it.readText()})
 private fun contract()=JourneyContract(JSONObject(context.assets.open("journey-v1.schema.json").bufferedReader().use {it.readText()}))
 @Test fun actualBackendSamplingRemainsReadOnly()=runBlocking {
  val name="context-network-test-${UUID.randomUUID()}.db"
  try {
   Store(context,name).use {store->
    val result=Api(store).call("/api/dyconet/d-day/context/sample",obj("lat" to 52.13,"lon" to 11.62),25000)
    assertEquals("d-day-context-sample-v1",result.getString("schema_version"))
    assertFalse(result.getBoolean("stored_on_server"));assertFalse(result.getBoolean("personal_exposure_measured"))
    assertTrue(result.getJSONObject("audit").getInt("weather_used") in 0..7)
    assertFalse(result.getJSONObject("audit").getJSONObject("values").has("uvIndex"))
    store.put("test-ambient-response",result)
    // Android serializes integral doubles as JSON integers; compare the saved
    // JSON representation rather than Java Number subtype identity.
    assertTrue("Ambient snapshot changed after SQLite serialization",canonical(result.copyJson())==canonical(store.json("test-ambient-response")))
   }
  }finally{context.deleteDatabase(name)}
 }
 @Test fun sharedContextContractOnAndroid(){
  cases().objects().forEach {c->assertEquals(c.getString("name"),c.getBoolean("valid"),runCatching {contract().validate(c.getJSONObject("record"),c.getJSONObject("journey"))}.isSuccess)}
 }
 @Test fun capturedAmbientSamplesSurviveSQLiteRoundTrip(){
  val name="context-capture-test-${UUID.randomUUID()}.db"
  try {
   Store(context,name).use {store->
    val c=cases().getJSONObject(0);val record=c.getJSONObject("record").copyJson();val trip=c.getJSONObject("journey").copyJson()
    record.put("journeys",JSONArray(listOf(trip)));store.saveRecord(record)
    val loaded=store.record(record.getString("search_id"))!!
    val restored=loaded.objects("journeys").single()
    assertEquals(canonical(trip),canonical(restored));contract().validate(loaded,restored)
    assertFalse(restored.getJSONObject("context_capture").objects("samples").single().getJSONObject("result").getBoolean("personal_exposure_measured"))
   }
  }finally{context.deleteDatabase(name)}
 }
}
