# v2-field-aug-experiment — Isolated Augmentation Experiment

Implementation only. **Not trained.** This folder was created to prepare (not run) a single isolated training experiment that changes only the augmentation strategy versus `v2-clean-baseline`, per `ml/v2_field_domain_planning/stage1_augmentation_experiment_plan.md` and your approval of 2 specific changes.

## What changed vs. the control (v2-clean-baseline)

Only two things:
1. **Vertical flip removed.**
2. **Brightness augmentation widened from 0.80–1.20× to 0.70–1.30×.**

Full diff: `config_diff_vs_v2_clean_baseline.md`. Everything else (architecture, optimizer, learning rates, epochs, fine-tuning depth, class weights, manifest/data split, shuffle seed, batch size) is reused unchanged via direct import of the same `ml/model.py`, `ml/class_weights.py`, `ml/dataset.py`, `ml/config.py` used by the control — none of those files were modified.

## Files in this folder

| File | Purpose |
|---|---|
| `augmentation_v2_field.py` | New augmentation layer (vertical flip removed, brightness widened). Does not touch `ml/augmentation.py`. |
| `dataset_v2_field_aug.py` | Dataset construction identical to `ml/dataset.py`'s, except it uses the new augmentation layer. Necessary because `ml/dataset.py` hard-imports the original augmentation module with no substitution point — reuses `ml/dataset.py`'s `_decode_image`/`_class_to_index`/`load_manifest` by direct import, does not duplicate them. |
| `train_v2_field_aug.py` | Training entry point. Reuses `ml/model.py` and `ml/class_weights.py` by direct import, unmodified. Writes to a new `ml/artifacts/v2-field-aug-experiment/` directory. **Not executed.** |
| `verify_experiment_setup.py` | Read-only verification script — confirms existing pipeline files are unchanged, Dataset 3 is unreferenced, output paths are new, the treatment reuses (not duplicates) control logic, and the manifest is byte-identical to the control's. All 5 checks pass (see "Verification results" below). |
| `config_diff_vs_v2_clean_baseline.md` | Full setting-by-setting diff against the control. |
| `README.md` | This file. |

## Exact command to launch training later (NOT run yet)

```
cd ml
.venv\Scripts\python.exe v2_field_aug_experiment\train_v2_field_aug.py --run-id v2-field-aug-experiment
```

This uses `ml/v2_baseline/proposed_clean_manifest.csv` by default (the same file the control was trained on — verified byte-identical by SHA-256), writes to `ml/artifacts/v2-field-aug-experiment/` (new, cannot collide with `v1-baseline/` or `v2-clean-baseline/`), and follows the exact same head-training-then-fine-tune procedure, callbacks, and logged artifacts (`model.keras`, `best_head.keras`, `best_fine_tuned.keras`, `class_weights.json`, `training_history.json`, `run_metadata.json`) as the control run.

Evaluation afterward (also NOT run yet) would follow the same pattern used for the control, against the same test split, using the existing unmodified `ml/evaluate.py`:

```
.venv\Scripts\python.exe evaluate.py --model-path artifacts\v2-field-aug-experiment\model.keras --run-id v2-field-aug-experiment --manifest-path v2_baseline\proposed_clean_manifest.csv --artifacts-dir artifacts
```

**Per the approved plan, no Dataset 3 evaluation happens automatically** — that would only happen once, on explicit request, after the internal test-set comparison is reviewed.

## Verification results (all checks re-run and passing)

| # | Check | Result |
|---|---|---|
| 1 | Existing pipeline files (`config.py`, `model.py`, `train.py`, `dataset.py`, `augmentation.py`, `class_weights.py`, `evaluate.py`, `prepare_dataset.py`) have zero changes | ✅ PASS — `git diff --stat` empty |
| 2 | Dataset 3 not referenced or accessible anywhere in this experiment's code | ✅ PASS — 0 matches for "dataset3", "cornguard_external_eval", "leaf_images", "maize_in_field", "Downloads" across `augmentation_v2_field.py`, `dataset_v2_field_aug.py`, `train_v2_field_aug.py` |
| 3 | Output path is new, cannot overwrite earlier artifacts | ✅ PASS — `ml/artifacts/v2-field-aug-experiment/` does not exist; existing `v1-baseline/` and `v2-clean-baseline/` untouched |
| 4 | Experiment changes only the intended augmentation behavior | ✅ PASS — `train_v2_field_aug.py` imports `build_model`, `compile_for_head_training`, `unfreeze_for_fine_tuning`, `compute_training_class_weights`, `load_manifest` directly from the unmodified control modules; none are locally redefined |
| 5 | Control and treatment use comparable (identical) data splits | ✅ PASS — manifest SHA-256 matches exactly between the control's manifest and this experiment's default manifest path |

No blockers. **Training has not been started.**
