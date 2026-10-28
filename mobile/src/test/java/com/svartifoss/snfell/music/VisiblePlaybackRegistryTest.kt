package com.svartifoss.snfell.music

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Pins the hand-off between `MusicService` and the activity the watch opens.
 *
 * What it guards is mostly about time and about strangers: a request that is claimed late must not
 * start music (a phone unlocked minutes after a tap on the wrist), a link must not work twice, and a
 * link nobody registered must reach nothing - the activity is browsable, so web pages can open it.
 */
class VisiblePlaybackRegistryTest {

    private val play = VisibleLaunch(VisiblePlayTechnique.PLAY_FROM_SEARCH_URI,
            VisiblePlaybackPlan.ACTION_MEDIA_PLAY_FROM_SEARCH, "spotify:playlist:1", "com.spotify.music",
            listOf(VisiblePlaybackPlan.CATEGORY_BROWSABLE), emptyMap())
    private val navigation = VisibleLaunch.navigation("spotify:playlist:1", "com.spotify.music")

    private fun token() = "test-" + UUID.randomUUID()

    @Test
    fun `a fresh request tries the playing launch, then the plain open`() {
        val handle = VisiblePlaybackRegistry.register(play, navigation, nowMs = 1_000, token = token())
        val claim = VisiblePlaybackRegistry.claim(handle.token, nowMs = 2_000)!!
        assertTrue(claim.autoplay)
        assertEquals(listOf(play, navigation), claim.launches)
    }

    @Test
    fun `a late request only opens the content`() {
        val handle = VisiblePlaybackRegistry.register(play, navigation, nowMs = 0, token = token())
        val claim = VisiblePlaybackRegistry.claim(
                handle.token, nowMs = VisiblePlaybackRegistry.AUTOPLAY_WINDOW_MS + 1)!!
        assertFalse(claim.autoplay)
        assertEquals(listOf(navigation), claim.launches)
    }

    @Test
    fun `a request older than the navigation window is gone`() {
        val handle = VisiblePlaybackRegistry.register(play, navigation, nowMs = 0, token = token())
        assertNull(VisiblePlaybackRegistry.claim(
                handle.token, nowMs = VisiblePlaybackRegistry.NAVIGATION_WINDOW_MS + 1))
    }

    @Test
    fun `a link works once`() {
        val handle = VisiblePlaybackRegistry.register(play, navigation, nowMs = 0, token = token())
        assertTrue(VisiblePlaybackRegistry.claim(handle.token, 1) != null)
        assertNull(VisiblePlaybackRegistry.claim(handle.token, 2))
    }

    @Test
    fun `a link nobody registered reaches nothing`() {
        assertNull(VisiblePlaybackRegistry.claim("made-up", 0))
        assertNull(VisiblePlaybackRegistry.claim("", 0))
    }

    @Test
    fun `a superseded request can no longer be claimed`() {
        val handle = VisiblePlaybackRegistry.register(play, navigation, nowMs = 0, token = token())
        VisiblePlaybackRegistry.cancel(handle)
        assertFalse(VisiblePlaybackRegistry.isPending(handle.token))
        assertNull(VisiblePlaybackRegistry.claim(handle.token, 1))
    }

    @Test
    fun `a burst of picks keeps only the newest few`() {
        val handles = (1..6).map { VisiblePlaybackRegistry.register(play, navigation, nowMs = 0, token = token()) }
        assertFalse(VisiblePlaybackRegistry.isPending(handles.first().token))
        assertTrue(VisiblePlaybackRegistry.isPending(handles.last().token))
    }

    @Test
    fun `tokens are not guessable from one another`() {
        val first = VisiblePlaybackRegistry.register(play, navigation, nowMs = 0)
        val second = VisiblePlaybackRegistry.register(play, navigation, nowMs = 0)
        assertNotEquals(first.token, second.token)
        assertTrue(first.token.length >= 32)
    }

    @Test
    fun `the link carries only the token`() {
        assertEquals("svartifoss://play-shortcut?t=abc", VisiblePlaybackRegistry.uriFor("abc"))
    }

    @Test
    fun `the waiter hears which launch started, and whether it was the playing one`() = runBlocking {
        val handle = VisiblePlaybackRegistry.register(play, navigation, nowMs = 0, token = token())
        val waiting = async { VisiblePlaybackRegistry.awaitOutcome(handle) }
        val claim = VisiblePlaybackRegistry.claim(handle.token, 1)!!
        VisiblePlaybackRegistry.reportLaunched(claim, play)
        assertEquals(VisiblePlaybackRegistry.Outcome(play, autoplay = true, fresh = true), waiting.await())
    }

    @Test
    fun `falling back to the plain open is not reported as a play`() = runBlocking {
        val handle = VisiblePlaybackRegistry.register(play, navigation, nowMs = 0, token = token())
        val waiting = async { VisiblePlaybackRegistry.awaitOutcome(handle) }
        val claim = VisiblePlaybackRegistry.claim(handle.token, 1)!!
        VisiblePlaybackRegistry.reportLaunched(claim, navigation)
        assertEquals(VisiblePlaybackRegistry.Outcome(navigation, autoplay = false, fresh = true), waiting.await())
    }

    @Test
    fun `a late open says it was late, so nothing is learned from it`() = runBlocking {
        val handle = VisiblePlaybackRegistry.register(play, navigation, nowMs = 0, token = token())
        val waiting = async { VisiblePlaybackRegistry.awaitOutcome(handle) }
        val claim = VisiblePlaybackRegistry.claim(handle.token, VisiblePlaybackRegistry.AUTOPLAY_WINDOW_MS + 1)!!
        VisiblePlaybackRegistry.reportLaunched(claim, navigation)
        assertEquals(VisiblePlaybackRegistry.Outcome(navigation, autoplay = false, fresh = false), waiting.await())
    }

    @Test
    fun `a waiter on a superseded request gets null, not a crash`() = runBlocking {
        val handle = VisiblePlaybackRegistry.register(play, navigation, nowMs = 0, token = token())
        val waiting = async { VisiblePlaybackRegistry.awaitOutcome(handle) }
        VisiblePlaybackRegistry.cancel(handle)
        assertNull(waiting.await())
    }
}
