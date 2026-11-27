"""
Trains cornguard_mobilenetv2_v5: the recipe of the shipped v4 model (CORNGUARD_EDA_&_TRAINING_v2.ipynb:
MobileNetV2 + 256/128 head, label smoothing 0.1, head phase then fine-tune from layer 60, same
augmentation, same [-1, 1] input scaling, same class order) on v5_field/manifest_v5.csv, which adds
field-condition smartphone images to the public set.

Run from ml/:  .venv/Scripts/python v5_field/train_v5.py
Outputs go to artifacts/v5-field/.

Images are first re-encoded once into data/v5_cache/<sha256>.jpg (shorter side CACHE_SIDE) because
the field originals are 12 MP and some are HEIC, which tf.io cannot decode.
"""

import csv
import json
import os
import sys
import time
from pathlib import Path

import numpy as np
import tensorflow as tf
from PIL import Image, ImageOps
from pillow_heif import register_heif_opener
from sklearn.metrics import classification_report, confusion_matrix, f1_score
from sklearn.utils.class_weight import compute_class_weight
from tensorflow.keras import Model, layers
from tensorflow.keras.applications import MobileNetV2, mobilenet_v2

register_heif_opener()

ML_DIR = Path(__file__).resolve().parent.parent
MANIFEST = ML_DIR / "v5_field" / "manifest_v5.csv"
CACHE_DIR = ML_DIR / "data" / "v5_cache"
OUT_DIR = ML_DIR / "artifacts" / "v5-field"
CACHE_SIDE = 384

SEED = 42
IMAGE_SIZE = (224, 224)
BATCH_SIZE = 32
EPOCHS_HEAD = int(os.environ.get("EPOCHS_HEAD", 12))
EPOCHS_FINE = int(os.environ.get("EPOCHS_FINE", 20))
LR_HEAD = 1e-3
LR_FINE = 3e-5
UNFREEZE_FROM = 60
LABEL_SMOOTHING = 0.1
MODEL_VERSION = "cornguard_mobilenetv2_v5"

CLASSES = ["Blight", "Common_Rust", "Gray_Leaf_Spot", "Healthy"]  # sorted == app labels.json order
CLASS_TO_IDX = {c: i for i, c in enumerate(CLASSES)}
NUM_CLASSES = len(CLASSES)
APP_LABELS = {
    "Blight": ("northern_leaf_blight", "Northern Leaf Blight"),
    "Common_Rust": ("common_rust", "Common Rust"),
    "Gray_Leaf_Spot": ("gray_leaf_spot", "Gray Leaf Spot"),
    "Healthy": ("healthy", "Healthy"),
}

tf.keras.utils.set_random_seed(SEED)
AUTOTUNE = tf.data.AUTOTUNE


def load_manifest():
    with open(MANIFEST, newline="", encoding="utf-8") as fh:
        return list(csv.DictReader(fh))


def build_cache(rows):
    CACHE_DIR.mkdir(parents=True, exist_ok=True)
    made = 0
    for r in rows:
        dst = CACHE_DIR / f"{r['sha256']}.jpg"
        r["cached"] = str(dst)
        if dst.exists():
            continue
        with Image.open(ML_DIR / r["path"]) as im:
            im = ImageOps.exif_transpose(im).convert("RGB")
            w, h = im.size
            s = CACHE_SIDE / min(w, h)
            if s < 1:
                im = im.resize((round(w * s), round(h * s)), Image.LANCZOS)
            im.save(dst, quality=95)
        made += 1
    print(f"cache: {made} new, {len(rows)} total", flush=True)


def decode(path):
    img = tf.io.decode_jpeg(tf.io.read_file(path), channels=3)
    return tf.cast(img, tf.float32) / 255.0


def center_square(img):
    h, w = tf.shape(img)[0], tf.shape(img)[1]
    s = tf.minimum(h, w)
    return tf.image.crop_to_bounding_box(img, (h - s) // 2, (w - s) // 2, s, s)


def eval_preprocess(img):
    return tf.image.resize(center_square(img), IMAGE_SIZE, method="bilinear")


def train_preprocess(img):
    s = tf.minimum(tf.shape(img)[0], tf.shape(img)[1])
    crop = tf.cast(tf.cast(s, tf.float32) * tf.random.uniform([], 0.75, 1.0), tf.int32)
    img = tf.image.random_crop(img, tf.stack([crop, crop, 3]))
    img = tf.image.resize(img, IMAGE_SIZE, method="bilinear")
    img = tf.image.random_flip_left_right(img)
    img = tf.image.random_flip_up_down(img)
    img = tf.image.rot90(img, tf.random.uniform([], 0, 4, dtype=tf.int32))
    img = tf.image.random_brightness(img, 0.15)
    img = tf.image.random_contrast(img, 0.8, 1.2)
    img = tf.image.random_saturation(img, 0.8, 1.2)
    img = tf.image.random_hue(img, 0.03)
    img = tf.clip_by_value(img, 0.0, 1.0)
    img = tf.image.random_jpeg_quality(img, 60, 100)
    return img


def to_model_input(img01):
    return mobilenet_v2.preprocess_input(img01 * 255.0)


random_tilt = layers.RandomRotation(0.05, fill_mode="reflect", seed=SEED)


def make_dataset(rows, training, class_weights=None):
    paths = np.array([r["cached"] for r in rows])
    labels = np.array([CLASS_TO_IDX[r["label"]] for r in rows], dtype=np.int32)
    if training:
        weights = np.array([class_weights[l] for l in labels], dtype=np.float32)
        ds = tf.data.Dataset.from_tensor_slices((paths, labels, weights))
        ds = ds.shuffle(len(rows), seed=SEED, reshuffle_each_iteration=True)
        ds = ds.map(lambda p, l, w: (to_model_input(train_preprocess(decode(p))),
                                     tf.one_hot(l, NUM_CLASSES), w), num_parallel_calls=AUTOTUNE)
        ds = ds.batch(BATCH_SIZE)
        ds = ds.map(lambda x, y, w: (random_tilt(x, training=True), y, w), num_parallel_calls=AUTOTUNE)
    else:
        ds = tf.data.Dataset.from_tensor_slices((paths, labels))
        ds = ds.map(lambda p, l: (to_model_input(eval_preprocess(decode(p))), tf.one_hot(l, NUM_CLASSES)),
                    num_parallel_calls=AUTOTUNE)
        ds = ds.batch(BATCH_SIZE).cache()
    return ds.prefetch(AUTOTUNE)


def build_model():
    base = MobileNetV2(input_shape=(*IMAGE_SIZE, 3), include_top=False, weights="imagenet")
    base.trainable = False
    inputs = tf.keras.Input(shape=(*IMAGE_SIZE, 3))
    x = base(inputs, training=False)
    x = layers.GlobalAveragePooling2D()(x)
    x = layers.Dense(256, activation="relu")(x)
    x = layers.Dropout(0.40)(x)
    x = layers.Dense(128, activation="relu")(x)
    x = layers.Dropout(0.30)(x)
    outputs = layers.Dense(NUM_CLASSES, activation="softmax")(x)
    return Model(inputs, outputs), base


class MacroF1(tf.keras.callbacks.Callback):
    """val_macro_f1 over the whole val set, plus field-healthy recall so we can see it move."""

    def __init__(self, ds, y, field_healthy_mask):
        super().__init__()
        self.ds, self.y, self.mask = ds, y, field_healthy_mask

    def on_epoch_end(self, epoch, logs=None):
        pred = self.model.predict(self.ds, verbose=0).argmax(axis=1)
        logs["val_macro_f1"] = f1_score(self.y, pred, average="macro")
        fh = float((pred[self.mask] == CLASS_TO_IDX["Healthy"]).mean()) if self.mask.any() else float("nan")
        logs["val_field_healthy_recall"] = fh
        print(f" - val_macro_f1: {logs['val_macro_f1']:.4f} - val_field_healthy_recall: {fh:.3f}", flush=True)


def report(model, rows, name):
    if not rows:
        return {}
    ds = make_dataset(rows, training=False)
    proba = model.predict(ds, verbose=0)
    y = np.array([CLASS_TO_IDX[r["label"]] for r in rows])
    pred = proba.argmax(1)
    present = sorted(set(y) | set(pred))
    rep = classification_report(y, pred, labels=present, target_names=[CLASSES[i] for i in present],
                                digits=4, zero_division=0, output_dict=True)
    cm = confusion_matrix(y, pred, labels=list(range(NUM_CLASSES)))
    print(f"\n== {name}  n={len(rows)}  acc={(pred == y).mean():.4f}  "
          f"macroF1(present classes)={f1_score(y, pred, average='macro', labels=sorted(set(y))):.4f}")
    print(classification_report(y, pred, labels=present, target_names=[CLASSES[i] for i in present],
                                digits=4, zero_division=0))
    print("confusion (rows=true, cols=pred, order", CLASSES, ")\n", cm, flush=True)
    return {"n": len(rows), "accuracy": float((pred == y).mean()), "report": rep, "confusion": cm.tolist()}


def main():
    t0 = time.time()
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    rows = load_manifest()
    build_cache(rows)
    split = {s: [r for r in rows if r["split"] == s] for s in ("train", "val", "test")}
    print({s: len(v) for s, v in split.items()}, flush=True)

    y_train = np.array([CLASS_TO_IDX[r["label"]] for r in split["train"]])
    cw = compute_class_weight("balanced", classes=np.arange(NUM_CLASSES), y=y_train)
    class_weights = dict(enumerate(cw))
    print("class weights:", {CLASSES[i]: round(w, 3) for i, w in class_weights.items()})

    train_ds = make_dataset(split["train"], True, class_weights)
    val_ds = make_dataset(split["val"], False)
    y_val = np.array([CLASS_TO_IDX[r["label"]] for r in split["val"]])
    fh_mask = np.array([r["domain"] == "field" and r["label"] == "Healthy" for r in split["val"]])
    steps_per_epoch = int(tf.data.experimental.cardinality(train_ds))

    model, base = build_model()
    loss = tf.keras.losses.CategoricalCrossentropy(label_smoothing=LABEL_SMOOTHING)
    model.compile(optimizer=tf.keras.optimizers.Adam(LR_HEAD), loss=loss, metrics=["accuracy"])
    model.fit(train_ds, epochs=EPOCHS_HEAD, validation_data=val_ds, verbose=2, callbacks=[
        MacroF1(val_ds, y_val, fh_mask),
        tf.keras.callbacks.ReduceLROnPlateau(monitor="val_loss", factor=0.5, patience=2, min_lr=1e-6, verbose=1),
        tf.keras.callbacks.EarlyStopping(monitor="val_macro_f1", mode="max", patience=4,
                                         restore_best_weights=True, verbose=1),
    ])
    model.save(OUT_DIR / "best_head.keras")

    base.trainable = True
    for layer in base.layers[:UNFREEZE_FROM]:
        layer.trainable = False
    for layer in base.layers:
        if isinstance(layer, layers.BatchNormalization):
            layer.trainable = False
    schedule = tf.keras.optimizers.schedules.CosineDecay(
        initial_learning_rate=LR_FINE / 10, decay_steps=steps_per_epoch * EPOCHS_FINE, alpha=0.05,
        warmup_target=LR_FINE, warmup_steps=steps_per_epoch * 2)
    model.compile(optimizer=tf.keras.optimizers.AdamW(learning_rate=schedule, weight_decay=1e-4),
                  loss=loss, metrics=["accuracy"])
    model.fit(train_ds, epochs=EPOCHS_FINE, validation_data=val_ds, verbose=2, callbacks=[
        MacroF1(val_ds, y_val, fh_mask),
        tf.keras.callbacks.EarlyStopping(monitor="val_macro_f1", mode="max", patience=6,
                                         restore_best_weights=True, verbose=1),
    ])
    model.save(OUT_DIR / "model.keras")

    results = {
        "model_version": MODEL_VERSION,
        "test_public": report(model, [r for r in split["test"] if r["domain"] == "public"], "TEST public"),
        "test_field": report(model, [r for r in split["test"] if r["domain"] == "field"], "TEST field"),
        "test_all": report(model, split["test"], "TEST all"),
    }

    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]  # dynamic-range, same as v4
    tfl = converter.convert()
    tfl_path = OUT_DIR / "model.tflite"
    tfl_path.write_bytes(tfl)

    # TFLite parity on the whole test split
    it = tf.lite.Interpreter(model_content=tfl)
    it.allocate_tensors()
    i_idx, o_idx = it.get_input_details()[0]["index"], it.get_output_details()[0]["index"]
    keras_pred = model.predict(make_dataset(split["test"], False), verbose=0).argmax(1)
    tfl_pred = []
    for x, _ in make_dataset(split["test"], False).unbatch():
        it.set_tensor(i_idx, x[None].numpy())
        it.invoke()
        tfl_pred.append(int(it.get_tensor(o_idx)[0].argmax()))
    agree = float((np.array(tfl_pred) == keras_pred).mean())
    results["tflite_keras_agreement_test"] = agree
    print(f"TFLite vs Keras agreement on test: {agree:.4f}")

    assets = OUT_DIR / "android_assets"
    assets.mkdir(exist_ok=True)
    (assets / "model.tflite").write_bytes(tfl)
    (assets / "labels.json").write_text(json.dumps(
        [{"index": i, "disease_code": APP_LABELS[c][0], "display_label": APP_LABELS[c][1]}
         for i, c in enumerate(CLASSES)], indent=2))
    (assets / "model_config.json").write_text(json.dumps(
        {"input_size": IMAGE_SIZE[0], "normalization": "minus_one_to_one"}, indent=2))
    (assets / "model_version.txt").write_text(MODEL_VERSION)

    results["minutes"] = round((time.time() - t0) / 60, 1)
    (OUT_DIR / "evaluation_report.json").write_text(json.dumps(results, indent=2))
    print("done in", results["minutes"], "min", flush=True)


if __name__ == "__main__":
    sys.exit(main())
