# CORNGUARD — TEAM RESPONSIBILITIES & FEATURE BRANCHES

Feature branches are created only when work begins. The branch names below are examples tied to approved work; do not pre-create all branches.

## Ligue — Android Mobile & Integration Lead

### Responsibilities

- Android app structure
- UI screens
- Navigation
- Camera/gallery
- Local SQLite
- Local treatment/reference data
- TFLite integration
- Scan history
- Offline behavior
- Mobile-side Firebase/GIS integration
- Release builds

### Example Branches

- `feature/android-foundation`
- `feature/auth-ui`
- `feature/home-navigation`
- `feature/camera-gallery`
- `feature/tflite-integration`
- `feature/diagnosis-result-ui`
- `feature/treatment-ui`
- `feature/local-history`
- `feature/community-ui`
- `feature/map-ui`
- `feature/notification-ui`
- `feature/mobile-polish`
- `feature/release-build`

## Panes — Firebase, Community & GIS Services Lead

### Responsibilities

- Firebase configuration
- Authentication
- Cloud data model
- Community backend
- Storage
- Location queries
- GIS service
- Heatmap aggregation
- FCM notifications
- Moderation/admin operations
- Security rules

### Example Branches

- `feature/firebase-setup`
- `feature/auth-service`
- `feature/user-farm-data`
- `feature/community-data`
- `feature/community-comments`
- `feature/community-storage`
- `feature/location-services`
- `feature/gis-data`
- `feature/heatmap-aggregation`
- `feature/fcm-notifications`
- `feature/moderation`
- `feature/firebase-security`

## Acenas — AI/ML, Dataset & Model Validation Lead

### Responsibilities

- Dataset preparation
- Model training
- Model evaluation
- TFLite export
- Model contract
- Performance benchmarking
- Field validation

### Example Branches

- `feature/dataset-pipeline`
- `feature/model-baseline`
- `feature/model-augmentation`
- `feature/class-weighting`
- `feature/model-evaluation`
- `feature/tflite-export`
- `feature/model-metadata`
- `feature/device-benchmark`
- `feature/field-validation`

## Cross-Module Branches

Cross-module work should still have one owner.

Examples:

- `feature/scan-to-community-integration` — owner: Ligue, reviewed by Panes and Acenas
- `feature/scan-location-pipeline` — owner: Panes or Ligue depending implementation boundary
- `feature/model-app-validation` — owner: Acenas, reviewed by Ligue
- `feature/end-to-end-testing` — owner may rotate, all review
- `feature/bug-fixes-<area>` — owner is the module owner

## Suggested Sprint Branch Flow

### Sprint 0

- Ligue: `feature/android-foundation`
- Panes: `feature/firebase-setup`
- Acenas: `feature/dataset-pipeline`

All -> `develop`

### Sprint 1

- Ligue: `feature/home-navigation`
- Panes: `feature/auth-service`
- Acenas: `feature/model-baseline`

All -> `develop`

### Sprint 2

- Ligue: `feature/tflite-integration`
- Panes: `feature/diagnosis-cloud-contract`
- Acenas: `feature/tflite-export`

All -> `develop`

### Sprint 3

- Ligue: `feature/community-ui`
- Panes: `feature/community-data`
- Acenas: `feature/model-metadata`

All -> `develop`

### Sprint 4

- Ligue: `feature/map-ui`
- Panes: `feature/gis-notifications`
- Acenas: `feature/field-validation-prep`

All -> `develop`

### Sprint 5

- Ligue: `feature/mobile-polish`
- Panes: `feature/firebase-security`
- Acenas: `feature/release-model`

All -> `develop`

### Sprint 6

- Ligue: `feature/mobile-system-testing`
- Panes: `feature/cloud-system-testing`
- Acenas: `feature/model-field-testing`

All -> `develop`

### Sprint 7

- Ligue: `feature/release-build`
- Panes: `feature/production-config`
- Acenas: `feature/final-model-package`

All -> `develop` -> `main` after full release review.

## Branch Lifecycle

Create -> Develop -> Test -> Commit -> Push -> Pull Request -> Peer Review -> Merge to `develop` -> Delete feature branch.

## Review Ownership

A PR that changes a shared contract requires review from the affected owner:

- Model input/output -> Acenas + Ligue
- Cloud schema/security -> Panes + consuming developer
- Android navigation/shared UI -> Ligue
- GIS/report metadata -> Panes + Ligue
- Release/integration -> all three

No developer should approve their own PR as the only reviewer.
