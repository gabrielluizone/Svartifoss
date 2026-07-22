package com.svartifoss.snfell.view.settings

import android.animation.TimeAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.svartifoss.snfell.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * A small stylized, animated illustration of Svartifoss - the Icelandic waterfall the app is
 * named after - for the "you found the hidden waterfall" easter egg: black basalt columns
 * framing a falling cascade into a pool.
 *
 * Purely procedural (no bundled image/video), so it stays lightweight and scales to any dialog
 * width. Two design rules keep it smooth and un-schematic:
 *  - The cascade is drawn as continuous wobbling strands over a soft veil, with the sense of
 *    downward flow carried by a *repeating* vertical gradient (bright bands) whose local matrix
 *    is translated over time - not dashed lines, which read as dots rather than water.
 *  - Motion is driven by a [TimeAnimator]'s monotonically increasing timestamp, never a 0..1
 *    animator that resets. Every animated quantity is either periodic (`sin(t*w)`) or wrapped
 *    against its own tile period (`(t*speed).mod(period)`), so a REPEAT-tiled shader scrolls
 *    forever with no discontinuity - the old loop "snap" came from linear-in-phase terms
 *    jumping when the phase wrapped 1 -> 0.
 *
 * Column/strand geometry is regenerated with a fixed seed on resize, so it's stable rather than
 * reshuffling every frame.
 */
class SvartifossWaterfallView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density

    private val voidColor = ContextCompat.getColor(context, R.color.lyra_void)
    private val carbonColor = ContextCompat.getColor(context, R.color.lyra_carbon)
    private val shadowColor = ContextCompat.getColor(context, R.color.lyra_shadow)
    private val snowColor = ContextCompat.getColor(context, R.color.lyra_snow)
    private val accentColor = ContextCompat.getColor(context, R.color.lyra_accent)

    private val rockPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val striationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
        color = ColorUtils.setAlphaComponent(Color.BLACK, 90)
    }
    private val snowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = snowColor }
    private val veilPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val sprayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val mistPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val poolPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val poolShimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val clipPath = Path()
    private val veilPath = Path()
    private val strandPath = Path()
    private val flowMatrix = Matrix()
    private val shimmerMatrix = Matrix()

    private data class Column(val x: Float, val width: Float, val top: Float, val darker: Boolean)
    private data class Strand(val x: Float, val amplitude: Float, val phase: Float, val strokeWidth: Float, val alpha: Int)
    private data class Spray(val x: Float, val size: Float, val phase: Float, val speed: Float)

    private var columns: List<Column> = emptyList()
    private var strands: List<Strand> = emptyList()
    private var sprays: List<Spray> = emptyList()

    private var poolTop = 0f
    private var flowPeriod = 46f
    private var shimmerPeriod = 16f

    private var flowShader: LinearGradient? = null
    private var poolShimmerShader: LinearGradient? = null

    private var timeSeconds = 0f
    private val animator = TimeAnimator().apply {
        setTimeListener { _, totalTimeMs, _ ->
            timeSeconds = totalTimeMs / 1000f
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            buildScene(w.toFloat(), h.toFloat())
        }
    }

    private fun buildScene(w: Float, h: Float) {
        val random = Random(SEED)
        poolTop = h * POOL_TOP_FRACTION

        val gapCenter = (GAP_START_FRACTION + GAP_END_FRACTION) / 2f
        val halfGap = (GAP_END_FRACTION - GAP_START_FRACTION) / 2f

        val newColumns = mutableListOf<Column>()
        var x = 0f
        while (x < w) {
            val colWidth = (COLUMN_MIN_WIDTH_DP + random.nextFloat() * COLUMN_WIDTH_VARIANCE_DP) * density
            val t = (x + colWidth / 2f) / w
            val distancePastGap = (abs(t - gapCenter) - halfGap).coerceAtLeast(0f)
            val falloff = (distancePastGap / EDGE_FALLOFF_FRACTION).coerceIn(0f, 1f)
            val heightFraction = RIM_MIN_FRACTION + (RIM_MAX_FRACTION - RIM_MIN_FRACTION) * falloff
            newColumns.add(Column(x, colWidth, h * (1f - heightFraction), random.nextBoolean()))
            x += colWidth + COLUMN_GAP_DP * density
        }
        columns = newColumns

        val gapStartPx = w * GAP_START_FRACTION
        val gapEndPx = w * GAP_END_FRACTION

        // The cascade veil: a soft translucent sheet, narrow at the lip and fanning slightly as
        // it falls into the pool - the base the brighter strands are drawn over.
        val spread = (gapEndPx - gapStartPx) * VEIL_SPREAD_FRACTION
        veilPath.reset()
        veilPath.moveTo(gapStartPx, 0f)
        veilPath.lineTo(gapEndPx, 0f)
        veilPath.lineTo(gapEndPx + spread, poolTop)
        veilPath.lineTo(gapStartPx - spread, poolTop)
        veilPath.close()
        veilPaint.shader = LinearGradient(
                0f, 0f, 0f, poolTop,
                intArrayOf(
                        ColorUtils.setAlphaComponent(Color.WHITE, 16),
                        ColorUtils.setAlphaComponent(Color.WHITE, 42),
                        ColorUtils.setAlphaComponent(Color.WHITE, 20)),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
        )

        val newStrands = mutableListOf<Strand>()
        for (i in 0 until STRAND_COUNT) {
            val fx = gapStartPx + (gapEndPx - gapStartPx) * (i + 0.5f) / STRAND_COUNT
            newStrands.add(
                    Strand(
                            x = fx,
                            amplitude = (1.5f + random.nextFloat() * 3f) * density,
                            phase = random.nextFloat() * TWO_PI,
                            strokeWidth = (1.4f + random.nextFloat() * 2.2f) * density,
                            alpha = 90 + random.nextInt(120)
                    )
            )
        }
        strands = newStrands

        val newSprays = mutableListOf<Spray>()
        for (i in 0 until SPRAY_COUNT) {
            newSprays.add(
                    Spray(
                            x = gapStartPx - spread + (gapEndPx - gapStartPx + 2 * spread) * random.nextFloat(),
                            size = (1.1f + random.nextFloat() * 1.6f) * density,
                            phase = random.nextFloat(),
                            speed = 0.5f + random.nextFloat() * 0.6f
                    )
            )
        }
        sprays = newSprays

        // Repeating bright bands that, translated downward over time, read as flowing water.
        flowPeriod = FLOW_PERIOD_DP * density
        flowShader = LinearGradient(
                0f, 0f, 0f, flowPeriod,
                intArrayOf(
                        ColorUtils.setAlphaComponent(Color.WHITE, 30),
                        ColorUtils.setAlphaComponent(Color.WHITE, 190),
                        ColorUtils.setAlphaComponent(Color.WHITE, 30)),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.REPEAT
        )

        // Pool ripples: faint horizontal bands drifting down, same seamless-tile trick.
        shimmerPeriod = SHIMMER_PERIOD_DP * density
        poolShimmerShader = LinearGradient(
                0f, 0f, 0f, shimmerPeriod,
                intArrayOf(Color.TRANSPARENT, ColorUtils.setAlphaComponent(Color.WHITE, 26), Color.TRANSPARENT),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.REPEAT
        )

        // Built at full alpha; the pulse is applied per-frame via mistPaint.alpha so no shader is
        // reallocated each draw.
        val cx = w * gapCenter
        mistPaint.shader = RadialGradient(
                cx, poolTop, (gapEndPx - gapStartPx) * MIST_RADIUS_FACTOR + spread,
                Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!animator.isStarted) animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        clipPath.reset()
        clipPath.addRoundRect(RectF(0f, 0f, w, h), CORNER_RADIUS_DP * density, CORNER_RADIUS_DP * density, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)

        canvas.drawColor(voidColor)
        drawColumns(canvas, h)
        drawWaterfall(canvas)
        drawMist(canvas)
        drawPool(canvas, w, h)

        canvas.restore()
    }

    private fun drawColumns(canvas: Canvas, h: Float) {
        columns.forEach { col ->
            rockPaint.color = if (col.darker) shadowColor else carbonColor
            canvas.drawRect(col.x, col.top, col.x + col.width, h, rockPaint)
            // A couple of vertical striations hint at the hexagonal columnar basalt Svartifoss is
            // famous for, instead of flat blocks.
            val inset = col.width * 0.32f
            canvas.drawLine(col.x + inset, col.top, col.x + inset, h, striationPaint)
            canvas.drawLine(col.x + col.width - inset, col.top, col.x + col.width - inset, h, striationPaint)
            if (col.top < h * SNOW_LINE_FRACTION) {
                canvas.drawRect(col.x, col.top, col.x + col.width, col.top + SNOW_CAP_DP * density, snowPaint)
            }
        }
    }

    private fun drawWaterfall(canvas: Canvas) {
        canvas.drawPath(veilPath, veilPaint)

        val shader = flowShader ?: return
        flowMatrix.setTranslate(0f, (timeSeconds * FLOW_SPEED_DP * density).mod(flowPeriod))
        shader.setLocalMatrix(flowMatrix)
        strandPaint.shader = shader

        val step = 9f * density
        strands.forEach { s ->
            strandPath.reset()
            strandPath.moveTo(s.x, 0f)
            var y = 0f
            while (y < poolTop) {
                // Water fans out and grows choppier as it falls: a spread factor plus a second,
                // faster harmonic on top of the base wobble - continuous in time, so no seam.
                val spread = 1f + (y / poolTop) * 0.7f
                val wobble = sin(y / (26f * density) + s.phase + timeSeconds * BASE_WOBBLE_W) +
                        0.3f * sin(y / (10f * density) - timeSeconds * CHOP_WOBBLE_W)
                strandPath.lineTo(s.x + wobble * s.amplitude * spread, y)
                y += step
            }
            strandPaint.strokeWidth = s.strokeWidth
            strandPaint.alpha = s.alpha
            canvas.drawPath(strandPath, strandPaint)
        }
        strandPaint.alpha = 255

        // Spray specks rising from the plunge point and fading - a little life at the base.
        val rise = SPRAY_RISE_DP * density
        sprays.forEach { sp ->
            val progress = (timeSeconds * sp.speed + sp.phase).mod(1f)
            val y = poolTop - progress * rise
            // sin envelope: alpha is 0 at both progress 0 and 1, so a speck fades in as it rises
            // and fades out at the top - it never pops in or out when its cycle wraps.
            sprayPaint.alpha = (sin(progress * PI) * 150).toInt()
            canvas.drawCircle(sp.x, y, sp.size, sprayPaint)
        }
        sprayPaint.alpha = 255
    }

    private fun drawMist(canvas: Canvas) {
        val w = width.toFloat()
        // Continuous pulse (periodic in time), so it breathes without ever snapping.
        val pulse = 0.7f + 0.3f * sin(timeSeconds * MIST_PULSE_W)
        mistPaint.alpha = (135 * pulse).toInt()
        val cx = w * (GAP_START_FRACTION + GAP_END_FRACTION) / 2f
        canvas.drawCircle(cx, poolTop, height.toFloat(), mistPaint)
    }

    private fun drawPool(canvas: Canvas, w: Float, h: Float) {
        poolPaint.shader = LinearGradient(
                0f, poolTop, 0f, h,
                ColorUtils.blendARGB(accentColor, Color.BLACK, 0.35f),
                ColorUtils.blendARGB(accentColor, Color.BLACK, 0.78f),
                Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, poolTop, w, h, poolPaint)

        val shader = poolShimmerShader ?: return
        shimmerMatrix.setTranslate(0f, poolTop + (timeSeconds * SHIMMER_SPEED_DP * density).mod(shimmerPeriod))
        shader.setLocalMatrix(shimmerMatrix)
        poolShimmerPaint.shader = shader
        canvas.drawRect(0f, poolTop, w, h, poolShimmerPaint)
    }

    companion object {
        private const val SEED = 8112L
        private const val TWO_PI = 6.2832f

        private const val COLUMN_MIN_WIDTH_DP = 8f
        private const val COLUMN_WIDTH_VARIANCE_DP = 10f
        private const val COLUMN_GAP_DP = 1.5f
        private const val RIM_MIN_FRACTION = 0.18f
        private const val RIM_MAX_FRACTION = 0.7f
        private const val EDGE_FALLOFF_FRACTION = 0.3f
        private const val SNOW_LINE_FRACTION = 0.4f
        private const val SNOW_CAP_DP = 3f

        private const val GAP_START_FRACTION = 0.36f
        private const val GAP_END_FRACTION = 0.64f
        private const val POOL_TOP_FRACTION = 0.78f
        private const val VEIL_SPREAD_FRACTION = 0.28f

        private const val STRAND_COUNT = 11
        private const val FLOW_PERIOD_DP = 46f
        private const val FLOW_SPEED_DP = 170f
        private const val BASE_WOBBLE_W = 2.1f
        private const val CHOP_WOBBLE_W = 5.5f

        private const val SPRAY_COUNT = 12
        private const val SPRAY_RISE_DP = 26f

        private const val MIST_RADIUS_FACTOR = 0.9f
        private const val MIST_PULSE_W = 1.6f

        private const val SHIMMER_PERIOD_DP = 16f
        private const val SHIMMER_SPEED_DP = 9f

        private const val CORNER_RADIUS_DP = 12f
    }
}
