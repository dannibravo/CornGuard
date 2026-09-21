package com.cornguard.app.data.repository.local

import com.cornguard.app.data.local.db.dao.DiagnosisRecordDao
import com.cornguard.app.data.local.db.entity.DiagnosisRecordEntity
import com.cornguard.app.data.repository.DiagnosisHistoryRepository
import kotlinx.coroutines.flow.Flow

class LocalDiagnosisHistoryRepository(
    private val dao: DiagnosisRecordDao
) : DiagnosisHistoryRepository {

    override fun observeHistory(): Flow<List<DiagnosisRecordEntity>> = dao.observeAll()

    override suspend fun getById(localId: Long): DiagnosisRecordEntity? = dao.getById(localId)

    override suspend fun saveScan(record: DiagnosisRecordEntity): Long = dao.insert(record)

    override suspend fun markShared(localId: Long, cloudRecordId: String) =
        dao.markShared(localId, cloudRecordId)

    override suspend fun delete(record: DiagnosisRecordEntity) = dao.delete(record)
}
