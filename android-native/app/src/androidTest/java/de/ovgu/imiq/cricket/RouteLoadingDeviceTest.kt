package de.ovgu.imiq.cricket

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ServerSocket
import java.util.UUID
import java.util.zip.GZIPOutputStream
import java.io.ByteArrayOutputStream
import kotlin.concurrent.thread

/** Isolated databases and local HTTP: never edits the user's journeys. */
@RunWith(AndroidJUnit4::class)
class RouteLoadingDeviceTest {
 private val instrumentation=InstrumentationRegistry.getInstrumentation()
 private val context=instrumentation.targetContext
 private fun isolated(block:(Store)->Unit){
  val name="route-loading-test-${UUID.randomUUID()}.db"
  val store=Store(context,name)
  try {block(store)}finally{store.close();context.deleteDatabase(name)}
 }
 private fun fixture(id:String,created:String):JSONObject {
  val record=JSONArray(instrumentation.context.assets.open("journey_cases.json").bufferedReader().use {it.readText()}).getJSONObject(0).getJSONObject("record").copyJson()
  record.put("search_id",id).put("created_at",created)
  record.getJSONObject("request").put("search_id",id)
  record.getJSONObject("result").put("search_id",id).put("test_padding","x".repeat(3_000_000))
  record.put("journeys",JSONArray())
  return record
 }
 @Test fun pendingLookupMatchesHistoryWithoutReadingUnrelatedRecords()=isolated {store->
  repeat(5){i->store.saveRecord(fixture("test-$i","2026-10-0${i+1}T12:00:00Z"))}
  store.mutateRecord("test-2"){it.put("journeys",JSONArray().put(obj("journey_id" to "pending-2","status" to "awaiting_confirmation")))}
  // A matching token elsewhere is not a pending journey.
  store.mutateRecord("test-4"){it.put("extra_status","awaiting_confirmation")}
  val start=SystemClock.elapsedRealtime()
  val expected=store.history().firstNotNullOfOrNull {r->r.objects("journeys").lastOrNull {it.optString("status")=="awaiting_confirmation"}?.let {r.getString("search_id") to it.getString("journey_id")}}
  val eagerMs=SystemClock.elapsedRealtime()-start
  val lookupStart=SystemClock.elapsedRealtime()
  val actual=store.firstPendingJourney()!!
  val lookupMs=SystemClock.elapsedRealtime()-lookupStart
  assertEquals(expected,actual.first.getString("search_id") to actual.second.getString("journey_id"))
  store.mutateRecord("test-2"){it.getJSONArray("journeys").getJSONObject(0).put("status","confirmed")}
  assertNull(store.firstPendingJourney())
  val second=Store(context,store.databaseName)
  try {second.mutateRecord("test-1"){it.put("journeys",JSONArray().put(obj("journey_id" to "new","status" to "awaiting_confirmation")))}}finally{second.close()}
  assertEquals("test-1",store.firstPendingJourney()!!.first.getString("search_id"))
  android.util.Log.i("CricketRouteLoading","15MB history: eager=${eagerMs}ms pending=${lookupMs}ms")
 }
 @Test fun androidHttpClientDecodesLargeNegotiatedGzip()=isolated {store->
  val body=obj("schema" to "transport-test","evidence" to "clima · Straßenbahn ".repeat(160_000)).toString().toByteArray(Charsets.UTF_8)
  val compressed=ByteArrayOutputStream().also {out->GZIPOutputStream(out).use {it.write(body)}}.toByteArray()
  ServerSocket(0).use {server->
   store.put("cricket.connection.v1","http://127.0.0.1:${server.localPort}")
   var failure:Throwable?=null
   val worker=thread {
    try {server.accept().use {socket->
     socket.soTimeout=10000
     val reader=socket.getInputStream().bufferedReader()
     val headers=buildList {while(true){val line=reader.readLine()?:break;if(line.isEmpty())break;add(line)}}
     check(headers.any {it.startsWith("Accept-Encoding:",ignoreCase=true)&&it.contains("gzip",ignoreCase=true)})
     socket.getOutputStream().use {out->out.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Encoding: gzip\r\nContent-Length: ${compressed.size}\r\nConnection: close\r\n\r\n".toByteArray());out.write(compressed)}
    }}catch(e:Throwable){failure=e}
   }
   try {val response=runBlocking {Api(store).call("/routes")};assertEquals(JSONObject(body.toString(Charsets.UTF_8)).getString("evidence"),response.getString("evidence"))}
   finally {server.close();worker.join(10000)}
   failure?.let {throw AssertionError("HTTP fixture failed",it)}
   android.util.Log.i("CricketRouteLoading","Gzip decoded exactly: ${body.size} bytes -> ${compressed.size} bytes")
  }
 }
}
