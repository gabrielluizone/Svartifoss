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
import com.svartifoss.snfell.common.SeekMarkerVisibility
import com.svartifoss.snfell.R
import com.svartifoss.snfell.watch.view.panel.PanelReadout
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
    HOUR_SEGMENTS_12,
    /** Two slim concentric progress rails. */
    DOUBLE,
    /** Twenty-four explicit round markers with larger quarter markers. */
    BEADS,
    /** Alternating long dash and compact dot rhythm. */
    DASH_DOT,
    /** Twenty-four radial ticks, with a longer marker at each quarter. */
    TICKS_24,
    /** Played portion only, with no resting track. */
    PROGRESS_ONLY,
    /** Broad translucent accent halo under a crisp progress core. */
    GLOW,
    /** Secondary-color lead followed by a primary-color progress head. */
    DUOTONE,
    /** Fine track plus an analogue radial pointer at the current position. */
    NEEDLE,
    /** Alternates primary and secondary blocks around the bezel. */
    ALTERNATING,
    /** Hairline track with a bright oversized progress head. */
    SPARK;

    companion object {
        fun fromPref(value: String?): RingStyle = when (value) {
            "dashed" -> DASHED
            "dots" -> DOTS
            "hairline" -> HAIRLINE
            "comet" -> COMET
            "watch_dots_60" -> WATCH_DOTS_60
            "watch_ticks_60" -> WATCH_TICKS_60
            "hour_segments_12" -> HOUR_SEGMENTS_12
            "double" -> DOUBLE
            "beads" -> BEADS
            "dash_dot" -> DASH_DOT
            "ticks_24" -> TICKS_24
            "progress_only" -> PROGRESS_ONLY
            "glow" -> GLOW
            "duotone" -> DUOTONE
            "needle" -> NEEDLE
            "alternating" -> ALTERNATING
            "spark" -> SPARK
            else -> SOLID
        }
    }
}

/**
 * Geometry of the progress surface, independent of [RingStyle]. Angles use Canvas' convention
 * (0 degrees at three o'clock, positive clockwise); a negative sweep therefore lets the right
 * arc fill bottom-to-top. Insets are density-independent so the same layout reads consistently
 * across watch sizes.
 */
enum class ProgressRingLayout(
        val startAngle: Float,
        val sweepAngle: Float,
        val radiusInsetDp: Float,
        val strokeScale: Float = 1f,
        val drawsSecondRing: Boolean = false,
        val secondRingInsetDp: Float = 0f
) {
    /** Original full ring at the display edge. */
    EDGE(-90f, 360f, 0f),
    /** Full ring moved just clear of the bezel. */
    INSET(-90f, 360f, 7f),
    /** Compact inner ring that frames the main controls. */
    INNER(-90f, 360f, 17f),
    /** Heavy full ring with geometry-aware clipping protection. */
    BOLD(-90f, 360f, 1f, strokeScale = 1.8f),
    /** Three-quarter ring whose gap is centred at six o'clock. */
    OPEN_BOTTOM(135f, 270f, 1f),
    /** Three-quarter ring whose gap is centred at twelve o'clock. */
    OPEN_TOP(-45f, 270f, 1f),
    /** Left bezel arc, filling from the lower to the upper edge. */
    LEFT_ARC(100f, 160f, 0f),
    /** Mirrored right bezel arc, also filling bottom-to-top. */
    RIGHT_ARC(80f, -160f, 0f),
    /** Three-quarter ring whose gap is centred on the left edge. */
    OPEN_LEFT(45f, 270f, 1f),
    /** Three-quarter ring whose gap is centred on the right edge. */
    OPEN_RIGHT(-135f, 270f, 1f),
    /** Two concentric full rings, the inner rail taking the secondary palette color. */
    DOUBLE(
            -90f,
            360f,
            1f,
            strokeScale = .72f,
            drawsSecondRing = true,
            secondRingInsetDp = 7f);

    companion object {
        fun fromPref(value: String?): ProgressRingLayout = when (value) {
            "inset" -> INSET
            "inner" -> INNER
            "bold" -> BOLD
            "open_bottom" -> OPEN_BOTTOM
            "open_top" -> OPEN_TOP
            "left_arc" -> LEFT_ARC
            "right_arc" -> RIGHT_ARC
            "open_left" -> OPEN_LEFT
            "open_right" -> OPEN_RIGHT
            "double" -> DOUBLE
            else -> EDGE
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

        private const val MARKER_WIDTH_DP = 2.5f
        /** How far the tick overshoots the ring band on each side, as a multiple of the stroke. */
        private const val MARKER_LENGTH_SCALE = 2.4f

        private const val CANCEL_REVEAL_DURATION_MS = 180L

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
    private val secondCircleBounds = RectF()

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

    /**
     * When the position tick is drawn - see [SeekMarkerVisibility].
     *
     * Held here rather than resolved at draw time so the enum is decoded once per preference
     * change instead of once per frame, the same shape [ringStyle] and [ringLayout] use.
     */
    var markerVisibility: SeekMarkerVisibility = SeekMarkerVisibility.DURING_SEEK
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /**
     * Whether music is actually playing, which three of the four [markerVisibility] modes consult.
     *
     * A paused session and true idle both count as not playing, matching how `MainActivity`
     * resolves `wear_track_time_mode` from the same flag.
     */
    var playing: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                if (markerVisibility != SeekMarkerVisibility.DURING_SEEK) invalidate()
            }
        }

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

    /**
     * Fired instead of [onSeekFinished] when the finger is released inside the cancel zone. The
     * track's position is left exactly where it was, so the caller's job is only to put the screen
     * back - see [SeekDragPolicy].
     */
    var onSeekCancelled: (() -> Unit)? = null

    /**
     * Fired when the finger crosses into (true) or back out of (false) the cancel zone, and once
     * more with false when the gesture ends.
     *
     * The affordance lives with the caller rather than being painted here, because the thing it has
     * to replace is the seek time - a sibling View on the player, a composable on the dedicated
     * progress screen - and this View cannot reach either. Drawing its own marker beside that
     * readout was the first attempt and it read as clutter: two things in the middle of the screen
     * saying different things about the same gesture.
     */
    var onCancelArmedChanged: ((Boolean) -> Unit)? = null

    /**
     * Where playback actually was when this drag started.
     *
     * The whole reason the marker exists: [displayProgress] is overwritten by the finger from the
     * first touch, so once a drag is under way nothing on screen says where the track is any more -
     * the user is choosing a destination with no reference point for where they are leaving from.
     */
    private var dragOriginProgress = 0f

    /** Angle-derived position of the finger, kept apart from [displayProgress] because while the
     *  cancel is arming the two deliberately differ. */
    private var fingerProgress = 0f

    private var cancelArmed = false

    /** 0f..1f reveal of the cancel affordance, and the same factor that walks the ring back to
     *  [dragOriginProgress]. */
    private var cancelReveal = 0f
    private var cancelAnimator: ValueAnimator? = null

    private val markerPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

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
    private fun capAngleFraction(bounds: RectF, strokeWidth: Float): Float {
        val radius = minOf(bounds.width(), bounds.height()) / 2f
        if (radius <= 0f) return 0f
        val halfStroke = strokeWidth / 2f
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

    var ringLayout: ProgressRingLayout = ProgressRingLayout.EDGE
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
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
                requestLayout()
                invalidate()
            }
        }

    private val baseStrokeWidth = resources.getDimension(R.dimen.seek_bar_width)

    private fun effectiveStrokeWidth(): Float =
            baseStrokeWidth * edgeStrokeScale * ringLayout.strokeScale

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
            val clamped = value.coerceIn(0f, 1f)
            if (isDragging) {
                // The arc belongs to the finger for the duration of the drag, but the *marker* is
                // where the track actually is - and the track keeps playing while the user decides.
                // Feeding the tick here is what makes it live; it was previously frozen at the
                // value captured on touch-down, which is exactly the moment it stops being true.
                if (dragOriginProgress != clamped) {
                    dragOriginProgress = clamped
                    // While the cancel is armed the arc is being walked back onto the marker, so a
                    // marker that has moved has to take the arc with it or releasing would land on
                    // a position the ring never showed.
                    if (cancelReveal > 0f) {
                        displayProgress = SeekDragPolicy.previewProgress(
                                fingerProgress, dragOriginProgress, cancelReveal)
                    }
                    invalidate()
                }
                return
            }
            animateProgressTo(clamped)
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
        val strokeWidth = effectiveStrokeWidth()
        val density = resources.displayMetrics.density
        val layoutInset = ringLayout.radiusInsetDp * density
        val circleStroke = strokeWidth / 2f + layoutInset
        val circleSize = (viewSize - strokeWidth - layoutInset * 2f).coerceAtLeast(1f)

        val horizontalMargin = measuredWidth - viewSize
        val verticalMargin = measuredHeight - viewSize

        circleBounds.left = circleStroke + horizontalMargin / 2
        circleBounds.top = circleStroke + verticalMargin / 2
        circleBounds.right = circleBounds.left + circleSize
        circleBounds.bottom = circleBounds.top + circleSize

        secondCircleBounds.set(circleBounds)
        if (ringLayout.drawsSecondRing) {
            val secondInset = ringLayout.secondRingInsetDp * density
            secondCircleBounds.inset(secondInset, secondInset)
        }
    }

    private fun markerFraction(index: Int, count: Int): Float =
            if (kotlin.math.abs(ringLayout.sweepAngle) >= 359.5f) {
                index.toFloat() / count
            } else {
                index.toFloat() / (count - 1).coerceAtLeast(1)
            }

    private fun markIsPlayed(fraction: Float): Boolean =
            displayProgress > 0f && fraction < displayProgress

    private fun drawWatchDots(
            canvas: Canvas,
            bounds: RectF,
            baseWidth: Float,
            count: Int = 60,
            emphasizeEvery: Int = 5,
            normalRadiusScale: Float = .22f,
            emphasizedRadiusScale: Float = .36f
    ) {
        val cx = bounds.centerX()
        val cy = bounds.centerY()
        val orbit = bounds.width() / 2f
        headDotPaint.style = Paint.Style.FILL

        repeat(count) { index ->
            val fraction = markerFraction(index, count)
            val angle = Math.toRadians(
                    (ringLayout.startAngle + fraction * ringLayout.sweepAngle).toDouble())
            val major = emphasizeEvery > 0 && index % emphasizeEvery == 0
            headDotPaint.color = if (markIsPlayed(fraction)) {
                foregroundPaint.color
            } else {
                backgroundPaint.color
            }
            canvas.drawCircle(
                    cx + orbit * cos(angle).toFloat(),
                    cy + orbit * sin(angle).toFloat(),
                    baseWidth * if (major) emphasizedRadiusScale else normalRadiusScale,
                    headDotPaint)
        }
    }

    private fun drawWatchTicks(
            canvas: Canvas,
            bounds: RectF,
            baseWidth: Float,
            count: Int,
            emphasizeEvery: Int,
            normalLength: Float,
            emphasizedLength: Float,
            normalWidth: Float,
            emphasizedWidth: Float
    ) {
        val cx = bounds.centerX()
        val cy = bounds.centerY()
        // The ordinary stroke is centred baseWidth/2 inside the screen. These explicit marks are
        // slightly inset so their rounded caps never get clipped by the physical display edge.
        val outerRadius = bounds.width() / 2f + baseWidth * 0.25f
        headDotPaint.style = Paint.Style.STROKE
        headDotPaint.strokeCap = Paint.Cap.ROUND

        repeat(count) { index ->
            val fraction = markerFraction(index, count)
            val angle = Math.toRadians(
                    (ringLayout.startAngle + fraction * ringLayout.sweepAngle).toDouble())
            val emphasized = emphasizeEvery > 0 && index % emphasizeEvery == 0
            val length = if (emphasized) emphasizedLength else normalLength
            headDotPaint.strokeWidth = if (emphasized) emphasizedWidth else normalWidth
            headDotPaint.color = if (markIsPlayed(fraction)) {
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
        // `cancelReveal > 0` keeps the ring up for the affordance's fade-out after the finger has
        // already lifted, so the cancel does not vanish in the same frame it takes effect.
        val gestureVisible = isDragging || cancelReveal > 0f
        if (!shouldDrawEdgeProgress(seekable, drawProgress, gestureVisible)) {
            return
        }

        val baseWidth = effectiveStrokeWidth()
        drawRingStyle(canvas, circleBounds, baseWidth, allowPaletteGradient = true)

        if (ringLayout.drawsSecondRing) {
            val originalColor = foregroundPaint.color
            foregroundPaint.color = resolvedSecondaryColor()
            drawRingStyle(
                    canvas,
                    secondCircleBounds,
                    baseWidth * .76f,
                    allowPaletteGradient = false)
            foregroundPaint.color = originalColor
        }

        // The tick is a mark *on* the band, so it is inside the early return above rather than
        // beside it: with the ring hidden it would be a lone dash at the screen edge, which is a
        // different object rather than a smaller version of the same one.
        if (gestureVisible ||
                SeekMarkerVisibility.shouldDraw(markerVisibility, isDragging, playing)) {
            drawSeekOriginMarker(canvas, baseWidth)
        }
    }

    /**
     * The tick showing where the track actually is.
     *
     * A radial tick rather than a second dot on the arc: a dot at ring radius reads as another
     * thumb, i.e. as a second thing being dragged, while a mark crossing the band reads as a scale
     * marking - which is what it is. White with a dark outer stroke rather than the accent, because
     * it has to stay legible on all twenty-odd ring styles, several of which are already painted in
     * the accent at that exact radius.
     *
     * **Where it sits depends on whether a finger has the arc.** During a drag it marks
     * [dragOriginProgress] - the position playback is actually at, which keeps advancing while the
     * user decides - because [displayProgress] has been taken over by the finger and no longer says
     * anything about the track. At rest the two are the same thing, so it follows [displayProgress]
     * and reads as a playhead. Anchoring it to [dragOriginProgress] in both cases would leave a
     * stale mark frozen wherever the last drag started.
     */
    private fun drawSeekOriginMarker(canvas: Canvas, baseWidth: Float) {
        markerPaint.style = Paint.Style.STROKE
        markerPaint.pathEffect = null
        val density = resources.displayMetrics.density
        val radius = circleBounds.width() / 2f
        val markerProgress = if (isDragging) dragOriginProgress else displayProgress
        val angle = Math.toRadians(
                (ringLayout.startAngle + markerProgress * ringLayout.sweepAngle).toDouble())
        val cosA = cos(angle).toFloat()
        val sinA = sin(angle).toFloat()
        val half = (baseWidth * MARKER_LENGTH_SCALE) / 2f
        val cx = circleBounds.centerX()
        val cy = circleBounds.centerY()
        val innerR = radius - half
        val outerR = radius + half

        // The palette's tertiary rather than white on a black outline: the tick belongs to the
        // same surface as the ring it crosses, and an outlined white mark read as a foreign object
        // dropped on top of it. Tertiary is the slot that is furthest from the arc's own primary
        // in every treatment, and it is lifted so a dark album cannot make the tick invisible.
        markerPaint.color = markerColor()
        markerPaint.strokeWidth = MARKER_WIDTH_DP * density
        canvas.drawLine(
                cx + cosA * innerR, cy + sinA * innerR,
                cx + cosA * outerR, cy + sinA * outerR,
                markerPaint)
    }

    private fun markerColor(): Int {
        val palette = tertiaryColorInt.takeIf { it != 0 }
                ?: secondaryColorInt.takeIf { it != 0 }
                ?: foregroundPaint.color
        return PanelReadout.liftedAccent(palette)
    }

    /**
     * Runs [cancelReveal] to its target, redrawing the ring *and* re-emitting the preview on every
     * frame - the time readout is a separate View owned by the caller, so without the second half
     * the arc would walk back to the live position while the clock stayed frozen on the abandoned
     * destination.
     */
    private fun animateCancelReveal(armed: Boolean) {
        cancelAnimator?.cancel()
        cancelAnimator = ValueAnimator.ofFloat(cancelReveal, if (armed) 1f else 0f).apply {
            duration = CANCEL_REVEAL_DURATION_MS
            addUpdateListener {
                cancelReveal = it.animatedValue as Float
                if (isDragging) {
                    displayProgress = SeekDragPolicy.previewProgress(
                            fingerProgress, dragOriginProgress, cancelReveal)
                    onSeekPreview?.invoke(displayProgress)
                }
                invalidate()
            }
            start()
        }
    }

    private fun resetScratchPaints(baseWidth: Float) {
        foregroundPaint.pathEffect = null
        backgroundPaint.pathEffect = null
        foregroundPaint.shader = null
        backgroundPaint.shader = null
        foregroundPaint.strokeWidth = baseWidth
        backgroundPaint.strokeWidth = baseWidth
        foregroundPaint.strokeCap = Paint.Cap.ROUND
        backgroundPaint.strokeCap = Paint.Cap.BUTT
    }

    private fun drawRingStyle(
            canvas: Canvas,
            bounds: RectF,
            baseWidth: Float,
            allowPaletteGradient: Boolean
    ) {
        resetScratchPaints(baseWidth)

        val start = ringLayout.startAngle
        val totalSweep = ringLayout.sweepAngle
        val playedSweep = displayProgress * totalSweep
        when (ringStyle) {
            RingStyle.SOLID -> drawSolidRing(
                    canvas,
                    bounds,
                    baseWidth,
                    start,
                    totalSweep,
                    playedSweep,
                    allowPaletteGradient)
            RingStyle.DASHED -> {
                val dash = DashPathEffect(floatArrayOf(baseWidth * 1.9f, baseWidth * 1.5f), 0f)
                foregroundPaint.pathEffect = dash
                backgroundPaint.pathEffect = dash
                foregroundPaint.strokeCap = Paint.Cap.BUTT
                canvas.drawArc(bounds, start, totalSweep, false, backgroundPaint)
                canvas.drawArc(bounds, start, playedSweep, false, foregroundPaint)
            }
            RingStyle.DOTS -> {
                // Zero-length "on" intervals with a round cap render as a trail of dots.
                val dots = DashPathEffect(floatArrayOf(0.01f, baseWidth * 2.6f), 0f)
                foregroundPaint.pathEffect = dots
                backgroundPaint.pathEffect = dots
                foregroundPaint.strokeCap = Paint.Cap.ROUND
                backgroundPaint.strokeCap = Paint.Cap.ROUND
                canvas.drawArc(bounds, start, totalSweep, false, backgroundPaint)
                canvas.drawArc(bounds, start, playedSweep, false, foregroundPaint)
            }
            RingStyle.HAIRLINE -> {
                foregroundPaint.strokeWidth = baseWidth * 0.45f
                backgroundPaint.strokeWidth = baseWidth * 0.3f
                canvas.drawArc(bounds, start, totalSweep, false, backgroundPaint)
                canvas.drawArc(bounds, start, playedSweep, false, foregroundPaint)
            }
            RingStyle.COMET -> drawComet(
                    canvas, bounds, baseWidth, start, totalSweep, playedSweep)
            RingStyle.WATCH_DOTS_60 -> drawWatchDots(canvas, bounds, baseWidth)
            RingStyle.WATCH_TICKS_60 -> drawWatchTicks(
                    canvas = canvas,
                    bounds = bounds,
                    baseWidth = baseWidth,
                    count = 60,
                    emphasizeEvery = 5,
                    normalLength = baseWidth * 0.58f,
                    emphasizedLength = baseWidth * 0.95f,
                    normalWidth = baseWidth * 0.17f,
                    emphasizedWidth = baseWidth * 0.29f)
            RingStyle.HOUR_SEGMENTS_12 -> drawWatchTicks(
                    canvas = canvas,
                    bounds = bounds,
                    baseWidth = baseWidth,
                    count = 12,
                    emphasizeEvery = 1,
                    normalLength = baseWidth,
                    emphasizedLength = baseWidth,
                    normalWidth = baseWidth * 0.4f,
                    emphasizedWidth = baseWidth * 0.4f)
            RingStyle.DOUBLE -> drawDoubleRingStyle(
                    canvas, bounds, baseWidth, start, totalSweep, playedSweep)
            RingStyle.BEADS -> drawWatchDots(
                    canvas = canvas,
                    bounds = bounds,
                    baseWidth = baseWidth,
                    count = 24,
                    emphasizeEvery = 6,
                    normalRadiusScale = .34f,
                    emphasizedRadiusScale = .58f)
            RingStyle.DASH_DOT -> {
                val dashDot = DashPathEffect(
                        floatArrayOf(
                                baseWidth * 2.7f,
                                baseWidth * 1.25f,
                                baseWidth * .18f,
                                baseWidth * 1.25f),
                        0f)
                foregroundPaint.pathEffect = dashDot
                backgroundPaint.pathEffect = dashDot
                foregroundPaint.strokeCap = Paint.Cap.ROUND
                backgroundPaint.strokeCap = Paint.Cap.ROUND
                canvas.drawArc(bounds, start, totalSweep, false, backgroundPaint)
                canvas.drawArc(bounds, start, playedSweep, false, foregroundPaint)
            }
            RingStyle.TICKS_24 -> drawWatchTicks(
                    canvas = canvas,
                    bounds = bounds,
                    baseWidth = baseWidth,
                    count = 24,
                    emphasizeEvery = 6,
                    normalLength = baseWidth * .75f,
                    emphasizedLength = baseWidth * 1.35f,
                    normalWidth = baseWidth * .25f,
                    emphasizedWidth = baseWidth * .48f)
            RingStyle.PROGRESS_ONLY -> {
                foregroundPaint.strokeWidth = baseWidth * 1.3f
                foregroundPaint.strokeCap = Paint.Cap.ROUND
                canvas.drawArc(bounds, start, playedSweep, false, foregroundPaint)
            }
            RingStyle.GLOW -> {
                val accent = foregroundPaint.color
                backgroundPaint.strokeWidth = baseWidth * .34f
                canvas.drawArc(bounds, start, totalSweep, false, backgroundPaint)
                if (kotlin.math.abs(playedSweep) > .5f) {
                    val glowBounds = RectF(bounds).apply {
                        inset(baseWidth * .72f, baseWidth * .72f)
                    }
                    foregroundPaint.strokeWidth = baseWidth * 2.35f
                    foregroundPaint.color = ColorUtils.setAlphaComponent(
                            accent, 0x38)
                    canvas.drawArc(glowBounds, start, playedSweep, false, foregroundPaint)
                    foregroundPaint.color = accent
                    foregroundPaint.strokeWidth = baseWidth * .58f
                    canvas.drawArc(bounds, start, playedSweep, false, foregroundPaint)
                }
            }
            RingStyle.DUOTONE -> drawDuotoneRing(
                    canvas, bounds, baseWidth, start, totalSweep)
            RingStyle.NEEDLE -> drawNeedleRing(
                    canvas, bounds, baseWidth, start, totalSweep, playedSweep)
            RingStyle.ALTERNATING -> {
                val alternating = DashPathEffect(
                        floatArrayOf(baseWidth * 2.2f, baseWidth * .8f), 0f)
                backgroundPaint.pathEffect = alternating
                foregroundPaint.pathEffect = alternating
                foregroundPaint.strokeCap = Paint.Cap.BUTT
                backgroundPaint.strokeCap = Paint.Cap.BUTT
                canvas.drawArc(bounds, start, totalSweep, false, backgroundPaint)
                val original = foregroundPaint.color
                foregroundPaint.color = resolvedSecondaryColor()
                canvas.drawArc(bounds, start, playedSweep, false, foregroundPaint)
                foregroundPaint.pathEffect = DashPathEffect(
                        floatArrayOf(baseWidth * 2.2f, baseWidth * .8f), baseWidth * 3f)
                foregroundPaint.color = original
                canvas.drawArc(bounds, start, playedSweep, false, foregroundPaint)
            }
            RingStyle.SPARK -> {
                foregroundPaint.strokeWidth = baseWidth * .42f
                backgroundPaint.strokeWidth = baseWidth * .24f
                canvas.drawArc(bounds, start, totalSweep, false, backgroundPaint)
                canvas.drawArc(bounds, start, playedSweep, false, foregroundPaint)
                if (kotlin.math.abs(playedSweep) > .5f) {
                    drawHeadDot(canvas, bounds, baseWidth * 1.15f, start + playedSweep)
                }
            }
        }
        foregroundPaint.shader = null
        foregroundPaint.pathEffect = null
        backgroundPaint.pathEffect = null
    }

    private fun drawSolidRing(
            canvas: Canvas,
            bounds: RectF,
            baseWidth: Float,
            start: Float,
            totalSweep: Float,
            playedSweep: Float,
            allowPaletteGradient: Boolean
    ) {
        canvas.drawArc(bounds, start, totalSweep, false, backgroundPaint)
        // SweepGradient progresses clockwise. Counter-clockwise layouts keep their primary color
        // rather than showing a reversed/clamped palette at the start cap.
        if (playedSweep > 1f && allowPaletteGradient && gradientEnabled &&
                hasVisibleHueSpread()) {
                // A flat single-hue treatment (Normal, Desaturated, Expressive/Monochrome on most
                // covers) falls under the threshold and draws exactly as before - only a treatment
                // that deliberately rotates hue (Triadic, Complementary, Analogous, Duotone) gets
                // the gradient, so this is additive rather than a restyle of the existing look.
                    // The round cap at the arc's start extends BACKWARDS past the start angle by
                    // half the stroke. In an unshifted sweep gradient those angles sit just below
                    // position 1.0, where the shader clamps to its LAST color - so the tip of the
                    // bar was painted in the gradient's end color, reading as a stray mark of the
                    // wrong hue at 12 o'clock. Shifting the whole gradient forward by the cap's own
                    // angle and holding the primary across that lead-in puts the cap back in the
                    // color the bar actually starts with.
            val capFraction = capAngleFraction(bounds, baseWidth)
            val gradientStart = capFraction
            val shader = SweepGradient(
                            bounds.centerX(), bounds.centerY(),
                            intArrayOf(
                                    foregroundPaint.color,
                                    foregroundPaint.color,
                                    secondaryColorInt,
                                    tertiaryColorInt),
                            floatArrayOf(
                                    0f,
                                    gradientStart,
                                    (gradientStart + playedSweep / 360f * 0.5f).coerceAtMost(1f),
                                    (gradientStart + playedSweep / 360f).coerceAtMost(1f))
                    )
            val rotate = Matrix()
            rotate.setRotate(
                    start - capFraction * 360f,
                    bounds.centerX(),
                    bounds.centerY())
            shader.setLocalMatrix(rotate)
            foregroundPaint.shader = shader
            canvas.drawArc(bounds, start, playedSweep, false, foregroundPaint)
            foregroundPaint.shader = null
        } else {
            canvas.drawArc(bounds, start, playedSweep, false, foregroundPaint)
        }
    }

    private fun drawComet(
            canvas: Canvas,
            bounds: RectF,
            baseWidth: Float,
            start: Float,
            totalSweep: Float,
            playedSweep: Float
    ) {
        backgroundPaint.strokeWidth = baseWidth * 0.5f
        canvas.drawArc(bounds, start, totalSweep, false, backgroundPaint)
        if (kotlin.math.abs(playedSweep) > 1f) {
            if (playedSweep > 0f) {
                    // Sweep gradient from transparent at 12 o'clock to the full accent at the
                    // progress head, shifted past the round cap for the same reason SOLID is: the
                    // cap sits just below shader position 1.0, where an unshifted gradient clamps
                    // to its last stop - here full opacity, so the tail that is meant to fade in
                    // from nothing started with an opaque dot.
                    val accent = foregroundPaint.color
                val capFraction = capAngleFraction(bounds, baseWidth)
                    val shader = SweepGradient(
                            bounds.centerX(), bounds.centerY(),
                            intArrayOf(
                                    ColorUtils.setAlphaComponent(accent, 0x00),
                                    ColorUtils.setAlphaComponent(accent, 0x00),
                                    ColorUtils.setAlphaComponent(accent, 0xFF)
                            ),
                            floatArrayOf(
                                    0f,
                                    capFraction,
                                    (capFraction + playedSweep / 360f).coerceAtMost(1f))
                    )
                    val rotate = Matrix()
                    rotate.setRotate(
                            start - capFraction * 360f,
                            bounds.centerX(),
                            bounds.centerY())
                    shader.setLocalMatrix(rotate)
                    foregroundPaint.shader = shader
                canvas.drawArc(bounds, start, playedSweep, false, foregroundPaint)
                    foregroundPaint.shader = null
            } else {
                canvas.drawArc(bounds, start, playedSweep, false, foregroundPaint)
            }
        }
        if (displayProgress <= 0f) return
        drawHeadDot(canvas, bounds, baseWidth * .5f, start + playedSweep)
    }

    private fun drawDoubleRingStyle(
            canvas: Canvas,
            bounds: RectF,
            baseWidth: Float,
            start: Float,
            totalSweep: Float,
            playedSweep: Float
    ) {
        val originalColor = foregroundPaint.color
        foregroundPaint.strokeWidth = baseWidth * .5f
        backgroundPaint.strokeWidth = baseWidth * .5f
        canvas.drawArc(bounds, start, totalSweep, false, backgroundPaint)
        canvas.drawArc(bounds, start, playedSweep, false, foregroundPaint)

        val innerBounds = RectF(bounds).apply {
            inset(baseWidth * 1.35f, baseWidth * 1.35f)
        }
        foregroundPaint.color = resolvedSecondaryColor()
        canvas.drawArc(innerBounds, start, totalSweep, false, backgroundPaint)
        canvas.drawArc(innerBounds, start, playedSweep, false, foregroundPaint)
        foregroundPaint.color = originalColor
    }

    private fun drawDuotoneRing(
            canvas: Canvas,
            bounds: RectF,
            baseWidth: Float,
            start: Float,
            totalSweep: Float
    ) {
        val originalColor = foregroundPaint.color
        backgroundPaint.strokeWidth = baseWidth * .62f
        backgroundPaint.color = ColorUtils.setAlphaComponent(resolvedSecondaryColor(), 0x38)
        canvas.drawArc(bounds, start, totalSweep, false, backgroundPaint)

        val split = .55f
        val secondaryProgress = min(displayProgress, split)
        foregroundPaint.color = resolvedSecondaryColor()
        canvas.drawArc(bounds, start, totalSweep * secondaryProgress, false, foregroundPaint)
        if (displayProgress > split) {
            foregroundPaint.color = originalColor
            canvas.drawArc(
                    bounds,
                    start + totalSweep * split,
                    totalSweep * (displayProgress - split),
                    false,
                    foregroundPaint)
        }
        foregroundPaint.color = originalColor
        backgroundPaint.color = resources.getColor(R.color.glass_surface_border, null)
    }

    private fun drawNeedleRing(
            canvas: Canvas,
            bounds: RectF,
            baseWidth: Float,
            start: Float,
            totalSweep: Float,
            playedSweep: Float
    ) {
        backgroundPaint.strokeWidth = baseWidth * .28f
        canvas.drawArc(bounds, start, totalSweep, false, backgroundPaint)
        foregroundPaint.strokeWidth = baseWidth * .34f
        canvas.drawArc(bounds, start, playedSweep, false, foregroundPaint)
        if (displayProgress <= 0f) return

        val angle = Math.toRadians((start + playedSweep).toDouble())
        val cosAngle = cos(angle).toFloat()
        val sinAngle = sin(angle).toFloat()
        val outerRadius = bounds.width() / 2f
        val innerRadius = (outerRadius - baseWidth * 2.3f).coerceAtLeast(0f)
        headDotPaint.style = Paint.Style.STROKE
        headDotPaint.strokeCap = Paint.Cap.ROUND
        headDotPaint.strokeWidth = baseWidth * .62f
        headDotPaint.color = foregroundPaint.color
        canvas.drawLine(
                bounds.centerX() + innerRadius * cosAngle,
                bounds.centerY() + innerRadius * sinAngle,
                bounds.centerX() + outerRadius * cosAngle,
                bounds.centerY() + outerRadius * sinAngle,
                headDotPaint)
        drawHeadDot(canvas, bounds, baseWidth * .38f, start + playedSweep)
    }

    private fun drawHeadDot(canvas: Canvas, bounds: RectF, radius: Float, angleDeg: Float) {
        val angle = Math.toRadians(angleDeg.toDouble())
        headDotPaint.style = Paint.Style.FILL
        headDotPaint.color = foregroundPaint.color
        canvas.drawCircle(
                bounds.centerX() + bounds.width() / 2f * cos(angle).toFloat(),
                bounds.centerY() + bounds.height() / 2f * sin(angle).toFloat(),
                radius,
                headDotPaint)
    }

    private fun resolvedSecondaryColor(): Int =
            if (secondaryColorInt != 0) secondaryColorInt else foregroundPaint.color

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

    private fun touchAngle(dx: Float, dy: Float): Double =
            (Math.toDegrees(atan2(dy, dx).toDouble()) + 360.0) % 360.0

    private fun directedAngleDelta(angle: Double): Double =
            if (ringLayout.sweepAngle >= 0f) {
                ((angle - ringLayout.startAngle) + 360.0) % 360.0
            } else {
                ((ringLayout.startAngle - angle) + 360.0) % 360.0
            }

    private fun isAngleInsideLayout(dx: Float, dy: Float): Boolean {
        val span = kotlin.math.abs(ringLayout.sweepAngle).toDouble()
        return span >= 359.5 || directedAngleDelta(touchAngle(dx, dy)) <= span
    }

    private fun circularAngleDistance(first: Double, second: Double): Double {
        val direct = kotlin.math.abs(first - second) % 360.0
        return min(direct, 360.0 - direct)
    }

    private fun angleToProgress(dx: Float, dy: Float): Float {
        val angle = touchAngle(dx, dy)
        val span = kotlin.math.abs(ringLayout.sweepAngle).toDouble()
        val delta = directedAngleDelta(angle)
        if (span >= 359.5 || delta <= span) {
            return (delta / span).toFloat().coerceIn(0f, 1f)
        }

        // A drag may leave an open/side arc after it has started. Snap to the nearest endpoint
        // rather than wrapping across the hidden gap and jumping from zero straight to one.
        val end = (ringLayout.startAngle + ringLayout.sweepAngle + 360.0) % 360.0
        return if (circularAngleDistance(angle, ringLayout.startAngle.toDouble()) <=
                circularAngleDistance(angle, end)) {
            0f
        } else {
            1f
        }
    }

    private fun isInsideActiveRingBand(distanceFromCenter: Float): Boolean {
        val radii = if (ringLayout.drawsSecondRing) {
            floatArrayOf(circleBounds.width() / 2f, secondCircleBounds.width() / 2f)
        } else {
            floatArrayOf(circleBounds.width() / 2f)
        }
        return radii.any { radius ->
            kotlin.math.abs(distanceFromCenter - radius) <= touchBand
        }
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
        val dx = event.x - centerX
        val dy = event.y - centerY
        val distanceFromCenter = hypot(dx, dy)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (!isInsideActiveRingBand(distanceFromCenter) ||
                        !isAngleInsideLayout(dx, dy)) {
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
                // Captured before the finger overwrites displayProgress - this is the whole
                // reference the marker and the cancel both work from.
                dragOriginProgress = displayProgress
                cancelArmed = false
                cancelAnimator?.cancel()
                cancelReveal = 0f

                // Tells any parent that intercepts edge/bezel touches (e.g. a swipe-to-dismiss
                // container) to back off for the duration of this gesture - without it, a held
                // touch near the bezel could get stolen mid-drag, which is what made the ring
                // appear to freeze and never reach onSeekFinished.
                parent?.requestDisallowInterceptTouchEvent(true)

                fingerProgress = angleToProgress(dx, dy)
                displayProgress = fingerProgress
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
                // The angle is still tracked while the cancel is armed, so sliding back out to
                // the ring resumes the scrub from where the finger actually is rather than from
                // wherever it was when the cancel engaged.
                fingerProgress = angleToProgress(dx, dy)

                val armed = SeekDragPolicy.isInsideCancelZone(
                        distanceFromCenter, circleBounds.width() / 2f, touchBand)
                if (armed != cancelArmed) {
                    cancelArmed = armed
                    animateCancelReveal(armed)
                    onCancelArmedChanged?.invoke(armed)
                }

                displayProgress = SeekDragPolicy.previewProgress(
                        fingerProgress, dragOriginProgress, cancelReveal)
                invalidate()
                onSeekPreview?.invoke(displayProgress)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!isDragging) {
                    return false
                }
                isDragging = false
                parent?.requestDisallowInterceptTouchEvent(false)

                if (cancelArmed || event.action == MotionEvent.ACTION_CANCEL) {
                    // Nothing is sent to the phone: the position was never changed, so putting the
                    // ring back on the live value is the entire undo. The affordance fades out
                    // rather than disappearing with the finger, which is what makes a cancel look
                    // deliberate rather than like the gesture having failed.
                    cancelArmed = false
                    displayProgress = dragOriginProgress
                    animateCancelReveal(false)
                    invalidate()
                    onCancelArmedChanged?.invoke(false)
                    onSeekCancelled?.invoke()
                    return true
                }

                cancelAnimator?.cancel()
                cancelReveal = 0f
                invalidate()
                onCancelArmedChanged?.invoke(false)
                // A quick tap (no ACTION_MOVE in between) still jumps straight to that position;
                // ACTION_DOWN already emitted its preview so tap and drag share the same feedback.
                onSeekFinished?.invoke(displayProgress)
                return true
            }
            else -> return false
        }
    }
}
