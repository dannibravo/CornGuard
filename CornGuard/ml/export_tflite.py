"""
Converts a trained Keras model to TFLite (float32 and dynamic-range-quantized variants), then
re-evaluates the converted model on the held-out test set — per
claude/09_ML_MODEL_CONTRACT.md "TFLite model re-evaluated after conversion" and the "Model
Version Package" requirements (checksum included).

Never executed — see ml/README.md.

Usage (run from the ml/ directory, after train.py):
    python export_tflite.py --model-path artifacts/v1-baseline/model.keras \
        --run-id v1-baseline --model-version cornguard_mobilenetv2_v1
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

import numpy as np
import tensorflow as tf
from sklearn.metrics import classification_report

import config
from dataset import build_split_dataset, load_manifest


def convert_to_tflite(model_path: Path, quantize: bool) -> bytes:
    converter = tf.lite.TFLiteConverter.from_keras_model(tf.keras.models.load_model(model_path))
    if quantize:
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
    return converter.convert()


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _evaluate_tflite(tflite_bytes: bytes, manifest, dataset_root: Path) -> dict:
    interpreter = tf.lite.Interpreter(model_content=tflite_bytes)
    interpreter.allocate_tensors()
    input_details = interpreter.get_input_details()[0]
    output_details = interpreter.get_output_details()[0]

    test_ds = build_split_dataset(manifest, "test", dataset_root, batch_size=1, shuffle=False, augment=False)

    y_true = []
    y_pred = []
    for image_batch, label_batch in test_ds:
        image = tf.cast(image_batch, input_details["dtype"]).numpy()
        interpreter.set_tensor(input_details["index"], image)
        interpreter.invoke()
        probabilities = interpreter.get_tensor(output_details["index"])[0]
        y_pred.append(int(np.argmax(probabilities)))
        y_true.append(int(label_batch.numpy()[0]))

    report = classification_report(
        y_true,
        y_pred,
        labels=list(range(config.NUM_CLASSES)),
        target_names=config.CLASS_ORDER,
        output_dict=True,
        zero_division=0,
    )
    return {
        "accuracy": report["accuracy"],
        "f1_macro": report["macro avg"]["f1-score"],
        "sample_count": len(y_true),
    }


def export(
    model_path: Path,
    run_id: str,
    model_version: str,
    manifest_path: Path,
    dataset_root: Path,
    artifacts_dir: Path,
    quantize: bool,
    reevaluate: bool,
) -> Path:
    run_dir = artifacts_dir / run_id
    run_dir.mkdir(parents=True, exist_ok=True)

    tflite_bytes = convert_to_tflite(model_path, quantize=quantize)
    tflite_path = run_dir / f"{model_version}.tflite"
    tflite_path.write_bytes(tflite_bytes)

    checksum = _sha256(tflite_bytes)
    size_bytes = len(tflite_bytes)

    export_info = {
        "model_version": model_version,
        "quantization_mode": "dynamic_range" if quantize else "none_float32",
        "tflite_size_bytes": size_bytes,
        "sha256_checksum": checksum,
    }

    if reevaluate:
        manifest = load_manifest(manifest_path)
        export_info["tflite_evaluation"] = _evaluate_tflite(tflite_bytes, manifest, dataset_root)

    with (run_dir / f"{model_version}_export_info.json").open("w", encoding="utf-8") as f:
        json.dump(export_info, f, indent=2)

    labels_path = run_dir / f"labels_{model_version}.json"
    with labels_path.open("w", encoding="utf-8") as f:
        json.dump(
            [
                {"index": i, "disease_code": code, "display_label": config.DISPLAY_LABELS[code]}
                for i, code in enumerate(config.CLASS_ORDER)
            ],
            f,
            indent=2,
        )

    print(f"Wrote {tflite_path} ({size_bytes} bytes, sha256={checksum})")
    print(f"Wrote {labels_path}")
    if reevaluate:
        print(f"TFLite re-evaluation: {export_info['tflite_evaluation']}")

    return tflite_path


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model-path", type=Path, required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--model-version", required=True, help="e.g. cornguard_mobilenetv2_v1")
    parser.add_argument("--manifest-path", type=Path, default=config.MANIFEST_PATH)
    parser.add_argument("--dataset-root", type=Path, default=Path("."))
    parser.add_argument("--artifacts-dir", type=Path, default=config.ARTIFACTS_DIR)
    parser.add_argument("--quantize", action="store_true", help="Apply default dynamic-range quantization")
    parser.add_argument("--no-reevaluate", dest="reevaluate", action="store_false")
    args = parser.parse_args()

    export(
        args.model_path,
        args.run_id,
        args.model_version,
        args.manifest_path,
        args.dataset_root,
        args.artifacts_dir,
        args.quantize,
        args.reevaluate,
    )


if __name__ == "__main__":
    main()
