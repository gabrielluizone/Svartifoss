package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueShortcutLinkTest {
    @Test
    fun `web links are offered`() {
        assertEquals(
                "https://music.youtube.com/watch?v=track",
                QueueShortcutLink.candidate(" https://music.youtube.com/watch?v=track "))
    }

    @Test
    fun `provider app schemes are offered`() {
        assertEquals("spotify:track:abc", QueueShortcutLink.candidate("spotify:track:abc"))
    }

    @Test
    fun `local and executable schemes are not offered`() {
        assertNull(QueueShortcutLink.candidate("content://media/external/audio/media/42"))
        assertNull(QueueShortcutLink.candidate("file:///sdcard/song.mp3"))
        assertNull(QueueShortcutLink.candidate("javascript:alert(1)"))
    }

    @Test
    fun `web links need a host`() {
        assertNull(QueueShortcutLink.candidate("https:///track"))
    }

    @Test
    fun `youtube search fallback combines title and artist`() {
        assertEquals(
                "https://music.youtube.com/search?q=Fly%20Away%20From%20This%20World%20Blade%20%26%20Bath",
                QueueShortcutLink.youtubeMusicSearch("Fly Away From This World", "Blade & Bath"))
    }

    @Test
    fun `youtube search fallback needs at least one label`() {
        assertNull(QueueShortcutLink.youtubeMusicSearch(" ", null))
    }
}
