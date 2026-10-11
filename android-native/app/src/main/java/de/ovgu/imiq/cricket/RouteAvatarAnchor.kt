package de.ovgu.imiq.cricket

import kotlin.math.*

/** Presentation only: remote GPS fixes must never replace the chosen route's avatar. */
internal fun routeAvatarTarget(route:MapRoute?,fix:GeoPoint?,recording:Boolean):GeoPoint? {
 if(!recording||route==null||fix==null||!fix.lat.isFinite()||!fix.lon.isFinite())return null
 val north=111320.0
 val east=north*cos(fix.lat*PI/180).coerceAtLeast(.01)
 var nearest:GeoPoint?=null
 var distance=75.0 // Small GPS deviations may be shown on the path, never across the city.
 fun consider(a:GeoPoint,b:GeoPoint) {
  val ax=(a.lon-fix.lon)*east;val ay=(a.lat-fix.lat)*north
  val dx=(b.lon-a.lon)*east;val dy=(b.lat-a.lat)*north
  val length=dx*dx+dy*dy
  val fraction=if(length==0.0)0.0 else (-(ax*dx+ay*dy)/length).coerceIn(0.0,1.0)
  val deviation=hypot(ax+dx*fraction,ay+dy*fraction)
  if(deviation<=distance) {distance=deviation;nearest=GeoPoint(a.lat+(b.lat-a.lat)*fraction,a.lon+(b.lon-a.lon)*fraction)}
 }
 route.segments.forEach {segment->
  if(segment.points.size==1)consider(segment.points.first(),segment.points.first())
  segment.points.zipWithNext().forEach {(a,b)->consider(a,b)}
 }
 return nearest
}
