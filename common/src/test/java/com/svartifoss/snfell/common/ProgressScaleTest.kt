package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressScaleTest {

    /** The four scales the ring draws, as (divisions on a ring, long mark every n-th). */
    private val shippedScales = listOf(
            60 to 5, // watch dots and watch ticks
            24 to 6, // beads and 24-hour ticks
            12 to 1) // hour segments: every mark alike

    @Test
    fun `sixty divisions in fives run from a long mark to a long mark across an open arc`() {
        val divisions = ProgressScale.divisions(60, 5, closed = false)
        assertEquals(60, divisions)
        // One mark more than divisions: both ends carry one.
        assertEquals(61, ProgressScale.markCount(60, 5, closed = false))

        assertTrue(ProgressScale.isMajor(0, 5))
        assertTrue(ProgressScale.isMajor(divisions, 5))
        // And the middle of the scale is one too, which is the one that sits on the axis.
        assertTrue(ProgressScale.isMajor(divisions / 2, 5))
        assertEquals(0.5f, ProgressScale.fraction(divisions / 2, divisions), 0f)
    }

    @Test
    fun `Open at top puts its marks four and a half degrees apart`() {
        val divisions = ProgressScale.divisions(60, 5, closed = false)
        assertEquals(4.5f, ProgressScale.fraction(1, divisions) * 270f, 1e-4f)
        // 12 o'clock is where the gap is centred, so the middle mark is at 6 o'clock.
        val middle = -45f + ProgressScale.fraction(divisions / 2, divisions) * 270f
        assertEquals(90f, middle, 1e-4f)
    }

    @Test
    fun `an arc reads the same from both ends`() {
        for ((count, every) in shippedScales + listOf(15 to 5, 10 to 5, 12 to 4, 7 to 2, 9 to 3)) {
            val divisions = ProgressScale.divisions(count, every, closed = false)
            val marks = ProgressScale.markCount(count, every, closed = false)
            for (index in 0 until marks) {
                val mirror = marks - 1 - index
                assertEquals(
                        "$count/$every: mark $index and mark $mirror differ in weight",
                        ProgressScale.isMajor(index, every),
                        ProgressScale.isMajor(mirror, every))
                assertEquals(
                        "$count/$every: mark $index and mark $mirror are not mirrored",
                        1f,
                        ProgressScale.fraction(index, divisions) +
                                ProgressScale.fraction(mirror, divisions),
                        1e-5f)
            }
        }
    }

    @Test
    fun `an arc with a beat finishes on the beat it began with`() {
        for ((count, every) in listOf(60 to 5, 24 to 6, 15 to 5, 12 to 4, 7 to 2)) {
            val last = ProgressScale.markCount(count, every, closed = false) - 1
            assertTrue("$count/$every starts long", ProgressScale.isMajor(0, every))
            assertTrue("$count/$every ends long", ProgressScale.isMajor(last, every))
        }
    }

    @Test
    fun `a count that is not a whole number of beats is rounded up to one`() {
        assertEquals(60, ProgressScale.divisions(58, 5, closed = false))
        assertEquals(61, ProgressScale.markCount(58, 5, closed = false))
        assertEquals(10, ProgressScale.divisions(7, 5, closed = false))
    }

    @Test
    fun `the long marks of an open sixty are every fifth and thirteen in all`() {
        val marks = ProgressScale.markCount(60, 5, closed = false)
        val long = (0 until marks).filter { ProgressScale.isMajor(it, 5) }
        assertEquals((0..60 step 5).toList(), long)
        assertEquals(13, long.size)
    }

    @Test
    fun `a ring has one mark per division and starts on a long one`() {
        for ((count, every) in shippedScales) {
            assertEquals(count, ProgressScale.divisions(count, every, closed = true))
            assertEquals(count, ProgressScale.markCount(count, every, closed = true))
            assertEquals(0f, ProgressScale.fraction(0, count), 0f)
            // The last mark stops short of the first, where the ring closes on itself.
            assertEquals((count - 1f) / count, ProgressScale.fraction(count - 1, count), 1e-6f)
        }
        assertTrue(ProgressScale.isMajor(0, 5))
        assertEquals(12, (0 until 60).count { ProgressScale.isMajor(it, 5) })
        assertEquals(4, (0 until 24).count { ProgressScale.isMajor(it, 6) })
    }

    @Test
    fun `a scale with nothing emphasised keeps its marks and spans both ends of an arc`() {
        // Twelve hour segments, all alike: nothing to finish on, so nothing changes.
        assertEquals(12, ProgressScale.markCount(12, 1, closed = false))
        assertEquals(11, ProgressScale.divisions(12, 1, closed = false))
        assertEquals(0f, ProgressScale.fraction(0, 11), 0f)
        assertEquals(1f, ProgressScale.fraction(11, 11), 0f)
        // Dots with no beat at all are the same.
        assertEquals(20, ProgressScale.markCount(20, 0, closed = false))
        assertEquals(19, ProgressScale.divisions(20, 0, closed = false))
        assertFalse(ProgressScale.isMajor(3, 0))
    }

    @Test
    fun `a scale that cannot be drawn draws nothing`() {
        assertEquals(0, ProgressScale.divisions(0, 5, closed = false))
        assertEquals(0, ProgressScale.markCount(0, 5, closed = false))
        assertEquals(0, ProgressScale.markCount(-3, 5, closed = true))
        assertEquals(0f, ProgressScale.fraction(4, 0), 0f)
        // One mark is a mark, not a divide by zero.
        assertEquals(1, ProgressScale.markCount(1, 0, closed = false))
        assertEquals(0f, ProgressScale.fraction(0, ProgressScale.divisions(1, 0, closed = false)), 0f)
    }

    @Test
    fun `nothing is played at zero and the first mark lights as soon as there is any progress`() {
        val divisions = ProgressScale.divisions(60, 5, closed = false)
        val marks = ProgressScale.markCount(60, 5, closed = false)
        assertTrue((0 until marks).none {
            ProgressScale.isPlayed(ProgressScale.fraction(it, divisions), 0f)
        })
        assertTrue(ProgressScale.isPlayed(ProgressScale.fraction(0, divisions), 0.001f))
        assertFalse(ProgressScale.isPlayed(ProgressScale.fraction(1, divisions), 0.001f))
    }

    @Test
    fun `the played marks are a prefix of the scale`() {
        val divisions = ProgressScale.divisions(60, 5, closed = false)
        val marks = ProgressScale.markCount(60, 5, closed = false)
        val played = (0 until marks).map {
            ProgressScale.isPlayed(ProgressScale.fraction(it, divisions), 0.4f)
        }
        // 0.4 of 60 divisions is mark 24; marks 0 to 23 are behind it, 24 is on it and not yet passed.
        assertEquals(24, played.count { it })
        assertEquals(played.sortedDescending(), played)
    }

    @Test
    fun `at the end every mark is played including the last one`() {
        val divisions = ProgressScale.divisions(60, 5, closed = false)
        val marks = ProgressScale.markCount(60, 5, closed = false)
        // The last mark of an arc sits at exactly 1, which no position can pass.
        assertEquals(1f, ProgressScale.fraction(marks - 1, divisions), 0f)
        assertTrue((0 until marks).all {
            ProgressScale.isPlayed(ProgressScale.fraction(it, divisions), 1f)
        })
        // Just short of the end the last one is still to come.
        assertFalse(ProgressScale.isPlayed(1f, 0.999f))
    }

    @Test
    fun `a ring plays all of its marks at the end too`() {
        assertTrue((0 until 60).all { ProgressScale.isPlayed(it / 60f, 1f) })
        assertTrue((0 until 60).none { ProgressScale.isPlayed(it / 60f, 0f) })
    }
}
