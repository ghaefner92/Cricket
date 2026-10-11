package de.ovgu.imiq.cricket

import android.content.pm.ActivityInfo
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic drafts: exercises the actual editor without overwriting the user's Passport. */
@RunWith(AndroidJUnit4::class)
class ValenceEditorDeviceTest {
 private val instrumentation=InstrumentationRegistry.getInstrumentation()
 private val context=instrumentation.targetContext
 private fun find(label:String):AccessibilityNodeInfo? {
  if(android.os.Build.VERSION.SDK_INT>=33)instrumentation.uiAutomation.clearCache()
  fun visit(node:AccessibilityNodeInfo):AccessibilityNodeInfo? {
   if(node.text?.toString()==label||node.contentDescription?.toString()?.lineSequence()?.any {it==label}==true)return node
   for(i in 0 until node.childCount)node.getChild(i)?.let(::visit)?.let {return it}
   return null
  }
  return instrumentation.uiAutomation.rootInActiveWindow?.let(::visit)
 }
 private fun await(label:String):AccessibilityNodeInfo {
  val until=android.os.SystemClock.elapsedRealtime()+4000
  while(android.os.SystemClock.elapsedRealtime()<until){find(label)?.let{return it};Thread.sleep(50)}
  error("Missing editor control: $label")
 }
 private fun click(label:String) {
  var node=await(label);while(!node.isClickable)node=node.parent?:error("Not clickable: $label")
  assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK));Thread.sleep(80)
 }
 private fun sliderNode(label:String):AccessibilityNodeInfo {
  var node=await(label);while(node.rangeInfo==null)node=node.parent?:error("No range for $label")
  return node
 }
 private fun avatarCount():Int {
  fun count(n:AccessibilityNodeInfo):Int = (if(n.contentDescription?.toString()?.lineSequence()?.any {it.startsWith("Avatar with transport ")}==true)1 else 0)+(0 until n.childCount).sumOf {n.getChild(it)?.let(::count)?:0}
  return instrumentation.uiAutomation.rootInActiveWindow?.let(::count)?:0
 }
 private fun screenshot(name:String) {
  val bitmap=instrumentation.uiAutomation.takeScreenshot()
  context.cacheDir.resolve(name).outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
 }
 private fun assertFeeling(label:String,expected:String) {
  val until=android.os.SystemClock.elapsedRealtime()+3000
  while(android.os.SystemClock.elapsedRealtime()<until) {
   if(find(label)!=null&&sliderNode(label).stateDescription?.toString()==expected)return
   Thread.sleep(50)
  }
  screenshot("valence-failure.png")
  assertEquals(expected,sliderNode(label).stateDescription?.toString())
 }
 @Test fun allModesAndCompanionsReactAcrossTheSevenValues()=exercise(false)
 @Test fun reducedMotionAndLargeGermanTextRemainUsable()=exercise(true)
 private fun exercise(reduced:Boolean) {
  val scenario=ActivityScenario.launch<MainActivity>(android.content.Intent(context,MainActivity::class.java))
  try {
   scenario.onActivity {it.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT};Thread.sleep(600)
   val appearances=if(reduced)listOf("naturalist")else listOf("robot","explorer","creature","naturalist")
   for(mode in modes)for(appearance in appearances) {
    val recorded=java.util.concurrent.CopyOnWriteArrayList<Int>()
    val navigation=java.util.concurrent.CopyOnWriteArrayList<String>()
    scenario.onActivity {activity->activity.setContent {
     val density=LocalDensity.current
     CompositionLocalProvider(LocalDensity provides Density(density.density,if(reduced)1.3f else 1f)) {
      CricketTheme(reduced) {key("$mode-$appearance-$reduced") {
       var rating by remember {mutableStateOf<Int?>(null)}
       Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
        PixelCard(Modifier.width(320.dp)) {ValenceEditor(appearance,mode,rating,!reduced,reduced,onPrevious={navigation.add("previous")},onNext={navigation.add("next")}){rating=it;recorded.add(it)}}
       }
      }}
     }
    }}
    val slider=if(reduced)"Gefühlsskala von minus 3 bis plus 3"else"Feeling scale from minus 3 to plus 3"
    val down=if(reduced)"Gefühl verringern"else"Decrease feeling"
    val up=if(reduced)"Gefühl erhöhen"else"Increase feeling"
    assertFeeling(slider,valenceFeeling(null,reduced))
    click(if(reduced)"Vorheriges Verkehrsmittel"else"Previous mode")
    click(if(reduced)"Nächstes Verkehrsmittel"else"Next mode")
    assertEquals(listOf("previous","next"),navigation.toList())
    assertTrue("Navigation must not answer a question",recorded.isEmpty())
    val heartDown=if(reduced)"Herz: Gefühl um eine Stufe verringern"else"Heart: decrease feeling by one level"
    val heartUp=if(reduced)"Herz: Gefühl um eine Stufe erhöhen"else"Heart: increase feeling by one level"
    assertNull("Redundant minus button",find(down));assertNull("Redundant plus button",find(up))
    click(heartDown);assertEquals(-1,recorded.last())
    click(heartUp);assertEquals(0,recorded.last())
    click(heartDown);click(heartDown);click(heartDown)
    assertEquals(listOf(-1,0,-1,-2,-3),recorded.toList())
    for(value in -3..3) {
     if(value> -3)click(heartUp)
     val feeling=valenceFeeling(value,reduced)
     assertFeeling(slider,feeling)
     val pet=when(appearance){"robot"->"drone";"explorer"->"spider";"creature"->"spirit";else->"puppy"}
     for(label in listOf("Avatar with transport $mode: $feeling","Companion $pet: $feeling")) {
      val n=await(label);val rect=Rect();n.getBoundsInScreen(rect);assertTrue(n.isVisibleToUser&&!rect.isEmpty)
     }
     assertNull("Standalone avatar must not return",find("Avatar: $feeling"))
     assertNull("Central pet must not return",find("Pet: $feeling"))
     assertEquals("Exactly one avatar scene",1,avatarCount())
     if(!reduced&&value in listOf(-3,3)&&mode=="walk"){Thread.sleep(240);screenshot("valence-$appearance-walk-$value.png")}
     if(!reduced&&mode=="bike"&&value in listOf(-3,3)) {
      screenshot("valence-$appearance-bike-$value-a.png")
      Thread.sleep(450)
      screenshot("valence-$appearance-bike-$value-b.png")
     }
     if(!reduced&&appearance=="naturalist"&&value in listOf(-3,3)){Thread.sleep(240);screenshot("valence-$mode-$value.png")}
    }
    var upNode:AccessibilityNodeInfo?=await(heartUp);var disabled=false
    while(upNode!=null){if(!upNode.isEnabled){disabled=true;break};upNode=upNode.parent}
    assertTrue("Positive heart must be disabled at +3",disabled)
    assertEquals(listOf(-1,0,-1,-2,-3)+(-2..3).toList(),recorded.toList())
    click(heartDown);assertEquals(2,recorded.last())
    val args=Bundle().apply {putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,0f)}
    assertTrue(sliderNode(slider).performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id,args));Thread.sleep(150)
    assertEquals(0,recorded.last())
    click(heartDown);assertEquals(-1,recorded.last())
    click(if(reduced)"Neutral wählen"else"Choose neutral");assertEquals(0,recorded.last())
    if(reduced)screenshot("valence-$mode-reduced-german.png")
    if(appearance=="naturalist") {
     click(if(reduced)"Über diese Szene"else"About this scene")
     await(if(reduced)"Mehr erfahren"else"Learn more")
     click(if(reduced)"Schließen"else"Close")
     assertEquals(0,recorded.last())
    }
   }
  }finally {scenario.onActivity {it.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED};scenario.close()}
 }
}
