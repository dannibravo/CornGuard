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
2. Outputs go to Drive `Acenas_Dataset/models/`: the `.keras` checkpoints (not committed — too large for git) and
   `android_assets/` (`model.tflite`, `labels.json`, `model_config.json`, `model_version.txt`).

## Updating the app's model

1. Copy the four files from `android_assets/` into `android/app/src/main/assets/`.
2. Update `EXPECTED_MODEL_SHA256` / `EXPECTED_MODEL_VERSION` in
   `android/app/src/androidTest/java/com/cornguard/app/model/EmulatorLeafImagesTest.kt` and the version in
   `TfliteCornLeafClassifierTest.kt`.
3. Rebuild and run the instrumented tests (`connectedDebugAndroidTest`).
4. The app needs TFLite 2.17 or newer to load models converted with TensorFlow 2.20.
