# CORNGUARD Development Documentation Pack

This folder is the developer-facing implementation guide for **CORNGUARD: A Mobile Application for Real-Time Corn Disease Detection Using Convolutional Neural Networks**.

It translates the structure and discipline used in the Engage development documentation into a CORNGUARD-specific workflow. It is based primarily on the CORNGUARD capstone manuscript and the proposal-defense minutes. It does **not** copy Engage's features or stack.

## Recommended reading order

1. `01_MASTER_DEVELOPMENT_CONTEXT.md`
2. `02_PROJECT_CONTEXT.md`
3. `03_SOURCE_ALIGNMENT_AND_DECISION_GATES.md`
4. `04_DEVELOPMENT_RULES.md`
5. `05_DEVELOPMENT_PLAN.md`
6. `06_TEAM.md`
7. `07_TEAM_RESPONSIBILITIES_AND_FEATURE_BRANCHES.md`
8. `08_DATA_AND_INTEGRATION_CONTRACT.md`
9. `09_ML_MODEL_CONTRACT.md`
10. `10_ENV_GUIDE.md`
11. `11_OFFICIAL_GITHUB_WORKFLOW.md`
12. `12_TESTING_AND_ACCEPTANCE_PLAN.md`
13. `13_DEVELOPMENT_PHASE_GUIDE.md`
14. `14_GITHUB_BEST_PRACTICE.md`
15. `15_CLAUDE.md`

## Three-developer assignment

- **Ligue** — Android Mobile & Integration Lead
- **Panes** — Firebase, Community & GIS Services Lead
- **Acenas** — AI/ML, Dataset & Model Validation Lead

The assignments are intentionally separated by primary ownership, but CORNGUARD is an integrated system. Each developer must still participate in pull-request review, integration testing, and final system validation.

## Core implementation principle

CORNGUARD has a **hybrid offline/online architecture**:

- Disease scanning, TensorFlow Lite inference, treatment guidance, and local scan history must remain usable offline.
- Authentication, cloud synchronization, community discussions, uploaded images, GIS outbreak data, and push notifications depend on Firebase/network services.

The offline detection flow is the product's critical path and must never be made dependent on Firebase availability.

## Important warning about source conflicts

The manuscript and proposal minutes contain several requirements that are not yet fully harmonized, such as the exact Firebase database choice, the Agricultural Technician role, model normalization, dataset split, inference-time target, outbreak thresholds, and the farm entity. These are documented in `03_SOURCE_ALIGNMENT_AND_DECISION_GATES.md`.

Developers must not silently choose conflicting requirements. Any decision that changes a contract must be recorded in the project documentation before dependent code is merged.
