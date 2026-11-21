"""Stage 0, item 3 -- verifies every row in the proposed clean manifest: does its class_label
match (a) the folder segment in its own relative_path, and (b) the index the training loader's
_class_to_index() mapping (config.CLASS_ORDER) would assign it. Read-only: no files are written
except the output report this script prints (captured by the caller into the audit .md).
"""
import csv
from pathlib import Path

MANIFEST = str(Path(__file__).resolve().parent.parent / "v2_baseline" / "proposed_clean_manifest.csv")
CLASS_ORDER = ["common_rust", "gray_leaf_spot", "northern_leaf_blight", "healthy"]  # from config.py, read not re-derived

with open(MANIFEST, newline="", encoding="utf-8") as f:
    rows = list(csv.DictReader(f))

print(f"Total rows: {len(rows)}")

mismatches = []
for r in rows:
    # relative_path looks like: data\raw\<class_name>\public\<filename>
    parts = r["relative_path"].split("\\")
    folder_class = parts[2] if len(parts) > 2 else None
    if folder_class != r["class_label"]:
        mismatches.append({"sample_id": r["sample_id"], "manifest_class": r["class_label"],
                            "folder_class": folder_class, "relative_path": r["relative_path"]})

print(f"Rows where class_label != folder segment of relative_path: {len(mismatches)}")
for m in mismatches[:20]:
    print(" ", m)

invalid_class_rows = [r for r in rows if r["class_label"] not in CLASS_ORDER]
print(f"\nRows whose class_label is NOT in config.CLASS_ORDER {CLASS_ORDER}: {len(invalid_class_rows)}")
for r in invalid_class_rows[:10]:
    print(" ", r["sample_id"], r["class_label"])

# confirm every class actually present maps to a valid, in-range index
class_to_index = {name: i for i, name in enumerate(CLASS_ORDER)}
print(f"\nclass_to_index mapping as the training loader computes it: {class_to_index}")
from collections import Counter
print(f"Distinct class_label values actually present in the manifest: {Counter(r['class_label'] for r in rows)}")
