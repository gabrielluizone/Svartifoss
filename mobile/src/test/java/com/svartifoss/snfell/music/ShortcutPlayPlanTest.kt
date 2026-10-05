package com.svartifoss.snfell.music

import com.svartifoss.snfell.proto.ShortcutPlayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins how a shortcut started from its watch screen asks for Play or Shuffle.
 *
 * The two mechanisms must never both fire for one link: a YouTube Music playlist shuffles through
 * its link, and a session shuffle request sent on top of that is a second, competing instruction to
 * an app known not to honour it reliably. And AS_SAVED - every button, gesture and older watch -
 * must change nothing at all, or a link saved shuffled would stop shuffling from the button it was
 * assigned to.
 */
class ShortcutPlayPlanTest {

    private val ytmPlaylist = "https://music.youtube.com/watch?list=PL123"
    private val ytmPlaylistShuffled = "https://music.youtube.com/watch?list=PL123&shuffle=true"
    private val spotifyPlaylist = "spotify:playlist:abc"

    @Test
    fun `as saved hands the link over untouched and leaves the session alone`() {
        listOf(ytmPlaylist, ytmPlaylistShuffled, spotifyPlaylist,
                "https://www.deezer.com/album/1").forEach { link ->
            val plan = StreamingShortcutLinks.planFor(link, ShortcutPlayMode.AS_SAVED)
            assertEquals(link, plan.link)
            assertNull(link, plan.sessionShuffle)
        }
    }

    @Test
    fun `a youtube music playlist shuffles through its link and never through the session`() {
        val shuffle = StreamingShortcutLinks.planFor(ytmPlaylist, ShortcutPlayMode.SHUFFLE)
        assertTrue(StreamingShortcutLinks.hasShuffle(shuffle.link))
        assertNull(shuffle.sessionShuffle)

        val inOrder = StreamingShortcutLinks.planFor(ytmPlaylistShuffled, ShortcutPlayMode.IN_ORDER)
        assertFalse(StreamingShortcutLinks.hasShuffle(inOrder.link))
        assertNull(inOrder.sessionShuffle)
    }

    @Test
    fun `shuffling a youtube music playlist twice does not stack the flag`() {
        val plan = StreamingShortcutLinks.planFor(ytmPlaylistShuffled, ShortcutPlayMode.SHUFFLE)
        assertEquals(1, Regex("shuffle=true").findAll(plan.link).count())
    }

    @Test
    fun `every other link keeps its address and asks the session instead`() {
        val shuffle = StreamingShortcutLinks.planFor(spotifyPlaylist, ShortcutPlayMode.SHUFFLE)
        assertEquals(spotifyPlaylist, shuffle.link)
        assertEquals(true, shuffle.sessionShuffle)

        // In order is an active request, not the absence of one: most players keep the mode across
        // queues, so after one Shuffle every later Play would shuffle too.
        val inOrder = StreamingShortcutLinks.planFor(spotifyPlaylist, ShortcutPlayMode.IN_ORDER)
        assertEquals(spotifyPlaylist, inOrder.link)
        assertEquals(false, inOrder.sessionShuffle)
    }

    @Test
    fun `a youtube music album has no link flag, so it is asked through the session`() {
        val album = "https://music.youtube.com/browse/MPREb_abc"
        val plan = StreamingShortcutLinks.planFor(album, ShortcutPlayMode.SHUFFLE)
        assertEquals(album, plan.link)
        assertEquals(true, plan.sessionShuffle)
    }

    @Test
    fun `shuffle is offered for collections and unknown links, never for single items or streams`() {
        val offered = StreamingContentType.values().filter { it.offersShuffle }.toSet()
        assertEquals(
                setOf(StreamingContentType.PLAYLIST, StreamingContentType.ALBUM,
                        StreamingContentType.ARTIST, StreamingContentType.UNKNOWN),
                offered)
    }
}
