# CORNGUARD — Cloud Logical Schema (Draft, Sprint 0)

Elaborates the logical entities in `claude/08_DATA_AND_INTEGRATION_CONTRACT.md` into concrete
field types and illustrative Firestore collection paths. This is a **draft pending D-01**
(Firestore vs. Realtime Database). Field names and types are written so they translate directly
to a Realtime Database node structure if the team chooses that instead.

Conventions (from the Data and Integration Contract):

- IDs are strings (Firestore document IDs, or generated UUIDs if targeting RTDB).
- Timestamps are stored as UTC (Firestore `Timestamp`, or ISO-8601 string / epoch millis for RTDB).
- Latitude ∈ [-90, 90], Longitude ∈ [-180, 180].
- Confidence is stored as a float in **0.0–1.0** (never mixed with a 0–100 display form).
- `role` is never client-writable — see `access-control-matrix.md`.

---

## `users/{userId}`

| Field | Type | Notes |
|---|---|---|
| `display_name` | string | |
| `email` | string, optional | present only if email/password auth used |
| `mobile_number` | string, optional | present only if phone auth used |
| `role` | string enum: `farmer` \| `admin` | `technician` reserved, disabled until D-02 |
| `barangay` | string | |
| `municipality` | string | |
| `province` | string | default context is Bukidnon; not hardcoded |
| `farm_ids` | array\<string\> | supports 1-to-many pending D-03; empty array if none yet |
| `account_status` | string enum: `active` \| `suspended` | |
| `created_at` | timestamp | |
| `updated_at` | timestamp | |

## `farms/{farmId}`

Provisional per D-03 — do not treat cardinality as final.

| Field | Type | Notes |
|---|---|---|
| `owner_user_id` | string (ref → users) | |
| `farm_name_or_label` | string | |
| `barangay` | string | |
| `municipality` | string | |
| `province` | string | |
| `latitude` | float, optional | exact point; sensitive, see access matrix |
| `longitude` | float, optional | |
| `created_at` | timestamp | |
| `updated_at` | timestamp | |

## `diagnosisRecordsCloud/{recordId}`

Created only for scans the farmer explicitly shares (default is local-only, per D-11).

| Field | Type | Notes |
|---|---|---|
| `user_id` | string (ref → users) | |
| `farm_id` | string (ref → farms), optional | D-03 resolved 2026-10-30: one farmer may have multiple farms; a shared record should link to the farm it came from, but remains optional here for farmers with no farm on file |
| `disease_code` | string enum: `common_rust` \| `gray_leaf_spot` \| `northern_leaf_blight` \| `healthy` | |
| `confidence` | float [0.0–1.0] | |
| `image_url` | string, optional | Cloud Storage download URL. Absent whenever Storage is unavailable/not provisioned (cornguard-dev currently has no Storage — Blaze plan deferred) or the upload failed, not only when no photo was taken — do not infer image presence from this field alone |
| `has_image` | bool | resolved 2026-10-30: authoritative signal of whether an image actually attached. Always set on new writes; on a document written before this field existed, treat as `image_url != null` |
| `captured_at` | timestamp | |
| `latitude` / `longitude` | float, optional | |
| `barangay` / `municipality` / `province` | string | |
| `model_version` | string | mandatory per Model Versioning rule |
| `verification_status` | string enum: `unverified` \| `verified` \| `rejected` | default `unverified` |
| `verified_by` | string, optional | ref → users (admin only — D-02 resolved 2026-10-30: no technician role in MVP) |
| `verified_at` | timestamp, optional | |
| `source` | string enum: `ai_scan` \| `manual` | |

## `diagnosisRecordsCloud/{recordId}/verificationHistory/{entryId}`

Resolved 2026-10-30 (Phase 2, D-02's deferred audit-trail item). Append-only — written exactly
once per `verifyDiagnosisRecord`/`rejectDiagnosisRecord` call, in the same `WriteBatch` as the
parent record's `verification_status`/`verified_by`/`verified_at` update, so the two can never
diverge. Never updated or deleted by anyone, including admins, once written. Absent entirely for
any record that predates this phase or has never been verified/rejected — an empty history is
expected, not an error.

| Field | Type | Notes |
|---|---|---|
| `action` | string enum: `verified` \| `rejected` | matches the parent record's new `verification_status` at the moment this entry was written |
| `actor_user_id` | string (ref → users) | the admin who took the action |
| `at` | timestamp | |
| `note` | string, optional | free-text reason (e.g. why rejected); never required, never shown to the reported farmer in this phase |

## `verifiedOccurrences/{recordId}`

Resolved 2026-10-30 (Phase 4). Redacted, public-safe projection of an admin-verified
`diagnosisRecordsCloud` document — same doc ID as its source record. Written/updated only by the
admin verify action, deleted by the admin reject action or (for their own record) the record
owner, all atomically with the source record's own update — see
`repositories/admin-repository-interface.md` and `diagnosis-sharing-repository-interface.md`.
Exists only for records that both (a) were verified and (b) had coordinates — a verified record
with no location produces no projection and no map marker.

| Field | Type | Notes |
|---|---|---|
| `disease_code` | string enum: 4 model classes | |
| `latitude` / `longitude` | float | rounded to 3 decimal places (~110m) from the source record's exact coordinates — never the exact value |
| `barangay` / `municipality` / `province` | string | |
| `captured_at` | timestamp | |
| `has_image` | bool | |
| `confidence` | float [0.0–1.0] | |
| `verified_at` | timestamp | |

Deliberately absent: `user_id`, `farm_id`, `verified_by`, `image_url` (even if the source record
has one), exact coordinates. Nothing in this collection identifies who made the report.

## `communityPosts/{postId}`

| Field | Type | Notes |
|---|---|---|
| `user_id` | string (ref → users) | |
| `linked_diagnosis_record_id` | string, optional | ref → diagnosisRecordsCloud |
| `title` | string | |
| `body` | string | |
| `disease_tag` | string enum: 4 disease classes \| `unknown` | |
| `image_url` | string, optional | |
| `latitude` / `longitude` | float, optional | display precision per access matrix |
| `barangay` / `municipality` / `province` | string | |
| `verification_status` | string enum: `unverified` \| `verified` \| `rejected` | |
| `moderation_status` | string enum: `visible` \| `hidden` \| `removed` | |
| `upvote_count` | int | server-computed only, never trust client value |
| `created_at` / `updated_at` | timestamp | |

## `communityPosts/{postId}/comments/{commentId}`

Single-level nesting only (per Data Contract: "unlimited nesting" is not required).

| Field | Type | Notes |
|---|---|---|
| `user_id` | string (ref → users) | |
| `body` | string | |
| `verification_or_expert_flag` | bool | inert/unused — D-02 (resolved 2026-10-30) removed the technician role this flag was for; kept for schema compatibility, not wired to anything |
| `moderation_status` | string enum: `visible` \| `hidden` \| `removed` | |
| `created_at` | timestamp | |

## `communityPosts/{postId}/votes/{userId}`

Document ID = voting user's ID, enforcing one active upvote per user per post structurally.

| Field | Type | Notes |
|---|---|---|
| `created_at` | timestamp | |

## `notifications/{notificationId}`

Written only by trusted backend logic (Cloud Function), never directly by clients.

| Field | Type | Notes |
|---|---|---|
| `recipient_user_id` | string, optional | null if `area_scope` targets a geographic audience |
| `area_scope` | string, optional | e.g. `barangay:<name>` — exact scope pending D-06(diagram)/geo rule |
| `type` | string enum: `nearby_report` \| `verified_case` \| `monitoring_alert` \| `outbreak_alert` \| `community_reply` \| `verification_update` | |
| `title` / `message` | string | |
| `disease_code` | string, optional | |
| `related_post_id` / `related_record_id` | string, optional | |
| `created_at` | timestamp | |
| `read_at` | timestamp, optional | |
| `delivery_status` | string enum: `pending` \| `sent` \| `failed` | |

## `deviceTokens/{tokenId}`

| Field | Type | Notes |
|---|---|---|
| `user_id` | string (ref → users) | |
| `device_id_or_installation_id` | string | |
| `fcm_token` | string | |
| `active` | bool | |
| `updated_at` | timestamp | |

## `diseaseReferenceCloud/{disease_code}`

Public read-only reference content; admin-maintained.

| Field | Type | Notes |
|---|---|---|
| `display_name` | string | |
| `symptoms` | string | |
| `treatment_steps` | string | |
| `prevention_steps` | string | |
| `source_reference` | string | |
| `content_version` | string | |
| `published_at` | timestamp | |

## `outbreakRules/{ruleId}`

Structure only — **no production values** until D-07 is expert-approved. All rules start with
`active: false`.

| Field | Type | Notes |
|---|---|---|
| `disease_code` | string | |
| `minimum_confidence` | float, optional | |
| `minimum_verified_reports` | int, optional | |
| `time_window_hours` | int, optional | |
| `radius_km` \| `administrative_scope` | float \| string, optional | |
| `verification_required` | bool | |
| `severity_level` | string, optional | |
| `notification_cooldown_hours` | int, optional | |
| `active` | bool | must be `false` in production until D-07 resolves |
| `approved_by` / `approved_at` | string / timestamp, optional | |
| `version` | string | |

---

## Cross-module payloads (unchanged from the Data Contract)

`DetectionResult`, `ShareableScanDraft`, `MapOccurrence`, and `NearbyReportSummary` are defined in
`claude/08_DATA_AND_INTEGRATION_CONTRACT.md` and are consumed as in-memory DTOs on the Android
side (Ligue) — they are not standalone Firestore collections.
