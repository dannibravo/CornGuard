package com.cornguard.app.data.sync

import com.cornguard.app.data.local.db.entity.DiagnosisRecordEntity
import com.cornguard.app.data.repository.DiagnosisHistoryRepository
import com.cornguard.app.ui.common.runCatchingCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The offline share queue. A scan the farmer chose to share while offline is marked queued in
 * Room ([DiagnosisHistoryRepository.queueShare]) with its barangay already filed; this sends it
 * once the phone is online and its owner is signed in, oldest first.
 *
 * Sending reuses the normal share call, which the backend makes idempotent per scan
 * (`diagnosisRecords.share`), so a retry after a dropped connection never creates a duplicate.
 * Failed attempts are retried with exponential backoff ([nextBackoffMs]); going offline or
 * signing out pauses the queue, and coming back (or [kick]) resumes it at once.
 *
 * Runs only while the app process is alive (started from ServiceLocator.init). It never touches
 * the offline scan path, and it only ever sends scans the farmer explicitly asked to share (D-11).
 */
class ShareQueue(
    private val history: DiagnosisHistoryRepository,
    /** Shares one scan for the signed-in [ownerUid]; returns the cloud record id. */
    private val share: suspend (record: DiagnosisRecordEntity, ownerUid: String) -> String,
    private val signedInUid: Flow<String?>,
    private val online: Flow<Boolean>,
) {

    private val kicks = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Wake the queue now (a scan was just queued). */
    fun kick() {
        kicks.tryEmit(Unit)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(scope: CoroutineScope): Job = scope.launch {
        combine(signedInUid.distinctUntilChanged(), online.distinctUntilChanged()) { uid, isOnline ->
            uid.takeIf { isOnline }
        }
            .distinctUntilChanged()
            // A new state cancels the running drain: offline / signed out pauses it.
            .collectLatest { uid -> if (uid != null) drainWhileOnline(uid) }
    }

    private suspend fun drainWhileOnline(uid: String) {
        var backoff = INITIAL_BACKOFF_MS
        while (true) {
            val queued = runCatchingCancellable { history.getQueuedShares(uid) }.getOrDefault(emptyList())
            if (queued.isEmpty()) {
                kicks.first() // idle until something is queued
                continue
            }
            var anyFailed = false
            for (record in queued) {
                val result = runCatchingCancellable { share(record, uid) }
                result.onSuccess { cloudId -> history.markShared(record.localId, cloudId) }
                result.onFailure { error ->
                    anyFailed = true
                    history.setShareError(record.localId, error.message?.take(MAX_ERROR_CHARS) ?: error.javaClass.simpleName)
                }
            }
            if (anyFailed) {
                // Wait, but let a new queued scan (or a manual retry) wake it sooner.
                withTimeoutOrNull(backoff) { kicks.first() }
                backoff = nextBackoffMs(backoff)
            } else {
                backoff = INITIAL_BACKOFF_MS
            }
        }
    }

    companion object {
        const val INITIAL_BACKOFF_MS = 30_000L
        const val MAX_BACKOFF_MS = 30 * 60_000L
        private const val MAX_ERROR_CHARS = 200

        /** Doubles the wait after each failed round, capped at [MAX_BACKOFF_MS]. */
        fun nextBackoffMs(current: Long): Long = (current * 2).coerceAtMost(MAX_BACKOFF_MS)
    }
}
