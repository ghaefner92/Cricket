package de.ovgu.imiq.cricket

import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real editor with local state; does not save a user's priorities. */
@RunWith(AndroidJUnit4::class)
class WeeklyPrioritiesDeviceTest {
 private val instrumentation=InstrumentationRegistry.getInstrumentation()
 private fun find(label:String):AccessibilityNodeInfo? {
  if(android.os.Build.VERSION.SDK_INT>=33)instrumentation.uiAutomation.clearCache()
  fun visit(node:AccessibilityNodeInfo):AccessibilityNodeInfo? {
   if(node.contentDescription?.toString()==label||node.text?.toString()?.lineSequence()?.firstOrNull()==label)return node
   for(i in 0 until node.childCount)node.getChild(i)?.let {visit(it)}?.let {return it}
   return null
  }
  return instrumentation.uiAutomation.rootInActiveWindow?.let(::visit)
 }
 private fun awaitNode(label:String):AccessibilityNodeInfo {
  val until=android.os.SystemClock.elapsedRealtime()+5000
  while(android.os.SystemClock.elapsedRealtime()<until){find(label)?.let {return it};Thread.sleep(60)}
  screenshot("daily-editor-control-missing.png")
  error("Control not visible: $label")
 }
 private fun click(label:String){
  var node=awaitNode(label)
  while(!node.isClickable)node=node.parent?:error("Control not clickable: $label")
  assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK));Thread.sleep(180)
 }
 private fun controlEnabled(label:String):Boolean {
  var node=awaitNode(label)
  while(!node.isClickable)node=node.parent?:return node.isEnabled
  return node.isEnabled
 }
 private fun hasReaction(state:String):Boolean {
  if(android.os.Build.VERSION.SDK_INT>=33)instrumentation.uiAutomation.clearCache()
  fun visit(node:AccessibilityNodeInfo):Boolean {
   if(node.stateDescription?.toString()==state)return true
   for(i in 0 until node.childCount)if(node.getChild(i)?.let(::visit)==true)return true
   return false
  }
  return instrumentation.uiAutomation.rootInActiveWindow?.let(::visit)==true
 }
 private fun awaitReaction(state:String){
  val until=android.os.SystemClock.elapsedRealtime()+650
  while(android.os.SystemClock.elapsedRealtime()<until){if(hasReaction(state))return;Thread.sleep(35)}
  error("Reaction not visible: $state")
 }
 private fun screenshot(name:String){
  val image=instrumentation.uiAutomation.takeScreenshot()
  instrumentation.targetContext.cacheDir.resolve(name).outputStream().use {image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
  image.recycle()
 }
 private fun scrollList(forward:Boolean):Boolean {
  val bounds=android.graphics.Rect()
  instrumentation.uiAutomation.rootInActiveWindow?.getBoundsInScreen(bounds)?:return false
  val x=bounds.centerX();val high=(bounds.height()*.4f).toInt();val low=(bounds.height()*.7f).toInt()
  val from=if(forward)low else high;val to=if(forward)high else low
  instrumentation.uiAutomation.executeShellCommand("input swipe $x $from $x $to 250").use {
   android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
  }
  Thread.sleep(300);return true
 }
 @Test fun independentRatingsKeepCompanionFeedbackAndBounds(){
  val context=instrumentation.targetContext
  val scenario=ActivityScenario.launch<MainActivity>(android.content.Intent(context,MainActivity::class.java))
  var current by mutableStateOf(goalKeys.associateWith {if(it=="env")5 else 0})
  var saved=0
  var appearance by mutableStateOf("robot")
  var animated by mutableStateOf(true)
  try {
   scenario.onActivity {activity->activity.setContent {
    CricketTheme(false){
     Box(Modifier.safeDrawingPadding()){WeeklyPrioritiesEditor(current,false,onAdjust={key,delta->
      val next=(current[key]?:0)+delta
      if(next in 1..7)current=current+(key to next)
     },onSave={saved++},onBack={},appearance=appearance,animated=animated)}
    }
   }}
   awaitNode("10 left to rate");assertFalse(controlEnabled("Save priorities"))
   repeat(2){click("Increase rating for Lower emissions")}
   awaitReaction("Approval")
   screenshot("weekly-priorities-approval.png")
   awaitNode("Your companion at Lower emissions")
   awaitNode("10 left to rate")
   assertEquals(7,current["env"])
   assertFalse(awaitNode("Increase rating for Lower emissions").isEnabled);assertTrue(awaitNode("Increase rating for Move more").isEnabled)
   // A second rating remains adjustable even when the first is at seven.
   click("Decrease rating for Lower emissions")
   awaitReaction("Negation, arms crossed")
   screenshot("weekly-priorities-negation.png")
   awaitNode("10 left to rate")
   click("Increase rating for Move more")
   awaitNode("Your companion at Move more")
   assertNull(find("Your companion at Lower emissions"))
   awaitNode("9 left to rate")
   assertEquals(6,current["env"]);assertEquals(1,current["health_activity"]);assertFalse(awaitNode("Decrease rating for Move more").isEnabled)
   awaitNode("Lower emissions: 6 of 7")
   awaitNode("Move more: 1 of 7")
   click("Lower emissions")
   awaitNode("Got it");click("Got it")
   awaitNode("Save priorities")
   repeat(5){if(find("Traffic safety")==null)assertTrue(scrollList(true))}
   awaitNode("Traffic safety")
   awaitNode("9 left to rate");awaitNode("Save priorities")
   repeat(5){scrollList(false)}
   awaitNode("Lower emissions")
   for(kind in listOf("explorer","creature","naturalist")){
    scenario.onActivity {appearance=kind}
    click("Decrease rating for Lower emissions")
    awaitReaction("Negation, arms crossed")
    screenshot("weekly-priorities-$kind-negation.png")
    click("Increase rating for Lower emissions")
    awaitReaction("Approval")
   }
   scenario.onActivity {animated=false}
   click("Decrease rating for Lower emissions")
   assertFalse(hasReaction("Negation, arms crossed"))
   click("Increase rating for Lower emissions")
   assertFalse(hasReaction("Approval"))
   assertEquals(6,current["env"])
   scenario.onActivity {current=goalKeys.associateWith {current[it]?.takeIf {v->v in 1..7}?:1}}
   awaitNode("All priorities rated")
   assertTrue(controlEnabled("Save priorities"))
   screenshot("weekly-priorities-preview.png")
   click("Save priorities");assertEquals(1,saved)
  }finally{scenario.close()}
 }
}
