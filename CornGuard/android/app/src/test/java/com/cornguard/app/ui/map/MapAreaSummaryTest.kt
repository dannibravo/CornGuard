package com.cornguard.app.ui.map

import com.cornguard.app.data.model.BarangayStat
import com.cornguard.app.ui.map.MapAreaSummary.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapAreaSummaryTest {

    private fun stat(
        disease: String,
        tier: String = "mild",
        farms: Int = 2,
        status: String = "provisional",
        barangay: String = "Poblacion",
        intensity: Double? = null,
        since: Long? = null
    ) = BarangayStat(
        barangay = barangay,
        municipality = "Valencia",
        diseaseCode = disease,
        severityTier = tier,
        weightedScore = farms.toDouble(),
        distinctFarms = farms,
        rawReportCount = farms,
        isActiveOutbreak = tier != "mild",
        monitoringStatus = status,
        monitoringSince = since,
        intensity = intensity
    )

    @Test
    fun noRowsMeansNoReports() {
        val s = MapAreaSummary.summarize(listOf(stat("common_rust", barangay = "Other")), "Poblacion", "Valencia", null)
        assertEquals(Level.NONE, s.level)
        assertNull(s.lead)
    }

    @Test
    fun hottestTierLeadsAndOthersFollow() {
        val stats = listOf(
            stat("gray_leaf_spot", "mild", 2, intensity = 0.3),
            stat("common_rust", "severe", 6, intensity = 0.84),
            stat("northern_leaf_blight", "moderate", 3, intensity = 0.5)
        )
        val s = MapAreaSummary.summarize(stats, "Poblacion", "Valencia", null)
        assertEquals(Level.SEVERE, s.level)
        assertEquals("common_rust", s.lead!!.diseaseCode)
        assertEquals(listOf("northern_leaf_blight", "gray_leaf_spot"), s.others.map { it.diseaseCode })
    }

    @Test
    fun diseaseFilterLimitsTheRows() {
        val stats = listOf(stat("common_rust", "severe", 6), stat("gray_leaf_spot", "mild", 2))
        val s = MapAreaSummary.summarize(stats, "Poblacion", "Valencia", "gray_leaf_spot")
        assertEquals(Level.MILD, s.level)
        assertEquals(emptyList<BarangayStat>(), s.others)
    }

    @Test
    fun collectingOnlyWhenNothingIsMonitored() {
        val collecting = stat("common_rust", farms = 9, status = "collecting")
        assertEquals(Level.COLLECTING, MapAreaSummary.summarize(listOf(collecting), "Poblacion", "Valencia", null).level)
        // A monitored row outranks a collecting one, even with fewer farms.
        val mixed = listOf(collecting, stat("gray_leaf_spot", "mild", 1))
        val s = MapAreaSummary.summarize(mixed, "Poblacion", "Valencia", null)
        assertEquals(Level.MILD, s.level)
        assertEquals(listOf("common_rust"), s.others.map { it.diseaseCode })
    }

    @Test
    fun rowsWithNoFarmsThisWeekAreIgnored() {
        val s = MapAreaSummary.summarize(listOf(stat("common_rust", farms = 0)), "Poblacion", "Valencia", null)
        assertEquals(Level.NONE, s.level)
    }

    @Test
    fun warmupWeeksAreCapped() {
        val week = 7L * 24 * 60 * 60 * 1000
        val now = 100 * week
        assertEquals(0, MapAreaSummary.warmupWeeks(stat("common_rust"), now))
        assertEquals(2, MapAreaSummary.warmupWeeks(stat("common_rust", since = now - 2 * week - 1), now))
        assertEquals(4, MapAreaSummary.warmupWeeks(stat("common_rust", since = now - 30 * week), now))
    }
}
