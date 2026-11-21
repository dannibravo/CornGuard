# CORNGUARD — Admin Repository Interface (Draft, Sprint 5)

Written retroactively to match `android/app/src/main/java/com/cornguard/app/data/repository/`
`AdminRepository.kt` and its Firebase implementation exactly, keeping this directory a complete,
consistent reference alongside the other five repository interfaces
(`auth-repository-interface.md`, `user-farm-repository-interface.md`,
`diagnosis-sharing-repository-interface.md`, `community-repository-interface.md`,
`gis-service-interface.md`) — Contract-First Integration
(`claude/04_DEVELOPMENT_RULES.md` #3).

Every operation here corresponds to something `security/firestore.rules` only allows an
`isAdmin()` caller to perform. This repository does not re-check that client-side — Firestore
rejects an unauthorized attempt on its own, same as every other repository in this directory.
Authorization is enforced server-side; client-side UI hiding is not authorization
(`claude/04_DEVELOPMENT_RULES.md` #14).

## Verification is not blocked by D-02

Verifying a diagnosis record or community post does **not** wait on D-02 (Agricultural Technician
role). Admin is already a confirmed role in the approved system, distinct from the conditional
Technician role D-02 gates (`claude/03_SOURCE_ALIGNMENT_AND_DECISION_GATES.md`). It is still
subject to D-07/D-08: an admin verifying an individual record does not create or enable any
production outbreak-alert trigger — that stays a separate, still-gated concern (see
`gis-service-interface.md`'s `getHeatmapAggregates`, which reads exactly the `verified` records
this repository's verification methods produce).

## Operations

```
verifyDiagnosisRecord(recordId: string, adminUid: string, note: string? = null) -> void
rejectDiagnosisRecord(recordId: string, adminUid: string, note: string? = null) -> void

  Sets verification_status ("verified" | "rejected"), verified_by, verified_at on
  diagnosisRecordsCloud/{recordId}, appends one entry to its verificationHistory subcollection,
  AND creates/updates (verify) or deletes (reject) its verifiedOccurrences/{recordId} public
  projection — all three writes commit in the same WriteBatch (Phase 2 + Phase 4, resolved
  2026-10-30), so the record's status, its audit trail, and its public map marker can never
  diverge. [note] is an optional free-text reason, stored on the history entry only, never on the
  record or the public projection. Verifying a record with no latitude/longitude produces no
  projection (no invented coordinates) — verification still succeeds, it just won't appear on the
  map. Rejecting a previously-verified record removes its projection even if one never existed
  (harmless no-op delete).

verifyPost(postId: string, adminUid: string) -> void
rejectPost(postId: string, adminUid: string) -> void

  Sets verification_status ("verified" | "rejected"), verified_by, verified_at on
  communityPosts/{postId}. Deliberately unchanged by Phase 2 — posts do not get a
  verificationHistory subcollection this phase.

getPendingDiagnosisRecords(limit: int = 100) -> List<DiagnosisRecordCloud>

  diagnosisRecordsCloud documents with verification_status == "unverified", sorted oldest-first
  client-side (a single-field equality query needs no composite index). Powers the Admin Review
  queue.

getVerificationHistory(recordId: string) -> List<VerificationHistoryEntry>

  VerificationHistoryEntry { entry_id, action ("verified" | "rejected"), actor_user_id, at,
  note? } — oldest-first. Empty (not an error) for a record with no history entries yet.

setPostModerationStatus(postId: string, status: "visible" | "hidden" | "removed") -> void
setCommentModerationStatus(postId: string, commentId: string, status: "visible" | "hidden" | "removed") -> void

setUserAccountStatus(uid: string, status: "active" | "suspended") -> void

promoteToAdmin(targetUid: string) -> void

  Calls the promoteToAdmin Cloud Function (firebase/functions/index.mjs) rather than writing
  Firestore directly — granting admin requires setting the Auth custom claim
  security/firestore.rules actually checks via isAdmin(), which only a trusted backend context can
  do. Fails with permission-denied if the caller isn't already an admin. The very first admin
  cannot be created through this path at all — see scripts/bootstrap-first-admin.mjs, a
  deliberate, manual, out-of-band exception run once by a trusted operator.

updateDiseaseReference(diseaseCode: string, fields: Map<string, any>) -> void

getRecentNotifications(limit: int) -> List<NotificationLogEntry>

  NotificationLogEntry { notification_id, type, recipient_user_id?, area_scope?, title,
  delivery_status, created_at } — a monitoring/logging view over the notifications collection
  (fcm-plan.md), not a send path.
```

## Explicit non-goals

- No bulk/batch moderation — one document at a time, matching what the rules authorize per-write.
- No outbreak-rule editing here — `outbreakRules/{id}` writes are admin-only per the rules, but
  the Development Rules explicitly say no production threshold may be set without expert approval
  (D-07); this repository does not add a path around that, and none is planned until D-07
  resolves.
- ~~No audit trail / change history~~ — resolved 2026-10-30 (Phase 2): see
  `verificationHistory` above. This remains true for `communityPosts` — no audit trail exists for
  post verification/moderation; only diagnosis records get one this phase.
- No admin UI for resolving a *rejected* record back to unverified, or for re-verifying a record
  an admin previously rejected — `rejectDiagnosisRecord` is a terminal action in this phase's UI
  (`AdminReviewFragment` only shows `unverified` records, so a rejected one simply drops out of
  the queue). Reopening a rejected record is unimplemented, not merely hidden.
