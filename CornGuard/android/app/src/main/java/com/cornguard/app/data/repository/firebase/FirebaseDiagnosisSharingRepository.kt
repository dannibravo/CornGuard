package com.cornguard.app.data.repository.firebase

import android.net.Uri
import com.cornguard.app.data.local.db.entity.DiagnosisRecordEntity
import com.cornguard.app.data.model.DiagnosisRecordCloud
import com.cornguard.app.data.repository.DiagnosisSharingRepository
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.io.File

private const val RECORDS_COLLECTION = "diagnosisRecordsCloud"

/**
 * Firestore + Storage-backed implementation. Field names match
 * firebase/schema/logical-schema.md exactly; the Storage path matches
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

        val imageUrl = uploadImage(ownerUserId, recordId, record.imageUriOrLocalPath)

        val data = buildMap {
            put("user_id", ownerUserId)
            farmId?.let { put("farm_id", it) }
            put("disease_code", record.diseaseCode)
            put("confidence", record.confidence)
            imageUrl?.let { put("image_url", it) }
            put("captured_at", record.capturedAt)
            put("barangay", record.barangay ?: "")
            put("municipality", record.municipality ?: "")
            put("province", record.province ?: "")
            put("model_version", record.modelVersion)
            // Fixed at creation — security/firestore.rules rejects any other initial value.
            put("verification_status", "unverified")
            put("source", "ai_scan")
        }

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
        db.collection(RECORDS_COLLECTION).document(recordId).delete().await()
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

    private fun DocumentSnapshot.toDiagnosisRecordCloud(): DiagnosisRecordCloud? {
        if (!exists()) return null
        val diseaseCode = getString("disease_code") ?: return null
        return DiagnosisRecordCloud(
            recordId = id,
            userId = getString("user_id") ?: return null,
            farmId = getString("farm_id"),
            diseaseCode = diseaseCode,
            confidence = getDouble("confidence")?.toFloat() ?: 0f,
            imageUrl = getString("image_url"),
            capturedAt = getLong("captured_at") ?: 0L,
            barangay = getString("barangay") ?: "",
            municipality = getString("municipality") ?: "",
            province = getString("province") ?: "",
            modelVersion = getString("model_version") ?: "",
            verificationStatus = getString("verification_status") ?: "unverified",
            source = getString("source") ?: "ai_scan"
        )
    }
}
