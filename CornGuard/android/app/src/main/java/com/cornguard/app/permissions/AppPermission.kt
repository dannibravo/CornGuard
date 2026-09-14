package com.cornguard.app.permissions

import android.Manifest
import android.os.Build

/**
 * Runtime permissions used by CORNGUARD, mapped to their platform manifest string(s). Grouped by
 * the offline/online boundary they belong to (claude/02_PROJECT_CONTEXT.md) so each screen only
 * ever requests what its own feature needs.
 */
enum class AppPermission(val manifestPermissions: List<String>) {

    /** Scan Corn Leaf — camera capture. Offline core. */
    CAMERA(listOf(Manifest.permission.CAMERA)),

    /** Scan Corn Leaf — gallery selection. Offline core. */
    GALLERY(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    ),

    /**
     * Attaching approximate location to a scan/report and nearby-report/GIS queries. Online
     * feature; denial must degrade gracefully, never block scanning
     * (claude/04_DEVELOPMENT_RULES.md #2, #9).
     */
    LOCATION(
        listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    ),

    /** Outbreak/community push notifications. Required at runtime on API 33+ only. */
    NOTIFICATIONS(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyList()
        }
    );
}
