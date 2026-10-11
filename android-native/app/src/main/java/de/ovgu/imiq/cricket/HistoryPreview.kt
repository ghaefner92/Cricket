package de.ovgu.imiq.cricket

import org.json.JSONArray
import org.json.JSONObject

/** Display metadata only; never retain simulation payloads or GPS arrays. */
internal fun historyPreview(record:JSONObject):JSONObject = JSONObject().apply {
 put("history_preview",true)
 listOf("search_id","created_at","status","places","choices").forEach {key->if(record.has(key))put(key,record.get(key))}
 put("journeys",JSONArray().apply {record.objects("journeys").forEach {trip->
  put(JSONObject().apply {
   listOf("journey_id","status","capture_mode").forEach {key->if(trip.has(key))put(key,trip.get(key))}
   put("tracking",JSONObject().apply {val tracking=trip.optJSONObject("tracking");listOf("distance_m","elapsed_seconds").forEach {key->if(tracking?.has(key)==true)put(key,tracking.get(key))}})
  })
 }})
}
