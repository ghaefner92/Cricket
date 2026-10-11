package de.ovgu.imiq.cricket

import org.json.JSONArray
import org.json.JSONObject

/** An index into the same atomic backup, rather than a second copy of trajectories. */
internal fun longitudinalIndex(storage:JSONObject,records:List<JSONObject>):JSONObject {
 require(records.map {it.getString("search_id")}.distinct().size==records.size) {"Duplicate search ID"}
 val passports=sortedMapOf<String,Pair<JSONObject,JSONObject>>()
 val warnings=sortedSetOf<String>()
 fun add(wrapper:JSONObject?,source:JSONObject) {
  val p=wrapper?.optJSONObject("response")?.optJSONObject("cognitive_passport")?:return
  val lineage=p.optJSONObject("lineage")?:return
  val id=lineage.optString("passport_id");if(id.isEmpty())return
  passports[id]?.let {require(canonical(it.first.copyJson())==canonical(p.copyJson())) {"Conflicting immutable Passport: $id"};return}
  passports[id]=p to source
 }
 storage.keys().asSequence().filter {it==PASSPORT||it.startsWith("$PASSPORT.archive.")}.sorted().forEach {key->add(JSONObject(storage.getString(key)),obj("storage_key" to key))}
 val searches=JSONArray();val events=JSONArray();val seenEvents=mutableSetOf<String>()
 records.sortedBy {it.getString("search_id")}.forEach {record->
  val id=record.getString("search_id")
  add(record.optJSONObject("confirmed_snapshot"),obj("search_id" to id,"field" to "confirmed_snapshot"))
  val reference=record.optJSONObject("passport_reference")
  val simulation=record.optJSONObject("result")?.optJSONObject("contextual_deliberation")
  if(reference==null)warnings.add("$id: decision Passport reference unavailable")
  if(simulation==null)warnings.add("$id: simulation unavailable (${record.optString("status")})")
  simulation?.let {sim->
   require(sim.getString("search_id")==id) {"Simulation belongs to another search"}
   val archive=sim.optJSONObject("trajectory_archive")?:JSONObject()
   val summaries=listOfNotNull(sim.optJSONObject("baseline"))+sim.objects("candidate_results").map {it.getJSONObject("hotco")}
   summaries.forEach {summary->
    val e=summary.optJSONObject("simulation_evidence")
    val trajectory=e?.optString("trajectory_id")?.takeIf {it.isNotEmpty()}
    if(trajectory==null)warnings.add("$id: full trajectory unavailable")else {
     val states=archive.getJSONArray(trajectory);val columns=e.getJSONArray("state_columns")
     require(e.getString("state_kind")=="model_prediction"&&e.getString("time_reference")=="model_units_not_elapsed_travel_time"&&e.getDouble("dt")>0&&columns.length()==19&&states.length()==e.getInt("sample_count")) {"Invalid simulation evidence"}
     for(i in 0 until states.length()) {val row=states.getJSONArray(i);require(row.length()==columns.length()&&(0 until row.length()).all {row.opt(it) is Number&&row.getDouble(it).isFinite()}) {"Invalid trajectory sample"}}
    }
   }
  }
  val policy=record.optJSONObject("request")?.optString("context_policy","legacy_context_drive")?:"legacy_context_drive"
  if(policy!="observations_only")warnings.add("$id: legacy contextual policy; excluded from D-Day causal assumptions")
  searches.put(obj("search_id" to id,"created_at" to record.opt("created_at"),"completed_at" to record.opt("completed_at"),
   "status" to record.getString("status"),"decision_passport" to reference?.copyJson(),"context_policy" to policy,
   "source_record" to id,"recommendation_presentation" to "not_instrumented",
   "trajectory_ids" to JSONArray(simulation?.optJSONObject("trajectory_archive")?.keys()?.asSequence()?.sorted()?.toList()?:emptyList<String>())))
  record.objects("journeys").forEach {journey->
   val experience=journey.optJSONObject("experience")?:return@forEach
   val event=experience.getString("event_id")
   require(seenEvents.add(event)) {"Duplicate Experience event: $event"}
   val transitions=record.objects("passport_transitions").filter {it.optString("event_id")==event}
   require(transitions.size<=1) {"Duplicate Passport transition"}
   if(transitions.isEmpty())warnings.add("$event: before/after Passport transition unavailable (legacy or no Passport)")
   events.put(obj("event_id" to event,"at" to experience.getString("confirmed_at"),"search_id" to id,
    "journey_id" to journey.getString("journey_id"),"choice_event_id" to journey.getString("choice_event_id"),
    "passport_transition" to transitions.singleOrNull()?.copyJson(),"learning_applied" to false,"state_carryover_applied" to false))
  }
  require(record.objects("passport_transitions").all {t->record.objects("journeys").any {it.optJSONObject("experience")?.optString("event_id")==t.optString("event_id")}}) {"Orphan Passport transition"}
 }
 records.forEach {record->
  validateProspectiveRecord(record)
  record.optJSONObject("passport_reference")?.optString("passport_id")?.takeIf {it.isNotEmpty()&&!passports.containsKey(it)}?.let {warnings.add("${record.getString("search_id")}: decision Passport snapshot unavailable ($it)")}
  record.objects("passport_transitions").forEach {t->
   val before=passports[t.getJSONObject("before").getString("passport_id")]?.first?:error("Missing Passport before Experience")
   val after=passports[t.getJSONObject("after").getString("passport_id")]?.first?:error("Missing Passport after Experience")
   val b=before.getJSONObject("lineage");val a=after.getJSONObject("lineage")
   require(a.getString("parent_passport_id")==b.getString("passport_id")&&a.getInt("revision")==b.getInt("revision")+1) {"Broken Passport transition"}
   require(t.getJSONObject("before").getInt("revision")==b.getInt("revision")&&t.getJSONObject("after").getInt("revision")==a.getInt("revision")) {"Incorrect Passport revision"}
   require(canonical(before.getJSONObject("profile").copyJson())==canonical(after.getJSONObject("profile").copyJson())) {"Evidence-only revision changed cognitive profile"}
   require(after.objects("evidence_history").any {it.optString("event_id")==t.getString("event_id")}) {"Passport lacks Experience evidence"}
   val journey=record.objects("journeys").single {it.optJSONObject("experience")?.optString("event_id")==t.getString("event_id")}
   val history=after.objects("evidence_history");val entry=history.single {it.optString("event_id")==t.getString("event_id")}
   require(entry.getString("search_id")==record.getString("search_id")&&entry.getString("journey_id")==journey.getString("journey_id")&&entry.getString("choice_event_id")==journey.getString("choice_event_id")&&canonical(entry.getJSONObject("experience"))==canonical(journey.getJSONObject("experience"))) {"Passport evidence does not match journey"}
   require(canonical(JSONArray(before.objects("evidence_history")))==canonical(JSONArray(history.filter {it.optString("event_id")!=t.getString("event_id")}))) {"Passport evidence history was overwritten"}
   require(t.opt("learning_applied")==false&&t.opt("state_carryover_applied")==false) {"Unexpected learning in D-Day phases 1–3"}
  }
 }
 val versions=JSONArray(passports.map {(id,pair)->
  val lineage=pair.first.getJSONObject("lineage")
  val parent=lineage.optString("parent_passport_id").takeIf {it.isNotEmpty()&&it!="null"}
  if(parent!=null&&!passports.containsKey(parent))warnings.add("$id: parent snapshot unavailable ($parent)")
  obj("passport_id" to id,"revision" to lineage.getInt("revision"),"parent_passport_id" to parent,"source" to pair.second)
 })
 return obj("schema_version" to "d-day-longitudinal-v1","learning_applied" to false,"state_carryover_applied" to false,
  "model_time_reference" to "model_units_not_elapsed_travel_time","observation_time_reference" to "recorded_wall_clock_timestamps",
  "passport_versions" to versions,"searches" to searches,"experience_events" to events,"warnings" to JSONArray(warnings.toList()))
}

internal fun passportTransition(before:JSONObject,after:JSONObject,journey:JSONObject)=obj(
 "event_id" to journey.getJSONObject("experience").getString("event_id"),
 "before" to before.getJSONObject("response").getJSONObject("cognitive_passport").getJSONObject("lineage").copyJson(),
 "after" to after.getJSONObject("response").getJSONObject("cognitive_passport").getJSONObject("lineage").copyJson(),
 "learning_applied" to false,"state_carryover_applied" to false)
