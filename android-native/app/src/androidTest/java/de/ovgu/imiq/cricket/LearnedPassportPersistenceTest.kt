package de.ovgu.imiq.cricket

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** Isolated DB only: no GPS, network, active profile change or real Experience. */
class LearnedPassportPersistenceTest {
 private fun fixture():JSONObject=JSONObject(InstrumentationRegistry.getInstrumentation().context.assets
  .open("learned_passport_cases.json").bufferedReader().use {it.readText()})
 private fun job(request:JSONObject,event:String)=obj("schema_version" to "d-day-learned-job-v1","status" to "pending",
  "event_id" to event,"as_of" to request.getString("as_of"),"search_id" to "synthetic-search","journey_id" to "synthetic-journey","request" to request)
 @Test fun durableCommitRetryAndReviewReversalPreserveActivePassport(){
  val context=ApplicationProvider.getApplicationContext<Context>();val database="learned-test-${UUID.randomUUID()}.db"
  try {
   val f=fixture();val r1=f.getJSONObject("result");val root=r1.getString("original_passport_id")
   val active=obj("response" to obj("cognitive_passport" to f.getJSONObject("request").getJSONObject("initial_passport")))
   Store(context,database).use {store->
    store.put(PASSPORT,active);store.put("$PASSPORT.archive.$root",active)
    val request=f.getJSONObject("request");val key="$LEARNED.job.synthetic-e0"
    store.put(key,job(request,"synthetic-e0"));store.commitLearned(key,request,r1);store.commitLearned(key,request,r1)
    val next=f.getJSONObject("next_request");val r2=f.getJSONObject("next_result");val key2="$LEARNED.job.synthetic-e1"
    store.put(key2,job(next,"synthetic-e1"));store.commitLearned(key2,next,r2)
    store.selectLearned(root,r2.getString("tip_passport_id"));store.selectLearned(root,r1.getString("tip_passport_id"))
    assertEquals(canonical(active),canonical(store.json(PASSPORT)))
    validateLearnedStorage(store.storage())
   }
   Store(context,database).use {store->
    assertEquals(f.getJSONObject("next_result").getString("tip_passport_id"),store.get("$LEARNED.tip.$root"))
    assertEquals(r1.getString("tip_passport_id"),store.get("$LEARNED.review.$root"))
    assertEquals("complete",store.json("$LEARNED.job.synthetic-e1")!!.getString("status"))
    store.selectLearned(root,root);validateLearnedStorage(store.storage())
    assertNotNull(store.json("$LEARNED.version.${f.getJSONObject("next_result").getString("tip_passport_id")}"))
   }
  }finally{context.deleteDatabase(database)}
 }
 @Test fun conflictingVersionRollsBackEveryEarlierWrite(){
  val context=ApplicationProvider.getApplicationContext<Context>();val database="learned-test-${UUID.randomUUID()}.db"
  try {Store(context,database).use {store->
   val f=fixture();val request=f.getJSONObject("request").copyJson()
   request.put("records",f.getJSONObject("next_request").getJSONArray("records")).put("as_of",f.getJSONObject("next_request").getString("as_of"))
   val result=f.getJSONObject("result").copyJson();val first=result.objects("new_versions").single()
   val next=f.getJSONObject("next_result").objects("new_versions").single()
   result.put("new_versions",JSONArray(listOf(first,next))).put("tip_passport_id",next.getString("passport_id"))
   result.put("request_digest",learnedDigest(request))
   val key="$LEARNED.job.synthetic-e1";store.put(key,job(request,"synthetic-e1"))
   store.put("$LEARNED.version.${next.getString("passport_id")}",next.copyJson().put("revision",99))
   assertTrue(runCatching {store.commitLearned(key,request,result)}.isFailure)
   assertNull(store.json("$LEARNED.version.${first.getString("passport_id")}"))
   assertNull(store.get("$LEARNED.tip.${result.getString("original_passport_id")}"))
   assertEquals("pending",store.json(key)!!.getString("status"))
  }}finally{context.deleteDatabase(database)}
 }
}
