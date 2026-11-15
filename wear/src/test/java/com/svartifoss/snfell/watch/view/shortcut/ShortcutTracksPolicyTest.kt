package com.svartifoss.snfell.watch.view.shortcut

import com.svartifoss.snfell.proto.ShortcutTracks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The songs section of the shortcut screen: which shortcuts ask, which answers count, and what a
 * refresh that fails does to a list already on screen.
 */
class ShortcutTracksPolicyTest {

    private val ytm = "com.google.android.apps.youtube.music"
    private val playlist = "https://music.youtube.com/playlist?list=PLabcdefghijklmnopqrstuvwxyz012345"

    private fun answer(
            status: ShortcutTracks.Status,
            link: String = playlist,
            requestId: String = "r1",
            vararg titles: String
    ): ShortcutTracks = ShortcutTracks.newBuilder()
            .setLink(link)
            .setRequestId(requestId)
            .setStatus(status)
            .addAllTracks(titles.mapIndexed { index, title ->
                ShortcutTracks.Track.newBuilder()
                        .setEntryId("https://music.youtube.com/watch?v=v$index&list=PL")
                        .setTitle(title)
                        .setArtist("Artist $index")
                        .build()
            })
            .setTotalCount(titles.size)
            .build()

    @Test
    fun `only YouTube playlists and albums ask, never the private lists or other services`() {
        assertTrue(ShortcutDestination.mayListSongs(playlist))
        assertTrue(ShortcutDestination.mayListSongs("$ytm|$playlist"))
        assertTrue(ShortcutDestination.mayListSongs(
                "https://music.youtube.com/playlist?list=OLAK5uy_abcdefghijklmnopqrstuvwxyz0123456"))
        assertFalse(ShortcutDestination.mayListSongs("https://music.youtube.com/playlist?list=LM"))
        assertFalse(ShortcutDestination.mayListSongs("$ytm|https://music.youtube.com/watch?list=LM"))
        assertFalse(ShortcutDestination.mayListSongs("https://music.youtube.com/watch?v=abcdefghijk"))
        assertFalse(ShortcutDestination.mayListSongs("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M"))
        assertFalse(ShortcutDestination.mayListSongs("https://soundcloud.com/someone/sets/a-set"))
        assertEquals(ShortcutTracksUi.Hidden,
                ShortcutTracksPolicy.initialState("https://open.spotify.com/album/1", null))
        assertEquals(ShortcutTracksUi.Loading, ShortcutTracksPolicy.initialState(playlist, null))
    }

    @Test
    fun `a list seen moments ago is shown at once`() {
        val listed = ShortcutTracksUi.Listed(listOf(ShortcutTrackRow("e", "Song", null)), total = 1)
        assertSame(listed, ShortcutTracksPolicy.initialState(playlist, listed))
    }

    @Test
    fun `only the answer to this shortcut's newest request counts`() {
        val listed = answer(ShortcutTracks.Status.LISTED, titles = arrayOf("One", "Two"))
        val state = ShortcutTracksPolicy.stateFor(listed, playlist, "r1") as ShortcutTracksUi.Listed
        assertEquals(listOf("One", "Two"), state.rows.map { it.title })
        assertEquals("Artist 0", state.rows.first().artist)
        // Another shortcut's answer, and one replayed from an older request, are not this one's.
        assertNull(ShortcutTracksPolicy.stateFor(listed, "https://music.youtube.com/playlist?list=PLother", "r1"))
        assertNull(ShortcutTracksPolicy.stateFor(listed, playlist, "r2"))
        assertNull(ShortcutTracksPolicy.stateFor(listed, playlist, null))
    }

    @Test
    fun `each status becomes what the screen shows`() {
        assertEquals(ShortcutTracksUi.Offer,
                ShortcutTracksPolicy.stateFor(answer(ShortcutTracks.Status.NEEDS_CONSENT), playlist, "r1"))
        assertEquals(ShortcutTracksUi.Failed,
                ShortcutTracksPolicy.stateFor(answer(ShortcutTracks.Status.FAILED), playlist, "r1"))
        assertEquals(ShortcutTracksUi.Hidden,
                ShortcutTracksPolicy.stateFor(answer(ShortcutTracks.Status.NOT_LISTABLE), playlist, "r1"))
        // An empty playlist has nothing to list: the screen is the one it always was.
        assertEquals(ShortcutTracksUi.Hidden,
                ShortcutTracksPolicy.stateFor(answer(ShortcutTracks.Status.LISTED), playlist, "r1"))
    }

    @Test
    fun `a refresh that cannot answer leaves the list on screen`() {
        val listed = ShortcutTracksUi.Listed(listOf(ShortcutTrackRow("e", "Song", null)), total = 1)
        assertSame(listed, ShortcutTracksPolicy.merge(listed, ShortcutTracksUi.Failed))
        assertSame(listed, ShortcutTracksPolicy.merge(listed, ShortcutTracksUi.Offer))
        assertSame(listed, ShortcutTracksPolicy.merge(listed, ShortcutTracksUi.Loading))
        // The phone saying it can no longer be listed (made private) does take it away.
        assertEquals(ShortcutTracksUi.Hidden, ShortcutTracksPolicy.merge(listed, ShortcutTracksUi.Hidden))
        assertEquals(ShortcutTracksUi.Failed,
                ShortcutTracksPolicy.merge(ShortcutTracksUi.Loading, ShortcutTracksUi.Failed))
    }

    @Test
    fun `the memory keeps a few recent lists and forgets old ones`() {
        val listed = ShortcutTracksUi.Listed(listOf(ShortcutTrackRow("e", "Song", null)), total = 1)
        ShortcutTrackMemory.keep("memory-test-0", listed, nowMs = 1_000)
        assertTrue(ShortcutTrackMemory.isRecent("memory-test-0", 1_000 + 60_000))
        assertFalse(ShortcutTrackMemory.isRecent("memory-test-0",
                1_000 + ShortcutTracksPolicy.REMEMBERED_MS))
        // A clock that went backwards is not "recent".
        assertFalse(ShortcutTrackMemory.isRecent("memory-test-0", 0))
        for (index in 1..10) ShortcutTrackMemory.keep("memory-test-$index", listed, nowMs = 2_000)
        assertNull("the oldest is dropped", ShortcutTrackMemory.recall("memory-test-0"))
        assertSame(listed, ShortcutTrackMemory.recall("memory-test-10"))
    }
}
