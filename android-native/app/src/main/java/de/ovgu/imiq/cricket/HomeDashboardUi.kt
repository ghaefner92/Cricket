package de.ovgu.imiq.cricket

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import kotlin.math.sin

@Composable fun HomeDashboardScreen(model:CricketModel) {
 val animated=model.settings().optString("motion")=="full"
 var weather by remember {mutableStateOf<JSONObject?>(null)}
 var loading by remember {mutableStateOf(true)}
 var unavailable by remember {mutableStateOf(false)}
 var refresh by remember {mutableIntStateOf(0)}
 val origin=model.origin
 LaunchedEffect(origin?.lat,origin?.lon,refresh) {
  loading=true;weather=null;unavailable=false
  try {
   val response=model.api.call("/api/dyconet/weather?lat=${origin?.lat?:52.13}&lon=${origin?.lon?:11.62}",timeout=25000)
   require(response.optString("schema_version")=="cricket-weather-v1")
   weather=response;unavailable=response.optString("status")!="AVAILABLE"
  }catch(e:CancellationException){throw e}catch(e:Exception){unavailable=true}finally{loading=false}
 }
 val large=model.settings().optString("textSize")=="large"
 val readable=androidx.compose.ui.text.TextStyle(fontFamily=FontFamily.SansSerif,fontSize=(if(large)16 else 14).sp,lineHeight=(if(large)22 else 20).sp)
 val typography=MaterialTheme.typography.copy(bodyLarge=readable,bodyMedium=readable.copy(fontSize=(if(large)18 else 16).sp),bodySmall=readable.copy(fontSize=(if(large)14 else 12).sp,lineHeight=18.sp),labelSmall=readable.copy(fontSize=(if(large)14 else 12).sp,lineHeight=18.sp),titleMedium=readable.copy(fontFamily=PixelFont,fontSize=(if(large)14 else 12).sp,lineHeight=24.sp))
 MaterialTheme(typography=typography) {
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
  PixelCard(spacing=8.dp,padding=10.dp) {
   BoxWithConstraints {
    val compact=maxWidth<310.dp||large
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
     CompanionSprite(model.companion().optString("appearance","robot"),Modifier.size(40.dp),animated=animated,portrait=true)
     Text(model.text("Where to?","Wohin?"),style=MaterialTheme.typography.titleMedium,color=Gold,modifier=Modifier.weight(1f))
     if(!compact)HomePlanButton(model)
    }
    if(compact)HomePlanButton(model,Modifier.fillMaxWidth())
    }
   }
   }
  PixelCard(spacing=8.dp,padding=12.dp) { DailyGoalsSphere(model) }
  PixelCard(spacing=6.dp,padding=12.dp) {
   Row(verticalAlignment=Alignment.CenterVertically) {
    Heading(model.text("Current weather","Aktuelles Wetter"));Spacer(Modifier.weight(1f))
    if(loading)CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp)
    else TextButton({refresh++}){Text(model.text("Refresh","Aktualisieren"),style=MaterialTheme.typography.labelSmall)}
   }
   Text(if(origin==null)"Magdeburg"else model.text("Near your selected origin","Nahe deinem gewählten Start"),style=MaterialTheme.typography.bodySmall,color=MutedText)
   if(loading)Text(model.text("I'm checking the conditions for you…","Ich schaue nach den Bedingungen für dich…"),style=MaterialTheme.typography.bodySmall,color=Mint)
   else if(unavailable)Text(model.text("I couldn't get current conditions. Tap Refresh to try again.","Ich konnte die aktuellen Bedingungen nicht abrufen. Tippe auf Aktualisieren, um es erneut zu versuchen."),style=MaterialTheme.typography.bodySmall,color=MutedText)
   else JourneyWeatherStrip(model,emptyList(),snapshot=weather,grid=true,compactHome=true)
  }
 }
 }
}

@Composable private fun HomePlanButton(model:CricketModel,modifier:Modifier=Modifier){
 Button(onClick={model.tab="journey";model.routeView="search"},modifier=modifier.heightIn(min=48.dp),contentPadding=PaddingValues(horizontal=12.dp,vertical=8.dp)) {
  PixelIcon("travel",Modifier.size(20.dp));Spacer(Modifier.width(6.dp))
  Text(model.text("Plan journey","Reise planen"),fontFamily=PixelFont,fontSize=9.sp,lineHeight=16.sp)
 }
}
