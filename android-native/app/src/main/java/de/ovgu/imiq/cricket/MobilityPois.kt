package de.ovgu.imiq.cricket

import android.content.Context
import org.json.JSONObject
import kotlin.math.*
import androidx.compose.ui.geometry.Offset

data class MobilityPoi(val id:String,val kind:String,val point:GeoPoint,val name:String,val tags:JSONObject,val positionKind:String) {
 val waitingPlace get()=kind=="stop"&&tags.optString("public_transport")!="stop_position"&&(tags.optString("public_transport")=="platform"||tags.optString("railway")=="platform"||tags.optString("highway")=="bus_stop")
 val gtfsIds get():List<String> {val ids=tags.optJSONArray("gtfs_ids");return if(ids!=null)(0 until ids.length()).map {ids.getString(it)}else listOfNotNull(tags.optString("gtfs_id").takeIf {it.isNotBlank()})}
 val access get()=tags.optString("motorcar",tags.optString("motor_vehicle",tags.optString("access","unknown")))
 val selectable get()=kind=="parking"&&access in listOf("yes","permissive","unknown")
 val publicParking get()=kind=="parking"&&access in listOf("yes","permissive")
}
fun poiDistance(a:GeoPoint,b:GeoPoint):Double {
 val lat=(b.lat-a.lat)*PI/180;val lon=(b.lon-a.lon)*PI/180
 val h=sin(lat/2).pow(2)+cos(a.lat*PI/180)*cos(b.lat*PI/180)*sin(lon/2).pow(2)
 return 6371000*2*asin(sqrt(h.coerceIn(0.0,1.0)))
}
fun mobilityPois(data:JSONObject):List<MobilityPoi> = data.objects("points").map {p->
 MobilityPoi(p.getString("id"),p.getString("kind"),GeoPoint(p.getDouble("lat"),p.getDouble("lon")),p.optString("name"),p.getJSONObject("tags"),p.getString("position_kind"))
}
object MagdeburgMobility {
 @Volatile private var cached:List<MobilityPoi>?=null
 @Synchronized fun load(context:Context):List<MobilityPoi> {
  cached?.let {return it}
  return mobilityPois(JSONObject(context.assets.open("magdeburg-mobility.json").bufferedReader().use {it.readText()})).also {cached=it}
 }
}
fun nearbyMobility(pois:List<MobilityPoi>,mode:String,origin:GeoPoint?,destination:GeoPoint?):List<MobilityPoi> {
 val kind=when(mode){"car"->"parking";"pt"->"stop";else->return emptyList()}
 return pois.filter {it.kind==kind&&listOfNotNull(origin,destination).any {p->poiDistance(p,it.point)<=2000}}
}
data class MapPoiPreferences(val parking:Boolean=true,val stops:Boolean=true,val radius:Int=200,val gps:Boolean=true,val restricted:Boolean=false) {
 fun json()=obj("parking" to parking,"stops" to stops,"radius" to radius,"gps" to gps,"restricted" to restricted)
 companion object {fun fromJson(p:JSONObject?)=MapPoiPreferences(p?.optBoolean("parking",true)?:true,p?.optBoolean("stops",true)?:true,(p?.optInt("radius",200)?:200).coerceIn(50,2000),p?.optBoolean("gps",true)?:true,p?.optBoolean("restricted",false)?:false)}
}
fun mapParkingVisible(points:JSONObject?,mode:String,planning:Boolean)=if(!planning&&mode=="pt")points?.optBoolean("ptParking",false)?:false else points?.optBoolean("parking",true)?:true
fun visibleMobility(pois:List<MobilityPoi>,preferences:MapPoiPreferences,origin:GeoPoint?,destination:GeoPoint?,gps:GeoPoint?):List<MobilityPoi> {
 val centers=listOfNotNull(origin,destination,gps?.takeIf {preferences.gps})
 return pois.filter {p->(p.kind=="stop"&&preferences.stops||p.kind=="parking"&&preferences.parking&&(p.selectable||preferences.restricted))&&centers.any {poiDistance(it,p.point)<=preferences.radius}}
}
fun platformPois(osm:List<MobilityPoi>,data:JSONObject):List<MobilityPoi> {
 val linked=data.objects("stops").filter {it.optJSONObject("waiting_platform")!=null}.groupBy {it.getJSONObject("waiting_platform").getString("id")}
 return osm.filter {it.kind!="stop"||it.waitingPlace}.map {p->
  val stops=linked[p.id].orEmpty();if(stops.isEmpty())p else p.copy(tags=p.tags.copyJson().put("gtfs_ids",org.json.JSONArray(stops.map {it.getString("gtfsId")})))
 }
}
fun routeStopPois(card:RouteCard?,pois:List<MobilityPoi>):List<MobilityPoi> = card?.route?.objects("legs")?.filter {it.optString("mode")=="pt"}?.flatMap {leg->
 listOf("from","to").mapNotNull {role->val transit=leg.optJSONObject("transit");val p=transit?.optJSONObject(role);val id=p?.optJSONObject("stop")?.optString("gtfsId")?.takeIf {it.isNotBlank()};val lat=p?.number("lat");val lon=p?.number("lon")
 if(id==null||lat==null||lon==null)null else pois.firstOrNull {id in it.gtfsIds}?.let {it.copy(tags=it.tags.copyJson().put("route_role",role).put("line",transit.optString("line")))}
 }}?.distinctBy {it.id}?:emptyList()
fun nearestPublicParking(pois:List<MobilityPoi>,point:GeoPoint):MobilityPoi? = pois.filter {it.publicParking&&poiDistance(point,it.point)<=2000}.minWithOrNull(compareBy<MobilityPoi> {poiDistance(point,it.point)}.thenBy {it.id})
/** Nearby waiting platforms are visible before a route exists; proximity is not service eligibility. */
fun nearestOriginPlatforms(pois:List<MobilityPoi>,origin:GeoPoint?,enabled:Boolean):List<MobilityPoi> {
 if(origin==null||!enabled)return emptyList()
 return pois.filter {it.waitingPlace&&poiDistance(origin,it.point)<=2000}
  .sortedWith(compareBy<MobilityPoi> {poiDistance(origin,it.point)}.thenBy {it.id})
  .take(3).map {it.copy(tags=it.tags.copyJson().put("origin_nearby_metres",poiDistance(origin,it.point)))}
}
data class PoiCluster(val members:List<MobilityPoi>,val position:Offset)
fun clusterMobility(points:List<Pair<MobilityPoi,Offset>>,selected:List<String>,cell:Float):List<PoiCluster> {
 if(cell<=0)return points.map {PoiCluster(listOf(it.first),it.second)}
 val singles=points.filter {it.first.id in selected}.map {PoiCluster(listOf(it.first),it.second)}
 val groups=points.filter {it.first.id !in selected}.groupBy {(poi,p)->Triple(poi.kind,floor(p.x/cell).toInt(),floor(p.y/cell).toInt())}.values.map {group->
  PoiCluster(group.map {it.first},Offset(group.map {it.second.x}.average().toFloat(),group.map {it.second.y}.average().toFloat()))
 }
 return groups+singles
}

/** Trip sequence, not stations inferred from proximity to a rail. */
fun itineraryStopPois(card:RouteCard?,pois:List<MobilityPoi>,details:JSONObject?):List<MobilityPoi> {
 val legs=card?.route?.objects("legs").orEmpty().mapIndexedNotNull {i,l->if(l.optString("mode")=="pt")i to l.optJSONObject("transit") else null}
 val found=mutableListOf<MobilityPoi>()
 legs.forEachIndexed {ordinal,(index,t)->
  if(t==null)return@forEachIndexed
  val sequence=details?.objects("legs")?.find {it.optInt("leg_index")==index}?.objects("stops").orEmpty()
  val endpoints=listOfNotNull(t.optJSONObject("from"),t.optJSONObject("to")).map {p->p.copyJson().put("gtfsId",p.optJSONObject("stop")?.optString("gtfsId"))}
  val stops=sequence.ifEmpty {endpoints}
  stops.forEachIndexed {n,p->
   val id=p.optString("gtfsId");val lat=p.number("lat");val lon=p.number("lon")
   if(id.isBlank()||lat==null||lon==null)return@forEachIndexed
   val role=when {n==0->if(ordinal==0)"boarding"else"transfer";n==stops.lastIndex->if(ordinal==legs.lastIndex)"final"else"transfer";else->"intermediate"}
   val platform=pois.firstOrNull {id in it.gtfsIds}
   val base=platform?:MobilityPoi("trip-stop-$id","stop",GeoPoint(lat,lon),p.optString("name"),obj("gtfs_ids" to org.json.JSONArray(listOf(id)),"display_only" to true),"GTFS_stop_reference")
   found.add(base.copy(tags=base.tags.copyJson().put("route_role",role).put("line",t.optString("line"))))
  }
 }
 return found.groupBy {it.id}.values.map {group->group.maxBy {when(it.tags.optString("route_role")){"transfer"->4;"final"->3;"boarding"->2;else->1}}.let {it.copy(tags=it.tags.copyJson().put("lines",group.map {p->p.tags.optString("line")}.filter {l->l.isNotBlank()}.distinct().joinToString(" ↔ ")))}}
}
