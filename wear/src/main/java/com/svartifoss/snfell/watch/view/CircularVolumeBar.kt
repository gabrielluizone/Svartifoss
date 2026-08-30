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
    GROOVE,
    /** Large round markers, for a tactile jewellery-like scale. */
    BEADS,
    /** Two parallel rails using the primary and secondary palette colors. */
    DUAL,
    /** Slim level line with a soft expanding marker at the current value. */
    PULSE,
    /** Polished neutral metal with a bright specular highlight. */
    CHROME,
    /** A fixed full-hue rainbow rather than an album-derived gradient. */
    SPECTRUM,
    /** Graduated radial/linear blocks that grow with the level. */
    STEPS;

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
            "beads" -> BEADS
            "dual" -> DUAL
            "pulse" -> PULSE
            "chrome" -> CHROME
            "spectrum" -> SPECTRUM
            "steps" -> STEPS
            else -> GLASS
        }
    }
}

/**
 * Composition of the volume indicator. Every arc variant is expressed purely as a bounds/start/
 * sweep triple ([activeArcBounds], [activeArcStart], [activeArcSweep]), which drawing, the
 * Material thumb and hit-testing all read - so a new arc geometry works with all 24 [VolumeStyle]s
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
    METER,
    /** Matching left and right bezel arcs; either side can be dragged. */
    DOUBLE_EDGE,
    /** Upright bottom-to-top level meter near the left side. */
    VERTICAL_LEFT,
    /** Upright bottom-to-top level meter near the right side. */
    VERTICAL_RIGHT,
    /** Horizontal level meter above the readout. */
    METER_TOP,
    /** Horizontal level meter close to the lower edge. */
    METER_BOTTOM,
    /** Compact centre dial with a radial value needle. */
    DIAL;

    companion object {
        fun fromPref(value: String?): VolumeLayout = when (value) {
            "halo" -> HALO
            "meter" -> METER
            "edge_tall" -> EDGE_TALL
            "edge_right" -> EDGE_RIGHT
            "edge_top" -> EDGE_TOP
            "edge_bottom" -> EDGE_BOTTOM
            "ring" -> RING
            "double_edge" -> DOUBLE_EDGE
            "vertical_left" -> VERTICAL_LEFT
            "vertical_right" -> VERTICAL_RIGHT
            "meter_top" -> METER_TOP
            "meter_bottom" -> METER_BOTTOM
            "dial" -> DIAL
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
        private const val DIAL_START_DEG = 120f
        private const val DIAL_SWEEP_DEG = 300f
    }

    private data class ArcGeometry(
            val bounds: RectF,
            val start: Float,
            val sweep: Float
    )

    // Scratch stroke paint reconfigured per style each draw; and a fill paint for the material thumb.
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val circleBounds = RectF()
    private val haloBounds = RectF()
    private val dialBounds = RectF()
    private val meterBounds = RectF()
    private val meterTopBounds = RectF()
    private val meterBottomBounds = RectF()
    private val verticalLeftBounds = RectF()
    private val verticalRightBounds = RectF()
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
    private var draggingArcIndex = 0

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

        val dialSize = min(viewSize - 76f * density, 104f * density)
                .coerceAtLeast(68f * density)
        dialBounds.set(
                measuredWidth / 2f - dialSize / 2f,
                measuredHeight / 2f - dialSize / 2f,
                measuredWidth / 2f + dialSize / 2f,
                measuredHeight / 2f + dialSize / 2f)

        val meterWidth = min(measuredWidth * .68f, 132f * density)
        val meterHeight = 11f * density
        val meterY = measuredHeight / 2f + 29f * density
        meterBounds.set(
                measuredWidth / 2f - meterWidth / 2f,
                meterY - meterHeight / 2f,
                measuredWidth / 2f + meterWidth / 2f,
                meterY + meterHeight / 2f)

        val edgeMeterWidth = min(measuredWidth * .58f, 116f * density)
        val upperY = measuredHeight / 2f - 45f * density
        val lowerY = measuredHeight / 2f + 45f * density
        meterTopBounds.set(
                measuredWidth / 2f - edgeMeterWidth / 2f,
                upperY - meterHeight / 2f,
                measuredWidth / 2f + edgeMeterWidth / 2f,
                upperY + meterHeight / 2f)
        meterBottomBounds.set(
                measuredWidth / 2f - edgeMeterWidth / 2f,
                lowerY - meterHeight / 2f,
                measuredWidth / 2f + edgeMeterWidth / 2f,
                lowerY + meterHeight / 2f)

        val verticalHeight = min(measuredHeight * .54f, 112f * density)
        val verticalWidth = meterHeight
        val verticalCenterY = measuredHeight / 2f
        val verticalInset = max(22f * density, (measuredWidth - viewSize) / 2f + 22f * density)
        verticalLeftBounds.set(
                verticalInset - verticalWidth / 2f,
                verticalCenterY - verticalHeight / 2f,
                verticalInset + verticalWidth / 2f,
                verticalCenterY + verticalHeight / 2f)
        verticalRightBounds.set(
                measuredWidth - verticalInset - verticalWidth / 2f,
                verticalCenterY - verticalHeight / 2f,
                measuredWidth - verticalInset + verticalWidth / 2f,
                verticalCenterY + verticalHeight / 2f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        when (barLayout) {
            VolumeLayout.METER,
            VolumeLayout.METER_TOP,
            VolumeLayout.METER_BOTTOM -> {
                drawLinearMeter(canvas, activeHorizontalMeterBounds(), vertical = false)
                return
            }
            VolumeLayout.VERTICAL_LEFT,
            VolumeLayout.VERTICAL_RIGHT -> {
                drawLinearMeter(canvas, activeVerticalMeterBounds(), vertical = true)
                return
            }
            else -> Unit
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
            VolumeStyle.BEADS -> {
                val beads = DashPathEffect(floatArrayOf(0.01f, baseStroke * 2.55f), 0f)
                strokePaint.pathEffect = beads
                drawArc(canvas, baseStroke * 1.45f, Paint.Cap.ROUND,
                        ColorUtils.setAlphaComponent(accentColorInt, 0x30), accentColorInt)
                strokePaint.pathEffect = null
            }
            VolumeStyle.DUAL -> drawDualArc(canvas)
            VolumeStyle.PULSE -> drawPulseArc(canvas)
            VolumeStyle.CHROME -> {
                drawArc(canvas, baseStroke * 1.55f, Paint.Cap.ROUND,
                        0x66000000, 0, fillShader = chromeShader())
                drawArc(canvas, baseStroke * 0.28f, Paint.Cap.ROUND,
                        0x22FFFFFF, 0xCCFFFFFF.toInt())
            }
            VolumeStyle.SPECTRUM ->
                drawArc(canvas, baseStroke * 1.25f, Paint.Cap.ROUND,
                        0x24FFFFFF, 0, fillShader = spectrumShader())
            VolumeStyle.STEPS -> drawVolumeSteps(canvas)
        }

        if (barLayout == VolumeLayout.DIAL) {
            drawDialNeedle(canvas)
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

    /** Neutral alternating highlights make the fill read as curved polished metal. */
    private fun chromeShader(): Shader {
        val bounds = activeArcBounds()
        return LinearGradient(
                bounds.left,
                bounds.top,
                bounds.right,
                bounds.bottom,
                intArrayOf(
                        0xFF62666B.toInt(),
                        0xFFF8FAFC.toInt(),
                        0xFF8B9097.toInt(),
                        0xFFFFFFFF.toInt(),
                        0xFF555A60.toInt()),
                floatArrayOf(0f, .22f, .48f, .7f, 1f),
                Shader.TileMode.CLAMP)
    }

    /** Fixed hue wheel, deliberately independent of the current cover palette. */
    private fun spectrumShader(): Shader {
        val bounds = activeArcBounds()
        return SweepGradient(
                bounds.centerX(),
                bounds.centerY(),
                intArrayOf(
                        0xFFFF3B30.toInt(),
                        0xFFFFCC00.toInt(),
                        0xFF34C759.toInt(),
                        0xFF32ADE6.toInt(),
                        0xFF5856D6.toInt(),
                        0xFFAF52DE.toInt(),
                        0xFFFF3B30.toInt()),
                null)
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
        drawArcGeometries(
                canvas = canvas,
                geometries = activeArcGeometries(),
                stroke = stroke,
                cap = cap,
                trackColor = trackColor,
                fillColor = fillColor,
                fillShader = fillShader)
    }

    private fun drawArcGeometries(
            canvas: Canvas,
            geometries: List<ArcGeometry>,
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
        geometries.forEach { geometry ->
            canvas.drawArc(
                    geometry.bounds, geometry.start, geometry.sweep, false, strokePaint)
        }

        if (volume > 0.001f) {
            if (fillShader != null) {
                strokePaint.shader = fillShader
            } else {
                strokePaint.shader = null
                strokePaint.color = fillColor
            }
            geometries.forEach { geometry ->
                canvas.drawArc(
                        geometry.bounds,
                        geometry.start,
                        volume * geometry.sweep,
                        false,
                        strokePaint)
            }
            strokePaint.shader = null
        }
    }

    /** Two separated rails remain readable even when the selected layout itself has two edges. */
    private fun drawDualArc(canvas: Canvas) {
        val geometries = activeArcGeometries()
        drawArcGeometries(
                canvas,
                geometries,
                baseStroke * .48f,
                Paint.Cap.ROUND,
                ColorUtils.setAlphaComponent(accentColorInt, 0x28),
                accentColorInt)
        val inner = geometries.map { geometry ->
            ArcGeometry(
                    RectF(geometry.bounds).apply { inset(baseStroke * 1.15f, baseStroke * 1.15f) },
                    geometry.start,
                    geometry.sweep)
        }
        drawArcGeometries(
                canvas,
                inner,
                baseStroke * .48f,
                Paint.Cap.ROUND,
                ColorUtils.setAlphaComponent(secondaryColorInt, 0x28),
                secondaryColorInt)
    }

    /** A thin trail plus three concentric alpha levels around the current-value marker. */
    private fun drawPulseArc(canvas: Canvas) {
        drawArc(
                canvas,
                baseStroke * .5f,
                Paint.Cap.ROUND,
                ColorUtils.setAlphaComponent(accentColorInt, 0x22),
                accentColorInt)
        if (volume <= .001f) return

        fillPaint.shader = null
        activeArcGeometries().forEach { geometry ->
            val angle = Math.toRadians((geometry.start + volume * geometry.sweep).toDouble())
            val radius = geometry.bounds.width() / 2f
            val x = geometry.bounds.centerX() + radius * cos(angle).toFloat()
            val y = geometry.bounds.centerY() + radius * sin(angle).toFloat()
            fillPaint.color = ColorUtils.setAlphaComponent(accentColorInt, 0x20)
            canvas.drawCircle(x, y, baseStroke * 2.1f, fillPaint)
            fillPaint.color = ColorUtils.setAlphaComponent(accentColorInt, 0x58)
            canvas.drawCircle(x, y, baseStroke * 1.25f, fillPaint)
            fillPaint.color = accentColorInt
            canvas.drawCircle(x, y, baseStroke * .52f, fillPaint)
        }
    }

    /** Twelve marks grow toward the loud end, unlike SEGMENTS' equal-width dash pattern. */
    private fun drawVolumeSteps(canvas: Canvas) {
        strokePaint.shader = null
        strokePaint.pathEffect = null
        strokePaint.strokeCap = Paint.Cap.BUTT
        val count = 12
        activeArcGeometries().forEach { geometry ->
            repeat(count) { index ->
                val fraction = (index + .5f) / count
                val angle = Math.toRadians(
                        (geometry.start + geometry.sweep * fraction).toDouble())
                val length = baseStroke * (.65f + 1.25f * fraction)
                val radius = geometry.bounds.width() / 2f
                val cosAngle = cos(angle).toFloat()
                val sinAngle = sin(angle).toFloat()
                strokePaint.strokeWidth = baseStroke * (.38f + .35f * fraction)
                strokePaint.color = if (fraction <= volume + .001f) {
                    accentColorInt
                } else {
                    ColorUtils.setAlphaComponent(accentColorInt, 0x2C)
                }
                canvas.drawLine(
                        geometry.bounds.centerX() + (radius - length) * cosAngle,
                        geometry.bounds.centerY() + (radius - length) * sinAngle,
                        geometry.bounds.centerX() + radius * cosAngle,
                        geometry.bounds.centerY() + radius * sinAngle,
                        strokePaint)
            }
        }
    }

    /** Material slider-style dot sitting on the arc at the current volume level. */
    private fun drawThumb(canvas: Canvas, color: Int) {
        fillPaint.color = color
        fillPaint.shader = null
        activeArcGeometries().forEach { geometry ->
            val angleRad = Math.toRadians(
                    (geometry.start + volume * geometry.sweep).toDouble())
            val radius = geometry.bounds.width() / 2f
            canvas.drawCircle(
                    geometry.bounds.centerX() + radius * cos(angleRad).toFloat(),
                    geometry.bounds.centerY() + radius * sin(angleRad).toFloat(),
                    baseStroke * 0.95f,
                    fillPaint)
        }
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

    private fun activeArcBounds(): RectF = when (barLayout) {
        VolumeLayout.HALO -> haloBounds
        VolumeLayout.DIAL -> dialBounds
        else -> circleBounds
    }

    private fun activeArcGeometries(): List<ArcGeometry> {
        if (barLayout == VolumeLayout.DOUBLE_EDGE) {
            return listOf(
                    ArcGeometry(circleBounds, ARC_START_DEG, ARC_SWEEP_DEG),
                    ArcGeometry(circleBounds, 50f, -ARC_SWEEP_DEG))
        }
        return listOf(ArcGeometry(activeArcBounds(), activeArcStart(), activeArcSweep()))
    }

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
        VolumeLayout.DIAL -> DIAL_START_DEG
        else -> ARC_START_DEG
    }

    private fun activeArcSweep(): Float = when (barLayout) {
        VolumeLayout.HALO -> HALO_SWEEP_DEG
        VolumeLayout.EDGE_TALL -> 160f
        VolumeLayout.EDGE_RIGHT -> -100f
        VolumeLayout.EDGE_TOP -> 70f
        VolumeLayout.EDGE_BOTTOM -> -70f
        VolumeLayout.RING -> 360f
        VolumeLayout.DIAL -> DIAL_SWEEP_DEG
        else -> ARC_SWEEP_DEG
    }

    /** The dial layout adds a real radial pointer and hub instead of reading as a smaller halo. */
    private fun drawDialNeedle(canvas: Canvas) {
        val geometry = activeArcGeometries().first()
        val angle = Math.toRadians((geometry.start + geometry.sweep * volume).toDouble())
        val cosAngle = cos(angle).toFloat()
        val sinAngle = sin(angle).toFloat()
        val radius = geometry.bounds.width() / 2f
        val cx = geometry.bounds.centerX()
        val cy = geometry.bounds.centerY()

        strokePaint.pathEffect = null
        strokePaint.shader = null
        strokePaint.strokeCap = Paint.Cap.ROUND
        strokePaint.strokeWidth = baseStroke * .72f
        strokePaint.color = 0x66000000
        canvas.drawLine(
                cx + radius * .13f * cosAngle + baseStroke * .2f,
                cy + radius * .13f * sinAngle + baseStroke * .2f,
                cx + radius * .62f * cosAngle + baseStroke * .2f,
                cy + radius * .62f * sinAngle + baseStroke * .2f,
                strokePaint)
        strokePaint.strokeWidth = baseStroke * .42f
        strokePaint.color = if (barStyle == VolumeStyle.CHROME) {
            0xFFFFFFFF.toInt()
        } else {
            accentColorInt
        }
        canvas.drawLine(
                cx + radius * .13f * cosAngle,
                cy + radius * .13f * sinAngle,
                cx + radius * .62f * cosAngle,
                cy + radius * .62f * sinAngle,
                strokePaint)
        fillPaint.shader = null
        fillPaint.color = strokePaint.color
        canvas.drawCircle(cx, cy, baseStroke * .62f, fillPaint)
    }

    private fun activeHorizontalMeterBounds(): RectF = when (barLayout) {
        VolumeLayout.METER_TOP -> meterTopBounds
        VolumeLayout.METER_BOTTOM -> meterBottomBounds
        else -> meterBounds
    }

    private fun activeVerticalMeterBounds(): RectF =
            if (barLayout == VolumeLayout.VERTICAL_RIGHT) {
                verticalRightBounds
            } else {
                verticalLeftBounds
            }

    /** Linear compositions use the same style vocabulary in either orientation. Vertical meters
     * fill bottom-to-top, while horizontal ones keep the established left-to-right direction. */
    private fun drawLinearMeter(canvas: Canvas, bounds: RectF, vertical: Boolean) {
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

        when (barStyle) {
            VolumeStyle.BEADS -> {
                drawBeadMeter(canvas, bounds, vertical, fillColor)
                return
            }
            VolumeStyle.DUAL -> {
                drawDualMeter(canvas, bounds, vertical)
                return
            }
            VolumeStyle.PULSE -> {
                drawPulseMeter(canvas, bounds, vertical)
                return
            }
            VolumeStyle.STEPS -> {
                drawStepMeter(canvas, bounds, vertical, fillColor)
                return
            }
            VolumeStyle.SEGMENTS -> {
                drawSegmentMeter(canvas, bounds, vertical, fillColor)
                return
            }
            else -> Unit
        }

        val fillShader = linearMeterShader(bounds, vertical)
        drawBasicLinearMeter(
                canvas, bounds, vertical, trackColor, fillColor, fillShader)

        if (barStyle == VolumeStyle.CHROME && volume > .001f) {
            // A single offset highlight turns the neutral gradient into a visibly polished rail.
            strokePaint.shader = null
            strokePaint.pathEffect = null
            strokePaint.strokeCap = Paint.Cap.ROUND
            strokePaint.strokeWidth = min(bounds.width(), bounds.height()) * .16f
            strokePaint.color = 0xB3FFFFFF.toInt()
            val point = linearPoint(bounds, vertical, volume)
            if (vertical) {
                val x = bounds.centerX() - bounds.width() * .18f
                canvas.drawLine(x, bounds.bottom, x, point.second, strokePaint)
            } else {
                val y = bounds.centerY() - bounds.height() * .18f
                canvas.drawLine(bounds.left, y, point.first, y, strokePaint)
            }
        }

        if (barStyle in setOf(VolumeStyle.MATERIAL, VolumeStyle.GLASS, VolumeStyle.TONAL)) {
            val point = linearPoint(bounds, vertical, volume)
            fillPaint.shader = null
            fillPaint.color = Color.WHITE
            canvas.drawCircle(
                    point.first,
                    point.second,
                    min(bounds.width(), bounds.height()) * .36f,
                    fillPaint)
        }
    }

    private fun drawBasicLinearMeter(
            canvas: Canvas,
            bounds: RectF,
            vertical: Boolean,
            trackColor: Int,
            fillColor: Int,
            fillShader: Shader? = null
    ) {
        val radius = min(bounds.width(), bounds.height()) / 2f
        fillPaint.shader = null
        fillPaint.color = trackColor
        canvas.drawRoundRect(bounds, radius, radius, fillPaint)

        if (volume > .001f) {
            val fill = if (vertical) {
                RectF(
                        bounds.left,
                        bounds.bottom - bounds.height() * volume,
                        bounds.right,
                        bounds.bottom)
            } else {
                RectF(
                        bounds.left,
                        bounds.top,
                        bounds.left + bounds.width() * volume,
                        bounds.bottom)
            }
            fillPaint.shader = fillShader
            fillPaint.color = fillColor
            canvas.drawRoundRect(fill, radius, radius, fillPaint)
            fillPaint.shader = null
        }
    }

    private fun linearMeterShader(bounds: RectF, vertical: Boolean): Shader? = when (barStyle) {
        VolumeStyle.GRADIENT,
        VolumeStyle.AURORA,
        VolumeStyle.PRISM -> if (vertical) {
            LinearGradient(
                    bounds.centerX(), bounds.bottom, bounds.centerX(), bounds.top,
                    tertiaryColorInt, secondaryColorInt, Shader.TileMode.CLAMP)
        } else {
            LinearGradient(
                    bounds.left, bounds.centerY(), bounds.right, bounds.centerY(),
                    tertiaryColorInt, secondaryColorInt, Shader.TileMode.CLAMP)
        }
        VolumeStyle.CHROME -> {
            val colors = intArrayOf(
                    0xFF555A60.toInt(), 0xFFF8FAFC.toInt(), 0xFF81868D.toInt(),
                    0xFFFFFFFF.toInt(), 0xFF4D5258.toInt())
            val stops = floatArrayOf(0f, .2f, .48f, .72f, 1f)
            if (vertical) {
                LinearGradient(
                        bounds.left, bounds.centerY(), bounds.right, bounds.centerY(),
                        colors, stops, Shader.TileMode.CLAMP)
            } else {
                LinearGradient(
                        bounds.centerX(), bounds.top, bounds.centerX(), bounds.bottom,
                        colors, stops, Shader.TileMode.CLAMP)
            }
        }
        VolumeStyle.SPECTRUM -> {
            val colors = intArrayOf(
                    0xFFFF3B30.toInt(), 0xFFFFCC00.toInt(), 0xFF34C759.toInt(),
                    0xFF32ADE6.toInt(), 0xFF5856D6.toInt(), 0xFFAF52DE.toInt())
            if (vertical) {
                LinearGradient(
                        bounds.centerX(), bounds.bottom, bounds.centerX(), bounds.top,
                        colors, null, Shader.TileMode.CLAMP)
            } else {
                LinearGradient(
                        bounds.left, bounds.centerY(), bounds.right, bounds.centerY(),
                        colors, null, Shader.TileMode.CLAMP)
            }
        }
        else -> null
    }

    private fun drawSegmentMeter(
            canvas: Canvas,
            bounds: RectF,
            vertical: Boolean,
            fillColor: Int
    ) {
        val count = 10
        val gap = resources.displayMetrics.density * 3f
        val axisLength = if (vertical) bounds.height() else bounds.width()
        val segmentLength = (axisLength - gap * (count - 1)) / count
        repeat(count) { index ->
            val fraction = (index + 1f) / count
            fillPaint.shader = null
            fillPaint.color = if (fraction <= volume + .001f) {
                fillColor
            } else {
                ColorUtils.setAlphaComponent(fillColor, 0x28)
            }
            val segment = if (vertical) {
                val bottom = bounds.bottom - index * (segmentLength + gap)
                RectF(bounds.left, bottom - segmentLength, bounds.right, bottom)
            } else {
                val left = bounds.left + index * (segmentLength + gap)
                RectF(left, bounds.top, left + segmentLength, bounds.bottom)
            }
            val radius = min(segment.width(), segment.height()) / 2f
            canvas.drawRoundRect(segment, radius, radius, fillPaint)
        }
    }

    private fun drawBeadMeter(
            canvas: Canvas,
            bounds: RectF,
            vertical: Boolean,
            fillColor: Int
    ) {
        val count = 9
        val beadRadius = min(bounds.width(), bounds.height()) * .48f
        fillPaint.shader = null
        repeat(count) { index ->
            val fraction = index.toFloat() / (count - 1)
            val point = linearPoint(bounds, vertical, fraction, beadRadius)
            fillPaint.color = if (volume > .001f && fraction <= volume + .001f) {
                fillColor
            } else {
                ColorUtils.setAlphaComponent(fillColor, 0x2C)
            }
            val majorScale = if (index == 0 || index == count - 1) 1f else .78f
            canvas.drawCircle(
                    point.first, point.second, beadRadius * majorScale, fillPaint)
        }
    }

    private fun drawDualMeter(canvas: Canvas, bounds: RectF, vertical: Boolean) {
        val first: RectF
        val second: RectF
        if (vertical) {
            val rail = bounds.width() * .3f
            first = RectF(bounds.left, bounds.top, bounds.left + rail, bounds.bottom)
            second = RectF(bounds.right - rail, bounds.top, bounds.right, bounds.bottom)
        } else {
            val rail = bounds.height() * .3f
            first = RectF(bounds.left, bounds.top, bounds.right, bounds.top + rail)
            second = RectF(bounds.left, bounds.bottom - rail, bounds.right, bounds.bottom)
        }
        drawBasicLinearMeter(
                canvas,
                first,
                vertical,
                ColorUtils.setAlphaComponent(accentColorInt, 0x28),
                accentColorInt)
        drawBasicLinearMeter(
                canvas,
                second,
                vertical,
                ColorUtils.setAlphaComponent(secondaryColorInt, 0x28),
                secondaryColorInt)
    }

    private fun drawPulseMeter(canvas: Canvas, bounds: RectF, vertical: Boolean) {
        val slim = RectF(bounds)
        if (vertical) {
            slim.inset(bounds.width() * .31f, 0f)
        } else {
            slim.inset(0f, bounds.height() * .31f)
        }
        drawBasicLinearMeter(
                canvas,
                slim,
                vertical,
                ColorUtils.setAlphaComponent(accentColorInt, 0x22),
                accentColorInt)
        if (volume <= .001f) return
        val point = linearPoint(bounds, vertical, volume)
        val base = min(bounds.width(), bounds.height())
        fillPaint.shader = null
        fillPaint.color = ColorUtils.setAlphaComponent(accentColorInt, 0x20)
        canvas.drawCircle(point.first, point.second, base * 1.3f, fillPaint)
        fillPaint.color = ColorUtils.setAlphaComponent(accentColorInt, 0x60)
        canvas.drawCircle(point.first, point.second, base * .78f, fillPaint)
        fillPaint.color = accentColorInt
        canvas.drawCircle(point.first, point.second, base * .34f, fillPaint)
    }

    private fun drawStepMeter(
            canvas: Canvas,
            bounds: RectF,
            vertical: Boolean,
            fillColor: Int
    ) {
        val count = 9
        val gap = resources.displayMetrics.density * 2f
        val axisLength = if (vertical) bounds.height() else bounds.width()
        val segmentLength = (axisLength - gap * (count - 1)) / count
        fillPaint.shader = null
        repeat(count) { index ->
            val fraction = (index + 1f) / count
            val crossScale = .3f + .7f * fraction
            fillPaint.color = if (fraction <= volume + .001f) {
                fillColor
            } else {
                ColorUtils.setAlphaComponent(fillColor, 0x2C)
            }
            val step = if (vertical) {
                val bottom = bounds.bottom - index * (segmentLength + gap)
                val width = bounds.width() * crossScale
                RectF(
                        bounds.centerX() - width / 2f,
                        bottom - segmentLength,
                        bounds.centerX() + width / 2f,
                        bottom)
            } else {
                val left = bounds.left + index * (segmentLength + gap)
                val height = bounds.height() * crossScale
                RectF(
                        left,
                        bounds.centerY() - height / 2f,
                        left + segmentLength,
                        bounds.centerY() + height / 2f)
            }
            canvas.drawRoundRect(step, baseStroke * .18f, baseStroke * .18f, fillPaint)
        }
    }

    /** Point on a linear meter. [edgeInset] keeps large beads inside their nominal bounds. */
    private fun linearPoint(
            bounds: RectF,
            vertical: Boolean,
            fraction: Float,
            edgeInset: Float = 0f
    ): Pair<Float, Float> {
        val safeFraction = fraction.coerceIn(0f, 1f)
        return if (vertical) {
            Pair(
                    bounds.centerX(),
                    bounds.bottom - edgeInset -
                            (bounds.height() - edgeInset * 2f) * safeFraction)
        } else {
            Pair(
                    bounds.left + edgeInset +
                            (bounds.width() - edgeInset * 2f) * safeFraction,
                    bounds.centerY())
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (barLayout) {
            VolumeLayout.METER,
            VolumeLayout.METER_TOP,
            VolumeLayout.METER_BOTTOM ->
                return handleLinearMeterTouch(
                        event, activeHorizontalMeterBounds(), vertical = false)
            VolumeLayout.VERTICAL_LEFT,
            VolumeLayout.VERTICAL_RIGHT ->
                return handleLinearMeterTouch(
                        event, activeVerticalMeterBounds(), vertical = true)
            else -> Unit
        }

        val geometries = activeArcGeometries()

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val touchedIndex = geometries.indexOfFirst { geometry ->
                    isTouchOnArc(event.x, event.y, geometry)
                }
                if (touchedIndex < 0) return false
                draggingArcIndex = touchedIndex
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

        val geometry = geometries[draggingArcIndex.coerceIn(0, geometries.lastIndex)]
        val fraction = arcTouchFraction(event.x, event.y, geometry)
        volume = fraction
        onVolumeChanged?.invoke(fraction)

        return true
    }

    private fun isTouchOnArc(x: Float, y: Float, geometry: ArcGeometry): Boolean {
        val dx = x - geometry.bounds.centerX()
        val dy = y - geometry.bounds.centerY()
        val radius = geometry.bounds.width() / 2f
        if (kotlin.math.abs(hypot(dx, dy) - radius) > touchBand) return false

        val angleDeg = (Math.toDegrees(atan2(dy, dx).toDouble()) + 360.0) % 360.0
        val delta = directedAngleDelta(angleDeg, geometry.start, geometry.sweep)
        return delta <= kotlin.math.abs(geometry.sweep).toDouble()
    }

    private fun arcTouchFraction(x: Float, y: Float, geometry: ArcGeometry): Float {
        val dx = x - geometry.bounds.centerX()
        val dy = y - geometry.bounds.centerY()
        val angleDeg = (Math.toDegrees(atan2(dy, dx).toDouble()) + 360.0) % 360.0
        val span = kotlin.math.abs(geometry.sweep).toDouble()
        return (directedAngleDelta(angleDeg, geometry.start, geometry.sweep) / span)
                .toFloat()
                .coerceIn(0f, 1f)
    }

    /** Angular distance from [start] measured in [sweep]'s drawing/fill direction. */
    private fun directedAngleDelta(angle: Double, start: Float, sweep: Float): Double =
            if (sweep >= 0f) {
                ((angle - start) + 360.0) % 360.0
            } else {
                ((start - angle) + 360.0) % 360.0
            }

    private fun handleLinearMeterTouch(
            event: MotionEvent,
            bounds: RectF,
            vertical: Boolean
    ): Boolean {
        val expanded = RectF(bounds).apply { inset(-touchBand, -touchBand) }
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
        val fraction = if (vertical) {
            ((bounds.bottom - event.y) / bounds.height()).coerceIn(0f, 1f)
        } else {
            ((event.x - bounds.left) / bounds.width()).coerceIn(0f, 1f)
        }
        volume = fraction
        onVolumeChanged?.invoke(fraction)
        return true
    }
}
