package de.ovgu.imiq.cricket

import org.json.JSONObject

internal const val NEED_IMPORTANCE_SCALE="ordinal_importance_1_7"

/** Zero means unanswered in the editor and must never reach the questionnaire API. */
internal fun needImportanceRatings(ratings:JSONObject):JSONObject {
 require(goalKeys.all {ratings.opt(it) is Int&&ratings.getInt(it) in 1..7})
 return obj(*goalKeys.mapIndexed {index,key->needKeys[index] to ratings.getInt(key)}.toTypedArray())
}

/** Allocation points cannot be treated as independent importance responses. */
internal fun importancePlan(candidate:JSONObject?,weekStart:String):JSONObject {
 if(candidate?.optString("scale")==NEED_IMPORTANCE_SCALE&&
  goalKeys.all {candidate.optJSONObject("points")?.opt(it) is Int&&candidate.getJSONObject("points").getInt(it) in 0..7})return candidate
 return obj("version" to 2,"weekStart" to weekStart,"scale" to NEED_IMPORTANCE_SCALE,
  "points" to obj(*goalKeys.map {it to 0}.toTypedArray()))
}
