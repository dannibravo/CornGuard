package com.cornguard.app.ui.map

import com.cornguard.app.data.model.BarangayStat

/**
 * What the outbreak map's bottom sheet says about one barangay: its level this week for the
 * selected disease filter, and which stat row the explanation sentence is about. Pure (no Android
 * types) so it is unit-tested (MapAreaSummaryTest). The level comes from the backend's severity
 * tier; intensity only orders rows of the same tier.
 */
object MapAreaSummary {

    enum class Level { NONE, COLLECTING, MILD, MODERATE, SEVERE }

    data class Summary(
        val level: Level,
        /** The row the explanation is about (hottest one); null when there are no reports. */
        val lead: BarangayStat?,
        /** Other diseases reported here (same filter), hottest first. */
        val others: List<BarangayStat>
    )

    /** Warm-up before detection starts. KEEP IN SYNC with OUTBREAK_RULES in backend/convex/lib/outbreakRules.ts. */
    const val WARMUP_WEEKS = 4
    const val WARMUP_SCANS = 10
    private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

    /**
     * @param diseaseFilter null = all diseases, otherwise one disease code (the sheet's pills).
     */
    fun summarize(
        stats: List<BarangayStat>,
        barangay: String,
        municipality: String,
        diseaseFilter: String?
    ): Summary {
        val here = stats.filter {
            it.barangay == barangay && it.municipality == municipality &&
                (diseaseFilter == null || it.diseaseCode == diseaseFilter) &&
                it.distinctFarms > 0
        }
        val (collecting, monitored) = here.partition { it.monitoringStatus == "collecting" }
        val ordered = monitored.sortedWith(
            compareByDescending<BarangayStat> { tierRank(it.severityTier) }
                .thenByDescending { it.intensity ?: -1.0 }
                .thenByDescending { it.distinctFarms }
        ) + collecting.sortedByDescending { it.distinctFarms }
        val lead = ordered.firstOrNull() ?: return Summary(Level.NONE, null, emptyList())
        val level = if (lead.monitoringStatus == "collecting") Level.COLLECTING else when (lead.severityTier) {
            "severe" -> Level.SEVERE
            "moderate" -> Level.MODERATE
            else -> Level.MILD
        }
        return Summary(level, lead, ordered.drop(1))
    }

    /** Whole weeks of data collected so far, capped at the warm-up length. */
    fun warmupWeeks(stat: BarangayStat, now: Long): Int {
        val since = stat.monitoringSince ?: return 0
        return ((now - since) / WEEK_MS).toInt().coerceIn(0, WARMUP_WEEKS)
    }

    private fun tierRank(tier: String) = when (tier) {
        "severe" -> 2
        "moderate" -> 1
        else -> 0
    }
}
