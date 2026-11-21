package com.cornguard.app.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun diagnosisRecordDao(): DiagnosisRecordDao
    abstract fun diseaseReferenceDao(): DiseaseReferenceDao

    companion object {
        private const val DATABASE_NAME = "cornguard.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                ).build().also { instance = it }
            }
        }
    }
}
