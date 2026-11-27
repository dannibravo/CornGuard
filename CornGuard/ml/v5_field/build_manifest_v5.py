"""
Builds the v5 training manifest: the public (lab-style) images the shipped v4 model was trained on,
plus field-condition smartphone images, so the model stops calling real healthy leaves diseased.

Run from ml/:  .venv/Scripts/python v5_field/build_manifest_v5.py

What it does, in order:
1. Collects public images (data/raw/<class>/public) and field images (data/downloads/field/...).
   Field folders whose labels could not be confirmed as one of CornGuard's 4 classes are NOT listed
   in FIELD_SOURCES (see v5_field/README.md, "Excluded sources").
2. Drops unreadable files (some Mendeley downloads returned a 404 JSON body instead of an image).
3. SHA-256 + pHash (imagehash.phash, hash_size=8, same as the Dataset 3 tooling).
4. Hard stop vs Dataset 3: any field or public image whose SHA-256 matches, or whose pHash is within
   SUSPECTED_MAX of, a Dataset 3 image is removed. Only Dataset 3's stored hashes are read; no
   Dataset 3 image is opened.
5. Field images within NEAR_DUPLICATE_MAX of a public image are removed (cross-source leakage).
6. Same de-duplication rule as the training notebook: identical pHash with conflicting labels ->
   all copies dropped; identical pHash, same label -> first copy kept.
7. Splits 70/15/15. Public: stratified by class, seed 42, as in the notebook. Field: group-aware so
   repeated shots of one leaf never straddle train/test. Groups = union-find over pHash distance
   <= GROUP_MAX within a source, joined with fixed blocks of BURST_BLOCK consecutive file numbers.
"""

import csv
import hashlib
import os
import random
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

import imagehash
import numpy as np
from PIL import Image
from pillow_heif import register_heif_opener
from sklearn.model_selection import train_test_split

register_heif_opener()

SEED = 42
ML_DIR = Path(__file__).resolve().parent.parent
OUT_CSV = ML_DIR / "v5_field" / "manifest_v5.csv"
REPORT = ML_DIR / "v5_field" / "manifest_v5_report.txt"
DATASET3_MANIFEST = Path(
    r"C:\Users\danse\Downloads\cornguard_external_eval\dataset3_maize_in_field_results\dataset3_manifest.csv"
)

NEAR_DUPLICATE_MAX = 6   # same thresholds as find_duplicates_dataset3.py
SUSPECTED_MAX = 12
GROUP_MAX = NEAR_DUPLICATE_MAX  # 12 chains different leaves together (leaf textures hash alike)
BURST_BLOCK = 10         # files numbered in the same block of 10 are treated as one burst

# Notebook class names; sorted() order == app labels.json order (Blight=NLB, Rust, GLS, Healthy).
CLASSES = ["Blight", "Common_Rust", "Gray_Leaf_Spot", "Healthy"]
PUBLIC_DIRS = {
    "Blight": "data/raw/northern_leaf_blight/public",
    "Common_Rust": "data/raw/common_rust/public",
    "Gray_Leaf_Spot": "data/raw/gray_leaf_spot/public",
    "Healthy": "data/raw/healthy/public",
}
F = "data/downloads/field/"
# (source id, folder, CornGuard class, max images or None)
FIELD_SOURCES = [
    ("mld_3myfctrgk3", F + "mld_3myfctrgk3/Maize dataset/Healthy", "Healthy", None),
    ("mld_3myfctrgk3", F + "mld_3myfctrgk3/Maize dataset/Gray_Spot", "Gray_Leaf_Spot", None),
    ("bdmf_sb4wwjs8bz", F + "bdmf_sb4wwjs8bz/RAW_Image/Healthy_Leaf", "Healthy", None),
    ("bdmf_sb4wwjs8bz", F + "bdmf_sb4wwjs8bz/RAW_Image/Common_Rust", "Common_Rust", None),
    ("bdmf_sb4wwjs8bz", F + "bdmf_sb4wwjs8bz/RAW_Image/Gray_Leaf_Spot", "Gray_Leaf_Spot", None),
    ("tanzania_fkw49mz3xs", F + "tanzania_fkw49mz3xs/HEALTHY", "Healthy", 600),
]
IMG_EXT = (".jpg", ".jpeg", ".png", ".heic", ".heif")


def list_images(folder):
    p = ML_DIR / folder
    if not p.is_dir():
        return []
    return sorted(str(f) for f in p.rglob("*") if f.suffix.lower() in IMG_EXT)


def hash_file(path):
    try:
        with Image.open(path) as im:
            im = im.convert("RGB")
            ph = imagehash.phash(im, hash_size=8)
            w, h = im.size
    except Exception:
        return None
    sha = hashlib.sha256(Path(path).read_bytes()).hexdigest()
    return sha, int(str(ph), 16), w, h


def popcount(x):
    return bin(x).count("1")


def min_distance(h, others):
    if len(others) == 0:
        return 99
    x = np.bitwise_xor(others, np.uint64(h))
    # popcount over uint64
    bits = np.unpackbits(x.view(np.uint8)).reshape(len(others), 64).sum(1)
    return int(bits.min())


def file_number(path):
    m = re.findall(r"\d+", Path(path).stem)
    return int(m[-1]) if m else None


class UF:
    def __init__(self, n):
        self.p = list(range(n))

    def find(self, a):
        while self.p[a] != a:
            self.p[a] = self.p[self.p[a]]
            a = self.p[a]
        return a

    def union(self, a, b):
        self.p[self.find(a)] = self.find(b)


def group_split(rows, rng):
    """70/15/15 by group, per class, so bursts of one leaf stay on one side."""
    by_class = defaultdict(lambda: defaultdict(list))
    for r in rows:
        by_class[r["label"]][r["group"]].append(r)
    for label, groups in by_class.items():
        keys = sorted(groups)
        rng.shuffle(keys)
        total = sum(len(groups[k]) for k in keys)
        done = 0
        for k in keys:
            frac = done / total
            split = "train" if frac < 0.70 else ("val" if frac < 0.85 else "test")
            for r in groups[k]:
                r["split"] = split
            done += len(groups[k])


def main():
    log = []

    def say(*a):
        line = " ".join(str(x) for x in a)
        print(line)
        log.append(line)

    rows = []
    for label, folder in PUBLIC_DIRS.items():
        for f in list_images(folder):
            rows.append({"path": f, "label": label, "source": "public", "domain": "public"})
    rng = random.Random(SEED)
    for src, folder, label, cap in FIELD_SOURCES:
        files = list_images(folder)
        if not files:
            say(f"[warn] no files yet for {src} {label} ({folder})")
            continue
        if cap and len(files) > cap:
            # take whole contiguous chunks rather than random singles, so bursts stay together
            files = sorted(files, key=lambda f: (file_number(f) or 0, f))
            chunk = 20
            chunks = [files[i:i + chunk] for i in range(0, len(files), chunk)]
            rng.shuffle(chunks)
            picked = []
            for c in chunks:
                if len(picked) >= cap:
                    break
                picked.extend(c)
            files = picked[:cap]
        for f in files:
            rows.append({"path": f, "label": label, "source": src, "domain": "field"})
    say("collected:", Counter((r["domain"], r["label"]) for r in rows))

    kept = []
    unreadable = Counter()
    for i, r in enumerate(rows):
        h = hash_file(r["path"])
        if h is None:
            unreadable[(r["source"], r["label"])] += 1
            continue
        r["sha256"], r["phash"], r["w"], r["h"] = h
        kept.append(r)
        if i % 500 == 0:
            print(f"  hashed {i}/{len(rows)}", flush=True)
    say("unreadable dropped:", dict(unreadable))
    rows = kept

    # --- Dataset 3 hard stop (hashes only) ---
    d3_sha, d3_ph = set(), []
    with open(DATASET3_MANIFEST, newline="", encoding="utf-8") as fh:
        for r in csv.DictReader(fh):
            d3_sha.add(r["sha256"])
            d3_ph.append(int(r["phash"], 16))
    d3_ph = np.array(d3_ph, dtype=np.uint64)
    say("Dataset 3 hashes loaded:", len(d3_sha))
    kept, d3_hits = [], Counter()
    for r in rows:
        if r["sha256"] in d3_sha or min_distance(r["phash"], d3_ph) <= SUSPECTED_MAX:
            d3_hits[(r["domain"], r["label"])] += 1
            continue
        kept.append(r)
    say("removed as possible Dataset 3 overlap (pHash <= %d or same SHA):" % SUSPECTED_MAX, dict(d3_hits))
    rows = kept

    # --- field vs public leakage ---
    pub_ph = np.array([r["phash"] for r in rows if r["domain"] == "public"], dtype=np.uint64)
    pub_sha = {r["sha256"] for r in rows if r["domain"] == "public"}
    kept, cross = [], Counter()
    for r in rows:
        if r["domain"] == "field" and (
            r["sha256"] in pub_sha or min_distance(r["phash"], pub_ph) <= NEAR_DUPLICATE_MAX
        ):
            cross[r["label"]] += 1
            continue
        kept.append(r)
    say("field images removed as near-duplicates of public images:", dict(cross))
    rows = kept

    # --- notebook de-dup rule ---
    labels_per_hash = defaultdict(set)
    for r in rows:
        labels_per_hash[r["phash"]].add(r["label"])
    conflicting = {h for h, s in labels_per_hash.items() if len(s) > 1}
    n_conf = sum(r["phash"] in conflicting for r in rows)
    rows = [r for r in rows if r["phash"] not in conflicting]
    seen, kept = set(), []
    for r in rows:
        if r["phash"] in seen:
            continue
        seen.add(r["phash"])
        kept.append(r)
    say(f"conflicting-label images dropped: {n_conf}; duplicate copies dropped: {len(rows) - len(kept)}")
    rows = kept

    # --- split ---
    public = [r for r in rows if r["domain"] == "public"]
    labels = [r["label"] for r in public]
    idx = np.arange(len(public))
    tr, tmp = train_test_split(idx, test_size=0.30, stratify=labels, random_state=SEED)
    va, te = train_test_split(tmp, test_size=0.50, stratify=[labels[i] for i in tmp], random_state=SEED)
    for name, ids in (("train", tr), ("val", va), ("test", te)):
        for i in ids:
            public[i]["split"] = name
            public[i]["group"] = f"public:{i}"

    field = [r for r in rows if r["domain"] == "field"]
    by_src = defaultdict(list)
    for r in field:
        by_src[(r["source"], r["label"])].append(r)
    for (src, label), items in by_src.items():
        uf = UF(len(items))
        ph = np.array([r["phash"] for r in items], dtype=np.uint64)
        for i in range(len(items)):
            x = np.bitwise_xor(ph, np.uint64(items[i]["phash"]))
            d = np.unpackbits(x.view(np.uint8)).reshape(len(items), 64).sum(1)
            for j in np.nonzero(d <= GROUP_MAX)[0]:
                if j > i:
                    uf.union(i, int(j))
        # Fixed blocks of consecutive file numbers (not chained runs: files are numbered 1..N
        # with no gaps, so chaining neighbours would merge the whole folder into one group).
        first_in_block = {}
        for k, r in enumerate(items):
            n = file_number(r["path"])
            if n is None:
                continue
            block = n // BURST_BLOCK
            if block in first_in_block:
                uf.union(k, first_in_block[block])
            else:
                first_in_block[block] = k
        for k, r in enumerate(items):
            r["group"] = f"{src}:{label}:{uf.find(k)}"
    group_split(field, random.Random(SEED))

    rows = public + field
    say("final counts (domain, label, split):")
    for k, v in sorted(Counter((r["domain"], r["label"], r["split"]) for r in rows).items()):
        say("  ", k, v)
    say("field groups per class:", {
        lab: len({r["group"] for r in field if r["label"] == lab}) for lab in CLASSES
    })

    OUT_CSV.parent.mkdir(parents=True, exist_ok=True)
    cols = ["path", "label", "split", "domain", "source", "group", "sha256", "phash", "w", "h"]
    with open(OUT_CSV, "w", newline="", encoding="utf-8") as fh:
        w = csv.DictWriter(fh, fieldnames=cols)
        w.writeheader()
        for r in rows:
            out = {c: r[c] for c in cols}
            out["path"] = os.path.relpath(r["path"], ML_DIR)
            out["phash"] = f"{r['phash']:016x}"
            w.writerow(out)
    REPORT.write_text("\n".join(log) + "\n", encoding="utf-8")
    say("wrote", OUT_CSV)


if __name__ == "__main__":
    sys.exit(main())
