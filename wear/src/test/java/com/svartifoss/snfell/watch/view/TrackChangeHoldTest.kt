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
    fun `a playing state on a track not passed through ends the window`() {
        assertEquals(TrackChangeHold.Decision.APPLY, decide(playing = true, title = "Second"))
        assertEquals(TrackChangeHold.Decision.APPLY, decide(playing = true, title = "Fourth"))
    }

    /**
     * The player is working through a burst one press at a time and really does play each track it
     * passes on the way. Drawn as they land, those states walk the screen backwards through tracks
     * the user has already gone past while the one they asked for is on screen and waiting.
     */
    @Test
    fun `a track the user pressed past is held even while it plays`() {
        assertEquals(TrackChangeHold.Decision.DEFER, decide(playing = true, title = "First"))
    }

    @Test
    fun `the whole of a burst is held while the player catches up`() {
        val burst = listOf("First", "Second", "Third")
        assertEquals(TrackChangeHold.Decision.DEFER,
                decide(playing = true, title = "First", leftBehind = burst))
        assertEquals(TrackChangeHold.Decision.DEFER,
                decide(playing = true, title = "Second", leftBehind = burst))
        assertEquals(TrackChangeHold.Decision.DEFER,
                decide(playing = true, title = "Third", leftBehind = burst))
        // The one the presses were aimed at is not in the memo, so it lands at once.
        assertEquals(TrackChangeHold.Decision.APPLY,
                decide(playing = true, title = "Fourth", leftBehind = burst))
    }

    @Test
    fun `a blank left-behind entry never swallows a playing state`() {
        assertEquals(TrackChangeHold.Decision.APPLY,
                decide(playing = true, title = "Second", leftBehind = listOf("")))
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

    @Test
    fun `a track skipped back onto stops counting as left behind`() {
        val burst = mutableListOf("First", "Second", "Third")
        TrackChangeHold.forgetArrivedTrack(burst, "Second")
        assertEquals(listOf("First", "Third"), burst)
    }

    @Test
    fun `forgetting matches the way the echo is recognised`() {
        val burst = mutableListOf("First", "  second  ")
        TrackChangeHold.forgetArrivedTrack(burst, "SECOND")
        assertEquals(listOf("First"), burst)
    }

    @Test
    fun `no arrival title forgets nothing`() {
        val burst = mutableListOf("First", "Second")
        TrackChangeHold.forgetArrivedTrack(burst, null)
        TrackChangeHold.forgetArrivedTrack(burst, "   ")
        assertEquals(listOf("First", "Second"), burst)
    }

    /**
     * The user skipped forward and back, so the track they are looking at is one this window
     * also left behind. Once it is forgotten, the phone's real pause on it is a pause and not an
     * echo - which is what stopped it being drawn seconds late, behind the rest of the burst.
     */
    @Test
    fun `the pause on a track skipped back onto is drawn at once`() {
        val burst = mutableListOf("First", "Second")
        assertEquals(TrackChangeHold.Decision.DEFER,
                decide(title = "First", positionMs = 47_000L, leftBehind = burst))

        TrackChangeHold.forgetArrivedTrack(burst, "First")

        assertEquals(TrackChangeHold.Decision.APPLY,
                decide(title = "First", positionMs = 47_000L, leftBehind = burst))
        assertEquals(TrackChangeHold.Decision.APPLY,
                decide(playing = true, title = "First", leftBehind = burst))
    }

    /** Forgetting it does not make the swap itself visible: at the start of the track it is still
     *  the player reloading, which is the case ASSUME_PLAYING covers. */
    @Test
    fun `the start of a track skipped back onto is still the transition`() {
        val burst = mutableListOf("First", "Second")
        TrackChangeHold.forgetArrivedTrack(burst, "First")

        assertEquals(TrackChangeHold.Decision.ASSUME_PLAYING,
                decide(title = "First", positionMs = 0L, leftBehind = burst))
    }
}
