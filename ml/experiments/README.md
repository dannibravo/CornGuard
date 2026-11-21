# ML experiments and audits

Each folder holds one audit or experiment: the code it ran, its raw run logs (`*_run_log.txt`),
its CSV outputs, and its written reports. Model checkpoints are **not** stored here. They live
in the git-ignored `ml/artifacts/<run_id>/`, and anything shipped in the app is recorded in
`ml/releases/`.

Listed in the order they build on each other (each later folder's reports cite the earlier ones):

| Folder | Run ID | What it is | Start with |
|---|---|---|---|
| `v1-training-audit/` | `v1-baseline` (audited, not retrained) | Read-only audit of the v1 pipeline and its training data. Found the 12 cross-split duplicate pairs. | `audit_report.md` |
| `v2-clean-baseline/` | `v2-clean-baseline` | Builds the corrected manifest (`proposed_clean_manifest.csv`) from the audit's findings, then trains/evaluates on it. | `v2_clean_baseline_results.md` |
| `v2-field-domain-planning/` | — | Evidence summary and staged plan for closing the field-domain gap. Planning only. | `field_domain_gap_plan.md` |
| `v2-field-aug-experiment/` | `v2-field-aug-experiment` | Isolated augmentation-only change vs. `v2-clean-baseline`. | `v2_field_aug_results.md` |
| `field-gap-evaluation-2026-10-30/` | — | Synthesis of external (Dataset 3) field results across checkpoints. Analysis only. | `field_gap_investigation.md` |

## Running experiment code

Run from `ml/`, same as the pipeline, e.g.:

```bash
cd ml
python experiments/v2-field-aug-experiment/train_v2_field_aug.py --run-id v2-field-aug-experiment
```

Note: several audit scripts (`check_split_overlap.py`, `duplicate_review.py`,
`build_clean_manifest.py`, `dataset_leakage_check.py`) build paths with Windows `\` separators
and only run correctly on Windows.

## Paths in older reports

These folders were moved on 2026-11-21 (previously directly under `ml/`). Location pointers in
the reports were updated, but **commands recorded as actually run** (e.g. in
`v2_clean_baseline_results.md`, `v2_field_aug_results.md`, `pretraining_verification_report.md`)
were left exactly as executed. Translate them with:

| Old path (relative to `ml/`) | New path (relative to `ml/`) |
|---|---|
| `v1_training_audit/` | `experiments/v1-training-audit/` |
| `v2_baseline/` | `experiments/v2-clean-baseline/` |
| `v2_field_domain_planning/` | `experiments/v2-field-domain-planning/` |
| `v2_field_aug_experiment/` | `experiments/v2-field-aug-experiment/` |
| `field_gap_evaluation_comparison_20261030/` | `experiments/field-gap-evaluation-2026-10-30/` |
| `config.py`, `dataset.py`, `augmentation.py`, `model.py`, `class_weights.py` | `cornguard_ml/<same name>` |
| `python train.py …` (and the other stage scripts) | `python -m pipeline.train …` |
