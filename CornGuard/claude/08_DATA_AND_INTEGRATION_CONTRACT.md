# CORNGUARD — DATA AND INTEGRATION CONTRACT v0.1

This document is the equivalent of an API contract for CORNGUARD's hybrid architecture. CORNGUARD is not defined as a custom REST-backend system; its online layer uses Firebase services while its offline layer uses Android/TFLite/SQLite. Therefore the contract defines logical entities, repository boundaries, and cross-module payloads rather than invented HTTP endpoints.

Changing a field name, enum, model class order, or integration payload after another module begins consuming it requires updating this file and notifying the team.

## Conventions

- IDs are strings unless a local SQLite integer key is explicitly local-only.
- Timestamps should be stored in an unambiguous UTC form for cloud records.
- Latitude range: -90 to 90.
- Longitude range: -180 to 180.
- Confidence is represented consistently as either 0.0–1.0 internally or 0–100 for display; do not mix units in storage.
- Store `model_version` with diagnosis records.
- Community verification status must be distinct from model confidence.
- Exact farm coordinates must not automatically be exposed publicly.

## Role Values

Confirmed baseline:

- `farmer`
- `admin`

Reserved/conditional:

- `technician`

Do not enable `technician` authorization until Decision D-02 is approved.

---

# Local SQLite Contract

## LocalDiagnosisRecord

Required fields:

- `local_id`
- `user_id` — nullable if scan is performed before/without cloud identity availability
- `farm_id` — nullable until farm association is confirmed
- `disease_code`
- `display_label`
- `confidence`
- `image_uri_or_local_path`
- `captured_at`
- `latitude` — nullable
- `longitude` — nullable
- `barangay` — nullable
- `municipality` — nullable
- `province` — default target region may be Bukidnon but do not hardcode if records can exist elsewhere
- `model_version`
- `shared_to_cloud` boolean
- `cloud_record_id` — nullable

Rules:

- Record must remain readable offline.
- A scan is local/private by default unless approved workflow says otherwise.
- Deleting a cloud post must not automatically delete local scan history unless the user explicitly deletes the local record.

## LocalDiseaseReference

Fields:

- `disease_code`
- `display_name`
- `symptoms`
- `treatment_steps`
- `prevention_steps`
- `source_reference`
- `content_version`
- `updated_at`

Rules:

- Must be available offline.
- Content must be validated from approved agricultural references.

## CachedCommunityContent

Implementation may rely on Firebase/client offline caching rather than a custom SQLite table. If a custom cache is built, it must be clearly separated from guaranteed local diagnosis history and must not be treated as authoritative outbreak state when stale.

---

# Cloud Logical Data Contract

The exact Firebase database product is Decision D-01. The entities below are logical and can map to Firestore collections or Realtime Database nodes.

## User

Fields:

- `user_id`
- `display_name`
- `email` — optional depending registration method
- `mobile_number` — optional depending registration method
- `role`
- `barangay`
- `municipality`
- `province`
- `farm_ids` or equivalent relationship after D-03
- `created_at`
- `updated_at`
- `account_status`

Security:

- Users may update only approved profile fields.
- Role elevation must not be client-controlled.

## Farm — Required Schema Revision from Panel Recommendation

Final cardinality pending D-03.

Provisional fields:

- `farm_id`
- `owner_user_id`
- `farm_name_or_label`
- `barangay`
- `municipality`
- `province`
- `latitude` — optional/exact location where approved
- `longitude` — optional/exact location where approved
- `created_at`
- `updated_at`

Do not add arbitrary farm business fields not supported by the study.

## DiagnosisRecordCloud

Created only for scans that are intentionally synchronized/shared according to the approved workflow.

Fields:

- `record_id`
- `user_id`
- `farm_id` — after farm model approval
- `disease_code`
- `confidence`
- `image_url` — nullable if no image uploaded
- `captured_at`
- `latitude`
- `longitude`
- `barangay`
- `municipality`
- `province`
- `model_version`
- `verification_status`
- `verified_by` — nullable
- `verified_at` — nullable
- `source` — e.g. `ai_scan` or approved manual source

Rules:

- Model confidence is not the same as verification status.
- Record location must be validated before being used for GIS aggregation.

## CommunityPost

Fields based on the study:

- `post_id`
- `user_id`
- `linked_diagnosis_record_id` — nullable
- `title`
- `body`
- `disease_tag`
- `image_url` — optional
- `latitude` — optional/private depending display policy
- `longitude` — optional/private depending display policy
- `barangay`
- `municipality`
- `province`
- `created_at`
- `updated_at`
- `verification_status`
- `moderation_status`
- `upvote_count` — if upvotes remain in final scope

Allowed disease tags should include the four model classes and `unknown` if the community design retains it.

## Comment

Fields:

- `comment_id`
- `post_id`
- `user_id`
- `body`
- `created_at`
- `verification_or_expert_flag` — only if technician role is approved
- `moderation_status`

Threading beyond one level must be explicitly approved before implementing complex recursive structures. The manuscript requires threaded/multi-turn discussion but does not require unlimited nesting.

## Vote / Upvote

If retained:

- `post_id`
- `user_id`
- `created_at`

Rule:

- One active upvote per user per post.
- Do not trust client-supplied aggregate count.

## Notification

Fields:

- `notification_id`
- `recipient_user_id` or geographic audience reference
- `type`
- `title`
- `message`
- `disease_code` — nullable
- `related_post_id` — nullable
- `related_record_id` — nullable
- `area_scope`
- `created_at`
- `read_at` — nullable
- `delivery_status`

## DeviceToken

Fields:

- `user_id`
- `device_id_or_installation_id`
- `fcm_token`
- `updated_at`
- `active`

Tokens must be invalidated/updated when FCM rotates them.

## DiseaseReferenceCloud

If the admin can maintain reference content centrally:

- `disease_code`
- `display_name`
- `symptoms`
- `treatment_steps`
- `prevention_steps`
- `source_reference`
- `content_version`
- `published_at`

The mobile app may download approved updates and store them locally, but the app must still retain offline guidance.

## OutbreakRule — Configurable, Not Finalized

Do not assign production values until agricultural expert approval.

Potential fields:

- `rule_id`
- `disease_code`
- `minimum_confidence`
- `minimum_verified_reports`
- `time_window_hours`
- `radius_km` or administrative scope
- `verification_required`
- `severity_level`
- `notification_cooldown_hours`
- `active`
- `approved_by`
- `approved_at`
- `version`

This structure supports panel-required safeguards without inventing the actual thresholds.

---

# Cross-Module Payloads

## DetectionResult

Produced by Acenas's model contract and consumed by Ligue.

- `disease_code`
- `class_index`
- `confidence`
- `probabilities[4]`
- `model_version`
- `inference_time_ms`

## ShareableScanDraft

Produced by local scan flow and consumed by community report flow.

- `local_diagnosis_id`
- `disease_code`
- `confidence`
- `image_uri`
- `captured_at`
- `approximate_location`
- `model_version`

The user must still review/edit approved report fields before publishing.

## MapOccurrence

Produced by Panes's cloud/GIS layer and consumed by Ligue's map UI.

- `occurrence_id`
- `disease_code`
- `latitude_or_render_coordinate`
- `longitude_or_render_coordinate`
- `barangay`
- `municipality`
- `occurred_at`
- `verification_status`
- `severity_or_weight` — only if approved rule defines it

## NearbyReportSummary

- `post_id`
- `disease_tag`
- `barangay`
- `municipality`
- `created_at`
- `distance_km` — only if location calculation is enabled
- `verification_status`

---

# Security Contract

- Authentication is handled by Firebase Authentication for online account features.
- Authorization must be enforced in Firebase security rules/backend logic, not only in UI.
- Admin role cannot be self-assigned by client writes.
- Exact coordinates require access rules appropriate to privacy decisions.
- Uploaded images must use restricted paths and validated content type/size.
- Community users cannot edit/delete another user's content unless approved moderation privileges apply.
- Cloud rules must be tested before production.
