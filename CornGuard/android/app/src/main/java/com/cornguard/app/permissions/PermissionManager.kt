package com.cornguard.app.permissions

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Thin, framework-free permission-state check reusable from any screen. Sprint 0 scope is the
 * strategy/plumbing only — per-screen request flows (rationale UI, denied-state fallbacks) are
 * built alongside the feature that needs them (Sprint 1+), per
 * claude/05_DEVELOPMENT_PLAN.md Sprint 0 ("permission strategy" only, not full request UX).
 *
 * A denied permission must never block the offline scan path itself; only the specific input
 * method (camera vs. gallery) or optional metadata (location) it gates.
 */
class PermissionManager(private val appContext: Context) {

    /** True if every manifest permission backing [permission] is currently granted. */
    fun isGranted(permission: AppPermission): Boolean {
        if (permission.manifestPermissions.isEmpty()) return true
        return permission.manifestPermissions.all { manifestPermission ->
            ContextCompat.checkSelfPermission(
                appContext,
                manifestPermission
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /** Manifest permission strings still missing for [permission], for use with an Activity Result launcher. */
    fun missingManifestPermissions(permission: AppPermission): List<String> {
        return permission.manifestPermissions.filter { manifestPermission ->
            ContextCompat.checkSelfPermission(
                appContext,
                manifestPermission
            ) != PackageManager.PERMISSION_GRANTED
        }
    }
}
