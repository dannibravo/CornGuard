"""
Runs the corn / not-corn gate on your own photos and prints its decision for each one.

Usage (from the ml/ directory):
    python predict_corn_gate.py path\to\photo.jpg
    python predict_corn_gate.py path\to\folder           (every image inside, including subfolders)
    python predict_corn_gate.py photo1.jpg photo2.png --threshold 0.8

A photo counts as corn when the model's corn probability is at least --threshold (default 0.5).
"""

from __future__ import annotations

import argparse
import os
from pathlib import Path

os.environ.setdefault("TF_CPP_MIN_LOG_LEVEL", "2")  # hide TensorFlow's startup info lines

import numpy as np
import tensorflow as tf
from PIL import Image

import config
# Unused directly, but importing it registers MobileNetV2Preprocess so load_model can rebuild the
# saved model (same requirement as evaluate.py).
import model  # noqa: F401

IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".jfif", ".jpe", ".png", ".bmp", ".webp", ".tif", ".tiff"}


def collect(paths: list[Path]) -> list[Path]:
    images = []
    for p in paths:
        if p.is_dir():
            images += sorted(f for f in p.rglob("*") if f.is_file() and f.suffix.lower() in IMAGE_EXTENSIONS)
        elif p.is_file():
            images.append(p)
        else:
            print(f"Skipping {p}: not found")
    return images


def load(path: Path) -> tf.Tensor:
    try:
        raw = tf.io.read_file(str(path))
        image = tf.io.decode_image(raw, channels=config.IMAGE_CHANNELS, expand_animations=False)
    except tf.errors.InvalidArgumentError:
        # TensorFlow cannot decode some formats (e.g. WebP); fall back to Pillow for those.
        with Image.open(path) as pil_image:
            image = tf.constant(np.asarray(pil_image.convert("RGB")))
    return tf.image.resize(image, config.IMAGE_SIZE)  # raw [0, 255]; the model normalizes itself


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("paths", type=Path, nargs="+", help="image files and/or folders")
    parser.add_argument("--model-path", type=Path, default=Path("artifacts/corn-gate-v1/model.keras"))
    parser.add_argument("--threshold", type=float, default=0.5)
    args = parser.parse_args()

    images = collect(args.paths)
    if not images:
        raise SystemExit("No images found.")

    gate = tf.keras.models.load_model(args.model_path)
    corn_count = 0
    print(f"\n{'DECISION':<10} {'CORN %':>7}  FILE")
    for path in images:
        try:
            p_corn = float(gate(tf.expand_dims(load(path), 0), training=False).numpy()[0, 0])
        except Exception as e:  # unreadable or unsupported file
            print(f"{'ERROR':<10} {'':>7}  {path}  ({type(e).__name__})")
            continue
        decision = "CORN" if p_corn >= args.threshold else "NOT CORN"
        corn_count += decision == "CORN"
        print(f"{decision:<10} {p_corn:>7.1%}  {path}")
    print(f"\n{corn_count} of {len(images)} judged corn (threshold {args.threshold:.0%}).")


if __name__ == "__main__":
    main()
