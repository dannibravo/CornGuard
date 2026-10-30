package com.cornguard.app.data.local.db

import com.cornguard.app.model.DiseaseCode
import com.cornguard.app.model.ReviewStatus
import com.cornguard.app.model.reviewLabelFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guards the evidence review of the seed content (docs/TREATMENT_SOURCES.md): the removed,
 * unsupported advice must not come back, and the review label must be backed by a source.
 */
class DiseaseReferenceSeedDataTest {

    private val all = DiseaseReferenceSeedData.all

    @Test
    fun coversEveryDiseaseCodeOnce() {
        assertEquals(4, all.map { it.diseaseCode }.toSet().size)
        assertEquals(4, all.size)
    }

    @Test
    fun noUnsupportedAdvice() {
        // Bt traits target insects, not fungal disease; these home sprays have no corn field evidence.
        val banned = listOf("bt corn", "baking soda", "neem", "garlic", "burn all")
        for (reference in all) {
            val text = listOf(
                reference.treatmentSteps, reference.preventionSteps, reference.duration, reference.causes
            ).joinToString(" ").lowercase()
            for (phrase in banned) {
                assertFalse("${reference.diseaseCode} mentions \"$phrase\"", phrase in text)
            }
        }
    }

    @Test
    fun diseaseEntriesAreSourceReviewed_notExpertVerified() {
        for (reference in all.filter { it.diseaseCode != DiseaseCode.HEALTHY }) {
            val label = reviewLabelFor(reference)
            assertNotNull("${reference.diseaseCode} should carry a review label", label)
            assertEquals(ReviewStatus.SOURCES_REVIEWED, label?.status)
        }
    }

    @Test
    fun healthyHasNoReviewLabel() {
        val healthy = all.single { it.diseaseCode == DiseaseCode.HEALTHY }
        assertNull(reviewLabelFor(healthy))
    }
}
