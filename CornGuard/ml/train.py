"""
Trains the CornGuard MobileNetV2 classifier: frozen-base head training, then a fine-tuning phase
with the top of the base unfrozen. Never executed — see ml/README.md.

Usage (run from the ml/ directory, after prepare_dataset.py):
    python train.py --run-id v1-baseline
"""

from __future__ import annotations

import argparse
import json
from datetime import datetime, timezone
from pathlib import Path

import tensorflow as tf

import config
from class_weights import compute_training_class_weights
from dataset import build_split_dataset, load_manifest
from model import build_model, compile_for_head_training, unfreeze_for_fine_tuning


def train(run_id: str, manifest_path: Path, dataset_root: Path, artifacts_dir: Path) -> Path:
    manifest = load_manifest(manifest_path)

    train_ds = build_split_dataset(manifest, "train", dataset_root, shuffle=True, augment=True)
    val_ds = build_split_dataset(manifest, "val", dataset_root, shuffle=False, augment=False)

    class_weights = compute_training_class_weights(manifest)
    print(f"Class weights: {class_weights}")

    model, base_model = build_model()
    compile_for_head_training(model)

    run_dir = artifacts_dir / run_id
    run_dir.mkdir(parents=True, exist_ok=True)

    checkpoint_path = run_dir / "best_head.keras"
    callbacks = [
        tf.keras.callbacks.ModelCheckpoint(
            filepath=str(checkpoint_path), save_best_only=True, monitor="val_accuracy"
        ),
        tf.keras.callbacks.EarlyStopping(monitor="val_accuracy", patience=3, restore_best_weights=True),
    ]

    print(f"Head training for up to {config.HEAD_EPOCHS} epochs...")
    head_history = model.fit(
        train_ds,
        validation_data=val_ds,
        epochs=config.HEAD_EPOCHS,
        class_weight=class_weights,
        callbacks=callbacks,
    )

    print(f"Fine-tuning for up to {config.FINE_TUNE_EPOCHS} epochs...")
    unfreeze_for_fine_tuning(model, base_model)
    fine_tune_checkpoint_path = run_dir / "best_fine_tuned.keras"
    fine_tune_callbacks = [
        tf.keras.callbacks.ModelCheckpoint(
            filepath=str(fine_tune_checkpoint_path), save_best_only=True, monitor="val_accuracy"
        ),
        tf.keras.callbacks.EarlyStopping(monitor="val_accuracy", patience=3, restore_best_weights=True),
    ]
    fine_tune_history = model.fit(
        train_ds,
        validation_data=val_ds,
        epochs=config.FINE_TUNE_EPOCHS,
        class_weight=class_weights,
        callbacks=fine_tune_callbacks,
    )

    final_model_path = run_dir / "model.keras"
    model.save(final_model_path)

    with (run_dir / "class_weights.json").open("w", encoding="utf-8") as f:
        json.dump({config.CLASS_ORDER[i]: w for i, w in class_weights.items()}, f, indent=2)

    with (run_dir / "training_history.json").open("w", encoding="utf-8") as f:
        json.dump(
            {"head": head_history.history, "fine_tune": fine_tune_history.history},
            f,
            indent=2,
        )

    run_metadata = {
        "run_id": run_id,
        "trained_at_utc": datetime.now(timezone.utc).isoformat(),
        "architecture": config.ARCHITECTURE,
        "class_order": config.CLASS_ORDER,
        "input_shape": list(config.INPUT_SHAPE),
        "normalization_mode": config.NORMALIZATION_MODE,
        "head_epochs_requested": config.HEAD_EPOCHS,
        "fine_tune_epochs_requested": config.FINE_TUNE_EPOCHS,
        "fine_tune_at_layer": config.FINE_TUNE_AT_LAYER,
        "manifest_path": str(manifest_path),
    }
    with (run_dir / "run_metadata.json").open("w", encoding="utf-8") as f:
        json.dump(run_metadata, f, indent=2)

    print(f"Saved trained model and run artifacts to {run_dir}")
    return final_model_path


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run-id", required=True, help="e.g. v1-baseline")
    parser.add_argument("--manifest-path", type=Path, default=config.MANIFEST_PATH)
    parser.add_argument("--dataset-root", type=Path, default=Path("."))
    parser.add_argument("--artifacts-dir", type=Path, default=config.ARTIFACTS_DIR)
    args = parser.parse_args()

    train(args.run_id, args.manifest_path, args.dataset_root, args.artifacts_dir)


if __name__ == "__main__":
    main()
