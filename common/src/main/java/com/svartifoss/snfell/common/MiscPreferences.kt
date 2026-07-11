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

    /** Bottom scrim strength (0–100%) when dim album art is enabled. */
    val ALBUM_ART_DIM_STRENGTH: PreferenceDefinition<Int> = SimplePreferenceDefinition("album_art_dim_strength", 80)

    /** How long volume/seek overlays stay visible after adjustment (milliseconds). */
    val VOLUME_OVERLAY_TIMEOUT: PreferenceDefinition<Int> = SimplePreferenceDefinition("volume_overlay_timeout", 1000)

    /** Minimum rotary crown movement before volume/seek changes apply. */
    val ROTARY_DEADZONE: PreferenceDefinition<Int> = SimplePreferenceDefinition("rotary_deadzone", 6)

    /** Album art opacity in ambient mode (20–100%). */
    val AMBIENT_ALBUM_ART_OPACITY: PreferenceDefinition<Int> = SimplePreferenceDefinition("ambient_album_art_opacity", 55)

    // --- Always-on display (ambient mode) ---

    /** How the always-on display renders the now-playing screen: "follow" (match the selected
     *  [WEAR_SCREEN_FACE] - classic face gets the classic AOD, expressive face gets the outlined
     *  expressive AOD), "classic", "expressive" or "minimal" (pure black with dimmed text only -
     *  the biggest battery saver). All variants stay burn-in-audited: outlined/dim rendering,
     *  no animations, and the shared pixel-jiggle applies to every one of them. */
    val WEAR_AOD_STYLE: PreferenceDefinition<String> = SimplePreferenceDefinition("wear_aod_style", "follow")

    /** Show the (dimmed) album art on the always-on display. Off = pure black background,
     *  which is markedly cheaper on AMOLED. [AMBIENT_ALBUM_ART_OPACITY] applies when on;
     *  the "minimal" [WEAR_AOD_STYLE] never shows art regardless. */
    val WEAR_AOD_SHOW_ART: PreferenceDefinition<Boolean> = SimplePreferenceDefinition("wear_aod_show_art", true)

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

    /** Show the outlined bottom pill trio on the expressive AOD (only when no mini buttons are
     *  configured, mirroring the interactive face). */
    val WEAR_AOD_SHOW_PILLS: PreferenceDefinition<Boolean> =
            SimplePreferenceDefinition("wear_aod_show_pills", true)

    /** Overall AOD brightness (20-100%): scales the alpha of the expressive AOD's outlines,
     *  glyphs and text, and of the classic/minimal AOD text block. Lower = dimmer = cheaper. */
    val WEAR_AOD_INTENSITY: PreferenceDefinition<Int> = SimplePreferenceDefinition("wear_aod_intensity", 100)

    // --- Wear OS experience toggles (configured on phone, synced to watch) ---

    /** Long-press the center of the now-playing screen to open the playback queue. */
    val WEAR_CENTER_LONG_PRESS_QUEUE: PreferenceDefinition<Boolean> =
            SimplePreferenceDefinition("wear_center_long_press_queue", false)

    // There is deliberately no complication content preference: the watch-face complication is
    // image-only (album cover), always - text complication types were dropped because whether a
    // face renders an attached cover on a text slot is up to its renderer, and a setting around
    // that just produced "broken-looking" combinations. See AlbumArtComplicationDataSourceService.

    /** Which face renders the now-playing screen: "classic" (the original View layout with the
     *  bezel seek ring and quadrant hint icons) or "expressive" (a Compose face styled after the
     *  Material 3 Expressive system media controls, with pill transport buttons and a wavy
     *  progress ring). The face is the structural layout; [WEAR_SCREEN_THEME] remains a set of
     *  lighter variations applied on top of the classic face. */
    val WEAR_SCREEN_FACE: PreferenceDefinition<String> = SimplePreferenceDefinition("wear_screen_face", "classic")

    /** How the expressive face exposes drag-to-seek by touch: "central" (the progress ring around
     *  the cookie play button becomes draggable), "edge" (the classic bezel seek ring is shown on
     *  the expressive face too) or "none" (display-only; seek only via the rotary crown). Only the
     *  expressive face is affected — the classic face always uses the edge ring. */
    val WEAR_EXPRESSIVE_SEEK_MODE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_expressive_seek_mode", "central")

    /** Now-playing screen layout: default, minimal, compact, or cinema. */
    val WEAR_SCREEN_THEME: PreferenceDefinition<String> = SimplePreferenceDefinition("wear_screen_theme", "default")

    /** When the track position ("1:23 / 3:45") is shown on the now-playing screen: "always",
     *  "playing" (only while music plays), "paused" (only while paused) or "never". */
    val WEAR_TRACK_TIME_MODE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_track_time_mode", "always")

    /** Extract accent color from album art on the watch (when off, uses the static theme accent). */
    val WEAR_DYNAMIC_ACCENT: PreferenceDefinition<Boolean> = SimplePreferenceDefinition("wear_dynamic_accent", true)

    /** How the now-playing title text behaves when it doesn't fit its available width: "smart"
     *  (default - shrinks first, wraps to 2 lines if that helps, and only scrolls as a last
     *  resort), "marquee" (always a single line at full size, scrolling if it overflows), "wrap"
     *  (fixed size, wraps up to 2 lines, ellipsizes beyond that - never shrinks or scrolls) or
     *  "shrink" (word-safe shrink down to a floor size, ellipsizes beyond that - never scrolls). */
    val WEAR_TITLE_TEXT_MODE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_title_text_mode", "smart")

    /** Where the now-playing artist text color comes from: "neutral" (static theme accent),
     *  "album" (the watch's dynamic album-art accent, see [WEAR_DYNAMIC_ACCENT]) or "custom"
     *  ([WEAR_ARTIST_CUSTOM_COLOR]). */
    val WEAR_ARTIST_COLOR_MODE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_artist_color_mode", "album")

    /** Hex color (#RRGGBB) used when [WEAR_ARTIST_COLOR_MODE] is "custom". */
    val WEAR_ARTIST_CUSTOM_COLOR: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_artist_custom_color", "")

    /** Soften the album-derived artist text color (only applies in "album" color mode). */
    val WEAR_ARTIST_DESATURATED: PreferenceDefinition<Boolean> =
            SimplePreferenceDefinition("wear_artist_desaturated", false)

    /** Where the now-playing progress bar's accent color comes from: "neutral" (static theme
     *  accent), "album" (the watch's dynamic album-art accent, see [WEAR_DYNAMIC_ACCENT]) or
     *  "custom" ([WEAR_PROGRESS_CUSTOM_COLOR]). */
    val WEAR_PROGRESS_COLOR_MODE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_progress_color_mode", "album")

    /** Hex color (#RRGGBB) used when [WEAR_PROGRESS_COLOR_MODE] is "custom". */
    val WEAR_PROGRESS_CUSTOM_COLOR: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_progress_custom_color", "")

    /** Soften the album-derived progress bar color (only applies in "album" color mode). */
    val WEAR_PROGRESS_DESATURATED: PreferenceDefinition<Boolean> =
            SimplePreferenceDefinition("wear_progress_desaturated", false)

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

    /** Mini-button pill background: "glass" (translucent), "solid" or "transparent" (none). */
    val WEAR_SCREEN_BUTTONS_BG: PreferenceDefinition<String> =
            SimplePreferenceDefinition("screen_buttons_bg_style", "glass")

    /** Where the mini-button pill color comes from: "neutral" (theme glass), "album" (the
     *  watch's dynamic album-art accent) or "custom" ([WEAR_SCREEN_BUTTONS_CUSTOM_COLOR]). */
    val WEAR_SCREEN_BUTTONS_COLOR_MODE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("screen_buttons_color_mode", "neutral")

    /** Hex color (#RRGGBB) used when [WEAR_SCREEN_BUTTONS_COLOR_MODE] is "custom". */
    val WEAR_SCREEN_BUTTONS_CUSTOM_COLOR: PreferenceDefinition<String> =
            SimplePreferenceDefinition("screen_buttons_custom_color", "")

    /** Soften the album-derived mini-button color (only applies in "album" color mode). */
    val WEAR_SCREEN_BUTTONS_DESATURATED: PreferenceDefinition<Boolean> =
            SimplePreferenceDefinition("screen_buttons_desaturated", false)

    // The quick actions panel (double-tap center) is configured entirely through the
    // QuickPanelButtons ButtonInfo slots - no preferences involved.

    /** Blur radius (px) for the full-screen acrylic backdrop behind the volume/seek rings and
     *  the quick actions panel - independent from [ALBUM_ART_BLUR_RADIUS], which only styles
     *  the now-playing background when the blurred album art styles are selected. */
    val WEAR_OVERLAY_BLUR_RADIUS: PreferenceDefinition<Int> =
            SimplePreferenceDefinition("overlay_blur_radius", 35)

    // --- Selectable visual styles for the overlay surfaces. All share one vocabulary:
    //   "glass"    - frosted translucent panels over the blur backdrop (the original look).
    //   "minimal"  - pure-black AMOLED, hairline accent outlines, thin marks, no blur.
    //   "material" - solid dark-grey Material Design 2 surfaces with rounded corners + a thumb.
    //   "tonal"    - large rounded containers tinted in the album accent (matches the expressive
    //                face).
    //   "neon"     - transparent surfaces with glowing album-accent outlines and accent glyphs.
    //   "light"    - light surfaces with dark text/icons (a light-theme counterpoint).
    //   "gradient" - fills painted with an album-accent vertical gradient.
    //   "mono"     - neutral greyscale, ignoring the album accent entirely.
    //   "outline"  - thick white cartoon outlines over transparent fills.
    //   "duotone"  - two-hue: the album accent plus its complementary colour.
    //   "contrast" - pure black/white, thick strokes (high-contrast/accessibility).
    //   "terminal" - sharp-cornered monochrome green CRT look, accent forced to green.
    //   "frost"    - light translucent frosted panels (a light-glass variant).
    // See each surface's renderer for how it interprets these.

    /** Visual style of the volume overlay (arc on the left edge). */
    val WEAR_VOLUME_STYLE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_volume_style", "glass")

    /** Visual style of the quick-actions panel opened by double-tapping the screen centre. */
    val WEAR_QUICK_PANEL_STYLE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_quick_panel_style", "glass")

    /** Visual style of the playback queue screen. */
    val WEAR_QUEUE_STYLE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_queue_style", "glass")

    /** Visual style of the edge progress/seek ring: "solid" (default), "dashed", "dots",
     *  "hairline" or "comet" - see RingStyle on the watch. */
    val WEAR_PROGRESS_STYLE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_progress_style", "solid")

    /** Visual style of the scrub-time readout shown while seeking: "plain" (default), "pill",
     *  "giant" or "split" (position stacked over total). */
    val WEAR_SEEK_STYLE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_seek_style", "plain")

    fun isAnyKindOfAutoStartEnabled(preferences: SharedPreferences): Boolean {
        return Preferences.getBoolean(preferences, AUTO_START) || Preferences.getEnum(preferences, AUTO_START_MODE) != AutoStartMode.OFF
    }

    /** Every preference that represents a configurable "default behavior" rather than transient
     *  navigation/runtime state (e.g. [LAST_MENU_DISPLAYED] is deliberately excluded). Used by
     *  the config export/import feature - add new preferences here too when adding them above. */
    val EXPORTABLE: List<PreferenceDefinition<*>> = listOf(
            ALWAYS_SHOW_TIME, PAUSE_ON_SWIPE_EXIT, ROTATING_CROWN_OFF_PERIOD, ROTATING_CROWN_SENSITIVITY,
            ROTARY_SEEK, HAPTIC_FEEDBACK, DISABLE_PHYSICAL_DOUBLE_CLICK_IN_AMBIENT, AUTO_START, AUTO_START_MODE,
            AUTO_START_APP_BLACKLIST, CLOSE_TIMEOUT, ENABLE_NOTIFICATION_POPUP, NOTIFICATION_TIMEOUT,
            ALWAYS_SELECT_CENTER_ACTION, DIM_ALBUM_ART, ALBUM_ART_STYLE, ALBUM_ART_BLUR_RADIUS,
            ALBUM_ART_DIM_STRENGTH, VOLUME_OVERLAY_TIMEOUT, ROTARY_DEADZONE, AMBIENT_ALBUM_ART_OPACITY,
            WEAR_AOD_STYLE, WEAR_AOD_SHOW_ART, WEAR_AOD_SHOW_CLOCK, WEAR_AOD_SHOW_TRACK_INFO,
            WEAR_AOD_COLOR_MODE, WEAR_AOD_CUSTOM_COLOR, WEAR_AOD_SHOW_TRANSPORT, WEAR_AOD_SHOW_PROGRESS,
            WEAR_AOD_SHOW_PILLS, WEAR_AOD_INTENSITY,
            WEAR_CENTER_LONG_PRESS_QUEUE, WEAR_SCREEN_FACE, WEAR_EXPRESSIVE_SEEK_MODE, WEAR_SCREEN_THEME,
            WEAR_TRACK_TIME_MODE,
            WEAR_DYNAMIC_ACCENT, WEAR_ALBUM_ART_FADE, WEAR_SCREEN_BUTTONS_OFFSET, WEAR_SCREEN_BUTTONS_CURVE_STYLE,
            WEAR_SCREEN_BUTTONS_BG, WEAR_SCREEN_BUTTONS_COLOR_MODE, WEAR_SCREEN_BUTTONS_CUSTOM_COLOR,
            WEAR_SCREEN_BUTTONS_DESATURATED, WEAR_OVERLAY_BLUR_RADIUS,
            WEAR_VOLUME_STYLE, WEAR_QUICK_PANEL_STYLE, WEAR_QUEUE_STYLE,
            WEAR_PROGRESS_STYLE, WEAR_SEEK_STYLE,
            WEAR_TITLE_TEXT_MODE, WEAR_ARTIST_COLOR_MODE, WEAR_ARTIST_CUSTOM_COLOR, WEAR_ARTIST_DESATURATED,
            WEAR_PROGRESS_COLOR_MODE, WEAR_PROGRESS_CUSTOM_COLOR, WEAR_PROGRESS_DESATURATED
    )
}
