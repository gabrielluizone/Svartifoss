package com.svartifoss.snfell.config

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.svartifoss.snfell.common.MiscPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Exports/imports the button configs (playing + stopped), the action list and the
 * [MiscPreferences.EXPORTABLE] watch-behavior settings as a single JSON document - everything
 * that defines "how the app/watch behaves", as opposed to personal runtime data (search/track
 * history, playlist shortcuts), which is intentionally left out.
 *
 * The button-config and action-list files are opaque [android.os.PersistableBundle] blobs
 * (see [com.svartifoss.snfell.config.buttons.DiskButtonConfigStorage] /
 * [com.svartifoss.snfell.config.actionlist.DiskActionListStorage]) - they're copied as raw
 * base64 bytes rather than re-decoded, so this doesn't need to understand their internal shape.
 */
object ConfigBackup {
    private const val SCHEMA_VERSION = 1

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
            if (file.exists()) {
                json.put(jsonKey, Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))
            }
        }

        val prefsJson = JSONObject()
        val allPrefs = preferences.all
        for (definition in MiscPreferences.EXPORTABLE) {
            when (val value = allPrefs[definition.key]) {
                is Boolean -> prefsJson.put(definition.key, value)
                is String -> prefsJson.put(definition.key, value)
                is Set<*> -> prefsJson.put(definition.key, JSONArray(value.toList()))
            }
        }
        json.put("preferences", prefsJson)

        return json
    }

    /** Writes the config files + preferences to disk. The app must be restarted afterwards for
     *  the already-loaded in-memory config singletons to pick up the new files. */
    fun import(context: Context, preferences: SharedPreferences, json: JSONObject) {
        for ((jsonKey, fileName) in CONFIG_FILES) {
            if (!json.has(jsonKey)) continue
            val bytes = Base64.decode(json.getString(jsonKey), Base64.NO_WRAP)
            File(context.filesDir, fileName).writeBytes(bytes)
        }

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
        editor.apply()
    }
}
