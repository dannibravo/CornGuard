"""v2 field-augmentation experiment — dataset construction. Reuses ml/dataset.py's existing,
UNMODIFIED decode/class-index/manifest-loading logic by direct import (no duplication of that
code). The only new logic here is build_split_dataset_field_aug(), which is a near-identical copy
of ml/dataset.py's build_split_dataset() with ONE substitution: it calls this experiment's own
augmentation_v2_field.build_augmentation_layer() instead of ml/augmentation.py's version.

This duplication is unavoidable, not a design preference: ml/dataset.py's build_split_dataset()
hard-imports `from augmentation import build_augmentation_layer` at module level with no injection
seam, so swapping the augmentation module without editing that file requires re-implementing the
small amount of surrounding glue (shuffle/batch/prefetch wiring) here instead. See
config_diff_vs_v2_clean_baseline.md for this documented as an explicit, unavoidable difference.

Must be run with ml/ as the working directory (same convention as every other script in this
pipeline, e.g. `cd ml && python v2_field_aug_experiment/train_v2_field_aug.py`), so that `import
config`, `import dataset`, and the relative manifest/data paths all resolve exactly as they do for
train.py and evaluate.py.
"""

import tensorflow as tf

import config
from dataset import _decode_image, _class_to_index, load_manifest  # noqa: F401  (load_manifest re-exported for convenience)
from augmentation_v2_field import build_augmentation_layer as build_field_augmentation_layer


def build_split_dataset_field_aug(
    manifest,
    split: str,
    dataset_root,
    batch_size: int = config.BATCH_SIZE,
    shuffle: bool = False,
    augment: bool = False,
) -> tf.data.Dataset:
    """Identical to dataset.py::build_split_dataset, except augment=True uses
    augmentation_v2_field.build_augmentation_layer() instead of augmentation.build_augmentation_layer().
    Validation/test callers must pass augment=False, exactly as the control's train.py/evaluate.py do.
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
        augmentation_layer = build_field_augmentation_layer()
        ds = ds.map(
            lambda image, label: (augmentation_layer(image, training=True), label),
            num_parallel_calls=tf.data.AUTOTUNE,
        )

    ds = ds.batch(batch_size).prefetch(tf.data.AUTOTUNE)
    return ds
