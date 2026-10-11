package de.ovgu.imiq.cricket

import android.content.pm.ActivityInfo
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Layout and actions with synthetic routes; never saves choices or journeys. */
@RunWith(AndroidJUnit4::class)
class RouteSummaryDeviceTest {
 private val instrumentation=InstrumentationRegistry.getInstrumentation()
 private val context=instrumentation.targetContext
 private val time=1791555200000L
 private fun card(mode:String):RouteCard {
  val columns=needKeys+listOf("action:car","action:bike","action:pt","action:walk")
  val matrix=JSONArray(columns.map {JSONArray(columns.map {0.0})})
  val weights=mapOf("pro_env" to .9,"privacy" to .8,"autonomy" to .7,"comfort" to -.9,"speed" to -.8,"cost" to -.7)
  weights.forEach {(need,w)->matrix.getJSONArray(columns.indexOf("action:$mode")).put(columns.indexOf(need),w)}
  val route=obj("rank" to 1,"mode_key" to mode,"summary" to obj("duration_seconds" to 1249.0,"distance_meters" to 1733.0))
  if(mode=="pt")route.put("transit",obj("start_time_ms" to time+300000,"end_time_ms" to time+1800000)).put("legs",JSONArray().put(obj("mode" to "pt","transit" to obj("source_mode" to "TRAM","line" to "6","realtime" to true,"departure_delay_seconds" to 120))).put(obj("mode" to "pt","transit" to obj("source_mode" to "BUS","line" to "48"))))
  return RouteCard(route,null,obj("hotco" to obj("simulation_evidence" to obj("state_columns" to JSONArray(columns),"topology" to matrix),
   "node_activations" to obj("needs" to obj("final" to obj(*needKeys.map {it to .5}.toTypedArray()))))))
 }
 private fun find(prefix:String):AccessibilityNodeInfo? {
  if(android.os.Build.VERSION.SDK_INT>=33)instrumentation.uiAutomation.clearCache()
  fun visit(n:AccessibilityNodeInfo):AccessibilityNodeInfo? {
   if(n.contentDescription?.toString()?.lineSequence()?.any {it.startsWith(prefix)}==true||n.text?.toString()==prefix)return n
   for(i in 0 until n.childCount)n.getChild(i)?.let {visit(it)}?.let {return it}
   return null
  }
  return instrumentation.uiAutomation.rootInActiveWindow?.let(::visit)
 }
 private fun await(prefix:String):AccessibilityNodeInfo {
  val until=android.os.SystemClock.elapsedRealtime()+5000
  while(android.os.SystemClock.elapsedRealtime()<until){find(prefix)?.let {return it};Thread.sleep(50)}
  snapshot("route-summary-missing.png")
  error("Missing summary element: $prefix")
 }
 private fun click(prefix:String) {
  var n=await(prefix);while(!n.isClickable)n=n.parent?:error("Not clickable: $prefix")
  assertTrue(n.performAction(AccessibilityNodeInfo.ACTION_CLICK));Thread.sleep(200)
 }
 private fun snapshot(name:String) {
  val bitmap=instrumentation.uiAutomation.takeScreenshot()
  context.cacheDir.resolve(name).outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
  bitmap.recycle()
 }
 private fun verifyVisible() {
  val bounds=Rect();await("Test route summary").getBoundsInScreen(bounds)
  val expected=listOf("Reduce my travel emissions · Simulated support","Have more space and privacy · Simulated support","Stay flexible and independent · Simulated support",
   "Travel more comfortably · Simulated obstacle","Save travel time · Simulated obstacle","Spend less on travel · Simulated obstacle",
   "Distance:","Arrival ≈:","Your feeling about")
  expected.forEach {label->val n=await(label);val child=Rect();n.getBoundsInScreen(child);assertTrue("$label clipped: $child outside $bounds",!child.isEmpty&&bounds.contains(child));assertTrue(n.isVisibleToUser)}
  assertTrue(bounds.height()<=context.resources.displayMetrics.heightPixels*.15+3)
 }
 private fun exercise(landscape:Boolean) {
  val scenario=ActivityScenario.launch<MainActivity>(android.content.Intent(context,MainActivity::class.java))
  var details=0;var starts=0;var finishes=0;var pauses=0;var resumes=0
  try {
   scenario.onActivity {it.requestedOrientation=if(landscape)ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT}
   Thread.sleep(650)
   val widths=if(landscape)listOf(720)else listOf(320,360)
   for(width in widths)for(mode in modes) {
    val c=card(mode)
    scenario.onActivity {activity->
     val model=ViewModelProvider(activity)[CricketModel::class.java]
     val saved=model.passport()?.optJSONObject("response")?.optJSONObject("cognitive_passport")
     activity.setContent {
      CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,1.3f)) {
       CricketTheme(false){Box(Modifier.fillMaxSize().safeDrawingPadding(),contentAlignment=Alignment.BottomStart) {
        Box(Modifier.width(width.dp).semantics {contentDescription="Test route summary"}) {
         RouteSummaryPanel(model,c,time,false,onDetails={details++},passportSnapshot=saved){RoutePlayButton("Test start journey"){starts++}}
        }
       }}
      }
     }
    }
    await("Test start journey");verifyVisible()
    await(if(mode=="pt")"From now:"else"Travel time:")
    if(mode=="pt")await("Live departure delay")
    snapshot("route-summary-${if(landscape)"landscape"else "portrait"}-$width-$mode.png")
    click("Route details");click("Test start journey")
    assertEquals(starts,details)
    if(mode=="bike"&&width==widths.first()){
     click("Simulated goals:");await("All simulated connections");await("Supports · 0.4500")
     var scroller=await("All simulated connections")
     while(!scroller.isScrollable)scroller=scroller.parent?:error("Goal details must scroll")
     repeat(4){if(find("Opposes · -0.4500")==null){scroller.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);Thread.sleep(150)}}
     await("Opposes · -0.4500");snapshot("route-summary-goal-details.png");click("Close")
    }
   }
   for(paused in listOf(false,true))scenario.onActivity {activity->
    val model=ViewModelProvider(activity)[CricketModel::class.java]
    val saved=model.passport()?.optJSONObject("response")?.optJSONObject("cognitive_passport")
    activity.setContent {CricketTheme(false){Box(Modifier.fillMaxSize().safeDrawingPadding(),contentAlignment=Alignment.BottomStart){
     Box(Modifier.width(widths.first().dp).semantics {contentDescription="Test route summary"}) {
      RouteSummaryPanel(model,card("bike"),time,false,onDetails={finishes++},elapsed="01:23",controls=true,statusLabel=if(paused)"Paused · GPS off"else"Journey in progress",passportSnapshot=saved) {
       if(paused)RoutePlayButton("Test resume"){resumes++}else RoutePauseButton("Test pause"){pauses++}
      }
     }
    }}}
   }.also {
    await("Elapsed: 01:23");verifyVisible();click(if(paused)"Test resume"else"Test pause");click("Stop journey · Experience")
   }
   assertEquals(2,finishes);assertEquals(1,pauses);assertEquals(1,resumes)
  }finally{scenario.onActivity {it.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED};scenario.close()}
 }
 @Test fun portraitShowsSixGoalsMetricsFaceAndControlsAtNarrowWidthAndLargeFont()=exercise(false)
 @Test fun landscapeKeepsAllElementsWithinFifteenPercentPanel()=exercise(true)
}
