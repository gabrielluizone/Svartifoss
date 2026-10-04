package com.svartifoss.snfell.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins which links are a per-account collection (liked songs, Flow) and so get that collection's
 * cover, and which are not.
 *
 * Both directions matter. A collection link that is missed falls back to the service's generic
 * cover, which is merely plainer. A playlist that is mistaken for one is worse: it is drawn as
 * somebody's liked songs, and with the artwork switch on is given the service's liked-songs image.
 */
class StreamingCollectionTest {

    @Test
    fun `every spelling of youtube music liked music is recognised`() {
        listOf(
                "https://music.youtube.com/watch?list=LM",
                "https://music.youtube.com/playlist?list=LM",
                "music.youtube.com/playlist?list=LM",
                "https://music.youtube.com/watch?v=abc&list=LM",
                "https://www.youtube.com/playlist?list=LM&si=xyz"
        ).forEach { link ->
            assertEquals(link, StreamingCollection.YOUTUBE_MUSIC_LIKED,
                    StreamingCollection.forLink(link))
        }
    }

    @Test
    fun `an ordinary youtube playlist is not liked music`() {
        listOf(
                "https://music.youtube.com/playlist?list=PL123",
                "https://music.youtube.com/playlist?list=LMX",
                "https://music.youtube.com/playlist?list=lm",
                "https://music.youtube.com/watch?v=LM",
                // LL is YouTube's liked *videos*, not YouTube Music's liked songs.
                "https://www.youtube.com/playlist?list=LL"
        ).forEach { link -> assertNull(link, StreamingCollection.forLink(link)) }
    }

    @Test
    fun `spotify liked songs is recognised in app and web form`() {
        listOf(
                "spotify:collection:tracks",
                "spotify:user:someone:collection",
                "https://open.spotify.com/collection/tracks",
                "https://open.spotify.com/intl-pt/collection/tracks/"
        ).forEach { link ->
            assertEquals(link, StreamingCollection.SPOTIFY_LIKED, StreamingCollection.forLink(link))
        }
        listOf(
                "spotify:playlist:abc",
                "spotify:collection",
                "https://open.spotify.com/playlist/abc",
                "https://open.spotify.com/collection/albums"
        ).forEach { link -> assertNull(link, StreamingCollection.forLink(link)) }
    }

    @Test
    fun `soundcloud likes and deezer flow are recognised`() {
        assertEquals(StreamingCollection.SOUNDCLOUD_LIKES,
                StreamingCollection.forLink("https://soundcloud.com/you/likes"))
        assertEquals(StreamingCollection.SOUNDCLOUD_LIKES,
                StreamingCollection.forLink("https://m.soundcloud.com/someone/likes/"))
        assertNull(StreamingCollection.forLink("https://soundcloud.com/someone/sets/likes"))

        assertEquals(StreamingCollection.DEEZER_FLOW,
                StreamingCollection.forLink("https://www.deezer.com/flow"))
        assertEquals(StreamingCollection.DEEZER_FLOW,
                StreamingCollection.forLink("https://www.deezer.com/pt/flow"))
        assertNull(StreamingCollection.forLink("https://www.deezer.com/playlist/123"))
    }

    @Test
    fun `nonsense never throws`() {
        listOf("", "   ", "not a link", "https://", "spotify:", "::::", "https://exa mple.com/")
                .forEach { assertNull(it, StreamingCollection.forLink(it)) }
    }

    @Test
    fun `only the services that publish one have official artwork`() {
        assertNotNull(StreamingCollection.YOUTUBE_MUSIC_LIKED.officialArtworkUrl)
        assertNotNull(StreamingCollection.SPOTIFY_LIKED.officialArtworkUrl)
        assertNull(StreamingCollection.SOUNDCLOUD_LIKES.officialArtworkUrl)
        assertNull(StreamingCollection.DEEZER_FLOW.officialArtworkUrl)
        StreamingCollection.entries.mapNotNull { it.officialArtworkUrl }
                .forEach { assertTrue(it, it.startsWith("https://")) }
    }

    @Test
    fun `each collection keeps its artwork under its own key`() {
        val keys = StreamingCollection.entries.map { it.storeKey }
        assertEquals(keys.size, keys.toSet().size)
        assertEquals(keys.toSet(), StreamingCollection.storeKeys)
        // A store key must never be mistaken for a saved link - the store keys files by link too.
        keys.forEach { assertNull(it, StreamingCollection.forLink(it)) }
    }

    @Test
    fun `every known service has its own cover colours`() {
        StreamingService.entries.forEach { service ->
            val gradient = ServiceCoverPalette.gradientFor(service)
            if (service == StreamingService.GENERIC) {
                assertNull(gradient)
            } else {
                assertNotNull(service.name, gradient)
            }
        }
    }
}
