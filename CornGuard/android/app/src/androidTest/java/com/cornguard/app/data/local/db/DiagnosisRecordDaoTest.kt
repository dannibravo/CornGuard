package com.cornguard.app.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cornguard.app.data.local.db.entity.DiagnosisRecordEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the local diagnosis history round-trip that the offline Scan History screen depends
 * on (claude/12_TESTING_AND_ACCEPTANCE_PLAN.md #2: "local database create/read/update/delete").
 * Uses an in-memory database so it exercises the real Room-generated SQL, not a fake.
 */
@RunWith(AndroidJUnit4::class)
class DiagnosisRecordDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: com.cornguard.app.data.local.db.dao.DiagnosisRecordDao

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.diagnosisRecordDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private fun sampleRecord(capturedAt: Long) = DiagnosisRecordEntity(
        userId = null,
        farmId = null,
        diseaseCode = "common_rust",
        displayLabel = "Common Rust",
        confidence = 0.87f,
        imageUriOrLocalPath = "content://media/external/images/media/1",
        capturedAt = capturedAt,
        latitude = null,
        longitude = null,
        barangay = null,
        municipality = null,
        province = null,
        modelVersion = "cornguard_mobilenetv2_v1"
    )

    @Test
    fun insertAndObserve_returnsNewestScanFirst() = runTest {
        val olderId = dao.insert(sampleRecord(capturedAt = 1_000L))
        val newerId = dao.insert(sampleRecord(capturedAt = 2_000L))

        val all = dao.observeAll().first()

        assertEquals(2, all.size)
        assertEquals(newerId, all.first().localId)
        assertEquals(olderId, all.last().localId)
    }

    @Test
    fun insertedRecord_defaultsToLocalAndUnshared() = runTest {
        val id = dao.insert(sampleRecord(capturedAt = 1_000L))

        val stored = dao.getById(id)

        assertTrue(stored != null && !stored.sharedToCloud)
        assertNull(stored?.cloudRecordId)
    }

    @Test
    fun markShared_setsCloudRecordId() = runTest {
        val id = dao.insert(sampleRecord(capturedAt = 1_000L))

        dao.markShared(id, cloudRecordId = "cloud-abc123")
        val stored = dao.getById(id)

        assertTrue(stored?.sharedToCloud == true)
        assertEquals("cloud-abc123", stored?.cloudRecordId)
    }

    @Test
    fun delete_removesRecord() = runTest {
        val id = dao.insert(sampleRecord(capturedAt = 1_000L))
        val stored = requireNotNull(dao.getById(id))

        dao.delete(stored)

        assertNull(dao.getById(id))
    }
}
