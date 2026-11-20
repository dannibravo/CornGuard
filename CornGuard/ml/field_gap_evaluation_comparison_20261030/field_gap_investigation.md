# CORNGUARD — Field-Domain Gap Investigation (Analysis Only, Existing Artifacts)

Synthesis report only. No model was trained or modified. Dataset 3 was not accessed or evaluated — every number below is read from already-computed, existing output files. No image was opened or inspected for this report.

## Source artifacts located and read (paths confirmed before use)

| Artifact | Path |
|---|---|
| Original shipped model, Dataset 3 eval | `cornguard_external_eval\dataset3_maize_in_field_results\evaluation_run_20261030_002559\metrics.json`, `evaluation_report.md`, `confusion_matrix.csv`, `misclassified_images.csv` |
| Original model's error analysis | `...\evaluation_run_20261030_002559\error_analysis\error_analysis_report.md`, `verified_metrics.json` |
| Original model's visual review checklist | `...\evaluation_run_20261030_002559\visual_error_review\visual_review_checklist.csv` |
| v2-field-aug-experiment, Dataset 3 eval | `cornguard_external_eval\dataset3_maize_in_field_results\v2_field_aug_experiment_dataset3_eval_20261030\metrics.json`, `evaluation_report.md`, `confusion_matrix.csv`, `misclassified_images.csv` |
| v2-clean-baseline, internal test | `ml\v2_baseline\v2_clean_baseline_results.md`, `ml\artifacts\v2-clean-baseline\evaluation_report.json` |
| v2-field-aug-experiment, internal test | `ml\v2_field_aug_experiment\v2_field_aug_results.md`, `ml\artifacts\v2-field-aug-experiment\evaluation_report.json` |
| v1 training audit | `ml\v1_training_audit\audit_report.md` |
| Field-domain gap plan | `ml\v2_field_domain_planning\field_domain_gap_plan.md` |

**All paths above exist and were read in full.** One artifact gap found and reported rather than invented: `visual_error_review\visual_review_checklist.csv` has **0 of 60 rows filled in** (checked directly) — the human visual review this checklist was built for has not happened. Any claim about image-level causes (lighting, background, image quality) would have no supporting evidence in the existing artifacts; none is made in this report (see Task 3).

---

## 1–2. Side-by-side comparison, by class

| Metric | Original shipped model | v2-field-aug-experiment | Δ |
|---|---|---|---|
| Overall accuracy | 25.30% | 29.24% | **+3.94 pts** |
| Macro F1 | 0.2797 | 0.2906 | +0.0109 |
| common_rust predictions (of 1,091) | 746 (68.4%) | 631 (57.8%) | **−115, bias eased but still dominant** |

### Per-class precision / recall / F1

| Class | Original P/R/F1 | v2-field-aug P/R/F1 | Change |
|---|---|---|---|
| common_rust | 0.101 / **0.701** / 0.176 | 0.100 / **0.589** / 0.171 | Recall **−0.112** |
| gray_leaf_spot | 0.886 / **0.150** / 0.257 | 0.845 / **0.238** / **0.371** | Recall **+0.087**, F1 **+0.114** (largest single improvement) |
| northern_leaf_blight | 0.465 / 0.614 / **0.529** | 0.382 / 0.636 / **0.477** | F1 **−0.052** (the one class that got worse) |
| healthy | 0.400 / 0.097 / 0.157 | 0.377 / 0.088 / 0.143 | Essentially unchanged, still the worst-performing class by recall |

### Largest error patterns (both models, ranked by count)

| Rank | Error type | Original | v2-field-aug |
|---|---|---|---|
| 1 | gray_leaf_spot → common_rust | **433** | **361** (−72) |
| 2 | healthy → common_rust | 190 | 172 (−18) |
| 3 | gray_leaf_spot → northern_leaf_blight | 64 | 87 (+23) |
| 4 | northern_leaf_blight → common_rust | 48 | 35 (−13) |
| 5 | healthy → northern_leaf_blight | 11 | **28 (+17, more than doubled)** |

(v2-field-aug's full error-pair breakdown computed directly from its `confusion_matrix.csv`, consistent with its `misclassified_images.csv` total of 772 rows; the original model's ranking is quoted directly from `error_analysis_report.md` §2.)

### What the comparison shows (confirmed from the data, not inferred)

The dominant failure mode — gray_leaf_spot and healthy images being swallowed into `common_rust` — **persists in both models**, but its magnitude eased: common_rust absorbed 115 fewer total predictions in v2-field-aug. Tracing where that freed-up probability mass went (via the confusion matrices): most of it went to **correctly identifying more true gray_leaf_spot images** (93→147 correct, +54, the single largest driver of the accuracy improvement, since gray_leaf_spot is over half the dataset by support). A smaller portion shifted into **over-predicting northern_leaf_blight as a secondary "dumping ground"** — most visibly, healthy→northern_leaf_blight errors nearly tripled (11→28), and gray_leaf_spot→northern_leaf_blight rose by 23. This is also why northern_leaf_blight's own F1 *dropped* (0.529→0.477) despite its recall staying flat: its false-positive count rose from 99 to 144, driven by the other three classes leaking into it more.

**Net reading**: v2-field-aug-experiment did not fix the underlying class-confusion problem — it partially *redistributed* it, trading some common_rust over-prediction for new northern_leaf_blight over-prediction, with a net positive but modest effect on overall accuracy. Both models still fail badly on `healthy` (recall ~9%) and both still confuse gray_leaf_spot with the other disease classes far more than they correctly identify it.

---

## 3. Image quality, lighting, background, or labeling differences — insufficient existing evidence to attribute causes

Per instruction, **no visual cause is inferred here without supporting evidence**, and no image was inspected to produce this report. What the existing artifacts do and do not support:

**Supported by existing documentation:**
- `ml\v1_training_audit\audit_report.md` §5 confirms, as a verified fact: **100% of the 4,188 training/val/test images are `source_type="public"`; zero are field-sourced.** This is a structural, confirmed fact about the training data's composition — the model has never seen a genuine field photograph in training.
- The preflight audit (`cornguard_external_eval\dataset3_maize_in_field_results\preflight_audit\preflight_report.md`, written earlier in this project) already documented a *qualitative* visual contrast between Dataset 3 (hand-held, outdoor, diverse backgrounds, partial/overlapping leaves) and the training set's more controlled framing — this is pre-existing documentation, not a new inference made here.
- The error analysis's confirmed finding (`error_analysis_report.md` §3) that the original model's confidence carries almost no information about correctness (ECE = 0.52, flat ~20–31% accuracy across all confidence bins) is consistent with — but does not by itself prove — a domain-shift explanation rather than a labeling-difference explanation.

**Not supported — a real gap, not invented here:**
- **The 60-image visual review built specifically to answer whether errors trace to image quality, ambiguity, or label concerns was never completed** (`visual_review_checklist.csv`, 0/60 filled). No report in this project currently contains item-level evidence distinguishing "the model is confused by genuinely similar-looking lesions" from "the photo is blurry/poorly lit" from "the label itself is questionable" for any of the 772–815 misclassified images.
- No lighting/background/image-quality metric (e.g. brightness histograms, blur detection) has been computed over Dataset 3 images in any existing artifact.
- The only labeling-difference evidence that exists is structural (Dataset 3's 5-class taxonomy mapped to CornGuard's 4 classes, and the 11 excluded NoFoliarSymptoms+disease conflicts, both already documented during dataset preparation) — this explains some *excluded* images' ambiguity but was already controlled for before either Dataset 3 evaluation ran, so it does not explain the *remaining* errors analyzed above.

**Conclusion for Task 3**: existing evidence supports attributing the gap to **domain shift in the broad, structural sense** (no field images ever in training) and to a **systematic class-confusion bias** (quantified above). It does **not** yet support attributing the gap to any specific visual factor (lighting, background, blur) — that would require either completing the existing 60-image visual review or a new, explicitly-scoped image-quality analysis, neither of which this report performs.

---

## 4. Most useful next experiment (using a separate development dataset, not Dataset 3)

Per `ml\v2_field_domain_planning\field_domain_gap_plan.md` §C–E (already-approved staged plan) and the pattern observed above: the single most informative next step is to **source and verify a genuinely new field-condition development dataset** (distinct from Dataset 3, per the dataset-verification protocol already written in `field_domain_gap_plan.md` §D), and use it — never Dataset 3 — for:

1. **Iterative development-set evaluation** of candidate changes (augmentation variants, partial field-data fine-tuning, calibration adjustments), with per-class recall as the primary diagnostic, since the current failure is concentrated in specific classes (`gray_leaf_spot`, `healthy`) rather than uniform across all four.
2. **Specifically targeting the redistribution problem found above**: any candidate change should be checked for whether it genuinely improves class separability or merely shifts which class absorbs the confusion (as v2-field-aug-experiment did, partially, from common_rust to northern_leaf_blight). A useful diagnostic: track the full confusion matrix on the dev set after each change, not just accuracy/F1, to catch this redistribution pattern early.
3. **Completing the existing, already-prepared 60-image visual review** (`visual_review_checklist.csv`) is a zero-cost, already-built next step that doesn't require new data — it would convert Task 3's current "insufficient evidence" finding into actual evidence, cheaply, before investing in a larger data-collection effort.

This experiment should **not** touch Dataset 3 at all until a candidate is judged ready by dev-set metrics, consistent with the rationed-evaluation-log process already recommended in `field_domain_gap_plan.md` §E.

---

## 5. Recommended measurable acceptance criteria (proposed, for team sign-off — not a unilateral decision)

Current best field-domain result (v2-field-aug-experiment on Dataset 3): accuracy 29.24%, macro F1 0.2906, with two classes (`common_rust`, `healthy`) under 0.60 recall and `gray_leaf_spot` at 0.24 recall. Proposed staged criteria, evaluated on a **held-out field-domain development set** (not Dataset 3) for Milestones 1–2, with Dataset 3 reserved for a final, rationed check:

| Milestone | Overall accuracy | Macro F1 | Per-class recall (every class) | Purpose |
|---|---|---|---|---|
| **1 — meaningful signal** | ≥ 50% | ≥ 0.40 | ≥ 0.40 | First checkpoint worth continuing investment in |
| **2 — deployment-consideration bar** | ≥ 70% | ≥ 0.65 | ≥ 0.60 | Minimum bar before any deployment conversation starts |
| **3 — final confirmation** | Same as Milestone 2 | Same as Milestone 2 | Same as Milestone 2 | Confirmed once, on the frozen Dataset 3 set, per the rationed-evaluation process — not used to pick between candidates |

These numbers are starting proposals, not derived from a formal statistical power analysis — intended as a concrete basis for team discussion, not a final specification. The per-class recall floor is included deliberately because both evaluated models pass reasonable-looking accuracy/F1 while still failing specific classes badly (`healthy` recall ~9% in both) — an aggregate-only bar would not catch that.

---

## 6. Additional data or evidence still needed

1. **A genuinely new, independently-verified field-condition dataset**, distinct from Dataset 3, vetted per `field_domain_gap_plan.md` §D's protocol (source/license, label mapping, duplicate screening against both the training manifest and Dataset 3) — nothing in the current artifacts substitutes for this.
2. **Completion of the existing 60-image visual review** (`visual_review_checklist.csv`) — already built, zero new data collection required, currently 0/60 filled in.
3. **A full calibration (ECE / reliability-table) analysis for v2-field-aug-experiment's Dataset 3 result** — the original model has one (`error_analysis_report.md`, ECE=0.52); the authorized single evaluation of v2-field-aug-experiment only captured simple correct/incorrect confidence means (0.739 vs. 0.738), not the full 10-bin reliability table, so calibration quality for this checkpoint on field data is not yet characterized to the same depth.
4. **A multi-seed or multi-run ablation** — both internal-test and Dataset 3 comparisons so far are single runs per condition; per `ml\v2_field_aug_experiment\v2_field_aug_results.md`'s own stated limitation, this means the observed differences can't be cleanly separated from ordinary training-run variance.
5. **Ground-truth plant/session metadata for the training images** — still absent (confirmed in the v1 audit), which limits confidence in how leakage-resistant any future split really is.
6. **Independent, first-party confirmation of Dataset 3's own source/license** — still only indirectly verified via search-engine corroboration (documented in the preflight audit), not a direct fetch of the primary repository page.

---

## Summary

The v2-field-aug-experiment checkpoint is a small, measured improvement over the original shipped model on Dataset 3 (+3.9 accuracy points, +0.011 macro F1), driven mainly by better gray_leaf_spot recall, but it **partially redistributed rather than resolved** the model's core problem: a systematic bias toward predicting common_rust (now somewhat toward northern_leaf_blight as well) regardless of true class. `healthy` remains essentially undetected in both models (~9% recall). No existing artifact yet supports attributing this to a specific visual cause (lighting/background/quality) rather than the broader, well-confirmed structural fact that the model has never trained on a field-condition image — closing that gap requires genuinely new field-domain training/dev data, not further tuning on the data already available.

This report was produced entirely from existing files; no Dataset 3 image was accessed, and no model was trained, modified, or evaluated to produce it.
