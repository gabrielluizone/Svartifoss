package com.svartifoss.snfell.config

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.svartifoss.snfell.common.FaceScopedPreferences
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.ThemeAppearance
import com.svartifoss.snfell.music.PlaylistShortcutStorage
import com.svartifoss.snfell.music.ShortcutArtworkStore
import com.svartifoss.snfell.notifications.AppGlyphStore
import com.svartifoss.snfell.util.BundleFileSerialization
import com.svartifoss.snfell.util.BundleJson
import com.svartifoss.snfell.view.watchface.theme.WatchThemeRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Exports/imports the button configs (playing + stopped), the action list and the
 * complete phone/watch configuration as a single JSON document. It includes every supported
 * default-preference value, saved themes, personal shortcut/history data and the on-disk icon
 * stores those configs reference. Device-local consent and transient bookkeeping are excluded by
 * [ConfigBackupPreferencePolicy].
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
    private const val SCHEMA_VERSION = 5
    private const val WATCH_THEMES_KEY = "watchThemes"
    private const val USER_DATA_KEY = "userData"
    private const val ASSETS_KEY = "assets"
    private const val TYPE_KEY = "type"
    private const val VALUE_KEY = "value"

    private const val MAX_ASSETS_PER_STORE = 200
    private const val MAX_SINGLE_ASSET_BYTES = 4 * 1024 * 1024
    private const val MAX_TOTAL_ASSET_BYTES = 32 * 1024 * 1024

    private val SAFE_ASSET_NAME = Regex("[A-Za-z0-9_.-]{1,240}\\.png")

    private val CONFIG_FILES = mapOf(
            "buttonConfigPlaying" to "action_config_playing",
            "buttonConfigStopped" to "action_config_stopped",
            "actionList" to "actions_list"
    )

    /** Personal runtime data stored as JSON strings in the default preferences: saved streaming
     *  shortcuts, plus search and track history. Included so a reinstall keeps the user's own
     *  library and history (opt-in via the same single backup file). */
    private val USER_DATA_KEYS = listOf(
            "playlist_shortcuts",
            "search_history",
            "track_history"
    )

    private data class AssetStore(
            val jsonKey: String,
            val folder: (Context) -> File,
            val afterRestore: ((Context) -> Unit)? = null)

    private data class BackupAsset(val name: String, val bytes: ByteArray)

    private data class RestoredAssetStore(val store: AssetStore, val assets: List<BackupAsset>)

    private val ASSET_STORES = listOf(
            AssetStore("customIcons", CustomIconStorage::backupDirectory),
            AssetStore("shortcutArtwork", ShortcutArtworkStore::backupDirectory),
            AssetStore("appGlyphs", AppGlyphStore::backupDirectory, AppGlyphStore::markRestored)
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
        // Typed envelopes retain integer/long/float values too. Earlier schemas only supported
        // booleans, strings and sets from EXPORTABLE, which silently dropped phone-only settings.
        for ((key, value) in allPrefs) {
            if (ConfigBackupPreferencePolicy.shouldExport(key, value)) {
                prefsJson.put(key, preferenceToJson(value ?: continue))
            }
        }
        json.put("preferences", prefsJson)
        // The full theme library intentionally lives outside default SharedPreferences so it is
        // never mirrored wholesale to Wear. Export it explicitly; the repository first captures
        // any edits made to the active custom snapshot in the shared Watch editor.
        json.put(WATCH_THEMES_KEY, WatchThemeRepository(context).exportToJson(preferences))

        // Personal runtime data (saved shortcuts, search/track history) as opaque JSON strings.
        val userDataJson = JSONObject()
        for (key in USER_DATA_KEYS) {
            (allPrefs[key] as? String)?.let { userDataJson.put(key, it) }
        }
        json.put(USER_DATA_KEY, userDataJson)
        json.put(ASSETS_KEY, exportAssets(context))

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
        val schemaVersion = schemaVersion(json)
        // Validate the phone-only theme catalog before the first config/preference write. This
        // preserves the all-or-nothing guarantee for malformed or future-schema backups.
        val themeRepository = WatchThemeRepository(context)
        val themesJson = when {
            !json.has(WATCH_THEMES_KEY) -> null
            json.optJSONObject(WATCH_THEMES_KEY) != null -> json.optJSONObject(WATCH_THEMES_KEY)
            else -> throw IOException("Invalid watch theme library")
        }
        themesJson?.let(themeRepository::validateImport)

        // Decode every icon before touching the existing config. A malformed backup therefore
        // cannot leave actions restored with missing custom imagery.
        val restoredAssets = parseAssets(json)
        val prefsJson = json.optJSONObject("preferences") ?: JSONObject()
        if (schemaVersion >= 5) validateTypedPreferences(prefsJson)

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
        restoredAssets.forEach { restored ->
            replaceAssetStore(restored.store.folder(context), restored.assets)
            restored.store.afterRestore?.invoke(context)
        }

        val editor = preferences.edit()
        if (schemaVersion >= 5) {
            restoreTypedPreferences(editor, prefsJson)
        } else {
            restoreLegacyPreferences(editor, prefsJson)
        }
        // Restore personal runtime data (saved shortcuts, search/track history) if present. Older
        // backups (schema <= 3) simply omit it and leave whatever is already on the phone.
        val userDataJson = json.optJSONObject(USER_DATA_KEY)
        if (userDataJson != null) {
            for (key in USER_DATA_KEYS) {
                if (userDataJson.has(key)) editor.putString(key, userDataJson.getString(key))
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

        // Push the restored shortcut library to the watch's dedicated cache so it appears there
        // without waiting for the next manual edit.
        if (userDataJson != null && userDataJson.has("playlist_shortcuts")) {
            PlaylistShortcutStorage.syncToWatch(context)
        }

        // Schema 1/2 backups have no theme library and retain whatever is already on the phone.
        // Schema 3+ replaces the library as one validated unit and re-materializes its active
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

    private fun preferenceToJson(value: Any): JSONObject {
        val entry = JSONObject()
        when (value) {
            is Boolean -> entry.put(TYPE_KEY, "boolean").put(VALUE_KEY, value)
            is String -> entry.put(TYPE_KEY, "string").put(VALUE_KEY, value)
            is Int -> entry.put(TYPE_KEY, "int").put(VALUE_KEY, value)
            is Long -> entry.put(TYPE_KEY, "long").put(VALUE_KEY, value)
            is Float -> entry.put(TYPE_KEY, "float").put(VALUE_KEY, value.toDouble())
            is Set<*> -> entry.put(TYPE_KEY, "stringSet").put(VALUE_KEY, JSONArray(value.toList()))
            else -> throw IllegalArgumentException("Unsupported preference value")
        }
        return entry
    }

    private fun restoreTypedPreferences(editor: SharedPreferences.Editor, prefsJson: JSONObject) {
        val keys = prefsJson.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val entry = prefsJson.optJSONObject(key)
                    ?: throw IOException("Invalid typed preference '$key'")
            if (!ConfigBackupPreferencePolicy.shouldRestore(key)) continue
            when (entry.optString(TYPE_KEY)) {
                "boolean" -> editor.putBoolean(key, entry.getBoolean(VALUE_KEY))
                "string" -> editor.putString(key, entry.getString(VALUE_KEY))
                "int" -> editor.putInt(key, entry.getInt(VALUE_KEY))
                "long" -> editor.putLong(key, entry.getLong(VALUE_KEY))
                "float" -> editor.putFloat(key, entry.getDouble(VALUE_KEY).toFloat())
                "stringSet" -> {
                    val values = entry.optJSONArray(VALUE_KEY)
                            ?: throw IOException("Invalid string set preference '$key'")
                    editor.putStringSet(key, (0 until values.length()).map { values.getString(it) }.toSet())
                }
                else -> throw IOException("Unknown preference type for '$key'")
            }
        }
    }

    private fun validateTypedPreferences(prefsJson: JSONObject) {
        val keys = prefsJson.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val entry = prefsJson.optJSONObject(key)
                    ?: throw IOException("Invalid typed preference '$key'")
            if (!ConfigBackupPreferencePolicy.shouldRestore(key)) continue
            when (entry.optString(TYPE_KEY)) {
                "boolean" -> entry.getBoolean(VALUE_KEY)
                "string" -> entry.getString(VALUE_KEY)
                "int" -> entry.getInt(VALUE_KEY)
                "long" -> entry.getLong(VALUE_KEY)
                "float" -> entry.getDouble(VALUE_KEY)
                "stringSet" -> {
                    val values = entry.optJSONArray(VALUE_KEY)
                            ?: throw IOException("Invalid string set preference '$key'")
                    (0 until values.length()).forEach { values.getString(it) }
                }
                else -> throw IOException("Unknown preference type for '$key'")
            }
        }
    }

    private fun restoreLegacyPreferences(editor: SharedPreferences.Editor, prefsJson: JSONObject) {
        for (definition in MiscPreferences.EXPORTABLE) {
            if (!prefsJson.has(definition.key)) continue
            putLegacyPreference(editor, definition.key, prefsJson.get(definition.key))
        }
        // Restore the per-face variants ("<baseKey>@<face>") of scoped exportable keys.
        val exportableKeys = MiscPreferences.EXPORTABLE.map { it.key }.toSet()
        val prefKeys = prefsJson.keys()
        while (prefKeys.hasNext()) {
            val key = prefKeys.next()
            if (isExportableScopedKey(key, exportableKeys)) {
                putLegacyPreference(editor, key, prefsJson.get(key))
            }
        }
    }

    private fun putLegacyPreference(editor: SharedPreferences.Editor, key: String, value: Any) {
        when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is String -> editor.putString(key, value)
            is JSONArray -> editor.putStringSet(
                    key, (0 until value.length()).map { value.getString(it) }.toSet())
        }
    }

    private fun exportAssets(context: Context): JSONObject {
        val assetsJson = JSONObject()
        var totalBytes = 0L
        for (store in ASSET_STORES) {
            val files = store.folder(context).listFiles()
                    ?.filter { it.isFile && SAFE_ASSET_NAME.matches(it.name) }
                    ?.sortedBy { it.name }
                    ?: emptyList()
            if (files.size > MAX_ASSETS_PER_STORE) {
                throw IOException("Too many ${store.jsonKey} assets to back up")
            }
            val entries = JSONArray()
            for (file in files) {
                val bytes = file.readBytes()
                validateAssetSize(store.jsonKey, bytes.size, totalBytes)
                totalBytes += bytes.size
                entries.put(JSONObject()
                        .put("name", file.name)
                        .put("data", Base64.encodeToString(bytes, Base64.NO_WRAP)))
            }
            assetsJson.put(store.jsonKey, entries)
        }
        return assetsJson
    }

    private fun parseAssets(json: JSONObject): List<RestoredAssetStore> {
        if (!json.has(ASSETS_KEY)) return emptyList()
        val assetsJson = json.optJSONObject(ASSETS_KEY)
                ?: throw IOException("Invalid backup assets")
        var totalBytes = 0L
        return ASSET_STORES.mapNotNull { store ->
            if (!assetsJson.has(store.jsonKey)) return@mapNotNull null
            val entries = assetsJson.optJSONArray(store.jsonKey)
                    ?: throw IOException("Invalid ${store.jsonKey} assets")
            if (entries.length() > MAX_ASSETS_PER_STORE) {
                throw IOException("Too many ${store.jsonKey} assets in backup")
            }
            val names = HashSet<String>()
            val decoded = ArrayList<BackupAsset>(entries.length())
            for (index in 0 until entries.length()) {
                val entry = entries.optJSONObject(index)
                        ?: throw IOException("Invalid ${store.jsonKey} asset")
                val name = entry.optString("name")
                if (!SAFE_ASSET_NAME.matches(name) || !names.add(name)) {
                    throw IOException("Unsafe ${store.jsonKey} asset name")
                }
                val bytes = try {
                    Base64.decode(entry.getString("data"), Base64.NO_WRAP)
                } catch (e: IllegalArgumentException) {
                    throw IOException("Invalid ${store.jsonKey} asset data", e)
                }
                validateAssetSize(store.jsonKey, bytes.size, totalBytes)
                totalBytes += bytes.size
                decoded.add(BackupAsset(name, bytes))
            }
            RestoredAssetStore(store, decoded)
        }
    }

    private fun validateAssetSize(store: String, bytes: Int, totalBefore: Long) {
        if (bytes <= 0 || bytes > MAX_SINGLE_ASSET_BYTES ||
                totalBefore + bytes > MAX_TOTAL_ASSET_BYTES) {
            throw IOException("Invalid $store asset size")
        }
    }

    private fun replaceAssetStore(folder: File, assets: List<BackupAsset>) {
        val parent = folder.parentFile ?: throw IOException("Invalid asset folder")
        val staging = File(parent, ".${folder.name}.backup-stage")
        val previous = File(parent, ".${folder.name}.backup-previous")
        deleteTreeIfPresent(staging)
        deleteTreeIfPresent(previous)
        try {
            if (!staging.mkdirs()) throw IOException("Could not create asset staging folder")
            for (asset in assets) {
                File(staging, asset.name).writeBytes(asset.bytes)
            }
            val hadOriginal = folder.exists()
            if (hadOriginal && !folder.renameTo(previous)) {
                throw IOException("Could not stage existing assets")
            }
            if (!staging.renameTo(folder)) {
                if (hadOriginal) previous.renameTo(folder)
                throw IOException("Could not restore assets")
            }
            deleteTreeIfPresent(previous)
        } finally {
            deleteTreeIfPresent(staging)
        }
    }

    private fun deleteTreeIfPresent(file: File) {
        if (!file.exists()) return
        if (file.isDirectory) {
            file.listFiles()?.forEach(::deleteTreeIfPresent)
        }
        if (!file.delete()) throw IOException("Could not remove temporary backup files")
    }

    private fun schemaVersion(json: JSONObject): Int {
        if (!json.has("schemaVersion")) return 1
        val value = json.opt("schemaVersion") as? Number
                ?: throw IOException("Invalid backup schema version")
        val version = value.toInt()
        if (value.toDouble() != version.toDouble() || version !in 1..SCHEMA_VERSION) {
            throw IOException("Unsupported backup schema version")
        }
        return version
    }

    /** True for a "<baseKey>@<face>" key whose base is a face-scoped exportable preference. */
    private fun isExportableScopedKey(key: String, exportableKeys: Set<String>): Boolean {
        val separator = key.indexOf(FaceScopedPreferences.SCOPE_SEPARATOR)
        if (separator <= 0) return false
        val base = key.substring(0, separator)
        return base in exportableKeys && FaceScopedPreferences.isScoped(base)
    }
}
