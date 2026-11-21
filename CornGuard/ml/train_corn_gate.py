"""
Trains the corn / not-corn gate: a 2-class MobileNetV2 that decides whether a photo is a corn leaf
before the disease model runs. Same architecture, preprocessing, augmentation and two-phase
training (frozen head, then fine-tuning) as the disease model (model.py, augmentation.py,
train.py), only with two classes.

Data:
    corn      every row of the clean disease manifest (v2_baseline/proposed_clean_manifest.csv),
              keeping its existing train/val/test split, so the disease test images stay unseen
    not_corn  data/not_corn_public/ (built by build_not_corn_public.py), split 70/15/15
              stratified by category with a fixed seed

Outputs (artifacts/<run-id>/):
    model.keras, best_head.keras, best_fine_tuned.keras
    gate_manifest.csv          every image used, with its label, split and category
    training_history.json, class_weights.json, run_metadata.json
    gate_evaluation.json/.md   held-out test results, overall and per not-corn category
    test_predictions.csv       per-image test predictions

Usage (from the ml/ directory):
    python train_corn_gate.py --run-id corn-gate-v1
"""

from __future__ import annotations

import argparse
import json
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
import pandas as pd
import tensorflow as tf
from sklearn.model_selection import train_test_split
from sklearn.utils.class_weight import compute_class_weight

import config
from augmentation import build_augmentation_layer
from model import build_model, compile_for_head_training, unfreeze_for_fine_tuning

GATE_CLASSES = ["corn", "not_corn"]  # output index order of the gate model
CORN, NOT_CORN = 0, 1


# ---------------------------------------------------------------------------
# Manifest
# ---------------------------------------------------------------------------

def build_gate_manifest(corn_manifest: Path, not_corn_dir: Path, seed: int) -> pd.DataFrame:
    corn = pd.read_csv(corn_manifest)
    corn_rows = pd.DataFrame({
        "relative_path": corn["relative_path"],
        "label": "corn",
        "split": corn["split"],
        "category": "corn_" + corn["class_label"],
        "source": corn["source_type"],
    })

    not_corn_manifest = not_corn_dir / "not_corn_public_manifest.csv"
    if not not_corn_manifest.exists():
        raise SystemExit(f"Missing {not_corn_manifest}. Run build_not_corn_public.py first.")
    nc = pd.read_csv(not_corn_manifest)
    train_part, holdout = train_test_split(nc, test_size=0.30, stratify=nc["category"], random_state=seed)
    val_part, test_part = train_test_split(holdout, test_size=0.50, stratify=holdout["category"], random_state=seed)
    nc = nc.assign(split="")
    nc.loc[train_part.index, "split"] = "train"
    nc.loc[val_part.index, "split"] = "val"
    nc.loc[test_part.index, "split"] = "test"
    not_corn_rows = pd.DataFrame({
        "relative_path": nc["file"],
        "label": "not_corn",
        "split": nc["split"],
        "category": nc["category"],
        "source": nc["source"],
    })

    manifest = pd.concat([corn_rows, not_corn_rows], ignore_index=True)
    missing = [p for p in manifest["relative_path"] if not Path(p).exists()]
    if missing:
        raise SystemExit(f"{len(missing)} manifest images are missing on disk, e.g. {missing[:3]}")
    return manifest


# ---------------------------------------------------------------------------
# Data
# ---------------------------------------------------------------------------

def _load_image(path: tf.Tensor) -> tf.Tensor:
    raw = tf.io.read_file(path)
    image = tf.io.decode_image(raw, channels=config.IMAGE_CHANNELS, expand_animations=False)
    image.set_shape([None, None, config.IMAGE_CHANNELS])
    return tf.image.resize(image, config.IMAGE_SIZE)


def build_dataset(rows: pd.DataFrame, shuffle: bool, augment: bool, seed: int) -> tf.data.Dataset:
    labels = (rows["label"] == "not_corn").astype("int32").to_numpy()
    ds = tf.data.Dataset.from_tensor_slices((rows["relative_path"].tolist(), labels))
    if shuffle:
        ds = ds.shuffle(len(rows), seed=seed, reshuffle_each_iteration=True)
    ds = ds.map(lambda p, y: (_load_image(p), y), num_parallel_calls=tf.data.AUTOTUNE)
    if augment:
        aug = build_augmentation_layer()
        ds = ds.map(lambda x, y: (aug(x, training=True), y), num_parallel_calls=tf.data.AUTOTUNE)
    return ds.batch(config.BATCH_SIZE).prefetch(tf.data.AUTOTUNE)


# ---------------------------------------------------------------------------
# Evaluation
# ---------------------------------------------------------------------------

def evaluate(model: tf.keras.Model, test_rows: pd.DataFrame, seed: int) -> tuple[dict, pd.DataFrame]:
    probs = model.predict(build_dataset(test_rows, shuffle=False, augment=False, seed=seed), verbose=0)
    p_corn = probs[:, CORN]
    preds = test_rows.assign(p_corn=p_corn, predicted=np.where(p_corn >= 0.5, "corn", "not_corn"))
    preds["correct"] = preds["predicted"] == preds["label"]

    is_corn = preds["label"] == "corn"
    report = {
        "test_images": int(len(preds)),
        "accuracy": float(preds["correct"].mean()),
        "corn_accepted": float((preds.loc[is_corn, "predicted"] == "corn").mean()),
        "not_corn_rejected": float((preds.loc[~is_corn, "predicted"] == "not_corn").mean()),
        "confusion": {
            "corn_as_corn": int(((preds.label == "corn") & (preds.predicted == "corn")).sum()),
            "corn_as_not_corn": int(((preds.label == "corn") & (preds.predicted == "not_corn")).sum()),
            "not_corn_as_corn": int(((preds.label == "not_corn") & (preds.predicted == "corn")).sum()),
            "not_corn_as_not_corn": int(((preds.label == "not_corn") & (preds.predicted == "not_corn")).sum()),
        },
        "by_category": {},
        "threshold_sweep": [],
    }
    for category, group in preds.groupby("category"):
        report["by_category"][category] = {
            "n": int(len(group)),
            "correct_rate": float(group["correct"].mean()),
        }
    for t in [0.3, 0.5, 0.7, 0.8, 0.9, 0.95]:
        report["threshold_sweep"].append({
            "p_corn_threshold": t,
            "corn_accepted": float((p_corn[is_corn.to_numpy()] >= t).mean()),
            "not_corn_rejected": float((p_corn[~is_corn.to_numpy()] < t).mean()),
        })
    return report, preds


def write_markdown(path: Path, run_id: str, report: dict, counts: dict) -> None:
    c = report["confusion"]
    lines = [
        f"# Corn gate evaluation: {run_id}",
        "",
        "Held-out test split only. Corn images are public-style photos from the clean disease manifest; "
        "real field photos have NOT been tested here.",
        "",
        "## Data used",
        "",
        "| Split | Corn | Not corn |",
        "|---|---|---|",
    ]
    for split in ("train", "val", "test"):
        lines.append(f"| {split} | {counts[split]['corn']} | {counts[split]['not_corn']} |")
    lines += [
        "",
        "## Test results (p_corn >= 0.5 means corn)",
        "",
        f"- Accuracy: **{report['accuracy']:.2%}** on {report['test_images']} images",
        f"- Corn accepted: **{report['corn_accepted']:.2%}**",
        f"- Not-corn rejected: **{report['not_corn_rejected']:.2%}**",
        "",
        "| true \\ predicted | corn | not_corn |",
        "|---|---|---|",
        f"| corn | {c['corn_as_corn']} | {c['corn_as_not_corn']} |",
        f"| not_corn | {c['not_corn_as_corn']} | {c['not_corn_as_not_corn']} |",
        "",
        "## By category",
        "",
        "| Category | Test images | Correctly handled |",
        "|---|---|---|",
    ]
    for category, r in report["by_category"].items():
        lines.append(f"| {category} | {r['n']} | {r['correct_rate']:.1%} |")
    lines += ["", "## Threshold sweep", "", "| p_corn threshold | Corn accepted | Not-corn rejected |", "|---|---|---|"]
    for r in report["threshold_sweep"]:
        lines.append(f"| {r['p_corn_threshold']} | {r['corn_accepted']:.1%} | {r['not_corn_rejected']:.1%} |")
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--run-id", default="corn-gate-v1")
    parser.add_argument("--corn-manifest", type=Path, default=Path("v2_baseline/proposed_clean_manifest.csv"))
    parser.add_argument("--not-corn-dir", type=Path, default=Path("data/not_corn_public"))
    parser.add_argument("--artifacts-dir", type=Path, default=config.ARTIFACTS_DIR)
    parser.add_argument("--seed", type=int, default=config.RANDOM_SEED)
    args = parser.parse_args()

    run_dir = args.artifacts_dir / args.run_id
    if (run_dir / "model.keras").exists():
        raise SystemExit(f"{run_dir} already has a trained model. Use a new --run-id.")
    run_dir.mkdir(parents=True, exist_ok=True)

    # Seeded, unlike the v2 disease runs, so this run can be repeated.
    tf.keras.utils.set_random_seed(args.seed)

    manifest = build_gate_manifest(args.corn_manifest, args.not_corn_dir, args.seed)
    manifest.to_csv(run_dir / "gate_manifest.csv", index=False)
    counts = {s: manifest[manifest.split == s]["label"].value_counts().to_dict() for s in ("train", "val", "test")}
    print(f"Gate manifest: {counts}")

    train_rows = manifest[manifest.split == "train"]
    val_rows = manifest[manifest.split == "val"]
    test_rows = manifest[manifest.split == "test"]
    train_ds = build_dataset(train_rows, shuffle=True, augment=True, seed=args.seed)
    val_ds = build_dataset(val_rows, shuffle=False, augment=False, seed=args.seed)

    y_train = (train_rows["label"] == "not_corn").astype(int).to_numpy()
    weights = compute_class_weight("balanced", classes=np.array([CORN, NOT_CORN]), y=y_train)
    class_weights = {CORN: float(weights[0]), NOT_CORN: float(weights[1])}
    print(f"Class weights: {class_weights}")

    model, base_model = build_model(num_classes=len(GATE_CLASSES))
    compile_for_head_training(model)

    def callbacks(name: str) -> list:
        return [
            tf.keras.callbacks.ModelCheckpoint(str(run_dir / name), save_best_only=True, monitor="val_accuracy"),
            tf.keras.callbacks.EarlyStopping(monitor="val_accuracy", patience=3, restore_best_weights=True),
        ]

    print(f"Head training for up to {config.HEAD_EPOCHS} epochs...")
    head = model.fit(train_ds, validation_data=val_ds, epochs=config.HEAD_EPOCHS,
                     class_weight=class_weights, callbacks=callbacks("best_head.keras"))

    print(f"Fine-tuning for up to {config.FINE_TUNE_EPOCHS} epochs...")
    unfreeze_for_fine_tuning(model, base_model)
    fine = model.fit(train_ds, validation_data=val_ds, epochs=config.FINE_TUNE_EPOCHS,
                     class_weight=class_weights, callbacks=callbacks("best_fine_tuned.keras"))

    model.save(run_dir / "model.keras")
    (run_dir / "training_history.json").write_text(
        json.dumps({"head": head.history, "fine_tune": fine.history}, indent=2), encoding="utf-8")
    (run_dir / "class_weights.json").write_text(
        json.dumps({GATE_CLASSES[k]: v for k, v in class_weights.items()}, indent=2), encoding="utf-8")
    (run_dir / "run_metadata.json").write_text(json.dumps({
        "run_id": args.run_id,
        "purpose": "corn / not-corn gate",
        "trained_at_utc": datetime.now(timezone.utc).isoformat(),
        "architecture": config.ARCHITECTURE,
        "class_order": GATE_CLASSES,
        "input_shape": list(config.INPUT_SHAPE),
        "normalization_mode": config.NORMALIZATION_MODE,
        "seed": args.seed,
        "corn_manifest": str(args.corn_manifest),
        "not_corn_dir": str(args.not_corn_dir),
        "split_counts": counts,
        "head_epochs_requested": config.HEAD_EPOCHS,
        "fine_tune_epochs_requested": config.FINE_TUNE_EPOCHS,
        "fine_tune_at_layer": config.FINE_TUNE_AT_LAYER,
    }, indent=2), encoding="utf-8")

    report, preds = evaluate(model, test_rows, args.seed)
    (run_dir / "gate_evaluation.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    preds.to_csv(run_dir / "test_predictions.csv", index=False)
    write_markdown(run_dir / "gate_evaluation.md", args.run_id, report, counts)
    print(json.dumps({k: report[k] for k in ("accuracy", "corn_accepted", "not_corn_rejected")}, indent=2))
    print(f"Saved model and reports to {run_dir}")


if __name__ == "__main__":
    main()
