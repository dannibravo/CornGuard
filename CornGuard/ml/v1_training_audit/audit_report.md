# CORNGUARD v1 Training Pipeline Audit (Read-Only)

Audit only — no retraining, no inference, no changes to the model, APK, Android source, dataset, manifest, or evaluation outputs. All findings below are sourced directly from code and saved artifacts in the repository; where something could not be verified, it is labeled as unverified rather than assumed.

## Scope and sources inspected

- `ml/prepare_dataset.py` — manifest-building and split-assignment logic
- `ml/dataset.py` — `tf.data` pipeline, per-split dataset construction
- `ml/config.py` — all hyperparameters, split ratios, thresholds
- `ml/model.py` — architecture, preprocessing layer, compile settings
- `ml/train.py` — training loop, callbacks, class weights, artifact logging
- `ml/class_weights.py`, `ml/augmentation.py`
- `ml/evaluate.py` — test-set evaluation methodology
- `ml/data/dataset_manifest.csv` (4,188 rows — matches `CornDataset.zip`'s image count exactly)
- `ml/artifacts/v1-baseline/` — the actual saved run: `run_metadata.json`, `class_weights.json`, `training_history.json`, `evaluation_report.json`, `model_metadata_cornguard_mobilenetv2_v1.json`
- `ml/data/raw/<class>/public/*` — the actual image files (confirmed present and counts match the manifest exactly: 1306/574/1146/1162)

## 1. Exact per-class, per-split image counts (Task 2)

Computed directly from `dataset_manifest.csv` (see `split_class_counts.csv` for the full table):

| Class | train | val | test | total |
|---|---|---|---|---|
| common_rust | 914 | 196 | 196 | 1,306 |
| gray_leaf_spot | 401 | 86 | 87 | 574 |
| northern_leaf_blight | 802 | 172 | 172 | 1,146 |
| healthy | 813 | 174 | 175 | 1,162 |
| **Total** | **2,930** | **628** | **630** | **4,188** |

The test-split counts (196/87/172/175, total 630) exactly match the `support` values and `sample_count` recorded in `ml/artifacts/v1-baseline/evaluation_report.json` — direct confirmation that the saved evaluation report was computed on this exact manifest split, not a different or re-sampled set.

## 2. Split methodology — stratified, fixed seed (Task 3)

**Confirmed from code** (`prepare_dataset.py::_assign_splits`): the split is generated **per class**, using `sklearn.model_selection.train_test_split` called twice per class (first train vs. holdout, then holdout split into val/test) with `test_size` set from `config.VAL_SPLIT`/`config.TEST_SPLIT` (0.15/0.15) and `random_state=seed`.

- **Stratified by class: yes.** Every class's actual split percentages land within 0.2 points of the intended 70/15/15 (e.g. gray_leaf_spot: 69.86% / 14.98% / 15.16%) — see `split_class_counts.csv`.
- **Random seed fixed: yes.** `config.RANDOM_SEED = 42`, passed explicitly into both `train_test_split` calls via the `seed` parameter (sourced from `config.RANDOM_SEED` by `prepare_dataset.py`'s own CLI default). The split is reproducible given the same raw image set.
- **Caveat found in the code's own comments**: `config.py`'s module docstring states the 70/15/15 ratio was **not a frozen team decision** — it was a "pragmatic solo-context default" chosen to resolve a recorded "UNRESOLVED SOURCE CONFLICT" between a 70/15/15 spec and a separate "test set minimum 20%" spec. This is disclosed in the code itself, not an inference made here.

## 3. Split independence — NOT fully independent; a small, confirmed leak exists (Task 4, Task 6 — you explicitly asked not to assume independence)

A SHA-256 + perceptual-hash (pHash, Hamming distance ≤6 = near-duplicate, matching the threshold used throughout this project's external-evaluation work) scan was run across **all 4,188 training images**, using only already-installed tooling (`PIL`, `imagehash`, `numpy` in `cornguard_external_eval/venv` — no packages installed). Full results: `duplicate_overlap_findings.csv`.

**Confirmed findings:**
- **1 exact SHA-256 duplicate pair crosses splits** — and it is also a **cross-label duplicate**: `Corn_Gray_Spot (564).jpg` (test split, labeled `gray_leaf_spot`) is byte-for-byte identical to `Corn_Blight (1104).jpg` (train split, labeled `northern_leaf_blight`). The same physical photograph is present under two different class labels in two different splits. This is a genuine data-quality defect in the underlying public dataset, not an artifact of this audit's method.
- **11 near-duplicate pairs (Hamming ≤6) cross splits**, including one more cross-label gray_leaf_spot/northern_leaf_blight pair at Hamming distance 0 (`Corn_Gray_Spot (29).jpg` vs `Corn_Blight (106).jpg`) and several within-class near-duplicates (e.g. `Corn_Health (250).jpg`, test, is a Hamming-0 near-duplicate of `Corn_Health (453).jpg`, train).
- **Scale**: 12 overlapping pairs out of 4,188 images (~8.77 million possible pairs) is a very small fraction. This is real, confirmed test/train leakage — not hypothetical — but too small in absolute terms to be the primary explanation for an ~64-point accuracy gap between test and external evaluation on its own.
- **Pattern worth noting**: 8 of the 12 overlap rows involve `northern_leaf_blight`/`gray_leaf_spot`, the same two classes this project's earlier external-evaluation work (Dataset 1/Dataset 3 label review) already found to be the most visually confusable and label-uncertain pair.

## 4. Same plant / same capture session across splits (Task 5)

The manifest has **no plant ID, session ID, or capture-date column** — only `sample_id`, `original_filename`, `class_label`, `source_type` ("public" for all 4,188 rows), `source_repository_or_farm`/`source_barangay` (both blank, since no local images exist), and `split`. The split logic in `_assign_splits` operates purely at the individual-image level with **no grouping mechanism of any kind** — nothing in the code checks or prevents two images of the same physical plant/leaf/session from landing in different splits.

**This cannot be fully verified or ruled out from the manifest alone**, since there is no plant/session identifier to check against. The 12 overlap pairs found in §3 (same or near-identical images split across train/test/val) are **direct evidence that this failure mode does occur in practice** for at least a small number of images — consistent with what this project's earlier external-evaluation work already established about this public dataset family (e.g. the `augmented_Corn_Gray_Spot(N)` naming convention found during Dataset 1 screening, indicating the same base photos were mechanically duplicated/augmented and distributed under different filenames within this dataset lineage).

## 5. Training configuration (Task 6)

All values below are read directly from `config.py`, `model.py`, `train.py`, and confirmed against the actually-saved `ml/artifacts/v1-baseline/*.json` where applicable:

| Setting | Value | Source |
|---|---|---|
| Architecture | MobileNetV2, ImageNet-pretrained, `include_top=False` | `model.py` |
| Preprocessing | `(x / 127.5) - 1.0`, custom registered Keras layer | `model.py` — matches the exact MUL/SUB constants independently confirmed from the exported `.tflite` file in the preflight audit |
| Loss | `sparse_categorical_crossentropy` | `model.py` |
| Optimizer | Adam, lr=1e-3 (head phase), lr=1e-5 (fine-tune phase) | `config.py`, `model.py` |
| Epochs | up to 10 head + up to 10 fine-tune, `EarlyStopping(monitor="val_accuracy", patience=3, restore_best_weights=True)` | `config.py`, `train.py` |
| Fine-tuning | base model frozen entirely for head phase; layers 0–99 stay frozen, layers 100+ unfrozen for fine-tune phase | `config.py` (`FINE_TUNE_AT_LAYER=100`), `model.py` |
| Class weights | sklearn `compute_class_weight(class_weight="balanced")` computed from the **train split only** | `class_weights.py`; actually logged: common_rust 0.80, gray_leaf_spot 1.83, northern_leaf_blight 0.91, healthy 0.90 (`ml/artifacts/v1-baseline/class_weights.json`) |
| Augmentation | horizontal+vertical flip, ±15° rotation, ±10% zoom, brightness 80–120% | `config.py`, `augmentation.py` — **confirmed applied to the train split only** (`dataset.py`'s `build_split_dataset` only augments when `augment=True`, and `train.py` passes `augment=True` solely for `train_ds`; `val_ds`/`evaluate.py`'s `test_ds` both use `augment=False`) |
| Dropout | 0.2, before the final Dense/softmax layer | `model.py` |

No field-domain images were ever part of training: **100% of the 4,188 manifest rows have `source_type = "public"`**; zero rows have `source_type = "local"`, and no `local_image_metadata.csv` exists anywhere in the repo (the pipeline supports local/field images via `ml/data/raw/<class>/local/`, but that path was never populated for this run).

## 6. Source of the recorded 89.52% test accuracy / 87.41% macro F1 (Task 7)

**Confirmed, reproducible, and fully traced**: `ml/evaluate.py` loads the saved Keras model, builds the `test`-split dataset via `build_split_dataset(manifest, "test", ..., shuffle=False, augment=False)` (630 images, no augmentation — a genuine held-out evaluation, not a training-time metric), runs `sklearn.metrics.classification_report`, and writes the result to `ml/artifacts/v1-baseline/evaluation_report.json`. The per-class `support` values in that file (196, 87, 172, 175) match the test-split counts in `dataset_manifest.csv` exactly. This is the same methodology and the same recorded numbers already cited in the external-evaluation error-analysis report.

The project's own acceptance gates (`config.MIN_ACCURACY = 0.90`, `config.MIN_F1_MACRO = 0.88`) were **not met** by this result (89.52% < 90%, 87.41% < 88%), and `model_metadata_cornguard_mobilenetv2_v1.json` explicitly records `"meets_accuracy_threshold": false` — this was already a known shortfall on the model's own held-out test set, before any external field evaluation.

## 7. The "96.65%" figure (Task 8)

Re-searched the entire `ml/` directory (excluding dependency/.venv files) for "96.65" and "96.6" in `.py`, `.json`, and `.md` files: **zero matches.** No training/validation epoch in `training_history.json` reaches it either — the closest values are a peak training accuracy of 90.96% (head phase) / 90.72% (fine-tune phase) and a peak validation accuracy of 91.56% (head phase, epoch 5). **This figure remains unverified — it does not appear in any saved artifact in this repository**, consistent with the same conclusion already reached in the external-evaluation error-analysis report. It should not be cited as a real result.

## 8. Plausible explanations for the test-accuracy-to-field-accuracy gap (Task 9)

**Confirmed facts (established above, not inferred):**
- The model was trained, validated, and tested exclusively on "public"-sourced, non-field images — it never saw a single field-condition photograph (varied outdoor lighting, natural backgrounds, partial/overlapping leaves, insect damage, weathering) during training, validation, or its own held-out test evaluation.
- A small amount of genuine train/test leakage exists (12 of 4,188 images, including one exact cross-label duplicate) — real, but numerically too small to account for a 64-percentage-point gap on its own.
- The model's own held-out test accuracy (89.52%) already fell short of the project's 90% minimum gate, before any domain shift was introduced.
- The external-evaluation error analysis (separate report) found the model's confidence scores carry almost no information about correctness on field images (ECE = 0.52) and that it systematically over-predicts `common_rust`.

**Hypotheses (plausible, but not established by this audit or any prior report — presented as such, not as fact):**
- **Domain shift between "public"-style and field-condition photography** is the leading candidate explanation. The training images are from a public dataset lineage (previously traced to PlantVillage-derived sources in this project's earlier work) that tends toward controlled, relatively uniform framing/background/lighting; MobileNetV2's head was trained with the base frozen, and fine-tuning only unfroze the top ~55 of its ~155 layers — a transfer-learning setup that can learn features well-suited to the training distribution's specific visual conditions without necessarily generalizing to the very different conditions of outdoor field photography.
- **Label-taxonomy mismatch** between the external dataset's 5-disease labeling scheme (GLS/NCLB/PLS/CR/SR, per the earlier Dataset 3 investigation) and the model's 4 trained classes could contribute some error, though the external evaluation already excluded unmapped/multi-label/conflicting images specifically to control for this.
- **Geography/cultivar differences** between the training images' likely origin and the external field dataset's South African provenance may affect leaf appearance, lesion presentation, or background vegetation in ways the model never saw.
- **The systematic common_rust over-prediction bias** found in the external evaluation is not explained by this training-pipeline audit; it may relate to class-weight/loss interactions, color/texture cues common_rust shares with "difficult" or ambiguous field photos, or something else entirely — this remains an open question, not a confirmed cause.

This audit does not attempt to weight or rank these hypotheses quantitatively — doing so would require the controlled experiments proposed in `v2_experiment_plan.md`.

## 9. Limitations of this audit

- Near-duplicate detection used pHash at Hamming ≤6, the same threshold used elsewhere in this project; a different threshold could find a different (likely larger, noisier) overlap count. The one exact-SHA256 cross-label duplicate is unambiguous regardless of threshold choice.
- No plant/session ground truth exists for the public training images, so §4's "same plant/session across splits" question could only be answered indirectly via duplicate detection, not directly verified or ruled out.
- This audit is entirely static analysis of code and saved artifacts — it does not re-run training or inference, per the task's explicit constraints, so no new accuracy numbers were produced here.

## 10. Report files in this folder

| File | Contents |
|---|---|
| `audit_report.md` | This report |
| `split_class_counts.csv` | Exact train/val/test counts and percentages per class, from `dataset_manifest.csv` |
| `duplicate_overlap_findings.csv` | All 12 confirmed exact/near-duplicate pairs crossing splits |
| `v2_experiment_plan.md` | Proposed controlled v2 experiment plan (not executed) |
| `check_split_overlap.py`, `build_split_counts.py` | Scripts run for this audit, for reproducibility |
