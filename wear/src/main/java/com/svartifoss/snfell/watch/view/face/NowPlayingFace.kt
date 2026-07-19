package com.svartifoss.snfell.watch.view.face

import androidx.compose.ui.text.font.FontFamily
import com.svartifoss.snfell.common.PlayerBackgroundStyle
import com.svartifoss.snfell.common.PlayerShadingStyle
import com.svartifoss.snfell.watch.theme.GoogleSansFamily
import com.svartifoss.snfell.watch.theme.WatchTheme
import com.svartifoss.snfell.watch.theme.lainFont
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
data class NowPlayingFaceState(
        val title: String = "",
        val artist: String = "",
        /** Independent metadata visibility preferences. Status/error text may force the
         * corresponding line visible in the host before it reaches this state. */
        val showTitle: Boolean = true,
        val showArtist: Boolean = true,
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
        /** Resolved artist text color (already accounts for color mode, desaturation and the
         *  plain-white status-message override - it mirrors the classic artist line exactly). */
        val artistColor: Int = WatchTheme.ACCENT_DEFAULT,
        /** Queue entry immediately after the current item. The host refreshes it when a visual AOD
         *  that exposes Up Next becomes active. Empty values mean that the playing app did not
         *  publish a usable queue; ambient faces render a quiet unavailable state instead of stale
         *  metadata. */
        val upNextTitle: String = "",
        val upNextArtist: String = "",
        /** Optional cover for [upNextTitle]. Queue publishers are not required to expose one. */
        val upNextArtwork: androidx.compose.ui.graphics.ImageBitmap? = null,
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
        /** Whether the interactive curved clock is visible. This is the Compose equivalent of
         *  the Classic face's ALWAYS_SHOW_TIME clock and is independent from the AOD clock. */
        val showClock: Boolean = false,
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
        /** The user's font choice for title/artist text (MiscPreferences.WEAR_FONT key). Resolved
         *  through [watchFontFamily]; the Lain keyword easter egg still takes precedence. */
        val fontKey: String = "google_sans",
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
) {
    /**
     * The [FontFamily] to use for the track title. The Lain keyword easter egg (title or artist
     * containing a trigger word, case-insensitive substring) always wins; otherwise the user's
     * [fontKey] choice applies, defaulting to [GoogleSansFamily] so normal tracks are unaffected.
     */
    val titleFont: FontFamily
        get() = lainFont(title) ?: lainFont(artist) ?: watchFontFamily(fontKey)

    /**
     * The [FontFamily] to use for the artist line — always matched with [titleFont] so both
     * lines flip to the typewriter style together whenever the track qualifies.
     */
    val artistFont: FontFamily
        get() = titleFont
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
