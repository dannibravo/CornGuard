package com.cornguard.app.data.model

/**
 * One immutable entry in a diagnosisRecordsCloud/{recordId}/verificationHistory subcollection.
 * Append-only — security/firestore.rules rejects update/delete on this subcollection entirely.
 * Mirrors firebase/docs/schema/logical-schema.md exactly.
 */
data class VerificationHistoryEntry(
    val entryId: String,
    val action: String,
    val actorUserId: String,
    val at: Long,
    val note: String?
)
