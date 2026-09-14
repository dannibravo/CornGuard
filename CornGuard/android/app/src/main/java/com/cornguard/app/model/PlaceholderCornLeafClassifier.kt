package com.cornguard.app.model

import android.graphics.Bitmap

/**
 * Inert [CornLeafClassifier] used until the real TFLite integration lands (Sprint 2). It
 * deliberately never returns a fabricated disease label or confidence — doing so would violate
 * the Model Rule in claude/15_CLAUDE.md ("Never guess normalization, class order, ... model
 * version") and the D-04 decision gate, which blocks final preprocessing until Acenas freezes it.
 *
 * Calling code (the Scan screen) must catch [ModelNotReadyException] and show
 * `R.string.scan_model_not_ready` rather than crash.
 */
class PlaceholderCornLeafClassifier : CornLeafClassifier {

    override val modelVersion: String = "unassigned"

    override fun classify(leafImage: Bitmap): DetectionResult {
        throw ModelNotReadyException(
            "No CornLeafClassifier implementation is wired in yet. Blocked on D-04 " +
                "(model input normalization) and the frozen class-order export from Acenas — " +
                "see claude/03_SOURCE_ALIGNMENT_AND_DECISION_GATES.md and " +
                "claude/09_ML_MODEL_CONTRACT.md."
        )
    }

    override fun close() = Unit
}
