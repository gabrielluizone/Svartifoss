package com.svartifoss.snfell.watch.view

import com.svartifoss.snfell.common.ProgressScale
import com.svartifoss.snfell.common.ProgressShape
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tick and dot scales on every layout that leaves the ring open.
 *
 * "Open at top" used to start on a long mark at one end of its arc and finish on a short one at the
 * other, with its mark at six o'clock two degrees off the axis: the scale looked lopsided against
 * the gap it frames. These lay the marks out the way `CircularProgressSeekBar` does, from the real
 * layout angles, and check what a person sees - that each side of the gap is the mirror of the other.
 */
class ProgressRingScaleSymmetryTest {

    /** (divisions on a ring, a long mark every n-th) for every scale the ring draws. */
    private val scales = listOf(60 to 5, 24 to 6, 12 to 1)

    private class Mark(val angle: Double, val major: Boolean)

    private fun normalized(angle: Double): Double = ((angle % 360.0) + 360.0) % 360.0

    private fun circularDistance(a: Double, b: Double): Double {
        val direct = abs(normalized(a) - normalized(b))
        return minOf(direct, 360.0 - direct)
    }

    private fun marks(layout: ProgressRingLayout, count: Int, every: Int): List<Mark> {
        val closed = ProgressShape.isClosed(layout.sweepAngle)
        val divisions = ProgressScale.divisions(count, every, closed)
        return (0 until ProgressScale.markCount(count, every, closed)).map { index ->
            val fraction = ProgressScale.fraction(index, divisions)
            Mark(
                    normalized(layout.startAngle + fraction * layout.sweepAngle.toDouble()),
                    ProgressScale.isMajor(index, every))
        }
    }

    private val openLayouts: List<ProgressRingLayout>
        get() = ProgressRingLayout.values().filter { !ProgressShape.isClosed(it.sweepAngle) }

    @Test
    fun `every open layout has its eight arcs under test`() {
        // Guards the guard: if a layout is added or renamed, the sweep below must still reach it.
        assertEquals(8, openLayouts.size)
    }

    @Test
    fun `every open layout is the mirror of itself about the middle of its arc`() {
        for (layout in openLayouts) {
            val axis = layout.startAngle + layout.sweepAngle / 2.0
            for ((count, every) in scales) {
                val marks = marks(layout, count, every)
                for (mark in marks) {
                    val mirrored = 2 * axis - mark.angle
                    val twin = marks.firstOrNull { circularDistance(it.angle, mirrored) < 1e-3 }
                    assertNotNull(
                            "$layout $count/$every: the mark at ${mark.angle} has no mirror image",
                            twin)
                    assertEquals(
                            "$layout $count/$every: the mark at ${mark.angle} and its mirror " +
                                    "image differ in weight",
                            mark.major,
                            twin!!.major)
                }
            }
        }
    }

    @Test
    fun `Open at top has a long mark at each side of the gap and one at six o'clock`() {
        val layout = ProgressRingLayout.OPEN_TOP
        val long = marks(layout, 60, 5).filter { it.major }.map { it.angle }

        // The arc starts at -45 degrees (upper right), runs the long way round through six o'clock
        // and ends at 225 degrees (upper left); the gap between the two ends is centred at twelve.
        assertTrue("right side of the gap", long.any { circularDistance(it, -45.0) < 1e-3 })
        assertTrue("left side of the gap", long.any { circularDistance(it, 225.0) < 1e-3 })
        assertTrue("six o'clock", long.any { circularDistance(it, 90.0) < 1e-3 })
        assertEquals(13, long.size)
    }

    @Test
    fun `Open at top is left-right symmetric with every long mark answered on the other side`() {
        val marks = marks(ProgressRingLayout.OPEN_TOP, 60, 5)
        for (mark in marks) {
            // Mirroring across the vertical axis takes the angle a to 180 - a.
            val twin = marks.first { circularDistance(it.angle, 180.0 - mark.angle) < 1e-3 }
            assertEquals(mark.major, twin.major)
        }
    }

    @Test
    fun `a ring is untouched`() {
        for ((count, every) in scales) {
            val marks = marks(ProgressRingLayout.EDGE, count, every)
            assertEquals(count, marks.size)
            // Twelve o'clock is where a ring's scale starts, and it is a long mark.
            assertEquals(normalized(-90.0), marks.first().angle, 1e-6)
            assertTrue(marks.first().major)
        }
    }
}
