package com.cornguard.app.data.model

/**
 * Mirrors firebase/schema/logical-schema.md's DiagnosisRecordCloud fields relevant to mapping,
 * and firebase/gis/gis-service-interface.md's MapOccurrence value type. Sourced only from
 * diagnosisRecordsCloud (verified AI scans) — see [GisRepository] for why community posts feed
 * NearbyReportSummary instead, not this.
 */
data class MapOccurrence(
    val occurrenceId: String,
    val diseaseCode: String,
    val barangay: String,
    val municipality: String,
    val occurredAt: Long,
    val verificationStatus: String
)

/** Mirrors firebase/gis/gis-service-interface.md's NearbyReportSummary — sourced from communityPosts. */
data class NearbyReportSummary(
    val postId: String,
    val diseaseTag: String,
    val barangay: String,
    val municipality: String,
    val createdAt: Long,
    val verificationStatus: String
)

/**
 * One area's worth of aggregated occurrence counts. Mirrors
 * firebase/gis/gis-service-interface.md's getHeatmapAggregates return shape.
 */
data class HeatmapAggregate(
    val areaLabel: String,
    val count: Int,
    val diseaseBreakdown: Map<String, Int>
)
