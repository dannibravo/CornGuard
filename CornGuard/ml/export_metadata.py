"""
Assembles model_metadata_<version>.json from the real artifacts a prior train.py + evaluate.py +
export_tflite.py run already produced, per claude/09_ML_MODEL_CONTRACT.md "Model Version Package"
/ "suggested metadata fields". Refuses to run if any required upstream artifact is missing —
it never fills a metric field with a guessed or placeholder number.

Usage (run from the ml/ directory, after train.py, evaluate.py, and export_tflite.py):
    python export_metadata.py --run-id v1-baseline --model-version cornguard_mobilenetv2_v1
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import pandas as pd

import config

KNOWN_LIMITATIONS = [
    "Trained on the manuscript's public dataset baseline; Bukidnon local field images are "
    "incorporated only to the extent recorded in the dataset manifest's source_type column for "
    "this dataset_version — check that before trusting field performance.",
    "Class order and D-04 preprocessing normalization are a pragmatic solo-context default "
    "(see ml/config.py's module docstring), not a value Acenas has formally frozen. Confirm both "
    "before treating this build as integration-ready, per claude/09_ML_MODEL_CONTRACT.md's Model "
    "Release Gate.",
    "No independent field validation (claude/09_ML_MODEL_CONTRACT.md's Field Validation section) "
    "has been performed on this run unless a separate field-validation record says otherwise.",
    "Android parity test (Python/Keras vs. TFLite vs. on-device TFLite) has not been run for this "
    "export unless ml/parity_test.py has been executed and its output attached to this release.",
]


def _load_json(path: Path) -> dict:
    if not path.exists():
        raise SystemExit(
            f"Missing required artifact: {path}. Run the full pipeline in order "
            "(prepare_dataset.py -> train.py -> evaluate.py -> export_tflite.py) before "
            "assembling metadata — this script will not fabricate a metric it can't find."
        )
    return json.loads(path.read_text(encoding="utf-8"))


def build_metadata(run_id: str, model_version: str, artifacts_dir: Path, manifest_path: Path) -> dict:
    run_dir = artifacts_dir / run_id
    run_metadata = _load_json(run_dir / "run_metadata.json")
    evaluation_report = _load_json(run_dir / "evaluation_report.json")
    export_info = _load_json(run_dir / f"{model_version}_export_info.json")

    manifest_df = pd.read_csv(manifest_path)
    dataset_versions = manifest_df["dataset_version"].unique().tolist()
    dataset_version = dataset_versions[0] if len(dataset_versions) == 1 else dataset_versions

    metadata = {
        "model_version": model_version,
        "architecture": run_metadata["architecture"],
        "dataset_version": dataset_version,
        "training_date": run_metadata["trained_at_utc"],
        "input_shape": run_metadata["input_shape"],
        "input_dtype": config.INPUT_DTYPE,
        "normalization": run_metadata["normalization_mode"],
        "class_order": run_metadata["class_order"],
        "accuracy": evaluation_report["accuracy"],
        "precision_macro": evaluation_report["precision_macro"],
        "recall_macro": evaluation_report["recall_macro"],
        "f1_macro": evaluation_report["f1_macro"],
        "per_class_metrics": evaluation_report["per_class"],
        "tflite_size_bytes": export_info["tflite_size_bytes"],
        "tflite_sha256_checksum": export_info["sha256_checksum"],
        "quantization_mode": export_info["quantization_mode"],
        "tflite_reevaluation": export_info.get("tflite_evaluation"),
        "meets_accuracy_threshold": evaluation_report["accuracy"] >= config.MIN_ACCURACY,
        "meets_f1_macro_threshold": evaluation_report["f1_macro"] >= config.MIN_F1_MACRO,
        "known_limitations": KNOWN_LIMITATIONS,
    }
    return metadata


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--model-version", required=True)
    parser.add_argument("--artifacts-dir", type=Path, default=config.ARTIFACTS_DIR)
    parser.add_argument("--manifest-path", type=Path, default=config.MANIFEST_PATH)
    args = parser.parse_args()

    metadata = build_metadata(args.run_id, args.model_version, args.artifacts_dir, args.manifest_path)

    output_path = args.artifacts_dir / args.run_id / f"model_metadata_{args.model_version}.json"
    output_path.write_text(json.dumps(metadata, indent=2), encoding="utf-8")
    print(f"Wrote {output_path}")

    if not metadata["meets_accuracy_threshold"] or not metadata["meets_f1_macro_threshold"]:
        print(
            "WARNING: this run does not meet the release thresholds in "
            "claude/09_ML_MODEL_CONTRACT.md's Required Evaluation section — do not label it "
            "release-candidate."
        )


if __name__ == "__main__":
    main()
