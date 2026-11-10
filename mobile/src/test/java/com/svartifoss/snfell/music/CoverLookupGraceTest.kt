package com.svartifoss.snfell.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverLookupGraceTest {

    @Test
    fun `a track without a cover is looked up only once the wait is over`() {
        val grace = CoverLookupGrace(graceMs = 2_000L)

        assertFalse(grace.withoutCover("free me", nowMs = 10_000L))
        assertEquals(12_000L, grace.dueAtMs())
        assertFalse(grace.withoutCover("free me", nowMs = 11_999L))
        assertTrue(grace.withoutCover("free me", nowMs = 12_000L))
    }

    /** YouTube Music: the track first, its own cover a second later. */
    @Test
    fun `a cover from the player in time means no lookup`() {
        val grace = CoverLookupGrace(graceMs = 2_000L)
        grace.withoutCover("free me", nowMs = 10_000L)

        grace.playerCover()

        assertNull(grace.dueAtMs())
        // Losing it for a moment later on starts a fresh wait, not one that began at 10 s.
        assertFalse(grace.withoutCover("free me", nowMs = 40_000L))
        assertTrue(grace.withoutCover("free me", nowMs = 42_000L))
    }

    @Test
    fun `the next track waits on its own`() {
        val grace = CoverLookupGrace(graceMs = 2_000L)
        grace.withoutCover("first", nowMs = 0L)
        assertTrue(grace.withoutCover("first", nowMs = 5_000L))

        assertFalse(grace.withoutCover("second", nowMs = 5_500L))
        assertEquals(7_500L, grace.dueAtMs())
    }
}
