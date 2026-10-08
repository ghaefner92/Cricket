package de.ovgu.imiq.cricket

import android.Manifest
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.core.view.WindowInsetsControllerCompat
import org.json.JSONObject

class MainActivity:ComponentActivity() {
 private lateinit var model:CricketModel
 private var exportPayload:JSONObject?=null
 private var migration:WebView?=null
 private val exportFile=registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri->
  if(uri!=null)runCatching { contentResolver.openOutputStream(uri)?.use { it.write((exportPayload?:model.store.export()).toString(2).toByteArray()) } }.onSuccess { model.notice=model.text("File saved","Datei gespeichert") }.onFailure { model.error=it.message }
  exportPayload=null
 }
 private val importFile=registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri->
  if(uri!=null)runCatching { val text=contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }?:error("Cannot read backup");JSONObject(text) }.onSuccess { model.pendingImport=it }.onFailure { model.error=it.message }
 }
 private val locationPermission=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted->
  if(granted.values.any { it })locate() else model.error=model.text("Location permission denied. You can enter an address.","Standortberechtigung verweigert. Du kannst eine Adresse eingeben.")
 }
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState);enableEdgeToEdge();model=ViewModelProvider(this)[CricketModel::class.java]
  WindowInsetsControllerCompat(window,window.decorView).apply { isAppearanceLightStatusBars=false;isAppearanceLightNavigationBars=false }
  setContent { CricketApp(model,onExport={value,name->exportPayload=value;exportFile.launch(name)},onImport={importFile.launch(arrayOf("application/json","text/plain","*/*"))},onLocate={locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))},onRetryMigration={migrateLegacy()}) }
  if(getPreferences(MODE_PRIVATE).getBoolean("native_migrated",false))model.refresh()else migrateLegacy()
 }
 private fun migrateLegacy() {
  migration?.destroy();model.migrationError=null
  migration=WebView(this).apply {
   settings.javaScriptEnabled=true;settings.domStorageEnabled=true
   addJavascriptInterface(object {
    @JavascriptInterface fun complete(text:String) { runOnUiThread {
     runCatching {
      val value=JSONObject(text);if(value.has("error"))error(value.getString("error"))
      val backup=model.store.export();if(backup.getJSONObject("storage").length()>0||backup.getJSONArray("simulations").length()>0)filesDir.resolve("before-native-migration.json").writeText(backup.toString())
      model.store.importBackup(value,false)
      getPreferences(MODE_PRIVATE).edit().putBoolean("native_migrated",true).apply();model.refresh()
     }.onFailure { model.migrationError=it.message }
     migration?.destroy();migration=null
    } }
   },"CricketMigration")
   webViewClient=WebViewClient()
   loadDataWithBaseURL("https://localhost/", """
    <html><body><script>
    (async()=>{try{
      const storage={};for(let i=0;i<localStorage.length;i++){const key=localStorage.key(i);if(key.startsWith('imiq.experimental.')||['cricket.settings.v1','cricket.recent-places.v1','cricket.chosen-journey.v1','cricket.passport-renewal.v1','cricket.connection.v1'].includes(key))storage[key]=localStorage.getItem(key)}
      const simulations=await new Promise((resolve,reject)=>{const r=indexedDB.open('cricket-simulation-history-v1',1);r.onupgradeneeded=()=>r.result.createObjectStore('searches',{keyPath:'search_id'});r.onerror=()=>reject(Error('Cannot open old history'));r.onsuccess=()=>{const db=r.result;const q=db.transaction('searches','readonly').objectStore('searches').getAll();q.onsuccess=()=>{resolve(q.result);db.close()};q.onerror=()=>reject(Error('Cannot read old history'))}});
      CricketMigration.complete(JSON.stringify({schema:'cricket-backup-v1',storage,simulations}));
    }catch(e){CricketMigration.complete(JSON.stringify({error:String(e)}))}})();
    </script></body></html>
   """.trimIndent(),"text/html","UTF-8",null)
  }
 }
 private fun locate(){requestLocation(this,model)}
 override fun onDestroy(){migration?.destroy();migration=null;super.onDestroy()}
}
