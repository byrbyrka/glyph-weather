package com.glyphweather.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Getting current coordinates via Fused Location Provider. */
class LocationProvider(private val context: Context) {

    fun hasPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /**
     * Returns coordinates or null if permission is missing / location is unavailable.
     * First tries to get a fresh position, and on failure, uses the last known one.
     */
    suspend fun current(): Location? {
        if (!hasPermission()) return null
        val client = LocationServices.getFusedLocationProviderClient(context)

        val fresh = suspendCancellableCoroutine<Location?> { cont ->
            val cts = CancellationTokenSource()
            cont.invokeOnCancellation { cts.cancel() }
            try {
                val req = CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                    .setMaxUpdateAgeMillis(5 * 60 * 1000) // Freshness: 5 minutes
                    .build()
                client.getCurrentLocation(req, cts.token)
                    .addOnSuccessListener {
                        if (cont.isActive) cont.resume(it)
                    }
                    .addOnFailureListener {
                        if (cont.isActive) cont.resume(null)
                    }
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(null)
            }
        }
        if (fresh != null) return fresh

        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { }
            try {
                client.lastLocation
                    .addOnSuccessListener {
                        if (cont.isActive) cont.resume(it)
                    }
                    .addOnFailureListener {
                        if (cont.isActive) cont.resume(null)
                    }
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(null)
            }
        }
    }
}
