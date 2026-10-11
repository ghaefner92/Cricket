package de.ovgu.imiq.cricket

import org.json.JSONObject

/** Preserve original points; the backend divides by ten without a Likert round trip. */
internal fun weeklyNeedAllocation(points:JSONObject):JSONObject {
 require(goalKeys.all {points.opt(it) is Int&&points.getInt(it) in 0..10})
 require(goalKeys.sumOf {points.getInt(it)}==10)
 return obj("schema_version" to "weekly_need_allocation_1.0","budget" to 10,
  "points" to obj(*goalKeys.mapIndexed {index,key->needKeys[index] to points.getInt(key)}.toTypedArray()))
}
