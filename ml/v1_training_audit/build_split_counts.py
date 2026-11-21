"""Writes split_class_counts.csv directly from ml/data/dataset_manifest.csv (no assumptions)."""
import csv
from collections import Counter
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
ML_DIR = SCRIPT_DIR.parent
MANIFEST = str(ML_DIR / "data" / "dataset_manifest.csv")
OUT = str(SCRIPT_DIR / "split_class_counts.csv")

with open(MANIFEST, newline="", encoding="utf-8") as f:
    rows = list(csv.DictReader(f))

counts = Counter((r["class_label"], r["split"]) for r in rows)
classes = ["common_rust", "gray_leaf_spot", "northern_leaf_blight", "healthy"]
splits = ["train", "val", "test"]

out_rows = []
for c in classes:
    total = sum(counts.get((c, s), 0) for s in splits)
    row = {"class_label": c}
    for s in splits:
        n = counts.get((c, s), 0)
        row[s] = n
        row[f"{s}_pct_of_class"] = round(100 * n / total, 2) if total else 0.0
    row["total"] = total
    out_rows.append(row)

grand_total = {"class_label": "ALL_CLASSES_TOTAL"}
for s in splits:
    n = sum(counts.get((c, s), 0) for c in classes)
    grand_total[s] = n
    grand_total[f"{s}_pct_of_class"] = round(100 * n / len(rows), 2)
grand_total["total"] = len(rows)
out_rows.append(grand_total)

fieldnames = ["class_label", "train", "train_pct_of_class", "val", "val_pct_of_class",
              "test", "test_pct_of_class", "total"]
with open(OUT, "w", newline="", encoding="utf-8") as f:
    w = csv.DictWriter(f, fieldnames=fieldnames)
    w.writeheader()
    w.writerows(out_rows)

print(f"Wrote {OUT}")
for r in out_rows:
    print(r)
