package com.cornguard.app.data.local.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies v1 → v2 (disease references gain `causes` / `duration`) and v2 → v3 (review metadata)
 * keep existing scan history and reference rows intact. Uses the exported schemas in app/schemas.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate1To2_keepsRowsAndAddsEmptyColumns() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO disease_references (diseaseCode, displayName, symptoms, treatmentSteps, " +
                    "preventionSteps, sourceReference, contentVersion, updatedAt) " +
                    "VALUES ('healthy', 'Healthy', 's', 't', 'p', 'src', 'v1', 1)"
            )
            execSQL(
                "INSERT INTO diagnosis_records (diseaseCode, displayLabel, confidence, imageUriOrLocalPath, " +
                    "capturedAt, modelVersion, sharedToCloud) " +
                    "VALUES ('common_rust', 'Common Rust', 0.9, '/x.jpg', 1, 'cornguard_mobilenetv2_v1', 0)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, AppDatabase.MIGRATION_1_2)

        db.query("SELECT treatmentSteps, causes, duration FROM disease_references WHERE diseaseCode = 'healthy'").use {
            it.moveToFirst()
            assertEquals("t", it.getString(0))
            assertEquals("", it.getString(1))
            assertEquals("", it.getString(2))
        }
        db.query("SELECT COUNT(*) FROM diagnosis_records").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
    }

    @Test
    fun migrate2To3_keepsRowsAndDefaultsReviewFields() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                "INSERT INTO disease_references (diseaseCode, displayName, symptoms, treatmentSteps, " +
                    "preventionSteps, causes, duration, sourceReference, contentVersion, updatedAt) " +
                    "VALUES ('healthy', 'Healthy', 's', 't', 'p', 'c', 'd', 'src', 'v1', 1)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, AppDatabase.MIGRATION_2_3)

        db.query("SELECT treatmentSteps, reviewStatus, reviewedBy, reviewedAt FROM disease_references WHERE diseaseCode = 'healthy'").use {
            it.moveToFirst()
            assertEquals("t", it.getString(0))
            // Existing content is never assumed reviewed.
            assertEquals("unreviewed", it.getString(1))
            assertEquals("", it.getString(2))
            assertEquals(0L, it.getLong(3))
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
