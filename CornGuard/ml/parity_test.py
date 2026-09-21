"""
Android Parity Test, steps 1-2 of claude/09_ML_MODEL_CONTRACT.md's "Android Parity Test":

    1. Run Python/Keras inference.
    2. Run TFLite inference in Python (reference interpreter).
    3. Run Android TFLite inference.               <- NOT done here, see note below.
    4. Compare predicted class and probability values within an agreed tolerance.

This script only covers steps 1, 2, and 4 for those two. Step 3 requires the actual Android app
(an instrumented test running the real TFLite interpreter on-device or in an Android-runtime
emulator) — that belongs in the Kotlin test suite (feature/model-app-validation, owned by Acenas,
reviewed by Ligue), not in this Python pipeline. Running this script and getting a pass here is
necessary but not sufficient for the contract's parity requirement.

Never executed — see ml/README.md.

Usage (run from the ml/ directory):
    python parity_test.py --keras-model-path artifacts/v1-baseline/model.keras \
        --tflite-model-path artifacts/v1-baseline/cornguard_mobilenetv2_v1.tflite \
        --reference-images path/to/some.jpg path/to/other.jpg
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np
import tensorflow as tf

import config


def _load_and_preprocess(image_path: Path) -> np.ndarray:
    raw = tf.io.read_file(str(image_path))
    image = tf.image.decode_jpeg(raw, channels=config.IMAGE_CHANNELS)
    image = tf.image.resize(image, config.IMAGE_SIZE)
    return tf.expand_dims(image, axis=0).numpy()


def _keras_predict(model: tf.keras.Model, image: np.ndarray) -> np.ndarray:
    return model.predict(image, verbose=0)[0]


def _tflite_predict(interpreter: tf.lite.Interpreter, image: np.ndarray) -> np.ndarray:
    input_details = interpreter.get_input_details()[0]
    output_details = interpreter.get_output_details()[0]
    interpreter.set_tensor(input_details["index"], image.astype(input_details["dtype"]))
    interpreter.invoke()
    return interpreter.get_tensor(output_details["index"])[0]


def run_parity_test(
    keras_model_path: Path,
    tflite_model_path: Path,
    reference_images: list[Path],
    probability_tolerance: float,
) -> list[dict]:
    keras_model = tf.keras.models.load_model(keras_model_path)
    interpreter = tf.lite.Interpreter(model_path=str(tflite_model_path))
    interpreter.allocate_tensors()

    results = []
    for image_path in reference_images:
        image = _load_and_preprocess(image_path)

        keras_probs = _keras_predict(keras_model, image)
        tflite_probs = _tflite_predict(interpreter, image)

        keras_class = int(np.argmax(keras_probs))
        tflite_class = int(np.argmax(tflite_probs))
        max_prob_delta = float(np.max(np.abs(keras_probs - tflite_probs)))

        results.append(
            {
                "image": str(image_path),
                "keras_class_index": keras_class,
                "keras_class": config.CLASS_ORDER[keras_class],
                "tflite_class_index": tflite_class,
                "tflite_class": config.CLASS_ORDER[tflite_class],
                "same_predicted_class": keras_class == tflite_class,
                "max_probability_delta": max_prob_delta,
                "within_tolerance": max_prob_delta <= probability_tolerance,
            }
        )
    return results


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--keras-model-path", type=Path, required=True)
    parser.add_argument("--tflite-model-path", type=Path, required=True)
    parser.add_argument("--reference-images", type=Path, nargs="+", required=True)
    parser.add_argument(
        "--probability-tolerance",
        type=float,
        default=0.01,
        help="Max allowed |keras_prob - tflite_prob| per class before this is flagged as a mismatch.",
    )
    parser.add_argument("--output-path", type=Path, default=None)
    args = parser.parse_args()

    results = run_parity_test(
        args.keras_model_path, args.tflite_model_path, args.reference_images, args.probability_tolerance
    )

    print(json.dumps(results, indent=2))
    failures = [r for r in results if not r["same_predicted_class"] or not r["within_tolerance"]]
    if failures:
        print(f"\n{len(failures)}/{len(results)} reference image(s) failed parity — "
              "per the contract, do not accept this model integration until these are resolved.")
    else:
        print(f"\nAll {len(results)} reference image(s) passed Python/Keras vs. TFLite parity. "
              "Step 3 (on-device Android TFLite inference) still required before acceptance.")

    if args.output_path:
        args.output_path.write_text(json.dumps(results, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
