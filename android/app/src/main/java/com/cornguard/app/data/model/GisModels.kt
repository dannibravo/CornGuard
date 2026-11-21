package com.cornguard.app.data.model

/**
 * Mirrors the verifiedOccurrences/{recordId} redacted projection (Phase 4, resolved
 * 2026-10-30) — NOT diagnosisRecordsCloud directly; that collection is owner/admin-read-only
 * (access-control-matrix.md), so a community-wide map cannot query it. [latitude]/[longitude] are
 * already rounded to 3 decimals server-side (~110m) when the projection is written — this type
 * never carries exact coordinates, a farm_id, or a user_id, because the source document doesn't
 * either. Every instance of this type is, by construction, an admin-verified occurrence — see
 * [GisRepository] for why unverified/community reports come from [NearbyReportSummary] instead.
 */
data class MapOccurrence(
    val occurrenceId: String,
    val diseaseCode: String,
    val latitude: Double,
    val longitude: Double,
    val barangay: String,
    val municipality: String,
    val occurredAt: Long,
    val hasImage: Boolean,
    val confidence: Float,
    val verificationStatus: String
)

/**
 * Mirrors firebase/docs/gis/gis-service-interface.md's NearbyReportSummary — sourced from
 * communityPosts, which is fully community-readable regardless of its own verification_status
 * (D-08) — distinct from [MapOccurrence]'s admin-verified diagnosis occurrences.
 * [linkedDiagnosisRecordId], when non-null, is the same recordId a [MapOccurrence] may exist
 * under — used to de-duplicate map markers when both sources reference the same underlying event.
 */
data class NearbyReportSummary(
    val postId: String,
    val diseaseTag: String,
    val latitude: Double?,
    val longitude: Double?,
    val linkedDiagnosisRecordId: String?,
    val barangay: String,
    val municipality: String,
    val createdAt: Long,
    val verificationStatus: String
)

/**
 * One area's worth of aggregated occurrence counts. Mirrors
 * firebase/docs/gis/gis-service-interface.md's getHeatmapAggregates return shape.
 */
data class HeatmapAggregate(
    val areaLabel: String,
    val count: Int,
    val diseaseBreakdown: Map<String, Int>
)
