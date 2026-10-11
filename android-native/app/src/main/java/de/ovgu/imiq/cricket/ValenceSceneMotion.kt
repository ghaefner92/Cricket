package de.ovgu.imiq.cricket

import kotlin.math.*

internal data class ValenceWorldPose(val x:Float,val ground:Float,val depth:Float,val visibility:Float,val facingRight:Boolean)

/** Coordinates refer to the authored 3:2 scenes, not device pixels. */
internal fun valenceWorldPose(mode:String,time:Float,animated:Boolean):ValenceWorldPose {
 val t=if(animated)time.coerceAtLeast(0f)else 0f
 if(mode=="walk") {
  // Three visible ramp runs alternate with passages hidden behind the tower.
  val points=listOf(.44f to .716f,.757f to .628f,.334f to .548f,.682f to .469f,.403f to .384f,.623f to .319f,.44f to .716f)
  // Keep the leisurely 8-second front runs; rear turns take only 0.9 seconds.
  val lap=(t+3.84f)%26.7f
  val turn=(lap/8.9f).toInt().coerceIn(0,2)
  val local=lap-turn*8.9f
  val segment=turn*2+if(local<8f)0 else 1
  val u=if(local<8f)local/8f else ((local-8f)/.9f).coerceIn(0f,1f)
  val a=points[segment];val b=points[segment+1]
  val visible=if(segment%2==0)1f else (1f-min(u,1-u)*12f).coerceIn(0f,1f)
  val distance=if(segment%2==0)sin(u*PI).toFloat()else 0f
  return ValenceWorldPose(a.first+(b.first-a.first)*u,a.second+(b.second-a.second)*u,
   (1f-segment*.065f)*(1f-.32f*distance),visible,b.first>a.first)
 }
 val angle=t*when(mode){"bike"->.23f;"pt"->.19f;else->.26f}
 return ValenceWorldPose(.5f+(if(mode=="bike").25f else .16f)*sin(angle),if(mode=="pt").90f else .93f,1f,1f,cos(angle)>=0)
}
