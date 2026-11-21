"""
MobileNetV2 transfer-learning model, per claude/09_ML_MODEL_CONTRACT.md "Base Architecture".
"""

import keras
import tensorflow as tf

import config


@keras.saving.register_keras_serializable(package="cornguard")
class MobileNetV2Preprocess(tf.keras.layers.Layer):
    """
    Registered, serializable equivalent of tf.keras.applications.mobilenet_v2.preprocess_input:
    maps [0, 255] RGB input to [-1, 1] via (x / 127.5) - 1.

    Implemented directly rather than wrapping the library function in a Lambda layer — Keras 3
    cannot reliably reconstruct a Lambda around an unregistered external function when reloading
    a saved model (`Could not locate function 'preprocess_input'`), which surfaced as a real
    failure the first time evaluate.py tried to load a model saved this way. A plain registered
    Layer subclass has no such dependency.
    """

    def call(self, inputs):
        return (inputs / 127.5) - 1.0


def build_preprocessing_layer() -> tf.keras.layers.Layer:
    """
    Implements config.NORMALIZATION_MODE. See config.py's module docstring — this is a pragmatic
    solo-context default for D-04, not Acenas's frozen decision.
    """
    if config.NORMALIZATION_MODE == "mobilenet_v2_pm1_1":
        return MobileNetV2Preprocess(name="mobilenet_v2_preprocess")
    raise ValueError(
        f"Unknown NORMALIZATION_MODE '{config.NORMALIZATION_MODE}'. If you're switching to the "
        "manuscript's other named option ([0, 1] normalization), add that branch here explicitly "
        "rather than guessing — see claude/09_ML_MODEL_CONTRACT.md's Normalization section."
    )


def build_model(num_classes: int = config.NUM_CLASSES) -> tuple[tf.keras.Model, tf.keras.Model]:
    """
    Returns (full_model, base_model). base_model is exposed separately so train.py can freeze it
    for head-only training and later unfreeze layers from config.FINE_TUNE_AT_LAYER for fine-tuning.
    """
    inputs = tf.keras.Input(shape=config.INPUT_SHAPE, name="corn_leaf_image")
    x = build_preprocessing_layer()(inputs)

    base_model = tf.keras.applications.MobileNetV2(
        input_shape=config.INPUT_SHAPE,
        include_top=False,
        weights="imagenet",
    )
    base_model.trainable = False

    x = base_model(x, training=False)
    x = tf.keras.layers.GlobalAveragePooling2D(name="global_average_pooling")(x)
    x = tf.keras.layers.Dropout(0.2, name="head_dropout")(x)
    outputs = tf.keras.layers.Dense(num_classes, activation="softmax", name="class_probabilities")(x)

    model = tf.keras.Model(inputs, outputs, name="cornguard_mobilenetv2")
    return model, base_model


def compile_for_head_training(model: tf.keras.Model) -> None:
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=config.BASE_LEARNING_RATE),
        loss="sparse_categorical_crossentropy",
        metrics=["accuracy"],
    )


def unfreeze_for_fine_tuning(model: tf.keras.Model, base_model: tf.keras.Model) -> None:
    base_model.trainable = True
    for layer in base_model.layers[: config.FINE_TUNE_AT_LAYER]:
        layer.trainable = False
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=config.FINE_TUNE_LEARNING_RATE),
        loss="sparse_categorical_crossentropy",
        metrics=["accuracy"],
    )
