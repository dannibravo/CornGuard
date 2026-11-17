package com.cornguard.app.data.repository

import com.cornguard.app.model.ReviewStatus
import com.cornguard.app.model.reviewLabelFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the bundled treatment-guide snapshot (assets/treatment/guides-snapshot.json) — what an
 * install shows before its first sync. It is exported from the admin-managed guides at each release
 * (`node scripts/guides-snapshot.mjs export`); these checks stop a bad export from shipping.
 * Formerly DiseaseReferenceSeedDataTest, from when the guides were hardcoded (docs/TREATMENT_SOURCES.md).
 */
class BundledGuideSnapshotTest {

    private val all = TreatmentGuideSync.parseSnapshot(
        File("src/main/assets/${TreatmentGuideSync.SNAPSHOT_ASSET}").readText()
    )

    @Test
    fun coversEveryModelClassOnce() {
        assertEquals(
            setOf("northern_leaf_blight", "common_rust", "gray_leaf_spot", "healthy"),
            all.map { it.diseaseCode }.toSet()
        )
        assertEquals(4, all.size)
    }

    @Test
    fun everyGuideHasANameAndTreatmentText() {
        for (guide in all) {
            assertTrue("${guide.diseaseCode} has a name", guide.displayName.isNotBlank())
            assertTrue("${guide.diseaseCode} has treatment text", guide.treatmentSteps.isNotBlank())
        }
    }

    @Test
    fun noUnsupportedAdvice() {
        // Bt traits target insects, not fungal disease; these home sprays have no corn field evidence.
        val banned = listOf("bt corn", "baking soda", "neem", "garlic", "burn all")
        for (guide in all) {
            val text = listOf(guide.treatmentSteps, guide.preventionSteps, guide.duration, guide.causes)
                .joinToString(" ").lowercase()
            for (phrase in banned) {
                assertFalse("${guide.diseaseCode} mentions \"$phrase\"", phrase in text)
            }
        }
    }

    /** The review label must never claim more than was done: any claimed review needs a source and a date. */
    @Test
    fun everyClaimedReviewIsBackedBySourceAndDate() {
        for (guide in all.filter { ReviewStatus.fromStoredValue(it.reviewStatus) != ReviewStatus.UNREVIEWED }) {
            assertNotNull("${guide.diseaseCode} claims a review without a source or date", reviewLabelFor(guide))
        }
    }
}
