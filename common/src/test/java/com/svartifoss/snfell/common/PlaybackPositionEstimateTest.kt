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

    // ---- reportableSample ----

    private val seek = PlaybackPositionEstimate.PendingSeek(targetMs = 60_000L, issuedAtRealtimeMs = 10_000L)

    @Test
    fun `a sample with nothing newer than it is reported as published`() {
        assertEquals(PlaybackPositionEstimate.PositionSample(120_000L, 9_000L),
                PlaybackPositionEstimate.reportableSample(
                        positionMs = 120_000L, sampleRealtimeMs = 9_000L,
                        trackFirstSeenRealtimeMs = 1_000L, pendingSeek = null,
                        nowRealtimeMs = 12_000L))
    }

    @Test
    fun `a seek the player has not answered stands in for its stale sample`() {
        // The reported case: the player last published at 9s, before the seek went out at 10s.
        // Its sample still says where playback was before the seek, and reporting it would put
        // the watch back there after it had drawn the new position.
        assertEquals(PlaybackPositionEstimate.PositionSample(60_000L, 10_000L),
                PlaybackPositionEstimate.reportableSample(
                        positionMs = 120_000L, sampleRealtimeMs = 9_000L,
                        trackFirstSeenRealtimeMs = 1_000L, pendingSeek = seek,
                        nowRealtimeMs = 13_000L))
    }

    @Test
    fun `any sample published after the seek stands the seek down, whatever it says`() {
        // Including one that ignores the seek entirely - that is the player's answer, not ours.
        assertEquals(PlaybackPositionEstimate.PositionSample(123_000L, 10_500L),
                PlaybackPositionEstimate.reportableSample(
                        positionMs = 123_000L, sampleRealtimeMs = 10_500L,
                        trackFirstSeenRealtimeMs = 1_000L, pendingSeek = seek,
                        nowRealtimeMs = 13_000L))
    }

    @Test
    fun `a sample taken at the instant the seek went out counts as its answer`() {
        assertEquals(PlaybackPositionEstimate.PositionSample(60_100L, 10_000L),
                PlaybackPositionEstimate.reportableSample(
                        positionMs = 60_100L, sampleRealtimeMs = 10_000L,
                        trackFirstSeenRealtimeMs = 1_000L, pendingSeek = seek,
                        nowRealtimeMs = 13_000L))
    }

    @Test
    fun `an unanswered seek stops standing in once it is too old`() {
        // A player that ignored the seek and then said nothing: past the bound, its own last
        // sample is the better guess again.
        val now = seek.issuedAtRealtimeMs + PlaybackPositionEstimate.PENDING_SEEK_MAX_AGE_MS + 1L
        assertEquals(PlaybackPositionEstimate.PositionSample(120_000L, 9_000L),
                PlaybackPositionEstimate.reportableSample(
                        positionMs = 120_000L, sampleRealtimeMs = 9_000L,
                        trackFirstSeenRealtimeMs = 1_000L, pendingSeek = seek,
                        nowRealtimeMs = now))
    }

    @Test
    fun `an unanswered seek still stands at exactly the bound`() {
        val now = seek.issuedAtRealtimeMs + PlaybackPositionEstimate.PENDING_SEEK_MAX_AGE_MS
        assertEquals(PlaybackPositionEstimate.PositionSample(60_000L, 10_000L),
                PlaybackPositionEstimate.reportableSample(
                        positionMs = 120_000L, sampleRealtimeMs = 9_000L,
                        trackFirstSeenRealtimeMs = 1_000L, pendingSeek = seek,
                        nowRealtimeMs = now))
    }

    @Test
    fun `a sample from the previous track is reported as zero when this one was first seen`() {
        // Not as zero "now": rebuilt three seconds later, that would put the track back at its
        // start, where zero at 5s keeps it advancing correctly.
        assertEquals(PlaybackPositionEstimate.PositionSample(0L, 5_000L),
                PlaybackPositionEstimate.reportableSample(
                        positionMs = 150_000L, sampleRealtimeMs = 4_000L,
                        trackFirstSeenRealtimeMs = 5_000L, pendingSeek = null,
                        nowRealtimeMs = 8_000L))
    }

    @Test
    fun `with no track boundary to protect every published sample counts`() {
        // The service started mid-song, or the user switched apps: the player published long
        // before this side first read the track, and that sample is still the right one.
        assertEquals(PlaybackPositionEstimate.PositionSample(200_000L, 2_000L),
                PlaybackPositionEstimate.reportableSample(
                        positionMs = 200_000L, sampleRealtimeMs = 2_000L,
                        trackFirstSeenRealtimeMs = 0L, pendingSeek = null,
                        nowRealtimeMs = 90_000L))
    }

    @Test
    fun `a session that never published a position is zero now, not zero at boot`() {
        assertEquals(PlaybackPositionEstimate.PositionSample(0L, 90_000L),
                PlaybackPositionEstimate.reportableSample(
                        positionMs = 0L, sampleRealtimeMs = 0L,
                        trackFirstSeenRealtimeMs = 0L, pendingSeek = null,
                        nowRealtimeMs = 90_000L))
    }

    // ---- relativeSeekTargetMs ----

    @Test
    fun `forward seek advances immediately by requested amount`() {
        assertEquals(70_000L, PlaybackPositionEstimate.relativeSeekTargetMs(60_000L, 10_000L, 180_000L))
    }

    @Test
    fun `backward seek clamps at start`() {
        assertEquals(0L, PlaybackPositionEstimate.relativeSeekTargetMs(4_000L, -10_000L, 180_000L))
    }

    @Test
    fun `forward seek clamps at known duration`() {
        assertEquals(180_000L, PlaybackPositionEstimate.relativeSeekTargetMs(175_000L, 30_000L, 180_000L))
    }

    @Test
    fun `unknown duration still permits forward seek`() {
        // A live stream: nothing but the start bounds it.
        assertEquals(35_000L, PlaybackPositionEstimate.relativeSeekTargetMs(30_000L, 5_000L, 0L))
    }

    /**
     * The other reported fault, as one case: a live stream whose player published its position
     * once, when it started, and nothing in the five minutes since. Seeking back ten seconds from
     * that raw sample went to the very start; from the extrapolated position it goes back ten
     * seconds.
     */
    @Test
    fun `a relative seek from the extrapolated position does not restart a quiet stream`() {
        val sample = PlaybackPositionEstimate.reportableSample(
                positionMs = 3_000L, sampleRealtimeMs = 10_000L,
                trackFirstSeenRealtimeMs = 0L, pendingSeek = null,
                nowRealtimeMs = 310_000L)
        val live = PlaybackPositionEstimate.positionAtMs(
                positionMs = sample.positionMs, durationMs = 0L, playing = true,
                playbackSpeed = 1f, elapsedSinceSampleMs = 310_000L - sample.sampledAtRealtimeMs)

        assertEquals(0L, PlaybackPositionEstimate.relativeSeekTargetMs(3_000L, -10_000L, 0L))
        assertEquals(293_000L, PlaybackPositionEstimate.relativeSeekTargetMs(live, -10_000L, 0L))
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
