package com.cornguard.app.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ConfidenceBandTest {

    @Test
    fun bandsFollowTheThresholds() {
        assertEquals(ConfidenceBand.HIGH, ConfidenceBand.of(0.95f))
        assertEquals(ConfidenceBand.HIGH, ConfidenceBand.of(0.80f))
        assertEquals(ConfidenceBand.MEDIUM, ConfidenceBand.of(0.7999f))
        assertEquals(ConfidenceBand.MEDIUM, ConfidenceBand.of(0.60f))
        assertEquals(ConfidenceBand.LOW, ConfidenceBand.of(0.5999f))
        assertEquals(ConfidenceBand.LOW, ConfidenceBand.of(0f))
    }

    @Test
    fun lowMatchesTheBackendAutoVerifyCutOff() {
        // backend/convex/diagnosisRecords.ts AUTO_VERIFY_CONFIDENCE = 0.6
        assertEquals(0.6f, ConfidenceBand.REVIEW_BELOW)
    }
}
