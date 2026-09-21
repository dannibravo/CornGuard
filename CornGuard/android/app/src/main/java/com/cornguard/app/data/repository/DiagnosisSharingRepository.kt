package com.cornguard.app.data.repository

import com.cornguard.app.data.local.db.entity.DiagnosisRecordEntity
import com.cornguard.app.data.model.DiagnosisRecordCloud
import kotlinx.coroutines.flow.Flow

/**
 * Opt-in cloud sharing of a local scan. Mirrors
 * firebase/repositories/diagnosis-sharing-repository-interface.md.
 *
 * Per D-11 (claude/03_SOURCE_ALIGNMENT_AND_DECISION_GATES.md): a scan is local/private by
 * default. Nothing here is called unless the farmer explicitly chooses to share a completed local
 * scan — there is no "share automatically" mode. The local SQLite [DiagnosisHistoryRepository]
 * remains the source of truth for a farmer's own history regardless of what this repository does.
 *
 * Online-only, same connectivity-handling expectation as AuthRepository/UserFarmRepository.
 */
interface DiagnosisSharingRepository {

    /**
     * Uploads [record]'s image and creates the corresponding diagnosisRecordsCloud document.
     * Always created with verification_status="unverified" and source="ai_scan" —
     * security/firestore.rules rejects any other initial value. Returns the new cloud record id.
     *
     * Does NOT touch the local record — callers are responsible for calling
     * [DiagnosisHistoryRepository.markShared] with the returned id afterward, per
     * claude/04_DEVELOPMENT_RULES.md #16 (local and cloud ids are kept separate).
     */
    suspend fun shareScan(record: DiagnosisRecordEntity, ownerUserId: String, farmId: String?): String

    suspend fun getSharedRecordsForUser(uid: String): List<DiagnosisRecordCloud>
    fun observeSharedRecordsForUser(uid: String): Flow<List<DiagnosisRecordCloud>>

    /**
     * Deletes the cloud record only. Per the Data and Integration Contract, this must NEVER
     * cascade to the local SQLite history — that's a separate, explicit user action.
     */
    suspend fun deleteSharedRecord(recordId: String)
}
