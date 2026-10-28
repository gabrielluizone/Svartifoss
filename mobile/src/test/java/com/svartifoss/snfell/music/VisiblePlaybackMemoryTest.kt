package com.svartifoss.snfell.music

import com.svartifoss.snfell.music.VisiblePlayTechnique.PLAY_FROM_SEARCH_URI
import com.svartifoss.snfell.music.VisiblePlayTechnique.VIEW_AUTOPLAY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins how the phone settles on the hand-off technique that plays for it.
 *
 * Two failure modes this guards: repeating a technique that is seen to do nothing (every tap would
 * just open the app, which is what this all exists to fix), and abandoning one that works over a
 * single miss (a phone with no signal for a moment would then lose the working technique for a
 * week).
 */
class VisiblePlaybackMemoryTest {

    private val candidates = listOf(PLAY_FROM_SEARCH_URI, VIEW_AUTOPLAY)
    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day

    @Test
    fun `nothing known means the best candidate`() {
        assertEquals(PLAY_FROM_SEARCH_URI, VisiblePlaybackMemory.choose(candidates, emptyMap(), now))
    }

    @Test
    fun `a technique that never worked is benched after one failure`() {
        val records = VisiblePlaybackMemory.recorded(emptyMap(), PLAY_FROM_SEARCH_URI, played = false, nowMs = now)
        assertEquals(VIEW_AUTOPLAY, VisiblePlaybackMemory.choose(candidates, records, now + 1))
    }

    @Test
    fun `a technique that worked is kept, even when a better-ranked one is untried`() {
        var records = VisiblePlaybackMemory.recorded(emptyMap(), PLAY_FROM_SEARCH_URI, false, now)
        records = VisiblePlaybackMemory.recorded(records, VIEW_AUTOPLAY, true, now + 1)
        // The bench on the first runs out a week later; the proven one still wins.
        assertEquals(VIEW_AUTOPLAY, VisiblePlaybackMemory.choose(candidates, records, now + 30 * day))
    }

    @Test
    fun `a proven technique survives one miss and is benched after two`() {
        var records = VisiblePlaybackMemory.recorded(emptyMap(), PLAY_FROM_SEARCH_URI, true, now)
        records = VisiblePlaybackMemory.recorded(records, PLAY_FROM_SEARCH_URI, false, now + 1)
        assertEquals(PLAY_FROM_SEARCH_URI, VisiblePlaybackMemory.choose(candidates, records, now + 2))

        records = VisiblePlaybackMemory.recorded(records, PLAY_FROM_SEARCH_URI, false, now + 3)
        assertEquals(VIEW_AUTOPLAY, VisiblePlaybackMemory.choose(candidates, records, now + 4))
    }

    @Test
    fun `a success clears the strikes`() {
        var records = VisiblePlaybackMemory.recorded(emptyMap(), PLAY_FROM_SEARCH_URI, true, now)
        records = VisiblePlaybackMemory.recorded(records, PLAY_FROM_SEARCH_URI, false, now + 1)
        records = VisiblePlaybackMemory.recorded(records, PLAY_FROM_SEARCH_URI, true, now + 2)
        assertTrue(records.getValue(PLAY_FROM_SEARCH_URI).provenGood)
        assertEquals(0, records.getValue(PLAY_FROM_SEARCH_URI).failures)
    }

    @Test
    fun `when everything failed lately the plain open is used`() {
        var records = VisiblePlaybackMemory.recorded(emptyMap(), PLAY_FROM_SEARCH_URI, false, now)
        records = VisiblePlaybackMemory.recorded(records, VIEW_AUTOPLAY, false, now)
        assertNull(VisiblePlaybackMemory.choose(candidates, records, now + 1))
    }

    @Test
    fun `a benched technique is tried again once the app may have been updated`() {
        val records = VisiblePlaybackMemory.recorded(emptyMap(), PLAY_FROM_SEARCH_URI, false, now)
        assertEquals(PLAY_FROM_SEARCH_URI, VisiblePlaybackMemory.choose(
                candidates, records, now + VisiblePlaybackMemory.RETRY_AFTER_MS))
    }

    @Test
    fun `a clock set back does not bench anything for years`() {
        val records = VisiblePlaybackMemory.recorded(emptyMap(), PLAY_FROM_SEARCH_URI, false, now)
        assertEquals(PLAY_FROM_SEARCH_URI, VisiblePlaybackMemory.choose(candidates, records, now - day))
    }

    @Test
    fun `records survive being written and read back`() {
        var records = VisiblePlaybackMemory.recorded(emptyMap(), PLAY_FROM_SEARCH_URI, false, now)
        records = VisiblePlaybackMemory.recorded(records, VIEW_AUTOPLAY, true, now + 5)
        assertEquals(records, VisiblePlaybackMemory.decode(VisiblePlaybackMemory.encode(records)))
    }

    @Test
    fun `anything unreadable is treated as never tried`() {
        assertTrue(VisiblePlaybackMemory.decode(null).isEmpty())
        assertTrue(VisiblePlaybackMemory.decode("").isEmpty())
        assertTrue(VisiblePlaybackMemory.decode("garbage|uri_search:x:1:2|unknown:1:2:3").isEmpty())
        assertEquals(setOf(VIEW_AUTOPLAY),
                VisiblePlaybackMemory.decode("view_autoplay:5:0:0|nonsense").keys)
    }

    @Test
    fun `storage keys never change`() {
        // Persisted on every phone that has used a technique: renaming one forgets what it learned.
        assertEquals(listOf("uri_search", "view_autoplay", "app_action", "name_search"),
                VisiblePlayTechnique.entries.map { it.storageKey })
    }
}
