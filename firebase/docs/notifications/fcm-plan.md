# CORNGUARD — FCM Setup Plan (Draft, Sprint 0)

## Device token lifecycle

1. On successful sign-in (or app launch if already signed in), the Android client requests an FCM
   registration token and writes it to `deviceTokens/{tokenId}` (see `schema/logical-schema.md`),
   keyed by `user_id` + `device_id_or_installation_id`.
2. On FCM token rotation (`onNewToken`), the client updates the existing document rather than
   creating a duplicate — `active: true` on the fresh token, and the client marks any prior token
   for the same `device_id_or_installation_id` `active: false`.
3. Tokens are never written by any user other than their owner (enforced in `firestore.rules`).

## Delivery targeting

Two targeting strategies, matched to notification `type`:

- **Per-user** (`community_reply`, `verification_update`): sent directly to the recipient's
  active token(s) looked up from `deviceTokens` where `user_id == recipient_user_id`.
- **Area-scoped** (`nearby_report`, `monitoring_alert`, `outbreak_alert`): sent via an FCM
  **topic** subscription named by administrative area, e.g. `barangay_<slug>` or
  `municipality_<slug>`. The client subscribes to the topic(s) matching the farmer's registered
  `barangay`/`municipality` (from their `users` document) at profile-save time and re-subscribes
  if that location changes. This avoids querying all device tokens in an area for every
  notification.

## Who is allowed to trigger a send

All sends happen from a trusted backend context (Cloud Function), never directly from a client
SDK call — this matches `notifications/{id}` being create-denied for clients in `firestore.rules`.
A Cloud Function writes the `notifications/{id}` document first (the durable, queryable record),
then calls the FCM Admin SDK to push it.

- `community_reply` — triggered by a new document under `communityPosts/{postId}/comments`.
- `nearby_report`, `monitoring_alert`, `outbreak_alert` — **must** originate from the approved
  outbreak-rule evaluation path (D-07). Until an `outbreakRules` document has `active: true` with
  a recorded `approved_by`/`approved_at`, the corresponding Cloud Function trigger stays a no-op
  in any environment other than development-only test configurations, per
  `claude/04_DEVELOPMENT_RULES.md` #5 and `claude/08_DATA_AND_INTEGRATION_CONTRACT.md`'s
  `OutbreakRule` guidance.
- `verified_case`, `verification_update` — inert until D-02 (Agricultural Technician / verifier
  role) is resolved; no authorized writer exists yet for verification fields.

## Payload shape

The FCM data payload mirrors the `notifications/{id}` fields relevant to rendering, so the
Android client can display a notification even before it has fetched the full document:

```
{
  "notification_id": "...",
  "type": "nearby_report" | "verified_case" | "monitoring_alert" | "outbreak_alert"
          | "community_reply" | "verification_update",
  "title": "...",
  "message": "...",
  "disease_code": "..." | null,
  "related_post_id": "..." | null,
  "related_record_id": "..." | null
}
```

The client treats this as a hint to refresh/open the relevant screen and always re-reads the
authoritative `notifications/{id}` document rather than trusting the push payload as final state
(handles delivery race conditions and keeps `read_at` server-tracked).

## Failure handling

- FCM send failures update `delivery_status: "failed"` on the notification document; the
  Cloud Function does not retry indefinitely (avoids notification storms if a topic is
  misconfigured).
- The Android client must handle "notifications unavailable offline" gracefully — this is a
  best-effort online feature per the offline/online boundary in
  `claude/02_PROJECT_CONTEXT.md`.
