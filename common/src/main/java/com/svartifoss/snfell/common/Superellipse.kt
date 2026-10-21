package com.svartifoss.snfell.common

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * The continuous-curvature "app-icon" silhouette: a superellipse, `|x|^n + |y|^n = 1`.
 *
 * A rounded rectangle is not this shape however large its corners are. It runs dead straight along
 * each side and then turns a quarter circle, so the curvature jumps from nothing to a lot at the
 * join, while a superellipse bends a little all the way along, which is the look of a modern phone
 * icon. That is why [CoverShape.SUPERELLIPSE] cannot be one of the registry's four-radius
 * rectangles and has a path of its own.
 *
 * [EXPONENT] was fitted to a reference icon rather than chosen by eye: the silhouette's edge was
 * read off row by row, normalised to the unit square, and the exponent that best satisfies the
 * equation across the whole outline is 2.66 (the per-row estimates stay between 2.58 and 2.71, and
 * the residual is under a hundredth of the side). [SuperellipseTest] pins samples of that
 * measurement, so a "tidy" change to the number shows up as a different silhouette.
 *
 * Pure arithmetic and no `android.*` so the watch (Compose) and the phone preview (Canvas) build
 * the outline from the same points and cannot drift apart, which is the reason the corner-radius
 * shapes beside it are shared the same way.
 */
object Superellipse {

    /** The fitted exponent - see the class note. Two would be an ellipse, infinity a rectangle. */
    const val EXPONENT = 2.66f

    /**
     * Segments per quarter of the outline. The straight chords stay within about a thousandth of
     * the side of the true curve at this count, under a tenth of a pixel on any screen a cover is
     * drawn on, so the polygon reads as a smooth curve without a spline both toolkits would have
     * to agree on.
     */
    const val SEGMENTS_PER_QUARTER = 32

    /** Points in an [outline]: one per segment, around all four quarters. */
    const val POINT_COUNT = SEGMENTS_PER_QUARTER * 4

    /**
     * The outline of the superellipse that fills the box, as `x0, y0, x1, y1, ...` clockwise on a
     * y-down screen starting at the middle of the top edge. A box that is not square gives the
     * stretched figure, which is what a clip over a non-square area wants.
     */
    fun outline(left: Float, top: Float, right: Float, bottom: Float): FloatArray {
        val halfWidth = (right - left) / 2f
        val halfHeight = (bottom - top) / 2f
        val centerX = left + halfWidth
        val centerY = top + halfHeight
        val power = 2.0 / EXPONENT
        val points = FloatArray(POINT_COUNT * 2)
        for (index in 0 until POINT_COUNT) {
            val angle = -PI / 2.0 + 2.0 * PI * index / POINT_COUNT
            val c = cos(angle)
            val s = sin(angle)
            points[index * 2] = (centerX + halfWidth * sign(c) * abs(c).pow(power)).toFloat()
            points[index * 2 + 1] = (centerY + halfHeight * sign(s) * abs(s).pow(power)).toFloat()
        }
        return points
    }

    /**
     * How far in from the left edge of a [width] by [height] box the silhouette runs at height [y]
     * (measured from the top): zero at the middle, where it touches the edge, growing towards the
     * top and the bottom. Used to keep a queue's cover clear of the row's own outline.
     */
    fun leftInset(y: Float, width: Float, height: Float): Float {
        if (width <= 0f || height <= 0f) return 0f
        val halfHeight = height / 2f
        val away = (abs(y.coerceIn(0f, height) - halfHeight) / halfHeight).toDouble()
        val reach = (1.0 - away.pow(EXPONENT.toDouble())).coerceAtLeast(0.0)
                .pow(1.0 / EXPONENT)
        return (width / 2f * (1.0 - reach)).toFloat()
    }
}
