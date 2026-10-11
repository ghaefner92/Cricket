package de.ovgu.imiq.cricket

import java.io.StringReader
import java.io.Reader
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StreamingRecordTest {
 @Test fun fullReadPreservesNestedDataUnicodeEscapesNumbersAndNull(){
  val source=JSONObject().put("search_id","r").put("text","Straße 🌧 \"quoted\" \\ newline\n").put("null",JSONObject.NULL).put("nested",obj("n" to -2.5e-8,"yes" to true,"list" to org.json.JSONArray().put(1).put(obj("x" to "y")))).toString()
  assertEquals(canonical(JSONObject(source)),canonical(readStreamingRecord(StringReader(source))))
 }
 @Test fun researchProjectionKeepsEvidenceButSkipsRoutingFrozenInputsAndGps(){
  val original=obj("search_id" to "r","confirmed_snapshot" to obj("fingerprint" to "p"),"result" to obj("huge" to true),"d_day_forecast" to obj("status" to "pending","request" to obj("large" to true)),"journeys" to org.json.JSONArray().put(obj("tracking" to obj("points" to org.json.JSONArray().put(1)),"experience" to obj("event_id" to "e"),"plan" to obj("context_at_selection" to obj("snapshot" to obj("rain" to 2))))))
  val actual=readStreamingRecord(StringReader(original.toString()),research=true)
  assertFalse(actual.has("result"));assertEquals("pending",actual.getJSONObject("d_day_forecast").getString("status"));assertFalse(actual.getJSONObject("d_day_forecast").has("request"))
  val trip=actual.objects("journeys").single();assertFalse(trip.has("tracking"));assertEquals("e",trip.getJSONObject("experience").getString("event_id"));assertEquals(canonical(original.getJSONObject("confirmed_snapshot")),canonical(actual.getJSONObject("confirmed_snapshot")));assertEquals(canonical(original.objects("journeys").single().getJSONObject("plan")),canonical(trip.getJSONObject("plan")))
 }
 @Test fun uiProjectionPreservesForecastResultsAndFullRouting(){val raw=obj("result" to obj("routing" to 1),"d_day_forecast" to obj("request" to obj("large" to 1),"result" to obj("predictions" to 2)));val read=readStreamingRecord(StringReader(raw.toString()),ui=true);assertEquals(canonical(raw.getJSONObject("result")),canonical(read.getJSONObject("result")));assertEquals(2,read.getJSONObject("d_day_forecast").getJSONObject("result").getInt("predictions"));assertFalse(read.getJSONObject("d_day_forecast").has("request"))}
 @Test fun oversizedDiscardedStringIsStreamedWithoutAWholeRecordBuffer(){
  val prefix="{\"result\":{\"payload\":\"";val suffix="\"},\"search_id\":\"after\"}";val repeats=30_000_000
  val source=object:Reader(){var pos=0;override fun close(){};override fun read(buffer:CharArray,offset:Int,length:Int):Int{if(pos>=prefix.length+repeats+suffix.length)return -1;var n=0;while(n<length&&pos<prefix.length+repeats+suffix.length){buffer[offset+n]=if(pos<prefix.length)prefix[pos]else if(pos<prefix.length+repeats)'x'else suffix[pos-prefix.length-repeats];pos++;n++};return n}}
  val value=readStreamingRecord(source,research=true);assertEquals("after",value.getString("search_id"));assertFalse(value.has("result"))
 }
 @Test(expected=IllegalStateException::class) fun incompleteRecordFailsRatherThanReturningPartialEvidence(){readStreamingRecord(StringReader("{\"search_id\":\"broken"))}
}
