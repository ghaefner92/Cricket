package de.ovgu.imiq.cricket
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProspectiveLearningTest {
 @Test fun explicitlyClassifiedTestsAreNotScoredButMissingGpsAloneIsAllowed(){
  val (r,j)=fixture();assertEquals("scored",prospectiveEvaluation(r,j).getString("status"))
  j.put("evidence_classification","stationary_test")
  assertEquals("explicit_stationary_test",prospectiveEvaluation(r,j).getString("reason"))
  j.put("evidence_classification","unknown")
  assertThrows(IllegalArgumentException::class.java){prospectiveEvaluation(r,j)}
 }
 private fun fixture():Pair<JSONObject,JSONObject> {
  val models=obj(*listOf("static","basic","contextual").map {it to obj("prediction_usable_for_research" to true,"category_probabilities" to JSONArray(List(7){1.0/7}))}.toTypedArray())
  val target=obj("predictions" to JSONArray(listOf(obj("prediction_id" to "1-walk","mode" to "walk","models" to models))))
  val record=obj("d_day_forecast" to obj("status" to "complete","recorded_at" to "2026-10-09T10:00:30Z","result" to obj("request_digest" to "fixture","as_of" to "2026-10-09T10:00:00Z","targets" to obj("comfort" to target.copyJson(),"affect" to target.copyJson()))))
  val journey=obj("journey_id" to "j","choice_event_id" to "c","selection" to obj("chosen_at" to "2026-10-09T10:01:00Z","routing_rank" to 1,"mode_key" to "walk"),
   "experience" to obj("event_id" to "e","completion" to "as_planned","comfort_observation" to obj("mode" to "walk","rating" to 4),"mode_valences_raw_minus3_to_3" to obj("walk" to 0)))
  return record to journey
 }
 @Test fun scoresUseCategoryProbabilitiesForBothTargetsAndNeutralIsObserved(){
  val (r,j)=fixture();val e=prospectiveEvaluation(r,j);assertEquals("scored",e.getString("status"));assertEquals(6,e.getJSONArray("scores").length())
  e.objects("scores").forEach {assertEquals(kotlin.math.ln(1.0/7),it.getDouble("log_score"),1e-12);assertEquals(2.0/21,it.getDouble("ranked_probability_score"),1e-12);assertEquals(0.0,it.getDouble("normalized_absolute_error"),1e-12)}
 }
 @Test fun lateChangedAndNotTakenAreUnscored(){
  val (r,j)=fixture();r.getJSONObject("d_day_forecast").put("recorded_at","2026-10-09T10:02:00Z")
  assertEquals("forecast_recorded_after_choice",prospectiveEvaluation(r,j).getString("reason"))
  j.getJSONObject("experience").put("completion","changed");assertEquals("changed_route_context_not_attributable",prospectiveEvaluation(r,j).getString("reason"))
  j.getJSONObject("experience").put("completion","not_taken");assertEquals("trip_not_taken",prospectiveEvaluation(r,j).getString("reason"))
 }
 @Test fun evaluationsAreIdempotentAndFailedSamplerIsExcluded(){
  val (r,j)=fixture();r.getJSONObject("d_day_forecast").getJSONObject("result").getJSONObject("targets").getJSONObject("affect").objects("predictions")[0].getJSONObject("models").getJSONObject("basic").put("prediction_usable_for_research",false)
  appendProspectiveEvaluation(r,j);val before=canonical(r);appendProspectiveEvaluation(r,j);assertEquals(before,canonical(r));assertEquals(5,r.objects("d_day_evaluations")[0].getJSONArray("scores").length())
 }
 @Test fun aForecastFinishingAfterConfirmationCannotBecomeAnEligiblePrediction(){
  val (r,j)=fixture();val frozen=r.getJSONObject("d_day_forecast").copyJson()
  r.getJSONObject("d_day_forecast").put("status","pending");appendProspectiveEvaluation(r,j)
  assertEquals("forecast_not_available",r.objects("d_day_evaluations").single().getString("reason"))
  frozen.put("recorded_at","2026-10-09T10:02:00Z");r.put("d_day_forecast",frozen);appendProspectiveEvaluation(r,j)
  assertEquals(1,r.getJSONArray("d_day_evaluations").length());assertEquals("forecast_recorded_after_choice",r.objects("d_day_evaluations").single().getString("reason"))
 }
 @Test fun newScoresRetainWeatherVersionWithoutRewritingLegacyScores(){
  val (r,j)=fixture();assertTrue(prospectiveEvaluation(r,j).objects("scores").none {it.has("weather_model_version")})
  val targets=r.getJSONObject("d_day_forecast").getJSONObject("result").getJSONObject("targets")
  for(target in listOf("comfort","affect"))targets.getJSONObject(target).put("weather_contract",obj("weather_model_version" to "d-day-weather-intensity-v2"))
  assertTrue(prospectiveEvaluation(r,j).objects("scores").all {it.getString("weather_model_version")=="d-day-weather-intensity-v2"})
 }
}
