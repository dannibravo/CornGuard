# CORNGUARD — ML MODEL CONTRACT v0.1

This file controls the handoff between the Python/TensorFlow training pipeline and the Kotlin/TensorFlow Lite Android application.

**Owner:** Acenas

**Primary consumer:** Ligue

A TFLite model is not integration-ready until every field in the release contract below is filled with actual final values.

## Model Purpose

Classify a corn leaf image into one of four classes:

- Common Rust
- Gray Leaf Spot
- Northern Leaf Blight
- Healthy

## Base Architecture

- MobileNetV2
- Transfer learning from ImageNet-pretrained weights
- TensorFlow / Keras training
- TensorFlow Lite mobile deployment

## Dataset Baseline

Public dataset counts stated in the manuscript:

- Common Rust: 1,306
- Gray Leaf Spot: 574
- Northern Leaf Blight: 1,146
- Healthy: 1,162
- Total: 4,188

Local Bukidnon field images should be incorporated according to the final research protocol and must be separately traceable in the dataset manifest.

## Required Dataset Manifest

For every training run intended for evaluation, record:

- dataset version
- source repository/source farm
- original filename or stable sample ID
- class label
- public/local source indicator
- location/source barangay for local data where ethically/legally appropriate
- expert-validation status for local samples
- split assignment
- augmentation eligibility

Do not leak test images into training through duplicate or augmented copies.

## Split Decision Gate

The study contains two different testing-split statements:

- 70% train / 15% validation / 15% test
- test set minimum 20%

The final split must be approved and recorded before final evaluation claims are made.

## Input Contract

### Tensor shape

Expected manuscript target:

`224 x 224 x 3`

### Color order

RGB unless the final training pipeline explicitly changes it.

### Input type

Must be recorded for final model:

- float32, or
- quantized integer type

### Normalization

**UNRESOLVED SOURCE CONFLICT:** The manuscript states both `[-1, 1]` MobileNetV2 preprocessing and `[0, 1]` normalization in different sections.

Acenas must freeze one exact transformation that matches the trained/exported model and provide:

- formula
- code reference
- expected pixel range
- a small test vector/image with expected preprocessed values

Ligue must not implement final preprocessing until this is frozen.

## Training Augmentation

Training-only augmentations described in the manuscript:

- random horizontal flip
- random vertical flip
- random rotation ±15 degrees
- random zoom ±10%
- brightness scaling approximately 80%–120%

Validation and test samples must not receive random augmentation.

## Class Imbalance Handling

Gray Leaf Spot is underrepresented in the listed dataset. The manuscript specifies balanced class weights using `compute_class_weight` or equivalent.

The exact class weights used for every final training run must be logged.

## Output Contract

Final output must provide four class probabilities or scores.

Acenas must publish the exact class-index order, for example only after confirmation:

- index 0 -> `<class>`
- index 1 -> `<class>`
- index 2 -> `<class>`
- index 3 -> `<class>`

Do not infer class order alphabetically unless that is exactly how the exported model was trained.

## DetectionResult Contract

The Android model wrapper returns:

- `class_index`
- `disease_code`
- `display_label`
- `confidence`
- `probabilities`
- `model_version`
- `inference_time_ms`

The confidence shown to the user must be derived from the same output probability semantics used in evaluation.

## Model Version Package

Each model release must include:

- `cornguard_mobilenetv2_<version>.tflite`
- `labels_<version>.json` or equivalent
- `model_metadata_<version>.json`
- evaluation report
- confusion matrix
- checksum

Suggested metadata fields:

- `model_version`
- `architecture`
- `dataset_version`
- `training_date`
- `input_shape`
- `input_dtype`
- `normalization`
- `class_order`
- `accuracy`
- `precision_macro`
- `recall_macro`
- `f1_macro`
- per-class metrics
- TFLite size
- quantization mode
- known limitations

## Required Evaluation

Before release candidate approval:

- accuracy >= 90%
- F1-score >= 0.88 if final NFR retains this requirement
- precision reported
- recall reported
- confusion matrix produced
- held-out test set only
- field images evaluated separately
- TFLite model re-evaluated after conversion

## Android Parity Test

For a fixed set of reference images:

1. Run Python/Keras inference.
2. Run TFLite inference in Python or reference interpreter.
3. Run Android TFLite inference.
4. Compare predicted class and probability values within an agreed tolerance.

A model integration is not accepted if Android produces a different class because of preprocessing, color order, tensor shape, quantization, or label mapping.

## Performance Target

Use <2.0 seconds as the engineering target for end-to-end inference/result behavior until the manuscript's <2 vs <3 second wording is harmonized.

Record separately:

- image preprocessing time
- raw TFLite inference time
- postprocessing time
- total result time
- device model/specification

## Field Validation

The panel requires actual field validation and qualified disease validators.

Acenas must coordinate evidence showing:

- where field images were captured
- environmental conditions
- ground-truth validation method
- validator qualification
- predicted result
- confidence
- correct/incorrect result
- notes on failure cause where known

## Treatment Guidance Boundary

The model predicts disease class only. It does not generate treatment advice. Treatment content comes from the local DiseaseReference knowledge base and must be validated independently.

## Model Release Gate

A model may be labeled `release-candidate` only when:

- preprocessing contract is frozen
- class order is frozen
- dataset/split is documented
- metric thresholds pass
- TFLite conversion succeeds
- Android parity test passes
- model metadata is complete
