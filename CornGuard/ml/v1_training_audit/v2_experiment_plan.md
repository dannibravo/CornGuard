# CORNGUARD v2 — Proposed Controlled Experiment Plan (NOT EXECUTED)

This is a proposal only. Nothing in this document has been run. No model, dataset, manifest, or code was changed to produce it. It is written to be reviewed and approved before any v2 work begins, consistent with the v1 training audit's findings (`audit_report.md`).

## Why a v2 experiment, not a direct retrain

The audit found the v1 gap between held-out test accuracy (89.52%) and external field accuracy (25.30%) has at least two confirmed contributing factors (never-seen field domain, a small confirmed train/test leak) and several unconfirmed hypotheses (domain shift severity, label-taxonomy mismatch, geography/cultivar differences, the common_rust bias). A single retrain attempt would conflate all of these — if accuracy improves or doesn't, it wouldn't be clear *why*. The plan below isolates variables.

## Pre-requisites (must happen before any training run)

1. **Fix the confirmed data-quality defect**: `Corn_Gray_Spot (564).jpg` / `Corn_Blight (1104).jpg` are byte-identical but carry two different class labels. This needs human review (which label, if either, is correct) before being used in any future split — not an automatic fix.
2. **Re-run split generation with a leakage check built in**: extend `prepare_dataset.py` (or add a pre-split screening step) to compute SHA-256 + pHash across all candidate images *before* assigning splits, and either exclude near-duplicate clusters from being split across train/val/test, or force each cluster entirely into one split. This directly reuses the tooling already written for this audit and for the external-evaluation work (`check_split_overlap.py`, and the broader hashing pipeline in `cornguard_external_eval/`).
3. **Decide what field data v2 can use for training without contaminating future evaluation.** Dataset 3 (the Maize In-Field dataset, 1,091 evaluation-eligible images from this project's external evaluation) must **not** be silently reused as v2 training data — that would retroactively invalidate the very evaluation result that motivated v2. Options, for discussion:
   - (a) Hold Dataset 3 back entirely as a permanent, frozen field-domain test set; source genuinely new field images (new dataset, new collection effort) for v2 training/validation.
   - (b) Split Dataset 3 itself once, up front, into a v2-training portion and a permanently-frozen v2-test portion (e.g. by `group_id`, respecting the grouping audit's finding that groups should not be split across uses) — accepting that the "external" test set shrinks and is no longer fully held out from all training-related decisions.
   - This is a real tradeoff with no free option; it should be an explicit decision, not an assumption.

## Experiment matrix

Each run below should log the same artifact set v1 already does (`run_metadata.json`, `class_weights.json`, `training_history.json`, `evaluation_report.json`, confusion matrix), **plus** a mandatory field-domain evaluation report (the `evaluation_report_field.json` path `evaluate.py` already scaffolds but has never produced, since v1 never had field-sourced rows in its manifest).

| Run | Change from v1 | Isolates |
|---|---|---|
| **A — Leak-fixed baseline** | Same data/config as v1, but with the mislabeled duplicate resolved and the 12 cross-split overlaps forced into a single split each | How much of the 89.52% test accuracy was leakage-inflated |
| **B — + field images in train/val** | Add a field-domain training/validation set (per the pre-requisite decision above), same architecture/hyperparameters otherwise | Whether field-domain training data alone closes most of the gap |
| **C — + stronger/field-realistic augmentation** | On top of Run B (or standalone vs. Run A, for comparison), add augmentation closer to field conditions: wider brightness/contrast range, synthetic background variation, partial-leaf/occlusion simulation, if a field augmentation library is introduced — note this would need explicit approval, since it's a new dependency | Whether augmentation alone (without new field photos) narrows the gap |
| **D — deeper fine-tuning** | Unfreeze more of the MobileNetV2 base (lower `FINE_TUNE_AT_LAYER`) with Run A's leak-fixed data | Whether limited fine-tuning depth (only layers 100+ unfrozen in v1) was a contributing factor |
| **E — calibration pass** | Same model as the best of A-D, add temperature scaling or label smoothing, re-measure ECE specifically on the field-domain test set | Whether the severe miscalibration (v1's ECE=0.52 on field data) can be corrected independently of raw accuracy |

Each run is independently optional — they don't have to all happen, and could be prioritized based on budget/time. Run A is the cheapest and most informative first step (it directly tests the audit's own leakage finding) and is the recommended starting point if only one run is approved.

## Required reporting for every run

- Accuracy, macro/weighted precision/recall/F1, confusion matrix — on the **public-style held-out test set** (as v1 already does)
- The same full metric set on a **field-domain held-out test set** — mandatory, not optional, so no future run can report only its best-looking number
- ECE / reliability table on both, so calibration is tracked alongside accuracy
- Explicit statement of exactly which images were used for train/val/test and confirmation (via the leakage-check tooling) that no duplicate/near-duplicate crosses those splits
- A short note distinguishing "this run's actual measured result" from "comparison to v1" — avoid the kind of unverified-figure confusion the audit found with the "96.65%" number

## What this plan deliberately does not do

- It does not propose touching the shipped `model.tflite` or the Android app in any way — all of this is `ml/` training-pipeline work, evaluated independently before any export/integration decision.
- It does not assume field-domain training data will fully close the 64-point gap — that is exactly what Run B is designed to measure, not assume.
- It does not recommend retraining immediately on Dataset 3 as-is without the frozen-test-set decision above being made explicitly.

## Next step

This plan requires your review and explicit approval — including the Dataset 3 usage tradeoff in the pre-requisites section — before any run is executed.
