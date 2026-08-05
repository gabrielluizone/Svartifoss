package com.svartifoss.snfell.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins [resolveReportedSession], the rule that decides which media session the watch is told
 * about.
 *
 * The regression these guard against: pausing sent the watch to the "Nothing playing" idle screen.
 * The first pass after a pause looked right, so the bug only appeared on the *second* pass - any
 * audio-info change from an idle player, a session-list change or a re-activate - once the field
 * the fallback read had already been cleared. Repeated resolution is therefore the case that
 * actually matters here, not the single-shot one.
 */
class ReportedSessionTest {

    @Test
    fun aPlayingSessionIsAlwaysReported() {
        assertEquals("playing", resolveReportedSession(
                playing = "playing", lastReported = "other", stillActive = { true }))
    }

    @Test
    fun pausingKeepsReportingTheSameSession() {
        // Nothing is playing, but the paused session is still live: the watch must keep showing
        // the track, not drop to the idle screen.
        assertEquals("paused", resolveReportedSession(
                playing = null, lastReported = "paused", stillActive = { true }))
    }

    @Test
    fun repeatedResolutionWhilePausedKeepsTheSession() {
        // The actual regression. Feeding each result back in models the second/third pass that
        // used to collapse to null.
        var reported: String? = "paused"
        repeat(5) {
            reported = resolveReportedSession(
                    playing = null, lastReported = reported, stillActive = { true })
        }
        assertEquals("paused", reported)
    }

    @Test
    fun aSessionThatWentAwayResolvesToNull() {
        // The player was closed - the idle screen is now the correct thing to show, so the
        // retention must not be unconditional.
        assertNull(resolveReportedSession(
                playing = null, lastReported = "gone", stillActive = { false }))
    }

    @Test
    fun nothingEverSeenResolvesToNull() {
        assertNull(resolveReportedSession<String>(
                playing = null, lastReported = null, stillActive = { true }))
    }

    @Test
    fun aNewlyPlayingSessionReplacesTheRetainedOne() {
        // Resuming into a different app must switch immediately rather than linger on the old one.
        assertEquals("new", resolveReportedSession(
                playing = "new", lastReported = "old", stillActive = { true }))
    }
}
