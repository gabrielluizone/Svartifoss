package com.svartifoss.snfell.watch.communication

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Pins how few covers a neighbouring-cover announcement decodes. Each decode on the watch is a
 * full-screen bitmap (about 0.8 MB on a 466 px screen), so a regression here shows up as memory and
 * battery, never as a visible fault.
 */
class PrefetchedCoverReuseTest {

    private class Picture(val name: String)

    @Test
    fun aHeldPictureIsNeverDecodedAgain() = runTest {
        val previous = Picture("previous neighbour")
        val decoded = mutableListOf<String>()

        val result = PrefetchedCoverReuse.resolve(
                listOf("a", "b"),
                held = { id -> previous.takeIf { id == "a" } },
                decode = { id -> decoded += id; Picture(id) })

        assertSame(previous, result[0])
        assertEquals("b", result[1]?.name)
        assertEquals(listOf("b"), decoded)
    }

    @Test
    fun oneSleeveSharedByAWholeAlbumIsDecodedOnceAndShared() = runTest {
        val decoded = mutableListOf<String>()

        val result = PrefetchedCoverReuse.resolve(
                listOf("album", "album", "album", "album"),
                held = { null },
                decode = { id -> decoded += id; Picture(id) })

        assertEquals(listOf("album"), decoded)
        result.forEach { assertSame("every entry must share the one decoded bitmap", result[0], it) }
    }

    @Test
    fun anEntryWithoutAnAssetStaysEmptyAndCostsNothing() = runTest {
        val lookedUp = mutableListOf<String>()

        val result = PrefetchedCoverReuse.resolve(
                listOf(null, "a"),
                held = { id -> lookedUp += "held $id"; null },
                decode = { id -> lookedUp += "decode $id"; Picture(id) })

        assertNull(result[0])
        assertEquals("a", result[1]?.name)
        assertEquals(listOf("held a", "decode a"), lookedUp)
    }

    @Test
    fun anUnreadableAssetIsTriedOnceForTheWholeAnnouncement() = runTest {
        var attempts = 0

        val result = PrefetchedCoverReuse.resolve(
                listOf("broken", "broken"),
                held = { null },
                decode = { attempts++; null })

        assertEquals(1, attempts)
        assertEquals(listOf<Picture?>(null, null), result)
    }

    @Test
    fun theOrderOfTheAnnouncementIsKept() = runTest {
        val result = PrefetchedCoverReuse.resolve(
                listOf("c", "a", "b"),
                held = { null },
                decode = { id -> Picture(id) })

        assertEquals(listOf("c", "a", "b"), result.map { it?.name })
    }
}
