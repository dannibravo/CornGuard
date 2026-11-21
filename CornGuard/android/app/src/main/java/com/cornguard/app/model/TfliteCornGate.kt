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
 * On-device corn / not-corn gate (ml/train_corn_gate.py, exported by ml/export_corn_gate.py).
 *
 * Bundled assets:
 * - `corn_gate.tflite` — input `[1, 224, 224, 3]` float32 RGB, output `[1, 2]` softmax.
 * - `corn_gate_labels.json` — output index of `"corn"` / `"not_corn"`, read at runtime.
 * - `corn_gate_version.txt` — model version string.
 * - `corn_gate_config.json` — input size, pixel [InputNormalization] and decision threshold.
 *   Unlike the disease model, the gate's graph starts with its own MobileNetV2 preprocessing
 *   layer, so its mode is `raw_0_255`: scaling here as well would double-apply it.
 *
 * The whole photo is resized to the input size with no centre crop, which is how the gate was
 * trained and evaluated (ml/artifacts/corn-gate-v1/gate_evaluation.md: 99.68% of corn test
 * images accepted, 99.67% of not-corn rejected at threshold 0.5).
 */
class TfliteCornGate(context: Context) : CornGate {

    private val interpreter: Interpreter
    private val cornIndex: Int
    private val outputSize: Int
    private val inputSize: Int
    private val normalization: InputNormalization
    private val threshold: Float

    override val modelVersion: String

    init {
        val assets = context.assets
        interpreter = Interpreter(loadModelFile(assets))

        val labels = JSONArray(assets.open(LABELS_ASSET).bufferedReader().use { it.readText() })
        outputSize = labels.length()
        cornIndex = (0 until labels.length())
            .map { labels.getJSONObject(it) }
            .firstOrNull { it.getString("label") == CORN_LABEL }
            ?.getInt("index")
            ?: throw IllegalStateException("$LABELS_ASSET has no \"$CORN_LABEL\" entry")

        val config = runCatching {
            JSONObject(assets.open(CONFIG_ASSET).bufferedReader().use { it.readText() })
        }.getOrNull()
        inputSize = config?.optInt("input_size", DEFAULT_INPUT_SIZE) ?: DEFAULT_INPUT_SIZE
        normalization = config?.optString("normalization")?.takeIf { it.isNotEmpty() }
            ?.let(InputNormalization::fromConfigValue)
            ?: InputNormalization.RAW_0_255
        threshold = config?.optDouble("threshold", DEFAULT_THRESHOLD.toDouble())?.toFloat()
            ?: DEFAULT_THRESHOLD

        modelVersion = runCatching {
            assets.open(MODEL_VERSION_ASSET).bufferedReader().use { it.readText().trim() }
        }.getOrDefault("unknown")

        val outputClasses = interpreter.getOutputTensor(0).shape().last()
        check(outputClasses == outputSize) {
            "corn_gate.tflite outputs $outputClasses classes but $LABELS_ASSET has $outputSize entries"
        }
    }

    override fun check(image: Bitmap): CornGateResult {
        require(image.width > 0 && image.height > 0) { "image has no pixel data" }

        val resized = Bitmap.createScaledBitmap(image, inputSize, inputSize, true)
        val output = Array(1) { FloatArray(outputSize) }
        interpreter.run(preprocess(resized), output)

        return CornGateResult(
            cornProbability = output[0][cornIndex].coerceIn(0f, 1f),
            threshold = threshold,
            modelVersion = modelVersion
        )
    }

    override fun close() {
        interpreter.close()
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
        private const val MODEL_ASSET = "corn_gate.tflite"
        private const val LABELS_ASSET = "corn_gate_labels.json"
        private const val MODEL_VERSION_ASSET = "corn_gate_version.txt"
        private const val CONFIG_ASSET = "corn_gate_config.json"
        private const val CORN_LABEL = "corn"
        private const val DEFAULT_INPUT_SIZE = 224
        private const val CHANNELS = 3

        /** The threshold the gate's test metrics were measured at; change only after re-measuring. */
        const val DEFAULT_THRESHOLD = 0.5f

        /** True if the bundled gate assets exist. Without them scans run ungated. */
        fun isAvailable(context: Context): Boolean = runCatching {
            context.assets.open(MODEL_ASSET).close()
            context.assets.open(LABELS_ASSET).close()
            true
        }.getOrDefault(false)
    }
}
