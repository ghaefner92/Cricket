package de.ovgu.imiq.cricket

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import kotlin.math.*
import org.json.JSONObject

private val bitmapCache=object:android.util.LruCache<String,ImageBitmap>(32*1024*1024){override fun sizeOf(key:String,value:ImageBitmap)=value.width*value.height*4}
/** Cache shared scene art; never decode a bitmap during animation frames. */
@Composable internal fun art(name:String):ImageBitmap {
 val context=LocalContext.current
 return remember(name){bitmapCache.get(name)?:context.assets.open("art/jrpg/$name").use {BitmapFactory.decodeStream(it).asImageBitmap()}.also {bitmapCache.put(name,it)}}
}
@Composable private fun sceneClock(animated:Boolean):State<Float> {
 val phase=remember {mutableFloatStateOf(0f)}
 LaunchedEffect(animated){phase.floatValue=0f;if(animated){var start=0L;while(true)withFrameNanos { now->if(start==0L)start=now;phase.floatValue=((now-start)/1_000_000_000.0).toFloat() }}}
 return phase
}

/** Smooth 64-unit vector sprites stay crisp at navigation and card sizes. */
@Composable fun PixelIcon(kind:String,modifier:Modifier=Modifier.size(24.dp)) {
 if(kind in goalKeys){GoalInventorySprite(kind,modifier);return}
 Canvas(modifier){scale(minOf(size.width,size.height)/64f,pivot=Offset.Zero){emblem(kind)}}
}
@Composable fun GoalIcon(key:String,modifier:Modifier=Modifier.size(40.dp))=PixelIcon(key,modifier)
/** Pixel medals share the app's sprite palette; only this small canvas animates. */
@Composable fun TransitRankSprite(rank:Int,animated:Boolean,modifier:Modifier=Modifier) {
 val clock=sceneClock(animated)
 val metal=when(rank){1->Gold;2->Color(0xFFC5D5E7);else->Color(0xFFD99668)}
 val digit=when(rank){1->listOf("010","110","010","010","111");2->listOf("111","001","111","100","111");else->listOf("111","001","111","001","111")}
 Canvas(modifier.clearAndSetSemantics {}) {
  val t=clock.value
  val frame=if(animated)(t*3).toInt()%4 else 0
  val bob=if(frame==1||frame==2)-1f else 0f
  scale(minOf(size.width,size.height)/24f,pivot=Offset.Zero) {
   translate(0f,bob) {
    fun pixel(x:Float,y:Float,w:Float,h:Float,color:Color)=drawRect(color,Offset(x,y),Size(w,h))
    pixel(6f,15f,5f,6f,Color(0xFF6866AD));pixel(13f,15f,5f,6f,Color(0xFF6866AD))
    pixel(7f,16f,3f,3f,Mint);pixel(14f,16f,3f,3f,Mint)
    pixel(5f,6f,14f,11f,Ink);pixel(7f,4f,10f,15f,Ink)
    pixel(6f,7f,12f,9f,metal);pixel(8f,5f,8f,13f,metal)
    pixel(8f,6f,7f,1f,Pale.copy(alpha=.8f));pixel(7f,8f,1f,6f,Pale.copy(alpha=.55f))
    digit.forEachIndexed {y,row->row.forEachIndexed {x,c->if(c=='1')pixel(10f+x,9f+y,1f,1f,Ink)}}
    if(rank==1){pixel(9f,2f,1f,2f,metal);pixel(11f,1f,2f,3f,metal);pixel(14f,2f,1f,2f,metal)}
    val x=if(frame<2)18f else 4f;val y=if(frame<2)5f else 13f
    pixel(x,y,1f,3f,Pale);pixel(x-1f,y+1f,3f,1f,Pale)
   }
  }
 }
}
/** Golden arcade frame, with a slow travelling glint and no flashing. */
@Composable fun Modifier.recommendationGlow(animated:Boolean,circular:Boolean=false):Modifier {
 val clock=sceneClock(animated)
 return drawWithContent {
  drawContent()
  val t=clock.value
  val pulse=if(animated).68f+.14f*sin(t*1.6f)else .8f
  val inset=4.dp.toPx();val bounds=Size((size.width-inset*2).coerceAtLeast(1f),(size.height-inset*2).coerceAtLeast(1f))
  val radius=if(circular)minOf(bounds.width,bounds.height)/2 else 8.dp.toPx()
  drawRoundRect(Gold.copy(alpha=.12f*pulse),Offset(inset,inset),bounds,CornerRadius(if(circular)radius else 10.dp.toPx()),style=Stroke(8.dp.toPx()))
  drawRoundRect(Gold.copy(alpha=pulse),Offset(inset,inset),bounds,CornerRadius(radius),style=Stroke(2.dp.toPx()))
  val perimeter=2*(bounds.width+bounds.height)
  val progress=if(animated)(t*.09f)%1f else .16f
  val d=progress*perimeter
  val p=if(circular)Offset(size.width/2+radius*cos(progress*2*PI).toFloat(),size.height/2+radius*sin(progress*2*PI).toFloat())else when {d<bounds.width->Offset(inset+d,inset);d<bounds.width+bounds.height->Offset(inset+bounds.width,inset+d-bounds.width);d<2*bounds.width+bounds.height->Offset(inset+2*bounds.width+bounds.height-d,inset+bounds.height);else->Offset(inset,inset+perimeter-d)}
  drawCircle(Gold.copy(alpha=.2f),6.dp.toPx(),p)
  drawLine(Pale,p-Offset(3.dp.toPx(),0f),p+Offset(3.dp.toPx(),0f),1.5.dp.toPx())
  drawLine(Pale,p-Offset(0f,3.dp.toPx()),p+Offset(0f,3.dp.toPx()),1.5.dp.toPx())
 }
}
/** Dedicated sixteen-pose startup atlas; all poses retain their authored anchors. */
@Composable fun WelcomeAvatar(appearance:String,seconds:Float,animated:Boolean,modifier:Modifier=Modifier) {
 val sheet=art("welcome-$appearance-v1.png")
 val t=seconds.coerceAtLeast(0f)/1.8f
 val frame=when {
  !animated->10
  t<.7f->(t*10f).toInt().coerceIn(0,6)
  t<2.5f->intArrayOf(7,8,9,10,11,10,9,8)[((t-.7f)*10f).toInt()%8]
  else->(12+((t-2.5f)*4f).toInt()).coerceIn(12,15)
 }
 Canvas(modifier.semantics {contentDescription="$appearance greeting"}) {
  val col=frame%4;val row=frame/4
  val x=col*sheet.width/4;val y=row*sheet.height/4
  val w=(col+1)*sheet.width/4-x;val h=(row+1)*sheet.height/4-y
  drawImage(sheet,IntOffset(x,y),IntSize(w,h),IntOffset.Zero,IntSize(size.width.roundToInt(),size.height.roundToInt()),filterQuality=FilterQuality.Medium)
 }
}
@Composable fun ContextSprite(kind:String,animated:Boolean,modifier:Modifier=Modifier.size(28.dp)) {
 val goalIndex=needKeys.indexOf(kind)
 val emblemKind=if(goalIndex>=0)goalKeys[goalIndex]else kind
 if(emblemKind in goalKeys){GoalInventorySprite(emblemKind,modifier);return}
 val clock=sceneClock(animated)
 Canvas(modifier){
  val t=clock.value
  val unit=minOf(size.width,size.height)/64f
  translate(top=if(animated)sin(t*1.8f)*size.height*.025f else 0f) {
   scale(unit*(if(animated)1f+.018f*sin(t*2f)else 1f),pivot=Offset.Zero){
    emblem(emblemKind)
    if(animated&&kind=="rain") {
     val y=43f+(t*12f)%14f
     drawLine(Color(0xFF96D8E9),Offset(22f,y),Offset(19f,y+5f),3f,StrokeCap.Round)
     drawLine(Color(0xFF96D8E9),Offset(43f,y-5f),Offset(40f,y),3f,StrokeCap.Round)
    }
   }
  }
 }
}
@Composable fun NavigationSprite(kind:String,selected:Boolean,animated:Boolean) {
 val clock=sceneClock(selected&&animated)
 Canvas(Modifier.size(26.dp)) {val factor=if(selected&&animated).98f+.02f*sin(clock.value*2)else 1f;scale(minOf(size.width,size.height)/64f*factor,pivot=Offset.Zero){emblem(kind)}}
}

private fun DrawScope.emblem(kind:String) {
 fun line(x:Float,y:Float,a:Float,b:Float,c:Color=Gold,w:Float=4f)=drawLine(c,Offset(x,y),Offset(a,b),w,StrokeCap.Round)
 fun box(x:Float,y:Float,w:Float,h:Float,c:Color=Mint,r:Float=6f){drawRoundRect(Ink,Offset(x-2,y-2),Size(w+4,h+4),CornerRadius(r+2));drawRoundRect(Brush.verticalGradient(listOf(c,c.copy(alpha=.72f))),Offset(x,y),Size(w,h),CornerRadius(r));line(x+4,y+3,x+w-4,y+3,Pale.copy(alpha=.6f),2f)}
 fun ring(x:Float,y:Float,r:Float,c:Color=Gold){drawCircle(Ink,r+2,Offset(x,y));drawCircle(c,r,Offset(x,y),style=Stroke(4f))}
 fun leaf(){val p=Path().apply{moveTo(16f,45f);cubicTo(6f,19f,37f,8f,51f,10f);cubicTo(53f,40f,31f,55f,16f,45f);close()};drawPath(p,Ink,style=Stroke(6f));drawPath(p,Brush.linearGradient(listOf(Mint,Color(0xFF348C79))));line(14f,52f,42f,21f,Gold,3f);line(27f,37f,24f,23f,Pale,2f)}
 when(kind){
 "temperature"->{box(26f,8f,12f,36f,Pale,6f);drawCircle(Ink,13f,Offset(32f,47f));drawCircle(Color(0xFFEA8284),10f,Offset(32f,47f));line(32f,22f,32f,45f,Color(0xFFEA8284),5f);line(43f,17f,50f,17f);line(43f,27f,48f,27f)}
 "rain"->{drawCircle(Ink,13f,Offset(20f,27f));drawCircle(Ink,18f,Offset(34f,23f));drawCircle(Ink,12f,Offset(47f,29f));box(10f,24f,45f,15f,Color(0xFF96D8E9),8f);drawCircle(Pale,10f,Offset(20f,26f));drawCircle(Pale,14f,Offset(34f,23f));line(24f,45f,19f,54f,Mint,3f);line(44f,45f,39f,54f,Mint,3f)}
 "humidity"->{val drop=Path().apply{moveTo(28f,7f);cubicTo(23f,18f,9f,29f,9f,40f);cubicTo(9f,60f,47f,60f,47f,40f);cubicTo(47f,29f,33f,18f,28f,7f);close()};drawPath(drop,Ink,style=Stroke(6f));drawPath(drop,Brush.verticalGradient(listOf(Pale,Color(0xFF57ABC9))));ring(23f,34f,3f,Ink);ring(35f,46f,3f,Ink);line(22f,48f,36f,32f,Ink,3f)}
 "windSpeed"->{line(8f,24f,42f,24f,Mint);ring(45f,18f,7f,Mint);line(12f,36f,54f,36f,Pale);line(8f,48f,35f,48f,Gold);ring(39f,52f,6f,Gold)}
 "windGust"->{line(15f,8f,15f,57f,Pale,4f);line(9f,58f,23f,58f,Gold,3f);val sock=Path().apply{moveTo(17f,12f);lineTo(56f,21f);lineTo(56f,32f);lineTo(17f,38f);close()};drawPath(sock,Ink,style=Stroke(5f));drawPath(sock,Gold);line(28f,16f,28f,35f,Pale,5f);line(43f,20f,43f,32f,Pale,4f);line(33f,46f,57f,46f,Mint,3f);line(49f,41f,57f,46f,Mint,3f);line(49f,51f,57f,46f,Mint,3f)}
 "uvIndex","lightIntensity","weather"->{drawCircle(Ink,16f,Offset(32f,32f));drawCircle(Gold,12f,Offset(32f,32f));for(i in 0..7){val angle=i*PI/4;line((32+20*cos(angle)).toFloat(),(32+20*sin(angle)).toFloat(),(32+27*cos(angle)).toFloat(),(32+27*sin(angle)).toFloat(),Gold,3f)}}
 "night"->{val moon=Path().apply {moveTo(38f,9f);cubicTo(8f,5f,5f,48f,31f,54f);cubicTo(44f,58f,55f,46f,54f,36f);cubicTo(27f,47f,21f,20f,38f,9f);close()};drawPath(moon,Ink,style=Stroke(6f));drawPath(moon,Brush.linearGradient(listOf(Pale,Gold)));line(48f,10f,48f,22f,Pale,2f);line(42f,16f,54f,16f,Pale,2f);drawRect(Mint,Offset(53f,27f),Size(3f,3f))}
 "finish"->{line(15f,57f,15f,7f,Pale,5f);val flag=Path().apply{moveTo(17f,10f);lineTo(55f,10f);lineTo(51f,38f);lineTo(17f,38f);close()};drawPath(flag,Ink,style=Stroke(5f));drawPath(flag,Pale);for(row in 0..2)for(col in 0..3)if((row+col)%2==0)drawRect(Ink,Offset(19f+col*8f,12f+row*8f),Size(8f,8f));line(9f,58f,23f,58f,Gold,4f)}
 "sparkle"->{val p=Path().apply{moveTo(32f,4f);lineTo(39f,24f);lineTo(59f,32f);lineTo(39f,40f);lineTo(32f,60f);lineTo(25f,40f);lineTo(5f,32f);lineTo(25f,24f);close()};drawPath(p,Ink,style=Stroke(5f));drawPath(p,Gold);line(32f,17f,32f,46f,Pale,2f)}
 "info"->{ring(32f,32f,24f,Mint);drawCircle(Panel,18f,Offset(32f,32f));drawRect(Pale,Offset(29f,16f),Size(6f,6f));line(32f,29f,32f,46f,Gold,5f);line(25f,46f,39f,46f,Gold,3f)}
 "previous"->{line(40f,13f,21f,32f,Pale,6f);line(21f,32f,40f,51f,Pale,6f)}
 "next"->{line(24f,13f,43f,32f,Pale,6f);line(43f,32f,24f,51f,Pale,6f)}
 "home","sun"->{box(15f,28f,34f,29f);val roof=Path().apply{moveTo(9f,31f);lineTo(32f,10f);lineTo(55f,31f);close()};drawPath(roof,Ink,style=Stroke(6f));drawPath(roof,Gold);box(27f,39f,10f,18f,Panel,2f);box(40f,35f,6f,8f,Pale,1f)}
 "passport","star"->{box(14f,8f,38f,48f,Color(0xFF5FA89F));line(20f,10f,20f,54f,Gold,3f);ring(37f,29f,9f);line(29f,45f,45f,45f,Pale,2f)}
 "menu","clock"->{box(10f,17f,44f,37f,Color(0xFFB68B68));box(23f,10f,18f,10f,Gold,4f);box(13f,20f,38f,14f,Mint);box(28f,29f,8f,13f,Gold,2f)}
 "search"->{ring(26f,26f,16f,Mint);line(38f,38f,54f,54f,Gold,7f);line(19f,20f,26f,17f,Pale,2f)}
 "recent"->{ring(33f,33f,22f,Mint);drawCircle(Panel,16f,Offset(33f,33f));line(33f,19f,33f,33f);line(33f,33f,44f,39f);line(8f,13f,8f,27f,Gold);line(8f,27f,22f,27f,Gold);line(8f,27f,18f,15f,Gold,3f)}
 "location"->{val p=Path().apply{moveTo(32f,58f);cubicTo(23f,45f,10f,31f,14f,20f);cubicTo(20f,0f,44f,0f,50f,20f);cubicTo(54f,31f,41f,45f,32f,58f);close()};drawPath(p,Ink,style=Stroke(6f));drawPath(p,Mint);drawCircle(Panel,8f,Offset(32f,23f));drawCircle(Gold,4f,Offset(32f,23f))}
 "swap","flex"->{line(10f,22f,51f,22f,Mint);line(51f,22f,42f,13f,Mint);line(51f,22f,42f,31f,Mint);line(54f,43f,13f,43f);line(13f,43f,22f,34f);line(13f,43f,22f,52f)}
 "preferences"->{listOf(17f,32f,47f).forEachIndexed{i,y->line(10f,y,54f,y,Edge,3f);box(if(i==1)36f else 19f,y-5,10f,10f,if(i==1)Gold else Mint,3f)}}
 "travel","map"->{ring(32f,32f,25f,Mint);val p=Path().apply{moveTo(42f,15f);lineTo(37f,39f);lineTo(22f,49f);lineTo(27f,25f);close()};drawPath(p,Gold);line(32f,32f,42f,15f,Pale,2f)}
 "env"->leaf()
 "bike"->{ring(15f,45f,12f,Mint);ring(49f,45f,12f,Mint);line(15f,45f,26f,25f);line(26f,25f,37f,45f);line(37f,45f,15f,45f);line(26f,25f,41f,25f);line(41f,25f,49f,45f);line(40f,25f,40f,16f);line(39f,16f,48f,16f);line(20f,22f,30f,22f,Pale)}
 "pt"->{box(8f,13f,48f,38f);box(14f,19f,36f,17f,Color(0xFF6CA6C2));line(9f,41f,55f,41f,Gold,4f);drawCircle(Gold,3f,Offset(15f,45f));drawCircle(Gold,3f,Offset(49f,45f));ring(18f,54f,5f,Edge);ring(46f,54f,5f,Edge);line(32f,12f,38f,5f);line(38f,5f,22f,5f)}
 "car"->{val p=Path().apply{moveTo(7f,35f);lineTo(16f,32f);lineTo(24f,17f);quadraticTo(32f,13f,43f,18f);lineTo(51f,32f);lineTo(57f,36f);quadraticTo(60f,43f,56f,50f);lineTo(9f,50f);quadraticTo(4f,44f,7f,35f);close()};drawPath(p,Ink,style=Stroke(6f));drawPath(p,Color(0xFFEA8284));box(24f,21f,18f,11f,Color(0xFF96D8E9),3f);line(33f,21f,33f,33f,Ink,2f);line(7f,40f,14f,40f,Gold,3f);line(50f,40f,57f,40f,Gold,3f);ring(17f,51f,7f,Edge);ring(47f,51f,7f,Edge)}
 "walk","health_activity"->{box(13f,15f,15f,26f,Mint);box(12f,38f,27f,13f,Gold);box(37f,7f,13f,24f,Mint);box(36f,29f,23f,12f,Gold);line(14f,53f,38f,53f,Pale,2f)}
 "cost"->{box(13f,21f,38f,34f,Color(0xFFB68B68),14f);box(22f,13f,20f,11f,Mint);ring(32f,38f,11f);line(32f,30f,32f,46f,Pale,2f)}
 "time","reliable"->{ring(32f,32f,24f,Mint);drawCircle(Panel,18f,Offset(32f,32f));line(32f,16f,32f,32f);line(32f,32f,44f,39f);if(kind=="reliable"){line(37f,48f,43f,54f);line(43f,54f,56f,41f)}}
 "comfort_physical"->{box(12f,25f,40f,28f,Mint,10f);box(17f,12f,30f,24f,Gold,10f);line(14f,56f,14f,60f,Edge);line(50f,56f,50f,60f,Edge)}
 "crowding"->{listOf(14f,50f).forEach{x->drawCircle(Mint,7f,Offset(x,17f));box(x-9,28f,18f,24f,Mint)};line(27f,38f,37f,38f);line(27f,38f,31f,33f);line(37f,38f,33f,43f)}
 "safety_crime"->{box(19f,21f,26f,31f,Gold,4f);box(25f,26f,14f,21f,Color(0xFFFFF0B7),2f);line(17f,20f,47f,20f,Mint,6f);line(21f,54f,43f,54f,Mint,5f);drawArc(Mint,180f,180f,false,Offset(24f,6f),Size(16f,20f),style=Stroke(4f))}
 "safety_accident"->{drawArc(Ink,180f,180f,true,Offset(7f,10f),Size(50f,50f));drawArc(Mint,180f,180f,true,Offset(10f,13f),Size(44f,44f));line(9f,39f,54f,39f,Gold,6f);line(46f,42f,42f,55f,Edge,4f);line(42f,55f,30f,55f,Edge,4f);line(23f,20f,31f,17f,Pale,3f)}
 else->{val p=Path().apply{moveTo(32f,7f);lineTo(53f,15f);lineTo(50f,38f);quadraticTo(45f,51f,32f,58f);quadraticTo(19f,51f,14f,38f);lineTo(11f,15f);close()};drawPath(p,Ink,style=Stroke(6f));drawPath(p,Mint);if(kind=="health_infection"){line(32f,23f,32f,43f,Gold,7f);line(22f,33f,42f,33f,Gold,7f)}else{line(22f,33f,29f,40f);line(29f,40f,43f,23f)}}
 }
}

@Composable fun CompanionSprite(appearance:String,modifier:Modifier=Modifier.size(90.dp),animated:Boolean=true,talking:Boolean=false,expression:String="neutral",transport:String?=null,portrait:Boolean=false) {
 if(transport!=null){TransportSprite(appearance,transport,animated,modifier);return}
 val safe=appearance.takeIf {it in listOf("robot","explorer","creature","naturalist")}?:"robot"
 val image=art("$safe-sheet-v2.png");val time=sceneClock(animated)
 val row=when{talking->1;expression=="joyful"->2;expression in listOf("reflective","curious")->3;else->0}
 Canvas(modifier.semantics {contentDescription=appearance}) {
  val t=time.value;val frame=if(!animated)0 else if(row==0){val p=t%5f;when{p<3.8f->0;p<3.95f->2;p<4.1f->3;else->0}}else {val sequence=intArrayOf(0,1,2,3,4,5,4,3,2,1);sequence[(t*3).toInt()%sequence.size]}
  val cell=image.width/6;val h=image.height/4;val crop=if(portrait)(h*.72f).roundToInt()else h
  val side=minOf(size.width,size.height);val breath=if(animated)sin(t*2.1f)*side*.009f else 0f
  drawImage(image,IntOffset(frame*cell+if(portrait)(cell-crop)/2 else 0,row*h),IntSize(if(portrait)crop else cell,crop),IntOffset(((size.width-side)/2).roundToInt(),(((size.height-side)/2)+breath).roundToInt()),IntSize(side.roundToInt(),side.roundToInt()),filterQuality=FilterQuality.Medium)
 }
}

@Composable private fun SceneFrame(animated:Boolean,garden:Boolean=false,content:@Composable BoxScope.()->Unit) {
 val background=art(if(garden)"garden-v2.png"else"station-v2.png");val clock=sceneClock(animated)
 Box(Modifier.fillMaxWidth().aspectRatio(16f/9f).clip(RoundedCornerShape(14.dp)).border(1.dp,Gold.copy(alpha=.45f),RoundedCornerShape(14.dp))) {
  Image(background,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
  Canvas(Modifier.fillMaxSize()){drawRect(Brush.verticalGradient(listOf(Ink.copy(alpha=.08f),Ink.copy(alpha=.15f))));repeat(7){i->val t=clock.value;val x=size.width*(.08f+i*.14f);val y=size.height*(.24f+.07f*sin(i*2f+t*.5f));drawCircle(Gold.copy(alpha=.25f+.25f*(sin(t+i)+1f)/2),1.5.dp.toPx(),Offset(x,y))}}
  content()
 }
}
@Composable fun GoalScene(appearance:String,key:String,animated:Boolean) {
 val scene:@Composable (String)->Unit={goal->SceneFrame(animated,garden=true){CompanionSprite(appearance,Modifier.align(Alignment.BottomStart).padding(start=30.dp,bottom=6.dp).size(104.dp),animated,expression="curious");Box(Modifier.align(Alignment.CenterEnd).padding(end=30.dp).size(74.dp).clip(RoundedCornerShape(20.dp)).background(Panel.copy(alpha=.92f)).border(1.dp,Gold,RoundedCornerShape(20.dp)),contentAlignment=Alignment.Center){GoalIcon(goal,Modifier.size(55.dp))}}}
 if(animated)Crossfade(key,label="goal-scene"){scene(it)}else scene(key)
}
@Composable fun TravelScene(appearance:String,mode:String,animated:Boolean,positive:Int?=null,background:String="hbf") {
 SceneFrame(animated,garden=background=="citadel"){
  TransportSprite(appearance,mode,animated,Modifier.align(Alignment.BottomCenter).padding(bottom=8.dp).size(when(mode){"pt"->176.dp;"car"->160.dp;"bike"->132.dp;else->108.dp},if(mode=="pt"||mode=="car")110.dp else 122.dp))
  if(positive!=null&&positive!=0)Text(if(positive>0)"♥"else"♡",color=if(positive>0)Color(0xFFFF9CAE)else Pale,modifier=Modifier.align(Alignment.TopEnd).padding(12.dp),fontSize=24.sp)
 }
}
@Composable internal fun TransportSprite(appearance:String,mode:String,animated:Boolean,modifier:Modifier,seconds:Float?=null,smoothFrames:Boolean=false,reportedValence:Int?=null) {
 val safe=appearance.takeIf {it in listOf("robot","explorer","creature","naturalist")}?:"robot"
 val context=LocalContext.current;val image=art("$safe-transport-v2.png");val time=if(seconds==null)sceneClock(animated)else rememberUpdatedState(seconds)
 val metadata=remember(safe){context.assets.open("art/jrpg/transport-atlas.json").bufferedReader().use{JSONObject(it.readText()).getJSONObject(safe)}}
 val row=when(modelMode(mode)){"bike"->1;"car"->2;"pt"->3;else->0}
 val reference=metadata.getJSONArray("reference").getJSONArray(row)
 val emotion by animateFloatAsState((reportedValence?:0).toFloat(),tween(if(animated)500 else 0),label="Transport avatar feeling")
 Canvas(modifier.semantics {contentDescription="$appearance · $mode"}){
  // Emotion is painted on the character already inside the mode sprite.
  // Its stable pose keeps facial coordinates aligned while movement is continuous.
  val t=time.value;val position=if(animated&&reportedValence==null)t*6 else 0f;val frame=position.toInt()%6
  val ratio=minOf(size.width/reference.getInt(0),size.height/reference.getInt(1))
  val bob=if(animated)sin(t*if(row==0)7f else 3f)*.6.dp.toPx()else 0f
  drawOval(Ink.copy(alpha=.3f),Offset(size.width*.15f,size.height-5.dp.toPx()),Size(size.width*.7f,5.dp.toPx()))
  fun drawFrame(index:Int,opacity:Float) {
   val bounds=metadata.getJSONArray("rows").getJSONArray(row).getJSONArray(index)
   val w=bounds.getInt(2)*ratio;val h=bounds.getInt(3)*ratio
   drawImage(image,IntOffset(bounds.getInt(0),bounds.getInt(1)),IntSize(bounds.getInt(2),bounds.getInt(3)),IntOffset(((size.width-w)/2).roundToInt(),(size.height-h-2.dp.toPx()+bob).roundToInt()),IntSize(w.roundToInt(),h.roundToInt()),alpha=opacity,filterQuality=FilterQuality.Medium)
   if(reportedValence!=null) {
    translate(((size.width-w)/2).roundToInt().toFloat(),(size.height-h-2.dp.toPx()+bob).roundToInt().toFloat()) {
     scale(ratio,pivot=Offset.Zero) {
      // Bounding boxes may extend into the preceding atlas row (tram pantograph).
      // Preserve that signed origin so the facial overlay stays inside the window.
      transportEmotion(safe,row,bounds.getInt(0)-index*(image.width/6),bounds.getInt(1)-row*(image.height/4),emotion)
     }
    }
   }
  }
  val blend=if(smoothFrames&&animated)position-position.toInt()else 0f
  drawFrame(frame,1f)
  if(blend>0f)drawFrame((frame+1)%6,blend)
 }
}

/** Mouth anchors in the first 256 × 256 cell of each established transport row. */
private fun DrawScope.transportEmotion(appearance:String,row:Int,cropX:Int,cropY:Int,emotion:Float) {
 val anchors=when(appearance) {
  "naturalist"->listOf(Triple(195f,114f,16f),Triple(170f,106f,14f),Triple(157f,120f,8f),Triple(144f,107f,7f))
  "explorer"->listOf(Triple(156f,147f,16f),Triple(139f,124f,14f),Triple(151f,141f,8f),Triple(140f,111f,8f))
  "creature"->listOf(Triple(190f,137f,18f),Triple(173f,110f,17f),Triple(170f,107f,8f),Triple(146f,103f,8f))
  else->listOf(Triple(156f,129f,18f),Triple(143f,102f,15f),Triple(153f,121f,10f),Triple(125f,118f,9f))
 }
 val (x,y,width)=anchors[row];val mouth=Offset(x-cropX,y-cropY)
 val skin=when(appearance){"naturalist"->Color(0xFFFFC28D);"explorer"->Color(0xFFFFBD74);"creature"->Color(0xFFD2E894);else->Mint}
 val outline=if(appearance=="robot")Color(0xFF143B39)else Color(0xFF653A33)
 drawOval(skin,mouth-Offset(width*.6f,width*.3f),Size(width*1.2f,width*.65f))
 val curve=Path().apply {moveTo(mouth.x-width*.42f,mouth.y);quadraticTo(mouth.x,mouth.y+emotion*width*.19f,mouth.x+width*.42f,mouth.y)}
 drawPath(curve,outline,style=Stroke((width*.12f).coerceAtLeast(1.2f),cap=StrokeCap.Round))
 if(emotion>0)for(sign in listOf(-1,1))drawOval(Color(0xFFFF849F).copy(alpha=.17f*emotion),mouth+Offset(sign*width*.8f-width*.2f,-width*.35f),Size(width*.4f,width*.22f))
}
@Composable fun IconAction(text:String,kind:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true) {
 OutlinedButton(onClick,modifier.heightIn(min=48.dp),enabled=enabled,shape=RoundedCornerShape(12.dp)){PixelIcon(kind,Modifier.size(24.dp));Spacer(Modifier.width(8.dp));Text(text,Modifier.weight(1f))}
}
@Composable fun SpriteDisclosure(title:String,kind:String,initiallyExpanded:Boolean=false,animate:Boolean=true,content:@Composable ColumnScope.()->Unit) {
 var expanded by remember {mutableStateOf(initiallyExpanded)}
 PixelCard(if(animate)Modifier.animateContentSize()else Modifier){
  Row(Modifier.fillMaxWidth().clickable(role=Role.Button){expanded=!expanded}.semantics {stateDescription=if(expanded)"Expanded"else"Collapsed"}.padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){PixelIcon(kind,Modifier.size(32.dp));Text(title,Modifier.weight(1f),color=Gold);Text(if(expanded)"−"else"+",color=Mint)}
  if(animate)AnimatedVisibility(expanded){Column(verticalArrangement=Arrangement.spacedBy(10.dp),content=content)}else if(expanded)Column(verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
 }
}
