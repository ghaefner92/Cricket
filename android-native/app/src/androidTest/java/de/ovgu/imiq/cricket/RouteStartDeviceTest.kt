package de.ovgu.imiq.cricket

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class RouteStartDeviceTest {
 private fun historyDigests(store:Store):Map<String,String> {
  val db=store.readableDatabase
  val ids=db.rawQuery("SELECT id FROM searches",null).use {c->buildList {while(c.moveToNext())add(c.getString(0))}}
  return ids.associateWith {id->
   val digest=java.security.MessageDigest.getInstance("SHA-256")
   val length=db.rawQuery("SELECT length(CAST(value AS BLOB)) FROM searches WHERE id=?",arrayOf(id)).use {it.moveToFirst();it.getLong(0)}
   var offset=0L
   while(offset<length){val bytes=db.rawQuery("SELECT substr(CAST(value AS BLOB),?,262144) FROM searches WHERE id=?",arrayOf((offset+1).toString(),id)).use {it.moveToFirst();it.getBlob(0)};digest.update(bytes);offset+=bytes.size}
   digest.digest().joinToString(""){"%02x".format(it)}
  }
 }
 @Test fun playWithLargeRecordKeepsMainThreadResponsiveAndSkipsOptions(){
  val instrumentation=InstrumentationRegistry.getInstrumentation()
  val context=instrumentation.targetContext
  val store=Store(context);val repo=TripRepository(context,store)
  assertNull("Do not interrupt a real journey",repo.active())
  val before=historyDigests(store)
  val record=JSONArray(instrumentation.context.assets.open("journey_cases.json").bufferedReader().use {it.readText()}).getJSONObject(0).getJSONObject("record").copyJson()
  val id="route-start-test-${UUID.randomUUID()}"
  record.put("search_id",id).put("created_at",now()).put("journeys",JSONArray())
  record.getJSONObject("request").put("search_id",id)
  record.getJSONObject("result").put("search_id",id).put("test_padding","x".repeat(5_000_000))
  store.saveRecord(record)
  val card=cards(record.getJSONObject("result")).first {it.route.optBoolean("available")&&!it.expired()}
  val scenario=ActivityScenario.launch<MainActivity>(android.content.Intent(context,MainActivity::class.java))
  var maxMainLatency=0L;var dispatchMs=0L;var sampled=0;var optionFlash=false
  var start=SystemClock.elapsedRealtime()
  try {
   scenario.onActivity {activity->
    val model=ViewModelProvider(activity)[CricketModel::class.java]
    model.ready=true;model.startupVisible=false;model.historyId=id;model.result=record.getJSONObject("result")
    model.tripSearchId=null;model.tripId=null;model.tab="journey";model.selectedId=card.id;model.routeView="alternatives"
   }
   // Measure Play on an already displayed route, separately from cold startup.
   Thread.sleep(2500)
   start=SystemClock.elapsedRealtime()
   scenario.onActivity {activity->
    val model=ViewModelProvider(activity)[CricketModel::class.java]
    val dispatch=SystemClock.elapsedRealtime()
    model.choose(card){search,index->model.openingTracking(search);TrackingService.action(activity,TrackingService.START,search,index)}
    dispatchMs=SystemClock.elapsedRealtime()-dispatch
   }
   val handler=Handler(Looper.getMainLooper())
   val deadline=SystemClock.elapsedRealtime()+15000
   while(SystemClock.elapsedRealtime()<deadline){
    val posted=SystemClock.elapsedRealtime();val latch=CountDownLatch(1)
    handler.post {maxMainLatency=maxOf(maxMainLatency,SystemClock.elapsedRealtime()-posted);latch.countDown()}
    assertTrue("Main thread stalled for over 2 seconds",latch.await(2,TimeUnit.SECONDS))
    scenario.onActivity {activity->
     val model=ViewModelProvider(activity)[CricketModel::class.java]
     assertNotEquals("Play must not navigate through preview","preview",model.routeView)
    }
    val root=instrumentation.uiAutomation.rootInActiveWindow
    fun inspect(node:android.view.accessibility.AccessibilityNodeInfo){
     if(node.packageName?.toString()==context.packageName){sampled++;if(node.text?.toString() in listOf("Your real journey","Choose a route first. You can then start recording or confirm a trip manually."))optionFlash=true}
     for(i in 0 until node.childCount)node.getChild(i)?.let(::inspect)
    }
    root?.let(::inspect)
    if(repo.active()?.first?.optString("search_id")==id)break
    Thread.sleep(30)
   }
   val active=repo.active()?:error("Journey did not start")
   assertEquals(id,active.first.getString("search_id"));repo.contract.validate(active.first,active.second)
   assertFalse("Unexpected journey options screen",optionFlash)
   assertTrue("Play dispatch blocked the main thread",dispatchMs<250)
   android.util.Log.i("CricketRouteStart","5MB record: dispatch=${dispatchMs}ms mainMax=${maxMainLatency}ms start=${SystemClock.elapsedRealtime()-start}ms accessibilityNodes=$sampled optionFlash=$optionFlash")
   TrackingService.action(context,TrackingService.CANCEL)
   val finishDeadline=SystemClock.elapsedRealtime()+15000
   while((TrackingService.running||repo.active()!=null)&&SystemClock.elapsedRealtime()<finishDeadline)Thread.sleep(100)
   assertNull(repo.active());assertFalse(TrackingService.running)
   assertEquals("cancelled",store.record(id)!!.objects("journeys").single().getString("status"))
  }finally{
   if(repo.active()?.first?.optString("search_id")==id)TrackingService.action(context,TrackingService.CANCEL)
   val cleanupDeadline=SystemClock.elapsedRealtime()+15000
   while(TrackingService.running&&SystemClock.elapsedRealtime()<cleanupDeadline)Thread.sleep(100)
   scenario.close()
   check(!TrackingService.running){"Test journey still active; retain its record"}
   store.writableDatabase.execSQL("DELETE FROM searches WHERE id=?",arrayOf(id));TripUpdates.changed()
   assertEquals("User history changed",before,historyDigests(store))
   store.close()
  }
 }
}
