"""v2 baseline prep, step 2 — builds a proposed clean split manifest from the v1 manifest:
  - LABEL_CONFLICT_UNRESOLVED pairs (2 pairs, 4 images): excluded entirely (train AND eval),
    pending qualified human review of the true label. Not relabeled automatically.
  - SAME_LABEL_DUPLICATE pairs (10 pairs, 20 images): the whole pair is forced into a single
    split via union-find (so duplicates never cross splits again), using the rule "if either
    member is already in train, the whole group goes to train" -- every one of the 10 pairs here
    has exactly one train-side member, so this simply pulls the val/test-side duplicate into train.
  - Every other image (4,164 of 4,188) keeps its EXACT v1 split assignment unchanged, so the v2
    baseline stays maximally comparable to v1 and this is a targeted leakage fix, not a reshuffle.

Read-only against ml/data/raw and the v1 manifest/audit outputs. Writes only into ml/v2_baseline/.
Does not reference, read, or import anything from cornguard_external_eval/dataset3_* (verified
separately by grep in this same folder's files -- see v2_preparation_report.md).
"""
import csv
from collections import defaultdict, Counter
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
ML_DIR = str(SCRIPT_DIR.parent)
V1_MANIFEST = f"{ML_DIR}\\data\\dataset_manifest.csv"
REVIEW_CSV = str(SCRIPT_DIR / "duplicate_review.csv")
OUT_DIR = str(SCRIPT_DIR)

with open(V1_MANIFEST, newline="", encoding="utf-8") as f:
    manifest_rows = list(csv.DictReader(f))
by_id = {r["sample_id"]: r for r in manifest_rows}

with open(REVIEW_CSV, newline="", encoding="utf-8") as f:
    review_rows = list(csv.DictReader(f))

excluded_ids = set()
merge_groups = []  # list of (sample_id_a, sample_id_b)
for r in review_rows:
    if r["review_classification"] == "LABEL_CONFLICT_UNRESOLVED":
        excluded_ids.add(r["sample_id_a"])
        excluded_ids.add(r["sample_id_b"])
    else:
        merge_groups.append((r["sample_id_a"], r["sample_id_b"]))

# union-find over sample_ids for the SAME_LABEL_DUPLICATE pairs only
parent = {r["sample_id"]: r["sample_id"] for r in manifest_rows}

def find(x):
    while parent[x] != x:
        parent[x] = parent[parent[x]]
        x = parent[x]
    return x

def union(a, b):
    ra, rb = find(a), find(b)
    if ra != rb:
        parent[ra] = rb

for a, b in merge_groups:
    union(a, b)

groups = defaultdict(list)
for sid in parent:
    groups[find(sid)].append(sid)

# decide the final split per group: if ANY member is already 'train', the whole group -> train;
# otherwise (shouldn't occur among the 10 reviewed pairs, but handled generally) keep majority vote
final_split = {}
for root, members in groups.items():
    if len(members) == 1:
        continue  # untouched, keeps its v1 split
    splits_present = [by_id[m]["split"] for m in members]
    if "train" in splits_present:
        chosen = "train"
    else:
        chosen = Counter(splits_present).most_common(1)[0][0]
    for m in members:
        final_split[m] = chosen

clean_rows = []
held_out = []
for r in manifest_rows:
    sid = r["sample_id"]
    if sid in excluded_ids:
        held_out.append({**r, "exclusion_reason": "label_conflict_unresolved_cross_split_duplicate"})
        continue
    new_r = dict(r)
    if sid in final_split:
        new_r["split"] = final_split[sid]
        new_r["split_changed_from_v1"] = "True" if final_split[sid] != r["split"] else "False"
    else:
        new_r["split_changed_from_v1"] = "False"
    clean_rows.append(new_r)

out_fieldnames = ["dataset_version", "sample_id", "original_filename", "relative_path", "class_label",
                   "source_type", "source_repository_or_farm", "source_barangay", "expert_validated",
                   "split", "augmentation_eligible", "split_changed_from_v1"]
with open(f"{OUT_DIR}\\proposed_clean_manifest.csv", "w", newline="", encoding="utf-8") as f:
    w = csv.DictWriter(f, fieldnames=out_fieldnames)
    w.writeheader()
    for r in clean_rows:
        w.writerow({k: r.get(k, "") for k in out_fieldnames})

held_fieldnames = ["dataset_version", "sample_id", "original_filename", "relative_path", "class_label",
                    "source_type", "split", "exclusion_reason"]
with open(f"{OUT_DIR}\\held_out_unresolved_samples.csv", "w", newline="", encoding="utf-8") as f:
    w = csv.DictWriter(f, fieldnames=held_fieldnames)
    w.writeheader()
    for r in held_out:
        w.writerow({k: r.get(k, "") for k in held_fieldnames})

print(f"Clean manifest: {len(clean_rows)} rows ({len(manifest_rows)} original - {len(held_out)} held out)")
print(f"Held-out (label-conflict-unresolved): {len(held_out)} rows -> held_out_unresolved_samples.csv")
changed = sum(1 for r in clean_rows if r["split_changed_from_v1"] == "True")
print(f"Images whose split was changed to resolve leakage: {changed}")

# --- split_class_counts.csv (clean) ---
classes = ["common_rust", "gray_leaf_spot", "northern_leaf_blight", "healthy"]
splits = ["train", "val", "test"]
counts = Counter((r["class_label"], r["split"]) for r in clean_rows)
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
grand = {"class_label": "ALL_CLASSES_TOTAL"}
for s in splits:
    n = sum(counts.get((c, s), 0) for c in classes)
    grand[s] = n
    grand[f"{s}_pct_of_class"] = round(100 * n / len(clean_rows), 2)
grand["total"] = len(clean_rows)
out_rows.append(grand)

with open(f"{OUT_DIR}\\split_class_counts.csv", "w", newline="", encoding="utf-8") as f:
    fieldnames = ["class_label", "train", "train_pct_of_class", "val", "val_pct_of_class",
                  "test", "test_pct_of_class", "total"]
    w = csv.DictWriter(f, fieldnames=fieldnames)
    w.writeheader()
    w.writerows(out_rows)

print("\nClean split_class_counts.csv:")
for r in out_rows:
    print(" ", r)
