# Proposed Plan: Multi-Leaf Diagnosis + Guided Leaf Outline

Status: **Proposal, not approved yet.** Nothing here is built.
Scope: Android app (`android/`), with small changes to the ML contract (`claude/09_ML_MODEL_CONTRACT.md`) and the data contract (`claude/08_DATA_AND_INTEGRATION_CONTRACT.md`).

---

## 1. Goal

1. **Use more than one leaf per scan.** A diagnosis comes from photos of 2 different leaves on the same plant, not one, so the result covers more of the plant and a single misread leaf can't decide it alone.
2. **Make the farmer frame the leaf properly.** The camera shows a leaf-shaped outline. The shutter only works once a corn leaf fills that outline, the photo is sharp, and the light is good enough.

Why: the disease classifier is a closed-set 4-way softmax (`northern_leaf_blight`, `common_rust`, `gray_leaf_spot`, `healthy`). It always picks one of the four, even for a blurry photo, a far-away plant, or a leaf that fills only 10% of the frame. Better input and more than one leaf are the cheapest ways to get more reliable results without retraining.

---

## 2. Current flow (as of `develop` @ 52d49f9)

| Step | Where | Notes |
|---|---|---|
| Open camera | `ScanFragment.launchCamera()` | Uses the **system camera app** (`ActivityResultContracts.TakePicture`). We can't draw anything over it. |
| Or pick from gallery | `ScanFragment.launchGallery()` | One image. |
| Decode | `ScanImageDecoder.decode()` | Downsampled, EXIF-upright. |
| Corn gate | `ServiceLocator.cornGate.check()` | Binary p(corn), threshold 0.5 (`corn_gate_config.json`). Rejects non-corn photos. |
| Classify | `CornLeafClassifier.classify(bitmap)` | One `DetectionResult` per bitmap, 224×224 input. |
| Save | `DiagnosisRecordEntity` | One image path, one confidence. DB schema v3. |
| Show | `ResultBottomSheet` | |

What's missing: no framing guide, no quality check (blur or brightness), the whole frame goes to the model with no crop, and one photo decides the result.

---

## 3. Part A: Guided capture with a leaf outline

### 3.1 Switch to an in-app camera (CameraX)

The system camera intent can't show an overlay or block the shutter, so this part needs an in-app camera.

- Add `androidx.camera:camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view` to `libs.versions.toml` and `app/build.gradle.kts`.
- New `ui/scan/capture/LeafCaptureFragment` with a `PreviewView`, an `ImageAnalysis` use case (for live checks), and an `ImageCapture` use case (for the final photo).
- Keep everything on-device and offline (Project Principle, `claude/15_CLAUDE.md`).

### 3.2 The outline overlay

```
┌──────────────────────────────┐
│  ←  Leaf 1 of 2              │
│                              │
│ ░░░░░░░░░░░░░░░░░░░░░░░░░░░░ │  ← dimmed area outside the outline
│ ░░░░░░░░░░╭──────╮░░░░░░░░░░ │
│ ░░░░░░░░░╱        ╲░░░░░░░░░ │
│ ░░░░░░░░│          │░░░░░░░░ │
│ ░░░░░░░░│   LEAF   │░░░░░░░░ │  ← long, narrow corn-leaf shape
│ ░░░░░░░░│  OUTLINE │░░░░░░░░ │     (portrait, about 60% of width,
│ ░░░░░░░░│          │░░░░░░░░ │      75% of height)
│ ░░░░░░░░░╲        ╱░░░░░░░░░ │
│ ░░░░░░░░░░╲______╱░░░░░░░░░░ │
│ ░░░░░░░░░░░░░░░░░░░░░░░░░░░░ │
│                              │
│  ✓ Corn leaf   ✓ Sharp  ✗ Too dark │  ← live checklist
│  "Move closer so the leaf fills the outline"
│                              │
│            (  ◉  )           │  ← shutter, grey and disabled until all ✓
└──────────────────────────────┘
```

- `LeafOutlineOverlayView`: a custom `View` drawing a vector leaf path (`res/drawable/leaf_outline.xml`), with a dimmed scrim outside it. The outline turns from white to `cg_accent_green` when all checks pass.
- The outline's rectangle in preview coordinates is mapped to sensor coordinates (via `PreviewView.outputTransform` / `CoordinateTransform`), so we know which part of the captured image is "inside the outline".

### 3.3 Live checks (what "required" means)

They run in `ImageAnalysis` on the region inside the outline only, throttled to about 2–3 checks per second so the preview stays smooth on low-end phones.

| Check | How | Default (dev-only, configurable) |
|---|---|---|
| **Corn leaf fills the outline** | Run the existing `CornGate` on the outline crop | p(corn) ≥ gate threshold (0.5) |
| **Sharp** | Variance of the Laplacian on a grayscale crop | ≥ tuned value (calibrate on real phones) |
| **Light** | Mean luminance of the crop | between about 50 and 220 (0–255) |
| **Steady** | Same checks pass on 3 frames in a row | 3 consecutive passes |

The shutter is **disabled until all checks pass.** The live hint line tells the farmer which check fails, one at a time, in plain words: "Move closer", "Hold still", "Too dark, find more light", "Point at a corn leaf".

All thresholds go in a new `assets/capture_config.json`, labelled development-only, in the same way `claude/04_DEVELOPMENT_RULES.md` #5 treats thresholds that haven't been validated.

### 3.4 Crop to the outline

After capture, crop the full-resolution photo to the outline's bounding box (plus a small margin) **before** the gate and the classifier. The model then sees mostly leaf rather than soil, sky or hands. Keep the uncropped original on disk for the history view.

> ⚠ **Check with ML first:** the training images (`ml/pipeline`) need to look similar to the cropped input (a close-up leaf filling most of the frame). If they don't, cropping could lower accuracy. Run §6.2 before turning this on by default.

### 3.5 Gallery path

Gallery photos can't be checked live. Proposal: after picking, show the same outline on top of the still image and let the farmer pan and zoom the photo until the leaf fills it. Then run the gate plus the blur and light checks once. If any check fails, show the same messages and ask for another photo.

---

## 4. Part B: Two different leaves per scan

### 4.1 Capture session

One scan session = **2 different leaves from the same plant**, one photo each (minimum 2; the farmer can tap "Add another leaf" up to a maximum of 4; both set in `capture_config.json`). Each shot has its own prompt:

1. **Leaf 1**: "Photograph the leaf with the most visible spots or damage"
2. **Leaf 2**: "Now a different leaf on the same plant"
3. **Leaf 3–4 (optional)**: "Another leaf on the same plant"

Every photo has to pass Part A's checks. A thumbnail strip at the bottom shows the leaves taken so far. The farmer can tap one to retake it.

```
Leaf 2 of 2 — A different leaf on the same plant
[ ✓ leaf 1 ] [ ● now ]   [ + Add another leaf ]
```

**Why different leaves, not repeat photos of one leaf.** Repeat photos of one leaf mostly repeat the same evidence (same leaf, light, phone and model), so if the model misreads that leaf it will likely misread every photo of it. Part A's live checks already block the bad shots that repeat photos would protect against. Different leaves add new evidence about the plant, and can catch a disease that shows on only one of them.

Field evidence: in the PlantVillage Nuru study on cassava (Mrisho et al., 2020, *Frontiers in Plant Science*, doi:10.3389/fpls.2020.590889), the smartphone model was right 65% of the time overall and 74–88% when six leaves per plant were assessed. That's a different crop and model, so it supports the design but doesn't predict CornGuard's numbers. §6.2 has to measure that.

### 4.2 Combining the results (aggregation)

Each leaf photo still gives a normal `DetectionResult` (the contract for each image doesn't change). Add a new pure-Kotlin `model/PlantResultAggregator`.

**Don't average probabilities across leaves.** Different leaves can really have different conditions. Averaging a rust leaf (0.9) with a healthy leaf (0.9) gives about 50/50 and hides the rust.

- **Per-leaf result**: each leaf keeps its own top class and confidence, and the result sheet shows them.
- **Confident leaf**: a leaf whose confidence ≥ `T_conf`. Leaves below `T_conf` are shown as "unclear" and don't count toward the summary.
- **Plant summary (`resultState`)**, checked in this order:
  - `UNCLEAR`: no confident leaves, **or** only healthy leaves but fewer than 2 confident ones. Suggest taking another leaf (up to the maximum) or asking a DA technician.
  - `MIXED_DISEASES`: confident leaves show 2 or more different diseases. Show each disease with its leaf count and suggest asking a DA technician.
  - `DISEASE_FOUND`: confident leaves show exactly one disease. Report it with its leaf count, e.g. "Common rust on 2 of 2 leaves" or "Common rust on 1 of 2 leaves (the other looks healthy)".
  - `HEALTHY`: every confident leaf is healthy, and there are at least 2 confident leaves.
- **Main result** (stored in `diseaseCode`): the disease found on the most confident leaves, with ties broken by higher mean confidence. It's `healthy` only when the state is `HEALTHY`. For `UNCLEAR`, it's the top class of the highest-confidence leaf, and the record is marked unclear so history and the map don't present it as a firm diagnosis.
- **Stored confidence**: the mean confidence of the confident leaves that show the main result (for `UNCLEAR` with no confident leaves, the confidence of the highest-confidence leaf).

Honesty rule: leaves from one plant are more independent than repeat photos of one leaf, but they're still not fully independent (same plant, field, phone and model). We report leaf counts ("2 of 2 leaves") and per-leaf confidences, never one combined "boosted" confidence, and we never claim that 2 leaves means 2× certainty.

`T_conf` and the "at least 2 confident leaves" rule are development-only values in `capture_config.json` until we have validated values (§6).

### 4.3 Data model changes

The DB moves from schema v3 to v4, with a Room migration and an updated `AppDatabaseMigrationTest`.

- **New table `scan_images`** (one row per leaf): `id`, `recordLocalId` (FK → `diagnosis_records.localId`, cascade delete), `position`, `imagePath`, `cornProbability`, `probabilitiesJson`, `topClassCode`, `confidence`, `sharpness`, `brightness`.
- **`diagnosis_records` new columns**: `imageCount INTEGER NOT NULL DEFAULT 1`, `leavesWithResult INTEGER` (nullable: how many confident leaves show the main result; null for old single-photo records), `resultState TEXT` (nullable; null for old single-photo records), `unclearReason TEXT` (nullable; set only when `resultState` is `UNCLEAR`, see §4.5).
- **Later, with technician confirmation (§4.5 step 5)**: `confirmedLabel TEXT`, `confirmedBy TEXT`, `confirmedAt INTEGER`, all nullable. These go in their own migration on the step 7 branch, not in v4.
- `diseaseCode`, `confidence` and `imageUriOrLocalPath` keep their meaning: the main result (§4.2) and the **main** photo, which is the confident leaf showing the main result with the highest confidence (leaf 1 if none is confident). History, the outbreak map and sharing keep working without changes.
- **Cloud (`backend/convex/diagnosisRecords.ts`)**: add optional `imageCount`, `leavesWithResult` and `resultState`. Sharing still uploads only the main photo for now, to keep mobile data use low (decision needed, see §8).

These are contract changes. Update `claude/08_DATA_AND_INTEGRATION_CONTRACT.md` and `claude/09_ML_MODEL_CONTRACT.md`, and tell the team before merging (`claude/04_DEVELOPMENT_RULES.md` #3).

### 4.4 Outbreak map

By default, `UNCLEAR` scans don't count toward outbreak signals. They can still be shared, but they're flagged. A scan with a disease on 2 of 2 leaves is a stronger signal than one with it on 1 of 2, so the outbreak method in `docs/OUTBREAK_DETECTION_METHOD.md` should decide whether to weight scans by `leavesWithResult / imageCount`. This needs a decision from whoever owns the outbreak logic.

### 4.5 Unclear scans

A scan is `UNCLEAR` for one of two reasons, stored in `unclearReason`:

- `NO_CONFIDENT_LEAF`: no leaf reaches `T_conf`. This is the important case. The model is a closed 4-class model, so damage it wasn't trained on (e.g. southern rust, nutrient deficiency, insect damage, very early symptoms) usually shows up as low confidence. "Unclear" is the honest answer here, not a failure.
- `TOO_FEW_CONFIDENT_HEALTHY`: the confident leaves are all healthy, but there are fewer than 2 of them. There isn't enough evidence to call the plant healthy yet.

**Step 1: retry with a better leaf before showing a result.** While the scan is `UNCLEAR` and under the maximum number of leaves, the app asks for another leaf instead of showing a result. The hint depends on the reason:
- `NO_CONFIDENT_LEAF`: "Photograph the leaf with the clearest spots, up close"
- `TOO_FEW_CONFIDENT_HEALTHY`: "One leaf looks healthy. Add one more leaf to confirm"

The result is recalculated after each new leaf. The farmer can skip this and see the unclear result straight away.

**Step 2: an honest result screen.** If the scan is still `UNCLEAR` at the maximum (or the farmer skips):
- The headline is "CornGuard can't tell from these photos". **No disease name in the headline**, because a guessed name can lead to the wrong pesticide.
- A collapsed "Details" section shows each leaf's best guess, labelled "low confidence".
- It gives safe next steps: show this scan to a DA technician or the municipal agriculturist; don't spray based on this result; check nearby plants; scan again in 2–3 days, since symptoms often become clearer.

**Step 3: save it with its reason.** The record goes into history with an "Unclear" badge and its `unclearReason`. It doesn't count toward the outbreak map (§4.4) and is flagged if shared.

**Step 4 (optional): rescan reminder.** The result screen offers "Remind me to rescan in 3 days". It's a local notification scheduled with WorkManager, so it works offline, and it's off unless the farmer turns it on. The reminder opens the old record so the farmer can compare.

**Step 5 (separate branch, needs consent work): technician confirmation.** A DA technician looking at the farmer's phone can record a confirmed diagnosis on any record, most usefully unclear ones (`confirmedLabel`, `confirmedBy`, `confirmedAt`). The farmer gets a real answer for that plant, and the history shows the confirmed label next to the model's result. Unclear scans are exactly the hard field cases the model gets wrong, so confirmed ones, **with the farmer's explicit consent**, can feed the Bukidnon field set (§6.2) and future retraining. Consent wording and how the data leaves the phone must be agreed and added to `claude/08_DATA_AND_INTEGRATION_CONTRACT.md` before this is built.

**Step 6: track how often scans are unclear.** Count unclear scans by reason locally during the field test (§6.3).
- A high `NO_CONFIDENT_LEAF` rate means `T_conf` is too strict, the capture checks let poor photos through, or the model struggles on field photos.
- If confirmed unclear scans keep turning out to be one condition outside the 4 classes, that condition is a candidate for a new class.

---

## 5. Implementation steps

Each step is its own branch off `develop` and merges back separately.

| # | Branch | Work | Depends on |
|---|---|---|---|
| 1 | `feature/camerax-capture` | Replace the `TakePicture` intent with a CameraX `LeafCaptureFragment` (single shot, no checks yet). Keep the gallery path as it is. | none |
| 2 | `feature/leaf-outline-overlay` | `LeafOutlineOverlayView`, outline → sensor coordinate mapping, crop-to-outline (behind a flag until §6.2 passes). | 1 |
| 3 | `feature/capture-quality-gate` | `ImageAnalysis` checks (gate, blur, light, steady), `capture_config.json`, disabled shutter, hint text. Gallery outline-fit screen. | 2 |
| 4 | `feature/plant-result-aggregator` | `PlantResultAggregator` and unit tests. Pure Kotlin, no UI. Can run alongside 1–3. | none |
| 5 | `feature/multi-leaf-session` | 2-leaf session UI, thumbnails, retake, "Add another leaf", DB v4 migration, result sheet with per-leaf results and the summary states, unclear-scan retry prompts and result screen (§4.5 steps 1–3), history detail showing all leaves. | 3, 4 |
| 6 | `feature/multi-leaf-sync` | Convex fields, sharing changes, outbreak-map counting (`UNCLEAR` filter, optional leaf-count weighting). | 5 |
| 7 | `feature/unclear-scan-followup` | Rescan reminder (§4.5 step 4) and unclear-rate counting (step 6). Technician confirmation (step 5) only after the consent decision in §8. | 5 |

---

## 6. Testing and validation

### 6.1 Automated
- **Unit (`src/test`)**: `PlantResultAggregator` (same disease on both leaves, disease + healthy, two different diseases, both healthy, one or both leaves below `T_conf`, tie on leaf count, 3–4 leaves, each `unclearReason`, a retry leaf turning `UNCLEAR` into a firm result), quality-check maths (blur and light on synthetic bitmaps), config parsing.
- **Instrumented (`src/androidTest`)**: migration v3 → v4 keeps old records (`imageCount = 1`, `resultState` null), `scan_images` cascade delete, aggregation run on the bundled sample leaf images (`EmulatorLeafImagesTest` style).

### 6.2 ML check (before turning on crop and multi-leaf by default)
Public datasets don't record which plant each leaf came from. Proposal:
- Simulate plants: pair held-out test images of the same class (two leaves of a diseased or healthy plant) and mixed pairs (diseased + healthy). Compare the plant summary against single-image results: how often the disease is caught, and how often a healthy plant is wrongly flagged. This is only an estimate, because real leaves from one plant look more alike than random pairs. Also compare full-frame against outline crop.
- **Field set**: collect a small set of real Bukidnon plants, 2–4 leaves each, with an agronomist labelling each leaf and each plant. This is the only reliable test, and it also shows whether a 3rd or 4th leaf adds anything.
- Pick `T_conf` and the blur and light thresholds from that data, then record them in `claude/09_ML_MODEL_CONTRACT.md`.

### 6.3 Field and usability
- Time per scan with 2 leaves (target: under 45 s for a first-time farmer).
- How often farmers photograph the same leaf twice (check a sample of field scans).
- Unclear rate by reason, and how often the retry leaf (§4.5 step 1) turns an unclear scan into a firm result.
- Whether farmers understand the unclear screen: do they know not to spray, and what to do next?
- How often farmers give up while the shutter is disabled. If they often can't get past a check, loosen that threshold.
- Test on the cheapest target phone in bright sun and in shade.

Add these to `docs/TESTING_GUIDE.md` and `claude/12_TESTING_AND_ACCEPTANCE_PLAN.md`.

---

## 7. Risks

| Risk | Mitigation |
|---|---|
| Live gate inference slows the preview on low-end phones | Throttle to 2–3 checks/s, run on a background executor, downscale analysis frames to 224 px |
| Checks too strict, farmers can't take a photo | Thresholds in config. Log failed checks (locally) during the field test. Show a "Having trouble? Tips" link after 20 s |
| Crop doesn't match the training images and accuracy drops | Crop behind a flag, validated in §6.2 first |
| Extra photos use storage | Save captures as JPEG q85 at most 1280 px on the long side, and delete extra photos when a record is deleted |
| Farmers find 2 leaves tedious | 2 is the minimum and "Add another leaf" is optional. Measure scan time in the field test (§6.3) |
| Farmer photographs the same leaf twice | Clear prompt ("a different leaf"). Optionally compare the two photos and warn if they look almost identical (decision needed) |
| A disease seen on only 1 of 2 leaves gets missed or overstated | Report the leaf count plainly ("1 of 2 leaves") and offer another leaf instead of hiding it or calling the plant healthy |

---

## 8. Open questions (need a decision)

1. **Number of leaves:** fixed 2, or 2 minimum with "Add another leaf" up to 4 (this draft assumes the second)?
2. **Which leaves:** 2 leaves from the same plant (this draft), or leaves from different plants in the same field (a small field sample)?
3. **Disease on 1 of 2 leaves:** report it as `DISEASE_FOUND` with the leaf count (this draft), or ask for a 3rd leaf first?
4. **Duplicate-leaf warning:** should the app compare the photos and warn if the same leaf seems to have been photographed twice?
5. **Gallery:** keep it (with the outline-fit step and several gallery images per scan), or remove gallery for diagnosis entirely?
6. **Cloud upload:** main photo only, or all leaf photos when sharing?
7. **Outbreak map:** do `UNCLEAR` scans count, and should scans be weighted by how many leaves show the disease?
8. **Language:** do the shot prompts and hints need Cebuano/Filipino versions from the start?
9. **Unclear details:** show each leaf's low-confidence guess under a collapsed "Details" (this draft), or hide the guesses completely?
10. **Rescan reminder:** include it in this plan (this draft, step 7 branch), or leave it for later?
11. **Technician confirmation:** who counts as a technician in the app (no login, PIN, or just a name), and what consent wording lets confirmed scans be used for the field set and retraining?
