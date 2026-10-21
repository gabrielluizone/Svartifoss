package com.svartifoss.snfell.watch.view.shortcut

import com.svartifoss.snfell.watch.view.shortcut.ShortcutDestination.Service
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins what the shortcut screen reads off a launch target: its service, and Liked Music. */
class ShortcutDestinationTest {

    private val ytm = "com.google.android.apps.youtube.music"

    @Test
    fun `the package envelope is read, and a plain link has none`() {
        assertEquals(ytm, ShortcutDestination.packageOf("$ytm|https://music.youtube.com/watch?list=LM"))
        assertEquals("https://music.youtube.com/watch?list=LM",
                ShortcutDestination.linkOf("$ytm|https://music.youtube.com/watch?list=LM"))
        assertNull(ShortcutDestination.packageOf("https://open.spotify.com/playlist/37i9"))
        assertEquals("spotify:collection:tracks", ShortcutDestination.linkOf("spotify:collection:tracks"))
        // A pipe in a link is not an envelope unless what precedes it is a package name.
        assertNull(ShortcutDestination.packageOf("https://example.com/a|b"))
        assertNull(ShortcutDestination.packageOf("|https://music.youtube.com"))
    }

    @Test
    fun `the service comes from the link`() {
        assertEquals(Service.YOUTUBE_MUSIC, ShortcutDestination.serviceOf("$ytm|https://music.youtube.com/watch?list=LM"))
        assertEquals(Service.YOUTUBE_MUSIC, ShortcutDestination.serviceOf("https://www.youtube.com/playlist?list=PL1"))
        assertEquals(Service.YOUTUBE_MUSIC, ShortcutDestination.serviceOf("https://youtu.be/abc"))
        assertEquals(Service.SPOTIFY, ShortcutDestination.serviceOf("com.spotify.music|spotify:collection:tracks"))
        assertEquals(Service.SPOTIFY, ShortcutDestination.serviceOf("https://open.spotify.com/album/1"))
        assertEquals(Service.SOUNDCLOUD, ShortcutDestination.serviceOf("https://soundcloud.com/you/likes"))
        assertEquals(Service.DEEZER, ShortcutDestination.serviceOf("https://www.deezer.com/flow"))
        assertEquals(Service.DEEZER, ShortcutDestination.serviceOf("https://deezer.page.link/xyz"))
    }

    @Test
    fun `a client of a service still shows that service`() {
        // A patched YouTube Music opens the same links; the mark is the service's.
        assertEquals(Service.YOUTUBE_MUSIC,
                ShortcutDestination.serviceOf("app.morphe.android.apps.youtube.music|https://music.youtube.com/watch?list=LM"))
    }

    @Test
    fun `the package answers only when the link says nothing`() {
        assertEquals(Service.DEEZER, ShortcutDestination.serviceOf("deezer.android.app|https://link.example/x"))
        assertNull(ShortcutDestination.serviceOf("com.example.player|https://link.example/x"))
        assertNull(ShortcutDestination.serviceOf("https://music.apple.com/album/1"))
        assertNull(ShortcutDestination.serviceOf("not a link"))
    }

    @Test
    fun `Liked Music is the LM playlist on YouTube, in any spelling`() {
        assertTrue(ShortcutDestination.isYouTubeMusicLikedMusic("$ytm|https://music.youtube.com/watch?list=LM"))
        assertTrue(ShortcutDestination.isYouTubeMusicLikedMusic("https://music.youtube.com/playlist?list=LM"))
        assertTrue(ShortcutDestination.isYouTubeMusicLikedMusic("https://www.youtube.com/watch?v=abc&list=LM"))
        assertFalse(ShortcutDestination.isYouTubeMusicLikedMusic("https://music.youtube.com/playlist?list=LMx"))
        assertFalse(ShortcutDestination.isYouTubeMusicLikedMusic("https://music.youtube.com/playlist?list=PL1"))
        assertFalse(ShortcutDestination.isYouTubeMusicLikedMusic("com.spotify.music|spotify:collection:tracks"))
        assertFalse(ShortcutDestination.isYouTubeMusicLikedMusic("https://example.com/watch?list=LM"))
        assertFalse(ShortcutDestination.isYouTubeMusicLikedMusic(""))
    }
}
