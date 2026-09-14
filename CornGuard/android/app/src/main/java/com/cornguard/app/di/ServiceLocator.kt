package com.cornguard.app.di

import android.content.Context
import com.cornguard.app.data.local.db.AppDatabase
import com.cornguard.app.data.repository.DiagnosisHistoryRepository
import com.cornguard.app.data.repository.DiseaseReferenceRepository
import com.cornguard.app.data.repository.local.LocalDiagnosisHistoryRepository
import com.cornguard.app.data.repository.local.LocalDiseaseReferenceRepository
import com.cornguard.app.model.CornLeafClassifier
import com.cornguard.app.model.PlaceholderCornLeafClassifier
import com.cornguard.app.permissions.PermissionManager
import com.cornguard.app.util.ConnectivityObserver

/**
 * Manual, process-lifetime service locator. CORNGUARD does not use a DI framework in Sprint 0 —
 * the module surface is small enough that a framework would be scope beyond what Sprint 0 asks
 * for (claude/05_DEVELOPMENT_PLAN.md). Every dependency exposed here is behind an interface so a
 * framework can be introduced later without touching call sites.
 *
 * Must be initialized once, from [com.cornguard.app.CornGuardApplication.onCreate].
 */
object ServiceLocator {

    private lateinit var appContext: Context

    private val database: AppDatabase by lazy { AppDatabase.getInstance(appContext) }

    val diagnosisHistoryRepository: DiagnosisHistoryRepository by lazy {
        LocalDiagnosisHistoryRepository(database.diagnosisRecordDao())
    }

    val diseaseReferenceRepository: DiseaseReferenceRepository by lazy {
        LocalDiseaseReferenceRepository(database.diseaseReferenceDao())
    }

    /**
     * Placeholder until Sprint 2 wires in the real TFLite-backed classifier
     * (see [PlaceholderCornLeafClassifier]).
     */
    val cornLeafClassifier: CornLeafClassifier by lazy { PlaceholderCornLeafClassifier() }

    val permissionManager: PermissionManager by lazy { PermissionManager(appContext) }

    val connectivityObserver: ConnectivityObserver by lazy { ConnectivityObserver(appContext) }

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}
