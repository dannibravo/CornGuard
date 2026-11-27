package com.cornguard.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.cornguard.app.data.local.db.entity.DiagnosisRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DiagnosisRecordDao {

    @Insert
    suspend fun insert(record: DiagnosisRecordEntity): Long

    /** Newest scan first, so Scan History reads correctly without extra sorting downstream. */
    @Query("SELECT * FROM diagnosis_records ORDER BY capturedAt DESC")
    fun observeAll(): Flow<List<DiagnosisRecordEntity>>

    /**
     * One account's history on this phone; `null` = guest scans (taken while signed out). `IS`
     * (not `=`) so a null argument matches null owners.
     */
    @Query("SELECT * FROM diagnosis_records WHERE userId IS :userId ORDER BY capturedAt DESC")
    fun observeForUser(userId: String?): Flow<List<DiagnosisRecordEntity>>

    /** Guest scans that were never shared go to the account that signs in next on this phone. */
    @Query("UPDATE diagnosis_records SET userId = :userId WHERE userId IS NULL AND sharedToCloud = 0")
    suspend fun claimUnsharedGuestScans(userId: String): Int

    /** Ownerless scans this account already shared (matched by the server's record ids) are its own. */
    @Query("UPDATE diagnosis_records SET userId = :userId WHERE userId IS NULL AND cloudRecordId IN (:cloudRecordIds)")
    suspend fun claimSharedScans(userId: String, cloudRecordIds: List<String>): Int

    @Query("UPDATE diagnosis_records SET userId = :userId WHERE localId = :localId")
    suspend fun assignOwner(localId: Long, userId: String)

    @Query("SELECT * FROM diagnosis_records WHERE localId = :localId")
    suspend fun getById(localId: Long): DiagnosisRecordEntity?

    @Query("SELECT * FROM diagnosis_records WHERE localId = :localId")
    fun observeById(localId: Long): Flow<DiagnosisRecordEntity?>

    /** Shared: also leaves the offline share queue. */
    @Query(
        "UPDATE diagnosis_records SET sharedToCloud = 1, cloudRecordId = :cloudRecordId, " +
            "shareQueuedAt = NULL, shareLastError = NULL WHERE localId = :localId"
    )
    suspend fun markShared(localId: Long, cloudRecordId: String)

    /** Joins the offline share queue (no-op once shared). */
    @Query(
        "UPDATE diagnosis_records SET shareQueuedAt = :queuedAt, shareLastError = NULL " +
            "WHERE localId = :localId AND sharedToCloud = 0"
    )
    suspend fun queueShare(localId: Long, queuedAt: Long)

    @Query("UPDATE diagnosis_records SET shareQueuedAt = NULL, shareLastError = NULL WHERE localId = :localId")
    suspend fun cancelQueuedShare(localId: Long)

    @Query("UPDATE diagnosis_records SET shareLastError = :error WHERE localId = :localId")
    suspend fun setShareError(localId: Long, error: String?)

    /** [userId]'s scans waiting to be shared, oldest request first. */
    @Query(
        "SELECT * FROM diagnosis_records WHERE userId = :userId AND sharedToCloud = 0 " +
            "AND shareQueuedAt IS NOT NULL ORDER BY shareQueuedAt ASC"
    )
    suspend fun getQueuedShares(userId: String): List<DiagnosisRecordEntity>

    @Query(
        "SELECT COUNT(*) FROM diagnosis_records WHERE userId IS :userId AND sharedToCloud = 0 " +
            "AND shareQueuedAt IS NOT NULL"
    )
    fun observeQueuedCount(userId: String?): Flow<Int>

    /** Where a shared scan was filed: its barangay, map point and (if shared from one) the farm. */
    @Query(
        "UPDATE diagnosis_records SET barangay = :barangay, municipality = :municipality, " +
            "province = :province, latitude = :latitude, longitude = :longitude, farmId = :farmId " +
            "WHERE localId = :localId"
    )
    suspend fun updateLocation(
        localId: Long,
        barangay: String,
        municipality: String,
        province: String,
        latitude: Double,
        longitude: Double,
        farmId: String?
    )

    @Delete
    suspend fun delete(record: DiagnosisRecordEntity)
}
