package com.svartifoss.snfell.watch.view

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackChangeHoldTest {

    private fun decide(
            holdActive: Boolean = true,
            title: String? = "Second",
            playing: Boolean = false,
            positionMs: Long = 0L,
            leftBehind: Collection<String> = listOf("First"),
    ) = TrackChangeHold.decide(holdActive, title, playing, positionMs, leftBehind)

    @Test
    fun `nothing is held outside the window`() {
        assertEquals(TrackChangeHold.Decision.APPLY, decide(holdActive = false, title = "First"))
        assertEquals(TrackChangeHold.Decision.APPLY, decide(holdActive = false, title = null))
    }

    @Test
    fun `a playing state always ends the window`() {
        assertEquals(TrackChangeHold.Decision.APPLY, decide(playing = true, title = "First"))
        assertEquals(TrackChangeHold.Decision.APPLY, decide(playing = true, title = "Second"))
    }

    @Test
    fun `the paused echo of the track just left is held`() {
        assertEquals(TrackChangeHold.Decision.DEFER, decide(title = "First"))
    }

    @Test
    fun `every track passed through in a burst is recognised, not just the last`() {
        val burst = listOf("First", "Second", "Third")
        assertEquals(TrackChangeHold.Decision.DEFER, decide(title = "First", leftBehind = burst))
        assertEquals(TrackChangeHold.Decision.DEFER, decide(title = "Second", leftBehind = burst))
        assertEquals(TrackChangeHold.Decision.DEFER, decide(title = "Third", leftBehind = burst))
    }

    @Test
    fun `the echo is matched the way the prediction is`() {
        assertEquals(TrackChangeHold.Decision.DEFER,
                decide(title = "  first ", leftBehind = listOf("First")))
    }

    @Test
    fun `an empty state is held rather than drawn as idle`() {
        assertEquals(TrackChangeHold.Decision.DEFER, decide(title = null))
        assertEquals(TrackChangeHold.Decision.DEFER, decide(title = ""))
        assertEquals(TrackChangeHold.Decision.DEFER, decide(title = "   "))
    }

    @Test
    fun `a new track that has not resumed yet is drawn as playing`() {
        assertEquals(TrackChangeHold.Decision.ASSUME_PLAYING, decide(title = "Fourth"))
    }

    @Test
    fun `a blank title left behind never matches everything`() {
        assertEquals(TrackChangeHold.Decision.ASSUME_PLAYING,
                decide(title = "Second", leftBehind = listOf("")))
    }

    @Test
    fun `a pause well into an unseen track is a real pause, not a transition`() {
        assertEquals(TrackChangeHold.Decision.APPLY,
                decide(title = "Fourth",
                        positionMs = TrackChangeHold.TRANSITION_POSITION_MS + 1))
    }

    @Test
    fun `a pause at the start of an unseen track is still the transition`() {
        assertEquals(TrackChangeHold.Decision.ASSUME_PLAYING,
                decide(title = "Fourth", positionMs = TrackChangeHold.TRANSITION_POSITION_MS))
    }

    /** The track ran to its end before the skip landed, so a late sample from it says nothing
     *  about the track now on screen however far in it reads. */
    @Test
    fun `a left-behind track is held however far into it the sample reads`() {
        assertEquals(TrackChangeHold.Decision.DEFER,
                decide(title = "First", positionMs = 200_000L))
    }
}
