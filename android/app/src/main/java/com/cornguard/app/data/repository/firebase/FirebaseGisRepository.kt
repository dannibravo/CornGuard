package com.cornguard.app.data.repository.firebase

import com.cornguard.app.data.model.HeatmapAggregate
import com.cornguard.app.data.model.MapOccurrence
import com.cornguard.app.data.model.NearbyReportSummary
import com.cornguard.app.data.repository.GisRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

private const val POSTS_COLLECTION = "communityPosts"
private const val RECORDS_COLLECTION = "diagnosisRecordsCloud"
private const val VERIFIED_OCCURRENCES_COLLECTION = "verifiedOccurrences"
private const val DEVICE_TOKENS_COLLECTION = "deviceTokens"

/**
 * Firestore + FCM-backed implementation. See [GisRepository] for why [getHeatmapAggregates]
 * correctly returns empty until D-02/D-08 verification exists — that is not a bug.
 */
class FirebaseGisRepository(
    private val db: FirebaseFirestore,
    private val messaging: FirebaseMessaging
) : GisRepository {

    override suspend fun getNearbyReports(
        areaFilter: Pair<String, String>,
        diseaseFilter: String?,
        limit: Int
    ): List<NearbyReportSummary> {
        var query: Query = db.collection(POSTS_COLLECTION)
            .whereEqualTo("moderation_status", "visible")
            .whereEqualTo(areaFilter.first, areaFilter.second)

        diseaseFilter?.let { query = query.whereEqualTo("disease_tag", it) }
        query = query.orderBy("created_at", Query.Direction.DESCENDING).limit(limit.toLong())

        val snapshot = query.get().await()
        return snapshot.documents.mapNotNull { doc ->
            val diseaseTag = doc.getString("disease_tag") ?: return@mapNotNull null
            NearbyReportSummary(
                postId = doc.id,
                diseaseTag = diseaseTag,
                latitude = doc.getDouble("latitude"),
                longitude = doc.getDouble("longitude"),
                linkedDiagnosisRecordId = doc.getString("linked_diagnosis_record_id"),
                barangay = doc.getString("barangay") ?: "",
                municipality = doc.getString("municipality") ?: "",
                createdAt = doc.getTimestamp("created_at")?.toDate()?.time ?: 0L,
                verificationStatus = doc.getString("verification_status") ?: "unverified"
            )
        }
    }

    override suspend fun getHeatmapAggregates(
        areaField: String,
        diseaseFilter: String?
    ): List<HeatmapAggregate> {
        var query: Query = db.collection(RECORDS_COLLECTION)
            // Deliberately hardcoded to "verified" — see the interface doc comment. Not a filter
            // parameter, so a caller can't accidentally loosen the outbreak safeguard.
            .whereEqualTo("verification_status", "verified")

        diseaseFilter?.let { query = query.whereEqualTo("disease_code", it) }
        query = query.limit(1000) // capstone-scale bound; revisit if this ever needs pagination.

        val snapshot = query.get().await()

        val counts = mutableMapOf<String, Int>()
        val breakdowns = mutableMapOf<String, MutableMap<String, Int>>()

        for (doc in snapshot.documents) {
            val area = doc.getString(areaField) ?: continue
            val disease = doc.getString("disease_code") ?: continue
            counts[area] = (counts[area] ?: 0) + 1
            val breakdown = breakdowns.getOrPut(area) { mutableMapOf() }
            breakdown[disease] = (breakdown[disease] ?: 0) + 1
        }

        return counts.map { (area, count) ->
            HeatmapAggregate(areaLabel = area, count = count, diseaseBreakdown = breakdowns[area] ?: emptyMap())
        }
    }

    /**
     * Reads the redacted [VERIFIED_OCCURRENCES_COLLECTION] projection (Phase 4, resolved
     * 2026-10-30) — NOT diagnosisRecordsCloud. That collection is owner/admin-read-only by
     * design (access-control-matrix.md), so this used to be permission-denied for every
     * non-admin caller; this projection is what "an admin verified it" actually makes visible to
     * the wider signed-in community, with coordinates already rounded and no owner-identifying
     * fields, by construction of how FirebaseAdminRepository writes it.
     */
    override suspend fun getVerifiedOccurrences(
        areaFilter: Pair<String, String>,
        diseaseFilter: String?,
        limit: Int
    ): List<MapOccurrence> {
        var query: Query = db.collection(VERIFIED_OCCURRENCES_COLLECTION)
            .whereEqualTo(areaFilter.first, areaFilter.second)

        diseaseFilter?.let { query = query.whereEqualTo("disease_code", it) }
        query = query.limit(limit.toLong())

        val snapshot = query.get().await()
        return snapshot.documents.mapNotNull { doc ->
            val diseaseCode = doc.getString("disease_code") ?: return@mapNotNull null
            val latitude = doc.getDouble("latitude") ?: return@mapNotNull null
            val longitude = doc.getDouble("longitude") ?: return@mapNotNull null
            MapOccurrence(
                occurrenceId = doc.id,
                diseaseCode = diseaseCode,
                latitude = latitude,
                longitude = longitude,
                barangay = doc.getString("barangay") ?: "",
                municipality = doc.getString("municipality") ?: "",
                occurredAt = doc.getLong("captured_at") ?: 0L,
                hasImage = doc.getBoolean("has_image") ?: false,
                confidence = doc.getDouble("confidence")?.toFloat() ?: 0f,
                verificationStatus = "verified"
            )
        }
    }

    override suspend fun registerDeviceToken(userId: String, deviceId: String, fcmToken: String) {
        val data = mapOf(
            "user_id" to userId,
            "device_id_or_installation_id" to deviceId,
            "fcm_token" to fcmToken,
            "active" to true
        )
        db.collection(DEVICE_TOKENS_COLLECTION).document("$userId-$deviceId").set(data).await()
    }

    override suspend fun deactivateDeviceToken(userId: String, deviceId: String) {
        db.collection(DEVICE_TOKENS_COLLECTION).document("$userId-$deviceId")
            .update("active", false).await()
    }

    override suspend fun subscribeToAreaTopic(topic: String) {
        messaging.subscribeToTopic(topic).await()
    }

    override suspend fun unsubscribeFromAreaTopic(topic: String) {
        messaging.unsubscribeFromTopic(topic).await()
    }
}
