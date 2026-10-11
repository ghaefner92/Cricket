package de.ovgu.imiq.cricket

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*

internal fun hasLocationAccess(context:Context)=listOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION).any {context.checkSelfPermission(it)==PackageManager.PERMISSION_GRANTED}
internal fun locationServicesEnabled(context:Context)=(context.getSystemService(Context.LOCATION_SERVICE) as LocationManager).isLocationEnabled
internal enum class LocationStartStep { Permission, Services, Ready }
internal fun locationStartStep(permission:Boolean,services:Boolean)=if(!permission)LocationStartStep.Permission else if(!services)LocationStartStep.Services else LocationStartStep.Ready
internal data class LocationActions(val request:()->Unit,val manage:()->Unit)

/** Android owns permission and the global location switch. Never start GPS on denial. */
@Composable internal fun rememberAndroidLocationAccess(model:CricketModel,allowWithoutLocation:Boolean=false,onReady:()->Unit,onUnavailable:()->Unit):LocationActions {
 val context=LocalContext.current
 val ready by rememberUpdatedState(onReady)
 val unavailable by rememberUpdatedState(onUnavailable)
 var busy by remember {mutableStateOf(false)}
 var fallback by remember {mutableStateOf<String?>(null)}
 var active by remember {mutableStateOf(true)}
 DisposableEffect(Unit){active=true;onDispose{active=false}}
 fun finish(){busy=false;if(!active)return;if(hasLocationAccess(context)&&locationServicesEnabled(context))ready()else unavailable()}
 val settings=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){finish()}
 val resolution=rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()){finish()}
 fun checkServices(){
  if(locationServicesEnabled(context)){finish();return}
  busy=true
  val request=LocationSettingsRequest.Builder().addLocationRequest(LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY,10000).build()).setAlwaysShow(true).build()
  LocationServices.getSettingsClient(context).checkLocationSettings(request)
   .addOnSuccessListener {if(active)finish()}
   .addOnFailureListener {error->if(active){if(error is ResolvableApiException)runCatching {resolution.launch(IntentSenderRequest.Builder(error.resolution).build())}.onFailure {busy=false;fallback="services"}else {busy=false;fallback="services"}}}
 }
 val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
  if(active){if(hasLocationAccess(context))checkServices()else {busy=false;fallback="permission"}}
 }
 fun request(){if(busy||!active)return;busy=true;when(locationStartStep(hasLocationAccess(context),locationServicesEnabled(context))){LocationStartStep.Ready->finish();LocationStartStep.Services->checkServices();LocationStartStep.Permission->permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))}}
 if(fallback!=null)AlertDialog(onDismissRequest={fallback=null;busy=false;unavailable()},title={Text(model.text("Location access","Standortzugriff"))},text={Text(model.text("Enable location in Android settings, or continue without it.","Standort in den Android-Einstellungen aktivieren oder ohne Standort fortfahren."))},confirmButton={TextButton({val type=fallback;fallback=null;busy=true;settings.launch(if(type=="permission")Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+context.packageName))else Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))}){Text(model.text("Android settings","Android-Einstellungen"))}},dismissButton={TextButton({fallback=null;busy=false;unavailable()}){Text(model.text("Continue without GPS","Ohne GPS fortfahren"))}})
 return LocationActions(::request,{if(!busy){if(!hasLocationAccess(context))request()else if(locationServicesEnabled(context)){busy=true;settings.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))}else request()}})
}
