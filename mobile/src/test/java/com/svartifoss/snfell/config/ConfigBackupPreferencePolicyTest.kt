package com.svartifoss.snfell.config

import com.svartifoss.snfell.common.MiscPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigBackupPreferencePolicyTest {

    @Test
    fun `keeps all supported configuration value types`() {
        assertTrue(ConfigBackupPreferencePolicy.shouldExport("enabled", true))
        assertTrue(ConfigBackupPreferencePolicy.shouldExport("name", "serif"))
        assertTrue(ConfigBackupPreferencePolicy.shouldExport("size", 42))
        assertTrue(ConfigBackupPreferencePolicy.shouldExport("timestamp", 42L))
        assertTrue(ConfigBackupPreferencePolicy.shouldExport("scale", 1.25f))
        assertTrue(ConfigBackupPreferencePolicy.shouldExport("apps", setOf("one", "two")))
    }

    @Test
    fun `keeps local consent and current app state when selected`() {
        assertTrue(ConfigBackupPreferencePolicy.shouldExport(
                MiscPreferences.CRASH_REPORTING_ENABLED.key, false))
        assertTrue(ConfigBackupPreferencePolicy.shouldExport(
                MiscPreferences.ANNOUNCEMENTS_ENABLED.key, false))
        assertTrue(ConfigBackupPreferencePolicy.shouldExport("notification_access_prompted", true))
        assertTrue(ConfigBackupPreferencePolicy.shouldExport("update_last_check_ms", 1L))
        assertTrue(ConfigBackupPreferencePolicy.shouldExport("current_accent_color", 0xff000000.toInt()))
    }

    @Test
    fun `assigns every state family to the expected backup section`() {
        assertEquals(ConfigBackupSection.PLAYLIST_SHORTCUTS,
                ConfigBackup.sectionForPreference("playlist_shortcuts"))
        assertEquals(ConfigBackupSection.HISTORY,
                ConfigBackup.sectionForPreference("track_history"))
        assertEquals(ConfigBackupSection.WATCH_APPEARANCE,
                ConfigBackup.sectionForPreference("wear_normal_color@expressive"))
        assertEquals(ConfigBackupSection.WATCH_APPEARANCE,
                ConfigBackup.sectionForPreference("wear_screen_face"))
        assertEquals(ConfigBackupSection.APP_SETTINGS,
                ConfigBackup.sectionForPreference("custom_accent_color"))
        assertEquals(ConfigBackupSection.APP_SETTINGS,
                ConfigBackup.sectionForPreference("notification_timeout"))
        assertEquals(ConfigBackupSection.PRIVACY,
                ConfigBackup.sectionForPreference("crash_reporting_enabled"))
        assertEquals(ConfigBackupSection.LOCAL_APP_STATE,
                ConfigBackup.sectionForPreference("current_accent_color"))
    }
}
