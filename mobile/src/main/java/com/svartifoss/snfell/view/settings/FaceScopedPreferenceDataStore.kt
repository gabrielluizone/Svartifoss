package com.svartifoss.snfell.view.settings

import android.content.SharedPreferences
import androidx.preference.PreferenceDataStore
import com.svartifoss.snfell.common.FaceScopedPreferences
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.ThemeAppearance

/**
 * Backs the Watch-tab preference screens so each now-playing face keeps its own appearance config.
 * Appearance keys (see [FaceScopedPreferences.SCOPED_KEYS]) read/write the active appearance
 * namespace. Built-ins retain `"<key>@<face>"`; a saved theme uses the isolated
 * `"<key>@custom_active"` snapshot while `wear_screen_face` continues to select its renderer.
 * Every non-appearance key passes straight through unscoped.
 *
 * Reads mirror the shared built-in/custom fallback rules so the UI shows exactly the value the
 * watch will apply. Every write still lands in the same [SharedPreferences] (just under the
 * resolved scope), so the existing `PreferencePusher` sync carries it to the watch unchanged.
 */
class FaceScopedPreferenceDataStore(
        private val prefs: SharedPreferences
) : PreferenceDataStore() {

    private val context
        get() = ThemeAppearance.resolve(prefs)

    private fun effectiveWriteKey(key: String): String =
            if (FaceScopedPreferences.isScoped(key)) {
                FaceScopedPreferences.scopedKey(key, FaceScopedPreferences.scopeFor(context))
            } else {
                key
            }

    override fun getString(key: String, defValue: String?): String? {
        if (!FaceScopedPreferences.isScoped(key)) return prefs.getString(key, defValue)
        val current = context
        val currentFace = current.baseFace
        val scope = FaceScopedPreferences.scopeFor(current)
        val scoped = FaceScopedPreferences.scopedKey(key, scope)
        // See FaceScopedPreferences.getString: surface defaults normally win over legacy globals;
        // album-art style alone preserves a non-default pre-preset choice until this face is saved.
        return when {
            prefs.contains(scoped) -> prefs.getString(scoped, defValue)
            scope == ThemeAppearance.CUSTOM_SCOPE ->
                FaceScopedPreferences.perFaceDefault(currentFace, key) ?: defValue
            key == MiscPreferences.ALBUM_ART_STYLE.key &&
                    FaceScopedPreferences.hasLegacyAlbumArtOverride(prefs) ->
                prefs.getString(key, defValue)
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
        val current = context
        val currentFace = current.baseFace
        val scope = FaceScopedPreferences.scopeFor(current)
        val scoped = FaceScopedPreferences.scopedKey(key, scope)
        // perFaceDefault has to be consulted here exactly as the watch consults it, or the settings
        // screen shows a switch in one position while the watch obeys the other. That is what
        // happened to Carousel's edge arc: the watch had it off by face default while this screen
        // reported the XML default of on, so it looked like the default had simply been ignored.
        return when {
            prefs.contains(scoped) -> prefs.getBoolean(scoped, defValue)
            // The custom scope consults perFaceDefault for the same reason the built-in branch
            // below does, and against the same face: FaceScopedPreferences.getBoolean's Custom
            // branch falls back to the base face's default, so skipping it here showed the switch
            // in one position while the watch obeyed the other - the getString twin above has
            // always had this step.
            scope == ThemeAppearance.CUSTOM_SCOPE ->
                FaceScopedPreferences.perFaceDefault(currentFace, key)?.toBooleanStrictOrNull()
                        ?: defValue
            // currentFace, not scope: for a built-in they are the same string, but passing the
            // scope was only accidentally right and would look up "custom_active" as a face name.
            else -> FaceScopedPreferences.perFaceDefault(currentFace, key)?.toBooleanStrictOrNull()
                    ?: if (prefs.contains(key)) prefs.getBoolean(key, defValue) else defValue
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
