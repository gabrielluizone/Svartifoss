package com.svartifoss.snfell.watch.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import kotlin.math.min

/** Display-only progress geometry used while bezel seeking. The edge ring remains the input
 * target underneath; this view only gives the Timeline and Segmented layouts their own visual
 * composition instead of recolouring the same ring. */
class OverlayProgressMeter @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Mode { TIMELINE, SEGMENTS }

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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val meterWidth = min(width * .68f, 132f * density)
        val meterHeight = if (mode == Mode.SEGMENTS) 9f * density else 7f * density
        val centerY = height / 2f + 25f * density
        bounds.set(
                (width - meterWidth) / 2f,
                centerY - meterHeight / 2f,
                (width + meterWidth) / 2f,
                centerY + meterHeight / 2f)

        when (mode) {
            Mode.TIMELINE -> drawTimeline(canvas, density)
            Mode.SEGMENTS -> drawSegments(canvas, density)
        }
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
}
