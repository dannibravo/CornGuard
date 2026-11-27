# CornGuard disease model v5: field-photo retraining

How and why the on-device disease model was retrained after phone testing showed it calling
healthy corn leaves diseased. Covers the investigation, the data, the code, every decision and
the reason for it, and how to reproduce the run.

| | |
|---|---|
| Model trained | `cornguard_mobilenetv2_v5` |
| Replaces | `cornguard_mobilenetv2_v4` (currently bundled in the Android app) |
| Branch | `feature/plant-result-aggregator` (work not yet committed) |
| Date | 2026-11-28 |
| Status | Trained and exported. **Not recommended for shipping**: it does not fix healthy-leaf phone photos (see [§9](#9-results)). Fix camera framing first. |

---

## 1. The problem

Phone testing of the Android app showed:

- Diseased leaves were identified correctly.
- **Healthy leaves were reported as a disease**, usually Northern Leaf Blight (NLB).

### 1.1 What the phone actually recorded

The 8 scans were pulled off the test phone (`com.cornguard.app.debug`) with `adb` and `run-as`.
Only the Room database (`databases/cornguard.db`) and the saved scan images (`files/scans/`) were
copied, nothing on the phone was changed.

| Scan | What the photo shows | v4 result |
|---|---|---|
| 1, 5, 6 | Healthy leaf in the field | NLB 68%, Rust 40%, NLB 69% (wrong) |
| 7, 8 | Healthy plant, wide shot | NLB 73%, NLB 81% (wrong) |
| 2 | Mostly healthy leaf, one small tan streak | Rust 48% (unclear) |
| 3, 4 | Clearly diseased leaf | NLB 90%, NLB 87% (correct) |

None of the 8 scans came back Healthy. Re-running v4 on the same images on a PC gave a Healthy
probability of **0.09 or less on every one**.

### 1.2 What it was not

The app's inference path was checked against training and is correct:

- Class order in `labels.json` matches the model's outputs.
- Pixel scaling (`minus_one_to_one`, i.e. `x / 127.5 - 1`) matches the notebook's
  `mobilenet_v2.preprocess_input`.
- Centre-square crop and resize to 224×224 are the same as training.

If any of these were wrong, disease detection would also fail, and it did not.

### 1.3 Root cause: the model learned what a lab photo of a healthy leaf looks like

The audit in `ml/v1_training_audit/audit_report.md` had already established that **all 4,188
training images are public, lab-style photos, and none are field photos.** The "healthy" class in
that data is extreme close-ups where green leaf texture fills the whole frame.

Three tests confirmed this is what the model keys on:

| Test | Result |
|---|---|
| v4 on 20 held-out *lab* healthy images | 20/20 Healthy (0.62–0.92) |
| v4 on the field healthy phone photos | 0/5 Healthy (Healthy ≤ 0.09) |
| Same field photos **cropped to just the leaf surface** | Healthy 0.95, 0.56, 0.88 |
| Diseased photos cropped the same way | Still NLB (0.68–0.80) |

So v4 calls anything with leaf edges, hands, soil, sky or other plants "diseased". The earlier
field evaluation (`ml/field_gap_evaluation_comparison_20261030/field_gap_investigation.md`) had
measured the same weakness on an older model: about 9% of field healthy leaves recognised, most
of the rest pushed into a disease class.

The 95.6% accuracy reported for v4 is real, but it was measured on a test set drawn from the same
lab-style images, so it says nothing about field photos.

### 1.4 Decision

Two fixes were identified:

1. **Retrain with field photos** so the model learns what a real healthy leaf looks like. *(This
   document.)*
2. **Guide the camera framing** in the app so only the leaf goes to the model (see
   `docs/MULTI_PHOTO_LEAF_CAPTURE_PLAN.md`). Still to do, and complementary to retraining.

---

## 2. Rules the work had to follow

These come from `ml/v2_field_domain_planning/field_domain_gap_plan.md` (§D, §E) and shaped most
decisions below.

- **Dataset 3 is evaluation-only.** Dataset 3 is the University of Pretoria "maize in field" set
  (about 2,355 images) at `C:\Users\danse\Downloads\cornguard_external_eval\`. It must never be
  used for training, validation, tuning or picking a checkpoint, and any new data must be screened
  against it for duplicates. Evaluating against it is rationed and logged.
- **Every new dataset is verified** for source, licence and label meaning before use. Labels are
  never trusted from folder names alone, and unclear labels are held out, not guessed.
- **Duplicate screening** by SHA-256 and perceptual hash (pHash), against the existing training
  data, against Dataset 3, and within the new data itself.
- **Group-aware splitting** so near-identical shots of one leaf cannot sit in both train and test.

---

## 3. Data sources

### 3.1 Search and verification

Candidate datasets were found by web search, then checked directly through the Mendeley Data
public API (`/public-api/datasets/<id>`), reading each dataset's own licence, authors, description
and file listing rather than trusting search summaries.

| Dataset | Licence | Healthy | Other classes | Used? |
|---|---|---|---|---|
| **MLD**: Multi-Class Maize Leaf Disease Image Dataset from Bangladesh (Faruk Ahmed), Mendeley `3myfctrgk3` v1 | CC BY 4.0 | 427 | Blight 489, Fall Armyworm 525, Gray Spot 555 | Yes (Healthy, Gray Spot) |
| **Open-Source Multi-Format Maize Leaf Image Dataset** (Rabbany, Hashi, Rahman, Adnan), Mendeley `sb4wwjs8bz` v1, `RAW_Image` folder only | CC BY 4.0 | 120 | Common Rust 135, Corn Leaf Blight 220, Gray Leaf Spot 100, others | Yes (Healthy, Common Rust) |
| **Maize Imagery Dataset – Tanzania** (Mduma, Mayo), Mendeley `fkw49mz3xs` v1 | CC BY 4.0 | 3,073 | MLN, MSV (not CornGuard classes) | Yes (600 Healthy) |
| Corn Leaf Disease Classification Dataset, Mendeley `hmkd6nbngr` | CC BY 4.0 | yes | 4 CornGuard classes + insect damage | No: 1.7 GB single zip, and its "17,000 field images" claim could not be checked without downloading all of it |
| PlantDoc | CC BY 4.0 | no corn healthy class | | No |

**Why these three:** all are smartphone photos of real maize leaves outdoors, all are CC BY 4.0
(free to use with attribution), and together they give about 1,150 healthy field photos.

**Attribution:** CC BY 4.0 requires credit. Before v5 ships, the three datasets above must be
added to `THIRD_PARTY_NOTICES.md`.

### 3.2 What the photos look like (checked by eye)

Random samples of every class used were viewed before training.

- **MLD and Bangladesh multi-format:** mostly single leaves held against a white sheet or pale
  sky. Healthy *and* diseased photos use the same backdrop, so the model cannot learn "white
  backdrop = healthy".
- **Tanzania:** close-ups of the leaf surface taken in the field, with natural specks, dust, glare
  and shadow, and some background at the edges. They teach the model that specks and shine are
  not disease, which is exactly v4's mistake.
- **None** of the sets show cluttered scenes (soil, hands, whole plants). That gap is better closed
  by camera framing in the app (§1.4) than by more data.

### 3.3 Label mapping, and what was excluded

| Source folder | CornGuard class | Decision and reason |
|---|---|---|
| MLD `Healthy`, Bangladesh `Healthy_Leaf`, Tanzania `HEALTHY` | Healthy | Used. Clearly healthy leaves. |
| MLD `Gray_Spot` | Gray Leaf Spot | Used, at moderate confidence. Elongated tan-brown spots, plausibly GLS but not textbook rectangular lesions. |
| Bangladesh `Common_Rust` | Common Rust | Used. Rust-like flecks, but only a handful of leaves photographed repeatedly (14 distinct images after de-duplication). |
| MLD `Leaf_Blight` | — | **Excluded.** Samples show small white/tan flecks and holes that look more like insect feeding than NLB's long cigar-shaped lesions. The dataset does not say which blight it means. |
| Bangladesh `Corn_Leaf_Blight` | — | **Excluded.** Samples show red-purple streaking and dying leaves (more like nutrient stress or ageing than NLB), with many near-identical repeat shots. |
| MLD `Fall_Armyworm`, Tanzania MLN/MSV, other Bangladesh diseases | — | Not CornGuard classes. |
| Bangladesh `Gray_Leaf_Spot` | — | Not used in this run: download was slow and stopped; MLD already supplies 555 field GLS photos. |

**Known gap:** there are **no field photos of Northern Leaf Blight** in v5's training data.
Excluding the doubtful "blight" folders was the safer choice (wrong labels would teach the model
the wrong idea of NLB), but it means the model could learn "real phone photo = not NLB". This is
tested explicitly in §9.

### 3.4 Download issues encountered

- Mendeley download links return HTTP 403 without a `User-Agent` header, so `curl -A` was used.
- **84 of 135 Bangladesh rust files were saved as a JSON "404 Not Found" body instead of an
  image.** The first download script did not check HTTP status. A second script retried with
  `curl --fail`, size checks and back-off, and the remaining bad files are dropped automatically as
  unreadable (48 at build time).
- Tanzania `HEALTHY.zip` (2.51 GB) was verified complete (size matches Mendeley's listing to the
  byte, 3,073 files, no unsafe paths). Only 600 images were extracted, because disk space was
  short (about 9 GB free).
- 6 Bangladesh healthy photos are iPhone HEIC files, read with `pillow-heif`.

Everything downloaded lives in `ml/data/downloads/field/`, which git ignores.

---

## 4. Building the training list (`ml/v5_field/build_manifest_v5.py`)

Run from `ml/`: `.venv\Scripts\python v5_field\build_manifest_v5.py`
Writes `ml/v5_field/manifest_v5.csv` and `ml/v5_field/manifest_v5_report.txt`.

### 4.1 Steps, and why

1. **Collect** the public images (`ml/data/raw/<class>/public`, the same images the earlier
   pipeline used) and the field folders listed in `FIELD_SOURCES`.
   *Why keep the public set:* it is what makes the model good at lab-style photos; dropping it
   would trade one weakness for another.
2. **Tanzania subset of 600**, picked in runs of 20 consecutive photos (seed 42).
   *Why 600:* Tanzania alone has 3,073 healthy photos; using all of them would make healthy
   dominate and make "Tanzania-looking photo" a shortcut to Healthy. *Why runs of 20:* consecutive
   photos are often the same leaf, so whole runs keep bursts together.
3. **Drop unreadable files** (the 404 bodies above).
4. **Hash every image**: SHA-256 and `imagehash.phash(hash_size=8)`, identical to the Dataset 3
   tooling, so hashes are comparable.
5. **Dataset 3 hard stop.** Any image whose SHA-256 matches, or whose pHash is within 12 bits of,
   a Dataset 3 image is removed. Only Dataset 3's *stored* hashes (`dataset3_manifest.csv`) are
   read; no Dataset 3 image is opened.
   *Why 12 (the "suspected" threshold) rather than 6 ("near-duplicate"):* overlap with the frozen
   test set is a hard stop, so the stricter cut is used. Result: 12 field and 17 public images
   removed. Most are probably just similar leaf texture, but removing them costs little.
6. **Field vs public leakage**: field images within pHash 6 of a public image are removed. None
   were found.
7. **Notebook de-duplication rule**: identical pHash with different labels, all copies dropped (6);
   identical pHash with the same label, first copy kept (154 dropped).
8. **Split 70/15/15.**
   - *Public:* stratified by class, seed 42, exactly as the training notebook does.
   - *Field:* **by group**, per class. A group is the union of (a) images within pHash 6 of each
     other and (b) images whose file numbers fall in the same block of 10.
     *Why:* field datasets contain bursts of the same leaf; a random split would put near-copies in
     both train and test and inflate the score.

### 4.2 A bug found and fixed during this step

The first version grouped files whose numbers were within 3 of each other. Because files are
numbered 1, 2, 3… with no gaps, that **chained the entire folder into one group**: all 555 field
GLS photos landed in training and none in test. The grouping threshold of pHash 12 also merged
different leaves, since leaf textures hash alike. The fix uses fixed blocks of 10 file numbers and
pHash ≤ 6. After the fix: 128 healthy groups and 54 GLS groups, split cleanly.

### 4.3 Final dataset (5,787 images)

| Class | Public train/val/test | Field train/val/test |
|---|---|---|
| Blight (NLB) | 799 / 171 / 171 | none |
| Common Rust | 910 / 195 / 195 | 11 / 3 / 0 |
| Gray Leaf Spot | 397 / 85 / 85 | 393 / 79 / 80 |
| Healthy | 808 / 173 / 174 | 748 / 156 / 154 |
| **Total** | 2,914 / 624 / 625 | 1,152 / 238 / 234 |

Overall split: 4,066 train, 862 validation, 859 test.

---

## 5. Training (`ml/v5_field/train_v5.py`)

Run from `ml/`: `.venv\Scripts\python v5_field\train_v5.py`
Outputs go to `ml/artifacts/v5-field/` (git-ignored).

### 5.1 Why copy v4's recipe

v4 was trained in Colab with `ml/CORNGUARD_EDA_&_TRAINING_v2.ipynb`, but **v4's Keras weights are
not on this machine**; only its `.tflite` export is, which cannot be fine-tuned. So v5 is trained
from ImageNet weights with the notebook's recipe unchanged, and **only the data differs**. That
keeps the comparison fair: any change in behaviour comes from the field photos, not from a new
architecture or settings.

| Setting | Value | Same as v4 notebook? |
|---|---|---|
| Backbone | MobileNetV2, ImageNet weights, 224×224×3 | Yes |
| Head | GlobalAveragePooling → Dense 256 ReLU → Dropout 0.4 → Dense 128 ReLU → Dropout 0.3 → Dense 4 softmax | Yes |
| Input scaling | `mobilenet_v2.preprocess_input` (to [-1, 1]) | Yes |
| Class order | Blight, Common_Rust, Gray_Leaf_Spot, Healthy (= app `labels.json` order) | Yes |
| Loss | Categorical cross-entropy, label smoothing 0.1 | Yes |
| Class weights | sklearn `balanced` on the training split | Yes |
| Phase 1 (head only) | Adam 1e-3, 12 epochs, ReduceLROnPlateau, early stop on val macro-F1 (patience 4) | Yes |
| Phase 2 (fine-tune) | Unfreeze MobileNetV2 from layer 60, BatchNorm frozen, AdamW (wd 1e-4), cosine decay with 2-epoch warm-up to 3e-5, early stop on val macro-F1 (patience 6) | Yes, except **20 epochs instead of 30** and **patience 6 instead of 8**, to fit CPU time |
| Augmentation (train only) | Random crop 75–100%, flips, 90° rotations, brightness ±0.15, contrast/saturation 0.8–1.2, hue ±0.03, JPEG quality 60–100, ±5% tilt | Yes |
| Batch size | 32 | Yes |
| Seed | 42 | Yes |

### 5.2 Additions in v5, and why

- **Image cache.** Each image is decoded once and saved to `ml/data/v5_cache/<sha256>.jpg` with its
  short side at 384 px (git-ignored). *Why:* field originals are 12 MP and some are HEIC, which
  `tf.io` cannot decode; decoding them every step on a CPU would make training far slower. 384 px
  leaves room for the random crop before the 224 px resize.
- **Field healthy recall printed every epoch** (`val_field_healthy_recall`): the share of field
  healthy *validation* photos called Healthy. *Why:* it is the number this whole effort exists to
  move, so it is watched directly.
- **Separate test reports** for public and field images. *Why:* a single overall score could hide
  the model getting worse on lab photos while improving on field photos, or the reverse.
- **TFLite export and parity check.** Dynamic-range quantised like v4, then checked against the
  Keras model on the whole test split. *Why:* the app runs the `.tflite` file, so it must give the
  same answers as the model that was evaluated.
- **App asset files** are written to `ml/artifacts/v5-field/android_assets/` in the exact format
  the app already reads (`model.tflite`, `labels.json`, `model_config.json`,
  `model_version.txt`), so shipping v5 needs no app code change.

### 5.3 Environment

- Windows 10, **CPU only (no GPU)**, Python 3.11 venv at `ml/.venv`, TensorFlow 2.16.2.
- Added to `ml/requirements.txt`: `imagehash==4.3.2` (same version as the Dataset 3 tooling) and
  `pillow-heif>=0.16`.
- Time: image cache about 20 minutes; phase 1 about 2.7 min/epoch; phase 2 about 3.8 min/epoch.
- v4's `.tflite` cannot be opened by TF 2.16 (it uses a newer `FULLY_CONNECTED` op version), so
  comparisons use `ai-edge-litert` in a separate throwaway venv.

### 5.4 Training log

Validation numbers per epoch (rows 1–12 phase 1, 13 onward phase 2). Full log:
`ml/artifacts/v5-field/train_run_log.txt`.

| Epoch | Val accuracy | Val macro-F1 | Field healthy recall |
|---|---|---|---|
| 1 | 0.923 | 0.906 | 0.994 |
| 5 | 0.937 | 0.923 | 0.994 |
| 10 (best of phase 1) | 0.950 | 0.938 | 0.994 |
| 13 (phase 2 start) | 0.929 | 0.911 | 0.994 |
| 18 | 0.959 | 0.950 | 0.994 |
| 25 | 0.965 | 0.957 | 0.994 |
| 26 (best, kept) | 0.969 | 0.962 | 0.994 |
| 32 (last) | 0.966 | 0.959 | 0.994 |

Field healthy recall sat at 0.99 from epoch 1. Validation field healthy photos come from the same
three sources as training (on different leaves), so this number is optimistic; §9 tests on the
phone scans, which come from elsewhere.

---

## 6. Monitoring tools

- `ml/v5_field/check_progress.py`: reads the training log and prints the phase, epoch, a per-epoch
  table and, at the end, the test results. `--watch [seconds]` clears the screen and refreshes.
- `ml/v5_field/check.ps1`: PowerShell wrapper that works from any folder.
  - From `ml/`: `.\v5_field\check.ps1 -Watch 5`
  - From the repo root: `.\ml\v5_field\check.ps1 -Watch 5`

---

## 7. Evaluation plan (`ml/v5_field/compare_tflite.py`)

```
python v5_field\compare_tflite.py <v4.tflite> <v5.tflite> [folder of extra photos]
```

Runs both app-format models with the app's own preprocessing (centre crop, bilinear resize,
`x / 127.5 - 1`) and reports, side by side:

1. **Public test split**: must not get worse than v4 (lab-style accuracy).
2. **Field test split**: field healthy and field GLS recall, on leaves never seen in training.
3. **The 8 phone scans**: the original complaint. Healthy scans should now come back Healthy, and
   the two diseased scans must **still** come back diseased (this is the check for the missing
   field-NLB data, §3.3).

Dataset 3 is **not** part of this plan. Under §2's rule it may be used once, read-only and
logged,
only after v5 is judged the best candidate by the checks above.

---

## 8. Files

| Path | In git? | What it is |
|---|---|---|
| `ML_V5_FIELD_RETRAINING.md` | to commit | This document |
| `ml/v5_field/build_manifest_v5.py` | to commit | Data collection, hashing, Dataset 3 screen, de-dup, split |
| `ml/v5_field/train_v5.py` | to commit | Training, evaluation, TFLite export, app assets |
| `ml/v5_field/export_v5.py` | to commit | TFLite export via SavedModel (works around the converter crash), parity check, app assets |
| `ml/v5_field/compare_tflite.py` | to commit | v4 vs v5 comparison |
| `ml/v5_field/check_progress.py`, `check.ps1` | to commit | Training progress viewer |
| `ml/v5_field/manifest_v5.csv`, `manifest_v5_report.txt` | to commit | Exact image list, splits and build log |
| `ml/requirements.txt`, `ml/.gitignore` | to commit | New dependencies; cache ignored |
| `ml/data/downloads/field/` | ignored | Downloaded datasets |
| `ml/data/v5_cache/` | ignored | Resized image cache |
| `ml/artifacts/v5-field/` | ignored | `model.keras`, `best_head.keras`, `model.tflite`, `android_assets/`, `evaluation_report.json`, `train_run_log.txt` |

---

## 9. Results

**Verdict: v5 should not ship as the fix for healthy leaves.** It is much better on field Gray Leaf
Spot, but it does **not** fix the original complaint: full-frame phone photos of healthy leaves
are still called diseased. What does fix them is cropping to the leaf, with either model.

### 9.1 Training run

- Best epoch: fine-tune epoch 14 (row 26 in §5.4); validation macro-F1 0.962, accuracy 0.969.
  Phase 2 ran all 20 epochs; the best weights were restored.
- Training took about 2 h 10 min on CPU (cache, 12 head epochs, 20 fine-tune epochs).
- The TFLite conversion at the end of `train_v5.py` **crashed** (`LLVM ERROR: Failed to infer
  result type(s)`), a TF 2.16 / Keras 3 converter problem. `model.keras` had already been saved.
  `ml/v5_field/export_v5.py` was added to export via a SavedModel instead, which worked.
- Exported `model.tflite`: 2.87 MB (v4: 2.90 MB), input `[1, 224, 224, 3]` float32, output
  `[1, 4]`. Agrees with the Keras model on 99.5% of test images (largest probability difference
  0.13, from weight quantisation); TFLite test accuracy 0.959, same as Keras.

### 9.2 v5 test set (Keras, from `train_v5.py`)

| Test set | n | Accuracy | Macro-F1 | Per-class recall |
|---|---|---|---|---|
| Public (lab) | 625 | 0.949 | 0.932 | NLB 0.912, Rust 1.000, GLS 0.800, Healthy 1.000 |
| Field | 234 | 0.987 | 0.986 | GLS 0.975, Healthy 0.994 |
| All | 859 | 0.959 | 0.950 | |

### 9.3 v4 vs v5, same images, app preprocessing (`compare_tflite.py`)

| Test set | v4 | v5 |
|---|---|---|
| Public (lab), 625 | **0.970** (GLS recall 0.953) | 0.950 (GLS recall 0.824) |
| Field, 234 | 0.650 (GLS 0.113, Healthy 0.929) | **0.987** (GLS 0.975, Healthy 0.994) |

v4's 0.970 here is on v5's test split, not v4's own; some of these images may have been in v4's
Colab training set, which would flatter v4.

### 9.4 The 8 phone scans (the original complaint)

| Scan | Shows | v4 | v5 |
|---|---|---|---|
| 1, 5, 6 | Healthy leaf in the field | NLB 0.81, NLB 0.43, NLB 0.77 | NLB 0.77, NLB 0.42, NLB 0.80 |
| 7 | Healthy plant, wide shot | NLB 0.72 | GLS 0.53 |
| 8 | Healthy plant, wide shot | NLB 0.85 | NLB 0.71 |
| 2 | Mostly healthy, one tan streak | Rust 0.40 | Healthy 0.43 |
| 3, 4 | Diseased | NLB 0.91, NLB 0.91 | NLB 0.81, NLB 0.86 |
| 1, 5, 6 **cropped to the leaf** | Healthy | Healthy 0.95, 0.56, 0.88 | Healthy 0.91, 0.84, 0.94 |
| 3, 4 **cropped to the leaf** (3 crops) | Diseased | NLB 0.80, 0.75, 0.68 | NLB 0.34, 0.67, 0.73 |

### 9.5 Why the test numbers did not carry over to the phone

The field photos that could be sourced (§3.2) are close-ups where the leaf fills most of the frame.
**v4 already handled that kind of photo reasonably well** (field healthy recall 0.929 on the field
test set), so the new data did not contain the case that actually fails: a healthy leaf with soil,
hands, sky or other plants in the frame. v5 learned field Gray Leaf Spot, which v4 could not
recognise (0.113 to 0.975), but the healthy-leaf failure was never in the training data, so it
was not fixed.

The crop rows of §9.4 show that the failure is about framing: once only the leaf is in the
frame, both models call healthy leaves healthy, and v5 does so with more confidence.

### 9.6 Recommendation

1. **Do not replace v4 with v5 yet.** v5 does not fix healthy leaves, is slightly weaker on lab
   GLS (0.953 to 0.824 recall), and is less confident on one cropped diseased scan (NLB 0.34).
2. **Fix framing in the app first.** A guide box (or leaf outline) that sends only the leaf to
   the model, as in `docs/MULTI_PHOTO_LEAF_CAPTURE_PLAN.md`. The crop test shows this works
   with the model already shipped.
3. **If retraining again**, the missing data is full-scene field photos in *every* class:
   healthy and diseased leaves with soil, hands and plants in frame, ideally taken with the app
   itself in local fields. Data from only one class would teach a background shortcut. Field NLB
   photos are needed too (none were usable here).
4. v5's field GLS gain is real and worth keeping for a later model. Its weights are in
   `ml/artifacts/v5-field/`.

---

## 10. Limitations and open items

- **No field NLB training data** (§3.3). Must be checked in §9 before shipping.
- **Gray Leaf Spot field labels are moderate confidence** and not expert-verified; field rust is
  a handful of leaves.
- **No cluttered-background training photos.** The camera framing fix is still needed.
- **v4's own weights are unavailable**, so v5 is a fresh training run, not a fine-tune of v4.
- **Single run, single seed.** Differences of a point or two between runs are within normal noise.
- **The phone-scan set is tiny (8 images)** and some of those look like web images rather than
  phone photos. It shows whether the original failure is fixed; it does not measure accuracy.
- **Before shipping:** add CC BY 4.0 attributions to `THIRD_PARTY_NOTICES.md`, update
  `android/README.md` ("Disease model"), and bump the version references in the Android tests that
  name v4.

## 11. Reproducing

From `ml/` with the venv set up (`pip install -r requirements.txt`):

1. Download the three datasets into `ml/data/downloads/field/` with the folder layout listed in
   `FIELD_SOURCES` in `build_manifest_v5.py` (Mendeley IDs in §3.1).
2. `.venv\Scripts\python v5_field\build_manifest_v5.py`
3. `.venv\Scripts\python v5_field\train_v5.py` (optionally set `EPOCHS_HEAD` / `EPOCHS_FINE`).
   On TF 2.16 its final TFLite step crashes (§9.1); `model.keras` is saved before that.
4. `.venv\Scripts\python v5_field\export_v5.py`
5. `python v5_field\compare_tflite.py <path to v4 model.tflite> artifacts\v5-field\model.tflite <phone scans folder>`
   (needs `ai-edge-litert`, since TF 2.16 cannot open v4)
