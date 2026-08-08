package com.svartifoss.snfell.watch.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.SweepGradient
import androidx.core.graphics.ColorUtils
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import com.svartifoss.snfell.common.ColorHarmony
import com.svartifoss.snfell.R
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import com.svartifoss.snfell.common.R as commonR

/**
 * Thin ring drawn around the very edge of the screen showing playback progress.
 *
 * Only the bezel-hugging band (the drawn ring itself, with a bit of touch slop) responds to
 * touch - everything else passes through untouched - so this can sit on top of
 * [FourWayTouchLayout][com.svartifoss.snfell.common.view.FourWayTouchLayout] in the
 * view hierarchy without stealing its quadrant taps/gestures. [excludedTouchViews] additionally
 * carves out exact no-go zones (e.g. the quadrant action icons) that would otherwise overlap
 * with the touch band near the bezel.
 */
/** Visual style of the edge progress/seek ring, selected on the phone
 *  ([MiscPreferences.WEAR_PROGRESS_STYLE][com.svartifoss.snfell.common.MiscPreferences.WEAR_PROGRESS_STYLE]).
 *  New styles are additive cases here plus a branch in [CircularProgressSeekBar.onDraw] -
 *  touch/seek behavior is shared by all of them. */
enum class RingStyle {
    /** Continuous accent arc over a faint track (original look). */
    SOLID,
    /** The arc broken into short dashes, like bezel minute marks. */
    DASHED,
    /** A trail of round dots. */
    DOTS,
    /** Ultra-thin arc, near-invisible track - the discreet option. */
    HAIRLINE,
    /** Comet: the played arc fades in from transparent to full accent at the head, with a
     *  bright head dot. */
    COMET,
    /** Sixty round watch marks, with a larger marker every five positions. */
    WATCH_DOTS_60,
    /** Sixty radial minute/second ticks, with a longer marker every five positions. */
    WATCH_TICKS_60,
    /** Twelve bold rounded marks inspired by the hour indices of an analogue dial. */
    HOUR_SEGMENTS_12;

    companion object {
        fun fromPref(value: String?): RingStyle = when (value) {
            "dashed" -> DASHED
            "dots" -> DOTS
            "hairline" -> HAIRLINE
            "comet" -> COMET
            "watch_dots_60" -> WATCH_DOTS_60
            "watch_ticks_60" -> WATCH_TICKS_60
            "hour_segments_12" -> HOUR_SEGMENTS_12
            else -> SOLID
        }
    }
}

internal fun shouldDrawEdgeProgress(
        seekable: Boolean,
        visualEnabled: Boolean,
        dragging: Boolean
): Boolean = dragging || (seekable && visualEnabled)

class CircularProgressSeekBar : View {
    companion object {
        // Slightly longer than MusicViewModel's tick interval so each animation is still
        // in flight (and gets smoothly redirected) when the next tick's value arrives.
        private const val PROGRESS_ANIMATION_DURATION_MS = 600L

        /** Hue-distance floor (degrees) for [hasVisibleHueSpread] - matches
         *  [ColorHarmony.MIN_DUOTONE_HUE_GAP], the same bar the palette math itself uses to decide
         *  two colors are worth showing as a pair rather than one. */
        private const val MIN_VISIBLE_HUE_SPREAD = ColorHarmony.MIN_DUOTONE_HUE_GAP
    }

    private val foregroundPaint: Paint = Paint()
    private val backgroundPaint: Paint
    private val headDotPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val circleBounds = RectF()

    private var isDragging = false

    /**
     * Whether a touch drag on the ring is in progress. Read by MainActivity to mute rotary input
     * for the duration: on watches with a touch bezel (every non-Classic Galaxy Watch) the very
     * finger motion that seeks along this ring is *also* reported as rotary scroll, so without
     * this an edge-seek drag popped the volume overlay open and stole the gesture.
     */
    val isSeekDragging: Boolean
        get() = isDragging

    private var displayProgress = 0f
    private var progressAnimator: ValueAnimator? = null
    private val touchBand = resources.getDimension(R.dimen.seek_bar_touch_band)

    private val touchLocation = IntArray(2)
    private val excludedViewRect = Rect()

    var seekable: Boolean = false

    /** Whether the progress track/arc is painted. The View deliberately stays present when false
     *  so [touchSeekingEnabled] can expose an invisible bezel scrub target. */
    var drawProgress: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /** Whether the bezel target accepts touch input. Kept independent from [drawProgress] so the
     *  user can choose either an invisible scrubber or a visible display-only ring. */
    var touchSeekingEnabled: Boolean = true

    /** Views (e.g. the quadrant action icons) that should always win over this ring on overlap. */
    var excludedTouchViews: List<View> = emptyList()

    var onSeekFinished: ((Float) -> Unit)? = null

    /** Fired continuously while the ring is being dragged (including the initial touch-down). */
    var onSeekPreview: ((Float) -> Unit)? = null

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : this(context, attrs, defStyleAttr, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) :
            super(context, attrs, defStyleAttr, defStyleRes)

    init {
        foregroundPaint.style = Paint.Style.STROKE
        foregroundPaint.strokeWidth = resources.getDimension(R.dimen.seek_bar_width)
        foregroundPaint.strokeCap = Paint.Cap.ROUND
        foregroundPaint.color = resources.getColor(commonR.color.accent, null)
        foregroundPaint.isAntiAlias = true

        backgroundPaint = Paint(foregroundPaint)
        backgroundPaint.strokeCap = Paint.Cap.BUTT
        backgroundPaint.color = resources.getColor(R.color.glass_surface_border, null)
    }

    /** Tints the progress arc, e.g. with a color extracted from the current album art. */
    var progressColor: Int
        get() = foregroundPaint.color
        set(value) {
            foregroundPaint.color = value
            invalidate()
        }

    private var secondaryColorInt: Int = 0
    private var tertiaryColorInt: Int = 0

    /**
     * Companion colors for a multi-hue [SurfaceColorTreatment][com.svartifoss.snfell.common.SurfaceColorTreatment]
     * (Triadic, Complementary, Analogous, Duotone...). [RingStyle.SOLID] blends them into the arc
     * as a sweep gradient instead of one flat tint, which is what actually makes those treatments
     * visible on the classic face - the ring is its only always-on-screen colored element, unlike
     * the volume overlay or quick panel, which only show a treatment's secondary/tertiary briefly.
     *
     * A treatment whose secondary/tertiary sit close to the primary hue (Normal, Desaturated,
     * Expressive on typical single-hue art, Monochrome) renders exactly as before: see
     * [hasVisibleHueSpread].
     */
    fun setPaletteColors(primary: Int, secondary: Int, tertiary: Int) {
        val changed = foregroundPaint.color != primary ||
                secondaryColorInt != secondary || tertiaryColorInt != tertiary
        foregroundPaint.color = primary
        secondaryColorInt = secondary
        tertiaryColorInt = tertiary
        if (changed) invalidate()
    }

    /**
     * How much of the full circle the round stroke cap covers, as a 0..1 gradient position.
     *
     * The cap is a half-disc of radius `strokeWidth / 2` sitting on the arc's end, so the angle it
     * spans is `atan(halfStroke / ringRadius)` - a couple of degrees on a real ring, which is
     * exactly why the wrong-coloured tip read as a thin mark rather than as a coloured segment.
     */
    private fun capAngleFraction(): Float {
        val radius = minOf(circleBounds.width(), circleBounds.height()) / 2f
        if (radius <= 0f) return 0f
        val halfStroke = foregroundPaint.strokeWidth / 2f
        val degrees = Math.toDegrees(atan2(halfStroke, radius).toDouble()).toFloat()
        return (degrees / 360f).coerceIn(0f, 0.25f)
    }

    /**
     * User's "Gradient progress ring" choice (MiscPreferences.WEAR_PROGRESS_GRADIENT). Off forces
     * the flat primary fill even under a hue-rotating treatment, which is the only way to keep an
     * album-derived palette everywhere else while having a single-colour bar here.
     */
    var gradientEnabled: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /** True when the companion colors carry a genuinely different hue from the primary - below
     *  this, a gradient would just be visual noise over what reads as a solid color anyway. */
    private fun hasVisibleHueSpread(): Boolean {
        val primary = foregroundPaint.color
        return ColorHarmony.hueDistance(primary, secondaryColorInt) >= MIN_VISIBLE_HUE_SPREAD ||
                ColorHarmony.hueDistance(primary, tertiaryColorInt) >= MIN_VISIBLE_HUE_SPREAD
    }

    var ringStyle: RingStyle = RingStyle.SOLID
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /**
     * Multiplier on the ring's stroke width, driven by the "edge" seek layouts. Applies uniformly
     * to every [RingStyle], so thickness stays orthogonal to appearance.
     *
     * Assigning it re-lays-out on purpose: [onMeasure] insets [circleBounds] by half the stroke to
     * keep the ring inside the bezel, so a thicker ring that skipped the re-measure would be
     * clipped by exactly the amount it grew.
     */
    var edgeStrokeScale: Float = 1f
        set(value) {
            val clamped = value.coerceIn(0.3f, 2.5f)
            if (field != clamped) {
                field = clamped
                foregroundPaint.strokeWidth = baseStrokeWidth * clamped
                requestLayout()
                invalidate()
            }
        }

    private val baseStrokeWidth = resources.getDimension(R.dimen.seek_bar_width)

    /**
     * Progress fraction, 0f..1f. Ignored while the user is actively dragging the ring.
     *
     * Animates smoothly towards the new value instead of jumping, since this is normally fed a
     * new value only every [com.svartifoss.snfell.watch.view.MusicViewModel] tick rather
     * than every frame.
     */
    var progress: Float
        get() = displayProgress
        set(value) {
            if (isDragging) return
            animateProgressTo(value.coerceIn(0f, 1f))
        }

    private fun animateProgressTo(target: Float) {
        progressAnimator?.cancel()

        // Every non-trivial change animates - including the backward sweep to zero on a track
        // change. That sweep once got mistaken for UI lag and replaced with an instant snap,
        // but the real lag was main-thread bitmap work elsewhere; the animated reset is the
        // intended look.
        if (kotlin.math.abs(target - displayProgress) < 0.0005f) {
            displayProgress = target
            invalidate()
            return
        }

        progressAnimator = ValueAnimator.ofFloat(displayProgress, target).apply {
            duration = PROGRESS_ANIMATION_DURATION_MS
            interpolator = LinearInterpolator()
            addUpdateListener {
                displayProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val viewSize = min(measuredWidth, measuredHeight).toFloat()

        val circleStroke = foregroundPaint.strokeWidth / 2
        val circleSize = viewSize - foregroundPaint.strokeWidth

        val horizontalMargin = measuredWidth - viewSize
        val verticalMargin = measuredHeight - viewSize

        circleBounds.left = circleStroke + horizontalMargin / 2
        circleBounds.top = circleStroke + verticalMargin / 2
        circleBounds.right = circleBounds.left + circleSize
        circleBounds.bottom = circleBounds.top + circleSize
    }

    private fun markIsPlayed(index: Int, count: Int): Boolean =
            displayProgress > 0f && index.toFloat() / count < displayProgress

    private fun drawWatchDots(canvas: Canvas, baseWidth: Float) {
        val cx = circleBounds.centerX()
        val cy = circleBounds.centerY()
        val orbit = circleBounds.width() / 2f
        headDotPaint.style = Paint.Style.FILL

        repeat(60) { index ->
            val angle = Math.toRadians((index * 6f - 90f).toDouble())
            val major = index % 5 == 0
            headDotPaint.color = if (markIsPlayed(index, 60)) {
                foregroundPaint.color
            } else {
                backgroundPaint.color
            }
            canvas.drawCircle(
                    cx + orbit * cos(angle).toFloat(),
                    cy + orbit * sin(angle).toFloat(),
                    baseWidth * if (major) 0.36f else 0.22f,
                    headDotPaint)
        }
    }

    private fun drawWatchTicks(
            canvas: Canvas,
            baseWidth: Float,
            count: Int,
            emphasizeEvery: Int,
            normalLength: Float,
            emphasizedLength: Float,
            normalWidth: Float,
            emphasizedWidth: Float
    ) {
        val cx = circleBounds.centerX()
        val cy = circleBounds.centerY()
        // The ordinary stroke is centred baseWidth/2 inside the screen. These explicit marks are
        // slightly inset so their rounded caps never get clipped by the physical display edge.
        val outerRadius = circleBounds.width() / 2f + baseWidth * 0.25f
        headDotPaint.style = Paint.Style.STROKE
        headDotPaint.strokeCap = Paint.Cap.ROUND

        repeat(count) { index ->
            val angle = Math.toRadians((index * 360f / count - 90f).toDouble())
            val emphasized = emphasizeEvery > 0 && index % emphasizeEvery == 0
            val length = if (emphasized) emphasizedLength else normalLength
            headDotPaint.strokeWidth = if (emphasized) emphasizedWidth else normalWidth
            headDotPaint.color = if (markIsPlayed(index, count)) {
                foregroundPaint.color
            } else {
                backgroundPaint.color
            }
            val cosAngle = cos(angle).toFloat()
            val sinAngle = sin(angle).toFloat()
            canvas.drawLine(
                    cx + (outerRadius - length) * cosAngle,
                    cy + (outerRadius - length) * sinAngle,
                    cx + outerRadius * cosAngle,
                    cy + outerRadius * sinAngle,
                    headDotPaint)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // A visually hidden ring remains a usable bezel target. Reveal it only for the lifetime of
        // an active drag, giving position feedback without changing the user's resting appearance.
        if (!shouldDrawEdgeProgress(seekable, drawProgress, isDragging)) {
            return
        }

        // The scratch paints are recolored/re-effected per draw; stroke geometry (and therefore
        // onMeasure's bounds and the touch band) stays that of the base solid ring for every
        // style, so seeking feels identical regardless of the visual.
        val baseWidth = baseStrokeWidth * edgeStrokeScale
        foregroundPaint.pathEffect = null
        backgroundPaint.pathEffect = null
        foregroundPaint.shader = null
        foregroundPaint.strokeWidth = baseWidth
        backgroundPaint.strokeWidth = baseWidth
        foregroundPaint.strokeCap = Paint.Cap.ROUND

        val sweep = displayProgress * 360f
        when (ringStyle) {
            RingStyle.SOLID -> {
                canvas.drawArc(circleBounds, 0f, 360f, false, backgroundPaint)
                // A flat single-hue treatment (Normal, Desaturated, Expressive/Monochrome on most
                // covers) falls under the threshold and draws exactly as before - only a treatment
                // that deliberately rotates hue (Triadic, Complementary, Analogous, Duotone) gets
                // the gradient, so this is additive rather than a restyle of the existing look.
                if (sweep > 1f && gradientEnabled && hasVisibleHueSpread()) {
                    // The round cap at the arc's start extends BACKWARDS past the start angle by
                    // half the stroke. In an unshifted sweep gradient those angles sit just below
                    // position 1.0, where the shader clamps to its LAST color - so the tip of the
                    // bar was painted in the gradient's end color, reading as a stray mark of the
                    // wrong hue at 12 o'clock. Shifting the whole gradient forward by the cap's own
                    // angle and holding the primary across that lead-in puts the cap back in the
                    // color the bar actually starts with.
                    val capFraction = capAngleFraction()
                    val start = capFraction
                    val shader = SweepGradient(
                            circleBounds.centerX(), circleBounds.centerY(),
                            intArrayOf(
                                    foregroundPaint.color,
                                    foregroundPaint.color,
                                    secondaryColorInt,
                                    tertiaryColorInt),
                            floatArrayOf(
                                    0f,
                                    start,
                                    (start + sweep / 360f * 0.5f).coerceAtMost(1f),
                                    (start + sweep / 360f).coerceAtMost(1f))
                    )
                    val rotate = Matrix()
                    rotate.setRotate(
                            -90f - capFraction * 360f,
                            circleBounds.centerX(),
                            circleBounds.centerY())
                    shader.setLocalMatrix(rotate)
                    foregroundPaint.shader = shader
                    canvas.drawArc(circleBounds, -90f, sweep, false, foregroundPaint)
                    foregroundPaint.shader = null
                } else {
                    canvas.drawArc(circleBounds, -90f, sweep, false, foregroundPaint)
                }
            }
            RingStyle.DASHED -> {
                val dash = DashPathEffect(floatArrayOf(baseWidth * 1.9f, baseWidth * 1.5f), 0f)
                foregroundPaint.pathEffect = dash
                backgroundPaint.pathEffect = dash
                foregroundPaint.strokeCap = Paint.Cap.BUTT
                canvas.drawArc(circleBounds, -90f, 360f, false, backgroundPaint)
                canvas.drawArc(circleBounds, -90f, sweep, false, foregroundPaint)
            }
            RingStyle.DOTS -> {
                // Zero-length "on" intervals with a round cap render as a trail of dots.
                val dots = DashPathEffect(floatArrayOf(0.01f, baseWidth * 2.6f), 0f)
                foregroundPaint.pathEffect = dots
                backgroundPaint.pathEffect = dots
                backgroundPaint.strokeCap = Paint.Cap.ROUND
                canvas.drawArc(circleBounds, -90f, 360f, false, backgroundPaint)
                canvas.drawArc(circleBounds, -90f, sweep, false, foregroundPaint)
                backgroundPaint.strokeCap = Paint.Cap.BUTT
            }
            RingStyle.HAIRLINE -> {
                foregroundPaint.strokeWidth = baseWidth * 0.45f
                backgroundPaint.strokeWidth = baseWidth * 0.3f
                canvas.drawArc(circleBounds, 0f, 360f, false, backgroundPaint)
                canvas.drawArc(circleBounds, -90f, sweep, false, foregroundPaint)
            }
            RingStyle.COMET -> {
                backgroundPaint.strokeWidth = baseWidth * 0.5f
                canvas.drawArc(circleBounds, 0f, 360f, false, backgroundPaint)
                if (sweep > 1f) {
                    // Sweep gradient from transparent at 12 o'clock to the full accent at the
                    // progress head, shifted past the round cap for the same reason SOLID is: the
                    // cap sits just below shader position 1.0, where an unshifted gradient clamps
                    // to its last stop - here full opacity, so the tail that is meant to fade in
                    // from nothing started with an opaque dot.
                    val accent = foregroundPaint.color
                    val capFraction = capAngleFraction()
                    val shader = SweepGradient(
                            circleBounds.centerX(), circleBounds.centerY(),
                            intArrayOf(
                                    ColorUtils.setAlphaComponent(accent, 0x00),
                                    ColorUtils.setAlphaComponent(accent, 0x00),
                                    ColorUtils.setAlphaComponent(accent, 0xFF)
                            ),
                            floatArrayOf(
                                    0f,
                                    capFraction,
                                    (capFraction + sweep / 360f).coerceAtMost(1f))
                    )
                    val rotate = Matrix()
                    rotate.setRotate(
                            -90f - capFraction * 360f,
                            circleBounds.centerX(),
                            circleBounds.centerY())
                    shader.setLocalMatrix(rotate)
                    foregroundPaint.shader = shader
                    canvas.drawArc(circleBounds, -90f, sweep, false, foregroundPaint)
                    foregroundPaint.shader = null
                }
                // Bright head dot so the current position always reads clearly.
                val headRad = Math.toRadians((sweep - 90f).toDouble())
                canvas.drawCircle(
                        circleBounds.centerX() + circleBounds.width() / 2f * kotlin.math.cos(headRad).toFloat(),
                        circleBounds.centerY() + circleBounds.height() / 2f * kotlin.math.sin(headRad).toFloat(),
                        baseWidth * 0.5f,
                        headDotPaint.apply {
                            style = Paint.Style.FILL
                            color = foregroundPaint.color
                        }
                )
            }
            RingStyle.WATCH_DOTS_60 -> drawWatchDots(canvas, baseWidth)
            RingStyle.WATCH_TICKS_60 -> drawWatchTicks(
                    canvas = canvas,
                    baseWidth = baseWidth,
                    count = 60,
                    emphasizeEvery = 5,
                    normalLength = baseWidth * 0.58f,
                    emphasizedLength = baseWidth * 0.95f,
                    normalWidth = baseWidth * 0.17f,
                    emphasizedWidth = baseWidth * 0.29f)
            RingStyle.HOUR_SEGMENTS_12 -> drawWatchTicks(
                    canvas = canvas,
                    baseWidth = baseWidth,
                    count = 12,
                    emphasizeEvery = 1,
                    normalLength = baseWidth,
                    emphasizedLength = baseWidth,
                    normalWidth = baseWidth * 0.4f,
                    emphasizedWidth = baseWidth * 0.4f)
        }
    }

    private fun isInsideExcludedView(localX: Float, localY: Float): Boolean {
        if (excludedTouchViews.isEmpty()) {
            return false
        }

        getLocationOnScreen(touchLocation)
        val screenX = (touchLocation[0] + localX).toInt()
        val screenY = (touchLocation[1] + localY).toInt()

        return excludedTouchViews.any { target ->
            target.visibility == View.VISIBLE &&
                    target.getGlobalVisibleRect(excludedViewRect) &&
                    excludedViewRect.contains(screenX, screenY)
        }
    }

    private fun angleToProgress(dx: Float, dy: Float): Float {
        // Angle measured clockwise from straight up, matching the -90f start used in onDraw.
        val angleFromTop = (Math.toDegrees(atan2(dy, dx).toDouble()) + 90.0 + 360.0) % 360.0
        return max(0f, min(1f, (angleFromTop / 360.0).toFloat()))
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Once a drag is already in progress, see it through even if seekable flips off for a
        // moment (e.g. a stale tick from the phone) - bailing out mid-gesture is what caused the
        // ring to freeze with no way to finish choosing a position.
        if ((!seekable || !touchSeekingEnabled) && !isDragging) {
            return false
        }

        val centerX = circleBounds.centerX()
        val centerY = circleBounds.centerY()
        val radius = circleBounds.width() / 2
        val dx = event.x - centerX
        val dy = event.y - centerY
        val distanceFromCenter = hypot(dx, dy)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (kotlin.math.abs(distanceFromCenter - radius) > touchBand) {
                    return false
                }
                if (isInsideExcludedView(event.x, event.y)) {
                    return false
                }
                // Without this, an animation already in flight from the last MusicViewModel
                // tick keeps overwriting displayProgress on every frame, fighting the drag and
                // making the ring appear to snap back towards wherever it was animating to.
                progressAnimator?.cancel()
                isDragging = true

                // Tells any parent that intercepts edge/bezel touches (e.g. a swipe-to-dismiss
                // container) to back off for the duration of this gesture - without it, a held
                // touch near the bezel could get stolen mid-drag, which is what made the ring
                // appear to freeze and never reach onSeekFinished.
                parent?.requestDisallowInterceptTouchEvent(true)

                displayProgress = angleToProgress(dx, dy)
                invalidate()
                onSeekPreview?.invoke(displayProgress)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) {
                    return false
                }

                // Deliberately NOT re-checking isInsideExcludedView here: the icons sit at the
                // same radius as this ring, so any drag that sweeps past one of the four cardinal
                // points would otherwise get killed mid-gesture - which looked like the ring
                // freezing and snapping back to the live playback position (the next position
                // tick would no longer be held back once isDragging flipped to false). The
                // exclusion only needs to stop a drag from *starting* on top of an icon.
                displayProgress = angleToProgress(dx, dy)
                invalidate()
                onSeekPreview?.invoke(displayProgress)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!isDragging) {
                    return false
                }
                isDragging = false
                invalidate()
                parent?.requestDisallowInterceptTouchEvent(false)
                // A quick tap (no ACTION_MOVE in between) still jumps straight to that position;
                // ACTION_DOWN already emitted its preview so tap and drag share the same feedback.
                onSeekFinished?.invoke(displayProgress)
                return true
            }
            else -> return false
        }
    }
}
