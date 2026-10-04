package com.cornguard.app.data.repository.convex

import com.cornguard.app.data.model.BarangayStat
import com.cornguard.app.data.model.HeatmapAggregate
import com.cornguard.app.data.model.MapReport
import com.cornguard.app.data.remote.convex.BarangayStatDto
import com.cornguard.app.data.remote.convex.MapReportDto
import com.cornguard.app.data.remote.convex.observe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.cornguard.app.data.model.MapOccurrence
import com.cornguard.app.data.model.NearbyReportSummary
import com.cornguard.app.data.remote.convex.CornGuardConvexClient
import com.cornguard.app.data.remote.convex.HeatmapDto
import com.cornguard.app.data.remote.convex.NearbyReportDto
import com.cornguard.app.data.remote.convex.OccurrenceDto
import com.cornguard.app.data.remote.convex.convexArgs
import com.cornguard.app.data.remote.convex.execute
import com.cornguard.app.data.remote.convex.queryOnce
import com.cornguard.app.data.repository.GisRepository
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

/**
 * Map/area data from Convex, plus device-token registration for push. Area topics stay on FCM
 * itself (push delivery is the one part of Firebase still in use).
 *
 * `areaFilter` / `areaField` must name one of `barangay`, `municipality` or `province`.
 */
class ConvexGisRepository(
    private val client: CornGuardConvexClient,
    private val messaging: FirebaseMessaging
) : GisRepository {

    override suspend fun getNearbyReports(
        areaFilter: Pair<String, String>,
        diseaseFilter: String?,
        limit: Int
    ): List<NearbyReportSummary> = client.queryOnce<List<NearbyReportDto>>(
        "posts:nearbyReports",
        convexArgs(
            "areaField" to areaFilter.first,
            "areaValue" to areaFilter.second,
            "diseaseTag" to diseaseFilter,
            "limit" to limit
        )
    ).map {
        NearbyReportSummary(
            postId = it.postId,
            diseaseTag = it.diseaseTag,
            barangay = it.barangay,
            municipality = it.municipality,
            createdAt = it.createdAt.toLong(),
            verificationStatus = it.verificationStatus
        )
    }

    override suspend fun getHeatmapAggregates(areaField: String, diseaseFilter: String?): List<HeatmapAggregate> =
        client.queryOnce<List<HeatmapDto>>(
            "diagnosisRecords:heatmapAggregates",
            convexArgs("areaField" to areaField, "diseaseCode" to diseaseFilter)
        ).map { dto ->
            HeatmapAggregate(
                areaLabel = dto.areaLabel,
                count = dto.count.toInt(),
                diseaseBreakdown = dto.diseaseBreakdown.mapValues { it.value.toInt() }
            )
        }

    override suspend fun getVerifiedOccurrences(
        areaFilter: Pair<String, String>,
        diseaseFilter: String?,
        limit: Int
    ): List<MapOccurrence> = client.queryOnce<List<OccurrenceDto>>(
        "diagnosisRecords:verifiedOccurrences",
        convexArgs(
            "areaField" to areaFilter.first,
            "areaValue" to areaFilter.second,
            "diseaseCode" to diseaseFilter,
            "limit" to limit
        )
    ).map {
        MapOccurrence(
            occurrenceId = it.occurrenceId,
            diseaseCode = it.diseaseCode,
            barangay = it.barangay,
            municipality = it.municipality,
            occurredAt = it.occurredAt.toLong(),
            verificationStatus = it.verificationStatus
        )
    }

    override fun observeBarangayStats(): Flow<List<BarangayStat>> =
        client.observe<List<BarangayStatDto>>("barangayStats:getAllStats", emptyMap(), fallback = emptyList())
            .map { rows ->
                rows.map {
                    BarangayStat(
                        barangay = it.barangay,
                        municipality = it.municipality,
                        diseaseCode = it.diseaseCode,
                        severityTier = it.severityTier,
                        weightedScore = it.weightedScore,
                        distinctFarms = it.distinctFarms.toInt(),
                        rawReportCount = it.rawReportCount.toInt(),
                        isActiveOutbreak = it.isActiveOutbreak
                    )
                }
            }

    override fun observeMapReports(): Flow<List<MapReport>> =
        client.observe<List<MapReportDto>>("diagnosisRecords:mapReports", emptyMap(), fallback = emptyList())
            .map { rows ->
                rows.map {
                    MapReport(
                        recordId = it.recordId,
                        diseaseCode = it.diseaseCode,
                        confidence = it.confidence,
                        capturedAt = it.capturedAt.toLong(),
                        latitude = it.latitude,
                        longitude = it.longitude,
                        barangay = it.barangay,
                        municipality = it.municipality
                    )
                }
            }

    /** The backend keys tokens by the signed-in user, so [userId] isn't sent. */
    override suspend fun registerDeviceToken(userId: String, deviceId: String, fcmToken: String) {
        client.execute("devices:registerToken", convexArgs("deviceId" to deviceId, "fcmToken" to fcmToken))
    }

    override suspend fun deactivateDeviceToken(userId: String, deviceId: String) {
        client.execute("devices:deactivateToken", convexArgs("deviceId" to deviceId))
    }

    override suspend fun subscribeToAreaTopic(topic: String) {
        messaging.subscribeToTopic(topic).await()
    }

    override suspend fun unsubscribeFromAreaTopic(topic: String) {
        messaging.unsubscribeFromTopic(topic).await()
    }
}
