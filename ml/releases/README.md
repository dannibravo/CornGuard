# Model releases

One folder per model version that has been shipped in the Android app
(`android/app/src/main/assets/`). Each folder is the tracked, reviewable record of *which* model
shipped and *where it came from*. The `.tflite` binary itself lives only in the Android assets,
and checkpoints (`.keras`) stay in the git-ignored `ml/artifacts/<run_id>/`.

## What a release folder should contain

| File | Source |
|---|---|
| `SHA256SUMS` | `sha256sum` of the shipped `.tflite` and of the labels file |
| `labels_<model_version>.json` | `ml/artifacts/<run_id>/labels_<model_version>.json` (identical to `android/.../assets/labels.json`) |
| `<model_version>_export_info.json` | `ml/artifacts/<run_id>/`, written by `pipeline/export_tflite.py` (includes the post-conversion TFLite evaluation) |
| `model_metadata_<model_version>.json` | `ml/artifacts/<run_id>/`, written by `pipeline/export_metadata.py` |
| `evaluation_report.json` | `ml/artifacts/<run_id>/`, written by `pipeline/evaluate.py` |

To verify the app ships the recorded model:

```bash
sha256sum android/app/src/main/assets/model.tflite   # compare with releases/<model_version>/SHA256SUMS
```

## Releases

| Model version | Run ID | Status of this folder |
|---|---|---|
| `cornguard_mobilenetv2_v1` | `v1-baseline` | **Incomplete.** `SHA256SUMS` and the labels file were taken from the shipped Android assets on 2026-11-21. The export info, metadata and evaluation report exist only in the git-ignored `ml/artifacts/v1-baseline/` on the machine that ran the export. Whoever has that folder: copy those three files in. |
