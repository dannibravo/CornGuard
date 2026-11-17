package com.cornguard.app.data.repository

import com.cornguard.app.data.local.db.entity.DiseaseReferenceEntity
import com.cornguard.app.data.remote.convex.TreatmentGuideDto
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Keeps the offline treatment-guide store ([DiseaseReferenceRepository], what every screen reads)
 * in step with the guides admins manage in the admin website:
 *
 *  1. Until this install has synced once, the bundled snapshot is loaded, so a farmer who scans
 *     before ever going online still gets guidance (Treatment Rule).
 *  2. Then it follows the server's live list. Each list it receives replaces the store exactly
 *     (edits and deletions included). A `null` update — offline, or a failed query — changes
 *     nothing: the last guides received stay readable.
 *
 * Plain Kotlin behind small interfaces, so the rules are unit-tested without Android or Convex.
 */
class TreatmentGuideSync(
    private val repository: DiseaseReferenceRepository,
    private val marker: SyncMarker,
    private val clock: () -> Long = System::currentTimeMillis
) {
    /** Remembers whether this install has ever received the server's guides. */
    interface SyncMarker {
        fun lastSyncedAt(): Long
        fun markSynced(at: Long)
    }

    /** Step 1. [readSnapshot] is only called when it's needed. */
    suspend fun loadSnapshotIfNeverSynced(readSnapshot: () -> String) {
        if (marker.lastSyncedAt() > 0L) return
        val guides = runCatching { parseSnapshot(readSnapshot()) }.getOrNull() ?: return
        // Upsert, not replace: an install that already has guides keeps any the snapshot lacks.
        repository.upsertAll(guides)
    }

    /** Step 2: applies one update from the server's live list. */
    suspend fun apply(update: List<TreatmentGuideDto>?) {
        if (update == null) return
        repository.replaceAll(update.map { it.toEntity() })
        marker.markSynced(clock())
    }

    /** Step 1 then step 2, in order, so a late snapshot can never overwrite newer server guides. */
    suspend fun run(readSnapshot: () -> String, updates: Flow<List<TreatmentGuideDto>?>) {
        loadSnapshotIfNeverSynced(readSnapshot)
        updates.collect { apply(it) }
    }

    companion object {
        const val SNAPSHOT_ASSET = "treatment/guides-snapshot.json"

        private val json = Json { ignoreUnknownKeys = true }

        fun parseSnapshot(text: String): List<DiseaseReferenceEntity> =
            json.decodeFromString(ListSerializer(TreatmentGuideDto.serializer()), text).map { it.toEntity() }
    }
}

fun TreatmentGuideDto.toEntity() = DiseaseReferenceEntity(
    diseaseCode = diseaseCode,
    displayName = displayName,
    symptoms = symptoms,
    treatmentSteps = treatmentSteps,
    preventionSteps = preventionSteps,
    causes = causes,
    duration = duration,
    sourceReference = sourceReference,
    contentVersion = contentVersion,
    updatedAt = updatedAt.toLong(),
    reviewStatus = reviewStatus,
    reviewedBy = reviewedBy,
    reviewedAt = reviewedAt.toLong()
)
