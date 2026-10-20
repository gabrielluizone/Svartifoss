package com.svartifoss.snfell.music

import com.svartifoss.snfell.music.EndOfTrackSleep.Decision
import com.svartifoss.snfell.music.EndOfTrackSleep.Playback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EndOfTrackSleepTest {

    private fun reading(
            key: String = "a|b",
            positionMs: Long = 0L,
            durationMs: Long = 200_000L,
            playing: Boolean = true,
            speed: Float = 1f,
            at: Long = 1_000_000L
    ) = Playback(key, positionMs, durationMs, playing, speed, at)

    @Test
    fun `a playing track is paused just before its end`() {
        val decision = EndOfTrackSleep.decide(null, reading(positionMs = 150_000L))
        assertEquals(Decision.PauseIn(50_000L - EndOfTrackSleep.END_MARGIN_MS), decision)
    }

    @Test
    fun `the schedule follows the playback speed`() {
        val decision = EndOfTrackSleep.decide(null, reading(positionMs = 100_000L, speed = 2f))
        assertEquals(Decision.PauseIn(50_000L - EndOfTrackSleep.END_MARGIN_MS), decision)
    }

    @Test
    fun `a track a moment from its end is paused at once rather than scheduled into the past`() {
        val decision = EndOfTrackSleep.decide(null, reading(positionMs = 199_900L))
        assertEquals(Decision.PauseIn(0L), decision)
    }

    @Test
    fun `a paused track schedules nothing, and neither does one with no length`() {
        assertEquals(Decision.Wait, EndOfTrackSleep.decide(null, reading(playing = false)))
        assertEquals(Decision.Wait, EndOfTrackSleep.decide(null, reading(durationMs = 0L)))
    }

    @Test
    fun `a track that changed at its end pauses the next one`() {
        val before = reading(positionMs = 195_000L, at = 0L)
        // Read again 4.5 s later: the old track had run to 199.5 s of 200 s when the new one began.
        val after = reading(key = "c|d", positionMs = 0L, at = 4_500L)
        assertEquals(Decision.PauseNow, EndOfTrackSleep.decide(before, after))
    }

    @Test
    fun `a skip in the middle of a track moves the timer on to the new one`() {
        val before = reading(positionMs = 30_000L, at = 0L)
        val after = reading(key = "c|d", positionMs = 0L, durationMs = 180_000L, at = 1_000L)
        assertEquals(Decision.PauseIn(180_000L - EndOfTrackSleep.END_MARGIN_MS),
                EndOfTrackSleep.decide(before, after))
    }

    @Test
    fun `a live stream can only end by changing`() {
        val live = reading(durationMs = 0L, positionMs = 3_600_000L, at = 0L).copy(live = true)
        val next = reading(key = "c|d", durationMs = 0L, at = 1_000L)
        assertEquals(Decision.PauseNow, EndOfTrackSleep.decide(live, next))
    }

    @Test
    fun `a length not published yet is not a live stream`() {
        // A skip in progress: the player published the next track with a duration of 0, and then
        // moved on again before filling it in. That change is a skip, not an ending.
        val skipping = reading(key = "c|d", durationMs = 0L, positionMs = 0L, playing = false, at = 0L)
        val next = reading(key = "e|f", durationMs = 0L, playing = false, at = 300L)
        assertEquals(Decision.Wait, EndOfTrackSleep.decide(skipping, next))
    }

    @Test
    fun `a paused track that changed is a skip, measured from where it stopped`() {
        val pausedEarly = reading(positionMs = 20_000L, playing = false, at = 0L)
        // Ten minutes paused does not move a paused track's position towards its end.
        val next = reading(key = "c|d", playing = false, at = 600_000L)
        assertEquals(Decision.Wait, EndOfTrackSleep.decide(pausedEarly, next))
    }

    @Test
    fun `time left is real time, and unknown for a track with no length`() {
        assertEquals(25_000L, reading(positionMs = 150_000L, speed = 2f).timeLeftMs(1_000_000L))
        assertEquals(5_000L, reading(positionMs = 150_000L, speed = 2f).timeLeftMs(1_020_000L))
        assertNull(reading(durationMs = 0L).timeLeftMs(1_000_000L))
    }
}
