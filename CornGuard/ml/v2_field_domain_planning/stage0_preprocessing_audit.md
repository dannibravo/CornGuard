# CORNGUARD Stage 0 — Preprocessing, Label, and Split Audit (Read-Only)

Audit only. No file was modified — `ml/dataset.py`, `ml/model.py`, `ml/augmentation.py`, `ml/config.py`, `ml/train.py`, `ml/evaluate.py` were read, not edited. No training, inference, or Dataset 3 access occurred. Only new files are in `ml/v2_field_domain_planning/`.

## 1–2. Exact preprocessing code, decoding, channels, resize, normalization, class order

Traced directly from `ml/dataset.py` and `ml/model.py`, the same code path used for both training and evaluation (`evaluate.py` calls the identical `build_split_dataset`/`_decode_image` functions — there is only one preprocessing implementation in this codebase, not a separate train vs. eval path).

**Decoding (`dataset.py::_decode_image`, lines 22-26):**
```python
raw = tf.io.read_file(path)
image = tf.image.decode_jpeg(raw, channels=config.IMAGE_CHANNELS)   # channels=3
image = tf.image.resize(image, config.IMAGE_SIZE)                    # (224, 224)
```
- `tf.image.decode_jpeg(..., channels=3)` forces 3-channel RGB output regardless of the source JPEG's actual encoding (grayscale, CMYK, etc. are all converted to RGB by the decoder) — channel handling is explicit and correct, not left to source-file variation.
- `tf.image.resize` with no explicit `method=` argument uses TensorFlow's default, **bilinear interpolation**. This is a confirmed fact about TF's API default, not inferred from this codebase. It matches the interpolation method (`Image.BILINEAR`) used throughout this project's own evaluation/sanity-check scripts (`sanity_check.py`, `run_evaluation.py`) for consistency, though the two are different library implementations (TensorFlow's vs. Pillow's bilinear) — both are bilinear in method, not necessarily bit-identical in output. This has not been found to matter in practice (the model performed as expected on training-distribution sanity checks earlier in this project), but is noted as a minor, unquantified source of numerical difference between the Python training/eval pipeline and the Android app's own `Bitmap.createScaledBitmap` resize.
- `tf.image.decode_jpeg` returns `uint8`; `tf.image.resize` converts to `float32` internally but **does not rescale the value range** — the output of `_decode_image` is float32 pixels still in **[0, 255]**, not [0,1] or [-1,1].

**Normalization (`model.py`, lines 12-25, `MobileNetV2Preprocess` layer):**
```python
def call(self, inputs):
    return (inputs / 127.5) - 1.0
```
This layer is the **first layer inside the model itself** (`build_model()`, line 48: `x = build_preprocessing_layer()(inputs)`), not a step in `dataset.py`. Confirmed: `dataset.py` never normalizes; the raw [0,255] float output of `_decode_image` is fed straight to the model, which performs `(x/127.5)-1` as its own first op. **This is architecturally identical to the shipped Android `.tflite` model**, which was independently confirmed in the preflight audit to have the exact same transform baked into its graph (`MUL` constant 0.00784314 = 1/127.5, `SUB` constant 1.0). The Python training/eval pipeline and the Android app apply normalization in the same place, the same way, once each — no double-normalization risk in either path.

**Class-index order (`dataset.py::_class_to_index`, `config.py::CLASS_ORDER`):**
```python
CLASS_ORDER = ["common_rust", "gray_leaf_spot", "northern_leaf_blight", "healthy"]
```
Verified programmatically against the actual clean manifest (`stage0_label_consistency_check.py`, see §3) — this order is what both training and evaluation use to turn `class_label` strings into integer targets. It matches `labels.json`'s index order already confirmed against the shipped model in the preflight audit.

## 3. Label verification: manifest vs. folder names vs. loader mapping

Ran a direct, programmatic check (`stage0_label_consistency_check.py`) over all 4,184 rows of `ml/v2_baseline/proposed_clean_manifest.csv`:

- **Rows where `class_label` disagrees with the folder segment of its own `relative_path`** (i.e. `data\raw\<folder>\public\...`): **0 of 4,184.**
- **Rows whose `class_label` is not one of the 4 valid `config.CLASS_ORDER` values**: **0 of 4,184.**
- Class counts present: common_rust 1,306, healthy 1,162, northern_leaf_blight 1,144, gray_leaf_spot 572 — consistent with every prior count in this project.

**No label/folder/loader inconsistency found.** This check only verifies internal consistency (does the manifest agree with itself and the folder layout) — it does not re-verify whether the *original* public dataset's labels are botanically correct, which is a separate question already partially addressed by the v1 audit's discovery of the 2 cross-label duplicate pairs (excluded in the clean manifest, still unresolved pending qualified review).

## 4. Augmentation appropriateness review (inspection only — `augmentation.py` NOT modified)

Current pipeline (`augmentation.py`, driven by `config.AUGMENTATION`): horizontal + vertical flip, rotation ±15°, zoom ±10%, brightness 80–120%. Assessed for corn-leaf appropriateness:

| Transform | Assessment |
|---|---|
| Horizontal flip | **Appropriate.** A corn leaf has no inherent left-right asymmetry; flipping does not change disease identity or create an unrealistic leaf. |
| Vertical flip | **Worth reconsidering — flagged as a hypothesis, not a confirmed defect.** A corn leaf has a real base-to-tip axis (attached at the stalk, tapering to a tip), and classic lesion morphologies in this domain run lengthwise along that axis (Northern Leaf Blight's "cigar-shaped" lesions, Gray Leaf Spot's vein-constrained rectangular lesions — both described in this project's own earlier visual reviews). Vertically flipping the image doesn't change the lesion *shape*, but it does invert the leaf's natural orientation relative to gravity/growth direction, which real field photographs would essentially never show. This may be training the model on leaf orientations it will never actually see, diluting useful signal rather than adding helpful invariance. This is a candidate change for the Stage 1 plan, not applied here.
| Rotation ±15° | **Appropriate.** Small-angle rotation plausibly simulates natural hand-held camera angle variation without producing an unrealistic leaf pose. |
| Zoom ±10% | **Appropriate.** Simulates reasonable camera-distance variation. |
| Brightness 80–120% | **Directionally appropriate but likely too narrow for field conditions.** A ±20% brightness range is a modest simulation of indoor/controlled lighting variation. The external Dataset 3 evaluation's error analysis found severe miscalibration and no evidence the model handles field lighting well; field photography (direct sun, shade, overcast, golden-hour, flash) plausibly spans a much wider dynamic range than ±20%. This is the clearest, lowest-risk candidate for a Stage 1 change.

**No new transform was implemented or selected here — that is explicitly Stage 1's job.** This section only evaluates the existing four against corn-leaf-specific plausibility, to inform that plan.

## 5. Split integrity and leakage-resistant model selection — the old test set must NOT be described as untouched

**The v1 630-image original test set is NOT clean.** The v1 audit (`ml/v1_training_audit/audit_report.md` §3) found 12 confirmed cross-split duplicate/near-duplicate pairs touching it, including 2 cross-label conflicts. **It must not be described as "untouched" or leakage-free in any report going forward** — that description would be false.

**The v2-clean manifest's test split (624 images, `ml/v2_baseline/proposed_clean_manifest.csv`) has been verified against the specific leakage this project knows how to detect** — SHA-256 exact match + perceptual-hash near-duplicate (Hamming ≤6) — **twice independently**, with 0 remaining cross-split overlaps both times (`ml/v2_baseline/dataset_leakage_check.csv`, and the fresh re-run in `pretraining_verification_report.md` §3).

**This is not the same as a provable guarantee of zero leakage, and should not be stated as one.** Two specific, honest limitations:

1. **No plant/session/capture-date ground truth exists** for this public dataset (`audit_report.md` §4 already noted this). The split is purely image-level stratified random assignment. If the same physical plant or leaf was photographed multiple times under conditions that produce a perceptual hash *outside* the Hamming ≤6 threshold (different angle, heavier crop, different lighting, or an algorithmic augmentation applied before the dataset was published), our detection method would miss it entirely, and such images could still be split across train/val/test undetected.
2. **The validation split (622 images) is reused across experiment generations** — it already drove checkpoint selection for both v1 (`val_accuracy` monitored by `ModelCheckpoint`/`EarlyStopping` in `train.py`) and v2-clean-baseline, and would be reused again for any Stage 1 variant's model selection. This is normal, expected ML practice (validation sets are *meant* to be reused within a project, unlike a locked external test set), but it means validation-driven decisions across v1/v2/Stage 1 are not fully independent of each other — worth naming explicitly rather than leaving implicit.

**Conclusion for Stage 1 planning**: the current split *can* support leakage-resistant model selection with respect to the specific leakage pattern already found and fixed, but this is a confirmed-detection-method-dependent result, not an absolute one. Stage 1's experiment plan should continue using this same manifest (no new split needed) while carrying this caveat forward honestly.

## Summary table

| Item | Finding |
|---|---|
| Decoding/channels | `decode_jpeg(channels=3)` — forces RGB, confirmed correct |
| Resize/interpolation | `tf.image.resize`, default bilinear — confirmed, minor library-vs-library difference from Android noted, not found to matter |
| Pixel range into the model | [0, 255] float32, unnormalized by `dataset.py` — confirmed |
| Normalization | `(x/127.5)-1`, inside the model as its first layer — confirmed identical to the shipped `.tflite` graph |
| Class-index order | `common_rust, gray_leaf_spot, northern_leaf_blight, healthy` — confirmed against manifest and prior `labels.json` verification |
| Label/folder/manifest consistency | 0 mismatches across all 4,184 rows — confirmed |
| Augmentation appropriateness | Horizontal flip, rotation, zoom: appropriate. Vertical flip: questionable for this domain, flagged for Stage 1. Brightness range: likely too narrow for field conditions, flagged for Stage 1. |
| Split leakage-resistance | v1's original test set must NOT be called untouched (12 known overlaps). v2-clean's test split is verified clean against the specific method used, twice — but not provably leak-free in an absolute sense given no plant/session metadata exists. |

**No blockers found that would prevent proceeding to a Stage 1 plan.** Nothing here required or performed a code change, a training run, or any Dataset 3 access.
