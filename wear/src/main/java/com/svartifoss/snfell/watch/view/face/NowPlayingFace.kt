package com.svartifoss.snfell.watch.view.face

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.svartifoss.snfell.common.AccentFloorStyle
import com.svartifoss.snfell.common.MiniButtonSurfaces
import com.svartifoss.snfell.common.SplitPanelStyle
import com.svartifoss.snfell.common.LyricLine
import com.svartifoss.snfell.common.PlayerBackgroundStyle
import com.svartifoss.snfell.common.TrackMetadataFields
import com.svartifoss.snfell.common.SpecialEliteKeywordPolicy
import com.svartifoss.snfell.common.WatchTypography
import com.svartifoss.snfell.common.PlayerShadingStyle
import com.svartifoss.snfell.watch.theme.GoogleSansFamily
import com.svartifoss.snfell.watch.theme.SpecialEliteFamily
import com.svartifoss.snfell.watch.theme.WatchTheme
import com.svartifoss.snfell.watch.theme.flexFontFamily
import com.svartifoss.snfell.watch.theme.watchFontFamily

internal data class MetadataVisibility(
        val title: Boolean,
        val artist: Boolean
)

/** User choices own real metadata, while operational status must remain visible. */
internal fun resolveMetadataVisibility(
        title: String,
        artist: String,
        showTitle: Boolean,
        showArtist: Boolean,
        titleIsStatus: Boolean,
        artistIsStatus: Boolean
): MetadataVisibility = MetadataVisibility(
        title = title.isNotEmpty() && (showTitle || titleIsStatus),
        artist = artist.isNotEmpty() && (showArtist || artistIsStatus)
)

/**
 * Classic renders the app mark in a sibling View rather than inside [resolveMetadataVisibility]'s
 * text view. It must therefore explicitly follow that resolved line: leaving it visible when the
 * artist is hidden produces an orphaned icon, while a status line such as "Playback stopped"
 * remains a valid sibling.
 */
internal fun shouldShowClassicSourceIcon(
        hasSourceIcon: Boolean,
        artistVisible: Boolean,
        ambient: Boolean
): Boolean = hasSourceIcon && artistVisible && !ambient

/** The full-screen edge View must survive visual hiding whenever its independent gesture is on. */
internal fun shouldKeepEdgeSeekView(
        edgeProgressVisible: Boolean,
        edgeSeekEnabled: Boolean
): Boolean = edgeProgressVisible || edgeSeekEnabled

/** When true, the face's central ring is interactive for seeking. */
internal fun shouldEnableCentralSeek(expressiveSeekMode: String): Boolean =
        expressiveSeekMode == "central"


/**
 * Contract between [MainActivity][com.svartifoss.snfell.watch.view.MainActivity] and a
 * now-playing *face* - the swappable central rendering of the now-playing screen (artwork
 * texts, transport controls, progress). Faces only render and raise high-level events; they
 * own no input plumbing. Everything else on the screen is shared by all faces and stays in
 * MainActivity: the FourWayTouchLayout quadrant/swipe gestures, stem buttons, rotary input,
 * mini buttons, quick actions panel, volume/seek overlays, notification popup and the idle
 * ("nothing playing") state.
 *
 * The player gallery is selected by
 * [MiscPreferences.WEAR_SCREEN_FACE][com.svartifoss.snfell.common.MiscPreferences.WEAR_SCREEN_FACE]
 * on the phone:
 *  - "classic" - the original View-based presentation (bezel seek ring, quadrant hint icons,
 *    centered text block). It predates this contract and keeps rendering through MainActivity's
 *    views directly; MainActivity *is* its implementation.
 *  - "expressive" - [ExpressiveFace], a Compose face styled after the Material 3 Expressive
 *    system media controls.
 *  - "vinyl" - [VinylFace], a real dark record whose label is the current cover.
 *  - "poster" - [PosterFace], full-bleed artwork with editorial typography.
 *  - "studio" - [StudioFace], a restrained, legibility-first player.
 *  - "halo" - [HaloFace], compact artwork surrounded by album-color progress rings.
 *  - "aurora" - [AuroraFace], layered album-color gradients and a single play focus.
 *  - "eclipse" - [EclipseFace], a near-black AMOLED composition.
 *  - "spectrum" - [SpectrumFace], an animated per-track bar field whose color fill can show
 *    progress without changing its full-width seek interaction.
 * The curated faces keep their composition stable when mini buttons are configured and only
 * protect lower chrome from the shortcut row. They do not add their own fixed rows of
 * queue/menu/volume actions: those actions remain user-configurable through mini buttons and
 * gestures.
 *
 * Ambient (always-on display) rendering is selected separately by
 * [MiscPreferences.WEAR_AOD_STYLE][com.svartifoss.snfell.common.MiscPreferences.WEAR_AOD_STYLE]:
 * the classic AOD stays View-based in MainActivity, while the expressive AOD is [ExpressiveFace]
 * itself with [NowPlayingFaceState.ambient] set - an outlined, animation-free variant that is
 * burn-in-audited and cheap on AMOLED (no fills, no marquee, no clock of its own; the host's
 * jiggled ambient clock covers time).
 */
/**
 * One entry of the playback queue, ready to draw: text plus the cover the phone already resolved
 * and shipped as a queue thumbnail (see `QueueArtworkResolver`). [art] is null whenever that
 * resolution found nothing, which is common for streaming players.
 */
/**
 * One configured mini button, ready for a face that hosts the row inside its own composition
 * instead of letting the host's View row float over it.
 *
 * [slotCode] is the [ScreenButtons][com.svartifoss.snfell.common.ScreenButtons] slot, and it is
 * what goes back to the host on a tap: the face never resolves the action itself, exactly as it
 * never resolves a quadrant's. Only slots the user actually configured appear here, so the list's
 * *size* is the number of buttons to draw.
 */
data class FaceMiniButton(
        val slotCode: Int,
        val icon: androidx.compose.ui.graphics.ImageBitmap?,
        /** False for a full-colour icon (an app icon, fetched cover art) that must not be
         *  flattened to the surface's single tint. */
        val iconTintable: Boolean = true,
        val description: String? = null
)

data class QueueCard(
        val entryId: String,
        val title: String,
        val artist: String,
        val art: androidx.compose.ui.graphics.ImageBitmap?
)

data class NowPlayingFaceState(
        val title: String = "",
        val artist: String = "",
        /** Independent metadata visibility preferences. Status/error text may force the
         * corresponding line visible in the host before it reaches this state. */
        val showTitle: Boolean = true,
        val showArtist: Boolean = true,
        /**
         * Whether [title] / [artist] currently hold operational status text ("ERROR", "Playback
         * Stopped", "No phone connected") rather than real track metadata.
         *
         * Every face renders both the same way, so most never need this. It exists for faces that
         * *accumulate* metadata instead of only displaying the current value - the Chat face's
         * thread being the first - where a status string would otherwise be recorded as though it
         * were a track that played, and then keep scrolling past forever. Matching on the strings
         * themselves is not an option: they are localized into thirteen languages.
         */
        val titleIsStatus: Boolean = false,
        val artistIsStatus: Boolean = false,
        /** Interactive player theme shared by Classic and every Compose face. Ambient rendering
         *  deliberately ignores it and continues to use the independent AOD preferences. */
        val screenTheme: ScreenTheme = ScreenTheme.DEFAULT,
        /** Whether built-in player controls are painted. Their hit targets and accessibility
         *  actions stay active when false; this is a visual preference, not an input remap. */
        val showControls: Boolean = true,
        val playing: Boolean = false,
        /** True when there is no track at all - the face renders nothing and the shared idle
         *  ("nothing playing") group shows through instead. A *paused* track is not idle. */
        val idle: Boolean = true,
        /** Playback progress fraction (0f..1f) of [positionMs] / [durationMs]. */
        val progress: Float = 0f,
        val seekable: Boolean = false,
        /** Whether curated layouts should draw their optional, composition-owned progress
         *  indicator. Expressive's cookie ring and Material's center ring belong to their
         *  play/pause controls and are intentionally always drawn. The host's edge arc and
         *  ambient progress remain independent choices. */
        val showInternalProgress: Boolean = true,
        /** When true (expressive "central" seek mode) the face's own progress ring is draggable to
         *  scrub; otherwise the ring is display-only and seeking is left to the host (edge ring or
         *  rotary crown). */
        val centralSeekEnabled: Boolean = false,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        /**
         * The session's playback rate, as the phone reports it.
         *
         * Only the Metadata face reads it, and only to show a row when it is *not* 1x. It is kept
         * on the state rather than fetched, because the position readout beside it is already
         * extrapolated at this rate by `PlaybackClock` - a face showing one and not the other would
         * be showing a counter running visibly faster than the number that explains it.
         */
        val playbackSpeed: Float = 1f,
        /** Whether the "1:23 / 3:45" line should show - already combines the phone-synced
         *  track-time mode with whether a usable position exists. */
        val showTrackTime: Boolean = false,
        /** Raw album accent (or static theme accent) - the same color the shared UI tracks. */
        val accentColor: Int = WatchTheme.ACCENT_DEFAULT,
        /** Album-derived surface tint resolved by the host for the Material backdrop. Unlike
         *  [accentColor], this may already be softened when an album desaturation option is active,
         *  allowing the face to follow that choice without duplicating preference-mode logic. */
        val materialSurfaceColor: Int = WatchTheme.ACCENT_DEFAULT,
        /** Whether [materialSurfaceColor] is the softened album variant. The Material renderer
         *  uses this to lift only the desaturated treatment while preserving its normal palette. */
        val materialSurfaceSoftened: Boolean = false,
        /** Additional real swatches extracted from the current artwork. Curated faces use all
         *  three colors for their gradients; when artwork has no useful extra swatches the host
         *  supplies harmonized fallbacks instead. */
        val secondaryAccentColor: Int = WatchTheme.ACCENT_DEFAULT,
        val tertiaryAccentColor: Int = WatchTheme.ACCENT_DEFAULT,
        /** Current album art. The classic/expressive faces render art through the host's shared
         *  background ImageView instead and ignore this; it exists for faces that draw the art
         *  as a shape of their own (the vinyl face's disc). Wraps the host bitmap - no copy. */
        val albumArt: androidx.compose.ui.graphics.ImageBitmap? = null,
        /** Icon of the app currently playing, shown next to the artist on faces that support it
         *  (Immersive). Null when unavailable or the user turned the element off. */
        val sourceIcon: androidx.compose.ui.graphics.ImageBitmap? = null,
        /** True when [sourceIcon] is the notification's monochrome template glyph, which faces
         *  tint to their own artist colour; false for full-colour launcher artwork. */
        val sourceIconTemplate: Boolean = false,
        /** Resolved artist text color (already accounts for color mode, desaturation and the
         *  plain-white status-message override - it mirrors the classic artist line exactly). */
        val artistColor: Int = WatchTheme.ACCENT_DEFAULT,
        /**
         * Colour for the track title, or null to keep whatever colour the face designed.
         *
         * Null is the default and the normal case: every face draws its title in its own white,
         * several at deliberately different alphas, so there is no single "title colour" to fall
         * back to. Faces read it through [titleTextColor], which preserves their own alpha.
         */
        val titleColor: Int? = null,
        /** Queue entry immediately after the current item. The host refreshes it when a visual AOD
         *  that exposes Up Next becomes active. Empty values mean that the playing app did not
         *  publish a usable queue; ambient faces render a quiet unavailable state instead of stale
         *  metadata. */
        val upNextTitle: String = "",
        val upNextArtist: String = "",

        /**
         * The playback queue as renderable cards, current track first, for faces that show the
         * queue itself rather than a single "up next" line.
         *
         * Empty for every other face and whenever the player publishes no queue - a face that
         * consumes this must degrade to the current track alone rather than showing nothing, since
         * "no queue at all" is a common and legitimate state (see the queue notes in CLAUDE.md).
         */
        val queueCards: List<QueueCard> = emptyList(),

        /**
         * Timed lyric lines for the current track, for a face that follows the words.
         *
         * Empty for every face but Verse, and empty there too whenever the track has no synced
         * lyric - which is common, so a face consuming this must have a composition for "no words"
         * that looks deliberate rather than broken.
         *
         * The host only fills this while such a face is selected: the lookup is a network call on
         * the phone, and nobody who has not asked for lyrics should be paying for one.
         */
        /** The accent wash along the bottom edge - a shared piece, not one face's fixture.
         *  Rendered by [PlayerBackgroundTreatment] so it lands above the backdrop and below the
         *  face's own content, which is the only stacking that works for it. */
        val accentFloor: AccentFloorStyle = AccentFloorStyle.OFF,
        /** Resolved floor colour, independent from the face's main accent. */
        val accentFloorColor: Int = WatchTheme.ACCENT_DEFAULT,
        /** How the Split face fills its lower panel. Ignored by every other face - Split is the
         *  only one that paints its own backdrop, which is why it needs its own control. */
        val splitPanelStyle: SplitPanelStyle = SplitPanelStyle.DEFAULT,
        val lyricLines: List<LyricLine> = emptyList(),
        /**
         * True while the lookup for the current track is still outstanding.
         *
         * Distinguishes "not yet" from "this track has none", which look identical in
         * [lyricLines] and want opposite treatments - one is worth waiting through, the other is
         * final.
         */
        val lyricsPending: Boolean = false,
        /** Resolved progress accent (already accounts for the progress color mode). */
        val progressColor: Int = WatchTheme.ACCENT_DEFAULT,
        /** Raw universal progress-ring style (solid/dashed/dots/hairline/comet). Interactive
         *  bezel and low-power visual rings consume the same choice; AOD has no duplicate picker. */
        val progressRingStyle: String = "solid",
        /** True while the user has no mini buttons configured - faces with a default bottom
         *  button trio (queue/volume/overflow) may show it; configured mini buttons own that
         *  part of the screen otherwise. */
        val showDefaultBottomPills: Boolean = false,
        /** Actual top edge of the configured mini-button row as a fraction of screen height.
         *  Curated layouts use this instead of assuming the default offset, so a user can move
         *  the shortcuts without making the player overlap them. */
        val miniButtonsTopFraction: Float = .58f,
        /**
         * The configured mini buttons, for a face that draws them itself.
         *
         * Non-empty only while the host has handed the row over (see
         * [MiniButtonPlacement.isHostedByFace][com.svartifoss.snfell.common.MiniButtonPlacement.Companion.isHostedByFace]):
         * every other face leaves them to the shared View row above the artwork, and reading this
         * would draw them a second time.
         */
        val miniButtons: List<FaceMiniButton> = emptyList(),
        /**
         * How those buttons are painted, resolved from the user's own `screen_buttons_bg_style`
         * by the host so a face-drawn button and the View row cannot look different.
         * [MiniButtonSurfaces.Surface.followsFaceNeutral] is the default and means "this face's
         * own button", which is how a face keeps its designed control until the user picks a
         * style.
         */
        val miniButtonSurface: MiniButtonSurfaces.Surface = MiniButtonSurfaces.Surface(
                followsFaceNeutral = true),
        /** The mini-button group's opacity preference, applied by the face as the View row
         *  applies it to itself. */
        val miniButtonsAlpha: Float = 1f,
        /** Whether the interactive curved clock is visible. This is the Compose equivalent of
         *  the Classic face's ALWAYS_SHOW_TIME clock and is independent from the AOD clock. */
        val showClock: Boolean = false,
        /** Whether to show the awake Up Next pill at the bottom of the player - the same pill the
         *  AOD shows, brought to the main screen (MiscPreferences.WEAR_SHOW_UP_NEXT_PILL). The host
         *  only sets this true while the mini-buttons row is not occupying that space, so the two
         *  never fight for the bottom band. */
        val showUpNextPill: Boolean = false,
        /** Resolved ARGB background/text colours for the awake Up Next pill, from the shared
         *  WEAR_UP_NEXT_PILL_STYLE (a transparent fill is a valid choice). */
        val upNextPillFill: Int = 0x38FFFFFF,
        val upNextPillTextColor: Int = WatchTheme.COLOR_WHITE,
        /** Fully-resolved ARGB colour (opacity already baked in) for the awake clock, so both the
         *  Compose [FaceClock] and the Classic View clock consume the identical value. The host
         *  resolves it from the clock colour-mode/opacity prefs, sampling the artwork region under
         *  the clock for the "dynamic" mode - see MainActivity.resolveClockColor. */
        val clockColor: Int = WatchTheme.COLOR_WHITE_60,
        /** Current wall-clock time text (e.g. "7:21"), kept fresh by the host's updateClock(). Used
         *  by the Chrono ambient face, which renders its own large clock in Compose; other faces
         *  keep using the host's jiggled ambient-clock View. */
        val clockText: String = "",
        /** True while the watch is in ambient (always-on display) mode. Faces must render an
         *  outlined, animation-free, mostly-black variant: no fills, no marquee, no track time
         *  and no touch affordances (input is dead in ambient anyway). The optional static
         *  Up Next pill is independently controlled by [ambientShowPills]. */
        val ambient: Boolean = false,
        /** Whether the title/artist lines should show in ambient mode
         *  (MiscPreferences.WEAR_AOD_SHOW_TRACK_INFO). Ignored while [ambient] is false. */
        val ambientShowTrackInfo: Boolean = true,
        /** Resolved color for ambient outlines, glyphs, metadata and the host clock
         *  (MiscPreferences.WEAR_AOD_COLOR_MODE, already lifted for legibility on black by the
         *  host) - white, the album accent or a custom color.
         *  Ignored while [ambient] is false. */
        val ambientTint: Int = WatchTheme.COLOR_WHITE,
        /** Overall ambient brightness 0.2f..1f (MiscPreferences.WEAR_AOD_INTENSITY) - scales the
         *  alpha of everything the ambient face draws. Ignored while [ambient] is false. */
        val ambientIntensity: Float = 1f,
        /** Whether the outlined prev/play/next row shows in ambient mode. */
        val ambientShowTransport: Boolean = true,
        /** Whether the progress ring shows in ambient mode (needs [ambientShowTransport]). */
        val ambientShowProgress: Boolean = true,
        /** Whether the static outlined Up Next pill shows in supported ambient faces. */
        val ambientShowPills: Boolean = true,
        /** The global fallback font for title/artist text (MiscPreferences.WEAR_FONT key).
         *  Per-line "follow" choices resolve through it. */
        val fontKey: String = "google_sans",
        /** The title's own font choice, or "follow"/null to retain [fontKey]. */
        val titleFontKey: String? = null,
        /** The artist's own font choice, or "follow"/null to retain [fontKey]. */
        val artistFontKey: String? = null,
        /** The clock's own font choice (MiscPreferences.WEAR_CLOCK_FONT), or "follow"/null to keep
         *  tracking [fontKey] as the clock always did. Resolved by [WatchTypography.clockFontKey]. */
        val clockFontKey: String? = null,
        /** The lyrics' own font choice (MiscPreferences.WEAR_LYRICS_FONT), or "follow"/null to keep
         *  the serif the Verse face was designed around. Resolved by [lyricFont]. */
        val lyricsFontKey: String? = null,
        /** The elapsed/total readout's own font choice, or "follow"/null to preserve the
         *  individual face's authored numeric typeface. Resolved by [trackTimeFont]. */
        val trackTimeFontKey: String? = null,
        /**
         * Everything the phone knows about the playing track, for the Metadata face.
         *
         * Null until the phone answers, which is the face's own empty state rather than a loading
         * one worth blocking on - see MetadataFeed. Only requested while that face is selected, so
         * every other face carries a null here and pays nothing for it.
         */
        val metadata: com.svartifoss.snfell.proto.TrackMetadata? = null,
        /** Which blocks of the Metadata face the user left switched on - see
         *  [TrackMetadataFields.Group]. */
        val metadataGroups: Set<TrackMetadataFields.Group> = emptySet(),
        /** The clock's weight/italic/size/tracking. Its opacity is *not* here - that is baked into
         *  [clockColor] from WEAR_CLOCK_OPACITY, so [WatchTypography.clockSpec] pins alpha at 1. */
        val clockTypography: WatchTypography.TextSpec = WatchTypography.IDENTITY_TEXT,
        /** MiscPreferences.WEAR_TITLE_TEXT_MODE's raw value ("smart"/"marquee"/"wrap"/"shrink"),
         *  read by every face's title/artist text through AdaptiveTitleText - previously only the
         *  classic face's OutlineTextView consulted this. */
        val titleTextMode: String = "smart",
        /** Background/artwork treatment selected independently from this face's structural
         * layout. This lets, for example, Material controls use the Expressive blur or Poster
         * artwork treatment without swapping the controls themselves. */
        val backgroundStyle: PlayerBackgroundStyle = PlayerBackgroundStyle.COVER,
        /** Whether the selected [backgroundStyle] suppresses artwork (Hidden/Eclipse). Curated
         *  layouts with their own small cover shape fall back to the palette gradient. */
        val albumArtHidden: Boolean = false,
        /** Whether [backgroundStyle] requests monochrome artwork. */
        val albumArtGrayscale: Boolean = false,
        /** Whether [backgroundStyle] requests blurred artwork. */
        val albumArtBlurred: Boolean = false,
        /** Raw blur radius in pixels (MiscPreferences.ALBUM_ART_BLUR_RADIUS) - only meaningful
         *  together with [albumArtBlurred]. Kept in px, matching the host's own RenderEffect
         *  radius, rather than pre-converted to Dp so every consumer applies the same density
         *  conversion the platform blur itself expects. */
        val albumArtBlurRadiusPx: Float = 35f,
        /** Mirrors MiscPreferences.WEAR_ALBUM_ART_FADE. Shared full-screen artwork crossfades in
         *  the host; Vinyl/Halo's composition-owned mini covers use AlbumArtwork's crossfade. */
        val albumArtFade: Boolean = true,
        /** Master switch for the user-selected layer between artwork and player chrome. */
        val backdropDimEnabled: Boolean = true,
        /** Named soft/balanced/strong level resolved to a shared 0f..1f multiplier. */
        val backdropDimStrength: Float = .8f,
        /** Explicit treatment, or FOLLOW to keep the selected background's authored treatment. */
        val backdropShadingStyle: PlayerShadingStyle = PlayerShadingStyle.FOLLOW,
        /** Colour of the shading gradient (black by default; album/desaturated/custom resolved to
         *  a dark tone by the host). */
        val backdropShadingColor: Int = android.graphics.Color.BLACK,
        /** Icons of the actions configured on the LEFT / RIGHT screen quadrants (single tap). The
         *  faces with side action buttons render these instead of fixed skip glyphs when set, so
         *  the buttons follow the user's Controls config; null keeps the default previous/next
         *  skip icon and behaviour. Descriptions travel with the bitmaps so accessibility never
         *  announces “Previous” for a button configured as Volume, Search, etc. */
        val leftActionIcon: androidx.compose.ui.graphics.ImageBitmap? = null,
        val rightActionIcon: androidx.compose.ui.graphics.ImageBitmap? = null,
        val leftActionIconTintable: Boolean = true,
        val rightActionIconTintable: Boolean = true,
        val leftActionDescription: String? = null,
        val rightActionDescription: String? = null,
        /** Per-element typography resolved from the user's title preferences. Layers weight, slant,
         *  size, opacity and tracking over the [fontKey] family; the defaults are the exact
         *  identity, so a face that never consults these renders unchanged. */
        val titleTypography: WatchTypography.TextSpec = WatchTypography.IDENTITY_TEXT,
        /** As [titleTypography], for the artist line. Independent on purpose - a heavier title
         *  against a lighter artist line is the common ask. */
        val artistTypography: WatchTypography.TextSpec = WatchTypography.IDENTITY_TEXT,
        /** Typography deltas for the elapsed/total readout. The family stays face-authored until
         *  [trackTimeFontKey] is explicitly selected. */
        val trackTimeTypography: WatchTypography.TextSpec = WatchTypography.IDENTITY_TEXT,
        /** Size/opacity for [sourceIcon]. */
        val sourceIconTypography: WatchTypography.IconSpec = WatchTypography.IDENTITY_ICON,
        /** Google Sans Flex axes belonging to the global [fontKey] family. */
        val flexAxes: WatchTypography.FlexAxes = WatchTypography.IDENTITY_FLEX_AXES,
        /** Axes for an explicit Title-only Google Sans Flex choice. */
        val titleFlexAxes: WatchTypography.FlexAxes = WatchTypography.IDENTITY_FLEX_AXES,
        /** Axes for an explicit Artist-only Google Sans Flex choice. */
        val artistFlexAxes: WatchTypography.FlexAxes = WatchTypography.IDENTITY_FLEX_AXES,
        /** Axes for an explicit Clock-only Google Sans Flex choice. */
        val clockFlexAxes: WatchTypography.FlexAxes = WatchTypography.IDENTITY_FLEX_AXES,
        /** Axes for an explicit Lyrics-only Google Sans Flex choice. */
        val lyricsFlexAxes: WatchTypography.FlexAxes = WatchTypography.IDENTITY_FLEX_AXES,
        /** Axes for an explicit Track-time-only Google Sans Flex choice. */
        val trackTimeFlexAxes: WatchTypography.FlexAxes = WatchTypography.IDENTITY_FLEX_AXES,
) {
    /**
     * The [FontFamily] to use for the track title. The explicit [titleFontKey] choice applies,
     * falling back to [fontKey]. Google Sans Flex additionally bakes in [titleTypography]'s own
     * weight/slant plus the axes belonging to the family that selected it, since its variable axes
     * need a per-element instance no plain family lookup can express - see [flexFontFamily].
     */
    val titleFont: FontFamily
        get() {
            if (SpecialEliteKeywordPolicy.matches(title, artist)) {
                return SpecialEliteFamily
            }
            val resolved = WatchTypography.titleFontKey(titleFontKey, fontKey)
            return if (WatchTypography.isFlexFont(resolved)) {
                flexFontFamily(
                        titleTypography,
                        if (titleFontKey == WatchTypography.FLEX_FONT_KEY) {
                            titleFlexAxes
                        } else {
                            flexAxes
                        })
            } else {
                watchFontFamily(resolved)
            }
        }

    /**
     * The [FontFamily] to use for the artist line. The explicit [artistFontKey] choice falls back
     * to [fontKey]. For Google Sans Flex it bakes in [artistTypography]'s own weight/slant and
     * either its own axes or the global axes, depending on which family selection supplied Flex.
     */
    val artistFont: FontFamily
        get() {
            if (SpecialEliteKeywordPolicy.matches(title, artist)) {
                return SpecialEliteFamily
            }
            val resolved = WatchTypography.artistFontKey(artistFontKey, fontKey)
            return if (WatchTypography.isFlexFont(resolved)) {
                flexFontFamily(
                        artistTypography,
                        if (artistFontKey == WatchTypography.FLEX_FONT_KEY) {
                            artistFlexAxes
                        } else {
                            flexAxes
                        })
            } else {
                watchFontFamily(resolved)
            }
        }

    /** [titleTypography]'s weight as a Compose [FontWeight]. */
    val titleFontWeight: FontWeight get() = FontWeight(titleTypography.weight)

    /** [artistTypography]'s weight as a Compose [FontWeight]. */
    val artistFontWeight: FontWeight get() = FontWeight(artistTypography.weight)

    /** [titleTypography]'s slant, or null to leave the face's own choice alone. */
    val titleFontStyle: FontStyle? get() = FontStyle.Italic.takeIf { titleTypography.italic }

    /** [artistTypography]'s slant, or null to leave the face's own choice alone. */
    val artistFontStyle: FontStyle? get() = FontStyle.Italic.takeIf { artistTypography.italic }

    /** [titleTypography]'s extra tracking as a Compose [TextUnit]. */
    val titleLetterSpacing: TextUnit get() = titleTypography.trackingEm.em

    /** [artistTypography]'s extra tracking as a Compose [TextUnit]. */
    val artistLetterSpacing: TextUnit get() = artistTypography.trackingEm.em

    /**
     * Optional family override for the elapsed/total readout.
     *
     * Null deliberately means “retain the face's own family”; this is unlike [clockFont], whose
     * historical default is to follow the track family. A Flex override is instantiated with the
     * readout's own weight/slant and its own width/optical-size/grade/roundness axes.
     */
    val trackTimeFont: FontFamily?
        get() {
            val resolved = WatchTypography.trackTimeFontKey(trackTimeFontKey) ?: return null
            return if (WatchTypography.isFlexFont(resolved)) {
                flexFontFamily(trackTimeTypography, trackTimeFlexAxes)
            } else {
                watchFontFamily(resolved)
            }
        }

    /** A default 400 preserves each face's own designed weight. */
    val trackTimeFontWeight: FontWeight?
        get() = FontWeight(trackTimeTypography.weight).takeUnless {
            trackTimeTypography.weight == 400
        }

    /** A default upright style preserves each face's own designed style. */
    val trackTimeFontStyle: FontStyle?
        get() = FontStyle.Italic.takeIf { trackTimeTypography.italic }

    /** The size controls are relative to the face's authored readout size. */
    fun trackTimeTextSize(designedSize: TextUnit): TextUnit =
            (designedSize.value * trackTimeTypography.scale).sp

    /** Tracking at zero preserves any deliberate letter spacing on the composition. */
    fun trackTimeLetterSpacing(designedSpacing: TextUnit): TextUnit =
            if (trackTimeTypography.trackingEm == 0f) {
                designedSpacing
            } else {
                trackTimeTypography.trackingEm.em
            }

    /** Applies opacity over a face's own colour rather than replacing its intentional alpha. */
    fun trackTimeColor(designedColor: Color): Color =
            designedColor.copy(alpha = designedColor.alpha * trackTimeTypography.alpha)

    /**
     * The [FontFamily] for the awake clock: [clockFontKey] when the user picked one, otherwise
     * [fontKey] as the clock always followed.
     *
     * The clock is chrome, not track text, so it does not change typeface based on what is playing.
     * A Clock-only Flex choice receives its own width/optical-size/grade/roundness set; a "follow"
     * choice keeps the title/artist axes.
     */
    val clockFont: FontFamily
        get() {
            val resolved = WatchTypography.clockFontKey(clockFontKey, fontKey)
            return if (WatchTypography.isFlexFont(resolved)) {
                flexFontFamily(
                        clockTypography,
                        if (clockFontKey == WatchTypography.FLEX_FONT_KEY) {
                            clockFlexAxes
                        } else {
                            flexAxes
                        })
            } else {
                watchFontFamily(resolved)
            }
        }

    /**
     * The [FontFamily] for song lyrics on a face that renders them.
     *
     * The Verse face had `MarcellusFamily` written into every one of its text calls, which made the
     * one thing that face is *about* the one thing about it a user could not change. A face owns
     * its composition - what goes where, at what size - and never its settings; a typeface is a
     * setting.
     *
     * "follow" therefore resolves to [fontKey], the theme's own typeface, exactly as [clockFont]
     * does. It deliberately does **not** resolve back to the serif: keeping that as the default
     * preserved every existing theme byte-for-byte and, in doing so, preserved the complaint - the
     * words of the song stayed put while everything around them followed the font you picked.
     * Marcellus is still in the catalog for anyone who wants the original look.
     *
     * A Lyrics-only Flex choice has its own four visual axes while retaining the title's
     * weight/slant (lyrics have no separate weight or slant control). A "follow" choice keeps the
     * title/artist axes instead.
     */
    val lyricFont: FontFamily
        get() {
            val resolved = WatchTypography.lyricsFontKey(lyricsFontKey, fontKey)
            return if (WatchTypography.isFlexFont(resolved)) {
                flexFontFamily(
                        titleTypography,
                        if (lyricsFontKey == WatchTypography.FLEX_FONT_KEY) {
                            lyricsFlexAxes
                        } else {
                            flexAxes
                        })
            } else {
                watchFontFamily(resolved)
            }
        }
}

/** Events a face raises back to the host. Implemented by MainActivity, which routes them into
 *  the same pipelines the classic face uses (haptics, optimistic state, Data Layer sends). */
interface NowPlayingFaceListener {
    /** Single tap on the central play/pause control. */
    fun onPlayPauseTap()

    /** Double tap on the central control - toggles the quick actions panel, matching the
     *  classic face's center tap zone. */
    fun onCenterDoubleTap()

    /** Long press on the central control - opens the queue when the corresponding preference
     *  is enabled, matching the classic face's center tap zone. */
    fun onCenterLongPress()

    fun onSkipPreviousTap()

    fun onSkipNextTap()

    /** Tap / long press on a mini button the face is hosting, by its
     *  [ScreenButtons][com.svartifoss.snfell.common.ScreenButtons] slot code. The face resolves no
     *  action of its own: this goes back through the same ButtonInfo pipeline the host's own row
     *  uses, so a face-drawn button and a row-drawn one run the identical configured action. */
    fun onMiniButtonTap(slotCode: Int)
    fun onMiniButtonLongPress(slotCode: Int)

    /** Default bottom-trio pill: open the playback queue. */
    fun onQueueTap()

    /** Default bottom-trio pill: show the volume overlay. */
    fun onVolumeTap()

    /** Default bottom-trio pill: open the actions menu. */
    fun onOverflowTap()

    /** Preserve the host's configurable swipe actions when a gesture starts on a face-owned
     * control instead of the shared FourWayTouchLayout below the Compose surface. */
    fun onSwipeUp()
    fun onSwipeDown()
    fun onSwipeLeft()

    /** Commit a seek to [fraction] (0f..1f) of the track. Used by central ring scrubbing and
     *  directly interactive timelines such as Spectrum; rotary seek stays host-side. */
    fun onSeek(fraction: Float)
}
