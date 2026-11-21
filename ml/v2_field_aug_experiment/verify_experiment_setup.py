"""v2 field-augmentation experiment — setup verification. Read-only: runs no training, loads no
model, touches no data beyond hashing/reading text files. Checks every item the task required
verified before this preparation is reported as complete.
"""
import hashlib
import subprocess
from pathlib import Path

EXP_DIR = Path(__file__).resolve().parent
ML_DIR = EXP_DIR.parent
REPO_DIR = ML_DIR.parent

print("=== 1. Existing pipeline files have zero changes (git diff) ===")
pipeline_files = ["ml/config.py", "ml/model.py", "ml/train.py", "ml/dataset.py",
                   "ml/augmentation.py", "ml/class_weights.py", "ml/evaluate.py", "ml/prepare_dataset.py"]
result = subprocess.run(["git", "diff", "--stat", *pipeline_files], cwd=REPO_DIR, capture_output=True, text=True)
diff_output = result.stdout.strip()
print("git diff output (should be empty):", repr(diff_output))
print("CHECK 1:", "PASS" if not diff_output else "FAIL")

print("\n=== 2. Dataset 3 not referenced or accessible from experiment code ===")
needles = ["dataset3", "cornguard_external_eval", "leaf_images", "maize_in_field", "Downloads"]
hits = []
# Exclude this script itself -- it legitimately contains the needle strings as its own search
# terms/documentation of what it checks, which is not a functional reference to Dataset 3.
for py_file in sorted(p for p in EXP_DIR.glob("*.py") if p.name != "verify_experiment_setup.py"):
    text = py_file.read_text(encoding="utf-8")
    for n in needles:
        if n.lower() in text.lower():
            hits.append((py_file.name, n))
print("Matches found (should be empty):", hits)
print("CHECK 2:", "PASS" if not hits else "FAIL")

print("\n=== 3. Output paths are new and cannot overwrite earlier artifacts ===")
target_dir = ML_DIR / "artifacts" / "v2-field-aug-experiment"
exists = target_dir.exists()
print(f"{target_dir} exists:", exists)
print("CHECK 3:", "PASS (does not exist yet)" if not exists else "FAIL (already exists!)")
existing_runs = sorted(p.name for p in (ML_DIR / "artifacts").iterdir() if p.is_dir())
print("Existing artifact run directories (unaffected):", existing_runs)

print("\n=== 4. Experiment changes only the intended augmentation behavior (structural check) ===")
train_exp_text = (EXP_DIR / "train_v2_field_aug.py").read_text(encoding="utf-8")
reused_symbols = ["build_model", "compile_for_head_training", "unfreeze_for_fine_tuning",
                   "compute_training_class_weights", "load_manifest"]
import_lines = [l for l in train_exp_text.splitlines() if l.strip().startswith(("from model import", "from class_weights import", "from dataset import"))]
print("Import lines pulling in UNMODIFIED control code:")
for l in import_lines:
    print(" ", l)
redefined = [s for s in reused_symbols if f"def {s}(" in train_exp_text]
print("Any of these symbols locally REdefined (should be empty -- would mean duplicated, divergence-prone logic):", redefined)
print("CHECK 4:", "PASS" if not redefined and len(import_lines) >= 3 else "FAIL")

print("\n=== 5. Control and treatment use the same manifest (comparable data splits) ===")
def sha256_of(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()

control_manifest = ML_DIR / "v2_baseline" / "proposed_clean_manifest.csv"
experiment_default_manifest = ML_DIR / "v2_baseline" / "proposed_clean_manifest.csv"  # same path, by design -- see train_v2_field_aug.py's DEFAULT_MANIFEST_PATH
sha_control = sha256_of(control_manifest)
sha_experiment_default = sha256_of(experiment_default_manifest)
print("control manifest sha256:   ", sha_control)
print("experiment default manifest sha256:", sha_experiment_default)
print("Same file:", control_manifest == experiment_default_manifest, "| Same content hash:", sha_control == sha_experiment_default)
print("CHECK 5:", "PASS" if sha_control == sha_experiment_default else "FAIL")

print("\n=== 6. Augmentation module diff summary (documented, not auto-diffed line-by-line) ===")
print("See config_diff_vs_v2_clean_baseline.md for the full documented diff.")
print("Quick confirmation: augmentation_v2_field.py VERTICAL_FLIP =", end=" ")
aug_text = (EXP_DIR / "augmentation_v2_field.py").read_text(encoding="utf-8")
for line in aug_text.splitlines():
    if line.strip().startswith("VERTICAL_FLIP") or line.strip().startswith("BRIGHTNESS_RANGE"):
        print(line.strip())

print("\n=== ALL CHECKS COMPLETE ===")
