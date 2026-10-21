package com.svartifoss.snfell.common

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The outline of a row in the watch's queue, chosen apart from the style that paints it.
 *
 * The queue's style used to settle everything about a row at once - its colours, its outline, the
 * cut of its cover and its rhythm - so liking one style's outline meant taking its colours with it.
 * The outline is now a choice of its own ([MiscPreferences.WEAR_QUEUE_ROW_SHAPE]). [FOLLOW] keeps
 * the outline each style was drawn with, which is what every setting saved before this existed
 * resolves to, so nothing changes for anyone until they pick one.
 *
 * Only the outline. The style keeps its padding and spacing and [MiscPreferences.WEAR_LIST_ROW_SIZE]
 * keeps the height: square corners for Tonal should give Tonal with square corners, not Tonal
 * re-spaced into a denser list.
 *
 * Shared by the watch and the phone's miniature for the reason [MeterShape] is: both draw the same
 * outlines with different toolkits, and an outline whose corners are decided twice is one that
 * drifts.
 */
enum class QueueRowShape(val token: String) {
    /** The outline the queue style was drawn with. */
    FOLLOW("follow"),

    /** A capsule: both ends fully round, at every row height. */
    PILL("pill"),

    /** A card with generous corners. */
    ROUNDED("rounded"),

    /** A card with tight corners - what Cover Square used to be the only way to get. */
    SOFT("soft"),

    /** No rounding at all. */
    SQUARE("square"),

    /** Two opposite corners swept round and the other two tight: the leaf the mini buttons and
     *  the covers already have, so the app keeps one shape language. */
    LEAF("leaf"),

    /** The speech bubble the Bubble style is drawn with. */
    BUBBLE("bubble"),

    /** A basalt column on its side, pointed at both ends - the outline the Basalt style is drawn
     *  with. */
    HEXAGON("hexagon");

    /**
     * The outline this choice draws, or null for [FOLLOW], whose outline is the style's to say.
     *
     * A getter rather than a constructor argument: enum entries are built while the class
     * initialises, and an argument reaching into [QueueRowOutline]'s companion from there is the
     * kind of initialisation order that hands out a half-built value instead of failing.
     */
    val outline: QueueRowOutline?
        get() = when (this) {
            FOLLOW -> null
            PILL -> QueueRowOutline.Rounded.uniform(QueueRowOutline.STADIUM)
            ROUNDED -> QueueRowOutline.Rounded.uniform(18f)
            SOFT -> QueueRowOutline.Rounded.uniform(10f)
            SQUARE -> QueueRowOutline.Rounded.uniform(0f)
            LEAF -> QueueRowOutline.Rounded(
                    QueueRowOutline.STADIUM, 6f, QueueRowOutline.STADIUM, 6f)
            BUBBLE -> QueueRowOutline.speechBubble()
            HEXAGON -> QueueRowOutline.Hexagon(QueueRowOutline.BASALT_POINT_DP)
        }

    companion object {
        /** Unknown values follow the style, which is what every existing install already draws. */
        fun fromPreference(value: String?): QueueRowShape =
                entries.firstOrNull { it.token == value } ?: FOLLOW
    }
}

/**
 * The geometry of one queue-row outline, in a form both renderers can draw: Compose builds its
 * `Shape` from it on the watch, the phone's miniature builds a `Path`.
 *
 * Beyond drawing it, the outline answers where things may sit inside it - the now-playing keyline,
 * the ink underline, the strata lines and the cover. Those used to be placed against the row's
 * bounding box, which was right only for the outline each style happened to be drawn with; with the
 * outline now chosen independently, a keyline placed at the box's edge would hang outside a capsule.
 */
sealed interface QueueRowOutline {

    /**
     * A rectangle with its own radius at each corner, in dp, or [STADIUM] for half the row's
     * height. Corners are absolute (left/right) rather than start/end, as [CoverShape]'s are: the
     * phone draws on a Canvas that has no layout direction, and an outline that mirrored on the
     * watch alone would be the preview disagreeing with the wrist.
     */
    data class Rounded(
            val topLeft: Float,
            val topRight: Float,
            val bottomRight: Float,
            val bottomLeft: Float
    ) : QueueRowOutline {

        /**
         * Each corner's radius in pixels for a row [width] x [height], in top-left, top-right,
         * bottom-right, bottom-left order.
         *
         * Every corner is held to half the shorter side. That is what makes [STADIUM] a capsule,
         * and it means two neighbouring corners can never add up past the side between them -
         * which matters because Compose and Skia scale such an overflow down differently, so the
         * watch and its miniature would disagree about the very outline they share.
         */
        fun radii(width: Float, height: Float, dp: Float): FloatArray {
            val ceiling = max(0f, min(width, height) / 2f)
            fun px(radius: Float) = min(max(0f, radius * dp), ceiling)
            return floatArrayOf(px(topLeft), px(topRight), px(bottomRight), px(bottomLeft))
        }

        companion object {
            fun uniform(radius: Float) = Rounded(radius, radius, radius, radius)
        }
    }

    /** An elongated hexagon pointed at both ends: a basalt column on its side. */
    data class Hexagon(val pointDp: Float) : QueueRowOutline {

        /** How far the pointed ends reach in, held to a third of the width so a very narrow row
         *  stays a hexagon instead of folding over itself. */
        fun reach(width: Float, dp: Float): Float = (pointDp * dp).coerceIn(0f, width / 3f)

        /** The six corners as x, y pairs, clockwise from the top-left. */
        fun points(left: Float, top: Float, right: Float, bottom: Float, dp: Float): FloatArray {
            val reach = reach(right - left, dp)
            val middle = (top + bottom) / 2f
            return floatArrayOf(
                    left + reach, top,
                    right - reach, top,
                    right, middle,
                    right - reach, bottom,
                    left + reach, bottom,
                    left, middle)
        }
    }

    /**
     * How far in from the left of the row's bounding box the outline runs at height [y], for a
     * row [width] x [height] pixels.
     */
    fun leftInset(y: Float, width: Float, height: Float, dp: Float): Float = when (this) {
        is Rounded -> {
            val radii = radii(width, height, dp)
            roundedSideInset(y, height, radii[0], radii[3])
        }
        is Hexagon -> hexagonSideInset(y, height, reach(width, dp))
    }

    /** [leftInset]'s mirror on the right-hand side. */
    fun rightInset(y: Float, width: Float, height: Float, dp: Float): Float = when (this) {
        is Rounded -> {
            val radii = radii(width, height, dp)
            roundedSideInset(y, height, radii[1], radii[2])
        }
        is Hexagon -> hexagonSideInset(y, height, reach(width, dp))
    }

    /**
     * Where a mark spanning [top] to [bottom] down the left edge has to sit to stay inside the
     * outline. Both outline families are convex and widest at their middle, so the deepest point of
     * the edge over any span is at one of its ends.
     */
    fun leftEdgeOver(top: Float, bottom: Float, width: Float, height: Float, dp: Float): Float =
            max(leftInset(top, width, height, dp), leftInset(bottom, width, height, dp))

    /**
     * Where a cover [size] pixels square, centred down a row [width] x [height], has to start: at
     * [inset] from the row's left, and further in if any part of it would come within [clearance]
     * of this outline, given the cover's own [corners].
     *
     * Two numbers rather than one because they answer different things. [inset] is the even frame
     * round the cover - the same at its top, bottom and left - and every combination a style was
     * drawn with sits exactly there, which is why choosing nothing moves nothing. [clearance] only
     * matters once the outline and the cover's cut are chosen apart: a square cover in a capsule
     * would hang its corners outside the round ends at the inset a circle sits at, so it steps in
     * just far enough instead of being cropped by the row, which would make it read as a broken
     * image rather than as a square. Holding the whole cover to the full [inset] instead would have
     * nudged several styles' own covers by up to 2 dp.
     *
     * Found by walking down the cover rather than solved: a leaf cover in a hexagonal row has no
     * tidy closed form, and a few dozen steps cost nothing next to drawing the row.
     */
    fun coverStart(
            width: Float,
            height: Float,
            size: Float,
            corners: CoverCorners,
            inset: Float,
            clearance: Float,
            dp: Float
    ): Float {
        if (size <= 0f || height <= 0f) return inset
        val top = (height - size) / 2f
        var start = inset
        for (step in 0..COVER_START_SAMPLES) {
            val local = size * step / COVER_START_SAMPLES
            val needed = leftInset(top + local, width, height, dp) + clearance -
                    corners.leftInset(local, size)
            if (needed > start) start = needed
        }
        return start
    }

    /** The side padding text keeps from the row's ends, in dp. A hexagon's points eat into the
     *  ends, so its text steps in by roughly the width of a point. */
    val textInsetDp: Float
        get() = when (this) {
            is Rounded -> 16f
            is Hexagon -> 16f + pointDp * 2f / 3f
        }

    companion object {
        /** The radius that means half the row's height: the corner of a capsule. */
        const val STADIUM = Float.POSITIVE_INFINITY

        /** How far a hexagonal row's pointed ends reach in from its rectangle, in dp. */
        const val BASALT_POINT_DP = 12f

        /** The least a cover keeps from the outline anywhere - see [coverStart]. Small enough that
         *  no style's own cover moves, large enough that a corner never touches the curve. */
        const val COVER_CLEARANCE_DP = 2.5f

        /** The Bubble style's outline: round all over but for one tight corner, the tail. */
        fun speechBubble(): Rounded = Rounded(28f, 28f, 28f, 6f)
    }
}

/**
 * The cut of a cover: each corner's radius as a fraction of the cover's side, in the form
 * [CoverShape] already uses, so a queue cover can wear any of the shapes the faces' covers wear.
 *
 * The queue's own styles each carry a single fraction ([QueueStyleSpec.coverCornerFraction]), which
 * is the uniform case of the same thing.
 *
 * A cover cut as [CoverShape.SUPERELLIPSE] is not four radii, so [superellipse] says to draw the
 * [Superellipse] outline instead; the fractions are then only the stand-in a renderer that ignored
 * the flag would draw.
 */
data class CoverCorners(
        val topLeft: Float,
        val topRight: Float,
        val bottomRight: Float,
        val bottomLeft: Float,
        val superellipse: Boolean = false
) {

    /** The eight Canvas radii for a cover [size] pixels across, in `Path.addRoundRect` order. */
    fun radii(size: Float): FloatArray = floatArrayOf(
            topLeft * size, topLeft * size,
            topRight * size, topRight * size,
            bottomRight * size, bottomRight * size,
            bottomLeft * size, bottomLeft * size)

    /** How far in from its own left edge the cover's outline runs at height [y] of [size]. */
    fun leftInset(y: Float, size: Float): Float =
            if (superellipse) {
                Superellipse.leftInset(y, size, size)
            } else {
                roundedSideInset(
                        y,
                        size,
                        (topLeft * size).coerceIn(0f, size / 2f),
                        (bottomLeft * size).coerceIn(0f, size / 2f))
            }

    val isUniform: Boolean
        get() = !superellipse && topLeft == topRight && topRight == bottomRight &&
                bottomRight == bottomLeft

    companion object {
        fun uniform(fraction: Float) = CoverCorners(fraction, fraction, fraction, fraction)

        fun of(shape: CoverShape) = CoverCorners(
                shape.topLeft, shape.topRight, shape.bottomRight, shape.bottomLeft,
                superellipse = shape.isSuperellipse)
    }
}

private const val COVER_START_SAMPLES = 48

/**
 * How far in from a rounded rectangle's side its outline runs at height [y] of [height], when the
 * side's two corners are [upper] and [lower] pixels round. Shared by the row and its cover so the
 * two are measured with the same arithmetic.
 */
private fun roundedSideInset(y: Float, height: Float, upper: Float, lower: Float): Float {
    val at = y.coerceIn(0f, height)
    return when {
        upper > 0f && at < upper ->
            upper - sqrt(max(0f, upper * upper - (upper - at) * (upper - at)))
        lower > 0f && at > height - lower -> {
            val depth = at - (height - lower)
            lower - sqrt(max(0f, lower * lower - depth * depth))
        }
        else -> 0f
    }
}

/** A hexagon's side runs straight from its point at the middle out to [reach] at top and bottom. */
private fun hexagonSideInset(y: Float, height: Float, reach: Float): Float {
    val half = height / 2f
    if (half <= 0f) return 0f
    return reach * min(1f, abs(y.coerceIn(0f, height) - half) / half)
}
