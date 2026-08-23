package com.svartifoss.snfell

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchPreferenceSyncCoordinatorTest {

    @Test
    fun `syncs global and face scoped watch settings`() {
        assertTrue(shouldSyncWatchPreference("rotary_seek"))
        assertTrue(shouldSyncWatchPreference("wear_screen_face"))
        assertTrue(shouldSyncWatchPreference("wear_queue_style@material"))
        assertTrue(shouldSyncWatchPreference("album_art_style@custom_active"))
        assertTrue(shouldSyncWatchPreference("wear_dev_show_layout_bounds"))
        assertTrue(shouldSyncWatchPreference("wear_dev_show_player_info"))
    }

    @Test
    fun `ignores phone local and transient preferences`() {
        assertFalse(shouldSyncWatchPreference(null))
        assertFalse(shouldSyncWatchPreference("app_theme"))
        assertFalse(shouldSyncWatchPreference("last_update_check"))
    }

    /**
     * The three personal-data storages live in the phone's *default* preference file, which the
     * DataItem push used to ship to the watch wholesale - the watch reads none of them, and they
     * spent the same 100 KB item budget the watch-facing keys need. Both transports now filter on
     * this predicate, so these must stay out of it.
     */
    @Test
    fun `ignores the phone's own history and shortcut storages`() {
        assertFalse(shouldSyncWatchPreference("track_history"))
        assertFalse(shouldSyncWatchPreference("search_history"))
        assertFalse(shouldSyncWatchPreference("playlist_shortcuts"))
    }
}
