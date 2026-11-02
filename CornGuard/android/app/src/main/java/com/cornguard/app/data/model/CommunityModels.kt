package com.cornguard.app.data.model

import androidx.annotation.StringRes
import com.cornguard.app.R

/**
 * Mirrors firebase/schema/logical-schema.md's communityPosts/{postId} shape and
 * firebase/repositories/community-repository-interface.md's CommunityPost value type.
 */
data class CommunityPost(
    val postId: String,
    val userId: String,
    val linkedDiagnosisRecordId: String?,
    val title: String,
    val body: String,
    val diseaseTag: String,
    val imageUrl: String?,
    val barangay: String,
    val municipality: String,
    val province: String,
    val verificationStatus: String,
    val moderationStatus: String,
    val upvoteCount: Int,
    val createdAt: Long,
    /** Display fields for the feed; filled by the backend, defaults keep older callers working. */
    val authorName: String = "",
    val commentCount: Int = 0,
    val likedByMe: Boolean = false,
    val recentComments: List<Comment> = emptyList()
)

/** Mirrors communityPosts/{postId}/comments/{commentId}. Single-level nesting only. */
data class Comment(
    val commentId: String,
    val postId: String,
    val userId: String,
    val body: String,
    val moderationStatus: String,
    val createdAt: Long,
    val authorName: String = ""
)

/**
 * Local-only, not-yet-submitted post — the interface doc's DraftPost. Never written to Firestore
 * directly; [CommunityRepository.createPost] takes one of these and produces the real document.
 * The farmer must still review/edit before submitting (Data and Integration Contract).
 */
data class DraftPost(
    val title: String,
    val body: String,
    val diseaseTag: String,
    val imageUri: String?,
    val linkedDiagnosisRecordId: String?,
    val barangay: String,
    val municipality: String,
    val province: String
)

/** What a report is about: `targetType` sent to `reports:create`. */
enum class ReportTarget(val code: String) { POST("post"), COMMENT("comment") }

/**
 * Why a post or comment was reported. [code] is what the backend stores.
 * KEEP IN SYNC with backend/convex/schema.ts reportReason and admin-web's REPORT_REASON_LABEL.
 */
enum class ReportReason(val code: String, @StringRes val label: Int) {
    WRONG_INFO("wrong_info", R.string.report_reason_wrong_info),
    SPAM("spam", R.string.report_reason_spam),
    OFFENSIVE("offensive", R.string.report_reason_offensive),
    OFF_TOPIC("off_topic", R.string.report_reason_off_topic),
    OTHER("other", R.string.report_reason_other)
}
