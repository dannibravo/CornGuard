package com.cornguard.app.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cornguard.app.data.local.db.dao.DiagnosisRecordDao
import com.cornguard.app.data.local.db.dao.DiseaseReferenceDao
import com.cornguard.app.data.local.db.entity.DiagnosisRecordEntity
import com.cornguard.app.data.local.db.entity.DiseaseReferenceEntity

/**
 * The guaranteed-offline SQLite store (claude/01_MASTER_DEVELOPMENT_CONTEXT.md Offline Data).
 * Cached community content, if implemented, must live elsewhere and never be merged into this
 * database (claude/08_DATA_AND_INTEGRATION_CONTRACT.md CachedCommunityContent rule).
 */
@Database(
    entities = [DiagnosisRecordEntity::class, DiseaseReferenceEntity::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun diagnosisRecordDao(): DiagnosisRecordDao
    abstract fun diseaseReferenceDao(): DiseaseReferenceDao

    companion object {
        private const val DATABASE_NAME = "cornguard.db"

        /** v2: disease references gain `causes` and `duration` (caps 3 knowledge base). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `disease_references` ADD COLUMN `causes` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `disease_references` ADD COLUMN `duration` TEXT NOT NULL DEFAULT ''")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
        }
    }
}
