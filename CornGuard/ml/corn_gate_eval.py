"""
Measures how well a trained CornGuard model can tell "corn leaf" from "not corn" using post-hoc
scores, following the method in docs/supporting study claim/rejection_threshold_supporting_studies.md
(Sci. Reports 2024: score each input, reject it when the score passes a threshold).

Nothing is retrained. The model's outputs are turned into five "corn-likeness" scores, where
higher always means more corn-like:

    msp         max softmax probability                    (computable in the app today)
    margin      top-1 minus top-2 softmax probability      (computable in the app today)
    neg_entropy minus the softmax entropy                  (computable in the app today)
    max_logit   largest pre-softmax logit                  (needs a logits output in the TFLite export)
    neg_energy  logsumexp of the logits                    (needs a logits output in the TFLite export)

Threshold selection: for each score, the threshold is set on the CORN VALIDATION split so that a
target fraction of corn images (default 95%) is accepted. It is then applied, unchanged, to the
corn TEST split and to the not-corn images, so the reported rates are not tuned on the data they
are reported on.

Inputs:
    --manifest-path      corn images (val split for thresholds, test split for reporting)
    --not-corn-dir       folder of non-corn images; each immediate subfolder is reported as its
                         own source (e.g. not_corn/flowers, not_corn/screens). Optional: without
                         it the script still writes corn score distributions and thresholds, but
                         cannot report AUROC or false-accept rates.
    --extra-corn-dir     optional folder of extra corn photos that must be accepted (e.g. phone
                         photos of real field leaves), reported separately. Do not point this at
                         Dataset 3: it is reserved for rationed final checks.

Usage (from the ml/ directory):
    python corn_gate_eval.py --run-id v2-clean-baseline --not-corn-dir data/not_corn
"""

from __future__ import annotations

import argparse
import csv
import json
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
import pandas as pd
import tensorflow as tf
from sklearn.metrics import roc_auc_score

import config
# Unused directly, but importing it registers MobileNetV2Preprocess so load_model can rebuild the
# saved model (same requirement as evaluate.py).
import model  # noqa: F401

SCORES = ["msp", "margin", "neg_entropy", "max_logit", "neg_energy"]
APP_COMPUTABLE = {"msp", "margin", "neg_entropy"}
IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}


# ---------------------------------------------------------------------------
# Model and scoring
# ---------------------------------------------------------------------------

def build_logits_model(keras_model: tf.keras.Model) -> tf.keras.Model:
    """
    Returns a model that outputs pre-softmax logits. The saved model's last layer is a Dense with a
    softmax activation, so logits = penultimate_features @ W + b using that layer's own weights.
    The penultimate layer is dropout, which is the identity at inference time.
    """
    dense = keras_model.get_layer("class_probabilities")
    features = keras_model.get_layer("head_dropout").output
    kernel, bias = dense.get_weights()
    logits = tf.keras.layers.Dense(
        kernel.shape[1], activation=None, name="logits",
        kernel_initializer=tf.constant_initializer(kernel),
        bias_initializer=tf.constant_initializer(bias),
    )(features)
    return tf.keras.Model(keras_model.input, logits, name="logits_model")


def scores_from_logits(logits: np.ndarray) -> dict[str, np.ndarray]:
    shifted = logits - logits.max(axis=1, keepdims=True)
    exp = np.exp(shifted)
    probs = exp / exp.sum(axis=1, keepdims=True)
    sorted_probs = np.sort(probs, axis=1)
    log_probs = np.log(np.clip(probs, 1e-12, 1.0))
    return {
        "msp": sorted_probs[:, -1],
        "margin": sorted_probs[:, -1] - sorted_probs[:, -2],
        "neg_entropy": (probs * log_probs).sum(axis=1),
        "max_logit": logits.max(axis=1),
        "neg_energy": logits.max(axis=1) + np.log(exp.sum(axis=1)),
        "_probs": probs,
    }


def _load_image(path: str) -> tf.Tensor:
    raw = tf.io.read_file(path)
    # decode_image (not decode_jpeg) so PNG/BMP/WebP phone or screenshot images also load.
    image = tf.io.decode_image(raw, channels=config.IMAGE_CHANNELS, expand_animations=False)
    image = tf.image.resize(image, config.IMAGE_SIZE)  # same bilinear resize as dataset.py
    return image


def score_paths(logits_model: tf.keras.Model, paths: list[str], batch_size: int) -> dict[str, np.ndarray]:
    ds = tf.data.Dataset.from_tensor_slices(paths).map(_load_image, num_parallel_calls=tf.data.AUTOTUNE)
    ds = ds.batch(batch_size).prefetch(tf.data.AUTOTUNE)
    logits = np.concatenate([logits_model(batch, training=False).numpy() for batch in ds], axis=0)
    return scores_from_logits(logits)


# ---------------------------------------------------------------------------
# Inputs
# ---------------------------------------------------------------------------

def list_images(folder: Path) -> list[Path]:
    return sorted(p for p in folder.rglob("*") if p.is_file() and p.suffix.lower() in IMAGE_EXTENSIONS)


def not_corn_sources(folder: Path) -> dict[str, list[Path]]:
    """Each immediate subfolder is one source; loose files directly in the folder are 'unsorted'."""
    sources: dict[str, list[Path]] = {}
    loose = sorted(p for p in folder.iterdir() if p.is_file() and p.suffix.lower() in IMAGE_EXTENSIONS)
    if loose:
        sources["unsorted"] = loose
    for sub in sorted(p for p in folder.iterdir() if p.is_dir()):
        images = list_images(sub)
        if images:
            sources[sub.name] = images
    return sources


# ---------------------------------------------------------------------------
# Metrics
# ---------------------------------------------------------------------------

def threshold_for_acceptance(corn_scores: np.ndarray, accept_rate: float) -> float:
    """Lowest score that still accepts `accept_rate` of the given corn images (accept if score >= t)."""
    return float(np.quantile(corn_scores, 1.0 - accept_rate))


def evaluate_scores(groups: dict[str, dict], accept_rate: float) -> dict:
    """groups: name -> {"role": "corn_val"|"corn_test"|"corn_extra"|"not_corn", "scores": {...}}"""
    val = groups["corn_val"]["scores"]
    test = groups["corn_test"]["scores"]
    not_corn_names = [n for n, g in groups.items() if g["role"] == "not_corn"]

    results = {}
    for score in SCORES:
        threshold = threshold_for_acceptance(val[score], accept_rate)
        entry = {
            "app_computable_today": score in APP_COMPUTABLE,
            "threshold": threshold,
            "corn_val_accept_rate": float(np.mean(val[score] >= threshold)),
            "corn_test_accept_rate": float(np.mean(test[score] >= threshold)),
        }
        for name, group in groups.items():
            if group["role"] == "corn_extra":
                entry[f"{name}_accept_rate"] = float(np.mean(group["scores"][score] >= threshold))

        if not_corn_names:
            all_not_corn = np.concatenate([groups[n]["scores"][score] for n in not_corn_names])
            y_true = np.concatenate([np.ones_like(test[score]), np.zeros_like(all_not_corn)])
            y_score = np.concatenate([test[score], all_not_corn])
            entry["auroc_corn_test_vs_not_corn"] = float(roc_auc_score(y_true, y_score))
            # FPR@95TPR on the test split itself (threshold re-derived on test, as in the study's tables).
            test_threshold = threshold_for_acceptance(test[score], 0.95)
            entry["fpr_at_95_tpr"] = float(np.mean(all_not_corn >= test_threshold))
            entry["not_corn_reject_rate_overall"] = float(np.mean(all_not_corn < threshold))
            entry["not_corn_reject_rate_by_source"] = {
                n: float(np.mean(groups[n]["scores"][score] < threshold)) for n in not_corn_names
            }
        results[score] = entry
    return results


def score_distribution(values: np.ndarray) -> dict:
    q = np.quantile(values, [0.05, 0.25, 0.5, 0.75, 0.95])
    return {"n": int(len(values)), "min": float(values.min()), "p05": float(q[0]), "p25": float(q[1]),
            "median": float(q[2]), "p75": float(q[3]), "p95": float(q[4]), "max": float(values.max())}


# ---------------------------------------------------------------------------
# Report
# ---------------------------------------------------------------------------

def write_markdown(path: Path, report: dict) -> None:
    lines = [
        f"# Corn gate evaluation: {report['run_id']}",
        "",
        f"Generated {report['generated_at_utc']} by `ml/corn_gate_eval.py`. No model was retrained.",
        "",
        f"- Model: `{report['model_path']}`",
        f"- Corn images: val {report['counts']['corn_val']} (threshold setting), "
        f"test {report['counts']['corn_test']} (reporting), from `{report['manifest_path']}`",
        f"- Target corn acceptance on val: {report['target_accept_rate']:.0%}",
    ]
    nc = {k: v for k, v in report["counts"].items() if k.startswith("not_corn:")}
    if nc:
        lines.append("- Not-corn images: " + ", ".join(f"{k.split(':', 1)[1]} {v}" for k, v in nc.items()))
    else:
        lines += ["", "**No not-corn images were supplied, so AUROC and false-accept rates are not "
                  "available. Only corn-side thresholds are reported.**"]
    extra = {k: v for k, v in report["counts"].items() if k.startswith("corn_extra")}
    if extra:
        lines.append("- Extra corn images: " + ", ".join(f"{k} {v}" for k, v in extra.items()))
    lines += ["", "## Results per score", ""]

    header = ["Score", "In app today", "Threshold", "Corn test accepted"]
    if nc:
        header += ["AUROC", "FPR@95TPR", "Not-corn rejected"]
    header += [f"{k} accepted" for k in extra]
    lines.append("| " + " | ".join(header) + " |")
    lines.append("|" + "---|" * len(header))
    for score, r in report["scores"].items():
        row = [score, "yes" if r["app_computable_today"] else "no (needs logits)",
               f"{r['threshold']:.4f}", f"{r['corn_test_accept_rate']:.1%}"]
        if nc:
            row += [f"{r['auroc_corn_test_vs_not_corn']:.4f}", f"{r['fpr_at_95_tpr']:.1%}",
                    f"{r['not_corn_reject_rate_overall']:.1%}"]
        row += [f"{r[f'{k}_accept_rate']:.1%}" for k in extra]
        lines.append("| " + " | ".join(row) + " |")

    if nc:
        lines += ["", "## Not-corn rejection by source (at the val-set threshold)", ""]
        sources = list(next(iter(report["scores"].values()))["not_corn_reject_rate_by_source"].keys())
        lines.append("| Score | " + " | ".join(sources) + " |")
        lines.append("|---|" + "---|" * len(sources))
        for score, r in report["scores"].items():
            lines.append(f"| {score} | " + " | ".join(
                f"{r['not_corn_reject_rate_by_source'][s]:.1%}" for s in sources) + " |")

    lines += ["", "## How to read this", "",
              "- A score is useful only if it rejects most not-corn images while keeping corn test "
              "acceptance near the target. AUROC of 0.5 means no separation; 1.0 is perfect.",
              "- `max_logit` and `neg_energy` cannot be used by the app until the TFLite export also "
              "outputs logits.",
              "- These corn images are public-style test photos. Acceptance of real field photos must "
              "be checked separately (`--extra-corn-dir`) before any threshold ships."]
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--run-id", default="v2-clean-baseline")
    parser.add_argument("--model-path", type=Path, default=None,
                        help="defaults to artifacts/<run-id>/model.keras")
    parser.add_argument("--manifest-path", type=Path, default=Path("v2_baseline/proposed_clean_manifest.csv"))
    parser.add_argument("--dataset-root", type=Path, default=Path("."))
    parser.add_argument("--not-corn-dir", type=Path, default=None)
    parser.add_argument("--extra-corn-dir", type=Path, default=None)
    parser.add_argument("--accept-rate", type=float, default=0.95)
    parser.add_argument("--batch-size", type=int, default=32)
    parser.add_argument("--output-dir", type=Path, default=None,
                        help="defaults to artifacts/<run-id>/corn_gate")
    args = parser.parse_args()

    model_path = args.model_path or config.ARTIFACTS_DIR / args.run_id / "model.keras"
    output_dir = args.output_dir or config.ARTIFACTS_DIR / args.run_id / "corn_gate"
    output_dir.mkdir(parents=True, exist_ok=True)

    keras_model = tf.keras.models.load_model(model_path)
    logits_model = build_logits_model(keras_model)

    manifest = pd.read_csv(args.manifest_path)
    inputs: dict[str, tuple[str, list[str]]] = {}
    for split in ("val", "test"):
        rows = manifest[manifest["split"] == split]
        if rows.empty:
            raise SystemExit(f"No '{split}' rows in {args.manifest_path}.")
        inputs[f"corn_{split}"] = (f"corn_{split}", [str(args.dataset_root / p) for p in rows["relative_path"]])
    if args.extra_corn_dir:
        images = list_images(args.extra_corn_dir)
        if not images:
            raise SystemExit(f"No images found under {args.extra_corn_dir}.")
        inputs["corn_extra"] = ("corn_extra", [str(p) for p in images])
    if args.not_corn_dir:
        sources = not_corn_sources(args.not_corn_dir)
        if not sources:
            raise SystemExit(f"No images found under {args.not_corn_dir}.")
        for name, images in sources.items():
            inputs[f"not_corn:{name}"] = ("not_corn", [str(p) for p in images])

    # Sanity check: the rebuilt logits must reproduce the saved model's softmax output.
    probe = inputs["corn_val"][1][:8]
    probe_batch = tf.stack([_load_image(p) for p in probe])
    saved_probs = keras_model(probe_batch, training=False).numpy()
    rebuilt_probs = scores_from_logits(logits_model(probe_batch, training=False).numpy())["_probs"]
    max_diff = float(np.max(np.abs(saved_probs - rebuilt_probs)))
    if max_diff > 1e-4:
        raise SystemExit(f"Rebuilt logits do not match the saved model's softmax (max diff {max_diff}).")
    print(f"Logits check passed (max softmax diff {max_diff:.2e}).")

    groups: dict[str, dict] = {}
    rows_out = []
    for name, (role, paths) in inputs.items():
        print(f"Scoring {name}: {len(paths)} images...")
        scores = score_paths(logits_model, paths, args.batch_size)
        groups[name] = {"role": role, "scores": scores}
        predicted = scores["_probs"].argmax(axis=1)
        for i, p in enumerate(paths):
            rows_out.append({"group": name, "role": role, "path": p,
                             "predicted_class": config.CLASS_ORDER[predicted[i]],
                             **{s: round(float(scores[s][i]), 6) for s in SCORES}})

    report = {
        "run_id": args.run_id,
        "generated_at_utc": datetime.now(timezone.utc).isoformat(),
        "model_path": str(model_path),
        "manifest_path": str(args.manifest_path),
        "target_accept_rate": args.accept_rate,
        "logits_check_max_softmax_diff": max_diff,
        "counts": {name: len(paths) for name, (_, paths) in inputs.items()},
        "corn_score_distributions": {
            name: {s: score_distribution(g["scores"][s]) for s in SCORES}
            for name, g in groups.items() if g["role"] != "not_corn"
        },
        "not_corn_score_distributions": {
            name: {s: score_distribution(g["scores"][s]) for s in SCORES}
            for name, g in groups.items() if g["role"] == "not_corn"
        },
        "scores": evaluate_scores(groups, args.accept_rate),
    }

    (output_dir / "corn_gate_report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    with (output_dir / "corn_gate_scores.csv").open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=list(rows_out[0].keys()))
        writer.writeheader()
        writer.writerows(rows_out)
    write_markdown(output_dir / "corn_gate_report.md", report)
    print(f"Wrote {output_dir / 'corn_gate_report.md'}, corn_gate_report.json, corn_gate_scores.csv")


if __name__ == "__main__":
    main()
