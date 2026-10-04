package com.cornguard.app.model

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Real, on-device TFLite classifier backed by the trained CornGuard model (MobileNetV2 backbone,
 * see "Disease model" in android/README.md). [com.cornguard.app.di.ServiceLocator] picks
 * this over [PlaceholderCornLeafClassifier] whenever the model assets are bundled (see [isAvailable]).
 *
 * Bundled assets:
 * - `model.tflite` — input `[1, 224, 224, 3]` float32 RGB, output `[1, 4]` softmax float32.
 * - `labels.json` — class-index order and disease codes, read at runtime, never hardcoded here.
 *   The trained order is Blight (= northern_leaf_blight), Common Rust, Gray Leaf Spot, Healthy.
 * - `model_version.txt` — the version string stored with every diagnosis record.
 * - `model_config.json` — input size and pixel [InputNormalization]. The model graph contains no
 *   scaling layer, so the mode there must match training (`mobilenet_v2.preprocess_input`, i.e.
 *   `minus_one_to_one`, per the training notebook).
 *
 * @param normalizationOverride forces a pixel scaling mode instead of the one in
 *   `model_config.json`; only the calibration test should pass this.
 */
class TfliteCornLeafClassifier(
    context: Context,
    normalizationOverride: InputNormalization? = null
) : CornLeafClassifier {

    private val interpreter: Interpreter
    private val labels: List<LabelEntry>
    private val inputSize: Int
    private val normalization: InputNormalization

    override val modelVersion: String

    init {
        val assets = context.assets
        interpreter = Interpreter(loadModelFile(assets))
        labels = loadLabels(assets)
        modelVersion = runCatching {
            assets.open(MODEL_VERSION_ASSET).bufferedReader().use { it.readText().trim() }
        }.getOrDefault("unknown")

        val config = loadConfig(assets)
        inputSize = config?.optInt("input_size", DEFAULT_INPUT_SIZE) ?: DEFAULT_INPUT_SIZE
        normalization = normalizationOverride
            ?: config?.optString("normalization")?.takeIf { it.isNotEmpty() }
                ?.let(InputNormalization::fromConfigValue)
            ?: InputNormalization.MINUS_ONE_TO_ONE

        val outputClasses = interpreter.getOutputTensor(0).shape().last()
        check(outputClasses == labels.size) {
            "model.tflite outputs $outputClasses classes but labels.json has ${labels.size} entries"
        }
    }

    override fun classify(leafImage: Bitmap): DetectionResult {
        require(leafImage.width > 0 && leafImage.height > 0) { "leafImage has no pixel data" }

        val startNanos = System.nanoTime()

        val resized = Bitmap.createScaledBitmap(centerCropSquare(leafImage), inputSize, inputSize, true)
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
            confidence = probabilities[classIndex].coerceIn(0f, 1f),
            probabilities = probabilities.copyOf(),
            modelVersion = modelVersion,
            inferenceTimeMs = inferenceTimeMs
        )
    }

    override fun close() {
        interpreter.close()
    }

    /** Crops the largest centered square so resizing to the square model input doesn't distort the leaf. */
    private fun centerCropSquare(bitmap: Bitmap): Bitmap {
        if (bitmap.width == bitmap.height) return bitmap
        val side = minOf(bitmap.width, bitmap.height)
        return Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
    }

    private fun preprocess(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * CHANNELS)
        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        for (pixel in pixels) {
            buffer.putFloat(normalization.scale((pixel shr 16) and 0xFF))
            buffer.putFloat(normalization.scale((pixel shr 8) and 0xFF))
            buffer.putFloat(normalization.scale(pixel and 0xFF))
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

    private fun loadConfig(assets: AssetManager): JSONObject? = runCatching {
        JSONObject(assets.open(MODEL_CONFIG_ASSET).bufferedReader().use { it.readText() })
    }.getOrNull()

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
        private const val MODEL_CONFIG_ASSET = "model_config.json"
        private const val DEFAULT_INPUT_SIZE = 224
        private const val CHANNELS = 3

        /** True if the bundled model assets this classifier needs actually exist. */
        fun isAvailable(context: Context): Boolean = runCatching {
            context.assets.open(MODEL_ASSET).close()
            context.assets.open(LABELS_ASSET).close()
            true
        }.getOrDefault(false)
    }
}
