package com.cornguard.app

import android.app.Application
import com.cornguard.app.di.ServiceLocator
import org.osmdroid.config.Configuration

/**
 * Application entry point. Owns process-lifetime singletons via [ServiceLocator] rather than a
 * DI framework — kept deliberately minimal for Sprint 0 (claude/05_DEVELOPMENT_PLAN.md).
 * Swapping in Hilt/Koin later is an internal engineering decision, not a contract change
 * (claude/01_MASTER_DEVELOPMENT_CONTEXT.md priority list, item 6).
 */
class CornGuardApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        configureOsmdroid()
    }

    /**
     * One-time, process-lifetime osmdroid setup (Phase 3, D-10). Must run before any MapView is
     * created. [userAgentValue] is required by OpenStreetMap's tile usage policy — requests
     * without a descriptive User-Agent get blocked/rate-limited by the tile server. The base path
     * points at app-specific external storage (no WRITE_EXTERNAL_STORAGE needed on API 26+,
     * cleaned up automatically on uninstall) — this is also where osmdroid's built-in tile cache
     * lives, so setting it correctly is what makes offline tile reuse work at all.
     */
    private fun configureOsmdroid() {
        val config = Configuration.getInstance()
        config.userAgentValue = packageName
        val basePath = getExternalFilesDir(null) ?: filesDir
        config.osmdroidBasePath = basePath
        config.osmdroidTileCache = basePath.resolve("osmdroid/tiles")
    }
}
