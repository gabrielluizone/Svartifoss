package com.svartifoss.snfell.watch.view

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaimedTouchStreamTest {

    @Test
    fun `stream claimed by Compose is observed through its end`() {
        val stream = ClaimedTouchStream()

        assertTrue(stream.shouldObserve(ClaimedTouchStream.Phase.DOWN, handledByChild = true))
        // Once claimed, an intermediate false return must not punch a hole in the sequence: the
        // detector needs DOWN, movement and the terminal event from the same finger.
        assertTrue(stream.shouldObserve(ClaimedTouchStream.Phase.CONTINUE, handledByChild = false))
        assertTrue(stream.shouldObserve(ClaimedTouchStream.Phase.END, handledByChild = false))
        assertFalse(stream.shouldObserve(ClaimedTouchStream.Phase.CONTINUE, handledByChild = true))
    }

    @Test
    fun `stream rejected by Compose remains exclusive to fallback gesture layer`() {
        val stream = ClaimedTouchStream()

        assertFalse(stream.shouldObserve(ClaimedTouchStream.Phase.DOWN, handledByChild = false))
        assertFalse(stream.shouldObserve(ClaimedTouchStream.Phase.CONTINUE, handledByChild = false))
        assertFalse(stream.shouldObserve(ClaimedTouchStream.Phase.END, handledByChild = false))
    }

    @Test
    fun `new down replaces an unfinished stream decision`() {
        val stream = ClaimedTouchStream()

        assertTrue(stream.shouldObserve(ClaimedTouchStream.Phase.DOWN, handledByChild = true))
        assertFalse(stream.shouldObserve(ClaimedTouchStream.Phase.DOWN, handledByChild = false))
        assertFalse(stream.shouldObserve(ClaimedTouchStream.Phase.CONTINUE, handledByChild = false))
    }
}
