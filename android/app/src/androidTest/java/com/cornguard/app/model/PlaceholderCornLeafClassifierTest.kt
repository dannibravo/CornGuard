package com.cornguard.app.model

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Confirms the Sprint 0 placeholder never fabricates a disease label (claude/15_CLAUDE.md Model
 * Rule) and instead fails loudly with [ModelNotReadyException] so the Scan screen can show
 * `R.string.scan_model_not_ready`.
 */
@RunWith(AndroidJUnit4::class)
class PlaceholderCornLeafClassifierTest {

    @Test
    fun classify_throwsModelNotReady_untilARealModelIsWired() {
        val classifier = PlaceholderCornLeafClassifier()
        val dummyLeafImage = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)

        assertThrows(ModelNotReadyException::class.java) {
            classifier.classify(dummyLeafImage)
        }
    }
}
