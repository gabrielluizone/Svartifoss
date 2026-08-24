package com.svartifoss.snfell.config

import com.svartifoss.snfell.common.MiscPreferences

/**
 * Keeps a config backup forward-compatible: preferences added by the phone app are preserved
 * automatically, while local consent, prompts and one-shot migration/update bookkeeping stay on
 * the device where they belong.
 */
internal object ConfigBackupPreferencePolicy {
    private val excludedKeys = setOf(
            MiscPreferences.AUTO_START.key,
            MiscPreferences.LAST_MENU_DISPLAYED.key,
            MiscPreferences.CRASH_REPORTING_ENABLED.key,
            MiscPreferences.ANNOUNCEMENTS_ENABLED.key,
            "center_long_press_repaired",
            "current_accent_color",
            "notification_access_prompted",
            "face_reset_prompt_handled",
            "update_last_check_ms",
            "update_last_notified_tag",
            "update_last_seen_version_code"
    )

    fun shouldExport(key: String, value: Any?): Boolean =
            shouldRestore(key) && isSupportedValue(value)

    fun shouldRestore(key: String): Boolean = key !in excludedKeys

    fun isSupportedValue(value: Any?): Boolean = when (value) {
        is Boolean, is String, is Int, is Long, is Float -> true
        is Set<*> -> value.all { it is String }
        else -> false
    }
}
