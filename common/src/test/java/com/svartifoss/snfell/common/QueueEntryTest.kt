package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueEntryTest {
    @Test
    fun `queue id and media id round-trip`() {
        val entryId = QueueEntry.encode(42L, "track/7")
        assertEquals(42L, QueueEntry.queueId(entryId))
        assertEquals("track/7", QueueEntry.mediaId(entryId))
    }

    /** Some sessions never set a media id on their queue items at all. */
    @Test
    fun `a missing media id decodes to null rather than an empty string`() {
        val entryId = QueueEntry.encode(3L, null)
        assertEquals(3L, QueueEntry.queueId(entryId))
        assertNull(QueueEntry.mediaId(entryId))
    }

    /**
     * Real media ids are arbitrary app-defined strings and frequently contain separators of their
     * own. Splitting on every `|` would truncate them into something the source app never handed
     * out, exactly the mistake docs/player-integration-notes.md warns against.
     */
    @Test
    fun `media ids containing the separator survive intact`() {
        val messy = "spotify:playlist|weird|id"
        val entryId = QueueEntry.encode(9L, messy)
        assertEquals(9L, QueueEntry.queueId(entryId))
        assertEquals(messy, QueueEntry.mediaId(entryId))
    }

    /** An id this object never produced must not be acted on as if it were a real queue position. */
    @Test
    fun `malformed ids decode to the unknown queue position`() {
        assertEquals(-1L, QueueEntry.queueId(CustomLists.SPECIAL_ITEM_ERROR))
        assertEquals(-1L, QueueEntry.queueId(""))
    }
}
