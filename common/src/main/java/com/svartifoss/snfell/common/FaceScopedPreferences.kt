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
 * Resolution order for a scoped read (see [getString]/[getBoolean]/[getInt]):
 *  1. `"<baseKey>@<face>"` if explicitly set — the user customised this face.
 *  2. `"<baseKey>"` legacy global value — installs from before per-face existed keep their look.
 *  3. [perFaceDefault] — drives the album-accent surface consistency on album faces.
 *  4. the definition's own default value.
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

    fun isScoped(baseKey: String): Boolean = baseKey in SCOPED_KEYS

    fun scopedKey(baseKey: String, face: String): String = baseKey + SCOPE_SEPARATOR + face

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

    fun perFaceDefault(face: String, baseKey: String): String? =
            if (face in ALBUM_ACCENT_FACES) ALBUM_ACCENT_SURFACE_DEFAULTS[baseKey] else null

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
            else -> perFaceDefault(face, def.key) ?: if (prefs.contains(def.key)) {
                prefs.getString(def.key, def.defaultValue) ?: def.defaultValue
            } else {
                def.defaultValue
            }
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

    private fun SharedPreferences.getStringSafe(key: String): String? = try {
        getString(key, null)
    } catch (_: ClassCastException) {
        // Tolerate a value stored as a raw int by some older path.
        try { getInt(key, 0).toString() } catch (_: ClassCastException) { null }
    }
}
