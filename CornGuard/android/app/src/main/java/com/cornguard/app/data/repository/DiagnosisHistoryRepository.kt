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
    /** Scans owned by [userId] on this phone; `null` = guest scans taken while signed out. */
    fun observeHistory(userId: String?): Flow<List<DiagnosisRecordEntity>>

    /**
     * Called when [userId] signs in: unshared guest scans become theirs, and so do ownerless scans
     * they already shared ([ownCloudRecordIds], from the server). Returns how many were claimed.
     */
    suspend fun claimGuestScans(userId: String, ownCloudRecordIds: List<String>): Int
    suspend fun assignOwner(localId: Long, userId: String)
    suspend fun getById(localId: Long): DiagnosisRecordEntity?
    suspend fun saveScan(record: DiagnosisRecordEntity): Long
    suspend fun markShared(localId: Long, cloudRecordId: String)
    suspend fun updateLocation(
        localId: Long,
        barangay: String,
        municipality: String,
        province: String,
        latitude: Double,
        longitude: Double
    )
    suspend fun delete(record: DiagnosisRecordEntity)
}
