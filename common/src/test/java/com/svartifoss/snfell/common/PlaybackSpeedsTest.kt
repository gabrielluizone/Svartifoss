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
}
