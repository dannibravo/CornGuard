# CornGuard

CornGuard is an Android application built as a capstone project to help farmers identify corn (maize) leaf diseases from a photo, using an on-device TensorFlow Lite classifier. It supports four classes: **Common Rust**, **Gray Leaf Spot**, **Northern Leaf Blight**, and **Healthy**.

Beyond the core scan feature, the app includes diagnosis history, opt-in cloud sharing of scans, a community feed, and a GIS/outbreak-reporting layer (see [Known limitations](#known-limitations) for what of this is actually finished).

**This is an active student capstone project, not a finished or field-validated product.** See [Current model and evaluation results](#current-model-and-evaluation-results) below before assuming the classifier is reliable in real field conditions.

## Project structure

| Directory | Contents |
|---|---|
| `android/` | The Kotlin/Android Studio app — UI, local Room database, repositories, the on-device classifier integration. See `android/README.md` for the full sprint-by-sprint build log. |
| `firebase/` | Firestore schema, security rules, Cloud Functions, and dev tooling for the cloud backend (auth, community posts, GIS data layer, admin moderation). See `firebase/README.md`. |
| `ml/` | The Python training pipeline for the on-device disease classifier (dataset preparation, training, evaluation, TFLite export) and the external-evaluation / field-domain-gap investigation. See `ml/README.md` and [Current model and evaluation results](#current-model-and-evaluation-results) below. |
| `claude/` | Numbered internal planning and contract documents (`00_README.md` through `16_...md`) — project context, development rules, the data/ML contracts, and team responsibilities. |
| `docs/` | The capstone proposal minutes and paper (pre-existing project documentation). |

## Setup instructions

These steps are reproduced from the project's own existing documentation (`android/README.md`, `firebase/README.md`, `ml/README.md`) and cross-checked against the actual files they reference (e.g. `android/local.properties.example`, `ml/requirements.txt` both exist and were read directly). **They were not independently re-executed in this review** — where the source documentation itself records a result (e.g. `android/README.md`: *"Verified with `./gradlew :app:compileDebugKotlin` and `:app:testDebugUnitTest` — both pass"*), that is the project's own prior claim, not something re-confirmed here today.

### Android app

1. Open the **`android/`** directory (not the repository root) in Android Studio.
2. Copy `android/local.properties.example` to `android/local.properties` and set `sdk.dir` to your local Android SDK path. This file is git-ignored.
3. Copy `firebase/config/google-services.dev.json` to `android/app/google-services.json` (git-ignored, required for the Firebase-backed features to build).
4. `minSdk 26` (Android 8.0), `targetSdk`/`compileSdk 34`.
5. Build and test — two separate commands, run from the `android/` directory:
   ```bash
   cd android
   ./gradlew :app:compileDebugKotlin
   ```
   ```bash
   ./gradlew :app:testDebugUnitTest
   ```

### Firebase

Firestore (database, rules, and indexes) is live and deployed in the `cornguard-dev` project. **Cloud Storage and Cloud Functions are not deployed** (require the Blaze billing plan, currently deferred).

Run the test suite — requires Java on `PATH`; the `npm install` is a one-time setup step before the first run:
```bash
cd firebase
npm --prefix tests install
```
```bash
firebase emulators:exec --only firestore "npm --prefix tests test"
```
(The second command must be run from `firebase/`, same as the first — both assume you're still in that directory.)

### ML pipeline

```bash
cd ml
pip install -r requirements.txt
```
The pipeline stages (`prepare_dataset.py` → `train.py` → `evaluate.py` → `export_tflite.py` → `export_metadata.py` → `parity_test.py`) are documented in full, with exact commands, in `ml/README.md` — refer to that file rather than this one for the authoritative pipeline usage. Note: `ml/README.md`'s own text currently describes the pipeline as "never executed" — that description predates the actual training runs referenced below and is now out of date in that one respect; the results in this README and in `ml/artifacts/`, `ml/v1_training_audit/`, `ml/v2_baseline/`, `ml/v2_field_aug_experiment/`, and `ml/v2_field_domain_planning/` reflect what has actually been run.

## Current model and evaluation results

The numbers below are read directly from this repository's own evaluation output files — paths are given so they can be independently checked.

### Checkpoint identity — verified, not assumed

This repository contains results from **three distinct training checkpoints**. They are trained on different (though related) data and evaluated on different test splits — their numbers are not interchangeable, and a higher number from one does not mean a lower number from another is wrong or stale. Verified by direct SHA-256 comparison:

| Checkpoint | Training data | Exported to `.tflite`? | Shipped in the app? |
|---|---|---|---|
| **`v1-baseline`** | `ml/data/dataset_manifest.csv` — the original manifest, later found to contain 12 cross-split duplicate/near-duplicate image pairs (see `ml/v1_training_audit/audit_report.md`) | **Yes** — `ml/artifacts/v1-baseline/cornguard_mobilenetv2_v1.tflite`, SHA-256 `de37cb95...4c6aac`, byte-identical to `android/app/src/main/assets/model.tflite` (independently re-verified with `sha256sum` on both files before writing this README) | **Yes — this is the only model ever shipped** |
| **`v2-clean-baseline`** | `ml/v2_baseline/proposed_clean_manifest.csv` — a corrected manifest with those 12 leaked pairs resolved (4 images excluded pending label review, 10 reassigned to a single split) | No `.tflite` file exists anywhere under `ml/artifacts/v2-clean-baseline/` (verified by directory listing) | No |
| **`v2-field-aug-experiment`** | Same manifest as `v2-clean-baseline`, with one isolated change: the training-only augmentation strategy | No `.tflite` file exists anywhere under `ml/artifacts/v2-field-aug-experiment/` (verified by directory listing) | No |

### The model currently shipped in the app (`android/app/src/main/assets/model.tflite` = checkpoint `v1-baseline`)

MobileNetV2 transfer-learning classifier, trained **only on publicly-sourced (non-field) corn leaf images** — no genuine field photograph was part of its training, validation, or internal test data.

| Evaluation | Accuracy | Macro F1 | Checkpoint | Test set | Source |
|---|---|---|---|---|---|
| **Internal held-out test set** (same distribution as training, 630 images) | 89.52% | 0.874 | `v1-baseline`, re-evaluated **after** TFLite conversion — i.e. this is the shipped `.tflite` file's own measured accuracy, not just the pre-export Keras model's | `v1-baseline`'s own 630-image test split (part of the original, pre-leakage-fix manifest) | `ml/artifacts/v1-baseline/cornguard_mobilenetv2_v1_export_info.json` (`tflite_evaluation` field) |
| **External field evaluation** (1,091 independently-sourced real field photographs, never used in training) | **25.30%** | **0.280** | `v1-baseline` (the shipped `.tflite`, run via `tf.lite.Interpreter`) | Dataset 3 — a separate, frozen, external field-photo set, unrelated to either internal test split above | `ml/field_gap_evaluation_comparison_20261030/field_gap_investigation.md` (the raw Dataset 3 images and per-image outputs are not part of this repository — see the note below) |

The internal test result already fell short of this project's own 90% accuracy / 88% macro-F1 acceptance thresholds. The external field result is close to random-chance level for a 4-class problem: the model systematically over-predicts "Common Rust" regardless of the true class, and its confidence score carries almost no information about whether a given prediction is correct (measured Expected Calibration Error ≈ 0.52 on the external set).

**This is a measured, reproducible result, not a known bug awaiting a quick fix.** The root cause documented in this repository is structural: the training data contains zero field-condition images.

### Experimental follow-up work — not deployed, and not directly comparable to the shipped model's numbers above

A leakage audit (`ml/v1_training_audit/`) found the 12 cross-split duplicate pairs noted above; `ml/v2_baseline/` built the corrected manifest from those findings and used it to train `v2-clean-baseline`, which reached **91.99% accuracy / 0.9007 macro F1** on its *own* 624-image internal test split (`ml/artifacts/v2-clean-baseline/evaluation_report.json`) — a different checkpoint, trained and tested on a different (corrected) data split than the 89.52%/0.874 figure above, **not** a re-measurement of the shipped model. An isolated field-realistic-augmentation experiment on top of that (`v2-field-aug-experiment`, see `ml/v2_field_aug_experiment/`) reached 91.67%/0.892 on the same internal split, and **29.24% accuracy / 0.291 macro F1** on the external Dataset 3 field set, as reported in `ml/field_gap_evaluation_comparison_20261030/field_gap_investigation.md` — a modest improvement over the shipped model's 25.30%, still far below a usable threshold. (The raw Dataset 3 image set and its per-image evaluation outputs are not part of this repository — they live in a separate local working directory; the figures above are the in-repo summary of that work.)

**Neither experimental checkpoint has replaced `model.tflite` or been exported for deployment** (verified above — no `.tflite` file exists for either). The app currently ships, and only ever has shipped, `v1-baseline`.

## Known limitations

- **Not validated for real field conditions** — see the external evaluation numbers above. Do not present this model as production- or field-ready.
- **No field validation by a qualified agricultural expert has occurred** — `ml/README.md` documents this as explicitly out of scope for the ML pipeline alone.
- **The GIS "Map" screen does not currently render a map or heatmap.** Verified directly from source: `android/app/src/main/java/com/cornguard/app/ui/map/MapFragment.kt` renders two `RecyclerView` lists (no `MapView` or map-SDK widget exists in `fragment_map.xml`, and no map-rendering library is present in `android/app/build.gradle.kts`). A data-aggregation function for a future heatmap, `GisRepository.getHeatmapAggregates()` (`android/app/src/main/java/com/cornguard/app/data/repository/GisRepository.kt`), is implemented and queries real Firestore data, but it is never called by the Map screen, and it also depends on a `verification_status="verified"` flag that nothing in the app currently has an authorized path to set.
- **Several project decision gates remain formally unresolved** (cloud database choice, GIS/map SDK provider, the Agricultural Technician role, and others) — see `claude/03_SOURCE_ALIGNMENT_AND_DECISION_GATES.md` for the full, current list.
- **No LICENSE file currently exists in this repository.** Reuse/ownership terms are undefined; the team should decide on and add one before sharing this project outside the capstone context.

## Team and workflow

See `claude/06_TEAM.md` for roles, `claude/07_TEAM_RESPONSIBILITIES_AND_FEATURE_BRANCHES.md` for ownership per module, and `claude/11_OFFICIAL_GITHUB_WORKFLOW.md` for the branching/PR workflow this project follows.
