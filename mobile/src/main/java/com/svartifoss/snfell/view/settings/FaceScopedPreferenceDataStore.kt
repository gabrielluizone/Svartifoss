package com.svartifoss.snfell.view.settings

import android.content.SharedPreferences
import androidx.preference.PreferenceDataStore
import com.svartifoss.snfell.common.FaceScopedPreferences
import com.svartifoss.snfell.common.MiscPreferences

/**
 * Backs the Watch-tab preference screens so each now-playing face keeps its own appearance config.
 * Appearance keys (see [FaceScopedPreferences.SCOPED_KEYS]) read/write `"<key>@<face>"`, where
 * `<face>` is the current global `wear_screen_face`; every other key (the face selector, dev
 * toggles, ...) passes straight through unscoped.
 *
 * Reads use the shared resolution (scoped -> legacy global -> per-face default -> supplied
 * default) so the UI shows exactly the value the watch will apply. Every write still lands in the
 * same [SharedPreferences] (just under the scoped key), so the existing `PreferencePusher` sync
 * carries them to the watch unchanged.
 */
class FaceScopedPreferenceDataStore(
        private val prefs: SharedPreferences
) : PreferenceDataStore() {

    private val face: String
        get() = prefs.getString(MiscPreferences.WEAR_SCREEN_FACE.key, "classic") ?: "classic"

    private fun effectiveWriteKey(key: String): String =
            if (FaceScopedPreferences.isScoped(key)) FaceScopedPreferences.scopedKey(key, face) else key

    override fun getString(key: String, defValue: String?): String? {
        if (!FaceScopedPreferences.isScoped(key)) return prefs.getString(key, defValue)
        val currentFace = face
        val scoped = FaceScopedPreferences.scopedKey(key, currentFace)
        // See FaceScopedPreferences.getString: the per-face default wins over a pre-existing
        // *global* legacy value, so the UI shows the same resolved value the watch will apply.
        return when {
            prefs.contains(scoped) -> prefs.getString(scoped, defValue)
            else -> FaceScopedPreferences.perFaceDefault(currentFace, key)
                    ?: if (prefs.contains(key)) prefs.getString(key, defValue) else defValue
        }
    }

    override fun putString(key: String, value: String?) {
        val editor = prefs.edit()
        val target = effectiveWriteKey(key)
        if (value == null) editor.remove(target) else editor.putString(target, value)
        editor.apply()
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        if (!FaceScopedPreferences.isScoped(key)) return prefs.getBoolean(key, defValue)
        val currentFace = face
        val scoped = FaceScopedPreferences.scopedKey(key, currentFace)
        return when {
            prefs.contains(scoped) -> prefs.getBoolean(scoped, defValue)
            prefs.contains(key) -> prefs.getBoolean(key, defValue)
            else -> defValue
        }
    }

    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(effectiveWriteKey(key), value).apply()
    }

    // Numeric appearance prefs persist as strings (wearutils convention); resolve them through the
    // string path so scoping and the per-face fallback still apply.
    override fun getInt(key: String, defValue: Int): Int =
            getString(key, null)?.toIntOrNull() ?: defValue

    override fun putInt(key: String, value: Int) = putString(key, value.toString())
}
