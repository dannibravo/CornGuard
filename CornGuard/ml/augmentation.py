"""
Training-only augmentation pipeline, per claude/09_ML_MODEL_CONTRACT.md "Training Augmentation":
random horizontal flip, random vertical flip, random rotation +/-15 degrees, random zoom +/-10%,
brightness scaling ~80%-120%. Validation and test samples must not receive random augmentation —
callers must only apply this to the training split (see train.py's dataset construction).
"""

import tensorflow as tf

import config


def build_augmentation_layer() -> tf.keras.Sequential:
    aug = config.AUGMENTATION
    layers = []

    if aug["horizontal_flip"] and aug["vertical_flip"]:
        layers.append(tf.keras.layers.RandomFlip("horizontal_and_vertical"))
    elif aug["horizontal_flip"]:
        layers.append(tf.keras.layers.RandomFlip("horizontal"))
    elif aug["vertical_flip"]:
        layers.append(tf.keras.layers.RandomFlip("vertical"))

    rotation_fraction = aug["rotation_degrees"] / 360.0
    layers.append(tf.keras.layers.RandomRotation(rotation_fraction))

    layers.append(tf.keras.layers.RandomZoom(aug["zoom_percent"]))

    low, high = aug["brightness_range"]
    # RandomBrightness factor is symmetric around 0; approximate the manuscript's asymmetric
    # 80%-120% multiplicative range with a symmetric delta of the same width.
    brightness_delta = max(1.0 - low, high - 1.0)
    layers.append(tf.keras.layers.RandomBrightness(brightness_delta))

    return tf.keras.Sequential(layers, name="training_augmentation")
