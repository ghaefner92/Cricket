package de.ovgu.imiq.cricket

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val goalArtCache=object:LruCache<String,ImageBitmap>(8*1024*1024){
 override fun sizeOf(key:String,value:ImageBitmap)=value.width*value.height*4
}

/** Decode on a worker, sampled to icon resolution; never load eleven full atlases. */
@Composable internal fun GoalInventorySprite(key:String,modifier:Modifier){
 val assets=LocalContext.current.assets
 val image by produceState<ImageBitmap?>(goalArtCache.get(key),key){
  value=withContext(Dispatchers.IO){
   goalArtCache.get(key)?:run {
    val path="art/jrpg/goal-$key-inventory-v1.png"
    val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
    assets.open(path).use {BitmapFactory.decodeStream(it,null,bounds)}
    var sample=1
    while(maxOf(bounds.outWidth,bounds.outHeight)/sample>512)sample*=2
    val options=BitmapFactory.Options().apply {inSampleSize=sample}
    val decoded=assets.open(path).use {BitmapFactory.decodeStream(it,null,options)}!!.asImageBitmap()
    goalArtCache.put(key,decoded);decoded
   }
  }
 }
 image?.let {Image(it,contentDescription=null,modifier=modifier)}?:Box(modifier)
}
