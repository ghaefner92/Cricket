package de.ovgu.imiq.cricket

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TrackingDeviceTest {
 private val instrumentation=InstrumentationRegistry.getInstrumentation()
 private val context=instrumentation.targetContext
 private fun fixture():JSONObject {
  val record=JSONArray(instrumentation.context.assets.open("journey_cases.json").bufferedReader().use {it.readText()}).getJSONObject(0).getJSONObject("record").copyJson()
  val id="phase1-device-${UUID.randomUUID()}";record.put("search_id",id);record.getJSONObject("request").put("search_id",id);record.getJSONObject("result").put("search_id",id)
  return record
 }
 private fun await(condition:()->Boolean,timeout:Long=10000){val until=SystemClock.elapsedRealtime()+timeout;while(!condition()&&SystemClock.elapsedRealtime()<until)Thread.sleep(100);assertTrue("Timed out",condition())}
 @Test fun realForegroundServiceLifecycleAndPersistence(){
  val store=Store(context);val repository=TripRepository(context,store);assertNull("A real journey is active; do not interrupt it",repository.active())
  val record=fixture();val id=record.getString("search_id");store.saveRecord(record)
  val scenario=ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java))
  try {
   scenario.onActivity {it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
   scenario.onActivity {TrackingService.action(it,TrackingService.START,id,0)}
   await({TrackingService.running&&repository.active()?.second?.optString("status")=="recording"})
   val tripId=repository.active()!!.second.getString("journey_id")
   scenario.onActivity {TrackingService.action(it,TrackingService.START,id,0)}
   instrumentation.waitForIdleSync()
   assertEquals(tripId,repository.active()!!.second.getString("journey_id"))
   val active=repository.active()!!;repository.contract.validate(active.first,active.second)
   assertTrue(runCatching {repository.start(id,0,true)}.isFailure)
   // Sensor delivery varies indoors. Record actual fixes if available, without injecting fake GPS.
   scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
   Thread.sleep(15000)
   assertTrue(TrackingService.running)
   val progress=repository.active()!!;repository.contract.validate(progress.first,progress.second)
   assertEquals("recording",progress.second.getString("status"))
   android.util.Log.i("CricketTrackingValidation","Real valid fixes: ${progress.second.getJSONObject("tracking").getJSONArray("points").length()}")
   TrackingService.action(context,TrackingService.PAUSE)
   await({repository.active()?.second?.optString("status")=="paused"})
   // Resume from the actual notification while the activity remains stopped.
   val notifications=context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
   await({notifications.activeNotifications.firstOrNull {it.id==71}?.notification?.actions?.firstOrNull()?.title?.toString() in listOf("Resume","Fortsetzen")})
   notifications.activeNotifications.single {it.id==71}.notification.actions[0].actionIntent.send()
   await({repository.active()?.second?.optString("status")=="recording"})
   TrackingService.action(context,TrackingService.RESUME)
   Thread.sleep(250)
   assertEquals("recording",repository.active()!!.second.getString("status"))
   notifications.activeNotifications.single {it.id==71}.notification.actions[1].actionIntent.send()
   await({!TrackingService.running&&repository.active()==null})
   val reopened=Store(context);val saved=reopened.record(id)!!;val trip=saved.objects("journeys").single()
   assertEquals("awaiting_confirmation",trip.getString("status"));repository.contract.validate(saved,trip)
   assertTrue(trip.getJSONObject("tracking").getDouble("elapsed_seconds")>=15)
   repository.change(id,tripId){TrackingEngine.confirm(it,"as_planned",listOf("walk"),obj("comfort" to 6),"",null,now())}
   assertTrue(runCatching {repository.change(id,tripId){TrackingEngine.confirm(it,"as_planned",listOf("walk"),JSONObject(),"",null,now())}}.isFailure)
   assertEquals("confirmed",reopened.record(id)!!.objects("journeys").single().getString("status"));reopened.close()
  }finally{
   runCatching {if(repository.active()?.first?.optString("search_id")==id)TrackingService.action(context,TrackingService.CANCEL)}
   await({!TrackingService.running})
   scenario.close();store.writableDatabase.execSQL("DELETE FROM searches WHERE id=?",arrayOf(id));TripUpdates.changed();store.close()
  }
 }
 @Test fun manualExperienceBackupAndTransactionalRejection(){
  val store=Store(context);val repo=TripRepository(context,store);assertNull(repo.active());val record=fixture();val id=record.getString("search_id");store.saveRecord(record)
  try {
   val trip=repo.start(id,0,false);repo.change(id,trip.getString("journey_id")){TrackingEngine.confirm(it,"not_taken",emptyList(),JSONObject(),"",null,now())}
   val before=store.export().toString()
   store.importBackup(JSONObject(before));assertEquals("confirmed",store.record(id)!!.objects("journeys").single().getString("status"))
   val invalid=JSONObject(before);val bad=invalid.objects("simulations").single {it.getString("search_id")==id};bad.objects("journeys").single().getJSONObject("experience").put("ratings",obj("comfort" to 7))
   assertTrue(runCatching {store.importBackup(invalid)}.isFailure)
   assertEquals(canonical(JSONObject(before).getJSONObject("storage")),canonical(store.storage()))
   assertTrue(store.record(id)!!.objects("journeys").single().getJSONObject("experience").getJSONObject("ratings").length()==0)
  }finally{store.writableDatabase.execSQL("DELETE FROM searches WHERE id=?",arrayOf(id));TripUpdates.changed();store.close()}
 }
}
