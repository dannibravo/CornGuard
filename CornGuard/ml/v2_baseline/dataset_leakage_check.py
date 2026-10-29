"""v2 baseline prep, step 3 — re-runs the same exact/near-duplicate cross-split check from the v1
audit, but against proposed_clean_manifest.csv's NEW split assignments, to confirm the leakage fix
actually works before any training happens. Read-only; writes only dataset_leakage_check.csv.
"""
import csv
import hashlib
import os
import sys
from pathlib import Path
import numpy as np
from PIL import Image
import imagehash

SCRIPT_DIR = Path(__file__).resolve().parent
ML_DIR = str(SCRIPT_DIR.parent)
CLEAN_MANIFEST = str(SCRIPT_DIR / "proposed_clean_manifest.csv")
OUT_DIR = str(SCRIPT_DIR)
NEAR_MAX = 6

with open(CLEAN_MANIFEST, newline="", encoding="utf-8") as f:
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
    hashed.append({"sample_id": r["sample_id"], "split": r["split"], "class_label": r["class_label"],
                    "relative_path": r["relative_path"], "sha256": sha, "phash_int": int(str(ph), 16)})
    if (i + 1) % 1000 == 0:
        print(f"  hashed {i+1}/{len(rows)}")

print(f"Hashed {len(hashed)}/{len(rows)} clean-manifest images.")

by_sha = {}
for r in hashed:
    by_sha.setdefault(r["sha256"], []).append(r)

exact_cross_split = []
for sha, members in by_sha.items():
    if len(members) > 1 and len(set(m["split"] for m in members)) > 1:
        exact_cross_split.append(members)

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
near_dup_cross_split = []
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
            continue
        if splits_arr[i] != splits_arr[jj]:
            near_dup_cross_split.append((i, jj, int(dist_chunk[ii, j])))
    if i0 % 2000 == 0:
        print(f"  near-dup scan progress: {i0}/{n}")

out_rows = []
for members in exact_cross_split:
    for i in range(len(members)):
        for j in range(i + 1, len(members)):
            a, b = members[i], members[j]
            out_rows.append({
                "overlap_type": "exact_sha256_cross_split", "hamming_distance": 0,
                "sample_id_a": a["sample_id"], "split_a": a["split"], "class_a": a["class_label"], "path_a": a["relative_path"],
                "sample_id_b": b["sample_id"], "split_b": b["split"], "class_b": b["class_label"], "path_b": b["relative_path"],
            })
for i, jj, dist in near_dup_cross_split:
    a, b = hashed[i], hashed[jj]
    out_rows.append({
        "overlap_type": "near_duplicate_phash_cross_split", "hamming_distance": dist,
        "sample_id_a": a["sample_id"], "split_a": a["split"], "class_a": a["class_label"], "path_a": a["relative_path"],
        "sample_id_b": b["sample_id"], "split_b": b["split"], "class_b": b["class_label"], "path_b": b["relative_path"],
    })

with open(f"{OUT_DIR}\\dataset_leakage_check.csv", "w", newline="", encoding="utf-8") as f:
    fieldnames = ["overlap_type", "hamming_distance", "sample_id_a", "split_a", "class_a", "path_a",
                  "sample_id_b", "split_b", "class_b", "path_b"]
    w = csv.DictWriter(f, fieldnames=fieldnames)
    w.writeheader()
    w.writerows(out_rows)

print(f"\nREMAINING cross-split overlaps in the clean manifest: {len(out_rows)}")
print("Wrote dataset_leakage_check.csv" + (" (0 rows = leakage fix confirmed clean)" if not out_rows else " -- NOT CLEAN, see rows"))
