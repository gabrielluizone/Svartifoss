package com.svartifoss.snfell.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins which public launches the visible rung of the playback ladder may use to make a shortcut
 * *play*, and for which apps.
 *
 * The plan is deliberately narrow: each technique is one an app declares in its own manifest, and
 * each is offered only to the app that declared it. A launch reaching some other app, an alternative
 * client being sent Spotify's URI form, or a personal collection being looked up by name would each
 * play the wrong thing - confidently, with nothing on screen to say why.
 */
class VisiblePlaybackPlanTest {

    private val spotify = StreamingService.SPOTIFY.packageName!!
    private val soundcloud = StreamingService.SOUNDCLOUD.packageName!!

    private fun plan(link: String, target: String, name: String? = "Name") =
            VisiblePlaybackPlan.candidates(
                    link, target, StreamingShortcutLinks.inspect(link).contentType, name)

    @Test
    fun `a spotify playlist is asked to play through its own uri, play-from-search first`() {
        listOf("https://open.spotify.com/playlist/37i9dQZF1DX", "spotify:playlist:37i9dQZF1DX").forEach { link ->
            val launches = plan(link, spotify)
            assertEquals(link, listOf(VisiblePlayTechnique.PLAY_FROM_SEARCH_URI, VisiblePlayTechnique.VIEW_AUTOPLAY),
                    launches.map { it.technique })
            val search = launches[0]
            assertEquals(VisiblePlaybackPlan.ACTION_MEDIA_PLAY_FROM_SEARCH, search.action)
            assertEquals("spotify:playlist:37i9dQZF1DX", search.data)
            val autoplay = launches[1]
            assertEquals(VisiblePlaybackPlan.ACTION_VIEW, autoplay.action)
            assertEquals("spotify:playlist:37i9dQZF1DX:play", autoplay.data)
        }
    }

    @Test
    fun `every spotify launch carries browsable, the filter spotify opened to everyone`() {
        // Spotify declares MEDIA_PLAY_FROM_SEARCH with its scheme *and* BROWSABLE. Asking with the
        // category means the launch can only ever match an entry point the app exposed to the web.
        plan("spotify:album:1", spotify).forEach {
            assertTrue(it.categories.contains(VisiblePlaybackPlan.CATEGORY_BROWSABLE))
        }
    }

    @Test
    fun `spotify liked songs uses the collection uri in every spelling`() {
        listOf("spotify:collection:tracks", "https://open.spotify.com/collection/tracks",
                "spotify:user:someone:collection").forEach { link ->
            val launches = plan(link, spotify)
            assertEquals(link, "spotify:collection:tracks", launches.first().data)
            assertEquals(link, "spotify:collection:tracks:play", launches[1].data)
        }
    }

    @Test
    fun `an alternative spotify client is never sent spotify's own uri techniques`() {
        assertTrue(plan("https://open.spotify.com/playlist/1", "com.example.spotifyclient").isEmpty())
    }

    @Test
    fun `a spotify short link names nothing playable yet`() {
        assertTrue(plan("https://spotify.link/abc", spotify).isEmpty())
    }

    @Test
    fun `soundcloud's own likes use the app's play-likes action`() {
        val launches = plan("https://soundcloud.com/you/likes", soundcloud)
        assertEquals(listOf(VisiblePlayTechnique.APP_PLAY_ACTION), launches.map { it.technique })
        assertEquals(VisiblePlaybackPlan.SOUNDCLOUD_PLAY_LIKES, launches.single().action)
        assertEquals(null, launches.single().data)
    }

    @Test
    fun `somebody else's soundcloud likes are not the account's own`() {
        // "Play Likes" plays the signed-in account's likes, so a profile's likes must not map to it -
        // and a personal collection is never looked up by name either.
        assertTrue(plan("https://soundcloud.com/someartist/likes", soundcloud).isEmpty())
    }

    @Test
    fun `a soundcloud set falls back to a playlist search by its name`() {
        val launch = plan("https://soundcloud.com/artist/sets/late-night", soundcloud, "Late Night").single()
        assertEquals(VisiblePlayTechnique.PLAY_FROM_SEARCH_QUERY, launch.technique)
        assertEquals(null, launch.data)
        assertEquals("Late Night", launch.extras[VisiblePlaybackPlan.EXTRA_QUERY])
        assertEquals(VisiblePlaybackPlan.FOCUS_PLAYLIST, launch.extras[VisiblePlaybackPlan.EXTRA_MEDIA_FOCUS])
        assertEquals("Late Night", launch.extras[VisiblePlaybackPlan.EXTRA_MEDIA_PLAYLIST])
    }

    @Test
    fun `a soundcloud short link is asked for by its name, without the service's suffix`() {
        // What SoundCloud's share sheet hands out: on.soundcloud.com, which names neither a set nor
        // a track until opened, saved under the title the share text carried.
        val launch = plan("https://on.soundcloud.com/DjYLffVA8ZOPh1tfxA", soundcloud,
                "Virtualism - SoundCloud").single()
        assertEquals(VisiblePlayTechnique.PLAY_FROM_SEARCH_QUERY, launch.technique)
        assertEquals("Virtualism", launch.extras[VisiblePlaybackPlan.EXTRA_QUERY])
        assertEquals(VisiblePlaybackPlan.FOCUS_ANY, launch.extras[VisiblePlaybackPlan.EXTRA_MEDIA_FOCUS])
        assertEquals(2, launch.extras.size)
    }

    @Test
    fun `an artist in any app can be asked for by name, after an exact uri`() {
        val deezer = plan("https://www.deezer.com/artist/27", "deezer.android.app", "Daft Punk").single()
        assertEquals(VisiblePlayTechnique.PLAY_FROM_SEARCH_QUERY, deezer.technique)
        assertEquals(VisiblePlaybackPlan.FOCUS_ARTIST, deezer.extras[VisiblePlaybackPlan.EXTRA_MEDIA_FOCUS])
        assertEquals("Daft Punk", deezer.extras[VisiblePlaybackPlan.EXTRA_MEDIA_ARTIST])

        val spotifyArtist = plan("https://open.spotify.com/artist/4tZwfgrHOc3mvqYlEYSvVi", spotify, "Daft Punk")
        assertEquals(listOf(VisiblePlayTechnique.PLAY_FROM_SEARCH_URI, VisiblePlayTechnique.VIEW_AUTOPLAY,
                VisiblePlayTechnique.PLAY_FROM_SEARCH_QUERY), spotifyArtist.map { it.technique })
    }

    @Test
    fun `no name means no name search`() {
        assertTrue(plan("https://www.deezer.com/artist/27", "deezer.android.app", name = null).isEmpty())
        assertTrue(plan("https://www.deezer.com/artist/27", "deezer.android.app", name = "  ").isEmpty())
    }

    @Test
    fun `a precise playlist elsewhere is never swapped for a same-named one`() {
        // Somebody else's playlist called "Workout" would play confidently and be wrong.
        assertTrue(plan("https://www.deezer.com/playlist/1", "deezer.android.app", "Workout").isEmpty())
        assertTrue(plan("https://tidal.com/browse/playlist/1", "com.aspiro.tidal", "Workout").isEmpty())
    }

    @Test
    fun `youtube music gets nothing here, its media browser already plays`() {
        assertTrue(plan("https://music.youtube.com/watch?list=LM", StreamingService.YOUTUBE_MUSIC.packageName!!).isEmpty())
        assertTrue(plan("https://music.youtube.com/playlist?list=PL1", StreamingService.YOUTUBE_MUSIC.packageName!!).isEmpty())
    }

    @Test
    fun `every launch names exactly the app it is for`() {
        listOf(
                "spotify:playlist:1" to spotify,
                "https://open.spotify.com/artist/1" to spotify,
                "https://soundcloud.com/you/likes" to soundcloud,
                "https://soundcloud.com/a/sets/b" to soundcloud,
                "https://www.deezer.com/artist/27" to "deezer.android.app"
        ).forEach { (link, target) ->
            plan(link, target).forEach { assertEquals(link, target, it.targetPackage) }
        }
    }

    @Test
    fun `outcomes are remembered per app and per kind of content`() {
        assertEquals("$spotify/playlist", VisiblePlaybackPlan.memoryKey(
                spotify, "spotify:playlist:1", StreamingContentType.PLAYLIST))
        assertEquals("$spotify/album", VisiblePlaybackPlan.memoryKey(
                spotify, "spotify:album:1", StreamingContentType.ALBUM))
        assertEquals("$spotify/collection", VisiblePlaybackPlan.memoryKey(
                spotify, "spotify:collection:tracks", StreamingContentType.PLAYLIST))
    }

    @Test
    fun `the plain open views the link in the app and starts nothing`() {
        val navigation = VisibleLaunch.navigation("spotify:playlist:1", spotify)
        assertEquals(null, navigation.technique)
        assertEquals(VisiblePlaybackPlan.ACTION_VIEW, navigation.action)
        assertEquals("spotify:playlist:1", navigation.data)
        assertTrue(navigation.categories.contains(VisiblePlaybackPlan.CATEGORY_BROWSABLE))
    }
}
