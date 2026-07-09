package com.svartifoss.snfell.watch.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.core.graphics.ColorUtils
import com.svartifoss.snfell.R
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** User-selectable visual style of the volume overlay (see [MiscPreferences.WEAR_VOLUME_STYLE] on
 *  the phone). All four keep the same left-edge arc geometry - and therefore the same angle-based
 *  drag - and only differ in how the arc is painted, so touch handling is identical. */
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
    /** Accent vertical-gradient fill. */
    GRADIENT,
    /** Neutral greyscale fill, ignoring the album accent. */
    MONO,
    /** Bold chunky bar (cartoon outline). */
    OUTLINE,
    /** Two-hue: complementary track, accent fill. */
    DUOTONE,
    /** Pure white fill on a faint track (high contrast). */
    CONTRAST,
    /** Monochrome-green CRT fill. */
    TERMINAL,
    /** Brighter frosted track with an accent fill. */
    FROST;

    companion object {
        fun fromPref(value: String?): VolumeStyle = when (value) {
            "minimal" -> MINIMAL
            "material" -> MATERIAL
            "tonal" -> TONAL
            "neon" -> NEON
            "light" -> LIGHT
            "gradient" -> GRADIENT
            "mono" -> MONO
            "outline" -> OUTLINE
            "duotone" -> DUOTONE
            "contrast" -> CONTRAST
            "terminal" -> TERMINAL
            "frost" -> FROST
            else -> GLASS
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
        private const val ARC_END_DEG = ARC_START_DEG + ARC_SWEEP_DEG
    }

    // Scratch stroke paint reconfigured per style each draw; and a fill paint for the material thumb.
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val circleBounds = RectF()
    private val touchBand = resources.getDimension(R.dimen.seek_bar_touch_band)

    private val baseStroke = resources.getDimension(R.dimen.seek_bar_width)
    // The bounds inset uses the fattest style's stroke so no style ever clips at the screen edge.
    private val maxStroke = baseStroke * 1.7f
    private val glassTrackColor = resources.getColor(R.color.music_screen_volume_bar_background_color, null)

    // The accent (album/theme) color, set via progressColor. Kept in its own field so per-draw
    // recolouring of the scratch paints never clobbers it.
    private var accentColorInt = resources.getColor(R.color.music_screen_volume_bar_foreground_color, null)

    var barStyle: VolumeStyle = VolumeStyle.GLASS
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
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

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
                drawArc(canvas, baseStroke, Paint.Cap.ROUND, tonal(complementary(accentColorInt), 0.30f), accentColorInt)
            VolumeStyle.CONTRAST ->
                drawArc(canvas, baseStroke * 1.3f, Paint.Cap.BUTT, 0x55FFFFFF, 0xFFFFFFFF.toInt())
            VolumeStyle.TERMINAL ->
                drawArc(canvas, baseStroke * 0.9f, Paint.Cap.BUTT, ColorUtils.setAlphaComponent(TERMINAL_GREEN, 0x40), TERMINAL_GREEN)
            VolumeStyle.FROST ->
                drawArc(canvas, baseStroke, Paint.Cap.ROUND, 0x44FFFFFF, accentColorInt)
        }
    }

    /** The album accent's complementary hue (used by the duotone style). */
    private fun complementary(accent: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(accent, hsl)
        hsl[0] = (hsl[0] + 180f) % 360f
        return ColorUtils.HSLToColor(hsl)
    }

    /** Vertical accent gradient (light at top, dark at bottom) spanning the arc bounds, for the
     *  gradient style's fill. */
    private fun verticalArcShader(): Shader = LinearGradient(
            circleBounds.left, circleBounds.top, circleBounds.left, circleBounds.bottom,
            tonal(accentColorInt, 0.62f), tonal(accentColorInt, 0.30f), Shader.TileMode.CLAMP
    )

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
        canvas.drawArc(circleBounds, ARC_START_DEG, ARC_SWEEP_DEG, false, strokePaint)

        if (volume > 0.001f) {
            if (fillShader != null) {
                strokePaint.shader = fillShader
            } else {
                strokePaint.shader = null
                strokePaint.color = fillColor
            }
            canvas.drawArc(circleBounds, ARC_START_DEG, volume * ARC_SWEEP_DEG, false, strokePaint)
            strokePaint.shader = null
        }
    }

    /** Material slider-style dot sitting on the arc at the current volume level. */
    private fun drawThumb(canvas: Canvas, color: Int) {
        val angleRad = Math.toRadians((ARC_START_DEG + volume * ARC_SWEEP_DEG).toDouble())
        val radius = circleBounds.width() / 2f
        fillPaint.color = color
        canvas.drawCircle(
                circleBounds.centerX() + radius * cos(angleRad).toFloat(),
                circleBounds.centerY() + radius * sin(angleRad).toFloat(),
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val centerX = circleBounds.centerX()
        val centerY = circleBounds.centerY()
        val radius = circleBounds.width() / 2
        val dx = event.x - centerX
        val dy = event.y - centerY
        val distanceFromCenter = hypot(dx, dy)

        // Same convention as Canvas.drawArc: 0deg = East, increasing clockwise (screen Y is
        // already "down", so atan2(dy, dx) lines up with it directly, no extra offset needed).
        val angleDeg = (Math.toDegrees(atan2(dy, dx).toDouble()) + 360.0) % 360.0
        val withinArc = angleDeg in ARC_START_DEG.toDouble()..ARC_END_DEG.toDouble()

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

        val fraction = ((angleDeg - ARC_START_DEG) / ARC_SWEEP_DEG).toFloat().coerceIn(0f, 1f)
        volume = fraction
        onVolumeChanged?.invoke(fraction)

        return true
    }
}
