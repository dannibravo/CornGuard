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
