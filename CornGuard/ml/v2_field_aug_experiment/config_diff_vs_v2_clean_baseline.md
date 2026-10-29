# Configuration Diff — v2-field-aug-experiment vs. v2-clean-baseline (control)

Everything not listed below is identical by construction (reused via direct import of the same, unmodified `ml/` modules — see `verify_experiment_setup.py` check 4).

| Setting | v2-clean-baseline (control) | v2-field-aug-experiment (treatment) | Source |
|---|---|---|---|
| Manifest | `ml/v2_baseline/proposed_clean_manifest.csv` | **same file**, byte-identical (SHA-256 verified) | both |
| Architecture | MobileNetV2, ImageNet weights, `include_top=False` | unchanged | `model.py` (imported, not modified) |
| Preprocessing | `(x/127.5)-1`, inside the model | unchanged | `model.py` (imported, not modified) |
| Loss | `sparse_categorical_crossentropy` | unchanged | `model.py` (imported, not modified) |
| Optimizer / learning rates | Adam, 1e-3 head / 1e-5 fine-tune | unchanged | `model.py` (imported, not modified) |
| Epochs | up to 10 head + up to 10 fine-tune | unchanged | `config.py` (imported, not modified) |
| Fine-tune-at-layer | 100 | unchanged | `config.py` (imported, not modified) |
| Early stopping | `monitor="val_accuracy"`, patience=3, restore_best_weights=True | unchanged | reimplemented identically in `train_v2_field_aug.py` (same callback construction as `train.py`) |
| Class weights | sklearn `balanced`, computed from train split | unchanged — same function, same manifest, so same weights | `class_weights.py` (imported, not modified) |
| **Augmentation: horizontal flip** | True | unchanged | `augmentation_v2_field.py` |
| **Augmentation: vertical flip** | **True** | **False — removed** | `augmentation_v2_field.py`, per your approval |
| **Augmentation: rotation** | ±15° | unchanged, read from `config.AUGMENTATION` | `augmentation_v2_field.py` |
| **Augmentation: zoom** | ±10% | unchanged, read from `config.AUGMENTATION` | `augmentation_v2_field.py` |
| **Augmentation: brightness** | **0.80–1.20×** | **0.70–1.30× — widened**, per your approval | `augmentation_v2_field.py` |
| Batch size | 32 | unchanged | `config.py` (imported, not modified) |
| Data shuffle seed | `config.RANDOM_SEED = 42` | unchanged | `config.py` (imported, not modified) |
| **Global Keras/TF weight-init seed** | **not explicitly set** (fact about the existing `train.py`/`model.py`, not a defect) | **explicitly set**: `tf.keras.utils.set_random_seed(42)` at startup | `train_v2_field_aug.py` — intentional addition, documented in `stage1_augmentation_experiment_plan.md` |
| Run ID / output directory | `v2-clean-baseline` | `v2-field-aug-experiment` | new, non-colliding |

## Only two settings actually change the model's training signal

1. **Vertical flip removed.**
2. **Brightness range widened from 0.80–1.20× to 0.70–1.30×.**

Everything else that could plausibly affect the result — architecture, optimizer, learning rates, epoch budget, fine-tuning depth, class weights, data split, shuffle seed, batch size — is reused via direct Python import of the same unmodified files the control run used, not re-typed or approximated. The one necessary structural duplication (reimplementing the small amount of dataset-construction glue in `dataset_v2_field_aug.py`, and the small amount of training-loop glue in `train_v2_field_aug.py`) exists only because `ml/dataset.py`'s `build_split_dataset()` hard-imports `ml/augmentation.py` with no substitution point — not because any other logic needed to differ. The weight-init seed addition is a new, intentional reproducibility aid for this experiment specifically; it does not retroactively change what the control run did.
