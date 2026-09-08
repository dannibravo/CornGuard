# CORNGUARD — Diagnosis-Sharing Repository Interface (Draft, Sprint 0)

Provider-agnostic contract for `diagnosisRecordsCloud/{recordId}` in `schema/logical-schema.md`.
Kept separate from the Community repository because a shared diagnosis record and a community
post are distinct entities that only optionally reference each other
(`CommunityPost.linked_diagnosis_record_id`).

Per D-11 (`claude/03_SOURCE_ALIGNMENT_AND_DECISION_GATES.md`): **a scan is local/private by
default.** Nothing in this repository is called unless the farmer explicitly chooses to share a
completed local scan. The local SQLite `LocalDiagnosisRecord` (Ligue's domain) is the source of
truth for a farmer's own history; this repository only handles the subset the farmer opts to
publish.

## Value types

```
ShareableScanDraft {          // produced locally by Ligue's scan flow, consumed here
  local_diagnosis_id: string,
  disease_code: string,
  confidence: float,          // 0.0-1.0
  image_uri: string,
  captured_at: timestamp,
  approximate_location: AdministrativeArea,   // see gis-service-interface.md
  model_version: string
}

DiagnosisRecordCloud {
  record_id: string,
  user_id: string,
  farm_id: string?,
  disease_code: string,
  confidence: float,
  image_url: string?,
  captured_at: timestamp,
  barangay: string,
  municipality: string,
  province: string,
  model_version: string,
  verification_status: "unverified" | "verified" | "rejected",
  source: "ai_scan" | "manual"
}
```

## Operations

```
shareScan(draft: ShareableScanDraft, farmId: string?, ownerUserId: string) -> recordId: string

  Uploads the image (see security/storage.rules, diagnosisImages/{userId}/{recordId}/) and
  creates the diagnosisRecordsCloud document. Always created with
  verification_status="unverified" and source="ai_scan" — server rules reject any other initial
  value (security/firestore.rules).

getSharedRecordsForUser(uid: string) -> List<DiagnosisRecordCloud>
observeSharedRecordsForUser(uid: string) -> Stream<List<DiagnosisRecordCloud>>
deleteSharedRecord(recordId: string) -> void

  Per the Data and Integration Contract: deleting a cloud-shared record must NOT delete the local
  SQLite history entry unless the user explicitly deletes that local record too — those are two
  separate user actions, not one cascading operation.
```

## Explicit non-goals

- No verification-field writes (`verification_status`, `verified_by`, `verified_at`) — reserved,
  inert until D-02 resolves who is authorized to verify.
- No GIS aggregation logic — a shared record becoming visible on the disease map/heatmap is the
  GIS service's read-side concern (`gis/gis-service-interface.md`), not something this repository
  triggers directly.
- Does not decide whether every scan gets auto-shared — per D-11, the UI must present an explicit
  share action; this repository has no "share automatically" mode.
