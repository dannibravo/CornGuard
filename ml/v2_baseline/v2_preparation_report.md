# CORNGUARD v2 — Clean Baseline Preparation Report (Phase 1 + 2 only — NOT TRAINED)

No training was run. No model, APK, Android source, original dataset archive, or v1 training artifact was modified, moved, renamed, or overwritten. Everything below is new, in `ml/v2_baseline/`.

## What this prepares

A corrected `dataset_manifest.csv` that removes the 12 cross-split duplicate/near-duplicate leaks the v1 audit found (`ml/v1_training_audit/audit_report.md`, `duplicate_overlap_findings.csv`), while changing as little else as possible so a future v2-clean-baseline run stays comparable to v1.

## Phase 1 — investigation results

### All 12 pairs reviewed (`duplicate_review.csv`)

Every pair's source paths, split assignments, labels, and freshly-recomputed SHA-256 + perceptual hashes are recorded. Each pair was classified as one of:

- **SAME_LABEL_DUPLICATE (10 of 12 pairs, 20 images)** — both copies already carry the same class label; the only problem is that they're split across train/val/test. Safe to merge into a single split without touching any label.
- **LABEL_CONFLICT_UNRESOLVED (2 of 12 pairs, 4 images)** — the two copies of the same photograph carry *different* class labels.

### The exact duplicate: `Corn_Gray_Spot (564)` (test, `gray_leaf_spot`) vs. `Corn_Blight (1104)` (train, `northern_leaf_blight`)

Confirmed byte-identical (same SHA-256). **I visually inspected the image directly** (not the model's prediction on it, which was never consulted): it shows a single, well-defined, elongated tan-centered lesion with a dark reddish-brown border on an otherwise healthy leaf. This single-lesion presentation reads as more consistent with a Northern Leaf Blight lesion than with Gray Leaf Spot's typical pattern of multiple small, rectangular, vein-constrained lesions. **This is recorded as a visual observation for a qualified reviewer to weigh — it was not applied as a label correction.** The pair is excluded from the proposed clean manifest either way, pending that review.

### The second conflict: `Corn_Gray_Spot (29)` (test, `gray_leaf_spot`) vs. `Corn_Blight (106)` (train, `northern_leaf_blight`)

Not byte-identical, but perceptually identical (Hamming distance 0 — same photographic content, different file encoding/compression). The image is a wide field view of multiple corn plants with extensive tan/white blighted leaf patches, and is itself credited "© Gary Munkvold" in the frame. This large-scale blighted-field presentation also reads as more consistent with Northern Leaf Blight than Gray Leaf Spot. **Same caveat as above: an observation only, not a label change.** Also excluded pending review.

**Both conflicts involve the same two classes** (gray_leaf_spot / northern_leaf_blight) — the same pair this project's earlier external-evaluation work already flagged as the most visually confusable and label-uncertain classes in this dataset family.

## Phase 1 — proposed clean split logic

1. **LABEL_CONFLICT_UNRESOLVED (4 images)**: excluded entirely from the proposed clean manifest (not just from evaluation — from training too, since a confirmed-ambiguous label is unsound to train on as well). Listed in `held_out_unresolved_samples.csv`. **This goes slightly beyond your instruction #6** ("keep unresolved samples out of the evaluation split"), which I'm flagging as a decision for you to confirm or override — see "Decisions requiring your input" below.
2. **SAME_LABEL_DUPLICATE (10 pairs, 20 images)**: every one of the 10 pairs has exactly one member already in `train` and one member in `val` or `test` (confirmed — no val/test-vs-val/test pairs exist among the 10). Rule applied: **the whole pair is forced into `train`** — i.e. the non-train copy's split is changed to `train`. This is the conservative choice: it removes the duplicate from eval (protecting evaluation integrity) rather than removing data from training.
3. **Every other image (4,164 of 4,188) keeps its exact v1 split assignment, unchanged.** This is a targeted fix, not a re-split — chosen specifically to keep the v2 baseline maximally comparable to v1, per the goal of "preserving v1" comparability.

## Phase 1 — class stratification preserved, with reported trade-offs

| Class | v1 train/val/test | v2-clean train/val/test | Change |
|---|---|---|---|
| common_rust | 914 / 196 / 196 | 915 / 196 / **195** | -1 test (1 duplicate moved to train) |
| gray_leaf_spot | 401 / 86 / 87 | 401 / 86 / **85** | -2 test (2 unresolved images excluded) |
| northern_leaf_blight | 802 / 172 / 172 | **805** / **169** / **170** | -2 train (unresolved excluded), +5 train from merged duplicates (net: val -3, test -2, train +3 after exclusion) |
| healthy | 813 / 174 / 175 | **817** / **171** / **174** | +4 train, val -3, test -1 (duplicates merged to train) |
| **Total** | 2,930 / 628 / 630 (4,188) | **2,938 / 622 / 624 (4,184)** | -4 images total (excluded), 10 images moved train-ward |

Every class's clean-manifest split percentage stays within **0.4 points** of the intended 70/15/15 (full table: `split_class_counts.csv`) — stratification is well preserved despite the fix. The trade-off is a small reduction in eval-set size (val 628→622, test 630→624) in exchange for a verified-zero-leakage evaluation set.

## Leakage re-check (`dataset_leakage_check.csv`)

Re-ran the identical SHA-256 + perceptual-hash (Hamming ≤6) cross-split scan from the v1 audit, this time against the proposed clean manifest's new split assignments. **Result: 0 remaining cross-split overlaps** (file has a header row only). The fix is confirmed to work, not just assumed.

## Dataset 3 isolation (Task 7)

Grepped every file in this folder for "dataset3", "cornguard_external_eval", "leaf_images", "maize_in_field": the only match is this report's own sentence stating that no such reference exists. **No Dataset 3 path, file, or image is read, imported, or referenced anywhere in this preparation or in the proposed training pipeline.** Dataset 3 remains exclusively the frozen external evaluation set.

## Preprocessing and class order (Task 8)

The proposed clean manifest changes only the `split` column and drops 4 rows — `class_label`, `relative_path`, and every other column are untouched. Any v2 training run against this manifest would use the **exact same** `ml/model.py` (MobileNetV2, `(x/127.5)-1` preprocessing) and `ml/config.py` `CLASS_ORDER` (`common_rust, gray_leaf_spot, northern_leaf_blight, healthy`) already verified against the shipped `.tflite` model in the preflight audit — nothing about the model contract changes in this preparation.

## Decisions requiring your input before Phase 3 (training)

1. **Excluding the 4 unresolved-label images from training, not just evaluation** — I went beyond the letter of "keep them out of the evaluation split" to also keep them out of train, reasoning that a confirmed-ambiguous label shouldn't be trained on either. Confirm this is what you want, or tell me to keep them in train (with their original, possibly-wrong label) and only exclude them from val/test.
2. **Who resolves the 2 label conflicts, and how** — my visual read favors `northern_leaf_blight` for both, but I'm not a qualified plant pathologist and this project has consistently deferred exactly this kind of call to a human reviewer. These 4 images stay excluded until you decide.
3. **The merge-into-train rule** — forcing duplicate pairs into `train` rather than `val`/`test` was my choice to protect eval-set integrity at the cost of eval-set size. An alternative would be to simply drop one copy of each duplicate pair entirely (reducing train size slightly instead of growing it) — let me know if you'd prefer that instead.
4. **Everything in `v2_experiment_plan.md`'s pre-requisites** (from the prior audit) is still open — in particular, how/whether Dataset 3 is ever used for v2 training without losing it as a frozen test set. This preparation does not touch that question; it only fixes the v1 leakage.

## Phase 3 (training) — NOT executed, stopping here as instructed

If and when you approve, the baseline v2-clean training run would use `proposed_clean_manifest.csv` in place of `data/dataset_manifest.csv`, with no other code changes (run from the `ml/` directory, matching the v1 scripts' own documented usage):

```
cd ml
python train.py --run-id v2-clean-baseline --manifest-path v2_baseline/proposed_clean_manifest.csv --artifacts-dir artifacts
python evaluate.py --model-path artifacts/v2-clean-baseline/model.keras --run-id v2-clean-baseline --manifest-path v2_baseline/proposed_clean_manifest.csv --artifacts-dir artifacts
```

This would write a new `ml/artifacts/v2-clean-baseline/` directory — it would not touch `ml/artifacts/v1-baseline/` or any existing file. **I have not run these commands.** They're recorded here so the exact, reproducible command is on hand for when you approve Phase 3.

## Files in this folder

| File | Contents |
|---|---|
| `duplicate_review.csv` | All 12 pairs: paths, splits, labels, hashes, classification, visual notes, proposed action |
| `proposed_clean_manifest.csv` | 4,184-row manifest (v1 manifest minus the 4 excluded rows, with 10 rows' `split` corrected), plus a `split_changed_from_v1` column |
| `held_out_unresolved_samples.csv` | The 4 excluded images and why |
| `split_class_counts.csv` | Per-class train/val/test counts and percentages for the clean manifest |
| `dataset_leakage_check.csv` | Re-verification scan result (0 rows = confirmed clean) |
| `v2_preparation_report.md` | This report |
| `duplicate_review.py`, `build_clean_manifest.py`, `dataset_leakage_check.py` | Scripts run, for reproducibility |
