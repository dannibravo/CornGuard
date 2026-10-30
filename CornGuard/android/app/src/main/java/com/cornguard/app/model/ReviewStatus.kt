package com.cornguard.app.model

import com.cornguard.app.data.local.db.entity.DiseaseReferenceEntity

/**
 * How far a disease reference's treatment content has been checked. Stored in
 * `disease_references.reviewStatus`; drives the small review label under each Treatment section.
 * The label must never claim more than was actually done, so anything unknown is [UNREVIEWED].
 */
enum class ReviewStatus(val storedValue: String) {
    /** Not checked — no label is shown. */
    UNREVIEWED("unreviewed"),

    /** Checked against published sources (extension research, FPA registrations) by the team. */
    SOURCES_REVIEWED("sources_reviewed"),

    /** Signed off by a named agricultural expert (DA technician / agronomist). */
    EXPERT_VERIFIED("expert_verified");

    companion object {
        fun fromStoredValue(value: String): ReviewStatus =
            entries.firstOrNull { it.storedValue == value } ?: UNREVIEWED
    }
}

/** What the review label shows: the tier, the reviewer (expert tier only) and the review time. */
data class ReviewLabel(val status: ReviewStatus, val reviewedBy: String, val reviewedAt: Long)

/**
 * The label to show under [reference]'s treatment, or null for none. A label needs a review time
 * and a traceable source; the expert tier also needs a named reviewer, otherwise it is shown as
 * [ReviewStatus.SOURCES_REVIEWED].
 */
fun reviewLabelFor(reference: DiseaseReferenceEntity): ReviewLabel? {
    val status = ReviewStatus.fromStoredValue(reference.reviewStatus)
    if (status == ReviewStatus.UNREVIEWED) return null
    if (reference.reviewedAt <= 0L || reference.sourceReference.isBlank()) return null
    return if (status == ReviewStatus.EXPERT_VERIFIED && reference.reviewedBy.isNotBlank()) {
        ReviewLabel(ReviewStatus.EXPERT_VERIFIED, reference.reviewedBy, reference.reviewedAt)
    } else {
        ReviewLabel(ReviewStatus.SOURCES_REVIEWED, "", reference.reviewedAt)
    }
}
