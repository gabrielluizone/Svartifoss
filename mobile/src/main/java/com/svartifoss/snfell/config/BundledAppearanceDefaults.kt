package com.svartifoss.snfell.config

import android.content.Context
import android.content.SharedPreferences
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.FaceScopedPreferences
import org.json.JSONObject
import timber.log.Timber

/**
 * The appearance values the app actually ships with, read back out of `R.raw.default_config`.
 *
 * "Default" in this app does not mean the XML `android:defaultValue` of each row: a fresh install
 * seeds `default_config.json` (see `GlobalActionConfig.seedBundledDefaultConfig`), which carries
 * roughly 160 explicit `key@face` entries for every built-in face - the author's own tuned setup.
 * So the defaults a user has ever seen are *these*, and the definition defaults underneath them
 * are a look that has never shipped to anybody.
 *
 * That is what made "Reset this layout" restore a configuration nobody chose. It deleted the
 * shipped `key@face` entries, and resolution then fell through [FaceScopedPreferences]'s remaining
 * steps - a per-face default, a leftover pre-3.0 global, or the definition default - none of which
 * is the face as installed. Worse, it was one-way: once reset, the shipped look could not be got
 * back short of reinstalling or importing a backup.
 *
 * Reset now restores from here and only *removes* a key this document does not carry, which is the
 * honest reading of "back to defaults" in both directions.
 */
object BundledAppearanceDefaults {

    /**
     * Parsed once per process and held: the document is ~420 KB of JSON, and both reset paths
     * (one face, or every face) walk it repeatedly across a single user action.
     *
     * `null` means the read or parse failed. Callers treat that as "no shipped defaults available"
     * and fall back to clearing, which is the pre-fix behaviour - degraded, but never worse than
     * what it replaced. It is cached as an explicit absence too, so a broken resource is not
     * re-parsed on every tap.
     */
    private var cached: Map<String, JSONObject>? = null
    private var parsed = false

    /**
     * Every scoped appearance entry in the shipped document, keyed by its full `key@scope` name.
     *
     * Only keys in [FaceScopedPreferences.SCOPED_KEYS] are kept. The document is an ordinary
     * backup and carries behaviour settings and phone-only state as well, and a reset must not
     * reach any of those - it is scoped to one face's appearance by definition.
     */
    @Synchronized
    private fun scopedEntries(context: Context): Map<String, JSONObject>? {
        if (parsed) return cached
        parsed = true
        cached = try {
            val json = context.resources.openRawResource(R.raw.default_config).use {
                JSONObject(it.readBytes().decodeToString())
            }
            val prefs = json.optJSONObject("preferences")
                    ?: throw IllegalStateException("default_config.json has no preferences block")
            val entries = HashMap<String, JSONObject>()
            val keys = prefs.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val separator = key.indexOf(FaceScopedPreferences.SCOPE_SEPARATOR)
                if (separator <= 0) continue
                if (key.substring(0, separator) !in FaceScopedPreferences.SCOPED_KEYS) continue
                prefs.optJSONObject(key)?.let { entries[key] = it }
            }
            entries
        } catch (e: Exception) {
            // A reset still works without this - it just falls back to the definition defaults, so
            // this is a degraded path and not a crash. WARN keeps it out of Crashlytics as an issue
            // while leaving the cause in the watch/phone log.
            Timber.w(e, "Could not read the bundled appearance defaults")
            null
        }
        return cached
    }

    /**
     * Restores one appearance scope to the shipped defaults, in [editor].
     *
     * Every key in [FaceScopedPreferences.SCOPED_KEYS] is either written from the shipped document
     * or removed, so the scope ends up as a *snapshot* rather than a merge - the same rule
     * `ConfigBackup.clearMissingPreferences` follows, and for the same reason: a value the user
     * added that the shipped setup never had is not part of "defaults" either.
     *
     * Returns false when the shipped document is unavailable, having still removed every key -
     * the caller then reports the same outcome it always did.
     */
    fun restoreScope(context: Context, editor: SharedPreferences.Editor, scope: String): Boolean {
        val entries = scopedEntries(context)
        for (baseKey in FaceScopedPreferences.SCOPED_KEYS) {
            val scopedKey = FaceScopedPreferences.scopedKey(baseKey, scope)
            val shipped = entries?.get(scopedKey)
            if (shipped == null) {
                editor.remove(scopedKey)
            } else {
                try {
                    ConfigBackup.putTypedPreference(editor, scopedKey, shipped)
                } catch (e: Exception) {
                    // One malformed entry must not abandon the rest of the face half-reset.
                    Timber.w(e, "Bundled default for '%s' is unusable", scopedKey)
                    editor.remove(scopedKey)
                }
            }
        }
        return entries != null
    }
}
