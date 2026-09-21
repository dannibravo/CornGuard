package com.cornguard.app.data.model

/**
 * Mirrors firebase/schema/logical-schema.md's diagnosisRecordsCloud/{recordId} shape and
 * firebase/repositories/diagnosis-sharing-repository-interface.md's DiagnosisRecordCloud value
 * type.
 *
 * There is deliberately no separate `ShareableScanDraft` Kotlin type: that concept in the
 * interface doc is exactly [com.cornguard.app.data.local.db.entity.DiagnosisRecordEntity], which
 * Ligue's local scan flow already produces — [DiagnosisSharingRepository.shareScan] takes that
 * entity directly rather than duplicating its fields into a second model.
 */
data class DiagnosisRecordCloud(
    val recordId: String,
    val userId: String,
    val farmId: String?,
    val diseaseCode: String,
    val confidence: Float,
    val imageUrl: String?,
    val capturedAt: Long,
    val barangay: String,
    val municipality: String,
    val province: String,
    val modelVersion: String,
    val verificationStatus: String,
    val source: String
)
