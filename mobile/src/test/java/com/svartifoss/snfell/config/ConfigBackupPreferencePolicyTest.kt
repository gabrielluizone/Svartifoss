package com.svartifoss.snfell.config

import com.svartifoss.snfell.common.MiscPreferences
import org.junit.Assert.assertFalse
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
    fun `does not move local consent or transient state to another phone`() {
        assertFalse(ConfigBackupPreferencePolicy.shouldExport(
                MiscPreferences.CRASH_REPORTING_ENABLED.key, false))
        assertFalse(ConfigBackupPreferencePolicy.shouldExport(
                MiscPreferences.ANNOUNCEMENTS_ENABLED.key, false))
        assertFalse(ConfigBackupPreferencePolicy.shouldExport("notification_access_prompted", true))
        assertFalse(ConfigBackupPreferencePolicy.shouldExport("update_last_check_ms", 1L))
        assertFalse(ConfigBackupPreferencePolicy.shouldExport("current_accent_color", 0xff000000.toInt()))
    }
}
