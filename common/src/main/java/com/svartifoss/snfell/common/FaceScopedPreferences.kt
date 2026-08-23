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
            // A shared piece rather than one face's fixture - see AccentFloorStyle.
            "wear_accent_floor",
            // Per face, not per app: "keep the screen on" is a property of the composition you are
            // looking at, not of the watch. A face you read (Verse, Chat) wants it; the one you
            // glance at does not, and paying its battery cost on every face to have it on one is
            // not a trade anyone would choose.
            "wear_keep_screen_on",
            "album_art_style",
            "album_art_blur_radius",
            "album_art_dim_strength",
            "ambient_album_art_opacity",
            "dim_album_art",
            "wear_player_shading_style",
            "wear_player_shading_intensity",
            "wear_shading_color_mode",
            "wear_shading_custom_color",
            "overlay_blur_radius",
            "always_show_time",
            "screen_buttons_bg_style",
            "screen_buttons_curve_style",
            "screen_buttons_opacity",
            "screen_buttons_shape",
            // The odd one out until now: the mini-button row's vertical offset was the only
            // screen_buttons_* key left global, so a row nudged up to clear one face's transport
            // controls moved on every other face too. It is layout, and layout differs per face -
            // Carousel hides the row entirely, Classic and Expressive put different things under
            // it. An existing global value still resolves as the legacy fallback, so nobody's
            // current position moves until they set one for a face.
            "screen_buttons_bottom_offset",
            // The odd one out until now: the mini-button row's vertical offset was the only
            // screen_buttons_* key left global, so a row nudged up to clear one face's transport
            // controls moved on every other face too. It is layout, and layout differs per face -
            // Carousel hides the row entirely, Classic and Expressive put different things under
            // it. An existing global value still resolves as the legacy fallback, so nobody's
            // current position moves until they set one for a face.
            // The odd one out until now: the mini-button row's vertical offset was the only
            // screen_buttons_* key left global, so a row nudged up to clear one face's transport
            // controls moved on every other face too. It is layout, and layout differs per face -
            // Carousel hides the row entirely, Classic and Expressive put different things under
            // it. An existing global value still resolves as the legacy fallback, so nobody's
            // current position moves until they set one for a face.
            // The odd one out until now: the mini-button row's vertical offset was the only
            // screen_buttons_* key left global, so a row nudged up to clear one face's transport
            // controls moved on every other face too. It is layout, and layout differs per face -
            // Carousel hides the row entirely, Classic and Expressive put different things under
            // it. An existing global value still resolves as the legacy fallback, so nobody's
            // current position moves until they set one for a face.
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
            "wear_clock_color_mode",
            "wear_clock_custom_color",
            "wear_clock_opacity",
            // Scoped like every other clock control and like "wear_font" itself: a clock typeface
            // is chosen against one face's composition, so carrying it onto another is the same
            // mistake the per-face scoping exists to prevent.
            "wear_clock_font",
            // The lyrics typeface, for the lyrics screen and the Verse face alike. Scoped like
            // every other appearance key so a saved theme carries it - see WEAR_LYRICS_FONT.
            "wear_lyrics_font",
            // The metadata face's blocks - see TrackMetadataFields.Group. Scoped, so a theme built
            // around that face can show everything while another theme keeps only the essentials.
            "wear_metadata_show_core",
            "wear_metadata_show_release",
            "wear_metadata_show_credits",
            "wear_metadata_show_identifiers",
            "wear_metadata_show_technical",
            "wear_metadata_show_playback",
            // Split's own backdrop control. Scoped like every other appearance key, so a saved
            // theme built on Split carries the panel treatment it was designed with.
            "wear_split_panel",
            "wear_clock_font_weight",
            "wear_clock_font_italic",
            "wear_clock_font_scale",
            "wear_clock_font_tracking",
            "wear_clock_adaptive_contrast",
            "wear_artist_color_mode",
            "wear_artist_custom_color",
            "wear_artist_desaturated",
            "wear_color_treatment",
            "wear_normal_color",
            // Pairs with wear_normal_color and has to be scoped with it: the Normal treatment is a
            // per-face choice, so "one colour or a derived palette" is too. Left global it was also
            // the one key on the Watch face screen that changed every face at once, which is
            // exactly what AppearancePreferenceScopingTest exists to catch.
            "wear_normal_color_multi",
            "wear_color_modifier",
            "wear_color_hue_shift",
            "wear_album_accent_source",
            "wear_title_font_weight",
            "wear_title_font_italic",
            "wear_title_font_scale",
            "wear_title_font_opacity",
            "wear_title_font_tracking",
            "wear_artist_font_weight",
            "wear_artist_font_italic",
            "wear_artist_font_scale",
            "wear_artist_font_opacity",
            "wear_artist_font_tracking",
            "wear_artist_adaptive_contrast",
            "wear_title_color_mode",
            "wear_title_custom_color",
            "wear_title_adaptive_contrast",
            "wear_progress_gradient",
            "wear_source_icon_scale",
            "wear_source_icon_opacity",
            "wear_font_flex_width",
            "wear_font_flex_optical_size",
            "wear_font_flex_grade",
            "wear_font_flex_roundness",
            "wear_classic_icons_visible",
            "wear_dynamic_accent",
            "wear_edge_progress_visible",
            "wear_edge_seek_enabled",
            "wear_expressive_seek_mode",
            "wear_font",
            "wear_carousel_card_shape",
            "wear_internal_progress_visible",
            "wear_overlay_backdrop_style",
            "wear_progress_color_mode",
            "wear_progress_custom_color",
            "wear_progress_desaturated",
            "wear_progress_style",
            "wear_queue_style",
            "wear_quick_panel_shortcut_cover",
            "wear_list_row_size",
            "wear_mini_buttons_mode",
            "wear_gestures_mode",
            // wear_quick_panel_source is deliberately NOT scoped: it drives a phone-side binding
            // (whether the notification's media actions are mirrored), which has no per-face notion.
            "wear_quick_panel_style",
            "wear_quick_panel_layout",
            "wear_up_next_pill_style",
            "wear_show_up_next_pill",
            "wear_quick_panel_color_mode",
            "wear_quick_panel_custom_color",
            "wear_screen_theme",
            "wear_quadrant_tap_flash",
            "wear_seek_style",
            "wear_seek_layout",
            "wear_show_source_icon",
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

    /**
     * Faces that compose the whole screen themselves, edge to edge, and so cannot share the band
     * the standard chrome expects to own.
     *
     * Chat's thread runs to the bottom of the screen and carries its own two round actions; Split's
     * lower half is a solid panel holding the track text. On both, the mini-button row lands on top
     * of the composition rather than beside it. Neither wants the edge progress arc either - Chat's
     * waveform already *is* the progress bar, and on Split an arc around a two-tone card reads as a
     * stray ring.
     */
    private val SELF_COMPOSED_FACES = setOf("split", "verse")

    /** Defaults only - both keys stay face-scoped and switchable like any other appearance key, so
     *  a user who wants the mini buttons back on these faces simply turns them on. */
    private val SELF_COMPOSED_DEFAULTS = mapOf(
            MiscPreferences.WEAR_MINI_BUTTONS_MODE.key to ActivityVisibility.NEVER,
            MiscPreferences.WEAR_EDGE_PROGRESS_VISIBLE.key to "false"
    )

    /**
     * Verse's own additions on top of [SELF_COMPOSED_DEFAULTS].
     *
     * The accent floor is a *default* here, not a fixture: the treatment is a shared piece any face
     * can wear (see [AccentFloorStyle]), and this face simply ships wearing it because its
     * composition was designed around one. Turning it off on Verse is as ordinary as turning it on
     * anywhere else.
     */
    private val VERSE_DEFAULTS = SELF_COMPOSED_DEFAULTS + mapOf(
            MiscPreferences.WEAR_ACCENT_FLOOR.key to AccentFloorStyle.STANDARD.preferenceValue
    )

    /**
     * Split's own additions on top of [SELF_COMPOSED_DEFAULTS]. The source-app badge is not a
     * decoration on this face - it is the notification card's app icon, the element that makes the
     * composition read as a card at all - so it defaults on rather than following the global
     * preference, which a user may well have turned off for the faces where it *is* decoration.
     */
    private val SPLIT_DEFAULTS = SELF_COMPOSED_DEFAULTS +
            (MiscPreferences.WEAR_SHOW_SOURCE_ICON.key to "true")

    /**
     * Note's one override. It leaves the bottom band free, so unlike [SELF_COMPOSED_FACES] its
     * mini-button row stays on - that row is how anything is reached on a face with no controls of
     * its own. The edge arc still goes, because a ring around a deliberately empty screen is the
     * one decoration this composition cannot absorb.
     */
    private val NOTE_DEFAULTS = mapOf(
            MiscPreferences.WEAR_EDGE_PROGRESS_VISIBLE.key to "false"
    )

    /**
     * Chat used to sit in [SELF_COMPOSED_FACES] for the same reason the others do - its thread runs
     * to the bottom of the screen, and the shared mini-button row landed on top of the round
     * actions already there.
     *
     * It no longer needs that. The face *hosts* the row now (see
     * [MiniButtonPlacement.isHostedByFace]): its own circles are the configured mini buttons, one
     * per slot, so there is nothing left to collide with, and defaulting them off would only hide
     * the buttons a user configured from the one face built to show them. The edge arc still goes,
     * because that is a full-screen decoration and the composition does run edge to edge.
     */
    private val CHAT_DEFAULTS = mapOf(
            MiscPreferences.WEAR_EDGE_PROGRESS_VISIBLE.key to "false"
    )

    fun perFaceDefault(face: String, baseKey: String): String? = when {
        baseKey == MiscPreferences.ALBUM_ART_STYLE.key ->
            PlayerBackgroundStyle.defaultForFace(face).preferenceValue
        face == "chat" -> CHAT_DEFAULTS[baseKey]
        face == "split" -> SPLIT_DEFAULTS[baseKey]
        face == "note" -> NOTE_DEFAULTS[baseKey]
        face == "verse" -> VERSE_DEFAULTS[baseKey]
        face in SELF_COMPOSED_FACES -> SELF_COMPOSED_DEFAULTS[baseKey]
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

    /**
     * The boolean twin of [getString], and it must walk the **same** four steps: explicit per-face
     * value, then [perFaceDefault], then the legacy global, then the definition default.
     *
     * The `perFaceDefault` step was missing here for built-in faces while the Custom branch below
     * had it, and the phone's `FaceScopedPreferenceDataStore` consulted it on both. The result was
     * a face default that the settings screen honoured and the watch ignored: the switch read off
     * while the watch drew the element anyway, and it only came right once the user toggled it -
     * because *that* wrote an explicit `key@face` entry, which is the one branch that did work.
     * It went unnoticed for as long as no boolean had a per-face default.
     */
    fun getBoolean(prefs: SharedPreferences, def: PreferenceDefinition<Boolean>, face: String): Boolean {
        val scoped = scopedKey(def.key, face)
        return when {
            prefs.contains(scoped) -> prefs.getBoolean(scoped, def.defaultValue)
            else -> perFaceDefault(face, def.key)?.toBooleanStrictOrNull()
                    ?: if (prefs.contains(def.key)) {
                        prefs.getBoolean(def.key, def.defaultValue)
                    } else {
                        def.defaultValue
                    }
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
