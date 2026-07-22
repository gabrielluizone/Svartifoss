package com.svartifoss.snfell.watch.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.graphics.SweepGradient
import android.view.MotionEvent
import androidx.core.graphics.ColorUtils
import com.svartifoss.snfell.R
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** User-selectable paint style of the volume overlay. Geometry is selected independently through
 * [VolumeLayout], so a theme is no longer just another colour on the same left-edge arc. */
enum class VolumeStyle {
    /** Frosted rounded arc over the blur backdrop (original look). */
    GLASS,
    /** Thin AMOLED hairline arc, faint track. */
    MINIMAL,
    /** Material Design 2: flat-capped grey track + accent fill with a round thumb. */
    MATERIAL,
    /** Fat rounded accent capsule with a tonal track. */
    TONAL,
    /** Bright accent line with a faint accent track (neon glow). */
    NEON,
    /** Light track with an accent fill. */
    LIGHT,
    /** Two real album swatches blended through a vertical-gradient fill. */
    GRADIENT,
    /** Neutral greyscale fill, ignoring the album accent. */
    MONO,
    /** Rich three-swatch album spectrum with a slim glass highlight. */
    PRISM,
    /** Bold chunky bar (cartoon outline). */
    OUTLINE,
    /** Two-hue: secondary album swatch track, primary album swatch fill. */
    DUOTONE,
    /** Pure white fill on a faint track (high contrast). */
    CONTRAST,
    /** Monochrome-green CRT fill. */
    TERMINAL,
    /** Brighter frosted track with an accent fill. */
    FROST,
    /** The arc broken into discrete tick blocks, lighting up like a level meter. */
    SEGMENTS,
    /** Multi-hue gradient fill made from three album swatches (northern-lights look). */
    AURORA,
    /** Wide translucent accent halo with a thin solid core, like a wet ink stroke. */
    INK,
    /** Recessed dark channel with a slim bright accent core running inside it. */
    GROOVE;

    companion object {
        fun fromPref(value: String?): VolumeStyle = when (value) {
            "minimal" -> MINIMAL
            "material" -> MATERIAL
            "tonal" -> TONAL
            "neon" -> NEON
            "prism" -> PRISM
            "light" -> LIGHT
            "gradient" -> GRADIENT
            "mono" -> MONO
            "outline" -> OUTLINE
            "duotone" -> DUOTONE
            "contrast" -> CONTRAST
            "terminal" -> TERMINAL
            "frost" -> FROST
            "segments" -> SEGMENTS
            "aurora" -> AURORA
            "ink" -> INK
            "groove" -> GROOVE
            else -> GLASS
        }
    }
}

/**
 * Composition of the volume indicator. Every arc variant is expressed purely as a bounds/start/
 * sweep triple ([activeArcBounds], [activeArcStart], [activeArcSweep]), which drawing, the
 * Material thumb and hit-testing all read - so a new arc geometry works with all 18 [VolumeStyle]s
 * and stays draggable without touching any of them.
 *
 * A **negative sweep** means the arc fills counter-clockwise from its start. That is what keeps
 * "up is louder" true on the mirrored right-hand and bottom arcs, instead of having them fill
 * downwards or right-to-left.
 */
enum class VolumeLayout {
    /** Left bezel arc, filling bottom-to-top (the original look). */
    EDGE,
    /** The left arc stretched over a longer span of the bezel, for finer dragging. */
    EDGE_TALL,
    /** Mirror of [EDGE] on the right bezel, for left-handed wear or a rotated watch. */
    EDGE_RIGHT,
    /** Arc across the top of the bezel, filling left-to-right. */
    EDGE_TOP,
    /** Arc across the bottom of the bezel, filling left-to-right. */
    EDGE_BOTTOM,
    /** Complete bezel ring, filling clockwise from 12 o'clock. */
    RING,
    /** Small centred arc rather than a bezel one. */
    HALO,
    /** Horizontal level bar, unrelated to the bezel geometry. */
    METER;

    companion object {
        fun fromPref(value: String?): VolumeLayout = when (value) {
            "halo" -> HALO
            "meter" -> METER
            "edge_tall" -> EDGE_TALL
            "edge_right" -> EDGE_RIGHT
            "edge_top" -> EDGE_TOP
            "edge_bottom" -> EDGE_BOTTOM
            "ring" -> RING
            else -> EDGE
        }
    }
}

/** Monochrome-green used by the terminal/CRT style. */
private val TERMINAL_GREEN = 0xFF33FF66.toInt()

/**
 * Volume indicator styled after the stock Wear OS media controls: a vertical arc hugging the
 * left edge of the screen (not the old screen-spanning ring, nor a bottom arc), filling from the
 * bottom upwards as volume increases. Supports both the rotary crown (via [incrementVolume],
 * driven from [MainActivity][com.svartifoss.snfell.watch.view.MainActivity]'s
 * `onGenericMotionEvent`) and direct touch-drag along the arc itself.
 */
class CircularVolumeBar : android.view.View {
    companion object {
        // Canvas.drawArc() convention: 0deg = 3 o'clock, increasing clockwise. 180deg = 9
        // o'clock (true left) - centering the arc there puts it on the left edge of the screen.
        private const val ARC_START_DEG = 130f
        private const val ARC_SWEEP_DEG = 100f
        private const val HALO_START_DEG = 135f
        private const val HALO_SWEEP_DEG = 270f
    }

    // Scratch stroke paint reconfigured per style each draw; and a fill paint for the material thumb.
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val circleBounds = RectF()
    private val haloBounds = RectF()
    private val meterBounds = RectF()
    private val touchBand = resources.getDimension(R.dimen.seek_bar_touch_band)

    private val baseStroke = resources.getDimension(R.dimen.seek_bar_width)
    // The bounds inset uses the fattest style's stroke so no style ever clips at the screen edge.
    private val maxStroke = baseStroke * 1.7f
    private val glassTrackColor = resources.getColor(R.color.music_screen_volume_bar_background_color, null)

    // The accent (album/theme) color, set via progressColor. Kept in its own field so per-draw
    // recolouring of the scratch paints never clobbers it.
    private var accentColorInt = resources.getColor(R.color.music_screen_volume_bar_foreground_color, null)
    private var secondaryColorInt = accentColorInt
    private var tertiaryColorInt = accentColorInt

    var barStyle: VolumeStyle = VolumeStyle.GLASS
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var barLayout: VolumeLayout = VolumeLayout.EDGE
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private var isDragging = false

    var onVolumeChanged: ((Float) -> Unit)? = null

    constructor(context: Context?) : this(context, null)
    constructor(context: Context?, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : this(context, attrs, defStyleAttr, 0)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    var volume = 0.5f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    /** Tints the filled arc, e.g. with the same color extracted from the current album art. */
    var progressColor: Int
        get() = accentColorInt
        set(value) {
            accentColorInt = value
            invalidate()
        }

    /** Additional album-palette swatches for styles that intentionally use more than one hue. */
    var secondaryColor: Int
        get() = secondaryColorInt
        set(value) {
            secondaryColorInt = value
            invalidate()
        }

    var tertiaryColor: Int
        get() = tertiaryColorInt
        set(value) {
            tertiaryColorInt = value
            invalidate()
        }

    fun incrementVolume(change: Float) {
        volume = min(1f, max(0f, volume + change))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val viewSize = min(measuredWidth, measuredHeight).toFloat()

        val circleStroke = maxStroke / 2
        val circleSize = viewSize - maxStroke

        val horizontalMargin = measuredWidth - viewSize
        val verticalMargin = measuredHeight - viewSize

        circleBounds.left = circleStroke + horizontalMargin / 2
        circleBounds.top = circleStroke + verticalMargin / 2
        circleBounds.right = circleBounds.left + circleSize
        circleBounds.bottom = circleBounds.top + circleSize

        val density = resources.displayMetrics.density
        val haloSize = min(viewSize - 52f * density, 138f * density)
                .coerceAtLeast(80f * density)
        haloBounds.set(
                measuredWidth / 2f - haloSize / 2f,
                measuredHeight / 2f - haloSize / 2f,
                measuredWidth / 2f + haloSize / 2f,
                measuredHeight / 2f + haloSize / 2f)
        val meterWidth = min(measuredWidth * .68f, 132f * density)
        val meterHeight = 11f * density
        val meterY = measuredHeight / 2f + 29f * density
        meterBounds.set(
                measuredWidth / 2f - meterWidth / 2f,
                meterY - meterHeight / 2f,
                measuredWidth / 2f + meterWidth / 2f,
                meterY + meterHeight / 2f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (barLayout == VolumeLayout.METER) {
            drawMeter(canvas)
            return
        }

        when (barStyle) {
            VolumeStyle.GLASS ->
                drawArc(canvas, baseStroke, Paint.Cap.ROUND, glassTrackColor, accentColorInt)
            VolumeStyle.MINIMAL ->
                drawArc(canvas, baseStroke * 0.5f, Paint.Cap.ROUND, 0x22FFFFFF, accentColorInt)
            VolumeStyle.MATERIAL -> {
                drawArc(canvas, baseStroke * 1.1f, Paint.Cap.BUTT, 0x33FFFFFF, accentColorInt)
                drawThumb(canvas, accentColorInt)
            }
            VolumeStyle.TONAL ->
                drawArc(canvas, baseStroke * 1.7f, Paint.Cap.ROUND,
                        tonal(accentColorInt, 0.22f), tonal(accentColorInt, 0.72f))
            VolumeStyle.NEON ->
                drawArc(canvas, baseStroke * 0.8f, Paint.Cap.ROUND,
                        ColorUtils.setAlphaComponent(accentColorInt, 0x40), accentColorInt)
            VolumeStyle.LIGHT ->
                drawArc(canvas, baseStroke, Paint.Cap.ROUND, 0x88CCCCCC.toInt(), accentColorInt)
            VolumeStyle.GRADIENT ->
                drawArc(canvas, baseStroke * 1.2f, Paint.Cap.ROUND,
                        tonal(accentColorInt, 0.18f), 0, fillShader = verticalArcShader())
            VolumeStyle.MONO ->
                drawArc(canvas, baseStroke, Paint.Cap.ROUND, 0x33FFFFFF, 0xFFE0E0E0.toInt())
            VolumeStyle.OUTLINE ->
                drawArc(canvas, baseStroke * 1.4f, Paint.Cap.BUTT, 0x55FFFFFF, accentColorInt)
            VolumeStyle.DUOTONE ->
                drawArc(canvas, baseStroke, Paint.Cap.ROUND,
                        tonal(secondaryColorInt, 0.30f), accentColorInt)
            VolumeStyle.PRISM -> {
                drawArc(canvas, baseStroke * 1.7f, Paint.Cap.ROUND,
                        0x22FFFFFF, 0, fillShader = prismShader())
                drawArc(canvas, baseStroke * 0.35f, Paint.Cap.ROUND, 0, 0xA6FFFFFF.toInt())
            }
            VolumeStyle.CONTRAST ->
                drawArc(canvas, baseStroke * 1.3f, Paint.Cap.BUTT, 0x55FFFFFF, 0xFFFFFFFF.toInt())
            VolumeStyle.TERMINAL ->
                drawArc(canvas, baseStroke * 0.9f, Paint.Cap.BUTT, ColorUtils.setAlphaComponent(TERMINAL_GREEN, 0x40), TERMINAL_GREEN)
            VolumeStyle.FROST ->
                drawArc(canvas, baseStroke, Paint.Cap.ROUND, 0x44FFFFFF, accentColorInt)
            VolumeStyle.SEGMENTS -> {
                // Discrete tick blocks lighting up like a level meter.
                val segments = DashPathEffect(floatArrayOf(baseStroke * 1.3f, baseStroke * 0.9f), 0f)
                strokePaint.pathEffect = segments
                drawArc(canvas, baseStroke * 1.5f, Paint.Cap.BUTT,
                        ColorUtils.setAlphaComponent(accentColorInt, 0x28), accentColorInt)
                strokePaint.pathEffect = null
            }
            VolumeStyle.AURORA ->
                drawArc(canvas, baseStroke * 1.3f, Paint.Cap.ROUND,
                        0x22FFFFFF, 0, fillShader = auroraShader())
            VolumeStyle.INK -> {
                // Halo pass first (wide, translucent), then the solid core on top.
                drawArc(canvas, baseStroke * 2.1f, Paint.Cap.ROUND,
                        0x00000000, ColorUtils.setAlphaComponent(accentColorInt, 0x3A))
                drawArc(canvas, baseStroke * 0.7f, Paint.Cap.ROUND, 0x22FFFFFF, accentColorInt)
            }
            VolumeStyle.GROOVE -> {
                // Wide dark channel first (track only), then a slim bright accent core inside it.
                drawArc(canvas, baseStroke * 1.8f, Paint.Cap.ROUND, 0x55000000, 0)
                drawArc(canvas, baseStroke * 0.6f, Paint.Cap.ROUND, 0, accentColorInt)
            }
        }
    }

    /** Vertical gradient through real tertiary/primary/secondary album-art swatches. */
    private fun auroraShader(): Shader {
        val bounds = activeArcBounds()
        return LinearGradient(
                bounds.left, bounds.top, bounds.left, bounds.bottom,
                intArrayOf(
                        tonal(tertiaryColorInt, 0.60f),
                        tonal(accentColorInt, 0.60f),
                        tonal(secondaryColorInt, 0.60f)
                ),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
    }

    /** Vertical accent gradient (light at top, dark at bottom) spanning the arc bounds, for the
     *  gradient style's fill. */
    private fun verticalArcShader(): Shader {
        val bounds = activeArcBounds()
        return LinearGradient(
                bounds.left, bounds.top, bounds.left, bounds.bottom,
                tonal(accentColorInt, 0.62f), tonal(secondaryColorInt, 0.30f),
                Shader.TileMode.CLAMP)
    }

    /** Repeats the three real cover swatches around the circle so this left-edge arc receives
     *  all three hues, unlike a two-stop vertical gradient that only exposes its middle. */
    private fun prismShader(): Shader {
        val bounds = activeArcBounds()
        return SweepGradient(
                bounds.centerX(),
                bounds.centerY(),
                intArrayOf(
                        tonal(tertiaryColorInt, .38f),
                        tonal(accentColorInt, .62f),
                        tonal(secondaryColorInt, .48f),
                        tonal(tertiaryColorInt, .38f),
                        tonal(accentColorInt, .62f)
                ),
                floatArrayOf(0f, .36f, .50f, .64f, 1f))
    }

    /** Draws the track arc plus the volume-filled arc from the bottom end upwards. When
     *  [fillShader] is set it paints the fill instead of [fillColor]. */
    private fun drawArc(
            canvas: Canvas,
            stroke: Float,
            cap: Paint.Cap,
            trackColor: Int,
            fillColor: Int,
            fillShader: Shader? = null
    ) {
        strokePaint.strokeWidth = stroke
        strokePaint.strokeCap = cap

        strokePaint.shader = null
        strokePaint.color = trackColor
        val bounds = activeArcBounds()
        val start = activeArcStart()
        val sweep = activeArcSweep()
        canvas.drawArc(bounds, start, sweep, false, strokePaint)

        if (volume > 0.001f) {
            if (fillShader != null) {
                strokePaint.shader = fillShader
            } else {
                strokePaint.shader = null
                strokePaint.color = fillColor
            }
            canvas.drawArc(bounds, start, volume * sweep, false, strokePaint)
            strokePaint.shader = null
        }
    }

    /** Material slider-style dot sitting on the arc at the current volume level. */
    private fun drawThumb(canvas: Canvas, color: Int) {
        val bounds = activeArcBounds()
        val angleRad = Math.toRadians((activeArcStart() + volume * activeArcSweep()).toDouble())
        val radius = bounds.width() / 2f
        fillPaint.color = color
        canvas.drawCircle(
                bounds.centerX() + radius * cos(angleRad).toFloat(),
                bounds.centerY() + radius * sin(angleRad).toFloat(),
                baseStroke * 0.95f,
                fillPaint
        )
    }

    /** Album accent mapped to a chosen lightness, saturation kept in a readable band - for the
     *  tonal style's track (dark) and fill (light). */
    private fun tonal(accent: Int, lightness: Float): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(accent, hsl)
        hsl[1] = hsl[1].coerceIn(0.25f, 0.60f)
        hsl[2] = lightness
        return ColorUtils.HSLToColor(hsl)
    }

    private fun activeArcBounds(): RectF =
            if (barLayout == VolumeLayout.HALO) haloBounds else circleBounds

    // Canvas.drawArc convention: 0deg = 3 o'clock, increasing clockwise. So 90 = bottom,
    // 180 = left, 270 = top. Each pair below is written as (start, sweep) with the start at the
    // *quiet* end, so a negative sweep is used wherever filling towards "louder" runs
    // counter-clockwise.
    private fun activeArcStart(): Float = when (barLayout) {
        VolumeLayout.HALO -> HALO_START_DEG
        VolumeLayout.EDGE_TALL -> 100f
        // 50deg is the lower-right; sweeping back through 0deg reaches the upper right.
        VolumeLayout.EDGE_RIGHT -> 50f
        VolumeLayout.EDGE_TOP -> 235f
        // 125deg is the lower-left; sweeping back through 90deg reaches the lower right.
        VolumeLayout.EDGE_BOTTOM -> 125f
        VolumeLayout.RING -> 270f
        else -> ARC_START_DEG
    }

    private fun activeArcSweep(): Float = when (barLayout) {
        VolumeLayout.HALO -> HALO_SWEEP_DEG
        VolumeLayout.EDGE_TALL -> 160f
        VolumeLayout.EDGE_RIGHT -> -100f
        VolumeLayout.EDGE_TOP -> 70f
        VolumeLayout.EDGE_BOTTOM -> -70f
        VolumeLayout.RING -> 360f
        else -> ARC_SWEEP_DEG
    }

    /** Horizontal level composition. The selected paint style still controls its material, but
     * this geometry is intentionally unrelated to the bezel arc. */
    private fun drawMeter(canvas: Canvas) {
        val radius = meterBounds.height() / 2f
        val trackColor = when (barStyle) {
            VolumeStyle.LIGHT -> 0x88CCCCCC.toInt()
            VolumeStyle.TERMINAL -> ColorUtils.setAlphaComponent(TERMINAL_GREEN, 0x40)
            VolumeStyle.DUOTONE -> tonal(secondaryColorInt, .30f)
            VolumeStyle.TONAL -> tonal(accentColorInt, .22f)
            else -> 0x35FFFFFF
        }
        val fillColor = when (barStyle) {
            VolumeStyle.MONO -> 0xFFE0E0E0.toInt()
            VolumeStyle.CONTRAST -> 0xFFFFFFFF.toInt()
            VolumeStyle.TERMINAL -> TERMINAL_GREEN
            VolumeStyle.TONAL -> tonal(accentColorInt, .72f)
            else -> accentColorInt
        }
        fillPaint.shader = null
        fillPaint.color = trackColor
        canvas.drawRoundRect(meterBounds, radius, radius, fillPaint)

        if (barStyle == VolumeStyle.SEGMENTS) {
            val count = 10
            val gap = resources.displayMetrics.density * 3f
            val segmentWidth = (meterBounds.width() - gap * (count - 1)) / count
            repeat(count) { index ->
                val left = meterBounds.left + index * (segmentWidth + gap)
                fillPaint.color = if ((index + 1f) / count <= volume + .001f) {
                    fillColor
                } else {
                    ColorUtils.setAlphaComponent(fillColor, 0x28)
                }
                canvas.drawRoundRect(
                        left, meterBounds.top, left + segmentWidth, meterBounds.bottom,
                        segmentWidth / 2f, segmentWidth / 2f, fillPaint)
            }
            return
        }

        if (volume > .001f) {
            val fill = RectF(
                    meterBounds.left, meterBounds.top,
                    meterBounds.left + meterBounds.width() * volume, meterBounds.bottom)
            fillPaint.shader = when (barStyle) {
                VolumeStyle.GRADIENT, VolumeStyle.AURORA, VolumeStyle.PRISM -> LinearGradient(
                        meterBounds.left, meterBounds.centerY(), meterBounds.right,
                        meterBounds.centerY(), tertiaryColorInt, secondaryColorInt,
                        Shader.TileMode.CLAMP)
                else -> null
            }
            fillPaint.color = fillColor
            canvas.drawRoundRect(fill, radius, radius, fillPaint)
            fillPaint.shader = null
        }
        if (barStyle in setOf(VolumeStyle.MATERIAL, VolumeStyle.GLASS, VolumeStyle.TONAL)) {
            fillPaint.color = Color.WHITE
            canvas.drawCircle(
                    meterBounds.left + meterBounds.width() * volume,
                    meterBounds.centerY(), radius * .72f, fillPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (barLayout == VolumeLayout.METER) {
            return handleMeterTouch(event)
        }
        val bounds = activeArcBounds()
        val centerX = bounds.centerX()
        val centerY = bounds.centerY()
        val radius = bounds.width() / 2
        val dx = event.x - centerX
        val dy = event.y - centerY
        val distanceFromCenter = hypot(dx, dy)

        // Same convention as Canvas.drawArc: 0deg = East, increasing clockwise (screen Y is
        // already "down", so atan2(dy, dx) lines up with it directly, no extra offset needed).
        val angleDeg = (Math.toDegrees(atan2(dy, dx).toDouble()) + 360.0) % 360.0
        val start = activeArcStart()
        val sweep = activeArcSweep()
        // How far the touch sits from the arc's start measured *in the arc's own fill direction*,
        // so counter-clockwise arcs (negative sweep) hit-test and scrub the same way.
        val delta = if (sweep >= 0f) {
            ((angleDeg - start) + 360.0) % 360.0
        } else {
            ((start - angleDeg) + 360.0) % 360.0
        }
        val span = kotlin.math.abs(sweep).toDouble()
        val withinArc = delta <= span

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (!withinArc || kotlin.math.abs(distanceFromCenter - radius) > touchBand) {
                    return false
                }
                isDragging = true
                // The arc sits right where WearableDrawerLayout watches for an edge swipe/hold
                // to open the drawer - without this, a held drag here could get stolen mid-touch.
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) {
                    return false
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!isDragging) {
                    return false
                }
                isDragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
            else -> return false
        }

        val fraction = (delta / span).toFloat().coerceIn(0f, 1f)
        volume = fraction
        onVolumeChanged?.invoke(fraction)

        return true
    }

    private fun handleMeterTouch(event: MotionEvent): Boolean {
        val expanded = RectF(meterBounds).apply { inset(-touchBand, -touchBand) }
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (!expanded.contains(event.x, event.y)) return false
                isDragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> if (!isDragging) return false
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!isDragging) return false
                isDragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
            else -> return false
        }
        val fraction = ((event.x - meterBounds.left) / meterBounds.width()).coerceIn(0f, 1f)
        volume = fraction
        onVolumeChanged?.invoke(fraction)
        return true
    }
}
