package de.ovgu.imiq.cricket

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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

// Shared charcoal/plum surfaces; accents remain mint and gold.
val Ink=Color(0xFF19151F)
val Panel=Color(0xFF27212F)
val PanelRaised=Color(0xFF35303D)
val MutedText=Color(0xFFB6ACBE)
val Gold=Color(0xFFFFDA83)
val Mint=Color(0xFF79D6B5)
val Pale=Color(0xFFF4EFE7)
val Edge=Color(0xFF756B82)
val PixelFont=FontFamily(Font(R.font.press_start))
fun color(text:String)=runCatching { Color(android.graphics.Color.parseColor(text)) }.getOrDefault(Pale)
fun modeColor(mode:String)=when(modelMode(mode)){"car"->Color(0xFFCE4B4B);"bike"->Mint;"pt"->Gold;else->Color(0xFF85B7FF)}

// Same warm transit family: tram gold, bus apricot. Vehicle comes from the leg.
fun mapSegmentColor(segment:MapSegment)=if(segment.mode=="pt"&&segment.transitVehicle in listOf("BUS","TROLLEYBUS"))Color(0xFFFFA96B)else modeColor(segment.mode)

@Composable fun CricketTheme(large:Boolean,content:@Composable ()->Unit) {
 val size=if(large)12.sp else 10.sp
 val body=androidx.compose.ui.text.TextStyle(fontFamily=PixelFont,fontSize=size,lineHeight=if(large)24.sp else 20.sp)
 MaterialTheme(colorScheme=darkColorScheme(
  primary=Mint,onPrimary=Ink,primaryContainer=Color(0xFF293E37),onPrimaryContainer=Pale,inversePrimary=Color(0xFF306B57),
  secondary=Gold,onSecondary=Ink,secondaryContainer=Color(0xFF3E3348),onSecondaryContainer=Gold,
  tertiary=Color(0xFFDDB3C7),onTertiary=Ink,tertiaryContainer=Color(0xFF49303F),onTertiaryContainer=Pale,
  background=Ink,onBackground=Pale,surface=Panel,onSurface=Pale,surfaceVariant=PanelRaised,onSurfaceVariant=MutedText,
  surfaceTint=Color.Transparent,surfaceDim=Ink,surfaceBright=PanelRaised,
  surfaceContainerLowest=Ink,surfaceContainerLow=Panel,surfaceContainer=Panel,surfaceContainerHigh=PanelRaised,surfaceContainerHighest=Color(0xFF403948),
  outline=MutedText,outlineVariant=Edge,inverseSurface=Pale,inverseOnSurface=Ink,
  error=Color(0xFFFFB4AC),onError=Ink,errorContainer=Color(0xFF4E252B),onErrorContainer=Color(0xFFFFDAD6),scrim=Ink
 ),typography=Typography(bodyLarge=body,bodyMedium=body,bodySmall=body.copy(fontSize=9.sp,lineHeight=18.sp),labelLarge=body.copy(fontSize=9.sp,lineHeight=18.sp),titleLarge=body.copy(fontSize=15.sp,lineHeight=26.sp),titleMedium=body.copy(fontSize=12.sp,lineHeight=23.sp),titleSmall=body.copy(fontSize=11.sp,lineHeight=21.sp)),content=content)
}
@Composable fun PixelCard(modifier:Modifier=Modifier,highlight:Boolean=false,spacing:androidx.compose.ui.unit.Dp=12.dp,padding:androidx.compose.ui.unit.Dp=14.dp,content:@Composable ColumnScope.()->Unit) {
 val shape=RoundedCornerShape(18.dp)
 Column(modifier.fillMaxWidth().clip(shape).background(Brush.verticalGradient(listOf(PanelRaised,Panel))).border(if(highlight)2.dp else 1.dp,if(highlight)Gold else Edge.copy(alpha=.55f),shape).padding(padding),verticalArrangement=Arrangement.spacedBy(spacing),content=content)
}
@Composable fun PixelButton(text:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,primary:Boolean=false) {
 if(primary)Button(onClick,modifier.heightIn(min=48.dp),enabled=enabled,shape=RoundedCornerShape(12.dp),contentPadding=PaddingValues(12.dp,10.dp)){Text(text)}
 else OutlinedButton(onClick,modifier.heightIn(min=48.dp),enabled=enabled,shape=RoundedCornerShape(12.dp),border=androidx.compose.foundation.BorderStroke(1.dp,MutedText.copy(alpha=.65f)),contentPadding=PaddingValues(12.dp,10.dp)){Text(text,color=if(enabled)Pale else MutedText)}
}
@Composable fun Heading(text:String){Text(text,style=MaterialTheme.typography.titleMedium,color=Gold)}
@Composable fun Copy(text:String,color:Color=Pale){Text(text,color=color)}
@Composable fun catalog():JSONObject {val context=LocalContext.current;return remember { context.assets.open("catalog.json").bufferedReader().use { JSONObject(it.readText()) } }}
@Composable fun modeName(mode:String,de:Boolean):String {val c=catalog();return c.getJSONObject("beliefs").getJSONObject(if(de)"de" else "en").getJSONObject("modes").optString(modelMode(mode),mode)}
@Composable fun needName(need:String,de:Boolean):String {val c=catalog();return c.getJSONObject("beliefs").getJSONObject(if(de)"de" else "en").getJSONObject("needs").optString(need,need)}
@Composable fun goalCopy(key:String,de:Boolean):JSONObject=catalog().getJSONObject("weekly").getJSONObject(if(de)"de"else"en").getJSONObject("goals").getJSONObject(key)
