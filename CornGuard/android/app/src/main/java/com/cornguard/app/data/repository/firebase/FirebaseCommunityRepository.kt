package com.cornguard.app.data.repository.firebase

import android.net.Uri
import com.cornguard.app.data.local.db.entity.DiagnosisRecordEntity
import com.cornguard.app.data.model.Comment
import com.cornguard.app.data.model.CommunityPost
import com.cornguard.app.data.model.DraftPost
import com.cornguard.app.data.repository.CommunityRepository
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.io.File

private const val POSTS_COLLECTION = "communityPosts"

/**
 * Firestore + Storage-backed implementation. Field names match
 * firebase/schema/logical-schema.md exactly; the [getPostsFeed] filter combinations match the
 * composite indexes in firebase/firestore.indexes.json exactly — adding a new filter combination
 * here needs a matching index added there first, or the query throws at runtime.
 *
 * Image upload is NOT yet exercisable against the live dev project — Cloud Storage needs the
 * Blaze plan, same deferral as FirebaseDiagnosisSharingRepository.
 */
class FirebaseCommunityRepository(
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage
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
        val postRef = db.collection(POSTS_COLLECTION).document()
        val postId = postRef.id

        val imageUrl = draft.imageUri?.let { uploadPostImage(postId, it) }

        val data = buildMap {
            put("user_id", userId)
            draft.linkedDiagnosisRecordId?.let { put("linked_diagnosis_record_id", it) }
            put("title", draft.title)
            put("body", draft.body)
            put("disease_tag", draft.diseaseTag)
            imageUrl?.let { put("image_url", it) }
            put("barangay", draft.barangay)
            put("municipality", draft.municipality)
            put("province", draft.province)
            // Fixed at creation — security/firestore.rules rejects any other initial value.
            put("verification_status", "unverified")
            put("moderation_status", "visible")
            put("upvote_count", 0)
        }

        postRef.set(data).await()
        return postId
    }

    override suspend fun getPostsFeed(
        areaFilter: Pair<String, String>?,
        diseaseTag: String?,
        limit: Int
    ): List<CommunityPost> {
        var query: Query = db.collection(POSTS_COLLECTION)
            .whereEqualTo("moderation_status", "visible")

        areaFilter?.let { (field, value) -> query = query.whereEqualTo(field, value) }
        diseaseTag?.let { query = query.whereEqualTo("disease_tag", it) }

        query = query.orderBy("created_at", Query.Direction.DESCENDING).limit(limit.toLong())

        val snapshot = query.get().await()
        return snapshot.documents.mapNotNull { it.toCommunityPost() }
    }

    override suspend fun getPost(postId: String): CommunityPost? {
        val snapshot = db.collection(POSTS_COLLECTION).document(postId).get().await()
        return snapshot.toCommunityPost()
    }

    override fun observePost(postId: String): Flow<CommunityPost?> = callbackFlow {
        val registration = db.collection(POSTS_COLLECTION).document(postId)
            .addSnapshotListener { snapshot, _ -> trySend(snapshot?.toCommunityPost()) }
        awaitClose { registration.remove() }
    }

    override suspend fun updatePostContent(postId: String, fields: Map<String, Any>) {
        // security/firestore.rules rejects moderation_status/upvote_count/verification_status
        // here regardless of what's sent — the rules are the actual enforcement point, same as
        // FirebaseUserFarmRepository.updateUserProfile.
        db.collection(POSTS_COLLECTION).document(postId).update(fields).await()
    }

    override suspend fun deletePost(postId: String) {
        db.collection(POSTS_COLLECTION).document(postId).delete().await()
    }

    override suspend fun addComment(postId: String, userId: String, body: String): String {
        val data = mapOf(
            "user_id" to userId,
            "body" to body,
            "moderation_status" to "visible"
        )
        val ref = db.collection(POSTS_COLLECTION).document(postId)
            .collection("comments").add(data).await()
        return ref.id
    }

    override suspend fun getComments(postId: String): List<Comment> {
        val snapshot = db.collection(POSTS_COLLECTION).document(postId)
            .collection("comments").get().await()
        return snapshot.documents.mapNotNull { it.toComment(postId) }
    }

    override fun observeComments(postId: String): Flow<List<Comment>> = callbackFlow {
        val registration = db.collection(POSTS_COLLECTION).document(postId)
            .collection("comments")
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.documents?.mapNotNull { it.toComment(postId) } ?: emptyList())
            }
        awaitClose { registration.remove() }
    }

    override suspend fun toggleUpvote(postId: String, userId: String): Boolean {
        val voteRef = db.collection(POSTS_COLLECTION).document(postId)
            .collection("votes").document(userId)

        val existing = voteRef.get().await()
        return if (existing.exists()) {
            voteRef.delete().await()
            false
        } else {
            voteRef.set(mapOf("created_at" to com.google.firebase.Timestamp.now())).await()
            true
        }
        // upvote_count itself is recomputed server-side by onVoteWrite — see the interface doc.
    }

    private suspend fun uploadPostImage(postId: String, localPath: String): String? {
        if (localPath.isBlank()) return null
        val uri = if (localPath.contains("://")) Uri.parse(localPath) else Uri.fromFile(File(localPath))
        val fileName = uri.lastPathSegment ?: "post.jpg"
        val ref = storage.reference.child("postImages/$postId/$fileName")
        ref.putFile(uri).await()
        return ref.downloadUrl.await().toString()
    }

    private fun DocumentSnapshot.toCommunityPost(): CommunityPost? {
        if (!exists()) return null
        return CommunityPost(
            postId = id,
            userId = getString("user_id") ?: return null,
            linkedDiagnosisRecordId = getString("linked_diagnosis_record_id"),
            title = getString("title") ?: "",
            body = getString("body") ?: "",
            diseaseTag = getString("disease_tag") ?: "unknown",
            imageUrl = getString("image_url"),
            barangay = getString("barangay") ?: "",
            municipality = getString("municipality") ?: "",
            province = getString("province") ?: "",
            verificationStatus = getString("verification_status") ?: "unverified",
            moderationStatus = getString("moderation_status") ?: "visible",
            upvoteCount = (getLong("upvote_count") ?: 0L).toInt(),
            createdAt = getTimestamp("created_at")?.toDate()?.time ?: 0L
        )
    }

    private fun DocumentSnapshot.toComment(postId: String): Comment? {
        if (!exists()) return null
        return Comment(
            commentId = id,
            postId = postId,
            userId = getString("user_id") ?: return null,
            body = getString("body") ?: "",
            moderationStatus = getString("moderation_status") ?: "visible",
            createdAt = getTimestamp("created_at")?.toDate()?.time ?: 0L
        )
    }
}
