package com.cornguard.app.model

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The bundled model must load in the app's TFLite runtime and produce a valid 4-class result.
 * Catches runtime/op-version mismatches (a model converted with a newer TensorFlow than the
 * bundled interpreter supports) that would otherwise only surface as a failed scan.
 */
@RunWith(AndroidJUnit4::class)
class TfliteCornLeafClassifierTest {

    @Test
    fun bundledModel_loadsAndClassifies() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("model assets missing", TfliteCornLeafClassifier.isAvailable(context))

        val classifier = TfliteCornLeafClassifier(context)
        try {
            val leaf = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(60, 140, 50)) }
            val result = classifier.classify(leaf)

            assertEquals(4, result.probabilities.size)
            assertTrue(result.diseaseCode in DiseaseCode.ALL)
            assertEquals(1f, result.probabilities.sum(), 0.01f)
            assertEquals("cornguard_mobilenetv2_v3", result.modelVersion)
        } finally {
            classifier.close()
        }
    }
}
