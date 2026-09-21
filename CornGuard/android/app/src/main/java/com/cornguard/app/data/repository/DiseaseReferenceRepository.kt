package com.cornguard.app.data.repository

import com.cornguard.app.data.local.db.entity.DiseaseReferenceEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository boundary for treatment/prevention reference content. Must remain queryable offline
 * at all times (claude/15_CLAUDE.md Treatment Rule) — any future cloud-sourced update path writes
 * into the same local store rather than being read directly by the UI.
 */
interface DiseaseReferenceRepository {
    fun observeAll(): Flow<List<DiseaseReferenceEntity>>
    suspend fun getByCode(diseaseCode: String): DiseaseReferenceEntity?
    suspend fun upsertAll(references: List<DiseaseReferenceEntity>)
}
