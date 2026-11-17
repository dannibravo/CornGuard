package com.cornguard.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.cornguard.app.data.local.db.entity.DiseaseReferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DiseaseReferenceDao {

    /** Treatment guides are admin-managed (admin website) and versioned — inserts or replaces by primary key. */
    @Upsert
    suspend fun upsertAll(references: List<DiseaseReferenceEntity>)

    @Query("SELECT * FROM disease_references WHERE diseaseCode = :diseaseCode")
    suspend fun getByCode(diseaseCode: String): DiseaseReferenceEntity?

    @Query("SELECT * FROM disease_references")
    fun observeAll(): Flow<List<DiseaseReferenceEntity>>

    @Query("DELETE FROM disease_references WHERE diseaseCode NOT IN (:keep)")
    suspend fun deleteAllExcept(keep: List<String>)

    @Query("DELETE FROM disease_references")
    suspend fun deleteAll()

    /** Makes the table exactly [references] (the server's current guides) in one transaction. */
    @Transaction
    suspend fun replaceAll(references: List<DiseaseReferenceEntity>) {
        if (references.isEmpty()) deleteAll() else deleteAllExcept(references.map { it.diseaseCode })
        upsertAll(references)
    }
}
