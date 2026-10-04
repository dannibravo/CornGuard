package com.cornguard.app.data.repository.convex

import com.cornguard.app.data.local.db.entity.DiagnosisRecordEntity
import com.cornguard.app.data.model.DiagnosisRecordCloud
import com.cornguard.app.data.remote.convex.ConvexFileUploader
import com.cornguard.app.data.remote.convex.CornGuardConvexClient
import com.cornguard.app.data.remote.convex.DiagnosisRecordDto
import com.cornguard.app.data.remote.convex.convexArgs
import com.cornguard.app.data.remote.convex.execute
import com.cornguard.app.data.remote.convex.observe
import com.cornguard.app.data.remote.convex.queryOnce
import com.cornguard.app.data.repository.DiagnosisSharingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Opt-in cloud sharing of on-device scans: the leaf image goes to Convex file storage, the record
 * to `diagnosisRecords`. Sharing is idempotent per local record, so retrying after a dropped
 * connection can't create a duplicate.
 */
class ConvexDiagnosisSharingRepository(
    private val client: CornGuardConvexClient,
    private val uploader: ConvexFileUploader
) : DiagnosisSharingRepository {

    override suspend fun shareScan(
        record: DiagnosisRecordEntity,
        ownerUserId: String,
        farmId: String?
    ): String {
        val imageId = uploader.uploadImage(record.imageUriOrLocalPath)
        return client.mutation<String>(
            "diagnosisRecords:share",
            convexArgs(
                "localId" to record.localId.toString(),
                "farmId" to farmId,
                "diseaseCode" to record.diseaseCode,
                "confidence" to record.confidence,
                "imageId" to imageId,
                "capturedAt" to record.capturedAt,
                "barangay" to (record.barangay ?: ""),
                "municipality" to (record.municipality ?: ""),
                "province" to (record.province ?: ""),
                "latitude" to record.latitude,
                "longitude" to record.longitude,
                "modelVersion" to record.modelVersion
            )
        )
    }

    override suspend fun getSharedRecordsForUser(uid: String): List<DiagnosisRecordCloud> =
        client.queryOnce<List<DiagnosisRecordDto>>("diagnosisRecords:listForUser", convexArgs("userId" to uid))
            .map { it.toModel() }

    override fun observeSharedRecordsForUser(uid: String): Flow<List<DiagnosisRecordCloud>> =
        client.observe<List<DiagnosisRecordDto>>(
            "diagnosisRecords:listForUser",
            convexArgs("userId" to uid),
            fallback = emptyList()
        ).map { records -> records.map { it.toModel() } }

    override suspend fun deleteSharedRecord(recordId: String) {
        client.execute("diagnosisRecords:remove", convexArgs("recordId" to recordId))
    }

    private fun DiagnosisRecordDto.toModel() = DiagnosisRecordCloud(
        recordId = recordId,
        userId = userId,
        farmId = farmId,
        diseaseCode = diseaseCode,
        confidence = confidence.toFloat(),
        imageUrl = imageUrl,
        capturedAt = capturedAt.toLong(),
        barangay = barangay,
        municipality = municipality,
        province = province,
        modelVersion = modelVersion,
        verificationStatus = verificationStatus,
        source = source
    )
}
