package com.cornguard.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers docs/MULTI_PHOTO_LEAF_CAPTURE_PLAN.md §4.2 / §4.5 / §6.1. The threshold values here are
 * test fixtures only, not proposed T_conf values.
 */
class PlantResultAggregatorTest {

    private val aggregator = PlantResultAggregator(
        PlantResultConfig(confidentThreshold = 0.6f, minConfidentHealthyLeaves = 2)
    )

    private fun leaf(code: String, confidence: Float): DetectionResult {
        val index = DiseaseCode.ALL.indexOf(code)
        val rest = (1f - confidence) / 3f
        val probabilities = FloatArray(DiseaseCode.ALL.size) { if (it == index) confidence else rest }
        return DetectionResult(
            classIndex = index,
            diseaseCode = code,
            displayLabel = code,
            confidence = confidence,
            probabilities = probabilities,
            modelVersion = "test",
            inferenceTimeMs = 0
        )
    }

    private val rust = DiseaseCode.COMMON_RUST
    private val gls = DiseaseCode.GRAY_LEAF_SPOT
    private val nlb = DiseaseCode.NORTHERN_LEAF_BLIGHT
    private val healthy = DiseaseCode.HEALTHY

    @Test
    fun `same disease on both leaves is disease found on 2 of 2`() {
        val result = aggregator.aggregate(listOf(leaf(rust, 0.8f), leaf(rust, 0.9f)))

        assertEquals(PlantResultState.DISEASE_FOUND, result.state)
        assertNull(result.unclearReason)
        assertEquals(rust, result.mainDiseaseCode)
        assertEquals(2, result.leavesWithResult)
        assertEquals(2, result.imageCount)
        assertEquals(0.85f, result.mainConfidence, 1e-5f)
        assertEquals(1, result.mainLeafPosition) // the 0.9 leaf
    }

    @Test
    fun `disease plus healthy leaf reports the disease, not an average`() {
        val result = aggregator.aggregate(listOf(leaf(healthy, 0.9f), leaf(rust, 0.9f)))

        assertEquals(PlantResultState.DISEASE_FOUND, result.state)
        assertEquals(rust, result.mainDiseaseCode)
        assertEquals(1, result.leavesWithResult)
        assertEquals(0.9f, result.mainConfidence, 1e-5f)
        assertEquals(1, result.mainLeafPosition)
    }

    @Test
    fun `two different diseases are mixed, both reported`() {
        val result = aggregator.aggregate(listOf(leaf(rust, 0.7f), leaf(gls, 0.8f)))

        assertEquals(PlantResultState.MIXED_DISEASES, result.state)
        assertEquals(listOf(gls, rust), result.diseasesFound.map { it.diseaseCode })
        assertEquals(gls, result.mainDiseaseCode) // tie on leaf count, higher mean confidence wins
    }

    @Test
    fun `mixed diseases main result is the one on more leaves`() {
        val result = aggregator.aggregate(
            listOf(leaf(gls, 0.95f), leaf(rust, 0.7f), leaf(rust, 0.7f))
        )

        assertEquals(PlantResultState.MIXED_DISEASES, result.state)
        assertEquals(rust, result.mainDiseaseCode)
        assertEquals(2, result.leavesWithResult)
    }

    @Test
    fun `full tie falls back to DiseaseCode order`() {
        val result = aggregator.aggregate(listOf(leaf(nlb, 0.8f), leaf(rust, 0.8f)))

        assertEquals(rust, result.mainDiseaseCode)
    }

    @Test
    fun `two confident healthy leaves is healthy`() {
        val result = aggregator.aggregate(listOf(leaf(healthy, 0.8f), leaf(healthy, 0.9f)))

        assertEquals(PlantResultState.HEALTHY, result.state)
        assertEquals(healthy, result.mainDiseaseCode)
        assertEquals(2, result.leavesWithResult)
        assertTrue(result.diseasesFound.isEmpty())
    }

    @Test
    fun `no confident leaf is unclear with no-confident-leaf reason`() {
        val result = aggregator.aggregate(listOf(leaf(rust, 0.4f), leaf(gls, 0.5f)))

        assertEquals(PlantResultState.UNCLEAR, result.state)
        assertEquals(UnclearReason.NO_CONFIDENT_LEAF, result.unclearReason)
        assertEquals(gls, result.mainDiseaseCode) // top class of the highest-confidence leaf
        assertEquals(0.5f, result.mainConfidence, 1e-5f)
        assertEquals(0, result.leavesWithResult)
        assertEquals(0, result.mainLeafPosition) // leaf 1 when none is confident
    }

    @Test
    fun `one confident healthy leaf is unclear with too-few-healthy reason`() {
        val result = aggregator.aggregate(listOf(leaf(healthy, 0.9f), leaf(rust, 0.4f)))

        assertEquals(PlantResultState.UNCLEAR, result.state)
        assertEquals(UnclearReason.TOO_FEW_CONFIDENT_HEALTHY, result.unclearReason)
        assertEquals(healthy, result.mainDiseaseCode)
        assertEquals(1, result.leavesWithResult)
    }

    @Test
    fun `low-confidence disease leaf does not count toward the summary`() {
        val result = aggregator.aggregate(
            listOf(leaf(healthy, 0.8f), leaf(healthy, 0.8f), leaf(rust, 0.5f))
        )

        assertEquals(PlantResultState.HEALTHY, result.state)
        assertEquals(false, result.leaves[2].isConfident)
    }

    @Test
    fun `confidence exactly at threshold counts as confident`() {
        val result = aggregator.aggregate(listOf(leaf(rust, 0.6f), leaf(healthy, 0.3f)))

        assertEquals(PlantResultState.DISEASE_FOUND, result.state)
    }

    @Test
    fun `retry leaf turns an unclear scan into a firm result`() {
        val first = aggregator.aggregate(listOf(leaf(healthy, 0.9f), leaf(rust, 0.4f)))
        val retried = aggregator.aggregate(
            listOf(leaf(healthy, 0.9f), leaf(rust, 0.4f), leaf(healthy, 0.85f))
        )

        assertEquals(PlantResultState.UNCLEAR, first.state)
        assertEquals(PlantResultState.HEALTHY, retried.state)
    }

    @Test
    fun `four leaves count each disease`() {
        val result = aggregator.aggregate(
            listOf(leaf(rust, 0.9f), leaf(healthy, 0.9f), leaf(rust, 0.7f), leaf(rust, 0.3f))
        )

        assertEquals(PlantResultState.DISEASE_FOUND, result.state)
        assertEquals(2, result.leavesWithResult) // the 0.3 rust leaf isn't confident
        assertEquals(4, result.imageCount)
        assertEquals(0, result.mainLeafPosition)
    }

    @Test
    fun `empty input is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { aggregator.aggregate(emptyList()) }
    }

    @Test
    fun `config rejects out-of-range values`() {
        assertThrows(IllegalArgumentException::class.java) {
            PlantResultConfig(confidentThreshold = 60f, minConfidentHealthyLeaves = 2)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PlantResultConfig(confidentThreshold = 0.6f, minConfidentHealthyLeaves = 0)
        }
    }
}
