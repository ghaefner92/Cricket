package de.ovgu.imiq.cricket

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ComfortPilotTest {
 private val cases=JSONArray(File("../../validation/fixtures/journey_cases.json").readText())
 private val contract=JourneyContract(JSONObject(File("../../contracts/journey-v1.schema.json").readText()))
 @Test fun requiredComfortKeepsNeutralAndRejectsWrongModeAndTime() {
  val record=cases.getJSONObject(0).getJSONObject("record").copyJson().put("comfort_pilot_required",true)
  val trip=TrackingEngine.create(record,0,false,"2026-10-08T10:01:00Z",1000,1)
  TrackingEngine.confirm(trip,"as_planned",listOf("walk"),JSONObject(),"",null,"2026-10-08T10:04:00Z")
  assertThrows(IllegalArgumentException::class.java){contract.validate(record,trip)}
  val answer=comfortObservation("walk",4,"en","2026-10-08T10:03:00Z",false)
  trip.getJSONObject("experience").put("comfort_observation",answer)
  contract.validate(record,trip);assertEquals(4,answer.getInt("rating"))
  answer.put("mode","bike");assertThrows(IllegalArgumentException::class.java){contract.validate(record,trip)}
  answer.put("mode","walk").put("answered_at","2026-10-08T10:05:00Z")
  assertThrows(IllegalArgumentException::class.java){contract.validate(record,trip)}
 }
 @Test fun multimodalAnswerMustIdentifyOneActualMode() {
  val record=cases.getJSONObject(0).getJSONObject("record").copyJson().put("comfort_pilot_required",true)
  val trip=TrackingEngine.create(record,0,false,"2026-10-08T10:01:00Z",1000,1)
  val answer=comfortObservation("pt",6,"de","2026-10-08T10:03:00Z",true)
  TrackingEngine.confirm(trip,"changed",listOf("walk","pt"),JSONObject(),"changed",null,"2026-10-08T10:04:00Z",comfortObservation=answer)
  contract.validate(record,trip)
  answer.put("mode_selection","single_actual_mode");trip.getJSONObject("experience").put("comfort_observation",answer)
  assertThrows(IllegalArgumentException::class.java){contract.validate(record,trip)}
 }
 @Test fun notTakenDoesNotRequireOrAcceptFabricatedComfort() {
  val record=cases.getJSONObject(0).getJSONObject("record").copyJson().put("comfort_pilot_required",true)
  val trip=TrackingEngine.create(record,0,false,"2026-10-08T10:01:00Z",1000,1)
  TrackingEngine.confirm(trip,"not_taken",emptyList(),JSONObject(),"",null,"2026-10-08T10:04:00Z")
  contract.validate(record,trip)
  trip.getJSONObject("experience").put("comfort_observation",comfortObservation("walk",4,"en","2026-10-08T10:03:00Z",false))
  assertThrows(IllegalArgumentException::class.java){contract.validate(record,trip)}
 }
}
