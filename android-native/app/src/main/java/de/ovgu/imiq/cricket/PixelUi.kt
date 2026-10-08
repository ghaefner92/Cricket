package de.ovgu.imiq.cricket

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay
import org.json.JSONObject
import kotlin.math.roundToInt

val Ink=Color(0xFF10182C)
val Panel=Color(0xFF182744)
val Gold=Color(0xFFFFDA83)
val Mint=Color(0xFF79D6B5)
val Pale=Color(0xFFEDF4FF)
val Edge=Color(0xFF7188AF)
val PixelFont=FontFamily(Font(R.font.press_start))
fun color(text:String)=runCatching { Color(android.graphics.Color.parseColor(text)) }.getOrDefault(Pale)
fun modeColor(mode:String)=when(modelMode(mode)){"car"->Color(0xFFCE4B4B);"bike"->Mint;"pt"->Gold;else->Color(0xFF85B7FF)}

@Composable fun CricketTheme(large:Boolean,content:@Composable ()->Unit) {
 val size=if(large)12.sp else 10.sp
 val body=androidx.compose.ui.text.TextStyle(fontFamily=PixelFont,fontSize=size,lineHeight=if(large)24.sp else 20.sp)
 MaterialTheme(colorScheme=darkColorScheme(primary=Mint,onPrimary=Ink,secondary=Gold,background=Ink,surface=Panel,onSurface=Pale,error=Color(0xFFFFB4AC)),typography=Typography(bodyLarge=body,bodyMedium=body,bodySmall=body.copy(fontSize=9.sp,lineHeight=18.sp),labelLarge=body.copy(fontSize=9.sp,lineHeight=18.sp),titleLarge=body.copy(fontSize=15.sp,lineHeight=26.sp),titleMedium=body.copy(fontSize=12.sp,lineHeight=23.sp),titleSmall=body.copy(fontSize=11.sp,lineHeight=21.sp)),content=content)
}
@Composable fun PixelCard(modifier:Modifier=Modifier,highlight:Boolean=false,content:@Composable ColumnScope.()->Unit) {
 Column(modifier.fillMaxWidth().background(Panel).border(if(highlight)3.dp else 2.dp,if(highlight)Gold else Edge).padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
}
@Composable fun PixelButton(text:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,primary:Boolean=false) {
 if(primary)Button(onClick,modifier.heightIn(min=48.dp),enabled=enabled,shape=RectangleShape,contentPadding=PaddingValues(12.dp,10.dp)){Text(text)}
 else OutlinedButton(onClick,modifier.heightIn(min=48.dp),enabled=enabled,shape=RectangleShape,border=androidx.compose.foundation.BorderStroke(2.dp,Edge),contentPadding=PaddingValues(12.dp,10.dp)){Text(text,color=if(enabled)Pale else Edge)}
}
@Composable fun Heading(text:String){Text(text,style=MaterialTheme.typography.titleMedium,color=Gold)}
@Composable fun Copy(text:String,color:Color=Pale){Text(text,color=color)}
@Composable fun catalog():JSONObject {val context=LocalContext.current;return remember { context.assets.open("catalog.json").bufferedReader().use { JSONObject(it.readText()) } }}
@Composable fun modeName(mode:String,de:Boolean):String {val c=catalog();return c.getJSONObject("beliefs").getJSONObject(if(de)"de" else "en").getJSONObject("modes").optString(modelMode(mode),mode)}
@Composable fun needName(need:String,de:Boolean):String {val c=catalog();return c.getJSONObject("beliefs").getJSONObject(if(de)"de" else "en").getJSONObject("needs").optString(need,need)}
@Composable fun goalCopy(key:String,de:Boolean):JSONObject=catalog().getJSONObject("weekly").getJSONObject(if(de)"de"else"en").getJSONObject("goals").getJSONObject(key)

@Composable fun PixelIcon(kind:String,modifier:Modifier=Modifier.size(24.dp)) {
 val rows=catalog().getJSONObject("icons").optJSONArray(kind)?:catalog().getJSONObject("icons").getJSONArray("star")
 Canvas(modifier) {val scale=minOf(size.width/16,size.height/16);for(y in 0 until rows.length()){val row=rows.optString(y);row.forEachIndexed { x,c->if(c!='.')drawRect(when(c){'2'->Pale;'3'->Color(0xFF648BE0);else->Gold},Offset(x*scale,y*scale),Size(scale,scale)) }}}
}
@Composable fun GoalIcon(key:String,modifier:Modifier=Modifier.size(40.dp)) {
 val rows=catalog().getJSONObject("goalArt").getJSONArray(key);val colors=mapOf('g' to Mint,'b' to Color(0xFFB7977B),'w' to Pale,'m' to Color(0xFF86D8D0),'y' to Gold)
 Canvas(modifier) {val unit=minOf(size.width/10,size.height/10);for(y in 0 until rows.length())rows.optString(y).forEachIndexed { x,c->colors[c]?.let { drawRect(it,Offset(x*unit,y*unit),Size(unit,unit)) } }}
}
@Composable fun GoalScene(appearance:String,key:String,animated:Boolean) {
 val rows=catalog().getJSONObject("goalArt").getJSONArray(key)
 val colors=mapOf('g' to Color(0xFF8BC98A),'b' to Color(0xFFB7977B),'w' to Pale,'m' to Color(0xFF86D8D0),'y' to Gold)
 Box(Modifier.fillMaxWidth().aspectRatio(320f/152f).border(2.dp,Edge)) {
  Canvas(Modifier.fillMaxSize()) {scale(size.width/320,size.height/152,pivot=Offset.Zero){
   drawRect(Panel)
   drawRect(Color(0xFF324A70),Offset(0f,85f),Size(320f,38f));drawRect(Color(0xFF466B73),Offset(0f,117f),Size(320f,35f));drawRect(Color(0xFF91AB95),Offset(0f,119f),Size(320f,3f));drawRect(Color(0xFF7C8C9F),Offset(0f,131f),Size(320f,15f))
   repeat(8){drawRect(Color(0xFFA7B6CC),Offset(10f+it*42,138f),Size(18f,2f))}
   listOf(25f to 25f,88f to 37f,174f to 19f,290f to 34f).forEach {(x,y)->drawRect(Color(0xFFB8C8EC),Offset(x,y),Size(2f,2f))}
   drawRect(Color(0xFFBBC9DE),Offset(229f,114f),Size(56f,5f));drawRect(Color(0xFF52688E),Offset(234f,119f),Size(46f,8f))
   for(y in 0 until rows.length())rows.optString(y).forEachIndexed {x,c->colors[c]?.let {drawRect(it,Offset(236f+x*4,69f+y*4),Size(4f,4f))}}
  }}
  CompanionSprite(appearance,Modifier.align(androidx.compose.ui.Alignment.BottomStart).padding(start=55.dp,bottom=10.dp).size(72.dp),animated=animated)
 }
}
@Composable fun CompanionSprite(appearance:String,modifier:Modifier=Modifier.size(90.dp),animated:Boolean=true,talking:Boolean=false,expression:String="neutral",transport:String?=null,portrait:Boolean=false) {
 val context=LocalContext.current
 val name=when { portrait->"$appearance-dialogue-v1.png";transport!=null->"$appearance-transport-sheet-v1.png";appearance=="naturalist"->"naturalist-sheet-v1.png";else->"$appearance-mint-sheet-v1.png" }
 val image=remember(name){context.assets.open("art/$name").use { BitmapFactory.decodeStream(it).asImageBitmap() }}
 var frame by remember(name){mutableIntStateOf(0)}
 LaunchedEffect(animated,talking,name){frame=0;if(animated)while(true){delay(if(transport!=null)260 else if(talking)300 else if(frame==0)2400 else 160);frame=(frame+1)%4}}
 val row=when { transport!=null->modes.indexOf(transport).coerceAtLeast(0);talking->1;expression=="joyful"->2;expression in listOf("reflective","curious")->3;else->0 }
 // Original sheets use the same measured crop bounds as the Vue sprites.
 val bounds=if(portrait)80 to 660 else if(transport!=null)when(appearance){"naturalist"->listOf(45 to 347,347 to 681,915 to 1210,681 to 915)[row];"creature"->listOf(20 to 328,324 to 668,907 to 1200,668 to 907)[row];"explorer"->listOf(35 to 324,324 to 664,905 to 1193,664 to 905)[row];else->listOf(20 to 325,325 to 665,925 to 1210,665 to 925)[row]}else if(appearance=="robot")row*64 to (row+1)*64 else when(appearance){"creature"->listOf(0 to 322,322 to 620,620 to 916,916 to 1230)[row];"naturalist"->listOf(0 to 312,312 to 607,607 to 904,904 to 1230)[row];else->listOf(0 to 315,315 to 610,610 to 907,907 to 1230)[row]}
 val reference=if(portrait)724 else if(appearance=="robot"&&transport==null)256 else if(transport!=null&&appearance in listOf("robot","explorer"))1243 else 1230
 val column=image.width/4f
 Canvas(modifier.semantics { contentDescription=appearance }) {
  drawImage(image,srcOffset=IntOffset((frame*column).roundToInt(),(bounds.first.toFloat()/reference*image.height).roundToInt()),srcSize=IntSize(column.roundToInt(),((bounds.second-bounds.first).toFloat()/reference*image.height).roundToInt()),dstSize=IntSize(size.width.roundToInt(),size.height.roundToInt()),filterQuality=FilterQuality.None)
 }
}
@Composable fun TravelScene(appearance:String,mode:String,animated:Boolean,positive:Int?=null,background:String="hbf") {
 val backdrop=catalog().getJSONObject("backdrops").getJSONArray(background)
 Box(Modifier.fillMaxWidth().aspectRatio(320f/180f).border(2.dp,Edge)) {
  Canvas(Modifier.fillMaxSize()) {scale(size.width/320,size.height/180,pivot=Offset.Zero){for(i in 0 until backdrop.length()){val r=backdrop.getJSONArray(i);drawRect(color(r.getString(4)),Offset(r.getDouble(0).toFloat(),r.getDouble(1).toFloat()),Size(r.getDouble(2).toFloat(),r.getDouble(3).toFloat()))}}}
  CompanionSprite(appearance,Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(bottom=8.dp).size(if(mode=="pt")140.dp else 90.dp),animated=animated,transport=mode)
  if(positive!=null&&positive!=0)Text(if(positive>0)"♥" else "♡",color=if(positive>0)Color(0xFFF77F92)else Color(0xFFB58BB3),modifier=Modifier.align(androidx.compose.ui.Alignment.TopCenter).padding(top=12.dp),fontSize=(18+4*kotlin.math.abs(positive)).sp)
 }
}
