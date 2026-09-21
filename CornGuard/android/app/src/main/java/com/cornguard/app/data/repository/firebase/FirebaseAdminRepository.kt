package com.cornguard.app.data.repository.firebase

import com.cornguard.app.data.model.NotificationLogEntry
import com.cornguard.app.data.repository.AdminRepository
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

/**
 * Firestore + Cloud Functions-backed implementation. Field names match
 * firebase/schema/logical-schema.md exactly — see [AdminRepository] for the authorization model
 * (enforced server-side, not re-checked here).
 */
class FirebaseAdminRepository(
    private val db: FirebaseFirestore,
    private val functions: FirebaseFunctions
) : AdminRepository {

    override suspend fun verifyDiagnosisRecord(recordId: String, adminUid: String) {
        setVerification(RECORDS_COLLECTION, recordId, "verified", adminUid)
    }

    override suspend fun rejectDiagnosisRecord(recordId: String, adminUid: String) {
        setVerification(RECORDS_COLLECTION, recordId, "rejected", adminUid)
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
