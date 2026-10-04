package com.cornguard.app.ui.common

import android.content.res.ColorStateList
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.cornguard.app.R
import com.cornguard.app.model.DiseaseCode

/** Disease badge colours and labels from the caps 3 design (History / Map badges). */
object DiseaseStyle {

    @ColorRes
    fun colorRes(diseaseCode: String): Int = when (diseaseCode) {
        DiseaseCode.HEALTHY -> R.color.disease_healthy
        DiseaseCode.NORTHERN_LEAF_BLIGHT -> R.color.disease_blight
        DiseaseCode.COMMON_RUST -> R.color.disease_rust
        DiseaseCode.GRAY_LEAF_SPOT -> R.color.disease_gray_leaf_spot
        else -> R.color.disease_unknown
    }

    fun label(diseaseCode: String): String = when (diseaseCode) {
        DiseaseCode.HEALTHY -> "Healthy"
        DiseaseCode.NORTHERN_LEAF_BLIGHT -> "Northern Leaf Blight"
        DiseaseCode.COMMON_RUST -> "Common Rust"
        DiseaseCode.GRAY_LEAF_SPOT -> "Gray Leaf Spot"
        "" -> ""
        else -> diseaseCode.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    /** Coloured text on a 12%-opacity tint of the same colour, on a [R.drawable.bg_pill] background. */
    fun applyBadge(view: TextView, diseaseCode: String, text: String = label(diseaseCode)) {
        val color = ContextCompat.getColor(view.context, colorRes(diseaseCode))
        view.text = text
        view.setTextColor(color)
        view.setBackgroundResource(R.drawable.bg_pill)
        view.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 0x20))
    }
}
