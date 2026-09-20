package com.svartifoss.snfell.watch.view

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictedTrackAdvanceTest {

    private val ids = listOf("10|a", "11|b", "12|c")
    private val titles = listOf("First", "Second", "Third")

    // ---- canPredict -------------------------------------------------------

    @Test
    fun `a playing track that reached its end may be predicted past`() {
        assertTrue(PredictedTrackAdvance.canPredict(
                playing = true, positionMs = 180_000L, durationMs = 180_000L,
                shuffleEnabled = false, repeatMode = 0))
    }

    @Test
    fun `the tolerance covers a tick that landed just short of the end`() {
        assertTrue(PredictedTrackAdvance.canPredict(
                playing = true, positionMs = 180_000L - PredictedTrackAdvance.END_TOLERANCE_MS,
                durationMs = 180_000L, shuffleEnabled = false, repeatMode = 0))
    }

    @Test
    fun `a track in the middle is never predicted past`() {
        assertFalse(PredictedTrackAdvance.canPredict(
                playing = true, positionMs = 90_000L, durationMs = 180_000L,
                shuffleEnabled = false, repeatMode = 0))
    }

    @Test
    fun `a paused track is never predicted past`() {
        assertFalse(PredictedTrackAdvance.canPredict(
                playing = false, positionMs = 180_000L, durationMs = 180_000L,
                shuffleEnabled = false, repeatMode = 0))
    }

    /** Repeat-one replays this track, so "the entry after it" is wrong by construction. */
    @Test
    fun `repeat one refuses to advance`() {
        assertFalse(PredictedTrackAdvance.canPredict(
                playing = true, positionMs = 180_000L, durationMs = 180_000L,
                shuffleEnabled = false, repeatMode = 2))
    }

    /** Repeat-all still plays the queue in order; only repeat-one repeats a single track. */
    @Test
    fun `repeat all still advances`() {
        assertTrue(PredictedTrackAdvance.canPredict(
                playing = true, positionMs = 180_000L, durationMs = 180_000L,
                shuffleEnabled = false, repeatMode = 1))
    }

    /**
     * The queue the phone published is not necessarily the order playback will follow when shuffle
     * is on, and there is no way to tell the players that publish the shuffled order from the ones
     * that do not. Consistently wrong is worse than merely late.
     */
    @Test
    fun `shuffle refuses to advance`() {
        assertFalse(PredictedTrackAdvance.canPredict(
                playing = true, positionMs = 180_000L, durationMs = 180_000L,
                shuffleEnabled = true, repeatMode = 0))
    }

    /**
     * A duration of zero is what a player publishes while its metadata is still settling. Without
     * this floor, `position >= duration` is true the instant a track starts.
     */
    @Test
    fun `an unpublished duration never satisfies the end test`() {
        assertFalse(PredictedTrackAdvance.canPredict(
                playing = true, positionMs = 0L, durationMs = 0L,
                shuffleEnabled = false, repeatMode = 0))
    }

    @Test
    fun `a placeholder duration below the floor is refused`() {
        assertFalse(PredictedTrackAdvance.canPredict(
                playing = true, positionMs = 1_000L, durationMs = 1_000L,
                shuffleEnabled = false, repeatMode = 0))
    }

    // ---- activeIndex ------------------------------------------------------

    @Test
    fun `the title identifies the playing row`() {
        assertEquals(1, PredictedTrackAdvance.activeIndex(ids, titles, null, "Second"))
    }

    @Test
    fun `the title match ignores case`() {
        assertEquals(2, PredictedTrackAdvance.activeIndex(ids, titles, null, "THIRD"))
    }

    /**
     * The reason the title is consulted first: a controller advances its metadata before it
     * advances activeQueueItemId, so on the pass that matters the id still names the track that
     * just ended.
     */
    @Test
    fun `the title wins over a stale active entry id`() {
        assertEquals(1, PredictedTrackAdvance.activeIndex(ids, titles, "10|a", "Second"))
    }

    @Test
    fun `the active entry id is used when the title matches nothing`() {
        assertEquals(2, PredictedTrackAdvance.activeIndex(ids, titles, "12|c", "Some Other Song"))
    }

    @Test
    fun `neither signal available is reported as unknown`() {
        assertEquals(-1, PredictedTrackAdvance.activeIndex(ids, titles, null, null))
    }

    @Test
    fun `an active entry id that is not in the page is reported as unknown`() {
        assertEquals(-1, PredictedTrackAdvance.activeIndex(ids, titles, "99|z", ""))
    }

    // ---- nextIndex --------------------------------------------------------

    @Test
    fun `the next row is the one after the playing row`() {
        assertEquals(2, PredictedTrackAdvance.nextIndex(ids, titles, null, "Second"))
    }

    /**
     * The queue the watch holds is a page, not the whole playlist, so the last loaded row is not
     * known to be followed by the first one - repeat-all or not.
     */
    @Test
    fun `the last loaded row has no known successor`() {
        assertEquals(-1, PredictedTrackAdvance.nextIndex(ids, titles, null, "Third"))
    }

    @Test
    fun `an unidentifiable playing row has no known successor`() {
        assertEquals(-1, PredictedTrackAdvance.nextIndex(ids, titles, null, "Not In The Queue"))
    }

    @Test
    fun `an empty queue has no known successor`() {
        assertEquals(-1, PredictedTrackAdvance.nextIndex(
                emptyList(), emptyList(), null, "First"))
    }

    // ---- manual skips -----------------------------------------------------

    @Test
    fun `a press may be predicted under repeat-one, unlike a boundary`() {
        assertTrue(PredictedTrackAdvance.canPredictManualSkip(shuffleEnabled = false))
    }

    @Test
    fun `shuffle still refuses, because the queue is not the play order`() {
        assertFalse(PredictedTrackAdvance.canPredictManualSkip(shuffleEnabled = true))
    }

    @Test
    fun `previous early in a track leaves it`() {
        assertFalse(PredictedTrackAdvance.previousRestartsTrack(0L))
        assertFalse(PredictedTrackAdvance.previousRestartsTrack(
                PredictedTrackAdvance.PREVIOUS_RESTARTS_AFTER_MS - 1))
    }

    @Test
    fun `previous later in a track restarts it instead`() {
        assertTrue(PredictedTrackAdvance.previousRestartsTrack(
                PredictedTrackAdvance.PREVIOUS_RESTARTS_AFTER_MS))
        assertTrue(PredictedTrackAdvance.previousRestartsTrack(90_000L))
    }

    // ---- previousIndex ----------------------------------------------------

    @Test
    fun `the row before the playing one is the previous track`() {
        assertEquals(0, PredictedTrackAdvance.previousIndex(ids, titles, null, "Second"))
        assertEquals(1, PredictedTrackAdvance.previousIndex(ids, titles, null, "Third"))
    }

    @Test
    fun `the first row has no predictable predecessor`() {
        assertEquals(-1, PredictedTrackAdvance.previousIndex(ids, titles, null, "First"))
    }

    @Test
    fun `an unidentifiable current track has no predictable predecessor`() {
        assertEquals(-1, PredictedTrackAdvance.previousIndex(ids, titles, null, "Not In The Queue"))
        assertEquals(-1, PredictedTrackAdvance.previousIndex(ids, titles, null, null))
    }

    @Test
    fun `previous falls back to the active entry id like next does`() {
        assertEquals(1, PredictedTrackAdvance.previousIndex(ids, titles, "12|c", "Some Other Song"))
    }

    // ---- isSameTrack ------------------------------------------------------

    @Test
    fun `a confirmation with the predicted title is a match`() {
        assertTrue(PredictedTrackAdvance.isSameTrack("Second", "Second"))
    }

    @Test
    fun `surrounding whitespace and case do not break the match`() {
        assertTrue(PredictedTrackAdvance.isSameTrack("Second", "  second "))
    }

    @Test
    fun `a different title is a rejected prediction`() {
        assertFalse(PredictedTrackAdvance.isSameTrack("Second", "Third"))
    }

    /** An empty title is the absence of an answer, not an answer that happens to agree. */
    @Test
    fun `a blank confirmation is never a match`() {
        assertFalse(PredictedTrackAdvance.isSameTrack("Second", ""))
        assertFalse(PredictedTrackAdvance.isSameTrack("Second", null))
        assertFalse(PredictedTrackAdvance.isSameTrack("", ""))
    }
}
