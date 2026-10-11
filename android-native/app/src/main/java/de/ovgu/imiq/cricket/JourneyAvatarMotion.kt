package de.ovgu.imiq.cricket

import org.json.JSONObject
import kotlin.math.*

/** Presentation-only inference. Never changes the saved choice or experience. */
class JourneyAvatarMotion(private val selected:String,private val route:MapRoute?) {
 var mode=if(selected=="pt")"walk"else selected
  private set
 private var processed=0
 private var previous:JSONObject?=null
 private var candidate:String?=null
 private var since=0L
 private var evidence=0
 private var transitLeg=-1
 private fun clear(){candidate=null;evidence=0;since=0L}
 fun update(points:List<JSONObject>):String {
  if(selected!="pt")return mode
  if(points.size<processed){processed=0;previous=null;mode="walk";transitLeg=-1;clear()}
  while(processed<points.size) {
   val point=points[processed++];val old=previous;previous=point
   if(old==null||old.optInt("segment_index")!=point.optInt("segment_index")){clear();continue}
   val ms=point.optLong("monotonic_ms");val dt=(ms-old.optLong("monotonic_ms"))/1000.0
   val accuracy=point.number("accuracy_m")?:Double.MAX_VALUE
   val oldAccuracy=old.number("accuracy_m")?:Double.MAX_VALUE
   if(dt !in 6.0..60.0||max(accuracy,oldAccuracy)>30.0){clear();continue}
   val p=GeoPoint(point.getDouble("lat"),point.getDouble("lon"))
   val distance=distanceMeters(old,point)
   val speed=distance/dt
   val lowerSpeed=(distance-max(accuracy,oldAccuracy)).coerceAtLeast(0.0)/dt
   val segments=route?.segments.orEmpty()
   val transit=segments.withIndex().filter {it.value.mode=="pt"}.minByOrNull {distanceToLine(p,it.value.points)}
   val transitDistance=transit?.let {distanceToLine(p,it.value.points)}?:Double.POSITIVE_INFINITY
   val exitWalk=segments.withIndex().filter {it.index>transitLeg&&it.value.mode=="walk"}.minByOrNull {distanceToLine(p,it.value.points)}
   val walkDistance=exitWalk?.let {distanceToLine(p,it.value.points)}?:Double.POSITIVE_INFINITY
   val next=when {
    mode=="walk"&&transit!=null&&transitDistance<=70+accuracy&&speed>=3.5&&lowerSpeed>=2.5->"pt"
    mode=="pt"&&transitLeg>=0&&exitWalk!=null&&speed in .3..2.2&&walkDistance<=40+accuracy&&walkDistance+15<transitDistance->"walk"
    else->null
   }
   if(next==null){clear();continue}
   if(candidate!=next){candidate=next;since=old.optLong("monotonic_ms");evidence=0}
   evidence++
   val dwell=if(next=="pt")20000L else 30000L
   if(evidence>=3&&ms-since>=dwell) {
    mode=next
    if(next=="pt")transitLeg=transit!!.index
    clear()
   }
  }
  return mode
 }
 private fun distanceToLine(p:GeoPoint,line:List<GeoPoint>):Double {
  val unit=PI/180*6371000.0;val longitude=unit*cos(p.lat*PI/180)
  return line.zipWithNext().minOfOrNull { (a,b)->
   val ax=(a.lon-p.lon)*longitude;val ay=(a.lat-p.lat)*unit
   val bx=(b.lon-p.lon)*longitude;val by=(b.lat-p.lat)*unit
   val dx=bx-ax;val dy=by-ay;val length=dx*dx+dy*dy
   val t=if(length==0.0)0.0 else (-(ax*dx+ay*dy)/length).coerceIn(0.0,1.0)
   hypot(ax+t*dx,ay+t*dy)
  }?:Double.POSITIVE_INFINITY
 }
}
