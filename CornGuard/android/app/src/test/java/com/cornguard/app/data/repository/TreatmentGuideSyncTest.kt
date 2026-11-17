package com.cornguard.app.data.repository

import com.cornguard.app.data.local.db.entity.DiseaseReferenceEntity
import com.cornguard.app.data.remote.convex.TreatmentGuideDto
import com.cornguard.app.model.ReviewStatus
import com.cornguard.app.model.reviewLabelFor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TreatmentGuideSyncTest {

    private class FakeRepository : DiseaseReferenceRepository {
        val rows = linkedMapOf<String, DiseaseReferenceEntity>()
        override fun observeAll(): Flow<List<DiseaseReferenceEntity>> = MutableStateFlow(rows.values.toList())
        override suspend fun getByCode(diseaseCode: String) = rows[diseaseCode]
        override suspend fun upsertAll(references: List<DiseaseReferenceEntity>) {
            references.forEach { rows[it.diseaseCode] = it }
        }
        override suspend fun replaceAll(references: List<DiseaseReferenceEntity>) {
            rows.clear(); upsertAll(references)
        }
    }

    private class FakeMarker(var at: Long = 0L) : TreatmentGuideSync.SyncMarker {
        override fun lastSyncedAt() = at
        override fun markSynced(at: Long) { this.at = at }
    }

    private fun guide(code: String, treatment: String = "Spray X") = TreatmentGuideDto(
        diseaseCode = code, displayName = code, treatmentSteps = treatment, contentVersion = "v1",
        updatedAt = 1000.0, reviewStatus = "sources_reviewed", reviewedBy = "team", reviewedAt = 2000.0
    )

    private val snapshotJson = """[{"diseaseCode":"common_rust","displayName":"Common Rust","treatmentSteps":"Snapshot text"}]"""

    @Test
    fun neverSyncedInstallGetsTheSnapshot() = runTest {
        val repo = FakeRepository()
        TreatmentGuideSync(repo, FakeMarker()).loadSnapshotIfNeverSynced { snapshotJson }
        assertEquals("Snapshot text", repo.getByCode("common_rust")?.treatmentSteps)
    }

    @Test
    fun syncedInstallIgnoresTheSnapshot() = runTest {
        val repo = FakeRepository()
        var read = false
        TreatmentGuideSync(repo, FakeMarker(at = 5L)).loadSnapshotIfNeverSynced { read = true; snapshotJson }
        assertTrue("snapshot must not even be read", !read)
        assertTrue(repo.rows.isEmpty())
    }

    @Test
    fun brokenSnapshotChangesNothing() = runTest {
        val repo = FakeRepository().apply { upsertAll(listOf(guide("healthy").toEntity())) }
        TreatmentGuideSync(repo, FakeMarker()).loadSnapshotIfNeverSynced { "not json" }
        assertEquals(setOf("healthy"), repo.rows.keys)
    }

    @Test
    fun failedOrOfflineUpdateNeverWipesTheCache() = runTest {
        val repo = FakeRepository()
        val marker = FakeMarker()
        val sync = TreatmentGuideSync(repo, marker)
        sync.apply(listOf(guide("common_rust")))
        sync.apply(null)
        assertEquals(setOf("common_rust"), repo.rows.keys)
        assertTrue(marker.at > 0)
    }

    @Test
    fun serverListReplacesTheCacheIncludingEditsAndDeletions() = runTest {
        val repo = FakeRepository()
        val sync = TreatmentGuideSync(repo, FakeMarker(), clock = { 42L })
        sync.apply(listOf(guide("common_rust"), guide("gray_leaf_spot")))
        sync.apply(listOf(guide("common_rust", treatment = "Edited by admin")))
        assertEquals(setOf("common_rust"), repo.rows.keys)
        assertEquals("Edited by admin", repo.getByCode("common_rust")?.treatmentSteps)
    }

    @Test
    fun runLoadsSnapshotBeforeServerUpdatesSoServerWins() = runTest {
        val repo = FakeRepository()
        val marker = FakeMarker()
        TreatmentGuideSync(repo, marker).run({ snapshotJson }, flowOf(null, listOf(guide("common_rust", "Server text"))))
        assertEquals("Server text", repo.getByCode("common_rust")?.treatmentSteps)
        assertTrue(marker.at > 0)
    }

    @Test
    fun dtoMapsToEntityAndKeepsTheReviewLabel() {
        val e = guide("common_rust").toEntity()
        assertEquals(1000L, e.updatedAt)
        assertEquals(2000L, e.reviewedAt)
        assertEquals("v1", e.contentVersion)
        assertEquals(ReviewStatus.SOURCES_REVIEWED, reviewLabelFor(e.copy(sourceReference = "CPN 2026"))?.status)
    }
}
