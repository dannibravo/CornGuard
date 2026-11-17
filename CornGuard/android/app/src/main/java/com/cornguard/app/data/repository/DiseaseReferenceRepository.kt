package com.cornguard.app.data.repository

import com.cornguard.app.data.local.db.entity.DiseaseReferenceEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository boundary for treatment guides. Must remain queryable offline at all times
 * (claude/15_CLAUDE.md Treatment Rule): screens read only this local store, which
 * [TreatmentGuideSync] fills from the admin-managed guides on the server (and, before the first
 * sync, from the bundled snapshot).
 */
interface DiseaseReferenceRepository {
    fun observeAll(): Flow<List<DiseaseReferenceEntity>>
    suspend fun getByCode(diseaseCode: String): DiseaseReferenceEntity?
    suspend fun upsertAll(references: List<DiseaseReferenceEntity>)

    /** Makes the store exactly [references]: guides deleted on the server are removed here too. */
    suspend fun replaceAll(references: List<DiseaseReferenceEntity>)
}
