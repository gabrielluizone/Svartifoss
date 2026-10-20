package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSpeedsTest {

    @Test
    fun `each press moves up one rung`() {
        assertEquals(1.25f, PlaybackSpeeds.next(1.0f), 0.0001f)
        assertEquals(1.5f, PlaybackSpeeds.next(1.25f), 0.0001f)
        assertEquals(0.75f, PlaybackSpeeds.next(0.5f), 0.0001f)
    }

    @Test
    fun `the top wraps to the bottom`() {
        assertEquals(PlaybackSpeeds.MIN, PlaybackSpeeds.next(2.0f), 0.0001f)
    }

    @Test
    fun `a full lap visits every rung and returns`() {
        var speed = PlaybackSpeeds.MIN
        val seen = mutableListOf(speed)
        repeat(7) {
            speed = PlaybackSpeeds.next(speed)
            seen += speed
        }
        assertEquals(listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 0.5f), seen)
    }

    @Test
    fun `a speed between rungs snaps to the rung below before stepping`() {
        assertEquals(1.25f, PlaybackSpeeds.next(1.1f), 0.0001f)
        assertEquals(1.0f, PlaybackSpeeds.next(0.9f), 0.0001f)
    }

    @Test
    fun `float noise on a rung does not skip a rung`() {
        assertEquals(1.5f, PlaybackSpeeds.next(1.2500001f), 0.0001f)
        assertEquals(1.25f, PlaybackSpeeds.next(0.9999999f), 0.0001f)
    }

    @Test
    fun `a quick run of presses walks up the ladder before the phone has answered`() {
        var now = 10_000L
        val taps = PlaybackSpeeds.TapMemory { now }
        // The phone keeps reporting 1x throughout: it has not answered yet, or it never will.
        assertEquals(1.25f, taps.next(reported = 1.0f), 0.0001f)
        now += 400
        assertEquals(1.5f, taps.next(reported = 1.0f), 0.0001f)
        now += 400
        assertEquals(1.75f, taps.next(reported = 1.0f), 0.0001f)
    }

    @Test
    fun `a press after the memory has lapsed starts from what the phone reports`() {
        var now = 10_000L
        val taps = PlaybackSpeeds.TapMemory { now }
        assertEquals(1.25f, taps.next(reported = 1.0f), 0.0001f)
        // The player ignored it, so a later press is a first press again.
        now += PlaybackSpeeds.TAP_MEMORY_MS
        assertEquals(1.25f, taps.next(reported = 1.0f), 0.0001f)
        // Just inside the memory it still counts.
        now += PlaybackSpeeds.TAP_MEMORY_MS - 1
        assertEquals(1.5f, taps.next(reported = 1.0f), 0.0001f)
    }

    @Test
    fun `the first press ever steps from the reported speed, whatever the clock reads`() {
        val taps = PlaybackSpeeds.TapMemory { 0L }
        assertEquals(2.0f, taps.next(reported = 1.75f), 0.0001f)
    }

    @Test
    fun `out of range input lands inside the ladder`() {
        assertEquals(PlaybackSpeeds.MIN, PlaybackSpeeds.next(0.1f), 0.0001f)
        assertEquals(PlaybackSpeeds.MIN, PlaybackSpeeds.next(3.0f), 0.0001f)
    }

    @Test
    fun `fine steps move by tenths in both directions`() {
        assertEquals(1.1f, PlaybackSpeeds.fineStep(1.0f, 1), 0.0001f)
        assertEquals(0.9f, PlaybackSpeeds.fineStep(1.0f, -1), 0.0001f)
        assertEquals(2.3f, PlaybackSpeeds.fineStep(2.0f, 3), 0.0001f)
    }

    @Test
    fun `a speed between tenths snaps to the nearest before stepping`() {
        assertEquals(1.4f, PlaybackSpeeds.fineStep(1.25f, 1), 0.0001f)
        assertEquals(1.2f, PlaybackSpeeds.fineStep(1.25f, -1), 0.0001f)
        assertEquals(1.8f, PlaybackSpeeds.fineStep(1.75f, 0), 0.0001f)
    }

    @Test
    fun `fine steps stop at the ends of their range`() {
        assertEquals(PlaybackSpeeds.FINE_MAX, PlaybackSpeeds.fineStep(2.95f, 4), 0.0001f)
        assertEquals(PlaybackSpeeds.FINE_MIN, PlaybackSpeeds.fineStep(0.6f, -5), 0.0001f)
    }

    @Test
    fun `a long run of fine steps does not drift off the tenths`() {
        var speed = 0.5f
        repeat(25) { speed = PlaybackSpeeds.fineStep(speed, 1) }
        assertEquals(3.0f, speed, 0.00001f)
    }

    @Test
    fun `the ladder wraps from a fine speed above its top`() {
        assertEquals(PlaybackSpeeds.MIN, PlaybackSpeeds.next(2.6f), 0.0001f)
    }

    @Test
    fun `labels drop trailing zeros and always use a point`() {
        assertEquals("1×", PlaybackSpeeds.label(1.0f))
        assertEquals("1.25×", PlaybackSpeeds.label(1.25f))
        assertEquals("2.3×", PlaybackSpeeds.label(2.3f))
        assertEquals("0.5×", PlaybackSpeeds.label(0.5f))
    }

    @Test
    fun `fine steps and resets share the press memory with the ladder`() {
        var now = 10_000L
        val taps = PlaybackSpeeds.TapMemory { now }
        assertEquals(1.1f, taps.fineStep(reported = 1.0f, steps = 1), 0.0001f)
        now += 300
        assertEquals(1.2f, taps.fineStep(reported = 1.0f, steps = 1), 0.0001f)
        assertEquals(1.2f, taps.recent()!!, 0.0001f)
        now += 300
        assertEquals(1.0f, taps.set(1.0f), 0.0001f)
        now += PlaybackSpeeds.TAP_MEMORY_MS
        assertEquals(null, taps.recent())
    }
}
