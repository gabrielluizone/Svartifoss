package com.svartifoss.snfell.music

import com.svartifoss.snfell.music.VisibleOpenFollowUp.Companion.STATE_BUFFERING
import com.svartifoss.snfell.music.VisibleOpenFollowUp.Companion.STATE_CONNECTING
import com.svartifoss.snfell.music.VisibleOpenFollowUp.Companion.STATE_PAUSED
import com.svartifoss.snfell.music.VisibleOpenFollowUp.Companion.STATE_PLAYING
import com.svartifoss.snfell.music.VisibleOpenFollowUp.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The follow-up to a shortcut that had to be opened on the phone's screen.
 *
 * Every case is a timeline of what the app's session reports, read every 250 ms as the service
 * reads it. The ones about a cold start are the reported bug: the app came up on the right page,
 * holding the track it played last, and was pressed play.
 */
class VisibleOpenFollowUpTest {

    private val old = PlaybackIdentity(queueItemId = 3, mediaId = "old:1", title = "Old Track")
    private val requested = PlaybackIdentity(queueItemId = 0, mediaId = "new:1", title = "Requested Track")

    /** One reading of the session: its state, or null for no session, and what it holds. */
    private data class Reading(val state: Int?, val identity: PlaybackIdentity = PlaybackIdentity.NONE)

    private val noSession = Reading(null)

    /** Follows [timeline] and returns every step that was not a wait, with its time. */
    private fun follow(
            followUp: VisibleOpenFollowUp,
            untilMs: Long = VisibleOpenFollowUp.FOLLOW_MS,
            timeline: (Long) -> Reading
    ): List<Pair<Long, Step>> {
        val steps = mutableListOf<Pair<Long, Step>>()
        var elapsed = VisibleOpenFollowUp.POLL_MS
        while (elapsed <= untilMs) {
            val reading = timeline(elapsed)
            val step = followUp.next(reading.state, reading.identity, elapsed)
            if (step != Step.WAIT) steps += elapsed to step
            if (step == Step.PLAYED) break
            elapsed += VisibleOpenFollowUp.POLL_MS
        }
        return steps
    }

    private fun cold(canRequest: Boolean = true) =
            VisibleOpenFollowUp(wasPlayingBefore = false, identityBefore = PlaybackIdentity.NONE,
                    canRequest = canRequest)

    private fun List<Pair<Long, Step>>.count(step: Step) = count { it.second == step }

    @Test
    fun aColdAppThatOnlyNavigatesIsNeverPressedPlay() {
        // The app opens on the right page 1.5 s in, reconnects, and settles paused on the track it
        // played last. The link only navigated; nothing it holds is what was asked for.
        val steps = follow(cold()) { t ->
            when {
                t < 1_500 -> noSession
                t < 2_400 -> Reading(STATE_CONNECTING, old)
                else -> Reading(STATE_PAUSED, old)
            }
        }
        assertEquals(0, steps.count(Step.PRESS_PLAY))
        assertEquals(0, steps.count(Step.PLAYED))
        assertEquals(VisibleOpenFollowUp.MAX_REQUESTS, steps.count(Step.REQUEST))
    }

    @Test
    fun aColdAppIsAskedOnceItsSessionSettlesHoweverLateItComesUp() {
        // The loop this replaces gave up 3.2 s after the open was asked for.
        val steps = follow(cold()) { t -> if (t < 6_000) noSession else Reading(STATE_PAUSED, old) }
        val first = steps.first { it.second == Step.REQUEST }.first
        assertTrue("asked only after the session settled, at $first", first >= 6_000 + 1_000)
        assertTrue("asked soon after it settled, at $first", first <= 6_000 + 1_500)
    }

    @Test
    fun aRequestIsNotSentWhileTheAppIsConnectingOrBuffering() {
        val connecting = follow(cold()) { Reading(STATE_CONNECTING, old) }
        val buffering = follow(cold()) { Reading(STATE_BUFFERING, old) }
        assertTrue(connecting.isEmpty())
        assertTrue(buffering.isEmpty())
    }

    @Test
    fun contentThatReplacesTheRestoredTrackIsResumed() {
        // Restored track first; the request is honoured, but the app loads the content paused.
        val followUp = cold()
        var requestedAt = -1L
        val steps = mutableListOf<Pair<Long, Step>>()
        var t = VisibleOpenFollowUp.POLL_MS
        var playing = false
        while (t <= VisibleOpenFollowUp.FOLLOW_MS) {
            val reading = when {
                playing -> Reading(STATE_PLAYING, requested)
                requestedAt >= 0 && t >= requestedAt + 500 -> Reading(STATE_PAUSED, requested)
                else -> Reading(STATE_PAUSED, old)
            }
            val step = followUp.next(reading.state, reading.identity, t)
            if (step != Step.WAIT) steps += t to step
            if (step == Step.REQUEST && requestedAt < 0) requestedAt = t
            if (step == Step.PRESS_PLAY) playing = true
            if (step == Step.PLAYED) break
            t += VisibleOpenFollowUp.POLL_MS
        }
        assertEquals(listOf(Step.REQUEST, Step.PRESS_PLAY, Step.PLAYED), steps.map { it.second })
        val loadedAt = requestedAt + 500
        val pressedAt = steps.first { it.second == Step.PRESS_PLAY }.first
        assertTrue("left alone to start by itself first", pressedAt - loadedAt >= VisibleOpenFollowUp.PRESS_PLAY_SETTLE_MS)
    }

    @Test
    fun metadataFilledInStepsIsNotNewContent() {
        // A session coming up publishes a title, then the media id, then its queue position. Read
        // field by field that is three different tracks, and the last of them would be resumed.
        val steps = follow(cold()) { t ->
            when {
                t < 1_000 -> Reading(STATE_PAUSED, PlaybackIdentity(null, null, "Old Track"))
                t < 2_000 -> Reading(STATE_PAUSED, PlaybackIdentity(null, "old:1", "Old Track"))
                else -> Reading(STATE_PAUSED, old)
            }
        }
        assertEquals(0, steps.count(Step.PRESS_PLAY))
    }

    @Test
    fun aWarmAppThatLoadsTheContentPausedIsResumed() {
        val followUp = VisibleOpenFollowUp(wasPlayingBefore = false, identityBefore = old, canRequest = true)
        val steps = follow(followUp, untilMs = 4_000) { t ->
            if (t < 750) Reading(STATE_PAUSED, old) else Reading(STATE_PAUSED, requested)
        }
        assertEquals(listOf(Step.PRESS_PLAY), steps.map { it.second })
        assertEquals("nothing is requested of an app that has already loaded it", 0, followUp.requests)
    }

    @Test
    fun aWarmAppStillOnItsOldTrackIsLeftPaused() {
        val followUp = VisibleOpenFollowUp(wasPlayingBefore = false, identityBefore = old, canRequest = true)
        val steps = follow(followUp) { Reading(STATE_PAUSED, old) }
        assertEquals(0, steps.count(Step.PRESS_PLAY))
        assertEquals(VisibleOpenFollowUp.MAX_REQUESTS, steps.count(Step.REQUEST))
    }

    @Test
    fun anAppStillPlayingWhatItPlayedBeforeIsNotCountedAsPlayed() {
        val followUp = VisibleOpenFollowUp(wasPlayingBefore = true, identityBefore = old, canRequest = true)
        val steps = follow(followUp) { Reading(STATE_PLAYING, old) }
        assertEquals(0, steps.count(Step.PLAYED))
        assertEquals(0, steps.count(Step.PRESS_PLAY))
    }

    @Test
    fun playingSomethingElseThanBeforeIsPlayed() {
        val followUp = VisibleOpenFollowUp(wasPlayingBefore = true, identityBefore = old, canRequest = true)
        val steps = follow(followUp) { t ->
            if (t < 500) Reading(STATE_PLAYING, old) else Reading(STATE_PLAYING, requested)
        }
        assertEquals(listOf(500L to Step.PLAYED), steps)
    }

    @Test
    fun anAppThatStartsByItselfIsNotSentTheRequest() {
        // A link that plays on its own: paused on the restored track for a moment, then buffering
        // and playing what was opened. A request sent into that would restart it.
        val followUp = cold()
        val steps = follow(followUp) { t ->
            when {
                t < 500 -> noSession
                t < 1_250 -> Reading(STATE_PAUSED, old)
                t < 2_500 -> Reading(STATE_BUFFERING, requested)
                else -> Reading(STATE_PLAYING, requested)
            }
        }
        assertEquals(listOf(Step.PLAYED), steps.map { it.second })
        assertEquals(0, followUp.requests)
    }

    @Test
    fun withNothingToRequestNothingIsSentAndTheRestoredTrackIsLeftAlone() {
        // A link that names nothing playable (a provider's short redirect, say).
        val steps = follow(cold(canRequest = false)) { t ->
            if (t < 2_000) noSession else Reading(STATE_PAUSED, old)
        }
        assertTrue(steps.isEmpty())
    }

    @Test
    fun requestsAreSpacedAndCapped() {
        val steps = follow(cold()) { Reading(STATE_PAUSED, old) }
        val times = steps.filter { it.second == Step.REQUEST }.map { it.first }
        assertEquals(VisibleOpenFollowUp.MAX_REQUESTS, times.size)
        times.zipWithNext().forEach { (a, b) ->
            assertTrue("requests $a and $b too close", b - a >= VisibleOpenFollowUp.REQUEST_INTERVAL_MS)
        }
    }

    @Test
    fun playIsPressedOnlyOnce() {
        // The app loaded the content and ignores the press: it is not pressed again.
        val followUp = VisibleOpenFollowUp(wasPlayingBefore = false, identityBefore = old, canRequest = true)
        val steps = follow(followUp) { Reading(STATE_PAUSED, requested) }
        assertEquals(1, steps.count(Step.PRESS_PLAY))
    }

    @Test
    fun anErrorStateIsNotResumed() {
        val error = 7
        val followUp = VisibleOpenFollowUp(wasPlayingBefore = false, identityBefore = old, canRequest = false)
        val steps = follow(followUp) { Reading(error, requested) }
        assertTrue(steps.isEmpty())
    }

    @Test
    fun aSessionThatGoesAwayStartsTheSettlingOver() {
        // Paused for 750 ms, gone, back: the request waits a full settling time from the return.
        val steps = follow(cold(), untilMs = 4_000) { t ->
            when {
                t < 1_000 -> Reading(STATE_PAUSED, old)
                t < 2_000 -> noSession
                else -> Reading(STATE_PAUSED, old)
            }
        }
        val first = steps.first { it.second == Step.REQUEST }.first
        assertTrue("requested at $first", first >= 3_000)
    }

    @Test
    fun theSameTrackIsRecognisedThroughMissingFields() {
        val titleOnly = PlaybackIdentity(null, null, "Old Track")
        val idOnly = PlaybackIdentity(null, "old:1", null)
        assertTrue(old.isSameTrackAs(titleOnly))
        assertTrue(old.isSameTrackAs(idOnly))
        assertTrue("no field in common is no evidence of a change", titleOnly.isSameTrackAs(idOnly))
        assertTrue(PlaybackIdentity.NONE.isSameTrackAs(old))
        assertFalse(old.isSameTrackAs(requested))
        assertFalse(titleOnly.isSameTrackAs(PlaybackIdentity(null, null, "Requested Track")))
        assertFalse(idOnly.isSameTrackAs(PlaybackIdentity(null, "new:1", null)))
        // The title decides where both have one: a queue rebuilt around the same track is the same.
        assertTrue(old.isSameTrackAs(PlaybackIdentity(9, "other-id", "Old Track")))
    }

    @Test
    fun aQueuePositionAloneNamesNoTrack() {
        assertFalse(PlaybackIdentity(4, null, null).hasTrack)
        assertTrue(PlaybackIdentity(null, "id", null).hasTrack)
        assertTrue(PlaybackIdentity(null, null, "Title").hasTrack)
    }

    @Test
    fun onlyAColdAppThatRefusedTheBrowserHasItsWokenSessionAskedFirst() {
        assertTrue(VisibleOpenFollowUp.triesWokenSession(
                browserRefused = true, appWasCold = true, opensInTheSameApp = true))
        // A running app was already asked in step 1; asking again only delays the open.
        assertFalse(VisibleOpenFollowUp.triesWokenSession(
                browserRefused = true, appWasCold = false, opensInTheSameApp = true))
        // One that connected was asked through its browser and did nothing with it.
        assertFalse(VisibleOpenFollowUp.triesWokenSession(
                browserRefused = false, appWasCold = true, opensInTheSameApp = true))
        // The follow-up watches the app the link opens in, which is not the one that was woken.
        assertFalse(VisibleOpenFollowUp.triesWokenSession(
                browserRefused = true, appWasCold = true, opensInTheSameApp = false))
    }

    @Test
    fun aWokenSessionCountsOnlyWhenItPlaysSomethingOtherThanWhatItCameUpHolding() {
        // How the woken session is followed: measured against the track it came up holding, so
        // that one starting by itself is not the request having worked. Read from YouTube Music
        // 8.30.54 cold: up paused on its last track, playing the requested one after the third
        // request, about 4.4 s in.
        val woken = { VisibleOpenFollowUp(wasPlayingBefore = true, identityBefore = old, canRequest = true) }
        val played = follow(woken(), untilMs = VisibleOpenFollowUp.WOKEN_SESSION_FOLLOW_MS) { t ->
            if (t < 4_400) Reading(STATE_PAUSED, old) else Reading(STATE_PLAYING, requested)
        }
        assertEquals(Step.PLAYED, played.last().second)
        assertEquals(VisibleOpenFollowUp.MAX_REQUESTS, played.count(Step.REQUEST))

        val restoredItself = follow(woken(), untilMs = VisibleOpenFollowUp.WOKEN_SESSION_FOLLOW_MS) { t ->
            if (t < 1_000) Reading(STATE_PAUSED, old) else Reading(STATE_PLAYING, old)
        }
        assertEquals("its own last track playing is not the request working",
                0, restoredItself.count(Step.PLAYED))
    }

    @Test
    fun theWokenSessionIsGivenAllItsRequestsAndStillLeavesTheWatchTimeToOpen() {
        val lastRequestAt = VisibleOpenFollowUp.STEADY_BEFORE_REQUEST_MS +
                (VisibleOpenFollowUp.MAX_REQUESTS - 1) * VisibleOpenFollowUp.REQUEST_INTERVAL_MS
        assertTrue("every request fits in the woken session's window",
                lastRequestAt < VisibleOpenFollowUp.WOKEN_SESSION_FOLLOW_MS)
        // The watch gives up on a verdict after 30 s and the ladder already spends up to about 22
        // before step 3; this wait is added only after a refused bind, which costs no time, but it
        // must never be what makes the verdict late.
        assertTrue(VisibleOpenFollowUp.WOKEN_SESSION_APPEAR_MS +
                VisibleOpenFollowUp.WOKEN_SESSION_FOLLOW_MS <= 10_000)
    }
}
