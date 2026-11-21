package com.cornguard.app.model

import android.content.res.AssetManager
import android.graphics.Bitmap
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Input handling shared by every bundled TFLite model ([TfliteCornLeafClassifier] and
 * [TfliteCornGate]). Both are exported from ml/model.py's MobileNetV2 graph, so both take the
 * same input: 1x224x224x3 float32, raw RGB in [0, 255].
 */
internal object TfliteImageInput {

    const val INPUT_SIZE = 224
    private const val CHANNELS = 3

    /**
     * Resizes to [INPUT_SIZE] and feeds raw [0, 255] pixel values with no manual normalization.
     * The exported graph's first op (MobileNetV2Preprocess) already applies (value / 127.5) - 1.
     * Normalizing again here double-applies the transform and crushes every image toward a
     * near-constant input. That was a real bug: the app called almost everything "Healthy" at
     * about 75-80% confidence (found with ml/diagnose_field_report.py against the Keras reference).
     */
    fun toInputBuffer(image: Bitmap): ByteBuffer {
        val resized = Bitmap.createScaledBitmap(image, INPUT_SIZE, INPUT_SIZE, true)
        val buffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * CHANNELS)
        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        for (pixel in pixels) {
            buffer.putFloat(((pixel shr 16) and 0xFF).toFloat())
            buffer.putFloat(((pixel shr 8) and 0xFF).toFloat())
            buffer.putFloat((pixel and 0xFF).toFloat())
        }
        buffer.rewind()
        return buffer
    }

    /** Memory-maps a model straight out of the APK (the build keeps .tflite files uncompressed). */
    fun loadModelFile(assets: AssetManager, assetName: String): MappedByteBuffer {
        val descriptor = assets.openFd(assetName)
        FileInputStream(descriptor.fileDescriptor).use { input ->
            return input.channel.map(
                FileChannel.MapMode.READ_ONLY,
                descriptor.startOffset,
                descriptor.declaredLength
            )
        }
    }
}
