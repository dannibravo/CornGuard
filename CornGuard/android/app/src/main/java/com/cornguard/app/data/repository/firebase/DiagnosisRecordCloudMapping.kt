package com.cornguard.app.data.repository.firebase

import com.cornguard.app.data.model.DiagnosisRecordCloud
import com.google.firebase.firestore.DocumentSnapshot

/**
 * Shared diagnosisRecordsCloud document mapper — used by both
 * [FirebaseDiagnosisSharingRepository] (a farmer's own shared records) and
 * [FirebaseAdminRepository] (pending records for the admin review queue), so the field mapping
 * and backward-compatibility fallback (see [hasImage][DiagnosisRecordCloud.hasImage]) can't drift
 * between the two call sites.
 */
internal fun DocumentSnapshot.toDiagnosisRecordCloud(): DiagnosisRecordCloud? {
    if (!exists()) return null
    val diseaseCode = getString("disease_code") ?: return null
    return DiagnosisRecordCloud(
        recordId = id,
        userId = getString("user_id") ?: return null,
        farmId = getString("farm_id"),
        diseaseCode = diseaseCode,
        confidence = getDouble("confidence")?.toFloat() ?: 0f,
        imageUrl = getString("image_url"),
        capturedAt = getLong("captured_at") ?: 0L,
        barangay = getString("barangay") ?: "",
        municipality = getString("municipality") ?: "",
        province = getString("province") ?: "",
        modelVersion = getString("model_version") ?: "",
        verificationStatus = getString("verification_status") ?: "unverified",
        source = getString("source") ?: "ai_scan",
        latitude = getDouble("latitude"),
        longitude = getDouble("longitude"),
        verifiedBy = getString("verified_by"),
        verifiedAt = getLong("verified_at"),
        // Falls back to inferring from image_url for documents written before has_image
        // existed, so older records remain correctly interpretable without a backfill.
        hasImage = getBoolean("has_image") ?: (getString("image_url") != null)
    )
}
