package com.svartifoss.snfell.common

import kotlin.math.ceil

/**
 * How a line of text that does not fit scrolls, and how strongly its ends are dissolved while it does.
 *
 * Three renderers draw a scrolling line - the Compose faces and the queue (`FaceChrome.trackMarquee`),
 * the classic View faces (`OutlineTextView`) and the phone's miniature (`WatchPreviewView`) - and they
 * used to run three different motions: Compose's `basicMarquee` rested before every pass, TextView's
 * marquee rested at the *end* of one, the miniature never rested at all, and each dissolved the ends on
 * its own terms. Worse, none of them knew when the line was standing still, so the dissolve stayed on
 * while it rested: on the Compose faces both ends of a title sitting at its start were faded, which
 * eats its first letters and reads as a rendering fault, not as text about to move. Every renderer
 * now asks this one object where the line is and how faded it is, so all three move and fade alike.
 *
 * The loop: the line rests at its start for [REST_MILLIS], moves left at [VELOCITY_DP_PER_SECOND]
 * until a second copy - drawn one [loopPx] behind the first - lands exactly where the first began,
 * and rests again. The end of a pass and the start of the next are therefore the same picture, which
 * is what lets the line rest without a jump.
 *
 * The fade follows the movement, not the clock: [fadeStrength] is zero while the line rests and grows
 * with the distance it has travelled from either rest position, reaching full strength one fade width
 * in. So the ends dissolve once the text is visibly moving and are sharp again by the time it settles.
 *
 * Distances are in pixels and times in milliseconds; each renderer converts dp with its own density.
 */
object MarqueeMotion {

    /** Scroll speed. The platform's own (TextView's marquee and Compose's both use 30dp/s). */
    const val VELOCITY_DP_PER_SECOND = 30f

    /** How long the line stands still at its start before each pass, the first one included. */
    const val REST_MILLIS = 1_200L

    /** The gap between the end of the line and its next copy, as a fraction of the visible width. */
    const val GAP_FRACTION = 1f / 3f

    /**
     * How far each end dissolves over at full strength.
     *
     * Wide enough that a glyph visibly thins out rather than being chopped, narrow enough that a short
     * word is never swallowed whole while it crosses.
     */
    const val FADE_WIDTH_DP = 18f

    /** Whether a line [contentPx] wide has to scroll at all in [viewportPx]. */
    fun overflows(contentPx: Float, viewportPx: Float): Boolean =
            viewportPx > 0f && contentPx > viewportPx

    /** The distance from one copy of the line to the next: the line itself plus the gap. */
    fun loopPx(contentPx: Float, viewportPx: Float): Float =
            contentPx.coerceAtLeast(0f) + viewportPx.coerceAtLeast(0f) * GAP_FRACTION

    /** How long one pass moves for, rest excluded. Zero for a loop that cannot move. */
    fun passMillis(loopPx: Float, pxPerSecond: Float): Long =
            if (loopPx <= 0f || pxPerSecond <= 0f) 0L
            else ceil(loopPx / pxPerSecond * 1_000f).toLong()

    /**
     * How far the first copy has moved left of its start [elapsedMillis] after the marquee began.
     *
     * Zero during every rest, [loopPx] at the very end of a pass (the second copy then sits exactly
     * where the first started, so that frame and the next rest look identical).
     */
    fun offsetAt(elapsedMillis: Long, loopPx: Float, pxPerSecond: Float): Float {
        val pass = passMillis(loopPx, pxPerSecond)
        if (pass <= 0L || elapsedMillis <= 0L) return 0f
        val within = elapsedMillis % (REST_MILLIS + pass)
        if (within < REST_MILLIS) return 0f
        return ((within - REST_MILLIS) * pxPerSecond / 1_000f).coerceAtMost(loopPx)
    }

    /**
     * How long the line will keep standing still, or zero while it is moving.
     *
     * Lets a renderer sleep through a rest instead of drawing frames that change nothing - a rest is
     * more than a second of every pass, on a screen that runs on a battery.
     */
    fun millisUntilMoving(elapsedMillis: Long, loopPx: Float, pxPerSecond: Float): Long {
        val pass = passMillis(loopPx, pxPerSecond)
        if (pass <= 0L) return Long.MAX_VALUE
        if (elapsedMillis < 0L) return REST_MILLIS - elapsedMillis
        val within = elapsedMillis % (REST_MILLIS + pass)
        return if (within < REST_MILLIS) REST_MILLIS - within else 0L
    }

    /**
     * How strongly both ends are dissolved with the first copy [offsetPx] along a loop of [loopPx]:
     * 0 at rest, rising to 1 once the line has moved [fadePx], and back to 0 as the next copy settles.
     *
     * Both ends share the one strength on purpose. A line about to move has its tail cut by the edge
     * and its head sharp; dissolving only the tail at rest would leave the same half-faded line this
     * replaces, just on the other side.
     */
    fun fadeStrength(offsetPx: Float, loopPx: Float, fadePx: Float): Float {
        if (fadePx <= 0f || loopPx <= 0f) return 0f
        val fromRest = minOf(offsetPx, loopPx - offsetPx)
        if (fromRest <= 0f) return 0f
        return (fromRest / fadePx).coerceIn(0f, 1f)
    }

    /**
     * The fade width for a viewport [viewportPx] wide: [fadePx], but never more than a third of the
     * viewport per end, so a narrow line is not left with only its middle fully opaque.
     */
    fun fadeWidthPx(fadePx: Float, viewportPx: Float): Float =
            minOf(fadePx, viewportPx / 3f).coerceAtLeast(0f)
}
