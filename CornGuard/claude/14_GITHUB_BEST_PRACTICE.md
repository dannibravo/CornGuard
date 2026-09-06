# CORNGUARD — GITHUB BEST PRACTICE BY DEVELOPMENT PHASE

## Permanent Branches

### `main`

Contains:

- stable capstone build
- adviser/demo-ready release
- tagged release versions

### `develop`

Contains:

- active integrated development
- completed and reviewed feature work
- release candidates before promotion to `main`

All developers branch from `develop`.

---

# Phase 0

## Ligue

Create:

```bash
git checkout develop
git pull origin develop
git checkout -b feature/android-foundation
```

Tasks:

- Android project structure
- base navigation
- SQLite foundation
- shared UI components

Merge:

`feature/android-foundation -> develop`

## Panes

Create:

```bash
git checkout develop
git pull origin develop
git checkout -b feature/firebase-setup
```

Tasks:

- Firebase dev config
- auth groundwork
- security-rule skeleton
- cloud repository interfaces

Merge:

`feature/firebase-setup -> develop`

## Acenas

Create:

```bash
git checkout develop
git pull origin develop
git checkout -b feature/dataset-pipeline
```

Tasks:

- dataset manifest
- preprocessing notebook
- reproducible training structure

Merge:

`feature/dataset-pipeline -> develop`

---

# Phase 1

## Ligue

Branch:

`feature/home-navigation`

Tasks:

- Home
- navigation
- profile/auth UI
- scan entry flow

## Panes

Branch:

`feature/auth-service`

Tasks:

- Firebase Auth
- profile storage
- user/farm security

## Acenas

Branch:

`feature/model-baseline`

Tasks:

- MobileNetV2 baseline
- augmentation
- class weighting
- training metrics

All -> `develop`

---

# Phase 2

## Ligue

Branch:

`feature/tflite-integration`

Tasks:

- camera/gallery
- preprocessing
- TFLite wrapper
- diagnosis result
- treatment
- history

## Panes

Branch:

`feature/diagnosis-cloud-contract`

Tasks:

- report/share data contract
- location contract
- upload service foundation

## Acenas

Branch:

`feature/tflite-export`

Tasks:

- final input/output metadata
- class order
- TFLite model
- parity reference outputs

All -> `develop`

---

# Phase 3

## Ligue

Branch:

`feature/community-ui`

Tasks:

- community feed
- create report
- comments/replies UI
- scan-to-report draft

## Panes

Branch:

`feature/community-data`

Tasks:

- cloud post storage
- comments
- Storage images
- location filtering
- moderation fields
- rules/indexes

## Acenas

Branch:

`feature/model-metadata`

Tasks:

- confidence interpretation
- limitations
- known-sample testing

All -> `develop`

---

# Phase 4

## Ligue

Branch:

`feature/map-ui`

Tasks:

- GIS screen
- heatmap UI
- nearby reports
- notification UI

## Panes

Branch:

`feature/gis-notifications`

Tasks:

- map/GIS data service
- aggregation
- FCM
- configurable alert logic

## Acenas

Branch:

`feature/field-validation-prep`

Tasks:

- field sample protocol
- model validation fixtures
- threshold consultation support

All -> `develop`

---

# Phase 5

## Ligue

Branch:

`feature/mobile-polish`

## Panes

Branch:

`feature/firebase-security`

## Acenas

Branch:

`feature/release-model`

All -> `develop`

---

# Phase 6

Testing branches should be scoped by defect or test area, not by one permanent testing branch.

Examples:

- `test/offline-scan`
- `test/firebase-rules`
- `test/gis-location`
- `fix/model-label-mapping`
- `fix/community-upload-retry`

All fixes -> PR -> `develop`.

---

# Phase 7 / Release

When final integration is approved:

```bash
git checkout develop
git pull origin develop
# final tests

git checkout main
git pull origin main
git merge develop
git push origin main
```

Then tag release.

Do not merge to `main` because a calendar date has arrived. Merge only when the release checklist passes.

---

# Commit Prefix Guide

Use clear prefixes:

- `feat:` new functionality
- `fix:` bug fix
- `test:` tests
- `docs:` documentation
- `refactor:` code restructuring without intended behavior change
- `build:` Gradle/build/configuration changes
- `chore:` maintenance

Examples:

- `feat: add offline diagnosis history`
- `fix: correct tflite class label mapping`
- `test: add firebase unauthorized write cases`
- `docs: freeze model preprocessing contract`

---

# Files That Require Extra Care

## Model files

Do not casually replace the `.tflite` file without updating metadata and tests.

## Firebase rules

Every change requires security review.

## Data schema/contracts

Field renames require coordination with all consumers.

## Location/privacy logic

Any change to public coordinate visibility requires explicit review.

## Outbreak rules

No production values without documented expert approval.
