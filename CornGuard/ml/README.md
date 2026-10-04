# CornGuard disease model — training

`CORNGUARD_EDA_&_TRAINING_v2.ipynb` is the Google Colab notebook that trained the model the app ships.

| | |
|---|---|
| Model | MobileNetV2 transfer learning, 4 classes: Blight (Northern Leaf Blight), Common Rust, Gray Leaf Spot, Healthy |
| Model version | `cornguard_mobilenetv2_v3` |
| App file | `android/app/src/main/assets/model.tflite` (dynamic-range quantised, 2.9 MB) |
| SHA-256 | `DFE14EFC796C04729B77DAB416052FA5BB772CA35D516DD7E265A80B50CB47D1` |
| Input | 224×224 RGB, centre square crop, bilinear resize, scaled to [-1, 1] (`model_config.json`: `minus_one_to_one`) |
| Dataset | Kaggle corn / maize leaf disease dataset (4,188 images), on Google Drive `Acenas_Dataset/data` |

## Files: where the app's model came from

Everything the notebook produced for the shipped model. The app-ready files are in this folder; the large training
checkpoints are attached to the GitHub release instead of the repository (they are git-ignored).

| File | What it is | SHA-256 | Where |
|---|---|---|---|
| `cornguard_v3_model.tflite` | Final exported TFLite model (dynamic-range quantised) | `dfe14efc796c0472…` | here |
| `android_assets/model.tflite` | The same model, as copied into the app (`android/app/src/main/assets/`) | `dfe14efc796c0472…` | here |
| `android_assets/labels.json` | Output index → disease code / display label used by the app | `a775c3ea41ad7808…` | here |
| `android_assets/model_config.json` | `input_size` 224, `normalization` `minus_one_to_one` | `d57c9fb3c1705cf7…` | here |
| `android_assets/model_version.txt` | `cornguard_mobilenetv2_v3` | `ec1f8275c6695cd7…` | here |
| `labels.json` | Class index → dataset folder name (`Blight`, `Common_Rust`, `Gray_Leaf_Spot`, `Healthy`) | `94f80123df378d1e…` | here |
| `cornguard_v3_final.keras` | Final Keras model (best fine-tuned weights) that the TFLite file was converted from — 31 MB | `71c484d45ea31195…` | [release v0.2.0-debug](https://github.com/dannibravo/CornGuard/releases/download/v0.2.0-debug/cornguard_v3_final.keras) |
| `cornguard_v3_finetuned_best.keras` | Best checkpoint of phase 2 (fine-tuning) — 31 MB | `d52e2f15b09756e4…` | [release v0.2.0-debug](https://github.com/dannibravo/CornGuard/releases/download/v0.2.0-debug/cornguard_v3_finetuned_best.keras) |
| `cornguard_v3_head_best.keras` | Best checkpoint of phase 1 (classification head only) — 14 MB | `b6d851b906cc8f64…` | [release v0.2.0-debug](https://github.com/dannibravo/CornGuard/releases/download/v0.2.0-debug/cornguard_v3_head_best.keras) |

To use a checkpoint, download it into this folder and load it with
`keras.models.load_model("ml/cornguard_v3_final.keras")` (TensorFlow 2.20 / Keras 3).

## Test results (627-image held-out test set)

Accuracy **96.65 %**, macro-F1 **0.956** (TFLite: 96.65 %, macro-F1 0.9556).

| Class | Precision | Recall | F1 |
|---|---|---|---|
| Blight (NLB) | 0.958 | 0.930 | 0.944 |
| Common Rust | 0.990 | 0.995 | 0.992 |
| Gray Leaf Spot | 0.867 | 0.918 | 0.891 |
| Healthy | 1.000 | 0.994 | 0.997 |

Most remaining errors are Northern Leaf Blight ↔ Gray Leaf Spot. Accuracy on real field (phone) photos is lower
than on the dataset's lab photos; more labelled field photos are the main way to improve it.

## Retraining

1. Open the notebook in Colab with a T4 GPU (*Runtime → Change runtime type*) and run all cells. The dataset is read
   from Google Drive `Acenas_Dataset/data`.
2. Outputs go to Drive `Acenas_Dataset/models/`: the `.keras` checkpoints (too large for git — attach them to a
   GitHub release) and `android_assets/` (`model.tflite`, `labels.json`, `model_config.json`, `model_version.txt`).

## Updating the app's model

1. Copy the four files from `android_assets/` into `android/app/src/main/assets/`.
2. Update `EXPECTED_MODEL_SHA256` / `EXPECTED_MODEL_VERSION` in
   `android/app/src/androidTest/java/com/cornguard/app/model/EmulatorLeafImagesTest.kt` and the version in
   `TfliteCornLeafClassifierTest.kt`.
3. Rebuild and run the instrumented tests (`connectedDebugAndroidTest`).
4. The app needs TFLite 2.17 or newer to load models converted with TensorFlow 2.20.
