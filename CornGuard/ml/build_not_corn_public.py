"""
Builds the public NOT-CORN training set for the corn/not-corn gate from already-downloaded
archives in ml/data/downloads/ (see the "Sources" table below). Nothing is downloaded here.

Output (gitignored):
    ml/data/not_corn_public/<category>/*.jpg
    ml/data/not_corn_public/not_corn_public_manifest.csv   (one row per image: category, source,
                                                            license, original file)

Categories and default counts (weighted toward the hardest cases):
    other_plants         1600   PlantVillage leaves of non-corn crops (+ its no-leaf backgrounds)
    flowers               600   TensorFlow flowers
    people                600   COCO val2017 images where a person covers >= 15% of the frame
    surroundings_objects  800   COCO val2017 images with no person
    bad_photos            400   synthetic: heavy blur / very dark / overexposed versions of
                                flowers and COCO images not used above (never corn)

Sources:
    tf_flowers   https://storage.googleapis.com/download.tensorflow.org/example_images/flower_photos.tgz
                 Flickr photos, CC-BY 2.0 per the archive's LICENSE.txt
    COCO 2017    http://images.cocodataset.org/zips/val2017.zip (+ annotations_trainval2017.zip)
                 Flickr photos under the per-image licenses listed in the annotations (recorded
                 per image in the manifest; some are non-commercial)
    PlantVillage https://data.mendeley.com/datasets/tywbtsjrjv (Mendeley Data, "Plant leaf
                 diseases" without augmentation); any class whose name contains corn/maize is
                 excluded so no corn image can enter the not-corn set

Usage (from the ml/ directory, after the downloads are extracted):
    python build_not_corn_public.py
"""

from __future__ import annotations

import argparse
import csv
import json
import random
import shutil
from pathlib import Path

from PIL import Image, ImageEnhance, ImageFilter

DOWNLOADS = Path("data/downloads")
OUTPUT = Path("data/not_corn_public")
MAX_SIDE = 512  # training resizes to 224 anyway; this keeps the set small on disk
SEED = 42
IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".bmp"}
CORN_WORDS = ("corn", "maize")

DEFAULT_COUNTS = {
    "other_plants": 1600,
    "flowers": 600,
    "people": 600,
    "surroundings_objects": 800,
    "bad_photos": 400,
}


def _images_under(folder: Path) -> list[Path]:
    return sorted(p for p in folder.rglob("*") if p.is_file() and p.suffix.lower() in IMAGE_EXTENSIONS)


def _save(src: Image.Image, dst: Path) -> None:
    image = src.convert("RGB")
    image.thumbnail((MAX_SIDE, MAX_SIDE))
    dst.parent.mkdir(parents=True, exist_ok=True)
    image.save(dst, "JPEG", quality=90)


# ---------------------------------------------------------------------------
# Source readers — each returns a list of (path, source, license) candidates
# ---------------------------------------------------------------------------

def flowers_candidates() -> list[tuple[Path, str, str]]:
    root = DOWNLOADS / "tf_flowers" / "flower_photos"
    if not root.exists():
        raise SystemExit(f"Missing {root}. Extract flower_photos.tgz first.")
    return [(p, f"tf_flowers/{p.parent.name}", "CC-BY 2.0 (Flickr, see LICENSE.txt)") for p in _images_under(root)]


def plantvillage_candidates() -> dict[str, list[tuple[Path, str, str]]]:
    """Returns {class_name: [...]} for every non-corn PlantVillage class, so sampling can balance them."""
    root = DOWNLOADS / "plantvillage"
    class_dirs = {}
    for folder in sorted(p for p in root.rglob("*") if p.is_dir()):
        # Use only the non-augmented release, and only leaf-level class folders.
        if "augment" in str(folder).lower() and "without" not in str(folder).lower():
            continue
        images = [p for p in folder.iterdir() if p.is_file() and p.suffix.lower() in IMAGE_EXTENSIONS]
        if images:
            class_dirs[folder.name] = images
    if not class_dirs:
        raise SystemExit(f"No PlantVillage class folders found under {root}. Extract the zip first.")

    kept, excluded = {}, []
    for name, images in class_dirs.items():
        if any(word in name.lower() for word in CORN_WORDS):
            excluded.append(name)
            continue
        kept[name] = [(p, f"plantvillage/{name}", "CC BY 4.0 (Mendeley Data tywbtsjrjv)") for p in sorted(images)]
    print(f"PlantVillage: keeping {len(kept)} non-corn classes, excluded corn classes: {excluded}")
    return kept


def coco_candidates(min_person_fraction: float) -> tuple[list, list]:
    """Returns (people, no_person) candidate lists from COCO val2017."""
    images_dir = DOWNLOADS / "coco_val2017" / "val2017"
    annotations = DOWNLOADS / "coco_val2017" / "annotations" / "instances_val2017.json"
    if not images_dir.exists() or not annotations.exists():
        raise SystemExit("Missing COCO val2017 images or annotations. Extract both zips first.")

    data = json.loads(annotations.read_text(encoding="utf-8"))
    licenses = {lic["id"]: lic["name"] for lic in data["licenses"]}
    person_id = next(c["id"] for c in data["categories"] if c["name"] == "person")
    image_info = {img["id"]: img for img in data["images"]}

    person_area: dict[int, float] = {}
    for ann in data["annotations"]:
        if ann["category_id"] == person_id:
            person_area[ann["image_id"]] = person_area.get(ann["image_id"], 0.0) + ann["area"]

    people, no_person = [], []
    for image_id, info in sorted(image_info.items()):
        path = images_dir / info["file_name"]
        if not path.exists():
            continue
        entry = (path, "coco_val2017", licenses.get(info["license"], "unknown"))
        fraction = person_area.get(image_id, 0.0) / (info["width"] * info["height"])
        if fraction >= min_person_fraction:
            people.append(entry)
        elif image_id not in person_area:
            no_person.append(entry)
        # Images with only a small person in them are skipped: neither a clear "people" example
        # nor a clean "no person" one.
    return people, no_person


# ---------------------------------------------------------------------------
# Synthetic bad photos
# ---------------------------------------------------------------------------

def make_bad_photo(src: Path, kind: str, rng: random.Random) -> Image.Image:
    image = Image.open(src).convert("RGB")
    image.thumbnail((MAX_SIDE, MAX_SIDE))
    if kind == "blur":
        return image.filter(ImageFilter.GaussianBlur(radius=rng.uniform(8, 20)))
    if kind == "dark":
        return ImageEnhance.Brightness(image).enhance(rng.uniform(0.05, 0.2))
    if kind == "overexposed":
        return ImageEnhance.Brightness(image).enhance(rng.uniform(2.5, 4.0))
    raise ValueError(kind)


# ---------------------------------------------------------------------------
# Build
# ---------------------------------------------------------------------------

def sample_balanced(groups: dict[str, list], total: int, rng: random.Random) -> list:
    """Takes about total/len(groups) from each group, topping up from larger groups if some are small."""
    pools = {k: rng.sample(v, len(v)) for k, v in groups.items()}
    chosen = []
    while len(chosen) < total and any(pools.values()):
        for key in list(pools):
            if pools[key] and len(chosen) < total:
                chosen.append(pools[key].pop())
    return chosen


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    for name, count in DEFAULT_COUNTS.items():
        parser.add_argument(f"--{name.replace('_', '-')}", type=int, default=count)
    parser.add_argument("--min-person-fraction", type=float, default=0.15)
    parser.add_argument("--overwrite", action="store_true", help="delete an existing output folder first")
    args = parser.parse_args()
    counts = {name: getattr(args, name) for name in DEFAULT_COUNTS}

    if OUTPUT.exists():
        if not args.overwrite:
            raise SystemExit(f"{OUTPUT} already exists. Pass --overwrite to rebuild it.")
        shutil.rmtree(OUTPUT)

    rng = random.Random(SEED)
    flowers = flowers_candidates()
    plants = plantvillage_candidates()
    people, no_person = coco_candidates(args.min_person_fraction)
    print(f"Candidates: flowers {len(flowers)}, plant classes {len(plants)} "
          f"({sum(len(v) for v in plants.values())} images), people {len(people)}, no-person {len(no_person)}")

    rng.shuffle(flowers)
    rng.shuffle(people)
    rng.shuffle(no_person)

    selections = {
        "other_plants": sample_balanced(plants, counts["other_plants"], rng),
        "flowers": flowers[: counts["flowers"]],
        "people": people[: counts["people"]],
        "surroundings_objects": no_person[: counts["surroundings_objects"]],
    }
    # Bad photos come only from flowers / no-person COCO images NOT already used above, so no
    # image appears twice and no corn can be involved.
    bad_pool = flowers[counts["flowers"]:] + no_person[counts["surroundings_objects"]:]
    rng.shuffle(bad_pool)
    bad_sources = bad_pool[: counts["bad_photos"]]

    rows = []
    for category, items in selections.items():
        if len(items) < counts[category]:
            print(f"WARNING: only {len(items)} images available for {category} (wanted {counts[category]}).")
        for i, (path, source, license_name) in enumerate(items):
            dst = OUTPUT / category / f"{category}_{i:05d}.jpg"
            _save(Image.open(path), dst)
            rows.append({"category": category, "file": str(dst), "source": source,
                         "license": license_name, "original_file": str(path), "transform": ""})

    kinds = ["blur", "dark", "overexposed"]
    for i, (path, source, license_name) in enumerate(bad_sources):
        kind = kinds[i % len(kinds)]
        dst = OUTPUT / "bad_photos" / f"bad_photos_{i:05d}.jpg"
        _save(make_bad_photo(path, kind, rng), dst)
        rows.append({"category": "bad_photos", "file": str(dst), "source": source,
                     "license": license_name, "original_file": str(path), "transform": kind})

    with (OUTPUT / "not_corn_public_manifest.csv").open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)

    summary = {}
    for row in rows:
        summary[row["category"]] = summary.get(row["category"], 0) + 1
    print(f"Wrote {len(rows)} images to {OUTPUT}: {summary}")


if __name__ == "__main__":
    main()
