package com.example.testappcc.utils


import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun FusedLocationProviderClient.await(): Location? {
    return suspendCancellableCoroutine { cont ->
        lastLocation
            .addOnSuccessListener { location -> cont.resume(location) }
            .addOnFailureListener { exception -> cont.resumeWithException(exception) }
    }
}
