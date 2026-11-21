package com.cornguard.app.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.cornguard.app.R
import com.cornguard.app.model.DiseaseCode

/**
 * The disease glyph from the map design: a surface-coloured disc, a ring in the disease's identity
 * colour and a dark letter (R / G / B). The letter carries the meaning, so colour is never the only cue.
 */
class DiseaseGlyphDrawable(context: Context, diseaseCode: String, sizeDp: Float = 18f) : Drawable() {

    private val density = context.resources.displayMetrics.density
    private val sizePx = (sizeDp * density).toInt()
    private val ringWidth = 2f * density
    private val letter = letterFor(diseaseCode)

    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.cg_surface)
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ringWidth
        color = ContextCompat.getColor(context, DiseaseStyle.colorRes(diseaseCode))
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.cg_text_primary)
        textAlign = Paint.Align.CENTER
        textSize = sizePx * 0.56f
        typeface = runCatching { ResourcesCompat.getFont(context, R.font.inter_bold) }.getOrNull()
        isFakeBoldText = typeface == null
    }
    private val textBounds = Rect()

    override fun getIntrinsicWidth() = sizePx
    override fun getIntrinsicHeight() = sizePx

    override fun draw(canvas: Canvas) {
        val b = bounds
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        val radius = minOf(b.width(), b.height()) / 2f
        canvas.drawCircle(cx, cy, radius, discPaint)
        canvas.drawCircle(cx, cy, radius - ringWidth / 2f, ringPaint)
        textPaint.getTextBounds(letter, 0, letter.length, textBounds)
        canvas.drawText(letter, cx, cy + textBounds.height() / 2f, textPaint)
    }

    override fun setAlpha(alpha: Int) {
        discPaint.alpha = alpha
        ringPaint.alpha = alpha
        textPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        // A tint would erase the identity colour; glyphs are never tinted.
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    companion object {
        fun letterFor(diseaseCode: String) = when (diseaseCode) {
            DiseaseCode.COMMON_RUST -> "R"
            DiseaseCode.GRAY_LEAF_SPOT -> "G"
            DiseaseCode.NORTHERN_LEAF_BLIGHT -> "B"
            DiseaseCode.HEALTHY -> "H"
            else -> "?"
        }
    }
}
