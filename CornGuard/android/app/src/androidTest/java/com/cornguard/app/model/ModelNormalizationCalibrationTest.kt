package com.cornguard.app.model

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Determines which pixel [InputNormalization] the bundled model was trained with. The model graph
 * has no scaling layer and the training script isn't in the repo, so this runs the real model on
 * labelled leaf images under every mode and checks that `assets/model_config.json` names the mode
 * that classifies them best.
 *
 * Put a few real images per class (from the training dataset) in the test APK's assets:
 * `.jpg` files in `app/src/androidTest/assets/calibration/<disease_code>/`, where `<disease_code>` is one of
 * [DiseaseCode.ALL]. Skipped when no images are present. The per-mode results are logged under the
 * `ModelCalibration` tag (`adb logcat -s ModelCalibration`) and included in any failure message.
 */
@RunWith(AndroidJUnit4::class)
class ModelNormalizationCalibrationTest {

    private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val testAssets = InstrumentationRegistry.getInstrumentation().context.assets

    private data class Sample(val expectedCode: String, val path: String)

    private data class ModeScore(val mode: InputNormalization, val correct: Int, val total: Int, val meanConfidence: Float) {
        val accuracy get() = correct.toFloat() / total
        override fun toString() =
            "%-17s accuracy %d/%d (%.1f%%), mean top confidence %.3f"
                .format(mode.configValue, correct, total, accuracy * 100, meanConfidence)
    }

    @Test
    fun configuredNormalization_classifiesLabelledImagesBest() {
        val samples = loadSamples()
        assumeTrue("No calibration images under androidTest/assets/calibration/ — skipping", samples.isNotEmpty())

        val scores = InputNormalization.entries.map { score(it, samples) }
        val report = scores.joinToString("\n")
        Log.i(TAG, "Calibration over ${samples.size} images:\n$report")

        val best = scores.maxWith(compareBy<ModeScore> { it.accuracy }.thenBy { it.meanConfidence })
        assertEquals(
            "model_config.json should use the best-scoring normalization.\n$report\n",
            best.mode,
            configuredNormalization()
        )
    }

    private fun score(mode: InputNormalization, samples: List<Sample>): ModeScore {
        val classifier = TfliteCornLeafClassifier(targetContext, normalizationOverride = mode)
        try {
            var correct = 0
            var confidenceSum = 0f
            for (sample in samples) {
                val bitmap = testAssets.open(sample.path).use { BitmapFactory.decodeStream(it) }
                    ?: error("Could not decode ${sample.path}")
                val result = classifier.classify(bitmap)
                if (result.diseaseCode == sample.expectedCode) correct++
                confidenceSum += result.confidence
            }
            return ModeScore(mode, correct, samples.size, confidenceSum / samples.size)
        } finally {
            classifier.close()
        }
    }

    private fun loadSamples(): List<Sample> = DiseaseCode.ALL.flatMap { code ->
        val dir = "$CALIBRATION_DIR/$code"
        (testAssets.list(dir) ?: emptyArray())
            .filter { it.substringAfterLast('.').lowercase() in IMAGE_EXTENSIONS }
            .map { Sample(code, "$dir/$it") }
    }

    private fun configuredNormalization(): InputNormalization {
        val json = targetContext.assets.open("model_config.json").bufferedReader().use { it.readText() }
        return InputNormalization.fromConfigValue(JSONObject(json).getString("normalization"))
    }

    private companion object {
        const val TAG = "ModelCalibration"
        const val CALIBRATION_DIR = "calibration"
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png")
    }
}
