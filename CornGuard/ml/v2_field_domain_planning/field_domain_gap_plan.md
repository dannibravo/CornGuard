# CORNGUARD — Field-Domain Gap: Evidence Summary and Staged Experiment Plan

Planning document only. No model was trained, fine-tuned, or evaluated to produce this. No existing file was modified. Sources are cited by exact path throughout; facts and hypotheses are labeled explicitly, not blended.

## Sources read for this report

- `ml\v1_training_audit\audit_report.md`, `ml\v1_training_audit\v2_experiment_plan.md`
- `ml\v2_baseline\pretraining_verification_report.md`, `ml\v2_baseline\v2_clean_baseline_results.md`, `ml\artifacts\v2-clean-baseline\evaluation_report.json`
- `cornguard_external_eval\dataset3_maize_in_field_results\evaluation_run_20261030_002559\evaluation_report.md`, `metrics.json`, `confusion_matrix.csv`
- `cornguard_external_eval\dataset3_maize_in_field_results\evaluation_run_20261030_002559\error_analysis\error_analysis_report.md`, `verified_metrics.json`
- `cornguard_external_eval\dataset3_maize_in_field_results\evaluation_run_20261030_002559\visual_error_review\visual_review_checklist.csv` (checked directly — see Task A note on its actual state)

---

## A. Strongest evidence for the field-domain gap — facts vs. hypotheses

### Confirmed facts

1. **The gap is large and real.** v2-clean-baseline: 91.99% accuracy / 90.07% macro F1 on its own held-out (public-style) test split (624 images, `ml\artifacts\v2-clean-baseline\evaluation_report.json`). Dataset 3 external field evaluation: 25.30% accuracy (276/1,091) (`...evaluation_run_20261030_002559\evaluation_report.md`). A ~67-point drop.
2. **Zero field images have ever been part of training, validation, or either internal test split**, v1 or v2-clean. 100% of `ml\data\dataset_manifest.csv` / `ml\v2_baseline\proposed_clean_manifest.csv` rows have `source_type="public"` (`audit_report.md` §5). The model has never seen a single genuinely field-conditioned photograph during training.
3. **Systematic, one-directional bias toward `common_rust`.** The model predicted common_rust for 746 of 1,091 Dataset 3 images (68.4%), versus common_rust being only 9.8% of the true labels present. 76% of all 815 errors are `gray_leaf_spot→common_rust` (433, 53% alone) or `healthy→common_rust` (190) (`error_analysis_report.md` §2-3).
4. **Confidence carries no useful information about correctness on Dataset 3.** Mean confidence on *incorrect* predictions (0.777) was higher than on *correct* ones (0.760). Expected Calibration Error = 0.52; the single largest confidence bin (0.9–1.0, 340 images) was only 20.9% accurate — no better than lower bins (`error_analysis_report.md` §3, `verified_metrics.json`).
5. **The "96.65%" figure some earlier project notes referenced does not exist in any saved artifact.** The only documented, reproducible internal result is v1's 89.52%/87.41% and now v2-clean's 91.99%/90.07% (`audit_report.md` §7, `error_analysis_report.md` §5).
6. **v1's own held-out test accuracy already failed the project's acceptance gates** before any field evaluation (89.52% < 90%, 87.41% < 88%) — v2-clean passes both, but on the same public-style data distribution, not on field data.

### Important caveat on the visual review claim

Your message states "visual review of 60 misclassified images found no obvious image or label problems." **I checked `visual_review_checklist.csv` directly** (`...evaluation_run_20261030_002559\visual_error_review\visual_review_checklist.csv`): as of this reading, **every reviewer-input column (`reviewer_name`, `review_date`, `visual_ambiguity`, `image_quality`, `potential_label_concern`, `reviewer_notes`) is blank for the rows checked.** I am not able to independently verify this conclusion from the file artifacts I have access to — I'm recording it as context you've provided, not as something I confirmed myself. If the review happened through a channel outside this checklist file, that's outside what I can see; if the checklist was meant to be the record, it does not yet show a completed review.

### Hypotheses (plausible, not established by any report so far)

1. **Domain shift** (lighting, background, leaf framing, partial/overlapping leaves, weathering, camera type) between the public-style training images and genuine field photography is the leading candidate, but no experiment has yet isolated it from the other hypotheses below.
2. **Label-taxonomy mismatch**: Dataset 3's native labels are a 5-class South African taxonomy (GLS/NCLB/PLS/CR/SR + NoFoliarSymptoms) mapped down to CornGuard's 4 classes; some residual mapping noise is possible despite the external-eval work already excluding unmapped/multi-label/conflicting images specifically to control for this.
3. **Geography/cultivar differences** between the training images' likely origin and Dataset 3's South African provenance.
4. **The common_rust bias specifically** is undiagnosed — it could stem from class-weight/loss interaction, shared color/texture cues, or something else. No report so far attributes a cause to it.

---

## B. Which classes and error types drive the external failures

From `error_analysis_report.md` §2 and `verified_metrics.json` (all recomputed and verified against raw records, zero discrepancies found):

| Rank | Error type | Count | Share of all 815 errors |
|---|---|---|---|
| 1 | gray_leaf_spot → common_rust | 433 | 53.1% |
| 2 | healthy → common_rust | 190 | 23.3% |
| 3 | gray_leaf_spot → northern_leaf_blight | 64 | 7.9% |
| 4 | northern_leaf_blight → common_rust | 48 | 5.9% |
| 5 | gray_leaf_spot → healthy | 28 | 3.4% |

**gray_leaf_spot is overwhelmingly the worst-performing true class**: 618 true examples, only 93 correctly predicted (recall 15.0%), 525 of its 525 false negatives nearly all land on common_rust or northern_leaf_blight. **healthy is the second worst**: 226 true examples, only 22 correct (recall 9.7%), 204 false negatives, 190 of which go to common_rust. **common_rust itself is not well-diagnosed either** — it has high recall (70.1%) but precision of only 10.1%, because it is absorbing nearly everything else. **northern_leaf_blight is comparatively the model's best class on Dataset 3** (recall 61.4%, F1 0.529) — still far below its public-test performance (v2-clean: recall 83.5%, F1 0.863), but the smallest relative collapse of the four.

**Conclusion for prioritization**: any field-domain remediation should prioritize gray_leaf_spot and healthy first — they contribute 76%+ of all errors between them (gray_leaf_spot alone contributes 525+64+28 = 617 of 815 errors it's involved in as the true label, i.e. ~68% of all misclassifications originate from true gray_leaf_spot images alone).

---

## C. Proposed staged experiment plan (NOT executed)

Ordered by your stated priorities. Each stage is gated on the previous stage's findings — nothing here commits to training yet.

### Stage 0 — Preprocessing and label-consistency audit (cheapest, do first)

- Confirm Dataset 3's actual on-device preprocessing path one more time end-to-end against the live Android code (already done once in the preflight audit, `cornguard_external_eval\dataset3_maize_in_field_results\preflight_audit\preflight_report.md` §4 — resize 224×224, raw [0,255], `(x/127.5)-1` baked into the graph, numerically confirmed). Re-confirming costs nothing and rules out a preprocessing-mismatch hypothesis definitively before investing in anything bigger.
- Spot-check a larger sample (beyond the 60 already selected) of Dataset 3 gray_leaf_spot and healthy images specifically (the two worst classes), visually, against the dataset's own native multi-label CSV (`cornguard_external_eval\dataset3_maize_in_field_results\Database.csv`) to look for systematic labeling issues specific to those classes — not just the general 60-image sample already drawn.
- **Gate to next stage**: only proceed if this audit does NOT find a preprocessing bug or a systematic labeling defect that alone could explain the gap (if it does, fix that first — no training needed).

### Stage 1 — Realistic training-only augmentation (no new data required)

- Extend `ml\augmentation.py`'s existing train-only pipeline (currently flip/rotate/zoom/brightness, confirmed train-split-only in `audit_report.md` §5) with field-realistic transforms: wider brightness/contrast range, synthetic shadow/glare, slight blur, background-crop variation — cheapest possible experiment since it touches zero new data.
- Train on the **existing clean public-style manifest only** (`ml\v2_baseline\proposed_clean_manifest.csv`), evaluate on the same held-out public test split AND once, read-only, against Dataset 3 (per §E below — single checkpoint, not tuned against it).
- **This isolates**: whether augmentation alone, without any field photos, narrows the gap at all. Low cost, informative regardless of outcome.

### Stage 2 — Source/verify a separate, expert-verified field-image training dataset

Only pursued if Stage 0/1 don't resolve the gap. See §D for the verification protocol any candidate dataset must pass before use. This is the most resource-intensive stage — finding a genuinely independent, well-labeled, field-condition dataset (not another PlantVillage-derived collection, per this project's repeated prior finding that most "different-named" public corn-disease datasets trace back to the same source lineage) may itself take significant search effort.

### Stage 3 — Leakage-resistant, group-aware splitting for any new data

- Any new field dataset brought in must be split using plant/session/capture-date grouping where that metadata exists, reusing the union-find + filename/timestamp heuristic approach already built and documented in `cornguard_external_eval\dataset3_maize_in_field_results\dataset3_grouping.py` and its known limitations (`preflight_audit\preflight_report.md` §5 — same-day photo-burst grouping is heuristic, not ground truth).
- Before splitting, run the same SHA-256 + perceptual-hash duplicate screen used in `ml\v2_baseline\dataset_leakage_check.py`, extended across the new data AND the existing public-style manifest, to prevent a repeat of the 12-pair leak found in the v1 audit.

### Stage 4 — Mandatory reporting for every stage

Every run, regardless of stage, must report: per-class precision/recall/F1, macro F1, full confusion matrix (verified class order), AND — per §E — at most one single read-only Dataset 3 evaluation per candidate checkpoint, never used to pick which checkpoint to keep.

---

## D. Verification protocol for any proposed new dataset (not optional, applies before any use)

Before any candidate field-image dataset is used for anything (training, validation, augmentation source, or reference), it must be verified exactly as every dataset in this project has been so far:

1. **Source and license**: identify the exact publisher/author, dataset name, and license terms from the dataset's own files or its original hosting page — not inferred from folder names or guessed. (Precedent: Dataset 3's own source was only accepted as "University of Pretoria / Hamish Craze / CC BY 4.0" after cross-referencing exact image count + exact label taxonomy; even then it was flagged as indirectly verified since the primary page couldn't be fetched directly — see `preflight_report.md` §Task 5. Any new dataset needs at least this level of scrutiny, ideally a successful direct fetch of the source page.)
2. **Label consistency**: inspect the dataset's own label file/taxonomy directly; map to CornGuard's 4 classes explicitly (never guess from folder names); flag and hold out any multi-label, conflicting, or unmappable images rather than auto-resolving them — exactly the process already built for Dataset 3 (`validate_metadata.py`, `dataset3_manifest.py`).
3. **Exact and near-duplicate screening**: SHA-256 + perceptual-hash scan against (a) the existing training manifest, (b) Dataset 3 (to make sure a "new" field dataset isn't secretly overlapping the frozen eval set), and (c) itself internally — reusing the same tooling (`compute_hashes*.py`, `find_duplicates*.py` patterns) already used three times in this project (CornDataset vs. Dataset 1, vs. Dataset 2, vs. Dataset 3).
4. **Overlap with Dataset 3 specifically is a hard stop, not a soft finding.** Given this project has already found multiple nominally-different public datasets secretly sharing the same root images (Dataset 2 turned out 100% duplicate of the training set; two separate archive versions of it were also duplicates), any new dataset must be explicitly screened against Dataset 3's 2,355 images before being trusted as independent.
5. **Do not assume suitability from the dataset's name, star rating, or download count.** Every one of the above checks must produce a positive, documented result before the dataset is used for anything beyond initial inspection.

---

## E. Keeping Dataset 3 locked, and preventing "tuning to the test set"

**Hard rule going forward**: Dataset 3 is read-only evaluation data. It must never appear in a training manifest, a validation split, an augmentation source, a hyperparameter search, or a checkpoint-selection decision. This has held true for every step taken so far — every new training/manifest file in `ml\v2_baseline\` and `ml\v1_training_audit\` was grepped for any Dataset 3 path reference before use, with zero matches each time (`pretraining_verification_report.md` §6, `v2_clean_baseline_results.md`).

**Process recommendation to prevent repeated tuning against it**:
- Treat each Dataset 3 evaluation as a **rationed, logged event** — not something run after every experiment. Maintain a running log (a new file, e.g. `dataset3_evaluation_log.csv`, appended to — never overwritten) recording every time Dataset 3 is evaluated against, which checkpoint, and why. The more times a team looks at Dataset 3's results while iterating, the more those decisions implicitly overfit to it even without formally training on it — this is a real, documented risk in ML practice ("test-set leakage through repeated querying"), not specific to this project.
- Use the **internal held-out public test split** (`ml\v2_baseline\proposed_clean_manifest.csv`'s test rows) as the metric that drives iteration decisions during Stages 0–3 above. Only evaluate a given candidate checkpoint against Dataset 3 **once it's already the team's best candidate by internal metrics** — not as a per-iteration tuning signal.
- If/when a genuinely new field-domain dataset is sourced and verified (§D), it becomes the *training-time* field-domain signal; Dataset 3 remains reserved as the final, independent check — never both at once.

---

## F. Recommended smallest useful next experiment

**Stage 0 (preprocessing/label audit) + Stage 1 (field-realistic augmentation only, no new data), run together as one combined next step.**

Rationale: both are low-cost (no new dataset to source/verify), touch no new data, and would directly test two of the four hypotheses in §A (domain shift via augmentation; any residual preprocessing/label defect). If Stage 1 alone closes a meaningful fraction of the 67-point gap, that's strong evidence domain shift (addressable via augmentation and later real field data) is the dominant factor over label-taxonomy or geography issues — informing how much effort Stage 2 (sourcing a real field dataset) deserves before committing to it.

### Evidence needed before I (or anyone) should be authorized to execute Stage 0/1

1. **Your explicit approval** to modify `ml\augmentation.py` (or add a new augmentation variant alongside it, not overwriting the original) and to run a new training job under a new run-id (e.g. `v2-augmented-baseline`), following the same non-destructive pattern already used for `v2-clean-baseline`.
2. **A one-time-only Dataset 3 evaluation budget approved for this experiment** — per §E, this experiment's single checkpoint gets evaluated against Dataset 3 once, logged, and not re-run to chase a better number.
3. **Confirmation of which metric is the pass/fail bar** for deciding whether Stage 1 "worked" — e.g. does any improvement above 25.3% count as a positive signal, or is there a target threshold the team wants to see before investing further? This isn't something this planning document should decide unilaterally.

**Nothing in this plan has been executed.** No training, fine-tuning, evaluation, code change, or Dataset 3 access beyond the read-only checklist inspection in Task A occurred while producing this report.
