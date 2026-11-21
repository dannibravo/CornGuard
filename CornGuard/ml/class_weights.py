"""
Balanced class weights for Gray Leaf Spot underrepresentation, per
claude/09_ML_MODEL_CONTRACT.md "Class Imbalance Handling": "balanced class weights using
compute_class_weight or equivalent." Every real training run must log the exact weights used —
train.py writes them to artifacts/<run_id>/class_weights.json alongside the model.
"""

import numpy as np
import pandas as pd
from sklearn.utils.class_weight import compute_class_weight

import config


def compute_training_class_weights(manifest: pd.DataFrame) -> dict[int, float]:
    train_rows = manifest[manifest["split"] == "train"]
    if train_rows.empty:
        raise ValueError("Manifest has no train-split rows to compute class weights from.")

    class_to_index = {name: i for i, name in enumerate(config.CLASS_ORDER)}
    y = train_rows["class_label"].map(class_to_index).to_numpy()

    present_classes = np.unique(y)
    weights = compute_class_weight(class_weight="balanced", classes=present_classes, y=y)

    # Any class entirely absent from the training split gets weight 1.0 rather than being
    # silently dropped from the dict — a caller indexing by config.CLASS_ORDER should never KeyError.
    result = {i: 1.0 for i in range(config.NUM_CLASSES)}
    for class_index, weight in zip(present_classes, weights):
        result[int(class_index)] = float(weight)
    return result
