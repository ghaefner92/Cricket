package de.ovgu.imiq.cricket

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class DatabaseConcurrencyDeviceTest {
 @Test fun exportKeepsPassportStorageAndSearchesAtTheSameCommit(){
  val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
  val name="export-snapshot-${UUID.randomUUID()}.db";val ui=Store(context,name);val writerStore=Store(context,name)
  val failure=AtomicReference<Throwable?>();val entered=CountDownLatch(1);var writer:Thread?=null
  try {
   val backup=JSONObject(instrumentation.context.assets.open("d_day_backup.json").bufferedReader().use {it.readText()})
   ui.importBackup(backup);val id=backup.objects("simulations").single().getString("search_id")
   ui.put(SETTINGS,"0");ui.mutateRecord(id){it.put("export_marker","0")}
   writer=thread {
    try {for(i in 1..80){
     val db=writerStore.writableDatabase;db.beginTransaction()
     try {writerStore.put(SETTINGS,i.toString());val r=writerStore.record(id)!!;r.put("export_marker",i.toString());writerStore.saveRecord(r);db.setTransactionSuccessful()}finally{db.endTransaction()}
     entered.countDown();Thread.sleep(5)
    }}catch(e:Throwable){failure.set(e);entered.countDown()}
   }
   assertTrue(entered.await(5,TimeUnit.SECONDS))
   repeat(30) {
    val captured=ui.export()
    assertEquals(captured.getJSONObject("storage").getString(SETTINGS),captured.objects("simulations").single().getString("export_marker"))
    assertEquals(1,captured.getJSONObject("longitudinal").objects("experience_events").size)
   }
   writer.join(5000);assertFalse(writer.isAlive);failure.get()?.let {throw AssertionError("Concurrent export failed",it)}
   val original=ui.passport()!!;ui.archive(original)
   val changed=original.copyJson();changed.getJSONObject("response").getJSONObject("cognitive_passport").getJSONObject("profile").getJSONObject("needs").put("comfort",1)
   assertThrows(IllegalArgumentException::class.java){ui.archive(changed)}
   assertEquals(canonical(original),canonical(ui.json("$PASSPORT.archive.experience-update")))
  }finally{writer?.join(5000);writerStore.close();ui.close();context.deleteDatabase(name)}
 }
 @Test fun closingServiceStoreKeepsUiConnectionAliveAndPersistsData(){
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val name="connection-lifecycle-test-${UUID.randomUUID()}.db"
  val ui=Store(context,name);val service=Store(context,name)
  try {
   assertSame(ui.writableDatabase,service.writableDatabase)
   service.put("test-value","saved")
   service.close();service.close()
   assertEquals("saved",ui.get("test-value"))
   ui.put("test-value","updated")
   ui.close()
   Store(context,name).use {reopened->assertEquals("updated",reopened.get("test-value"))}
  }finally{service.close();ui.close();context.deleteDatabase(name)}
 }
 @Test fun pauseWaitsForSnapshotWhileSettingsRemainReadable(){
  val instrumentation=InstrumentationRegistry.getInstrumentation()
  val context=instrumentation.targetContext
  val name="concurrency-test-${UUID.randomUUID()}.db"
  val ui=Store(context,name);val service=Store(context,name)
  val entered=CountDownLatch(1);val done=CountDownLatch(1)
  val failure=AtomicReference<Throwable?>()
  var reader:Thread?=null;var writer:Thread?=null
  try {
   val record=JSONArray(instrumentation.context.assets.open("journey_cases.json").bufferedReader().use {it.readText()}).getJSONObject(0).getJSONObject("record").copyJson()
   val id="concurrency-${UUID.randomUUID()}"
   record.put("search_id",id).put("journeys",JSONArray())
   record.getJSONObject("request").put("search_id",id);record.getJSONObject("result").put("search_id",id)
   ui.saveRecord(record);ui.put("test-settings","readable")
   val repo=TripRepository(context,service);repo.start(id,0,true)
   reader=thread {
    val db=ui.readableDatabase
    db.beginTransactionNonExclusive()
    try {ui.record(id);entered.countDown();Thread.sleep(3500);db.setTransactionSuccessful()}
    catch(e:Throwable){failure.compareAndSet(null,e)}finally{db.endTransaction()}
   }
   assertTrue(entered.await(5,TimeUnit.SECONDS))
   writer=thread {try {repo.changeActive {TrackingEngine.pause(it,now())}}catch(e:Throwable){failure.compareAndSet(null,e)}finally{done.countDown()}}
   // WAL readers must still progress while the writer waits for the snapshot.
   assertEquals("readable",service.get("test-settings"))
   assertTrue(done.await(8,TimeUnit.SECONDS))
   reader.join(5000)
   failure.get()?.let {throw AssertionError("Concurrent pause failed",it)}
   assertEquals("paused",repo.active()!!.second.getString("status"))
   repo.changeActive {TrackingEngine.resume(it,now())}
   assertEquals("recording",ui.record(id)!!.objects("journeys").single().getString("status"))
   repo.changeActive {TrackingEngine.pause(it,now())}
   assertEquals("paused",ui.record(id)!!.objects("journeys").single().getString("status"))
   repo.contract.validate(repo.active()!!.first,repo.active()!!.second)
  }finally {
   reader?.join(6000);writer?.join(6000)
   ui.close();service.close();context.deleteDatabase(name)
  }
 }
}
