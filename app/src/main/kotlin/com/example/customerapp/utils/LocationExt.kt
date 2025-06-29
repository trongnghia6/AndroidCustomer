package com.example.customerapp.utils


import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun FusedLocationProviderClient.await(context: Context): Location? {
    // Check for location permission
    val hasFineLocationPermission = ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    val hasCoarseLocationPermission = ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    // Return null if no location permissions are granted
    if (!hasFineLocationPermission && !hasCoarseLocationPermission) {
        return null // Or throw an exception if preferred
    }
    return suspendCancellableCoroutine { cont ->
        lastLocation
            .addOnSuccessListener { location -> cont.resume(location) }
            .addOnFailureListener { exception -> cont.resumeWithException(exception) }
    }
}
