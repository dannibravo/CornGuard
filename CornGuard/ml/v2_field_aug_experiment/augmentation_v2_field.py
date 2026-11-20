"""v2 field-augmentation experiment — a NEW, separate augmentation module. It does NOT modify or
replace ml/augmentation.py, which stays exactly as it is for every other run. Used only by
train_v2_field_aug.py in this same directory.

Changes vs. ml/augmentation.py, per explicit approval (stage1_augmentation_experiment_plan.md,
approved 2026-10-30):
  1. Vertical flip REMOVED (kept horizontal flip only) — corn leaves have a real base-to-tip axis;
     Stage 0's audit flagged vertical flipping as anatomically unrealistic for this domain.
  2. Brightness range WIDENED from the original 0.80-1.20x to 0.70-1.30x, to better approximate
     real field lighting variation (Stage 0's audit flagged the original range as likely too
     narrow). Explicitly approved bounds: 0.7-1.3x.
  3. Rotation (+/-15 degrees) and zoom (+/-10%) are UNCHANGED, and are read directly from the
     existing, unmodified config.AUGMENTATION dict, so these two values can never silently drift
     from what the control (v2-clean-baseline) uses.

Everything else about this module (RandomRotation/RandomZoom/RandomBrightness usage, the Keras
Sequential wrapper) mirrors ml/augmentation.py's structure exactly, so the only operative
differences are items 1 and 2 above.
"""

import tensorflow as tf

import config  # the SAME, unmodified ml/config.py — only rotation_degrees and zoom_percent are read from it

# Experiment-local augmentation parameters (NOT written into config.py, which stays unmodified).
VERTICAL_FLIP = False          # changed from config.AUGMENTATION["vertical_flip"] (True) -> False
HORIZONTAL_FLIP = True         # unchanged
BRIGHTNESS_RANGE = (0.70, 1.30)  # changed from config.AUGMENTATION["brightness_range"] (0.80, 1.20)


def build_augmentation_layer() -> tf.keras.Sequential:
    layers = []

    if HORIZONTAL_FLIP and VERTICAL_FLIP:
        layers.append(tf.keras.layers.RandomFlip("horizontal_and_vertical"))
    elif HORIZONTAL_FLIP:
        layers.append(tf.keras.layers.RandomFlip("horizontal"))
    elif VERTICAL_FLIP:
        layers.append(tf.keras.layers.RandomFlip("vertical"))

    # rotation_degrees and zoom_percent read from the existing config.py, unchanged from control
    rotation_fraction = config.AUGMENTATION["rotation_degrees"] / 360.0
    layers.append(tf.keras.layers.RandomRotation(rotation_fraction))

    layers.append(tf.keras.layers.RandomZoom(config.AUGMENTATION["zoom_percent"]))

    low, high = BRIGHTNESS_RANGE
    # Same symmetric-delta approximation ml/augmentation.py uses for RandomBrightness, applied to
    # the new 0.7-1.3 bounds instead of the original 0.8-1.2 bounds.
    brightness_delta = max(1.0 - low, high - 1.0)
    layers.append(tf.keras.layers.RandomBrightness(brightness_delta))

    return tf.keras.Sequential(layers, name="training_augmentation_v2_field")
