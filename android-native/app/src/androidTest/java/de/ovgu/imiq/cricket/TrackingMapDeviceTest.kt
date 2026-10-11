package de.ovgu.imiq.cricket

import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.io.FileOutputStream

class TrackingMapDeviceTest {
 private val instrument=InstrumentationRegistry.getInstrumentation()
 private val context=instrument.targetContext
 private fun allNodes():List<AccessibilityNodeInfo>{val found=mutableListOf<AccessibilityNodeInfo>();fun visit(node:AccessibilityNodeInfo?){if(node==null)return;found.add(node);for(i in 0 until node.childCount)visit(node.getChild(i))};visit(instrument.uiAutomation.rootInActiveWindow);return found}
 private fun await(condition:()->Boolean){val until=SystemClock.elapsedRealtime()+20000;while(!condition()&&SystemClock.elapsedRealtime()<until)Thread.sleep(150);assertTrue("Condition timed out; visible UI: "+allNodes().mapNotNull {it.text?.toString()}.joinToString(" | "),condition())}
 private fun nodes(text:String)=allNodes().filter {it.text?.toString()==text}
 private fun click(text:String){await {nodes(text).isNotEmpty()};var node:AccessibilityNodeInfo?=nodes(text).last();while(node!=null&&!node.isClickable)node=node.parent;assertTrue("Cannot click $text",node?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true)}
 private fun fixture():JSONObject {
  val r=JSONArray(instrument.context.assets.open("journey_cases.json").bufferedReader().use {it.readText()}).getJSONObject(0).getJSONObject("record").copyJson()
  val id="tracking-map-test-${UUID.randomUUID()}";r.put("search_id",id);r.getJSONObject("request").put("search_id",id);r.getJSONObject("result").put("search_id",id)
  r.getJSONObject("result").getJSONObject("routing").getJSONArray("routes").getJSONObject(0).put("legs",JSONArray().put(obj("mode" to "walk","geometry" to obj("type" to "LineString","coordinates" to JSONArray("[[11.6,52.1],[11.602,52.101],[11.604,52.102]]")))))
  return r
 }
 @Test fun largeRecordAndMapPauseResumeFinishCancel(){
  val store=Store(context);val repo=TripRepository(context,store)
  assertFalse("Do not interrupt live GPS",TrackingService.running)
  val previous=store.get(ACTIVE_TRIP)
  val previousLocale=store.get("imiq.experimental.language")
  assertTrue("Do not interrupt a recording journey",repo.active()?.second?.optString("status")!="recording")
  val padding="é🙂".repeat(750000);val first=fixture().put("test_padding",padding);val second=fixture()
  val ids=listOf(first.getString("search_id"),second.getString("search_id"));val key="imiq.experimental.tracking-map-test.${UUID.randomUUID()}"
  store.remove(ACTIVE_TRIP)
  val scenario=ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java))
  try {
   store.saveRecord(first);store.saveRecord(second);store.put(key,padding)
   assertEquals(padding,store.record(ids[0])!!.getString("test_padding"));assertEquals(padding,store.get(key));assertEquals(padding,store.storage().getString(key));assertTrue(store.history().any {it.getString("search_id")==ids[0]})
   assertEquals(1,mapRoutes(first.getJSONObject("result")).size)
   scenario.onActivity {it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);val m=ViewModelProvider(it)[CricketModel::class.java];m.setLanguage("en");TrackingService.action(it,TrackingService.START,ids[0],0);m.openTrip(ids[0])}
   await {repo.active()?.second?.optString("status")=="recording"}
   await {nodes("Pause").isNotEmpty()&&nodes("Finish").isNotEmpty()}
   assertTrue("Tracking view must hide Passport navigation",nodes("Passport").isEmpty())
   click("Pause");await {repo.active()?.second?.optString("status")=="paused"}
   click("Resume");await {repo.active()?.second?.optString("status")=="recording"}
   scenario.recreate();await {nodes("Pause").isNotEmpty()}
   Thread.sleep(10000)
   instrument.uiAutomation.takeScreenshot()?.let {image->FileOutputStream(context.filesDir.resolve("tracking-map-validation.png")).use {image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
   click("Finish");await {repo.active()==null&&!TrackingService.running}
   await {nodes("How did the journey go?").isNotEmpty()}
   val saved=store.record(ids[0])!!;val trip=saved.objects("journeys").single();assertEquals("awaiting_confirmation",trip.getString("status"));repo.contract.validate(saved,trip)
   assertEquals(padding,saved.getString("test_padding"))
   scenario.onActivity {val m=ViewModelProvider(it)[CricketModel::class.java];TrackingService.action(it,TrackingService.START,ids[1],0);m.openTrip(ids[1])}
   await {repo.active()?.second?.optString("status")=="recording"};await {nodes("Pause").isNotEmpty()}
   click("Cancel journey");await {nodes("Cancel this journey?").isNotEmpty()};click("Cancel journey")
   await {repo.active()==null&&!TrackingService.running}
   val cancelled=store.record(ids[1])!!;assertEquals("cancelled",cancelled.objects("journeys").single().getString("status"));repo.contract.validate(cancelled,cancelled.objects("journeys").single())
  }finally {
   if(repo.active()?.first?.optString("search_id")?.let {it in ids}==true)TrackingService.action(context,TrackingService.CANCEL)
   await {!TrackingService.running}
   scenario.close();ids.forEach {store.writableDatabase.execSQL("DELETE FROM searches WHERE id=?",arrayOf(it))};store.remove(key)
   if(previous!=null)store.put(ACTIVE_TRIP,previous)
   if(previousLocale!=null)store.put("imiq.experimental.language",previousLocale)else store.remove("imiq.experimental.language")
   TripUpdates.changed();store.close()
  }
 }
}
