package com.svartifoss.snfell.view.settings

/**
 * Keeps Settings Search actionable when a result is disabled by another preference.
 *
 * The resolver never changes stored state. It points at the prerequisite row so users can decide
 * whether to enable the feature, matching the conditional routing used by Watch appearance.
 */
internal object SettingsSearchTargetResolver {

    data class Target(val key: String, val redirected: Boolean)

    fun resolve(
            key: String,
            readString: (key: String, default: String) -> String,
            readBoolean: (key: String, default: Boolean) -> Boolean
    ): Target {
        val prerequisite = when {
            key == "update_include_prereleases" &&
                    !readBoolean("update_check_enabled", true) -> "update_check_enabled"
            key == "desaturated_color" &&
                    !readBoolean("dynamic_accent_color", true) -> "dynamic_accent_color"
            key == "notification_timeout" &&
                    !readBoolean("enable_notification_popup", false) ->
                "enable_notification_popup"
            key == "auto_start_apps_blacklist" &&
                    readString("auto_start_mode", "OFF") == "OFF" -> "auto_start_mode"
            else -> null
        }
        return Target(prerequisite ?: key, prerequisite != null)
    }
}
