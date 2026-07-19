package com.svartifoss.snfell.common

import android.content.SharedPreferences
import com.matejdro.wearutils.preferences.definition.PreferenceDefinition

/**
 * Watch-tab *appearance* preferences are scoped per now-playing face: the same base key holds an
 * independent value for each face, persisted as `"<baseKey>@<face>"`. Only the appearance keys in
 * [SCOPED_KEYS] are scoped; the face selector ([MiscPreferences.WEAR_SCREEN_FACE]) and every
 * behaviour preference stay global.
 *
 * Kept in `common` so the phone (UI + preview) and the watch resolve identical values. The phone
 * writes scoped keys through a `PreferenceDataStore`; the flat SharedPreferences sync
 * (`PreferencePusher`) then carries every `"<baseKey>@<face>"` entry to the watch unchanged.
 *
 * Resolution order for a built-in scoped string read (see [getString]):
 *  1. `"<baseKey>@<face>"` if explicitly set — the user customised this face.
 *  2. A non-default legacy global album-art style — preserves pre-preset artwork choices.
 *  3. [perFaceDefault] where one exists — drives consistent backgrounds/surfaces per layout.
 *  4. `"<baseKey>"` legacy global value — old installs keep their look for every other key.
 *  5. the definition's own default value.
 * Boolean/integer keys have no per-face override and keep scoped → legacy → definition order.
 * Custom themes are stricter: they read only the fixed custom snapshot, then face/default values.
 */
object FaceScopedPreferences {

    const val SCOPE_SEPARATOR = "@"

    /** Appearance keys scoped per face. Everything else (behaviour, the face selector, history,
     *  ...) stays global. Mirrors the appearance keys declared in `res/xml/watch_face_settings.xml`
     *  minus `wear_screen_face`. */
    val SCOPED_KEYS: Set<String> = setOf(
            "album_art_style",
            "album_art_blur_radius",
            "album_art_dim_strength",
            "ambient_album_art_opacity",
            "dim_album_art",
            "wear_player_shading_style",
            "wear_player_shading_intensity",
            "overlay_blur_radius",
            "always_show_time",
            "screen_buttons_bg_style",
            "screen_buttons_curve_style",
            "screen_buttons_opacity",
            "screen_buttons_shape",
            "wear_album_art_fade",
            "wear_aod_style",
            "wear_aod_art_treatment",
            "wear_aod_color_mode",
            "wear_aod_custom_color",
            "wear_aod_intensity",
            "wear_aod_show_art",
            "wear_aod_show_clock",
            "wear_aod_show_pills",
            "wear_aod_show_progress",
            "wear_aod_show_track_info",
            "wear_aod_show_transport",
            "wear_artist_color_mode",
            "wear_artist_custom_color",
            "wear_artist_desaturated",
            "wear_color_treatment",
            "wear_normal_color",
            "wear_classic_icons_visible",
            "wear_dynamic_accent",
            "wear_edge_progress_visible",
            "wear_edge_seek_enabled",
            "wear_expressive_seek_mode",
            "wear_font",
            "wear_internal_progress_visible",
            "wear_overlay_backdrop_style",
            "wear_progress_color_mode",
            "wear_progress_custom_color",
            "wear_progress_desaturated",
            "wear_progress_style",
            "wear_queue_style",
            // wear_quick_panel_source is deliberately NOT scoped: it drives a phone-side binding
            // (whether the notification's media actions are mirrored), which has no per-face notion.
            "wear_quick_panel_style",
            "wear_quick_panel_layout",
            "wear_quick_panel_color_mode",
            "wear_quick_panel_custom_color",
            "wear_screen_theme",
            "wear_seek_style",
            "wear_seek_layout",
            "wear_show_track_artist",
            "wear_show_track_title",
            "wear_title_text_mode",
            "wear_track_time_mode",
            "wear_volume_style",
            "wear_volume_layout",
            "wear_volume_color_mode",
            "wear_volume_custom_color"
    )

    /** Typed definitions that form one complete materialized appearance snapshot. Derived from
     *  the same export registry used by backup so a new scoped preference cannot silently be
     *  omitted by profile creation/import code. */
    val SCOPED_DEFINITIONS: List<PreferenceDefinition<*>> by lazy {
        MiscPreferences.EXPORTABLE.filter { it.key in SCOPED_KEYS }
    }

    val SCOPED_DEFINITIONS_BY_KEY: Map<String, PreferenceDefinition<*>> by lazy {
        SCOPED_DEFINITIONS.associateBy { it.key }
    }

    fun isScoped(baseKey: String): Boolean = baseKey in SCOPED_KEYS

    fun scopedKey(baseKey: String, face: String): String = baseKey + SCOPE_SEPARATOR + face

    fun scopeFor(context: AppearanceContext): String = when (context) {
        is AppearanceContext.BuiltIn -> context.baseFace
        is AppearanceContext.Custom -> ThemeAppearance.CUSTOM_SCOPE
    }

    /** Whether the active context contains an explicit value for this appearance key. Custom
     *  contexts deliberately never consult a global or base-face value. */
    fun containsExplicitValue(
            prefs: SharedPreferences,
            baseKey: String,
            context: AppearanceContext
    ): Boolean = when (context) {
        is AppearanceContext.BuiltIn ->
            prefs.contains(scopedKey(baseKey, context.baseFace)) || prefs.contains(baseKey)
        is AppearanceContext.Custom ->
            prefs.contains(scopedKey(baseKey, ThemeAppearance.CUSTOM_SCOPE))
    }

    /** Faces whose now-playing look is built from the album-art accent. Their overlay/panel
     *  surfaces default to the matching album-accent styles so the quick panel / volume / seek /
     *  queue read as part of the same composition instead of a neutral grey. */
    private val ALBUM_ACCENT_FACES = setOf(
            "expressive", "vinyl", "poster", "studio", "halo", "aurora", "eclipse", "spectrum", "material")

    /** Per-face default overrides (base key -> value). Only the surface styles differ, and only on
     *  album-accent faces; every other key falls through to its global default. */
    private val ALBUM_ACCENT_SURFACE_DEFAULTS = mapOf(
            "wear_quick_panel_style" to "tonal",
            "wear_queue_style" to "tonal",
            "wear_volume_style" to "tonal",
            "wear_seek_style" to "expressive"
            // wear_overlay_backdrop_style stays "follow": with tonal content it already resolves
            // to SOLID_ALBUM (see OverlayBackdropResolver), so the blur backdrop follows the album.
    )

    fun perFaceDefault(face: String, baseKey: String): String? = when {
        baseKey == MiscPreferences.ALBUM_ART_STYLE.key ->
            PlayerBackgroundStyle.defaultForFace(face).preferenceValue
        face in ALBUM_ACCENT_FACES -> ALBUM_ACCENT_SURFACE_DEFAULTS[baseKey]
        else -> null
    }

    /** `default_config.json` seeds the old global key with `cover` on every install, so key
     * presence alone cannot distinguish a user choice. Only a non-default legacy value is a real
     * override worth preserving ahead of the new per-layout background defaults. */
    fun hasLegacyAlbumArtOverride(prefs: SharedPreferences): Boolean {
        val key = MiscPreferences.ALBUM_ART_STYLE.key
        if (!prefs.contains(key)) return false
        val value = try {
            prefs.getString(key, MiscPreferences.ALBUM_ART_STYLE.defaultValue)
        } catch (_: ClassCastException) {
            null
        }
        return PlayerBackgroundStyle.isLegacyOverrideValue(value)
    }

    fun getString(prefs: SharedPreferences, def: PreferenceDefinition<String>, face: String): String {
        val scoped = scopedKey(def.key, face)
        // An explicit per-face choice always wins. Otherwise, the album-accent surface default
        // (e.g. quick panel/volume/seek "tonal" on Expressive) wins over a pre-existing *global*
        // legacy value: that value was never chosen "for this face" - it predates per-face scoping
        // entirely (every face shared one setting back then) and would otherwise silently keep
        // overriding the new smart default forever, which is exactly why the color-consistency
        // promise didn't hold up in practice. A key with no per-face default (most of them) is
        // unaffected and still falls back to the legacy value, preserving existing installs' look.
        return when {
            prefs.contains(scoped) -> prefs.getString(scoped, def.defaultValue) ?: def.defaultValue
            // Album-art style existed before backgrounds became per-layout presets. Preserve a
            // non-default legacy choice until the user saves a value for this face; `cover` is
            // seeded for every install and therefore cannot be treated as explicit intent.
            def.key == MiscPreferences.ALBUM_ART_STYLE.key && hasLegacyAlbumArtOverride(prefs) ->
                prefs.getString(def.key, def.defaultValue) ?: def.defaultValue
            else -> perFaceDefault(face, def.key) ?: if (prefs.contains(def.key)) {
                prefs.getString(def.key, def.defaultValue) ?: def.defaultValue
            } else {
                def.defaultValue
            }
        }
    }

    /** Resolves a string against a validated appearance context. The built-in branch delegates to
     *  the historical implementation unchanged. A custom snapshot is isolated from both legacy
     *  globals and `key@baseFace`, falling back only to the base preset default and definition. */
    fun getString(
            prefs: SharedPreferences,
            def: PreferenceDefinition<String>,
            context: AppearanceContext
    ): String = when (context) {
        is AppearanceContext.BuiltIn -> getString(prefs, def, context.baseFace)
        is AppearanceContext.Custom -> {
            val scoped = scopedKey(def.key, ThemeAppearance.CUSTOM_SCOPE)
            prefs.getStrictStringSafe(scoped)
                    ?: perFaceDefault(context.baseFace, def.key)
                    ?: def.defaultValue
        }
    }

    fun getBoolean(prefs: SharedPreferences, def: PreferenceDefinition<Boolean>, face: String): Boolean {
        val scoped = scopedKey(def.key, face)
        return when {
            prefs.contains(scoped) -> prefs.getBoolean(scoped, def.defaultValue)
            prefs.contains(def.key) -> prefs.getBoolean(def.key, def.defaultValue)
            else -> def.defaultValue
        }
    }

    fun getBoolean(
            prefs: SharedPreferences,
            def: PreferenceDefinition<Boolean>,
            context: AppearanceContext
    ): Boolean = when (context) {
        is AppearanceContext.BuiltIn -> getBoolean(prefs, def, context.baseFace)
        is AppearanceContext.Custom -> {
            val scoped = scopedKey(def.key, ThemeAppearance.CUSTOM_SCOPE)
            prefs.getBooleanSafe(scoped)
                    ?: perFaceDefault(context.baseFace, def.key)?.toBooleanStrictOrNull()
                    ?: def.defaultValue
        }
    }

    /** Ints are persisted as strings (see wearutils `Preferences.putInt`). */
    fun getInt(prefs: SharedPreferences, def: PreferenceDefinition<Int>, face: String): Int {
        val scoped = scopedKey(def.key, face)
        val raw = when {
            prefs.contains(scoped) -> prefs.getStringSafe(scoped)
            prefs.contains(def.key) -> prefs.getStringSafe(def.key)
            else -> null
        }
        return raw?.toIntOrNull() ?: def.defaultValue
    }

    fun getInt(
            prefs: SharedPreferences,
            def: PreferenceDefinition<Int>,
            context: AppearanceContext
    ): Int = when (context) {
        is AppearanceContext.BuiltIn -> getInt(prefs, def, context.baseFace)
        is AppearanceContext.Custom -> {
            val scoped = scopedKey(def.key, ThemeAppearance.CUSTOM_SCOPE)
            prefs.getStringIntSafe(scoped)
                    ?: perFaceDefault(context.baseFace, def.key)?.toIntOrNull()
                    ?: def.defaultValue
        }
    }

    /** Generic bridge for code materializing a snapshot from [SCOPED_DEFINITIONS]. */
    fun resolveValue(
            prefs: SharedPreferences,
            definition: PreferenceDefinition<*>,
            context: AppearanceContext
    ): Any = when (definition.defaultValue) {
        is String -> {
            @Suppress("UNCHECKED_CAST")
            getString(prefs, definition as PreferenceDefinition<String>, context)
        }
        is Boolean -> {
            @Suppress("UNCHECKED_CAST")
            getBoolean(prefs, definition as PreferenceDefinition<Boolean>, context)
        }
        is Int -> {
            @Suppress("UNCHECKED_CAST")
            getInt(prefs, definition as PreferenceDefinition<Int>, context)
        }
        else -> error("Unsupported scoped preference type for ${definition.key}")
    }

    private fun SharedPreferences.getStringSafe(key: String): String? = try {
        getString(key, null)
    } catch (_: ClassCastException) {
        // Tolerate a value stored as a raw int by some older path.
        try { getInt(key, 0).toString() } catch (_: ClassCastException) { null }
    }

    private fun SharedPreferences.getStrictStringSafe(key: String): String? = try {
        getString(key, null)
    } catch (_: ClassCastException) {
        null
    }

    private fun SharedPreferences.getBooleanSafe(key: String): Boolean? = try {
        if (contains(key)) getBoolean(key, false) else null
    } catch (_: ClassCastException) {
        null
    }

    private fun SharedPreferences.getStringIntSafe(key: String): Int? {
        val rawString = try {
            getString(key, null)
        } catch (_: ClassCastException) {
            null
        }
        if (rawString != null) return rawString.toIntOrNull()

        return try {
            if (contains(key)) getInt(key, 0) else null
        } catch (_: ClassCastException) {
            null
        }
    }
}
