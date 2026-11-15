package com.svartifoss.snfell.music

import com.svartifoss.snfell.music.ShortcutTrackListing.CacheUse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The song list of a saved playlist: which links can be listed, how a song is played inside its
 * playlist, and what a `playlistItems.list` page holds. Ids are made up; the shapes are the ones
 * the API returned on 2026-11-14 (`docs/streaming-app-interop.md` section 5.9).
 */
class ShortcutTrackListingTest {

    private val playlist = "PLabcdefghijklmnopqrstuvwxyz012345"
    private val album = "OLAK5uy_abcdefghijklmnopqrstuvwxyz0123456"

    @Test
    fun aYouTubeMusicPlaylistOrAlbumNamesItsList() {
        assertEquals(playlist, ShortcutTrackListing.playlistIdFor(
                "https://music.youtube.com/playlist?list=$playlist"))
        assertEquals(album, ShortcutTrackListing.playlistIdFor(
                "https://music.youtube.com/playlist?list=$album"))
        assertEquals(playlist, ShortcutTrackListing.playlistIdFor(
                "https://www.youtube.com/playlist?list=$playlist&si=share"))
        assertEquals(playlist, ShortcutTrackListing.playlistIdFor(
                "https://music.youtube.com/watch?v=abcdefghijk&list=$playlist"))
        assertEquals(playlist, ShortcutTrackListing.playlistIdFor(
                "https://youtu.be/abcdefghijk?list=$playlist"))
        // A short PL id the owner saved, shorter than the usual 34 characters, lists too.
        assertEquals("PLabcdefghijk", ShortcutTrackListing.playlistIdFor(
                "https://music.youtube.com/playlist?list=PLabcdefghijk"))
    }

    @Test
    fun theLaunchEnvelopeTheWatchHoldsIsUnwrapped() {
        assertEquals(playlist, ShortcutTrackListing.playlistIdFor(
                "com.google.android.apps.youtube.music|https://music.youtube.com/playlist?list=$playlist"))
    }

    @Test
    fun thePrivateListsAndOtherServicesAreNeverAskedAbout() {
        // Liked Music answered 404 to an API key; liked videos and Watch later are as private.
        assertNull(ShortcutTrackListing.playlistIdFor("https://music.youtube.com/playlist?list=LM"))
        assertNull(ShortcutTrackListing.playlistIdFor("https://music.youtube.com/watch?list=LM"))
        assertNull(ShortcutTrackListing.playlistIdFor("https://www.youtube.com/playlist?list=LL"))
        assertNull(ShortcutTrackListing.playlistIdFor("https://www.youtube.com/playlist?list=WL"))
        assertNull(ShortcutTrackListing.playlistIdFor("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M"))
        assertNull(ShortcutTrackListing.playlistIdFor("https://music.youtube.com/@someartist"))
        assertNull(ShortcutTrackListing.playlistIdFor("https://music.youtube.com/watch?v=abcdefghijk"))
        assertNull(ShortcutTrackListing.playlistIdFor("https://music.youtube.com.evil.example/playlist?list=$playlist"))
        assertNull(ShortcutTrackListing.playlistIdFor("https://music.youtube.com/playlist?list=PL%3Cscript%3E"))
        assertNull(ShortcutTrackListing.playlistIdFor("not a link at all"))
    }

    @Test
    fun aSongIsPlayedInsideItsPlaylist() {
        // watch?v=X&list=P started on X and went on with the playlist, warm and cold.
        assertEquals("https://music.youtube.com/watch?v=abcdefghijk&list=$playlist",
                ShortcutTrackListing.trackLink("abcdefghijk", playlist))
    }

    @Test
    fun youTubeMusicsTopicSuffixIsNotPartOfTheArtist() {
        assertEquals("Moisés Nieto", ShortcutTrackListing.artistOf("Moisés Nieto - Topic"))
        assertEquals("some uploader", ShortcutTrackListing.artistOf("some uploader"))
        assertNull(ShortcutTrackListing.artistOf(""))
        assertNull(ShortcutTrackListing.artistOf(null))
    }

    @Test
    fun aPageListsThePlayableSongsInOrder() {
        val page = ShortcutTrackListing.parsePage("""
            {
              "nextPageToken": "EAAaBlBUOkNESQ",
              "pageInfo": { "totalResults": 74 },
              "items": [
                { "snippet": { "title": "First", "videoOwnerChannelTitle": "Artist One - Topic" },
                  "contentDetails": { "videoId": "aaaaaaaaaaa" }, "status": { "privacyStatus": "public" } },
                { "snippet": { "title": "Unlisted one", "videoOwnerChannelTitle": "Release - Topic" },
                  "contentDetails": { "videoId": "bbbbbbbbbbb" }, "status": { "privacyStatus": "unlisted" } },
                { "snippet": { "title": "Private video" },
                  "contentDetails": { "videoId": "ccccccccccc" }, "status": { "privacyStatus": "private" } },
                { "snippet": { "title": "Deleted video" },
                  "contentDetails": { "videoId": "ddddddddddd" }, "status": { "privacyStatus": "privacyStatusUnspecified" } },
                { "snippet": { "title": "No id" }, "contentDetails": {}, "status": { "privacyStatus": "public" } },
                { "snippet": { "title": "A fan upload", "videoOwnerChannelTitle": "someone" },
                  "contentDetails": { "videoId": "eeeeeeeeeee" }, "status": { "privacyStatus": "public" } }
              ]
            }
        """.trimIndent())!!
        assertEquals(listOf(
                ShortcutTrackListing.Track("aaaaaaaaaaa", "First", "Artist One"),
                ShortcutTrackListing.Track("bbbbbbbbbbb", "Unlisted one", "Release"),
                ShortcutTrackListing.Track("eeeeeeeeeee", "A fan upload", "someone")),
                page.tracks)
        assertEquals("EAAaBlBUOkNESQ", page.nextPageToken)
        assertEquals(74, page.totalResults)
    }

    @Test
    fun theLastPageHasNoNextTokenAndGarbageIsNoPage() {
        val last = ShortcutTrackListing.parsePage("""{ "pageInfo": { "totalResults": 1 }, "items": [] }""")!!
        assertNull(last.nextPageToken)
        assertTrue(last.tracks.isEmpty())
        assertNull(ShortcutTrackListing.parsePage("<html>quota</html>"))
        assertNull(ShortcutTrackListing.parsePage("""{ "error": { "code": 404 } }"""))
    }

    @Test
    fun anAnswerIsFreshForADayAndNeverKeptPastTheApiPolicy() {
        val hour = 60L * 60 * 1000
        assertEquals(CacheUse.FRESH, ShortcutTrackListing.cacheUse(0))
        assertEquals(CacheUse.FRESH, ShortcutTrackListing.cacheUse(23 * hour))
        assertEquals(CacheUse.STALE, ShortcutTrackListing.cacheUse(25 * hour))
        assertEquals(CacheUse.STALE, ShortcutTrackListing.cacheUse(27 * 24 * hour))
        assertEquals(CacheUse.EXPIRED, ShortcutTrackListing.cacheUse(28 * 24 * hour))
        // A clock set back: no idea how old it is, so never fresh.
        assertEquals(CacheUse.STALE, ShortcutTrackListing.cacheUse(-hour))
        // The YouTube API Services policies allow 30 days at most.
        assertTrue(ShortcutTrackListing.MAX_AGE_MS < 30L * 24 * hour)
    }

    @Test
    fun aLookupIsAWholeNumberOfPages() {
        assertEquals(0, ShortcutTrackListing.MAX_TRACKS % ShortcutTrackListing.PAGE_SIZE)
        assertTrue("at most four quota units a lookup",
                ShortcutTrackListing.MAX_TRACKS / ShortcutTrackListing.PAGE_SIZE <= 4)
    }
}
