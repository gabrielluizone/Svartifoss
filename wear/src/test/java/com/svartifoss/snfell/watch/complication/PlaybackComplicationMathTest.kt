package com.svartifoss.snfell.watch.complication

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackComplicationMathTest {

    private fun math(
            positionMs: Long = 60_000L,
            durationMs: Long = 240_000L,
            playing: Boolean = true,
            speed: Float = 1f,
            sampledAt: Long = 1_000_000L
    ) = PlaybackComplicationMath(positionMs, durationMs, playing, speed, sampledAt)

    @Test
    fun `a playing track advances from its sample`() {
        assertEquals(90_000L, math().positionAt(1_030_000L))
        assertEquals(0.375f, math().fractionAt(1_030_000L), 0.0001f)
    }

    @Test
    fun `a paused track stays where it stopped`() {
        assertEquals(60_000L, math(playing = false).positionAt(9_000_000L))
    }

    @Test
    fun `speed scales the advance, and the anchor is where it would have started`() {
        val fast = math(speed = 2f)
        assertEquals(120_000L, fast.positionAt(1_030_000L))
        assertEquals(1_000_000L - 30_000L, fast.trackStartEpochMs(1_030_000L))
    }

    @Test
    fun `the prediction never runs past the end nor before the start`() {
        assertEquals(240_000L, math().positionAt(5_000_000L))
        assertEquals(1f, math().fractionAt(5_000_000L), 0f)
        // A watch clock a little behind the phone's must not read a negative position.
        assertEquals(0L, math(positionMs = 0L).positionAt(990_000L))
    }

    @Test
    fun `a track with no length has no fraction but still a position`() {
        val live = math(durationMs = 0L)
        assertEquals(0f, live.fractionAt(1_030_000L), 0f)
        assertEquals(90_000L, live.positionAt(1_030_000L))
        assertEquals(0f, live.fractionPerSecond, 0f)
    }

    @Test
    fun `a missing sample time reads the position as true now`() {
        val undated = math(sampledAt = 0L)
        assertEquals(60_000L, undated.positionAt(1_234_000L))
    }

    @Test
    fun `the per-second growth matches the fraction a face would count to`() {
        val m = math(speed = 1.5f)
        val start = m.trackStartEpochMs(1_000_000L)
        val now = 1_040_000L
        val counted = (now - start) / 1000f * m.fractionPerSecond
        assertEquals(m.fractionAt(now), counted, 0.001f)
    }

    @Test
    fun `a session reporting no speed is read at normal speed`() {
        assertEquals(90_000L, math(speed = 0f).positionAt(1_030_000L))
    }
}
