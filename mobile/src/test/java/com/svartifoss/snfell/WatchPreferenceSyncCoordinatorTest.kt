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
        assertFalse(shouldSyncWatchPreference("track_history"))
        assertFalse(shouldSyncWatchPreference("last_update_check"))
    }
}
