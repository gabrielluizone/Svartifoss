package com.svartifoss.snfell.common

import android.content.SharedPreferences

/**
 * Repairs the fixed-size artist value written by builds from before Matejdro restored the
 * original WearMusicCenter auto-sizing.
 *
 * Changing the face default is not enough for an existing installation: an explicit
 * `wear_artist_text_mode@matejdro=static` wins over every default forever. The marker makes this a
 * one-time correction, so choosing Static deliberately after the repaired build remains a valid
 * user choice. It is run on both devices because either APK can be updated/tested independently.
 */
object MatejdroArtistAutosizeMigration {

    const val MARKER_KEY = "matejdro_artist_autosize_repaired_v1"

    private const val FACE = "matejdro"

    val targetKey: String
        get() = FaceScopedPreferences.scopedKey(
                MiscPreferences.WEAR_ARTIST_TEXT_MODE.key,
                FACE)

    /** Returns the replacement only for the stale value this migration introduced. */
    fun replacementFor(storedValue: String?, alreadyHandled: Boolean): String? =
            if (!alreadyHandled && storedValue == "static") TitleTextMode.SMART else null

    /**
     * Applies the repair and returns true only when the artist mode itself changed. Preference
     * writes update SharedPreferences memory synchronously, so callers may resolve the face
     * immediately after this returns even though disk persistence uses [SharedPreferences.Editor.apply].
     */
    fun repair(preferences: SharedPreferences): Boolean {
        val alreadyHandled = try {
            preferences.getBoolean(MARKER_KEY, false)
        } catch (_: ClassCastException) {
            false
        }
        if (alreadyHandled) return false

        val storedValue = try {
            preferences.getString(targetKey, null)
        } catch (_: ClassCastException) {
            null
        }
        val replacement = replacementFor(storedValue, alreadyHandled)

        preferences.edit().apply {
            putBoolean(MARKER_KEY, true)
            if (replacement != null) putString(targetKey, replacement)
        }.apply()
        return replacement != null
    }
}
