package com.cornguard.app.classifier

import java.util.Locale

/** "northern_leaf_blight" -> "Northern Leaf Blight". Shared by any screen that only has a raw disease_code string (no DetectionResult.displayLabel available), e.g. cloud-sourced records. */
fun diseaseCodeToLabel(code: String): String = code
    .replace('_', ' ')
    .split(' ')
    .joinToString(" ") { it.replaceFirstChar { c -> c.titlecase(Locale.getDefault()) } }
