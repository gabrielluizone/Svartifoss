package com.svartifoss.snfell.music

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackReportPolicyTest {

    private val playing = PlaybackReportPolicy.Snapshot(
            playing = true,
            positionMs = 60_000L,
            positionUpdateTimeMs = 1_000_000L,
            speed = 1f,
            actions = 0x1FFL,
            activeQueueItemId = 7L,
            customActions = listOf("like|Like|12"))

    /** The flood this filters: a player publishing its position once a second. */
    @Test
    fun `a routine position update is not reported`() {
        val oneSecondLater = playing.copy(positionMs = 61_000L, positionUpdateTimeMs = 1_001_000L)

        assertFalse(PlaybackReportPolicy.worthReporting(playing, oneSecondLater))
    }

    @Test
    fun `a seek made on the phone is reported`() {
        val seeked = playing.copy(positionMs = 150_000L, positionUpdateTimeMs = 1_001_000L)

        assertTrue(PlaybackReportPolicy.worthReporting(playing, seeked))
    }

    @Test
    fun `a jump back is reported too`() {
        val restarted = playing.copy(positionMs = 0L, positionUpdateTimeMs = 1_001_000L)

        assertTrue(PlaybackReportPolicy.worthReporting(playing, restarted))
    }

    @Test
    fun `jitter below the seek threshold is not reported`() {
        val jittered = playing.copy(positionMs = 62_200L, positionUpdateTimeMs = 1_001_000L)

        assertFalse(PlaybackReportPolicy.worthReporting(playing, jittered))
    }

    /** A like toggled in the player changes its custom action's label or icon, nothing else. */
    @Test
    fun `a changed custom action is reported`() {
        val liked = playing.copy(customActions = listOf("like|Unlike|13"))

        assertTrue(PlaybackReportPolicy.worthReporting(playing, liked))
    }

    @Test
    fun `speed, actions and the active queue entry are reported`() {
        assertTrue(PlaybackReportPolicy.worthReporting(playing, playing.copy(speed = 1.5f)))
        assertTrue(PlaybackReportPolicy.worthReporting(playing, playing.copy(actions = 0x1L)))
        assertTrue(PlaybackReportPolicy.worthReporting(playing, playing.copy(activeQueueItemId = 8L)))
    }

    @Test
    fun `playback speed is taken into account when predicting the position`() {
        val fast = playing.copy(speed = 2f)
        val twoSecondsOfPlayback = fast.copy(positionMs = 62_000L, positionUpdateTimeMs = 1_001_000L)

        assertFalse(PlaybackReportPolicy.worthReporting(fast, twoSecondsOfPlayback))
    }

    @Test
    fun `a paused position that has not moved is not reported`() {
        val paused = playing.copy(playing = false)
        val later = paused.copy(positionUpdateTimeMs = 1_030_000L)

        assertFalse(PlaybackReportPolicy.worthReporting(paused, later))
    }

    @Test
    fun `nothing is reported without a previous sample`() {
        assertFalse(PlaybackReportPolicy.worthReporting(null, playing))
    }
}
