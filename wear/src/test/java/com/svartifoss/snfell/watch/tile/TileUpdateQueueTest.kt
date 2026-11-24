package com.svartifoss.snfell.watch.tile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TileUpdateQueueTest {

    @Test
    fun `the first request starts a connection and later ones ride on it`() {
        val queue = TileUpdateQueue<String>()
        assertTrue(queue.add("media"))
        assertFalse(queue.add("shortcuts"))
        assertFalse(queue.add("media"))
        assertEquals(listOf("media", "shortcuts"), queue.takeForSending())
    }

    @Test
    fun `a finished connection leaves the queue ready for the next`() {
        val queue = TileUpdateQueue<String>()
        queue.add("media")
        queue.takeForSending()
        queue.release()
        assertFalse(queue.inFlight)
        assertTrue(queue.add("media"))
    }

    @Test
    fun `a failed bind does not leave the queue stuck`() {
        // The library requester never cleared its flag after a failed bind and stopped requesting.
        val queue = TileUpdateQueue<String>()
        queue.add("media")
        queue.release()
        assertTrue(queue.add("shortcuts"))
        assertEquals(listOf("shortcuts"), queue.takeForSending())
    }

    @Test
    fun `nothing waits beyond the connection that ended`() {
        val queue = TileUpdateQueue<String>()
        queue.add("media")
        queue.add("recent")
        queue.release()
        assertTrue(queue.takeForSending().isEmpty())
    }
}
