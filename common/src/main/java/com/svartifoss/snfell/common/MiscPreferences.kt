package com.svartifoss.snfell.common

import android.content.SharedPreferences
import com.svartifoss.snfell.common.model.AutoStartMode
import com.matejdro.wearutils.preferences.definition.EnumPreferenceDefinition
import com.matejdro.wearutils.preferences.definition.PreferenceDefinition
import com.matejdro.wearutils.preferences.definition.Preferences
import com.matejdro.wearutils.preferences.definition.SimplePreferenceDefinition

object MiscPreferences {
    val ALWAYS_SHOW_TIME: PreferenceDefinition<Boolean>
            = SimplePreferenceDefinition("always_show_time", false)

    val PAUSE_ON_SWIPE_EXIT: PreferenceDefinition<Boolean> = SimplePreferenceDefinition("pause_on_swipe_exit", false)

    val ROTATING_CROWN_OFF_PERIOD: PreferenceDefinition<Int> = SimplePreferenceDefinition("rotating_crown_off_period", 300)

    // Default kept gentle on purpose - at 100% a single accidental nudge of the crown (e.g.
    // while taking a screenshot) changes volume by a full volumeStep, which is too noticeable.
    val ROTATING_CROWN_SENSITIVITY: PreferenceDefinition<Int> = SimplePreferenceDefinition("rotating_crown_sensitivity", 40)

    // When enabled, the rotary crown scrubs the playback timeline instead of changing volume.
    val ROTARY_SEEK: PreferenceDefinition<Boolean> = SimplePreferenceDefinition("rotary_seek", false)

    val HAPTIC_FEEDBACK: PreferenceDefinition<Boolean> = SimplePreferenceDefinition("haptic_feedback", true)

    val DISABLE_PHYSICAL_DOUBLE_CLICK_IN_AMBIENT: PreferenceDefinition<Boolean> = SimplePreferenceDefinition("disable_ambient_physical_double_click", false)

    // Legacy boolean superseded by AUTO_START_MODE. Kept only so the one-time migration in
    // MiscSettingsFragment can read and delete it. Deliberately absent from EXPORTABLE: exporting
    // it would let a restore resurrect the key the migration is meant to remove.
    val AUTO_START: PreferenceDefinition<Boolean> = SimplePreferenceDefinition("auto_start", false)

    val AUTO_START_MODE: EnumPreferenceDefinition<AutoStartMode> = EnumPreferenceDefinition("auto_start_mode", AutoStartMode.OFF)

    val AUTO_START_APP_BLACKLIST: PreferenceDefinition<Set<String>> = SimplePreferenceDefinition("auto_start_apps_blacklist", emptySet())

    val CLOSE_TIMEOUT: PreferenceDefinition<Int> = SimplePreferenceDefinition("close_timeout", 0)

    val ENABLE_NOTIFICATION_POPUP: PreferenceDefinition<Boolean> = SimplePreferenceDefinition("enable_notification_popup", false)

    val NOTIFICATION_TIMEOUT: PreferenceDefinition<Int> = SimplePreferenceDefinition("notification_timeout", 10)

    val ALWAYS_SELECT_CENTER_ACTION: PreferenceDefinition<Boolean> = SimplePreferenceDefinition("always_select_center_action", false)

    val LAST_MENU_DISPLAYED: PreferenceDefinition<String> = SimplePreferenceDefinition("last_menu_displayed", "-1")

    val DIM_ALBUM_ART: PreferenceDefinition<Boolean> = SimplePreferenceDefinition("dim_album_art", true)

    /** How the now-playing screen background is rendered on the watch. */
    val ALBUM_ART_STYLE: PreferenceDefinition<String> = SimplePreferenceDefinition("album_art_style", "cover")

    /** Blur radius in pixels when album art style is set to blur (API 31+ GPU blur). */
    val ALBUM_ART_BLUR_RADIUS: PreferenceDefinition<Int> = SimplePreferenceDefinition("album_art_blur_radius", 35)

    /** Legacy free-form dim percentage, retained so old backups remain readable. */
    val ALBUM_ART_DIM_STRENGTH: PreferenceDefinition<Int> = SimplePreferenceDefinition("album_art_dim_strength", 80)

    /** Artwork/player shading selected independently from the structural face. */
    val WEAR_PLAYER_SHADING_STYLE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_player_shading_style", "follow")

    /** Shared named strength: soft, balanced or strong. */
    val WEAR_PLAYER_SHADING_INTENSITY: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_player_shading_intensity", "balanced")

    /** How long volume/seek overlays stay visible after adjustment (milliseconds). */
    val VOLUME_OVERLAY_TIMEOUT: PreferenceDefinition<Int> = SimplePreferenceDefinition("volume_overlay_timeout", 1000)

    /** Minimum rotary crown movement before volume/seek changes apply. */
    val ROTARY_DEADZONE: PreferenceDefinition<Int> = SimplePreferenceDefinition("rotary_deadzone", 6)

    /** Album art opacity in ambient mode (20–100%). */
    val AMBIENT_ALBUM_ART_OPACITY: PreferenceDefinition<Int> = SimplePreferenceDefinition("ambient_album_art_opacity", 55)

    // --- Always-on display (ambient mode) ---

    /** How the always-on display renders the now-playing screen: "follow" (match the selected
     *  [WEAR_SCREEN_FACE], including every curated Compose face), "classic", "expressive" or
     *  "minimal" (pure black with dimmed text only -
     *  the biggest battery saver). All variants stay burn-in-audited: outlined/dim rendering,
     *  no animations, and the shared pixel-jiggle applies to every one of them. */
    val WEAR_AOD_STYLE: PreferenceDefinition<String> = SimplePreferenceDefinition("wear_aod_style", "follow")

    /** Show the (dimmed) album art on the always-on display. Off = pure black background,
     *  which is markedly cheaper on AMOLED. [AMBIENT_ALBUM_ART_OPACITY] applies when on;
     *  the "minimal" [WEAR_AOD_STYLE] never shows art regardless. */
    val WEAR_AOD_SHOW_ART: PreferenceDefinition<Boolean> = SimplePreferenceDefinition("wear_aod_show_art", true)

    /** Ambient artwork treatment: "blur" (historical default), "clear",
     *  "monochrome_blur", or "follow" (reuse [ALBUM_ART_STYLE]). */
    val WEAR_AOD_ART_TREATMENT: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_aod_art_treatment", AodArtTreatment.BLUR.preferenceValue)

    /** Show the clock on the always-on display. */
    val WEAR_AOD_SHOW_CLOCK: PreferenceDefinition<Boolean> = SimplePreferenceDefinition("wear_aod_show_clock", true)

    /** Show the track title/artist on the always-on display. */
    val WEAR_AOD_SHOW_TRACK_INFO: PreferenceDefinition<Boolean> =
            SimplePreferenceDefinition("wear_aod_show_track_info", true)

    /** Where the expressive AOD's outlines and glyphs take their color from: "white" (neutral),
     *  "album" (the dynamic album accent, lifted for legibility on black - the Wear OS 6
     *  reference look) or "custom" ([WEAR_AOD_CUSTOM_COLOR]). Text stays white for legibility. */
    val WEAR_AOD_COLOR_MODE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_aod_color_mode", "white")

    /** Hex color (#RRGGBB) used when [WEAR_AOD_COLOR_MODE] is "custom". */
    val WEAR_AOD_CUSTOM_COLOR: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_aod_custom_color", "")

    /** Show the outlined prev / play-pause / next row on the expressive AOD. Off = only text
     *  and clock, for the leanest always-on screen. */
    val WEAR_AOD_SHOW_TRANSPORT: PreferenceDefinition<Boolean> =
            SimplePreferenceDefinition("wear_aod_show_transport", true)

    /** Show the progress ring around the play button on the expressive AOD (position only
     *  refreshes about once a minute there). */
    val WEAR_AOD_SHOW_PROGRESS: PreferenceDefinition<Boolean> =
            SimplePreferenceDefinition("wear_aod_show_progress", true)

    /** Show the static outlined Up Next pill on supported visual AOD styles. */
    val WEAR_AOD_SHOW_PILLS: PreferenceDefinition<Boolean> =
            SimplePreferenceDefinition("wear_aod_show_pills", true)

    /** Overall AOD brightness (20-100%): scales the alpha of the expressive AOD's outlines,
     *  glyphs and text, and of the classic/minimal AOD text block. Lower = dimmer = cheaper. */
    val WEAR_AOD_INTENSITY: PreferenceDefinition<Int> = SimplePreferenceDefinition("wear_aod_intensity", 100)

    // --- Wear OS experience toggles (configured on phone, synced to watch) ---

    /** Long-press the center of the now-playing screen to open the playback queue. */
    val WEAR_CENTER_LONG_PRESS_QUEUE: PreferenceDefinition<Boolean> =
            SimplePreferenceDefinition("wear_center_long_press_queue", false)

    /** Developer-only diagnostic overlay: outline the bounds of visible player elements. This is
     * global (not face-scoped) and intentionally excluded from config backup/export. */
    val WEAR_DEV_SHOW_LAYOUT_BOUNDS: PreferenceDefinition<Boolean> =
            SimplePreferenceDefinition("wear_dev_show_layout_bounds", false)

    /** Developer-only diagnostic overlay: show live face/playback/render information. Global and
     * deliberately excluded from normal config backups. */
    val WEAR_DEV_SHOW_PLAYER_INFO: PreferenceDefinition<Boolean> =
            SimplePreferenceDefinition("wear_dev_show_player_info", false)

    // There is deliberately no complication content preference: the watch-face complication is
    // image-only (album cover), always - text complication types were dropped because whether a
    // face renders an attached cover on a text slot is up to its renderer, and a setting around
    // that just produced "broken-looking" combinations. See AlbumArtComplicationDataSourceService.

    /** Which structural face renders the now-playing screen: classic/expressive plus the curated
     *  vinyl, poster, studio, halo, aurora, eclipse and spectrum layouts. [WEAR_SCREEN_THEME] is
     *  the universal button treatment layered over that structure without changing its input map. */
    val WEAR_SCREEN_FACE: PreferenceDefinition<String> = SimplePreferenceDefinition("wear_screen_face", "classic")

    /** Show the current song title on the interactive player. Status/error messages are kept
     *  visible even when this is off so disabling metadata never hides important feedback. */
    val WEAR_SHOW_TRACK_TITLE: PreferenceDefinition<Boolean> =
            SimplePreferenceDefinition("wear_show_track_title", true)

    /** Show the current artist on the interactive player. Playback/error status text is not an
     *  artist name and remains available when this is off. */
    val WEAR_SHOW_TRACK_ARTIST: PreferenceDefinition<Boolean> =
            SimplePreferenceDefinition("wear_show_track_artist", true)

    /** Draw the built-in player controls on every interactive layout. Touch targets and
     *  accessibility actions remain active when the visuals are hidden, allowing a clean face
     *  without silently changing the user's input map. The persisted key intentionally keeps its
     *  original Classic-only name so existing installs and imported backups migrate losslessly. */
    val WEAR_PLAYER_CONTROLS_VISIBLE: PreferenceDefinition<Boolean> =
            SimplePreferenceDefinition("wear_classic_icons_visible", true)

    /** Source-compatible alias for code that still uses the old, Classic-only name. Do not add
     *  this alias separately to [EXPORTABLE] because it points at the exact same stored value. */
    @Deprecated("Use WEAR_PLAYER_CONTROLS_VISIBLE")
    val WEAR_CLASSIC_ICONS_VISIBLE: PreferenceDefinition<Boolean> = WEAR_PLAYER_CONTROLS_VISIBLE

    /** Show optional progress indicators owned by curated compositions, such as Studio's line.
     *  Expressive's cookie ring and Material's center-control ring are structural and deliberately
     *  ignore this preference. The bezel arc remains independently controlled by
     *  [WEAR_EDGE_PROGRESS_VISIBLE]. */
    val WEAR_INTERNAL_PROGRESS_VISIBLE: PreferenceDefinition<Boolean> =
            SimplePreferenceDefinition("wear_internal_progress_visible", true)

    /** Draw the universal playback-progress ring at the screen edge, regardless of the selected
     *  player layout. */
    val WEAR_EDGE_PROGRESS_VISIBLE: PreferenceDefinition<Boolean> =
            SimplePreferenceDefinition("wear_edge_progress_visible", true)

    /** Let touches/drags at the screen edge seek. Kept separate from visibility so the user can
     *  keep an invisible edge scrub target or a visible, display-only progress ring. */
    val WEAR_EDGE_SEEK_ENABLED: PreferenceDefinition<Boolean> =
            SimplePreferenceDefinition("wear_edge_seek_enabled", true)

    /** How the expressive face exposes drag-to-seek by touch: "central" (the progress ring around
     *  the cookie play button becomes draggable), "edge" (the classic bezel seek ring is shown on
     *  the expressive face too) or "none" (display-only; seek only via the rotary crown). Only the
     *  expressive face is affected — the classic face always uses the edge ring. */
    val WEAR_EXPRESSIVE_SEEK_MODE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_expressive_seek_mode", "central")

    /** Universal interactive-player control style: default, minimal, compact, cinema, vivid,
     *  contrast, amoled, or hidden (control icons drawn fully transparent while their touch
     *  targets and accessibility actions stay active). AOD and secondary surfaces keep their own
     *  independent appearance settings. */
    val WEAR_SCREEN_THEME: PreferenceDefinition<String> = SimplePreferenceDefinition("wear_screen_theme", "default")

    /** Typeface for title/artist text on every player layout. The catalog combines the bundled
     *  Google Sans/Love Letter faces with Android system-family aliases (rounded, light, thin,
     *  medium, black, small caps, casual, serif, mono, condensed and cursive). The older bundled
     *  "typewriter" choice remains readable but is hidden unless developer archived options are
     *  enabled. Decoded by watchFontFamily and mirrored by WatchPreviewView. */
    val WEAR_FONT: PreferenceDefinition<String> = SimplePreferenceDefinition("wear_font", "google_sans")

    /** When the track position ("1:23 / 3:45") is shown on the now-playing screen: "always",
     *  "playing" (only while music plays), "paused" (only while paused) or "never". */
    val WEAR_TRACK_TIME_MODE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_track_time_mode", "always")

    /** Extract accent color from album art on the watch (when off, uses the static theme accent). */
    val WEAR_DYNAMIC_ACCENT: PreferenceDefinition<Boolean> = SimplePreferenceDefinition("wear_dynamic_accent", true)

    /** One color policy for the complete interactive watch UI. "normal" uses the user's fixed
     * [WEAR_NORMAL_COLOR], "desaturated" derives a softened accent from the current cover and
     * "expressive" uses the full album palette. This supersedes the old independent
     * artist/progress switches, which remain readable only for migration. */
    val WEAR_COLOR_TREATMENT: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_color_treatment", "expressive")

    /** Hex color (#RRGGBB) used by the unified "normal" color treatment. */
    val WEAR_NORMAL_COLOR: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_normal_color", "")

    /** How the now-playing title text behaves when it doesn't fit its available width: "smart"
     *  (default - shrinks first, wraps to 2 lines if that helps, and only scrolls as a last
     *  resort), "marquee" (always a single line at full size, scrolling if it overflows), "wrap"
     *  (fixed size, wraps up to 2 lines, ellipsizes beyond that - never shrinks or scrolls) or
     *  "shrink" (word-safe shrink down to a floor size, ellipsizes beyond that - never scrolls). */
    val WEAR_TITLE_TEXT_MODE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_title_text_mode", "smart")

    /** Per-surface treatment for artist text: "follow" inherits [WEAR_COLOR_TREATMENT], while
     *  "normal", "desaturated" and "expressive" override it for this target only. Historical
     *  "neutral"/"album"/"custom" values are still accepted by the watch and migrated by the
     *  phone settings UI. */
    val WEAR_ARTIST_COLOR_MODE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_artist_color_mode", "follow")

    /** Hex color (#RRGGBB) used when [WEAR_ARTIST_COLOR_MODE] is "custom". */
    val WEAR_ARTIST_CUSTOM_COLOR: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_artist_custom_color", "")

    /** Legacy switch retained for migration from the old Album + Desaturated combination. */
    val WEAR_ARTIST_DESATURATED: PreferenceDefinition<Boolean> =
            SimplePreferenceDefinition("wear_artist_desaturated", false)

    /** Per-surface treatment for progress/seek chrome. Values match
     *  [WEAR_ARTIST_COLOR_MODE]. */
    val WEAR_PROGRESS_COLOR_MODE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_progress_color_mode", "follow")

    /** Hex color (#RRGGBB) used when [WEAR_PROGRESS_COLOR_MODE] is "custom". */
    val WEAR_PROGRESS_CUSTOM_COLOR: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_progress_custom_color", "")

    /** Legacy switch retained for migration from the old Album + Desaturated combination. */
    val WEAR_PROGRESS_DESATURATED: PreferenceDefinition<Boolean> =
            SimplePreferenceDefinition("wear_progress_desaturated", false)

    /** Per-surface treatment and optional Normal color for the volume overlay. */
    val WEAR_VOLUME_COLOR_MODE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_volume_color_mode", "follow")

    val WEAR_VOLUME_CUSTOM_COLOR: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_volume_custom_color", "")

    /** Per-surface treatment and optional Normal color for Quick Actions. */
    val WEAR_QUICK_PANEL_COLOR_MODE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_quick_panel_color_mode", "follow")

    val WEAR_QUICK_PANEL_CUSTOM_COLOR: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_quick_panel_custom_color", "")

    /** Cross-fade album art when the track changes. */
    val WEAR_ALBUM_ART_FADE: PreferenceDefinition<Boolean> = SimplePreferenceDefinition("wear_album_art_fade", true)

    /** Preferred distance (dp) between the bottom of the watch screen and the mini-buttons row
     *  (see ScreenButtons); the watch still auto-adjusts away from long titles on its own. The
     *  42dp default clears the bottom quadrant icon and keeps the full 3-button row inside a
     *  round screen's circle. */
    val WEAR_SCREEN_BUTTONS_OFFSET: PreferenceDefinition<Int> =
            SimplePreferenceDefinition("screen_buttons_bottom_offset", 42)

    /** How the mini-buttons row follows a round screen's curvature: "flat" (straight row),
     *  "arc" (side buttons raised along the bezel but kept upright), "curved_soft" (raised +
     *  half the tangent tilt) or "curved" (raised + full tangent tilt). Ignored on square
     *  screens. */
    val WEAR_SCREEN_BUTTONS_CURVE_STYLE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("screen_buttons_curve_style", "flat")

    /** Mini-button pill background. "glass" follows the selected layout (a per-face treatment);
     *  the other values render identically on every face so the appearance is the user's choice:
     *  "uniform_glass", "uniform_glass_light", "translucent_album", "glow_album",
     *  "solid_theme", "solid_album", "outline", "transparent" (icon only), plus the explicit
     *  Expressive-palette variants "solid_exp_album", "outline_exp_album", "icon_exp",
     *  "glow_exp" and "translucent_album_exp". */
    val WEAR_SCREEN_BUTTONS_BG: PreferenceDefinition<String> =
            SimplePreferenceDefinition("screen_buttons_bg_style", "glass")

    /** Opacity of the complete mini-buttons group (backgrounds, outlines and icons), 0-100%. */
    val WEAR_SCREEN_BUTTONS_OPACITY: PreferenceDefinition<Int> =
            SimplePreferenceDefinition("screen_buttons_opacity", 100)

    /** Mini-button shape. "pill" (default capsule), "circle" (equal width/height pill),
     *  "square", "rounded_square_soft", "rounded_square_medium", "pill_wide_small",
     *  "pill_wide_medium", "pill_wide_large", "pill_wide_xlarge", "rounded_rect_small",
     *  "rounded_rect_medium", "rounded_rect_large", "leaf", "drop", "squircle". */
    val WEAR_SCREEN_BUTTONS_SHAPE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("screen_buttons_shape", "pill")



    // The quick actions panel (double-tap center) is configured entirely through the
    // QuickPanelButtons ButtonInfo slots - no preferences involved.

    /** Blur radius (px) for the full-screen acrylic backdrop behind the volume/seek rings and
     *  the quick actions panel - independent from [ALBUM_ART_BLUR_RADIUS], which only styles
     *  the now-playing background when the blurred album art styles are selected. */
    val WEAR_OVERLAY_BLUR_RADIUS: PreferenceDefinition<Int> =
            SimplePreferenceDefinition("overlay_blur_radius", 35)

    /** Full-screen background behind volume, seek and quick actions. Kept independent from the
     *  styles of their arcs, readouts and buttons; "follow" preserves style-driven behaviour. */
    val WEAR_OVERLAY_BACKDROP_STYLE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_overlay_backdrop_style", "follow")

    // --- Selectable visual styles for the overlay surfaces. All share one vocabulary:
    //   "glass"    - frosted translucent panels over the blur backdrop (the original look).
    //   "minimal"  - pure-black AMOLED, hairline accent outlines, thin marks, no blur.
    //   "material" - solid dark-grey Material Design 2 surfaces with rounded corners + a thumb.
    //   "tonal"    - large rounded containers tinted in the album accent (matches the expressive
    //                face).
    //   "neon"     - transparent surfaces with glowing album-accent outlines and accent glyphs.
    //   "light"    - light surfaces with dark text/icons (a light-theme counterpoint).
    //   "gradient" - fills painted with real primary/secondary album swatches.
    //   "mono"     - neutral greyscale, ignoring the album accent entirely.
    //   "outline"  - thick white cartoon outlines over transparent fills.
    //   "duotone"  - two-hue: real primary/secondary swatches from the album art.
    //   "contrast" - pure black/white, thick strokes (high-contrast/accessibility).
    //   "terminal" - sharp-cornered monochrome green CRT look, accent forced to green.
    //   "frost"    - light translucent frosted panels (a light-glass variant).
    //   "prism"    - rich three-swatch album spectrum with a slim glass highlight.
    //   "segments" - the arc broken into discrete tick blocks, lighting up like a level meter.
    //   "aurora"   - multi-hue gradient from three album swatches (northern-lights look).
    //   "ink"      - wide translucent accent halo with a thin solid core (wet-ink stroke).
    //   "groove"   - recessed dark channel with a slim bright accent core running inside it.
    // Not every surface offers every style; see each surface's *Style enum + fromPref for the
    // exact set it interprets (the volume arc has the widest vocabulary).

    /** Visual style of the volume overlay (arc on the left edge). */
    val WEAR_VOLUME_STYLE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_volume_style", "glass")

    /** Geometry of the volume panel: bezel edge arc, centered halo, or horizontal meter. */
    val WEAR_VOLUME_LAYOUT: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_volume_layout", "edge")

    /** Visual style of the quick-actions panel opened by double-tapping the screen centre. */
    val WEAR_QUICK_PANEL_STYLE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_quick_panel_style", "glass")

    /** Composition of quick actions: metadata first, actions first, or a compact action deck. */
    val WEAR_QUICK_PANEL_LAYOUT: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_quick_panel_layout", "stacked")

    /** Whether the quick panel follows the active app's MediaSession actions or its manual slots. */
    val WEAR_QUICK_PANEL_SOURCE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_quick_panel_source", "manual")

    /** Visual style of the playback queue screen. */
    val WEAR_QUEUE_STYLE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_queue_style", "glass")

    /** Visual style of the edge progress/seek ring: "solid" (default), "dashed", "dots",
     *  "hairline", "comet", or one of the clock-index styles (60 dots, 60 ticks, 12 segments) -
     *  see RingStyle on the watch. */
    val WEAR_PROGRESS_STYLE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_progress_style", "solid")

    /** Visual style of the scrub-time readout shown while seeking: "plain" (default), "pill",
     *  "expressive", "material", "white", "giant" or "split" (position stacked over total). */
    val WEAR_SEEK_STYLE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_seek_style", "plain")

    /** Geometry of the seek overlay: bezel ring, continuous timeline, or segmented timeline. */
    val WEAR_SEEK_LAYOUT: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_seek_layout", "edge")

    /** Manual Firebase Crashlytics report upload on the phone. Enabled by default, but always
     *  user-controlled and applied before any Crashlytics logging tree is installed. This local
     *  consent choice is deliberately excluded from config backup/import. */
    val CRASH_REPORTING_ENABLED: PreferenceDefinition<Boolean> =
            SimplePreferenceDefinition("crash_reporting_enabled", true)

    fun isAnyKindOfAutoStartEnabled(preferences: SharedPreferences): Boolean {
        return Preferences.getBoolean(preferences, AUTO_START) || Preferences.getEnum(preferences, AUTO_START_MODE) != AutoStartMode.OFF
    }

    /** Every preference that represents a configurable "default behavior" rather than transient
     *  navigation/runtime state (e.g. [LAST_MENU_DISPLAYED] is deliberately excluded). Used by
     *  the config export/import feature - add new preferences here too when adding them above. */
    val EXPORTABLE: List<PreferenceDefinition<*>> = listOf(
            ALWAYS_SHOW_TIME, PAUSE_ON_SWIPE_EXIT, ROTATING_CROWN_OFF_PERIOD, ROTATING_CROWN_SENSITIVITY,
            ROTARY_SEEK, HAPTIC_FEEDBACK, DISABLE_PHYSICAL_DOUBLE_CLICK_IN_AMBIENT, AUTO_START_MODE,
            AUTO_START_APP_BLACKLIST, CLOSE_TIMEOUT, ENABLE_NOTIFICATION_POPUP, NOTIFICATION_TIMEOUT,
            ALWAYS_SELECT_CENTER_ACTION, DIM_ALBUM_ART, ALBUM_ART_STYLE, ALBUM_ART_BLUR_RADIUS,
            ALBUM_ART_DIM_STRENGTH, WEAR_PLAYER_SHADING_STYLE, WEAR_PLAYER_SHADING_INTENSITY,
            VOLUME_OVERLAY_TIMEOUT, ROTARY_DEADZONE, AMBIENT_ALBUM_ART_OPACITY,
            WEAR_AOD_STYLE, WEAR_AOD_SHOW_ART, WEAR_AOD_ART_TREATMENT,
            WEAR_AOD_SHOW_CLOCK, WEAR_AOD_SHOW_TRACK_INFO,
            WEAR_AOD_COLOR_MODE, WEAR_AOD_CUSTOM_COLOR, WEAR_AOD_SHOW_TRANSPORT, WEAR_AOD_SHOW_PROGRESS,
            WEAR_AOD_SHOW_PILLS, WEAR_AOD_INTENSITY,
            WEAR_CENTER_LONG_PRESS_QUEUE, WEAR_SCREEN_FACE, WEAR_SHOW_TRACK_TITLE, WEAR_SHOW_TRACK_ARTIST,
            WEAR_PLAYER_CONTROLS_VISIBLE, WEAR_INTERNAL_PROGRESS_VISIBLE,
            WEAR_EDGE_PROGRESS_VISIBLE, WEAR_EDGE_SEEK_ENABLED,
            WEAR_EXPRESSIVE_SEEK_MODE, WEAR_SCREEN_THEME, WEAR_FONT,
            WEAR_TRACK_TIME_MODE,
            WEAR_DYNAMIC_ACCENT, WEAR_COLOR_TREATMENT, WEAR_NORMAL_COLOR,
            WEAR_ALBUM_ART_FADE, WEAR_SCREEN_BUTTONS_OFFSET, WEAR_SCREEN_BUTTONS_CURVE_STYLE,
            WEAR_SCREEN_BUTTONS_BG, WEAR_SCREEN_BUTTONS_OPACITY, WEAR_SCREEN_BUTTONS_SHAPE,
            WEAR_OVERLAY_BLUR_RADIUS,
            WEAR_OVERLAY_BACKDROP_STYLE,
            WEAR_VOLUME_STYLE, WEAR_VOLUME_LAYOUT,
            WEAR_QUICK_PANEL_STYLE, WEAR_QUICK_PANEL_LAYOUT, WEAR_QUICK_PANEL_SOURCE, WEAR_QUEUE_STYLE,
            WEAR_PROGRESS_STYLE, WEAR_SEEK_STYLE, WEAR_SEEK_LAYOUT,
            WEAR_TITLE_TEXT_MODE, WEAR_ARTIST_COLOR_MODE, WEAR_ARTIST_CUSTOM_COLOR, WEAR_ARTIST_DESATURATED,
            WEAR_PROGRESS_COLOR_MODE, WEAR_PROGRESS_CUSTOM_COLOR, WEAR_PROGRESS_DESATURATED,
            WEAR_VOLUME_COLOR_MODE, WEAR_VOLUME_CUSTOM_COLOR,
            WEAR_QUICK_PANEL_COLOR_MODE, WEAR_QUICK_PANEL_CUSTOM_COLOR
    )
}
