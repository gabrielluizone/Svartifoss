package com.svartifoss.snfell.view.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTargetResolverTest {

    @Test
    fun `disabled dependent rows lead to their prerequisites`() {
        assertRedirect(
                resolve("update_include_prereleases", booleans = mapOf(
                        "update_check_enabled" to false)),
                "update_check_enabled")
        assertRedirect(
                resolve("desaturated_color", booleans = mapOf(
                        "dynamic_accent_color" to false)),
                "dynamic_accent_color")
        assertRedirect(
                resolve("notification_timeout", booleans = mapOf(
                        "enable_notification_popup" to false)),
                "enable_notification_popup")
        assertRedirect(
                resolve("auto_start_apps_blacklist", strings = mapOf(
                        "auto_start_mode" to "OFF")),
                "auto_start_mode")
    }

    @Test
    fun `enabled and independent rows keep their destination`() {
        assertDirect(resolve(
                "update_include_prereleases",
                booleans = mapOf("update_check_enabled" to true)))
        assertDirect(resolve(
                "desaturated_color",
                booleans = mapOf("dynamic_accent_color" to true)))
        assertDirect(resolve(
                "notification_timeout",
                booleans = mapOf("enable_notification_popup" to true)))
        assertDirect(resolve(
                "auto_start_apps_blacklist",
                strings = mapOf("auto_start_mode" to "OPEN_APP")))
        assertDirect(resolve("app_theme"))
    }

    private fun resolve(
            key: String,
            strings: Map<String, String> = emptyMap(),
            booleans: Map<String, Boolean> = emptyMap()
    ) = SettingsSearchTargetResolver.resolve(
            key,
            readString = { preferenceKey, default -> strings[preferenceKey] ?: default },
            readBoolean = { preferenceKey, default -> booleans[preferenceKey] ?: default })

    private fun assertRedirect(actual: SettingsSearchTargetResolver.Target, key: String) {
        assertTrue(actual.redirected)
        assertEquals(key, actual.key)
    }

    private fun assertDirect(actual: SettingsSearchTargetResolver.Target) {
        assertFalse(actual.redirected)
    }
}
