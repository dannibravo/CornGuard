package com.cornguard.app.ui.scan

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.hypot
import kotlin.math.min

/**
 * Shows a scan photo (fit-centre) with a square [LeafFrame] box the farmer moves onto the leaf:
 * drag inside the box to move it, drag a corner to resize, or pinch. The area outside the box is
 * dimmed. [cropToFrame] returns only the boxed pixels, which is what the disease model sees.
 */
class LeafFrameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var bitmap: Bitmap? = null
    var frame: LeafFrame? = null
        private set

    // Image → view mapping (fit-centre).
    private var scale = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    private val density = resources.displayMetrics.density
    private val handleTouchRadius = 32f * density
    private val handleLength = 22f * density

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dimPaint = Paint().apply { color = Color.argb(150, 0, 0, 0) }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 2f * density
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 5f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val viewRect = RectF()

    private enum class Drag { NONE, MOVE, RESIZE }
    private var drag = Drag.NONE
    private var lastX = 0f
    private var lastY = 0f
    private var anchorX = 0f
    private var anchorY = 0f

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            drag = Drag.NONE
            return frame != null
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            update { it.scaleBy(detector.scaleFactor) }
            return true
        }
    })

    /** Shows [image] with the box reset to the default centred square. */
    fun setImage(image: Bitmap) {
        bitmap = image
        frame = LeafFrame.centered(image.width, image.height)
        computeMapping()
        invalidate()
    }

    fun clear() {
        bitmap = null
        frame = null
        invalidate()
    }

    /** The boxed part of the photo, or null if no photo is shown. */
    fun cropToFrame(): Bitmap? {
        val image = bitmap ?: return null
        val (left, top, side) = (frame ?: return null).cropPixels()
        return Bitmap.createBitmap(image, left, top, side, side)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeMapping()
    }

    private fun computeMapping() {
        val image = bitmap ?: return
        if (width == 0 || height == 0) return
        val availW = (width - paddingLeft - paddingRight).toFloat()
        val availH = (height - paddingTop - paddingBottom).toFloat()
        scale = min(availW / image.width, availH / image.height)
        offsetX = paddingLeft + (availW - image.width * scale) / 2f
        offsetY = paddingTop + (availH - image.height * scale) / 2f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val image = bitmap ?: return
        val f = frame ?: return

        viewRect.set(offsetX, offsetY, offsetX + image.width * scale, offsetY + image.height * scale)
        canvas.drawBitmap(image, null, viewRect, bitmapPaint)

        val l = toViewX(f.left)
        val t = toViewY(f.top)
        val r = toViewX(f.right)
        val b = toViewY(f.bottom)
        // Dim everything outside the box (four bands around it).
        canvas.drawRect(viewRect.left, viewRect.top, viewRect.right, t, dimPaint)
        canvas.drawRect(viewRect.left, b, viewRect.right, viewRect.bottom, dimPaint)
        canvas.drawRect(viewRect.left, t, l, b, dimPaint)
        canvas.drawRect(r, t, viewRect.right, b, dimPaint)

        canvas.drawRect(l, t, r, b, borderPaint)
        val h = min(handleLength, (r - l) / 3f)
        drawCorner(canvas, l, t, h, h)
        drawCorner(canvas, r, t, -h, h)
        drawCorner(canvas, l, b, h, -h)
        drawCorner(canvas, r, b, -h, -h)
    }

    private fun drawCorner(canvas: Canvas, x: Float, y: Float, dx: Float, dy: Float) {
        canvas.drawLine(x, y, x + dx, y, handlePaint)
        canvas.drawLine(x, y, x, y + dy, handlePaint)
    }

    @SuppressLint("ClickableViewAccessibility") // Gesture-only box; tapping Analyze with the default box needs no gesture.
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val f = frame ?: return false
        scaleDetector.onTouchEvent(event)
        if (scaleDetector.isInProgress || event.pointerCount > 1) return true

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                lastX = event.x
                lastY = event.y
                val corners = listOf(
                    Triple(f.left, f.top, f.right to f.bottom),
                    Triple(f.right, f.top, f.left to f.bottom),
                    Triple(f.left, f.bottom, f.right to f.top),
                    Triple(f.right, f.bottom, f.left to f.top),
                )
                val hit = corners.minByOrNull { (x, y, _) -> hypot(toViewX(x) - event.x, toViewY(y) - event.y) }
                    ?.takeIf { (x, y, _) -> hypot(toViewX(x) - event.x, toViewY(y) - event.y) <= handleTouchRadius }
                drag = if (hit != null) {
                    anchorX = hit.third.first
                    anchorY = hit.third.second
                    Drag.RESIZE
                } else {
                    Drag.MOVE
                }
            }
            MotionEvent.ACTION_MOVE -> {
                when (drag) {
                    Drag.MOVE -> update { it.moveBy((event.x - lastX) / scale, (event.y - lastY) / scale) }
                    Drag.RESIZE -> update { it.resizeFromAnchor(anchorX, anchorY, toImageX(event.x), toImageY(event.y)) }
                    Drag.NONE -> Unit
                }
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                drag = Drag.NONE
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    private inline fun update(change: (LeafFrame) -> LeafFrame) {
        frame = frame?.let(change)
        invalidate()
    }

    private fun toViewX(imageX: Float) = offsetX + imageX * scale
    private fun toViewY(imageY: Float) = offsetY + imageY * scale
    private fun toImageX(viewX: Float) = (viewX - offsetX) / scale
    private fun toImageY(viewY: Float) = (viewY - offsetY) / scale
}
