package com.svartifoss.snfell.config

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.svartifoss.snfell.common.FaceScopedPreferences
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.ThemeAppearance
import com.svartifoss.snfell.util.BundleFileSerialization
import com.svartifoss.snfell.util.BundleJson
import com.svartifoss.snfell.view.watchface.theme.WatchThemeRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Exports/imports the button configs (playing + stopped), the action list and the
 * [MiscPreferences.EXPORTABLE] watch-behavior settings as a single JSON document - everything
 * that defines "how the app/watch behaves", as opposed to personal runtime data (search/track
 * history, playlist shortcuts), which is intentionally left out.
 *
 * The button-config and action-list files are [android.os.PersistableBundle]s on disk
 * (see [com.svartifoss.snfell.config.buttons.DiskButtonConfigStorage] /
 * [com.svartifoss.snfell.config.actionlist.DiskActionListStorage]). Since schema 2 they're
 * exported as a **portable** [BundleJson] tree and rebuilt (re-marshalled) on the importing
 * device, which is decodable on any Android version. Schema-1 backups stored them as raw base64
 * `Parcel.marshall()` bytes, whose layout is not stable across OS versions - those still import
 * (with a decodability check) so old backups keep working, but new exports never produce them.
 */
object ConfigBackup {
    private const val SCHEMA_VERSION = 3
    private const val WATCH_THEMES_KEY = "watchThemes"

    private val CONFIG_FILES = mapOf(
            "buttonConfigPlaying" to "action_config_playing",
            "buttonConfigStopped" to "action_config_stopped",
            "actionList" to "actions_list"
    )

    fun export(context: Context, preferences: SharedPreferences): JSONObject {
        val json = JSONObject()
        json.put("schemaVersion", SCHEMA_VERSION)
        json.put("exportedAt", System.currentTimeMillis())

        for ((jsonKey, fileName) in CONFIG_FILES) {
            val file = File(context.filesDir, fileName)
            // Read + re-encode as a portable JSON tree. readFromFile force-unparcels the top level;
            // BundleJson.toJson then walks (and forces) the nested bundles too. On the device that
            // wrote the config this always succeeds; a config that can't be read is simply omitted.
            val bundle = if (file.exists()) BundleFileSerialization.readFromFile(file) else null
            if (bundle != null) {
                json.put(jsonKey, BundleJson.toJson(bundle))
            }
        }

        val prefsJson = JSONObject()
        val allPrefs = preferences.all
        val exportableKeys = MiscPreferences.EXPORTABLE.map { it.key }.toSet()
        for (definition in MiscPreferences.EXPORTABLE) {
            when (val value = allPrefs[definition.key]) {
                is Boolean -> prefsJson.put(definition.key, value)
                is String -> prefsJson.put(definition.key, value)
                is Set<*> -> prefsJson.put(definition.key, JSONArray(value.toList()))
            }
        }
        // Also export the per-face variants ("<baseKey>@<face>") of every scoped exportable key so
        // each face's appearance survives a reinstall, not just the global fallback.
        for ((key, value) in allPrefs) {
            if (!isExportableScopedKey(key, exportableKeys)) continue
            when (value) {
                is Boolean -> prefsJson.put(key, value)
                is String -> prefsJson.put(key, value)
                is Set<*> -> prefsJson.put(key, JSONArray(value.toList()))
            }
        }
        json.put("preferences", prefsJson)
        // The full theme library intentionally lives outside default SharedPreferences so it is
        // never mirrored wholesale to Wear. Export it explicitly; the repository first captures
        // any edits made to the active custom snapshot in the shared Watch editor.
        json.put(WATCH_THEMES_KEY, WatchThemeRepository(context).exportToJson(preferences))

        return json
    }

    /** Writes the config files + preferences to disk. The app must be restarted afterwards for
     *  the already-loaded in-memory config singletons to pick up the new files.
     *
     *  Portable (schema 2, [BundleJson]) config entries are rebuilt into a locally-marshalled
     *  parcel, so they always apply. Legacy base64 blobs are validated for decodability first.
     *  Either way, everything is fully prepared *before* the first write, so a failed import
     *  (a malformed entry, or a legacy blob this Android version can't decode) never touches the
     *  existing on-disk config.
     *
     *  @throws java.io.IOException when a legacy config blob in the backup can't be decoded on this
     *  device (parcel bytes aren't stable across Android versions). */
    fun import(context: Context, preferences: SharedPreferences, json: JSONObject) {
        // Validate the phone-only theme catalog before the first config/preference write. This
        // preserves the all-or-nothing guarantee for malformed or future-schema backups.
        val themeRepository = WatchThemeRepository(context)
        val themesJson = when {
            !json.has(WATCH_THEMES_KEY) -> null
            json.optJSONObject(WATCH_THEMES_KEY) != null -> json.optJSONObject(WATCH_THEMES_KEY)
            else -> throw IOException("Invalid watch theme library")
        }
        themesJson?.let(themeRepository::validateImport)

        val pendingWrites = ArrayList<() -> Unit>()
        for ((jsonKey, fileName) in CONFIG_FILES) {
            if (!json.has(jsonKey)) continue
            val target = File(context.filesDir, fileName)
            when (val entry = json.get(jsonKey)) {
                is JSONObject -> {
                    // Portable format: rebuild the bundle and write it as a parcel marshalled by
                    // this device - guaranteed decodable here regardless of where it was exported.
                    val bundle = BundleJson.fromJson(entry)
                    pendingWrites.add { BundleFileSerialization.writeToFile(bundle, target) }
                }
                is String -> {
                    // Legacy format: raw parcel bytes. Validate before committing to any write.
                    val bytes = Base64.decode(entry, Base64.NO_WRAP)
                    if (!BundleFileSerialization.isDecodable(bytes)) {
                        throw IOException(
                                "Backup blob '$jsonKey' is not decodable on this device " +
                                        "(made on an incompatible Android version?)"
                        )
                    }
                    pendingWrites.add { target.writeBytes(bytes) }
                }
                else -> throw IOException("Unexpected config entry type for '$jsonKey'")
            }
        }
        pendingWrites.forEach { it() }

        val prefsJson = json.optJSONObject("preferences") ?: JSONObject()
        val editor = preferences.edit()
        for (definition in MiscPreferences.EXPORTABLE) {
            if (!prefsJson.has(definition.key)) continue
            when (val value = prefsJson.get(definition.key)) {
                is Boolean -> editor.putBoolean(definition.key, value)
                is String -> editor.putString(definition.key, value)
                is JSONArray -> editor.putStringSet(
                        definition.key,
                        (0 until value.length()).map { value.getString(it) }.toSet()
                )
            }
        }
        // Restore the per-face variants ("<baseKey>@<face>") of scoped exportable keys.
        val exportableKeys = MiscPreferences.EXPORTABLE.map { it.key }.toSet()
        val prefKeys = prefsJson.keys()
        while (prefKeys.hasNext()) {
            val key = prefKeys.next()
            if (!isExportableScopedKey(key, exportableKeys)) continue
            when (val value = prefsJson.get(key)) {
                is Boolean -> editor.putBoolean(key, value)
                is String -> editor.putString(key, value)
                is JSONArray -> editor.putStringSet(
                        key, (0 until value.length()).map { value.getString(it) }.toSet())
            }
        }
        if (themesJson == null) {
            // A schema-1/2 backup cannot describe the custom profile that may currently be
            // active. Deactivate that projection in the same preference transaction; otherwise
            // an imported wear_screen_face would silently rewrite the saved profile's base layout
            // the next time the Watch editor captures it.
            editor.putString(MiscPreferences.WEAR_ACTIVE_CUSTOM_THEME_ID.key, "")
            editor.putString(MiscPreferences.WEAR_CUSTOM_THEME_SCHEMA.key, "0")
            editor.putString(MiscPreferences.WEAR_CUSTOM_THEME_REVISION.key, "0")
            editor.putBoolean(MiscPreferences.WEAR_CUSTOM_THEME_COMPLETE.key, false)
        }
        editor.apply()

        // Schema 1/2 backups have no theme library and retain whatever is already on the phone.
        // Schema 3 replaces the library as one validated unit and re-materializes its active
        // profile into custom_active, or safely returns to the imported built-in face.
        if (themesJson != null) {
            themeRepository.replaceFromJson(themesJson, preferences)
        } else {
            // Keep the phone-only catalog, but clear its stale active marker to match the imported
            // legacy preference state. Saved themes remain available for later use.
            themeRepository.applyBuiltIn(
                    preferences, ThemeAppearance.resolve(preferences).baseFace)
        }
    }

    /** True for a "<baseKey>@<face>" key whose base is a face-scoped exportable preference. */
    private fun isExportableScopedKey(key: String, exportableKeys: Set<String>): Boolean {
        val separator = key.indexOf(FaceScopedPreferences.SCOPE_SEPARATOR)
        if (separator <= 0) return false
        val base = key.substring(0, separator)
        return base in exportableKeys && FaceScopedPreferences.isScoped(base)
    }
}
