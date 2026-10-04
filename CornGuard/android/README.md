# CORNGUARD — Android Application (Ligue)

This directory holds the Sprint 0 deliverables owned by the Android Mobile & Integration Lead,
per `claude/05_DEVELOPMENT_PLAN.md` (Sprint 0) and `claude/13_DEVELOPMENT_PHASE_GUIDE.md`
(Phase 0). It is a standard Kotlin/Android Studio Gradle project (Kotlin, XML layouts, Android
SDK — `claude/02_PROJECT_CONTEXT.md`).

## Opening the project

Open this `android/` directory (not the repo root) in Android Studio. Copy
`local.properties.example` to `local.properties` and set `sdk.dir` — `local.properties` is
git-ignored per `claude/10_ENV_GUIDE.md`.

`minSdk 26` (Android 8.0), `targetSdk`/`compileSdk 34` — the safer NFR baseline per
`claude/02_PROJECT_CONTEXT.md` until the manuscript's API-24-vs-8.0 wording is harmonized.

## Scope of this drop (Sprint 0)

- Gradle project structure (Kotlin DSL, version catalog, Gradle wrapper).
- Package/module conventions (see below).
- Base navigation: single-Activity shell, bottom nav (`Home`, `Scan`, `History`, `Map`,
  `Community`) driving a Jetpack Navigation graph, with `Diagnosis Result` and `Treatment`
  reachable only from `Scan` — matching the confirmed screens and Disease Scan Workflow in
  `claude/01_MASTER_DEVELOPMENT_CONTEXT.md`.
- A reusable connectivity banner (`ui.common.OfflineStatusBanner`) hosted once above the nav host,
  driven by `util.ConnectivityObserver`, so every screen shares one online/offline indicator
  (`claude/04_DEVELOPMENT_RULES.md` #11).
- Local SQLite abstraction: Room database (`data.local.db`) implementing the
  `LocalDiagnosisRecord` and `LocalDiseaseReference` contracts from
  `claude/08_DATA_AND_INTEGRATION_CONTRACT.md`, behind repository interfaces
  (`data.repository`) so the offline scan/history/treatment path never depends on the cloud backend.
- Camera/gallery/location/notification permission **strategy** (`permissions.AppPermission`,
  `permissions.PermissionManager`) — state checks only; per-screen request UX/rationale flows are
  built with the feature that needs them, starting Sprint 1/2.
- The model-service interface (`model.CornLeafClassifier`), implemented by
  `model.TfliteCornLeafClassifier` running the trained model bundled in `app/src/main/assets/`
  (see "Disease model" below). `model.PlaceholderCornLeafClassifier` remains the fallback if those
  assets are missing — it throws `ModelNotReadyException` rather than fabricate a disease label.
- `debug`/`release` build types (`app/build.gradle.kts`). No signing config is wired — a release
  keystore must never be committed (`claude/10_ENV_GUIDE.md`); it is injected from a controlled
  location when Sprint 7 actually produces a release build.

## Disease model

The app bundles four files in `app/src/main/assets/`, read by `TfliteCornLeafClassifier`:

| File | Contents |
|---|---|
| `model.tflite` | `cornguard_mobilenetv2_v3`: MobileNetV2 transfer model, dynamic-range quantised |
| `labels.json` | Output index → disease code: 0 `northern_leaf_blight`, 1 `common_rust`, 2 `gray_leaf_spot`, 3 `healthy` |
| `model_config.json` | `input_size` 224, `normalization` `minus_one_to_one` |
| `model_version.txt` | Version string saved with every scan |

- **Training:** done in Colab with `CORNGUARD_EDA_&_TRAINING_v2.ipynb`, which is kept outside this
  repo. The notebook's final step writes these four files to `Acenas_Dataset/models/android_assets/`
  in Google Drive.
- **Updating the model:** copy those four files over the ones here, then rebuild. Also update
  `EXPECTED_MODEL_SHA256` in `EmulatorLeafImagesTest` and the version in `TfliteCornLeafClassifierTest`.
- **Input:** `[1, 224, 224, 3]` float32 RGB. The app crops the centre square and resizes bilinearly,
  the same way the notebook does.
- **Scaling:** the model has no scaling layer inside it. Training used
  `mobilenet_v2.preprocess_input` (`x / 127.5 − 1`), so `model_config.json` must stay `minus_one_to_one`.
- **Output:** `[1, 4]` softmax probabilities. The model's "Blight" class is shown in the app as
  Northern Leaf Blight.
- **Runtime:** the model was converted with TensorFlow 2.20 and needs TFLite 2.17 or newer. Older
  runtimes fail with "Didn't find op for builtin opcode".
- **Tests:**
  - `TfliteCornLeafClassifierTest` checks that the model loads and returns four probabilities.
  - `EmulatorLeafImagesTest` checks that the installed app runs the expected model file, then
    classifies the photos in the device's `Pictures/CornGuard/` folder and logs the results
    (`adb logcat -s LeafImageTest`).

## Outbreak Heatmap and map data

The Map screen is caps 3's Outbreak Heatmap: a Leaflet map in a WebView (`ui/map/MapFragment`)
showing Bukidnon's barangay boundaries over OpenStreetMap, with one dot per verified report.

- **Assets** (`app/src/main/assets/`), served to the WebView by `WebViewAssetLoader` at
  `https://appassets.androidplatform.net/assets/...`:

  | Path | Contents |
  |---|---|
  | `map/outbreak-map.html` | The map page, ported from caps 3's `my-app/assets/map/outbreak-map.html` |
  | `map/leaflet/` | Leaflet 1.9.4 (`leaflet.js`, `leaflet.css`, marker images) |
  | `geo/bukidnon-barangays.json` | 464 barangay polygons, 22 municipalities (433 KB) |

  Boundaries and dots draw without internet; only the OpenStreetMap basemap tiles need it.
- **Dots and colours:** fill = the barangay's severity tier for that disease (red severe, amber
  moderate, green mild); ring = disease (blue Northern Leaf Blight, pink Common Rust, violet Gray Leaf
  Spot). Tapping a barangay shows its active diseases, severity, farms affected and report count.
- **Data:** `MapFragment` collects two live Convex queries and pushes them into the page with
  `window.updateMapData({stats, detections})`:
  - `barangayStats:getAllStats`: the severity engine (`backend/convex/barangayStats.ts`, ported from
    caps 3). It covers the last 14 days. Each report is weighted by confidence (1, 0.6 or 0.3).
    Severe is a score of at least 4 from at least 3 farms; moderate is a score of at least 2, or 2 farms.
  - `diagnosisRecords:mapReports`: verified shared scans that have coordinates.

  Shares at 85% confidence or higher are auto-verified, as in caps 3. The rest wait for an admin.
  Both queries require sign-in.
- **Barangay on scans:** `location/BarangayResolver` runs a point-in-polygon check of the scan's GPS
  fix against the same GeoJSON, so reports use the polygon's exact names. If there's no fix, or it's
  outside Bukidnon, the result sheet offers "Set location manually". Picking a barangay uses its
  centroid as the coordinates.
- **Outbreak alerts:** when a barangay and disease first turn Severe, `backend/convex/outbreakAlerts.ts`
  pushes an alert through FCM (`push.ts`, which needs `FCM_SERVICE_ACCOUNT_JSON` set in Convex).
  - **Who gets it:** every farmer with a signed-in device whose profile barangay is that one or a
    neighbouring one. Profile names are matched loosely, so "Valencia" matches "City of Valencia".
  - **Neighbours:** `convex/barangayNeighbors.ts` is generated from the boundary file by
    `backend/scripts/build-barangay-neighbors.mjs`.
  - **On the phone:** the alert arrives on the high-importance "Outbreak Alerts" channel, and tapping
    it opens the map.
- **Demo data:** `node backend/scripts/seed-map-demo.mjs` shares seven demo reports (one severe,
  one moderate and one mild barangay) through the real `share` mutation; `--clear` removes them.

### Where `bukidnon-barangays.json` comes from

It is caps 3's `my-app/assets/geo/bukidnon-barangays.json`, copied unchanged. Of the whole
`barangay-boundaries-repository` (about 400 MB), this is the only data the heatmap needs:

1. **Source:** the repository's 2023-10-24 snapshot, `2023-10-24/enriched_t0p005/adm4.geojson`
   (42,048 barangays nationwide, simplified at 0.005°).
2. **Filter:** only features with `ADM2_EN == "Bukidnon"`, which gives 464 polygons. The geometry is
   identical to the repository's.
3. **Trim:** the properties are cut down to `barangay` and `municipality`. 44 names were cleaned
   up, for example "Imbatug (Pob.)" → "Imbatug". Municipality names stay official, for example
   "City of Valencia".

The rest of the repository isn't used: the RDF/PSGC history, the other administrative levels, and
the Python toolchain. So it isn't copied into this repo. Licences and credits are in
`THIRD_PARTY_NOTICES.md` at the repo root:
- MIT for the repository
- PSA and NAMRIA for the boundary data, also shown in the map's attribution
- BSD-2 for Leaflet
- ODbL for the OpenStreetMap tiles

## Cloud backend: Convex (migrated from Firebase)

The online features (accounts, profiles and farms, scan sharing, community, map data, admin) now
run on Convex (`backend/`, see `backend/README.md`). Firebase remains only for push delivery (FCM).

- The repository interfaces in `data.repository` are unchanged. The implementations are in
  `data.repository.convex`, and the connection, auth handshake, image uploader and DTOs are in
  `data.remote.convex`.
- **Setup:**
  - Add `convex.url=https://<deployment>.convex.cloud` to `android/local.properties`. It becomes
    `BuildConfig.CONVEX_URL`. Without it the app still builds and works offline; online screens
    fail with a message saying Convex isn't configured.
  - `app/google-services.json` (FCM client config for the `cornguard-app` Firebase project) is
    committed; the old `firebase/` folder was removed.
- **Sessions:** a user stays signed in across restarts via Convex Auth's refresh token, stored in
  private SharedPreferences. `ConvexAuthProvider` refreshes the one-hour JWT in the background.
- **Password rules:** passwords need at least 8 characters. Password reset isn't available yet.
- **History:** the Sprint 1–5 sections below describe the original Firebase implementation. The
  `data.repository.firebase` package and the whole `firebase/` folder they mention (rules,
  functions, scripts, docs) were removed in the migration; the same logic lives in `backend/convex/`.

## Sprint 1 addition (`feature/auth-service`) — Firebase Auth + cloud repositories

Adds, on top of the Sprint 0 foundation above:

- `google-services` Gradle plugin + Firebase BoM/Auth/Firestore dependencies
  (`build.gradle.kts`, `app/build.gradle.kts`, `gradle/libs.versions.toml`).
- `data.repository.AuthRepository` + `data.repository.firebase.FirebaseAuthRepository` — mirrors
  `firebase/repositories/auth-repository-interface.md`. Email/password only for Sprint 1 (matches
  the provider actually enabled in the dev Firebase project); phone auth is deferred, not
  half-built, until the Phone provider and an Activity-bound verification UI are designed.
- `data.repository.UserFarmRepository` + `data.repository.firebase.FirebaseUserFarmRepository` —
  mirrors `firebase/repositories/user-farm-repository-interface.md`. Field names in Firestore
  documents match `firebase/schema/logical-schema.md` exactly (snake_case) — do not rename either
  side without updating the other.
- `ServiceLocator` gains `authRepository` and `userFarmRepository`, matching the existing lazy
  singleton pattern. Both are **online-only**: they throw on no connectivity/an expired session
  rather than silently no-op, and no offline screen may depend on them
  (`claude/01_MASTER_DEVELOPMENT_CONTEXT.md` Project Principle).
- `app/google-services.json` is **not committed** (already covered by this module's `.gitignore`)
  — copy it from `firebase/config/google-services.dev.json` locally. It now registers two Firebase
  Android app clients under the same dev project: `com.cornguard.app` (release) and
  `com.cornguard.app.debug` (the debug build type's `applicationIdSuffix`) — the debug build fails
  at `processDebugGoogleServices` without the second client entry.
- No dev/prod product flavor dimension yet — a "prod" flavor needs a prod Firebase project to
  point it at, which is Sprint 7 work per `claude/10_ENV_GUIDE.md`'s Production Configuration
  Freeze. Adding an empty flavor now would be structure with nothing real behind it.
- Verified with `./gradlew :app:compileDebugKotlin` and `:app:testDebugUnitTest` — both pass.

### Sprint 2 (continued on this branch) — cloud diagnosis-sharing

- `data.repository.DiagnosisSharingRepository` +
  `data.repository.firebase.FirebaseDiagnosisSharingRepository` — mirrors
  `firebase/repositories/diagnosis-sharing-repository-interface.md`. Deliberately takes the
  existing `DiagnosisRecordEntity` directly rather than introducing a parallel
  `ShareableScanDraft` Kotlin type — same concept, no duplicated model.
- Uploads the scan image to Cloud Storage at `diagnosisImages/{userId}/{recordId}/{fileName}`
  (matching `firebase/security/storage.rules` exactly), then creates the
  `diagnosisRecordsCloud/{recordId}` Firestore document with the resulting download URL.
- Per D-11, this is opt-in only — nothing calls it automatically from the scan flow. Per
  `claude/04_DEVELOPMENT_RULES.md` #16, it never touches local SQLite history; callers mark a
  local record as shared themselves via `DiagnosisHistoryRepository.markShared` afterward.
- Added `firebase-storage-ktx`. Compiles and passes the existing test suite, but **not yet
  exercisable against the live project** — Cloud Storage needs the Blaze plan (same as the
  Firestore/Storage rules deferral already noted in `firebase/README.md`).

### Sprint 3 (continued on this branch) — community posts, comments, upvotes

Built against Firestore per the pragmatic solo-context continuation note added under D-01
(`claude/03_SOURCE_ALIGNMENT_AND_DECISION_GATES.md`, 2026-09-21) — **not** the formal three-person
team decision D-01 still requires. Ligue and Acenas were unavailable; see that note for the full
reasoning and what happens if the team later picks Realtime Database instead.

- `data.repository.CommunityRepository` + `data.repository.firebase.FirebaseCommunityRepository` —
  mirrors `firebase/repositories/community-repository-interface.md`. Posts, single-level comments,
  and upvotes (via a `votes/{userId}` subcollection document, not a client-writable counter).
- `getPostsFeed`'s filter combinations (`moderation_status="visible"` + optional single area level
  + optional disease tag, ordered by `created_at` desc) match the composite indexes in
  `firebase/firestore.indexes.json` exactly — adding a new filter combination needs a matching
  index added there first, or the query throws at runtime.
- `toggleUpvote` only ever writes the `votes/{userId}` document — it deliberately does not touch
  `upvote_count` itself. That field is recomputed server-side by `onVoteWrite`
  (`firebase/functions/index.mjs`), a real Cloud Function trigger, not a TODO.
- `prefillPostFromScan` builds a local-only `DraftPost` from an existing `DiagnosisRecordEntity` —
  purely local, never touches the network, and never calls `createPost` itself; the farmer must
  still review and submit.
- Post images upload to `postImages/{postId}/{fileName}`, matching `security/storage.rules`
  exactly — same Blaze-plan deferral as the diagnosis-sharing repository above.
- Compiles and passes the existing test suite (`./gradlew :app:compileDebugKotlin`,
  `:app:testDebugUnitTest`).

### Sprint 4 (continued on this branch) — GIS data layer, FCM device tokens/topics

Pragmatic D-10 lean documented in `claude/03_SOURCE_ALIGNMENT_AND_DECISION_GATES.md`
(2026-09-21) — not the formal team decision. Unlike D-01, this doesn't even bind any map-SDK
code: `data.repository.GisRepository` has zero map-SDK dependency, only Firestore/FCM.

- `data.repository.GisRepository` + `data.repository.firebase.FirebaseGisRepository` — mirrors
  `firebase/gis/gis-service-interface.md`. `getNearbyReports` (from `communityPosts`, any
  verification status — informational display per D-08) vs. `getHeatmapAggregates` /
  `getVerifiedOccurrences` (from `diagnosisRecordsCloud`, **hardcoded** to
  `verification_status="verified"`, not a caller-settable filter).
- The verified-only filter is deliberate, not a placeholder to loosen later without a decision:
  per D-08, unverified reports must not read as confirmed outbreaks, and per D-07 no production
  outbreak signal may go out without expert-approved validation. Since nothing has an authorized
  path to set `verification_status="verified"` yet (D-02 unresolved), **both methods correctly
  return empty for now** — same "stays inert until approved" pattern as `outbreakRules`.
- `registerDeviceToken`/`deactivateDeviceToken` write `deviceTokens/{userId}-{deviceId}` (a
  deterministic id, so a token refresh updates in place rather than duplicating — matches
  `firebase/notifications/fcm-plan.md`'s device token lifecycle).
- `subscribeToAreaTopic`/`unsubscribeFromAreaTopic` wrap `FirebaseMessaging`'s topic APIs directly
  — topic naming (`barangay_<slug>`, etc.) matches what `firebase/functions/index.mjs`'s
  `onNotificationCreate` now actually sends to (see that branch's latest commit — area-scoped
  notifications used to silently do nothing; fixed there, this is the client-side half).
- Added `firebase-messaging-ktx`. Compiles cleanly, existing test suite still passes.

### Sprint 5 (continued on this branch) — admin authorization, moderation, user management

- `data.repository.AdminRepository` + `data.repository.firebase.FirebaseAdminRepository`. Every
  method corresponds to an operation `security/firestore.rules` only allows an `isAdmin()` caller
  to perform — authorization is enforced server-side, not re-checked client-side
  (`claude/04_DEVELOPMENT_RULES.md` #14).
- `verifyDiagnosisRecord`/`verifyPost` are **not blocked by D-02** — Admin is already a confirmed
  role, distinct from the conditional Technician role D-02 gates. These are what actually populate
  what `gisRepository`'s `getHeatmapAggregates`/`getVerifiedOccurrences` read — verified via a real
  automated rules test (`firebase/tests/rules.test.mjs`), not just asserted in a comment.
- `promoteToAdmin` calls the Cloud Function of the same name (`firebase/functions/index.mjs`) —
  the very first admin still can't be created this way (needs an existing admin to call it);
  that's `firebase/scripts/bootstrap-first-admin.mjs`'s job, done once, out-of-band.
- `setPostModerationStatus`/`setCommentModerationStatus`/`setUserAccountStatus`/
  `updateDiseaseReference` are thin, direct Firestore updates — the rules are the real
  enforcement point, same pattern as every other repository here.
- `getRecentNotifications` is the notification-monitoring/logging piece — reads the
  `notifications` collection ordered by `created_at`, admin-only per the rules.
- Added `firebase-functions-ktx`. Compiles cleanly, existing test suite still passes.

## What this drop deliberately does NOT do

Per `claude/03_SOURCE_ALIGNMENT_AND_DECISION_GATES.md`, none of the following are resolved here:

- **D-04 (model preprocessing)** — resolved: the class order comes from the trained model's labels,
  and the training notebook confirms [-1, 1] pixel scaling (see "Disease model" above).
- **D-03 (Farm ownership cardinality)** — `DiagnosisRecordEntity.farmId` is a nullable string FK
  only; `UserFarmRepository.createFarm` supports 1-to-many without deciding it either.
- **D-01 (cloud database choice)** — the project moved from Firestore to Convex because
  Firebase's free plan lacks the image storage and server functions the app needs. The
  repository interfaces stay backend-agnostic, so this can change again behind them.
- **D-10 (GIS/map SDK provider)** — the Map screen is a placeholder with no map dependency.
- **D-02 (Agricultural Technician role)** — no `technician` path exists anywhere in this module.

## Package conventions

Everything lives under `com.cornguard.app` (must match Panes's existing dev Firebase Android
client config exactly — do not rename without updating `firebase/config/` too):

- `ui.<feature>` — one package per screen/flow (`home`, `scan`, `result`, `treatment`, `history`,
  `map`, `community`), plus `ui.common` for reusable views.
- `data.local.db` — Room database, entities (`.entity`), DAOs (`.dao`).
- `data.repository` — repository interfaces, with local implementations under `.local`. A future
  cloud-backed implementation (Panes/Ligue, Sprint 3+) is added the same way, behind the same
  interface — screens must never depend on `LocalDiagnosisHistoryRepository` directly.
- `model` — the ML integration boundary (`CornLeafClassifier`, `DetectionResult`, `DiseaseCode`).
- `permissions` — runtime permission strategy.
- `util` — small cross-cutting helpers (currently just `ConnectivityObserver`).
- `di` — `ServiceLocator`, a manual process-lifetime dependency holder. No DI framework is used in
  Sprint 0; introducing one later is an internal engineering decision, not a contract change.

## Testing in this drop

- `src/test` — pure JVM unit test for the `DetectionResult` contract (confidence range,
  probabilities length).
- `src/androidTest` — instrumented tests: the placeholder classifier's refusal-to-guess behavior,
  Room DAO CRUD round-trips for both entities (per `claude/12_TESTING_AND_ACCEPTANCE_PLAN.md` #2),
  the Room v1→v2 migration, and `ModelNormalizationCalibrationTest`, which runs the real model on
  labelled images placed in `src/androidTest/assets/calibration/<disease_code>/`.
