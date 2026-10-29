"""v2 baseline prep, step 1 — inspects the 12 confirmed cross-split duplicate/near-duplicate pairs
from ml/v1_training_audit/duplicate_overlap_findings.csv, recomputes full SHA-256 + perceptual
hashes for every image involved (24 images across 12 pairs), classifies each pair as
SAME_LABEL_DUPLICATE (safe to merge into one split) or LABEL_CONFLICT_UNRESOLVED (requires human
review, not auto-resolved from a model prediction), and writes duplicate_review.csv.

Read-only against ml/data/raw and the v1 manifest. No model is loaded or run.
"""
import csv
import hashlib
from pathlib import Path
from PIL import Image
import imagehash

SCRIPT_DIR = Path(__file__).resolve().parent
ML_DIR = str(SCRIPT_DIR.parent)
AUDIT_FINDINGS = f"{ML_DIR}\\v1_training_audit\\duplicate_overlap_findings.csv"
OUT_DIR = str(SCRIPT_DIR)

with open(AUDIT_FINDINGS, newline="", encoding="utf-8") as f:
    pairs = list(csv.DictReader(f))

def sha256_of(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()

def full_hash(rel_path):
    full_path = f"{ML_DIR}\\{rel_path}"
    sha = sha256_of(full_path)
    with Image.open(full_path) as img:
        ph = imagehash.phash(img.convert("RGB"), hash_size=8)
    return full_path, sha, str(ph)

# Visual review performed directly on the 2 cross-label pairs (not from any model prediction):
# both pairs' "gray_leaf_spot" copy and "northern_leaf_blight" copy are the same underlying
# photograph. In both cases the lesion presentation (a single well-defined elongated tan lesion
# with a dark reddish-brown margin in pair 1; large-scale, extensively blighted field leaves in
# pair 2, credited "(c) Gary Munkvold" in the image itself) reads as visually more consistent with
# Northern Leaf Blight's typical presentation than Gray Leaf Spot's typical multiple small,
# vein-constrained rectangular lesions. This is recorded as a visually-grounded observation for a
# qualified reviewer to weigh — it is NOT applied automatically, and both pairs are marked
# unresolved either way, per instruction not to resolve a label from a model prediction and to
# hold ambiguous cases for review rather than auto-correcting them.
VISUAL_NOTES = {
    ("Corn_Gray_Spot (564)", "Corn_Blight (1104)"): (
        "Byte-identical image. Single elongated tan-centered lesion with dark reddish-brown "
        "border on an otherwise healthy leaf. Visually more consistent with a single Northern "
        "Leaf Blight lesion than Gray Leaf Spot's typical multiple small rectangular, "
        "vein-constrained lesions -- an observation only, not applied as a label change."
    ),
    ("Corn_Gray_Spot (29)", "Corn_Blight (106)"): (
        "Same photographic content (phash distance 0), different file encoding. Wide field view "
        "of multiple corn plants with extensive tan/white blighted leaf patches; image itself is "
        "credited '(c) Gary Munkvold'. Visually more consistent with severe Northern Leaf Blight "
        "field damage than Gray Leaf Spot -- an observation only, not applied as a label change."
    ),
}

rows_out = []
involved_sample_ids = set()
for p in pairs:
    path_a, sha_a, ph_a = full_hash(p["path_a"])
    path_b, sha_b, ph_b = full_hash(p["path_b"])
    involved_sample_ids.add(p["sample_id_a"])
    involved_sample_ids.add(p["sample_id_b"])

    is_label_conflict = p["class_a"] != p["class_b"]
    key = (p["sample_id_a"], p["sample_id_b"])
    visual_note = VISUAL_NOTES.get(key, "")

    rows_out.append({
        "overlap_type": p["overlap_type"],
        "hamming_distance": p["hamming_distance"],
        "sample_id_a": p["sample_id_a"], "split_a": p["split_a"], "class_a": p["class_a"],
        "path_a": path_a, "sha256_a": sha_a, "phash_a": ph_a,
        "sample_id_b": p["sample_id_b"], "split_b": p["split_b"], "class_b": p["class_b"],
        "path_b": path_b, "sha256_b": sha_b, "phash_b": ph_b,
        "review_classification": "LABEL_CONFLICT_UNRESOLVED" if is_label_conflict else "SAME_LABEL_DUPLICATE",
        "visual_observation_not_auto_applied": visual_note,
        "proposed_action": (
            "EXCLUDE both images from proposed_clean_manifest.csv pending qualified human review "
            "of the true label"
        ) if is_label_conflict else (
            "MERGE: reassign the non-train-split member into train, so the whole duplicate pair "
            "lands in a single split"
        ),
    })

with open(f"{OUT_DIR}\\duplicate_review.csv", "w", newline="", encoding="utf-8") as f:
    fieldnames = list(rows_out[0].keys())
    w = csv.DictWriter(f, fieldnames=fieldnames)
    w.writeheader()
    w.writerows(rows_out)

n_conflict = sum(1 for r in rows_out if r["review_classification"] == "LABEL_CONFLICT_UNRESOLVED")
n_same = len(rows_out) - n_conflict
print(f"Wrote {len(rows_out)} reviewed pairs to duplicate_review.csv")
print(f"  SAME_LABEL_DUPLICATE (safe to merge into one split): {n_same}")
print(f"  LABEL_CONFLICT_UNRESOLVED (excluded pending review): {n_conflict}")
print(f"  Total distinct images involved across all 12 pairs: {len(involved_sample_ids)}")
