package com.cornguard.app.model

import android.graphics.Bitmap

/**
 * Boundary between the Android app and the TFLite model produced by Acenas
 * (claude/09_ML_MODEL_CONTRACT.md). Sprint 0 ships only this interface and
 * [PlaceholderCornLeafClassifier] — the real implementation is added in Sprint 2
 * (feature/tflite-integration) once the preprocessing contract (D-04) and class order are frozen.
 *
 * Implementations MUST run entirely on-device. Nothing behind this interface may call Firebase
 * or require network connectivity (claude/15_CLAUDE.md Project Principle / claude/04_DEVELOPMENT_RULES.md #2).
 */
interface CornLeafClassifier {

    /** The `.tflite` release this classifier was built from, e.g. `cornguard_mobilenetv2_v1`. */
    val modelVersion: String

    /**
     * Classifies a single corn leaf image.
     *
     * @throws ModelNotReadyException if no real model is wired in yet.
     * @throws IllegalArgumentException if [leafImage] is not a decodable/usable image.
     */
    fun classify(leafImage: Bitmap): DetectionResult

    /** Releases the underlying TFLite interpreter and any native buffers. */
    fun close()
}
