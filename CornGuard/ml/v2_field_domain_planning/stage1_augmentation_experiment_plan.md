# CORNGUARD Stage 1 — Isolated Augmentation Experiment Plan (PLAN ONLY — NOT EXECUTED)

Conditionally authorized, pending your review of `stage0_preprocessing_audit.md`. **Nothing in this document has been run.** `ml/augmentation.py` has not been modified and will not be modified — any new augmentation logic goes in a new, separate file. No training has started.

## Hypothesis this experiment tests

From Stage 0 §4: the current training-only augmentation (`augmentation.py`) includes a vertical flip that may be anatomically unrealistic for corn leaves, and a brightness range (±20%) that is plausibly too narrow to simulate real field lighting. This experiment isolates **augmentation strategy alone** as the one changed variable, to test whether it narrows the measured gap to field-like conditions — without touching data, architecture, or any other hyperparameter, and **without using Dataset 3 at any point**.

## Control vs. treatment

| | Control | Treatment |
|---|---|---|
| Manifest | `ml/v2_baseline/proposed_clean_manifest.csv` (unchanged, exact same file) | same file, unchanged |
| Architecture, preprocessing, class order, loss, optimizer, epochs, early stopping, class weights | exact v2-clean-baseline configuration (`ml/config.py`, `ml/model.py`, `ml/train.py` — unmodified) | identical, unmodified |
| Augmentation | `ml/augmentation.py` as-is (horizontal+vertical flip, ±15° rotation, ±10% zoom, 80–120% brightness) | **new** module, e.g. `ml/v2_field_domain_planning/augmentation_v2_field.py` — not a replacement of the original file |
| Validation data | unaugmented (already true in `dataset.py` — `val_ds` is always built with `augment=False`) | unaugmented, identical |
| Test data | unaugmented | unaugmented |

The control run **is** `v2-clean-baseline`, already trained and evaluated (`ml/artifacts/v2-clean-baseline/evaluation_report.json`, 91.99% accuracy / 90.07% macro F1) — it does not need to be re-run. The treatment run is the only new training this plan proposes.

## Proposed augmentation changes (for your approval, not yet implemented)

1. **Remove vertical flip**, keep horizontal flip — addresses the orientation-realism concern from Stage 0 §4.
2. **Widen brightness range**, e.g. from 80–120% to roughly 60–140% (exact bounds open to discussion) — addresses the field-lighting-realism concern from Stage 0 §4.
3. **Keep rotation (±15°) and zoom (±10%) unchanged** — Stage 0 found no concern with either.
4. **Do not add any transform not already assessed in Stage 0** (e.g. no synthetic occlusion, no background compositing) in this first isolated experiment — one change at a time, so results are interpretable. Transforms not yet assessed would need their own Stage 0-style review first.

This keeps the experiment to the two specific, Stage-0-identified changes — not a general augmentation overhaul — so a result (positive or negative) is attributable to those specific choices.

## Implementation plan (not yet written)

- New file: `ml/v2_field_domain_planning/augmentation_v2_field.py` — a copy of `build_augmentation_layer()`'s structure with only the two changes above. `ml/augmentation.py` itself stays untouched; `train.py` is not edited either — instead, a new training entry point `ml/v2_field_domain_planning/train_v2_field_aug.py` would import the new augmentation module and otherwise call the exact same `build_model`, `compile_for_head_training`, `unfreeze_for_fine_tuning`, `build_split_dataset`, `compute_training_class_weights` functions `train.py` already uses — no duplication of training logic beyond swapping which augmentation layer is passed in.
- This keeps the "do not modify any existing v1 training pipeline file" rule airtight: every existing `.py` file in `ml/` remains byte-for-byte unchanged; only new files are added.

## Random seeds

- `config.RANDOM_SEED = 42` stays as-is for the manifest/shuffle seed (already baked into `proposed_clean_manifest.csv`'s split and into `dataset.py`'s `ds.shuffle(..., seed=config.RANDOM_SEED, ...)`) — the data ordering stays comparable to the control run.
- Keras/TensorFlow global random seed for weight initialization: **not currently set anywhere in `model.py` or `train.py`** (confirmed by inspection — this is a fact about the existing code, not a Stage 0 finding I'm newly reporting as a defect, just noting it here since it's relevant to this experiment's reproducibility). For Stage 1, I propose explicitly setting `tf.keras.utils.set_random_seed(42)` at the start of the new `train_v2_field_aug.py` entry point, so the treatment run's weight initialization is reproducible and comparable run-to-run — this is a new file's addition, not a change to any existing file.

## Output paths

- New run artifacts: `ml/artifacts/v2-field-aug-experiment/` (new `--run-id`, same `--artifacts-dir artifacts` pattern used for `v2-clean-baseline` — `train.py`'s own `run_dir.mkdir(parents=True, exist_ok=True)` guarantees no collision with `v1-baseline/` or `v2-clean-baseline/`).
- New augmentation module, training entry point, and this plan all live under `ml/v2_field_domain_planning/`.

## Leakage checks

No new data is introduced in Stage 1 — the manifest is identical to `v2-clean-baseline`'s, already verified leak-free against the known detection method (Stage 0 §5, re-confirmed twice). **No new leakage check is required for this specific experiment** since nothing about the data or split changes, only the augmentation applied at training time. If Stage 2 (sourcing new field data) is later approved, that would require the full duplicate-screening protocol from `field_domain_gap_plan.md` §D — out of scope here.

## Metrics to record (every run, same format as v1/v2-clean)

- Overall accuracy, macro/weighted precision/recall/F1, full confusion matrix (verified class order), per-class precision/recall/F1/support — via `evaluate.py`, unmodified, run against the same `v2_baseline/proposed_clean_manifest.csv` test split.
- `training_history.json` (head + fine-tune phase accuracy/loss/val_accuracy/val_loss per epoch) — same format `train.py` already produces.
- A short comparison table against the `v2-clean-baseline` control, by class, matching the format already used in `v2_clean_baseline_results.md`.

## Candidate selection without touching Dataset 3

- **Primary selection signal: validation accuracy/loss during training** (`val_accuracy`, monitored by the existing `ModelCheckpoint`/`EarlyStopping` callbacks, unmodified) — this is how `best_head.keras`/`best_fine_tuned.keras` are already chosen in the current pipeline, and Stage 1 reuses that mechanism as-is.
- **Secondary, post-hoc signal: the held-out test split's full metrics** (the same 624-image `v2_baseline` test set), evaluated once after training completes, exactly as done for `v2-clean-baseline` — used to compare treatment vs. control, not to pick between multiple treatment variants mid-experiment.
- **Dataset 3 is not used anywhere in this process.** If, after reviewing the treatment-vs-control comparison on the internal test set, there's a case for checking real-world field performance, that would be **one single, logged Dataset 3 evaluation of the one best candidate**, requested from you explicitly and recorded per `field_domain_gap_plan.md` §E's rationed-evaluation-log recommendation — not run automatically as part of this plan, and not run repeatedly to compare multiple augmentation variants against each other.

## Avoiding repeated tuning against the 624-image test set

- This plan proposes **exactly one treatment training run** and **one evaluation of it** against the test split, compared once to the existing control result. It does not propose a hyperparameter sweep (e.g. trying several brightness ranges) that would each query the test set — if you want to compare multiple brightness ranges, that should be a separate, explicitly-approved follow-up with its own plan, not folded into this one.
- If a future need arises to compare several augmentation variants, the recommended approach is to select among them using **validation metrics only**, and reserve a single test-set evaluation for the validation-selected winner — never iterate directly against the test set's reported number.

## What this plan does not do

- Does not modify `ml/augmentation.py`, `ml/train.py`, `ml/model.py`, `ml/config.py`, `ml/dataset.py`, or any other existing pipeline file.
- Does not train anything yet.
- Does not touch Dataset 3, the v1 model, the APK, or any application code.
- Does not propose sourcing new data — that remains Stage 2 in `field_domain_gap_plan.md`, out of scope here.

## What I need from you before implementing and running this

1. Approval of the two specific augmentation changes (drop vertical flip; widen brightness range — and if so, to what exact bounds, since "60–140%" above is a starting suggestion, not a fixed decision).
2. Confirmation that a single new training run (`v2-field-aug-experiment`) under the plan above is authorized to execute, or whether you want to review the implementation files first before any run.
3. Confirmation of the Dataset 3 evaluation condition in §"Candidate selection" — specifically, that no Dataset 3 evaluation happens automatically, only on your explicit request after you've seen the internal test-set comparison.
