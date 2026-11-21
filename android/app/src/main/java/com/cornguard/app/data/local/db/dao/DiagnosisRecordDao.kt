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

    @Query("SELECT * FROM diagnosis_records WHERE localId = :localId")
    suspend fun getById(localId: Long): DiagnosisRecordEntity?

    @Query("UPDATE diagnosis_records SET sharedToCloud = 1, cloudRecordId = :cloudRecordId WHERE localId = :localId")
    suspend fun markShared(localId: Long, cloudRecordId: String)

    @Delete
    suspend fun delete(record: DiagnosisRecordEntity)
}
