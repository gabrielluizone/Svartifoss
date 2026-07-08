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
            WEAR_CENTER_LONG_PRESS_QUEUE, WEAR_SCREEN_FACE, WEAR_SCREEN_THEME,
            WEAR_TRACK_TIME_MODE,
            WEAR_DYNAMIC_ACCENT, WEAR_ALBUM_ART_FADE, WEAR_SCREEN_BUTTONS_OFFSET, WEAR_SCREEN_BUTTONS_CURVE_STYLE,
            WEAR_SCREEN_BUTTONS_BG, WEAR_SCREEN_BUTTONS_COLOR_MODE, WEAR_SCREEN_BUTTONS_CUSTOM_COLOR,
            WEAR_SCREEN_BUTTONS_DESATURATED, WEAR_OVERLAY_BLUR_RADIUS,
            WEAR_TITLE_TEXT_MODE, WEAR_ARTIST_COLOR_MODE, WEAR_ARTIST_CUSTOM_COLOR, WEAR_ARTIST_DESATURATED,
            WEAR_PROGRESS_COLOR_MODE, WEAR_PROGRESS_CUSTOM_COLOR, WEAR_PROGRESS_DESATURATED
    )
}
