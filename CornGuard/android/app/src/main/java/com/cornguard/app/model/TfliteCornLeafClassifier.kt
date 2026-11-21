package com.cornguard.app.model

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import org.json.JSONArray
import org.tensorflow.lite.Interpreter

/**
 * Real, on-device TFLite classifier — replaces [PlaceholderCornLeafClassifier] once
 * `assets/model.tflite` and `assets/labels.json` actually exist (see [isAvailable] and
 * [com.cornguard.app.di.ServiceLocator], which picks between the two at startup).
 *
 * `assets/labels.json` is copied verbatim from ml/export_tflite.py's `labels_<version>.json`
 * output — the class-index order and disease codes come from that file at runtime, never
 * hardcoded here, per claude/09_ML_MODEL_CONTRACT.md ("do not infer class order alphabetically
 * ... must exactly match the trained model"). `assets/model_version.txt` holds the plain
 * `model_version` string from the same export run.
 *
 * Pixel normalization (D-04 — claude/03_SOURCE_ALIGNMENT_AND_DECISION_GATES.md) is NOT done here
 * — ml/export_tflite.py converts the full Keras model, so the (value / 127.5) - 1 transform
 * (ml/model.py's MobileNetV2Preprocess layer) is already the first op inside model.tflite itself.
 * [TfliteImageInput.toInputBuffer] only resizes and hands over raw [0, 255] RGB values;
 * normalizing again would double-apply the transform (a real bug, see that function's doc). If D-04's
 * normalization choice ever changes, that change belongs in ml/model.py and requires a re-export,
 * not an edit here — and the Android Parity Test (ml/parity_test.py) must be re-run.
 */
class TfliteCornLeafClassifier(context: Context) : CornLeafClassifier {

    private val interpreter: Interpreter
    private val labels: List<LabelEntry>

    override val modelVersion: String

    init {
        val assets = context.assets
        interpreter = Interpreter(TfliteImageInput.loadModelFile(assets, MODEL_ASSET))
        labels = loadLabels(assets)
        modelVersion = runCatching {
            assets.open(MODEL_VERSION_ASSET).bufferedReader().use { it.readText().trim() }
        }.getOrDefault("unknown")
    }

    override fun classify(leafImage: Bitmap): DetectionResult {
        require(leafImage.width > 0 && leafImage.height > 0) { "leafImage has no pixel data" }

        val startNanos = System.nanoTime()

        val inputBuffer = TfliteImageInput.toInputBuffer(leafImage)
        val outputBuffer = Array(1) { FloatArray(labels.size) }

        interpreter.run(inputBuffer, outputBuffer)

        val inferenceTimeMs = (System.nanoTime() - startNanos) / 1_000_000

        val probabilities = outputBuffer[0]
        val classIndex = probabilities.indices.maxByOrNull { probabilities[it] }
            ?: throw IllegalStateException("Model produced an empty output")
        val label = labels.getOrNull(classIndex)
            ?: throw IllegalStateException("Model output index $classIndex has no matching entry in labels.json")

        return DetectionResult(
            classIndex = classIndex,
            diseaseCode = label.diseaseCode,
            displayLabel = label.displayLabel,
            confidence = probabilities[classIndex],
            probabilities = probabilities.copyOf(),
            modelVersion = modelVersion,
            inferenceTimeMs = inferenceTimeMs
        )
    }

    override fun close() {
        interpreter.close()
    }

    private data class LabelEntry(val index: Int, val diseaseCode: String, val displayLabel: String)

    private fun loadLabels(assets: AssetManager): List<LabelEntry> {
        val json = assets.open(LABELS_ASSET).bufferedReader().use { it.readText() }
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val entry = array.getJSONObject(i)
            LabelEntry(
                index = entry.getInt("index"),
                diseaseCode = entry.getString("disease_code"),
                displayLabel = entry.getString("display_label")
            )
        }.sortedBy { it.index }
    }

    companion object {
        private const val MODEL_ASSET = "model.tflite"
        private const val LABELS_ASSET = "labels.json"
        private const val MODEL_VERSION_ASSET = "model_version.txt"

        /** True if the bundled model assets this classifier needs actually exist. */
        fun isAvailable(context: Context): Boolean = runCatching {
            context.assets.open(MODEL_ASSET).close()
            context.assets.open(LABELS_ASSET).close()
            true
        }.getOrDefault(false)
    }
}
