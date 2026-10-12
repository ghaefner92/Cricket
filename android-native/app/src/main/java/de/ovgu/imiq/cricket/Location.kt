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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import androidx.lifecycle.viewModelScope
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal fun usableMapFix(lat:Double,lon:Double,accuracy:Float,fixNanos:Long,nowNanos:Long):Boolean =
 lat.isFinite()&&lon.isFinite()&&lat in -90.0..90.0&&lon in -180.0..180.0&&accuracy.isFinite()&&accuracy>=0f&&
 fixNanos>=0L&&nowNanos>=fixNanos&&nowNanos-fixNanos<=30_000_000_000L

/** Foreground display observations only. No SQLite, trip or Experience writes. */
@SuppressLint("MissingPermission")
internal fun mapLocations(context:Context)=callbackFlow<Location> {
 val manager=context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
 val providers=listOf(LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER).filter {runCatching {manager.isProviderEnabled(it)}.getOrDefault(false)}
 if(providers.isEmpty()){close(IllegalStateException("Enable location services"));return@callbackFlow}
 var accepted:Location?=null
 val listener=object:LocationListener {
  override fun onLocationChanged(location:Location) {
   val now=android.os.SystemClock.elapsedRealtimeNanos()
   if(!location.hasAccuracy()||!usableMapFix(location.latitude,location.longitude,location.accuracy,location.elapsedRealtimeNanos,now))return
   val previous=accepted
   // Reject out-of-order fixes and brief coarse echoes after a precise GPS fix.
   if(previous!=null&&(location.elapsedRealtimeNanos<=previous.elapsedRealtimeNanos||
      now-previous.elapsedRealtimeNanos<5_000_000_000L&&location.accuracy>previous.accuracy*2))return
   accepted=Location(location);trySend(Location(location))
  }
  override fun onProviderEnabled(provider:String)=Unit
  override fun onProviderDisabled(provider:String){if(providers.none {runCatching {manager.isProviderEnabled(it)}.getOrDefault(false)})close(IllegalStateException("Location providers disabled"))}
  @Deprecated("Deprecated Android callback") override fun onStatusChanged(provider:String?,status:Int,extras:Bundle?)=Unit
 }
 val registered=providers.count {runCatching {manager.requestLocationUpdates(it,1000L,0f,listener,Looper.getMainLooper())}.isSuccess}
 if(registered==0)close(IllegalStateException("Location is unavailable"))
 awaitClose {runCatching {manager.removeUpdates(listener)}}
}

/** One-shot callers share the same freshness rules and cancellation cleanup. */
suspend fun currentMapLocation(context:Context):Location=withTimeout(25000){mapLocations(context).first()}

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
