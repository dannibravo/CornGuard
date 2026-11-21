package com.cornguard.app.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the corn / not-corn decision rule the scan screen relies on to block non-corn photos. */
class CornGateResultTest {

    private fun result(p: Float, threshold: Float = 0.5f) =
        CornGateResult(cornProbability = p, threshold = threshold, modelVersion = "cornguard_corn_gate_v1")

    @Test
    fun `accepts a photo at or above the threshold`() {
        assertTrue(result(0.5f).isCorn)
        assertTrue(result(0.99f).isCorn)
    }

    @Test
    fun `rejects a photo below the threshold`() {
        assertFalse(result(0.49f).isCorn)
        assertFalse(result(0.0f).isCorn)
    }

    @Test
    fun `respects a custom threshold`() {
        assertFalse(result(0.7f, threshold = 0.8f).isCorn)
        assertTrue(result(0.8f, threshold = 0.8f).isCorn)
    }

    @Test
    fun `rejects probabilities outside 0 to 1`() {
        assertThrows(IllegalArgumentException::class.java) { result(1.5f) }
        assertThrows(IllegalArgumentException::class.java) { result(-0.1f) }
    }

    @Test
    fun `rejects a threshold outside 0 to 1`() {
        assertThrows(IllegalArgumentException::class.java) { result(0.5f, threshold = 50f) }
    }
}
