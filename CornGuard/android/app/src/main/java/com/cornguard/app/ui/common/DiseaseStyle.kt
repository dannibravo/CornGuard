package com.cornguard.app.ui.common

import android.content.res.ColorStateList
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.cornguard.app.R
import com.cornguard.app.model.DiseaseCode

/**
 * Disease badge colours and labels (History / Map / Community badges). Colours are the "earth"
 * disease identity tokens (values/colors.xml); the badge always carries the name, never colour alone.
 */
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

    /**
     * Coloured text on a 12%-opacity tint of the same colour, on a [R.drawable.bg_pill] background.
     * The text is moved toward the theme's main text colour until it reaches WCAG AA (4.5:1) on the
     * tint, so lighter identity colours (Gray Leaf Spot teal) stay readable in both themes.
     */
    fun applyBadge(view: TextView, diseaseCode: String, text: String = label(diseaseCode)) {
        val context = view.context
        val color = ContextCompat.getColor(context, colorRes(diseaseCode))
        val surface = ContextCompat.getColor(context, R.color.cg_surface)
        val tint = ColorUtils.setAlphaComponent(color, BADGE_TINT_ALPHA)
        view.text = text
        view.setTextColor(
            readableOn(color, ColorUtils.compositeColors(tint, surface), ContextCompat.getColor(context, R.color.cg_text_primary)),
        )
        view.setBackgroundResource(R.drawable.bg_pill)
        view.backgroundTintList = ColorStateList.valueOf(tint)
    }

    /** [color], blended toward [strongest] in small steps until it has at least [minContrast] on [background]. */
    fun readableOn(color: Int, background: Int, strongest: Int, minContrast: Double = 4.5): Int {
        var ratio = 0f
        while (ratio < 1f) {
            val candidate = ColorUtils.blendARGB(color, strongest, ratio)
            if (ColorUtils.calculateContrast(candidate, background) >= minContrast) return candidate
            ratio += 0.05f
        }
        return strongest
    }

    private const val BADGE_TINT_ALPHA = 0x20
}
