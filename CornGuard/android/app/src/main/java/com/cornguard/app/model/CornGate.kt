package com.cornguard.app.model

import android.graphics.Bitmap

/**
 * First step of every scan: decides whether a photo shows a corn leaf at all, before the disease
 * classifier runs. The disease classifier is a closed-set 4-way softmax and must pick one of its
 * diseases for ANY input, so on its own it reports flowers, people and screens as diseased corn
 * with high confidence. The gate is a separate binary model (ml/train_corn_gate.py) trained on
 * corn leaves vs. non-corn images.
 *
 * Runs entirely on-device, like [CornLeafClassifier].
 */
interface CornGate {

    /** The `.tflite` release this gate was built from, e.g. `cornguard_corn_gate_v1`. */
    val modelVersion: String

    fun check(image: Bitmap): CornGateResult

    fun close()
}

/**
 * @property cornProbability the gate model's p(corn), 0.0-1.0.
 * @property threshold the cut-off the decision was made at; a photo is corn when
 *   [cornProbability] >= [threshold].
 */
data class CornGateResult(
    val cornProbability: Float,
    val threshold: Float,
    val modelVersion: String
) {
    init {
        require(cornProbability in 0f..1f) { "cornProbability must be within 0.0-1.0, was $cornProbability" }
        require(threshold in 0f..1f) { "threshold must be within 0.0-1.0, was $threshold" }
    }

    val isCorn: Boolean get() = cornProbability >= threshold
}
