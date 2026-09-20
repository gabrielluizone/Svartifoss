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

    private val groundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val density = Resources.getSystem().displayMetrics.density

    private var centerX = 0f
    private var centerY = 0f

    /** False until a touch has placed the pulse; (0, 0) is the screen corner, not a tap point. */
    private var placed = false

    // The action's own icon, shown inside the ring once the action has actually run - see
    // revealIcon. Its own timeline, deliberately not the ring's: the ring fires on touch-down and
    // this fires when the gesture has been classified, so restarting the ring to carry it would
    // stutter a pulse the user is already watching.
    private var icon: Drawable? = null
    private var iconProgress = 1f
    private var iconAnimator: ValueAnimator? = null

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
        placed = true
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

    /**
     * Show [icon] inside the ring, naming the action that just ran.
     *
     * The ring says "I felt that"; this says "and *this* is what it did". They are separate because
     * the two facts are known at different moments - the touch point on the way down, the action
     * only once the gesture has been classified - and because the second one is the whole point:
     * a quadrant's icon can be switched off, or hidden by the control style, and then nothing on
     * screen ever confirmed which of four actions fired. Sizing and placing it here rather than
     * animating the persistent hint is what makes it independent of whether that hint exists.
     *
     * [icon] must be a drawable this may keep and mutate - callers hand over their own copy, since
     * the hint view is still using the original.
     */
    fun revealIcon(icon: Drawable?) {
        // Nothing has told this where the finger was, so there is no honest place to draw it -
        // (0, 0) would put the glyph in the corner of the screen.
        if (!placed) return
        this.icon = icon ?: return
        iconAnimator?.cancel()
        iconAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ICON_IN_MS + ICON_HOLD_MS + ICON_OUT_MS
            addUpdateListener {
                iconProgress = it.animatedValue as Float
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
            // The ring is done, but the glyph outlives it: it starts when the action fires, which
            // is after the ring began, so returning here would cut the confirmation short.
            drawIcon(canvas)
            return
        }

        val radius = radiusPx * (START_SCALE + (END_SCALE - START_SCALE) * progress)

        fillPaint.color = ColorUtils.setAlphaComponent(accentColor, (alpha * FILL_ALPHA_FRACTION * 255).toInt())
        strokePaint.color = ColorUtils.setAlphaComponent(lightenForStroke(accentColor), (alpha * 255).toInt())

        canvas.drawCircle(centerX, centerY, radius, fillPaint)
        canvas.drawCircle(centerX, centerY, radius, strokePaint)
        drawIcon(canvas)
    }

    /**
     * The action glyph, on its own ground.
     *
     * The ground is not decoration: these icons are monochrome templates, the thing behind them is
     * whatever the album cover happens to be, and a white glyph on a pale sleeve is invisible
     * exactly when the user most needs to read it. The same reasoning the centre-tap confirmation
     * already carries its own backing for.
     */
    private fun drawIcon(canvas: Canvas) {
        val glyph = icon ?: return
        val alpha = iconAlphaAt(iconProgress)
        if (alpha <= 0.01f) return

        val half = ICON_SIZE_DP * density / 2f
        // Settles rather than pops: full size the instant it appears reads as a flicker in the
        // composition rather than as an answer.
        val scale = ICON_START_SCALE + (1f - ICON_START_SCALE) * minOf(1f, iconProgress * 4f)

        groundPaint.color = ColorUtils.setAlphaComponent(
                GROUND_COLOR, (alpha * GROUND_ALPHA_FRACTION * 255).toInt())
        canvas.drawCircle(centerX, centerY, half * scale * GROUND_RADIUS_MULTIPLIER, groundPaint)

        val size = half * scale
        glyph.setBounds(
                (centerX - size).toInt(), (centerY - size).toInt(),
                (centerX + size).toInt(), (centerY + size).toInt())
        glyph.alpha = (alpha * 255).toInt().coerceIn(0, 255)
        glyph.draw(canvas)
    }

    /** Fast in, hold, fade - the envelope the quadrant hint's own flash has always used. */
    private fun iconAlphaAt(progress: Float): Float {
        val total = (ICON_IN_MS + ICON_HOLD_MS + ICON_OUT_MS).toFloat()
        val elapsed = progress * total
        return when {
            elapsed <= ICON_IN_MS -> elapsed / ICON_IN_MS
            elapsed <= ICON_IN_MS + ICON_HOLD_MS -> 1f
            else -> 1f - (elapsed - ICON_IN_MS - ICON_HOLD_MS) / ICON_OUT_MS
        }.coerceIn(0f, 1f)
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

        // The action glyph inside the ring. Sized against the boosted ring it normally appears in
        // (30dp * 1.3 radius), so it reads as filling the pulse rather than floating in it.
        private const val ICON_SIZE_DP = 26f
        private const val ICON_START_SCALE = 0.86f
        private const val ICON_IN_MS = 90L
        private const val ICON_HOLD_MS = 260L
        private const val ICON_OUT_MS = 240L
        private const val GROUND_COLOR = 0xFF000000.toInt()
        private const val GROUND_ALPHA_FRACTION = 0.42f
        private const val GROUND_RADIUS_MULTIPLIER = 1.25f
    }
}
