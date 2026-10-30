package com.cornguard.app.model

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
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
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/**
 * Runs the bundled model on the leaf photos in the device's `Pictures/CornGuard/<Disease>/` folders
 * (Blight, Common_Rust, Gray_Leaf_Spot, Healthy) — the same decode ([ScanImageDecoder]) and classify
 * path the Scan screen uses — and COPIES every image the model scores below [THRESHOLD] confidence
 * into `Pictures/CornGuard_Below60/<Disease>_Below60/`. Originals are only read, never changed.
 *
 * Re-runnable: copies from a previous run are deleted first. Writes `all_results.csv` and
 * `below60.csv` to the app's internal files dir (read them with
 * `adb exec-out run-as com.cornguard.app.debug cat files/below60.csv`) and logs under the
 * `LowConfidenceSort` tag. Run it with `adb shell am instrument` (connectedAndroidTest uninstalls
 * the app and wipes its scans).
 */
@RunWith(AndroidJUnit4::class)
class LowConfidenceImageSortTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val resolver = context.contentResolver
    private val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    private data class LeafImage(val name: String, val folder: String, val uri: Uri, val mimeType: String)

    private data class Result(val image: LeafImage, val expected: String, val detection: DetectionResult) {
        val below = detection.confidence < THRESHOLD
        val correct = detection.diseaseCode == expected
    }

    @Test
    fun copyImagesBelow60PercentToSeparateFolder() {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.READ_EXTERNAL_STORAGE)
        val classifier = ServiceLocator.cornLeafClassifier
        assertTrue("App is not using the TFLite classifier", classifier is TfliteCornLeafClassifier)
        assertEquals("Unexpected model version", EXPECTED_MODEL_VERSION, classifier.modelVersion)

        val images = findImages("${SOURCE_ROOT}%")
        assumeTrue("No images in $SOURCE_ROOT<Disease>/ — push them first", images.isNotEmpty())
        val originalCounts = images.groupingBy { it.folder }.eachCount()

        val results = images.map { image ->
            val expected = FOLDER_TO_CODE[image.folder] ?: error("Unknown disease folder ${image.folder}")
            val bitmap = ScanImageDecoder.decode(resolver, image.uri) ?: error("Could not decode ${image.name}")
            Result(image, expected, classifier.classify(bitmap))
        }

        val removed = deletePreviousCopies()
        val below = results.filter { it.below }
        below.forEach { copyToTarget(it.image) }

        writeReports(results, below)
        log("Model ${classifier.modelVersion}; ${results.size} images; threshold ${(THRESHOLD * 100).toInt()}%; removed $removed old copies")
        for ((folder, count) in originalCounts.toSortedMap()) {
            val folderBelow = below.filter { it.image.folder == folder }
            log(String.format(Locale.US, "%-15s %2d/%d below 60%% (%d of them misclassified)",
                folder, folderBelow.size, count, folderBelow.count { !it.correct }))
        }

        // Every below-60% image was copied, and the originals are all still in place.
        assertEquals(below.size, findImages("$TARGET_ROOT%").size)
        assertEquals(originalCounts, findImages("${SOURCE_ROOT}%").groupingBy { it.folder }.eachCount())
    }

    /** Images whose RELATIVE_PATH matches [pathPattern] (SQL LIKE), with the folder below the root. */
    private fun findImages(pathPattern: String): List<LeafImage> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH, MediaStore.Images.Media.MIME_TYPE
        )
        val images = mutableListOf<LeafImage>()
        resolver.query(
            collection, projection, "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?", arrayOf(pathPattern),
            "${MediaStore.Images.Media.RELATIVE_PATH} ASC, ${MediaStore.Images.Media.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            while (cursor.moveToNext()) {
                // e.g. "Pictures/CornGuard/Blight/" -> "Blight"
                val folder = cursor.getString(pathCol).trimEnd('/').substringAfterLast('/')
                images += LeafImage(
                    name = cursor.getString(nameCol),
                    folder = folder,
                    uri = ContentUris.withAppendedId(collection, cursor.getLong(idCol)),
                    mimeType = cursor.getString(mimeCol) ?: "image/jpeg"
                )
            }
        }
        return images
    }

    /** Deletes this app's copies from an earlier run so re-runs don't duplicate them. */
    private fun deletePreviousCopies(): Int = findImages("$TARGET_ROOT%").sumOf { resolver.delete(it.uri, null, null) }

    private fun copyToTarget(image: LeafImage) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, image.name)
            put(MediaStore.Images.Media.MIME_TYPE, image.mimeType)
            // Galleries and the picker list folders by their own name, so suffix it to tell the
            // copies apart from the originals ("Blight_Below60" vs "Blight").
            put(MediaStore.Images.Media.RELATIVE_PATH, "$TARGET_ROOT${image.folder}$TARGET_SUFFIX/")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val copy = resolver.insert(collection, values) ?: error("Could not create a copy of ${image.name}")
        resolver.openInputStream(image.uri).use { input ->
            resolver.openOutputStream(copy).use { output ->
                requireNotNull(input).copyTo(requireNotNull(output))
            }
        }
        resolver.update(copy, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
    }

    private fun writeReports(results: List<Result>, below: List<Result>) {
        val header = "folder,file,expected,predicted,confidence_pct,result," +
            DiseaseOrder.joinToString(",") { "p_$it" }
        fun row(r: Result) = listOf(
            r.image.folder, "\"${r.image.name}\"", r.expected, r.detection.diseaseCode,
            String.format(Locale.US, "%.1f", r.detection.confidence * 100),
            if (r.correct) "CORRECT" else "WRONG"
        ).plus(r.detection.probabilities.map { String.format(Locale.US, "%.1f", it * 100) }).joinToString(",")

        // App-internal storage: Android 11 blocks adb from Android/data, but `run-as` can read this.
        val dir = context.filesDir
        File(dir, "all_results.csv").writeText((listOf(header) + results.map(::row)).joinToString("\n") + "\n")
        File(dir, "below60.csv").writeText((listOf(header) + below.map(::row)).joinToString("\n") + "\n")
        below.forEach { log("BELOW 60%: ${row(it)}") }
    }

    private fun log(line: String) = Log.i(TAG, line)

    private companion object {
        const val TAG = "LowConfidenceSort"
        const val THRESHOLD = 0.60f
        const val SOURCE_ROOT = "Pictures/CornGuard/"
        const val TARGET_ROOT = "Pictures/CornGuard_Below60/"
        const val TARGET_SUFFIX = "_Below60"
        const val EXPECTED_MODEL_VERSION = "cornguard_mobilenetv2_v3"

        /** The dataset folder names; the model's "Blight" class is Northern Leaf Blight. */
        val FOLDER_TO_CODE = mapOf(
            "Blight" to DiseaseCode.NORTHERN_LEAF_BLIGHT,
            "Common_Rust" to DiseaseCode.COMMON_RUST,
            "Gray_Leaf_Spot" to DiseaseCode.GRAY_LEAF_SPOT,
            "Healthy" to DiseaseCode.HEALTHY
        )

        /** Model output order, as in assets/labels.json. */
        val DiseaseOrder = listOf("northern_leaf_blight", "common_rust", "gray_leaf_spot", "healthy")
    }
}
