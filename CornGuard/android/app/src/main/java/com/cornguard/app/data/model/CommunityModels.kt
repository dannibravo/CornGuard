package com.cornguard.app.data.model

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
    val createdAt: Long
)

/** Mirrors communityPosts/{postId}/comments/{commentId}. Single-level nesting only. */
data class Comment(
    val commentId: String,
    val postId: String,
    val userId: String,
    val body: String,
    val moderationStatus: String,
    val createdAt: Long
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
