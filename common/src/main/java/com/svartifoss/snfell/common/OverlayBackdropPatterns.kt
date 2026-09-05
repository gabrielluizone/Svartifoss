package com.svartifoss.snfell.common

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.Matrix
import androidx.core.graphics.ColorUtils
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.abs

/**
 * Five [OverlayBackdrop] treatments built from a repeated structure - a grid, a set of rings, a
 * sweep - rather than the soft album-tinted gradients and blooms every other backdrop in the
 * catalogue is a variation of (Ember, Tideline, Horizon and the rest all resolve to "a wash that
 * fades"). These read as texture or geometry instead.
 *
 * Every function here draws with plain [Canvas]/[Paint] calls, which is what makes sharing them
 * possible in a way most of this app's drawing code cannot be: a Compose face and a Classic View
 * both draw with fundamentally different APIs, so `PlayerBackgroundTreatment` and
 * `PlayerBackgroundDrawable` stay two hand-written implementations of the same design that a test
 * only checks for structural agreement. `OverlayBackdropDrawables` (the watch, via a plain
 * `Drawable`) and `WatchPreviewView` (the phone preview, via its own `Canvas`) do not have that
 * problem - both already draw with the exact same `android.graphics` primitives - so these five
 * patterns are written once, here, and called identically from both. There is no second
 * implementation to drift out of sync with the first.
 *
 * Every pattern is deterministic in its own inputs (the resolved album colors) and nothing else:
 * no system clock, no random seed that isn't derived from the accent color, no animation. A
 * backdrop is meant to sit behind panel controls, not compete with them for attention, and a
 * static, reproducible pattern is what lets the phone preview show the truth before the user ever
 * looks at the watch.
 */
object OverlayBackdropPatterns {

    /**
     * A fine hex-offset grid of dots, like a technical schematic or a halftone print.
     *
     * The offset row (every other row shifted by half the pitch) is what keeps this from reading
     * as a plain rectangular grid, which at this dot size looks closer to a fabric weave than a
     * blueprint. Dots are drawn at one constant alpha rather than fading toward the edges - this
     * pattern's whole identity is that it is uniform, and a vignette on top of it would just be
     * [OverlayBackdrop.VIGNETTE] wearing a texture.
     */
    fun drawDotMatrix(
            canvas: Canvas,
            bounds: RectF,
            density: Float,
            baseColor: Int,
            dotColor: Int
    ) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = baseColor }
        canvas.drawRect(bounds, fill)

        val pitch = 11f * density
        val radius = 1.6f * density
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dotColor }

        var row = 0
        var y = bounds.top - pitch
        while (y < bounds.bottom + pitch) {
            val rowOffset = if (row % 2 == 0) 0f else pitch / 2f
            var x = bounds.left - pitch + rowOffset
            while (x < bounds.right + pitch) {
                canvas.drawCircle(x, y, radius, dot)
                x += pitch
            }
            y += pitch * 0.87f // hex packing: rows are closer than the dot pitch itself
            row++
        }
    }

    /**
     * Horizontal hairlines over a dark wash, alternating between two alphas so every other line
     * reads slightly brighter - the interlaced look of an old CRT rather than a flat repeating
     * stripe. A gentle top-to-bottom fade (brighter above, receding below) keeps it from reading
     * as an infinite, undifferentiated tile.
     */
    fun drawScanlines(
            canvas: Canvas,
            bounds: RectF,
            density: Float,
            baseColor: Int,
            lineColor: Int
    ) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = baseColor }
        canvas.drawRect(bounds, fill)

        val lineHeight = 1.4f * density
        val gap = 2.6f * density
        val pitch = lineHeight + gap
        val height = bounds.height().coerceAtLeast(1f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        var index = 0
        var y = bounds.top
        while (y < bounds.bottom) {
            val depthFraction = ((y - bounds.top) / height).coerceIn(0f, 1f)
            val brighter = index % 2 == 0
            val baseAlpha = if (brighter) 0x3A else 0x22
            // Fades from full strength at the top to about 40% of it at the bottom.
            val alpha = (baseAlpha * (1f - depthFraction * 0.6f)).toInt().coerceIn(0, 255)
            paint.color = ColorUtils.setAlphaComponent(lineColor, alpha)
            canvas.drawRect(bounds.left, y, bounds.right, min(y + lineHeight, bounds.bottom), paint)
            y += pitch
            index++
        }
    }

    /**
     * Concentric ring outlines from the centre outward, plus one fixed bright wedge - a radar
     * screen caught mid-sweep rather than a live animation, since a backdrop that keeps moving
     * behind panel controls competes with them for attention (and costs battery for no reading
     * anyone does twice). The sweep is held at a fixed screen angle rather than rotated by the
     * album accent, so it always points the same way on every theme - the rings are what carry
     * the album's colour.
     */
    fun drawRadarRings(
            canvas: Canvas,
            bounds: RectF,
            density: Float,
            cx: Float,
            cy: Float,
            radius: Float,
            baseColor: Int,
            ringColor: Int,
            sweepColor: Int
    ) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = baseColor }
        canvas.drawRect(bounds, fill)

        val sweep = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = SweepGradient(
                    cx, cy,
                    intArrayOf(
                            sweepColor, ColorUtils.setAlphaComponent(sweepColor, 0),
                            ColorUtils.setAlphaComponent(sweepColor, 0),
                            ColorUtils.setAlphaComponent(sweepColor, 0)),
                    floatArrayOf(0f, .22f, .75f, 1f)
            ).apply {
                // SweepGradient starts at the 3 o'clock position; rotate so the bright edge sits
                // at the upper-left, the conventional "radar sweep" resting point.
                val matrix = Matrix()
                matrix.setRotate(-135f, cx, cy)
                setLocalMatrix(matrix)
            }
        }
        canvas.drawCircle(cx, cy, radius, sweep)

        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f * density
            color = ColorUtils.setAlphaComponent(ringColor, 0x55)
        }
        val ringSpacing = radius / RADAR_RING_COUNT
        for (index in 1..RADAR_RING_COUNT) {
            canvas.drawCircle(cx, cy, ringSpacing * index, ringPaint)
        }
    }

    /**
     * Concentric closed contours, each nudged off a perfect circle by two low-order sine
     * harmonics - the deliberate opposite of [drawRadarRings]'s perfectly circular rings, so the
     * two textures never read as the same idea in different colours. The harmonics' phase is
     * derived from the album accent's own hue, so a given cover always produces the same contour
     * shape (never a different one on every open) while different covers visibly differ from one
     * another - the same "personal but reproducible" property [AccentFloorStyle] and the rest of
     * this app's album-derived treatments already have.
     */
    fun drawContourLines(
            canvas: Canvas,
            bounds: RectF,
            density: Float,
            cx: Float,
            cy: Float,
            radius: Float,
            baseColor: Int,
            lineColor: Int,
            accent: Int
    ) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = baseColor }
        canvas.drawRect(bounds, fill)

        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(accent, hsl)
        val phase = (hsl[0] / 360f) * (2 * PI).toFloat()

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f * density
            color = ColorUtils.setAlphaComponent(lineColor, 0x4A)
            isAntiAlias = true
        }
        val path = Path()
        val ringSpacing = radius / CONTOUR_RING_COUNT
        for (ring in 1..CONTOUR_RING_COUNT) {
            val baseRadius = ringSpacing * ring
            path.rewind()
            val steps = 96
            for (step in 0..steps) {
                val theta = (step.toFloat() / steps) * (2 * PI).toFloat()
                val wobble = 1f +
                        CONTOUR_AMPLITUDE_PRIMARY * sin(3f * theta + phase) +
                        CONTOUR_AMPLITUDE_SECONDARY * sin(5f * theta - phase * 1.7f)
                val r = baseRadius * wobble
                val x = cx + r * cos(theta)
                val y = cy + r * sin(theta)
                if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            canvas.drawPath(path, linePaint)
        }
    }

    /**
     * A low-poly field of triangular facets, each one a small deterministic tone step away from
     * its neighbours - a cut-gem surface rather than a smooth blend. The direction each cell's
     * diagonal splits alternates in a herringbone, so the facets read as a continuous cut surface
     * instead of one row of triangles repeated downward. Tone and diagonal direction both come
     * from a hash of the cell's own position mixed with the accent colour: reproducible for a
     * given album and grid, with no per-frame or per-launch randomness to disagree with the
     * preview.
     */
    fun drawFacetedCrystal(
            canvas: Canvas,
            bounds: RectF,
            density: Float,
            primary: Int,
            secondary: Int,
            tertiary: Int,
            accent: Int
    ) {
        val base = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tonalSurfaceForFacet(primary, 0.14f) }
        canvas.drawRect(bounds, base)

        val cellWidth = bounds.width() / FACET_GRID_SIZE
        val cellHeight = bounds.height() / FACET_GRID_SIZE
        val tones = intArrayOf(
                tonalSurfaceForFacet(primary, 0.20f),
                tonalSurfaceForFacet(secondary, 0.24f),
                tonalSurfaceForFacet(tertiary, 0.22f),
                tonalSurfaceForFacet(primary, 0.28f))
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.6f * density
            color = ColorUtils.setAlphaComponent(0x000000, 0x30)
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val path = Path()
        val cellSeed = facetSeed(accent)

        for (row in 0 until FACET_GRID_SIZE) {
            for (col in 0 until FACET_GRID_SIZE) {
                val left = bounds.left + col * cellWidth
                val top = bounds.top + row * cellHeight
                val right = left + cellWidth
                val bottom = top + cellHeight
                val cellHash = abs((row * 928371 + col * 121933 + cellSeed) and 0xFFFF)
                val diagonalFallsLeftToRight = (row + col) % 2 == 0

                if (diagonalFallsLeftToRight) {
                    drawTriangle(canvas, path, fillPaint, strokePaint,
                            left, top, right, top, left, bottom, tones[cellHash % tones.size])
                    drawTriangle(canvas, path, fillPaint, strokePaint,
                            right, top, right, bottom, left, bottom,
                            tones[(cellHash / 7) % tones.size])
                } else {
                    drawTriangle(canvas, path, fillPaint, strokePaint,
                            left, top, right, top, right, bottom, tones[cellHash % tones.size])
                    drawTriangle(canvas, path, fillPaint, strokePaint,
                            left, top, right, bottom, left, bottom,
                            tones[(cellHash / 7) % tones.size])
                }
            }
        }
    }

    /**
     * Tidal's three stroked waves, carried over from the player face of the same name.
     *
     * Here rather than in either renderer because the control points *are* the treatment: a wave
     * re-typed into the watch's Drawable and the phone's Canvas is two chances to get a cubic
     * slightly wrong, and the difference would show as the preview lying about the wrist rather
     * than as anything throwing. The player's Compose face still draws its own, since Compose
     * cannot share a `Path` builder with these two - but the numbers below are its numbers.
     */
    fun drawTidalWaves(
            canvas: Canvas,
            bounds: RectF,
            baseColor: Int,
            waveColors: IntArray
    ) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = baseColor }
        canvas.drawRect(bounds, fill)

        val span = minOf(bounds.width(), bounds.height())
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        floatArrayOf(.34f, .55f, .76f).forEachIndexed { index, y ->
            val path = Path().apply {
                moveTo(bounds.left, bounds.top + bounds.height() * y)
                cubicTo(
                        bounds.left + bounds.width() * .24f,
                        bounds.top + bounds.height() * (y - .11f + index * .02f),
                        bounds.left + bounds.width() * .70f,
                        bounds.top + bounds.height() * (y + .10f),
                        bounds.right,
                        bounds.top + bounds.height() * (y - .03f))
            }
            stroke.color = waveColors[index]
            stroke.strokeWidth = span * (.11f - index * .03f)
            canvas.drawPath(path, stroke)
        }
    }

    /** Nocturne's four points of light. Same reasoning as [drawTidalWaves]. */
    fun drawNocturneStars(canvas: Canvas, bounds: RectF, starColor: Int) {
        val span = minOf(bounds.width(), bounds.height())
        val star = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = starColor }
        listOf(.16f to .22f, .72f to .18f, .37f to .58f, .82f to .72f).forEach { (x, y) ->
            canvas.drawCircle(
                    bounds.left + bounds.width() * x,
                    bounds.top + bounds.height() * y,
                    span * .009f,
                    star)
        }
    }

    /**
     * A stroked ring carrying a sweep gradient, over [baseColor].
     *
     * Corona and Glass veil are the same object at different weights - a wide soft band hugging
     * the rim, and a fine bright hairline - so they share this rather than being two near-copies.
     *
     * [useMaxDimension] exists because Corona's ring is sized against the *longer* side and Glass
     * veil's against the shorter. On a round watch the two are the same number; in the phone
     * preview's rectangle they are not, and quietly picking one would make the miniature disagree
     * with the wrist on exactly one of the two.
     */
    fun drawSweepRing(
            canvas: Canvas,
            bounds: RectF,
            baseColor: Int,
            sweepColors: IntArray,
            radiusFraction: Float,
            strokeFraction: Float,
            useMaxDimension: Boolean = false,
            roundCap: Boolean = false
    ) {
        if (baseColor != 0) {
            canvas.drawRect(bounds, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = baseColor })
        }
        val span = minOf(bounds.width(), bounds.height())
        val basis = if (useMaxDimension) maxOf(bounds.width(), bounds.height()) else span
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = span * strokeFraction
            if (roundCap) strokeCap = Paint.Cap.ROUND
            shader = SweepGradient(bounds.centerX(), bounds.centerY(), sweepColors, null)
        }
        canvas.drawCircle(bounds.centerX(), bounds.centerY(), basis * radiusFraction, ring)
    }

    /** Crescent's single stroked arc, drawn on a box inflated past the bounds so the sweep runs
     *  off the edges rather than closing into a visible circle. */
    fun drawSweepArc(
            canvas: Canvas,
            bounds: RectF,
            baseColor: Int,
            sweepColors: IntArray,
            startAngle: Float,
            sweepAngle: Float,
            inflateFraction: Float,
            strokeFraction: Float
    ) {
        canvas.drawRect(bounds, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = baseColor })
        val span = minOf(bounds.width(), bounds.height())
        val inflateX = bounds.width() * inflateFraction
        val inflateY = bounds.height() * inflateFraction
        val oval = RectF(
                bounds.left - inflateX,
                bounds.top - inflateY,
                bounds.right + inflateX,
                bounds.bottom + inflateY)
        val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = span * strokeFraction
            shader = SweepGradient(oval.centerX(), oval.centerY(), sweepColors, null)
        }
        canvas.drawArc(oval, startAngle, sweepAngle, false, arc)
    }

    /** Grid's evenly spaced rules, [divisions] - 1 of them in each direction. */
    fun drawGridLines(
            canvas: Canvas,
            bounds: RectF,
            baseColor: Int,
            lineColor: Int,
            divisions: Int
    ) {
        canvas.drawRect(bounds, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = baseColor })
        val span = minOf(bounds.width(), bounds.height())
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = lineColor
            strokeWidth = span * .006f
        }
        for (step in 1 until divisions) {
            val x = bounds.left + bounds.width() * step / divisions.toFloat()
            val y = bounds.top + bounds.height() * step / divisions.toFloat()
            canvas.drawLine(x, bounds.top, x, bounds.bottom, line)
            canvas.drawLine(bounds.left, y, bounds.right, y, line)
        }
    }

    private fun drawTriangle(
            canvas: Canvas,
            path: Path,
            fillPaint: Paint,
            strokePaint: Paint,
            x0: Float, y0: Float,
            x1: Float, y1: Float,
            x2: Float, y2: Float,
            color: Int
    ) {
        path.rewind()
        path.moveTo(x0, y0)
        path.lineTo(x1, y1)
        path.lineTo(x2, y2)
        path.close()
        fillPaint.color = color
        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, strokePaint)
    }

    private fun tonalSurfaceForFacet(color: Int, lightness: Float): Int =
            PaletteTransforms.tonalSurface(color, lightness, minSat = 0.20f, maxSat = 0.55f)

    /** A small, deterministic integer derived from the accent so facet tone/direction vary per
     *  album without touching a system random source. */
    private fun facetSeed(accent: Int): Int = (accent and 0xFFFFFF) xor ((accent shr 8) and 0xFFFF)

    private const val RADAR_RING_COUNT = 4
    private const val CONTOUR_RING_COUNT = 5
    private const val CONTOUR_AMPLITUDE_PRIMARY = 0.05f
    private const val CONTOUR_AMPLITUDE_SECONDARY = 0.025f
    private const val FACET_GRID_SIZE = 7
}
