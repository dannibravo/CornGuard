package com.cornguard.app.model

/**
 * Plain-language confidence for the result screen ("87% sure · High confidence"). Display only:
 * it never changes the detected class.
 *
 * [LOW] is below [REVIEW_BELOW], the backend's auto-verify cut-off — KEEP IN SYNC with
 * AUTO_VERIFY_CONFIDENCE in backend/convex/diagnosisRecords.ts. A shared LOW scan therefore
 * always waits for an admin before it counts on the map, which is what the low-confidence
 * notice tells the farmer.
 */
enum class ConfidenceBand {
    HIGH, MEDIUM, LOW;

    companion object {
        const val HIGH_AT = 0.80f
        const val REVIEW_BELOW = 0.60f

        /** [confidence] is 0.0–1.0 (claude/08 Conventions). */
        fun of(confidence: Float): ConfidenceBand = when {
            confidence >= HIGH_AT -> HIGH
            confidence >= REVIEW_BELOW -> MEDIUM
            else -> LOW
        }
    }
}
