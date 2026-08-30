package com.svartifoss.snfell.view.watchface.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.svartifoss.snfell.R
import com.svartifoss.snfell.WATCH_SNAPSHOT_GUARD_BYTES
import com.svartifoss.snfell.estimateWatchPreferenceSnapshotBytes
import com.svartifoss.snfell.shouldSyncWatchPreference
import com.svartifoss.snfell.common.AppearanceContext
import com.svartifoss.snfell.common.ArchivedFaces
import com.svartifoss.snfell.common.FaceScopedPreferences
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.ThemeAppearance
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.Collections
import java.util.UUID

/** The maximum accepted length for either a local or public theme name. */
private const val MAX_THEME_NAME_LENGTH = 48

/** Public profile strings are deliberately capped before they cross the network boundary. */
internal const val MAX_PUBLIC_SETTING_TEXT_LENGTH = 128

/** Leaves ample room below the Wear Data Layer's 100 KiB per-item ceiling. */
private const val MAX_PUBLIC_MATERIALIZED_BYTES = 24 * 1024

/** One phone-local, user-named watch appearance. Only the active profile is projected into the
 * fixed [ThemeAppearance.CUSTOM_SCOPE], keeping the Wear preference payload bounded. */
data class WatchThemeProfile(
        val id: String,
        val name: String,
        val baseFace: String,
        val createdAt: Long,
        val updatedAt: Long,
        val revision: Int,
        val settings: Map<String, WatchThemeValue>,
        /**
         * The immutable identity of a gallery theme this local profile was installed from.
         *
         * [id] is deliberately the **local** profile id, not this id. Keeping the two namespaces
         * separate prevents a public catalogue id from colliding with (or replacing) a user's own
         * profile, while [revision] lets a later gallery update be detected without treating local
         * edits as a new published revision.
         */
        val publishedTheme: PublishedThemeSource? = null
)

/** Stable identity of a theme published in the community gallery. */
data class PublishedThemeSource(
        val id: String,
        val revision: Int
)

/** Explicit type tags make the library resilient to future preference-schema changes. */
sealed class WatchThemeValue {
    data class Text(val value: String) : WatchThemeValue()
    data class Flag(val value: Boolean) : WatchThemeValue()
    data class Number(val value: Int) : WatchThemeValue()
}

/**
 * A newly allocated, upload-ready public representation of one local theme.
 *
 * [serializedProfile] is intentionally the source of truth for upload. It contains a fresh public
 * id and never contains the local library id or [PublishedThemeSource] provenance. [settings] is
 * the same complete, typed snapshot so the submission layer can calculate its policy digest
 * without reparsing JSON. [profileJson] returns a new mutable object on each call, preventing a
 * caller from changing the serialized upload body by accident.
 */
data class CommunityThemeSubmissionDraft(
        val id: String,
        val name: String,
        val baseFace: String,
        val settings: Map<String, WatchThemeValue>,
        val serializedProfile: String
) {
    fun profileJson(): JSONObject = JSONObject(serializedProfile)
}

/** Explicit outcome of preparing a locally saved theme for the community submission queue. */
sealed class CommunityThemeSubmissionDraftResult {
    data class Ready(val draft: CommunityThemeSubmissionDraft) : CommunityThemeSubmissionDraftResult()
    object ProfileNotFound : CommunityThemeSubmissionDraftResult()
    object PublishedThemeCannotBeSubmitted : CommunityThemeSubmissionDraftResult()
    object InvalidPublicName : CommunityThemeSubmissionDraftResult()
    object InvalidProfile : CommunityThemeSubmissionDraftResult()
    object ProfileTooLarge : CommunityThemeSubmissionDraftResult()
}

class WatchThemeLimitReachedException : IllegalStateException()

/** Outcome of installing and applying one parsed community-gallery profile. */
sealed class PublishedThemeInstallResult {
    /**
     * The locally stored profile that is now active.
     *
     * When [alreadyInstalled] is true, its existing local settings were applied verbatim; a fresh
     * download never overwrites edits the user made after installing it. [updateAvailable] is
     * surfaced now so a later update UI can offer an explicit replacement/copy choice.
     */
    data class Applied(
            val profile: WatchThemeProfile,
            val alreadyInstalled: Boolean,
            val updateAvailable: Boolean
    ) : PublishedThemeInstallResult()

    /** A new gallery theme cannot be saved until the user frees one of the local 24 slots. */
    object LibraryFull : PublishedThemeInstallResult()

    /** Applying the complete snapshot would exceed the safe phone-to-watch transport budget. */
    object WatchSyncTooLarge : PublishedThemeInstallResult()

    /** The candidate was not produced by [WatchThemeRepository.parsePublishedProfile]. */
    object InvalidProfile : PublishedThemeInstallResult()

    /** Materializing the complete custom snapshot into default preferences failed. */
    object ApplyFailed : PublishedThemeInstallResult()
}

private fun valueMatchesDefinition(value: WatchThemeValue?, default: Any): Boolean = when (default) {
    is String -> value is WatchThemeValue.Text
    is Boolean -> value is WatchThemeValue.Flag
    is Int -> value is WatchThemeValue.Number
    else -> false
}

private fun valueToJson(value: WatchThemeValue): JSONObject = when (value) {
    is WatchThemeValue.Text -> JSONObject().put("type", "string").put("value", value.value)
    is WatchThemeValue.Flag -> JSONObject().put("type", "boolean").put("value", value.value)
    is WatchThemeValue.Number -> JSONObject().put("type", "int").put("value", value.value)
}

private fun immutableThemeSettings(
        values: Map<String, WatchThemeValue>
): Map<String, WatchThemeValue> = Collections.unmodifiableMap(LinkedHashMap(values))

/**
 * Returns the first available localized copy name, preserving the numeric suffix when the base has
 * to be truncated. Kept Android-free because built-in and custom duplication must follow exactly
 * the same collision policy.
 */
internal fun uniqueWatchThemeCopyName(
        copyBase: String,
        existingNames: Collection<String>,
        maxNameLength: Int = MAX_THEME_NAME_LENGTH
): String {
    require(maxNameLength > 0)
    val used = existingNames.mapTo(HashSet()) { it.lowercase() }
    var candidate = copyBase.take(maxNameLength)
    var number = 2
    while (candidate.lowercase() in used) {
        val suffix = " $number"
        candidate = if (suffix.length >= maxNameLength) {
            suffix.takeLast(maxNameLength)
        } else {
            copyBase.take(maxNameLength - suffix.length) + suffix
        }
        number++
    }
    return candidate
}

/**
 * Pure half of [WatchThemeRepository.prepareCommunityThemeSubmission]. Keeping it Android-free
 * makes the public wire contract directly testable; the repository remains the only production
 * entry point that resolves a profile id from the persisted library.
 */
internal object CommunityThemeSubmissionDraftFactory {

    fun build(
            source: WatchThemeProfile,
            publicName: String,
            publicId: String,
            nowMillis: Long,
            constraints: CommunityThemeConstraints? = null
    ): CommunityThemeSubmissionDraftResult {
        if (source.publishedTheme != null) {
            return CommunityThemeSubmissionDraftResult.PublishedThemeCannotBeSubmitted
        }
        if (!isCanonicalUuid(source.id)) return CommunityThemeSubmissionDraftResult.InvalidProfile
        if (source.baseFace !in ThemeAppearance.ALLOWED_BASE_FACES ||
                source.baseFace in ArchivedFaces.KEYS) {
            return CommunityThemeSubmissionDraftResult.InvalidProfile
        }
        val normalizedName = normalizePublicName(publicName)
                ?: return CommunityThemeSubmissionDraftResult.InvalidPublicName
        if (!isCanonicalUuid(publicId)) return CommunityThemeSubmissionDraftResult.InvalidProfile

        val definitions = FaceScopedPreferences.SCOPED_DEFINITIONS
        val definitionKeys = FaceScopedPreferences.SCOPED_DEFINITIONS_BY_KEY.keys
        // A public submission is deliberately a complete snapshot. Filling a corrupted or stale
        // local profile with defaults here would turn a storage error into a theme the user never
        // actually designed; only the *network import* has that migration behaviour.
        if (source.settings.keys != definitionKeys || source.settings.size != definitions.size) {
            return CommunityThemeSubmissionDraftResult.InvalidProfile
        }
        val completeSettings = LinkedHashMap<String, WatchThemeValue>(definitions.size)
        for (definition in definitions) {
            val value = source.settings[definition.key]
                    ?: return CommunityThemeSubmissionDraftResult.InvalidProfile
            if (!valueMatchesDefinition(value, definition.defaultValue) ||
                    (value is WatchThemeValue.Text &&
                            value.value.length > MAX_PUBLIC_SETTING_TEXT_LENGTH) ||
                    (constraints != null && !constraints.accepts(definition.key, value))) {
                return CommunityThemeSubmissionDraftResult.InvalidProfile
            }
            completeSettings[definition.key] = value
        }

        val timestamp = nowMillis.coerceAtLeast(0L)
        val profile = JSONObject().apply {
            put("schemaVersion", WatchThemeRepository.LIBRARY_SCHEMA)
            put("id", publicId)
            put("name", normalizedName)
            put("baseFace", source.baseFace)
            put("createdAt", timestamp)
            put("updatedAt", timestamp)
            put("revision", 1)
            put("settings", JSONObject().apply {
                completeSettings.forEach { (key, value) ->
                    put(key, valueToJson(value))
                }
            })
        }
        val serialized = profile.toString()
        if (serialized.toByteArray(Charsets.UTF_8).size > MAX_PUBLIC_MATERIALIZED_BYTES) {
            return CommunityThemeSubmissionDraftResult.ProfileTooLarge
        }
        return CommunityThemeSubmissionDraftResult.Ready(
                CommunityThemeSubmissionDraft(
                        id = publicId,
                        name = normalizedName,
                        baseFace = source.baseFace,
                        settings = immutableThemeSettings(completeSettings),
                        serializedProfile = serialized))
    }

    private fun normalizePublicName(raw: String): String? {
        if (raw.any(Character::isISOControl)) return null
        val normalized = raw.trim().replace(Regex("\\s+"), " ")
        if (normalized.any(Character::isISOControl)) return null
        return normalized.takeIf { it.isNotBlank() && it.length <= MAX_THEME_NAME_LENGTH }
    }

    private fun isCanonicalUuid(raw: String): Boolean = try {
        UUID.fromString(raw).toString() == raw
    } catch (_: IllegalArgumentException) {
        false
    }
}

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
        private const val LEGACY_PHASE_ONE_CINEMA_ID = "09ea139e-8e25-443d-a065-09e8d10da102"
        private const val MAX_NAME_LENGTH = MAX_THEME_NAME_LENGTH
        /** Public theme values are materialized into the phone→watch preference snapshot. */
        private const val MAX_PUBLISHED_SETTING_TEXT_LENGTH = MAX_PUBLIC_SETTING_TEXT_LENGTH
        /** Leaves ample room below the Wear Data Layer's 100 KiB per-item ceiling. */
        private const val MAX_PUBLISHED_MATERIALIZED_BYTES = MAX_PUBLIC_MATERIALIZED_BYTES

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
                "note" to R.string.watch_theme_face_note,
                "verse" to R.string.watch_theme_face_verse,
                "metadata" to R.string.watch_theme_face_metadata,
                "ribbon" to R.string.watch_theme_face_ribbon,
                "frame" to R.string.watch_theme_face_frame
        )

        fun displayNameForFace(context: Context, face: String): String =
                context.getString(
                        FACE_NAME_RESOURCES[ThemeAppearance.normalizeBaseFace(face)]
                                ?: R.string.watch_theme_face_classic)
    }

    private val appContext = context.applicationContext
    private val libraryPrefs = appContext.getSharedPreferences(LIBRARY_PREFS, Context.MODE_PRIVATE)
    /** A missing/corrupt semantic contract disables public import/submission rather than widening it. */
    private val communityThemeConstraints: CommunityThemeConstraints? by lazy {
        CommunityThemeConstraints.load(appContext)
    }

    val profiles: List<WatchThemeProfile>
        @Synchronized get() = loadState().profiles

    @Synchronized
    fun load(): List<WatchThemeProfile> = loadState().profiles

    @Synchronized
    fun activeProfile(defaultPrefs: SharedPreferences): WatchThemeProfile? {
        val active = ThemeAppearance.resolve(defaultPrefs) as? AppearanceContext.Custom ?: return null
        return loadState().profiles.firstOrNull { it.id == active.themeId }
    }

    /**
     * Builds a fresh, public submission body from one persisted user-owned profile.
     *
     * The caller supplies only a local profile id and its desired public name. The stored profile
     * is re-read here so a stale UI object cannot smuggle another profile's values, and a theme
     * installed from the gallery is never allowed to become a new public submission. The returned
     * id is newly allocated for the community queue; the local id and optional source provenance
     * never leave this method.
     */
    @Synchronized
    fun prepareCommunityThemeSubmission(
            profileId: String,
            publicName: String
    ): CommunityThemeSubmissionDraftResult {
        val source = loadState().profiles.firstOrNull { it.id == profileId }
                ?: return CommunityThemeSubmissionDraftResult.ProfileNotFound
        val constraints = communityThemeConstraints
                ?: return CommunityThemeSubmissionDraftResult.InvalidProfile
        val prepared = CommunityThemeSubmissionDraftFactory.build(
                source = source,
                publicName = publicName,
                publicId = newPublicSubmissionId(source.id),
                nowMillis = System.currentTimeMillis(),
                constraints = constraints)
        val draft = (prepared as? CommunityThemeSubmissionDraftResult.Ready)?.draft
                ?: return prepared

        // Use the same strict public parser and shipped-default normalization as downloaded
        // gallery content. Besides guarding future edits to the serializer, this proves the body
        // handed to Firebase can subsequently be published and installed by this app unchanged.
        val parsed = try {
            parsePublishedProfile(draft.profileJson())
        } catch (_: JSONException) {
            null
        } ?: return CommunityThemeSubmissionDraftResult.InvalidProfile
        if (parsed.id != draft.id ||
                parsed.name != draft.name ||
                parsed.baseFace != draft.baseFace ||
                parsed.revision != 1 ||
                parsed.settings != draft.settings ||
                parsed.publishedTheme != PublishedThemeSource(draft.id, 1)) {
            return CommunityThemeSubmissionDraftResult.InvalidProfile
        }
        return CommunityThemeSubmissionDraftResult.Ready(
                draft.copy(settings = immutableThemeSettings(parsed.settings)))
    }

    /**
     * Converts one published `profileToJson`-shaped object into a complete local candidate.
     *
     * The JSON's UUID and revision are retained as [WatchThemeProfile.publishedTheme], not as a
     * local profile id. A known setting must use its exact type tag; missing current-version
     * settings are completed from the built-in base layout. In particular, a published profile
     * never inherits an individual's current preferences.
     */
    @Synchronized
    fun parsePublishedProfile(json: JSONObject): WatchThemeProfile? =
            parsePublishedProfile(json, allowLegacyReadOnly = false)

    /**
     * Reads the one immutable Phase-1 compatibility profile that predates the canonical public
     * vocabulary. This is intentionally separate from [parsePublishedProfile]: the legacy value
     * never reaches a new submission, a public digest, or a normal gallery candidate.
     */
    @Synchronized
    internal fun parseTrustedLegacyPhaseOneProfile(json: JSONObject): WatchThemeProfile? =
            parsePublishedProfile(json, allowLegacyReadOnly = true)

    private fun parsePublishedProfile(
            json: JSONObject,
            allowLegacyReadOnly: Boolean
    ): WatchThemeProfile? {
        val parsed = parseProfile(json) ?: return null
        if (allowLegacyReadOnly && !isTrustedLegacyPhaseOneProfile(parsed)) return null
        val strictSettings = parsePublishedSettings(
                json.optJSONObject("settings"),
                allowLegacyReadOnly) ?: return null
        // Backups retain archived faces for existing users, but the public gallery must never
        // publish a theme that current pickers deliberately hide.
        if (parsed.baseFace in ArchivedFaces.KEYS) return null
        val source = PublishedThemeSource(parsed.id, parsed.revision)
        return normalizePublishedProfile(parsed.copy(
                settings = strictSettings,
                publishedTheme = source),
                allowLegacyReadOnly)
    }

    /**
     * Installs [profile] under a new local UUID and immediately applies it.
     *
     * Re-selecting an already installed gallery id applies the saved local copy instead, preserving
     * all edits made in the Watch editor. The capacity check happens before any default-preference
     * write, so a full library can never leave an active `custom_active` snapshot that has no
     * matching stored profile.
     */
    @Synchronized
    fun installAndApplyPublishedProfile(
            defaultPrefs: SharedPreferences,
            profile: WatchThemeProfile
    ): PublishedThemeInstallResult {
        val source = normalizePublishedThemeSource(profile.publishedTheme)
                ?: return PublishedThemeInstallResult.InvalidProfile
        if (normalizeUuid(profile.id) != source.id) {
            return PublishedThemeInstallResult.InvalidProfile
        }
        val normalized = normalizePublishedProfile(
                profile.copy(publishedTheme = source),
                allowLegacyReadOnly = isTrustedLegacyPhaseOneProfile(profile))
                ?: return PublishedThemeInstallResult.InvalidProfile
        val state = loadState()
        val existing = state.profiles.firstOrNull { it.publishedTheme?.id == source.id }
        val profileToApply = existing ?: normalized
        if (!fitsWatchSyncSnapshot(defaultPrefs, profileToApply)) {
            return PublishedThemeInstallResult.WatchSyncTooLarge
        }
        if (existing != null) {
            val installedRevision = existing.publishedTheme?.revision ?: 0
            return if (applyProfile(defaultPrefs, existing)) {
                PublishedThemeInstallResult.Applied(
                        profile = existing,
                        alreadyInstalled = true,
                        updateAvailable = source.revision > installedRevision)
            } else {
                PublishedThemeInstallResult.ApplyFailed
            }
        }
        if (state.profiles.size >= MAX_PROFILES) {
            return PublishedThemeInstallResult.LibraryFull
        }

        val installed = normalized.copy(
                id = newLocalProfileId(state.profiles),
                publishedTheme = source)
        return if (applyProfile(defaultPrefs, installed)) {
            PublishedThemeInstallResult.Applied(
                    profile = installed,
                    alreadyInstalled = false,
                    updateAvailable = false)
        } else {
            PublishedThemeInstallResult.ApplyFailed
        }
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
                settings = source.settings.toMap(),
                // A duplicate is a user-owned fork. It must not be silently overwritten by a
                // future update of the gallery theme it started from.
                publishedTheme = null)
        saveState(state.copy(profiles = state.profiles + copy))
        return copy
    }

    /** Copies the selected built-in face's current resolved look into My themes without applying it. */
    @Synchronized
    fun duplicateBuiltIn(
            face: String,
            defaultPrefs: SharedPreferences
    ): WatchThemeProfile {
        val state = loadState()
        ensureCapacity(state.profiles.size)
        val normalizedBase = ThemeAppearance.normalizeBaseFace(face)
        val now = System.currentTimeMillis()
        val copy = WatchThemeProfile(
                id = UUID.randomUUID().toString(),
                name = uniqueCopyName(
                        displayNameForFace(appContext, normalizedBase),
                        state.profiles),
                baseFace = normalizedBase,
                createdAt = now,
                updatedAt = now,
                revision = 1,
                settings = captureSettings(
                        defaultPrefs,
                        AppearanceContext.BuiltIn(normalizedBase))
        )
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
        return saveState(state.copy(
                profiles = updated,
                activeProfileId = state.activeProfileId.takeUnless { it == profileId }))
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
        val existing = state.profiles.firstOrNull { it.id == profile.id }
        // A profile that is not in the library would become an active-but-uneditable snapshot if
        // we materialized it at capacity. Reject it before touching default preferences instead.
        if (existing == null && state.profiles.size >= MAX_PROFILES) return false
        val stored = existing ?: profile
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
        } else {
            state.profiles + normalized
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
                settings = complete,
                // Bad/old source metadata must never make an otherwise-valid backup profile
                // unreadable. Losing only the optional update link is the safe degradation.
                publishedTheme = normalizePublishedThemeSource(profile.publishedTheme))
    }

    /**
     * Public gallery profiles must be reproducible on every phone. Unlike a config import, a
     * missing value therefore falls back to the base face's shipped default rather than a value
     * resolved from the user's existing preference file. That also keeps the gallery miniature
     * and the installed profile identical when a hand-authored Phase-1 profile specifies only
     * the settings that make its look distinct.
     */
    private fun normalizePublishedProfile(
            profile: WatchThemeProfile,
            allowLegacyReadOnly: Boolean = false
    ): WatchThemeProfile? {
        val constraints = communityThemeConstraints ?: return null
        val id = normalizeUuid(profile.id) ?: return null
        val base = profile.baseFace.takeIf { it in ThemeAppearance.ALLOWED_BASE_FACES }
                ?: return null
        if (base in ArchivedFaces.KEYS) return null
        val definitions = FaceScopedPreferences.SCOPED_DEFINITIONS
        if (profile.settings.keys.any { it !in FaceScopedPreferences.SCOPED_DEFINITIONS_BY_KEY }) {
            return null
        }
        val complete = definitions.associate { definition ->
            val candidate = profile.settings[definition.key]
            val value = if (candidate != null) {
                if (!valueMatchesDefinition(candidate, definition.defaultValue) ||
                        (candidate is WatchThemeValue.Text &&
                                candidate.value.length > MAX_PUBLISHED_SETTING_TEXT_LENGTH) ||
                        !constraints.accepts(
                                definition.key,
                                candidate,
                                allowLegacyReadOnly)) {
                    return null
                }
                candidate
            } else {
                publishedBaseDefault(base, definition.defaultValue, definition.key)
            }
            // Defaults are also part of the public originality baseline. A drift between Android
            // defaults and the canonical contract must reject the profile instead of making a
            // supposedly zero-change theme publishable under a different interpretation.
            if (!constraints.accepts(
                            definition.key,
                            value,
                            allowLegacyReadOnly && candidate != null)) {
                return null
            }
            definition.key to value
        }
        val normalized = profile.copy(
                id = id,
                name = normalizeName(profile.name),
                baseFace = base,
                createdAt = profile.createdAt.coerceAtLeast(0L),
                updatedAt = profile.updatedAt.coerceAtLeast(profile.createdAt.coerceAtLeast(0L)),
                revision = profile.revision.coerceAtLeast(1),
                settings = complete,
                publishedTheme = normalizePublishedThemeSource(profile.publishedTheme))
        // The complete snapshot is what reaches the watch. Reject oversized input before any
        // default preference is written, rather than letting a Data Layer item fail forever.
        return normalized.takeIf(::fitsPublishedSnapshot)
    }

    /** Converts the static base-face default into the typed value stored in a complete profile. */
    private fun publishedBaseDefault(
            baseFace: String,
            defaultValue: Any,
            key: String
    ): WatchThemeValue {
        val faceValue = FaceScopedPreferences.perFaceDefault(baseFace, key)
        return when (defaultValue) {
            is String -> WatchThemeValue.Text(faceValue ?: defaultValue)
            is Boolean -> WatchThemeValue.Flag(faceValue?.toBooleanStrictOrNull() ?: defaultValue)
            is Int -> WatchThemeValue.Number(faceValue?.toIntOrNull() ?: defaultValue)
            else -> error("Unsupported theme value for $key")
        }
    }

    /**
     * Public JSON is intentionally stricter than a user backup. Android's `optInt`/`optBoolean`
     * coerce malformed JSON (including a text value into zero), which is helpful for old backups
     * but unsafe at a network boundary. Each known key needs the exact tag and JSON value type.
     */
    private fun parsePublishedSettings(
            settingsObject: JSONObject?,
            allowLegacyReadOnly: Boolean
    ): Map<String, WatchThemeValue>? {
        val constraints = communityThemeConstraints ?: return null
        settingsObject ?: return null
        if (settingsObject.length() > FaceScopedPreferences.SCOPED_DEFINITIONS.size) return null
        val settings = LinkedHashMap<String, WatchThemeValue>()
        val keys = settingsObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val definition = FaceScopedPreferences.SCOPED_DEFINITIONS_BY_KEY[key] ?: return null
            val value = parseStrictPublishedValue(
                    key,
                    settingsObject.optJSONObject(key),
                    definition.defaultValue,
                    constraints,
                    allowLegacyReadOnly) ?: return null
            settings[key] = value
        }
        return settings
    }

    private fun parseStrictPublishedValue(
            key: String,
            json: JSONObject?,
            defaultValue: Any,
            constraints: CommunityThemeConstraints,
            allowLegacyReadOnly: Boolean
    ): WatchThemeValue? {
        json ?: return null
        val type = json.opt("type") as? String ?: return null
        if (!json.has("value")) return null
        val value = json.opt("value")
        val parsed = when (defaultValue) {
            is String -> (value as? String)
                    ?.takeIf { type == "string" && it.length <= MAX_PUBLISHED_SETTING_TEXT_LENGTH }
                    ?.let(WatchThemeValue::Text)
            is Boolean -> (value as? Boolean)
                    ?.takeIf { type == "boolean" }
                    ?.let(WatchThemeValue::Flag)
            is Int -> (value as? Int)
                    ?.takeIf { type == "int" }
                    ?.let(WatchThemeValue::Number)
            else -> null
        }
        return parsed?.takeIf { constraints.accepts(key, it, allowLegacyReadOnly) }
    }

    private fun fitsPublishedSnapshot(profile: WatchThemeProfile): Boolean =
            profileToJson(profile).toString().toByteArray(Charsets.UTF_8).size <=
                    MAX_PUBLISHED_MATERIALIZED_BYTES

    /**
     * A gallery profile joins, rather than replaces, other face-scoped settings already synced to
     * the watch. Estimate the final snapshot before committing default preferences so a download
     * can never report success while creating a Data Layer payload the watch cannot receive.
     */
    private fun fitsWatchSyncSnapshot(
            defaultPrefs: SharedPreferences,
            profile: WatchThemeProfile
    ): Boolean {
        val projected = defaultPrefs.all
                .filterKeys(::shouldSyncWatchPreference)
                .toMutableMap()
        profile.settings.forEach { (key, value) ->
            projected[FaceScopedPreferences.scopedKey(key, ThemeAppearance.CUSTOM_SCOPE)] =
                    materializedThemeValue(value)
        }
        projected[MiscPreferences.WEAR_SCREEN_FACE.key] = profile.baseFace
        projected[MiscPreferences.WEAR_ACTIVE_CUSTOM_THEME_ID.key] = profile.id
        projected[MiscPreferences.WEAR_CUSTOM_THEME_SCHEMA.key] =
                ThemeAppearance.CURRENT_SCHEMA.toString()
        projected[MiscPreferences.WEAR_CUSTOM_THEME_REVISION.key] = profile.revision.toString()
        projected[MiscPreferences.WEAR_CUSTOM_THEME_COMPLETE.key] = true
        return estimateWatchPreferenceSnapshotBytes(projected) < WATCH_SNAPSHOT_GUARD_BYTES
    }

    private fun materializedThemeValue(value: WatchThemeValue): Any = when (value) {
        is WatchThemeValue.Text -> value.value
        is WatchThemeValue.Flag -> value.value
        is WatchThemeValue.Number -> value.value.toString()
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
        val copyBase = appContext.getString(R.string.watch_theme_copy_name, source)
        return uniqueWatchThemeCopyName(
                copyBase = copyBase,
                existingNames = profiles.map { it.name },
                maxNameLength = MAX_NAME_LENGTH)
    }

    private fun nextRevision(value: Int): Int =
            if (value == Int.MAX_VALUE) 1 else (value + 1).coerceAtLeast(1)

    private fun normalizeUuid(raw: String): String? = try {
        UUID.fromString(raw).toString()
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun normalizePublishedThemeSource(
            source: PublishedThemeSource?
    ): PublishedThemeSource? {
        source ?: return null
        val id = normalizeUuid(source.id) ?: return null
        if (source.revision < 1) return null
        return PublishedThemeSource(id, source.revision)
    }

    /**
     * The one hand-authored Phase-1 Pages profile published before the public vocabulary became
     * canonical. Its Cinema value remains readable only through [parseTrustedLegacyPhaseOneProfile]
     * and only for this immutable public identity; local backups keep using the normal tolerant
     * preference migration path instead.
     */
    private fun isTrustedLegacyPhaseOneProfile(profile: WatchThemeProfile): Boolean =
            profile.id == LEGACY_PHASE_ONE_CINEMA_ID &&
                    profile.baseFace == "poster" &&
                    profile.revision == 1

    /** Public ids never become local primary keys; also avoid the vanishingly unlikely UUID clash. */
    private fun newLocalProfileId(profiles: List<WatchThemeProfile>): String {
        val used = profiles.mapTo(HashSet()) { it.id }
        var id: String
        do {
            id = UUID.randomUUID().toString()
        } while (id in used)
        return id
    }

    /** Public submissions deliberately use a different UUID namespace from the local library. */
    private fun newPublicSubmissionId(localId: String): String {
        var id: String
        do {
            id = UUID.randomUUID().toString()
        } while (id == localId)
        return id
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

    private fun saveState(state: LibraryState): Boolean {
        val committed = libraryPrefs.edit()
                .putString(LIBRARY_JSON, stateToJson(state).toString())
                .commit()
        if (committed) publishAvailableThemes(state.profiles)
        return committed
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

    private fun profileToJson(profile: WatchThemeProfile): JSONObject = JSONObject().apply {
        put("id", profile.id)
        put("name", profile.name)
        put("baseFace", profile.baseFace)
        put("createdAt", profile.createdAt)
        put("updatedAt", profile.updatedAt)
        put("revision", profile.revision)
        put("settings", JSONObject().apply {
            profile.settings.forEach { (key, value) ->
                put(key, valueToJson(value))
            }
        })
        profile.publishedTheme?.let { source ->
            put("publishedTheme", JSONObject()
                    .put("id", source.id)
                    .put("revision", source.revision))
        }
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
                settings = settings,
                publishedTheme = parsePublishedThemeSource(json.optJSONObject("publishedTheme")))
    }

    private fun parsePublishedThemeSource(json: JSONObject?): PublishedThemeSource? {
        json ?: return null
        val id = normalizeUuid(json.optString("id")) ?: return null
        if (!json.has("revision")) return null
        val revision = json.optInt("revision", 0)
        return if (revision >= 1) PublishedThemeSource(id, revision) else null
    }

    private fun parseValue(json: JSONObject?): WatchThemeValue? = when (json?.optString("type")) {
        "string" -> WatchThemeValue.Text(json.optString("value", ""))
        "boolean" -> if (json.has("value")) WatchThemeValue.Flag(json.optBoolean("value")) else null
        "int" -> if (json.has("value")) WatchThemeValue.Number(json.optInt("value")) else null
        else -> null
    }
}
