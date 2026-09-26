package com.svartifoss.snfell.music

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueThumbnailCacheTest {

    private var now = 0L
    private fun cache(maxBytes: Int = 10_000, missTtlMs: Long = 1_000L) =
            QueueThumbnailCache(maxBytes, missTtlMs) { now }

    @Test
    fun `an unknown key asks to be resolved`() {
        assertSame(QueueThumbnailCache.Lookup.Unknown, cache().lookup("row"))
    }

    @Test
    fun `a stored thumbnail comes back as the same bytes`() {
        val cache = cache()
        cache.putHit("row", byteArrayOf(1, 2, 3))

        val lookup = cache.lookup("row")
        assertTrue(lookup is QueueThumbnailCache.Lookup.Hit)
        assertArrayEquals(byteArrayOf(1, 2, 3), (lookup as QueueThumbnailCache.Lookup.Hit).bytes)
    }

    /** A queue of uncoverable rows must not retry every one of them on every track change. */
    @Test
    fun `a miss is remembered until its ttl runs out`() {
        val cache = cache(missTtlMs = 1_000L)
        cache.putMiss("row")

        now = 999L
        assertSame(QueueThumbnailCache.Lookup.KnownMiss, cache.lookup("row"))

        // Past the TTL the row is tried again, so a switch turned on meanwhile takes effect.
        now = 1_000L
        assertSame(QueueThumbnailCache.Lookup.Unknown, cache.lookup("row"))
    }

    @Test
    fun `a thumbnail found later replaces a remembered miss`() {
        val cache = cache()
        cache.putMiss("row")
        cache.putHit("row", byteArrayOf(9))

        assertTrue(cache.lookup("row") is QueueThumbnailCache.Lookup.Hit)
    }

    @Test
    fun `the least recently used thumbnail is evicted first`() {
        val cache = cache(maxBytes = 3 * (1_000 + 64))
        cache.putHit("a", ByteArray(1_000))
        cache.putHit("b", ByteArray(1_000))
        cache.putHit("c", ByteArray(1_000))

        // Reading "a" makes "b" the eldest.
        cache.lookup("a")
        cache.putHit("d", ByteArray(1_000))

        assertSame(QueueThumbnailCache.Lookup.Unknown, cache.lookup("b"))
        assertTrue(cache.lookup("a") is QueueThumbnailCache.Lookup.Hit)
        assertTrue(cache.lookup("c") is QueueThumbnailCache.Lookup.Hit)
        assertTrue(cache.lookup("d") is QueueThumbnailCache.Lookup.Hit)
    }

    @Test
    fun `the byte budget is never exceeded`() {
        val cache = cache(maxBytes = 5_000)
        repeat(50) { cache.putHit("row$it", ByteArray(700)) }

        assertTrue("used ${cache.sizeBytes()} of 5000", cache.sizeBytes() <= 5_000)
    }

    @Test
    fun `replacing an entry does not count its old bytes twice`() {
        val cache = cache()
        cache.putHit("row", ByteArray(1_000))
        cache.putHit("row", ByteArray(500))

        assertEquals(500 + 64, cache.sizeBytes())
    }

    @Test
    fun `a thumbnail larger than the whole budget is not stored`() {
        val cache = cache(maxBytes = 1_000)
        cache.putHit("small", ByteArray(100))
        cache.putHit("huge", ByteArray(5_000))

        assertSame(QueueThumbnailCache.Lookup.Unknown, cache.lookup("huge"))
        assertTrue("storing an oversized entry must not evict the others",
                cache.lookup("small") is QueueThumbnailCache.Lookup.Hit)
    }
}
