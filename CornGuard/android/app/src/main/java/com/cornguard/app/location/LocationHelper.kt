package com.cornguard.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import androidx.core.content.getSystemService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Best-effort, permission-agnostic location lookup for optionally geotagging a scan
 * (claude/04_DEVELOPMENT_RULES.md #9 — location is optional metadata, denial must degrade
 * gracefully, never block scanning). Uses [LocationManager.getLastKnownLocation] rather than
 * requesting a fresh GPS fix — a possibly-stale or missing last-known fix is an acceptable
 * trade-off for "approximate location on a scan," and avoids the request/timeout/cancel
 * complexity a live fix would need. No Play Services Location dependency needed.
 *
 * Callers MUST check [com.cornguard.app.permissions.PermissionManager.isGranted] for
 * [com.cornguard.app.permissions.AppPermission.LOCATION] before calling — this class does not
 * check permissions itself, it just reads whatever the platform will give it.
 */
class LocationHelper(private val appContext: Context) {

    @SuppressLint("MissingPermission")
    suspend fun getLastKnownLocation(): Location? = withContext(Dispatchers.IO) {
        val manager = appContext.getSystemService<LocationManager>() ?: return@withContext null
        val providers = runCatching { manager.getProviders(true) }.getOrNull().orEmpty()
        providers
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
    }
}
