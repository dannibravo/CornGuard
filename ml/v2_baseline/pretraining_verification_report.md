# CORNGUARD v2-clean-baseline — Pre-Training Verification Report

All checks below were re-run independently (not reused from the Phase 1/2 preparation outputs where a fresh check was feasible) immediately before this report. **Training has not been run.** No model, APK, Android source, original dataset archive, v1 artifact, or previous report was modified.

## Checklist results

| # | Check | Result |
|---|---|---|
| 1 | Recompute manifest/split/class counts fresh from `proposed_clean_manifest.csv` | ✅ PASS — matches Phase 1/2 report exactly (below) |
| 2 | All 4 excluded images absent from every split | ✅ PASS — 0 of 4 excluded sample IDs found in the clean manifest |
| 3 | Zero exact/near-duplicate overlaps across train/val/test | ✅ PASS — fresh full re-scan (4,184 images hashed again from scratch), 0 overlaps found |
| 4 | Repeated-weighting effect from the 10 reassigned duplicate pairs | ✅ CHECKED, quantified — see below. Minor, already covered by your approved decision #3. |
| 5 | Architecture/preprocessing/label order/training config match v1 | ✅ PASS — `git diff` against `config.py`, `model.py`, `train.py`, `dataset.py`, `class_weights.py`, `augmentation.py`, `evaluate.py` is empty; none were touched by this preparation |
| 6 | Training script reads only the clean manifest, cannot reach Dataset 3 | ✅ PASS — see below |
| 7 | v2 outputs go to a new, non-colliding run directory | ✅ PASS — `ml/artifacts/v2-clean-baseline/` does not currently exist |

**All 7 checks pass. No blockers.**

## 1. Fresh recomputation of split/class counts

| Class | train | val | test | total |
|---|---|---|---|---|
| common_rust | 915 | 196 | 195 | 1,306 |
| gray_leaf_spot | 401 | 86 | 85 | 572 |
| northern_leaf_blight | 805 | 169 | 170 | 1,144 |
| healthy | 817 | 171 | 174 | 1,162 |
| **Total** | **2,938** | **622** | **624** | **4,184** |

Identical to the Phase 1/2 report — confirms the manifest hasn't drifted.

## 2. Excluded-image absence check

The 4 held-out sample IDs (`Corn_Gray_Spot (29)`, `Corn_Gray_Spot (564)`, `Corn_Blight (106)`, `Corn_Blight (1104)`) were checked against every row of `proposed_clean_manifest.csv` by sample ID. **0 found present** — confirmed absent from train, val, and test alike, per your decision #1.

## 3. Fresh leakage re-scan

Re-ran the full SHA-256 + perceptual-hash (Hamming ≤6) cross-split scan from scratch against all 4,184 clean-manifest images (not reused from the cached Phase 2 result). **0 cross-split overlaps found.** Your decision #3's condition ("if all related copies remain in train and do not cross into validation or test") is verified true, not assumed.

## 4. Repeated-weighting effect inside train (quantified)

The 10 same-label duplicate pairs (20 images) are now co-located in `train`:

| Class | Pairs affected | Images affected |
|---|---|---|
| common_rust | 1 | 2 |
| northern_leaf_blight | 5 | 10 |
| healthy | 4 | 8 |
| gray_leaf_spot | 0 | 0 |

20 of 2,938 train images (0.68%) belong to a near-duplicate pair. All 10 pairs are near-duplicates (Hamming distance 2–6), **none are byte-identical** — confirmed from `duplicate_review.csv`'s `overlap_type` column. Effect: these pairs contribute a very slightly correlated gradient signal twice per epoch instead of once, and marginally nudge the `compute_class_weight("balanced")` calculation (which counts train-split images per class) for common_rust/northern_leaf_blight/healthy, not gray_leaf_spot. At under 1% of the training set and no exact duplicates involved, this is a minor, already-quantified side effect of the merge-to-train choice you approved in decision #3 — not a new concern, and not something this report treats as a blocker.

## 5. Architecture/preprocessing/config unchanged

`git diff --stat` against `config.py`, `model.py`, `train.py`, `dataset.py`, `class_weights.py`, `augmentation.py`, `evaluate.py` returns **empty** — none of these files have been touched since the last commit. This preparation only added new files under `ml/v1_training_audit/` and `ml/v2_baseline/`; it never edited the training pipeline itself. Architecture (MobileNetV2 transfer learning), preprocessing (`(x/127.5)-1`), class order (`common_rust, gray_leaf_spot, northern_leaf_blight, healthy`), loss/optimizer/epochs/early-stopping/class-weighting/augmentation are therefore **identical to v1 by construction**, not by assertion.

## 6. Training script cannot reach Dataset 3

- Every one of the 4,184 `relative_path` values in `proposed_clean_manifest.csv` starts with `data\raw\` (checked programmatically, 0 exceptions) — all point inside `ml/data/raw/`, nowhere near `cornguard_external_eval/` or `Downloads/`.
- `train.py`, `dataset.py`, `config.py` contain no hardcoded reference to "dataset3", "cornguard_external_eval", or "Downloads" anywhere (grepped, 0 matches).
- `train.py --manifest-path` defaults to `config.MANIFEST_PATH` (`data/dataset_manifest.csv`, the v1 manifest) but is explicitly overridden by `--manifest-path v2_baseline/proposed_clean_manifest.csv` in the command below — the clean manifest is used, not the default, and nothing in the code path can silently fall back to Dataset 3 since Dataset 3 is never referenced at all.

## 7. New, non-colliding output directory

`ml/artifacts/` currently contains only `v1-baseline/`. `ml/artifacts/v2-clean-baseline/` does not exist yet — `train.py` creates `artifacts_dir / run_id` itself (`run_dir.mkdir(parents=True, exist_ok=True)`), so running with `--run-id v2-clean-baseline` will create a fresh directory and cannot overwrite `v1-baseline/`.

## Final verified setup

- **Manifest**: `ml/v2_baseline/proposed_clean_manifest.csv` (4,184 images; 4 excluded pending label review; 0 cross-split duplicates)
- **Model/preprocessing/config**: identical to v1, unmodified
- **Dataset 3**: completely unreferenced, confirmed by path audit
- **Output location**: `ml/artifacts/v2-clean-baseline/` (new, does not exist yet)

## Exact training command (NOT yet run — awaiting your go-ahead)

```
cd ml
python train.py --run-id v2-clean-baseline --manifest-path v2_baseline/proposed_clean_manifest.csv --artifacts-dir artifacts
```

Followed, after training completes, by evaluation against the same clean manifest's held-out test split:

```
python evaluate.py --model-path artifacts/v2-clean-baseline/model.keras --run-id v2-clean-baseline --manifest-path v2_baseline/proposed_clean_manifest.csv --artifacts-dir artifacts
```

**I have not executed either command.** All 7 checks pass with no blockers — this report is the final checkpoint before training, per your instruction to stop and report before starting.
