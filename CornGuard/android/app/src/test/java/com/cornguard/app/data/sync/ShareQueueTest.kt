package com.cornguard.app.data.sync

import com.cornguard.app.data.local.db.entity.DiagnosisRecordEntity
import com.cornguard.app.data.repository.DiagnosisHistoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShareQueueTest {

    /** In-memory stand-in for the Room-backed history (only what the queue uses). */
    private class FakeHistory(records: List<DiagnosisRecordEntity>) : DiagnosisHistoryRepository {
        val rows = MutableStateFlow(records.associateBy { it.localId })
        operator fun get(id: Long) = rows.value.getValue(id)
        private fun update(id: Long, f: (DiagnosisRecordEntity) -> DiagnosisRecordEntity) {
            rows.value = rows.value + (id to f(get(id)))
        }

        override suspend fun getQueuedShares(userId: String) =
            rows.value.values.filter { it.userId == userId && it.isShareQueued }.sortedBy { it.shareQueuedAt }
        override suspend fun markShared(localId: Long, cloudRecordId: String) = update(localId) {
            it.copy(sharedToCloud = true, cloudRecordId = cloudRecordId, shareQueuedAt = null, shareLastError = null)
        }
        override suspend fun setShareError(localId: Long, error: String?) = update(localId) { it.copy(shareLastError = error) }
        override suspend fun queueShare(localId: Long, queuedAt: Long) = update(localId) { it.copy(shareQueuedAt = queuedAt) }
        override suspend fun cancelQueuedShare(localId: Long) = update(localId) { it.copy(shareQueuedAt = null) }
        override fun observeById(localId: Long): Flow<DiagnosisRecordEntity?> = rows.map { it[localId] }
        override fun observeQueuedCount(userId: String?) = flowOf(0)
        override fun observeHistory(userId: String?) = flowOf(rows.value.values.toList())
        override suspend fun claimGuestScans(userId: String, ownCloudRecordIds: List<String>) = 0
        override suspend fun assignOwner(localId: Long, userId: String) = Unit
        override suspend fun getById(localId: Long) = rows.value[localId]
        override suspend fun saveScan(record: DiagnosisRecordEntity) = record.localId
        override suspend fun updateLocation(
            localId: Long, barangay: String, municipality: String, province: String,
            latitude: Double, longitude: Double, farmId: String?
        ) = Unit
        override suspend fun delete(record: DiagnosisRecordEntity) = Unit
    }

    private fun scan(id: Long, owner: String = "u1", queuedAt: Long? = 100L + id) = DiagnosisRecordEntity(
        localId = id, userId = owner, farmId = null, diseaseCode = "common_rust", displayLabel = "Common Rust",
        confidence = 0.9f, imageUriOrLocalPath = "/x.jpg", capturedAt = 1L, latitude = 8.0, longitude = 125.0,
        barangay = "Dalirig", municipality = "Manolo Fortich", province = "Bukidnon",
        modelVersion = "v4", shareQueuedAt = queuedAt
    )

    @Test
    fun sendsQueuedScansOldestFirstOnceOnlineAndSignedIn() = runTest {
        val history = FakeHistory(listOf(scan(2, queuedAt = 50), scan(1, queuedAt = 10), scan(3, queuedAt = null)))
        val sent = mutableListOf<Long>()
        val online = MutableStateFlow(false)
        val queue = ShareQueue(history, { r, _ -> sent += r.localId; "cloud-${r.localId}" }, flowOf("u1"), online)
        val job = queue.start(backgroundScope)

        runCurrent()
        assertTrue("nothing is sent offline", sent.isEmpty())

        online.value = true
        runCurrent()
        assertEquals(listOf(1L, 2L), sent) // oldest request first; scan 3 was never queued
        assertTrue(history[1].sharedToCloud)
        assertEquals("cloud-2", history[2].cloudRecordId)
        assertNull(history[2].shareQueuedAt)
        assertFalse(history[3].sharedToCloud)
        job.cancel()
    }

    @Test
    fun onlySendsTheSignedInOwnersScans() = runTest {
        val history = FakeHistory(listOf(scan(1, owner = "u1"), scan(2, owner = "u2")))
        val sent = mutableListOf<Long>()
        val uid = MutableStateFlow<String?>(null)
        ShareQueue(history, { r, _ -> sent += r.localId; "c" }, uid, flowOf(true)).start(backgroundScope)

        runCurrent()
        assertTrue("signed out: nothing sent", sent.isEmpty())
        uid.value = "u2"
        runCurrent()
        assertEquals(listOf(2L), sent)
    }

    @Test
    fun failedShareStaysQueuedWithItsErrorAndIsRetriedWithBackoff() = runTest {
        val history = FakeHistory(listOf(scan(1)))
        var attempts = 0
        ShareQueue(
            history,
            { _, _ -> if (++attempts < 3) throw IllegalStateException("network down") else "cloud-1" },
            flowOf("u1"), flowOf(true)
        ).start(backgroundScope)

        runCurrent()
        assertEquals(1, attempts)
        assertTrue(history[1].isShareQueued)
        assertEquals("network down", history[1].shareLastError)

        advanceTimeBy(ShareQueue.INITIAL_BACKOFF_MS - 1); runCurrent()
        assertEquals("waits out the first backoff", 1, attempts)
        advanceTimeBy(1); runCurrent()
        assertEquals(2, attempts)

        advanceTimeBy(2 * ShareQueue.INITIAL_BACKOFF_MS); runCurrent()
        assertEquals("second wait is doubled", 3, attempts)
        assertTrue(history[1].sharedToCloud)
        assertNull(history[1].shareLastError)
    }

    @Test
    fun kickWakesAnIdleQueueForANewlyQueuedScan() = runTest {
        val history = FakeHistory(listOf(scan(1, queuedAt = null)))
        val sent = mutableListOf<Long>()
        val queue = ShareQueue(history, { r, _ -> sent += r.localId; "c" }, flowOf("u1"), flowOf(true))
        queue.start(backgroundScope)
        runCurrent()
        assertTrue(sent.isEmpty())

        history.queueShare(1, 500)
        queue.kick()
        runCurrent()
        assertEquals(listOf(1L), sent)
    }

    @Test
    fun backoffDoublesAndIsCapped() {
        assertEquals(60_000L, ShareQueue.nextBackoffMs(30_000L))
        assertEquals(ShareQueue.MAX_BACKOFF_MS, ShareQueue.nextBackoffMs(ShareQueue.MAX_BACKOFF_MS))
        assertEquals(ShareQueue.MAX_BACKOFF_MS, ShareQueue.nextBackoffMs(ShareQueue.MAX_BACKOFF_MS - 1))
    }
}
