package com.cornguard.app.data.local

import android.content.Context
import com.cornguard.app.data.repository.TreatmentGuideSync

/** When this install last received the server's treatment guides (0 = never: use the bundled snapshot). */
class TreatmentGuideSyncMarker(context: Context) : TreatmentGuideSync.SyncMarker {
    private val prefs = context.applicationContext.getSharedPreferences("treatment_guides", Context.MODE_PRIVATE)

    override fun lastSyncedAt(): Long = prefs.getLong(KEY, 0L)

    override fun markSynced(at: Long) {
        prefs.edit().putLong(KEY, at).apply()
    }

    private companion object {
        const val KEY = "lastSyncedAt"
    }
}
