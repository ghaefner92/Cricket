package de.ovgu.imiq.cricket

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import java.util.UUID

fun obj(vararg values: Pair<String,Any?>) = JSONObject().apply { values.forEach { put(it.first,it.second ?: JSONObject.NULL) } }
fun JSONObject.objects(key:String):List<JSONObject> = optJSONArray(key).objects()
fun JSONArray?.objects():List<JSONObject> = if(this==null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
fun JSONObject.copyJson() = JSONObject(toString())
fun JSONObject.number(key:String):Double? = if(isNull(key)) null else optDouble(key).takeIf { it.isFinite() }
fun now() = Instant.now().toString()
fun week() = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()
const val PASSPORT = "imiq.experimental.cognitive-passport.v1"
const val COMPANION = "imiq.experimental.companion.v1"
const val SETTINGS = "cricket.settings.v1"
const val ONBOARDING = "imiq.experimental.onboarding-step.v1"
val modes = listOf("walk","bike","pt","car")
val goalKeys = listOf("env","health_activity","cost","time","comfort_physical","reliable","flex","safety_crime","health_infection","crowding","safety_accident")
val needKeys = listOf("pro_env","physical","cost","speed","comfort","reliable","autonomy","safety_crime","health_infection","privacy","safety_accident")
fun modelMode(value:String) = when(value) { "foot"->"walk";"car_driver"->"car";"pt_bus_tram"->"pt";else->value }

class Store(context:Context):SQLiteOpenHelper(context,"cricket-native-v1.db",null,1) {
 override fun onCreate(db:SQLiteDatabase) {
  db.execSQL("CREATE TABLE kv (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
  db.execSQL("CREATE TABLE searches (id TEXT PRIMARY KEY, created TEXT NOT NULL, value TEXT NOT NULL)")
 }
 override fun onUpgrade(db:SQLiteDatabase,old:Int,new:Int) = Unit
 fun get(key:String):String? = readableDatabase.rawQuery("SELECT value FROM kv WHERE key=?",arrayOf(key)).use { if(it.moveToFirst())it.getString(0) else null }
 fun json(key:String):JSONObject? = runCatching { get(key)?.let(::JSONObject) }.getOrNull()
 fun put(key:String,value:Any) { writableDatabase.execSQL("INSERT OR REPLACE INTO kv VALUES (?,?)",arrayOf(key,value.toString())) }
 fun remove(key:String) { writableDatabase.execSQL("DELETE FROM kv WHERE key=?",arrayOf(key)) }
 fun storage():JSONObject = JSONObject().apply { readableDatabase.rawQuery("SELECT key,value FROM kv",null).use { while(it.moveToNext())put(it.getString(0),it.getString(1)) } }
 fun passport():JSONObject? = json(PASSPORT)?.takeIf { validPassport(it) }
 fun confirmedSnapshot(record:JSONObject):JSONObject? {
  record.optJSONObject("confirmed_snapshot")?.let { return it }
  val reference=record.optJSONObject("passport_reference")?:return null
  val values=storage()
  return values.keys().asSequence().filter { it==PASSPORT||it.startsWith("$PASSPORT.archive.") }.mapNotNull { key->json(key) }.firstOrNull { item->
   val lineage=item.optJSONObject("response")?.optJSONObject("cognitive_passport")?.optJSONObject("lineage")
   lineage?.optString("passport_id")==reference.optString("passport_id")&&lineage?.optInt("revision")==reference.optInt("revision")&&item.optString("fingerprint")==reference.optString("fingerprint")
  }
 }
 fun history():List<JSONObject> = readableDatabase.rawQuery("SELECT value FROM searches ORDER BY created DESC",null).use { c->buildList { while(c.moveToNext())add(JSONObject(c.getString(0))) } }
 fun record(id:String):JSONObject? = readableDatabase.rawQuery("SELECT value FROM searches WHERE id=?",arrayOf(id)).use { if(it.moveToFirst())JSONObject(it.getString(0)) else null }
 fun saveRecord(record:JSONObject) { require(validRecord(record));writableDatabase.execSQL("INSERT OR REPLACE INTO searches VALUES (?,?,?)",arrayOf(record.getString("search_id"),record.getString("created_at"),record.toString())) }
 fun export():JSONObject = obj("schema" to "cricket-backup-v1","exported_at" to now(),"storage" to storage(),"simulations" to JSONArray(history()))
 fun importBackup(backup:JSONObject,replace:Boolean=true) {
  require(backup.optString("schema")=="cricket-backup-v1") { "Unsupported backup" }
  val values=backup.getJSONObject("storage"); val records=backup.getJSONArray("simulations").objects()
  require(records.size==backup.getJSONArray("simulations").length()&&records.all(::validRecord)) { "Invalid journey history" }
  if(values.has(PASSPORT))require(validPassport(JSONObject(values.getString(PASSPORT)))) { "Invalid Passport" }
  require(values.keys().asSequence().all { allowedKey(it) && values.opt(it) is String }) { "Invalid backup keys" }
  val db=writableDatabase;db.beginTransaction()
  try {
   records.forEach { r->record(r.getString("search_id"))?.let { require(canonical(it)==canonical(r)) { "Conflicting journey ID" } } }
   val previous=passport()
   if(replace) {
    val preserved=storage();db.execSQL("DELETE FROM kv")
    preserved.keys().asSequence().filter { it.startsWith("$PASSPORT.archive.") }.forEach { key->
     if(values.has(key))require(values.getString(key)==preserved.getString(key)) { "Conflicting Passport archive" }
     put(key,preserved.getString(key))
    }
   }
   values.keys().forEach { key->put(key,values.getString(key)) }
   if(previous!=null&&previous.toString()!=get(PASSPORT))archive(previous)
   records.forEach(::saveRecord)
   db.setTransactionSuccessful()
  } finally { db.endTransaction() }
 }
 fun archive(item:JSONObject) { val id=item.optJSONObject("response")?.optJSONObject("cognitive_passport")?.optJSONObject("lineage")?.optString("passport_id");if(!id.isNullOrEmpty())put("$PASSPORT.archive.$id",item) }
 fun reset() { val db=writableDatabase;db.beginTransaction();try { db.execSQL("DELETE FROM searches");db.execSQL("DELETE FROM kv");db.setTransactionSuccessful() } finally { db.endTransaction() } }
 fun clearHistory() { writableDatabase.execSQL("DELETE FROM searches") }
}
fun allowedKey(key:String) = key.startsWith("imiq.experimental.") || key in listOf("cricket.settings.v1","cricket.recent-places.v1","cricket.chosen-journey.v1","cricket.passport-renewal.v1","cricket.connection.v1")
fun validPassport(item:JSONObject):Boolean {
 val p=item.optJSONObject("response")?.optJSONObject("cognitive_passport")?:return false
 val profile=p.optJSONObject("profile")?:return false
 return item.optInt("version")==1&&item.opt("fingerprint") is String&&p.optString("schema_version")=="2.0"&&p.optJSONObject("lineage")?.opt("passport_id") is String&&profile.optJSONObject("needs")!=null&&modes.all { profile.optJSONObject("beliefs")?.optJSONObject(it)!=null&&profile.optJSONObject("availability")?.opt(it) is Boolean }
}
fun validRecord(record:JSONObject):Boolean {
 val id=record.optString("search_id");val result=record.optJSONObject("result");val choices=record.optJSONArray("choices")?:return false
 return record.optInt("version")==1&&id.isNotEmpty()&&record.optString("created_at").isNotEmpty()&&record.optString("status") in listOf("pending","complete","failed")&&record.optJSONObject("request")?.optString("search_id")==id&&
  (record.optString("status")!="complete"||(result?.optString("search_id")==id&&result.optString("schema_version")=="routed-contextual-deliberation-v1"&&result.optJSONObject("routing")?.optJSONArray("routes")!=null&&result.optJSONArray("route_audit")!=null))&&
  choices.objects().size==choices.length()&&choices.objects().all { choice->result?.optJSONObject("routing")?.objects("routes")?.any { it.optInt("rank")==choice.optInt("routing_rank")&&it.optString("mode_key")==choice.optString("mode_key")&&it.optBoolean("available") }==true }
}
fun canonical(value:Any?):String = when(value) {
 is JSONObject->value.keys().asSequence().sorted().joinToString(prefix="{",postfix="}") { JSONObject.quote(it)+":"+canonical(value.opt(it)) }
 is JSONArray->(0 until value.length()).joinToString(prefix="[",postfix="]") { canonical(value.opt(it)) }
 is String->JSONObject.quote(value)
 else->value?.toString()?:"null"
}

class Api(private val store:Store) {
 fun base():String = normalizeBase(store.get("cricket.connection.v1")?:"http://localhost:8077")
 fun normalizeBase(text:String):String {
  val url=URI(text.trim());require(url.scheme in listOf("https","http")&&!url.host.isNullOrEmpty()&&url.userInfo==null&&url.query==null&&url.fragment==null) { "Enter a valid backend URL" };return url.toString().trimEnd('/')
 }
 suspend fun call(path:String,payload:JSONObject?=null,timeout:Int=20000):JSONObject = withContext(Dispatchers.IO) {
  val conn=URL(base()+path).openConnection() as HttpURLConnection
  try {
   conn.connectTimeout=15000;conn.readTimeout=timeout;conn.setRequestProperty("Accept","application/json")
   if(payload!=null){conn.requestMethod="POST";conn.doOutput=true;conn.setRequestProperty("Content-Type","application/json");conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }}
   val status=conn.responseCode;val text=(if(status in 200..299)conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
   ensureActive()
   val data=runCatching { JSONObject(text) }.getOrNull()
   if(status !in 200..299)throw IllegalStateException(data?.optString("message")?.takeIf { it.isNotEmpty() }?:"Server error $status")
   data?:throw IllegalStateException("Invalid server response")
  } finally { conn.disconnect() }
 }
}
