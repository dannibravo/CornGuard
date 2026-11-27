"""
Exports artifacts/v5-field/model.keras to TFLite and writes the app asset files.

Run from ml/:  .venv/Scripts/python v5_field/export_v5.py

Why this is a separate step: in TF 2.16 with Keras 3, TFLiteConverter.from_keras_model() on the
fine-tuned model crashed ("LLVM ERROR: Failed to infer result type(s)") at the end of
train_v5.py. Exporting to a SavedModel first (model.export) and converting with
from_saved_model() avoids that path.
"""

import json
import shutil
import sys
from pathlib import Path

import numpy as np
import tensorflow as tf

sys.path.insert(0, str(Path(__file__).resolve().parent))
import train_v5 as T  # noqa: E402  (reuses the exact eval preprocessing and constants)


def main():
    out = T.OUT_DIR
    model = tf.keras.models.load_model(out / "model.keras")

    saved = out / "saved_model"
    if saved.exists():
        shutil.rmtree(saved)
    model.export(str(saved))

    converter = tf.lite.TFLiteConverter.from_saved_model(str(saved))
    converter.optimizations = [tf.lite.Optimize.DEFAULT]  # dynamic-range, same as v4
    tfl = converter.convert()
    (out / "model.tflite").write_bytes(tfl)
    print(f"model.tflite: {len(tfl) / 1e6:.2f} MB")

    rows = T.load_manifest()
    for r in rows:
        r["cached"] = str(T.CACHE_DIR / f"{r['sha256']}.jpg")
    test = [r for r in rows if r["split"] == "test"]

    it = tf.lite.Interpreter(model_content=tfl)
    it.allocate_tensors()
    inp, outp = it.get_input_details()[0], it.get_output_details()[0]
    print("input", inp["shape"], inp["dtype"], "output", outp["shape"], outp["dtype"])

    keras_proba = model.predict(T.make_dataset(test, False), verbose=0)
    tfl_proba = []
    for x, _ in T.make_dataset(test, False).unbatch():
        it.set_tensor(inp["index"], x[None].numpy())
        it.invoke()
        tfl_proba.append(it.get_tensor(outp["index"])[0])
    tfl_proba = np.array(tfl_proba)
    agree = float((tfl_proba.argmax(1) == keras_proba.argmax(1)).mean())
    max_diff = float(np.abs(tfl_proba - keras_proba).max())
    y = np.array([T.CLASS_TO_IDX[r["label"]] for r in test])
    tfl_acc = float((tfl_proba.argmax(1) == y).mean())
    print(f"TFLite vs Keras: label agreement {agree:.4f}, max prob diff {max_diff:.4f}, "
          f"TFLite test accuracy {tfl_acc:.4f}")

    assets = out / "android_assets"
    assets.mkdir(exist_ok=True)
    (assets / "model.tflite").write_bytes(tfl)
    (assets / "labels.json").write_text(json.dumps(
        [{"index": i, "disease_code": T.APP_LABELS[c][0], "display_label": T.APP_LABELS[c][1]}
         for i, c in enumerate(T.CLASSES)], indent=2))
    (assets / "model_config.json").write_text(json.dumps(
        {"input_size": T.IMAGE_SIZE[0], "normalization": "minus_one_to_one"}, indent=2))
    (assets / "model_version.txt").write_text(T.MODEL_VERSION)

    report = {
        "model_version": T.MODEL_VERSION,
        "tflite_bytes": len(tfl),
        "tflite_keras_label_agreement_test": agree,
        "tflite_keras_max_prob_diff_test": max_diff,
        "tflite_test_accuracy": tfl_acc,
    }
    (out / "export_report.json").write_text(json.dumps(report, indent=2))
    print("wrote", assets)


if __name__ == "__main__":
    sys.exit(main())
