package com.svartifoss.snfell.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import com.svartifoss.snfell.common.AlbumArtSource
import org.junit.Test

/**
 * The decisions behind the online artwork lookup that are worth pinning without a device.
 *
 * All three are the same shape as the rest of this repo's pure tests: each one is a *fallback* or a
 * normalisation whose failure mode is silent. A bad primary-name guess fetches the wrong
 * performer's photograph; a cache key that does not normalise spends a network request on a picture
 * already on disk; a miss that never expires makes one dropped connection permanent.
 */
class OnlineArtworkPolicyTest {

    @Test
    fun `a plain credit is left alone`() {
        assertEquals("Daft Punk", OnlineArtworkFetcher.primaryArtistName("Daft Punk"))
        assertEquals("Sigur Rós", OnlineArtworkFetcher.primaryArtistName("Sigur Rós"))
        // A band whose actual name contains a word the separators look for must survive: the
        // separators are matched with their surrounding spaces for exactly this reason.
        assertEquals("Ftisland", OnlineArtworkFetcher.primaryArtistName("Ftisland"))
        assertEquals("Xavier", OnlineArtworkFetcher.primaryArtistName("Xavier"))
    }

    @Test
    fun `featured guests are dropped from the second query`() {
        assertEquals("Kanye West", OnlineArtworkFetcher.primaryArtistName("Kanye West feat. Jay-Z"))
        assertEquals("Kanye West", OnlineArtworkFetcher.primaryArtistName("Kanye West ft. Jay-Z"))
        assertEquals("Calvin Harris", OnlineArtworkFetcher.primaryArtistName("Calvin Harris featuring Rihanna"))
        assertEquals("Zedd", OnlineArtworkFetcher.primaryArtistName("Zedd with Maren Morris"))
    }

    @Test
    fun `the marker is matched case-insensitively`() {
        assertEquals("Drake", OnlineArtworkFetcher.primaryArtistName("Drake FEAT. Future"))
        assertEquals("Drake", OnlineArtworkFetcher.primaryArtistName("Drake Ft Future"))
    }

    @Test
    fun `co-headliners collapse to the leading name`() {
        assertEquals("Simon", OnlineArtworkFetcher.primaryArtistName("Simon & Garfunkel"))
        assertEquals("Above", OnlineArtworkFetcher.primaryArtistName("Above / Beyond"))
        assertEquals("Bowie", OnlineArtworkFetcher.primaryArtistName("Bowie, Queen"))
    }

    @Test
    fun `a trailing dash left by a split is trimmed`() {
        assertEquals("Röyksopp", OnlineArtworkFetcher.primaryArtistName("Röyksopp - feat. Robyn"))
    }

    @Test
    fun `an empty or blank credit yields nothing to query`() {
        assertEquals("", OnlineArtworkFetcher.primaryArtistName(""))
        assertEquals("", OnlineArtworkFetcher.primaryArtistName("   "))
    }

    @Test
    fun `only the sources that need a lookup produce a cache key`() {
        assertEquals(null,
                OnlineArtworkFetcher.cacheKeyFor(AlbumArtSource.LOCAL, "Daft Punk", "Aerodynamic"))
        assertEquals("artist|Daft Punk",
                OnlineArtworkFetcher.cacheKeyFor(AlbumArtSource.ARTIST, "Daft Punk", "Aerodynamic"))
        assertEquals("cover|Daft Punk|Aerodynamic",
                OnlineArtworkFetcher.cacheKeyFor(AlbumArtSource.ONLINE, "Daft Punk", "Aerodynamic"))
    }

    @Test
    fun `an artist picture is keyed by the performer alone so an album costs one lookup`() {
        assertEquals(
                OnlineArtworkFetcher.cacheKeyFor(AlbumArtSource.ARTIST, "Daft Punk", "Aerodynamic"),
                OnlineArtworkFetcher.cacheKeyFor(AlbumArtSource.ARTIST, "Daft Punk", "Da Funk"))
        // A cover belongs to the track, so those two must not collide.
        assertNotEquals(
                OnlineArtworkFetcher.cacheKeyFor(AlbumArtSource.ONLINE, "Daft Punk", "Aerodynamic"),
                OnlineArtworkFetcher.cacheKeyFor(AlbumArtSource.ONLINE, "Daft Punk", "Da Funk"))
    }

    @Test
    fun `a lookup with nothing to search for is refused before any request`() {
        assertEquals(null, OnlineArtworkFetcher.cacheKeyFor(AlbumArtSource.ARTIST, "  ", "Track"))
        // A cover needs both names; an artist alone matches their most popular record, which is
        // the wrong sleeve for every other one.
        assertEquals(null, OnlineArtworkFetcher.cacheKeyFor(AlbumArtSource.ONLINE, "Daft Punk", " "))
    }

    @Test
    fun `the cache key ignores case and surrounding whitespace`() {
        // The same performer reaches this from different players spelled differently, and a second
        // file would mean a second network request for a picture already on disk.
        val canonical = OnlineArtworkCache.key("artist|Daft Punk")
        assertEquals(canonical, OnlineArtworkCache.key("artist|daft punk"))
        assertEquals(canonical, OnlineArtworkCache.key("  ARTIST|DAFT PUNK  "))
        assertEquals(canonical, OnlineArtworkCache.key("artist|Daft   Punk"))
        assertNotEquals(canonical, OnlineArtworkCache.key("artist|Daft Punk II"))
    }

    @Test
    fun `a recorded absence stands for a week and then invites a retry`() {
        val written = 1_000_000L
        assertFalse(OnlineArtworkCache.missExpired(written, written))
        assertFalse(
                OnlineArtworkCache.missExpired(written, written + OnlineArtworkCache.MISS_TTL_MS - 1))
        assertTrue(
                OnlineArtworkCache.missExpired(written, written + OnlineArtworkCache.MISS_TTL_MS))
    }

    @Test
    fun `eviction drops the least recently written first`() {
        val entries = listOf(
                OnlineArtworkCache.Eviction("c", 300),
                OnlineArtworkCache.Eviction("a", 100),
                OnlineArtworkCache.Eviction("b", 200))
        assertEquals(
                listOf("a", "b"),
                OnlineArtworkCache.evictions(entries, 1).map { it.name })
    }

    @Test
    fun `eviction is stable when timestamps tie`() {
        // A filesystem may only report whole-second timestamps, and an eviction that picked
        // differently on each pass would keep re-downloading the same pictures.
        val entries = listOf(
                OnlineArtworkCache.Eviction("b", 100),
                OnlineArtworkCache.Eviction("a", 100),
                OnlineArtworkCache.Eviction("c", 100))
        assertEquals(
                listOf("a", "b"),
                OnlineArtworkCache.evictions(entries, 1).map { it.name })
    }

    @Test
    fun `nothing is evicted while the cache is within its bound`() {
        val entries = (1..OnlineArtworkCache.MAX_ENTRIES).map {
            OnlineArtworkCache.Eviction("f$it", it.toLong())
        }
        assertTrue(OnlineArtworkCache.evictions(entries, OnlineArtworkCache.MAX_ENTRIES).isEmpty())
    }
}
