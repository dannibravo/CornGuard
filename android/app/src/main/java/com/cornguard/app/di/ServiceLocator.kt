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
import com.cornguard.app.data.repository.firebase.FirebaseAdminRepository
import com.cornguard.app.data.repository.firebase.FirebaseAuthRepository
import com.cornguard.app.data.repository.firebase.FirebaseCommunityRepository
import com.cornguard.app.data.repository.firebase.FirebaseDiagnosisSharingRepository
import com.cornguard.app.data.repository.firebase.FirebaseGisRepository
import com.cornguard.app.data.repository.firebase.FirebaseUserFarmRepository
import com.cornguard.app.data.repository.local.LocalDiagnosisHistoryRepository
import com.cornguard.app.data.repository.local.LocalDiseaseReferenceRepository
import com.cornguard.app.location.LocationHelper
import com.cornguard.app.model.CornLeafClassifier
import com.cornguard.app.model.PlaceholderCornLeafClassifier
import com.cornguard.app.model.TfliteCornLeafClassifier
import com.cornguard.app.permissions.PermissionManager
import com.cornguard.app.util.ConnectivityObserver
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
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
     * Sprint 2 (feature/tflite-integration): real inference when `assets/model.tflite` +
     * `assets/labels.json` are bundled (see [TfliteCornLeafClassifier.isAvailable]), falling back
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

    // Online-only (Sprint 1, feature/auth-service). Never called from the offline scan path —
    // claude/01_MASTER_DEVELOPMENT_CONTEXT.md's Project Principle. Callers must check
    // connectivityObserver first; these throw on no connectivity rather than silently no-op.
    val authRepository: AuthRepository by lazy { FirebaseAuthRepository(FirebaseAuth.getInstance()) }

    val userFarmRepository: UserFarmRepository by lazy {
        FirebaseUserFarmRepository(FirebaseFirestore.getInstance())
    }

    // Sprint 2 (feature/auth-service, continued). Not yet exercisable against the live dev
    // project — Storage requires the Blaze plan, deferred (firebase/README.md). Written and
    // ready for when that's revisited.
    val diagnosisSharingRepository: DiagnosisSharingRepository by lazy {
        FirebaseDiagnosisSharingRepository(FirebaseFirestore.getInstance(), FirebaseStorage.getInstance())
    }

    // Sprint 3 (feature/auth-service, continued), built against Firestore per the pragmatic
    // solo-context continuation note under D-01 (claude/03_SOURCE_ALIGNMENT_AND_DECISION_GATES.md)
    // — not the formal team decision. Image upload has the same Blaze-plan caveat as above.
    val communityRepository: CommunityRepository by lazy {
        FirebaseCommunityRepository(FirebaseFirestore.getInstance(), FirebaseStorage.getInstance())
    }

    // Sprint 4 (feature/auth-service, continued). Pragmatic D-10 lean documented in
    // claude/03_SOURCE_ALIGNMENT_AND_DECISION_GATES.md — this is the data layer only, no map-SDK
    // dependency. getHeatmapAggregates/getVerifiedOccurrences return empty until something is
    // actually verified — see adminRepository below for the (now real) path that unblocks that.
    val gisRepository: GisRepository by lazy {
        FirebaseGisRepository(FirebaseFirestore.getInstance(), FirebaseMessaging.getInstance())
    }

    // Sprint 5 (feature/auth-service, continued). verifyDiagnosisRecord/verifyPost are what
    // actually populate what gisRepository above reads — not blocked by D-02, since Admin is
    // already a confirmed role (see AdminRepository's doc comment).
    val adminRepository: AdminRepository by lazy {
        FirebaseAdminRepository(FirebaseFirestore.getInstance(), FirebaseFunctions.getInstance())
    }

    // Process-lifetime scope for startup-only work (seeding). Not exposed for general use —
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
            // Dev-only placeholder content so the Treatment screen has something to render before
            // Sprint 2's Scan flow exists. See DiseaseReferenceSeedData's doc comment — this is
            // NOT verified agricultural guidance.
            diseaseReferenceRepository.upsertAll(DiseaseReferenceSeedData.all)
        }
    }
}
