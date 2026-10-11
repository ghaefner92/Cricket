package de.ovgu.imiq.cricket

import java.io.File
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class LearnedPassportTest {
 private fun fixture()=JSONObject(File("../../validation/fixtures/learned_passport_cases.json").readText())
 @Test fun backendVersionsPreserveLineageAndNeverActivate(){
  val f=fixture()
  validateLearnedResponse(f.getJSONObject("request"),f.getJSONObject("result"))
  validateLearnedResponse(f.getJSONObject("next_request"),f.getJSONObject("next_result"))
  validateLearnedStorage(f.getJSONObject("storage"))
  val v=f.getJSONObject("next_result").objects("new_versions").single()
  assertEquals(2,v.getInt("revision"));assertEquals(4,v.objects("evidence_ledger").size)
  assertFalse(v.getJSONObject("activation").getBoolean("applied"))
  assertEquals("original_prior_plus_unique_full_history",v.getJSONObject("configuration").getString("update_strategy"))
 }
 @Test fun corruptSamplesAndDuplicateEvidenceAreRejected(){
  val v=fixture().getJSONObject("result").objects("new_versions").single()
  val samples=v.getJSONObject("targets").getJSONObject("comfort").getJSONObject("walk").getJSONObject("basic").getJSONObject("samples")
  samples.put("digest","0".repeat(64))
  assertTrue(runCatching {validateLearnedVersion(v)}.isFailure)
  val v2=fixture().getJSONObject("result").objects("new_versions").single()
  v2.getJSONArray("evidence_ledger").put(v2.objects("evidence_ledger")[0].copyJson())
  assertTrue(runCatching {validateLearnedVersion(v2)}.isFailure)
 }
 @Test fun wrongParentsFutureCutoffsAndActiveResultsAreRejected(){
  val f=fixture();val r=f.getJSONObject("next_result")
  r.objects("new_versions").single().put("parent_passport_id","wrong")
  assertTrue(runCatching {validateLearnedResponse(f.getJSONObject("next_request"),r)}.isFailure)
  val clean=fixture();clean.getJSONObject("request").put("as_of","2026-10-08T00:00:00Z")
  assertTrue(runCatching {validateLearnedResponse(clean.getJSONObject("request"),clean.getJSONObject("result"))}.isFailure)
  val active=fixture();active.getJSONObject("result").put("hotco_modified",true)
  assertTrue(runCatching {validateLearnedResponse(active.getJSONObject("request"),active.getJSONObject("result"))}.isFailure)
 }
 @Test fun missingAncestorsAndForeignReviewPointersAreRejected(){
  val values=fixture().getJSONObject("storage")
  val v2=fixture().getJSONObject("next_result").objects("new_versions").single()
  values.remove("$LEARNED.version.${v2.getString("parent_passport_id")}")
  assertTrue(runCatching {validateLearnedStorage(values)}.isFailure)
  val clean=fixture().getJSONObject("storage");clean.put("$LEARNED.review.foreign",v2.getString("passport_id"))
  assertTrue(runCatching {validateLearnedStorage(clean)}.isFailure)
 }
 @Test fun originalCanBeSelectedWithoutDeletingLearnedVersions(){
  val f=fixture();val root=f.getJSONObject("result").getString("original_passport_id")
  val storage=f.getJSONObject("storage")
  storage.put("$PASSPORT.archive.$root",obj("response" to obj("cognitive_passport" to f.getJSONObject("request").getJSONObject("initial_passport"))).toString())
  storage.put("$LEARNED.review.$root",root)
  validateLearnedStorage(storage)
  assertEquals(2,storage.keys().asSequence().count {it.startsWith("$LEARNED.version.")})
  assertTrue(allowedKey("d-day.evidence-exclusions.v1"))
 }
 @Test fun excludedEventCanCompleteWithoutCreatingAnotherVersion(){
  val f=fixture();val request=f.getJSONObject("next_request")
  val previous=request.getJSONObject("previous_version")
  val r=obj("schema_version" to "d-day-learned-passport-result-v1","original_passport_id" to previous.getString("original_passport_id"),
   "request_digest" to learnedDigest(request),
   "previous_passport_id" to previous.getString("passport_id"),"tip_passport_id" to previous.getString("passport_id"),
   "new_versions" to JSONArray(),"status" to "unchanged","learning_applied_to_active_passport" to false,"hotco_modified" to false,"stored_on_server" to false)
  validateLearnedResponse(request,r)
 }
 @Test fun crossLanguageDigestsSurviveDecimalsUnicodeAndJsonRoundtrip(){
  val f=fixture()
  f.objects("digest_cases").forEach {c->assertEquals(c.getString("digest"),learnedDigest(c.opt("value")))}
  assertEquals(learnedDigest(obj("n" to 1.0)),learnedDigest(obj("n" to 1)))
  assertNotEquals(learnedDigest(obj("n" to true)),learnedDigest(obj("n" to 1)))
  assertTrue(runCatching {learnedDigest(9007199254740992L)}.isFailure)
 }
}
