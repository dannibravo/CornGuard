package com.cornguard.app.data.repository

import com.cornguard.app.data.model.NotificationLogEntry

/**
 * Admin authorization checks, moderation, user management, disease-reference maintenance, and
 * notification monitoring (Sprint 5). Every method here corresponds to an operation
 * security/firestore.rules only allows an `isAdmin()` caller to perform — this repository does
 * not re-check that client-side (claude/04_DEVELOPMENT_RULES.md #14: "Client-side UI hiding is
 * not authorization"). A non-admin caller gets a permission-denied failure from Firestore/Cloud
 * Functions itself, same as any other repository here.
 *
 * Verifying a diagnosis record or post is NOT blocked by D-02 (Agricultural Technician role) —
 * Admin is already a confirmed role, distinct from the conditional Technician role D-02 gates
 * (claude/03_SOURCE_ALIGNMENT_AND_DECISION_GATES.md). It IS still subject to D-07/D-08: verifying
 * individual records here does not create or enable any production outbreak-alert trigger — that
 * stays a separate, still-gated concern (see GisRepository's doc comments).
 *
 * Online-only, same connectivity-handling expectation as the other Firebase-backed repositories.
 */
interface AdminRepository {

    suspend fun verifyDiagnosisRecord(recordId: String, adminUid: String)
    suspend fun rejectDiagnosisRecord(recordId: String, adminUid: String)

    suspend fun verifyPost(postId: String, adminUid: String)
    suspend fun rejectPost(postId: String, adminUid: String)

    /** [status] is one of "visible" | "hidden" | "removed". */
    suspend fun setPostModerationStatus(postId: String, status: String)
    suspend fun setCommentModerationStatus(postId: String, commentId: String, status: String)

    /** [status] is one of "active" | "suspended". */
    suspend fun setUserAccountStatus(uid: String, status: String)

    /**
     * Calls the promoteToAdmin Cloud Function (firebase/functions/index.mjs) — grants the
     * `role: admin` custom auth claim, which is what security/firestore.rules actually checks via
     * isAdmin(). Fails with permission-denied if the caller isn't already an admin themselves; the
     * very first admin must be granted out-of-band (firebase/scripts/bootstrap-first-admin.mjs),
     * not through this method.
     */
    suspend fun promoteToAdmin(targetUid: String)

    suspend fun updateDiseaseReference(diseaseCode: String, fields: Map<String, Any>)

    suspend fun getRecentNotifications(limit: Int = 50): List<NotificationLogEntry>
}
