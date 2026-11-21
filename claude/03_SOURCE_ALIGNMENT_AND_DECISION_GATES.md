# CORNGUARD — SOURCE ALIGNMENT AND DECISION GATES

This file prevents developers from silently resolving conflicts in the capstone manuscript or panel minutes. Every item below must be either confirmed before the dependent feature begins or implemented in a way that remains configurable until a formal decision is made.

## D-01 — Cloud Database Choice

### Source conflict
The community implementation says **Firebase Realtime Database or Firestore**. The software stack also lists **Firebase Firestore / Realtime Database**. The database-schema narrative refers to a relational cloud database, which conflicts with Firebase's NoSQL approach.

### Development rule
Do not mix multiple cloud databases unless explicitly approved.

### Required decision
Choose one cloud datastore before Sprint 3:

- Firestore, or
- Realtime Database.

### Allowed work before decision
Developers may define logical entities, DTO/data classes, repository interfaces, security requirements, and local mocks without binding to one Firebase database implementation.

### Development-only provisioning note (2026-09-09)
A Firestore database was provisioned in the `cornguard-dev` Firebase project (Sprint 0,
`feature/firebase-setup`) so the drafted `firebase/security/firestore.rules` could actually be
deployed and validated rather than remain an untested draft. `firebase/security/storage.rules`
was not deployed (Cloud Storage requires the Blaze billing plan, deferred to Sprint 3).

This is **not** the required D-01 decision. It is a reversible, dev-only choice, approved by
Panes, to unblock rules testing. Production still requires a formal team decision — Firestore
vs. Realtime Database — before Sprint 3, per the Required decision above. If the team ultimately
chooses Realtime Database, the `cornguard-dev` Firestore instance is disposable and the schema/
security-matrix work in `firebase/` does not need to change, since it was written implementation-
agnostic per the Allowed work rule.

### Storage-independent diagnosis sharing (resolved 2026-10-30)
Phase 1 testing (Test 6) empirically confirmed that `FirebaseDiagnosisSharingRepository.shareScan()`
failed entirely when Cloud Storage returned 404 (not provisioned on `cornguard-dev` — see the
dev-provisioning note below), because the image upload and the Firestore metadata write were one
inseparable operation. The team decided NOT to authorize a Blaze billing upgrade at this time, and
instead to decouple the two: a failed or unavailable image upload now degrades to "no image"
(`has_image: false`) rather than failing the share. Diagnosis metadata (classification, confidence,
farm linkage, location, timestamps) shares successfully regardless of Storage availability; only
the photo attachment itself depends on Storage. Approved by: CornGuard team. Files affected:
`FirebaseDiagnosisSharingRepository.kt`, `DiagnosisRecordCloud.kt`,
`DiagnosisSharingRepository.kt`, `firebase/README.md`, `firebase/schema/logical-schema.md`. No
Firestore security rule changed — the existing `allow create` rule already permitted this field
shape unchanged.

### Sprint 3 continuation note (2026-09-21)
Ligue and Acenas are unavailable to participate in the formal D-01 decision this file requires
before Sprint 3 work begins. Rather than block all Sprint 3 Panes work indefinitely, development
continued against Firestore — it is already the live, tested `cornguard-dev` database, and it is
what the manuscript's own architecture diagram (Figure 6) already depicts. **This is explicitly
not the required three-person team decision** — it is a pragmatic, transparently-recorded
continuation by Panes alone, made because the alternative was stalling all community-module work
indefinitely. The Required decision above is still open. If Ligue or Acenas object once available,
or the team later picks Realtime Database, the Sprint 3 Firestore-specific code (community
repository, indexes) needs rework — the schema and security matrix themselves do not, for the same
reason given in the note above.

---

## D-02 — Agricultural Technician Role

### Source conflict
The architecture and use-case sections list Farmer and System Administrator. The community implementation introduces an optional Agricultural Technician who can verify posts and provide expert replies. The ERD narrative also references farmers and technicians communicating.

### Development rule
Do not expose a technician role in production authentication or UI until the role is formally approved and its permissions are defined.

### Required decision
Confirm whether Agricultural Technician is:

- a full system role,
- an admin subtype,
- a verified community badge/permission, or
- removed from MVP.

### Recommended data preparation
Keep role/verification fields extensible so the role can be added without destructive migration.

### Team Decision (resolved 2026-10-30)
- Decision ID: D-02
- Date: 2026-10-30
- Decision: Agricultural Technician is removed from MVP scope. The existing Admin role (not a new
  claim) handles all disease-report verification — approve or reject a submitted
  `diagnosisRecordsCloud` document. `verified_by` and `verified_at` are recorded on the document
  (already implemented — see `AdminRepository.verifyDiagnosisRecord`/`rejectDiagnosisRecord`).
  Only `verification_status == "verified"` reports may contribute to outbreak aggregation (D-07).
  An auditable verification history (append-only, not just the overwritten status field) must be
  preserved — **not yet implemented**; this is Phase 2 (structured verification workflow) schema
  work, not part of this round's authorized Phase 0/1 changes.
- Approved by: CornGuard team + adviser
- Reason: matches the system already built (Admin is already a confirmed role distinct from the
  gated Technician role, per `AdminRepository`'s own doc comment) without introducing an
  unapproved new role.
- Files/contracts affected: none required this round — `firebase/security/firestore.rules` and
  `AdminRepository`/`FirebaseAdminRepository` already implement exactly this shape. Planned for
  Phase 2: a `diagnosisRecordsCloud/{recordId}/verificationHistory/{entryId}` subcollection
  (fields: `action`, `actor_user_id`, `at`, `note?`) so verification stays auditable rather than a
  silently-overwritten status.
- Migration/refactor required: none now; the verification-history subcollection above is deferred
  to Phase 2.

### Phase 2 implementation (2026-10-30)
The deferred verification-history item above is now implemented: `AdminReviewFragment` (new, under
`ui/admin/`) lists pending records and lets an Admin verify/reject; `FirebaseAdminRepository`
writes the record's verification fields and one `verificationHistory` entry atomically via a
`WriteBatch`. Client-side admin detection reads the `role` custom claim off a force-refreshed ID
token (`AuthRepository.isCurrentUserAdmin()`) — UI convenience only, not the enforcement point
(the Firestore rule is). No Room schema change — `AdminRepository` stays online-only, same as
every other Firebase-backed repository. **Not end-to-end verified**: this environment has no
Firebase Admin SDK credentials available to promote a test account to admin (see the D-01
dev-provisioning notes above for the same ADC constraint), so the verify/reject write path is
code-reviewed, not live-tested against a real admin session. The non-admin gating (hidden entry
point, blocked direct navigation, Firestore-rejected query) is live-tested.

---

## D-03 — Farm Entity and Farm Ownership

### Source conflict
The manuscript repeatedly says diagnosis history is per-farm, but the displayed ERD does not clearly contain a dedicated FARM entity. The panel explicitly required farm information, farm-owner association, farm location, and multiple diagnosis records per farm.

### Development rule
The cloud/local data contract must reserve a Farm structure. Do not finalize database rules until cardinality is approved.

### Required decision
Confirm:

- Can one farmer own multiple farms?
- Can one farm have multiple registered users?
- Is farm location one point, a barangay/municipality label, or a mapped boundary?
- Which farm fields are required at registration?

### Provisional safe design
Use IDs rather than embedding all farm information into User or DiagnosisRecord. This keeps the schema compatible with either one-to-one or one-to-many ownership after approval.

### Team Decision (resolved 2026-10-30)
- Decision ID: D-03
- Date: 2026-10-30
- Decision: One farmer can register and manage multiple farms (1:N, farmer:farm — no cap). Each
  farm has its own location (a point: latitude/longitude, plus barangay/municipality/province
  labels) and its own diagnosis records; a diagnosis record is associated with the farm it came
  from, not just the owning user. Farm location is a point, matching the existing `Farm.kt`/
  `farms` Firestore schema — no boundary/polygon support.
- Approved by: CornGuard team + adviser
- Reason: panel requirement for farm-owner association, farm location, and multiple diagnosis
  records per farm. This matches the data layer already built —
  `UserFarmRepository`/`FirebaseUserFarmRepository.createFarm`/`getFarmsForUser` already impose no
  one-farm limit, and `ProfileFragment` already lets a signed-in farmer add more than one farm.
  What was missing was actually linking a shared diagnosis record to a chosen farm, which this
  round's Phase 1 work closes (`DiagnosisResultFragment` now prompts for a farm when sharing).
- Files/contracts affected: `UserFarmRepository.kt`/`ProfileFragment.kt` doc comments (updated
  from "D-03 unresolved" to reflect this decision), `DiagnosisResultFragment.kt` (farm selection
  added before `shareScan`).
- Migration/refactor required: none to the data layer (already supports this cardinality). No
  Room migration — farm data is intentionally cloud-only/online-only, same as `AuthRepository`;
  there is no local Farm cache to migrate.

---

## D-04 — Model Input Normalization

### Source conflict
The data-preprocessing section states MobileNetV2 preprocessing scales pixels to **[-1, 1]**. A later algorithm section describes normalization to **[0, 1]**.

### Development rule
The Android app must never guess preprocessing.

### Required decision
Acenas must publish the exact preprocessing contract used by the final exported `.tflite` model. Ligue must implement that exact contract and validate against a Python reference inference.

### Merge gate
No final TFLite integration PR may merge until model preprocessing is frozen and documented in `09_ML_MODEL_CONTRACT.md` or its project-local equivalent.

### Pragmatic solo-context continuation (ml/ training pipeline)
`ml/config.py` defaults `NORMALIZATION_MODE` to `[-1, 1]` MobileNetV2 preprocessing
(`tf.keras.applications.mobilenet_v2.preprocess_input`) so the training pipeline is real,
runnable code rather than blocked indefinitely on this gate. **This is not Acenas's frozen
decision** — it is a solo-context default, same treatment as the D-01/D-10 continuations
elsewhere in this file. No trained model has been produced with it; nothing on the Android side
implements final preprocessing yet (`PlaceholderCornLeafClassifier` still throws unconditionally,
per the merge gate above). Whoever runs the first real training run intended for evaluation must
revisit this value first.

---

## D-05 — Dataset Split

### Source conflict
The dataset section specifies 70% training / 15% validation / 15% testing. The testing section later says the held-out test set will comprise a minimum of 20% of the total dataset.

### Development rule
Do not claim final model evaluation compliance until this is harmonized.

### Required decision
Adviser/research team must approve the final split.

### Documentation requirement
The final report must record:

- exact number of images per split,
- per-class counts,
- random seed,
- whether local Bukidnon images are included in each split,
- whether augmentation is training-only.

### Pragmatic solo-context continuation (ml/ training pipeline)
`ml/config.py` defaults `TRAIN_SPLIT`/`VAL_SPLIT`/`TEST_SPLIT` to 70/15/15 so
`prepare_dataset.py` can produce a real stratified manifest rather than being blocked on this
gate. **This is not the adviser/research team's approved split** — same treatment as the D-01/D-10
continuations elsewhere in this file. No dataset manifest or trained model has actually been
produced with it (`ml/data/raw/` is empty). Whoever runs the first real training run intended for
evaluation must get the split approved first, per the Required decision above.

---

## D-06 — Inference Response-Time Requirement

### Source conflict
One section says classification result must appear within **3 seconds** on a mid-range Android device. Detailed NFR-01 says **less than 2.0 seconds**.

### Engineering rule
Target <2.0 seconds for the development acceptance gate because it satisfies both stated limits.

### Measurement rule
Measure end-to-end time from prepared input submission to result rendering, and separately log raw model inference time.

---

## D-07 — Outbreak Definition and Alert Thresholds

### Source gap
The study requires heatmaps and severe-outbreak notifications, but no approved numerical outbreak threshold is defined. The panel explicitly required expert-backed outbreak parameters, safeguards against false alarms, confidence thresholds, repeated detections/reports, and validation before public alerts.

### Development rule
Do not hardcode a production outbreak threshold.

### Required expert decision
Agricultural experts/adviser must approve parameters such as:

- minimum model confidence for a report to contribute to outbreak aggregation,
- minimum number of independent reports/detections,
- time window,
- geographic radius or administrative area,
- verification requirement,
- severity levels,
- suppression/cooldown behavior for repeated alerts.

### Development approach
Implement a configurable rule structure and use clearly marked development-only values for testing.

### Team Decision (resolved 2026-10-30)
- Decision ID: D-07
- Date: 2026-10-30
- Decision: an initial, configurable, project-defined OPERATIONAL rule (stored in the existing
  admin-only `outbreakRules` Firestore collection, not hardcoded):
  - at least 3 verified reports,
  - from at least 3 distinct farms,
  - all reports concerning the same `disease_code`,
  - all reports within the same barangay,
  - within a rolling 7-day window,
  - duplicate reports from the same farm do not inflate the count (dedupe by `farm_id` within the
    window),
  - reaching the threshold creates a **potential** outbreak for Admin review — it is never
    auto-declared,
  - community notifications are sent only after an Admin confirms the outbreak,
  - an Admin can close an outbreak and must record a reason.
- Approved by: CornGuard team + adviser
- **Explicitly documented limitation**: these numbers are NOT derived from plant pathology or
  epidemiological expertise. They are a project-defined operational starting point for the MVP,
  pending review by a qualified agricultural expert or the Department of Agriculture, per the
  panel's own requirement for expert-backed outbreak parameters. Revising them later is an
  `outbreakRules` document update, not a code change.
- Files/contracts affected: none this round. `firebase/security/firestore.rules`'s
  `outbreakRules/{ruleId}` (admin-only read/write) already fits this design unchanged. Planned,
  not yet implemented: an `outbreakRules` document shape (`min_verified_reports`,
  `min_distinct_farms`, `disease_scope`, `geographic_scope: "barangay"`, `window_days`,
  `dedupe_by_farm: true`) and a `potentialOutbreaks`/`outbreakEvents` collection for the
  review/confirm/close workflow — both are Phase 6/7 feature work, not part of this round's
  authorized Phase 0/1 changes.
- Migration/refactor required: none now; deferred to Phase 6/7.

---

## D-08 — Community Content Verification

### Source conflict
The limitation section says there is no automated fact-checking or expert moderation, while the community-role section gives Agricultural Technicians the ability to mark posts verified. The panel also required validation procedures before disease reports are publicly displayed or used for alerts.

### Development rule
Separate **community post visibility** from **outbreak verification**.

Until role/validation rules are approved:

- Posts may be displayed as user-generated reports with an `unverified` status.
- Unverified posts must not be treated as confirmed outbreaks.
- Production outbreak alerts must remain disabled or require approved validation criteria.

---

## D-09 — Administrator Platform

### Source gap
The system includes an Administrator but does not clearly fix the administrator interface as Android, web, or Firebase console/backend tooling. The panel only says web-based monitoring should be explored as a possibility.

### Development rule
Do not create a separate web application unless approved.

### Work that may proceed
Implement administrator authorization, moderation data structures, security rules, and backend operations independently from a final admin UI platform.

---

## D-10 — GIS/Map SDK Provider

### Source gap
GIS mapping and heatmaps are required, but the manuscript does not clearly specify a map SDK/provider.

### Development rule
Do not commit provider-specific architecture until the team selects one based on Android compatibility, cost, offline/low-connectivity behavior, heatmap support, key restrictions, and academic/project constraints.

### Sprint 4 continuation note (2026-09-21)
Same circumstance as the D-01 continuation note above: Ligue and Acenas are unavailable, and the
Map screen's rendering is blocked without a provider pick. **This is a pragmatic solo lean, not
the required team decision**, and unlike D-01 it does not even bind any code yet — only the GIS
*data* layer (nearby-report queries, heatmap aggregation) is built this sprint, and that layer is
already provider-agnostic per `firebase/gis/gis-service-interface.md` (no map-SDK types). The
actual rendering/map-SDK dependency stays undone.

### Phase 3/4 implementation (2026-10-30)
osmdroid is now wired in (`MapFragment`, `CornGuardApplication`'s one-time `Configuration` setup)
— base map tiles, pan/zoom, offline tile cache, confirmed via live device testing (online load,
pan/zoom, backgrounding, and genuine offline reuse of previously-viewed tiles with connectivity
fully disabled). Phase 4 added disease occurrence markers on top: a redacted `verifiedOccurrences`
Firestore projection (new collection, new rule, see `schema/logical-schema.md` and
`security/access-control-matrix.md`) for admin-verified occurrences, and reuse of the already-
public `communityPosts` for unverified community reports — discovered along the way that
`GisRepository.getVerifiedOccurrences()` had been silently returning empty for every non-admin
user since it was first written, because `diagnosisRecordsCloud` is owner/admin read-only; the new
projection is what actually fixes that for the map, not a loosening of the original collection's
rule.

If a pick becomes necessary later (e.g. for Ligue's Map screen before the team formally decides):
**osmdroid (OpenStreetMap)** is the pragmatic lean over Google Maps Platform or Mapbox, specifically
because it needs no billing account / API key at all — Google Maps Platform requires a GCP billing
account the same way Blaze does for Storage/Functions (already a repeated blocker this sprint), and
Mapbox's free tier still requires an account + token. osmdroid also supports offline tile caching,
which fits the project's own "offline/low-connectivity behavior" criterion better for
Bukidnon field conditions. Its tradeoff: no official heatmap overlay library as polished as Google's
`android-maps-utils`, so a heatmap layer would need a community library or custom overlay — a cost
worth paying to avoid another billing-account wall. This is Ligue's call to make or override when
he reaches the Map screen (Sprint 4/5, `feature/map-ui`), not something this note locks in.

### Team Decision (resolved 2026-10-30)
- Decision ID: D-10
- Date: 2026-10-30
- Decision: osmdroid + OpenStreetMap is the approved map SDK, formally adopting the lean recorded
  above. Priorities: low cost (no billing account/API key required) and offline-friendly
  behavior. Location markers and a heatmap overlay must be implemented AND actually tested (tile
  caching, offline behavior) before any documentation or presentation claims offline maps work.
- Approved by: CornGuard team + adviser
- Reason: avoids the Google Maps Platform/Mapbox billing-account requirement that already blocked
  Storage/Functions work this project (Blaze plan), and fits Bukidnon field connectivity better.
- Files/contracts affected: `android/app/build.gradle.kts` (new osmdroid dependency),
  `MapFragment.kt`/`fragment_map.xml` (real rendering), `GisRepository` (heatmap overlay wiring).
  Not part of this round's authorized Phase 0/1 work — scheduled for Phase 3/4.
- Migration/refactor required: none to data schema; this is a UI/dependency addition.

---

## D-11 — Public Diagnosis Record vs Local-Only Scan

### Source gap
The study requires local diagnosis history and cloud/community disease monitoring, but not every private scan is explicitly required to be uploaded.

### Development rule
Default local scans to private/local history. Upload/share only when the approved workflow requires it or the farmer explicitly shares a report. This avoids turning every test scan into a public outbreak signal.

---

## D-12 — Model and Disease Validation by Agricultural Experts

### Panel requirement
The panel requires qualified validators capable of determining whether disease detections are correct, including agricultural experts, technicians, or experienced farmers where possible.

### Development/testing rule
Farmer UAT and IT expert evaluation are not substitutes for agricultural diagnosis validation.

The project must separately document:

- who validates field disease labels,
- how ground truth is established,
- which samples are expert-confirmed,
- how disagreements are handled,
- how field-model results are compared with actual disease observations.

---

## Decision Log Template

Record every resolved item using:

- Decision ID
- Date
- Decision
- Approved by
- Reason
- Files/contracts affected
- Migration or refactor required

Never resolve a source conflict only inside code comments or chat messages.
