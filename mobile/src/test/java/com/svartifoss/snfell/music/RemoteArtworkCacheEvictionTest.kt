package com.svartifoss.snfell.music

import com.svartifoss.snfell.music.RemoteArtworkCache.Entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins [RemoteArtworkCache.evictions].
 *
 * The bug behind the cache existing at all: queue covers were written into the shortcut-thumbnail
 * store, which is user data with a per-store cap in the backup - a few hundred streaming queue
 * rows were enough to fail the *entire* export. A cache of its own only fixes that if it stays
 * bounded, and eviction has to be stable: a filesystem may report whole-second timestamps, and an
 * order that varies between passes would evict a different arbitrary set each time and keep
 * re-downloading covers it just dropped.
 */
class RemoteArtworkCacheEvictionTest {

    @Test
    fun nothingIsEvictedBelowTheCap() {
        val entries = (1..3).map { Entry("cover$it.png", it.toLong()) }
        assertTrue(RemoteArtworkCache.evictions(entries, 4).isEmpty())
        assertTrue(RemoteArtworkCache.evictions(entries, 3).isEmpty())
    }

    @Test
    fun theLeastRecentlyWrittenEntriesGoFirst() {
        val entries = listOf(
                Entry("newest.png", 300),
                Entry("oldest.png", 100),
                Entry("middle.png", 200))
        assertEquals(
                listOf("oldest.png", "middle.png"),
                RemoteArtworkCache.evictions(entries, 1).map { it.name })
    }

    @Test
    fun entriesSharingATimestampAreEvictedInAStableOrder() {
        val entries = listOf(
                Entry("c.png", 100),
                Entry("a.png", 100),
                Entry("b.png", 100))
        assertEquals(
                listOf("a.png", "b.png"),
                RemoteArtworkCache.evictions(entries, 1).map { it.name })
        assertEquals(
                RemoteArtworkCache.evictions(entries, 1),
                RemoteArtworkCache.evictions(entries.reversed(), 1))
    }

    @Test
    fun exactlyTheSurplusIsEvicted() {
        val entries = (1..500).map { Entry("cover$it.png", it.toLong()) }
        val evicted = RemoteArtworkCache.evictions(entries, RemoteArtworkCache.MAX_ENTRIES)
        assertEquals(500 - RemoteArtworkCache.MAX_ENTRIES, evicted.size)
        assertEquals(entries.size - evicted.size, RemoteArtworkCache.MAX_ENTRIES)
    }
}
