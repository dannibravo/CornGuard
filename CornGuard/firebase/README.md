# CORNGUARD — Firebase / Community / GIS Services (Panes)

This directory holds the Sprint 0 deliverables owned by the Firebase, Community & GIS
Services Lead, per `claude/05_DEVELOPMENT_PLAN.md` (Sprint 0) and
`claude/13_DEVELOPMENT_PHASE_GUIDE.md` (Phase 0).

## Scope of this drop

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
