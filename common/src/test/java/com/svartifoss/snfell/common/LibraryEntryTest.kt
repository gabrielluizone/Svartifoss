package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryEntryTest {
    @Test
    fun `browsable and playable round-trip their media id`() {
        assertEquals("album/42", LibraryEntry.mediaId(LibraryEntry.browsable("album/42")))
        assertEquals("track/7", LibraryEntry.mediaId(LibraryEntry.playable("track/7")))
    }

    @Test
    fun `only browsable rows report browsable`() {
        assertTrue(LibraryEntry.isBrowsable(LibraryEntry.browsable("x")))
        assertTrue(LibraryEntry.isBrowsable(LibraryEntry.UP))
        assertFalse(LibraryEntry.isBrowsable(LibraryEntry.playable("x")))
    }

    /**
     * Real media ids are arbitrary app-defined strings and frequently contain separators of their
     * own. Splitting on every `|` would truncate them and browse to a node that does not exist.
     */
    @Test
    fun `media ids containing the separator survive intact`() {
        val messy = "spotify:playlist|weird|id"
        assertEquals(messy, LibraryEntry.mediaId(LibraryEntry.browsable(messy)))
        assertEquals(messy, LibraryEntry.mediaId(LibraryEntry.playable(messy)))
    }

    /** Anything not produced here must not be handed to the browser as a node id. */
    @Test
    fun `unrecognised ids decode to no media id`() {
        assertNull(LibraryEntry.mediaId(LibraryEntry.UP))
        assertNull(LibraryEntry.mediaId(CustomLists.SPECIAL_ITEM_ERROR))
        assertNull(LibraryEntry.mediaId("raw-media-id"))
    }

    @Test
    fun `the up row is browsable but carries nothing to browse to`() {
        assertTrue(LibraryEntry.isBrowsable(LibraryEntry.UP))
        assertNull(LibraryEntry.mediaId(LibraryEntry.UP))
    }
}
