package com.cornguard.app.di

import android.content.Context
import android.provider.Settings
import com.cornguard.app.data.local.db.AppDatabase
import com.cornguard.app.data.local.db.DiseaseReferenceSeedData
import com.cornguard.app.data.repository.AdminRepository
import com.cornguard.app.data.repository.AuthRepository
import com.cornguard.app.data.repository.CommunityRepository
import com.cornguard.app.data.repository.DiagnosisHistoryRepository
import com.cornguard.app.data.repository.DiagnosisSharingRepository
import com.cornguard.app.data.repository.DiseaseReferenceRepository
import com.cornguard.app.data.repository.GisRepository
import com.cornguard.app.data.repository.UserFarmRepository
import com.cornguard.app.BuildConfig
import com.cornguard.app.data.remote.convex.ConvexBackend
import com.cornguard.app.data.repository.convex.ConvexAdminRepository
import com.cornguard.app.data.repository.convex.ConvexAuthRepository
import com.cornguard.app.data.repository.convex.ConvexCommunityRepository
import com.cornguard.app.data.repository.convex.ConvexDiagnosisSharingRepository
import com.cornguard.app.data.repository.convex.ConvexGisRepository
import com.cornguard.app.data.repository.convex.ConvexUserFarmRepository
import com.cornguard.app.data.repository.local.LocalDiagnosisHistoryRepository
import com.cornguard.app.data.repository.local.LocalDiseaseReferenceRepository
import com.cornguard.app.location.BarangayResolver
import com.cornguard.app.location.LocationHelper
import com.cornguard.app.model.CornLeafClassifier
import com.cornguard.app.model.PlaceholderCornLeafClassifier
import com.cornguard.app.model.TfliteCornLeafClassifier
import com.cornguard.app.permissions.PermissionManager
import com.cornguard.app.util.ConnectivityObserver
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
     * Real inference with the bundled trained model when `assets/model.tflite` +
     * `assets/labels.json` are present (see [TfliteCornLeafClassifier.isAvailable]), falling back
     * to [PlaceholderCornLeafClassifier] on a dev build without them — never a silent fake result.
     */
    val cornLeafClassifier: CornLeafClassifier by lazy {
        if (TfliteCornLeafClassifier.isAvailable(appContext)) {
            TfliteCornLeafClassifier(appContext)
        } else {
            PlaceholderCornLeafClassifier()
        }
    }

    val permissionManager: PermissionManager by lazy { PermissionManager(appContext) }

    val connectivityObserver: ConnectivityObserver by lazy { ConnectivityObserver(appContext) }

    val locationHelper: LocationHelper by lazy { LocationHelper(appContext) }

    /** GPS → barangay over the bundled Bukidnon boundaries (parsed once, ~430 KB). */
    val barangayResolver: BarangayResolver by lazy {
        BarangayResolver(appContext.assets.open(BarangayResolver.ASSET_PATH).bufferedReader().use { it.readText() })
    }

    // Online-only, backed by Convex (backend/convex). Never called from the offline scan path —
    // claude/01_MASTER_DEVELOPMENT_CONTEXT.md's Project Principle. One Convex connection serves
    // every repository below; its URL comes from BuildConfig.CONVEX_URL (android/local.properties).
    private val convexBackend: ConvexBackend by lazy { ConvexBackend(appContext, BuildConfig.CONVEX_URL) }

    private val convexAuthRepository: ConvexAuthRepository by lazy {
        ConvexAuthRepository(appContext, convexBackend)
    }

    val authRepository: AuthRepository get() = convexAuthRepository

    val userFarmRepository: UserFarmRepository by lazy { ConvexUserFarmRepository(convexBackend.client) }

    val diagnosisSharingRepository: DiagnosisSharingRepository by lazy {
        ConvexDiagnosisSharingRepository(convexBackend.client, convexBackend.uploader)
    }

    val communityRepository: CommunityRepository by lazy {
        ConvexCommunityRepository(convexBackend.client, convexBackend.uploader)
    }

    // Area topics are still plain FCM topics — push delivery is the only Firebase piece left.
    val gisRepository: GisRepository by lazy {
        ConvexGisRepository(convexBackend.client, FirebaseMessaging.getInstance())
    }

    val adminRepository: AdminRepository by lazy { ConvexAdminRepository(convexBackend.client) }

    // Process-lifetime scope for startup-only work (seeding, session restore). Not exposed for general use —
    // screens use viewLifecycleOwner.lifecycleScope, not this.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Stable per-install identifier used as the `{deviceId}` half of GisRepository's
     * `{userId}-{deviceId}` device token document id (fcm-plan.md). ANDROID_ID is stable for the
     * life of the app install on API 26+ and needs no permission or persisted state of our own.
     */
    val deviceId: String by lazy {
        Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown-device"
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        appScope.launch {
            // Upserted every launch so updated reference content replaces older copies. See
            // DiseaseReferenceSeedData's doc comment — not yet expert-verified guidance.
            diseaseReferenceRepository.upsertAll(DiseaseReferenceSeedData.all)
        }
        if (BuildConfig.CONVEX_URL.isNotBlank()) {
            // Keeps a signed-in user signed in across restarts (refresh-token exchange).
            appScope.launch { runCatching { convexAuthRepository.restoreSession() } }
        }
    }
}
