# CORNGUARD v2-clean-baseline — Training & Evaluation Results

Trained and evaluated per your approval, using the exact verified command sequence from `pretraining_verification_report.md`. No change to any training-pipeline code, the v1 model, the APK, or Dataset 3. New artifacts only: `ml/artifacts/v2-clean-baseline/`.

## Commands actually run

```
cd ml
.venv/Scripts/python.exe train.py --run-id v2-clean-baseline --manifest-path v2_baseline/proposed_clean_manifest.csv --artifacts-dir artifacts
.venv/Scripts/python.exe evaluate.py --model-path artifacts/v2-clean-baseline/model.keras --run-id v2-clean-baseline --manifest-path v2_baseline/proposed_clean_manifest.csv --artifacts-dir artifacts
```

(Ran via the project's dedicated `ml/.venv` environment — already had TensorFlow/Keras/sklearn/pandas installed; nothing was installed for this run.)

## Result: both acceptance gates now met

| Metric | v1-baseline (leakage present) | v2-clean-baseline (leakage-free) | Gate | v1 | v2 |
|---|---|---|---|---|---|
| Accuracy | 89.52% | **91.99%** | ≥90% | ❌ FAIL | ✅ PASS |
| Macro F1 | 87.41% | **90.07%** | ≥88% | ❌ FAIL | ✅ PASS |
| Test sample count | 630 | 624 | — | — | — |

## Per-class comparison

| Class | v1 P / R / F1 | v2 P / R / F1 |
|---|---|---|
| common_rust | 0.881 / 0.980 / 0.928 | 0.950 / 0.974 / **0.962** |
| gray_leaf_spot | 0.805 / 0.713 / 0.756 | 0.773 / 0.800 / **0.786** |
| northern_leaf_blight | 0.861 / 0.791 / 0.824 | 0.893 / 0.835 / **0.863** |
| healthy | 0.983 / 0.994 / 0.989 | 0.983 / 1.000 / **0.991** |

Every class's F1 improved. gray_leaf_spot's precision dipped slightly (0.805→0.773) while its recall rose more (0.713→0.800), for a net F1 gain.

### Confusion matrix (v2-clean-baseline, rows=true, cols=predicted, order: common_rust, gray_leaf_spot, northern_leaf_blight, healthy)

```
            common_rust  gray_leaf_spot  northern_leaf_blight  healthy
common_rust      190            2                2                1
gray_leaf_spot     2           68               15                0
northern_blight    8           18              142                2
healthy             0            0                0              174
```

## Why did accuracy go *up*, not down, after removing leakage? (hypothesis, not confirmed)

This is worth flagging rather than taking at face value. Removing leakage could in principle lower measured accuracy (if the model had effectively "seen" a near-copy of a test image during training). Instead it rose. Plausible, non-exhaustive explanations:

- **The 2 removed gray_leaf_spot test images may have been genuinely mislabeled.** Both excluded label-conflict images were in the test split and — per the visual review in the preparation phase — looked more consistent with northern_leaf_blight than gray_leaf_spot. If they were in fact mislabeled, they would have dragged down v1's measured gray_leaf_spot test performance regardless of model quality; removing them makes the remaining, more reliably-labeled gray_leaf_spot test images easier to score well on.
- **Train set grew slightly** (2,930 → 2,938 images, net +8) from merging duplicate pairs into train, which could offer a small additional generalization benefit.
- **Ordinary run-to-run variance.** This is a freshly-initialized and freshly-trained model, not a deterministic re-run of v1 — some of the difference is expected noise from random weight initialization and batch ordering, not necessarily attributable to the leakage fix alone.

This result does not let us cleanly isolate "how much of v1's 89.52% was leakage-inflated" — that would need a controlled ablation (e.g. evaluating v1's own model against this clean test set, or training multiple seeds) which was not part of this run.

## What this result does NOT show

**This evaluation used zero field-domain images** — same limitation as v1 (100% of `proposed_clean_manifest.csv` is still `source_type="public"`). It says nothing about whether the external Dataset 3 field-accuracy gap (25.3%, separately measured) has improved, narrowed, or changed at all. Dataset 3 was not referenced anywhere in this training or evaluation run (confirmed in the pre-training checks). **Closing the field-domain gap remains exactly the open problem described in `v2_experiment_plan.md` (Run B and beyond) — this run only addresses the leakage finding from the v1 audit, nothing more.**

## Files updated in this folder

| File | Contents |
|---|---|
| `train_run_log.txt` | Full training console output (head phase + fine-tune phase) |
| `evaluate_run_log.txt` | Evaluation console output |
| `v2_clean_baseline_results.md` | This report |

New artifacts (outside `v2_baseline/`, in a new non-colliding directory as verified): `ml/artifacts/v2-clean-baseline/model.keras`, `best_head.keras`, `best_fine_tuned.keras`, `class_weights.json`, `training_history.json`, `run_metadata.json`, `evaluation_report.json`. `ml/artifacts/v1-baseline/` is confirmed unchanged (file hash comparison performed).
