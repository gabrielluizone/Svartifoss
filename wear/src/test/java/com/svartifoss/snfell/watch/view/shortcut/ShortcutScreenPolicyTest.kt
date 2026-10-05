package com.svartifoss.snfell.watch.view.shortcut

import com.svartifoss.snfell.common.CustomLists
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins when picking a streaming shortcut opens its Play and Shuffle screen. Every `false` here is a
 * pick that starts at once, including an individual track that has no Shuffle choice to show.
 */
class ShortcutScreenPolicyTest {

    private val link = "com.google.android.apps.youtube.music|https://music.youtube.com/watch?list=PL1"

    @Test
    fun `a shortcut picked from its list opens its screen`() {
        assertTrue(ShortcutScreenPolicy.opensForListEntry(
                enabled = true, listId = CustomLists.PLAYLIST_SHORTCUTS, entryId = link,
                phoneKnowsPlayModes = true, shuffleable = true))
    }

    @Test
    fun `switched off, every pick starts at once`() {
        assertFalse(ShortcutScreenPolicy.opensForListEntry(
                enabled = false, listId = CustomLists.PLAYLIST_SHORTCUTS, entryId = link,
                phoneKnowsPlayModes = true, shuffleable = true))
        assertFalse(ShortcutScreenPolicy.opensForMenuAction(
                enabled = false, remoteUri = link, shortcutSubtitle = "YouTube Music • Playlist",
                shortcutShuffleable = true))
    }

    @Test
    fun `a phone that cannot honour a play mode keeps the one-tap start`() {
        // The screen would cost a tap to offer a choice the phone then ignores.
        assertFalse(ShortcutScreenPolicy.opensForListEntry(
                enabled = true, listId = CustomLists.PLAYLIST_SHORTCUTS, entryId = link,
                phoneKnowsPlayModes = false, shuffleable = true))
        assertFalse(ShortcutScreenPolicy.opensForMenuAction(
                enabled = true, remoteUri = link, shortcutSubtitle = null,
                shortcutShuffleable = true))
    }

    @Test
    fun `an unshuffleable shortcut starts at once`() {
        assertFalse(ShortcutScreenPolicy.opensForListEntry(
                enabled = true, listId = CustomLists.PLAYLIST_SHORTCUTS, entryId = link,
                phoneKnowsPlayModes = true, shuffleable = false))
        assertFalse(ShortcutScreenPolicy.opensForMenuAction(
                enabled = true, remoteUri = link, shortcutSubtitle = "YouTube Music • Track",
                shortcutShuffleable = false))
    }

    @Test
    fun `nothing but a real shortcut opens it`() {
        assertFalse(ShortcutScreenPolicy.opensForListEntry(
                enabled = true, listId = CustomLists.PLAYLIST_SHORTCUTS,
                entryId = CustomLists.SPECIAL_ITEM_ERROR, phoneKnowsPlayModes = true,
                shuffleable = true))
        assertFalse(ShortcutScreenPolicy.opensForListEntry(
                enabled = true, listId = CustomLists.PLAYLIST_SHORTCUTS, entryId = " ",
                phoneKnowsPlayModes = true, shuffleable = true))
        listOf(CustomLists.PLAYLIST, CustomLists.LIBRARY, CustomLists.SEARCH_RESULTS,
                CustomLists.SEARCH_HISTORY, CustomLists.HISTORY).forEach { listId ->
            assertFalse(listId, ShortcutScreenPolicy.opensForListEntry(
                    enabled = true, listId = listId, entryId = link, phoneKnowsPlayModes = true,
                    shuffleable = true))
        }
    }

    @Test
    fun `a menu entry needs both its description and a link to play`() {
        assertTrue(ShortcutScreenPolicy.opensForMenuAction(
                enabled = true, remoteUri = link, shortcutSubtitle = "YouTube Music • Playlist",
                shortcutShuffleable = true))
        // An empty subtitle is still a description: a link whose service and kind the phone could
        // not name is still a shortcut.
        assertTrue(ShortcutScreenPolicy.opensForMenuAction(
                enabled = true, remoteUri = link, shortcutSubtitle = "", shortcutShuffleable = true))
        assertFalse(ShortcutScreenPolicy.opensForMenuAction(
                enabled = true, remoteUri = null, shortcutSubtitle = "YouTube Music • Playlist",
                shortcutShuffleable = true))
        assertFalse(ShortcutScreenPolicy.opensForMenuAction(
                enabled = true, remoteUri = "  ", shortcutSubtitle = "YouTube Music • Playlist",
                shortcutShuffleable = true))
    }
}
