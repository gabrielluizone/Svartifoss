package com.svartifoss.snfell.common

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Geometry for keeping a block of text inside a round watch screen.
 *
 * A round display is not a rectangle with rounded corners: the usable width collapses fast towards
 * the top and bottom, and a text block placed low enough is clipped at *both* ends long before it
 * reaches the layout's own padding. Faces used to compensate with hand-tuned side padding, which
 * is only ever correct for the one line count the author happened to test - the moment a title
 * wrapped, the second line sat deeper, where the chord is narrower, and ran into the bezel.
 *
 * Lives in `common` for the usual reason: the watch faces and the phone preview must inset text
 * identically or the preview lies about where a title will be cut.
 *
 * All values are fractions of the screen's width, with the screen treated as a unit square holding
 * a unit-diameter circle. Depth is measured from the top edge, so 0 is the top of the screen, 0.5
 * the centre and 1 the bottom.
 */
object RoundScreenText {

    /** Breathing room kept between the text and the glass edge, as a fraction of the screen. */
    const val DEFAULT_MARGIN = 0.03f

    /**
     * Never inset so far that the text column collapses. A band placed very low mathematically has
     * almost no width at all, and honouring that literally would render an unreadable one-character
     * column; clamping produces a slightly clipped line instead, which at least stays legible and
     * signals to the user that the block is too low.
     */
    private const val MAX_INSET = 0.34f

    /**
     * Half of the circle's chord at [depth], i.e. how far the glass extends either side of the
     * vertical centre line at that height. 0.5 at the centre, 0 at the very top and bottom.
     */
    fun halfChordAt(depth: Float): Float {
        val dy = depth.coerceIn(0f, 1f) - 0.5f
        val squared = 0.25f - dy * dy
        return if (squared <= 0f) 0f else sqrt(squared.toDouble()).toFloat()
    }

    /**
     * The side inset that keeps a text block spanning [top]..[bottom] inside the glass.
     *
     * The binding constraint is whichever edge of the block sits *further* from the vertical centre,
     * not the bottom one: a block straddling the centre is narrowest at its top when it reaches
     * higher than it descends. Taking the minimum of the two chords covers both cases without the
     * caller having to know which side it is on.
     */
    fun sideInsetFor(top: Float, bottom: Float, margin: Float = DEFAULT_MARGIN): Float {
        val narrowest = minOf(halfChordAt(top), halfChordAt(bottom))
        return (0.5f - narrowest + margin).coerceIn(0f, MAX_INSET)
    }

    /**
     * Convenience for the common case: a block whose top edge is known and which grows downwards by
     * [lineHeight] per line for [lines] lines. Returns the inset for that many lines, so a caller
     * that measures its real line count gets a wide column while the text fits on one line and a
     * correctly narrowed one only once it actually wraps.
     */
    fun sideInsetForLines(
            top: Float,
            lineHeight: Float,
            lines: Int,
            margin: Float = DEFAULT_MARGIN
    ): Float = sideInsetFor(top, top + lineHeight * lines.coerceAtLeast(1), margin)

    /**
     * How many lines of [lineHeight] fit below [top] before the chord becomes too narrow to hold
     * readable text, capped at [maxLines].
     *
     * Used to decide how far a face may let a title wrap at all. [minUsableWidth] is the point
     * below which a column stops being worth rendering - roughly the width of a handful of
     * characters at the face's own font size.
     */
    fun linesThatFit(
            top: Float,
            lineHeight: Float,
            maxLines: Int,
            minUsableWidth: Float = 0.42f,
            margin: Float = DEFAULT_MARGIN
    ): Int {
        if (lineHeight <= 0f) return maxLines.coerceAtLeast(1)
        var fitting = 1
        for (lines in 2..maxLines) {
            val inset = sideInsetForLines(top, lineHeight, lines, margin)
            if (1f - 2f * inset < minUsableWidth) break
            fitting = lines
        }
        return fitting
    }

    /**
     * Whether [a] and [b] are close enough to treat as the same inset.
     *
     * The measure-then-inset loop a wrapping title runs (render, count lines, re-inset, re-render)
     * has to stop, and comparing raw floats would keep it oscillating on sub-pixel differences.
     */
    fun insetsAreEquivalent(a: Float, b: Float): Boolean = abs(a - b) < 0.002f
}
