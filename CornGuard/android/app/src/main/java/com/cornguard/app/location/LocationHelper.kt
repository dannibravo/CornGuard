package com.cornguard.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Best-effort, permission-agnostic location lookup for optionally geotagging a scan
 * (claude/04_DEVELOPMENT_RULES.md #9 — location is optional metadata, denial must degrade
 * gracefully, never block scanning). GPS needs no internet, so this works offline. No Play
 * Services Location dependency needed.
 *
 * A fix is only used if it is provably recent: its age, measured on the boot clock
 * ([Location.getElapsedRealtimeNanos]), must be between 0 and [MAX_FIX_AGE_MS]. Within
 * [FRESH_FIX_TIMEOUT_MS] in total it tries, in order:
 *  1. the platform's current location per provider (often answered instantly from its cache);
 *  2. if that answer is missing or not provably recent, a live update — the provider is switched on
 *     and the first fix that arrives after the request is taken.
 * Otherwise it returns null rather than a stale fix: a scan tagged with where the farmer was hours
 * ago, or in another field, is worse than none (the scan then falls back to its barangay's interior
 * point, see [BarangayResolver.placeScan]).
 *
 * Step 2 exists because a cached fix with a bogus timestamp can look "fresh" forever. The API 30
 * emulator stamps GPS fixes with wall-clock time instead of boot time, so the platform kept
 * returning the cached fix, never switched GPS on, and dropped every new emulator position — every
 * scan got the previous test point.
 *
 * Callers MUST check [com.cornguard.app.permissions.PermissionManager.isGranted] for
 * [com.cornguard.app.permissions.AppPermission.LOCATION] before calling — this class does not
 * check permissions itself, it just reads whatever the platform will give it.
 */
class LocationHelper(private val appContext: Context) {

    @SuppressLint("MissingPermission")
    suspend fun getLastKnownLocation(): Location? {
        val manager = appContext.getSystemService<LocationManager>() ?: return null
        val enabled = runCatching { manager.getProviders(true) }.getOrNull().orEmpty()
        val providers = PREFERRED_PROVIDERS.filter { it in enabled }
        if (providers.isEmpty()) return null

        val result = withTimeoutOrNull(FRESH_FIX_TIMEOUT_MS) {
            for (provider in providers) {
                currentLocation(manager, provider)?.takeIf { isRecent(it) }?.let { return@withTimeoutOrNull it }
            }
            // Nothing provably recent cached: switch a provider on and wait for a new fix.
            liveUpdate(manager, providers)
        }
        if (result != null) return result

        // Timed out: a last-known fix still counts if it is provably recent.
        return withContext(Dispatchers.IO) {
            providers
                .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
                .filter { isRecent(it) }
                .maxByOrNull { it.elapsedRealtimeNanos }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(manager: LocationManager, provider: String): Location? =
        withTimeoutOrNull(CURRENT_LOCATION_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                runCatching {
                    LocationManagerCompat.getCurrentLocation(
                        manager, provider, signal, ContextCompat.getMainExecutor(appContext)
                    ) { location -> if (cont.isActive) cont.resume(location) }
                }.onFailure { if (cont.isActive) cont.resume(null) }
            }
        }

    /** First fix any of [providers] delivers after this call. Keeps them on until then. */
    @SuppressLint("MissingPermission")
    private suspend fun liveUpdate(manager: LocationManager, providers: List<String>): Location? =
        suspendCancellableCoroutine { cont ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (cont.isActive) {
                        manager.removeUpdates(this)
                        cont.resume(location)
                    }
                }

                // Required on API < 30, where these aren't default methods.
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) = Unit
            }
            cont.invokeOnCancellation { manager.removeUpdates(listener) }
            providers.forEach { provider ->
                runCatching { manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper()) }
            }
        }

    /** Age on the boot clock, so a wrong wall-clock or a mis-stamped fix can't pass as fresh. */
    private fun isRecent(location: Location): Boolean {
        val ageMs = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000
        return ageMs in 0..MAX_FIX_AGE_MS
    }

    private companion object {
        const val FRESH_FIX_TIMEOUT_MS = 8_000L
        const val CURRENT_LOCATION_TIMEOUT_MS = 3_000L
        const val MAX_FIX_AGE_MS = 2 * 60 * 1000L
        val PREFERRED_PROVIDERS = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, "fused")
    }
}
