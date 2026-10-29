"""v2 field-augmentation experiment — training entry point. NOT executed by this preparation step.

Mirrors ml/train.py's train() function as closely as possible, reusing the exact same, UNMODIFIED
architecture/compile/fine-tune functions from ml/model.py, the exact same class-weight computation
from ml/class_weights.py, and the exact same hyperparameters from ml/config.py (epochs, learning
rates, fine-tune-at-layer, batch size, random seed). The ONLY intentional differences from
ml/train.py are:
  1. Dataset construction uses dataset_v2_field_aug.build_split_dataset_field_aug (this directory)
     instead of ml/dataset.py's build_split_dataset, so the training split gets the new
     vertical-flip-removed, widened-brightness augmentation instead of the original.
  2. Output run_id/artifacts path is new ("v2-field-aug-experiment"), so this can never collide
     with or overwrite v1-baseline or v2-clean-baseline.
  3. The manifest path argument defaults to ml/v2_baseline/proposed_clean_manifest.csv -- the
     SAME file the v2-clean-baseline control was trained on -- not the original v1 manifest, and
     not anything related to Dataset 3 (see verify_experiment_setup.py for a direct grep check
     confirming no Dataset 3 reference exists anywhere in this directory).

Validation data is built with augment=False (see build_val_ds below), exactly as the control does
-- validation and test images never receive any augmentation, original or new.

Must be run with ml/ as the working directory:
    cd ml
    python v2_field_aug_experiment/train_v2_field_aug.py --run-id v2-field-aug-experiment

This file has been created but NOT run as part of this preparation step.
"""

from __future__ import annotations

import argparse
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

import tensorflow as tf

sys.path.insert(0, str(Path(__file__).resolve().parent))  # for `import dataset_v2_field_aug`, `import augmentation_v2_field`
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))  # for `import config`, `import model`, `import class_weights`, `import dataset`

import config
from class_weights import compute_training_class_weights
from dataset import load_manifest
from dataset_v2_field_aug import build_split_dataset_field_aug
from model import build_model, compile_for_head_training, unfreeze_for_fine_tuning

DEFAULT_MANIFEST_PATH = Path(__file__).resolve().parent.parent / "v2_baseline" / "proposed_clean_manifest.csv"


def train(run_id: str, manifest_path: Path, dataset_root: Path, artifacts_dir: Path) -> Path:
    manifest = load_manifest(manifest_path)

    train_ds = build_split_dataset_field_aug(manifest, "train", dataset_root, shuffle=True, augment=True)
    val_ds = build_split_dataset_field_aug(manifest, "val", dataset_root, shuffle=False, augment=False)

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
        json.dump({"head": head_history.history, "fine_tune": fine_tune_history.history}, f, indent=2)

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
        "augmentation_module": "v2_field_aug_experiment/augmentation_v2_field.py",
        "augmentation_changes_vs_control": {
            "vertical_flip": "removed (control: True)",
            "brightness_range": "0.70-1.30 (control: 0.80-1.20)",
            "rotation_degrees": "unchanged, read from config.AUGMENTATION",
            "zoom_percent": "unchanged, read from config.AUGMENTATION",
        },
    }
    with (run_dir / "run_metadata.json").open("w", encoding="utf-8") as f:
        json.dump(run_metadata, f, indent=2)

    print(f"Saved trained model and run artifacts to {run_dir}")
    return final_model_path


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run-id", required=True, help="e.g. v2-field-aug-experiment")
    parser.add_argument("--manifest-path", type=Path, default=DEFAULT_MANIFEST_PATH)
    parser.add_argument("--dataset-root", type=Path, default=Path("."))
    parser.add_argument("--artifacts-dir", type=Path, default=config.ARTIFACTS_DIR)
    args = parser.parse_args()

    train(args.run_id, args.manifest_path, args.dataset_root, args.artifacts_dir)


if __name__ == "__main__":
    main()
