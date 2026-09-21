package com.cornguard.app.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cornguard.app.data.local.db.entity.DiseaseReferenceEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies treatment/prevention reference content stays queryable offline and that a versioned
 * content update replaces the prior entry rather than duplicating it
 * (claude/04_DEVELOPMENT_RULES.md #12).
 */
@RunWith(AndroidJUnit4::class)
class DiseaseReferenceDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: com.cornguard.app.data.local.db.dao.DiseaseReferenceDao

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.diseaseReferenceDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private fun sampleReference(contentVersion: String) = DiseaseReferenceEntity(
        diseaseCode = "gray_leaf_spot",
        displayName = "Gray Leaf Spot",
        symptoms = "Rectangular tan-to-gray lesions on leaves.",
        treatmentSteps = "Apply an approved fungicide per label instructions.",
        preventionSteps = "Rotate crops; use resistant hybrids where available.",
        sourceReference = "Bukidnon Agricultural Office reference sheet",
        contentVersion = contentVersion,
        updatedAt = 1_000L
    )

    @Test
    fun upsertAndGetByCode_roundTrips() = runTest {
        dao.upsertAll(listOf(sampleReference(contentVersion = "v1")))

        val stored = dao.getByCode("gray_leaf_spot")

        assertNotNull(stored)
        assertEquals("v1", stored?.contentVersion)
    }

    @Test
    fun upsert_replacesRatherThanDuplicates() = runTest {
        dao.upsertAll(listOf(sampleReference(contentVersion = "v1")))
        dao.upsertAll(listOf(sampleReference(contentVersion = "v2")))

        val all = dao.observeAll().first()

        assertEquals(1, all.size)
        assertEquals("v2", all.first().contentVersion)
    }
}
