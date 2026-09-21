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
