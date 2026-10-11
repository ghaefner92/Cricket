package de.ovgu.imiq.cricket

import kotlin.math.*

internal fun originalPetKey(appearance:String)=when(appearance){"naturalist"->"dogs";"explorer"->"spider";"creature"->"spirit";else->"drone"}
internal data class OriginalPetPose(val frame:Int,val tilt:Float=0f,val lift:Float=0f)
internal fun originalPetPose(pet:String,reaction:Int,time:Float,animated:Boolean):OriginalPetPose {
 val frames=when {
  reaction==1->when(pet){"dogs"->intArrayOf(14,15,22,23,30,31,15);"spider"->intArrayOf(8,9,10,11,10,9,8);"spirit"->intArrayOf(8,9,2,9,11,9,8);else->intArrayOf(8,10,8,10,8,10,8)}
  reaction==2->when(pet){"dogs"->intArrayOf(14,15,14,15);"spider"->intArrayOf(4,5,6,7);"spirit"->intArrayOf(4,5,4,5);else->intArrayOf(4,5,6,5)}
  else->when(pet){"dogs"->intArrayOf(14,15,15,14);"spider"->intArrayOf(8,8,8,10,11);"spirit"->intArrayOf(8,8,8,10,8);else->intArrayOf(9,9,9,8,9)}
 }
 val tick=if(!animated)if(reaction==0)0 else 1 else (time.coerceAtLeast(0f)*if(reaction==0)1.5f else 6f).toInt()
 val tilt=if(reaction==2)if(animated)sin((time/1.4f).coerceIn(0f,1f)*PI).toFloat()*9f else 7f else 0f
 val lift=if(animated&&reaction==1)abs(sin(time*PI*3)).toFloat()*.025f else if(animated&&reaction==0)sin(time*2f)*.006f else 0f
 return OriginalPetPose(frames[tick%frames.size],tilt,lift)
}

internal data class PetSpriteBounds(val left:Int,val top:Int,val right:Int,val bottom:Int)
/** Common bounds across the authored poses keep frame registration stable; no pixel edits. */
internal fun originalPetBounds(pixels:IntArray,width:Int,height:Int,cells:List<IntArray>):PetSpriteBounds {
 require(pixels.size==width*height&&cells.isNotEmpty())
 var left=Int.MAX_VALUE;var top=Int.MAX_VALUE;var right=0;var bottom=0
 for(c in cells){
  require(c.size==4&&c[0]>=0&&c[1]>=0&&c[2]>0&&c[3]>0&&c[0]+c[2]<=width&&c[1]+c[3]<=height)
  for(y in 0 until c[3])for(x in 0 until c[2])if((pixels[(c[1]+y)*width+c[0]+x] ushr 24)>32){
   left=minOf(left,x);top=minOf(top,y);right=maxOf(right,x+1);bottom=maxOf(bottom,y+1)
  }
 }
 require(right>left&&bottom>top){"Original pet sprite is empty"}
 return PetSpriteBounds(left,top,right,bottom)
}
