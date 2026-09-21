"""
Central configuration for the CornGuard disease-classification training pipeline.

Owner: Acenas (claude/07_TEAM_RESPONSIBILITIES_AND_FEATURE_BRANCHES.md).
Contract: claude/09_ML_MODEL_CONTRACT.md.

IMPORTANT — pragmatic solo-context continuation:
This pipeline has never been executed. Two values below sit on unresolved decision gates
recorded in claude/03_SOURCE_ALIGNMENT_AND_DECISION_GATES.md (D-04 normalization, and the
train/val/test split ratio). They are set here to a reasonable default so this code is real and
runnable, NOT because Acenas has frozen them. Whoever runs the first real training run intended
for evaluation must revisit both and record the decision in the decision-gates doc, per the same
pattern already used for D-01/D-10 on the Firebase/Android side of this project. Do not treat
anything in this file as a substitute for that.
"""

from pathlib import Path

# ---------------------------------------------------------------------------
# Paths
# ---------------------------------------------------------------------------

# Relative to the ml/ directory — every script in this pipeline is run with ml/ as the working
# directory (e.g. `cd ml && python train.py`), see README.md.
# Expected layout: RAW_DATA_DIR/<class_name>/<public|local>/<image files>, where class_name is
# one of CLASS_ORDER below. See prepare_dataset.py for how this becomes the dataset manifest.
RAW_DATA_DIR = Path("data/raw")
MANIFEST_PATH = Path("data/dataset_manifest.csv")
ARTIFACTS_DIR = Path("artifacts")

# ---------------------------------------------------------------------------
# Class order — UNCONFIRMED, pending Acenas
# ---------------------------------------------------------------------------

# claude/09_ML_MODEL_CONTRACT.md: "Acenas must publish the exact class-index order ... Do not
# infer class order alphabetically unless that is exactly how the exported model was trained."
# This order matches android/app/.../model/DiseaseCode.kt for consistency with the rest of the
# project, but it is NOT a substitute for Acenas publishing the real trained order. Before any
# TFLite export is treated as integration-ready, verify this against the actual trained model's
# output index order (see evaluate.py's classification report) and update both this list and the
# Kotlin DiseaseCode.ALL order together if they ever diverge.
CLASS_ORDER = [
    "common_rust",
    "gray_leaf_spot",
    "northern_leaf_blight",
    "healthy",
]

DISPLAY_LABELS = {
    "common_rust": "Common Rust",
    "gray_leaf_spot": "Gray Leaf Spot",
    "northern_leaf_blight": "Northern Leaf Blight",
    "healthy": "Healthy",
}

NUM_CLASSES = len(CLASS_ORDER)

# ---------------------------------------------------------------------------
# Input contract (claude/09_ML_MODEL_CONTRACT.md "Input Contract")
# ---------------------------------------------------------------------------

IMAGE_SIZE = (224, 224)
IMAGE_CHANNELS = 3
INPUT_SHAPE = (*IMAGE_SIZE, IMAGE_CHANNELS)
COLOR_ORDER = "RGB"

# D-04 — UNRESOLVED SOURCE CONFLICT (manuscript states both [-1, 1] MobileNetV2 preprocessing
# and [0, 1] normalization in different sections). Defaulting to the standard
# tf.keras.applications.mobilenet_v2.preprocess_input transform ([-1, 1]) because it is one of
# the two named options and is the canonical preprocessing for this exact architecture's
# ImageNet-pretrained weights. NOT Acenas's frozen decision — see the module docstring.
NORMALIZATION_MODE = "mobilenet_v2_pm1_1"  # maps to preprocess_input in model.py
EXPECTED_PIXEL_RANGE = (-1.0, 1.0)

INPUT_DTYPE = "float32"

# ---------------------------------------------------------------------------
# Dataset split — UNRESOLVED SOURCE CONFLICT (70/15/15 vs. "test set minimum 20%")
# ---------------------------------------------------------------------------

TRAIN_SPLIT = 0.70
VAL_SPLIT = 0.15
TEST_SPLIT = 0.15
assert abs(TRAIN_SPLIT + VAL_SPLIT + TEST_SPLIT - 1.0) < 1e-9

RANDOM_SEED = 42

# ---------------------------------------------------------------------------
# Training augmentation (claude/09_ML_MODEL_CONTRACT.md "Training Augmentation")
# Applied to the training split only — never validation/test (see augmentation.py).
# ---------------------------------------------------------------------------

AUGMENTATION = {
    "horizontal_flip": True,
    "vertical_flip": True,
    "rotation_degrees": 15,
    "zoom_percent": 0.10,
    "brightness_range": (0.80, 1.20),
}

# ---------------------------------------------------------------------------
# Training hyperparameters
# ---------------------------------------------------------------------------

BATCH_SIZE = 32
BASE_LEARNING_RATE = 1e-3
FINE_TUNE_LEARNING_RATE = 1e-5
HEAD_EPOCHS = 10
FINE_TUNE_EPOCHS = 10
FINE_TUNE_AT_LAYER = 100  # unfreeze MobileNetV2 layers from this index onward for fine-tuning

# ---------------------------------------------------------------------------
# Evaluation thresholds (claude/09_ML_MODEL_CONTRACT.md "Required Evaluation")
# ---------------------------------------------------------------------------

MIN_ACCURACY = 0.90
MIN_F1_MACRO = 0.88

# ---------------------------------------------------------------------------
# Model version — bump per real training run. "unassigned" (matching
# PlaceholderCornLeafClassifier.modelVersion on the Android side) until a real model exists.
# ---------------------------------------------------------------------------

MODEL_VERSION = "unassigned"
ARCHITECTURE = "mobilenetv2"
