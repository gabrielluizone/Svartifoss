package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSyncPolicyTest {

    // ---- round-trip compensation -----------------------------------------

    @Test
    fun `half the round trip is the inbound leg`() {
        assertEquals(150L, PlaybackSyncPolicy.oneWayDelayMs(300L))
    }

    @Test
    fun `an instant round trip adds nothing`() {
        assertEquals(0L, PlaybackSyncPolicy.oneWayDelayMs(0L))
    }

    /** A token from before a process restart can produce nonsense; a negative age would run the
     *  lyric ahead of the song rather than behind it. */
    @Test
    fun `a nonsensical negative round trip never yields a negative age`() {
        assertEquals(0L, PlaybackSyncPolicy.oneWayDelayMs(-5_000L))
    }

    @Test
    fun `an ordinary bluetooth round trip is usable`() {
        assertTrue(PlaybackSyncPolicy.isUsableRoundTrip(250L))
    }

    /**
     * The reason the cap exists: a reply delayed by a congested link or a service that had to be
     * started did not spend its time symmetrically, so halving it injects error instead of removing
     * it.
     */
    @Test
    fun `a round trip past the cap is discarded rather than halved`() {
        assertFalse(PlaybackSyncPolicy.isUsableRoundTrip(
                PlaybackSyncPolicy.MAX_USABLE_ROUND_TRIP_MS + 1))
    }

    @Test
    fun `a negative round trip is discarded`() {
        assertFalse(PlaybackSyncPolicy.isUsableRoundTrip(-1L))
    }

    // ---- correction sizing ------------------------------------------------

    /** Most checks should find nothing and change nothing - that is the design, not a shortcut. */
    @Test
    fun `drift inside the tolerance is left completely alone`() {
        assertEquals(0L, PlaybackSyncPolicy.correctionMs(100L))
        assertEquals(0L, PlaybackSyncPolicy.correctionMs(-100L))
        assertFalse(PlaybackSyncPolicy.needsCorrection(100L))
    }

    @Test
    fun `drift exactly at the ignore threshold is acted on`() {
        assertTrue(PlaybackSyncPolicy.needsCorrection(PlaybackSyncPolicy.IGNORE_THRESHOLD_MS))
    }

    @Test
    fun `middling drift is only partly absorbed so no single check is visible`() {
        val drift = 200L
        val correction = PlaybackSyncPolicy.correctionMs(drift)
        assertTrue("expected a partial correction, got $correction",
                correction != 0L && kotlin.math.abs(correction) < kotlin.math.abs(drift))
    }

    @Test
    fun `middling drift keeps its sign in both directions`() {
        assertTrue(PlaybackSyncPolicy.correctionMs(200L) > 0L)
        assertTrue(PlaybackSyncPolicy.correctionMs(-200L) < 0L)
    }

    /** Past the step threshold the line is already visibly wrong; easing in would prolong it. */
    @Test
    fun `large drift is corrected in full`() {
        assertEquals(5_000L, PlaybackSyncPolicy.correctionMs(5_000L))
        assertEquals(-5_000L, PlaybackSyncPolicy.correctionMs(-5_000L))
    }

    @Test
    fun `drift exactly at the step threshold snaps rather than eases`() {
        val drift = PlaybackSyncPolicy.STEP_THRESHOLD_MS
        assertEquals(drift, PlaybackSyncPolicy.correctionMs(drift))
    }

    /**
     * Repeatedly applying the partial correction has to converge, or the screen would ease towards
     * the truth forever without arriving.
     */
    @Test
    fun `repeated partial corrections converge into the tolerance`() {
        var drift = PlaybackSyncPolicy.STEP_THRESHOLD_MS - 1
        var checks = 0
        while (PlaybackSyncPolicy.needsCorrection(drift) && checks < 20) {
            drift -= PlaybackSyncPolicy.correctionMs(drift)
            checks++
        }
        assertFalse("still out of tolerance after $checks checks (drift=$drift)",
                PlaybackSyncPolicy.needsCorrection(drift))
    }

    // ---- interval backoff -------------------------------------------------

    @Test
    fun `a quiet check doubles the wait`() {
        assertEquals(PlaybackSyncPolicy.MIN_INTERVAL_MS * 2,
                PlaybackSyncPolicy.nextIntervalMs(PlaybackSyncPolicy.MIN_INTERVAL_MS, corrected = false))
    }

    @Test
    fun `the wait stops growing at the ceiling`() {
        assertEquals(PlaybackSyncPolicy.MAX_INTERVAL_MS,
                PlaybackSyncPolicy.nextIntervalMs(PlaybackSyncPolicy.MAX_INTERVAL_MS, corrected = false))
    }

    /** An unsettled track is watched closely for as long as it stays unsettled, and not one
     *  message longer. */
    @Test
    fun `a correction drops the wait straight back to the floor`() {
        assertEquals(PlaybackSyncPolicy.MIN_INTERVAL_MS,
                PlaybackSyncPolicy.nextIntervalMs(PlaybackSyncPolicy.MAX_INTERVAL_MS, corrected = true))
    }

    @Test
    fun `a nonsensical stored interval is clamped back into range`() {
        assertEquals(PlaybackSyncPolicy.MIN_INTERVAL_MS,
                PlaybackSyncPolicy.nextIntervalMs(0L, corrected = false))
    }

    @Test
    fun `backing off from the floor reaches the ceiling in a bounded number of steps`() {
        var interval = PlaybackSyncPolicy.MIN_INTERVAL_MS
        var steps = 0
        while (interval < PlaybackSyncPolicy.MAX_INTERVAL_MS && steps < 20) {
            interval = PlaybackSyncPolicy.nextIntervalMs(interval, corrected = false)
            steps++
        }
        assertEquals(PlaybackSyncPolicy.MAX_INTERVAL_MS, interval)
    }

    // ---- keepsLocalPosition / predatesLocalChange ----

    /**
     * The burst the rule exists for: three presses of "back 10 seconds" on the crown, with the
     * phone's confirmation of an earlier press landing after a later one has been drawn.
     */
    @Test
    fun `a confirmation landing just after a local change leaves the drawn position alone`() {
        assertTrue(PlaybackSyncPolicy.keepsLocalPosition(
                sinceLocalChangeMs = 120L, sameTrack = true, samePlayingState = true))
    }

    @Test
    fun `the hold ends when the check the local change scheduled is due`() {
        assertTrue(PlaybackSyncPolicy.keepsLocalPosition(
                sinceLocalChangeMs = PlaybackSyncPolicy.COMMAND_SETTLE_MS,
                sameTrack = true, samePlayingState = true))
        assertFalse(PlaybackSyncPolicy.keepsLocalPosition(
                sinceLocalChangeMs = PlaybackSyncPolicy.COMMAND_SETTLE_MS + 1L,
                sameTrack = true, samePlayingState = true))
    }

    @Test
    fun `a different track is news and always applies`() {
        assertFalse(PlaybackSyncPolicy.keepsLocalPosition(
                sinceLocalChangeMs = 50L, sameTrack = false, samePlayingState = true))
    }

    @Test
    fun `a change of play or pause is news and always applies`() {
        assertFalse(PlaybackSyncPolicy.keepsLocalPosition(
                sinceLocalChangeMs = 50L, sameTrack = true, samePlayingState = false))
    }

    @Test
    fun `nothing is held before this device has ever moved playback`() {
        assertFalse(PlaybackSyncPolicy.keepsLocalPosition(
                sinceLocalChangeMs = null, sameTrack = true, samePlayingState = true))
    }

    @Test
    fun `a state stamped before the local change is not held by it`() {
        // A negative gap means the state arrived first; it is not an answer to this change.
        assertFalse(PlaybackSyncPolicy.keepsLocalPosition(
                sinceLocalChangeMs = -5L, sameTrack = true, samePlayingState = true))
    }

    @Test
    fun `a reply to a question asked before the local change is refused`() {
        assertTrue(PlaybackSyncPolicy.predatesLocalChange(
                requestSentAtMs = 1_000L, localChangeAtMs = 1_200L))
    }

    @Test
    fun `a reply to a question asked after the local change is used`() {
        assertFalse(PlaybackSyncPolicy.predatesLocalChange(
                requestSentAtMs = 2_100L, localChangeAtMs = 1_200L))
        assertFalse(PlaybackSyncPolicy.predatesLocalChange(
                requestSentAtMs = 1_200L, localChangeAtMs = 1_200L))
    }

    @Test
    fun `with no local change every reply is used`() {
        assertFalse(PlaybackSyncPolicy.predatesLocalChange(
                requestSentAtMs = 1_000L, localChangeAtMs = null))
    }
}
