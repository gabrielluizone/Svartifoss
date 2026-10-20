package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressShapeTest {

    private val minute = ProgressShape.MINUTE_MS

    @Test
    fun `a track with no length has no pieces`() {
        assertEquals(0, ProgressShape.minuteSegments(0))
        assertEquals(0, ProgressShape.minuteSegments(-1))
        assertTrue(ProgressShape.minuteArcs(0, 360f, true).isEmpty())
    }

    @Test
    fun `a song is cut into one piece per started minute`() {
        assertEquals(1, ProgressShape.minuteSegments(30_000))
        assertEquals(3, ProgressShape.minuteSegments(3 * minute))
        assertEquals(4, ProgressShape.minuteSegments(3 * minute + 20_000))
        assertEquals(60, ProgressShape.minuteSegments(60 * minute))
    }

    @Test
    fun `a track longer than an hour gets sixty equal pieces`() {
        val twoHours = 120 * minute
        assertEquals(60, ProgressShape.minuteSegments(twoHours))
        assertEquals(2 * minute, ProgressShape.segmentMs(twoHours))
        // Never more than sixty, however long it is.
        assertEquals(60, ProgressShape.minuteSegments(10 * 60 * minute + 1))
    }

    @Test
    fun `the last piece is as short as the last minute`() {
        val arcs = ProgressShape.minuteArcs(3 * minute + 20_000, 360f, true)
        assertEquals(4, arcs.size)
        assertEquals(arcs[0].lengthDegrees, arcs[1].lengthDegrees, 1e-3f)
        assertEquals(arcs[0].lengthDegrees / 3f, arcs[3].lengthDegrees, 0.05f)
    }

    @Test
    fun `pieces and gaps exactly fill a closed ring`() {
        val arcs = ProgressShape.minuteArcs(5 * minute, 360f, true)
        val gap = ProgressShape.segmentGapDegrees(5, 360f)
        val last = arcs.last()
        assertEquals(360f, last.offsetDegrees + last.lengthDegrees + gap, 0.01f)
    }

    @Test
    fun `pieces and gaps exactly fill an open arc without a trailing gap`() {
        val arcs = ProgressShape.minuteArcs(5 * minute, 270f, false)
        val last = arcs.last()
        assertEquals(270f, last.offsetDegrees + last.lengthDegrees, 0.01f)
        assertEquals(0f, arcs.first().offsetDegrees, 0f)
    }

    @Test
    fun `a negative sweep is measured the same way`() {
        val forward = ProgressShape.minuteArcs(4 * minute, 160f, false)
        val backward = ProgressShape.minuteArcs(4 * minute, -160f, false)
        assertEquals(forward.map { it.lengthDegrees }, backward.map { it.lengthDegrees })
    }

    @Test
    fun `a piece reports how much of it is behind the position`() {
        val arcs = ProgressShape.minuteArcs(4 * minute, 360f, true)
        // 1:30 in: the first piece is full, the second half done, the rest untouched.
        val progress = 90_000f / (4 * minute)
        assertEquals(1f, arcs[0].played(progress), 1e-4f)
        assertEquals(0.5f, arcs[1].played(progress), 1e-4f)
        assertEquals(0f, arcs[2].played(progress), 0f)
        assertEquals(0f, arcs[3].played(progress), 0f)
    }

    @Test
    fun `the gap narrows as the pieces multiply but never vanishes`() {
        val few = ProgressShape.segmentGapDegrees(3, 360f)
        val many = ProgressShape.segmentGapDegrees(60, 360f)
        assertTrue(few > many)
        assertTrue(many >= 0.9f)
        assertEquals(0f, ProgressShape.segmentGapDegrees(1, 360f), 0f)
    }

    @Test
    fun `hex cells scale with the sweep and never drop below a row`() {
        assertEquals(48, ProgressShape.hexCellCount(360f))
        assertEquals(36, ProgressShape.hexCellCount(270f))
        assertEquals(36, ProgressShape.hexCellCount(-270f))
        assertEquals(ProgressShape.HEX_CELLS_MIN, ProgressShape.hexCellCount(20f))
    }

    @Test
    fun `an arc of cells reaches both of its ends but a ring wraps`() {
        assertEquals(0f, ProgressShape.hexCellFraction(0, 10, closed = false), 0f)
        assertEquals(1f, ProgressShape.hexCellFraction(9, 10, closed = false), 1e-6f)
        assertEquals(0.9f, ProgressShape.hexCellFraction(9, 10, closed = true), 1e-6f)
    }

    @Test
    fun `no cell is played at zero and the played ones are a prefix`() {
        val played = (0 until 20).map { ProgressShape.hexCellPlayed(it, 20, true, 0.4f) }
        assertEquals(8, played.count { it })
        assertEquals(played.sortedDescending(), played)
        assertTrue((0 until 20).none { ProgressShape.hexCellPlayed(it, 20, true, 0f) })
    }

    @Test
    fun `a hexagon has six vertices all at the circumradius`() {
        val out = FloatArray(2)
        repeat(6) { corner ->
            ProgressShape.hexVertex(10f, 20f, 5f, 90f, corner, out)
            val dx = out[0] - 10f
            val dy = out[1] - 20f
            assertEquals(5f, kotlin.math.sqrt(dx * dx + dy * dy), 1e-3f)
        }
        ProgressShape.hexVertex(0f, 0f, 4f, 0f, 0, out)
        assertEquals(4f, out[0], 1e-3f)
    }

    @Test
    fun `the weight choices map to multipliers and unknown ones are regular`() {
        assertEquals(0.6f, ProgressShape.weightScale("thin"), 0f)
        assertEquals(1f, ProgressShape.weightScale("regular"), 0f)
        assertEquals(1.5f, ProgressShape.weightScale("heavy"), 0f)
        assertEquals(2f, ProgressShape.weightScale("bold"), 0f)
        assertEquals(1f, ProgressShape.weightScale(null), 0f)
        assertEquals(1f, ProgressShape.weightScale("???"), 0f)
    }

    @Test
    fun `a full sweep is closed and a three-quarter one is not`() {
        assertTrue(ProgressShape.isClosed(360f))
        assertTrue(ProgressShape.isClosed(-360f))
        assertTrue(!ProgressShape.isClosed(270f))
    }
}
