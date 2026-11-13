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
 * Outbreak level for one barangay + municipality + disease over the last 7 days, compared with the
 * barangay's own weekly baseline (backend/convex/barangayStats.ts). Colours the heatmap's dots.
 */
data class BarangayStat(
    val barangay: String,
    val municipality: String,
    val diseaseCode: String,
    /** "mild", "moderate" or "severe" (always "mild" while [monitoringStatus] is "collecting"). */
    val severityTier: String,
    val weightedScore: Double,
    val distinctFarms: Int,
    val rawReportCount: Int,
    val isActiveOutbreak: Boolean,
    /** "collecting" (not enough data yet: outbreak status Undetermined), "provisional" or "established". */
    val monitoringStatus: String = "collecting",
    /** Usual farms reporting this disease per week; null until computed. */
    val baselineFarms: Double? = null,
    /** Farms in the 7 days before the current 7 (trend). */
    val lastWeekFarms: Int? = null,
    /** Baseline weeks in which the barangay shared any scan. */
    val dataWeeks: Int = 0,
    /** Verified scans of any kind in those weeks (warm-up progress). */
    val historyScans: Int = 0,
    /** The barangay's first verified scan (warm-up progress). */
    val monitoringSince: Long? = null
)

/** One verified shared scan with coordinates — one dot on the heatmap. */
data class MapReport(
    val recordId: String,
    val diseaseCode: String,
    val confidence: Double,
    val capturedAt: Long,
    val latitude: Double,
    val longitude: Double,
    val barangay: String,
    val municipality: String,
    /** "active" (last 14 days, counted in the level) or "recent" (14-28 days, drawn faded). */
    val stage: String = "active",
    /** Who reported it, shown in the dot's popup. */
    val farmerName: String = "Farmer"
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
