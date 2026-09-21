package com.cornguard.app.data.repository

import com.cornguard.app.data.local.db.entity.DiagnosisRecordEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository boundary for local scan history. Kept as an interface so history can later be read
 * from more than one source (e.g. a future cloud-merged view) without changing consumers — the
 * local implementation is the only one that exists in Sprint 0 and is the only one the offline
 * scan path may depend on (claude/04_DEVELOPMENT_RULES.md #2).
 */
interface DiagnosisHistoryRepository {
    fun observeHistory(): Flow<List<DiagnosisRecordEntity>>
    suspend fun getById(localId: Long): DiagnosisRecordEntity?
    suspend fun saveScan(record: DiagnosisRecordEntity): Long
    suspend fun markShared(localId: Long, cloudRecordId: String)
    suspend fun delete(record: DiagnosisRecordEntity)
}
