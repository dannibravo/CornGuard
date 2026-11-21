package com.cornguard.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local persistence for `LocalDiagnosisRecord` (claude/08_DATA_AND_INTEGRATION_CONTRACT.md).
 * `local_id` is a stable local-only key; a cloud sync must never overwrite it — the synced
 * record's remote id is kept separately in [cloudRecordId]
 * (claude/04_DEVELOPMENT_RULES.md #16).
 *
 * A scan is private/local by default: [sharedToCloud] starts `false` and [cloudRecordId] starts
 * `null` until the farmer explicitly shares it (D-11).
 */
@Entity(tableName = "diagnosis_records")
data class DiagnosisRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val localId: Long = 0,

    /** Nullable: a scan can be performed before/without a cloud identity being available. */
    val userId: String?,

    /** Nullable until the Farm entity's cardinality is approved (D-03). */
    val farmId: String?,

    val diseaseCode: String,
    val displayLabel: String,

    /** 0.0–1.0, not 0–100 — see the Conventions section of claude/08_DATA_AND_INTEGRATION_CONTRACT.md. */
    val confidence: Float,

    val imageUriOrLocalPath: String,

    /** Epoch millis, UTC. */
    val capturedAt: Long,

    val latitude: Double?,
    val longitude: Double?,
    val barangay: String?,
    val municipality: String?,

    /** Not hardcoded to Bukidnon — records can exist elsewhere (claude/08_DATA_AND_INTEGRATION_CONTRACT.md). */
    val province: String?,

    val modelVersion: String,
    val sharedToCloud: Boolean = false,
    val cloudRecordId: String? = null
)
