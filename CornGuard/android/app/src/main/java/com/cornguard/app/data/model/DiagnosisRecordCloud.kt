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
    val source: String,

    /** Present only if the original local scan captured a location (claude/04_DEVELOPMENT_RULES.md #9). */
    val latitude: Double?,
    val longitude: Double?,

    /** Set only by AdminRepository.verifyDiagnosisRecord/rejectDiagnosisRecord — see D-02. */
    val verifiedBy: String?,
    val verifiedAt: Long?,

    /**
     * Authoritative signal of whether an image is actually attached — Cloud Storage is an
     * optional dependency (not currently provisioned on cornguard-dev), so [imageUrl] can be
     * absent even when the original local scan had a photo. Do not infer image presence from
     * [imageUrl] alone.
     */
    val hasImage: Boolean
)
