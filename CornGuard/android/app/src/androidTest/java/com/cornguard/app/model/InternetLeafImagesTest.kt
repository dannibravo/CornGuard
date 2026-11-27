package com.cornguard.app.model

import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cornguard.app.di.ServiceLocator
import com.cornguard.app.ui.scan.ScanImageDecoder
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/**
 * Reporting test (not an accuracy gate): runs the Scan screen's exact path — [ScanImageDecoder],
 * then [ServiceLocator.cornGate], then [ServiceLocator.cornLeafClassifier] — on labelled photos
 * pushed to the app's external files dir:
 *
 *   adb push <dir>/. /sdcard/Android/data/com.cornguard.app.debug/files/leaftest/
 *
 * File names are `<disease_code>__<set>__<id>.jpg` (set = field | lab). Read the report with
 * `adb logcat -s LeafTest`.
 */
@RunWith(AndroidJUnit4::class)
class InternetLeafImagesTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun reportGateAndClassifierOnLabelledPhotos() {
        val dir = File(context.getExternalFilesDir(null), "leaftest")
        val files = dir.listFiles { f -> f.extension.lowercase() in setOf("jpg", "jpeg", "png") }
            ?.sortedBy { it.name }.orEmpty()
        assumeTrue("No photos in $dir — push them first", files.isNotEmpty())

        val gate = ServiceLocator.cornGate
        val classifier = ServiceLocator.cornLeafClassifier
        log("model=${classifier.modelVersion} gate=${gate?.modelVersion ?: "none"} photos=${files.size}")
        log("name | expected | gate corn% | outcome | predicted | conf% | probs NLB/Rust/GLS/Healthy")

        for (file in files) {
            val parts = file.nameWithoutExtension.split("__")
            val expected = parts.getOrNull(0) ?: "?"
            val set = parts.getOrNull(1) ?: "?"
            val bitmap = ScanImageDecoder.decode(context.contentResolver, Uri.fromFile(file))
            if (bitmap == null) {
                log("${file.name} | $expected | DECODE-FAILED")
                continue
            }
            val gateResult = gate?.check(bitmap)
            // The classifier is also run on gate-rejected photos, to see what it would have said.
            val result = classifier.classify(bitmap)
            val outcome = when {
                gateResult != null && !gateResult.isCorn -> "REJECTED-NOT-CORN"
                result.diseaseCode == expected -> "CORRECT"
                else -> "WRONG"
            }
            log(
                String.format(
                    Locale.US, "RESULT|%s|%s|%s|%.1f|%s|%s|%.1f|%s",
                    file.name, set, expected, (gateResult?.cornProbability ?: 1f) * 100, outcome,
                    result.diseaseCode, result.confidence * 100,
                    result.probabilities.joinToString("/") { String.format(Locale.US, "%.2f", it) }
                )
            )
        }
    }

    private fun log(line: String) = Log.i(TAG, line)

    private companion object {
        const val TAG = "LeafTest"
    }
}
