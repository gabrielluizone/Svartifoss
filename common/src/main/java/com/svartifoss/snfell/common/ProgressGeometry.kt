package com.svartifoss.snfell.common

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The numbers behind the progress-ring styles that say something about the track rather than just
 * about how far along it is.
 *
 * Pure, like [MeterShape], so the arithmetic is pinned by JVM tests while the drawing that consumes
 * it ([ProgressFx]) stays a thin layer of canvas calls shared by the watch and the phone's miniature.
 */
object ProgressShape {

    // ---- Track minutes ---------------------------------------------------------------------

    /** The ring is never cut into more pieces than this; past it a piece stops being readable. */
    const val MAX_MINUTE_SEGMENTS = 60

    /** One piece per minute. */
    const val MINUTE_MS = 60_000L

    /**
     * How long each piece is, in milliseconds.
     *
     * A minute, until the track is longer than [MAX_MINUTE_SEGMENTS] of them: then every piece is
     * an equal share of the whole, so a two-hour podcast reads as sixty two-minute pieces instead of
     * a ring too crowded to see.
     */
    fun segmentMs(durationMs: Long): Long =
            if (durationMs > MINUTE_MS * MAX_MINUTE_SEGMENTS) {
                ceil(durationMs.toDouble() / MAX_MINUTE_SEGMENTS).toLong()
            } else {
                MINUTE_MS
            }

    /**
     * How many pieces a track of [durationMs] is divided into, or 0 when its length is unknown.
     *
     * A live stream and a player that published no duration both land on 0, and the caller draws a
     * plain ring: inventing a length would be telling the listener something that is not true.
     */
    fun minuteSegments(durationMs: Long): Int {
        if (durationMs <= 0L) return 0
        return ceil(durationMs.toDouble() / segmentMs(durationMs)).toInt()
                .coerceIn(1, MAX_MINUTE_SEGMENTS)
    }

    /** The slice of the ring one piece covers, as an offset and a length in degrees. */
    class SegmentArc(
            val offsetDegrees: Float,
            val lengthDegrees: Float,
            /** Where the piece starts within the track, 0..1. */
            val startFraction: Float,
            /** How much of the track the piece spans, 0..1. */
            val weight: Float
    ) {
        /** How much of this piece is behind [progress] (0..1 of the track). */
        fun played(progress: Float): Float =
                if (weight <= 0f) 0f else ((progress - startFraction) / weight).coerceIn(0f, 1f)
    }

    /**
     * The gap between two pieces, in degrees.
     *
     * Wide enough to read as a separation on a few big pieces, narrowing as they multiply so sixty
     * of them still leave something to see.
     */
    fun segmentGapDegrees(count: Int, sweepAbs: Float): Float {
        if (count <= 1) return 0f
        return (sweepAbs / count * 0.16f).coerceIn(0.9f, 5f)
    }

    /**
     * The pieces of a ring that sweeps [sweepDegrees] (sign ignored) for a track of [durationMs].
     *
     * Each piece's angular length is proportional to the time it covers, so a 3:20 track is three
     * full pieces and one a third of their size - the ring is honest about the last minute being
     * short. [closed] says whether the sweep is a full circle, which needs a gap after the last piece
     * too so the first one does not touch it.
     */
    fun minuteArcs(durationMs: Long, sweepDegrees: Float, closed: Boolean): List<SegmentArc> {
        val count = minuteSegments(durationMs)
        if (count <= 0) return emptyList()
        val sweepAbs = abs(sweepDegrees)
        val gap = segmentGapDegrees(count, sweepAbs)
        val gaps = if (closed) count else count - 1
        val available = max(0f, sweepAbs - gap * gaps)
        val pieceMs = segmentMs(durationMs)
        val arcs = ArrayList<SegmentArc>(count)
        var offset = 0f
        for (index in 0 until count) {
            val startMs = index * pieceMs
            val lengthMs = min(pieceMs, durationMs - startMs).coerceAtLeast(0L)
            val weight = lengthMs.toFloat() / durationMs
            val length = available * weight
            arcs += SegmentArc(offset, length, startMs.toFloat() / durationMs, weight)
            offset += length + gap
        }
        return arcs
    }

    // ---- Hex cells -------------------------------------------------------------------------

    /** Cells in a full circle. */
    const val HEX_CELLS_FULL = 48

    /** At least this many, so a short arc still reads as a row of cells. */
    const val HEX_CELLS_MIN = 8

    fun hexCellCount(sweepDegrees: Float): Int =
            max(HEX_CELLS_MIN, (HEX_CELLS_FULL * abs(sweepDegrees) / 360f).roundToInt())

    /** Where cell [index] of [count] sits along the sweep, 0..1. A closed ring wraps; an arc spans both ends. */
    fun hexCellFraction(index: Int, count: Int, closed: Boolean): Float =
            if (closed) index.toFloat() / count else index.toFloat() / max(1, count - 1)

    /** True when cell [index] is behind [progress]. */
    fun hexCellPlayed(index: Int, count: Int, closed: Boolean, progress: Float): Boolean =
            progress > 0f && hexCellFraction(index, count, closed) < progress

    /**
     * Vertex [corner] (0..5) of a hexagon of circumradius [radius] centred on ([cx], [cy]) whose
     * first vertex points at [rotationDegrees]. Written into [out] as x, y.
     */
    fun hexVertex(cx: Float, cy: Float, radius: Float, rotationDegrees: Float, corner: Int,
                  out: FloatArray) {
        val angle = Math.toRadians((rotationDegrees + 60f * corner).toDouble())
        out[0] = cx + radius * cos(angle).toFloat()
        out[1] = cy + radius * sin(angle).toFloat()
    }

    /** True for a sweep that is a full circle. */
    fun isClosed(sweepDegrees: Float): Boolean = abs(sweepDegrees) >= 359.5f

    // ---- Weight ----------------------------------------------------------------------------

    /** Stroke-weight multiplier for the ring's stored choice; unknown values are the default. */
    fun weightScale(value: String?): Float = when (value) {
        "thin" -> 0.6f
        "heavy" -> 1.5f
        "bold" -> 2.0f
        else -> 1f
    }
}

/**
 * The marks of a tick or dot scale laid along a ring or an arc: how many there are, where each one
 * sits, which are the long ones and which are behind the playhead.
 *
 * The watch's `CircularProgressSeekBar` and the phone's miniature both lay a scale out from here, so
 * the two cannot disagree about a single mark. They had: each kept its own copy, and the copies
 * differed on exactly the case that matters, the open arcs.
 *
 * A scale is described by its *divisions* - sixty for a watch's minute track, twenty-four for the
 * quarter-marked one - and a division is the gap between two marks. Around a closed ring the last
 * division runs into the first mark, so there are as many marks as divisions. An open arc has two
 * ends and wants a mark on each, so it has one more mark than it has divisions. Laying the ring's
 * number of marks across an arc instead, which is what this used to do, leaves the far end one place
 * short of the beat the near end starts on: with a long mark every fifth, "Open at top" began on a
 * long mark at one side, ended on a short one at the other, and had its mark at six o'clock two
 * degrees off the axis.
 *
 * Pure, like [ProgressShape], so the arithmetic is pinned by JVM tests while the drawing that
 * consumes it stays a thin layer of canvas calls.
 */
object ProgressScale {

    /**
     * How many divisions the scale has.
     *
     * [count] on a closed ring. On an open arc with a beat ([emphasizeEvery] above one) it is
     * [count] rounded up to a whole number of beats, which is what lets the scale finish on the long
     * mark it began with: sixty divisions in fives run 0 to 60 rather than sixty marks 0 to 59. An
     * arc with no beat has nothing to finish on, so it keeps its [count] marks, both ends included,
     * which is [count] minus one divisions.
     */
    fun divisions(count: Int, emphasizeEvery: Int, closed: Boolean): Int = when {
        count <= 0 -> 0
        closed -> count
        emphasizeEvery > 1 -> (count + emphasizeEvery - 1) / emphasizeEvery * emphasizeEvery
        else -> max(1, count - 1)
    }

    /** How many marks are drawn: one per division around a ring, one more along an arc with a beat. */
    fun markCount(count: Int, emphasizeEvery: Int, closed: Boolean): Int = when {
        count <= 0 -> 0
        closed || emphasizeEvery <= 1 -> count
        else -> divisions(count, emphasizeEvery, closed) + 1
    }

    /** How far along the sweep mark [index] sits, 0..1, for a scale of [divisions]. */
    fun fraction(index: Int, divisions: Int): Float =
            if (divisions <= 0) 0f else index.toFloat() / divisions

    /** True for the long marks: every [emphasizeEvery]th, starting with the first. */
    fun isMajor(index: Int, emphasizeEvery: Int): Boolean =
            emphasizeEvery > 0 && index % emphasizeEvery == 0

    /**
     * True when a mark [fraction] of the way along is behind [progress] (0..1).
     *
     * Nothing is played at zero, and at the very end everything is: the last mark of an arc sits at
     * exactly 1, which no position can pass, so on a finished track it would otherwise stay dark.
     */
    fun isPlayed(fraction: Float, progress: Float): Boolean =
            progress > 0f && (fraction < progress || progress >= 1f)
}
