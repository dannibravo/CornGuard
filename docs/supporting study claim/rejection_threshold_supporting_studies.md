# Supporting study: rejecting non-corn images with a score threshold

Compiled 2026-11-21 after the paper was read through an open-access mirror. Nothing in this file
is a CornGuard result.

## Claim being supported

1. A closed-set softmax classifier (CornGuard's 4-way head) must assign every input to one of its
   classes, so it can report high confidence on images that are not corn leaves (observed in
   informal testing: flowers scored 80%+).
2. Published plant-disease work addresses this by computing a post-hoc score for each input and
   rejecting it as "unknown" when the score passes a threshold.
3. The threshold and the best scoring method have to be chosen from data for the specific model,
   so CornGuard must measure them on its own out-of-distribution (OOD) set.

## Sci. Reports 2024: OOD detection in plant disease

**Citation:** "The impact of fine-tuning paradigms on unknown plant diseases recognition",
Scientific Reports 14:17900 (2024).
**Links:** [nature.com](https://www.nature.com/articles/s41598-024-66958-2) (login redirect for
automated clients) · [Europe PMC full text, PMC11297179](https://europepmc.org/article/PMC/PMC11297179) ·
[authors' code, PDOOD](https://github.com/JiuqingDong/PDOOD) (named in the abstract, not opened).

**Supports:** claim 2, and the choice of scores to compare in claim 3.

**What it does.** Benchmarks open-set recognition and OOD detection for plant disease on a
ViT-B/16 pre-trained on ImageNet-21k, comparing full fine-tuning, linear-probe tuning and visual
prompt tuning.

**How it rejects unknowns (verbatim from the paper's method).** Five post-hoc scores are
compared: energy, entropy, variance, maximum softmax probability (MSP) and maximum logit (ML).
"A sample with a score greater than the threshold γ will be classified as unknown classes."
Scores are signed so that a higher score means more uncertain.

**Results used here (Table 5, PlantVillage, averaged over six OOD sets; FPR@95 lower is better,
AUROC higher is better):**

| Method | FPR@95 | AUROC |
|---|---|---|
| Visual prompt tuning, max logit | 7.40 | 98.25 |
| Visual prompt tuning, MSP | 10.35 | 97.60 |
| Visual prompt tuning, energy | 10.69 | 97.73 |
| Full fine-tuning, MSP | 8.25 | 96.94 |

The paper also says that no single method is consistently best across benchmarks.

**Why it fits CornGuard.** It supports building the rejection as a score plus a threshold, and it
supports comparing MSP, max-logit and energy instead of trusting MSP alone. Max-logit and energy
need pre-softmax logits, which CornGuard's current export does not return.

**Why it does not settle the threshold.**
- Its unknowns are diseased leaves of other plant species and other disease classes. The text I
  read has no non-plant images, so it does not test flowers, soil, hands or screens. Maize
  appears only as healthy corn (known) versus diseased corn (unknown).
- The images are PlantVillage-style, close to CornGuard's training data, not field photos.
- It reports no numeric threshold I could verify. A literal "0.5" and a remark that thresholds
  are hard to transfer between datasets appeared only in a model-written summary of a section I
  did not read verbatim. Do not cite either until the Discussion is read.

**Reading coverage.** About the first 80k and last 13k characters of the 153k-character full text
were read verbatim. The Discussion and Table 6 (few-shot and CLIP results) were not.

## Gap

No study found tests a maize classifier against flowers or other non-maize, non-plant images and
chooses its rejection threshold from that data. Hence the proposal measures the threshold on a
CornGuard-specific OOD set.

## Source files in this repository that back the claim

| File | What it shows |
|---|---|
| [ml/model.py](../../ml/model.py) (line 60) | The output layer is a 4-way softmax with no "not a corn leaf" class. |
| [TfliteCornLeafClassifier.kt](../../android/app/src/main/java/com/cornguard/app/model/TfliteCornLeafClassifier.kt) (lines 62-72) | The app takes the highest softmax probability and reports it as `confidence`. There is no rejection step. |
| [ScanFragment.kt](../../android/app/src/main/java/com/cornguard/app/ui/scan/ScanFragment.kt) (lines 140-158) | Any successful classification is saved and shown. This is where a rejection check would go. |
| [ml/v1_training_audit/audit_report.md](../../ml/v1_training_audit/audit_report.md) | All 4,188 training images are public-source, and none are field photos. |
| [ml/field_gap_evaluation_comparison_20261030/field_gap_investigation.md](../../ml/field_gap_evaluation_comparison_20261030/field_gap_investigation.md) | On field images the model's confidence carries almost no information about correctness (ECE 0.52, flat accuracy across confidence bins), and it over-predicts common_rust. |

## Limits of this evidence

- The paper was read through the Europe PMC copy, and only partly (see above). No copy of the
  paper is stored in this repository.
- The flower and laptop-screen observations come from informal app testing and are not recorded in
  any saved evaluation file.
