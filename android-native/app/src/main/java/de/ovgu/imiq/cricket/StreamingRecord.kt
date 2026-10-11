package de.ovgu.imiq.cricket

import java.io.Reader
import java.io.PushbackReader
import org.json.*

/** Read SQLite chunks directly; discarded branches never become strings or JSON trees. */
internal fun readStreamingRecord(source:Reader,research:Boolean=false,ui:Boolean=false):JSONObject {
 val reader=PushbackReader(source.buffered(),1)
 fun next():Int {var c=reader.read();while(c>=0&&c.toChar().isWhitespace())c=reader.read();return c}
 fun quoted(first:Int,retain:Boolean):String {
  check(first==34){"Expected JSON string"}
  val out=if(retain)StringBuilder().append('"')else null
  var escaped=false
  while(true){val n=reader.read();check(n>=0){"Incomplete JSON string"};val c=n.toChar();out?.append(c);if(!escaped&&c=='"')break;escaped=if(escaped)false else c=='\\'}
  return out?.toString().orEmpty()
 }
 fun skip(first:Int) {
  if(first==34){quoted(first,false);return}
  if(first==123||first==91){var depth=1;while(depth>0){val n=reader.read();check(n>=0){"Incomplete JSON value"};when(n){34->quoted(n,false);123,91->depth++;125,93->depth--}};return}
  var n=reader.read();while(n>=0&&n!=44&&n!=125&&n!=93&&!n.toChar().isWhitespace())n=reader.read();if(n>=0)reader.unread(n)
 }
 fun retain(path:String):Boolean {
  if(ui&&path=="d_day_forecast.request")return false
  if(!research)return true
  if(path=="result"||path.startsWith("result."))return false
  if(path.startsWith("d_day_forecast.")&&path!="d_day_forecast.status")return false
  if(path.startsWith("journeys[].tracking"))return false
  return true
 }
 fun value(first:Int,path:String):Any {
  check(first>=0){"Incomplete JSON record"}
  return when(first) {
   123->{val obj=JSONObject();var n=next();if(n!=125){while(true){val key=JSONTokener(quoted(n,true)).nextValue() as String;check(next()==58);val child=if(path.isEmpty())key else "$path.$key";val token=next();if(retain(child))obj.put(key,value(token,child))else skip(token);n=next();if(n==125)break;check(n==44);n=next()}};obj}
   91->{val array=JSONArray();var n=next();if(n!=93){while(true){array.put(value(n,"$path[]"));n=next();if(n==93)break;check(n==44);n=next()}};array}
   34->JSONTokener(quoted(first,true)).nextValue()
   else->{val out=StringBuilder().append(first.toChar());var n=reader.read();while(n>=0&&n!=44&&n!=125&&n!=93&&!n.toChar().isWhitespace()){out.append(n.toChar());n=reader.read()};if(n>=0)reader.unread(n);JSONTokener(out.toString()).nextValue()}
  }
 }
 val result=value(next(),"") as JSONObject
 check(next()==-1){"Trailing record data"}
 return result
}
