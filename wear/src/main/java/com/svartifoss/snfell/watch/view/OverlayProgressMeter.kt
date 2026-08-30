package com.svartifoss.snfell.watch.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Display-only progress geometry used while bezel seeking. The edge ring remains the input
 * target underneath; this view only gives the Timeline and Segmented layouts their own visual
 * composition instead of recolouring the same ring. */
class OverlayProgressMeter @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Mode {
        TIMELINE,
        SEGMENTS,
        TIMELINE_TOP,
        TIMELINE_BOTTOM,
        SEGMENTS_TOP,
        VERTICAL_LEFT,
        VERTICAL_RIGHT,
        DIAL,
        TWIN
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()

    var mode: Mode = Mode.TIMELINE
        set(value) {
            field = value
            invalidate()
        }

    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var accentColor: Int = Color.WHITE
        set(value) {
            field = value
            invalidate()
        }

    var secondaryColor: Int = Color.WHITE
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        when (mode) {
            Mode.TIMELINE -> {
                setHorizontalBounds(density, height / 2f + 25f * density, segmented = false)
                drawTimeline(canvas, density)
            }
            Mode.SEGMENTS -> {
                setHorizontalBounds(density, height / 2f + 25f * density, segmented = true)
                drawSegments(canvas, density)
            }
            Mode.TIMELINE_TOP -> {
                setHorizontalBounds(density, height * .27f, segmented = false)
                drawTimeline(canvas, density)
            }
            Mode.TIMELINE_BOTTOM -> {
                setHorizontalBounds(density, height * .73f, segmented = false)
                drawTimeline(canvas, density)
            }
            Mode.SEGMENTS_TOP -> {
                setHorizontalBounds(density, height * .27f, segmented = true)
                drawSegments(canvas, density)
            }
            Mode.VERTICAL_LEFT -> drawVerticalTimeline(canvas, density, left = true)
            Mode.VERTICAL_RIGHT -> drawVerticalTimeline(canvas, density, left = false)
            Mode.DIAL -> drawDial(canvas, density)
            Mode.TWIN -> drawTwinTimeline(canvas, density)
        }
    }

    private fun setHorizontalBounds(density: Float, centerY: Float, segmented: Boolean) {
        val meterWidth = min(width * .68f, 132f * density)
        val meterHeight = (if (segmented) 9f else 7f) * density
        bounds.set(
                (width - meterWidth) / 2f,
                centerY - meterHeight / 2f,
                (width + meterWidth) / 2f,
                centerY + meterHeight / 2f)
    }

    private fun drawTimeline(canvas: Canvas, density: Float) {
        val radius = bounds.height() / 2f
        paint.style = Paint.Style.FILL
        paint.color = 0x38FFFFFF
        canvas.drawRoundRect(bounds, radius, radius, paint)
        if (progress > 0f) {
            val fill = RectF(bounds.left, bounds.top,
                    bounds.left + bounds.width() * progress, bounds.bottom)
            paint.color = accentColor
            canvas.drawRoundRect(fill, radius, radius, paint)
        }
        val thumbX = bounds.left + bounds.width() * progress
        paint.color = Color.WHITE
        canvas.drawCircle(thumbX, bounds.centerY(), 4.5f * density, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = density
        paint.color = ColorUtils.setAlphaComponent(accentColor, 0xD8)
        canvas.drawCircle(thumbX, bounds.centerY(), 6f * density, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawSegments(canvas: Canvas, density: Float) {
        val count = 12
        val gap = 3f * density
        val segmentWidth = (bounds.width() - gap * (count - 1)) / count
        repeat(count) { index ->
            val left = bounds.left + index * (segmentWidth + gap)
            val segment = RectF(left, bounds.top, left + segmentWidth, bounds.bottom)
            paint.color = if ((index + 1f) / count <= progress + .001f) {
                accentColor
            } else {
                0x32FFFFFF
            }
            canvas.drawRoundRect(segment, segmentWidth / 2f, segmentWidth / 2f, paint)
        }
    }

    private fun drawVerticalTimeline(canvas: Canvas, density: Float, left: Boolean) {
        val meterHeight = min(height * .55f, 118f * density)
        val meterWidth = 7f * density
        val centerX = if (left) width * .22f else width * .78f
        bounds.set(
                centerX - meterWidth / 2f,
                (height - meterHeight) / 2f,
                centerX + meterWidth / 2f,
                (height + meterHeight) / 2f)
        val radius = meterWidth / 2f
        paint.style = Paint.Style.FILL
        paint.color = 0x38FFFFFF
        canvas.drawRoundRect(bounds, radius, radius, paint)
        val fillTop = bounds.bottom - bounds.height() * progress
        if (progress > 0f) {
            paint.color = accentColor
            canvas.drawRoundRect(
                    RectF(bounds.left, fillTop, bounds.right, bounds.bottom),
                    radius, radius, paint)
        }
        paint.color = Color.WHITE
        canvas.drawCircle(centerX, fillTop, 4.5f * density, paint)
    }

    private fun drawDial(canvas: Canvas, density: Float) {
        val radius = min(width, height) * .20f
        val cx = width / 2f
        val cy = height / 2f + 22f * density
        bounds.set(cx - radius, cy - radius, cx + radius, cy + radius)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 7f * density
        paint.color = 0x38FFFFFF
        canvas.drawArc(bounds, 135f, 270f, false, paint)
        paint.color = accentColor
        canvas.drawArc(bounds, 135f, 270f * progress, false, paint)
        val angle = Math.toRadians((135f + 270f * progress).toDouble())
        val thumbX = cx + cos(angle).toFloat() * radius
        val thumbY = cy + sin(angle).toFloat() * radius
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawCircle(thumbX, thumbY, 4.5f * density, paint)
        paint.strokeCap = Paint.Cap.BUTT
    }

    private fun drawTwinTimeline(canvas: Canvas, density: Float) {
        val meterWidth = min(width * .68f, 132f * density)
        val meterHeight = 7f * density
        val left = (width - meterWidth) / 2f
        val right = (width + meterWidth) / 2f
        listOf(height * .32f, height * .68f).forEachIndexed { index, centerY ->
            bounds.set(
                    left,
                    centerY - meterHeight / 2f,
                    right,
                    centerY + meterHeight / 2f)
            val radius = meterHeight / 2f
            paint.style = Paint.Style.FILL
            paint.color = 0x38FFFFFF
            canvas.drawRoundRect(bounds, radius, radius, paint)

            val fraction = if (index == 0) progress else 1f - progress
            if (fraction > 0f) {
                val fill = if (index == 0) {
                    RectF(left, bounds.top, left + meterWidth * fraction, bounds.bottom)
                } else {
                    RectF(right - meterWidth * fraction, bounds.top, right, bounds.bottom)
                }
                paint.color = if (index == 0) accentColor else secondaryColor
                canvas.drawRoundRect(fill, radius, radius, paint)
            }
        }
    }
}
