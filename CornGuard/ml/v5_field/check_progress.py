"""
Shows where the v5 training run is, from artifacts/v5-field/train_run_log.txt.

  cd ml
  .venv\\Scripts\\python v5_field\\check_progress.py          # summary
  .venv\\Scripts\\python v5_field\\check_progress.py --watch     # refresh every 30 s (Ctrl+C to stop)
  .venv\\Scripts\\python v5_field\\check_progress.py --watch 1   # refresh every 1 s
"""

import os
import re
import sys
import time
from pathlib import Path

ML_DIR = Path(__file__).resolve().parent.parent
LOG = ML_DIR / "artifacts" / "v5-field" / "train_run_log.txt"
CACHE = ML_DIR / "data" / "v5_cache"


def summary():
    if not LOG.exists():
        n = len(list(CACHE.glob("*.jpg"))) if CACHE.exists() else 0
        return f"No log yet. Cached images: {n}"
    text = LOG.read_text(encoding="utf-8", errors="replace")
    out = []

    phase = "fine-tune" if text.count("Epoch 1/") >= 2 else "head"
    epochs = re.findall(r"Epoch (\d+)/(\d+)", text)
    if epochs:
        cur, total = epochs[-1]
        out.append(f"Phase: {phase}   epoch {cur}/{total}")

    # Keras verbose=2 epoch summary lines + our MacroF1 line
    # Keras' end-of-epoch line ("128/128 - 216s - ... - val_macro_f1: ... - val_field_healthy_recall: ...")
    # carries every number we need, so parse only that line (the callback's own print repeats them).
    epoch_lines = re.findall(
        r"\d+/\d+ - (\d+)s - .*?accuracy: ([\d.]+) .*?val_accuracy: ([\d.]+)"
        r".*?val_macro_f1: ([\d.]+) - val_field_healthy_recall: ([\d.na]+)", text)
    if epoch_lines:
        out.append("  #   sec  train_acc  val_acc  val_macroF1  field_healthy_recall")
        for i, (secs, acc, vacc, f1, fh) in enumerate(epoch_lines, 1):
            out.append(f"  {i:<3} {secs:>4}  {acc:>9}  {vacc:>7}  {f1:>11}  {fh:>20}")

    for key in ("Restoring model weights", "TEST public", "TEST field", "TEST all",
                "TFLite vs Keras agreement", "done in"):
        for line in text.splitlines():
            if key in line:
                out.append(line.strip())
    if "Traceback" in text:
        out.append("ERROR in log:\n" + text[text.rfind("Traceback"):][-1500:])
    if "done in" not in text and "Traceback" not in text:
        out.append("Status: running")
    return "\n".join(out) or "Log is empty (still starting)."


if __name__ == "__main__":
    if "--watch" in sys.argv:
        i = sys.argv.index("--watch")
        try:
            interval = float(sys.argv[i + 1]) if i + 1 < len(sys.argv) else 30.0
        except ValueError:
            sys.exit("usage: check_progress.py [--watch [seconds]]")
        interval = max(interval, 0.5)
        try:
            while True:
                text = summary()
                os.system("cls" if os.name == "nt" else "clear")
                print(time.strftime("%H:%M:%S") + f"  (every {interval:g}s, Ctrl+C to stop)\n" + text, flush=True)
                time.sleep(interval)
        except KeyboardInterrupt:
            pass
    else:
        print(summary())
