"""
Compares two app-format .tflite models (e.g. shipped v4 vs v5) on the v5 test split (public and
field separately) and, optionally, a folder of extra photos such as scans pulled off a phone.

Preprocessing matches TfliteCornLeafClassifier: centre square crop, bilinear resize to 224,
x / 127.5 - 1. Output order: Blight(NLB), Common_Rust, Gray_Leaf_Spot, Healthy.

  python compare_tflite.py <model_a.tflite> <model_b.tflite> [extra_photo_dir]

Needs a TFLite runtime that can open both models (ai-edge-litert opens v4; TF 2.16's does not).
"""

import csv
import glob
import os
import sys
from collections import Counter
from pathlib import Path

import numpy as np
from PIL import Image, ImageOps

try:
    from ai_edge_litert.interpreter import Interpreter
except ImportError:
    from tensorflow.lite import Interpreter  # type: ignore

ML_DIR = Path(__file__).resolve().parent.parent
CLASSES = ["Blight", "Common_Rust", "Gray_Leaf_Spot", "Healthy"]


def load(path):
    it = Interpreter(model_path=str(path))
    it.allocate_tensors()
    return it, it.get_input_details()[0]["index"], it.get_output_details()[0]["index"]


def prep(path):
    im = ImageOps.exif_transpose(Image.open(path)).convert("RGB")
    w, h = im.size
    s = min(w, h)
    im = im.crop(((w - s) // 2, (h - s) // 2, (w - s) // 2 + s, (h - s) // 2 + s)).resize((224, 224), Image.BILINEAR)
    return (np.asarray(im, np.float32) / 127.5 - 1)[None]


def run(model, x):
    it, i, o = model
    it.set_tensor(i, x)
    it.invoke()
    return it.get_tensor(o)[0]


def summarize(name, y, preds):
    y = np.array(y)
    print(f"\n== {name} (n={len(y)})")
    for tag, p in preds.items():
        p = np.array(p)
        line = f"  {tag:<6} acc={np.mean(p == y):.3f}"
        for c in sorted(set(y)):
            m = y == c
            line += f" | {CLASSES[c]} recall={np.mean(p[m] == c):.3f}"
        print(line)
        wrong = Counter((CLASSES[a], CLASSES[b]) for a, b in zip(y, p) if a != b)
        if wrong:
            print("         top errors:", wrong.most_common(3))


def main():
    a_path, b_path = sys.argv[1], sys.argv[2]
    extra = sys.argv[3] if len(sys.argv) > 3 else None
    models = {"A": load(a_path), "B": load(b_path)}
    print("A =", a_path, "\nB =", b_path)

    with open(ML_DIR / "v5_field" / "manifest_v5.csv", newline="", encoding="utf-8") as fh:
        test = [r for r in csv.DictReader(fh) if r["split"] == "test"]
    cache = ML_DIR / "data" / "v5_cache"
    for domain in ("public", "field"):
        rows = [r for r in test if r["domain"] == domain]
        y, preds = [], {k: [] for k in models}
        for r in rows:
            x = prep(cache / f"{r['sha256']}.jpg")
            y.append(CLASSES.index(r["label"]))
            for k, m in models.items():
                preds[k].append(int(run(m, x).argmax()))
        summarize(f"TEST {domain}", y, preds)

    if extra:
        print(f"\n== extra photos: {extra}   (probabilities NLB/Rust/GLS/Healthy)")
        for f in sorted(glob.glob(os.path.join(extra, "*.jp*g"))):
            x = prep(f)
            parts = []
            for k, m in models.items():
                p = run(m, x)
                parts.append(f"{k}: {CLASSES[p.argmax()]:<14} [{' '.join(f'{v:.2f}' for v in p)}]")
            print(f"  {os.path.basename(f):<32} " + "   ".join(parts))


if __name__ == "__main__":
    main()
