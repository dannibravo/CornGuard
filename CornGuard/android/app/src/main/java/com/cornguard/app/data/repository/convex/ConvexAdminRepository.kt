package com.cornguard.app.data.repository.convex

import com.cornguard.app.data.model.NotificationLogEntry
import com.cornguard.app.data.remote.convex.CornGuardConvexClient
import com.cornguard.app.data.remote.convex.NotificationDto
import com.cornguard.app.data.remote.convex.convexArgs
import com.cornguard.app.data.remote.convex.execute
import com.cornguard.app.data.remote.convex.queryOnce
import com.cornguard.app.data.repository.AdminRepository

/**
 * Admin actions. Every backend function re-checks that the signed-in user is an admin, so the
 * `adminUid` parameters are informational only — the reviewer is recorded from the session.
 */
class ConvexAdminRepository(
    private val client: CornGuardConvexClient
) : AdminRepository {

    override suspend fun verifyDiagnosisRecord(recordId: String, adminUid: String) =
        setRecordVerification(recordId, "verified")

    override suspend fun rejectDiagnosisRecord(recordId: String, adminUid: String) =
        setRecordVerification(recordId, "rejected")

    override suspend fun verifyPost(postId: String, adminUid: String) = setPostVerification(postId, "verified")

    override suspend fun rejectPost(postId: String, adminUid: String) = setPostVerification(postId, "rejected")

    override suspend fun setPostModerationStatus(postId: String, status: String) {
        client.execute("admin:setPostModeration", convexArgs("postId" to postId, "status" to status))
    }

    override suspend fun setCommentModerationStatus(postId: String, commentId: String, status: String) {
        client.execute("admin:setCommentModeration", convexArgs("commentId" to commentId, "status" to status))
    }

    override suspend fun setUserAccountStatus(uid: String, status: String) {
        client.execute("admin:setAccountStatus", convexArgs("userId" to uid, "status" to status))
    }

    override suspend fun promoteToAdmin(targetUid: String) {
        client.execute("admin:promoteToAdmin", convexArgs("userId" to targetUid))
    }

    /** Accepts the former Firestore field names (snake_case) as well as the Convex ones. */
    override suspend fun updateDiseaseReference(diseaseCode: String, fields: Map<String, Any>) {
        val args = fields.entries.map { (key, value) ->
            val field = REFERENCE_FIELDS[key] ?: key.takeIf { it in REFERENCE_FIELDS.values }
                ?: throw IllegalArgumentException("Unknown disease reference field '$key'")
            field to value
        }
        client.execute("admin:updateDiseaseReference", convexArgs("diseaseCode" to diseaseCode, *args.toTypedArray()))
    }

    override suspend fun getRecentNotifications(limit: Int): List<NotificationLogEntry> =
        client.queryOnce<List<NotificationDto>>("admin:recentNotifications", convexArgs("limit" to limit)).map {
            NotificationLogEntry(
                notificationId = it.notificationId,
                type = it.type,
                recipientUserId = it.recipientUserId,
                areaScope = it.areaScope,
                title = it.title,
                deliveryStatus = it.deliveryStatus,
                createdAt = it.createdAt.toLong()
            )
        }

    private suspend fun setRecordVerification(recordId: String, status: String) {
        client.execute("admin:setRecordVerification", convexArgs("recordId" to recordId, "status" to status))
    }

    private suspend fun setPostVerification(postId: String, status: String) {
        client.execute("admin:setPostVerification", convexArgs("postId" to postId, "status" to status))
    }

    private companion object {
        val REFERENCE_FIELDS = mapOf(
            "display_name" to "displayName",
            "symptoms" to "symptoms",
            "treatment_steps" to "treatmentSteps",
            "prevention_steps" to "preventionSteps",
            "causes" to "causes",
            "duration" to "duration",
            "source_reference" to "sourceReference",
            "content_version" to "contentVersion"
        )
    }
}
