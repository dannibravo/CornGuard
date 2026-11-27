package com.cornguard.app.ui.home

import android.content.Context
import com.cornguard.app.ui.map.MapAreaSummary

/**
 * The last outbreak level Home showed for an account's barangay, so the card still says something
 * useful offline ("Last updated 2 h ago · connect to refresh"). Display cache only: never used to
 * decide anything, and replaced every time live stats arrive.
 */
class HomeAreaCache(context: Context) {

    data class Entry(
        val barangay: String,
        val municipality: String,
        val level: MapAreaSummary.Level,
        val explanation: String,
        val updatedAt: Long
    )

    private val prefs = context.getSharedPreferences("home_area_level", Context.MODE_PRIVATE)

    /** The cached entry for [uid], only if it is for this same barangay. */
    fun get(uid: String, barangay: String, municipality: String): Entry? {
        if (prefs.getString("$uid.barangay", null) != barangay) return null
        if (prefs.getString("$uid.municipality", null) != municipality) return null
        val level = prefs.getString("$uid.level", null)
            ?.let { name -> MapAreaSummary.Level.entries.firstOrNull { it.name == name } } ?: return null
        return Entry(
            barangay, municipality, level,
            prefs.getString("$uid.explanation", null).orEmpty(),
            prefs.getLong("$uid.updatedAt", 0L)
        )
    }

    fun put(uid: String, entry: Entry) {
        prefs.edit()
            .putString("$uid.barangay", entry.barangay)
            .putString("$uid.municipality", entry.municipality)
            .putString("$uid.level", entry.level.name)
            .putString("$uid.explanation", entry.explanation)
            .putLong("$uid.updatedAt", entry.updatedAt)
            .apply()
    }
}
