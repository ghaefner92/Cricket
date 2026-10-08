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
