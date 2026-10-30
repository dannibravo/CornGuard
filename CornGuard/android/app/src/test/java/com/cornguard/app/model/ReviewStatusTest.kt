package com.cornguard.app.model

import com.cornguard.app.data.local.db.entity.DiseaseReferenceEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The review label must only appear when the content was actually reviewed, and never overclaim. */
class ReviewStatusTest {

    private fun reference(
        status: String,
        reviewedBy: String = "",
        reviewedAt: Long = 1_793_318_400_000L,
        source: String = "Crop Protection Network (2026)"
    ) = DiseaseReferenceEntity(
        diseaseCode = "gray_leaf_spot",
        displayName = "Gray Leaf Spot",
        symptoms = "s",
        treatmentSteps = "t",
        preventionSteps = "p",
        sourceReference = source,
        contentVersion = "v",
        updatedAt = 1L,
        reviewStatus = status,
        reviewedBy = reviewedBy,
        reviewedAt = reviewedAt
    )

    @Test
    fun unreviewed_hasNoLabel() {
        assertNull(reviewLabelFor(reference("unreviewed")))
    }

    @Test
    fun unknownStatus_isTreatedAsUnreviewed() {
        assertNull(reviewLabelFor(reference("verified_by_magic")))
    }

    @Test
    fun missingReviewDate_hasNoLabel() {
        assertNull(reviewLabelFor(reference("sources_reviewed", reviewedAt = 0L)))
    }

    @Test
    fun blankSource_hasNoLabel() {
        assertNull(reviewLabelFor(reference("expert_verified", reviewedBy = "Juan Dela Cruz", source = " ")))
    }

    @Test
    fun sourcesReviewed_showsSourcesTier() {
        val label = reviewLabelFor(reference("sources_reviewed", reviewedBy = "team"))
        assertEquals(ReviewStatus.SOURCES_REVIEWED, label?.status)
        assertEquals("", label?.reviewedBy)
    }

    @Test
    fun expertVerified_withNamedReviewer_showsExpertTier() {
        val label = reviewLabelFor(reference("expert_verified", reviewedBy = "Juan Dela Cruz, DA-RFO 10"))
        assertEquals(ReviewStatus.EXPERT_VERIFIED, label?.status)
        assertEquals("Juan Dela Cruz, DA-RFO 10", label?.reviewedBy)
    }

    @Test
    fun expertVerified_withoutReviewer_fallsBackToSourcesTier() {
        val label = reviewLabelFor(reference("expert_verified", reviewedBy = ""))
        assertEquals(ReviewStatus.SOURCES_REVIEWED, label?.status)
    }
}
