package com.cornguard.app.data.local

import android.content.Context

/**
 * Last known Settings area (barangay / municipality / province) per account, so the Scan screen
 * can show it and file offline scans under it. The profile in Convex stays the source of truth:
 * this is only refreshed from it (see ConvexUserFarmRepository) and re-checked when a scan is shared.
 * Keyed by user id because one phone can be shared between accounts.
 */
class ProfileAreaStore(context: Context) {

    data class Area(val barangay: String, val municipality: String, val province: String)

    private val prefs = context.getSharedPreferences("profile_area", Context.MODE_PRIVATE)

    /** Null when unknown or when the profile has no barangay set. */
    fun get(uid: String): Area? {
        val barangay = prefs.getString("$uid.barangay", null).orEmpty()
        val municipality = prefs.getString("$uid.municipality", null).orEmpty()
        if (barangay.isBlank() || municipality.isBlank()) return null
        return Area(barangay, municipality, prefs.getString("$uid.province", null).orEmpty())
    }

    fun put(uid: String, barangay: String?, municipality: String?, province: String?) {
        prefs.edit().apply {
            barangay?.let { putString("$uid.barangay", it) }
            municipality?.let { putString("$uid.municipality", it) }
            province?.let { putString("$uid.province", it) }
        }.apply()
    }
}
