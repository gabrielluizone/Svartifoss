package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the arithmetic that decides where the watch thinks playback has reached.
 *
 * The bug this was extracted from is invisible in a code review: subtracting a phone timestamp from
 * the watch's own clock reads as ordinary elapsed-time maths and is wrong only by however far the
 * two devices' clocks happen to sit apart.
 */
class PlaybackPositionEstimateTest {

    // ---- elapsedSinceSampleMs ----

    @Test
    fun `an age plus the time held locally is the elapsed time`() {
        // The phone sampled 400ms before it sent; the watch has held it 1600ms since.
        assertEquals(2_000L, PlaybackPositionEstimate.elapsedSinceSampleMs(
                positionAgeMs = 400L, sinceAnchorMs = 1_600L, legacyElapsedMs = 999_999L))
    }

    @Test
    fun `the legacy cross-device value is ignored whenever an age is present`() {
        // The whole point: a wildly skewed legacy figure must not influence the answer at all.
        assertEquals(500L, PlaybackPositionEstimate.elapsedSinceSampleMs(
                positionAgeMs = 0L, sinceAnchorMs = 500L, legacyElapsedMs = -8_000L))
    }

    @Test
    fun `an age of zero is honoured, not treated as missing`() {
        // A locally re-anchored state (a seek, or optimistic play-pause) reports exactly this.
        assertEquals(120L, PlaybackPositionEstimate.elapsedSinceSampleMs(
                positionAgeMs = 0L, sinceAnchorMs = 120L, legacyElapsedMs = 60_000L))
    }

    @Test
    fun `a missing age falls back to the legacy value`() {
        assertEquals(3_000L, PlaybackPositionEstimate.elapsedSinceSampleMs(
                positionAgeMs = PlaybackPositionEstimate.NO_AGE,
                sinceAnchorMs = 10L,
                legacyElapsedMs = 3_000L))
    }

    @Test
    fun `a negative legacy value is clamped to zero rather than run backwards`() {
        // What a watch clock running behind the phone's used to produce.
        assertEquals(0L, PlaybackPositionEstimate.elapsedSinceSampleMs(
                positionAgeMs = PlaybackPositionEstimate.NO_AGE,
                sinceAnchorMs = 0L,
                legacyElapsedMs = -4_500L))
    }

    // ---- sampleBelongsToTrack ----

    @Test
    fun `a sample taken after the track was first seen belongs to it`() {
        assertTrue(PlaybackPositionEstimate.sampleBelongsToTrack(
                sampleRealtimeMs = 5_000L, trackFirstSeenRealtimeMs = 4_000L))
    }

    @Test
    fun `a sample taken before the track was first seen belongs to the previous one`() {
        // The 2:30-into-4:00 case: metadata for the new track arrives before its playback state,
        // so the position still describes the track that just ended.
        assertFalse(PlaybackPositionEstimate.sampleBelongsToTrack(
                sampleRealtimeMs = 3_999L, trackFirstSeenRealtimeMs = 4_000L))
    }

    @Test
    fun `a sample taken at the same instant counts as belonging`() {
        assertTrue(PlaybackPositionEstimate.sampleBelongsToTrack(
                sampleRealtimeMs = 4_000L, trackFirstSeenRealtimeMs = 4_000L))
    }

    @Test
    fun `a session that never published a position has no sample at all`() {
        assertFalse(PlaybackPositionEstimate.sampleBelongsToTrack(
                sampleRealtimeMs = 0L, trackFirstSeenRealtimeMs = 0L))
    }

    // ---- positionAtMs ----

    @Test
    fun `a playing track advances by the elapsed time`() {
        assertEquals(12_000L, PlaybackPositionEstimate.positionAtMs(
                positionMs = 10_000L, durationMs = 200_000L, playing = true,
                playbackSpeed = 1f, elapsedSinceSampleMs = 2_000L))
    }

    @Test
    fun `playback speed scales the advance`() {
        assertEquals(13_000L, PlaybackPositionEstimate.positionAtMs(
                positionMs = 10_000L, durationMs = 200_000L, playing = true,
                playbackSpeed = 1.5f, elapsedSinceSampleMs = 2_000L))
    }

    @Test
    fun `a paused track does not advance at all`() {
        // Advancing it is what would keep a paused lyric scrolling.
        assertEquals(10_000L, PlaybackPositionEstimate.positionAtMs(
                positionMs = 10_000L, durationMs = 200_000L, playing = false,
                playbackSpeed = 1f, elapsedSinceSampleMs = 90_000L))
    }

    @Test
    fun `the result is capped at the track duration`() {
        assertEquals(200_000L, PlaybackPositionEstimate.positionAtMs(
                positionMs = 190_000L, durationMs = 200_000L, playing = true,
                playbackSpeed = 1f, elapsedSinceSampleMs = 60_000L))
    }

    @Test
    fun `an unknown duration leaves the position uncapped`() {
        assertEquals(250_000L, PlaybackPositionEstimate.positionAtMs(
                positionMs = 190_000L, durationMs = 0L, playing = true,
                playbackSpeed = 1f, elapsedSinceSampleMs = 60_000L))
    }

    @Test
    fun `the position never goes below zero`() {
        assertEquals(0L, PlaybackPositionEstimate.positionAtMs(
                positionMs = -500L, durationMs = 200_000L, playing = false,
                playbackSpeed = 1f, elapsedSinceSampleMs = 0L))
    }

    /**
     * The end-to-end shape of the fault, as one case: two devices whose clocks sit 6 seconds apart,
     * on a track that has been playing for 30 seconds since the last state was sent.
     */
    @Test
    fun `a six second clock skew no longer reaches the reported position`() {
        val trueElapsed = 30_000L
        val clockSkew = 6_000L

        val skewed = PlaybackPositionEstimate.elapsedSinceSampleMs(
                positionAgeMs = PlaybackPositionEstimate.NO_AGE,
                sinceAnchorMs = trueElapsed,
                legacyElapsedMs = trueElapsed + clockSkew)
        assertEquals(36_000L, skewed)

        val corrected = PlaybackPositionEstimate.elapsedSinceSampleMs(
                positionAgeMs = 0L,
                sinceAnchorMs = trueElapsed,
                legacyElapsedMs = trueElapsed + clockSkew)
        assertEquals(30_000L, corrected)
    }
}
