package com.cornguard.app.data.repository.firebase

import com.cornguard.app.data.model.DiagnosisRecordCloud
import com.cornguard.app.data.model.NotificationLogEntry
import com.cornguard.app.data.model.VerificationHistoryEntry
import com.cornguard.app.data.repository.AdminRepository
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await
import java.util.Date

private const val RECORDS_COLLECTION = "diagnosisRecordsCloud"
private const val POSTS_COLLECTION = "communityPosts"
private const val USERS_COLLECTION = "users"
private const val DISEASE_REFERENCE_COLLECTION = "diseaseReferenceCloud"
private const val NOTIFICATIONS_COLLECTION = "notifications"
private const val VERIFICATION_HISTORY_SUBCOLLECTION = "verificationHistory"
private const val VERIFIED_OCCURRENCES_COLLECTION = "verifiedOccurrences"

/**
 * Firestore + Cloud Functions-backed implementation. Field names match
 * firebase/schema/logical-schema.md exactly — see [AdminRepository] for the authorization model
 * (enforced server-side, not re-checked here).
 */
class FirebaseAdminRepository(
    private val db: FirebaseFirestore,
    private val functions: FirebaseFunctions
) : AdminRepository {

    override suspend fun verifyDiagnosisRecord(recordId: String, adminUid: String, note: String?) {
        setDiagnosisVerification(recordId, "verified", adminUid, note)
    }

    override suspend fun rejectDiagnosisRecord(recordId: String, adminUid: String, note: String?) {
        setDiagnosisVerification(recordId, "rejected", adminUid, note)
    }

    /**
     * Updates the record's verification fields, appends one verificationHistory entry, AND
     * creates/updates/deletes its redacted [VERIFIED_OCCURRENCES_COLLECTION] projection — all in
     * a single [WriteBatch][com.google.firebase.firestore.WriteBatch] (Phase 4, resolved
     * 2026-10-30). All writes commit together or not at all, so the record's status, its audit
     * trail, and its public map projection can never diverge. Only diagnosis records get a
     * history subcollection/projection this phase — verifyPost/rejectPost below are deliberately
     * untouched and still use the simple single-document update they always did.
     *
     * Reads the current record BEFORE building the batch (Firestore write batches cannot contain
     * reads) — this is the source for the projection's redacted fields. If verifying a record
     * with no latitude/longitude, no projection is written at all (never invent coordinates, per
     * claude/04_DEVELOPMENT_RULES.md and this phase's explicit constraint) — verification still
     * proceeds normally, the record just won't produce a map marker.
     */
    private suspend fun setDiagnosisVerification(recordId: String, status: String, adminUid: String, note: String?) {
        val recordRef = db.collection(RECORDS_COLLECTION).document(recordId)
        val historyRef = recordRef.collection(VERIFICATION_HISTORY_SUBCOLLECTION).document()
        val occurrenceRef = db.collection(VERIFIED_OCCURRENCES_COLLECTION).document(recordId)
        val now = Date()

        val historyEntry = buildMap<String, Any> {
            put("action", status)
            put("actor_user_id", adminUid)
            put("at", now)
            note?.let { put("note", it) }
        }

        val batch = db.batch()
        batch.update(
            recordRef,
            mapOf(
                "verification_status" to status,
                "verified_by" to adminUid,
                "verified_at" to now
            )
        )
        batch.set(historyRef, historyEntry)

        if (status == "verified") {
            val record = recordRef.get().await()
            val latitude = record.getDouble("latitude")
            val longitude = record.getDouble("longitude")
            if (latitude != null && longitude != null) {
                batch.set(occurrenceRef, buildPublicOccurrenceProjection(record, latitude, longitude, now))
            }
            // else: no coordinates on the source record — no projection, no marker. Not an error.
        } else {
            // Covers both a fresh reject (no projection ever existed — harmless no-op delete) and
            // reversing a prior verify (removes the now-stale public projection).
            batch.delete(occurrenceRef)
        }

        batch.commit().await()
    }

    /**
     * Redacted fields only — deliberately excludes user_id, farm_id, verified_by, and the
     * record's exact (un-rounded) coordinates. [latitude]/[longitude] are rounded to 3 decimal
     * places (~110m) before being included, per access-control-matrix.md Rule #9.
     */
    private fun buildPublicOccurrenceProjection(
        record: DocumentSnapshot,
        latitude: Double,
        longitude: Double,
        verifiedAt: Date
    ): Map<String, Any> = buildMap {
        put("disease_code", record.getString("disease_code") ?: "")
        put("latitude", roundToThreeDecimals(latitude))
        put("longitude", roundToThreeDecimals(longitude))
        put("barangay", record.getString("barangay") ?: "")
        put("municipality", record.getString("municipality") ?: "")
        put("province", record.getString("province") ?: "")
        put("captured_at", record.getLong("captured_at") ?: 0L)
        put("has_image", record.getBoolean("has_image") ?: false)
        put("confidence", record.getDouble("confidence") ?: 0.0)
        put("verified_at", verifiedAt)
    }

    private fun roundToThreeDecimals(value: Double): Double =
        Math.round(value * 1000.0) / 1000.0

    override suspend fun getPendingDiagnosisRecords(limit: Int): List<DiagnosisRecordCloud> {
        val snapshot = db.collection(RECORDS_COLLECTION)
            .whereEqualTo("verification_status", "unverified")
            .limit(limit.toLong())
            .get()
            .await()
        // Sorted client-side (oldest-first) rather than via Firestore orderBy, so this query
        // stays a single-field equality filter and needs no composite index.
        return snapshot.documents.mapNotNull { it.toDiagnosisRecordCloud() }
            .sortedBy { it.capturedAt }
    }

    override suspend fun getVerificationHistory(recordId: String): List<VerificationHistoryEntry> {
        val snapshot = db.collection(RECORDS_COLLECTION).document(recordId)
            .collection(VERIFICATION_HISTORY_SUBCOLLECTION)
            .get()
            .await()
        return snapshot.documents.mapNotNull { doc ->
            val action = doc.getString("action") ?: return@mapNotNull null
            val actorUserId = doc.getString("actor_user_id") ?: return@mapNotNull null
            VerificationHistoryEntry(
                entryId = doc.id,
                action = action,
                actorUserId = actorUserId,
                at = doc.getTimestamp("at")?.toDate()?.time ?: 0L,
                note = doc.getString("note")
            )
        }.sortedBy { it.at }
    }

    override suspend fun verifyPost(postId: String, adminUid: String) {
        setVerification(POSTS_COLLECTION, postId, "verified", adminUid)
    }

    override suspend fun rejectPost(postId: String, adminUid: String) {
        setVerification(POSTS_COLLECTION, postId, "rejected", adminUid)
    }

    private suspend fun setVerification(collection: String, docId: String, status: String, adminUid: String) {
        db.collection(collection).document(docId).update(
            mapOf(
                "verification_status" to status,
                "verified_by" to adminUid,
                "verified_at" to Date()
            )
        ).await()
    }

    override suspend fun setPostModerationStatus(postId: String, status: String) {
        db.collection(POSTS_COLLECTION).document(postId)
            .update("moderation_status", status).await()
    }

    override suspend fun setCommentModerationStatus(postId: String, commentId: String, status: String) {
        db.collection(POSTS_COLLECTION).document(postId)
            .collection("comments").document(commentId)
            .update("moderation_status", status).await()
    }

    override suspend fun setUserAccountStatus(uid: String, status: String) {
        db.collection(USERS_COLLECTION).document(uid)
            .update("account_status", status).await()
    }

    override suspend fun promoteToAdmin(targetUid: String) {
        functions.getHttpsCallable("promoteToAdmin")
            .call(mapOf("targetUid" to targetUid))
            .await()
    }

    override suspend fun updateDiseaseReference(diseaseCode: String, fields: Map<String, Any>) {
        db.collection(DISEASE_REFERENCE_COLLECTION).document(diseaseCode).update(fields).await()
    }

    override suspend fun getRecentNotifications(limit: Int): List<NotificationLogEntry> {
        val snapshot = db.collection(NOTIFICATIONS_COLLECTION)
            .orderBy("created_at", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .get()
            .await()

        return snapshot.documents.map { doc ->
            NotificationLogEntry(
                notificationId = doc.id,
                type = doc.getString("type") ?: "",
                recipientUserId = doc.getString("recipient_user_id"),
                areaScope = doc.getString("area_scope"),
                title = doc.getString("title") ?: "",
                deliveryStatus = doc.getString("delivery_status") ?: "pending",
                createdAt = doc.getTimestamp("created_at")?.toDate()?.time ?: 0L
            )
        }
    }
}
