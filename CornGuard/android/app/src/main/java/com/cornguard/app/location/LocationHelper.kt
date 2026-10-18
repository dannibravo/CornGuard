package com.cornguard.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
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
 * gracefully, never block scanning).
 *
 * Uses the platform's last-known fix when it is recent. Otherwise asks for one fresh fix, waiting
 * at most [FRESH_FIX_TIMEOUT_MS] — the last-known fix is often missing (no other app has used GPS
 * lately), which left scans with "GPS unavailable". GPS needs no internet, so this works offline.
 * Falls back to a stale last-known fix, or null. No Play Services Location dependency needed.
 *
 * Callers MUST check [com.cornguard.app.permissions.PermissionManager.isGranted] for
 * [com.cornguard.app.permissions.AppPermission.LOCATION] before calling — this class does not
 * check permissions itself, it just reads whatever the platform will give it.
 */
class LocationHelper(private val appContext: Context) {

    @SuppressLint("MissingPermission")
    suspend fun getLastKnownLocation(): Location? {
        val manager = appContext.getSystemService<LocationManager>() ?: return null
        val providers = runCatching { manager.getProviders(true) }.getOrNull().orEmpty()
        val lastKnown = withContext(Dispatchers.IO) {
            providers
                .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
                .maxByOrNull { it.time }
        }
        if (lastKnown != null && System.currentTimeMillis() - lastKnown.time <= MAX_FIX_AGE_MS) return lastKnown

        val provider = PREFERRED_PROVIDERS.firstOrNull { it in providers } ?: return lastKnown
        val fresh = withTimeoutOrNull(FRESH_FIX_TIMEOUT_MS) {
            suspendCancellableCoroutine<Location?> { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                runCatching {
                    LocationManagerCompat.getCurrentLocation(
                        manager, provider, signal, ContextCompat.getMainExecutor(appContext)
                    ) { location -> if (cont.isActive) cont.resume(location) }
                }.onFailure { if (cont.isActive) cont.resume(null) }
            }
        }
        return fresh ?: lastKnown
    }

    private companion object {
        const val MAX_FIX_AGE_MS = 2 * 60 * 1000L
        const val FRESH_FIX_TIMEOUT_MS = 8_000L
        val PREFERRED_PROVIDERS = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, "fused")
    }
}
