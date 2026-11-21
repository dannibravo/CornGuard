"""
Exports the corn / not-corn gate (train_corn_gate.py) to TFLite and checks the converted model
against the Keras original on the gate's held-out test split.

Output, next to the Keras model in artifacts/<run-id>/:
    <model-version>.tflite                float32, raw [0, 255] RGB input, same as the disease model
    labels_<model-version>.json           ["corn", "not_corn"] with their output indices
    <model-version>_export_info.json      size, sha256, TFLite test metrics and Keras parity

The gate's output is a 2-way softmax; index 0 is p(corn). The app treats a photo as corn when
p(corn) >= threshold (default 0.5, the threshold the test metrics were measured at).

Usage (from the ml/ directory):
    python export_corn_gate.py
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
from pathlib import Path

import numpy as np
import tensorflow as tf

import config
import model  # noqa: F401  (registers MobileNetV2Preprocess for load_model)
from export_tflite import convert_to_tflite

GATE_CLASSES = ["corn", "not_corn"]


def _load(path: str) -> np.ndarray:
    raw = tf.io.read_file(path)
    image = tf.io.decode_image(raw, channels=config.IMAGE_CHANNELS, expand_animations=False)
    return tf.expand_dims(tf.image.resize(image, config.IMAGE_SIZE), 0).numpy()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--run-id", default="corn-gate-v1")
    parser.add_argument("--model-version", default="cornguard_corn_gate_v1")
    parser.add_argument("--threshold", type=float, default=0.5)
    parser.add_argument("--artifacts-dir", type=Path, default=Path(config.ARTIFACTS_DIR))
    args = parser.parse_args()

    run_dir = args.artifacts_dir / args.run_id
    keras_path = run_dir / "model.keras"

    tflite_bytes = convert_to_tflite(keras_path, quantize=False)
    tflite_path = run_dir / f"{args.model_version}.tflite"
    tflite_path.write_bytes(tflite_bytes)
    checksum = hashlib.sha256(tflite_bytes).hexdigest()

    labels_path = run_dir / f"labels_{args.model_version}.json"
    labels_path.write_text(
        json.dumps([{"index": i, "label": name} for i, name in enumerate(GATE_CLASSES)], indent=2),
        encoding="utf-8",
    )

    # TFLite vs Keras on every held-out test image of the gate.
    keras_model = tf.keras.models.load_model(keras_path)
    interpreter = tf.lite.Interpreter(model_content=tflite_bytes)
    interpreter.allocate_tensors()
    inp = interpreter.get_input_details()[0]
    out = interpreter.get_output_details()[0]

    with (run_dir / "gate_manifest.csv").open(encoding="utf-8") as f:
        test_rows = [r for r in csv.DictReader(f) if r["split"] == "test"]

    correct = agree = 0
    max_delta = 0.0
    for row in test_rows:
        image = _load(row["relative_path"])
        p_keras = float(keras_model(image, training=False).numpy()[0, 0])
        interpreter.set_tensor(inp["index"], image.astype(inp["dtype"]))
        interpreter.invoke()
        p_tflite = float(interpreter.get_tensor(out["index"])[0, 0])

        max_delta = max(max_delta, abs(p_keras - p_tflite))
        tflite_is_corn = p_tflite >= args.threshold
        agree += tflite_is_corn == (p_keras >= args.threshold)
        correct += tflite_is_corn == (row["label"] == "corn")

    info = {
        "model_version": args.model_version,
        "source_keras_model": str(keras_path),
        "quantization_mode": "none_float32",
        "tflite_size_bytes": len(tflite_bytes),
        "sha256_checksum": checksum,
        "input": "1x224x224x3 float32, raw RGB [0, 255] (the model normalizes internally)",
        "output": "1x2 softmax, index 0 = corn, index 1 = not_corn",
        "threshold": args.threshold,
        "tflite_test_evaluation": {
            "sample_count": len(test_rows),
            "accuracy": correct / len(test_rows),
        },
        "keras_parity": {
            "same_decision": agree,
            "sample_count": len(test_rows),
            "max_abs_p_corn_delta": max_delta,
        },
    }
    (run_dir / f"{args.model_version}_export_info.json").write_text(json.dumps(info, indent=2), encoding="utf-8")

    print(f"Wrote {tflite_path} ({len(tflite_bytes)} bytes, sha256={checksum})")
    print(f"TFLite test accuracy: {correct}/{len(test_rows)} = {correct / len(test_rows):.4%}")
    print(f"Keras parity: same decision on {agree}/{len(test_rows)}, max |p_corn delta| = {max_delta:.2e}")
    if agree != len(test_rows) or max_delta > 0.01:
        raise SystemExit("PARITY FAILED: do not ship this TFLite file.")


if __name__ == "__main__":
    main()
