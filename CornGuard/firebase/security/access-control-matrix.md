# CORNGUARD — Access Control Matrix (Draft, Sprint 0)

Implementation-agnostic security requirements for the entities in `schema/logical-schema.md`.
This matrix is what `security/firestore.rules` implements; if D-01 resolves to Realtime Database
instead, this matrix — not the Firestore-specific file — is the source of truth to re-implement
against.

Ground rules (from `claude/04_DEVELOPMENT_RULES.md` and `claude/08_DATA_AND_INTEGRATION_CONTRACT.md`):

- Authorization is enforced server-side (security rules / Cloud Functions), never by client UI
  hiding alone.
- `role` is never client-writable. Role assignment happens only through a trusted backend path
  (Cloud Function using the Admin SDK, setting a custom auth claim) — never a Firestore field a
  client can set on their own document.
- Community post visibility is separate from outbreak verification (D-08): any authenticated
  user can read posts; only a verification-authorized identity can change `verification_status`,
  and that path stays disabled until D-02 is resolved.
- Exact farm/diagnosis coordinates are not exposed beyond their owner and admin by default (Rule
  #9). Public-facing surfaces (map, community feed) read from barangay/municipality/province
  fields, not raw lat/lng, unless a future approved design says otherwise.

| Collection | Create | Read | Update | Delete |
|---|---|---|---|---|
| `users/{uid}` | self, on signup (fixed `role: farmer`, cannot self-assign `admin`) | self; admin (any) | self — allowed fields only (`display_name`, `barangay`, `municipality`, `province`); `role`, `account_status`, `farm_ids` are backend/admin-only | admin only |
| `farms/{farmId}` | owner (`owner_user_id == auth.uid`) | owner; admin | owner; admin | owner; admin |
| `diagnosisRecordsCloud/{id}` | owner (`user_id == auth.uid`), and only for scans the farmer chose to share (D-11) | owner; admin | owner (non-verification fields only); admin/verifier (verification fields only, disabled pending D-02) | owner; admin |
| `communityPosts/{id}` | any authenticated user (`user_id == auth.uid`) | any authenticated user | owner (content fields); admin (moderation fields) | owner; admin |
| `communityPosts/{id}/comments/{id}` | any authenticated user (`user_id == auth.uid`) | any authenticated user | owner (own comment body); admin (moderation) | owner; admin |
| `communityPosts/{id}/votes/{uid}` | the voting user, doc ID == their own uid (one vote/user/post structurally) | any authenticated user (for counting) | denied (delete + recreate to change vote) | the voting user; admin |
| `notifications/{id}` | backend only (Cloud Function via Admin SDK) — **denied for all clients** | recipient (`recipient_user_id == auth.uid`) or matching `area_scope`; admin | recipient may set `read_at` only; admin | admin |
| `deviceTokens/{id}` | owner (`user_id == auth.uid`) | owner; admin | owner; admin | owner; admin |
| `diseaseReferenceCloud/{code}` | admin only | any authenticated user (public reference content) | admin only | admin only |
| `outbreakRules/{id}` | admin only | admin only (not public — avoid leaking un-approved thresholds) | admin only | admin only |

## Notes on fields requiring extra care

- `communityPosts.upvote_count` — server/Cloud-Function computed from the `votes` subcollection;
  rules must reject any direct client write to this field (Data Contract: "do not trust
  client-supplied aggregate count").
- `diagnosisRecordsCloud.verification_status` / `verified_by` / `verified_at` — write access is
  structurally reserved but has **no authorized writer** until D-02 confirms who is allowed to
  verify. Until then, these fields are admin-only to touch, and admin verification (if it happens
  at all pre-D-02) must be treated as provisional, not production outbreak confirmation (D-07,
  D-08).
- `outbreakRules.active` — must default to and be enforced as `false` at the rules level for any
  document lacking `approved_by`/`approved_at`, so a rules bug can't accidentally let an
  unapproved threshold go live.
