package com.svartifoss.snfell.music

import org.junit.Assert.assertEquals
import org.junit.Test

class ActiveQueueRowTest {
    private val ids = listOf(10L, 11L, 12L)
    private val titles = listOf("One", "Two", "Three")

    @Test
    fun `the queue id wins when the player publishes one`() {
        assertEquals(2, ActiveQueueRow.index(ids, titles, activeQueueId = 12L, playingTitle = "One"))
    }

    @Test
    fun `the title answers for a player that publishes no queue id`() {
        assertEquals(1, ActiveQueueRow.index(ids, titles, ActiveQueueRow.UNKNOWN_ID, " two "))
        assertEquals(1, ActiveQueueRow.index(ids, titles, null, "Two"))
    }

    @Test
    fun `an id that is not in the queue falls back to the title`() {
        assertEquals(0, ActiveQueueRow.index(ids, titles, activeQueueId = 99L, playingTitle = "One"))
    }

    @Test
    fun `nothing to go on is no row`() {
        assertEquals(-1, ActiveQueueRow.index(ids, titles, null, null))
        assertEquals(-1, ActiveQueueRow.index(ids, titles, null, "  "))
        assertEquals(-1, ActiveQueueRow.index(ids, titles, null, "Four"))
    }
}
