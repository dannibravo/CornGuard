package com.cornguard.app.model

import android.Manifest
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cornguard.app.di.ServiceLocator
import com.cornguard.app.ui.scan.ScanImageDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import java.security.MessageDigest
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Runs the bundled model on the leaf photos in the device's `Pictures/CornGuard/` folder — the same
 * decode ([ScanImageDecoder]) and classify ([TfliteCornLeafClassifier]) path the Scan screen uses —
 * and logs a per-image report under the `LeafImageTest` tag. The expected disease comes from the
 * file name (`<disease_code>_<n>.jpg`).
 *
 * This is a reporting test, not a pass/fail accuracy gate: it only fails if no images are found or
 * the model can't run. Read the results with `adb logcat -s LeafImageTest`.
 */
@RunWith(AndroidJUnit4::class)
class EmulatorLeafImagesTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private data class LeafImage(val name: String, val uri: Uri, val expected: String?)

    @Test
    fun classifyPushedLeafImages() {
        verifyRuntimeModel()

        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.READ_EXTERNAL_STORAGE)
        val images = findImages()
        assumeTrue("No images in Pictures/CornGuard/ — push them first", images.isNotEmpty())

        // The exact classifier instance the app's Scan screen uses.
        val appClassifier = ServiceLocator.cornLeafClassifier as TfliteCornLeafClassifier
        report("APP RUNTIME CLASSIFIER (ServiceLocator.cornLeafClassifier)", appClassifier, images, detailed = true)

        for (mode in InputNormalization.entries) {
            val classifier = TfliteCornLeafClassifier(context, normalizationOverride = mode)
            try {
                report("MODE ${mode.configValue}", classifier, images, detailed = false)
            } finally {
                classifier.close()
            }
        }
    }

    /**
     * Proves which model the installed app runs before any result is reported: the app's own
     * classifier must be the TFLite one (not the placeholder) and the `model.tflite` it loads from
     * the installed APK must be byte-identical to the expected release ([EXPECTED_MODEL_NAME]).
     */
    private fun verifyRuntimeModel() {
        val classifier = ServiceLocator.cornLeafClassifier
        log("===== RUNTIME MODEL CHECK =====")
        log("app package: ${context.packageName}")
        log("ServiceLocator.cornLeafClassifier: ${classifier.javaClass.name}, modelVersion=${classifier.modelVersion}")
        assertTrue("App is not using the TFLite classifier", classifier is TfliteCornLeafClassifier)
        assertEquals("Unexpected model version", EXPECTED_MODEL_VERSION, classifier.modelVersion)

        val bytes = context.assets.open("model.tflite").use { it.readBytes() }
        val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02X".format(it) }
        log("assets/model.tflite in installed APK: ${bytes.size} bytes, SHA-256 $sha256")
        log("expected $EXPECTED_MODEL_NAME: SHA-256 $EXPECTED_MODEL_SHA256")
        assertEquals("Installed model is not $EXPECTED_MODEL_NAME", EXPECTED_MODEL_SHA256, sha256)
        log("MATCH: the app is running $EXPECTED_MODEL_NAME")
    }

    private fun report(title: String, classifier: TfliteCornLeafClassifier, images: List<LeafImage>, detailed: Boolean) {
        var correct = 0
        var labelled = 0
        log("===== $title =====")
        for (image in images) {
            val bitmap = ScanImageDecoder.decode(context.contentResolver, image.uri)
                ?: error("Could not decode ${image.name}")
            val result = classifier.classify(bitmap)
            val probabilities = result.probabilities
            val sorted = probabilities.sortedDescending()
            val margin = sorted[0] - sorted[1]
            val isCorrect = image.expected?.let { it == result.diseaseCode }
            if (isCorrect != null) {
                labelled++
                if (isCorrect) correct++
            }
            val flags = buildList {
                if (result.confidence < UNCERTAIN_CONFIDENCE) add("LOW-CONFIDENCE")
                if (margin < UNCERTAIN_MARGIN) add("CLOSE-SECOND")
                if (isCorrect == false) add("MISCLASSIFIED")
            }
            log(
                String.format(
                    Locale.US,
                    "%-28s expected=%-22s predicted=%-22s conf=%5.1f%% %s %s",
                    image.name, image.expected ?: "?", result.diseaseCode, result.confidence * 100,
                    when (isCorrect) { true -> "CORRECT"; false -> "WRONG"; null -> "-" },
                    flags.joinToString(",")
                )
            )
            if (detailed) {
                log("    probabilities: " + DiseaseOrder.indices.joinToString("  ") { i ->
                    String.format(Locale.US, "%s=%.1f%%", DiseaseOrder[i], probabilities[i] * 100)
                } + String.format(Locale.US, "  (margin %.1f pts, %d ms)", margin * 100, result.inferenceTimeMs))
            }
        }
        log(String.format(Locale.US, "ACCURACY %s: %d/%d (%.1f%%)", title, correct, labelled,
            if (labelled == 0) 0.0 else correct * 100.0 / labelled))
    }

    private fun findImages(): List<LeafImage> {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME)
        val images = mutableListOf<LeafImage>()
        context.contentResolver.query(
            collection,
            projection,
            "${MediaStore.Images.Media.RELATIVE_PATH} = ?",
            arrayOf("Pictures/CornGuard/"),
            "${MediaStore.Images.Media.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameCol)
                images += LeafImage(
                    name = name,
                    uri = ContentUris.withAppendedId(collection, cursor.getLong(idCol)),
                    expected = DiseaseCode.ALL.firstOrNull { name.startsWith(it) }
                )
            }
        }
        return images
    }

    private fun log(line: String) = Log.i(TAG, line)

    private companion object {
        const val TAG = "LeafImageTest"
        const val UNCERTAIN_CONFIDENCE = 0.70f
        const val UNCERTAIN_MARGIN = 0.15f

        /**
         * The model release the app should ship: SHA-256 of `cornguard_v3_model.tflite` from the v2
         * training notebook (`android_assets/model.tflite`). Update both when the model changes.
         * Previous release (caps 3 `cornguard_model.tflite`, v2): 1B568531…E96D26.
         */
        const val EXPECTED_MODEL_NAME = "cornguard_v3_model.tflite"
        const val EXPECTED_MODEL_VERSION = "cornguard_mobilenetv2_v3"
        const val EXPECTED_MODEL_SHA256 = "DFE14EFC796C04729B77DAB416052FA5BB772CA35D516DD7E265A80B50CB47D1"

        /** Model output order, as in assets/labels.json. */
        val DiseaseOrder = listOf("northern_leaf_blight", "common_rust", "gray_leaf_spot", "healthy")
    }
}
