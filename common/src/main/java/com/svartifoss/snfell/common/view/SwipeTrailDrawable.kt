package com.svartifoss.snfell.common.view

import android.animation.ValueAnimator
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.animation.AccelerateInterpolator
import androidx.core.graphics.ColorUtils

/**
 * Swipe feedback for [FourWayTouchLayout] hosts that have at least one gesture assigned: a fading
 * "comet" trail drawn along the finger's recorded path once a swipe is recognized - confirms the
 * gesture registered, the same spirit as [TapPulseDrawable]'s ring but for a gesture with no icon
 * of its own to flash. [record] silently accumulates the live path while the current touch hasn't
 * been classified yet (a tap or long-press never becomes visible); [reveal] freezes that path and
 * fades it out; [discard] drops the unclassified buffer with nothing ever having been drawn.
 */
class SwipeTrailDrawable(accentColor: Int) : Drawable() {

    var accentColor: Int = accentColor
        set(value) {
            field = value
            invalidateSelf()
        }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val density = Resources.getSystem().displayMetrics.density
    private val minWidthPx = MIN_WIDTH_DP * density
    private val maxWidthPx = MAX_WIDTH_DP * density

    private class Point(val x: Float, val y: Float, val t: Long)

    private val livePoints = ArrayDeque<Point>()
    private var frozenPoints: List<Point> = emptyList()

    // 1f = finished/invisible (also the initial state); a reveal runs the timeline 0 -> 1.
    private var fadeProgress = 1f
    private var animator: ValueAnimator? = null

    /** Appends a live touch point while the current gesture hasn't been classified yet - called
     *  on every ACTION_MOVE. Purely bookkeeping: nothing is drawn until [reveal]. */
    fun record(x: Float, y: Float) {
        val now = System.currentTimeMillis()
        livePoints.addLast(Point(x, y, now))
        while (livePoints.size > MAX_POINTS ||
                (livePoints.isNotEmpty() && now - livePoints.first().t > WINDOW_MS)) {
            livePoints.removeFirst()
        }
    }

    /** Freezes the recorded path and fades it out - called once the gesture is recognized as a
     *  swipe. */
    fun reveal() {
        if (livePoints.size < 2) {
            livePoints.clear()
            return
        }
        frozenPoints = livePoints.toList()
        livePoints.clear()

        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = DURATION_MS
            interpolator = AccelerateInterpolator()
            addUpdateListener {
                fadeProgress = it.animatedValue as Float
                invalidateSelf()
            }
            start()
        }
    }

    /** Drops the unclassified recording with no animation - called when a touch sequence ends
     *  without a swipe being recognized (a tap, long-press, or quadrant gesture). Never touches
     *  an unrelated trail from an earlier gesture that might still be fading. */
    fun discard() {
        livePoints.clear()
    }

    override fun draw(canvas: Canvas) {
        val overallAlpha = 1f - fadeProgress
        if (overallAlpha <= 0.01f || frozenPoints.size < 2) {
            return
        }

        val segments = frozenPoints.size - 1
        for (i in 0 until segments) {
            val from = frozenPoints[i]
            val to = frozenPoints[i + 1]
            val t = (i + 1) / segments.toFloat() // 0 at tail (oldest), 1 at head (newest).
            val segmentAlpha = (MIN_ALPHA_FRACTION + (1f - MIN_ALPHA_FRACTION) * t) * overallAlpha
            strokePaint.strokeWidth = minWidthPx + (maxWidthPx - minWidthPx) * t
            strokePaint.color = ColorUtils.setAlphaComponent(accentColor, (segmentAlpha * 255).toInt())
            canvas.drawLine(from.x, from.y, to.x, to.y, strokePaint)
        }
    }

    override fun setAlpha(alpha: Int) {
        // No-op: opacity is driven entirely by the reveal fade timeline above.
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        strokePaint.colorFilter = colorFilter
    }

    companion object {
        // Recorded window feeding the comet: long enough to capture a full swipe's path, short
        // enough that a finger resting mid-gesture doesn't drag in stale points.
        private const val WINDOW_MS = 250L
        private const val MAX_POINTS = 40
        private const val DURATION_MS = 350L
        private const val MIN_WIDTH_DP = 2f
        private const val MAX_WIDTH_DP = 9f
        private const val MIN_ALPHA_FRACTION = 0.15f
    }
}
