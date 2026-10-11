package de.ovgu.imiq.cricket
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AffectPilotTest {
 private val cases=JSONArray(File("../../validation/fixtures/journey_cases.json").readText())
 private val contract=JourneyContract(JSONObject(File("../../contracts/journey-v1.schema.json").readText()))
 @Test fun ordinalValuesUseTheSameNormalizationAsInitialPassport(){
  for(mode in modes)for(raw in -3..3)assertEquals(raw/3.0,normalizedExperienceValences(obj(mode to raw)).getDouble(mode),1e-12)
  assertThrows(IllegalArgumentException::class.java){normalizedExperienceValences(obj("walk" to 4))}
 }
 @Test fun eachActualModeIsRequiredAndNeutralIsAValidAnswer(){
  val record=cases.getJSONObject(0).getJSONObject("record").copyJson().put("affect_pilot_required",true)
  val trip=TrackingEngine.create(record,0,false,"2026-10-08T10:01:00Z",1000,1)
  TrackingEngine.confirm(trip,"changed",listOf("walk","pt"),JSONObject(),"",null,"2026-10-08T10:04:00Z",valences=obj("walk" to 0))
  assertThrows(IllegalArgumentException::class.java){contract.validate(record,trip)}
  val e=trip.getJSONObject("experience");val raw=obj("walk" to 0,"pt" to -2)
  e.put("mode_valences_raw_minus3_to_3",raw).put("mode_valences_normalized_minus1_to_1",normalizedExperienceValences(raw))
  contract.validate(record,trip)
  e.getJSONObject("mode_valences_normalized_minus1_to_1").put("pt",1)
  assertThrows(IllegalArgumentException::class.java){contract.validate(record,trip)}
 }
 @Test fun notTakenDoesNotRequireAffectiveEvidence(){
  val record=cases.getJSONObject(0).getJSONObject("record").copyJson().put("affect_pilot_required",true)
  val trip=TrackingEngine.create(record,0,false,"2026-10-08T10:01:00Z",1000,1)
  TrackingEngine.confirm(trip,"not_taken",emptyList(),JSONObject(),"",null,"2026-10-08T10:04:00Z")
  contract.validate(record,trip)
 }
}
