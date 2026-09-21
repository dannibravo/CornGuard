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
  (`data.repository`) so the offline scan/history/treatment path never depends on Firebase.
- Camera/gallery/location/notification permission **strategy** (`permissions.AppPermission`,
  `permissions.PermissionManager`) — state checks only; per-screen request UX/rationale flows are
  built with the feature that needs them, starting Sprint 1/2.
- A placeholder model-service interface (`model.CornLeafClassifier`,
  `model.PlaceholderCornLeafClassifier`) that intentionally throws `ModelNotReadyException`
  rather than fabricate a disease label, until Acenas freezes the preprocessing contract and class
  order (D-04). Real TFLite integration is Sprint 2 (`feature/tflite-integration`).
- `debug`/`release` build types (`app/build.gradle.kts`). No signing config is wired — a release
  keystore must never be committed (`claude/10_ENV_GUIDE.md`); it is injected from a controlled
  location when Sprint 7 actually produces a release build.

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

## What this drop deliberately does NOT do

Per `claude/03_SOURCE_ALIGNMENT_AND_DECISION_GATES.md`, none of the following are resolved here:

- **D-04 (model preprocessing / class order)** — no preprocessing code exists yet;
  `PlaceholderCornLeafClassifier` refuses to run rather than guess.
- **D-03 (Farm ownership cardinality)** — `DiagnosisRecordEntity.farmId` is a nullable string FK
  only; `UserFarmRepository.createFarm` supports 1-to-many without deciding it either.
- **D-01 (cloud database choice)** — Firestore is one illustrative binding (matching the dev
  project Panes provisioned), not a resolved team decision; see the dev-provisioning note under
  D-01 in `claude/03_SOURCE_ALIGNMENT_AND_DECISION_GATES.md`.
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
  and Room DAO CRUD round-trips for both entities, per
  `claude/12_TESTING_AND_ACCEPTANCE_PLAN.md` #2.

Full offline-with-airplane-mode testing and Android/TFLite parity testing apply once Sprint 2
lands real scan behavior; there is no scan flow to exercise yet in this drop.
