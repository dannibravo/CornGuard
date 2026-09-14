package com.cornguard.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.cornguard.app.data.local.db.entity.DiseaseReferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DiseaseReferenceDao {

    /** Reference content is admin/Acenas-maintained and versioned — inserts or replaces by primary key. */
    @Upsert
    suspend fun upsertAll(references: List<DiseaseReferenceEntity>)

    @Query("SELECT * FROM disease_references WHERE diseaseCode = :diseaseCode")
    suspend fun getByCode(diseaseCode: String): DiseaseReferenceEntity?

    @Query("SELECT * FROM disease_references")
    fun observeAll(): Flow<List<DiseaseReferenceEntity>>
}
