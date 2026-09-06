# CORNGUARD — DEVELOPMENT PHASE GUIDE

This is the developer-oriented equivalent of the Engage Development Phase document, adapted to CORNGUARD's nine-month Agile timeline and three-developer team.

## Study Timeline Context

The manuscript schedules:

- June–July 2026: requirements, interviews, feasibility, SRS
- August–October 2026: dataset collection and system design outputs
- September–November 2026: model training and evaluation
- October–December 2026: Android application development
- November–December 2026: Firebase/community module development
- December 2026–January 2027: system testing and UAT
- January–February 2027: documentation and final completion

The implementation sprints below fit inside that approved sequence.

---

# Phase 0 — Development Readiness

## Goal

Prepare a reproducible development environment and freeze the contracts that allow the three developers to work in parallel.

## Ligue

- Android Studio/Kotlin project
- navigation shell
- reusable UI components
- SQLite base
- camera/location permission plan
- TFLite service interface

## Panes

- Firebase dev project
- auth setup
- cloud repository abstraction
- Storage/FCM setup plan
- security-rule skeleton
- GIS service abstraction

## Acenas

- dataset manifest
- Colab notebooks
- MobileNetV2 baseline
- class/label definitions
- model metadata template

## Output

- Development environment works for all developers.
- Git workflow active.
- Decision gates documented.
- Shared contracts reviewed.

---

# Phase 1 — Core Android and Model Foundation

## Goal

Build the minimum application shell and a reproducible baseline model.

## Ligue

- Home and navigation
- auth/profile UI
- scan screen shell
- history repository foundation

## Panes

- Firebase Auth
- user/farm profile data services
- basic security rules

## Acenas

- clean dataset
- preprocessing
- augmentation
- class weights
- baseline model training

## Output

- Users can enter the app according to approved flow.
- App navigation is ready.
- Baseline model metrics exist.

---

# Phase 2 — Offline AI Diagnostic MVP

## Goal

Deliver CORNGUARD's most important function: offline corn disease detection.

## Ligue

- camera/gallery
- exact model preprocessing
- TFLite inference
- diagnosis result
- treatment guidance
- local scan history

## Panes

- cloud diagnosis/report contract
- location metadata contract
- upload service preparation

## Acenas

- model refinement
- metrics
- TFLite export
- class order
- model metadata
- parity reference cases

## Output

A farmer can scan a corn leaf and receive a result/treatment guidance without internet.

---

# Phase 3 — Community Reporting

## Goal

Add the online peer-to-peer disease reporting layer without weakening the offline core.

## Ligue

- community feed UI
- post/report UI
- scan-to-report draft
- reply UI
- loading/error/offline states

## Panes

- cloud database implementation
- posts
- comments
- image storage
- upvotes if retained
- location-aware feed
- moderation fields
- security rules

## Acenas

- confidence interpretation support
- test disease samples
- model limitation messaging

## Output

Farmers can report and discuss disease occurrences online.

---

# Phase 4 — GIS, Heatmap, and Notifications

## Goal

Turn reported/validated disease data into location-aware monitoring features.

## Ligue

- map screen
- heatmap layer UI
- nearby reports panel
- notification display

## Panes

- GIS provider integration
- coordinate/location storage
- nearby queries
- heatmap aggregation
- FCM
- outbreak-rule configuration structure

## Acenas

- field/model metadata support
- threshold consultation support
- validation samples

## Output

Location-based disease monitoring works with approved/test data.

---

# Phase 5 — Administration, Moderation, and Hardening

## Goal

Prepare an integrated system suitable for controlled field evaluation.

## Ligue

- UI polish
- accessibility
- device compatibility
- integration fixes
- approved admin UI only if platform is confirmed

## Panes

- admin authorization
- moderation operations
- reference-data maintenance
- security rule hardening
- cloud consistency checks

## Acenas

- release-candidate model
- device benchmark
- model card
- field failure analysis

## Output

Integrated release candidate.

---

# Phase 6 — Testing and Validation

## Goal

Demonstrate that the system works technically and practically in Bukidnon field conditions.

## Required testing

- model test-set evaluation
- TFLite evaluation
- Android unit/module testing
- integration testing
- offline testing
- Firebase/security testing
- GIS testing
- notification testing
- field testing
- farmer UAT
- agricultural expert validation
- IT expert review

## Output

- validated release candidate
- bug/fix evidence
- UAT/evaluation results

---

# Phase 7 — Final Release and Documentation

## Goal

Produce the stable capstone build, reproducible technical package, and final defense assets.

## Ligue

- release APK/AAB
- build instructions
- demo device setup

## Panes

- production Firebase configuration
- security rule freeze
- monitoring/admin demo setup

## Acenas

- final TFLite model
- training/evaluation archive
- model metrics and figures

## All

- final regression test
- `develop -> main`
- release tag
- final documentation
- known limitations
- defense/demo preparation

---

# Definition of Done for Any Feature

A feature is done only when:

1. Requirement is traceable to the study/approved revision.
2. Contract is documented.
3. Code is on a feature branch.
4. Error states are handled.
5. Relevant offline/online behavior is tested.
6. Security/privacy impact is reviewed.
7. Peer review is complete.
8. Feature is integrated into `develop`.
9. Documentation is updated.
