package com.svartifoss.snfell.common

import android.content.SharedPreferences
import com.svartifoss.snfell.common.model.AutoStartMode
import com.matejdro.wearutils.preferences.definition.EnumPreferenceDefinition
import com.matejdro.wearutils.preferences.definition.PreferenceDefinition
import com.matejdro.wearutils.preferences.definition.Preferences
import com.matejdro.wearutils.preferences.definition.SimplePreferenceDefinition

object MiscPreferences {

    /**
     * [WEAR_TITLE_COLOR_MODE] value meaning "leave the title the colour this face designed".
     *
     * Deliberately not "follow", which in the component-treatment vocabulary already means "follow
     * the watch-wide treatment" - two different things that would otherwise share one word.
     */
    const val TITLE_COLOR_FACE_DEFAULT: String = "face"

    val ALWAYS_SHOW_TIME: PreferenceDefinition<Boolean>
            = SimplePreferenceDefinition("always_show_time", false)

    val PAUSE_ON_SWIPE_EXIT: PreferenceDefinition<Boolean> = SimplePreferenceDefinition("pause_on_swipe_exit", false)

    val ROTATING_CROWN_OFF_PERIOD: PreferenceDefinition<Int> = SimplePreferenceDefinition("rotating_crown_off_period", 300)

    // Default kept gentle on purpose - at 100% a single accidental nudge of the crown (e.g.
    // while taking a screenshot) changes volume by a full volumeStep, which is too noticeable.
    val ROTATING_CROWN_SENSITIVITY: PreferenceDefinition<Int> = SimplePreferenceDefinition("rotating_crown_sensitivity", 40)

    // Legacy boolean superseded by WEAR_ROTARY_ACTION. Kept readable so an install (or a restored
    // backup) that predates the three-way preference resolves to the same behaviour - see
    // RotaryAction.resolve. Still exported so that migration survives a backup round-trip.
    val ROTARY_SEEK: PreferenceDefinition<Boolean> = SimplePreferenceDefinition("rotary_seek", false)

    /**
     * What rotary input does: change volume, scrub the timeline, or nothing at all. The "off" case
     * exists for touch-bezel watches, where circling the rim to edge-seek is also delivered as
     * rotary scroll - see [RotaryAction].
     *
     * The default is deliberately the empty "never chosen" sentinel rather than a real value, so
     * [RotaryAction.resolve] can tell an untouched install (which must keep obeying the legacy
     * [ROTARY_SEEK] boolean) apart from someone who actively picked "volume".
     */
    val WEAR_ROTARY_ACTION: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_rotary_action", "")

    val HAPTIC_FEEDBACK: PreferenceDefinition<Boolean> = SimplePreferenceDefinition("haptic_feedback", true)

    /**
     * UI language as a BCP-47 tag ("en", "pt-BR"), or [AppLocales.SYSTEM] to follow the device.
     *
     * Deliberately synced to the watch rather than left per-device: the two APKs are separate
     * installs with their own system locales, so without this a user who picks Portuguese on the
     * phone would still get an English watch. Both sides resolve it through [AppLocales].
     */
    val APP_LANGUAGE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("app_language", AppLocales.SYSTEM)

    val DISABLE_PHYSICAL_DOUBLE_CLICK_IN_AMBIENT: PreferenceDefinition<Boolean> = SimplePreferenceDefinition("disable_ambient_physical_double_click", false)

    // Legacy boolean superseded by AUTO_START_MODE. Kept only so the one-time migration in
    // MiscSettingsFragment can read and delete it. Deliberately absent from EXPORTABLE: exporting
    // it would let a restore resurrect the key the migration is meant to remove.
    val AUTO_START: PreferenceDefinition<Boolean> = SimplePreferenceDefinition("auto_start", false)

    val AUTO_START_MODE: EnumPreferenceDefinition<AutoStartMode> = EnumPreferenceDefinition("auto_start_mode", AutoStartMode.OFF)

    val AUTO_START_APP_BLACKLIST: PreferenceDefinition<Set<String>> = SimplePreferenceDefinition("auto_start_apps_blacklist", emptySet())

    val CLOSE_TIMEOUT: PreferenceDefinition<Int> = SimplePreferenceDefinition("close_timeout", 0)

    /** Auto-closes the watch app shortly after it reaches the truly-idle "Nothing playing" state
     *  (no track at all), so it never lingers there. Independent of [CLOSE_TIMEOUT], which governs
     *  the general non-playing (paused) case and stays off unless the user sets it. */
    val WEAR_CLOSE_ON_IDLE: PreferenceDefinition<Boolean> = SimplePreferenceDefinition("wear_close_on_idle", true)

    /** Minutes to keep the watch app reachable after pausing, or "always" - see [PausedHoldPolicy]. */
    val WEAR_PAUSED_HOLD: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_paused_hold", PausedHoldPolicy.DEFAULT_VALUE)

    /**
     * Hold the watch's screen on while a Svartifoss screen is in the foreground, instead of letting
     * it blank on the system's inactivity timeout.
     *
     * **Face-scoped**, and that is the point: whether you want this is a property of the
     * composition in front of you, not of the watch. A face you read - Verse's lyrics, Chat's
     * thread - wants the screen held; the one you glance at on the way past does not, and paying
     * the battery cost on every face in order to have it on one is not a trade anyone would pick.
     *
     * Off by default everywhere because it is a real battery cost, and because the timeout is the
     * right behaviour for a screen you glance at.
     *
     * What this can and cannot do is worth stating, because the difference is invisible until you
     * try it: it defeats the **inactivity timeout** only. Lowering your wrist is a system gesture
     * and no application flag overrides it, so the watch still goes ambient then - what keeps the
     * content visible at that point is the screen having an ambient variant, not this preference.
     *
     * The lyrics screen deliberately ignores this and always holds the screen: reading a lyric is
     * the one case where the timeout is guaranteed wrong, since following along involves no touching
     * at all and the screen would blank every few seconds mid-verse. The queue and the menu ignore
     * it too - they are choosers you pass through, and they belong to no face, so there is no
     * per-face value for them to read.
     */
    val WEAR_KEEP_SCREEN_ON: PreferenceDefinition<Boolean> =
            SimplePreferenceDefinition("wear_keep_screen_on", false)

    /**
     * Whether the phone may look track lyrics up online (LRCLIB) for the watch's lyrics screen.
     *
     * On by default, and the reasoning is the same as [QueueArtworkResolver]'s remote-artwork
     * switch rather than the opt-in stance the shortcut-artwork fetch takes: there is no second
     * source for a lyric, so off does not degrade the screen, it empties it. The lookup is also
     * user-initiated in a way the others are not - nothing is fetched until someone opens the
     * lyrics screen - so opening that screen is itself the consent. The switch is here for anyone
     * who would rather the phone never made the call at all.
     */
    val LYRICS_ENABLED: PreferenceDefinition<Boolean> =
            SimplePreferenceDefinition("lyrics_enabled", true)

    /**
     * What the idle screen's main button does - see [IdleScreenAction].
     *
     * Defaults to [IdleScreenAction.NONE]: the idle screen then follows the applied face's own
     * design instead of planting a button over it. The button is opt-in because "resume the last
     * app" genuinely does nothing when nothing has played yet, which made the default presentation
     * look broken rather than empty.
     */
    val WEAR_IDLE_BUTTON_ACTION: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_idle_button_action", IdleScreenAction.NONE.preferenceValue)

    /** Screen to open immediately when the app is opened with nothing playing. */
    val WEAR_IDLE_AUTO_OPEN: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_idle_auto_open", IdleScreenAction.NONE.preferenceValue)

    val ENABLE_NOTIFICATION_POPUP: PreferenceDefinition<Boolean> = SimplePreferenceDefinition("enable_notification_popup", false)

    val NOTIFICATION_TIMEOUT: PreferenceDefinition<Int> = SimplePreferenceDefinition("notification_timeout", 10)

    val ALWAYS_SELECT_CENTER_ACTION: PreferenceDefinition<Boolean> = SimplePreferenceDefinition("always_select_center_action", false)

    val LAST_MENU_DISPLAYED: PreferenceDefinition<String> = SimplePreferenceDefinition("last_menu_displayed", "-1")

    val DIM_ALBUM_ART: PreferenceDefinition<Boolean> = SimplePreferenceDefinition("dim_album_art", true)

    /** How the now-playing screen background is rendered on the watch. */
    val ALBUM_ART_STYLE: PreferenceDefinition<String> = SimplePreferenceDefinition("album_art_style", "cover")

    /** Independent album-art colour treatment, composited after the structural style. */
    val ALBUM_ART_FILTER: PreferenceDefinition<String> =
            SimplePreferenceDefinition("album_art_filter", "none")

    /** Blur radius in pixels when album art style is set to blur (API 31+ GPU blur). */
    val ALBUM_ART_BLUR_RADIUS: PreferenceDefinition<Int> = SimplePreferenceDefinition("album_art_blur_radius", 35)

    /** Shading strength as a direct percentage (0..[SHADING_MAX_PERCENT]); the live source. */
    val ALBUM_ART_DIM_STRENGTH: PreferenceDefinition<Int> = SimplePreferenceDefinition("album_art_dim_strength", 80)

    /** Artwork/player shading selected independently from the structural face. */
    val WEAR_PLAYER_SHADING_STYLE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_player_shading_style", "follow")

    /** Legacy named strength (soft/balanced/strong), superseded by the numeric
     *  [ALBUM_ART_DIM_STRENGTH]; kept only to migrate old values. */
    val WEAR_PLAYER_SHADING_INTENSITY: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_player_shading_intensity", "balanced")

    /** Colour of the shading gradient: "black" (default), "album", "desaturated" or "custom". */
    val WEAR_SHADING_COLOR_MODE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_shading_color_mode", "black")

    /** Hex colour (#RRGGBB) used when [WEAR_SHADING_COLOR_MODE] is "custom". */
    val WEAR_SHADING_CUSTOM_COLOR: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_shading_custom_color", "")

    /** Shows the currently-playing app's icon next to the artist line, on every face. The phone
     *  sends the icon from that app's media notification (the branded status-bar glyph), falling
     *  back to its launcher icon when no media notification is readable. On by default; the phone
     *  skips sending the icon entirely when this is off, to save Bluetooth. */
    val WEAR_SHOW_SOURCE_ICON: PreferenceDefinition<Boolean> =
            SimplePreferenceDefinition("wear_show_source_icon", true)

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

    // --- Awake clock (the "always show time" clock at the top of the now-playing screen; the AOD
    //     clock has its own AOD colour mode above). Face-scoped appearance prefs. ---

    /** Where the awake clock takes its colour: "white" (the historical semi-transparent white),
     *  "dynamic" (white or black by the luminance of the small artwork region *under the clock*,
     *  not the whole cover), "album" (the album/theme accent) or "custom" ([WEAR_CLOCK_CUSTOM_COLOR]).
     *  The colour opacity is applied separately via [WEAR_CLOCK_OPACITY]. */
    val WEAR_CLOCK_COLOR_MODE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_clock_color_mode", "white")

    /** Hex colour (#RRGGBB) used when [WEAR_CLOCK_COLOR_MODE] is "custom". */
    val WEAR_CLOCK_CUSTOM_COLOR: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_clock_custom_color", "")

    /** Awake clock opacity, 10-100%. Default 60 reproduces the old 0x99 (~60%) alpha. */
    val WEAR_CLOCK_OPACITY: PreferenceDefinition<Int> =
            SimplePreferenceDefinition("wear_clock_opacity", 60)

    /** Typeface for the awake clock: a [WEAR_FONT] catalog key, or [WatchTypography.CLOCK_FONT_FOLLOW]
     *  (the default) to keep following the track font as the clock always used to. Resolved by
     *  [WatchTypography.clockFontKey] on both sides. Lives in the Watch tab's Text section beside
     *  the track font rather than in the Clock section, because it is a font choice and that is
     *  where a user looks for one. */
    val WEAR_CLOCK_FONT: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_clock_font", WatchTypography.CLOCK_FONT_FOLLOW)

    /**
     * Typeface for song lyrics - the lyrics screen and the Verse face's lines alike.
     *
     * A [WEAR_FONT] catalog key, or [WatchTypography.LYRICS_FONT_FOLLOW] (the default) to keep
     * whatever each surface was designed with: the UI font on the lyrics screen, Marcellus on the
     * Verse face. Resolved by [WatchTypography.lyricsFontKey] on both sides, so old themes and
     * backups render exactly as they did.
     *
     * Face-scoped like the rest of the appearance keys, which is what lets a saved theme carry it
     * and lets Verse wear a serif while another face wearing the lyrics screen does not.
     */
    val WEAR_LYRICS_FONT: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_lyrics_font", WatchTypography.LYRICS_FONT_FOLLOW)

    /**
     * Typeface for the elapsed/total playback readout (for example, `1:23 / 3:45`).
     *
     * [WatchTypography.TRACK_TIME_FONT_FOLLOW] deliberately means "follow the design", rather
     * than following [WEAR_FONT]: the readout was part of each face's chrome before it gained a
     * control, and faces do not all use the same family for it. That identity default therefore
     * keeps existing themes byte-for-byte intact while a picked catalog family overrides it on
     * every face. Resolved by [WatchTypography.trackTimeFontKey].
     */
    val WEAR_TRACK_TIME_FONT: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_track_time_font", WatchTypography.TRACK_TIME_FONT_FOLLOW)

    // Which blocks of the metadata face are drawn. One switch per block rather than per field,
    // because a row only exists when the playing app published that tag - see
    // TrackMetadataFields.Group, which owns the keys and the defaults so the registry and the
    // renderer cannot disagree about what exists.
    val WEAR_METADATA_SHOW_CORE: PreferenceDefinition<Boolean> = newMetadataGroupPreference(
            TrackMetadataFields.Group.CORE)
    val WEAR_METADATA_SHOW_RELEASE: PreferenceDefinition<Boolean> = newMetadataGroupPreference(
            TrackMetadataFields.Group.RELEASE)
    val WEAR_METADATA_SHOW_CREDITS: PreferenceDefinition<Boolean> = newMetadataGroupPreference(
            TrackMetadataFields.Group.CREDITS)
    val WEAR_METADATA_SHOW_IDENTIFIERS: PreferenceDefinition<Boolean> = newMetadataGroupPreference(
            TrackMetadataFields.Group.IDENTIFIERS)
    val WEAR_METADATA_SHOW_TECHNICAL: PreferenceDefinition<Boolean> = newMetadataGroupPreference(
            TrackMetadataFields.Group.TECHNICAL)
    val WEAR_METADATA_SHOW_PLAYBACK: PreferenceDefinition<Boolean> = newMetadataGroupPreference(
            TrackMetadataFields.Group.PLAYBACK)

    /**
     * The definition behind one metadata block.
     *
     * Public so the watch and the phone preview can resolve a group without hunting it out of
     * [EXPORTABLE] and casting the result - which is what both of them did first, and which throws
     * rather than failing a compile the day a group is added and the registration is forgotten.
     */
    fun metadataGroupPreference(
            group: TrackMetadataFields.Group
    ): PreferenceDefinition<Boolean> = when (group) {
        TrackMetadataFields.Group.CORE -> WEAR_METADATA_SHOW_CORE
        TrackMetadataFields.Group.RELEASE -> WEAR_METADATA_SHOW_RELEASE
        TrackMetadataFields.Group.CREDITS -> WEAR_METADATA_SHOW_CREDITS
        TrackMetadataFields.Group.IDENTIFIERS -> WEAR_METADATA_SHOW_IDENTIFIERS
        TrackMetadataFields.Group.TECHNICAL -> WEAR_METADATA_SHOW_TECHNICAL
        TrackMetadataFields.Group.PLAYBACK -> WEAR_METADATA_SHOW_PLAYBACK
    }

    private fun newMetadataGroupPreference(
            group: TrackMetadataFields.Group
    ): PreferenceDefinition<Boolean> =
            SimplePreferenceDefinition(group.preferenceKey, group.defaultVisible)

    /**
     * Whether the phone may look a track up online (MusicBrainz) to fill in what the playing app
     * did not publish - ISRC, label, release date, catalogue ids.
     *
     * **Off by default**, unlike the lyrics lookup, and the difference is the point. A lyric has no
     * second source, so switching that off empties the screen; this one only ever *adds* rows to a
     * table that already stands on the player's own tags, so leaving it off costs a few lines
     * rather than the feature. Nothing is sent until someone both turns this on and selects the
     * metadata face.
     */
    val METADATA_LOOKUP_ENABLED: PreferenceDefinition<Boolean> =
            SimplePreferenceDefinition("metadata_lookup_enabled", false)

    // The clock's own weight/italic/size/tracking, mirroring the title and artist controls.
    // Picking its typeface alone turned out to be half a control: a display face at the clock's
    // designed 15sp is frequently the wrong size and weight for it, and there was no way to
    // compensate. Opacity is deliberately absent here - [WEAR_CLOCK_OPACITY] already owns it and a
    // second opacity control would be two settings fighting over one value.

    /** Clock weight, 1-1000. See [WEAR_TITLE_FONT_WEIGHT]. */
    val WEAR_CLOCK_FONT_WEIGHT: PreferenceDefinition<Int> =
            SimplePreferenceDefinition("wear_clock_font_weight", 400)

    /** Renders the clock italic. See [WEAR_TITLE_FONT_ITALIC]. */
    val WEAR_CLOCK_FONT_ITALIC: PreferenceDefinition<Boolean> =
            SimplePreferenceDefinition("wear_clock_font_italic", false)

    /** Clock size percentage over the face's designed size. See [WEAR_TITLE_FONT_SCALE]. */
    val WEAR_CLOCK_FONT_SCALE: PreferenceDefinition<Int> =
            SimplePreferenceDefinition("wear_clock_font_scale", 100)

    /** Clock letter spacing. See [WEAR_TITLE_FONT_TRACKING]. */
    val WEAR_CLOCK_FONT_TRACKING: PreferenceDefinition<Int> =
            SimplePreferenceDefinition("wear_clock_font_tracking", 0)

    /**
     * Lifts or darkens the artist line's album-derived colour until it separates from the artwork
     * *behind that line* - see [AdaptiveTextContrast].
     *
     * Off by default and deliberately a switch rather than another colour-treatment case: it is a
     * legibility correction applied *after* whatever treatment produced the colour, so it composes
     * with all of them (and with a hand-picked custom colour) instead of competing for the one
     * treatment slot. A user who tuned a palette by hand keeps exactly what they chose.
     */
    val WEAR_ARTIST_ADAPTIVE_CONTRAST: PreferenceDefinition<Boolean> =
            SimplePreferenceDefinition("wear_artist_adaptive_contrast", false)

    /**
     * Colour treatment for the track title, or [TITLE_COLOR_FACE_DEFAULT] to leave each face's own
     * choice alone.
     *
     * That extra value is why this cannot simply reuse the component-treatment vocabulary the
     * artist, progress ring, volume and quick panel share. Those surfaces have always been tinted
     * from the palette, so "follow" is a sensible floor for them. The title never was: all thirteen
     * Compose faces draw it in their own white (several at deliberately different alphas), and the
     * classic face has its own. Making "follow" the default here would repaint every one of them on
     * update - so the default is instead "whatever this face designed", exactly like the identity
     * defaults in [WatchTypography].
     */
    val WEAR_TITLE_COLOR_MODE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_title_color_mode", TITLE_COLOR_FACE_DEFAULT)

    /** Hex colour (#RRGGBB) used when [WEAR_TITLE_COLOR_MODE] is "normal". */
    val WEAR_TITLE_CUSTOM_COLOR: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_title_custom_color", "")

    /** [WEAR_ARTIST_ADAPTIVE_CONTRAST] for the title. Meaningless while the title keeps the face's
     *  own colour, which is not derived from the artwork - see [WEAR_TITLE_COLOR_MODE]. */
    val WEAR_TITLE_ADAPTIVE_CONTRAST: PreferenceDefinition<Boolean> =
            SimplePreferenceDefinition("wear_title_adaptive_contrast", false)

    /**
     * [WEAR_ARTIST_ADAPTIVE_CONTRAST] for the clock, and deliberately the same shape: off by
     * default, applied after the colour is resolved.
     *
     * Only meaningful while [WEAR_CLOCK_COLOR_MODE] is "album", which is the one mode whose colour
     * is *derived* rather than chosen. Correcting "white", a hand-picked "custom" colour, or
     * "dynamic" (which already flips black/white by the artwork beneath) would be overriding a
     * decision the user made rather than rescuing one the album made for them.
     */
    val WEAR_CLOCK_ADAPTIVE_CONTRAST: PreferenceDefinition<Boolean> =
            SimplePreferenceDefinition("wear_clock_adaptive_contrast", false)

    /**
     * Whether the Solid progress ring blends the treatment's companion colours into a sweep, or
     * fills with the primary alone.
     *
     * On by default because that is what the ring already did: a hue-rotating treatment (Triadic,
     * Complementary, Analogous, Duotone) is otherwise invisible on the classic face, since the ring
     * is its only always-on-screen coloured element. Turning it off is the one way to keep an
     * album-derived palette everywhere else and still have a single-colour bar - previously the
     * only route to a flat ring was setting the ring's own treatment to Normal, which also threw
     * away the album colour.
     */
    val WEAR_PROGRESS_GRADIENT: PreferenceDefinition<Boolean> =
            SimplePreferenceDefinition("wear_progress_gradient", true)

    // --- Wear OS experience toggles (configured on phone, synced to watch) ---

    /** Legacy boolean form of [WEAR_CENTER_LONG_PRESS]; kept only so an install that enabled it
     *  keeps the queue on that gesture. See [CenterLongPressAction.resolve]. */
    val WEAR_CENTER_LONG_PRESS_QUEUE: PreferenceDefinition<Boolean> =
            SimplePreferenceDefinition("wear_center_long_press_queue", false)

    /** What a long press on the centre of the now-playing screen does - see
     *  [CenterLongPressAction]. Empty default so the legacy boolean above can still be honoured. */
    val WEAR_CENTER_LONG_PRESS: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_center_long_press", "")

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

    /** ID of the user-created theme currently materialized in the fixed
     *  [ThemeAppearance.CUSTOM_SCOPE] preference scope. An empty value keeps the selected
     *  built-in face active. The profile library itself remains phone-local; only this active
     *  snapshot metadata and the scoped appearance values are synchronized to the watch. */
    val WEAR_ACTIVE_CUSTOM_THEME_ID: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_active_custom_theme_id", "")

    /** JSON array of available custom themes (id, name, baseFace) synced to the watch picker. */
    val WEAR_AVAILABLE_CUSTOM_THEMES: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_available_custom_themes", "[]")

    /** Schema of the materialized custom-theme snapshot. Stored as a string, like every other
     *  wearutils integer preference. A custom snapshot is accepted only when it exactly matches
     *  [ThemeAppearance.CURRENT_SCHEMA]. */
    val WEAR_CUSTOM_THEME_SCHEMA: PreferenceDefinition<Int> =
            SimplePreferenceDefinition("wear_custom_theme_schema", 0)

    /** Commit marker written only after every `key@custom_active` value has been materialized. */
    val WEAR_CUSTOM_THEME_COMPLETE: PreferenceDefinition<Boolean> =
            SimplePreferenceDefinition("wear_custom_theme_complete", false)

    /** Monotonic revision of the materialized snapshot, used by the mobile/Wear integration to
     *  identify a newly applied version without synchronizing the complete profile library. */
    val WEAR_CUSTOM_THEME_REVISION: PreferenceDefinition<Int> =
            SimplePreferenceDefinition("wear_custom_theme_revision", 0)

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

    /**
     * How the Split face fills the panel below its seam - see [SplitPanelStyle].
     *
     * Split paints its own opaque backdrop, so the Album background styles cannot reach it. This is
     * the control that replaces them for the one surface that face owns.
     */
    val WEAR_SPLIT_PANEL: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_split_panel", SplitPanelStyle.DEFAULT.preferenceValue)

    /**
     * The accent wash pooled along the bottom of the screen - see [AccentFloorStyle].
     *
     * A piece any face can wear, rather than something welded into the one it was designed for.
     * Off everywhere by default except Verse, whose composition was built around it.
     */
    val WEAR_ACCENT_FLOOR: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_accent_floor", AccentFloorStyle.DEFAULT.preferenceValue)

    /** Palette source for the accent floor: album primary/secondary/tertiary or a fixed colour. */
    val WEAR_ACCENT_FLOOR_COLOR_MODE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_accent_floor_color_mode", "album")

    /** User-picked floor colour, read only when [WEAR_ACCENT_FLOOR_COLOR_MODE] is `custom`. */
    val WEAR_ACCENT_FLOOR_CUSTOM_COLOR: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_accent_floor_custom_color", "")

    /**
     * The ordered background layer stack - see [BackgroundLayerStack] for the grammar.
     *
     * Empty means nobody has touched it, and every renderer then keeps its pre-stack path driven
     * by [ALBUM_ART_STYLE], [WEAR_PLAYER_SHADING_STYLE] and [WEAR_ACCENT_FLOOR]. That is also what
     * a watch build older than this key does with it, so a phone that has moved on degrades to the
     * previous look rather than to no background at all.
     */
    val WEAR_BACKGROUND_LAYERS: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_background_layers", "")

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

    /** Classic face only (Compose faces never show quadrant hint icons at all): when a quadrant's
     *  tap/double-tap/long-press fires its action, briefly flashes that icon to full opacity and
     *  back down to the current Screen Theme's resting alpha - most useful on Hidden, where the
     *  icon is otherwise invisible and gives no confirmation of which action just fired, but
     *  available on every theme. Independent of the existing scale-bounce pulse, which keeps
     *  running unconditionally regardless of this toggle. */
    val WEAR_QUADRANT_TAP_FLASH: PreferenceDefinition<Boolean> =
            SimplePreferenceDefinition("wear_quadrant_tap_flash", false)

    /** Default typeface for title/artist text on every player layout. An element can opt into a
     *  different catalog family through [WEAR_TITLE_FONT] or [WEAR_ARTIST_FONT]; their default is
     *  to follow this value. The catalog combines the bundled Google Sans/Special Elite faces with
     *  Android system-family aliases (rounded, light, thin, medium, black, small caps, casual,
     *  serif, mono, condensed and cursive), plus the packaged OFL reading, display, mono and
     *  script families. The older bundled "typewriter" choice remains readable but is hidden
     *  unless developer archived options are enabled. Decoded by watchFontFamily and mirrored by
     *  WatchPreviewView. */
    val WEAR_FONT: PreferenceDefinition<String> = SimplePreferenceDefinition("wear_font", "google_sans")

    /** Card outline for the Carousel face's cover rail - see [CoverShape]. */
    val WEAR_CAROUSEL_CARD_SHAPE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_carousel_card_shape",
                    CoverShape.ROUNDED.preferenceValue)

    /**
     * Silhouette of the Note face's cover disc - see [CoverShape], the same vocabulary
     * [WEAR_CAROUSEL_CARD_SHAPE] uses.
     *
     * Its own key rather than a shared one: the two faces sit at opposite ends of that vocabulary
     * (a rail of cards, a single round thumbnail beside a line of text), so one value would make
     * choosing a shape for either silently restyle the other.
     */
    val WEAR_NOTE_COVER_SHAPE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_note_cover_shape",
                    CoverShape.CIRCLE.preferenceValue)

    /** Whether Note draws its cover disc at all - see [WEAR_NOTE_COVER_SHAPE]. Its own key rather
     *  than folding "hidden" into the shape vocabulary itself: a shape is a corner radius every
     *  consumer of [CoverShape] can draw the same way, while "nothing here" is a layout decision
     *  each face has to make on its own (Note collapses its sentence to the centre; Chat's waveform
     *  widens; Metadata's identity block shrinks) - the same "does this element exist" question
     *  [WEAR_SHOW_SOURCE_ICON] already answers for the app-icon glyph, kept separate from *how*
     *  that glyph looks. */
    val WEAR_NOTE_SHOW_COVER: PreferenceDefinition<Boolean> =
            SimplePreferenceDefinition("wear_note_show_cover", true)

    /**
     * Anchor the *title* to the centre of the screen instead of the metadata block as a whole.
     *
     * Off, a face centres the block it composes - title, artist and whatever else it stacks with
     * them - so the point that actually lands on the middle of the display moves as soon as a
     * title wraps to a second line or an artist name appears. On, the block is shifted so the
     * title's own centre is the fixed point and everything else hangs off it.
     *
     * Only the three faces that centre such a block honour it (Classic, Poster, Studio); it is not
     * a general typography control, which is why it is a Player element rather than a Text one.
     * The shift is a translation of the composition, never a reordering: Classic keeps the artist
     * above the title where it has always drawn it.
     */
    val WEAR_TITLE_CENTERED: PreferenceDefinition<Boolean> =
            SimplePreferenceDefinition("wear_title_centered", false)

    /** Silhouette of Chat's avatar - the same [CoverShape] vocabulary as [WEAR_NOTE_COVER_SHAPE]
     *  and [WEAR_CAROUSEL_CARD_SHAPE], its own key for the same reason theirs is: a rail of cards,
     *  a disc beside a sentence and a message avatar are three different compositions, and one
     *  shared value would let choosing a shape for any of them silently restyle the others. */
    val WEAR_CHAT_COVER_SHAPE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_chat_cover_shape", CoverShape.CIRCLE.preferenceValue)

    /** Whether Chat draws the avatar in its voice bubble - see [WEAR_NOTE_SHOW_COVER]. */
    val WEAR_CHAT_SHOW_COVER: PreferenceDefinition<Boolean> =
            SimplePreferenceDefinition("wear_chat_show_cover", true)

    /** Silhouette of Metadata's small identity thumbnail - see [WEAR_CHAT_COVER_SHAPE]. */
    val WEAR_METADATA_COVER_SHAPE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_metadata_cover_shape", CoverShape.ROUNDED.preferenceValue)

    /** Whether Metadata draws its identity thumbnail - see [WEAR_NOTE_SHOW_COVER]. */
    val WEAR_METADATA_SHOW_COVER: PreferenceDefinition<Boolean> =
            SimplePreferenceDefinition("wear_metadata_show_cover", true)

    /**
     * Whether [WEAR_FONT] also styles the menu, the queue and the shared chrome, instead of only
     * the now-playing title/artist. It is scoped with [WEAR_FONT], so each theme can decide whether
     * its typeface extends to those surfaces. Decorative and script fonts remain opt-in per theme.
     */
    val WEAR_FONT_ALL_SCREENS: PreferenceDefinition<Boolean> =
            SimplePreferenceDefinition("wear_font_all_screens", false)

    // --- Per-element typography, layered on top of the default [WEAR_FONT] family choice ---
    //
    // Title and artist are styled independently because they carry different weight in the
    // hierarchy: users overwhelmingly want a heavier/larger title against a lighter, dimmer
    // artist line. Every value here is a *delta* over the face's own designed size, never an
    // absolute sp - the curated faces size their text against a 192dp reference watch, so an
    // absolute override would break their layout on any other screen size. Defaults are the
    // exact identity (weight 400, no italic, 100% size/opacity, no extra tracking), so an
    // install that never opens these controls renders bit-for-bit as it did before they existed.
    // The family choices follow [WEAR_FONT] until explicitly changed. Decoded by
    // [WatchTypography], mirrored by WatchPreviewView.

    /** Typeface for the track title: a [WEAR_FONT] catalog key, or
     *  [WatchTypography.TITLE_FONT_FOLLOW] (the default) to use the face's default track family.
     *  Resolved by [WatchTypography.titleFontKey]. */
    val WEAR_TITLE_FONT: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_title_font", WatchTypography.TITLE_FONT_FOLLOW)

    /** Typeface for the artist line: a [WEAR_FONT] catalog key, or
     *  [WatchTypography.ARTIST_FONT_FOLLOW] (the default) to use the face's default track family.
     *  Resolved by [WatchTypography.artistFontKey]. */
    val WEAR_ARTIST_FONT: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_artist_font", WatchTypography.ARTIST_FONT_FOLLOW)

    /** Track title weight, 100-900 in CSS-style steps. 400 is the face's normal weight. */
    val WEAR_TITLE_FONT_WEIGHT: PreferenceDefinition<Int> =
            SimplePreferenceDefinition("wear_title_font_weight", 400)

    /** Renders the track title italic. Synthesized by the platform for families with no italic
     *  cut, which every bundled face is - so this is always available, never silently ignored. */
    val WEAR_TITLE_FONT_ITALIC: PreferenceDefinition<Boolean> =
            SimplePreferenceDefinition("wear_title_font_italic", false)

    /** Track title size as a percentage of the face's designed size
     *  ([TYPOGRAPHY_MIN_SCALE]..[TYPOGRAPHY_MAX_SCALE]). */
    val WEAR_TITLE_FONT_SCALE: PreferenceDefinition<Int> =
            SimplePreferenceDefinition("wear_title_font_scale", 100)

    /** Track title opacity, [TYPOGRAPHY_MIN_OPACITY]-100%. */
    val WEAR_TITLE_FONT_OPACITY: PreferenceDefinition<Int> =
            SimplePreferenceDefinition("wear_title_font_opacity", 100)

    /** Extra title letter spacing in hundredths of an em
     *  ([TYPOGRAPHY_MIN_TRACKING]..[TYPOGRAPHY_MAX_TRACKING]); 0 keeps the font's own metrics. */
    val WEAR_TITLE_FONT_TRACKING: PreferenceDefinition<Int> =
            SimplePreferenceDefinition("wear_title_font_tracking", 0)

    /** Track title text-case transform - a [TextCase.preferenceValue], default
     *  [TextCase.NORMAL]. Applied last, after every other title typography control, so it always
     *  wins over whatever case a face's own composition happens to use. */
    val WEAR_TITLE_TEXT_CASE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_title_text_case", TextCase.NORMAL.preferenceValue)

    /** Artist line weight, 100-900. See [WEAR_TITLE_FONT_WEIGHT]. */
    val WEAR_ARTIST_FONT_WEIGHT: PreferenceDefinition<Int> =
            SimplePreferenceDefinition("wear_artist_font_weight", 400)

    /** Renders the artist line italic. See [WEAR_TITLE_FONT_ITALIC]. */
    val WEAR_ARTIST_FONT_ITALIC: PreferenceDefinition<Boolean> =
            SimplePreferenceDefinition("wear_artist_font_italic", false)

    /** Artist line size percentage. See [WEAR_TITLE_FONT_SCALE]. */
    val WEAR_ARTIST_FONT_SCALE: PreferenceDefinition<Int> =
            SimplePreferenceDefinition("wear_artist_font_scale", 100)

    /** Artist line opacity. See [WEAR_TITLE_FONT_OPACITY]. */
    val WEAR_ARTIST_FONT_OPACITY: PreferenceDefinition<Int> =
            SimplePreferenceDefinition("wear_artist_font_opacity", 100)

    /** Extra artist letter spacing. See [WEAR_TITLE_FONT_TRACKING]. */
    val WEAR_ARTIST_FONT_TRACKING: PreferenceDefinition<Int> =
            SimplePreferenceDefinition("wear_artist_font_tracking", 0)

    /**
     * The title's shadow: shape, colour and how far it is pushed. See [TextShadowStyle].
     *
     * Four keys rather than one encoded value, unlike [WEAR_BACKGROUND_LAYERS]: that one describes
     * a *list the user builds*, where the count is part of the answer, while this is a fixed set of
     * four questions about one effect. Four ordinary keys stay searchable, stay individually
     * resettable, and cost the community-theme vocabulary four entries it can validate by type.
     */
    val WEAR_TITLE_SHADOW_STYLE: PreferenceDefinition<String> =
            SimplePreferenceDefinition(
                    "wear_title_shadow_style",
                    TextShadowStyle.NONE.preferenceValue)

    /** Where the title shadow's colour comes from. See [TextShadowColorMode]. */
    val WEAR_TITLE_SHADOW_COLOR_MODE: PreferenceDefinition<String> =
            SimplePreferenceDefinition(
                    "wear_title_shadow_color_mode",
                    TextShadowColorMode.BLACK.preferenceValue)

    /** Hex colour (#RRGGBB) used when [WEAR_TITLE_SHADOW_COLOR_MODE] is "custom". */
    val WEAR_TITLE_SHADOW_CUSTOM_COLOR: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_title_shadow_custom_color", "")

    /** Title shadow intensity, as a percentage of the style's own geometry and opacity. */
    val WEAR_TITLE_SHADOW_STRENGTH: PreferenceDefinition<Int> =
            SimplePreferenceDefinition(
                    "wear_title_shadow_strength",
                    TextShadowSpec.DEFAULT_STRENGTH_PERCENT)

    /** [WEAR_TITLE_SHADOW_STYLE] for the artist line. */
    val WEAR_ARTIST_SHADOW_STYLE: PreferenceDefinition<String> =
            SimplePreferenceDefinition(
                    "wear_artist_shadow_style",
                    TextShadowStyle.NONE.preferenceValue)

    /** [WEAR_TITLE_SHADOW_COLOR_MODE] for the artist line. */
    val WEAR_ARTIST_SHADOW_COLOR_MODE: PreferenceDefinition<String> =
            SimplePreferenceDefinition(
                    "wear_artist_shadow_color_mode",
                    TextShadowColorMode.BLACK.preferenceValue)

    /** [WEAR_TITLE_SHADOW_CUSTOM_COLOR] for the artist line. */
    val WEAR_ARTIST_SHADOW_CUSTOM_COLOR: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_artist_shadow_custom_color", "")

    /** [WEAR_TITLE_SHADOW_STRENGTH] for the artist line. */
    val WEAR_ARTIST_SHADOW_STRENGTH: PreferenceDefinition<Int> =
            SimplePreferenceDefinition(
                    "wear_artist_shadow_strength",
                    TextShadowSpec.DEFAULT_STRENGTH_PERCENT)

    /** The title's outline: a stroke drawn around the glyphs. See [TextOutlineStyle]. */
    val WEAR_TITLE_OUTLINE_STYLE: PreferenceDefinition<String> =
            SimplePreferenceDefinition(
                    "wear_title_outline_style",
                    TextOutlineStyle.NONE.preferenceValue)

    /** Where the title outline's colour comes from. Shares [TextShadowColorMode] with the
     *  shadow - the four sources somebody picks from are the same four. */
    val WEAR_TITLE_OUTLINE_COLOR_MODE: PreferenceDefinition<String> =
            SimplePreferenceDefinition(
                    "wear_title_outline_color_mode",
                    TextShadowColorMode.BLACK.preferenceValue)

    /** Hex colour (#RRGGBB) used when [WEAR_TITLE_OUTLINE_COLOR_MODE] is "custom". */
    val WEAR_TITLE_OUTLINE_CUSTOM_COLOR: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_title_outline_custom_color", "")

    /** [WEAR_TITLE_OUTLINE_STYLE] for the artist line. */
    val WEAR_ARTIST_OUTLINE_STYLE: PreferenceDefinition<String> =
            SimplePreferenceDefinition(
                    "wear_artist_outline_style",
                    TextOutlineStyle.NONE.preferenceValue)

    /** [WEAR_TITLE_OUTLINE_COLOR_MODE] for the artist line. */
    val WEAR_ARTIST_OUTLINE_COLOR_MODE: PreferenceDefinition<String> =
            SimplePreferenceDefinition(
                    "wear_artist_outline_color_mode",
                    TextShadowColorMode.BLACK.preferenceValue)

    /** [WEAR_TITLE_OUTLINE_CUSTOM_COLOR] for the artist line. */
    val WEAR_ARTIST_OUTLINE_CUSTOM_COLOR: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_artist_outline_custom_color", "")

    /** A filled box drawn behind the title. See [TextBackdropStyle]. */
    val WEAR_TITLE_TEXT_BG_STYLE: PreferenceDefinition<String> =
            SimplePreferenceDefinition(
                    "wear_title_text_bg_style",
                    TextBackdropStyle.NONE.preferenceValue)

    /** Where the title backdrop's colour comes from. Shares [TextShadowColorMode]. */
    val WEAR_TITLE_TEXT_BG_COLOR_MODE: PreferenceDefinition<String> =
            SimplePreferenceDefinition(
                    "wear_title_text_bg_color_mode",
                    TextShadowColorMode.BLACK.preferenceValue)

    /** Hex colour (#RRGGBB) used when [WEAR_TITLE_TEXT_BG_COLOR_MODE] is "custom". */
    val WEAR_TITLE_TEXT_BG_CUSTOM_COLOR: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_title_text_bg_custom_color", "")

    /** Title backdrop opacity, scaling the chosen style's own alpha. */
    val WEAR_TITLE_TEXT_BG_OPACITY: PreferenceDefinition<Int> =
            SimplePreferenceDefinition(
                    "wear_title_text_bg_opacity",
                    TextBackdropSpec.DEFAULT_OPACITY_PERCENT)

    /** [WEAR_TITLE_TEXT_BG_STYLE] for the artist line. */
    val WEAR_ARTIST_TEXT_BG_STYLE: PreferenceDefinition<String> =
            SimplePreferenceDefinition(
                    "wear_artist_text_bg_style",
                    TextBackdropStyle.NONE.preferenceValue)

    /** [WEAR_TITLE_TEXT_BG_COLOR_MODE] for the artist line. */
    val WEAR_ARTIST_TEXT_BG_COLOR_MODE: PreferenceDefinition<String> =
            SimplePreferenceDefinition(
                    "wear_artist_text_bg_color_mode",
                    TextShadowColorMode.BLACK.preferenceValue)

    /** [WEAR_TITLE_TEXT_BG_CUSTOM_COLOR] for the artist line. */
    val WEAR_ARTIST_TEXT_BG_CUSTOM_COLOR: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_artist_text_bg_custom_color", "")

    /** [WEAR_TITLE_TEXT_BG_OPACITY] for the artist line. */
    val WEAR_ARTIST_TEXT_BG_OPACITY: PreferenceDefinition<Int> =
            SimplePreferenceDefinition(
                    "wear_artist_text_bg_opacity",
                    TextBackdropSpec.DEFAULT_OPACITY_PERCENT)

    /** Artist line text-case transform. See [WEAR_TITLE_TEXT_CASE]. */
    val WEAR_ARTIST_TEXT_CASE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_artist_text_case", TextCase.NORMAL.preferenceValue)

    // The elapsed/total readout is its own chrome element rather than secondary metadata. It has
    // a separate family plus the same small set of typography deltas as title/artist, so choosing
    // a display face for a compact `1:23 / 3:45` line does not require distorting either track
    // text. Identity preserves each face's authored family, size, opacity and spacing.

    /** Playback-time weight, 1-1000. See [WEAR_TITLE_FONT_WEIGHT]. */
    val WEAR_TRACK_TIME_FONT_WEIGHT: PreferenceDefinition<Int> =
            SimplePreferenceDefinition("wear_track_time_font_weight", 400)

    /** Renders the playback-time readout italic. See [WEAR_TITLE_FONT_ITALIC]. */
    val WEAR_TRACK_TIME_FONT_ITALIC: PreferenceDefinition<Boolean> =
            SimplePreferenceDefinition("wear_track_time_font_italic", false)

    /** Playback-time size percentage over the face's designed readout size. */
    val WEAR_TRACK_TIME_FONT_SCALE: PreferenceDefinition<Int> =
            SimplePreferenceDefinition("wear_track_time_font_scale", 100)

    /** Playback-time opacity percentage, layered over the face's designed readout color. */
    val WEAR_TRACK_TIME_FONT_OPACITY: PreferenceDefinition<Int> =
            SimplePreferenceDefinition("wear_track_time_font_opacity", 100)

    /** Extra playback-time letter spacing in hundredths of an em. */
    val WEAR_TRACK_TIME_FONT_TRACKING: PreferenceDefinition<Int> =
            SimplePreferenceDefinition("wear_track_time_font_tracking", 0)

    /** Size of the playing-app icon next to the artist line, as a percentage of its designed size
     *  ([TYPOGRAPHY_MIN_SCALE]..[TYPOGRAPHY_MAX_SCALE]). Only meaningful while
     *  [WEAR_SHOW_SOURCE_ICON] is on. */
    val WEAR_SOURCE_ICON_SCALE: PreferenceDefinition<Int> =
            SimplePreferenceDefinition("wear_source_icon_scale", 100)

    /** Playing-app icon opacity, [TYPOGRAPHY_MIN_OPACITY]-100%. */
    val WEAR_SOURCE_ICON_OPACITY: PreferenceDefinition<Int> =
            SimplePreferenceDefinition("wear_source_icon_opacity", 100)

    // --- Google Sans Flex variable-font axes (WatchTypography.FLEX_*) ---
    //
    // The global set belongs to any element following [WEAR_FONT]. Every explicit typeface
    // override gets its own set below: choosing Flex only for one element must not silently edit
    // the axes used by another element.

    /** Google Sans Flex `wdth` axis, [WatchTypography.FLEX_WIDTH_MIN]..[WatchTypography.FLEX_WIDTH_MAX]. */
    val WEAR_FONT_FLEX_WIDTH: PreferenceDefinition<Int> =
            SimplePreferenceDefinition("wear_font_flex_width", 100)

    /** Google Sans Flex `opsz` axis, [WatchTypography.FLEX_OPTICAL_SIZE_MIN]..[WatchTypography.FLEX_OPTICAL_SIZE_MAX]. */
    val WEAR_FONT_FLEX_OPTICAL_SIZE: PreferenceDefinition<Int> =
            SimplePreferenceDefinition("wear_font_flex_optical_size", 18)

    /** Google Sans Flex `GRAD` axis, [WatchTypography.FLEX_GRADE_MIN]..[WatchTypography.FLEX_GRADE_MAX]. */
    val WEAR_FONT_FLEX_GRADE: PreferenceDefinition<Int> =
            SimplePreferenceDefinition("wear_font_flex_grade", 0)

    /** Google Sans Flex `ROND` axis, [WatchTypography.FLEX_ROUNDNESS_MIN]..[WatchTypography.FLEX_ROUNDNESS_MAX]. */
    val WEAR_FONT_FLEX_ROUNDNESS: PreferenceDefinition<Int> =
            SimplePreferenceDefinition("wear_font_flex_roundness", 0)

    // The five individual typeface pickers each receive a full Flex-axis set. They intentionally
    // default to the variable font's own identity point rather than inheriting the track family's
    // values: an explicit element override is a separate piece of typography, and must remain
    // independently editable even while the track uses another family.

    /** Title-only Google Sans Flex `wdth` axis. */
    val WEAR_TITLE_FONT_FLEX_WIDTH: PreferenceDefinition<Int> =
            SimplePreferenceDefinition("wear_title_font_flex_width", 100)

    /** Title-only Google Sans Flex `opsz` axis. */
    val WEAR_TITLE_FONT_FLEX_OPTICAL_SIZE: PreferenceDefinition<Int> =
            SimplePreferenceDefinition("wear_title_font_flex_optical_size", 18)

    /** Title-only Google Sans Flex `GRAD` axis. */
    val WEAR_TITLE_FONT_FLEX_GRADE: PreferenceDefinition<Int> =
            SimplePreferenceDefinition("wear_title_font_flex_grade", 0)

    /** Title-only Google Sans Flex `ROND` axis. */
    val WEAR_TITLE_FONT_FLEX_ROUNDNESS: PreferenceDefinition<Int> =
            SimplePreferenceDefinition("wear_title_font_flex_roundness", 0)

    /** Artist-only Google Sans Flex `wdth` axis. */
    val WEAR_ARTIST_FONT_FLEX_WIDTH: PreferenceDefinition<Int> =
            SimplePreferenceDefinition("wear_artist_font_flex_width", 100)

    /** Artist-only Google Sans Flex `opsz` axis. */
    val WEAR_ARTIST_FONT_FLEX_OPTICAL_SIZE: PreferenceDefinition<Int> =
            SimplePreferenceDefinition("wear_artist_font_flex_optical_size", 18)

    /** Artist-only Google Sans Flex `GRAD` axis. */
    val WEAR_ARTIST_FONT_FLEX_GRADE: PreferenceDefinition<Int> =
            SimplePreferenceDefinition("wear_artist_font_flex_grade", 0)

    /** Artist-only Google Sans Flex `ROND` axis. */
    val WEAR_ARTIST_FONT_FLEX_ROUNDNESS: PreferenceDefinition<Int> =
            SimplePreferenceDefinition("wear_artist_font_flex_roundness", 0)

    /** Clock-only Google Sans Flex `wdth` axis. */
    val WEAR_CLOCK_FONT_FLEX_WIDTH: PreferenceDefinition<Int> =
            SimplePreferenceDefinition("wear_clock_font_flex_width", 100)

    /** Clock-only Google Sans Flex `opsz` axis. */
    val WEAR_CLOCK_FONT_FLEX_OPTICAL_SIZE: PreferenceDefinition<Int> =
            SimplePreferenceDefinition("wear_clock_font_flex_optical_size", 18)

    /** Clock-only Google Sans Flex `GRAD` axis. */
    val WEAR_CLOCK_FONT_FLEX_GRADE: PreferenceDefinition<Int> =
            SimplePreferenceDefinition("wear_clock_font_flex_grade", 0)

    /** Clock-only Google Sans Flex `ROND` axis. */
    val WEAR_CLOCK_FONT_FLEX_ROUNDNESS: PreferenceDefinition<Int> =
            SimplePreferenceDefinition("wear_clock_font_flex_roundness", 0)

    /** Lyrics-only Google Sans Flex `wdth` axis. */
    val WEAR_LYRICS_FONT_FLEX_WIDTH: PreferenceDefinition<Int> =
            SimplePreferenceDefinition("wear_lyrics_font_flex_width", 100)

    /** Lyrics-only Google Sans Flex `opsz` axis. */
    val WEAR_LYRICS_FONT_FLEX_OPTICAL_SIZE: PreferenceDefinition<Int> =
            SimplePreferenceDefinition("wear_lyrics_font_flex_optical_size", 18)

    /** Lyrics-only Google Sans Flex `GRAD` axis. */
    val WEAR_LYRICS_FONT_FLEX_GRADE: PreferenceDefinition<Int> =
            SimplePreferenceDefinition("wear_lyrics_font_flex_grade", 0)

    /** Lyrics-only Google Sans Flex `ROND` axis. */
    val WEAR_LYRICS_FONT_FLEX_ROUNDNESS: PreferenceDefinition<Int> =
            SimplePreferenceDefinition("wear_lyrics_font_flex_roundness", 0)

    /** Playback-time-only Google Sans Flex `wdth` axis. */
    val WEAR_TRACK_TIME_FONT_FLEX_WIDTH: PreferenceDefinition<Int> =
            SimplePreferenceDefinition("wear_track_time_font_flex_width", 100)

    /** Playback-time-only Google Sans Flex `opsz` axis. */
    val WEAR_TRACK_TIME_FONT_FLEX_OPTICAL_SIZE: PreferenceDefinition<Int> =
            SimplePreferenceDefinition("wear_track_time_font_flex_optical_size", 18)

    /** Playback-time-only Google Sans Flex `GRAD` axis. */
    val WEAR_TRACK_TIME_FONT_FLEX_GRADE: PreferenceDefinition<Int> =
            SimplePreferenceDefinition("wear_track_time_font_flex_grade", 0)

    /** Playback-time-only Google Sans Flex `ROND` axis. */
    val WEAR_TRACK_TIME_FONT_FLEX_ROUNDNESS: PreferenceDefinition<Int> =
            SimplePreferenceDefinition("wear_track_time_font_flex_roundness", 0)

    /** Bounds for the percentage-based typography controls. The scale ceiling is deliberately
     *  modest: the faces reserve fixed vertical space for the title/artist block, so a larger
     *  multiplier would push the artist line under the transport controls rather than look bigger.
     *  The opacity floor keeps text from being configured into complete invisibility, which reads
     *  as a rendering bug rather than a setting. */
    const val TYPOGRAPHY_MIN_SCALE: Int = 70
    const val TYPOGRAPHY_MAX_SCALE: Int = 140
    const val TYPOGRAPHY_MIN_OPACITY: Int = 20
    const val TYPOGRAPHY_MIN_TRACKING: Int = -5
    const val TYPOGRAPHY_MAX_TRACKING: Int = 20

    /** When the track position ("1:23 / 3:45") is shown on the now-playing screen: "always",
     *  "playing" (only while music plays), "paused" (only while paused) or "never". */
    val WEAR_TRACK_TIME_MODE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_track_time_mode", "always")

    /** When the edge ring's position tick is drawn - see [SeekMarkerVisibility] for what each
     *  value means and why the default is the one that only appears during a seek. */
    val WEAR_SEEK_MARKER: PreferenceDefinition<String> =
            SimplePreferenceDefinition(
                    "wear_seek_marker",
                    SeekMarkerVisibility.DURING_SEEK.preferenceValue)

    /** Extract accent color from album art on the watch (when off, uses the static theme accent). */
    val WEAR_DYNAMIC_ACCENT: PreferenceDefinition<Boolean> = SimplePreferenceDefinition("wear_dynamic_accent", true)

    /** One color policy for the complete interactive watch UI. "normal" uses the user's fixed
     * [WEAR_NORMAL_COLOR], "desaturated" derives a softened accent from the current cover and
     * "expressive" uses the full album palette (distinct primary/secondary/tertiary swatches).
     * This supersedes the old independent artist/progress switches, which remain readable only
     * for migration. */
    val WEAR_COLOR_TREATMENT: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_color_treatment", "expressive")

    /** Hex color (#RRGGBB) used by the unified "normal" color treatment. */
    val WEAR_NORMAL_COLOR: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_normal_color", "")

    /** Whether the "normal" color treatment generates a palette of secondary and tertiary colors 
     *  from the base color, or uses the base color for all three slots. */
    val WEAR_NORMAL_COLOR_MULTI: PreferenceDefinition<Boolean> =
            SimplePreferenceDefinition("wear_normal_color_multi", true)

    /**
     * Which swatch of the cover becomes the album accent every treatment then works from:
     * "balanced" (default) or "vibrant". Decoded by [AlbumAccentSource].
     *
     * Sits *before* [WEAR_COLOR_TREATMENT] in the pipeline rather than beside it - the treatments
     * decide what to build from the accent, this decides what the accent is. Both sides resolve it
     * through the same `selectPrimaryAccent`, which is what stopped the phone's miniature and the
     * watch from reporting two different colours for one cover.
     */
    val WEAR_ALBUM_ACCENT_SOURCE: PreferenceDefinition<String> =
            SimplePreferenceDefinition(
                    "wear_album_accent_source", AlbumAccentSource.BALANCED_VALUE)

    /** Tone filter applied on top of whatever [WEAR_COLOR_TREATMENT] produced: "none" (identity),
     *  "vibrant", "pastel", "warm" or "cool". Orthogonal to the treatment on purpose, so e.g.
     *  "triadic + pastel" needs no extra treatment value. Decoded by [ColorModifier]. */
    val WEAR_COLOR_MODIFIER: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_color_modifier", "none")

    /**
     * Turns the whole album-derived palette around the hue wheel, in degrees (0-359).
     *
     * Every treatment anchors its *primary* to the album's own hue and only rotates the companion
     * slots, so on its own the main accent never changes between treatments. This shifts all three
     * slots together, which varies that primary while leaving each harmony's internal angles
     * intact. 0 is the identity; a hand-picked Normal colour is never shifted
     * ([SurfacePaletteResolver.derive]).
     */
    val WEAR_COLOR_HUE_SHIFT: PreferenceDefinition<Int> =
            SimplePreferenceDefinition("wear_color_hue_shift", 0)

    /** How the now-playing title text behaves when it doesn't fit its available width: "smart"
     *  (default - shrinks first, wraps to 2 lines if that helps, and only scrolls as a last
     *  resort), "marquee" (always a single line at full size, scrolling if it overflows), "wrap"
     *  (fixed size, wraps up to 2 lines, ellipsizes beyond that - never shrinks or scrolls) or
     *  "shrink" (word-safe shrink down to a floor size, ellipsizes beyond that - never scrolls). */
    val WEAR_TITLE_TEXT_MODE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_title_text_mode", "smart")

    /** Per-surface treatment for artist text: "follow" inherits [WEAR_COLOR_TREATMENT], while
     *  "normal", "desaturated" and "expressive" override it for this target only.
     *  Historical "neutral"/"album"/"custom" values are still accepted by the watch and migrated
     *  by the phone settings UI. */
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

    /**
     * Retired. Nothing reads this any more and it must not be deleted.
     *
     * The mini-button row's position became fully automatic: `MainActivity.configureScreenButtons`
     * computes the lowest round-safe resting margin and `repositionScreenButtonsRow` lifts it only
     * as far as it must to clear the track text, so there is no offset left to prefer. There is no
     * settings row for it on either screen, and neither the watch nor the phone preview reads it.
     *
     * It stays in [EXPORTABLE] and [FaceScopedPreferences.SCOPED_KEYS] because published community
     * themes already carry it: `WatchThemeRepository.parsePublishedSettings` rejects the *whole*
     * profile on a key it does not recognise, so dropping it would make every theme in the gallery
     * that contains it fail to parse and silently vanish for everyone who has not installed it yet.
     * `OnlineThemeCatalogTest` fails if it is removed while any committed profile still names it,
     * which is the guard that turns that into a build error rather than a missing gallery. The
     * shipped constraint pins it to 42..42 so it can never carry anything but its own default.
     */
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

    /**
     * When the mini-buttons row may appear on this layout, as one of [ActivityVisibility]'s values
     * ("always" / "playing" / "paused" / "never"), mirroring the Track-time display option.
     * "playing" is [MusicViewModel][com.svartifoss.snfell.watch.view.MusicViewModel]'s `playing`
     * flag (true only during actual playback); a paused session and true idle both count as
     * "paused". Face-scoped and independent from the row's configured slots - "never" hides the row
     * and reclaims its space regardless of what is assigned, and the assignments still apply on
     * every other layout.
     */
    val WEAR_MINI_BUTTONS_MODE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_mini_buttons_mode", ActivityVisibility.ALWAYS)

    /**
     * When the four configurable screen gestures - the [ScreenQuadrant] taps/double-taps/
     * long-presses and the up/down/left [SwipeGesture]s - do anything on this layout, as an
     * [ActivityVisibility] value. Face-scoped and independent from their assignments in the Controls
     * tab. The centre tap (play/pause) and the quick-actions double-tap are separate, fixed
     * interactions and are never affected.
     */
    val WEAR_GESTURES_MODE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_gestures_mode", ActivityVisibility.ALWAYS)



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

    /** Dedicated background style for the awake Up Next pill in the quick-actions panel,
     *  independent from [WEAR_QUICK_PANEL_STYLE]. "follow" keeps the historical behaviour (the pill
     *  follows the quick-panel style); the rest give it its own fill: "accent", "translucent"
     *  (frosted semi-white), "white", "white_blur" (frosted white), "black", "dynamic" (a dark tone
     *  derived from the album accent). Face-scoped. Text/icon colour auto-contrasts. */
    val WEAR_UP_NEXT_PILL_STYLE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_up_next_pill_style", "follow")

    /** Whether to show an Up Next pill at the bottom of the player (the same information the AOD
     *  pill carries, on the main screen). Face-scoped. Meant to fill the space the mini-buttons row
     *  would take, so the watch only actually draws it while that row is not showing. */
    val WEAR_SHOW_UP_NEXT_PILL: PreferenceDefinition<Boolean> =
            SimplePreferenceDefinition("wear_show_up_next_pill", false)

    /** Whether the quick panel follows the active app's MediaSession actions or its manual slots. */
    val WEAR_QUICK_PANEL_SOURCE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_quick_panel_source", "manual")

    /** Visual style of the playback queue screen. */
    /** Every list-style value that fills a pill with the entry's own artwork. The phone checks
     *  this to decide whether to send higher-resolution covers, and the preview to draw them;
     *  the watch resolves the same values through QueueStyle.isCover. */
    val COVER_LIST_STYLES: Set<String> = setOf(
            "cover", "cover_blur", "cover_tonal", "cover_compact", "cover_tall", "cover_square")

    val WEAR_QUEUE_STYLE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_queue_style", "glass")

    /** When [WEAR_QUEUE_STYLE] is a Cover variant, whether an eligible quick-panel action row
     *  (one whose icon is genuine fetched cover art - e.g. a streaming shortcut's cached
     *  thumbnail - not just a generic app icon) also fills its pill with that art, the same
     *  treatment the Up Next row gets. Off by default: the underlying online-thumbnail fetch is
     *  itself opt-in, and not every user wants their shortcut rows turned into cover art. */
    val WEAR_QUICK_PANEL_SHORTCUT_COVER: PreferenceDefinition<Boolean> =
            SimplePreferenceDefinition("wear_quick_panel_shortcut_cover", false)

    /** Height of the full-width list pills - "compact", "normal", "tall" or "xtall". Applies to
     *  every list style, not just the cover ones, so a user can have roomy Tonal rows as easily as
     *  roomy Cover rows. The style contributes the padding rhythm; this contributes the content
     *  height they wrap. */
    val WEAR_LIST_ROW_SIZE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_list_row_size", "normal")

    /** Visual style of the edge progress/seek ring: "solid" (default), "dashed", "dots",
     *  "hairline", "comet", or one of the clock-index styles (60 dots, 60 ticks, 12 segments) -
     *  see RingStyle on the watch. */
    val WEAR_PROGRESS_STYLE: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_progress_style", "solid")

    /** Geometry of the resting/interactive edge ring, independent from its stroke style. */
    val WEAR_PROGRESS_LAYOUT: PreferenceDefinition<String> =
            SimplePreferenceDefinition("wear_progress_layout", "edge")

    /** Visual style of the scrub-time and volume readouts. Values are kept as stable tokens so
     *  new appearances can be added without changing existing saved faces. */
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

    /** Phone-local opt-out for developer announcement push notifications (Firebase Cloud
     *  Messaging). Enabled by default; gates the topic subscription in AnnouncementNotifications.
     *  Deliberately excluded from config backup/import and from EXPORTABLE, same reasoning as
     *  [CRASH_REPORTING_ENABLED] - it is this device's own consent choice, and announcements are a
     *  phone-only concept with no watch-side meaning. */
    val ANNOUNCEMENTS_ENABLED: PreferenceDefinition<Boolean> =
            SimplePreferenceDefinition("announcements_enabled", true)

    /** Shows/hides the phone app's persistent mini player bar (the currently-playing summary
     *  docked above the bottom nav). Enabled by default. Deliberately excluded from EXPORTABLE:
     *  it is a phone-UI-only concept with no watch-side meaning, so it does not sync to the
     *  watch. ConfigBackup still preserves it as part of the complete phone configuration. */
    val MINI_PLAYER_ENABLED: PreferenceDefinition<Boolean> =
            SimplePreferenceDefinition("mini_player_enabled", true)

    fun isAnyKindOfAutoStartEnabled(preferences: SharedPreferences): Boolean {
        return Preferences.getBoolean(preferences, AUTO_START) || Preferences.getEnum(preferences, AUTO_START_MODE) != AutoStartMode.OFF
    }

    /** Every preference that represents a configurable "default behavior" rather than transient
     *  navigation/runtime state (e.g. [LAST_MENU_DISPLAYED] is deliberately excluded). Used by
     *  the config export/import feature - add new preferences here too when adding them above. */
    val EXPORTABLE: List<PreferenceDefinition<*>> = listOf(
            ALWAYS_SHOW_TIME, PAUSE_ON_SWIPE_EXIT, ROTATING_CROWN_OFF_PERIOD, ROTATING_CROWN_SENSITIVITY,
            ROTARY_SEEK, WEAR_ROTARY_ACTION, HAPTIC_FEEDBACK, APP_LANGUAGE,
            DISABLE_PHYSICAL_DOUBLE_CLICK_IN_AMBIENT, AUTO_START_MODE,
            AUTO_START_APP_BLACKLIST, CLOSE_TIMEOUT, WEAR_CLOSE_ON_IDLE,
            WEAR_PAUSED_HOLD, WEAR_IDLE_BUTTON_ACTION, WEAR_IDLE_AUTO_OPEN,
            WEAR_KEEP_SCREEN_ON, LYRICS_ENABLED,
            ENABLE_NOTIFICATION_POPUP, NOTIFICATION_TIMEOUT,
            ALWAYS_SELECT_CENTER_ACTION, DIM_ALBUM_ART, ALBUM_ART_STYLE, ALBUM_ART_FILTER,
            ALBUM_ART_BLUR_RADIUS,
            ALBUM_ART_DIM_STRENGTH, WEAR_PLAYER_SHADING_STYLE, WEAR_PLAYER_SHADING_INTENSITY,
            WEAR_SHADING_COLOR_MODE, WEAR_SHADING_CUSTOM_COLOR, WEAR_SHOW_SOURCE_ICON,
            VOLUME_OVERLAY_TIMEOUT, ROTARY_DEADZONE, AMBIENT_ALBUM_ART_OPACITY,
            WEAR_AOD_STYLE, WEAR_AOD_SHOW_ART, WEAR_AOD_ART_TREATMENT,
            WEAR_AOD_SHOW_CLOCK, WEAR_AOD_SHOW_TRACK_INFO,
            WEAR_AOD_COLOR_MODE, WEAR_AOD_CUSTOM_COLOR, WEAR_AOD_SHOW_TRANSPORT, WEAR_AOD_SHOW_PROGRESS,
            WEAR_AOD_SHOW_PILLS, WEAR_AOD_INTENSITY,
            WEAR_CLOCK_COLOR_MODE, WEAR_CLOCK_CUSTOM_COLOR, WEAR_CLOCK_OPACITY, WEAR_CLOCK_FONT,
            WEAR_CLOCK_FONT_WEIGHT, WEAR_CLOCK_FONT_ITALIC, WEAR_CLOCK_FONT_SCALE,
            WEAR_CLOCK_FONT_TRACKING, WEAR_LYRICS_FONT,
            WEAR_METADATA_SHOW_CORE, WEAR_METADATA_SHOW_RELEASE, WEAR_METADATA_SHOW_CREDITS,
            WEAR_METADATA_SHOW_IDENTIFIERS, WEAR_METADATA_SHOW_TECHNICAL,
            WEAR_METADATA_SHOW_PLAYBACK, WEAR_SPLIT_PANEL,
            METADATA_LOOKUP_ENABLED,
            WEAR_ARTIST_ADAPTIVE_CONTRAST, WEAR_CLOCK_ADAPTIVE_CONTRAST, WEAR_PROGRESS_GRADIENT,
            WEAR_TITLE_COLOR_MODE, WEAR_TITLE_CUSTOM_COLOR, WEAR_TITLE_ADAPTIVE_CONTRAST,
            WEAR_CENTER_LONG_PRESS_QUEUE, WEAR_CENTER_LONG_PRESS, WEAR_SCREEN_FACE,
            WEAR_ACTIVE_CUSTOM_THEME_ID, WEAR_AVAILABLE_CUSTOM_THEMES, WEAR_CUSTOM_THEME_SCHEMA,
            WEAR_CUSTOM_THEME_COMPLETE, WEAR_CUSTOM_THEME_REVISION,
            WEAR_SHOW_TRACK_TITLE, WEAR_SHOW_TRACK_ARTIST,
            WEAR_PLAYER_CONTROLS_VISIBLE, WEAR_INTERNAL_PROGRESS_VISIBLE,
            WEAR_EDGE_PROGRESS_VISIBLE, WEAR_EDGE_SEEK_ENABLED, WEAR_ACCENT_FLOOR,
            WEAR_ACCENT_FLOOR_COLOR_MODE, WEAR_ACCENT_FLOOR_CUSTOM_COLOR,
            WEAR_BACKGROUND_LAYERS,
            WEAR_EXPRESSIVE_SEEK_MODE, WEAR_SCREEN_THEME, WEAR_QUADRANT_TAP_FLASH, WEAR_FONT,
            WEAR_FONT_ALL_SCREENS, WEAR_CAROUSEL_CARD_SHAPE, WEAR_NOTE_COVER_SHAPE, WEAR_NOTE_SHOW_COVER,
            WEAR_TITLE_CENTERED,
            WEAR_CHAT_COVER_SHAPE, WEAR_CHAT_SHOW_COVER, WEAR_METADATA_COVER_SHAPE, WEAR_METADATA_SHOW_COVER,
            WEAR_TITLE_SHADOW_STYLE, WEAR_TITLE_SHADOW_COLOR_MODE,
            WEAR_TITLE_SHADOW_CUSTOM_COLOR, WEAR_TITLE_SHADOW_STRENGTH,
            WEAR_ARTIST_SHADOW_STYLE, WEAR_ARTIST_SHADOW_COLOR_MODE,
            WEAR_ARTIST_SHADOW_CUSTOM_COLOR, WEAR_ARTIST_SHADOW_STRENGTH,
            WEAR_TITLE_OUTLINE_STYLE, WEAR_TITLE_OUTLINE_COLOR_MODE,
            WEAR_TITLE_OUTLINE_CUSTOM_COLOR,
            WEAR_ARTIST_OUTLINE_STYLE, WEAR_ARTIST_OUTLINE_COLOR_MODE,
            WEAR_ARTIST_OUTLINE_CUSTOM_COLOR,
            WEAR_TITLE_TEXT_BG_STYLE, WEAR_TITLE_TEXT_BG_COLOR_MODE,
            WEAR_TITLE_TEXT_BG_CUSTOM_COLOR, WEAR_TITLE_TEXT_BG_OPACITY,
            WEAR_ARTIST_TEXT_BG_STYLE, WEAR_ARTIST_TEXT_BG_COLOR_MODE,
            WEAR_ARTIST_TEXT_BG_CUSTOM_COLOR, WEAR_ARTIST_TEXT_BG_OPACITY,
            WEAR_TRACK_TIME_MODE, WEAR_SEEK_MARKER,
            WEAR_TRACK_TIME_FONT, WEAR_TRACK_TIME_FONT_WEIGHT,
            WEAR_TRACK_TIME_FONT_ITALIC, WEAR_TRACK_TIME_FONT_SCALE,
            WEAR_TRACK_TIME_FONT_OPACITY, WEAR_TRACK_TIME_FONT_TRACKING,
            WEAR_DYNAMIC_ACCENT, WEAR_COLOR_TREATMENT, WEAR_NORMAL_COLOR, WEAR_NORMAL_COLOR_MULTI, WEAR_COLOR_MODIFIER,
            WEAR_COLOR_HUE_SHIFT, WEAR_ALBUM_ACCENT_SOURCE,
            WEAR_TITLE_FONT, WEAR_TITLE_FONT_WEIGHT, WEAR_TITLE_FONT_ITALIC, WEAR_TITLE_FONT_SCALE,
            WEAR_TITLE_FONT_OPACITY, WEAR_TITLE_FONT_TRACKING, WEAR_TITLE_TEXT_CASE,
            WEAR_ARTIST_FONT, WEAR_ARTIST_FONT_WEIGHT, WEAR_ARTIST_FONT_ITALIC, WEAR_ARTIST_FONT_SCALE,
            WEAR_ARTIST_FONT_OPACITY, WEAR_ARTIST_FONT_TRACKING, WEAR_ARTIST_TEXT_CASE,
            WEAR_SOURCE_ICON_SCALE, WEAR_SOURCE_ICON_OPACITY,
            WEAR_FONT_FLEX_WIDTH, WEAR_FONT_FLEX_OPTICAL_SIZE,
            WEAR_FONT_FLEX_GRADE, WEAR_FONT_FLEX_ROUNDNESS,
            WEAR_TITLE_FONT_FLEX_WIDTH, WEAR_TITLE_FONT_FLEX_OPTICAL_SIZE,
            WEAR_TITLE_FONT_FLEX_GRADE, WEAR_TITLE_FONT_FLEX_ROUNDNESS,
            WEAR_ARTIST_FONT_FLEX_WIDTH, WEAR_ARTIST_FONT_FLEX_OPTICAL_SIZE,
            WEAR_ARTIST_FONT_FLEX_GRADE, WEAR_ARTIST_FONT_FLEX_ROUNDNESS,
            WEAR_CLOCK_FONT_FLEX_WIDTH, WEAR_CLOCK_FONT_FLEX_OPTICAL_SIZE,
            WEAR_CLOCK_FONT_FLEX_GRADE, WEAR_CLOCK_FONT_FLEX_ROUNDNESS,
            WEAR_LYRICS_FONT_FLEX_WIDTH, WEAR_LYRICS_FONT_FLEX_OPTICAL_SIZE,
            WEAR_LYRICS_FONT_FLEX_GRADE, WEAR_LYRICS_FONT_FLEX_ROUNDNESS,
            WEAR_TRACK_TIME_FONT_FLEX_WIDTH, WEAR_TRACK_TIME_FONT_FLEX_OPTICAL_SIZE,
            WEAR_TRACK_TIME_FONT_FLEX_GRADE, WEAR_TRACK_TIME_FONT_FLEX_ROUNDNESS,
            WEAR_ALBUM_ART_FADE, WEAR_SCREEN_BUTTONS_OFFSET, WEAR_SCREEN_BUTTONS_CURVE_STYLE,
            WEAR_SCREEN_BUTTONS_BG, WEAR_SCREEN_BUTTONS_OPACITY, WEAR_SCREEN_BUTTONS_SHAPE,
            WEAR_MINI_BUTTONS_MODE, WEAR_GESTURES_MODE,
            WEAR_OVERLAY_BLUR_RADIUS,
            WEAR_OVERLAY_BACKDROP_STYLE,
            WEAR_VOLUME_STYLE, WEAR_VOLUME_LAYOUT,
            WEAR_QUICK_PANEL_STYLE, WEAR_QUICK_PANEL_LAYOUT, WEAR_UP_NEXT_PILL_STYLE,
            WEAR_SHOW_UP_NEXT_PILL,
            WEAR_QUICK_PANEL_SOURCE, WEAR_QUEUE_STYLE, WEAR_QUICK_PANEL_SHORTCUT_COVER,
            WEAR_LIST_ROW_SIZE,
            WEAR_PROGRESS_STYLE, WEAR_PROGRESS_LAYOUT, WEAR_SEEK_STYLE, WEAR_SEEK_LAYOUT,
            WEAR_TITLE_TEXT_MODE, WEAR_ARTIST_COLOR_MODE, WEAR_ARTIST_CUSTOM_COLOR, WEAR_ARTIST_DESATURATED,
            WEAR_PROGRESS_COLOR_MODE, WEAR_PROGRESS_CUSTOM_COLOR, WEAR_PROGRESS_DESATURATED,
            WEAR_VOLUME_COLOR_MODE, WEAR_VOLUME_CUSTOM_COLOR,
            WEAR_QUICK_PANEL_COLOR_MODE, WEAR_QUICK_PANEL_CUSTOM_COLOR
    )
}
