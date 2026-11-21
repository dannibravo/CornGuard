"""
Builds the dataset manifest required by claude/09_ML_MODEL_CONTRACT.md ("Required Dataset
Manifest") from a raw image directory, and assigns a stratified train/val/test split.

Never executed — see ml/README.md. Written to be run as-is once real dataset images exist on
disk in the expected layout; nothing in here fabricates image content or counts.

Expected raw layout:

    ml/data/raw/<class_name>/public/*.jpg   — public dataset images (Kaggle/PlantVillage-style)
    ml/data/raw/<class_name>/local/*.jpg    — Bukidnon field images, per "the final research
                                               protocol" (claude/09_ML_MODEL_CONTRACT.md)

<class_name> must be one of config.CLASS_ORDER exactly.

Local images may optionally be described by a sidecar CSV at
ml/data/local_image_metadata.csv with columns:

    filename,source_farm,barangay,expert_validated

Any local image missing a row in that sidecar gets source_farm/barangay left blank and
expert_validated=False — this script never invents a farm name, barangay, or validation status
for a local field image (claude/04_DEVELOPMENT_RULES.md #3 — do not fabricate project facts).

Usage (run from the ml/ directory):
    python prepare_dataset.py --dataset-version v1
"""

from __future__ import annotations

import argparse
import csv
import sys
from pathlib import Path

from sklearn.model_selection import train_test_split

import config


def _load_local_metadata(path: Path) -> dict[str, dict[str, str]]:
    if not path.exists():
        return {}
    with path.open(newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        return {row["filename"]: row for row in reader}


def _scan_raw_images(raw_dir: Path, local_metadata: dict[str, dict[str, str]]) -> list[dict]:
    rows = []
    for class_name in config.CLASS_ORDER:
        class_dir = raw_dir / class_name
        if not class_dir.exists():
            print(f"WARNING: no directory for class '{class_name}' under {raw_dir}", file=sys.stderr)
            continue

        for source_type in ("public", "local"):
            source_dir = class_dir / source_type
            if not source_dir.exists():
                continue
            for image_path in sorted(source_dir.glob("*")):
                if not image_path.is_file():
                    continue
                meta = local_metadata.get(image_path.name, {}) if source_type == "local" else {}
                rows.append(
                    {
                        "sample_id": image_path.stem,
                        "original_filename": image_path.name,
                        # Relative to the cwd every consumer script (dataset.py, train.py,
                        # evaluate.py) is documented to run from — the ml/ directory — NOT
                        # relative to raw_dir.parent, which silently disagreed with dataset.py's
                        # dataset_root default and made every path resolve one directory short.
                        "relative_path": str(image_path.resolve().relative_to(Path.cwd().resolve())),
                        "class_label": class_name,
                        "source_type": source_type,
                        "source_repository_or_farm": meta.get("source_farm", ""),
                        "source_barangay": meta.get("barangay", ""),
                        "expert_validated": meta.get("expert_validated", "False") if source_type == "local" else "",
                        "augmentation_eligible": "True",
                    }
                )
    return rows


def _assign_splits(rows: list[dict], seed: int) -> None:
    by_class: dict[str, list[dict]] = {}
    for row in rows:
        by_class.setdefault(row["class_label"], []).append(row)

    for class_name, class_rows in by_class.items():
        if len(class_rows) < 3:
            # Too few samples to stratify meaningfully; keep them all in train and warn rather
            # than silently mis-splitting a near-empty class.
            print(
                f"WARNING: class '{class_name}' has only {len(class_rows)} sample(s); "
                "assigning all to train. Add more images before training on this class.",
                file=sys.stderr,
            )
            for row in class_rows:
                row["split"] = "train"
            continue

        train_rows, holdout_rows = train_test_split(
            class_rows,
            test_size=(config.VAL_SPLIT + config.TEST_SPLIT),
            random_state=seed,
        )
        val_fraction_of_holdout = config.VAL_SPLIT / (config.VAL_SPLIT + config.TEST_SPLIT)
        val_rows, test_rows = train_test_split(
            holdout_rows,
            test_size=(1 - val_fraction_of_holdout),
            random_state=seed,
        )

        for row in train_rows:
            row["split"] = "train"
        for row in val_rows:
            row["split"] = "val"
        for row in test_rows:
            row["split"] = "test"


def build_manifest(dataset_version: str, raw_dir: Path, manifest_path: Path, seed: int) -> list[dict]:
    local_metadata = _load_local_metadata(raw_dir.parent / "local_image_metadata.csv")
    rows = _scan_raw_images(raw_dir, local_metadata)

    if not rows:
        raise SystemExit(
            f"No images found under {raw_dir}. Populate ml/data/raw/<class>/public or "
            "ml/data/raw/<class>/local before running this script — it does not generate or "
            "download images itself."
        )

    _assign_splits(rows, seed)

    for row in rows:
        row["dataset_version"] = dataset_version

    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    fieldnames = [
        "dataset_version",
        "sample_id",
        "original_filename",
        "relative_path",
        "class_label",
        "source_type",
        "source_repository_or_farm",
        "source_barangay",
        "expert_validated",
        "split",
        "augmentation_eligible",
    ]
    with manifest_path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)

    print(f"Wrote {len(rows)} rows to {manifest_path}")
    for split_name in ("train", "val", "test"):
        count = sum(1 for row in rows if row["split"] == split_name)
        print(f"  {split_name}: {count}")

    return rows


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset-version", required=True, help="e.g. v1, v2-with-bukidnon-field-images")
    parser.add_argument("--raw-dir", type=Path, default=config.RAW_DATA_DIR)
    parser.add_argument("--manifest-path", type=Path, default=config.MANIFEST_PATH)
    parser.add_argument("--seed", type=int, default=config.RANDOM_SEED)
    args = parser.parse_args()

    build_manifest(args.dataset_version, args.raw_dir, args.manifest_path, args.seed)


if __name__ == "__main__":
    main()
