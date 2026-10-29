"""v1 training audit — checks for exact (SHA-256) and near-duplicate (perceptual hash) images
crossing the train/val/test splits recorded in ml/data/dataset_manifest.csv. Uses only packages
already installed in cornguard_external_eval/venv (PIL, imagehash, numpy) -- no install performed.
Read-only: only reads ml/data/raw/* and the manifest; writes nothing under ml/ except into
ml/v1_training_audit/.
"""
import csv
import hashlib
import os
import sys
from pathlib import Path
import numpy as np
from PIL import Image
import imagehash

ML_DIR = str(Path(__file__).resolve().parent.parent)
MANIFEST = f"{ML_DIR}\\data\\dataset_manifest.csv"
AUDIT_DIR = str(Path(__file__).resolve().parent)

with open(MANIFEST, newline="", encoding="utf-8") as f:
    rows = list(csv.DictReader(f))

def sha256_of(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()

hashed = []
for i, r in enumerate(rows):
    path = os.path.join(ML_DIR, r["relative_path"])
    try:
        sha = sha256_of(path)
        with Image.open(path) as img:
            ph = imagehash.phash(img.convert("RGB"), hash_size=8)
    except Exception as e:
        print(f"SKIP (unreadable) {path}: {e}", file=sys.stderr)
        continue
    hashed.append({
        "sample_id": r["sample_id"], "class_label": r["class_label"], "split": r["split"],
        "relative_path": r["relative_path"], "sha256": sha, "phash": str(ph), "phash_int": int(str(ph), 16),
    })
    if (i + 1) % 1000 == 0:
        print(f"  hashed {i+1}/{len(rows)}")

print(f"Hashed {len(hashed)}/{len(rows)} images successfully.")

# --- exact SHA-256 duplicates crossing splits ---
by_sha = {}
for r in hashed:
    by_sha.setdefault(r["sha256"], []).append(r)

exact_cross_split = []
exact_same_split = []
for sha, members in by_sha.items():
    if len(members) < 2:
        continue
    splits_present = set(m["split"] for m in members)
    if len(splits_present) > 1:
        exact_cross_split.append(members)
    else:
        exact_same_split.append(members)

print(f"\nExact SHA-256 duplicate groups found: {len(by_sha) - sum(1 for v in by_sha.values() if len(v)==1)} groups with 2+ members")
print(f"  Groups whose members CROSS splits (train/val/test): {len(exact_cross_split)}")
print(f"  Groups entirely within one split: {len(exact_same_split)}")

# --- near-duplicate (perceptual hash) crossing splits, vectorized, same thresholds as external-eval work ---
NEAR_MAX = 6  # Hamming distance 0-6 = confirmed_near_duplicate (same threshold used throughout this project)

def popcount64(arr):
    arr = arr.copy()
    count = np.zeros(arr.shape, dtype=np.uint64)
    for _ in range(64):
        count += arr & np.uint64(1)
        arr >>= np.uint64(1)
    return count

hashes_arr = np.array([r["phash_int"] for r in hashed], dtype=np.uint64)
splits_arr = [r["split"] for r in hashed]
n = len(hashed)
near_dup_cross_split_pairs = []
chunk = 500
for i0 in range(0, n, chunk):
    a_chunk = hashes_arr[i0:i0 + chunk].reshape(-1, 1)
    xor = a_chunk ^ hashes_arr.reshape(1, -1)
    dist_chunk = popcount64(xor).astype(np.uint8)
    global_i = np.arange(i0, min(i0 + chunk, n)).reshape(-1, 1)
    j_idx = np.arange(n).reshape(1, -1)
    mask = (j_idx > global_i) & (dist_chunk <= NEAR_MAX)
    ii_hits, jj_hits = np.where(mask)
    for ii, j in zip(ii_hits, jj_hits):
        i = i0 + int(ii)
        jj = int(j)
        if hashed[i]["sha256"] == hashed[jj]["sha256"]:
            continue  # already counted as exact
        if splits_arr[i] != splits_arr[jj]:
            near_dup_cross_split_pairs.append((i, jj, int(dist_chunk[ii, j])))
    if i0 % 2000 == 0:
        print(f"  near-dup scan progress: {i0}/{n}")

print(f"\nNear-duplicate (Hamming <= {NEAR_MAX}) pairs CROSSING splits: {len(near_dup_cross_split_pairs)}")

# --- write duplicate_overlap_findings.csv ---
out_rows = []
for members in exact_cross_split:
    for i in range(len(members)):
        for j in range(i + 1, len(members)):
            a, b = members[i], members[j]
            out_rows.append({
                "overlap_type": "exact_sha256_cross_split",
                "sample_id_a": a["sample_id"], "split_a": a["split"], "class_a": a["class_label"], "path_a": a["relative_path"],
                "sample_id_b": b["sample_id"], "split_b": b["split"], "class_b": b["class_label"], "path_b": b["relative_path"],
                "hamming_distance": 0,
            })
for i, jj, dist in near_dup_cross_split_pairs:
    a, b = hashed[i], hashed[jj]
    out_rows.append({
        "overlap_type": "near_duplicate_phash_cross_split",
        "sample_id_a": a["sample_id"], "split_a": a["split"], "class_a": a["class_label"], "path_a": a["relative_path"],
        "sample_id_b": b["sample_id"], "split_b": b["split"], "class_b": b["class_label"], "path_b": b["relative_path"],
        "hamming_distance": dist,
    })

with open(f"{AUDIT_DIR}\\duplicate_overlap_findings.csv", "w", newline="", encoding="utf-8") as f:
    fieldnames = ["overlap_type", "sample_id_a", "split_a", "class_a", "path_a",
                  "sample_id_b", "split_b", "class_b", "path_b", "hamming_distance"]
    w = csv.DictWriter(f, fieldnames=fieldnames)
    w.writeheader()
    w.writerows(out_rows)

print(f"\nWrote {len(out_rows)} overlap rows to duplicate_overlap_findings.csv")
print(f"SUMMARY: exact_cross_split_pairs={sum(len(m)*(len(m)-1)//2 for m in exact_cross_split)} "
      f"near_dup_cross_split_pairs={len(near_dup_cross_split_pairs)}")
