import csv
from pathlib import Path
MANIFEST_PATH = Path(__file__).resolve().parent / "proposed_clean_manifest.csv"
with open(MANIFEST_PATH, newline="", encoding="utf-8") as f:
    rows = list(csv.DictReader(f))
bad = [r for r in rows if not r["relative_path"].startswith("data\\raw\\")]
print("rows whose relative_path does NOT start with data\\raw\\ (should be 0):", len(bad))
if bad:
    for r in bad[:5]:
        print(" ", r)
print("sample paths:", rows[0]["relative_path"], "|", rows[-1]["relative_path"])
