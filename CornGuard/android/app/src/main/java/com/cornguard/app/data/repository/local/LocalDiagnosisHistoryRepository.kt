package com.cornguard.app.data.repository.local

import com.cornguard.app.data.local.db.dao.DiagnosisRecordDao
import com.cornguard.app.data.local.db.entity.DiagnosisRecordEntity
import com.cornguard.app.data.repository.DiagnosisHistoryRepository
import kotlinx.coroutines.flow.Flow

class LocalDiagnosisHistoryRepository(
    private val dao: DiagnosisRecordDao
) : DiagnosisHistoryRepository {

    override fun observeHistory(userId: String?): Flow<List<DiagnosisRecordEntity>> = dao.observeForUser(userId)

    override suspend fun claimGuestScans(userId: String, ownCloudRecordIds: List<String>): Int {
        val shared = if (ownCloudRecordIds.isEmpty()) 0 else dao.claimSharedScans(userId, ownCloudRecordIds)
        return shared + dao.claimUnsharedGuestScans(userId)
    }

    override suspend fun assignOwner(localId: Long, userId: String) = dao.assignOwner(localId, userId)

    override suspend fun getById(localId: Long): DiagnosisRecordEntity? = dao.getById(localId)

    override suspend fun saveScan(record: DiagnosisRecordEntity): Long = dao.insert(record)

    override suspend fun markShared(localId: Long, cloudRecordId: String) =
        dao.markShared(localId, cloudRecordId)

    override fun observeById(localId: Long): Flow<DiagnosisRecordEntity?> = dao.observeById(localId)

    override suspend fun queueShare(localId: Long, queuedAt: Long) = dao.queueShare(localId, queuedAt)

    override suspend fun cancelQueuedShare(localId: Long) = dao.cancelQueuedShare(localId)

    override suspend fun setShareError(localId: Long, error: String?) = dao.setShareError(localId, error)

    override suspend fun getQueuedShares(userId: String): List<DiagnosisRecordEntity> = dao.getQueuedShares(userId)

    override fun observeQueuedCount(userId: String?): Flow<Int> = dao.observeQueuedCount(userId)

    override suspend fun updateLocation(
        localId: Long,
        barangay: String,
        municipality: String,
        province: String,
        latitude: Double,
        longitude: Double,
        farmId: String?
    ) = dao.updateLocation(localId, barangay, municipality, province, latitude, longitude, farmId)

    override suspend fun delete(record: DiagnosisRecordEntity) = dao.delete(record)
}
