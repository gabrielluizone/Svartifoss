package com.svartifoss.snfell.common.view

import android.animation.ValueAnimator
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.graphics.ColorUtils

/**
 * Tap feedback for [FourWayTouchLayout]: a soft ring (faint fill + brighter outline) that
 * expands and fades at the touch point - a deliberate replica of the center tap-zone's pulse
 * (center_tap_pulse.xml animated by MainActivity.pulseCenterTapFeedback), so a quadrant tap and
 * a center tap read as the same visual language. Differences are intentional: it appears at the
 * finger (not screen center), uses the live [accentColor] extracted from the album art instead
 * of fixed white, and runs a bit larger/slower so it stays perceptible in peripheral vision.
 *
 * Replaces two earlier attempts (a Material ripple clipped to the quadrant's triangle, then a
 * flash+ring "shockwave") that both read as either dated or jarring - the triangle clipping in
 * particular cut the ring against the quadrant borders, which looked broken rather than designed.
 */
class TapPulseDrawable(accentColor: Int) : Drawable() {

    var accentColor: Int = accentColor
        set(value) {
            field = value
            invalidateSelf()
        }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * Resources.getSystem().displayMetrics.density
    }

    private val density = Resources.getSystem().displayMetrics.density

    private var centerX = 0f
    private var centerY = 0f

    // 1f = finished/invisible (also the initial state); the timeline runs 0 -> 1 once per tap.
    private var progress = 1f
    private var animator: ValueAnimator? = null

    // Per-press envelope, set in press() - boosted stretches all three so the same drawable can
    // stand in for a face's missing quadrant icon (see MainActivity.onTouchDown) rather than just
    // acknowledging a touch point.
    private var radiusPx = BASE_RADIUS_DP * density
    private var peakAlpha = PEAK_ALPHA

    /** Starts (or restarts) the pulse at [x], [y] - called on touch-down. Like the center
     *  pulse, it always plays its full fixed timeline regardless of when the finger lifts.
     *  [boosted] plays a bigger, brighter, longer pulse - used on faces with no persistent
     *  quadrant icon to flash, so the ripple alone has to carry that confirmation. */
    fun press(x: Float, y: Float, boosted: Boolean = false) {
        centerX = x
        centerY = y
        radiusPx = BASE_RADIUS_DP * density * (if (boosted) BOOST_RADIUS_MULTIPLIER else 1f)
        peakAlpha = if (boosted) BOOST_PEAK_ALPHA else PEAK_ALPHA

        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (boosted) BOOST_DURATION_MS else DURATION_MS
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidateSelf()
            }
            start()
        }
    }

    /** No-op - the pulse is one self-contained timeline (see [press]). Kept so callers reacting
     *  to touch-up don't need to special-case this drawable. */
    fun release() = Unit

    override fun draw(canvas: Canvas) {
        // Same envelope as the center pulse: alpha 0.9 -> 0 across the whole run while the ring
        // scales up, so it's brightest at the moment of impact and dissolves as it grows.
        val alpha = (1f - progress) * peakAlpha
        if (alpha <= 0.01f) {
            return
        }

        val radius = radiusPx * (START_SCALE + (END_SCALE - START_SCALE) * progress)

        fillPaint.color = ColorUtils.setAlphaComponent(accentColor, (alpha * FILL_ALPHA_FRACTION * 255).toInt())
        strokePaint.color = ColorUtils.setAlphaComponent(lightenForStroke(accentColor), (alpha * 255).toInt())

        canvas.drawCircle(centerX, centerY, radius, fillPaint)
        canvas.drawCircle(centerX, centerY, radius, strokePaint)
    }

    /** The outline pops brighter than the fill (mirroring the white-on-translucent look of
     *  center_tap_pulse.xml) - raising lightness keeps that contrast even for dark accents. */
    private fun lightenForStroke(color: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[2] = hsl[2].coerceAtLeast(0.72f)
        return ColorUtils.HSLToColor(hsl)
    }

    override fun setAlpha(alpha: Int) {
        // No-op: opacity is driven entirely by the pulse timeline above.
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        strokePaint.colorFilter = colorFilter
    }

    companion object {
        // Center pulse is 52dp scaling 0.6->1.25 over 300ms; this runs a touch larger and slower
        // on purpose - a quadrant tap lands in peripheral vision, where the center pulse's exact
        // parameters were reported as too quick to even notice.
        private const val DURATION_MS = 420L
        private const val BASE_RADIUS_DP = 30f
        private const val START_SCALE = 0.65f
        private const val END_SCALE = 1.4f
        private const val PEAK_ALPHA = 0.9f
        private const val FILL_ALPHA_FRACTION = 0.16f

        // Boosted stand-in for a face's missing quadrant icon (see press()) - bigger, brighter,
        // longer than the plain touch-point acknowledgement.
        private const val BOOST_DURATION_MS = 550L
        private const val BOOST_RADIUS_MULTIPLIER = 1.3f
        private const val BOOST_PEAK_ALPHA = 1f
    }
}
