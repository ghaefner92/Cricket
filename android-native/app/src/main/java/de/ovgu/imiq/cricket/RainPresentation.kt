package de.ovgu.imiq.cricket

import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Query reception and dateModified cannot establish the age of a sensor report. */
fun displayedRain(readings:List<JSONObject>):JSONObject? = readings.filter {
 it.optString("source_entity_id")=="Sensor:Weather:Winfred" &&
 it.optString("variable")=="rain" && it.optString("status")=="OBSERVED" &&
 it.optJSONObject("source_metadata")?.optString("source_attribute")=="rainIntensity" &&
 it.optJSONObject("source_metadata")?.opt("normalization_eligible")!=false &&
 it.optString("unit").lowercase() in listOf("mm/h","mm/hour","mm/hr") &&
 it.number("numeric_value")?.let {v->v.isFinite()&&v>=0&&v<=450}==true
}.maxByOrNull {rainReportInstant(it)?.toEpochMilli()?:rainKnownInstant(it)?.toEpochMilli()?:Long.MIN_VALUE}

/** Published clock retained as a last known timestamp, never used for freshness. */
fun rainKnownInstant(reading:JSONObject):Instant? {
 val metadata=reading.optJSONObject("source_metadata")
 val stamp=metadata?.optString("parsed_datetime")?.takeIf {it.isNotBlank()}
  ?:metadata?.optString("parsed_local_datetime")?.takeIf {it.isNotBlank()}
  ?:reading.optString("timestamp").takeIf {it.isNotBlank()}?:return null
 return runCatching {Instant.parse(stamp)}.getOrNull()
  ?:runCatching {java.time.OffsetDateTime.parse(stamp).toInstant()}.getOrNull()
  ?:runCatching {LocalDateTime.parse(stamp).atZone(ZoneId.of("Europe/Berlin")).toInstant()}.getOrNull()
}

fun rainKnownTime(reading:JSONObject?,de:Boolean):String {
 val stamp=reading?.let {rainKnownInstant(it)?:rainReportInstant(it)}?:return ""
 return DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.of("Europe/Berlin")).format(stamp)
}

fun rainReportInstant(reading:JSONObject):Instant? {
 val metadata=reading.optJSONObject("source_metadata")?:return null
 if(metadata.optString("last_report_time_provenance")!="EXPLICIT_ATTRIBUTE_RECEIVED_AT")return null
 return runCatching {Instant.parse(metadata.getString("last_report_at"))}.getOrNull()
}

fun rainReportAge(reading:JSONObject?,de:Boolean,now:Instant=Instant.now()):String {
 val report=reading?.let {rainReportInstant(it)}
 val minutes=report?.let {java.time.Duration.between(it,now).toMinutes()}
 return if(minutes==null||report?.isAfter(now)==true) {
  if(de)"Alter des Berichts unbekannt"else "Report age unknown"
 }else if(de)"Bericht vor $minutes Min."else "Reported $minutes min ago"
}

/** Only the selected Winfred intensity may appear in any weather visualization. */
fun weatherDisplayReadings(readings:List<JSONObject>):List<JSONObject> =
 readings.filter {it.optString("variable") !in listOf("uvIndex","rain","rainIntensity","cumulativeRainfall","rainHourly","rainDaily","rainWeekly","rainMonthly","rainYearly")}+
 listOfNotNull(displayedRain(readings))
