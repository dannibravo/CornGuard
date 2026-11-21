package com.cornguard.app.data.repository

import com.cornguard.app.data.model.HeatmapAggregate
import com.cornguard.app.data.model.MapOccurrence
import com.cornguard.app.data.model.NearbyReportSummary

/**
 * GIS data layer: nearby-report queries, heatmap aggregation, and FCM device-token/topic
 * management. Mirrors firebase/docs/gis/gis-service-interface.md and
 * firebase/docs/notifications/fcm-plan.md.
 *
 * Built against Firestore per the Sprint 4 pragmatic solo-context continuation note under D-10
 * (claude/03_SOURCE_ALIGNMENT_AND_DECISION_GATES.md) — not a formal team decision. Deliberately
 * has no map-SDK dependency at all: this is the data layer only. Rendering it on an actual map
 * (Ligue's Map screen) needs the separate provider pick D-10 still requires.
 *
 * Online-only, same connectivity-handling expectation as the other Firebase-backed repositories.
 */
interface GisRepository {

    /**
     * Nearby reports for the *community* view — sourced from communityPosts (any
     * verification_status, since this is informational display, not outbreak confirmation; see
     * D-08). [areaFilter] is a single (field, value) pair, same shape as
     * CommunityRepository.getPostsFeed's.
     */
    suspend fun getNearbyReports(
        areaFilter: Pair<String, String>,
        diseaseFilter: String?,
        limit: Int = 50
    ): List<NearbyReportSummary>

    /**
     * Aggregated occurrence counts by area, for the heatmap. Sourced only from
     * diagnosisRecordsCloud where verification_status="verified" — per D-08, "unverified posts
     * must not be treated as confirmed outbreaks," and per D-07, no production outbreak signal
     * may go out without expert-approved validation. D-02 is now resolved (Admin verifies, via
     * AdminRepository.verifyDiagnosisRecord) so this empties only once nothing has actually been
     * verified yet, not because the verification path itself is unauthorized — it stays empty in
     * a fresh/dev environment until an Admin exercises that path, which is the intended
     * safeguard, not a bug to "fix" by loosening the filter.
     */
    suspend fun getHeatmapAggregates(areaField: String, diseaseFilter: String?): List<HeatmapAggregate>

    /**
     * Individual verified occurrences for map markers (as opposed to [getHeatmapAggregates]'s
     * blob-level counts). Sourced from the redacted `verifiedOccurrences` projection (Phase 4,
     * resolved 2026-10-30), not diagnosisRecordsCloud directly — that collection is owner/admin
     * read-only, so a community-wide map could never query it; every entry returned here already
     * has coordinates rounded to ~110m and carries no farmer-identifying fields, by construction
     * of how the projection is written (FirebaseAdminRepository.setDiagnosisVerification). Empty
     * until an admin has actually verified something with a location on it — that's the intended
     * safeguard (D-08), not a bug.
     */
    suspend fun getVerifiedOccurrences(
        areaFilter: Pair<String, String>,
        diseaseFilter: String?,
        limit: Int = 200
    ): List<MapOccurrence>

    /**
     * Registers or refreshes this device's FCM token. Uses a deterministic document id
     * (`{userId}-{deviceId}`) so a token refresh updates the existing document instead of
     * creating a duplicate, per fcm-plan.md's device token lifecycle.
     */
    suspend fun registerDeviceToken(userId: String, deviceId: String, fcmToken: String)

    /** Marks this device's token inactive (sign-out, uninstall detection, etc.) without deleting the record. */
    suspend fun deactivateDeviceToken(userId: String, deviceId: String)

    /**
     * Subscribes this device to area-scoped push notifications for [topic] (e.g.
     * `"barangay_malaybalay_poblacion"`), per fcm-plan.md's topic-naming convention. Call once
     * per area the farmer's profile is registered in, and again if their barangay/municipality
     * changes.
     */
    suspend fun subscribeToAreaTopic(topic: String)

    suspend fun unsubscribeFromAreaTopic(topic: String)
}
