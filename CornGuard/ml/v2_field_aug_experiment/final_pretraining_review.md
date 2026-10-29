# Final Pre-Training Review — v2-field-aug-experiment

Read-only review. No training run, no Dataset 3 access. Every file quoted below was re-read fresh for this review, not recalled from earlier summaries.

## Finding: an unintended difference existed — see §2. Corrected on 2026-10-30; see §7 for the correction, the exact diff, and what remains a documented limitation.

---

## 1. Random-seed settings and call order — control vs. treatment (exact code evidence)

Exhaustive grep for "seed" across the entire dependency chain of both runs (`train.py`, `train_v2_field_aug.py`, `dataset.py`, `dataset_v2_field_aug.py`, `model.py`, `class_weights.py`, `augmentation.py`, `augmentation_v2_field.py`, `config.py`) found exactly two distinct seed-related statements in the whole codebase:

**Shared by both (identical in both files):**
```python
# dataset.py line 56 (control) / dataset_v2_field_aug.py line 48 (treatment) -- textually identical
ds = ds.shuffle(buffer_size=len(paths), seed=config.RANDOM_SEED, reshuffle_each_iteration=True)
```

**Present ONLY in the treatment:**
```python
# train_v2_field_aug.py line 56, the FIRST statement inside train(), before load_manifest(),
# before any dataset construction, before build_model()
tf.keras.utils.set_random_seed(config.RANDOM_SEED)
```

**`train.py` (the control) contains no call to `tf.keras.utils.set_random_seed`, `tf.random.set_seed`, `np.random.seed`, or `random.seed` anywhere.** Nor does `model.py`, `class_weights.py`, `augmentation.py`, or `augmentation_v2_field.py` — confirmed by the same grep, zero matches in any of those four files. The only seed-aware code either run ever executes is the single `ds.shuffle(..., seed=config.RANDOM_SEED, ...)` line, plus — in the treatment only — the added global-seed call.

**Call order in the treatment**: the global seed call happens *before* everything else in `train()`, including before the dataset-construction call that contains the (identical-looking) op-level shuffle seed.

## 2. Does the added seed call change initialization, shuffling, augmentation, or other randomness relative to the control? — YES, in four ways

`tf.keras.utils.set_random_seed()` (Keras's own utility) sets Python's `random` module seed, NumPy's global seed, and TensorFlow's **graph-level** seed (via `tf.random.set_seed()`) all at once. This is a different, broader thing than the **op-level** `seed=` argument passed to `ds.shuffle()`.

Per TensorFlow's own documented behavior for how op-level and graph-level seeds combine (`tf.random.set_seed`'s documentation, which `tf.data.Dataset.shuffle`'s own `seed` parameter explicitly defers to): when only an op-level seed is set (the control's situation — no graph-level seed ever set), the actual random sequence is derived from that op-level seed combined with TensorFlow's *default* graph-level seed state. When both are explicitly set (the treatment's situation, since `set_random_seed(42)` sets the graph-level seed to 42 before the shuffle call runs), the combination — and therefore the resulting sequence — is different from the control's, **even though the literal text `seed=config.RANDOM_SEED` (42) appears identically in both files.**

Concretely, four things are affected by this difference, not just one:

1. **Dataset shuffle order** (`train_ds`'s `.shuffle(...)` call): per the above, the actual training-image presentation order is not guaranteed to match between control and treatment, despite both specifying the same op-level seed value.
2. **Weight initialization**: the model's final `Dense(num_classes, activation="softmax")` layer (`model.py`) uses Keras's default kernel initializer (`glorot_uniform`), which draws from the global RNG. MobileNetV2's convolutional weights themselves are loaded from fixed pretrained ImageNet weights (not randomly initialized, so this specific concern doesn't apply to them) — but the new head's Dense layer's random initial weights differ between a run where the global seed is set (treatment) and one where it isn't (control).
3. **Dropout randomness**: `model.py`'s `Dropout(0.2)` layer draws a random mask on every training forward pass from the global RNG — seeded in treatment, not in control.
4. **Augmentation randomness — including the two UNCHANGED transforms**: neither `augmentation.py` nor `augmentation_v2_field.py` passes an explicit per-layer `seed=` to any of its `RandomFlip`/`RandomRotation`/`RandomZoom`/`RandomBrightness` layers (confirmed by the same grep — zero `seed` references in either file). All four layers therefore draw from the global RNG. This means that even **rotation and zoom — the two transforms you specified should remain unchanged** — will draw literally different random angle/zoom values per image between the control and treatment runs, because the global RNG state they're drawing from differs. The *distribution* (±15°, ±10%) is unchanged; the *specific per-image draws* are not reproducible between the two runs.

## 3. Can it be confirmed that the only intended treatment differences are vertical-flip removal and brightness widening? — NOT FULLY

**The augmentation transform types and parameter ranges differ only in the two approved ways** — confirmed in §4 below. **But the overall training randomness regime differs in additional respects beyond those two approved changes**, as detailed in §2: weight initialization, dropout masking, data shuffle order, and the literal (not distributional) random draws for rotation and zoom are all governed by a different global random state in the treatment than in the control, as a side effect of the added `set_random_seed()` call — not as a side effect of the augmentation change itself.

**This is the unintended difference.** It was introduced by me in the prior step as a documented "reproducibility aid," with the stated rationale "it does not change what the control run did" — that statement is true only in the narrow sense that it doesn't retroactively alter the control run's already-saved results. It is misleading as a claim that treatment vs. control is a *clean, single-variable* comparison, which was the explicit goal of this isolated experiment. **I am reporting this and stopping, per your instruction, rather than editing `train_v2_field_aug.py` to fix it.**

## 4. Confirmed unchanged vs. control: rotation/zoom type, architecture, pretrained weights, optimizer, learning rates, class weights, batch size, epoch limits, early stopping, manifest, split assignments

All confirmed by direct code comparison (fresh re-read of both files side by side) and by `verify_experiment_setup.py`'s check 4 (import-based reuse, nothing redefined locally):

| Item | Evidence |
|---|---|
| Rotation transform type/range | Both read `config.AUGMENTATION["rotation_degrees"]` (15) — same source, same value |
| Zoom transform type/range | Both read `config.AUGMENTATION["zoom_percent"]` (0.10) — same source, same value |
| Architecture | Both call `build_model()`, imported unmodified from `model.py` |
| Pretrained weights | `build_model()` itself (unmodified) loads `MobileNetV2(weights="imagenet", ...)` — identical in both, since it's the same function |
| Optimizer / learning rates | Both call `compile_for_head_training(model)` and `unfreeze_for_fine_tuning(model, base_model)`, imported unmodified from `model.py` — identical Adam/1e-3/1e-5 configuration |
| Class weights | Both call `compute_training_class_weights(manifest)`, imported unmodified from `class_weights.py`, against the same manifest file (SHA-256 confirmed identical, §6) |
| Batch size | Both use `config.BATCH_SIZE` (32), same imported `config.py` |
| Epoch limits | Both use `config.HEAD_EPOCHS` (10) / `config.FINE_TUNE_EPOCHS` (10) |
| Early stopping | Both construct `EarlyStopping(monitor="val_accuracy", patience=3, restore_best_weights=True)` — identical parameters, line-for-line matching code in both files |
| Manifest | Both default to `ml/v2_baseline/proposed_clean_manifest.csv` — SHA-256 verified byte-identical (§6) |
| Train/val/test assignments | Same manifest file, same `split` column — assignments are identical by construction, not just by intent |

## 5. Validation and test images receive no augmentation — CONFIRMED

```python
# train_v2_field_aug.py line 61
val_ds = build_split_dataset_field_aug(manifest, "val", dataset_root, shuffle=False, augment=False)
```
`augment=False` explicitly. `dataset_v2_field_aug.py`'s `build_split_dataset_field_aug` only calls the new augmentation layer when `augment=True` (same `if augment:` gate as the original `dataset.py`, lines 63-68 vs. 58-62) — confirmed by re-reading both functions side by side.

**Test images are not touched by any file in this experiment directory at all.** `train_v2_field_aug.py` only constructs `train_ds` and `val_ds`; test-split evaluation would use the existing, unmodified `ml/evaluate.py`, which builds its test dataset via the original `ml/dataset.py::build_split_dataset` with `augment=False` — the new augmentation module is never imported or reachable from the evaluation path at all.

## 6. Re-run of `verify_experiment_setup.py` — all 5 checks still pass

| # | Check | Result |
|---|---|---|
| 1 | Existing pipeline files unchanged | ✅ PASS — `git diff --stat` empty |
| 2 | Dataset 3 unreferenced | ✅ PASS — 0 matches |
| 3 | Output path new, no collision | ✅ PASS — `ml/artifacts/v2-field-aug-experiment/` still does not exist; `v1-baseline/` and `v2-clean-baseline/` untouched |
| 4 | Only augmentation behavior changed (structural) | ✅ PASS — all shared logic imported, nothing redefined |
| 5 | Manifest identical to control | ✅ PASS — SHA-256 `449df19a5fc7...159da3ec0` matches on both sides |

No existing pipeline file or artifact was altered by this review.

## 7. Correction applied (approved 2026-10-30) — exact diff and verification

**Change made, narrowly scoped to `ml/v2_field_aug_experiment/train_v2_field_aug.py` only:**

```diff
 def train(run_id: str, manifest_path: Path, dataset_root: Path, artifacts_dir: Path) -> Path:
-    # Intentional addition vs. ml/train.py -- makes THIS run's weight init reproducible. Does not
-    # alter anything about the already-completed v2-clean-baseline control run.
-    tf.keras.utils.set_random_seed(config.RANDOM_SEED)
-
     manifest = load_manifest(manifest_path)
```

```diff
-  3. tf.keras.utils.set_random_seed(config.RANDOM_SEED) is called explicitly at startup, which
-     ml/train.py does NOT do -- an intentional addition for this experiment's own reproducibility,
-     documented in config_diff_vs_v2_clean_baseline.md. It does not change what the control run did.
-  4. The manifest path argument defaults to ml/v2_baseline/proposed_clean_manifest.csv -- the
+  3. The manifest path argument defaults to ml/v2_baseline/proposed_clean_manifest.csv -- the
```

```diff
             "zoom_percent": "unchanged, read from config.AUGMENTATION",
         },
-        "random_seed_explicitly_set": config.RANDOM_SEED,
     }
```

The second and third hunks (docstring renumbering, removing the now-stale `run_metadata.json` field) were made alongside the code removal because leaving them would have made the file *describe* a seed call that no longer exists — not because any additional seed-related behavior changed. No other line in the file was touched; `tf.keras.utils.set_random_seed` does not appear anywhere in the file or anywhere else in this experiment directory.

**Verified, not assumed:**
- `grep -n "seed" train_v2_field_aug.py` now returns exactly one hit — the plain-English phrase "random seed" inside the docstring's list of hyperparameters reused from `config.py`, which remains accurate (the existing `config.RANDOM_SEED` is still correctly reused by `ds.shuffle()` inside `dataset_v2_field_aug.py`, untouched by this correction).
- `verify_experiment_setup.py` re-run after the edit: **all 5 checks still pass** (existing pipeline files unchanged; Dataset 3 unreferenced; output path still doesn't exist; only augmentation logic differs structurally; manifest SHA-256 `449df19a5fc7...159da3ec0` still identical to the control's).
- **Manifest and split assignments**: unchanged by this correction — same file, same SHA-256, confirmed again.
- **The two intended augmentation differences remain exactly as before**: `augmentation_v2_field.py` was not touched by this correction (file untouched since its original creation, confirmed by timestamp and content check) — `VERTICAL_FLIP = False` and `BRIGHTNESS_RANGE = (0.70, 1.30)` are unchanged.
- **Rotation, zoom, preprocessing, model configuration, and validation/test augmentation behavior**: all unchanged — none of this correction's edits touched `dataset_v2_field_aug.py`, `model.py`, or the `val_ds = build_split_dataset_field_aug(..., augment=False)` line.
- **No existing pipeline file, the v1 model, the APK, or previous experiment artifacts were modified**: `git diff --stat` on all 8 existing pipeline files remains empty; MD5 of `ml/artifacts/v1-baseline/model.keras`, `ml/artifacts/v2-clean-baseline/model.keras`, and `android/app/src/main/assets/model.tflite` are identical to before this correction; `augmentation_v2_field.py`, `dataset_v2_field_aug.py`, `config_diff_vs_v2_clean_baseline.md`, and `README.md` in this experiment directory were not touched by this correction.

### What this correction does and does not establish

Removing the added seed call restores the treatment run to the **same randomness regime as the control**: both now rely solely on the single op-level `seed=config.RANDOM_SEED` passed to `ds.shuffle()`, with no global/graph-level seed set by either, and no per-layer seed on any augmentation layer in either module. This removes the specific confound identified in §2 (weight init, dropout, shuffle order, and rotation/zoom draws no longer diverge from the control *for this reason*).

**This does not make the treatment run "fully deterministic," and I am not claiming that.** Neither the control nor the treatment sets a global seed, so neither run is reproducible against itself run-to-run — this was already true of `v1-baseline` and `v2-clean-baseline` before this experiment existed, and remains true now. What this correction achieves is narrower and specific: it removes the one respect in which the treatment's non-determinism differed in *kind* from the control's, so that any measured difference between the two runs' results is attributable to the two approved augmentation changes and ordinary shared run-to-run noise — not to an added, asymmetric source of randomness divergence that affected even the unchanged rotation/zoom transforms.

**I have not trained anything, evaluated Dataset 3, or modified the Android app.** Stopping here as instructed.
