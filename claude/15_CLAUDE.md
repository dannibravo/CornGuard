# CORNGUARD Project — AI Coding Assistant Instructions

You are assisting the CORNGUARD development team.

Before making implementation decisions, read in this order:

1. `00_README.md`
2. `01_MASTER_DEVELOPMENT_CONTEXT.md`
3. `02_PROJECT_CONTEXT.md`
4. `03_SOURCE_ALIGNMENT_AND_DECISION_GATES.md`
5. `04_DEVELOPMENT_RULES.md`
6. `08_DATA_AND_INTEGRATION_CONTRACT.md`
7. `09_ML_MODEL_CONTRACT.md`
8. the relevant approved UI/use-case/ERD/DFD artifacts

## Project Principle

CORNGUARD is an offline-first Android disease-detection application with online community/GIS/notification services.

Never make the offline scan path dependent on Firebase.

## Do Not Invent Features

Do not add:

- new disease classes,
- a web admin dashboard,
- cloud-only disease inference,
- chatbot features,
- unrelated analytics,
- automatic expert diagnosis,
- unapproved outbreak thresholds,
- new user roles,

unless the approved project documents are updated.

## UI Rule

Follow the approved mobile screens and simple farmer-centered workflow. Do not redesign major navigation or create extra major screens without approval.

## Model Rule

Never guess:

- normalization,
- class order,
- tensor type,
- quantization parameters,
- model version.

Read `09_ML_MODEL_CONTRACT.md` and the actual release metadata.

## Data Rule

Follow `08_DATA_AND_INTEGRATION_CONTRACT.md`.

If a field or entity is missing, do not silently create a permanent schema. Check decision gates and project artifacts first.

## Firebase Rule

- Enforce authorization in security rules.
- Never use open production rules.
- Never put Admin SDK service-account secrets in the Android app.
- Do not trust client-supplied admin role values.

## Location Rule

Treat exact farm coordinates as sensitive. Do not expose them publicly unless the approved design explicitly requires that precision.

## Outbreak Rule

A community report is not automatically a confirmed outbreak. Do not code production alert thresholds without approved expert-backed parameters.

## Treatment Rule

Treatment guidance comes from approved local reference content; the ML model only classifies the leaf.

## Git Rule

Work only on feature/fix/test/docs branches created from `develop`.

Normal flow:

`feature/* -> develop -> main`

## Error Handling

Every online feature must handle:

- no internet
- auth expiration
- permission denial
- upload failure
- unavailable cloud data

Every scan feature must handle:

- unreadable image
- camera/gallery failure
- unsupported/corrupt image
- model load failure
- invalid tensor/output state

## Testing Rule

Do not declare work complete without tests appropriate to the module.

For model integration, compare Android outputs against reference Python/TFLite outputs.

For cloud changes, test Firebase rules and unauthorized access.

For offline changes, test with network disabled.

## Source Conflict Rule

If project sources disagree, do not pick one silently. Add or reference a Decision Gate and ask the team to resolve it before implementing the dependent final behavior.
