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
        assertEquals(StreamingService.QOBUZ,
            StreamingShortcutLinks.detect("https://open.qobuz.com/playlist/12345"))
        assertEquals(StreamingService.BANDCAMP,
            StreamingShortcutLinks.detect("https://artist.bandcamp.com/album/example"))
        assertEquals(StreamingService.AUDIOMACK,
            StreamingShortcutLinks.detect("https://audiomack.com/artist/song/example"))
        assertEquals(StreamingService.MIXCLOUD,
            StreamingShortcutLinks.detect("https://www.mixcloud.com/artist/example-show/"))
        assertEquals(StreamingService.PANDORA,
            StreamingShortcutLinks.detect("https://pandora.app.link/example"))
    }

    @Test
    fun amazonMusicIsRecognisedByTheSchemeItsAppDeclares() {
        // The phone app declares `amznmp3` (and the generic `amzn`), not `amzn-music`.
        assertEquals(StreamingService.AMAZON_MUSIC,
            StreamingShortcutLinks.detect("amznmp3://albums/B0EXAMPLE"))
        assertEquals(StreamingContentType.ALBUM,
            StreamingShortcutLinks.detectContentType("amznmp3://albums/B0EXAMPLE"))
        assertEquals(StreamingService.AMAZON_MUSIC,
            StreamingShortcutLinks.detect("amzn-music://playlists/B0EXAMPLE"))
        // `amzn:` alone belongs to Amazon's other apps as much as to this one.
        assertEquals(StreamingService.GENERIC,
            StreamingShortcutLinks.detect("amzn://apps/android?p=com.example"))
        assertEquals("amznmp3://albums/B0EXAMPLE",
            StreamingShortcutLinks.extractSharedLink("Listen: amznmp3://albums/B0EXAMPLE"))
    }

    @Test
    fun providerLookalikeHostsStayGeneric() {
        listOf(
            "https://qobuz.com.example.org/track/1",
            "https://notbandcamp.com/album/example",
            "https://audiomack.com.example.org/artist/song/example",
            "https://notmixcloud.com/artist/example-show/",
            "https://pandora.app.link.example.org/example"
        ).forEach { link ->
            assertEquals(StreamingService.GENERIC, StreamingShortcutLinks.detect(link))
        }
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
    fun youtubeSearchFallbackProvidesPlayFromSearchQuery() {
        val link = "https://music.youtube.com/search?q=Fly%20Away%20From%20This%20World%20Blade%20%26%20Bath"
        assertEquals(
                "Fly Away From This World Blade & Bath",
                StreamingShortcutLinks.youtubeMusicSearchQuery(link))
        assertEquals(
                StreamingContentType.UNKNOWN,
                StreamingShortcutLinks.detectContentType(link))
    }

    @Test
    fun youtubeSearchQueryDoesNotAcceptOtherPathsOrProviders() {
        assertNull(StreamingShortcutLinks.youtubeMusicSearchQuery(
                "https://music.youtube.com/watch?v=abc"))
        assertNull(StreamingShortcutLinks.youtubeMusicSearchQuery(
                "https://open.spotify.com/search?q=abc"))
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
    fun detectsAdditionalProviderEntityShapes() {
        assertEquals(
            StreamingContentType.TRACK,
            StreamingShortcutLinks.detectContentType("https://open.qobuz.com/track/12345")
        )
        assertEquals(
            StreamingContentType.ALBUM,
            StreamingShortcutLinks.detectContentType(
                "https://artist.bandcamp.com/album/example"
            )
        )
        assertEquals(
            StreamingContentType.PLAYLIST,
            StreamingShortcutLinks.detectContentType(
                "https://audiomack.com/artist/playlist/example"
            )
        )
        assertEquals(
            StreamingContentType.SHOW,
            StreamingShortcutLinks.detectContentType(
                "https://www.mixcloud.com/artist/example-show/"
            )
        )
        assertEquals(
            StreamingContentType.SHOW,
            StreamingShortcutLinks.detectContentType(
                "https://www.pandora.com/podcast/example/PC:123"
            )
        )
        assertEquals(
            StreamingContentType.MIX,
            StreamingShortcutLinks.detectContentType("https://www.deezer.com/flow")
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
    fun unwrapsOnlyValidRemoteLaunchTargets() {
        assertEquals(
            "https://open.qobuz.com/album/12345",
            StreamingShortcutLinks.unwrapRemoteTarget(
                "com.qobuz.music|https://open.qobuz.com/album/12345"
            )
        )
        assertEquals(
            "spotify:collection:tracks",
            StreamingShortcutLinks.unwrapRemoteTarget(
                "com.spotify.music|spotify:collection:tracks"
            )
        )

        val ordinaryLinkWithPipe = "https://example.com/playlist/one|two"
        assertEquals(
            ordinaryLinkWithPipe,
            StreamingShortcutLinks.unwrapRemoteTarget(ordinaryLinkWithPipe)
        )
        val unsafeEnvelope = "com.qobuz.music|javascript:alert(1)"
        assertEquals(unsafeEnvelope, StreamingShortcutLinks.unwrapRemoteTarget(unsafeEnvelope))
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

    @Test
    fun searchableNameDropsTheServiceNameShareSheetsAppend() {
        assertEquals("Virtualism", StreamingShortcutLinks.searchableName("Virtualism - SoundCloud"))
        assertEquals("TAKO∞TAKOVER", StreamingShortcutLinks.searchableName("TAKO∞TAKOVER - SoundCloud"))
        assertEquals("Song", StreamingShortcutLinks.searchableName("Song | Spotify"))
        assertEquals("Mix", StreamingShortcutLinks.searchableName("  Mix — YouTube Music "))
        // A separator before anything that is not a service is part of the name.
        assertEquals("Black Gole • Muse", StreamingShortcutLinks.searchableName("Black Gole • Muse"))
        assertEquals("Best of Spotify", StreamingShortcutLinks.searchableName("Best of Spotify"))
        assertEquals(null, StreamingShortcutLinks.searchableName(" - SoundCloud"))
        assertEquals(null, StreamingShortcutLinks.searchableName("   "))
    }

    /**
     * What the YouTube app's share sheet hands out is played by YouTube Music under another
     * address, and only those shapes: see [StreamingShortcutLinks.playsUnderAnotherAddress].
     */
    @Test
    fun youtubeLinksToOneVideoOrPlaylistArePlayedUnderTheMusicAddress() {
        listOf(
                "https://youtu.be/dQw4w9WgXcQ?si=abc",
                "https://youtu.be/dQw4w9WgXcQ?list=PL1234567890",
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PL1234567890&index=3",
                "https://m.youtube.com/watch?v=dQw4w9WgXcQ&feature=shared",
                "https://youtube.com/playlist?list=PL1234567890&si=abc",
                "youtube.com/watch?v=dQw4w9WgXcQ"
        ).forEach { link ->
            assertTrue(link, StreamingShortcutLinks.playsUnderAnotherAddress(link))
            val playback = StreamingShortcutLinks.forPlayback(link)
            assertTrue("$link -> $playback", playback.startsWith("https://music.youtube.com/watch?"))
        }
    }

    @Test
    fun linksThatArePlayedWhereTheyOpenAreNotSentToAnotherApp() {
        listOf(
                // Already YouTube Music's own address: the app that opens it is the one that plays.
                "https://music.youtube.com/watch?v=dQw4w9WgXcQ&si=AbC",
                "https://music.youtube.com/playlist?list=PL1234567890",
                "https://music.youtube.com/watch?list=LM",
                // A channel plays by a name search, which could match somebody else there.
                "https://www.youtube.com/@LofiGirl",
                "https://www.youtube.com/channel/UCabcdef",
                // Not known to play in YouTube Music; they stay with the YouTube app.
                "https://www.youtube.com/live/jfKfPfyJRdk?si=abc",
                "https://www.youtube.com/shorts/abcdefghijk",
                "https://www.youtube.com/embed/dQw4w9WgXcQ",
                // Other services have one app for both.
                "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M",
                "spotify:playlist:37i9dQZF1DXcBWIGoYBM5M",
                "https://soundcloud.com/forss/sets/soulhack",
                "https://www.deezer.com/playlist/908622995",
                "https://example.com/playlist/1",
                ""
        ).forEach { link ->
            assertFalse(link, StreamingShortcutLinks.playsUnderAnotherAddress(link))
        }
    }

    @Test
    fun spotifyShortLinksOfBothGenerationsAreSpotify() {
        assertEquals(StreamingService.SPOTIFY,
                StreamingShortcutLinks.detect("https://spotify.link/AbCdEfGhIj"))
        assertEquals(StreamingService.SPOTIFY,
                StreamingShortcutLinks.detect("https://spotify.app.link/AbCdEfGhIj"))
        // What they point at is not known until they are opened.
        assertEquals(StreamingContentType.UNKNOWN,
                StreamingShortcutLinks.detectContentType("https://spotify.app.link/AbCdEfGhIj"))
        assertEquals(StreamingService.GENERIC,
                StreamingShortcutLinks.detect("https://notspotify.app.link/AbCdEfGhIj"))
    }

    @Test
    fun amazonMusicCollectionsOtherThanAlbumsAreRecognised() {
        fun type(link: String) = StreamingShortcutLinks.detectContentType(link)
        assertEquals(StreamingContentType.PLAYLIST,
                type("https://music.amazon.com/user-playlists/abcdef0123456789?ref=dm_sh_x"))
        assertEquals(StreamingContentType.PLAYLIST,
                type("https://music.amazon.com/my/playlists/123e4567-e89b"))
        assertEquals(StreamingContentType.PLAYLIST,
                type("https://music.amazon.com.br/playlists/B07XYZ1234"))
        assertEquals(StreamingContentType.MIX,
                type("https://music.amazon.com/stations/A2UW0MECRAWILL"))
        assertEquals(StreamingContentType.SHOW,
                type("https://music.amazon.com/podcasts/123e4567/name"))
        // An album link that names one of its tracks is still the album.
        assertEquals(StreamingContentType.ALBUM, type(
                "https://music.amazon.com/albums/B08FCB1JZP?marketplaceId=A&trackAsin=B08FC9T1ZP"))
        assertEquals(StreamingContentType.UNKNOWN, type("https://music.amazon.com/"))
    }

    @Test
    fun soundcloudProfileTabsAndSitePagesAreNotTracks() {
        fun type(link: String) = StreamingShortcutLinks.detectContentType(link)
        // A track and a set, as before.
        assertEquals(StreamingContentType.TRACK, type("https://soundcloud.com/forss/flickermood"))
        assertEquals(StreamingContentType.TRACK,
                type("https://soundcloud.com/forss/flickermood/s-AbCdE"))
        assertEquals(StreamingContentType.PLAYLIST, type("https://soundcloud.com/forss/sets/soulhack"))
        assertEquals(StreamingContentType.PLAYLIST,
                type("https://soundcloud.com/discover/sets/weekly::forss"))
        assertEquals(StreamingContentType.PLAYLIST, type("https://soundcloud.com/you/likes"))
        assertEquals(StreamingContentType.ARTIST, type("https://soundcloud.com/forss"))
        // A profile's tabs are that artist, played by the saved name.
        listOf("tracks", "popular-tracks", "albums", "sets", "reposts", "likes").forEach { tab ->
            assertEquals(tab, StreamingContentType.ARTIST, type("https://soundcloud.com/forss/$tab"))
        }
        // SoundCloud's own pages are nobody's profile and nobody's track.
        assertEquals(StreamingContentType.UNKNOWN, type("https://soundcloud.com/search?q=lofi"))
        assertEquals(StreamingContentType.UNKNOWN, type("https://soundcloud.com/charts/top"))
        assertEquals(StreamingContentType.UNKNOWN, type("https://soundcloud.com/discover"))
        assertEquals(StreamingContentType.MIX,
                type("https://soundcloud.com/stations/track/forss/flickermood"))
        // A short link still names nothing until it is opened.
        assertEquals(StreamingContentType.UNKNOWN, type("https://on.soundcloud.com/AbCdEf"))
    }
}
