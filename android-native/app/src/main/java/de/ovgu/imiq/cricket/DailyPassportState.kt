package de.ovgu.imiq.cricket

import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

internal fun today()=LocalDate.now().toString()
internal fun dailyKey(date:String,suffix:String)="imiq.experimental.daily-goals.v1.$date.$suffix"
internal fun dailyGoalPayload(plan:JSONObject):JSONObject=obj("local_date" to plan.getString("localDate"),
 "timezone" to plan.getString("timezone"),"confirmed_at" to plan.getString("confirmedAt"),
 "ratings" to needImportanceRatings(plan.getJSONObject("points")))

internal fun editedDailyGoal(base:JSONObject,key:String,rating:Int):JSONObject {
 require(key in goalKeys&&rating in 1..7&&base.getString("scale")==NEED_IMPORTANCE_SCALE)
 return base.copyJson().also {it.getJSONObject("points").put(key,rating)}
}

internal fun proposedDailyPlan(candidate:JSONObject?,previous:JSONObject?,date:String):JSONObject {
 if(candidate?.optString("localDate")==date)return importancePlan(candidate,date).put("localDate",date).put("timezone",ZoneId.systemDefault().id)
 val proposal=importancePlan(null,date).put("localDate",date).put("timezone",ZoneId.systemDefault().id)
 val prior=previous?.optJSONObject("sourceSnapshot")?.optJSONObject("plan")
 if(prior?.optString("scale")==NEED_IMPORTANCE_SCALE){
  val values=prior.optJSONObject("points")
  if(values!=null&&goalKeys.all {values.opt(it) is Int&&values.getInt(it) in 1..7})proposal.put("points",values.copyJson()).put("proposalFromPreviousDay",true)
 }
 return proposal
}

/** Evidence changes the version, not the inferred cognitive state in this delivery. */
internal fun passportWithExperience(current:JSONObject,record:JSONObject,journey:JSONObject):JSONObject {
 val next=current.copyJson();val p=next.getJSONObject("response").getJSONObject("cognitive_passport")
 val experience=journey.getJSONObject("experience");val eventId=experience.getString("event_id")
 val history=p.optJSONArray("evidence_history")?:org.json.JSONArray()
 if(history.objects().any {it.optString("event_id")==eventId})return next
 val parent=p.getJSONObject("lineage").copyJson()
 val event=obj("event_id" to eventId,"kind" to "experience_recorded","at" to experience.getString("confirmed_at"),
  "search_id" to record.getString("search_id"),"journey_id" to journey.getString("journey_id"),
  "choice_event_id" to journey.getString("choice_event_id"),"decision_passport" to record.getJSONObject("passport_reference").copyJson(),
  "experience" to experience.copyJson(),"learning_applied" to false,"state_carryover_applied" to false)
 history.put(event);p.put("evidence_history",history)
 p.put("lineage",parent.copyJson().put("passport_id",UUID.nameUUIDFromBytes((parent.getString("passport_id")+"|"+eventId).toByteArray()).toString())
  .put("parent_passport_id",parent.getString("passport_id")).put("revision",parent.getInt("revision")+1)
  .put("origin","experience_evidence_only").put("last_update_event",event.copyJson()))
 // Existing adaptive questions belong to their original Passport ID.
 p.put("adaptive_questioning",obj("question_needed" to false,"reason" to "evidence_only_revision"))
 next.put("createdAt",experience.getString("confirmed_at"))
 return next
}
