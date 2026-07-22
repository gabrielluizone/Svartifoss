package com.svartifoss.snfell.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingShortcutLinksTest {
    @Test
    fun detectsSupportedStreamingServices() {
        assertEquals(StreamingService.YOUTUBE_MUSIC,
            StreamingShortcutLinks.detect("https://music.youtube.com/playlist?list=PL1"))
        assertEquals(StreamingService.SPOTIFY,
            StreamingShortcutLinks.detect("https://open.spotify.com/playlist/abc"))
        assertEquals(StreamingService.DEEZER,
            StreamingShortcutLinks.detect("https://www.deezer.com/album/1"))
        assertEquals(StreamingService.TIDAL,
            StreamingShortcutLinks.detect("https://listen.tidal.com/album/1"))
        assertEquals(StreamingService.APPLE_MUSIC,
            StreamingShortcutLinks.detect("https://music.apple.com/us/album/example/1"))
        assertEquals(StreamingService.AMAZON_MUSIC,
            StreamingShortcutLinks.detect("https://music.amazon.com/albums/example"))
        assertEquals(StreamingService.SOUNDCLOUD,
            StreamingShortcutLinks.detect("https://on.soundcloud.com/example"))
    }

    @Test
    fun pastedHostGetsHttpsScheme() {
        assertEquals(
            "https://open.spotify.com/track/abc",
            StreamingShortcutLinks.canonicalize("open.spotify.com/track/abc")
        )
        assertEquals("spotify:track:abc", StreamingShortcutLinks.canonicalize("spotify:track:abc"))
    }

    @Test
    fun youtubePlaylistUsesWatchEndpointAndKeepsParameters() {
        assertEquals(
            "https://music.youtube.com/watch?list=PL1&shuffle=true",
            StreamingShortcutLinks.forBrowser(
                "https://music.youtube.com/playlist?list=PL1&shuffle=true"
            )
        )
    }

    @Test
    fun spotifySupportedEntitiesBecomeAppUris() {
        listOf("track", "album", "playlist", "artist", "show", "episode").forEach { type ->
            assertEquals(
                "spotify:$type:abc123",
                StreamingShortcutLinks.spotifyAppUri(
                    "https://open.spotify.com/$type/abc123?si=share"
                )
            )
        }
        assertEquals(
            "spotify:track:abc123",
            StreamingShortcutLinks.spotifyAppUri(
                "https://open.spotify.com/intl-pt/track/abc123"
            )
        )
        assertNull(StreamingShortcutLinks.spotifyAppUri("https://open.spotify.com/collection/tracks"))
    }

    @Test
    fun detectsSpotifyEntityTypeFromWebAndAppUris() {
        assertEquals(
            StreamingContentType.TRACK,
            StreamingShortcutLinks.detectContentType(
                "https://open.spotify.com/intl-br/track/abc123?si=share"
            )
        )
        assertEquals(
            StreamingContentType.PLAYLIST,
            StreamingShortcutLinks.detectContentType("spotify:playlist:abc123")
        )
        assertEquals(
            StreamingContentType.ALBUM,
            StreamingShortcutLinks.inspect("https://open.spotify.com/album/abc123").contentType
        )
        assertEquals(
            StreamingContentType.UNKNOWN,
            StreamingShortcutLinks.detectContentType("https://spotify.link/short-code")
        )
    }

    @Test
    fun detectsYoutubeAndAppleEntityTypesWithoutNetworkLookup() {
        assertEquals(
            StreamingContentType.PLAYLIST,
            StreamingShortcutLinks.detectContentType(
                "https://music.youtube.com/playlist?list=PL1"
            )
        )
        assertEquals(
            StreamingContentType.PLAYLIST,
            StreamingShortcutLinks.detectContentType(
                "https://music.youtube.com/watch?list=PL1"
            )
        )
        assertEquals(
            StreamingContentType.TRACK,
            StreamingShortcutLinks.detectContentType(
                "https://music.youtube.com/watch?v=video&amp;list=PL1"
                    .replace("&amp;", "&")
            )
        )
        assertEquals(
            StreamingContentType.ALBUM,
            StreamingShortcutLinks.detectContentType(
                "https://music.apple.com/br/album/example/123"
            )
        )
        assertEquals(
            StreamingContentType.TRACK,
            StreamingShortcutLinks.detectContentType(
                "https://music.apple.com/br/album/example/123?i=456"
            )
        )
    }

    @Test
    fun detectsProviderSpecificPlaylistShapes() {
        assertEquals(
            StreamingContentType.PLAYLIST,
            StreamingShortcutLinks.detectContentType(
                "https://soundcloud.com/artist/sets/weekend"
            )
        )
        assertEquals(
            StreamingContentType.PLAYLIST,
            StreamingShortcutLinks.detectContentType(
                "https://tidal.com/browse/playlist/abc"
            )
        )
        assertEquals(
            StreamingContentType.ALBUM,
            StreamingShortcutLinks.detectContentType(
                "https://music.amazon.com/albums/abc"
            )
        )
        assertEquals(
            StreamingContentType.PLAYLIST,
            StreamingShortcutLinks.detectContentType("deezer:playlist:abc")
        )
    }

    @Test
    fun spotifyUriHasWebFallback() {
        assertEquals(
            "https://open.spotify.com/playlist/abc123",
            StreamingShortcutLinks.forBrowser("spotify:playlist:abc123")
        )
    }

    @Test
    fun shuffleIsOnlyAddedForYoutubeMusic() {
        assertTrue(StreamingShortcutLinks.supportsShuffle("https://music.youtube.com/playlist?list=PL1"))
        assertFalse(StreamingShortcutLinks.supportsShuffle("https://music.youtube.com/watch?v=track1"))
        assertFalse(StreamingShortcutLinks.supportsShuffle("https://open.spotify.com/playlist/abc"))
        assertEquals(
            "https://open.spotify.com/playlist/abc",
            StreamingShortcutLinks.withYoutubeShuffle("https://open.spotify.com/playlist/abc")
        )
        assertEquals(
            "https://music.youtube.com/playlist?list=PL1&shuffle=true#player",
            StreamingShortcutLinks.withYoutubeShuffle(
                "https://music.youtube.com/playlist?list=PL1#player"
            )
        )
    }

    @Test
    fun strippingShufflePreservesOtherQueryAndFragment() {
        assertEquals(
            "https://music.youtube.com/playlist?list=PL1#player",
            StreamingShortcutLinks.stripShuffle(
                "https://music.youtube.com/playlist?shuffle=true&list=PL1#player"
            )
        )
    }

    @Test
    fun extractsLinkAndSuggestedNameFromSharePayload() {
        val shared = "Discovery Mix\nhttps://open.spotify.com/playlist/abc?si=share"
        assertEquals(
            "https://open.spotify.com/playlist/abc?si=share",
            StreamingShortcutLinks.extractSharedLink(shared)
        )
        assertEquals("Discovery Mix", StreamingShortcutLinks.suggestedName(shared))
        assertEquals(
            "Weekend",
            StreamingShortcutLinks.suggestedName(shared, subject = " Weekend ")
        )
    }

    @Test
    fun acceptsGenericAppUrisButRejectsUnsafeSchemes() {
        assertTrue(StreamingShortcutLinks.isSafeLink("spotify:album:abc"))
        assertTrue(StreamingShortcutLinks.isSafeLink("examplemusic://playlist/abc"))
        assertFalse(StreamingShortcutLinks.isSafeLink("file:///sdcard/private"))
        assertFalse(StreamingShortcutLinks.isSafeLink("intent://playlist#Intent;end"))
        assertFalse(StreamingShortcutLinks.isSafeLink("javascript:alert(1)"))
        assertNull(StreamingShortcutLinks.extractSharedLink("this is not a streaming link"))
    }

    @Test
    fun artistLinksAreTreatedAsPlayable() {
        // An artist share used to only navigate; it must now be playable so a play command is
        // actually issued (Spotify plays the artist's top tracks from spotify:artist:...).
        assertTrue(StreamingContentType.ARTIST.isPlayable)
        assertTrue(
            StreamingShortcutLinks.inspect("https://open.spotify.com/artist/abc123")
                .contentType.isPlayable
        )
    }

    @Test
    fun detectsYoutubeMusicArtistLinkForms() {
        // All three shapes a YouTube Music artist share can take. This matters beyond
        // classification: playDeepLink only substitutes the shortcut's name as a playFromSearch
        // query when the type is ARTIST, and that search is the one command that actually starts
        // an artist playing - the URI on its own only navigates to the page.
        assertEquals(
            StreamingContentType.ARTIST,
            StreamingShortcutLinks.inspect(
                "https://music.youtube.com/channel/UCabc123").contentType
        )
        assertEquals(
            StreamingContentType.ARTIST,
            StreamingShortcutLinks.inspect(
                "https://music.youtube.com/browse/UCabc123").contentType
        )
        assertEquals(
            StreamingContentType.ARTIST,
            StreamingShortcutLinks.inspect(
                "https://music.youtube.com/@someartist").contentType
        )
    }

    @Test
    fun youtubeMusicBrowseIdsDistinguishAlbumsAndPlaylists() {
        // Guards the browse/ prefix table: these ids are upper-case in the wild ("MPREb_", "VLPL"),
        // and inspect() lower-cases path segments before matching, so the prefixes it compares
        // against must stay lower-case too.
        assertEquals(
            StreamingContentType.ALBUM,
            StreamingShortcutLinks.inspect(
                "https://music.youtube.com/browse/MPREb_abc123").contentType
        )
        assertEquals(
            StreamingContentType.PLAYLIST,
            StreamingShortcutLinks.inspect(
                "https://music.youtube.com/browse/VLPLabc123").contentType
        )
    }

    @Test
    fun spotifyLikedSongsCollectionIsPlayable() {
        // The Play Spotify Liked Songs action relies on this URI classifying as a playable list.
        assertEquals(
            StreamingContentType.PLAYLIST,
            StreamingShortcutLinks.detectContentType("spotify:collection:tracks")
        )
        assertTrue(
            StreamingShortcutLinks.inspect("spotify:collection:tracks").contentType.isPlayable
        )
    }

    @Test
    fun youtubeMusicPlaybackUriIsCanonicalized() {
        // youtu.be short links and plain youtube.com links must become music.youtube.com/watch,
        // the shape that actually starts playback rather than only opening a page.
        assertEquals(
            "https://music.youtube.com/watch?v=abc123",
            StreamingShortcutLinks.forPlayback("https://youtu.be/abc123?si=xyz")
        )
        assertEquals(
            "https://music.youtube.com/watch?v=abc123",
            StreamingShortcutLinks.forPlayback("https://www.youtube.com/watch?v=abc123")
        )
        // A share already in music.youtube.com form is left intact.
        assertEquals(
            "https://music.youtube.com/watch?list=LM",
            StreamingShortcutLinks.forPlayback("https://music.youtube.com/watch?list=LM")
        )
    }
}
