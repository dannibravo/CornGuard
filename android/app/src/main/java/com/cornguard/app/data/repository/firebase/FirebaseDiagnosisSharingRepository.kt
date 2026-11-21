package com.cornguard.app.data.repository.firebase

import android.net.Uri
import android.util.Log
import com.cornguard.app.data.local.db.entity.DiagnosisRecordEntity
import com.cornguard.app.data.model.DiagnosisRecordCloud
import com.cornguard.app.data.repository.DiagnosisSharingRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.io.File

private const val RECORDS_COLLECTION = "diagnosisRecordsCloud"
private const val VERIFIED_OCCURRENCES_COLLECTION = "verifiedOccurrences"
private const val TAG = "DiagnosisSharingRepo"

/**
 * Firestore + Storage-backed implementation. Field names match
 * firebase/docs/schema/logical-schema.md exactly; the Storage path matches
 * firebase/security/storage.rules exactly (`diagnosisImages/{userId}/{recordId}/{fileName}`) —
 * do not change either without updating the other (claude/04_DEVELOPMENT_RULES.md #3).
 *
 * NOT yet exercisable against the live dev project: Cloud Storage requires the Blaze billing
 * plan, which the team deferred (see D-01 dev-provisioning note in
 * claude/03_SOURCE_ALIGNMENT_AND_DECISION_GATES.md, and firebase/README.md). This class is
 * written and ready for when that's revisited.
 */
class FirebaseDiagnosisSharingRepository(
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage
) : DiagnosisSharingRepository {

    override suspend fun shareScan(
        record: DiagnosisRecordEntity,
        ownerUserId: String,
        farmId: String?
    ): String {
        val recordRef = db.collection(RECORDS_COLLECTION).document()
        val recordId = recordRef.id

        // Best-effort: Cloud Storage is an optional dependency (not currently provisioned on
        // cornguard-dev — see firebase/README.md Status). A failed/unavailable upload must not
        // block the metadata share itself; only a failure of the Firestore write below (the
        // actual share) should propagate to the caller as a sharing failure.
        val imageUrl = runCatching { uploadImage(ownerUserId, recordId, record.imageUriOrLocalPath) }
            .onFailure { e ->
                // No file paths or user content logged — record id + exception type/message only.
                Log.w(TAG, "Image upload failed for recordId=$recordId, continuing without image", e)
            }
            .getOrNull()

        val data = buildMap {
            put("user_id", ownerUserId)
            farmId?.let { put("farm_id", it) }
            put("disease_code", record.diseaseCode)
            put("confidence", record.confidence)
            imageUrl?.let { put("image_url", it) }
            // Explicit, always-set signal of whether an image actually attached — distinct from
            // "image_url absent because this doc predates the field" (see toDiagnosisRecordCloud).
            put("has_image", imageUrl != null)
            put("captured_at", record.capturedAt)
            put("barangay", record.barangay ?: "")
            put("municipality", record.municipality ?: "")
            put("province", record.province ?: "")
            put("model_version", record.modelVersion)
            // Fixed at creation — security/firestore.rules rejects any other initial value.
            put("verification_status", "unverified")
            put("source", "ai_scan")
            record.latitude?.let { put("latitude", it) }
            record.longitude?.let { put("longitude", it) }
        }

        // Unguarded: a failure here (e.g. Firestore unreachable, permission denied) is a real
        // sharing failure and must propagate to the caller.
        recordRef.set(data).await()
        return recordId
    }

    override suspend fun getSharedRecordsForUser(uid: String): List<DiagnosisRecordCloud> {
        val snapshot = db.collection(RECORDS_COLLECTION)
            .whereEqualTo("user_id", uid)
            .get()
            .await()
        return snapshot.documents.mapNotNull { it.toDiagnosisRecordCloud() }
    }

    override fun observeSharedRecordsForUser(uid: String): Flow<List<DiagnosisRecordCloud>> = callbackFlow {
        val registration = db.collection(RECORDS_COLLECTION)
            .whereEqualTo("user_id", uid)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.documents?.mapNotNull { it.toDiagnosisRecordCloud() } ?: emptyList())
            }
        awaitClose { registration.remove() }
    }

    override suspend fun deleteSharedRecord(recordId: String) {
        // Deliberately does not touch local history — see the interface doc's contract.
        //
        // Batched with the verifiedOccurrences delete (Phase 4, resolved 2026-10-30): if this
        // record was ever admin-verified, it has a redacted public projection with the same doc
        // ID. Deleting only the source record would leave that projection orphaned but still
        // publicly readable (security/firestore.rules' verifiedOccurrences read rule is
        // `isSignedIn()`, not owner-scoped) — a real privacy regression, not just stale data.
        // security/firestore.rules explicitly allows the record's owner to delete the matching
        // verifiedOccurrences doc (via a get()-based ownership check on the record being deleted
        // in the same batch), so this works for a non-admin farmer deleting their own record, not
        // just for admins. A delete() on a verifiedOccurrences doc that never existed (record was
        // never verified) is a harmless no-op — Firestore does not error on deleting a
        // non-existent document.
        db.batch()
            .delete(db.collection(RECORDS_COLLECTION).document(recordId))
            .delete(db.collection(VERIFIED_OCCURRENCES_COLLECTION).document(recordId))
            .commit()
            .await()
    }

    /** Returns null (no image_url set) if [localPath] is blank — sharing without a photo is allowed. */
    private suspend fun uploadImage(userId: String, recordId: String, localPath: String): String? {
        if (localPath.isBlank()) return null

        val uri = if (localPath.contains("://")) Uri.parse(localPath) else Uri.fromFile(File(localPath))
        val fileName = uri.lastPathSegment ?: "leaf.jpg"
        val ref = storage.reference.child("diagnosisImages/$userId/$recordId/$fileName")

        ref.putFile(uri).await()
        return ref.downloadUrl.await().toString()
    }
}
