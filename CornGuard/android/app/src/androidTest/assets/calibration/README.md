# Model calibration images

`ModelNormalizationCalibrationTest` runs the bundled model on the images here under every pixel
normalization mode, and checks that `app/src/main/assets/model_config.json` uses the best one.

Add about 5 or more real leaf photos per class (ideally from the dataset the model was trained on),
in one folder per disease code:

```
calibration/
  northern_leaf_blight/   <- the model's "Blight" class
  common_rust/
  gray_leaf_spot/
  healthy/
```

`.jpg`, `.jpeg` and `.png` are picked up. Then run:

```
cd android
./gradlew connectedDebugAndroidTest --tests "com.cornguard.app.model.ModelNormalizationCalibrationTest"
```

The per-mode accuracy table is printed in the failure message, or in logcat under the tag
`ModelCalibration`. If another mode wins, set `"normalization"` in `model_config.json` to it
(`minus_one_to_one`, `zero_to_one` or `raw_0_255`).
