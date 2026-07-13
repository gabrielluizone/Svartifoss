package com.svartifoss.snfell.config

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.util.BundleFileSerialization
import com.svartifoss.snfell.util.BundleJson
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
    private const val SCHEMA_VERSION = 2

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
        editor.apply()
    }
}
