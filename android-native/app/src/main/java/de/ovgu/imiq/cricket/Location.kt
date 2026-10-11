package de.ovgu.imiq.cricket

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import androidx.lifecycle.viewModelScope
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One fresh fix for map centering. Does not edit places or record a journey. */
@SuppressLint("MissingPermission")
suspend fun currentMapLocation(context:Context):Location = withTimeout(25000) {
 val manager=context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
 val providers=listOf(LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER).filter {runCatching {manager.isProviderEnabled(it)}.getOrDefault(false)}
 require(providers.isNotEmpty()) {"Enable location services"}
 suspendCancellableCoroutine {continuation->
  val listener=object:LocationListener {
   override fun onLocationChanged(location:Location) {
    val age=android.os.SystemClock.elapsedRealtimeNanos()-location.elapsedRealtimeNanos
    if(!location.latitude.isFinite()||!location.longitude.isFinite()||!location.hasAccuracy()||!location.accuracy.isFinite()||age<0||age>30_000_000_000L)return
    manager.removeUpdates(this)
    if(continuation.isActive)continuation.resume(location)
   }
   override fun onProviderEnabled(provider:String)=Unit
   override fun onProviderDisabled(provider:String)=Unit
   @Deprecated("Deprecated Android callback") override fun onStatusChanged(provider:String?,status:Int,extras:Bundle?)=Unit
  }
  continuation.invokeOnCancellation {runCatching {manager.removeUpdates(listener)}}
  val registered=providers.count {runCatching {manager.requestLocationUpdates(it,0L,0f,listener,Looper.getMainLooper())}.isSuccess}
  if(registered==0&&continuation.isActive)continuation.resumeWithException(IllegalStateException("Location is unavailable"))
 }
}

@SuppressLint("MissingPermission")
fun requestLocation(context:Context,model:CricketModel) {
 if(model.busy)return
 model.run {
  val manager=context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
  val provider=listOf(LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER).firstOrNull { manager.isProviderEnabled(it) }?:error(model.text("Enable location services or enter an address","Standort aktivieren oder Adresse eingeben"))
  val location=withTimeout(20000) { suspendCancellableCoroutine<Location> { continuation->
   val listener=object:LocationListener {
    override fun onLocationChanged(location:Location){manager.removeUpdates(this);if(continuation.isActive)continuation.resume(location)}
    override fun onProviderDisabled(provider:String){manager.removeUpdates(this);if(continuation.isActive)continuation.resumeWithException(IllegalStateException("Location provider disabled"))}
    override fun onProviderEnabled(provider:String)=Unit
    @Deprecated("Deprecated Android callback") override fun onStatusChanged(provider:String?,status:Int,extras:Bundle?)=Unit
   }
   continuation.invokeOnCancellation { manager.removeUpdates(listener) }
   manager.requestSingleUpdate(provider,listener,Looper.getMainLooper())
  } }
  model.setPlace(true,Place(location.latitude,location.longitude,model.text("My current location","Mein aktueller Standort"),"geo-${location.time}",source="geolocation",accuracy=location.accuracy))
 }
}
