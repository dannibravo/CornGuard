package com.cornguard.app.data.repository

import com.cornguard.app.data.local.db.entity.DiagnosisRecordEntity
import com.cornguard.app.data.model.Comment
import com.cornguard.app.data.model.CommunityPost
import com.cornguard.app.data.model.DraftPost
import kotlinx.coroutines.flow.Flow

/**
 * Posts, comments, and upvotes. Mirrors
 * firebase/repositories/community-repository-interface.md exactly — see that file for the full
 * contract and explicit non-goals (no automated fact-checking/moderation, no reply-to-comment
 * nesting, no outbreak-alert triggering here).
 *
 * Online-only, same connectivity-handling expectation as the other Firebase-backed repositories.
 */
interface CommunityRepository {

    /**
     * Builds an unsent [DraftPost] from a completed scan — never calls [createPost] itself. The
     * farmer must still review/edit before submitting (claude/08_DATA_AND_INTEGRATION_CONTRACT.md).
     * Purely local; does not touch the network.
     */
    fun prefillPostFromScan(record: DiagnosisRecordEntity): DraftPost

    /**
     * Always created with upvote_count=0 and moderation_status="visible" — security/firestore.rules
     * rejects any other initial value for either field. Returns the new post's id.
     */
    suspend fun createPost(userId: String, draft: DraftPost): String

    /**
     * Location-aware feed. [areaFilter] is a single (field, value) pair — one of "barangay",
     * "municipality", or "province" — matching one of the composite indexes in
     * firebase/firestore.indexes.json; null returns the unfiltered feed. [diseaseTag] narrows to
     * one disease_tag when set. Always excludes anything but moderation_status="visible".
     */
    suspend fun getPostsFeed(
        areaFilter: Pair<String, String>?,
        diseaseTag: String?,
        limit: Int = 50
    ): List<CommunityPost>

    suspend fun getPost(postId: String): CommunityPost?
    fun observePost(postId: String): Flow<CommunityPost?>

    /** Only title/body/disease_tag/image_url may be present — moderation/verification/upvote fields are rejected server-side. */
    suspend fun updatePostContent(postId: String, fields: Map<String, Any>)
    suspend fun deletePost(postId: String)

    suspend fun addComment(postId: String, userId: String, body: String): String
    suspend fun getComments(postId: String): List<Comment>
    fun observeComments(postId: String): Flow<List<Comment>>

    /**
     * Creates or deletes a votes/{userId} document under the post. upvote_count is NOT updated
     * here — a Cloud Function (firebase/functions/index.mjs's onVoteWrite) recomputes it from the
     * votes subcollection, per "do not trust client-supplied aggregate count"
     * (firebase/security/access-control-matrix.md). Returns the new vote state (true = now
     * upvoted).
     */
    suspend fun toggleUpvote(postId: String, userId: String): Boolean
}
