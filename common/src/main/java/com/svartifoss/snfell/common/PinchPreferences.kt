package com.svartifoss.snfell.common

import android.content.SharedPreferences
import com.matejdro.wearutils.preferences.definition.PreferenceDefinition
import com.matejdro.wearutils.preferences.definition.Preferences

/** Reads the original integer-valued builds as well as the normal string-valued preferences. */
object PinchPreferences {
    fun readSettings(preferences: SharedPreferences): PinchDetectorSettings {
        val values = preferences.all
        fun read(definition: PreferenceDefinition<Int>): Int = when (val value = values[definition.key]) {
            is Int -> value
            is String -> value.toIntOrNull() ?: definition.defaultValue
            else -> definition.defaultValue
        }
        return PinchDetectorSettings(
                read(MiscPreferences.WEAR_PINCH_SENSITIVITY),
                read(MiscPreferences.WEAR_PINCH_MAX_GAP),
                read(MiscPreferences.WEAR_PINCH_COOLDOWN)
        ).normalized()
    }

    /** Keep the same storage representation as Preferences.getInt and the settings UI. */
    fun writeSettings(editor: SharedPreferences.Editor, settings: PinchDetectorSettings) {
        val normalized = settings.normalized()
        Preferences.putInt(editor, MiscPreferences.WEAR_PINCH_SENSITIVITY, normalized.sensitivityPercent)
        Preferences.putInt(editor, MiscPreferences.WEAR_PINCH_MAX_GAP, normalized.maxPinchGapMs)
        Preferences.putInt(editor, MiscPreferences.WEAR_PINCH_COOLDOWN, normalized.cooldownMs)
    }
}
