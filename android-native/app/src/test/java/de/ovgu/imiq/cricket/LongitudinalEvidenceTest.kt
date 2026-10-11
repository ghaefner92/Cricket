package de.ovgu.imiq.cricket

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class LongitudinalEvidenceTest {
 private fun fixture()=JSONObject(File("../../validation/fixtures/d_day_backup.json").readText())
 @Test fun sharedExportIndexDistinguishesDecisionAndUpdatePassports() {
  val b=fixture();val index=longitudinalIndex(b.getJSONObject("storage"),b.objects("simulations"))
  assertEquals(canonical(b.getJSONObject("longitudinal")),canonical(index))
  assertEquals("fixture-passport",index.objects("searches").single().getJSONObject("decision_passport").getString("passport_id"))
  assertEquals("daily-update",index.objects("experience_events").single().getJSONObject("passport_transition").getJSONObject("before").getString("passport_id"))
  assertEquals(3,index.objects("passport_versions").size)
 }
 @Test fun conflictingPassportIdentityCannotOverwriteHistory() {
  val b=fixture();val r=b.objects("simulations").single()
  r.getJSONObject("confirmed_snapshot").getJSONObject("response").getJSONObject("cognitive_passport").getJSONObject("profile").getJSONObject("needs").put("comfort",1)
  assertThrows(IllegalArgumentException::class.java) {longitudinalIndex(b.getJSONObject("storage"),listOf(r))}
 }
 @Test fun missingPriorVersionAndDuplicateExperienceAreRejected() {
  val b=fixture();val storage=b.getJSONObject("storage");storage.remove("$PASSPORT.archive.daily-update")
  assertTrue(runCatching {longitudinalIndex(storage,b.objects("simulations"))}.isFailure)
  val original=fixture();val r=original.objects("simulations").single();r.getJSONArray("journeys").put(r.objects("journeys").single().copyJson())
  assertThrows(IllegalArgumentException::class.java) {longitudinalIndex(original.getJSONObject("storage"),listOf(r))}
 }
 @Test fun simulatedStateCannotBeInjectedByAnEvidenceOnlyRevision() {
  val b=fixture();val storage=b.getJSONObject("storage");val current=JSONObject(storage.getString(PASSPORT))
  current.getJSONObject("response").getJSONObject("cognitive_passport").getJSONObject("profile").getJSONObject("needs").put("comfort",.99)
  storage.put(PASSPORT,current.toString())
  assertThrows(IllegalArgumentException::class.java) {longitudinalIndex(storage,b.objects("simulations"))}
 }
 @Test fun alteredFeedbackCannotDisagreeWithPassportEvidence() {
  val b=fixture();b.objects("simulations").single().objects("journeys").single().getJSONObject("experience").put("changes","altered feedback")
  assertThrows(IllegalArgumentException::class.java) {longitudinalIndex(b.getJSONObject("storage"),b.objects("simulations"))}
 }
}
