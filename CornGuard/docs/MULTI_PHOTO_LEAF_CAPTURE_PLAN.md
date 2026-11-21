# Proposed Plan: Multi-Photo Diagnosis + Guided Leaf Outline

Status: **Proposal, not approved yet.** Nothing here is built.
Scope: Android app (`android/`), with small changes to the ML contract (`claude/09_ML_MODEL_CONTRACT.md`) and the data contract (`claude/08_DATA_AND_INTEGRATION_CONTRACT.md`).

---

## 1. Goal

1. **Use more than one photo per leaf.** A diagnosis comes from several photos of the same leaf, not one, so a single bad photo can't decide the result.
2. **Make the farmer frame the leaf properly.** The camera shows a leaf-shaped outline. The shutter only works once a corn leaf fills that outline, the photo is sharp, and the light is good enough.

Why: the disease classifier is a closed-set 4-way softmax (`northern_leaf_blight`, `common_rust`, `gray_leaf_spot`, `healthy`). It always picks one of the four, even for a blurry photo, a far-away plant, or a leaf that fills only 10% of the frame. Better input and more than one view are the cheapest ways to get more reliable results without retraining.

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
│  ←  Photo 1 of 3             │
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

## 4. Part B: More than one photo per leaf

### 4.1 Capture session

One scan session = **3 photos of the same leaf** (minimum 2, maximum 5; set in `capture_config.json`). Each shot has its own prompt so the views actually differ:

1. **Whole leaf**: "Fit the whole leaf in the outline"
2. **Closer to spots**: "Move closer to the spots or damaged area"
3. **Other part of the leaf**: "Now the other end of the leaf" (or the underside, if agronomists confirm it helps for rust)

Every shot has to pass Part A's checks. A thumbnail strip at the bottom shows the photos taken so far. The farmer can tap one to retake it.

```
Photo 2 of 3 — Move closer to the spots
[ ✓ img1 ] [ ● now ] [   3   ]
```

### 4.2 Combining the results (aggregation)

Each photo still gives a normal `DetectionResult` (the contract for each image doesn't change). Add a new pure-Kotlin `model/MultiViewAggregator`:

- **Combined probabilities** = mean of the 4-class probability vectors (soft voting).
- **Combined confidence** = max of the combined vector.
- **Agreement** = share of photos whose top class matches the combined top class.
- **Result state**:
  - `CONFIDENT`: combined confidence ≥ T_conf **and** agreement ≥ 2/3
  - `UNCERTAIN`: anything else. The result sheet says the photos disagree and suggests either taking another photo (up to the maximum) or asking a DA technician. We never hide this behind a confident-looking label.

Honesty rule: the photos show the **same leaf**, so they aren't independent evidence. We report the average, not a "boosted" confidence, and we never claim that 3 photos means 3× certainty.

`T_conf` and the agreement rule are development-only values in `capture_config.json` until we have a validated value (§6).

### 4.3 Data model changes

The DB moves from schema v3 to v4, with a Room migration and an updated `AppDatabaseMigrationTest`.

- **New table `scan_images`**: `id`, `recordLocalId` (FK → `diagnosis_records.localId`, cascade delete), `position`, `imagePath`, `cornProbability`, `probabilitiesJson`, `topClassCode`, `sharpness`, `brightness`.
- **`diagnosis_records` new columns**: `imageCount INTEGER NOT NULL DEFAULT 1`, `agreement REAL` (nullable, null for old single-photo records), `resultState TEXT NOT NULL DEFAULT 'confident'`.
- `diseaseCode`, `confidence` and `imageUriOrLocalPath` keep their meaning: the combined result and the **main** photo (shot 1). History, the outbreak map and sharing keep working without changes.
- **Cloud (`backend/convex/diagnosisRecords.ts`)**: add an optional `imageCount` and `agreement`. Sharing still uploads only the main photo for now, to keep mobile data use low (decision needed, see §8).

These are contract changes. Update `claude/08_DATA_AND_INTEGRATION_CONTRACT.md` and `claude/09_ML_MODEL_CONTRACT.md`, and tell the team before merging (`claude/04_DEVELOPMENT_RULES.md` #3).

### 4.4 Outbreak map

Only `CONFIDENT` scans count toward outbreak signals by default. `UNCERTAIN` scans can still be shared, but they're flagged, and the outbreak method in `docs/OUTBREAK_DETECTION_METHOD.md` decides whether they count. This needs a decision from whoever owns the outbreak logic.

---

## 5. Implementation steps

Each step is its own branch off `develop` and merges back separately.

| # | Branch | Work | Depends on |
|---|---|---|---|
| 1 | `feature/camerax-capture` | Replace the `TakePicture` intent with a CameraX `LeafCaptureFragment` (single shot, no checks yet). Keep the gallery path as it is. | none |
| 2 | `feature/leaf-outline-overlay` | `LeafOutlineOverlayView`, outline → sensor coordinate mapping, crop-to-outline (behind a flag until §6.2 passes). | 1 |
| 3 | `feature/capture-quality-gate` | `ImageAnalysis` checks (gate, blur, light, steady), `capture_config.json`, disabled shutter, hint text. Gallery outline-fit screen. | 2 |
| 4 | `feature/multi-view-aggregator` | `MultiViewAggregator` and unit tests. Pure Kotlin, no UI. Can run alongside 1–3. | none |
| 5 | `feature/multi-photo-session` | 3-shot session UI, thumbnails, retake, DB v4 migration, result sheet `UNCERTAIN` state, history detail showing all photos. | 3, 4 |
| 6 | `feature/multi-photo-sync` | Convex fields, sharing changes, outbreak-map filtering. | 5 |

---

## 6. Testing and validation

### 6.1 Automated
- **Unit (`src/test`)**: `MultiViewAggregator` (all agree, split 2/1, all different, below-threshold), quality-check maths (blur and light on synthetic bitmaps), config parsing.
- **Instrumented (`src/androidTest`)**: migration v3 → v4 keeps old records (`imageCount = 1`), `scan_images` cascade delete, aggregation run on the bundled sample leaf images (`EmulatorLeafImagesTest` style).

### 6.2 ML check (before turning on crop and multi-photo by default)
Public datasets rarely have several photos of the *same* leaf. Proposal:
- Simulate it: for each held-out test image, make 3 views (full, zoomed lesion-area crop, other-half crop). Compare single-image accuracy against the 3-view average. Also compare full-frame against outline crop.
- **Field set**: collect a small set of real Bukidnon leaves, 3 photos each, labelled by an agronomist. This is the only reliable test.
- Pick `T_conf` and the blur and light thresholds from that data, then record them in `claude/09_ML_MODEL_CONTRACT.md`.

### 6.3 Field and usability
- Time per scan with 3 photos (target: under 45 s for a first-time farmer).
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
| Farmers find 3 photos tedious | Allow 2 as the minimum, and skip shot 3 when the first 2 agree with high confidence (decision needed) |

---

## 8. Open questions (need a decision)

1. **Number of photos:** fixed 3, or 2 minimum with an "add another" option?
2. **Early stop:** can the session end after 2 photos if both agree strongly?
3. **Gallery:** keep it (with the outline-fit step), allow several gallery images per leaf, or remove gallery for diagnosis entirely?
4. **Cloud upload:** main photo only, or all photos when sharing?
5. **Outbreak map:** do `UNCERTAIN` scans count?
6. **Underside shot:** do agronomists want an underside photo (useful for some diseases), or only the top of the leaf?
7. **Language:** do the shot prompts and hints need Cebuano/Filipino versions from the start?
