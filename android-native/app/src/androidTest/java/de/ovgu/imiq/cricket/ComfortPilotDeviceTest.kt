package de.ovgu.imiq.cricket

import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises actual controls with local answers; never confirms a user experience. */
@RunWith(AndroidJUnit4::class)
class ComfortPilotDeviceTest {
 private val instrumentation=InstrumentationRegistry.getInstrumentation()
 private fun find(label:String):AccessibilityNodeInfo? {
  if(android.os.Build.VERSION.SDK_INT>=33)instrumentation.uiAutomation.clearCache()
  fun visit(n:AccessibilityNodeInfo):AccessibilityNodeInfo? {
   if(n.text?.toString()==label||n.contentDescription?.toString()?.lineSequence()?.any {it==label}==true)return n
   for(i in 0 until n.childCount)n.getChild(i)?.let(::visit)?.let {return it};return null
  }
  return instrumentation.uiAutomation.rootInActiveWindow?.let(::visit)
 }
 private fun await(label:String):AccessibilityNodeInfo {
  val until=android.os.SystemClock.elapsedRealtime()+5000
  while(android.os.SystemClock.elapsedRealtime()<until){find(label)?.let{return it};Thread.sleep(50)}
  error("Missing comfort control: $label")
 }
 private fun control(label:String):AccessibilityNodeInfo {var n=await(label);while(!n.isClickable)n=n.parent?:error("Not a button: $label");return n}
 private fun click(label:String){assertTrue(control(label).performAction(AccessibilityNodeInfo.ACTION_CLICK));Thread.sleep(120)}
 private fun scrollTo(label:String) {
  repeat(12) {
   if(find(label)!=null)return
   fun scroll(n:AccessibilityNodeInfo):Boolean {
    if(n.isScrollable&&n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))return true
    for(i in 0 until n.childCount)n.getChild(i)?.let {if(scroll(it))return true};return false
   }
   instrumentation.uiAutomation.rootInActiveWindow?.let(::scroll);Thread.sleep(180)
  }
  await(label)
 }
 @Test fun actualExperienceFormRequiresComfortAndAnExplicitNeutralEmotion() {
  val scenario=ActivityScenario.launch<MainActivity>(android.content.Intent(instrumentation.targetContext,MainActivity::class.java))
  var originalLocale="en"
  try {
   val record=JSONArray(instrumentation.context.assets.open("journey_cases.json").bufferedReader().use {it.readText()}).getJSONObject(0).getJSONObject("record").copyJson()
   val trip=TrackingEngine.create(record,0,false,"2026-10-08T10:01:00Z",1000,1)
   scenario.onActivity {activity->
    val model=ViewModelProvider(activity)[CricketModel::class.java];originalLocale=model.locale;model.locale="en"
    activity.setContent {CricketTheme(false){Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp)){ExperienceForm(model,record,trip)}}}
   }
   click("Yes, as planned");scrollTo("Physical comfort · required");click("4")
   scrollTo("Confirm experience");assertFalse(control("Confirm experience").isEnabled)
   // Scroll back to the affect scale if it lies above the visible confirm button.
   fun up(n:AccessibilityNodeInfo):Boolean {
    if(n.isScrollable&&n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD))return true
    for(i in 0 until n.childCount)n.getChild(i)?.let {if(up(it))return true};return false
   }
   repeat(8){if(find("0")==null){instrumentation.uiAutomation.rootInActiveWindow?.let(::up);Thread.sleep(180)}}
   click("0");scrollTo("Confirm experience");assertTrue(control("Confirm experience").isEnabled)
   // No confirmation is clicked; test answers never reach the participant database.
  }finally{scenario.onActivity {ViewModelProvider(it)[CricketModel::class.java].locale=originalLocale};scenario.close()}
 }
 @Test fun neutralAnswerAndExplicitModeAttributionRemainDistinctFromMissing() {
  val scenario=ActivityScenario.launch<MainActivity>(android.content.Intent(instrumentation.targetContext,MainActivity::class.java))
  var answer:JSONObject?=null;var originalLocale="en"
  try {
   scenario.onActivity {activity->
    val model=ViewModelProvider(activity)[CricketModel::class.java];originalLocale=model.locale;model.locale="en"
    activity.setContent {CricketTheme(false){var state by remember {mutableStateOf<JSONObject?>(null)}
     Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
      PixelCard {ComfortPilotQuestion(model,listOf("walk","pt"),state){state=it;answer=it}}
     }
    }}
   }
   await("Physical comfort · required");assertFalse(control("4").isEnabled);assertNull(answer)
   val names=JSONObject(instrumentation.targetContext.assets.open("catalog.json").bufferedReader().use {it.readText()}).getJSONObject("beliefs").getJSONObject("en").getJSONObject("modes")
   click(names.getString("pt"));assertTrue(control("4").isEnabled);assertNull(answer)
   click("4");await("Neither helped nor hindered");assertEquals(4,answer!!.getInt("rating"))
   assertEquals("pt",answer!!.getString("mode"));assertEquals("user_selected_actual_mode",answer!!.getString("mode_selection"))
   click(names.getString("walk"));assertNull(answer)
   click("7");await("Helped a lot");assertEquals("walk",answer!!.getString("mode"))
   val bitmap=instrumentation.uiAutomation.takeScreenshot()
   instrumentation.targetContext.cacheDir.resolve("comfort-pilot.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
  } finally {scenario.onActivity {ViewModelProvider(it)[CricketModel::class.java].locale=originalLocale};scenario.close()}
 }
}
