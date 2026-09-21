# CORNGUARD — Firebase / Community / GIS Services (Panes)

This directory holds the Firebase/Community/GIS deliverables owned by the Firebase, Community &
GIS Services Lead, per `claude/05_DEVELOPMENT_PLAN.md`. Currently spans Sprint 0 (schema, rules,
interfaces) and the start of Sprint 1 (Cloud Functions, dev fixtures).

## Sprint 0 — schema, rules, interfaces

- `schema/logical-schema.md` — concrete elaboration of the logical cloud entities defined in
  `claude/08_DATA_AND_INTEGRATION_CONTRACT.md`, with field types and illustrative Firestore
  collection paths.
- `security/access-control-matrix.md` — implementation-agnostic access rules (who can read/write
  what), independent of the final database product.
- `security/firestore.rules` — a **development draft** implementing the access matrix in
  Firestore Rules syntax.
- `security/storage.rules` — Cloud Storage path/validation rules for uploaded images.
- `gis/gis-service-interface.md` — a provider-agnostic GIS/location service interface.
- `notifications/fcm-plan.md` — FCM device-token and notification-payload plan.
- `repositories/auth-repository-interface.md` — account creation/sign-in contract.
- `repositories/user-farm-repository-interface.md` — profile and farm data contract.
- `repositories/diagnosis-sharing-repository-interface.md` — opt-in cloud sharing of a local scan.
- `repositories/community-repository-interface.md` — posts, comments, and upvotes contract.
- `tests/rules.test.mjs` — automated Firestore emulator tests verifying `security/firestore.rules`
  actually enforces the access-control matrix (role self-elevation, cross-user data access,
  faked upvote counts, direct notification writes, non-admin outbreak-rule visibility, and the
  default-deny fallback). Run with:
  ```
  cd firebase
  firebase emulators:exec --only firestore "npm --prefix tests test"
  ```
  (requires Java on PATH for the Firestore emulator, and `npm install` inside `tests/` once).

## Sprint 1 (in progress) — Cloud Functions, dev fixtures

- `functions/logic.mjs` + `functions/index.mjs` — Cloud Functions implementing the two pieces
  `security/firestore.rules` assumes but Sprint 0 never built: granting the `role: admin` custom
  auth claim (`promoteToAdmin`, admin-only) and the backend-only writer for `notifications/{id}`
  (`onCommentCreate` creates one when someone replies to a post; `onNotificationCreate` then sends
  the FCM push and marks `delivery_status`). Logic is separated into `logic.mjs` so it's
  unit-testable without any emulator.
- `scripts/bootstrap-first-admin.mjs` — the one deliberate, manual, out-of-band exception to
  "only an admin can promote an admin": grants the very first admin account, run once locally by
  a trusted operator, never as a deployed function.
- `scripts/seed-dev-fixtures.mjs` — seeds `cornguard-dev`'s live Firestore with sample
  users/farms/disease-reference/community-post data for Ligue's Android integration testing.
  **The disease reference text it writes is explicitly placeholder** (labeled as such in the
  seeded documents) — real treatment/prevention content must come from an approved agricultural
  source per `claude/09_ML_MODEL_CONTRACT.md`'s Treatment Guidance Boundary, not from this script.
- `tests/functions-logic.test.mjs` — pure unit tests for `functions/logic.mjs`, no emulator
  needed.
- `tests/functions-integration.test.mjs` — end-to-end test proving the real `onCommentCreate` ->
  `onNotificationCreate` trigger chain fires, run against the Firestore + Functions emulators
  together:
  ```
  cd firebase
  firebase emulators:exec --only firestore,functions "npm --prefix tests run test:integration"
  ```

**Status:** written and passing against the local emulator (26 rules/logic tests + 2 integration
tests, all green). **Not deployed** to the live `cornguard-dev` project — like Storage, Cloud
Functions Gen 2 requires the Blaze billing plan, which the team deferred (see
`claude/03_SOURCE_ALIGNMENT_AND_DECISION_GATES.md`, D-01 dev-provisioning note). Deploy once
that's revisited.

## What this drop deliberately does NOT do

Per `claude/03_SOURCE_ALIGNMENT_AND_DECISION_GATES.md`, none of the following decision gates are
resolved by these files — they are written to remain valid regardless of how each gate resolves:

- **D-01 (cloud database choice)** — the schema and access matrix are described in
  implementation-agnostic terms first; the Firestore rules file is one illustrative binding,
  clearly labeled as a draft, not a team decision.
- **D-02 (Agricultural Technician role)** — no `technician` authorization path is enabled.
- **D-03 (Farm ownership cardinality)** — the schema uses a `farm_ids` relationship compatible
  with either one-to-one or one-to-many ownership.
- **D-07 (outbreak thresholds)** — no production alert threshold is set; the outbreak rule
  structure is present but inert until expert-approved values are supplied.
- **D-10 (GIS/map SDK provider)** — the GIS service interface has no provider-specific types.

Any change to these files that touches a shared contract must be reflected back into
`claude/08_DATA_AND_INTEGRATION_CONTRACT.md` per the Contract-First Integration rule
(`claude/04_DEVELOPMENT_RULES.md` #3).
