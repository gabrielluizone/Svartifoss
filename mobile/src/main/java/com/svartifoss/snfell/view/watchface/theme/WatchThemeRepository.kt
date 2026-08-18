package com.svartifoss.snfell.view.watchface.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.AppearanceContext
import com.svartifoss.snfell.common.FaceScopedPreferences
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.ThemeAppearance
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

/** One phone-local, user-named watch appearance. Only the active profile is projected into the
 * fixed [ThemeAppearance.CUSTOM_SCOPE], keeping the Wear preference payload bounded. */
data class WatchThemeProfile(
        val id: String,
        val name: String,
        val baseFace: String,
        val createdAt: Long,
        val updatedAt: Long,
        val revision: Int,
        val settings: Map<String, WatchThemeValue>
)

/** Explicit type tags make the library resilient to future preference-schema changes. */
sealed class WatchThemeValue {
    data class Text(val value: String) : WatchThemeValue()
    data class Flag(val value: Boolean) : WatchThemeValue()
    data class Number(val value: Int) : WatchThemeValue()
}

class WatchThemeLimitReachedException : IllegalStateException()

/**
 * Versioned custom-theme library. It deliberately uses a named SharedPreferences file: the
 * default preference file is mirrored wholesale to Wear, while this library may contain up to
 * [MAX_PROFILES] complete snapshots. Applying a profile copies exactly one snapshot to the fixed
 * custom scope in a single default-preference transaction.
 */
class WatchThemeRepository(context: Context) {

    companion object {
        const val MAX_PROFILES = 24
        const val LIBRARY_SCHEMA = 1
        private const val LIBRARY_PREFS = "watch_theme_library"
        private const val LIBRARY_JSON = "library_json"
        private const val MAX_NAME_LENGTH = 48

        private val FACE_NAME_RESOURCES = mapOf(
                "classic" to R.string.watch_theme_face_classic,
                "expressive" to R.string.watch_theme_face_expressive,
                "vinyl" to R.string.watch_theme_face_vinyl,
                "poster" to R.string.watch_theme_face_poster,
                "studio" to R.string.watch_theme_face_studio,
                "halo" to R.string.watch_theme_face_halo,
                "aurora" to R.string.watch_theme_face_aurora,
                "eclipse" to R.string.watch_theme_face_eclipse,
                "spectrum" to R.string.watch_theme_face_spectrum,
                "material" to R.string.watch_theme_face_material,
                "immersive" to R.string.watch_theme_face_immersive,
                "depth" to R.string.watch_theme_face_depth,
                "carousel" to R.string.watch_theme_face_carousel,
                "chat" to R.string.watch_theme_face_chat,
                "split" to R.string.watch_theme_face_split,
                "note" to R.string.watch_theme_face_note
        )

        fun displayNameForFace(context: Context, face: String): String =
                context.getString(
                        FACE_NAME_RESOURCES[ThemeAppearance.normalizeBaseFace(face)]
                                ?: R.string.watch_theme_face_classic)
    }

    private val appContext = context.applicationContext
    private val libraryPrefs = appContext.getSharedPreferences(LIBRARY_PREFS, Context.MODE_PRIVATE)

    val profiles: List<WatchThemeProfile>
        @Synchronized get() = loadState().profiles

    @Synchronized
    fun load(): List<WatchThemeProfile> = loadState().profiles

    @Synchronized
    fun activeProfile(defaultPrefs: SharedPreferences): WatchThemeProfile? {
        val active = ThemeAppearance.resolve(defaultPrefs) as? AppearanceContext.Custom ?: return null
        return loadState().profiles.firstOrNull { it.id == active.themeId }
    }

    /** Creates a complete snapshot from the selected built-in base without activating it. */
    @Synchronized
    fun create(
            name: String,
            baseFace: String,
            defaultPrefs: SharedPreferences
    ): WatchThemeProfile {
        val state = loadState()
        ensureCapacity(state.profiles.size)
        val now = System.currentTimeMillis()
        val normalizedBase = ThemeAppearance.normalizeBaseFace(baseFace)
        val profile = WatchThemeProfile(
                id = UUID.randomUUID().toString(),
                name = normalizeName(name),
                baseFace = normalizedBase,
                createdAt = now,
                updatedAt = now,
                revision = 1,
                settings = captureSettings(
                        defaultPrefs,
                        AppearanceContext.BuiltIn(normalizedBase))
        )
        saveState(state.copy(profiles = state.profiles + profile))
        return profile
    }

    @Synchronized
    fun duplicate(profile: WatchThemeProfile): WatchThemeProfile {
        val state = loadState()
        ensureCapacity(state.profiles.size)
        val source = state.profiles.firstOrNull { it.id == profile.id } ?: profile
        val now = System.currentTimeMillis()
        val copy = source.copy(
                id = UUID.randomUUID().toString(),
                name = uniqueCopyName(source.name, state.profiles),
                createdAt = now,
                updatedAt = now,
                revision = 1,
                settings = source.settings.toMap())
        saveState(state.copy(profiles = state.profiles + copy))
        return copy
    }

    @Synchronized
    fun rename(profile: WatchThemeProfile, name: String): WatchThemeProfile =
            rename(profile.id, name)

    @Synchronized
    fun rename(profileId: String, name: String): WatchThemeProfile {
        val state = loadState()
        val index = state.profiles.indexOfFirst { it.id == profileId }
        require(index >= 0) { "Unknown watch theme" }
        val old = state.profiles[index]
        val renamed = old.copy(
                name = normalizeName(name),
                updatedAt = System.currentTimeMillis(),
                revision = nextRevision(old.revision))
        val updated = state.profiles.toMutableList().apply { set(index, renamed) }
        saveState(state.copy(profiles = updated))
        return renamed
    }

    @Synchronized
    fun delete(profile: WatchThemeProfile): Boolean = delete(profile.id)

    @Synchronized
    fun delete(profileId: String): Boolean {
        val state = loadState()
        val updated = state.profiles.filterNot { it.id == profileId }
        if (updated.size == state.profiles.size) return false
        saveState(state.copy(
                profiles = updated,
                activeProfileId = state.activeProfileId.takeUnless { it == profileId }))
        return true
    }

    /** Captures edits made by the normal Watch preference screen while a custom scope is active. */
    @Synchronized
    fun captureActive(defaultPrefs: SharedPreferences): WatchThemeProfile? {
        val appearance = ThemeAppearance.resolve(defaultPrefs) as? AppearanceContext.Custom
                ?: return null
        val state = loadState()
        val index = state.profiles.indexOfFirst { it.id == appearance.themeId }
        if (index < 0) return null
        val old = state.profiles[index]
        val settings = captureSettings(defaultPrefs, appearance)
        if (old.settings == settings && old.baseFace == appearance.baseFace) {
            if (state.activeProfileId != old.id) {
                saveState(state.copy(activeProfileId = old.id))
            }
            return old
        }
        val captured = old.copy(
                baseFace = appearance.baseFace,
                settings = settings,
                updatedAt = System.currentTimeMillis(),
                revision = nextRevision(old.revision))
        val updated = state.profiles.toMutableList().apply { set(index, captured) }
        saveState(state.copy(profiles = updated, activeProfileId = captured.id))
        return captured
    }

    /** Atomically projects every scoped definition plus validation metadata to default prefs. */
    @Synchronized
    fun applyProfile(defaultPrefs: SharedPreferences, profile: WatchThemeProfile): Boolean {
        val state = loadState()
        val stored = state.profiles.firstOrNull { it.id == profile.id } ?: profile
        val normalized = normalizeProfile(stored, defaultPrefs) ?: return false
        val materialRevision = nextMaterializedRevision(defaultPrefs, normalized.revision)
        val editor = defaultPrefs.edit()
        for (definition in FaceScopedPreferences.SCOPED_DEFINITIONS) {
            val value = normalized.settings.getValue(definition.key)
            putThemeValue(
                    editor,
                    FaceScopedPreferences.scopedKey(definition.key, ThemeAppearance.CUSTOM_SCOPE),
                    value)
        }
        editor.putString(MiscPreferences.WEAR_SCREEN_FACE.key, normalized.baseFace)
        editor.putString(MiscPreferences.WEAR_ACTIVE_CUSTOM_THEME_ID.key, normalized.id)
        editor.putString(
                MiscPreferences.WEAR_CUSTOM_THEME_SCHEMA.key,
                ThemeAppearance.CURRENT_SCHEMA.toString())
        editor.putString(
                MiscPreferences.WEAR_CUSTOM_THEME_REVISION.key,
                materialRevision.toString())
        editor.putBoolean(MiscPreferences.WEAR_CUSTOM_THEME_COMPLETE.key, true)
        if (!editor.commit()) return false

        val existingIndex = state.profiles.indexOfFirst { it.id == normalized.id }
        val profiles = if (existingIndex >= 0) {
            state.profiles.toMutableList().apply { set(existingIndex, normalized) }
        } else if (state.profiles.size < MAX_PROFILES) {
            state.profiles + normalized
        } else {
            state.profiles
        }
        saveState(LibraryState(profiles, normalized.id))
        return true
    }

    /** Returns to an existing built-in scope and invalidates custom metadata atomically. */
    @Synchronized
    fun applyBuiltIn(defaultPrefs: SharedPreferences, face: String): Boolean {
        val normalizedFace = ThemeAppearance.normalizeBaseFace(face)
        val committed = defaultPrefs.edit()
                .putString(MiscPreferences.WEAR_SCREEN_FACE.key, normalizedFace)
                .putString(MiscPreferences.WEAR_ACTIVE_CUSTOM_THEME_ID.key, "")
                .putString(MiscPreferences.WEAR_CUSTOM_THEME_SCHEMA.key, "0")
                .putString(MiscPreferences.WEAR_CUSTOM_THEME_REVISION.key, "0")
                .putBoolean(MiscPreferences.WEAR_CUSTOM_THEME_COMPLETE.key, false)
                .commit()
        if (committed) saveState(loadState().copy(activeProfileId = null))
        return committed
    }

    /** Backup hook. The active projection is captured before serializing the phone-only library. */
    @Synchronized
    fun exportToJson(defaultPrefs: SharedPreferences): JSONObject {
        captureActive(defaultPrefs)
        return stateToJson(loadState())
    }

    @Synchronized
    fun serialize(defaultPrefs: SharedPreferences): JSONObject = exportToJson(defaultPrefs)

    /** Replaces, rather than merges, the library so a backup restore cannot leave orphan themes. */
    @Synchronized
    fun replaceFromJson(json: JSONObject, defaultPrefs: SharedPreferences): List<WatchThemeProfile> {
        validateImport(json)
        val imported = parseState(json, defaultPrefs)
        saveState(imported)
        val active = imported.activeProfileId?.let { id ->
            imported.profiles.firstOrNull { it.id == id }
        }
        if (active != null) {
            applyProfile(defaultPrefs, active)
        } else {
            val currentFace = ThemeAppearance.resolve(defaultPrefs).baseFace
            applyBuiltIn(defaultPrefs, currentFace)
        }
        return loadState().profiles
    }

    @Synchronized
    fun importFromJson(json: JSONObject, defaultPrefs: SharedPreferences): List<WatchThemeProfile> =
            replaceFromJson(json, defaultPrefs)

    @Synchronized
    fun deserialize(json: JSONObject, defaultPrefs: SharedPreferences): List<WatchThemeProfile> =
            parseState(json, defaultPrefs).profiles

    /** Strict structural validation used before ConfigBackup commits any of its other files or
     * preferences. Individual missing/obsolete setting values remain migratable and are completed
     * from the base layout by [normalizeProfile]. */
    @Synchronized
    fun validateImport(json: JSONObject) {
        if (json.optInt("schemaVersion", -1) != LIBRARY_SCHEMA) {
            throw JSONException("Unsupported watch theme library schema")
        }
        val profilesJson = json.optJSONArray("profiles")
                ?: throw JSONException("Missing watch theme profiles")
        if (profilesJson.length() > MAX_PROFILES) {
            throw JSONException("Too many watch theme profiles")
        }
        val ids = HashSet<String>()
        for (index in 0 until profilesJson.length()) {
            val raw = profilesJson.optJSONObject(index)
                    ?: throw JSONException("Invalid watch theme profile at index $index")
            val parsed = parseProfile(raw)
                    ?: throw JSONException("Invalid watch theme profile at index $index")
            if (!ids.add(parsed.id)) throw JSONException("Duplicate watch theme profile id")
        }
        if (json.has("activeProfileId") && !json.isNull("activeProfileId")) {
            val active = json.optString("activeProfileId", "")
            if (active !in ids) throw JSONException("Active watch theme does not exist")
        }
    }

    private fun captureSettings(
            prefs: SharedPreferences,
            context: AppearanceContext
    ): Map<String, WatchThemeValue> = FaceScopedPreferences.SCOPED_DEFINITIONS.associate { definition ->
        val resolved = FaceScopedPreferences.resolveValue(prefs, definition, context)
        definition.key to when (resolved) {
            is String -> WatchThemeValue.Text(resolved)
            is Boolean -> WatchThemeValue.Flag(resolved)
            is Int -> WatchThemeValue.Number(resolved)
            else -> error("Unsupported theme value for ${definition.key}")
        }
    }

    /** Imported/old profiles are completed against their base preset and unknown keys disappear. */
    private fun normalizeProfile(
            profile: WatchThemeProfile,
            defaultPrefs: SharedPreferences
    ): WatchThemeProfile? {
        val id = normalizeUuid(profile.id) ?: return null
        val base = ThemeAppearance.normalizeBaseFace(profile.baseFace)
        val defaults = captureSettings(defaultPrefs, AppearanceContext.BuiltIn(base))
        val complete = FaceScopedPreferences.SCOPED_DEFINITIONS.associate { definition ->
            val candidate = profile.settings[definition.key]
            definition.key to if (valueMatchesDefinition(candidate, definition.defaultValue)) {
                candidate!!
            } else {
                defaults.getValue(definition.key)
            }
        }
        return profile.copy(
                id = id,
                name = normalizeName(profile.name),
                baseFace = base,
                createdAt = profile.createdAt.coerceAtLeast(0L),
                updatedAt = profile.updatedAt.coerceAtLeast(profile.createdAt.coerceAtLeast(0L)),
                revision = profile.revision.coerceAtLeast(1),
                settings = complete)
    }

    private fun valueMatchesDefinition(value: WatchThemeValue?, default: Any): Boolean = when (default) {
        is String -> value is WatchThemeValue.Text
        is Boolean -> value is WatchThemeValue.Flag
        is Int -> value is WatchThemeValue.Number
        else -> false
    }

    private fun putThemeValue(editor: SharedPreferences.Editor, key: String, value: WatchThemeValue) {
        when (value) {
            is WatchThemeValue.Text -> editor.putString(key, value.value)
            is WatchThemeValue.Flag -> editor.putBoolean(key, value.value)
            // Numeric appearance preferences use wearutils' string convention.
            is WatchThemeValue.Number -> editor.putString(key, value.value.toString())
        }
    }

    private fun nextMaterializedRevision(prefs: SharedPreferences, profileRevision: Int): Int {
        val current = try {
            prefs.getString(MiscPreferences.WEAR_CUSTOM_THEME_REVISION.key, null)?.toIntOrNull()
        } catch (_: ClassCastException) {
            try {
                prefs.getInt(MiscPreferences.WEAR_CUSTOM_THEME_REVISION.key, 0)
            } catch (_: ClassCastException) {
                0
            }
        } ?: 0
        return maxOf(nextRevision(current), profileRevision.coerceAtLeast(1))
    }

    private fun ensureCapacity(size: Int) {
        if (size >= MAX_PROFILES) throw WatchThemeLimitReachedException()
    }

    private fun normalizeName(raw: String): String =
            raw.trim().replace(Regex("\\s+"), " ").take(MAX_NAME_LENGTH)
                    .ifBlank { appContext.getString(R.string.watch_theme_generic_name) }

    private fun uniqueCopyName(source: String, profiles: List<WatchThemeProfile>): String {
        val used = profiles.map { it.name.lowercase() }.toSet()
        val copyBase = appContext.getString(R.string.watch_theme_copy_name, source)
        var candidate = copyBase.take(MAX_NAME_LENGTH)
        var number = 2
        while (candidate.lowercase() in used) {
            val suffix = " $number"
            candidate = (copyBase.take(MAX_NAME_LENGTH - suffix.length) + suffix)
            number++
        }
        return candidate
    }

    private fun nextRevision(value: Int): Int =
            if (value == Int.MAX_VALUE) 1 else (value + 1).coerceAtLeast(1)

    private fun normalizeUuid(raw: String): String? = try {
        UUID.fromString(raw).toString()
    } catch (_: IllegalArgumentException) {
        null
    }

    private data class LibraryState(
            val profiles: List<WatchThemeProfile>,
            val activeProfileId: String?
    )

    private fun loadState(): LibraryState {
        val raw = libraryPrefs.getString(LIBRARY_JSON, null) ?: return LibraryState(emptyList(), null)
        return try {
            parseState(JSONObject(raw), null)
        } catch (_: JSONException) {
            LibraryState(emptyList(), null)
        }
    }

    private fun saveState(state: LibraryState) {
        libraryPrefs.edit().putString(LIBRARY_JSON, stateToJson(state).toString()).commit()
        publishAvailableThemes(state.profiles)
    }

    /**
     * Republishes the on-watch picker's theme list from the stored library.
     *
     * Needed as its own entry point because the library lives in a separate, phone-local prefs file
     * while the picker reads a *synced* key, and that key was only ever written by [saveState] - so
     * a library built before the key existed (or on a phone whose watch was paired afterwards) had
     * no route to the watch at all short of the user editing a theme to trigger a save. Called once
     * per process start, alongside the preference snapshot re-publish that exists for the same
     * class of staleness.
     *
     * Writing the identical value is free: `WatchPreferenceSyncCoordinator` only reacts to actual
     * changes, so an unchanged library costs nothing on the wire.
     */
    fun publishAvailableThemes(profiles: List<WatchThemeProfile> = this.profiles) {
        val defaultPrefs = PreferenceManager.getDefaultSharedPreferences(appContext)
        val availableJson = JSONArray().apply {
            profiles.take(MAX_PROFILES).forEach {
                put(JSONObject().put("id", it.id).put("name", it.name).put("baseFace", it.baseFace))
            }
        }.toString()
        defaultPrefs.edit().putString(MiscPreferences.WEAR_AVAILABLE_CUSTOM_THEMES.key, availableJson).apply()
    }

    private fun stateToJson(state: LibraryState): JSONObject = JSONObject()
            .put("schemaVersion", LIBRARY_SCHEMA)
            .put("activeProfileId", state.activeProfileId ?: JSONObject.NULL)
            .put("profiles", JSONArray().apply {
                state.profiles.take(MAX_PROFILES).forEach { put(profileToJson(it)) }
            })

    private fun profileToJson(profile: WatchThemeProfile): JSONObject = JSONObject()
            .put("id", profile.id)
            .put("name", profile.name)
            .put("baseFace", profile.baseFace)
            .put("createdAt", profile.createdAt)
            .put("updatedAt", profile.updatedAt)
            .put("revision", profile.revision)
            .put("settings", JSONObject().apply {
                profile.settings.forEach { (key, value) ->
                    put(key, valueToJson(value))
                }
            })

    private fun valueToJson(value: WatchThemeValue): JSONObject = when (value) {
        is WatchThemeValue.Text -> JSONObject().put("type", "string").put("value", value.value)
        is WatchThemeValue.Flag -> JSONObject().put("type", "boolean").put("value", value.value)
        is WatchThemeValue.Number -> JSONObject().put("type", "int").put("value", value.value)
    }

    private fun parseState(json: JSONObject, defaults: SharedPreferences?): LibraryState {
        if (json.optInt("schemaVersion", -1) !in 1..LIBRARY_SCHEMA) {
            return LibraryState(emptyList(), null)
        }
        val profilesJson = json.optJSONArray("profiles") ?: JSONArray()
        val profiles = ArrayList<WatchThemeProfile>(minOf(profilesJson.length(), MAX_PROFILES))
        val seenIds = HashSet<String>()
        for (index in 0 until minOf(profilesJson.length(), MAX_PROFILES)) {
            val raw = profilesJson.optJSONObject(index) ?: continue
            val parsed = parseProfile(raw) ?: continue
            if (!seenIds.add(parsed.id)) continue
            val normalized = if (defaults != null) normalizeProfile(parsed, defaults) else parsed
            if (normalized != null) profiles += normalized
        }
        val active = json.optString("activeProfileId", "")
                .takeIf { id -> profiles.any { it.id == id } }
        return LibraryState(profiles, active)
    }

    private fun parseProfile(json: JSONObject): WatchThemeProfile? {
        val id = normalizeUuid(json.optString("id")) ?: return null
        val base = json.optString("baseFace").takeIf { it in ThemeAppearance.ALLOWED_BASE_FACES }
                ?: return null
        val settingsObject = json.optJSONObject("settings") ?: JSONObject()
        val settings = LinkedHashMap<String, WatchThemeValue>()
        val keys = settingsObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key !in FaceScopedPreferences.SCOPED_KEYS) continue
            parseValue(settingsObject.optJSONObject(key))?.let { settings[key] = it }
        }
        return WatchThemeProfile(
                id = id,
                name = normalizeName(json.optString("name")),
                baseFace = base,
                createdAt = json.optLong("createdAt", 0L).coerceAtLeast(0L),
                updatedAt = json.optLong("updatedAt", 0L).coerceAtLeast(0L),
                revision = json.optInt("revision", 1).coerceAtLeast(1),
                settings = settings)
    }

    private fun parseValue(json: JSONObject?): WatchThemeValue? = when (json?.optString("type")) {
        "string" -> WatchThemeValue.Text(json.optString("value", ""))
        "boolean" -> if (json.has("value")) WatchThemeValue.Flag(json.optBoolean("value")) else null
        "int" -> if (json.has("value")) WatchThemeValue.Number(json.optInt("value")) else null
        else -> null
    }
}
