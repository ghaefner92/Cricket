package de.ovgu.imiq.cricket

import org.json.JSONObject

internal fun liveDepartureDelay(transit:JSONObject?):Double? =
 if(transit?.optBoolean("realtime")==true)transit.number("departure_delay_seconds")?.takeIf {it.isFinite()&&it>0} else null

// A card shows the largest individual departure delay, never a sum of delays.
internal fun largestDepartureDelay(route:JSONObject):JSONObject? = route.objects("legs")
 .filter {modelMode(it.optString("mode"))=="pt"}
 .mapNotNull {it.optJSONObject("transit")}
 .filter {liveDepartureDelay(it)!=null}
 .maxByOrNull {liveDepartureDelay(it)!!}

internal fun departureDelayLabel(seconds:Double):String =
 if(seconds<60)"+${metric(seconds,0)} s" else "+${metric(seconds/60,1)} min"

/** Presentation only: saved routing metrics and HOTCO evidence remain immutable. */
internal fun summaryArrivalMs(card:RouteCard,now:Long):Double? {
 if(card.mode=="pt")return transitTiming(card,now)?.arrivalMs
 if(card.route.optJSONObject("summary")?.optBoolean("origin_access_time_unknown")==true)return null
 val seconds=card.route.optJSONObject("summary")?.number("duration_seconds")?:return null
 if(!seconds.isFinite()||seconds<0||seconds>30*24*60*60)return null
 return now.toDouble()+seconds*1000
}

/** Lower bound only: no time is invented for unmapped access. */
internal fun carArrivalLowerBoundMs(card:RouteCard,now:Long):Double? {
 if(card.mode!="car"||originAccessGap(card)==null&&destinationAccessGap(card)==null)return null
 val seconds=card.route.optJSONObject("summary")?.number("duration_seconds")?:return null
 if(!seconds.isFinite()||seconds<0||seconds>30*24*60*60)return null
 return now.toDouble()+seconds*1000
}

internal data class TransitService(val vehicle:String,val line:String?)
internal fun transitServices(card:RouteCard):List<TransitService> = card.route.objects("legs")
 .filter {modelMode(it.optString("mode"))=="pt"}
 .map {leg->
  val transit=leg.optJSONObject("transit")
  val type=when(transit?.optString("source_mode")?.uppercase()) {
   "TRAM","LIGHT_RAIL","STREETCAR"->"tram"
   "BUS","TROLLEYBUS"->"bus"
   "RAIL","TRAIN","SUBWAY","METRO","MONORAIL"->"train"
   else->"pt"
  }
  val line=transit?.optString("line")?.trim()?.takeUnless {it.isBlank()||it=="null"}
  TransitService(type,line)
 }
 .fold(emptyList()) {services,service->if(services.lastOrNull()==service)services else services+service}
