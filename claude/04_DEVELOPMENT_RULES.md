# CORNGUARD — DEVELOPMENT RULES

## 1. Follow Approved Project Documentation

Develop exactly what the approved CORNGUARD study defines. Do not add unrelated features, extra platforms, new disease classes, or new workflows without documented approval.

## 2. Preserve the Offline Diagnostic Core

The following must not depend on Firebase or network availability:

- camera/gallery image selection,
- preprocessing,
- TFLite inference,
- result display,
- treatment/prevention guidance,
- local scan history.

A network outage must not prevent a farmer from scanning a leaf.

## 3. Contract-First Integration

Before two developers connect modules, define the data contract first.

Examples:

- Acenas defines model input/output contract before Ligue integrates TFLite.
- Panes defines cloud report schema before Ligue builds final community submission binding.
- Panes defines GIS report/query contract before Ligue finalizes map UI behavior.

Do not silently rename fields after another developer has started consuming them.

## 4. No Hardcoded Secrets

Never commit:

- Firebase service-account keys,
- private signing keys,
- unrestricted map provider secrets,
- private API tokens,
- personal credentials.

Android client configuration that is necessarily public must still be environment-specific and restricted using the provider's supported security controls.

## 5. No Production Outbreak Threshold Without Approval

Outbreak thresholds and severe-alert criteria must be expert-backed and configurable. Test values must be labeled development-only.

## 6. Model Versioning Is Mandatory

Every TFLite model released to the app must have:

- model version,
- training dataset version,
- class-order definition,
- preprocessing definition,
- evaluation metrics,
- export date,
- SHA-256 checksum or equivalent integrity identifier,
- known limitations.

Diagnosis records should store the model version used for the scan.

## 7. Never Guess Class Order

The Android app must use the exact class order exported by Acenas. A correct model with the wrong class-index mapping produces incorrect disease labels.

## 8. Reproduce Preprocessing Exactly

Python training/evaluation preprocessing and Android inference preprocessing must match. If they differ, the mobile result is not valid even if the model file loads successfully.

## 9. Location Data Is Sensitive

Use the minimum location precision necessary for the approved function. Do not expose private farm coordinates in community UI unless that level of precision is explicitly approved. Separate stored exact coordinates from public display labels where needed.

## 10. Community Reports Are Not Automatically Confirmed Outbreaks

Maintain statuses such as `unverified`, `verified`, `rejected`, or equivalent approved values. Do not use a normal community post as official confirmation by default.

## 11. UI Must Match the Study's Intended Simplicity

The application is designed for farmers with varying digital literacy.

Rules:

- large and clear actions,
- simple language,
- strong visual hierarchy,
- minimal steps,
- camera reachable quickly,
- clear online/offline status where relevant,
- clear distinction between AI diagnosis and community advice.

## 12. Keep Treatment Guidance Local and Versioned

Treatment/prevention guidance must remain available offline. Changes to treatment content should be traceable to an approved agricultural source/version.

## 13. Firebase Rules Are Code

Firebase security rules and indexes must be version-controlled and peer-reviewed. Do not rely on open development rules in production.

## 14. Least Privilege

- Farmers can access only their authorized profile/private records and permitted community data.
- Admin privileges must be explicitly checked.
- Conditional technician privileges must not exist until approved.
- Client-side UI hiding is not authorization.

## 15. Validate Inputs

Validate:

- image format and readability,
- required post fields,
- disease tags,
- role values,
- latitude/longitude ranges,
- timestamps,
- file type/size for uploads,
- confidence range,
- IDs and ownership references.

## 16. Separate Local and Cloud IDs

Offline records need stable local IDs. If a record is later shared/synced to the cloud, store the cloud ID separately. Do not overwrite the local primary key with a remote ID.

## 17. Logging Must Protect Privacy

Do not log passwords, auth tokens, exact private location values, or personal data in release builds.

## 18. Testing Is Part of Every Feature

A feature is not complete when the UI appears. It is complete only when:

- unit/module behavior is tested,
- error states are handled,
- offline/online behavior is tested where applicable,
- integration contract is verified,
- developer acceptance checklist passes.

## 19. Pull Requests Only

All implementation work must be done on feature branches and merged through a pull request into `develop`.

## 20. Main Is Release-Ready

`main` should contain only approved, integrated, tested sprint/release output. Do not use it as a daily work branch.

## 21. Do Not Redesign Major Flows Without Approval

Confirmed wireframes and use cases are development references. If the team identifies a usability problem, document the proposed change and obtain approval before replacing the workflow.

## 22. Source Conflict Rule

If the manuscript contains conflicting requirements, stop the dependent implementation and record the conflict in `03_SOURCE_ALIGNMENT_AND_DECISION_GATES.md`. Do not choose whichever version is easier to code.
