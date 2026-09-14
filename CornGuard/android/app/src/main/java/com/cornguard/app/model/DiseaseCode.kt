package com.cornguard.app.model

/**
 * The four confirmed model output classes (claude/01_MASTER_DEVELOPMENT_CONTEXT.md,
 * claude/09_ML_MODEL_CONTRACT.md). These are stable string codes only — they carry no assumed
 * class-index order. Acenas publishes the authoritative index-to-code mapping when the release
 * TFLite model is frozen (claude/04_DEVELOPMENT_RULES.md #7: never guess class order).
 */
object DiseaseCode {
    const val COMMON_RUST = "common_rust"
    const val GRAY_LEAF_SPOT = "gray_leaf_spot"
    const val NORTHERN_LEAF_BLIGHT = "northern_leaf_blight"
    const val HEALTHY = "healthy"

    val ALL = listOf(COMMON_RUST, GRAY_LEAF_SPOT, NORTHERN_LEAF_BLIGHT, HEALTHY)
}
