package de.ovgu.imiq.cricket

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.io.File

class OriginalPetMotionTest {
 @Test fun allReactionsUseFramesInTheOriginalFullBodyAtlases(){
  val atlas=JSONObject(File("src/main/assets/art/jrpg/companion-intro-atlas.json").readText())
  for((appearance,pet) in listOf("naturalist" to "dogs","explorer" to "spider","robot" to "drone","creature" to "spirit")){
   assertEquals(pet,originalPetKey(appearance))
   val meta=atlas.getJSONObject(pet)
   assertEquals("companion-intro-$pet-v1.png",meta.getString("file"))
   for(reaction in 0..2)for(tick in 0..140){
    val pose=originalPetPose(pet,reaction,tick/10f,true)
    assertTrue(pose.frame in 0 until meta.getJSONArray("frames").length())
    assertTrue(pose.tilt.isFinite()&&pose.lift.isFinite())
   }
   for(reaction in 0..2)assertEquals(originalPetPose(pet,reaction,0f,false),originalPetPose(pet,reaction,20f,false))
  }
 }
 @Test fun commonCropFitsEveryPoseWithoutMutatingOriginalPixels(){
  val pixels=IntArray(40)
  pixels[1*10+1]=-1;pixels[2*10+3]=-1
  pixels[0*10+7]=-1;pixels[3*10+9]=-1
  val original=pixels.copyOf()
  val bounds=originalPetBounds(pixels,10,4,listOf(intArrayOf(0,0,5,4),intArrayOf(5,0,5,4)))
  assertEquals(PetSpriteBounds(1,0,5,4),bounds)
  assertArrayEquals(original,pixels)
  assertThrows(IllegalArgumentException::class.java){originalPetBounds(IntArray(4),2,2,listOf(intArrayOf(0,0,2,2)))}
 }
}
