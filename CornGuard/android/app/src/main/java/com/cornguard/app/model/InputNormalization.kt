package com.cornguard.app.model

/**
 * How RGB pixel values (0..255) are scaled before being fed to the model. The bundled model has no
 * Rescaling/Normalization layer inside its graph, so the scaling used at training time must be
 * applied here instead. The active mode is read from `assets/model_config.json`
 * (`"normalization"`); the training notebook uses `mobilenet_v2.preprocess_input`, i.e.
 * [MINUS_ONE_TO_ONE].
 */
enum class InputNormalization(val configValue: String) {
    /** `tf.keras.applications.mobilenet_v2.preprocess_input`: `x / 127.5 - 1`. */
    MINUS_ONE_TO_ONE("minus_one_to_one"),

    /** `Rescaling(1/255)` / `ImageDataGenerator(rescale=1/255)`: `x / 255`. */
    ZERO_TO_ONE("zero_to_one"),

    /** No scaling — raw 0..255 floats. */
    RAW_0_255("raw_0_255");

    fun scale(channel: Int): Float = when (this) {
        MINUS_ONE_TO_ONE -> (channel / 127.5f) - 1f
        ZERO_TO_ONE -> channel / 255f
        RAW_0_255 -> channel.toFloat()
    }

    companion object {
        fun fromConfigValue(value: String): InputNormalization =
            entries.firstOrNull { it.configValue == value }
                ?: throw IllegalArgumentException("Unknown normalization '$value' in model_config.json")
    }
}
