package com.cornguard.app.data.repository.local

import com.cornguard.app.data.local.db.dao.DiseaseReferenceDao
import com.cornguard.app.data.local.db.entity.DiseaseReferenceEntity
import com.cornguard.app.data.repository.DiseaseReferenceRepository
import kotlinx.coroutines.flow.Flow

class LocalDiseaseReferenceRepository(
    private val dao: DiseaseReferenceDao
) : DiseaseReferenceRepository {

    override fun observeAll(): Flow<List<DiseaseReferenceEntity>> = dao.observeAll()

    override suspend fun getByCode(diseaseCode: String): DiseaseReferenceEntity? =
        dao.getByCode(diseaseCode)

    override suspend fun upsertAll(references: List<DiseaseReferenceEntity>) =
        dao.upsertAll(references)
}
