package com.nexoofficial.astralauncher.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Criteria
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class LocationService(private val context: Context) {

    fun hasLocationPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    suspend fun bestAvailableLocation(): Location? {
        if (!hasLocationPermission()) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null

        val cached = bestLastKnown(manager)
        val freshEnough = cached?.let { System.currentTimeMillis() - it.time <= 30 * 60 * 1000L } == true
        if (freshEnough) return cached

        val fresh = withTimeoutOrNull(8_000L) {
            withContext(Dispatchers.Main.immediate) {
                currentLocation(manager)
            }
        }
        return fresh ?: cached
    }

    private fun bestLastKnown(manager: LocationManager): Location? {
        val providers = runCatching { manager.getProviders(true) }.getOrDefault(emptyList())
        return providers.mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.sortedWith(
            compareByDescending<Location> { it.time }
                .thenBy { it.accuracy }
        ).firstOrNull()
    }

    private suspend fun currentLocation(manager: LocationManager): Location? =
        suspendCancellableCoroutine { continuation ->
            val provider = chooseProvider(manager)
            if (provider == null) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val cancellationSignal = CancellationSignal()
                    manager.getCurrentLocation(
                        provider,
                        cancellationSignal,
                        context.mainExecutor
                    ) { location ->
                        if (continuation.isActive) continuation.resume(location)
                    }
                    continuation.invokeOnCancellation { cancellationSignal.cancel() }
                } else {
                    @Suppress("DEPRECATION")
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            if (continuation.isActive) continuation.resume(location)
                            manager.removeUpdates(this)
                        }

                        @Deprecated("Deprecated in Android")
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                        override fun onProviderEnabled(provider: String) = Unit
                        override fun onProviderDisabled(provider: String) = Unit
                    }

                    @Suppress("DEPRECATION")
                    manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                    continuation.invokeOnCancellation { manager.removeUpdates(listener) }
                }
            } catch (_: SecurityException) {
                if (continuation.isActive) continuation.resume(null)
            } catch (_: IllegalArgumentException) {
                if (continuation.isActive) continuation.resume(null)
            }
        }

    private fun chooseProvider(manager: LocationManager): String? {
        val precise = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val preferred = buildList {
            add(LocationManager.NETWORK_PROVIDER)
            if (precise) add(LocationManager.GPS_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }

        preferred.firstOrNull { provider ->
            runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
        }?.let { return it }

        return runCatching {
            manager.getBestProvider(Criteria().apply { accuracy = Criteria.ACCURACY_COARSE }, true)
        }.getOrNull()
    }
}
