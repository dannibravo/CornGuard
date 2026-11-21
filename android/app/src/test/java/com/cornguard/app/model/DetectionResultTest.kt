package com.cornguard.app.model

import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Guards the DetectionResult contract (claude/08_DATA_AND_INTEGRATION_CONTRACT.md,
 * claude/09_ML_MODEL_CONTRACT.md): confidence must stay in the 0.0–1.0 unit convention and
 * probabilities must always carry exactly one entry per confirmed disease class.
 */
class DetectionResultTest {

    private fun validProbabilities() = floatArrayOf(0.7f, 0.1f, 0.1f, 0.1f)

    @Test
    fun `accepts a well-formed result`() {
        val result = DetectionResult(
            classIndex = 0,
            diseaseCode = DiseaseCode.COMMON_RUST,
            displayLabel = "Common Rust",
            confidence = 0.7f,
            probabilities = validProbabilities(),
            modelVersion = "cornguard_mobilenetv2_v1",
            inferenceTimeMs = 120
        )

        assert(result.confidence == 0.7f)
        assert(result.probabilities.size == DiseaseCode.ALL.size)
    }

    @Test
    fun `rejects confidence above 1_0`() {
        assertThrows(IllegalArgumentException::class.java) {
            DetectionResult(
                classIndex = 0,
                diseaseCode = DiseaseCode.HEALTHY,
                displayLabel = "Healthy",
                confidence = 87f, // looks like a 0-100 value leaking into the 0.0-1.0 field
                probabilities = validProbabilities(),
                modelVersion = "cornguard_mobilenetv2_v1",
                inferenceTimeMs = 120
            )
        }
    }

    @Test
    fun `rejects a probabilities array with the wrong length`() {
        assertThrows(IllegalArgumentException::class.java) {
            DetectionResult(
                classIndex = 0,
                diseaseCode = DiseaseCode.HEALTHY,
                displayLabel = "Healthy",
                confidence = 0.9f,
                probabilities = floatArrayOf(0.9f, 0.1f), // only 2 of 4 classes
                modelVersion = "cornguard_mobilenetv2_v1",
                inferenceTimeMs = 120
            )
        }
    }
}
