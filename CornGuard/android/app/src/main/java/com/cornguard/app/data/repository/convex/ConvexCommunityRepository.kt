package com.cornguard.app.data.repository.convex

import com.cornguard.app.data.local.db.entity.DiagnosisRecordEntity
import com.cornguard.app.data.model.Comment
import com.cornguard.app.data.model.CommunityPost
import com.cornguard.app.data.model.DraftPost
import com.cornguard.app.data.remote.convex.CommentDto
import com.cornguard.app.data.remote.convex.ConvexFileUploader
import com.cornguard.app.data.remote.convex.CornGuardConvexClient
import com.cornguard.app.data.remote.convex.PostDto
import com.cornguard.app.data.remote.convex.convexArgs
import com.cornguard.app.data.remote.convex.execute
import com.cornguard.app.data.remote.convex.observe
import com.cornguard.app.data.remote.convex.queryOnce
import com.cornguard.app.data.repository.CommunityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Community posts, comments and upvotes in Convex. The upvote counter and reply notifications are
 * maintained server-side by `posts:toggleUpvote` / `posts:addComment`.
 */
class ConvexCommunityRepository(
    private val client: CornGuardConvexClient,
    private val uploader: ConvexFileUploader
) : CommunityRepository {

    override fun prefillPostFromScan(record: DiagnosisRecordEntity): DraftPost = DraftPost(
        title = "",
        body = "",
        diseaseTag = record.diseaseCode,
        imageUri = record.imageUriOrLocalPath.takeIf { it.isNotBlank() },
        linkedDiagnosisRecordId = record.cloudRecordId,
        barangay = record.barangay ?: "",
        municipality = record.municipality ?: "",
        province = record.province ?: ""
    )

    override suspend fun createPost(userId: String, draft: DraftPost): String {
        val imageId = draft.imageUri?.let { uploader.uploadImage(it) }
        return client.mutation<String>(
            "posts:create",
            convexArgs(
                "linkedDiagnosisRecordId" to draft.linkedDiagnosisRecordId,
                "title" to draft.title,
                "body" to draft.body,
                "diseaseTag" to draft.diseaseTag,
                "imageId" to imageId,
                "barangay" to draft.barangay,
                "municipality" to draft.municipality,
                "province" to draft.province
            )
        )
    }

    override suspend fun getPostsFeed(
        areaFilter: Pair<String, String>?,
        diseaseTag: String?,
        limit: Int
    ): List<CommunityPost> = client.queryOnce<List<PostDto>>(
        "posts:feed",
        convexArgs(
            "areaField" to areaFilter?.first,
            "areaValue" to areaFilter?.second,
            "diseaseTag" to diseaseTag,
            "limit" to limit
        )
    ).map { it.toModel() }

    override suspend fun getPost(postId: String): CommunityPost? =
        client.queryOnce<PostDto?>("posts:get", convexArgs("postId" to postId))?.toModel()

    override fun observePost(postId: String): Flow<CommunityPost?> =
        client.observe<PostDto?>("posts:get", convexArgs("postId" to postId), fallback = null)
            .map { it?.toModel() }

    /** Accepts the former Firestore field names (`disease_tag`) as well as the Convex ones. */
    override suspend fun updatePostContent(postId: String, fields: Map<String, Any>) {
        val args = fields.entries.map { (key, value) ->
            val field = EDITABLE_FIELDS[key] ?: throw IllegalArgumentException("Field '$key' can't be edited")
            field to value
        }
        client.execute("posts:updateContent", convexArgs("postId" to postId, *args.toTypedArray()))
    }

    override suspend fun deletePost(postId: String) {
        client.execute("posts:remove", convexArgs("postId" to postId))
    }

    override suspend fun addComment(postId: String, userId: String, body: String): String =
        client.mutation<String>("posts:addComment", convexArgs("postId" to postId, "body" to body))

    override suspend fun getComments(postId: String): List<Comment> =
        client.queryOnce<List<CommentDto>>("posts:comments", convexArgs("postId" to postId)).map { it.toModel() }

    override fun observeComments(postId: String): Flow<List<Comment>> =
        client.observe<List<CommentDto>>("posts:comments", convexArgs("postId" to postId), fallback = emptyList())
            .map { comments -> comments.map { it.toModel() } }

    override suspend fun toggleUpvote(postId: String, userId: String): Boolean =
        client.mutation<Boolean>("posts:toggleUpvote", convexArgs("postId" to postId))

    private fun PostDto.toModel() = CommunityPost(
        postId = postId,
        userId = userId,
        linkedDiagnosisRecordId = linkedDiagnosisRecordId,
        title = title,
        body = body,
        diseaseTag = diseaseTag,
        imageUrl = imageUrl,
        barangay = barangay,
        municipality = municipality,
        province = province,
        verificationStatus = verificationStatus,
        moderationStatus = moderationStatus,
        upvoteCount = upvoteCount.toInt(),
        createdAt = createdAt.toLong(),
        authorName = authorName,
        commentCount = commentCount.toInt(),
        likedByMe = likedByMe,
        recentComments = recentComments.map { it.toModel() }
    )

    private fun CommentDto.toModel() = Comment(
        commentId = commentId,
        postId = postId,
        userId = userId,
        body = body,
        moderationStatus = moderationStatus,
        createdAt = createdAt.toLong(),
        authorName = authorName
    )

    private companion object {
        val EDITABLE_FIELDS = mapOf(
            "title" to "title",
            "body" to "body",
            "disease_tag" to "diseaseTag",
            "diseaseTag" to "diseaseTag"
        )
    }
}
