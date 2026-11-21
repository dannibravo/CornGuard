package com.cornguard.app.model

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONArray
import org.tensorflow.lite.Interpreter

/**
 * On-device corn / not-corn gate. Loads:
 *   - `assets/corn_gate.tflite`        from ml/export_corn_gate.py
 *   - `assets/corn_gate_labels.json`   that run's `labels_<version>.json`; the index of the
 *                                      "corn" output is read from it, not hardcoded
 *   - `assets/corn_gate_version.txt`   the plain model_version string
 *
 * Same input contract as the disease model (see [TfliteImageInput]): raw [0, 255] RGB, 224x224.
 */
class TfliteCornGate(
    context: Context,
    private val threshold: Float = DEFAULT_THRESHOLD
) : CornGate {

    private val interpreter: Interpreter
    private val cornIndex: Int
    private val outputSize: Int

    override val modelVersion: String

    init {
        val assets = context.assets
        interpreter = Interpreter(TfliteImageInput.loadModelFile(assets, MODEL_ASSET))

        val labels = JSONArray(assets.open(LABELS_ASSET).bufferedReader().use { it.readText() })
        outputSize = labels.length()
        cornIndex = (0 until labels.length())
            .map { labels.getJSONObject(it) }
            .firstOrNull { it.getString("label") == CORN_LABEL }
            ?.getInt("index")
            ?: throw IllegalStateException("$LABELS_ASSET has no \"$CORN_LABEL\" entry")

        modelVersion = runCatching {
            assets.open(MODEL_VERSION_ASSET).bufferedReader().use { it.readText().trim() }
        }.getOrDefault("unknown")
    }

    override fun check(image: Bitmap): CornGateResult {
        require(image.width > 0 && image.height > 0) { "image has no pixel data" }

        val output = Array(1) { FloatArray(outputSize) }
        interpreter.run(TfliteImageInput.toInputBuffer(image), output)

        return CornGateResult(
            cornProbability = output[0][cornIndex].coerceIn(0f, 1f),
            threshold = threshold,
            modelVersion = modelVersion
        )
    }

    override fun close() {
        interpreter.close()
    }

    companion object {
        private const val MODEL_ASSET = "corn_gate.tflite"
        private const val LABELS_ASSET = "corn_gate_labels.json"
        private const val MODEL_VERSION_ASSET = "corn_gate_version.txt"
        private const val CORN_LABEL = "corn"

        /**
         * The threshold ml/artifacts/corn-gate-v1/gate_evaluation.md was measured at (99.68% of
         * corn test images accepted, 99.67% of not-corn rejected). Change it only after
         * re-measuring on held-out data.
         */
        const val DEFAULT_THRESHOLD = 0.5f

        /** True if the bundled gate assets exist. Without them scans run ungated (old behaviour). */
        fun isAvailable(context: Context): Boolean = runCatching {
            context.assets.open(MODEL_ASSET).close()
            context.assets.open(LABELS_ASSET).close()
            true
        }.getOrDefault(false)
    }
}
