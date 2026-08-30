package com.svartifoss.snfell.config

/**
 * Keeps a config backup forward-compatible: every supported value in the app's default preference
 * file is preserved automatically. The user chooses whether even local consent, prompts and
 * one-shot migration/update state should travel with the backup on the selection screen.
 */
internal object ConfigBackupPreferencePolicy {
    fun shouldExport(key: String, value: Any?): Boolean =
            shouldRestore(key) && isSupportedValue(value)

    fun shouldRestore(key: String): Boolean = true

    fun isSupportedValue(value: Any?): Boolean = when (value) {
        is Boolean, is String, is Int, is Long, is Float -> true
        is Set<*> -> value.all { it is String }
        else -> false
    }
}
