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
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * User-facing groups of state that can be included in a backup.
 *
 * Keep this list about persisted state, rather than individual preference keys. New preference
 * keys automatically follow the group selected by [ConfigBackup.sectionForPreference], so adding a
 * setting does not silently make the backup screen incomplete.
 */
enum class ConfigBackupSection(val id: String) {
    BUTTONS("buttons"),
    ACTIONS("actions"),
    APP_SETTINGS("appSettings"),
    WATCH_APPEARANCE("watchAppearance"),
    PLAYLIST_SHORTCUTS("playlistShortcuts"),
    HISTORY("history"),
    ICONS("icons"),
    PRIVACY("privacy"),
    LOCAL_APP_STATE("localAppState"),
    AUXILIARY_DATA("auxiliaryData");

    companion object {
        val ALL: Set<ConfigBackupSection> =
                values().toCollection(LinkedHashSet())

        fun fromId(id: String): ConfigBackupSection? =
                values().firstOrNull { it.id == id }
    }
}

/**
 * Exports/imports the button configs (playing + stopped), the action list and the
 * complete phone/watch configuration as a single JSON document. It includes every supported
 * default-preference value, saved themes, personal shortcut/history data and the on-disk icon
 * stores those configs reference. Every supported preference is preserved, including local app
 * state and consent; the selection screen lets the user decide whether those parts travel too.
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
    private const val SCHEMA_VERSION = 7
    private const val INCLUDED_SECTIONS_KEY = "includedSections"
    private const val WATCH_THEMES_KEY = "watchThemes"
    private const val USER_DATA_KEY = "userData"
    private const val ASSETS_KEY = "assets"
    private const val NAMED_PREFERENCES_KEY = "namedPreferences"
    private const val INTERNAL_FILES_KEY = "internalFiles"
    private const val TYPE_KEY = "type"
    private const val VALUE_KEY = "value"

    private const val MAX_ASSETS_PER_STORE = 200
    private const val MAX_SINGLE_ASSET_BYTES = 4 * 1024 * 1024
    private const val MAX_TOTAL_ASSET_BYTES = 32 * 1024 * 1024
    private const val MAX_INTERNAL_FILES = 500
    private const val MAX_SINGLE_INTERNAL_FILE_BYTES = 8 * 1024 * 1024
    private const val MAX_TOTAL_INTERNAL_FILE_BYTES = 64 * 1024 * 1024

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

    private val WATCH_APPEARANCE_GLOBAL_KEYS = setOf(
            MiscPreferences.WEAR_SCREEN_FACE.key,
            MiscPreferences.WEAR_ACTIVE_CUSTOM_THEME_ID.key,
            MiscPreferences.WEAR_AVAILABLE_CUSTOM_THEMES.key,
            MiscPreferences.WEAR_CUSTOM_THEME_SCHEMA.key,
            MiscPreferences.WEAR_CUSTOM_THEME_COMPLETE.key,
            MiscPreferences.WEAR_CUSTOM_THEME_REVISION.key
    )

    private val PRIVACY_KEYS = setOf(
            MiscPreferences.CRASH_REPORTING_ENABLED.key,
            MiscPreferences.ANNOUNCEMENTS_ENABLED.key
    )

    /** State which changes the phone's current presentation or records a completed migration.
     * It is intentionally selectable: a user moving to a new phone may want a clean first-run
     * experience, while a device clone should preserve it. */
    private val LOCAL_APP_STATE_KEYS = setOf(
            "current_accent_color",
            MiscPreferences.LAST_MENU_DISPLAYED.key,
            "center_long_press_repaired",
            "shortcut_artwork_store_repaired",
            "notification_access_prompted",
            "face_reset_prompt_handled",
            "update_last_check_ms",
            "update_last_notified_tag",
            "update_last_seen_version_code"
    )

    private data class AssetStore(
            val jsonKey: String,
            val folder: (Context) -> File,
            val afterRestore: ((Context) -> Unit)? = null)

    private data class BackupAsset(val name: String, val bytes: ByteArray)

    private data class RestoredAssetStore(val store: AssetStore, val assets: List<BackupAsset>)

    private data class NamedPreferenceStore(
            val name: String,
            val preferences: SharedPreferences
    )

    private data class RestoredNamedPreferenceStore(
            val name: String,
            val values: Map<String, JSONObject>
    )

    private data class BackupInternalFile(val path: String, val bytes: ByteArray)

    private val ASSET_STORES = listOf(
            AssetStore("customIcons", CustomIconStorage::backupDirectory),
            AssetStore("shortcutArtwork", ShortcutArtworkStore::backupDirectory),
            AssetStore("appGlyphs", AppGlyphStore::backupDirectory, AppGlyphStore::markRestored)
    )

    /** Small named preference files that carry user-facing auxiliary state. Runtime counters for
     * caches and the Wear message sequence are deliberately not part of the backup. */
    private fun namedPreferenceStores(context: Context): List<NamedPreferenceStore> = listOf(
            NamedPreferenceStore(
                    "custom_icon_storage",
                    context.getSharedPreferences("custom_icon_storage", Context.MODE_PRIVATE)),
            NamedPreferenceStore(
                    "community_theme_submission",
                    context.getSharedPreferences("community_theme_submission", Context.MODE_PRIVATE))
    )

    fun export(
            context: Context,
            preferences: SharedPreferences,
            sections: Set<ConfigBackupSection> = ConfigBackupSection.ALL
    ): JSONObject {
        val selectedSections = sections.toSet()
        val json = JSONObject()
        json.put("schemaVersion", SCHEMA_VERSION)
        json.put("exportedAt", System.currentTimeMillis())
        json.put(INCLUDED_SECTIONS_KEY, JSONArray().apply {
            ConfigBackupSection.values()
                    .filter { it in selectedSections }
                    .forEach { put(it.id) }
        })

        for ((jsonKey, fileName) in CONFIG_FILES) {
            val section = if (jsonKey == "actionList") {
                ConfigBackupSection.ACTIONS
            } else {
                ConfigBackupSection.BUTTONS
            }
            if (section !in selectedSections) continue
            val file = File(context.filesDir, fileName)
            // Read + re-encode as a portable JSON tree. readFromFile force-unparcels the top level;
            // BundleJson.toJson then walks (and forces) the nested bundles too. On the device that
            // wrote the config this always succeeds. An explicit null records a missing file so a
            // complete restore can also remove a stale file from the destination device.
            val bundle = if (file.exists()) BundleFileSerialization.readFromFile(file) else null
            if (bundle != null) {
                json.put(jsonKey, BundleJson.toJson(bundle))
            } else {
                // Schema 7 records an explicit absence too, so importing a complete snapshot can
                // remove a stale config file from the destination device.
                json.put(jsonKey, JSONObject.NULL)
            }
        }

        val prefsJson = JSONObject()
        val allPrefs = preferences.all
        // Typed envelopes retain integer/long/float values too. Earlier schemas only supported
        // booleans, strings and sets from EXPORTABLE, which silently dropped phone-only settings.
        for ((key, value) in allPrefs) {
            if (ConfigBackupPreferencePolicy.shouldExport(key, value) &&
                    sectionForPreference(key) in selectedSections) {
                prefsJson.put(key, preferenceToJson(value ?: continue))
            }
        }
        json.put("preferences", prefsJson)

        if (ConfigBackupSection.WATCH_APPEARANCE in selectedSections) {
            // The full theme library intentionally lives outside default SharedPreferences so it
            // is never mirrored wholesale to Wear. Export it explicitly; the repository first
            // captures any edits made to the active custom snapshot in the shared Watch editor.
            json.put(WATCH_THEMES_KEY, WatchThemeRepository(context).exportToJson(preferences))
        }

        // Personal runtime data (saved shortcuts, search/track history) as opaque JSON strings.
        val userDataJson = JSONObject()
        for (key in USER_DATA_KEYS) {
            val section = sectionForPreference(key)
            if (section in selectedSections) {
                (allPrefs[key] as? String)?.let { userDataJson.put(key, it) }
            }
        }
        if (userDataJson.length() > 0) {
            json.put(USER_DATA_KEY, userDataJson)
        }
        if (ConfigBackupSection.ICONS in selectedSections) {
            json.put(ASSETS_KEY, exportAssets(context))
        }
        if (ConfigBackupSection.AUXILIARY_DATA in selectedSections) {
            json.put(NAMED_PREFERENCES_KEY, exportNamedPreferences(context))
            json.put(INTERNAL_FILES_KEY, exportInternalFiles(context))
        }

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
    fun import(
            context: Context,
            preferences: SharedPreferences,
            json: JSONObject
    ): Set<ConfigBackupSection> {
        val schemaVersion = schemaVersion(json)
        val selectedSections = includedSections(json)
        val hasExplicitSections = json.has(INCLUDED_SECTIONS_KEY)
        // Validate the phone-only theme catalog before the first config/preference write. This
        // preserves the all-or-nothing guarantee for malformed or future-schema backups.
        val themeRepository = WatchThemeRepository(context)
        val themesJson = if (ConfigBackupSection.WATCH_APPEARANCE !in selectedSections) {
            null
        } else when {
            !json.has(WATCH_THEMES_KEY) && hasExplicitSections && schemaVersion >= SCHEMA_VERSION ->
                throw IOException("Backup is missing the watch theme library")
            !json.has(WATCH_THEMES_KEY) -> null
            json.optJSONObject(WATCH_THEMES_KEY) != null -> json.optJSONObject(WATCH_THEMES_KEY)
            else -> throw IOException("Invalid watch theme library")
        }
        themesJson?.let(themeRepository::validateImport)

        // Decode every icon before touching the existing config. A malformed backup therefore
        // cannot leave actions restored with missing custom imagery.
        if (ConfigBackupSection.ICONS in selectedSections &&
                schemaVersion >= SCHEMA_VERSION && hasExplicitSections && !json.has(ASSETS_KEY)) {
            throw IOException("Backup is missing icon assets")
        }
        val restoredAssets = if (ConfigBackupSection.ICONS in selectedSections) {
            parseAssets(json)
        } else {
            emptyList()
        }
        val restoredNamedPreferences = if (ConfigBackupSection.AUXILIARY_DATA in selectedSections) {
            parseNamedPreferences(context, json)
        } else {
            emptyList()
        }
        val restoredInternalFiles = if (ConfigBackupSection.AUXILIARY_DATA in selectedSections) {
            parseInternalFiles(json)
        } else {
            emptyList()
        }
        val prefsJson = json.optJSONObject("preferences") ?: JSONObject()
        if (schemaVersion >= 5) validateTypedPreferences(prefsJson, selectedSections)

        val pendingWrites = ArrayList<() -> Unit>()
        for ((jsonKey, fileName) in CONFIG_FILES) {
            if (!json.has(jsonKey)) continue
            val section = if (jsonKey == "actionList") {
                ConfigBackupSection.ACTIONS
            } else {
                ConfigBackupSection.BUTTONS
            }
            if (section !in selectedSections) continue
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
                JSONObject.NULL -> pendingWrites.add { target.delete() }
                else -> throw IOException("Unexpected config entry type for '$jsonKey'")
            }
        }
        if (schemaVersion >= SCHEMA_VERSION && hasExplicitSections) {
            val requiredConfigKeys = when {
                ConfigBackupSection.BUTTONS in selectedSections &&
                        ConfigBackupSection.ACTIONS in selectedSections -> CONFIG_FILES.keys
                ConfigBackupSection.BUTTONS in selectedSections ->
                        setOf("buttonConfigPlaying", "buttonConfigStopped")
                ConfigBackupSection.ACTIONS in selectedSections -> setOf("actionList")
                else -> emptySet()
            }
            requiredConfigKeys.forEach { key ->
                if (!json.has(key)) throw IOException("Backup is missing config entry '$key'")
            }
        }
        pendingWrites.forEach { it() }
        restoredAssets.forEach { restored ->
            replaceAssetStore(restored.store.folder(context), restored.assets)
            restored.store.afterRestore?.invoke(context)
        }

        val editor = preferences.edit()
        if (schemaVersion >= 5) {
            restoreTypedPreferences(editor, prefsJson, selectedSections)
        } else {
            restoreLegacyPreferences(editor, prefsJson, selectedSections)
        }
        if (schemaVersion >= SCHEMA_VERSION && hasExplicitSections) {
            clearMissingPreferences(preferences, editor, prefsJson, selectedSections)
        }
        // Restore personal runtime data (saved shortcuts, search/track history) if present. Older
        // backups that do not carry it simply leave whatever is already on the phone.
        val userDataJson = json.optJSONObject(USER_DATA_KEY)
        if (userDataJson != null) {
            for (key in USER_DATA_KEYS) {
                if (userDataJson.has(key) && sectionForPreference(key) in selectedSections) {
                    editor.putString(key, userDataJson.getString(key))
                }
            }
        }
        val legacyBackupWithoutThemeLibrary = !hasExplicitSections && schemaVersion <= 2
        if (ConfigBackupSection.WATCH_APPEARANCE in selectedSections &&
                legacyBackupWithoutThemeLibrary) {
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
        if (userDataJson != null && userDataJson.has("playlist_shortcuts") &&
                ConfigBackupSection.PLAYLIST_SHORTCUTS in selectedSections) {
            PlaylistShortcutStorage.syncToWatch(context)
        }

        // Schema 1/2 backups have no theme library and retain whatever is already on the phone.
        // Schema 3+ replaces the library as one validated unit and re-materializes its active
        // profile into custom_active, or safely returns to the imported built-in face.
        if (themesJson != null) {
            themeRepository.replaceFromJson(themesJson, preferences)
        } else if (legacyBackupWithoutThemeLibrary &&
                ConfigBackupSection.WATCH_APPEARANCE in selectedSections) {
            // Keep the phone-only catalog, but clear its stale active marker to match the imported
            // legacy preference state. Saved themes remain available for later use.
            themeRepository.applyBuiltIn(
                    preferences, ThemeAppearance.resolve(preferences).baseFace)
        }
        if (ConfigBackupSection.AUXILIARY_DATA in selectedSections) {
            restoreNamedPreferences(context, restoredNamedPreferences)
            restoredInternalFiles.forEach { restored ->
                val target = safeInternalFile(context, restored.path)
                target.parentFile?.mkdirs()
                target.writeBytes(restored.bytes)
            }
        }
        return selectedSections
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

    private fun restoreTypedPreferences(
            editor: SharedPreferences.Editor,
            prefsJson: JSONObject,
            selectedSections: Set<ConfigBackupSection>
    ) {
        val keys = prefsJson.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (!ConfigBackupPreferencePolicy.shouldRestore(key) ||
                    sectionForPreference(key) !in selectedSections) continue
            val entry = prefsJson.optJSONObject(key)
                    ?: throw IOException("Invalid typed preference '$key'")
            putTypedPreference(editor, key, entry)
        }
    }

    private fun validateTypedPreferences(
            prefsJson: JSONObject,
            selectedSections: Set<ConfigBackupSection>
    ) {
        val keys = prefsJson.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (!ConfigBackupPreferencePolicy.shouldRestore(key) ||
                    sectionForPreference(key) !in selectedSections) continue
            val entry = prefsJson.optJSONObject(key)
                    ?: throw IOException("Invalid typed preference '$key'")
            readTypedPreference(entry, key)
        }
    }

    private fun putTypedPreference(
            editor: SharedPreferences.Editor,
            key: String,
            entry: JSONObject
    ) {
        when (val value = readTypedPreference(entry, key)) {
            is Boolean -> editor.putBoolean(key, value)
            is String -> editor.putString(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            else -> throw IOException("Unsupported preference type for '$key'")
        }
    }

    private fun readTypedPreference(entry: JSONObject, key: String): Any = when {
        entry.optString(TYPE_KEY) == "boolean" -> entry.getBoolean(VALUE_KEY)
        entry.optString(TYPE_KEY) == "string" -> entry.getString(VALUE_KEY)
        entry.optString(TYPE_KEY) == "int" -> entry.getInt(VALUE_KEY)
        entry.optString(TYPE_KEY) == "long" -> entry.getLong(VALUE_KEY)
        entry.optString(TYPE_KEY) == "float" -> entry.getDouble(VALUE_KEY).toFloat()
        entry.optString(TYPE_KEY) == "stringSet" -> {
            val values = entry.optJSONArray(VALUE_KEY)
                    ?: throw IOException("Invalid string set preference '$key'")
            (0 until values.length()).map { values.getString(it) }.toSet()
        }
        else -> throw IOException("Unknown preference type for '$key'")
    }

    /** A selected category is a snapshot, not a merge: values that existed only on the target
     * device must not survive a full restore. Removing them makes the normal preference defaults
     * take effect again. Older schemas remain merge-compatible. */
    private fun clearMissingPreferences(
            preferences: SharedPreferences,
            editor: SharedPreferences.Editor,
            prefsJson: JSONObject,
            selectedSections: Set<ConfigBackupSection>
    ) {
        preferences.all.keys
                .filter { ConfigBackupPreferencePolicy.shouldRestore(it) }
                .filter { sectionForPreference(it) in selectedSections }
                .filter { !prefsJson.has(it) }
                .forEach(editor::remove)
    }

    private fun restoreLegacyPreferences(
            editor: SharedPreferences.Editor,
            prefsJson: JSONObject,
            selectedSections: Set<ConfigBackupSection>
    ) {
        for (definition in MiscPreferences.EXPORTABLE) {
            if (!prefsJson.has(definition.key) ||
                    sectionForPreference(definition.key) !in selectedSections) continue
            putLegacyPreference(editor, definition.key, prefsJson.get(definition.key))
        }
        // Restore the per-face variants ("<baseKey>@<face>") of scoped exportable keys.
        val exportableKeys = MiscPreferences.EXPORTABLE.map { it.key }.toSet()
        val prefKeys = prefsJson.keys()
        while (prefKeys.hasNext()) {
            val key = prefKeys.next()
            if (isExportableScopedKey(key, exportableKeys) &&
                    sectionForPreference(key) in selectedSections) {
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
        for (store in ASSET_STORES) {
            val files = store.folder(context).listFiles()
                    ?.filter { it.isFile && SAFE_ASSET_NAME.matches(it.name) }
                    ?.sortedBy { it.name }
                    ?: emptyList()
            val entries = JSONArray()
            var totalBytes = 0L
            for (file in files) {
                val bytes = file.readBytes()
                // A zero-byte or outsized file here is a partial write or an interrupted
                // download, not something the user asked to back up - skipping it is what keeps
                // one stray file in one store from failing every other selected section too.
                if (bytes.isEmpty() || bytes.size > MAX_SINGLE_ASSET_BYTES ||
                        totalBytes + bytes.size > MAX_TOTAL_ASSET_BYTES) {
                    Timber.w("Skipping invalid %s asset '%s' (%d bytes)",
                            store.jsonKey, file.name, bytes.size)
                    continue
                }
                totalBytes += bytes.size
                entries.put(JSONObject()
                        .put("name", file.name)
                        .put("data", Base64.encodeToString(bytes, Base64.NO_WRAP)))
            }
            if (entries.length() > MAX_ASSETS_PER_STORE) {
                throw IOException("Too many ${store.jsonKey} assets to back up")
            }
            assetsJson.put(store.jsonKey, entries)
        }
        return assetsJson
    }

    private fun exportNamedPreferences(context: Context): JSONObject {
        val storesJson = JSONObject()
        for (store in namedPreferenceStores(context)) {
            val valuesJson = JSONObject()
            for ((key, value) in store.preferences.all) {
                if (ConfigBackupPreferencePolicy.isSupportedValue(value)) {
                    valuesJson.put(key, preferenceToJson(value ?: continue))
                }
            }
            storesJson.put(store.name, valuesJson)
        }
        return storesJson
    }

    private fun parseNamedPreferences(
            context: Context,
            json: JSONObject
    ): List<RestoredNamedPreferenceStore> {
        val storesJson = json.optJSONObject(NAMED_PREFERENCES_KEY)
                ?: throw IOException("Backup is missing named preferences")
        return namedPreferenceStores(context).map { store ->
            val valuesJson = storesJson.optJSONObject(store.name)
                    ?: throw IOException("Backup is missing named preference store '${store.name}'")
            val values = LinkedHashMap<String, JSONObject>()
            val keys = valuesJson.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val entry = valuesJson.optJSONObject(key)
                        ?: throw IOException("Invalid named preference '$key'")
                // Decode every value before any named preference file is cleared or rewritten.
                readTypedPreference(entry, "${store.name}.$key")
                values[key] = entry
            }
            RestoredNamedPreferenceStore(store.name, values)
        }
    }

    private fun restoreNamedPreferences(
            context: Context,
            stores: List<RestoredNamedPreferenceStore>
    ) {
        for (store in stores) {
            val editor = context.getSharedPreferences(store.name, Context.MODE_PRIVATE)
                    .edit()
                    .clear()
            store.values.forEach { (key, entry) -> putTypedPreference(editor, key, entry) }
            if (!editor.commit()) {
                throw IOException("Could not restore named preference store '${store.name}'")
            }
        }
    }

    /** Captures any future user data written to filesDir without duplicating the known bundle and
     * asset stores. Cache files are intentionally excluded: Android may delete cacheDir at any
     * time, and regenerating a cache is not part of restoring app configuration. */
    private fun exportInternalFiles(context: Context): JSONArray {
        val representedRoots = CONFIG_FILES.values.toSet() + ASSET_STORES.map { store ->
            store.folder(context).name
        }
        val candidates = context.filesDir.walkTopDown()
                .filter { it.isFile }
                .mapNotNull { file ->
                    val path = file.relativeTo(context.filesDir).path
                            .replace(File.separatorChar, '/')
                    val firstSegment = path.substringBefore('/')
                    if (firstSegment in representedRoots || path.split('/').any { it.startsWith('.') }) {
                        null
                    } else {
                        path to file
                    }
                }
                .toList()
                .sortedBy { it.first }

        var totalBytes = 0L
        val filesJson = JSONArray()
        candidates.forEach { (path, file) ->
            val bytes = file.readBytes()
            // This sweep exists to catch future user data nobody has modeled here yet - not to
            // gate every other selected section on whatever else happens to sit in filesDir. A
            // zero-byte file (a partial write, a quarantined ".corrupt" config) or one this
            // device has no business backing up (a stray multi-megabyte download) is skipped
            // rather than failing the whole export.
            if (bytes.isEmpty() || bytes.size > MAX_SINGLE_INTERNAL_FILE_BYTES ||
                    totalBytes + bytes.size > MAX_TOTAL_INTERNAL_FILE_BYTES) {
                Timber.w("Skipping invalid internal file '%s' (%d bytes)", path, bytes.size)
                return@forEach
            }
            totalBytes += bytes.size
            filesJson.put(JSONObject()
                    .put("path", path)
                    .put("data", Base64.encodeToString(bytes, Base64.NO_WRAP)))
        }
        if (filesJson.length() > MAX_INTERNAL_FILES) {
            throw IOException("Too many internal files to back up")
        }
        return filesJson
    }

    private fun parseInternalFiles(json: JSONObject): List<BackupInternalFile> {
        val filesJson = json.optJSONArray(INTERNAL_FILES_KEY)
                ?: throw IOException("Backup is missing internal files")
        if (filesJson.length() > MAX_INTERNAL_FILES) {
            throw IOException("Too many internal files in backup")
        }
        val paths = HashSet<String>()
        var totalBytes = 0L
        return (0 until filesJson.length()).map { index ->
            val entry = filesJson.optJSONObject(index)
                    ?: throw IOException("Invalid internal file")
            val path = entry.optString("path")
            if (!isSafeInternalPath(path) || !paths.add(path)) {
                throw IOException("Unsafe internal file path")
            }
            val bytes = try {
                Base64.decode(entry.getString("data"), Base64.NO_WRAP)
            } catch (e: IllegalArgumentException) {
                throw IOException("Invalid internal file data", e)
            }
            if (bytes.isEmpty() || bytes.size > MAX_SINGLE_INTERNAL_FILE_BYTES ||
                    totalBytes + bytes.size > MAX_TOTAL_INTERNAL_FILE_BYTES) {
                throw IOException("Invalid internal file size")
            }
            totalBytes += bytes.size
            BackupInternalFile(path, bytes)
        }
    }

    private fun isSafeInternalPath(path: String): Boolean {
        if (path.isBlank() || path.startsWith('/') || path.contains('\\')) return false
        val segments = path.split('/')
        return segments.all { segment ->
            segment.isNotBlank() && segment != "." && segment != ".." &&
                    segment.length <= 240 && segment.all { it.isLetterOrDigit() || it in "._@-" }
        }
    }

    private fun safeInternalFile(context: Context, path: String): File {
        if (!isSafeInternalPath(path)) throw IOException("Unsafe internal file path")
        val root = context.filesDir.canonicalFile
        val target = File(root, path).canonicalFile
        if (target.path != root.path && !target.path.startsWith(root.path + File.separator)) {
            throw IOException("Internal file escapes files directory")
        }
        val representedRoots = CONFIG_FILES.values + ASSET_STORES.map { it.folder(context).name }
        if (path.substringBefore('/') in representedRoots) {
            throw IOException("Internal file collides with a managed store")
        }
        return target
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

    /**
     * Backups before schema 6 represented one complete snapshot, so their missing section list
     * means "all". New backups always carry the list, including an intentionally empty selection.
     */
    private fun includedSections(json: JSONObject): Set<ConfigBackupSection> {
        if (!json.has(INCLUDED_SECTIONS_KEY)) return ConfigBackupSection.ALL
        val array = json.optJSONArray(INCLUDED_SECTIONS_KEY)
                ?: throw IOException("Invalid backup section list")
        val sections = LinkedHashSet<ConfigBackupSection>()
        for (index in 0 until array.length()) {
            val id = array.opt(index) as? String
                    ?: throw IOException("Invalid backup section")
            val section = ConfigBackupSection.fromId(id)
                    ?: throw IOException("Unknown backup section '$id'")
            if (!sections.add(section)) throw IOException("Duplicate backup section '$id'")
        }
        return sections
    }

    /** Maps every default-preference entry to exactly one selectable backup section. */
    internal fun sectionForPreference(key: String): ConfigBackupSection = when {
        key == "playlist_shortcuts" -> ConfigBackupSection.PLAYLIST_SHORTCUTS
        key == "search_history" || key == "track_history" -> ConfigBackupSection.HISTORY
        key in PRIVACY_KEYS -> ConfigBackupSection.PRIVACY
        key in LOCAL_APP_STATE_KEYS -> ConfigBackupSection.LOCAL_APP_STATE
        isWatchAppearancePreference(key) -> ConfigBackupSection.WATCH_APPEARANCE
        else -> ConfigBackupSection.APP_SETTINGS
    }

    private fun isWatchAppearancePreference(key: String): Boolean {
        val baseKey = key.substringBefore(FaceScopedPreferences.SCOPE_SEPARATOR)
        return baseKey in WATCH_APPEARANCE_GLOBAL_KEYS || FaceScopedPreferences.isScoped(baseKey)
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
