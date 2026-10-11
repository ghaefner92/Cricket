package de.ovgu.imiq.cricket

import kotlin.math.*

internal data class GoalProjection(val x:Float,val y:Float,val depth:Float)
internal fun projectGoalPoint(x:Double,y:Double,z:Double,yaw:Float,pitch:Float):GoalProjection {
 val rx=x*cos(yaw.toDouble())+z*sin(yaw.toDouble())
 val rz=z*cos(yaw.toDouble())-x*sin(yaw.toDouble())
 val ry=y*cos(pitch.toDouble())-rz*sin(pitch.toDouble())
 val depth=y*sin(pitch.toDouble())+rz*cos(pitch.toDouble())
 val perspective=3.5/(3.5-depth)
 return GoalProjection((rx*perspective).toFloat(),(ry*perspective).toFloat(),depth.toFloat())
}
internal fun goalSpherePoint(index:Int,count:Int,yaw:Float,pitch:Float):GoalProjection {
 val y=1.0-2.0*(index+.5)/count
 val radius=sqrt(1-y*y);val angle=index*PI*(3-sqrt(5.0))
 return projectGoalPoint(radius*cos(angle),y,radius*sin(angle),yaw,pitch)
}
internal fun goalSalience(rating:Int):Float?=if(rating in 1..7)(rating-1)/6f else null

/** Directional selector with a central cancellation zone and an outer limit. */
internal fun aimedGoalIndex(x:Float,y:Float,rx:Float,ry:Float,count:Int):Int? {
 if(rx<=0||ry<=0||count<=0||!x.isFinite()||!y.isFinite())return null
 val nx=x/rx;val ny=y/ry;val radius=sqrt(nx*nx+ny*ny)
 if(radius<.55f||radius>1.45f)return null
 val angle=atan2(ny.toDouble(),nx.toDouble())+PI/2
 return ((angle/(2*PI)*count).roundToInt()%count+count)%count
}

internal fun shortGoalName(key:String,de:Boolean=false)=if(de)when(key){
 "env"->"Öko-Modus";"health_activity"->"Körperliche Aktivität";"cost"->"Sparen";"time"->"Zeit"
 "comfort_physical"->"Komfort";"reliable"->"Zuverlässigkeit";"flex"->"Flexibilität"
 "safety_crime"->"Schutz";"health_infection"->"Infektion";"crowding"->"Freiraum"
 "safety_accident"->"Verkehr";else->key
}else when(key){
 "env"->"Eco-mode";"health_activity"->"Physical Activity";"cost"->"Savings";"time"->"Time"
 "comfort_physical"->"Comfort";"reliable"->"Reliability";"flex"->"Flexibility"
 "safety_crime"->"Security";"health_infection"->"Infection";"crowding"->"Space"
 "safety_accident"->"Traffic";else->key
}

internal data class GoalPriorityRank(val position:Int,val tied:Boolean)
/** Competition ranking: two first places are 1=, 1=, then 3. Unanswered is unranked. */
internal fun goalPriorityRanks(ratings:Map<String,Int>):Map<String,GoalPriorityRank> {
 val valid=ratings.filterValues {it in 1..7}
 return valid.mapValues {(_,value)->GoalPriorityRank(1+valid.values.count {it>value},valid.values.count {it==value}>1)}
}
