package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveStreamTrackerTest {

    private val confirm = LiveStreamTracker.CONFIRM_MS

    /** The reported case: a YouTube live publishes 0 and keeps publishing it. */
    @Test
    fun `a zero duration that lasts is a live stream`() {
        val tracker = LiveStreamTracker()
        val first = tracker.observe("live", durationPublishedAsZero = true, nowMs = 10_000)
        assertFalse(first.live)
        assertEquals(confirm, first.recheckAfterMs)

        val later = tracker.observe("live", durationPublishedAsZero = true, nowMs = 10_000 + confirm)
        assertTrue(later.live)
        assertNull(later.recheckAfterMs)
    }

    /** Players publish a track's metadata with 0 and fill the length in a moment later. */
    @Test
    fun `a zero that turns into a real duration is never called live`() {
        val tracker = LiveStreamTracker()
        assertFalse(tracker.observe("song", true, 10_000).live)
        assertFalse(tracker.observe("song", false, 10_800).live)
        assertFalse(tracker.observe("song", false, 10_000 + confirm + 500).live)
    }

    @Test
    fun `the wait says how much of it is left`() {
        val tracker = LiveStreamTracker()
        tracker.observe("live", true, 10_000)
        assertEquals(confirm - 1_200, tracker.observe("live", true, 11_200).recheckAfterMs)
    }

    /** A real duration in between throws away what had been waited for. */
    @Test
    fun `the wait starts over after a real duration`() {
        val tracker = LiveStreamTracker()
        tracker.observe("live", true, 10_000)
        tracker.observe("live", false, 12_000)
        val again = tracker.observe("live", true, 12_500)
        assertFalse(again.live)
        assertEquals(confirm, again.recheckAfterMs)
    }

    /** The label belongs to a track: the next one has to earn it. */
    @Test
    fun `another track starts the wait over`() {
        val tracker = LiveStreamTracker()
        tracker.observe("first", true, 10_000)
        assertTrue(tracker.observe("first", true, 10_000 + confirm).live)

        val next = tracker.observe("second", true, 10_000 + confirm + 100)
        assertFalse(next.live)
        assertEquals(confirm, next.recheckAfterMs)
    }

    @Test
    fun `a stream that stays live stays live on every later reading`() {
        val tracker = LiveStreamTracker()
        tracker.observe("live", true, 10_000)
        for (offset in listOf(confirm, confirm + 1, confirm * 10)) {
            assertTrue(tracker.observe("live", true, 10_000 + offset).live)
        }
    }

    @Test
    fun `nothing published means nothing pending`() {
        val verdict = LiveStreamTracker().observe("song", false, 10_000)
        assertFalse(verdict.live)
        assertNull(verdict.recheckAfterMs)
    }
}
