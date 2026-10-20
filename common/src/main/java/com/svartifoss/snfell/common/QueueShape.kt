package com.svartifoss.snfell.common

/**
 * The numbers behind the queue's drawn treatments, shared by the watch and the phone's miniature
 * for the reason [MeterShape] is: both draw the same shapes with different toolkits, and a shape
 * whose points are decided twice is a shape that drifts.
 *
 * Pure, so a JVM test can say where the route runs without a canvas. The row outlines themselves
 * are [QueueRowOutline]'s.
 */
object QueueShape {

    // ---- Strata row ------------------------------------------------------------------------

    /** Layers drawn along the foot of a strata row. */
    const val STRATA_LAYERS = 3

    /** Thickness of one layer, and of the gap between two, in dp. */
    const val STRATA_LINE_DP = 1.6f
    const val STRATA_GAP_DP = 1.4f

    /**
     * Top edge of each layer, counted from the bottom of a row [height] tall: the first entry is the
     * lowest. They stack upwards so the stratum nearest the ground is always the same one.
     */
    fun strataLineTops(height: Float, line: Float, gap: Float): FloatArray =
            FloatArray(STRATA_LAYERS) { height - line - it * (line + gap) }

    // ---- Route -----------------------------------------------------------------------------

    /** The route's stroke, in dp. */
    const val ROUTE_LINE_DP = 2f

    /** How far the route runs on past the last station it shows when the queue goes on, in dp -
     *  far enough to say "this continues", short of reaching anything that is not a station. */
    const val ROUTE_TAIL_DP = 26f

    /** A station whose entry has no cover is drawn as a node this large at most, in dp. */
    const val ROUTE_NODE_DP = 5.5f

    /** How a station marks its place in the queue. */
    enum class Node { PAST, NOW, UPCOMING }

    /** What kind of node an entry [offset] places from the playing one gets. */
    fun node(offset: Int): Node = when {
        offset < 0 -> Node.PAST
        offset == 0 -> Node.NOW
        else -> Node.UPCOMING
    }

    /** Node radius as a share of [ROUTE_NODE_DP]: the playing entry is the landmark. */
    fun nodeScale(node: Node): Float = when (node) {
        Node.NOW -> 1f
        Node.UPCOMING -> 0.5f
        Node.PAST -> 0.42f
    }

    /**
     * One entry's station, on screen: its queue [index], its centre, and the opacity the list gives
     * its row - rows fade as they near the round screen's edge, and the route fades with them.
     */
    data class Station(val index: Int, val x: Float, val y: Float, val alpha: Float = 1f)

    /**
     * One stretch of the route, from one point down to another. [travelled] stretches lie above
     * the playing entry - the part of the queue already behind - and are drawn in the album's
     * colour. A tail runs out to nothing at its far end ([fadesAtStart] above the first station,
     * [fadesAtEnd] below the last), which is how the route says the queue goes on past what is on
     * screen without drawing a station it does not have.
     */
    data class Segment(
            val fromX: Float,
            val fromY: Float,
            val toX: Float,
            val toY: Float,
            val travelled: Boolean,
            val alpha: Float = 1f,
            val fadesAtStart: Boolean = false,
            val fadesAtEnd: Boolean = false)

    /**
     * Where a station sits on screen, from what a `ScalingLazyColumn` reports about its row.
     *
     * The column scales every row about its horizontal centre and its top edge, and reports
     * [itemOffset] as the row's centre measured from the viewport's centre line, already scaled.
     * So a point [stationX] across a row [itemWidth] wide lands at the viewport's centre plus its
     * distance from the row's centre, times the row's [scale]; and since a station sits halfway
     * down its row, its height is the row's centre.
     *
     * This is what lets the route run through the covers themselves. The timeline it replaced
     * followed the bezel instead, a fixed arc the rows only happened to be near.
     */
    fun station(
            index: Int,
            viewportWidth: Float,
            viewportHeight: Float,
            itemWidth: Float,
            stationX: Float,
            itemOffset: Float,
            scale: Float,
            alpha: Float = 1f
    ): Station = Station(
            index,
            viewportWidth / 2f + (stationX - itemWidth / 2f) * scale,
            viewportHeight / 2f + itemOffset,
            alpha.coerceIn(0f, 1f))

    /**
     * The route through the visible [stations] (any order), for a queue of [itemCount] entries in
     * which the one at [playingIndex] is playing, or none when it is negative - the
     * recently-played fallback, where there is no "you are here" and nothing is marked travelled.
     *
     * Drawn once over the whole list rather than by each row: the list scales its rows towards the
     * edges of the round screen, so a line drawn inside each row would step sideways from one row to
     * the next. Joining the stations where they really are bends the line with that scaling instead.
     *
     * Stations next to each other are joined directly. Past the first and last one shown, a tail
     * [tail] long fades out where the queue goes on - above unless the first entry is on screen,
     * below unless the last is and [continuesBelow] does not promise more.
     */
    fun routeSegments(
            stations: List<Station>,
            itemCount: Int,
            playingIndex: Int,
            continuesBelow: Boolean,
            tail: Float
    ): List<Segment> {
        if (stations.isEmpty()) return emptyList()
        val sorted = stations.sortedBy { it.index }
        val playing = playingIndex.takeIf { it >= 0 }
        // A stretch is travelled when it leads into the playing station or anything before it.
        fun travelledInto(index: Int) = playing != null && index <= playing

        val segments = ArrayList<Segment>(sorted.size + 1)
        val first = sorted.first()
        if (first.index > 0) {
            segments += Segment(
                    first.x, first.y - tail, first.x, first.y,
                    travelled = travelledInto(first.index),
                    alpha = first.alpha,
                    fadesAtStart = true)
        }
        sorted.zipWithNext { upper, lower ->
            // Two rows that are not neighbours in the queue have something between them that is
            // not a station - nothing in this list today, but a line would claim otherwise.
            if (lower.index == upper.index + 1) {
                segments += Segment(
                        upper.x, upper.y, lower.x, lower.y,
                        travelled = travelledInto(lower.index),
                        alpha = minOf(upper.alpha, lower.alpha))
            }
        }
        val last = sorted.last()
        if (last.index < itemCount - 1 || continuesBelow) {
            segments += Segment(
                    last.x, last.y, last.x, last.y + tail,
                    travelled = travelledInto(last.index + 1),
                    alpha = last.alpha,
                    fadesAtEnd = true)
        }
        return segments
    }

    // ---- Relative position -----------------------------------------------------------------

    /** Entries that already played are dimmed on request; the rest are never touched. */
    fun rowAlpha(offset: Int, dimPlayed: Boolean): Float =
            if (dimPlayed && offset < 0) 0.5f else 1f

    /**
     * Each entry's offset from the playing one, or null for all of them when none is playing - a
     * history fallback, say - because then there is nothing to be relative to.
     */
    fun offsets(playing: List<Boolean>): List<Int>? {
        val index = playing.indexOfFirst { it }
        if (index < 0) return null
        return List(playing.size) { it - index }
    }
}
