# CornGuard ML Pipeline

**Owner:** Acenas (claude/07_TEAM_RESPONSIBILITIES_AND_FEATURE_BRANCHES.md). **Branch:**
`feature/dataset-pipeline` (Sprint 0), continuing through the later stages documented in
claude/07 and claude/11_OFFICIAL_GITHUB_WORKFLOW.md (`feature/model-baseline`,
`feature/tflite-export`, `feature/model-metadata`, etc.) as consolidated commits on this branch —
same pattern already used for the Panes and Ligue branches on this project (see the
`(continued on this branch)` sections in `android/README.md` and `firebase/README.md`).

## Status: never executed

**Every script in this directory is real, complete, runnable code — and none of it has been run.**
There is no trained model, no real accuracy number, no confusion matrix, and no field-validation
result anywhere in this repository. `ml/cornguard_ml/config.py`'s `MODEL_VERSION` is `"unassigned"`, matching
`PlaceholderCornLeafClassifier.modelVersion` on the Android side — the app has nothing to load
until a real run happens and its output is committed deliberately.

This exists so the pipeline is ready to run the moment real dataset images (public + Bukidnon
field images, per the manuscript) are available, without inventing what running it would produce.
Do not report any number from this directory as a real result unless you actually ran the
corresponding script and are looking at its actual output file.

## Two pragmatic solo-context decisions

`ml/cornguard_ml/config.py`'s module docstring explains these in full; summary:

- **D-04 (input normalization)** — the manuscript states both `[-1, 1]` MobileNetV2 preprocessing
  and `[0, 1]` normalization in different sections (claude/09_ML_MODEL_CONTRACT.md, "Normalization").
  This pipeline defaults to `[-1, 1]` (`tf.keras.applications.mobilenet_v2.preprocess_input`) so
  the code is real and runnable — **this is not Acenas's frozen decision.**
- **Train/val/test split** — the manuscript states both 70/15/15 and "test set minimum 20%"
  (claude/09_ML_MODEL_CONTRACT.md, "Split Decision Gate"). This pipeline defaults to 70/15/15.

Both are recorded as open items under D-04 in
`claude/03_SOURCE_ALIGNMENT_AND_DECISION_GATES.md` — same treatment as the D-01/D-10 continuations
already recorded there for the Firebase/Android side of this project. Whoever runs the first real
training run intended for evaluation should revisit both first.

The class order in `config.CLASS_ORDER` is similarly **unconfirmed** — it matches
`android/app/.../classifier/DiseaseCode.kt` for consistency, but claude/09_ML_MODEL_CONTRACT.md is
explicit that Acenas must publish the real trained order, and it must not be assumed
alphabetically or by convention.

## Layout

| Path | Contents |
|---|---|
| `cornguard_ml/` | Shared, importable code: `config.py`, `dataset.py`, `augmentation.py`, `model.py`, `class_weights.py`. |
| `pipeline/` | One entry point per pipeline stage (table below). Run from `ml/` as `python -m pipeline.<stage>`. |
| `experiments/` | One folder per audit/experiment: its code, raw run logs, CSV outputs and reports. See `experiments/README.md`. |
| `releases/` | Tracked provenance for every model shipped in the app (labels, checksum, export info). See `releases/README.md`. |
| `data/` | Raw images and generated manifests. Git-ignored except `data/raw/.gitkeep`. |
| `artifacts/` | Per-run training output (`.keras`, reports). Git-ignored scratch; copy what a release needs into `releases/`. |

All commands assume `ml/` is the working directory: manifests store image paths relative to it
(e.g. `data\raw\common_rust\...`). `pip install -e .` is optional — it only makes `cornguard_ml`
importable from other directories (IDEs, notebooks).

## Pipeline stages

Each script does exactly one stage, so a stage can be re-run independently without repeating the
ones before it.

| Script | Stage | Reads | Writes |
|---|---|---|---|
| `pipeline/prepare_dataset.py` | Build dataset manifest + stratified split | `data/raw/<class>/<public\|local>/*` | `data/dataset_manifest.csv` |
| `pipeline/train.py` | Head training + fine-tuning | manifest, raw images | `artifacts/<run_id>/model.keras`, `class_weights.json`, `training_history.json`, `run_metadata.json` |
| `pipeline/evaluate.py` | Test-set evaluation | manifest, `model.keras` | `evaluation_report.json`, `confusion_matrix.png`, `evaluation_report_field.json` (if field images exist) |
| `pipeline/export_tflite.py` | TFLite conversion + re-evaluation | `model.keras` | `<model_version>.tflite`, `labels_<model_version>.json`, `<model_version>_export_info.json` |
| `pipeline/export_metadata.py` | Assemble release metadata | all of the above | `model_metadata_<model_version>.json` |
| `pipeline/parity_test.py` | Python/Keras vs. TFLite parity (contract steps 1-2 of 4) | `model.keras`, `.tflite`, reference images | parity report (stdout / `--output-path`) |

`export_metadata.py` refuses to run if any upstream artifact is missing — it will not fill a
metric field with a guessed number.

## Running the full pipeline

```bash
cd ml
pip install -r requirements.txt

# 1. Populate data/raw/<class>/public/*.jpg and/or data/raw/<class>/local/*.jpg first —
#    this pipeline does not download or generate images.
python -m pipeline.prepare_dataset --dataset-version v1

python -m pipeline.train --run-id v1-baseline

python -m pipeline.evaluate --model-path artifacts/v1-baseline/model.keras --run-id v1-baseline

python -m pipeline.export_tflite --model-path artifacts/v1-baseline/model.keras \
    --run-id v1-baseline --model-version cornguard_mobilenetv2_v1

python -m pipeline.export_metadata --run-id v1-baseline --model-version cornguard_mobilenetv2_v1

python -m pipeline.parity_test --keras-model-path artifacts/v1-baseline/model.keras \
    --tflite-model-path artifacts/v1-baseline/cornguard_mobilenetv2_v1.tflite \
    --reference-images path/to/some/reference/images/*.jpg
```

## What this does NOT do

- **Does not collect, download, or fabricate dataset images.** `data/raw/` is empty (only a
  `.gitkeep`) — real images are Acenas's to source per the manuscript's dataset protocol.
- **Does not perform Android on-device inference.** `parity_test.py` only covers steps 1-2 of the
  contract's 4-step Android Parity Test; step 3 (real on-device TFLite inference) belongs in the
  Kotlin instrumented test suite (`feature/model-app-validation`).
- **Does not perform field validation.** claude/09_ML_MODEL_CONTRACT.md's "Field Validation"
  section requires qualified disease validators and real farm evidence — nothing here can stand
  in for that.
- **Does not decide D-04 or the split ratio for real.** See above.

## Local field image metadata

If Bukidnon field images are added under `data/raw/<class>/local/`, describe them in
`data/local_image_metadata.csv` (not committed as sample data here — it doesn't exist yet):

```csv
filename,source_farm,barangay,expert_validated
IMG_0001.jpg,,Malaybalay Poblacion,False
```

Any local image without a matching row gets blank farm/barangay fields and
`expert_validated=False` in the manifest — `prepare_dataset.py` never invents this information.
