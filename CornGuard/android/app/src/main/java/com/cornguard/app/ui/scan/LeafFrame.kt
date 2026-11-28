package com.cornguard.app.ui.scan

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The square "frame the leaf" box, in image pixel coordinates. Only what is inside it is sent to
 * the disease model: v4 and v5 both call full-frame field photos of healthy leaves diseased, but
 * call the same leaves healthy once cropped to the leaf (ML_V5_FIELD_RETRAINING.md §9.4).
 *
 * Immutable: every edit returns a new frame that stays square, inside the image, and no smaller
 * than [minSide]. Plain Kotlin (no android.graphics) so it is unit-tested on the JVM.
 */
data class LeafFrame(
    val imageWidth: Int,
    val imageHeight: Int,
    val left: Float,
    val top: Float,
    val side: Float,
) {
    val right: Float get() = left + side
    val bottom: Float get() = top + side
    val centerX: Float get() = left + side / 2f
    val centerY: Float get() = top + side / 2f

    private val maxSide: Float get() = min(imageWidth, imageHeight).toFloat()

    /** Smallest allowed box: [MIN_SIDE_PX], or [MIN_SIDE_FRACTION] of the short edge if larger. */
    val minSide: Float
        get() = min(maxSide, max(MIN_SIDE_PX, maxSide * MIN_SIDE_FRACTION))

    /** Moves the box, stopping at the image edges. */
    fun moveBy(dx: Float, dy: Float): LeafFrame =
        copy(left = left + dx, top = top + dy).clamped()

    /** Resizes around the box centre (pinch), then shifts it back inside the image if needed. */
    fun scaleBy(factor: Float): LeafFrame {
        val newSide = (side * factor).coerceIn(minSide, maxSide)
        return copy(left = centerX - newSide / 2f, top = centerY - newSide / 2f, side = newSide).clamped()
    }

    /**
     * Resizes by dragging one corner while the opposite corner ([anchorX], [anchorY]) stays put.
     * The side follows the larger of the two drag distances, limited so the box stays in the image.
     */
    fun resizeFromAnchor(anchorX: Float, anchorY: Float, pointerX: Float, pointerY: Float): LeafFrame {
        val growsRight = pointerX >= anchorX
        val growsDown = pointerY >= anchorY
        val room = min(
            if (growsRight) imageWidth - anchorX else anchorX,
            if (growsDown) imageHeight - anchorY else anchorY,
        )
        val wanted = max(abs(pointerX - anchorX), abs(pointerY - anchorY))
        val newSide = wanted.coerceAtMost(room).coerceIn(minSide, maxSide)
        val newLeft = if (growsRight) anchorX else anchorX - newSide
        val newTop = if (growsDown) anchorY else anchorY - newSide
        return copy(left = newLeft, top = newTop, side = newSide).clamped()
    }

    /** Integer crop rectangle (left, top, side) that is always valid for Bitmap.createBitmap. */
    fun cropPixels(): Triple<Int, Int, Int> {
        val s = side.roundToInt().coerceIn(1, min(imageWidth, imageHeight))
        val l = left.roundToInt().coerceIn(0, imageWidth - s)
        val t = top.roundToInt().coerceIn(0, imageHeight - s)
        return Triple(l, t, s)
    }

    private fun clamped(): LeafFrame {
        val s = side.coerceIn(minSide, maxSide)
        return copy(
            left = left.coerceIn(0f, imageWidth - s),
            top = top.coerceIn(0f, imageHeight - s),
            side = s,
        )
    }

    companion object {
        /** Below this the 224×224 model input would be mostly upscaling blur. */
        const val MIN_SIDE_PX = 112f
        const val MIN_SIDE_FRACTION = 0.15f

        /**
         * Starting box, centred, as a fraction of the image's short edge. 1.0 is the same square
         * the classifier cropped before this step existed, so tapping Analyze straight away
         * behaves exactly as before.
         */
        const val INITIAL_FRACTION = 1f

        fun centered(imageWidth: Int, imageHeight: Int, fraction: Float = INITIAL_FRACTION): LeafFrame {
            require(imageWidth > 0 && imageHeight > 0) { "Image must not be empty" }
            val short = min(imageWidth, imageHeight).toFloat()
            val side = short * fraction.coerceIn(0f, 1f)
            return LeafFrame(
                imageWidth, imageHeight,
                left = (imageWidth - side) / 2f,
                top = (imageHeight - side) / 2f,
                side = side,
            ).clamped()
        }
    }
}
