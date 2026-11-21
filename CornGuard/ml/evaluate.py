"""
Evaluates a trained Keras model on the held-out test split, per
claude/09_ML_MODEL_CONTRACT.md "Required Evaluation": accuracy, macro precision/recall/F1,
confusion matrix, held-out test set only, field images evaluated separately.

Never executed — see ml/README.md. This script does not print or invent any metric value; every
number in its output comes from actually running the given model against the given test split.

Usage (run from the ml/ directory, after train.py):
    python evaluate.py --model-path artifacts/v1-baseline/model.keras --run-id v1-baseline
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import tensorflow as tf
from sklearn.metrics import classification_report, confusion_matrix

import config
from dataset import build_split_dataset, load_manifest
# Unused directly, but importing it runs @keras.saving.register_keras_serializable on
# MobileNetV2Preprocess — required before tf.keras.models.load_model can reconstruct a saved
# model that uses it, or loading fails with "Could not locate class 'MobileNetV2Preprocess'".
import model  # noqa: F401


def _predict(model: tf.keras.Model, ds: tf.data.Dataset) -> tuple[np.ndarray, np.ndarray]:
    y_true = []
    y_pred = []
    for images, labels in ds:
        probabilities = model.predict(images, verbose=0)
        y_pred.extend(np.argmax(probabilities, axis=1).tolist())
        y_true.extend(labels.numpy().tolist())
    return np.array(y_true), np.array(y_pred)


def _report(y_true: np.ndarray, y_pred: np.ndarray) -> dict:
    report = classification_report(
        y_true,
        y_pred,
        labels=list(range(config.NUM_CLASSES)),
        target_names=config.CLASS_ORDER,
        output_dict=True,
        zero_division=0,
    )
    cm = confusion_matrix(y_true, y_pred, labels=list(range(config.NUM_CLASSES)))
    return {
        "accuracy": report["accuracy"],
        "precision_macro": report["macro avg"]["precision"],
        "recall_macro": report["macro avg"]["recall"],
        "f1_macro": report["macro avg"]["f1-score"],
        "per_class": {
            class_name: report[class_name] for class_name in config.CLASS_ORDER
        },
        "confusion_matrix": cm.tolist(),
        "sample_count": int(len(y_true)),
    }


def _save_confusion_matrix_plot(cm: list, output_path: Path) -> None:
    cm_array = np.array(cm)
    fig, ax = plt.subplots(figsize=(6, 5))
    im = ax.imshow(cm_array, cmap="Blues")
    ax.set_xticks(range(config.NUM_CLASSES))
    ax.set_yticks(range(config.NUM_CLASSES))
    ax.set_xticklabels(config.CLASS_ORDER, rotation=45, ha="right")
    ax.set_yticklabels(config.CLASS_ORDER)
    ax.set_xlabel("Predicted")
    ax.set_ylabel("True")
    for i in range(config.NUM_CLASSES):
        for j in range(config.NUM_CLASSES):
            ax.text(j, i, str(cm_array[i, j]), ha="center", va="center")
    fig.colorbar(im)
    fig.tight_layout()
    fig.savefig(output_path)
    plt.close(fig)


def evaluate(model_path: Path, run_id: str, manifest_path: Path, dataset_root: Path, artifacts_dir: Path) -> dict:
    model = tf.keras.models.load_model(model_path)
    manifest = load_manifest(manifest_path)

    test_ds = build_split_dataset(manifest, "test", dataset_root, shuffle=False, augment=False)
    y_true, y_pred = _predict(model, test_ds)
    report = _report(y_true, y_pred)

    run_dir = artifacts_dir / run_id
    run_dir.mkdir(parents=True, exist_ok=True)

    with (run_dir / "evaluation_report.json").open("w", encoding="utf-8") as f:
        json.dump(report, f, indent=2)
    _save_confusion_matrix_plot(report["confusion_matrix"], run_dir / "confusion_matrix.png")

    print(json.dumps({k: v for k, v in report.items() if k not in ("confusion_matrix", "per_class")}, indent=2))
    print(f"Meets accuracy threshold ({config.MIN_ACCURACY}): {report['accuracy'] >= config.MIN_ACCURACY}")
    print(f"Meets F1-macro threshold ({config.MIN_F1_MACRO}): {report['f1_macro'] >= config.MIN_F1_MACRO}")

    # Field images evaluated separately, per the contract — only meaningful once local/field rows
    # actually exist in the test split; an empty result here is correct, not an error, until then.
    field_rows = manifest[(manifest["split"] == "test") & (manifest["source_type"] == "local")]
    if not field_rows.empty:
        field_ds = build_split_dataset(
            manifest[manifest["sample_id"].isin(field_rows["sample_id"])],
            "test",
            dataset_root,
            shuffle=False,
            augment=False,
        )
        field_y_true, field_y_pred = _predict(model, field_ds)
        field_report = _report(field_y_true, field_y_pred)
        with (run_dir / "evaluation_report_field.json").open("w", encoding="utf-8") as f:
            json.dump(field_report, f, indent=2)
        print(f"Field-image subset evaluated separately: {field_report['sample_count']} samples "
              f"-> written to evaluation_report_field.json")
    else:
        print("No local/field-sourced images in the test split yet — skipping the separate field "
              "evaluation report required by claude/09_ML_MODEL_CONTRACT.md.")

    return report


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model-path", type=Path, required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--manifest-path", type=Path, default=config.MANIFEST_PATH)
    parser.add_argument("--dataset-root", type=Path, default=Path("."))
    parser.add_argument("--artifacts-dir", type=Path, default=config.ARTIFACTS_DIR)
    args = parser.parse_args()

    evaluate(args.model_path, args.run_id, args.manifest_path, args.dataset_root, args.artifacts_dir)


if __name__ == "__main__":
    main()
