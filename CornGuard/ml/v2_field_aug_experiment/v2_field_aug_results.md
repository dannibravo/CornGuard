# v2-field-aug-experiment — Training & Evaluation Results

Trained and evaluated using the corrected, verified setup (`final_pretraining_review.md`). No existing pipeline file, the v1 model, the APK, `v2-clean-baseline`'s artifacts, or any prior report was modified. **Dataset 3 was not accessed at any point in this run.**

## Commands run

```
cd ml
.venv\Scripts\python.exe v2_field_aug_experiment\train_v2_field_aug.py --run-id v2-field-aug-experiment
.venv\Scripts\python.exe evaluate.py --model-path artifacts\v2-field-aug-experiment\model.keras --run-id v2-field-aug-experiment --manifest-path v2_baseline\proposed_clean_manifest.csv --artifacts-dir artifacts
```

## Result: both gates still pass, but slightly below the control on the internal test set

| Metric | v2-clean-baseline (control) | v2-field-aug-experiment (treatment) | Gate |
|---|---|---|---|
| Accuracy | 91.99% | **91.67%** | ≥90% — both ✅ |
| Macro F1 | 90.07% | **89.23%** | ≥88% — both ✅ |
| Test sample count | 624 | 624 (same split) | — |

## Per-class comparison

| Class | Control P / R / F1 | Treatment P / R / F1 |
|---|---|---|
| common_rust | 0.950 / 0.974 / 0.962 | 0.951 / 0.990 / **0.970** |
| gray_leaf_spot | 0.773 / 0.800 / 0.786 | 0.759 / 0.741 / **0.750** |
| northern_leaf_blight | 0.893 / 0.835 / 0.863 | 0.882 / 0.835 / **0.858** |
| healthy | 0.983 / 1.000 / 0.991 | 0.983 / 1.000 / **0.991** |

common_rust improved slightly; gray_leaf_spot and northern_leaf_blight both dipped slightly; healthy unchanged.

### Confusion matrix (treatment, rows=true, cols=predicted, order: common_rust, gray_leaf_spot, northern_leaf_blight, healthy)

```
                 common_rust  gray_leaf_spot  northern_leaf_blight  healthy
common_rust          193            1                0                1
gray_leaf_spot         3           63               19                0
northern_blight        7           19              142                2
healthy                 0            0                0              174
```

## Interpretation — read carefully, do not over-interpret

**This result says nothing about the field-domain gap.** Both this run and the control were trained and evaluated exclusively on the same public-style manifest; Dataset 3 was not touched. The augmentation change (removing vertical flip, widening brightness to 0.70–1.30×) produced a small, mixed effect on the *internal, same-distribution* test set — a slight net decrease in headline accuracy/macro F1, with class-level results moving in different directions. This is consistent with the augmentation change being a small-magnitude, directionally mixed effect on in-distribution data, as might be expected — this experiment was never expected to improve in-distribution test accuracy; it was designed to test whether more field-realistic augmentation helps *field* performance, which remains unmeasured.

**On reproducibility**: per the correction applied in `final_pretraining_review.md` §7, this run shares the control's randomness regime (no global seed set in either), so the ~0.3–0.8 point differences above are not fully attributable to the augmentation change alone — ordinary run-to-run training noise (weight init, dropout, shuffle order, augmentation draws — none seeded in either run) is a plausible contributor to part of this gap, not just the augmentation change itself. This experiment's design (single run each, no repeated trials) cannot separate "real effect of the augmentation change" from "ordinary training variance" with only one run per condition — that would require multiple seeded repeats, which was not part of the approved scope.

## What would be needed to assess the actual hypothesis (field-domain generalization)

Per `field_domain_gap_plan.md` §E and the Stage 1 plan's own "candidate selection" section: **a single, explicitly-requested, logged Dataset 3 evaluation of this checkpoint** is the next step needed to learn whether the augmentation change helped real field performance — the only thing this internal-test-set result establishes is that the change didn't break in-distribution performance below the acceptance gates. **I have not run that evaluation.** It requires your explicit request, consistent with the rationed-evaluation-log process already agreed.

## Files in this folder

| File | Contents |
|---|---|
| `train_run_log.txt` | Full training console output |
| `evaluate_run_log.txt` | Evaluation console output |
| `v2_field_aug_results.md` | This report |

New artifacts (in a new, non-colliding directory, verified): `ml/artifacts/v2-field-aug-experiment/model.keras`, `best_head.keras`, `best_fine_tuned.keras`, `class_weights.json`, `training_history.json`, `run_metadata.json`, `evaluation_report.json`. `v1-baseline/` and `v2-clean-baseline/` confirmed unchanged (MD5 comparison).
