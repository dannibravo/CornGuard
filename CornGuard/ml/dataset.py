"""
Builds tf.data.Dataset pipelines from the manifest CSV produced by prepare_dataset.py.
Augmentation is applied to the training split only (claude/09_ML_MODEL_CONTRACT.md).
"""

import pandas as pd
import tensorflow as tf

import config
from augmentation import build_augmentation_layer


def load_manifest(manifest_path=config.MANIFEST_PATH) -> pd.DataFrame:
    if not manifest_path.exists():
        raise SystemExit(
            f"No manifest at {manifest_path}. Run prepare_dataset.py first — this pipeline does "
            "not assume a manifest exists."
        )
    return pd.read_csv(manifest_path)


def _decode_image(path: tf.Tensor) -> tf.Tensor:
    raw = tf.io.read_file(path)
    image = tf.image.decode_jpeg(raw, channels=config.IMAGE_CHANNELS)
    image = tf.image.resize(image, config.IMAGE_SIZE)
    return image


def _class_to_index() -> dict[str, int]:
    return {name: i for i, name in enumerate(config.CLASS_ORDER)}


def build_split_dataset(
    manifest: pd.DataFrame,
    split: str,
    dataset_root,
    batch_size: int = config.BATCH_SIZE,
    shuffle: bool = False,
    augment: bool = False,
) -> tf.data.Dataset:
    """
    dataset_root is the directory prepare_dataset.py's --raw-dir's *parent* was run from (the
    manifest's relative_path column is relative to that directory) — normally the ml/ directory
    itself, i.e. the same cwd every other script in this pipeline expects.
    """
    rows = manifest[manifest["split"] == split]
    if rows.empty:
        raise ValueError(f"No rows for split='{split}' in the manifest.")

    class_to_index = _class_to_index()
    paths = [str(dataset_root / p) for p in rows["relative_path"]]
    labels = [class_to_index[c] for c in rows["class_label"]]

    ds = tf.data.Dataset.from_tensor_slices((paths, labels))
    if shuffle:
        ds = ds.shuffle(buffer_size=len(paths), seed=config.RANDOM_SEED, reshuffle_each_iteration=True)

    ds = ds.map(
        lambda path, label: (_decode_image(path), label),
        num_parallel_calls=tf.data.AUTOTUNE,
    )

    if augment:
        augmentation_layer = build_augmentation_layer()
        ds = ds.map(
            lambda image, label: (augmentation_layer(image, training=True), label),
            num_parallel_calls=tf.data.AUTOTUNE,
        )

    ds = ds.batch(batch_size).prefetch(tf.data.AUTOTUNE)
    return ds
