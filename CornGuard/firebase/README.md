# CORNGUARD — Firebase / Community / GIS Services (Panes)

This directory holds the Firebase-side deliverables owned by the Firebase, Community & GIS
Services Lead, per `claude/05_DEVELOPMENT_PLAN.md`. It now spans Sprint 0 through Sprint 5 —
schema, rules, Cloud Functions, and dev tooling. The Kotlin/Android side of the same sprints
(repositories that call into what's documented here) lives on the `feature/auth-service` branch —
see `android/README.md` there for the matching Sprint 1–5 sections.

## Schema, security, and interface contracts

- `schema/logical-schema.md` — concrete elaboration of the logical cloud entities defined in
  `claude/08_DATA_AND_INTEGRATION_CONTRACT.md`, with field types and illustrative Firestore
  collection paths.
- `security/access-control-matrix.md` — implementation-agnostic access rules (who can read/write
  what), independent of the final database product.
- `security/firestore.rules` — a **development draft** implementing the access matrix in
  Firestore Rules syntax. Deployed and live in the `cornguard-dev` project.
- `security/storage.rules` — Cloud Storage path/validation rules for uploaded images. Drafted, not
  deployed — Storage needs the Blaze plan (see Status below).
- `firestore.indexes.json` — composite indexes for the location-aware community feed and
  nearby-report queries. Deployed and live (index deployment doesn't need Blaze).
- `gis/gis-service-interface.md` — a provider-agnostic GIS/location service interface.
- `notifications/fcm-plan.md` — FCM device-token and notification-payload plan.
- `repositories/auth-repository-interface.md` — account creation/sign-in contract.
- `repositories/user-farm-repository-interface.md` — profile and farm data contract.
- `repositories/diagnosis-sharing-repository-interface.md` — opt-in cloud sharing of a local scan.
- `repositories/community-repository-interface.md` — posts, comments, and upvotes contract.
- `repositories/admin-repository-interface.md` — admin verification, moderation, user management,
  and notification monitoring contract (written retroactively to match the Kotlin implementation
  — see Sprint 5 below).

## Cloud Functions (`functions/`)

Implements the pieces `security/firestore.rules` assumes but no rules file alone can provide:

- `promoteToAdmin` — grants the `role: admin` custom auth claim `isAdmin()` checks. Admin-only;
  the very first admin is a separate, manual exception (`scripts/bootstrap-first-admin.mjs`).
- `onCommentCreate` → `onNotificationCreate` — creates a notification when someone replies to a
  post, then delivers it (per-user token lookup or FCM topic send for area-scoped alerts) and
  tracks `delivery_status` so failures are visible instead of retrying indefinitely.
- `onVoteWrite` — recomputes `communityPosts.upvote_count` from the `votes` subcollection in a
  transaction; nothing else ever computed the real value (the rules only block clients from faking
  it).

Logic is separated into `functions/logic.mjs` so the decision-making is unit-testable without any
emulator; `functions/index.mjs` wires it to the actual Firestore/Auth/Messaging calls.

## Dev tooling (`scripts/`)

- `scripts/bootstrap-first-admin.mjs` — the one deliberate, manual, out-of-band exception to "only
  an admin can promote an admin": grants the very first admin account, run once locally.
- `scripts/seed-dev-fixtures.mjs` — seeds `cornguard-dev`'s live Firestore with sample
  users/farms/disease-reference/community-post data for Android integration testing. **The disease
  reference text it writes is explicitly placeholder** — real treatment/prevention content must
  come from an approved agricultural source per `claude/09_ML_MODEL_CONTRACT.md`'s Treatment
  Guidance Boundary, not from this script.

## Tests (`tests/`)

- `tests/rules.test.mjs` — Firestore emulator tests verifying `security/firestore.rules` enforces
  the access-control matrix: role self-elevation, cross-user data access, faked upvote counts,
  direct notification writes, non-admin outbreak-rule visibility, the default-deny fallback, and
  (Sprint 5) the admin verification/moderation/user-management bypass paths.
- `tests/functions-logic.test.mjs` — pure unit tests for `functions/logic.mjs`, no emulator
  needed.
- `tests/functions-integration.test.mjs` — end-to-end tests proving the real Cloud Function
  triggers fire: comment → notification → FCM send attempt, vote create/delete →
  `upvote_count` increment/decrement, and area-scoped notification → topic routing.

Run the emulator-only suite (rules + logic, 36 tests):
```
cd firebase
firebase emulators:exec --only firestore "npm --prefix tests test"
```
Run the full suite including real trigger chains (add the Functions emulator):
```
firebase emulators:exec --only firestore,functions "npm --prefix tests run test:integration"
```
(Both require Java on PATH for the Firestore/Functions emulators, and `npm install` inside
`tests/` once.)

## Status

All of the above is written and passing locally: 36 rules/logic tests + 3 integration tests, all
green. **Cloud Storage and Cloud Functions are not deployed** to the live `cornguard-dev`
project — both require the Blaze billing plan, which the team deferred (see
`claude/03_SOURCE_ALIGNMENT_AND_DECISION_GATES.md`'s D-01 dev-provisioning note). Firestore
(database, rules, and indexes) **is** live and deployed.

## What this directory deliberately does NOT do

Per `claude/03_SOURCE_ALIGNMENT_AND_DECISION_GATES.md`, none of the following decision gates are
resolved here — everything is written to remain valid regardless of how each gate resolves:

- **D-01 (cloud database choice)** — the schema and access matrix are implementation-agnostic
  first; Firestore is a pragmatic dev-only/solo-continuation lean (see the two continuation notes
  under D-01), not the required team decision.
- **D-02 (Agricultural Technician role)** — no `technician` authorization path is enabled anywhere.
- **D-03 (Farm ownership cardinality)** — the schema uses a `farm_ids` relationship compatible
  with either one-to-one or one-to-many ownership.
- **D-07 (outbreak thresholds)** — no production alert threshold is set; the outbreak rule
  structure is present but inert until expert-approved values are supplied. Nothing built here
  (including Sprint 4's heatmap aggregation) creates a path around that.
- **D-10 (GIS/map SDK provider)** — the GIS service interface has no provider-specific types; see
  its own continuation note for the pragmatic osmdroid lean, which binds no code here either.

Any change to these files that touches a shared contract must be reflected back into
`claude/08_DATA_AND_INTEGRATION_CONTRACT.md` per the Contract-First Integration rule
(`claude/04_DEVELOPMENT_RULES.md` #3).
