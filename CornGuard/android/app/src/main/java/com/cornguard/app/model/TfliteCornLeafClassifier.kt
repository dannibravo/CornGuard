package com.cornguard.app.model

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import org.json.JSONArray
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

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
 * Preprocessing MUST match ml/model.py's `build_preprocessing_layer` exactly (D-04 —
 * claude/03_SOURCE_ALIGNMENT_AND_DECISION_GATES.md): pixels scaled to [-1, 1] via
 * `(value / 127.5) - 1`, RGB order, 224x224. This is Acenas's pragmatic solo-context default,
 * not a frozen decision — if that changes, this class's [preprocess] must change with it, and
 * the Android Parity Test (ml/parity_test.py) must be re-run.
 */
class TfliteCornLeafClassifier(context: Context) : CornLeafClassifier {

    private val interpreter: Interpreter
    private val labels: List<LabelEntry>

    override val modelVersion: String

    init {
        val assets = context.assets
        interpreter = Interpreter(loadModelFile(assets))
        labels = loadLabels(assets)
        modelVersion = runCatching {
            assets.open(MODEL_VERSION_ASSET).bufferedReader().use { it.readText().trim() }
        }.getOrDefault("unknown")
    }

    override fun classify(leafImage: Bitmap): DetectionResult {
        require(leafImage.width > 0 && leafImage.height > 0) { "leafImage has no pixel data" }

        val startNanos = System.nanoTime()

        val resized = Bitmap.createScaledBitmap(leafImage, INPUT_SIZE, INPUT_SIZE, true)
        val inputBuffer = preprocess(resized)
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

    private fun preprocess(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * CHANNELS)
        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            buffer.putFloat((r / 127.5f) - 1f)
            buffer.putFloat((g / 127.5f) - 1f)
            buffer.putFloat((b / 127.5f) - 1f)
        }
        buffer.rewind()
        return buffer
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

    private fun loadModelFile(assets: AssetManager): MappedByteBuffer {
        val descriptor = assets.openFd(MODEL_ASSET)
        FileInputStream(descriptor.fileDescriptor).use { input ->
            return input.channel.map(
                FileChannel.MapMode.READ_ONLY,
                descriptor.startOffset,
                descriptor.declaredLength
            )
        }
    }

    companion object {
        private const val MODEL_ASSET = "model.tflite"
        private const val LABELS_ASSET = "labels.json"
        private const val MODEL_VERSION_ASSET = "model_version.txt"
        private const val INPUT_SIZE = 224
        private const val CHANNELS = 3

        /** True if the bundled model assets this classifier needs actually exist. */
        fun isAvailable(context: Context): Boolean = runCatching {
            context.assets.open(MODEL_ASSET).close()
            context.assets.open(LABELS_ASSET).close()
            true
        }.getOrDefault(false)
    }
}
