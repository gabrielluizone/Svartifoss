package com.svartifoss.snfell.view.watchface

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import androidx.preference.PreferenceManager
import com.svartifoss.snfell.R
import timber.log.Timber
import com.svartifoss.snfell.view.settings.WatchFontCatalog
import com.svartifoss.snfell.view.watchface.theme.WatchThemeProfile
import com.svartifoss.snfell.view.watchface.theme.WatchThemeValue
import com.svartifoss.snfell.common.ActivityVisibility
import com.svartifoss.snfell.common.AodArtTreatment
import com.svartifoss.snfell.common.AppearanceContext
import com.svartifoss.snfell.common.BitmapBlur
import com.svartifoss.snfell.common.FaceGeometry
import com.svartifoss.snfell.common.FaceScopedPreferences
import com.svartifoss.snfell.common.FrostedEdges
import com.svartifoss.snfell.common.MiniButtonPlacement
import com.svartifoss.snfell.common.MiniButtonSurfaces
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.OverlayBackdrop
import com.svartifoss.snfell.common.OverlayBackdropResolver
import com.svartifoss.snfell.common.PaletteTransforms
import com.svartifoss.snfell.common.PlayerBackgroundStyle
import com.svartifoss.snfell.common.PlayerShadingIntensity
import com.svartifoss.snfell.common.PlayerShadingStyle
import com.svartifoss.snfell.common.SHADING_MAX_MULTIPLIER
import com.svartifoss.snfell.common.SHADING_MAX_PERCENT
import com.svartifoss.snfell.common.QuickPanelButtons
import com.svartifoss.snfell.common.ScreenQuadrant
import com.svartifoss.snfell.common.ScreenTheme as SharedScreenTheme
import com.svartifoss.snfell.common.ScreenThemeTokens
import com.svartifoss.snfell.common.SpecialEliteKeywordPolicy
import android.os.Build
import com.svartifoss.snfell.common.AlbumAccentSource
import com.svartifoss.snfell.common.ColorHarmony
import com.svartifoss.snfell.common.SwatchInfo
import com.svartifoss.snfell.common.selectPrimaryAccent
import com.svartifoss.snfell.common.AccentFloorStyle
import com.svartifoss.snfell.common.SplitPanelStyle
import com.svartifoss.snfell.common.AdaptiveTextContrast
import com.svartifoss.snfell.common.CoverShape
import com.svartifoss.snfell.common.TextCase
import com.svartifoss.snfell.common.RoundScreenText
import com.svartifoss.snfell.common.WatchTypography
import com.svartifoss.snfell.common.ColorModifier
import com.svartifoss.snfell.common.SurfaceColorTreatment
import com.svartifoss.snfell.common.SurfacePaletteResolver
import com.svartifoss.snfell.common.ThemeAppearance
import com.svartifoss.snfell.common.TitleTextMode
import com.svartifoss.snfell.common.TrackMetadataFields
import com.svartifoss.snfell.common.resolveAodArtwork
import com.svartifoss.snfell.common.R as commonR
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Live miniature of the watch's now-playing screen, driving the "Watch face" customization tab.
 * Renders a sample track over procedurally generated album art, honoring the active-player
 * preference keys the watch reads. It switches contextually between the active player, AOD,
 * volume/seek overlays, quick panel, queue and mini-button demonstration so every visual Watch
 * preference has an immediate phone-side representation.
 *
 * Without connected-device data the watch is modeled as a 192dp round screen; [setDeviceProfile]
 * switches to the real round/square shape, aspect ratio and dp extent. Geometry mirrors the real
 * classic View face and Compose faces in `wear/`. Purely decorative - not interactive.
 */
class WatchPreviewView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null
) : View(context, attrs) {

    /**
     * A representative watch surface that can be rendered from the same complete appearance
     * snapshot. Community-theme details use these to show how a look carries beyond the player
     * itself, without taking or uploading a screenshot from the person's real watch.
     */
    enum class PreviewSurface {
        PLAYER,
        AOD,
        VOLUME,
        SEEK,
        QUICK_PANEL,
        QUEUE,
        MINI_BUTTONS
    }

    private companion object {
        /** WatchTheme.ACCENT_DEFAULT on the wear side - the static "neutral" accent. */
        const val ACCENT_NEUTRAL = 0xFF87A89F.toInt()

        /** Fixed "palette-extracted" accent of the generated sample art below. */
        const val SAMPLE_ALBUM_ACCENT = 0xFFD98E76.toInt()
        /** Other colours physically painted by [buildSampleArt], used by multi-colour styles. */
        const val SAMPLE_ALBUM_SECONDARY = 0xFF6E3B33.toInt()
        const val SAMPLE_ALBUM_TERTIARY = 0xFF241B2F.toInt()

        const val WATCH_DP = 192f
        const val SAMPLE_PROGRESS = 0.35f
        const val SAMPLE_VOLUME = 0.65f
        /** A deliberately non-personal time for the Firestore moderation thumbnail. */
        const val MODERATION_PREVIEW_CLOCK = "10:09"

        const val TERMINAL_GREEN = 0xFF33FF66.toInt()
        const val MATERIAL_SURFACE = 0xFF2A2A2A.toInt()
        const val LIGHT_SURFACE = 0xFFECECEC.toInt()
        const val LIGHT_ON = 0xFF111111.toInt()
        const val MONO_SURFACE = 0xFF262626.toInt()
        const val MONO_ACTIVE = 0xFFE0E0E0.toInt()
        /** Flat neutral surface + tight corner of the "slab" quick-panel style. */
        const val SLAB_SURFACE = 0xFF1E1E20.toInt()
        const val SLAB_CORNER_DP = 10f

        // Face geometry lives in FaceGeometry (common), which both this preview and the Wear
        // faces read - these were two copies of the same numbers kept in step by hand until the
        // module boundary stopped being treated as a reason they had to be.
        private val CAROUSEL_CARD_FRACTION = FaceGeometry.Carousel.CARD_FRACTION
        private val CAROUSEL_RAIL_CENTER = FaceGeometry.Carousel.RAIL_CENTER
        private val CAROUSEL_CARD_TOP = FaceGeometry.Carousel.CARD_TOP
        private val CAROUSEL_CARD_BOTTOM = FaceGeometry.Carousel.CARD_BOTTOM
        private val CAROUSEL_ARTIST_ROW = FaceGeometry.Carousel.ARTIST_ROW_FRACTION
        private val CAROUSEL_ARTIST_TOP = FaceGeometry.Carousel.ARTIST_TOP
        private val CAROUSEL_TITLE_TOP = FaceGeometry.Carousel.TITLE_TOP
        private val CAROUSEL_NEAR_SHADE = FaceGeometry.Carousel.NEAR_SHADE
        private val CAROUSEL_FAR_SHADE = FaceGeometry.Carousel.FAR_SHADE
        private val CAROUSEL_SOURCE_ICON_SIZE = FaceGeometry.Carousel.SOURCE_ICON_SIZE_DP

        private val CLASSIC_TITLE_MAX_SP = FaceGeometry.Classic.TITLE_MAX_SP
        private val CLASSIC_TITLE_MIN_SP = FaceGeometry.Classic.TITLE_MIN_SP
        private val CLASSIC_ARTIST_MAX_SP = FaceGeometry.Classic.ARTIST_MAX_SP
        private val CLASSIC_ARTIST_MIN_SP = FaceGeometry.Classic.ARTIST_MIN_SP
        private val CLASSIC_ARTIST_MAX_LINES = FaceGeometry.Classic.ARTIST_MAX_LINES
        private val CLASSIC_SOURCE_ICON_SIZE_ARTIST_FACTOR =
                FaceGeometry.Classic.SOURCE_ICON_SIZE_ARTIST_FACTOR
        private val CLASSIC_SOURCE_ICON_END_MARGIN_ARTIST_FACTOR =
                FaceGeometry.Classic.SOURCE_ICON_END_MARGIN_ARTIST_FACTOR
        private val CLASSIC_CLOCK_SP = FaceGeometry.Classic.CLOCK_SP
        private val CLASSIC_CLOCK_TOP_PADDING_DP = FaceGeometry.Classic.CLOCK_TOP_PADDING_DP
        private val CLASSIC_TRACK_TIME_SP = FaceGeometry.Classic.TRACK_TIME_SP
        private val CLASSIC_ROUND_BOX_INSET_FRACTION =
                FaceGeometry.Classic.ROUND_BOX_INSET_FRACTION
        private val CLASSIC_SQUARE_TEXT_MARGIN_DP = FaceGeometry.Classic.SQUARE_TEXT_MARGIN_DP

        private val IMMERSIVE_BOTTOM_PADDING_FRACTION =
                FaceGeometry.Immersive.BOTTOM_PADDING_FRACTION
        private val IMMERSIVE_SIDE_PADDING_FRACTION =
                FaceGeometry.Immersive.SIDE_PADDING_FRACTION
        private val IMMERSIVE_TITLE_SP = FaceGeometry.Immersive.TITLE_SP
        private val IMMERSIVE_TITLE_LINE_HEIGHT_SP =
                FaceGeometry.Immersive.TITLE_LINE_HEIGHT_SP
        private val IMMERSIVE_ARTIST_TOP_PADDING_DP =
                FaceGeometry.Immersive.ARTIST_TOP_PADDING_DP
        private val IMMERSIVE_ARTIST_SP = FaceGeometry.Immersive.ARTIST_SP
        private val IMMERSIVE_ARTIST_LINE_HEIGHT_SP =
                FaceGeometry.Immersive.ARTIST_LINE_HEIGHT_SP
        private val IMMERSIVE_SOURCE_ICON_SIZE_DP =
                FaceGeometry.Immersive.SOURCE_ICON_SIZE_DP
        private val IMMERSIVE_TRACK_TIME_TOP_PADDING_DP =
                FaceGeometry.Immersive.TRACK_TIME_TOP_PADDING_DP
        private val IMMERSIVE_TRACK_TIME_SP = FaceGeometry.Immersive.TRACK_TIME_SP
        private val IMMERSIVE_TRACK_TIME_LINE_HEIGHT_SP =
                FaceGeometry.Immersive.TRACK_TIME_LINE_HEIGHT_SP

        private val CHAT_INCOMING_LIGHTNESS = FaceGeometry.Chat.INCOMING_LIGHTNESS
        private val CHAT_OUTGOING_LIGHTNESS = FaceGeometry.Chat.OUTGOING_LIGHTNESS
        private val CHAT_WAVE_PATTERN = FaceGeometry.Chat.WAVE_PATTERN
        private val CHAT_SIDE_PADDING_FRACTION = FaceGeometry.Chat.SIDE_PADDING_FRACTION
        private val CHAT_TOP_PADDING_FRACTION = FaceGeometry.Chat.TOP_PADDING_FRACTION
        private val CHAT_BOTTOM_PADDING_FRACTION = FaceGeometry.Chat.BOTTOM_PADDING_FRACTION
        private val CHAT_DAY_TEXT_SP = FaceGeometry.Chat.DAY_CHIP_TEXT_SP
        private val CHAT_DAY_HORIZONTAL_PADDING_DP = FaceGeometry.Chat.DAY_CHIP_HORIZONTAL_PADDING_DP
        private val CHAT_DAY_VERTICAL_PADDING_DP = FaceGeometry.Chat.DAY_CHIP_VERTICAL_PADDING_DP
        private val CHAT_DAY_TO_MESSAGE_GAP_DP = FaceGeometry.Chat.DAY_TO_MESSAGE_GAP_DP
        private val CHAT_TITLE_SP = FaceGeometry.Chat.CURRENT_BUBBLE_TITLE_SP
        private val CHAT_ARTIST_SP = FaceGeometry.Chat.CURRENT_BUBBLE_ARTIST_SP
        private val CHAT_CURRENT_BUBBLE_MAX_WIDTH_DP =
                FaceGeometry.Chat.CURRENT_BUBBLE_MAX_WIDTH_DP
        private val CHAT_BUBBLE_HORIZONTAL_PADDING_DP =
                FaceGeometry.Chat.CURRENT_BUBBLE_HORIZONTAL_PADDING_DP
        private val CHAT_BUBBLE_VERTICAL_PADDING_DP =
                FaceGeometry.Chat.CURRENT_BUBBLE_VERTICAL_PADDING_DP
        private val CHAT_CURRENT_TO_VOICE_GAP_DP = FaceGeometry.Chat.CURRENT_TO_VOICE_GAP_DP
        private val CHAT_BUBBLE_CORNER_DP = FaceGeometry.Chat.BUBBLE_CORNER_DP
        private val CHAT_BUBBLE_TAIL_CORNER_DP = FaceGeometry.Chat.BUBBLE_TAIL_CORNER_DP
        private val CHAT_VOICE_HEIGHT_DP = FaceGeometry.Chat.VOICE_BUBBLE_HEIGHT_DP
        private val CHAT_VOICE_HORIZONTAL_PADDING_DP =
                FaceGeometry.Chat.VOICE_BUBBLE_HORIZONTAL_PADDING_DP
        private val CHAT_AVATAR_SIZE_DP = FaceGeometry.Chat.AVATAR_SIZE_DP
        private val CHAT_AVATAR_TO_WAVE_GAP_DP = FaceGeometry.Chat.AVATAR_TO_WAVE_GAP_DP
        private val CHAT_WAVE_TO_GLYPH_GAP_DP = FaceGeometry.Chat.WAVE_TO_GLYPH_GAP_DP
        private val CHAT_WAVE_HEIGHT_DP = FaceGeometry.Chat.WAVE_HEIGHT_DP
        private val CHAT_WAVE_PLAYHEAD_PULSE_MIN_SCALE =
                FaceGeometry.Chat.WAVE_PLAYHEAD_PULSE_MIN_SCALE
        private val CHAT_WAVE_PLAYHEAD_PULSE_HALF_CYCLE_MS =
                FaceGeometry.Chat.WAVE_PLAYHEAD_PULSE_HALF_CYCLE_MS
        private val CHAT_GLYPH_SIZE_DP = FaceGeometry.Chat.PLAY_GLYPH_SIZE_DP
        private val CHAT_GLYPH_MARK_DP = FaceGeometry.Chat.PLAY_GLYPH_MARK_DP
        private val CHAT_TIME_TOP_PADDING_DP = FaceGeometry.Chat.TIME_TOP_PADDING_DP
        private val CHAT_TIME_END_PADDING_DP = FaceGeometry.Chat.TIME_END_PADDING_DP
        private val CHAT_TIME_TO_TICKS_GAP_DP = FaceGeometry.Chat.TIME_TO_TICKS_GAP_DP
        private val CHAT_TIME_SP = FaceGeometry.Chat.TIME_TEXT_SP
        private val CHAT_TICK_WIDTH_DP = FaceGeometry.Chat.TICK_WIDTH_DP
        private val CHAT_TICK_HEIGHT_DP = FaceGeometry.Chat.TICK_HEIGHT_DP
        private val CHAT_VOICE_TO_ACTION_GAP_DP = FaceGeometry.Chat.VOICE_TO_ACTION_GAP_DP
        private val CHAT_ACTION_DIAMETER_FRACTION = FaceGeometry.Chat.ACTION_DIAMETER_FRACTION
        private val CHAT_ACTION_GAP_FRACTION = FaceGeometry.Chat.ACTION_GAP_FRACTION
        private val CHAT_ACTION_MIN_DIAMETER_DP = FaceGeometry.Chat.ACTION_MIN_DIAMETER_DP
        private val CHAT_ACTION_MIN_DESIGNED_DIAMETER_DP =
                FaceGeometry.Chat.ACTION_MIN_DESIGNED_DIAMETER_DP
        private val CHAT_ACTION_MAX_DESIGNED_DIAMETER_DP =
                FaceGeometry.Chat.ACTION_MAX_DESIGNED_DIAMETER_DP

        private val SPLIT_SEAM_FRACTION = FaceGeometry.Split.SEAM_FRACTION
        /** The panel's artwork alpha as an 8-bit Canvas value; the face states it as a fraction. */
        private val SPLIT_PANEL_ART_ALPHA =
                (FaceGeometry.Split.PANEL_ART_ALPHA * 255f).roundToInt()
        private val SPLIT_PANEL_LIGHTNESS = FaceGeometry.Split.PANEL_LIGHTNESS
        private val SPLIT_BADGE_FRACTION = FaceGeometry.Split.BADGE_FRACTION

        private val NOTE_COVER_FRACTION = FaceGeometry.Note.COVER_FRACTION
        private val NOTE_MAX_LINES = FaceGeometry.Note.MAX_LINES

        private val VERSE_BAND_TOP = FaceGeometry.Verse.BAND_TOP
        private val VERSE_BAND_BOTTOM = FaceGeometry.Verse.BAND_BOTTOM
        private val VERSE_BAND_CENTER = FaceGeometry.Verse.BAND_CENTER
        const val VERSE_PREVIEW_LINE_PROGRESS = 0.55f

        private val METADATA_TABLE_HEIGHT_FRACTION = FaceGeometry.Metadata.TABLE_HEIGHT_FRACTION
        private val METADATA_ROW_HEIGHT_DP = FaceGeometry.Metadata.ROW_HEIGHT_DP
        private val METADATA_MIN_ROWS = FaceGeometry.Metadata.MIN_ROWS
        private val METADATA_MAX_ROWS = FaceGeometry.Metadata.MAX_ROWS

        private val COOKIE_LOBES = FaceGeometry.Expressive.COOKIE_LOBES
        private val COOKIE_SOFTNESS = FaceGeometry.Expressive.COOKIE_SOFTNESS
        private val COOKIE_MODULATION = FaceGeometry.Expressive.COOKIE_MODULATION
        private val RING_MODULATION = FaceGeometry.Expressive.RING_MODULATION
        private val RING_GAP_DEGREES = FaceGeometry.Expressive.RING_GAP_DEGREES
    }

    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val grayscaleFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
    private val iconDst = RectF()

    private val fontRegular: Typeface? = ResourcesCompat.getFont(context, R.font.google_sans_regular)
    private val fontBold: Typeface? = ResourcesCompat.getFont(context, R.font.google_sans_bold)
    private val fontMomsTypewriter: Typeface? = ResourcesCompat.getFont(context, R.font.moms_typewriter)
    private val fontPoppins: Typeface? = ResourcesCompat.getFont(context, R.font.poppins_regular)
    private val fontMontserrat: Typeface? = ResourcesCompat.getFont(context, R.font.montserrat_regular)
    private val fontMarcellus: Typeface? = ResourcesCompat.getFont(context, R.font.marcellus_regular)

    private var sampleArt: Bitmap? = null
    private var sampleArtBlurred: Bitmap? = null
    private var sampleOverlayArtBlurred: Bitmap? = null
    private var sampleAlternateArt: Bitmap? = null
    private var sampleAlternateArtBlurred: Bitmap? = null

    /**
     * An in-memory profile supplied by the online gallery. It deliberately never materializes into
     * the default preference file: gallery cards may render many profiles before the user decides
     * to install one. While present, [readPreferenceSnapshot] reads only this profile's appearance
     * values (falling back to built-in defaults for a malformed/incomplete input).
     */
    private var themeProfile: WatchThemeProfile? = null

    /**
     * The gallery's ordinary cards and moderation renderer must be deterministic and impersonal.
     * A full detail screen may opt into the *locally held* album cover so a person can see how an
     * uninstalled theme reacts to it. This flag never changes the profile, preferences, uploaded
     * review thumbnail, catalogue, or watch sync; title, artist, progress, and queue stay on the
     * bundled sample even when the cover is enabled.
     */
    private var themeProfileUsesLocalArtwork = false

    /**
     * When set, replaces the device clock everywhere it appears in this preview. This exists only
     * for the uploaded moderation thumbnail: its pixels must not reveal the author's local time
     * or 12/24-hour preference.
     */
    private var moderationPreviewMode = false

    // --- Live now-playing data (see setNowPlaying()/setPlayback()) ---
    /** The playing app's mark and whether it is a tintable template - the pair the watch's
     *  `sourceIcon`/`sourceIconTemplate` carry, kept together for the reason `AppMark` does: a
     *  glyph taken as an image and its tintability decided separately is how a flat-white template
     *  ends up invisible on a light surface. */
    private var sourceGlyph: Bitmap? = null
    private var sourceGlyphTintable = true

    private var nowPlayingSource: Bitmap? = null
    private var nowPlayingTitle: String? = null
    private var nowPlayingArtist: String? = null
    private var liveArt: Bitmap? = null
    private var liveArtBlurred: Bitmap? = null
    /** Frosted-rim composition plus the source and radius it was built from - see [frostedPreviewArt]. */
    private var frostedPreviewArt: Bitmap? = null
    private var frostedPreviewSource: Bitmap? = null
    private var frostedPreviewRadius: Int = -1
    private var liveOverlayArtBlurred: Bitmap? = null
    private var liveAccent: Int? = null
    private var liveSecondaryAccent: Int? = null
    private var liveTertiaryAccent: Int? = null
    private var livePlaying: Boolean? = null
    private var livePositionMs: Long = -1
    private var liveDurationMs: Long = -1

    /** Icons from the same playing/stopped action config currently active on the watch. */
    private var miniButtonIcons: List<PreviewActionIcon> = emptyList()
    private var quadrantIcons: Map<Int, PreviewActionIcon> = emptyMap()
    private var quickPanelIcons: Map<Int, PreviewActionIcon> = emptyMap()
    private var demoMiniButtonIcons: List<PreviewActionIcon> = emptyList()

    private var surface = PreviewSurface.PLAYER
    private var focusedPreference: String? = null
    private var candidateKey: String? = null
    private var candidateValue: Any? = null
    private var candidateActive = false

    /** The connected watch profile. A null shape deliberately falls back to the historical
     *  192dp round preview, while a reported square device keeps its real dp aspect ratio. */
    private var deviceRound: Boolean? = null
    private var deviceWidthDp = WATCH_DP
    private var deviceHeightDp = WATCH_DP

    // --- Preference snapshot (see refresh()) ---
    private var face = "classic"
    /** Storage namespace for appearance values. Saved themes render [face] while reading an
     * isolated, fully materialized snapshot so their edits never mutate the built-in layout. */
    private var appearanceScope = "classic"
    private var expressiveSeekMode = "central"
    private var playerControlsVisible = true
    private var showTrackTitle = true
    private var showTrackArtist = true
    private var showSourceIcon = true
    private var internalProgressVisible = true
    private var edgeProgressVisible = true
    private var edgeSeekEnabled = true
    private var screenTheme = "default"
    private var wearFontKey = "google_sans"
    private var fontAllScreens = false
    private var wearTitleFontKey = WatchTypography.TITLE_FONT_FOLLOW
    private var wearArtistFontKey = WatchTypography.ARTIST_FONT_FOLLOW
    private var wearClockFontKey = WatchTypography.CLOCK_FONT_FOLLOW
    private var wearLyricsFontKey = WatchTypography.LYRICS_FONT_FOLLOW
    private var wearTrackTimeFontKey = WatchTypography.TRACK_TIME_FONT_FOLLOW
    private var artStyle = "cover"
    private var accentFloor = AccentFloorStyle.DEFAULT
    private var splitPanel = SplitPanelStyle.DEFAULT
    private var dimArt = true
    private var dimStrength = 80
    private var playerShadingStyle = PlayerShadingStyle.FOLLOW
    private var playerShadingIntensity = PlayerShadingIntensity.BALANCED.multiplier
    private var shadingColorMode = "black"
    private var shadingCustomColor = ""
    private var colorTreatment = "expressive"
    private var normalColor = ""
    private var normalColorMulti = true
    private var colorModifier = "none"
    private var colorHueShift = 0f
    /** Which cover swatch becomes the accent - see MiscPreferences.WEAR_ALBUM_ACCENT_SOURCE. */
    private var albumAccentSource = AlbumAccentSource.BALANCED_VALUE

    /** The accent source [extractLiveAccent] last ran with, so a changed source invalidates the
     *  per-bitmap cache the same way the watch's `lastPaletteAccentSource` does. */
    private var liveAccentSource: String? = null
    /** Per-element typography, mirroring what the watch resolves through WatchTypography. */
    private var titleTypographySpec = WatchTypography.IDENTITY_TEXT
    private var clockTypographySpec = WatchTypography.IDENTITY_TEXT
    private var artistTypographySpec = WatchTypography.IDENTITY_TEXT
    private var trackTimeTypographySpec = WatchTypography.IDENTITY_TEXT
    private var sourceIconTypographySpec = WatchTypography.IDENTITY_ICON
    private var flexAxesSpec = WatchTypography.IDENTITY_FLEX_AXES
    private var titleFlexAxesSpec = WatchTypography.IDENTITY_FLEX_AXES
    private var artistFlexAxesSpec = WatchTypography.IDENTITY_FLEX_AXES
    private var clockFlexAxesSpec = WatchTypography.IDENTITY_FLEX_AXES
    private var lyricsFlexAxesSpec = WatchTypography.IDENTITY_FLEX_AXES
    private var trackTimeFlexAxesSpec = WatchTypography.IDENTITY_FLEX_AXES
    private var titleColorMode = MiscPreferences.TITLE_COLOR_FACE_DEFAULT
    private var titleCustomColor = ""
    private var titleAdaptiveContrast = false
    private var artistMode = "follow"
    private var artistCustom = ""
    private var artistDesaturated = false
    private var artistAdaptiveContrast = false
    private var clockAdaptiveContrast = false
    private var carouselCardShape = "rounded"
    private var noteCoverShape = "circle"
    private var progressGradientEnabled = true
    private var progressMode = "follow"
    private var progressCustom = ""
    private var progressDesaturated = false
    private var volumeColorMode = "follow"
    private var volumeCustomColor = ""
    private var quickPanelColorMode = "follow"
    private var quickPanelCustomColor = ""
    private var progressStyle = "solid"
    private var progressLayout = "edge"
    private var trackTimeMode = "always"
    private var titleTextMode = "smart"
    private var alwaysShowTime = false
    private var clockColorMode = "white"
    private var clockCustomColor = ""
    private var clockOpacity = 60
    private var albumBlurRadius = 35
    private var overlayBlurRadius = 35
    private var overlayBackdropStyle = "follow"
    private var albumArtFade = true

    private var aodStyle = "follow"
    private var aodColorMode = "white"
    private var aodCustomColor = ""
    private var aodIntensity = 100
    private var aodShowTransport = true
    private var aodShowProgress = true
    private var aodShowPills = true
    private var aodShowArt = true
    private var aodArtTreatment = AodArtTreatment.BLUR
    private var ambientArtOpacity = 55
    private var aodShowClock = true
    private var aodShowTrackInfo = true

    private var volumeStyle = "glass"
    private var volumeLayout = "edge"
    private var seekStyle = "plain"
    private var seekLayout = "edge"
    private var quickPanelStyle = "glass"
    private var upNextPillStyle = "follow"
    private var showUpNextPill = false
    private var quickPanelLayout = "stacked"
    private var queueStyle = "glass"
    private var listRowSize = "normal"

    private var quickPanelSource = "manual"
    private var quickPanelShortcutCover = false
    private var previewVolumeArcStart = 130f
    private var previewVolumeArcSweep = 100f
    /** Set while the current frame drew a scrolling title - schedules the next frame. */
    private var marqueeActive = false
    private var transientAnimationActive = false
    private var buttonsCurveStyle = "flat"
    private var buttonsBgStyle = "glass"
    private var buttonsShape = "pill"
    private var buttonsOpacity = 100
    private var miniButtonsMode = ActivityVisibility.ALWAYS


    /** Preview-side mirror of the Wear ScreenTheme tokens. Themes layer on top of the user's
     * own dim/color choices; they never change actions, progress tint or mini-button setup. */
    // The control-style theme only carries icon alpha/scale (see common ScreenTheme); the preview
    // reads those two directly. The richer per-theme background/scrim/outline spec that used to
    // live here was never populated - it produced no visual difference - so it was removed.
    private fun screenThemeSpec(): ScreenThemeTokens =
            SharedScreenTheme.fromPreference(screenTheme).tokens

    /** Base typeface for title or artist, or null for Google Sans (the preloaded
     *  fontBold/fontRegular already cover that case). */
    private fun trackTextFontBase(title: Boolean): Typeface? {
        // Shared with the font picker's per-row rendering, so a font can never look one way in the
        // list and another in the preview.
        return WatchFontCatalog.typefaceFor(context, trackTextFontKey(title))
    }

    private fun trackTextFontKey(title: Boolean): String? = if (title) {
        WatchTypography.titleFontKey(wearTitleFontKey, wearFontKey)
    } else {
        WatchTypography.artistFontKey(wearArtistFontKey, wearFontKey)
    }

    /**
     * Title/artist typeface for the current font choice, matching the bold/regular Google Sans
     * variants this class already preloads. Use at every title/artist draw site instead of the
     * raw fontBold/fontRegular fields; leave chrome text (readouts, labels) on Google Sans.
     *
     * [title] chooses the user's Title or Artist family independently of [bold], which preserves
     * the visual emphasis authored by each face. It also applies that element's letter spacing to
     * [textPaint], since callers configure the shared paint through this function anyway; size and
     * opacity are applied by the callers that own those values.
     */
    private fun trackTextTypeface(title: Boolean, bold: Boolean): Typeface? {
        val spec = if (title) titleTypographySpec else artistTypographySpec
        textPaint.letterSpacing = spec.trackingEm
        if (SpecialEliteKeywordPolicy.matches(
                displayTitle(),
                if (isPlayingShown()) displayArtist() else "")) {
            val specialElite = WatchFontCatalog.typefaceFor(context, "love_letter")
            return styledPreviewTypeface(specialElite, bold, spec)
        }
        if (WatchTypography.isFlexFont(trackTextFontKey(title))) {
            val axes = when {
                title && wearTitleFontKey == WatchTypography.FLEX_FONT_KEY -> titleFlexAxesSpec
                !title && wearArtistFontKey == WatchTypography.FLEX_FONT_KEY -> artistFlexAxesSpec
                else -> flexAxesSpec
            }
            return flexPreviewTypeface(spec, axes)
        }
        val base = trackTextFontBase(title) ?: return styledPreviewTypeface(
                if (bold) fontBold else fontRegular, bold, spec)
        return styledPreviewTypeface(base, bold, spec)
    }

    private fun titleTypeface(bold: Boolean): Typeface? = trackTextTypeface(title = true, bold = bold)

    private fun artistTypeface(bold: Boolean): Typeface? = trackTextTypeface(title = false, bold = bold)

    /**
     * Draws a track-artist line the way the watch's `ArtistLineText` does, so the Text tab's artist
     * controls reach the miniature from one place instead of each face remembering to apply them.
     *
     * The merge is the same "a default means keep what this face designed" rule the title side
     * follows: [designedSize] and [designedTracking] are what the face composed with and survive
     * until the user moves that control off its identity value. Weight and slant ride on
     * [artistTypeface]; opacity is folded in here through [artistAlpha], so callers pass the colour
     * the face designed rather than an already-dimmed one.
     */
    private fun drawArtistLine(
            canvas: Canvas,
            text: String,
            x: Float,
            baselineY: Float,
            availWidth: Float,
            color: Int,
            designedSize: Float,
            designedTracking: Float = 0f,
            bold: Boolean = false,
            align: Paint.Align = Paint.Align.CENTER,
            /** Whether this face puts the playing app's mark before the line, as the watch does by
             *  placing SourceIconGlyph in the same Row. Off by default: the Metadata face's
             *  identity line and Verse's running head deliberately carry no glyph. */
            sourceGlyph: Boolean = false,
            /** The glyph's designed diameter, mirroring the size that face passes to
             *  SourceIconGlyph. Its own scale control is applied on top. */
            glyphSize: Float = designedSize * 1.25f,
            /** SourceIconGlyph's normal trailing spacer is .33 of the mark. Classic has a
             *  historical View margin of .28 of the artist text size, so it passes its own ratio. */
            glyphGapFraction: Float = .33f
    ) {
        val text = artistTypographySpec.case.apply(text)
        textPaint.typeface = artistTypeface(bold = bold)
        textPaint.textSize = artistTypographySpec.scaled(designedSize)
        textPaint.color = artistAlpha(color)
        textPaint.textAlign = align
        textPaint.letterSpacing =
                if (artistTypographySpec.trackingEm == 0f) designedTracking
                else artistTypographySpec.trackingEm

        val glyph = if (sourceGlyph && showSourceIcon) this.sourceGlyph else null
        if (glyph == null) {
            val label = ellipsize(text, availWidth)
            canvas.drawText(label, x, baselineY, textPaint)
            textPaint.letterSpacing = 0f
            return
        }
        // Glyph and line are one centred unit, the way the watch's Row lays them out - the glyph
        // emits its own trailing spacer there, so the gap keeps its proportion to the mark rather
        // than being a fixed inset that reads as misaligned once the mark is resized.
        val diameter = glyphSize * sourceIconTypographySpec.scale
        val gap = diameter * glyphGapFraction
        val label = ellipsize(text, (availWidth - diameter - gap).coerceAtLeast(1f))
        val textWidth = textPaint.measureText(label)
        val unit = diameter + gap + textWidth
        val left = when (align) {
            Paint.Align.LEFT -> x
            Paint.Align.RIGHT -> x - unit
            else -> x - unit / 2f
        }
        // `ArtistLineText` owns the artist typography opacity; SourceIconGlyph has a separate
        // opacity control and only inherits the face's designed tint. Passing textPaint.color
        // here multiplied the artist opacity into the icon too, unlike the watch's sibling Image.
        drawSourceGlyph(canvas, left + diameter / 2f, baselineY - textPaint.textSize * .34f,
                diameter, color)
        val previousAlign = textPaint.textAlign
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(label, left + diameter + gap, baselineY, textPaint)
        textPaint.textAlign = previousAlign
        textPaint.letterSpacing = 0f
    }

    /**
     * The playing app's mark, mirroring `FaceChrome.SourceIconGlyph`.
     *
     * A notification small icon is a flat white template, so it takes [tint] - the face's designed
     * colour, before the artist line's independent opacity - which is what makes it read as part
     * of that line instead of a foreign white blob. A launcher-icon fallback is real artwork and
     * is left untinted; both kinds then receive only the source-icon opacity. The rounded clip
     * matches the watch's .27 corner.
     */
    private fun drawSourceGlyph(
            canvas: Canvas,
            centerX: Float,
            centerY: Float,
            diameter: Float,
            tint: Int,
            /** .27 is the corner every face's inline mark uses. Split passes its own: a template
             *  there is left unclipped and only the launcher-icon fallback is rounded to a circle. */
            cornerFraction: Float = .27f
    ) {
        val glyph = sourceGlyph ?: return
        if (glyph.isRecycled || diameter <= 0f) return
        val half = diameter / 2f
        val rect = RectF(centerX - half, centerY - half, centerX + half, centerY + half)
        val corner = diameter * cornerFraction
        val saved = canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(rect, corner, corner, Path.Direction.CW) })
        val alpha = (Color.alpha(tint) * sourceIconTypographySpec.alpha)
                .roundToInt().coerceIn(0, 255)
        if (sourceGlyphTintable) {
            bitmapPaint.colorFilter = PorterDuffColorFilter(
                    ColorUtils.setAlphaComponent(tint, 255), PorterDuff.Mode.SRC_IN)
        }
        bitmapPaint.alpha = alpha
        // SourceIconGlyph uses ContentScale.Fit on the watch. A notification mark can be
        // rectangular, so Crop (max) loses its edge pixels before this rounded clip runs.
        val scale = min(diameter / glyph.width, diameter / glyph.height)
        canvas.drawBitmap(glyph, Matrix().apply {
            setScale(scale, scale)
            postTranslate(
                    rect.centerX() - glyph.width * scale / 2f,
                    rect.centerY() - glyph.height * scale / 2f)
        }, bitmapPaint)
        bitmapPaint.colorFilter = null
        bitmapPaint.alpha = 255
        canvas.restoreToCount(saved)
    }

    /**
     * Typeface for non-player watch surfaces. The real Wear UI supplies the selected family to
     * menus and Queue (and applies it to the View-based Quick actions panel) only when
     * `wear_font_all_screens` is enabled. Keeping this separate from [titleTypeface] avoids
     * leaking title/artist weight, slant and tracking into list chrome.
     */
    private fun watchUiTypeface(bold: Boolean): Typeface? {
        if (!fontAllScreens) return if (bold) fontBold else fontRegular
        val base = WatchFontCatalog.previewTypefaceFor(context, wearFontKey)
        return Typeface.create(base, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    /** Cached copy of the bundled Flex font, extracted once per process - mirrors the watch's
     *  identically-named private helper in WatchTheme.kt (mobile cannot depend on wear). */
    private var cachedFlexFontFile: java.io.File? = null

    private fun flexFontFile(): java.io.File {
        cachedFlexFontFile?.takeIf { it.length() > 0L }?.let { return it }
        val target = java.io.File(context.cacheDir, "google_sans_flex_variable.ttf")
        if (!target.exists() || target.length() == 0L) {
            context.resources.openRawResource(R.font.google_sans_flex).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
        cachedFlexFontFile = target
        return target
    }

    /** [titleTypeface]'s Google Sans Flex path: the same `Typeface.Builder(File)
     *  .setFontVariationSettings(String)` the watch's `flexTypeface` uses, built from the same
     *  [WatchTypography.flexVariationSettings] string, so the preview cannot show an axis
     *  combination the watch renders differently. */
    private fun flexPreviewTypeface(
            spec: WatchTypography.TextSpec,
            axes: WatchTypography.FlexAxes = flexAxesSpec
    ): Typeface? {
        val settings = WatchTypography.flexVariationSettings(spec, axes)
        return try {
            Typeface.Builder(flexFontFile())
                    .setFontVariationSettings(settings)
                    .build()
        } catch (e: Exception) {
            Timber.w(e, "Flex variation settings rejected in preview: %s", settings)
            ResourcesCompat.getFont(context, R.font.google_sans_flex)
        }
    }

    /** Mirrors the watch's `styledClassicTypeface`: the identity weight (400) keeps the preview's
     *  designed bold/regular split, and any other weight uses the numeric API where available. */
    private fun styledPreviewTypeface(
            base: Typeface?,
            bold: Boolean,
            spec: WatchTypography.TextSpec
    ): Typeface? {
        if (base == null) return null
        if (spec.weight == 400) {
            val style = when {
                bold && spec.italic -> Typeface.BOLD_ITALIC
                bold -> Typeface.BOLD
                spec.italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            return Typeface.create(base, style)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Typeface.create(base, spec.weight, spec.italic)
        }
        val style = when {
            spec.weight >= 600 && spec.italic -> Typeface.BOLD_ITALIC
            spec.weight >= 600 -> Typeface.BOLD
            spec.italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        return Typeface.create(base, style)
    }

    /** [color] with the artist line's configured opacity applied. */
    private fun artistAlpha(color: Int): Int = Color.argb(
            artistTypographySpec.applyAlpha(Color.alpha(color)),
            Color.red(color), Color.green(color), Color.blue(color))

    /**
     * [color] with the track title's configured opacity applied, and its hue swapped for the user's
     * chosen title colour when there is one.
     *
     * Every title draw in the preview already funnels through here, so this is the one place the
     * substitution has to happen - and it keeps the *incoming* alpha, exactly as the watch's
     * FaceChrome.titleTextColor does, so a face that draws its title at .88 stays at .88.
     */
    private fun titleAlpha(color: Int): Int {
        val chosen = resolvedTitleColor()
        val base = if (chosen == null) {
            color
        } else {
            Color.argb(Color.alpha(color), Color.red(chosen), Color.green(chosen), Color.blue(chosen))
        }
        return Color.argb(
                titleTypographySpec.applyAlpha(Color.alpha(base)),
                Color.red(base), Color.green(base), Color.blue(base))
    }

    init {
        refresh()
    }

    /**
     * Previews a complete theme profile without applying it or writing any default preferences.
     *
     * This is the gallery entry point: it isolates the supplied settings from the user's active
     * appearance. [useLocalArtwork] is intentionally opt-in and affects only the in-memory cover
     * bitmap already supplied through [setNowPlaying]; it never exposes current title, artist,
     * timing, or queue data. [clearThemeProfile] restores the normal default-preference and
     * live-media preview.
     */
    fun setThemeProfile(profile: WatchThemeProfile, useLocalArtwork: Boolean = false) {
        themeProfile = profile
        themeProfileUsesLocalArtwork = useLocalArtwork
        candidateActive = false
        candidateKey = null
        candidateValue = null
        focusedPreference = null
        surface = PreviewSurface.PLAYER
        readPreferenceSnapshot()
    }

    /**
     * Renders [surface] from the current profile or preference snapshot.
     *
     * This deliberately keeps [themeProfile] intact: a detail screen can switch between the
     * player, overlays and queue while looking at exactly the same uninstalled community theme.
     */
    fun showPreviewSurface(surface: PreviewSurface) {
        candidateActive = false
        candidateKey = null
        candidateValue = null
        focusedPreference = null
        this.surface = surface
        readPreferenceSnapshot()
    }

    /** Enables the non-personal, fixed clock and animation phase used in a moderation thumbnail. */
    fun setModerationPreviewMode(enabled: Boolean) {
        moderationPreviewMode = enabled
        invalidate()
    }

    /** Clears a profile supplied through [setThemeProfile] and returns to the active preferences. */
    fun clearThemeProfile() {
        themeProfile = null
        themeProfileUsesLocalArtwork = false
        refresh()
    }

    /** Selects the representative surface for a whole Watch tab section. */
    fun showSection(section: String) {
        candidateActive = false
        candidateKey = null
        candidateValue = null
        focusedPreference = null
        surface = when (section) {
            "aod" -> PreviewSurface.AOD
            "panels" -> PreviewSurface.VOLUME
            "miniButtons" -> PreviewSurface.MINI_BUTTONS
            else -> PreviewSurface.PLAYER
        }
        readPreferenceSnapshot()
    }

    /** Shows the watch surface affected by [key]. [candidateValue] is a transient, non-persisted
     *  value used by open list/color/numeric dialogs, so the preview can update before Apply. */
    fun showPreference(key: String, candidateValue: Any? = null) {
        focusedPreference = key
        candidateKey = key
        this.candidateValue = candidateValue
        candidateActive = candidateValue != null
        surface = surfaceForPreference(key)
        readPreferenceSnapshot()
    }

    /** Re-reads all watched preferences and redraws. Any dialog candidate is cleared because a
     *  refresh means the persisted source of truth has changed (or the view was resumed). */
    @JvmOverloads
    fun refresh(changedKey: String? = null) {
        candidateActive = false
        candidateKey = null
        candidateValue = null
        if (changedKey != null) {
            focusedPreference = changedKey
            surface = surfaceForPreference(changedKey)
        }
        readPreferenceSnapshot()
    }

    /** Applies the real connected-watch geometry reported by WatchInfo. Invalid/incomplete
     *  dimensions retain the 192dp fallback instead of producing a zero-sized device. */
    @JvmOverloads
    fun setDeviceProfile(
            round: Boolean?,
            displayWidthPx: Int = 0,
            displayHeightPx: Int = 0,
            density: Float = 1f
    ) {
        deviceRound = round
        if (displayWidthPx > 0 && displayHeightPx > 0 && density > 0f) {
            deviceWidthDp = (displayWidthPx / density).coerceAtLeast(80f)
            deviceHeightDp = (displayHeightPx / density).coerceAtLeast(80f)
        } else {
            deviceWidthDp = WATCH_DP
            deviceHeightDp = WATCH_DP
        }
        invalidate()
    }

    private fun surfaceForPreference(key: String): PreviewSurface = when {
        key.startsWith("wear_aod_") || key == "ambient_album_art_opacity" ->
            PreviewSurface.AOD
        // The awake clock lives on the player surface; editing its prefs previews the player.
        key.startsWith("wear_clock_") -> PreviewSurface.PLAYER
        key == "wear_volume_style" || key == "wear_volume_layout" ||
                key == "wear_volume_color_mode" || key == "wear_volume_custom_color" ->
            PreviewSurface.VOLUME
        key == "wear_progress_style" || key == "wear_progress_layout" ->
            PreviewSurface.PLAYER
        key == "wear_seek_style" || key == "wear_seek_layout" -> PreviewSurface.SEEK
        key == "wear_quick_panel_style" || key == "wear_quick_panel_layout" ||
                key == "wear_quick_panel_source" || key == "wear_quick_panel_color_mode" ||
                key == "wear_quick_panel_custom_color" || key == "wear_up_next_pill_style" ||
                key == "wear_quick_panel_shortcut_cover" ->
            PreviewSurface.QUICK_PANEL
        // queue_remote_artwork is declared on both settings screens and is not per-face, but on
        // the Watch tab it sits in the queue category beside the two rows above - editing it there
        // previewed the player, which is the one surface it has nothing to do with.
        key == "wear_queue_style" || key == "wear_list_row_size" ||
                key == "queue_remote_artwork" -> PreviewSurface.QUEUE
        key == "wear_font_all_screens" -> PreviewSurface.QUEUE
        key == "wear_overlay_backdrop_style" -> PreviewSurface.VOLUME
        key == "overlay_blur_radius" -> PreviewSurface.VOLUME
        key.startsWith("screen_buttons_") || key == "wear_mini_buttons_mode" ->
            PreviewSurface.MINI_BUTTONS
        key == "wear_show_up_next_pill" -> PreviewSurface.PLAYER
        else -> PreviewSurface.PLAYER
    }

    private fun readPreferenceSnapshot() {
        val previousAlbumBlur = albumBlurRadius
        val previousOverlayBlur = overlayBlurRadius

        val suppliedProfile = themeProfile
        if (suppliedProfile != null) {
            face = ThemeAppearance.normalizeBaseFace(suppliedProfile.baseFace)
            appearanceScope = ThemeAppearance.CUSTOM_SCOPE
        } else {
            val persistedContext = ThemeAppearance.resolve(prefs)
            face = ThemeAppearance.normalizeBaseFace(
                    candidateFor("wear_screen_face")?.toString() ?: persistedContext.baseFace)
            appearanceScope = if (FaceScopedPreferences.scopeFor(persistedContext) ==
                    ThemeAppearance.CUSTOM_SCOPE) {
                ThemeAppearance.CUSTOM_SCOPE
            } else {
                // While the layout dialog is open, its candidate should preview the candidate's own
                // built-in namespace before Android commits wear_screen_face.
                face
            }
        }
        expressiveSeekMode = readString("wear_expressive_seek_mode", "central")
        playerControlsVisible = readBoolean("wear_classic_icons_visible", true)
        showTrackTitle = readBoolean("wear_show_track_title", true)
        showTrackArtist = readBoolean("wear_show_track_artist", true)
        showSourceIcon = readBoolean("wear_show_source_icon", true)
        internalProgressVisible = readBoolean("wear_internal_progress_visible", true)
        edgeProgressVisible = readBoolean("wear_edge_progress_visible", true)
        edgeSeekEnabled = readBoolean("wear_edge_seek_enabled", true)
        screenTheme = readString("wear_screen_theme", "default")
        wearFontKey = readString("wear_font", "google_sans")
        fontAllScreens = readBoolean("wear_font_all_screens", false)
        wearTitleFontKey = readString("wear_title_font", WatchTypography.TITLE_FONT_FOLLOW)
        wearArtistFontKey = readString("wear_artist_font", WatchTypography.ARTIST_FONT_FOLLOW)
        wearClockFontKey = readString("wear_clock_font", WatchTypography.CLOCK_FONT_FOLLOW)
        wearLyricsFontKey = readString("wear_lyrics_font", WatchTypography.LYRICS_FONT_FOLLOW)
        wearTrackTimeFontKey = readString(
                "wear_track_time_font", WatchTypography.TRACK_TIME_FONT_FOLLOW)
        artStyle = readString("album_art_style", "cover")
        accentFloor = AccentFloorStyle.fromPreference(
                readString("wear_accent_floor", AccentFloorStyle.DEFAULT.preferenceValue))
        splitPanel = SplitPanelStyle.fromPref(
                readString("wear_split_panel", SplitPanelStyle.DEFAULT.preferenceValue))
        albumBlurRadius = readInt("album_art_blur_radius", 35).coerceIn(5, 120)
        dimArt = readBoolean("dim_album_art", true)
        dimStrength = readInt("album_art_dim_strength", 80).coerceIn(0, SHADING_MAX_PERCENT)
        playerShadingStyle = PlayerShadingStyle.fromPreference(
                readString("wear_player_shading_style", "follow"))
        // Mirror the watch: numeric percentage is the live source; a legacy named level migrates
        // in only when the user never touched the numeric slider (see resolveShadingMultiplier).
        val hasNumericShading = candidateFor("album_art_dim_strength") != null ||
                if (suppliedProfile != null) {
                    suppliedProfile.settings["album_art_dim_strength"] is WatchThemeValue.Number
                } else {
                    prefs.contains(effectiveKey("album_art_dim_strength"))
                }
        val hasNamedShading = candidateFor("wear_player_shading_intensity") != null ||
                if (suppliedProfile != null) {
                    suppliedProfile.settings["wear_player_shading_intensity"] is WatchThemeValue.Text
                } else {
                    prefs.contains(effectiveKey("wear_player_shading_intensity"))
                }
        val shadingPercent = if (!hasNumericShading && hasNamedShading) {
            PlayerShadingIntensity.percentFor(readString("wear_player_shading_intensity", "balanced"))
        } else {
            dimStrength
        }
        playerShadingIntensity = shadingPercent.coerceIn(0, SHADING_MAX_PERCENT) / 100f
        shadingColorMode = readString("wear_shading_color_mode", "black")
        shadingCustomColor = readString("wear_shading_custom_color", "")
        albumArtFade = readBoolean("wear_album_art_fade", true)
        overlayBlurRadius = readInt("overlay_blur_radius", 35).coerceIn(5, 120)
        overlayBackdropStyle = readString("wear_overlay_backdrop_style", "follow")
        colorTreatment = readColorTreatment()
        normalColor = readString("wear_normal_color", "")
        normalColorMulti = readBoolean("wear_normal_color_multi", true)
        colorModifier = readString("wear_color_modifier", "none")
        colorHueShift = readInt("wear_color_hue_shift", 0).toFloat()
        albumAccentSource = readString(
                "wear_album_accent_source", AlbumAccentSource.BALANCED_VALUE)

        // Read through the preview's own candidate-aware readers rather than WatchTypography's
        // SharedPreferences accessors, so an option being previewed live (before Android commits
        // it) shows up here the way every other appearance preference does.
        titleTypographySpec = WatchTypography.TextSpec(
                weight = WatchTypography.normalizeWeight(readInt("wear_title_font_weight", 400)),
                italic = readBoolean("wear_title_font_italic", false),
                scale = WatchTypography.normalizeScale(readInt("wear_title_font_scale", 100)),
                alpha = WatchTypography.normalizeOpacity(readInt("wear_title_font_opacity", 100)),
                trackingEm = WatchTypography.normalizeTracking(readInt("wear_title_font_tracking", 0)),
                case = TextCase.fromPreference(readString("wear_title_text_case", "normal")))
        artistTypographySpec = WatchTypography.TextSpec(
                weight = WatchTypography.normalizeWeight(readInt("wear_artist_font_weight", 400)),
                italic = readBoolean("wear_artist_font_italic", false),
                scale = WatchTypography.normalizeScale(readInt("wear_artist_font_scale", 100)),
                alpha = WatchTypography.normalizeOpacity(readInt("wear_artist_font_opacity", 100)),
                trackingEm = WatchTypography.normalizeTracking(readInt("wear_artist_font_tracking", 0)),
                case = TextCase.fromPreference(readString("wear_artist_text_case", "normal")))
        trackTimeTypographySpec = WatchTypography.TextSpec(
                weight = WatchTypography.normalizeWeight(readInt("wear_track_time_font_weight", 400)),
                italic = readBoolean("wear_track_time_font_italic", false),
                scale = WatchTypography.normalizeScale(readInt("wear_track_time_font_scale", 100)),
                alpha = WatchTypography.normalizeOpacity(readInt("wear_track_time_font_opacity", 100)),
                trackingEm = WatchTypography.normalizeTracking(
                        readInt("wear_track_time_font_tracking", 0)))
        sourceIconTypographySpec = WatchTypography.IconSpec(
                scale = WatchTypography.normalizeScale(readInt("wear_source_icon_scale", 100)),
                alpha = WatchTypography.normalizeOpacity(readInt("wear_source_icon_opacity", 100)))
        // Alpha stays at identity: the clock's opacity lives in wear_clock_opacity and is already
        // baked into resolveClockColor, exactly as WatchTypography.clockSpec does on the watch.
        clockTypographySpec = WatchTypography.TextSpec(
                weight = WatchTypography.normalizeWeight(readInt("wear_clock_font_weight", 400)),
                italic = readBoolean("wear_clock_font_italic", false),
                scale = WatchTypography.normalizeScale(readInt("wear_clock_font_scale", 100)),
                alpha = 1f,
                trackingEm = WatchTypography.normalizeTracking(readInt("wear_clock_font_tracking", 0)))
        fun readFlexAxes(prefix: String) = WatchTypography.FlexAxes(
                width = readInt("${prefix}_width", 100)
                        .toFloat().coerceIn(WatchTypography.FLEX_WIDTH_MIN, WatchTypography.FLEX_WIDTH_MAX),
                opticalSize = readInt("${prefix}_optical_size", 18)
                        .toFloat().coerceIn(
                                WatchTypography.FLEX_OPTICAL_SIZE_MIN, WatchTypography.FLEX_OPTICAL_SIZE_MAX),
                grade = readInt("${prefix}_grade", 0)
                        .toFloat().coerceIn(WatchTypography.FLEX_GRADE_MIN, WatchTypography.FLEX_GRADE_MAX),
                roundness = readInt("${prefix}_roundness", 0)
                        .toFloat().coerceIn(
                                WatchTypography.FLEX_ROUNDNESS_MIN, WatchTypography.FLEX_ROUNDNESS_MAX))
        flexAxesSpec = readFlexAxes("wear_font_flex")
        titleFlexAxesSpec = readFlexAxes("wear_title_font_flex")
        artistFlexAxesSpec = readFlexAxes("wear_artist_font_flex")
        clockFlexAxesSpec = readFlexAxes("wear_clock_font_flex")
        lyricsFlexAxesSpec = readFlexAxes("wear_lyrics_font_flex")
        trackTimeFlexAxesSpec = readFlexAxes("wear_track_time_font_flex")
        titleColorMode = readString(
                "wear_title_color_mode", MiscPreferences.TITLE_COLOR_FACE_DEFAULT)
        titleCustomColor = readString("wear_title_custom_color", "")
        titleAdaptiveContrast = readBoolean("wear_title_adaptive_contrast", false)
        artistMode = readString("wear_artist_color_mode", "follow")
        artistCustom = readString("wear_artist_custom_color", "")
        artistDesaturated = readBoolean("wear_artist_desaturated", false)
        artistAdaptiveContrast = readBoolean("wear_artist_adaptive_contrast", false)
        clockAdaptiveContrast = readBoolean("wear_clock_adaptive_contrast", false)
        carouselCardShape = readString("wear_carousel_card_shape", "rounded")
        noteCoverShape = readString("wear_note_cover_shape", "circle")
        progressGradientEnabled = readBoolean("wear_progress_gradient", true)
        progressMode = readString("wear_progress_color_mode", "follow")
        progressCustom = readString("wear_progress_custom_color", "")
        progressDesaturated = readBoolean("wear_progress_desaturated", false)
        volumeColorMode = readString("wear_volume_color_mode", "follow")
        volumeCustomColor = readString("wear_volume_custom_color", "")
        quickPanelColorMode = readString("wear_quick_panel_color_mode", "follow")
        quickPanelCustomColor = readString("wear_quick_panel_custom_color", "")
        progressStyle = readString("wear_progress_style", "solid")
        progressLayout = readString("wear_progress_layout", "edge")
        trackTimeMode = readString("wear_track_time_mode", "always")
        titleTextMode = readString("wear_title_text_mode", "smart")
        alwaysShowTime = readBoolean("always_show_time", false)
        clockColorMode = readString("wear_clock_color_mode", "white")
        clockCustomColor = readString("wear_clock_custom_color", "")
        clockOpacity = readInt("wear_clock_opacity", 60).coerceIn(10, 100)

        aodStyle = readString("wear_aod_style", "follow")
        aodColorMode = readString("wear_aod_color_mode", "white")
        aodCustomColor = readString("wear_aod_custom_color", "")
        aodIntensity = readInt("wear_aod_intensity", 100).coerceIn(20, 100)
        aodShowTransport = readBoolean("wear_aod_show_transport", true)
        aodShowProgress = readBoolean("wear_aod_show_progress", true)
        aodShowPills = readBoolean("wear_aod_show_pills", true)
        aodShowArt = readBoolean("wear_aod_show_art", true)
        aodArtTreatment = AodArtTreatment.fromPreference(
                readString("wear_aod_art_treatment", AodArtTreatment.BLUR.preferenceValue))
        ambientArtOpacity = readInt("ambient_album_art_opacity", 55).coerceIn(20, 100)
        aodShowClock = readBoolean("wear_aod_show_clock", true)
        aodShowTrackInfo = readBoolean("wear_aod_show_track_info", true)

        volumeStyle = readString("wear_volume_style", "glass")
        volumeLayout = readString("wear_volume_layout", "edge")
        seekStyle = readString("wear_seek_style", "plain")
        seekLayout = readString("wear_seek_layout", "edge")
        quickPanelSource = readString("wear_quick_panel_source", "manual")
        quickPanelShortcutCover = readBoolean("wear_quick_panel_shortcut_cover", false)
        quickPanelStyle = readString("wear_quick_panel_style", "glass")
        upNextPillStyle = readString("wear_up_next_pill_style", "follow")
        showUpNextPill = readBoolean("wear_show_up_next_pill", false)
        quickPanelLayout = readString("wear_quick_panel_layout", "stacked")
        queueStyle = readString("wear_queue_style", "glass")
        listRowSize = readString("wear_list_row_size", "normal")

        buttonsCurveStyle = readString("screen_buttons_curve_style", "flat")
        buttonsBgStyle = readString("screen_buttons_bg_style", "glass")
        buttonsShape = readString("screen_buttons_shape", "pill")
        buttonsOpacity = readInt("screen_buttons_opacity", 100).coerceIn(0, 100)
        miniButtonsMode = readString("wear_mini_buttons_mode", ActivityVisibility.ALWAYS)


        if (albumBlurRadius != previousAlbumBlur || overlayBlurRadius != previousOverlayBlur) {
            rebuildBlurCaches()
        }
        // The live accent is extracted once per bitmap, so a changed accent *source* would
        // otherwise not show until the track changed - the preview would keep reporting the colour
        // the previous source picked, which is exactly the setting the user is watching.
        if (!usesSyntheticGalleryMedia() && albumAccentSource != liveAccentSource) {
            extractLiveAccent(liveArt ?: nowPlayingSource)
        }
        invalidate()
    }

    /**
     * Feeds the preview the phone's actual current track (album art, title, artist from the
     * active media session) so it mirrors what the watch is really showing right now. Any null
     * piece falls back to the built-in sample. Passing the same art instance again is cheap -
     * scaling and palette extraction only rerun when the bitmap changes.
     */
    /**
     * The mark of the app currently playing, as the watch's faces draw it beside the artist line.
     *
     * [tintable] must describe [glyph] rather than being assumed: a notification small icon is a
     * flat white template and has to take the artist colour, while a launcher-icon fallback is real
     * artwork and must be left alone. Null clears it, which is also what a face shows before the
     * phone has learned that app's glyph - the same state the watch is in.
     */
    fun setSourceGlyph(glyph: Bitmap?, tintable: Boolean) {
        if (glyph === sourceGlyph && tintable == sourceGlyphTintable) return
        sourceGlyph = glyph
        sourceGlyphTintable = tintable
        invalidate()
    }

    fun setNowPlaying(art: Bitmap?, title: String?, artist: String?) {
        nowPlayingTitle = title?.takeIf { it.isNotBlank() }
        nowPlayingArtist = artist?.takeIf { it.isNotBlank() }
        if (art !== nowPlayingSource) {
            nowPlayingSource = art
            rebuildLiveArt()
            // Prefer the already center-cropped/small liveArt when the view has been measured;
            // falls back to the raw art otherwise (e.g. the very first call, before layout).
            extractLiveAccent(liveArt ?: art)
        }
        invalidate()
    }

    /**
     * Live playback state driving the dynamic parts of the preview - the progress ring, the
     * track time line and the play/pause presentation. The owning fragment ticks this every
     * 500ms while music plays (same cadence as the watch's position ticker), so the ring and
     * time advance in real time. A non-positive [durationMs] falls back to the fixed sample
     * progress; [playing] null resets to the sample "playing" state.
     */
    fun setPlayback(playing: Boolean?, positionMs: Long, durationMs: Long) {
        livePlaying = playing
        livePositionMs = positionMs
        liveDurationMs = durationMs
        if (surface == PreviewSurface.PLAYER ||
                surface == PreviewSurface.MINI_BUTTONS ||
                surface == PreviewSurface.AOD) {
            invalidate()
        }
    }

    /** Real mini-button and quadrant icons from the currently active action config. */
    internal fun setButtonIcons(icons: PreviewButtonIcons) {
        miniButtonIcons = icons.miniButtons
        quadrantIcons = icons.quadrants
        quickPanelIcons = icons.quickPanel
        invalidate()
    }

    private fun isPlayingShown(): Boolean {
        if (focusedPreference == "wear_track_time_mode") {
            return trackTimeMode != "paused"
        }
        // A public profile always keeps the sample playback state. The optional detail-only cover
        // is deliberately visual-only: it must not turn a community preview into a disclosure of
        // the person's current playback state.
        return if (themeProfile != null) true else livePlaying ?: true
    }

    private fun progressFraction(): Float = if (
            themeProfile == null &&
            (surface == PreviewSurface.PLAYER ||
                    surface == PreviewSurface.MINI_BUTTONS ||
                    surface == PreviewSurface.AOD) && liveDurationMs > 0
    ) {
        (livePositionMs.toFloat() / liveDurationMs).coerceIn(0f, 1f)
    } else {
        SAMPLE_PROGRESS
    }

    private fun timeText(): String = if (themeProfile == null && liveDurationMs > 0) {
        "${formatTime(livePositionMs.coerceAtLeast(0))} / ${formatTime(liveDurationMs)}"
    } else {
        "1:07 / 3:12"
    }

    /** Chat's voice-note timestamp is elapsed time only. Its Compose face calls
     * `formatFaceClockTime(positionMs)`, so reusing [timeText] here used to add a duration that
     * never appears on the wrist. */
    private fun chatElapsedTimeText(): String = if (themeProfile == null && liveDurationMs > 0) {
        formatTime(livePositionMs.coerceAtLeast(0))
    } else {
        "1:07"
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60)
    }

    /** Mirrors the watch's WEAR_TRACK_TIME_MODE handling against the shown playing state. */
    private fun trackTimeVisible(): Boolean = when (trackTimeMode) {
        "never" -> false
        "playing" -> isPlayingShown()
        "paused" -> !isPlayingShown()
        else -> true
    }

    private fun rebuildLiveArt() {
        recycleOwned(liveArt)
        recycleOwned(liveArtBlurred)
        // The frosted copy is derived from liveArt, so it dies with it.
        recycleOwned(frostedPreviewArt)
        frostedPreviewArt = null
        frostedPreviewSource = null
        recycleOwned(liveOverlayArtBlurred)
        liveArt = null
        liveArtBlurred = null
        liveOverlayArtBlurred = null
        val source = nowPlayingSource ?: return
        val size = min(width, height)
        if (size <= 0) {
            return // onSizeChanged() rebuilds once measured
        }
        liveArt = centerCrop(source, size)
        liveArtBlurred = blurArtwork(liveArt!!, albumBlurRadius)
        liveOverlayArtBlurred = blurArtwork(liveArt!!, overlayBlurRadius)
    }

    /** Center-crops [source] into a size×size square, like the watch's centerCrop ImageView. */
    private fun centerCrop(source: Bitmap, size: Int): Bitmap {
        val scale = size.toFloat() / min(source.width, source.height)
        val w = (source.width * scale).toInt().coerceAtLeast(size)
        val h = (source.height * scale).toInt().coerceAtLeast(size)
        val scaled = Bitmap.createScaledBitmap(source, w, h, true)
        val cropped = Bitmap.createBitmap(scaled, (w - size) / 2, (h - size) / 2, size, size)
        if (cropped !== scaled && scaled !== source) {
            scaled.recycle()
        }
        return cropped
    }

    /** The watch's own accent selection, run on the preview's art.
     *
     *  This used to claim to match the watch while quietly doing something else: it took the first
     *  named swatch (always Vibrant when the cover has one), where the watch ran that swatch
     *  through a population guard and fell back to the dominant colour. One cover could therefore
     *  come out beige here and grey on the wrist. Both sides now call the shared
     *  [selectPrimaryAccent] with the user's [MiscPreferences.WEAR_ALBUM_ACCENT_SOURCE], so the two
     *  can only differ if the preference does.
     *
     *  Synchronous rather than [Palette.generate]'s async callback: this preview thumbnail's
     *  art is already small (see [rebuildLiveArt]'s centerCrop, and Palette's own internal
     *  downsampling), so the extraction is fast enough not to jank - and doing it inline avoids
     *  a visible flash of the default/static accent before the real one lands a frame later. */
    private fun extractLiveAccent(art: Bitmap?) {
        liveAccent = null
        liveSecondaryAccent = null
        liveTertiaryAccent = null
        liveAccentSource = albumAccentSource
        art?.let { bitmap ->
            val p = Palette.from(bitmap).generate()
            val preferredColors = listOfNotNull(
                    p.vibrantSwatch,
                    p.mutedSwatch,
                    p.lightVibrantSwatch,
                    p.darkVibrantSwatch,
                    p.lightMutedSwatch,
                    p.darkMutedSwatch,
                    p.dominantSwatch
            ).map { it.rgb }.distinct()
            val primary = selectPrimaryAccent(
                    p.vibrantSwatch?.let { SwatchInfo(it.rgb, it.population) },
                    p.swatches.map { SwatchInfo(it.rgb, it.population) },
                    AlbumAccentSource.fromPreference(albumAccentSource)
            ) ?: preferredColors.firstOrNull() ?: return@let
            val albumColors = p.swatches.sortedByDescending { it.population }
                    .map { it.rgb }
                    .distinct()
            // Named tonal swatches first (Palette specifically chooses them to be distinct from
            // each other), population-ranked raw swatches only as a fallback - mirrors the
            // watch's selectAlbumCompanionColors ordering, see MainActivity.kt's palette
            // extraction for why: two of the most-populous swatches are often near-duplicate
            // shades of the same dominant hue.
            val companions = (preferredColors + albumColors).distinct().filter { it != primary }
            val secondary = companions.getOrNull(0)
            val tertiary = companions.getOrNull(1)
            liveAccent = primary
            liveSecondaryAccent = secondary
            liveTertiaryAccent = tertiary
        }
    }

    private fun candidateFor(key: String): Any? =
            if (candidateActive && candidateKey == key) candidateValue else null

    /** The complete supplied profile wins over the active default preferences, but dialog
     * candidates still win over both so the existing preference editor remains live-previewable. */
    private fun profileValue(key: String): WatchThemeValue? = themeProfile?.settings?.get(key)

    /** Whether this profile must use fixed sample media rather than its local cover preview. */
    private fun usesSyntheticGalleryMedia(): Boolean =
            themeProfile != null && !themeProfileUsesLocalArtwork

    /** Gallery cards and moderation previews remain deterministic. A detail page may opt in to
     * the current cover only; its other media fields still use the bundled sample below. */
    private fun displayedArt(): Bitmap? =
            if (usesSyntheticGalleryMedia()) sampleArt else liveArt ?: sampleArt

    private fun displayedBlurredArt(): Bitmap? =
            if (usesSyntheticGalleryMedia()) sampleArtBlurred else liveArtBlurred ?: sampleArtBlurred

    private fun displayedOverlayBlurredArt(): Bitmap? =
            if (usesSyntheticGalleryMedia()) sampleOverlayArtBlurred
            else liveOverlayArtBlurred ?: sampleOverlayArtBlurred

    private fun displayedSourceArt(): Bitmap? =
            if (usesSyntheticGalleryMedia()) sampleArt else nowPlayingSource ?: sampleArt

    /** Appearance keys are stored scoped per face ("<baseKey>@<face>"). Resolves the key actually
     *  present for the current [face]: the scoped entry if set, else the scoped key itself when a
     *  per-face default exists (so [scopedDefault] below wins over any pre-existing *global*
     *  legacy value - see FaceScopedPreferences.getString for why), else the legacy global entry,
     *  else the scoped key again (both absent -> the supplied default is used). Non-scoped keys
     *  pass through untouched. */
    private fun effectiveKey(baseKey: String): String {
        // Profile preview never reaches into the user's active namespace. The readers below
        // consume [profileValue] directly; this branch also protects any future contains() caller.
        if (themeProfile != null) return baseKey
        if (!FaceScopedPreferences.isScoped(baseKey)) return baseKey
        val scoped = FaceScopedPreferences.scopedKey(baseKey, appearanceScope)
        // A custom snapshot must never inherit a mutable base preset or a legacy global. Missing
        // values fall through to the supplied/per-face default, matching the watch resolver.
        if (appearanceScope == ThemeAppearance.CUSTOM_SCOPE) return scoped
        if (baseKey == MiscPreferences.ALBUM_ART_STYLE.key &&
                !prefs.contains(scoped) &&
                FaceScopedPreferences.hasLegacyAlbumArtOverride(prefs)) {
            return baseKey
        }
        val hasPerFaceDefault = FaceScopedPreferences.perFaceDefault(face, baseKey) != null
        return if (prefs.contains(scoped) || hasPerFaceDefault || !prefs.contains(baseKey)) {
            scoped
        } else {
            baseKey
        }
    }

    /** Per-face default for a scoped key (album-accent surface consistency), else the given one. */
    private fun scopedDefault(baseKey: String, default: String): String =
            if (FaceScopedPreferences.isScoped(baseKey))
                FaceScopedPreferences.perFaceDefault(face, baseKey) ?: default
            else default

    /**
     * [scopedDefault] for booleans and ints.
     *
     * These used to take the caller's literal default straight through, so a per-face default only
     * reached the preview when it happened to be a String key - which is why Carousel's "edge ring
     * and edge seek off" showed as ON here while the watch drew them off. Exactly the omission the
     * watch's own Boolean resolver had, in the other renderer.
     */
    private fun scopedDefaultBoolean(baseKey: String, default: Boolean): Boolean =
            if (FaceScopedPreferences.isScoped(baseKey))
                FaceScopedPreferences.perFaceDefault(face, baseKey)?.toBooleanStrictOrNull()
                        ?: default
            else default

    private fun scopedDefaultInt(baseKey: String, default: Int): Int =
            if (FaceScopedPreferences.isScoped(baseKey))
                FaceScopedPreferences.perFaceDefault(face, baseKey)?.toIntOrNull() ?: default
            else default

    /**
     * Mirrors `MainActivity.resolveColorTreatmentPreference`, including the migration underneath it.
     *
     * `wear_color_treatment` replaced the older `wear_dynamic_accent` boolean, and the watch still
     * falls back to that boolean whenever the new key has never been written - an install that
     * turned the dynamic accent off and has not opened the rewritten Colors page since is on
     * "normal", not "expressive". Reading only the new key here gave that install a miniature in a
     * treatment its watch does not use, which is exactly the disagreement this preview exists to
     * rule out. A supplied theme profile and an open picker's candidate both count as an explicit
     * choice, so neither reaches the fallback.
     */
    private fun readColorTreatment(): String {
        val key = MiscPreferences.WEAR_COLOR_TREATMENT.key
        // A supplied profile is a complete snapshot and an open picker's candidate is an explicit
        // choice. Neither has any business consulting *this* phone's legacy boolean - a gallery
        // theme would otherwise be previewed through the viewer's own migration state.
        if (themeProfile != null || candidateFor(key) != null) {
            return readString(key, "expressive")
        }
        val context = if (appearanceScope == ThemeAppearance.CUSTOM_SCOPE) {
            ThemeAppearance.resolve(prefs)
        } else {
            // The candidate face while the layout picker is open, which is the scope every other
            // read in this snapshot is using - not the persisted one resolve() would return.
            AppearanceContext.BuiltIn(face)
        }
        if (FaceScopedPreferences.containsExplicitValue(prefs, key, context)) {
            return readString(key, "expressive")
        }
        return if (readBoolean(MiscPreferences.WEAR_DYNAMIC_ACCENT.key, true)) "expressive"
        else "normal"
    }

    private fun readString(key: String, default: String): String {
        candidateFor(key)?.let { return it.toString() }
        val resolvedDefault = scopedDefault(key, default)
        if (themeProfile != null) {
            return (profileValue(key) as? WatchThemeValue.Text)?.value ?: resolvedDefault
        }
        val readKey = effectiveKey(key)
        return try {
            prefs.getString(readKey, resolvedDefault) ?: resolvedDefault
        } catch (ignored: ClassCastException) {
            prefs.all[readKey]?.toString() ?: resolvedDefault
        }
    }

    private fun readBoolean(key: String, default: Boolean): Boolean {
        candidateFor(key)?.let { candidate ->
            return when (candidate) {
                is Boolean -> candidate
                is Number -> candidate.toInt() != 0
                else -> candidate.toString().toBooleanStrictOrNull() ?: default
            }
        }
        val resolvedDefault = scopedDefaultBoolean(key, default)
        if (themeProfile != null) {
            return (profileValue(key) as? WatchThemeValue.Flag)?.value ?: resolvedDefault
        }
        val readKey = effectiveKey(key)
        return try {
            prefs.getBoolean(readKey, resolvedDefault)
        } catch (ignored: ClassCastException) {
            prefs.all[readKey]?.toString()?.toBooleanStrictOrNull() ?: resolvedDefault
        }
    }

    /** Numeric prefs may be persisted as Int or as String (EditTextPreference) - accept both. */
    private fun readInt(key: String, default: Int): Int {
        candidateFor(key)?.let { candidate ->
            return when (candidate) {
                is Number -> candidate.toInt()
                else -> candidate.toString().toIntOrNull() ?: default
            }
        }
        val resolvedDefault = scopedDefaultInt(key, default)
        if (themeProfile != null) {
            return (profileValue(key) as? WatchThemeValue.Number)?.value ?: resolvedDefault
        }
        val readKey = effectiveKey(key)
        return try {
            prefs.getInt(readKey, resolvedDefault)
        } catch (ignored: ClassCastException) {
            prefs.getString(readKey, null)?.toIntOrNull() ?: resolvedDefault
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val size = min(w, h)
        if (size <= 0) return
        recycleOwned(sampleArt)
        recycleOwned(sampleAlternateArt)
        recycleOwned(sampleArtBlurred)
        recycleOwned(sampleAlternateArtBlurred)
        recycleOwned(sampleOverlayArtBlurred)
        sampleArt = buildSampleArt(size, alternate = false)
        sampleAlternateArt = buildSampleArt(size, alternate = true)
        rebuildBlurCaches()
        if (demoMiniButtonIcons.isEmpty()) {
            demoMiniButtonIcons = buildDemoMiniButtonIcons()
        }
        rebuildLiveArt()
    }

    /** Procedural "album cover": a warm two-tone gradient with soft color blobs, tuned so
     *  [SAMPLE_ALBUM_ACCENT] reads as its natural palette accent. */
    private fun buildSampleArt(size: Int, alternate: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val start = if (alternate) 0xFF214C55.toInt() else 0xFF6E3B33.toInt()
        val end = if (alternate) 0xFF35234F.toInt() else 0xFF241B2F.toInt()
        p.shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(),
                start, end, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), p)
        p.shader = null
        p.color = if (alternate) 0x6674B9C4 else 0x66B86450
        c.drawCircle(size * (if (alternate) 0.30f else 0.68f), size * 0.30f, size * 0.34f, p)
        p.color = if (alternate) 0x556950A4 else 0x558A4A5E.toInt()
        c.drawCircle(size * (if (alternate) 0.73f else 0.25f), size * 0.75f, size * 0.40f, p)
        p.color = if (alternate) 0x3387DED5 else 0x33E8B08A
        c.drawCircle(size * 0.45f, size * 0.5f, size * 0.22f, p)
        return bmp
    }

    /**
     * The shared artwork blur, not a second copy of it.
     *
     * [BitmapBlur] exists precisely so a given radius looks the same on the wrist and here, and
     * this used to reimplement its loop rather than call it - already drifting, since it truncated
     * the sampled dimensions where the shared one rounds them, and repeated the 96px floor as a
     * literal. The result is cached; this work never runs while the user is dragging on the watch.
     */
    private fun blurArtwork(source: Bitmap, radius: Int): Bitmap {
        val blurred = BitmapBlur.blur(source, radius.toFloat())
        // BitmapBlur hands the source straight back at radius 0 - which the numeric field accepts,
        // and an imported profile can carry. Every caller here stores the result in a cache that
        // recycleOwned later frees, and that guard only spares nowPlayingSource, so returning the
        // shared bitmap would recycle artwork the rest of the view is still drawing from. The
        // hand-rolled loop this replaced always allocated, so the hazard is new with the call.
        if (blurred !== source) return blurred
        return source.copy(source.config ?: Bitmap.Config.ARGB_8888, false) ?: blurred
    }

    private fun rebuildBlurCaches() {
        recycleOwned(sampleArtBlurred)
        recycleOwned(sampleAlternateArtBlurred)
        recycleOwned(sampleOverlayArtBlurred)
        recycleOwned(liveArtBlurred)
        recycleOwned(liveOverlayArtBlurred)
        // Match the watch: when the background is already blurred, the overlay reuses the album
        // blur radius so a given radius reads the same and the two never look like different blurs.
        val backgroundBlurred = PlayerBackgroundStyle.fromPreference(artStyle).blurredArtwork
        val overlayRadius = if (backgroundBlurred) albumBlurRadius else overlayBlurRadius
        sampleArtBlurred = sampleArt?.let { blurArtwork(it, albumBlurRadius) }
        sampleAlternateArtBlurred = sampleAlternateArt?.let { blurArtwork(it, albumBlurRadius) }
        sampleOverlayArtBlurred = sampleArt?.let { blurArtwork(it, overlayRadius) }
        liveArtBlurred = liveArt?.let { blurArtwork(it, albumBlurRadius) }
        liveOverlayArtBlurred = liveArt?.let { blurArtwork(it, overlayRadius) }
    }

    /**
     * The frosted-rim composition for whichever artwork the preview is currently showing, built
     * through the same [FrostedEdges] the watch uses so the two cannot drift.
     *
     * Cached against the bitmap it was built from *and* the blur radius, since the radius slider
     * is live on this screen - unlike the watch, where a radius change arrives as a whole
     * preference sync.
     */
    private fun frostedPreviewArt(): Bitmap? {
        val base = displayedArt() ?: return null
        if (frostedPreviewSource === base && frostedPreviewRadius == albumBlurRadius) {
            frostedPreviewArt?.takeIf { !it.isRecycled }?.let { return it }
        }
        val frosted = FrostedEdges.compose(base, albumBlurRadius.toFloat())
        // Not recycled, for the same reason the watch does not - see frostArtworkIfSelected. The
        // previous composition may still be the one a queued draw pass is about to paint.
        frostedPreviewSource = base
        frostedPreviewRadius = albumBlurRadius
        frostedPreviewArt = frosted
        return frosted
    }

    private fun recycleOwned(bitmap: Bitmap?) {
        if (bitmap != null && !bitmap.isRecycled && bitmap !== nowPlayingSource) {
            bitmap.recycle()
        }
    }

    private fun buildDemoMiniButtonIcons(): List<PreviewActionIcon> = listOf(
            commonR.drawable.action_skip_prev,
            commonR.drawable.action_play,
            commonR.drawable.action_skip_next
    ).mapNotNull { resId ->
        val drawable = AppCompatResources.getDrawable(context, resId) ?: return@mapNotNull null
        val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).also { bitmap ->
            drawable.mutate()
            drawable.setTint(Color.WHITE)
            drawable.setBounds(0, 0, bitmap.width, bitmap.height)
            drawable.draw(Canvas(bitmap))
        }
        PreviewActionIcon(bitmap, tintable = true)
    }

    // --- Color resolution (mirrors the wear-side logic) ---

    private fun parseHexOrNull(hex: String): Int? =
            if (hex.isBlank()) null else try { Color.parseColor(hex) } catch (ignored: Exception) { null }

    private fun rawAlbumAccent(): Int =
            if (usesSyntheticGalleryMedia()) SAMPLE_ALBUM_ACCENT else liveAccent ?: SAMPLE_ALBUM_ACCENT

    /**
     * The face-wide palette, mirroring `MainActivity.applyAccentColor` on the watch. Both sides
     * call the shared resolver rather than switching on the raw preference string, which used to
     * treat any unrecognised value as Expressive - so a treatment the phone offers but this `when`
     * had not learned yet would preview as a plain album accent while the watch rendered it.
     */
    private fun globalTriad(): ColorHarmony.Triad = SurfacePaletteResolver.derive(
            SurfaceColorTreatment.fromPreference(
                    colorTreatment, default = SurfaceColorTreatment.EXPRESSIVE),
            ColorModifier.fromPreference(colorModifier),
            rawAlbumAccent(),
            rawSecondaryAccent(),
            rawTertiaryAccent(),
            parseHexOrNull(normalColor) ?: ACCENT_NEUTRAL,
            colorHueShift,
            normalColorMulti)

    private fun albumAccent(): Int = globalTriad().primary

    /** Colour that tints the shading gradient; mirrors MainActivity.resolvedShadingColor. */
    private fun resolvedShadingColor(): Int {
        val accent = albumAccent()
        return when (shadingColorMode) {
            "album" -> PaletteTransforms.shadingTone(accent)
            "desaturated" ->
                PaletteTransforms.shadingTone(PaletteTransforms.softenedAlbumAccent(accent))
            "custom" -> parseHexOrNull(shadingCustomColor)
                    ?.let { PaletteTransforms.shadingTone(it) } ?: Color.BLACK
            else -> Color.BLACK
        }
    }

    /** Real secondary/tertiary cover swatches, run through the face-wide treatment. A monochromatic
     * live cover falls back to a same-hue tone (see [rawSecondaryAccent]); the generated sample uses
     * colours actually present in [buildSampleArt]. */
    private fun albumSecondaryAccent(): Int = globalTriad().secondary

    private fun albumTertiaryAccent(): Int = globalTriad().tertiary

    private fun sameHueTone(color: Int, lightness: Float): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[1] = hsl[1].coerceIn(.25f, .82f)
        hsl[2] = lightness
        return ColorUtils.HSLToColor(hsl)
    }

    private fun displayTitle(): String {
        if (!showTrackTitle) return ""
        val title = if (themeProfile != null) {
            context.getString(R.string.preview_sample_title)
        } else {
            nowPlayingTitle ?: context.getString(R.string.preview_sample_title)
        }
        return if (focusedPreference == "wear_title_text_mode") {
            "$title · $title · $title"
        } else {
            title
        }
    }

    private fun displayArtist(): String = if (showTrackArtist) {
        if (themeProfile != null) {
            context.getString(R.string.preview_sample_artist)
        } else {
            nowPlayingArtist ?: context.getString(R.string.preview_sample_artist)
        }
    } else {
        ""
    }

    /** Truncates real track/artist names to the watch circle - uses [textPaint]'s current
     *  size/typeface, so set those first. */
    private fun ellipsize(text: String, maxWidth: Float): String =
            TextUtils.ellipsize(text, TextPaint(textPaint), maxWidth, TextUtils.TruncateAt.END).toString()

    // --- Title rendering (mirrors the watch's WEAR_TITLE_TEXT_MODE behaviors) ---

    /**
     * Scrolling title, like the watch's marquee: the text loops leftward inside a clip window,
     * drawn twice for a seamless wrap. Sets [marqueeActive] so onDraw schedules the next
     * animation frame - this is the only continuously animating piece of the preview (the mini
     * player's marquee titles already set that precedent).
     */
    private fun drawMarqueeText(canvas: Canvas, text: String, cx: Float, baseline: Float, availWidth: Float) =
            drawMarqueeRuns(
                    canvas,
                    listOf(TextRun(text, textPaint.color, textPaint.typeface,
                            textPaint.letterSpacing)),
                    cx,
                    baseline,
                    availWidth)

    /** One coloured run of a line built from more than one typeface or colour - the Note face's
     *  `Artist: Title` sentence is the only such line, and the reason these exist. */
    private class TextRun(
            val text: String,
            val color: Int,
            val typeface: Typeface?,
            val letterSpacing: Float)

    /** Applies [run]'s paint state and returns its width at the current text size. */
    private fun measureRun(run: TextRun): Float {
        textPaint.typeface = run.typeface
        textPaint.letterSpacing = run.letterSpacing
        textPaint.color = run.color
        return textPaint.measureText(run.text)
    }

    /** Draws [runs] as one line centred on [cx], each run in its own colour and typeface. */
    private fun drawTextRuns(canvas: Canvas, runs: List<TextRun>, cx: Float, baseline: Float) {
        val widths = runs.map { measureRun(it) }
        var x = cx - widths.sum() / 2f
        textPaint.textAlign = Paint.Align.LEFT
        runs.forEachIndexed { index, run ->
            measureRun(run)
            canvas.drawText(run.text, x, baseline, textPaint)
            x += widths[index]
        }
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.letterSpacing = 0f
    }

    /** [drawMarqueeText]'s scroll, for a line of several runs. The single-run case delegates here
     *  so the loop, the gap and the phase have one implementation. */
    private fun drawMarqueeRuns(
            canvas: Canvas,
            runs: List<TextRun>,
            cx: Float,
            baseline: Float,
            availWidth: Float
    ) {
        val widths = runs.map { measureRun(it) }
        val textWidth = widths.sum()
        val gap = availWidth * 0.4f
        val period = textWidth + gap
        val speedPxPerSecond = availWidth / 4f
        val phase = (previewAnimationTimeMillis() % 3_600_000L) / 1000f * speedPxPerSecond % period

        val left = cx - availWidth / 2f
        canvas.save()
        canvas.clipRect(left, baseline - textPaint.textSize * 1.2f,
                left + availWidth, baseline + textPaint.textSize * 0.45f)
        textPaint.textAlign = Paint.Align.LEFT
        // Drawn twice, a period apart, so the tail of one copy meets the head of the next.
        for (copy in 0..1) {
            var x = left - phase + copy * period
            runs.forEachIndexed { index, run ->
                measureRun(run)
                canvas.drawText(run.text, x, baseline, textPaint)
                x += widths[index]
            }
        }
        textPaint.textAlign = Paint.Align.CENTER
        canvas.restore()

        marqueeActive = true
    }

    /** Largest size in [maxSize]..[floorSize] at which [text] fits [availWidth], or null. */
    private fun findFittingTextSize(text: String, availWidth: Float, maxSize: Float, floorSize: Float): Float? {
        var size = maxSize
        while (size >= floorSize) {
            textPaint.textSize = size
            if (textPaint.measureText(text) <= availWidth) {
                return size
            }
            size -= 0.5f
        }
        return null
    }

    /** Greedy word-boundary split into up to [maxLines] lines at the current text size: each of
     *  the first `maxLines - 1` lines packs as many leading words as fit [availWidth]; whatever
     *  is left over is returned as the final line, raw and not itself guaranteed to fit (the
     *  caller ellipsizes it) - generalizes the old fixed two-line split the same way
     *  "wrap"/"wrap3"/"wrap5" generalize the watch's AdaptiveTitleText. */
    private fun splitLines(text: String, availWidth: Float, maxLines: Int): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var index = 0
        while (index < words.size && lines.size < maxLines - 1) {
            var line = ""
            var lastIndex = index
            for (i in index until words.size) {
                val candidate = if (line.isEmpty()) words[i] else "$line ${words[i]}"
                if (textPaint.measureText(candidate) <= availWidth || line.isEmpty()) {
                    line = candidate
                    lastIndex = i + 1
                } else {
                    break
                }
            }
            lines.add(line)
            index = lastIndex
        }
        val remainder = words.drop(index).joinToString(" ")
        if (remainder.isNotEmpty()) lines.add(remainder)
        return lines
    }

    /** Largest size in [maxSize]..[floorSize] at which [text] wraps to [maxLines] lines (or
     *  fewer) that all fit [availWidth] fully (no ellipsis) - the watch's "shrink until it wraps
     *  cleanly" behavior. Null if even at [floorSize] a line still overflows (caller then
     *  scrolls). */
    private fun findFittingWrapSize(
            text: String, availWidth: Float, maxSize: Float, floorSize: Float, maxLines: Int
    ): Float? {
        var size = maxSize
        while (size >= floorSize) {
            textPaint.textSize = size
            val lines = splitLines(text, availWidth, maxLines)
            if (lines.size > 1 && lines.all { textPaint.measureText(it) <= availWidth }) {
                return size
            }
            size -= 0.5f
        }
        return null
    }

    /** A resolved title layout: the chosen font [size], the line(s) to draw, and whether it
     *  scrolls. Leaves [textPaint]'s text size at [size]. */
    private class TitlePlan(val size: Float, val lines: List<String>, val marquee: Boolean)

    /**
     * The Classic artist is an [OutlineTextView] of its own: it always uses the View renderer's
     * smart two-line rule, independently of the title's selected overflow mode. Keep the plan
     * separate from [TitlePlan] because the source-icon sibling changes the available width as
     * the artist itself shrinks.
     */
    private class ClassicArtistPlan(
            val size: Float,
            val lines: List<String>,
            val marquee: Boolean,
            val labelWidth: Float
    )

    private fun planClassicArtist(
            text: String,
            totalWidth: Float,
            hasSourceGlyph: Boolean,
            dp: (Float) -> Float
    ): ClassicArtistPlan {
        val maxSize = artistTypographySpec.scaled(dp(CLASSIC_ARTIST_MAX_SP))
        val minSize = artistTypographySpec.scaled(dp(CLASSIC_ARTIST_MIN_SP))
        val sourceScale = sourceIconTypographySpec.scale

        textPaint.typeface = artistTypeface(bold = true)
        textPaint.letterSpacing = artistTypographySpec.trackingEm

        fun labelWidth(size: Float): Float {
            if (!hasSourceGlyph) return totalWidth.coerceAtLeast(1f)
            val icon = size * CLASSIC_SOURCE_ICON_SIZE_ARTIST_FACTOR * sourceScale
            val gap = size * CLASSIC_SOURCE_ICON_END_MARGIN_ARTIST_FACTOR
            return (totalWidth - icon - gap).coerceAtLeast(1f)
        }

        fun linesThatFit(size: Float, available: Float): List<String>? {
            textPaint.textSize = size
            val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.isEmpty()) return listOf(text)

            val lines = mutableListOf<String>()
            var line = ""
            words.forEach { word ->
                if (textPaint.measureText(word) > available) return null
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (line.isNotEmpty() && textPaint.measureText(candidate) > available) {
                    lines += line
                    if (lines.size >= CLASSIC_ARTIST_MAX_LINES) return null
                    line = word
                } else {
                    line = candidate
                }
            }
            if (line.isNotEmpty()) lines += line
            return lines.takeIf { it.size <= CLASSIC_ARTIST_MAX_LINES }
        }

        val step = dp(1f).coerceAtLeast(.01f)
        var size = maxSize
        while (size > minSize) {
            val available = labelWidth(size)
            linesThatFit(size, available)?.let { lines ->
                return ClassicArtistPlan(size, lines, marquee = false, labelWidth = available)
            }
            size = (size - step).coerceAtLeast(minSize)
        }

        val minimumWidth = labelWidth(minSize)
        return linesThatFit(minSize, minimumWidth)?.let { lines ->
            ClassicArtistPlan(minSize, lines, marquee = false, labelWidth = minimumWidth)
        } ?: ClassicArtistPlan(minSize, listOf(text), marquee = true, labelWidth = minimumWidth)
    }

    /**
     * Resolves how a title renders per the synced WEAR_TITLE_TEXT_MODE - "smart", "marquee",
     * "wrap"/"wrap3"/"wrap5" (up to two/three/five lines), "static" (one line) or "shrink" -
     * without drawing, so the caller can lay out the whole text block first. [textOverride] lets
     * curated faces pass their own case-transformed (e.g. uppercased) copy of [displayTitle].
     *
     * The watch has **two** title renderers and they agree on everything except one branch, which
     * is why [smartFallsBackToMarquee] exists rather than a single hardcoded cascade: the classic
     * face's OutlineTextView.applyShrinkOrWrapMode scrolls when a "smart" title cannot be made to
     * fit, while Compose's SmartTitleText accepts an ellipsis on two lines and never scrolls.
     * Pass true only for the classic face; every Compose face wants the default.
     *
     * Neither renderer shrinks in the wrap modes - OutlineTextView.applyWrapMode is documented as
     * "never shrinks or scrolls" and AdaptiveTitleText just hands Compose a maxLines - so this
     * deliberately does not search for a smaller fitting size there. It used to, which made the
     * preview draw a title at a size the wrist never renders.
     */
    private fun planTitle(
            availWidth: Float,
            maxSize: Float,
            floorSize: Float,
            textOverride: String? = null,
            bold: Boolean = true,
            smartFallsBackToMarquee: Boolean = false,
            /** Mirrors AdaptiveTitleText's own ceiling: a face with a fixed title band caps the
             *  mode's line count rather than letting it grow into whatever sits below. */
            maxLines: Int? = null,
            /** False only for Note's combined "Artist: Title" sentence, which has already cased
             *  its artist and title parts independently (their own typography specs) before
             *  concatenating them - applying the title's case here again would re-case the artist
             *  half by the wrong setting. Every other caller passes pure title text and wants the
             *  normal behaviour. */
            applyCase: Boolean = true
    ): TitlePlan {
        val raw = textOverride ?: displayTitle()
        val text = if (applyCase) titleTypographySpec.case.apply(raw) else raw
        textPaint.typeface = titleTypeface(bold = bold)
        // Scaling the whole size band (not just the ceiling) keeps the shrink/wrap cascade's
        // proportions, so a scaled title still degrades the way the unscaled one does.
        val maxSize = titleTypographySpec.scaled(maxSize)
        val floorSize = titleTypographySpec.scaled(floorSize)

        fun linesPlan(size: Float, maxLines: Int): TitlePlan {
            textPaint.textSize = size
            // splitLines returns nothing at all for empty text at a one-line limit, which a real
            // session with a blank title reaches through "static".
            val rawLines = splitLines(text, availWidth, maxLines).ifEmpty { listOf(text) }
            // The final line is ellipsized even when the split produced only one, because
            // "static" is a one-line limit: skipping it there let a long title run off the face.
            return TitlePlan(
                    size,
                    rawLines.dropLast(1) + ellipsize(rawLines.last(), availWidth),
                    false)
        }

        fun capped(lines: Int) = if (maxLines == null) lines else minOf(lines, maxLines)

        val wrapLines = TitleTextMode.wrapLines(titleTextMode)
        return when {
            titleTextMode == "marquee" -> TitlePlan(maxSize, listOf(text), marquee = true)
            wrapLines != null -> linesPlan(maxSize, capped(wrapLines))
            titleTextMode == "shrink" -> {
                val fitted = findFittingTextSize(text, availWidth, maxSize, floorSize) ?: floorSize
                textPaint.textSize = fitted
                TitlePlan(fitted, listOf(ellipsize(text, availWidth)), false)
            }
            smartFallsBackToMarquee -> {
                // Classic: the largest size that wraps cleanly within the line limit, else scroll.
                val fitted = findFittingTextSize(text, availWidth, maxSize, floorSize)
                when {
                    fitted != null -> TitlePlan(fitted, listOf(text), false)
                    else -> findFittingWrapSize(text, availWidth, maxSize, floorSize, capped(2))
                            ?.let { linesPlan(it, capped(2)) }
                            ?: TitlePlan(maxSize, listOf(text), marquee = true)
                }
            }
            else -> {
                // Compose: shrink on one line down to the floor, then two lines *at that floor*,
                // then accept the ellipsis. SmartTitleText never scrolls.
                val fitted = findFittingTextSize(text, availWidth, maxSize, floorSize)
                if (fitted != null) TitlePlan(fitted, listOf(text), false)
                else linesPlan(floorSize, capped(2))
            }
        }
    }

    /** Draws a title at [baselineY] honoring the synced text mode via [planTitle] - the shared
     *  entry point every curated/expressive title block uses, so the preview matches the watch's
     *  AdaptiveTitleText (marquee/wrap/static/shrink/smart) instead of each face only ever
     *  shrinking-or-scrolling with no wrap tier. Returns the total height of every line drawn, so
     *  the caller can push whatever follows (e.g. the artist line) down accordingly.
     *
     *  [color] is the face's already-resolved colour (callers run it through [titleAlpha]).
     *  [minSize] mirrors AdaptiveTitleText's `minFontSize`, whose default is the same 0.62 of the
     *  designed size - a face that passes its own explicit floor on the watch must pass it here
     *  too. [designedTracking] is the tracking that face composed with: it survives unless the
     *  user actually moved the title tracking off zero, which is the same "a default means keep
     *  what this face designed" merge AdaptiveTitleText performs. */
    private fun drawAdaptiveTitle(
            canvas: Canvas,
            cx: Float,
            baselineY: Float,
            availWidth: Float,
            maxSize: Float,
            color: Int,
            textOverride: String? = null,
            bold: Boolean = true,
            minSize: Float = maxSize * 0.62f,
            designedTracking: Float = 0f,
            smartFallsBackToMarquee: Boolean = false,
            align: Paint.Align = Paint.Align.CENTER,
            bottomAnchored: Boolean = false,
            lineHeight: Float? = null,
            maxLines: Int? = null
    ): Float = drawTitlePlan(
            canvas,
            planTitle(availWidth, maxSize, minSize, textOverride, bold, smartFallsBackToMarquee,
                    maxLines),
            cx,
            baselineY,
            availWidth,
            color,
            bold,
            designedTracking,
            lineHeight,
            align = align,
            bottomAnchored = bottomAnchored)

    /** Draws an already-resolved [plan]. Split out of [drawAdaptiveTitle] for the faces that
     *  cannot pick their text width until they know how many lines the title settled on: on a
     *  round screen the usable chord narrows with depth, so those plan first, re-measure, and then
     *  draw - the same settle CarouselFace performs through AdaptiveTitleText's `onLineCount`.
     *
     *  [lineHeight] overrides the font's own leading for a face that composed a fixed one. It does
     *  not scale as the title shrinks, matching AdaptiveTitleText's documented behaviour for an
     *  explicitly passed `lineHeight`. */
    private fun drawTitlePlan(
            canvas: Canvas,
            plan: TitlePlan,
            x: Float,
            baselineY: Float,
            availWidth: Float,
            color: Int,
            bold: Boolean = true,
            designedTracking: Float = 0f,
            lineHeight: Float? = null,
            align: Paint.Align = Paint.Align.CENTER,
            bottomAnchored: Boolean = false
    ): Float {
        textPaint.typeface = titleTypeface(bold = bold)
        textPaint.textSize = plan.size
        textPaint.color = color
        textPaint.textAlign = align
        textPaint.letterSpacing =
                if (titleTypographySpec.trackingEm == 0f) designedTracking
                else titleTypographySpec.trackingEm
        val fm = textPaint.fontMetrics
        val lineH = lineHeight ?: (fm.descent - fm.ascent)
        // Depth and Immersive stack their metadata upward from the bottom edge, so a title that
        // wrapped has to grow towards the top - their watch counterparts are bottom-aligned
        // Columns, where an extra line pushes everything above it up rather than overrunning the
        // artist line below.
        val baselineY = if (bottomAnchored) {
            baselineY - lineH * (plan.lines.size - 1)
        } else {
            baselineY
        }
        if (plan.marquee) {
            // drawMarqueeText centres its scroll window on the x it is handed and leaves the paint
            // centre-aligned, so a left-aligned face (Split's panel) has to hand it the middle of
            // the run and put its own alignment back afterwards.
            drawMarqueeText(
                    canvas,
                    plan.lines.first(),
                    if (align == Paint.Align.LEFT) x + availWidth / 2f else x,
                    baselineY,
                    availWidth)
            textPaint.textAlign = align
        } else {
            plan.lines.forEachIndexed { index, line ->
                canvas.drawText(line, x, baselineY + index * lineH, textPaint)
            }
        }
        textPaint.letterSpacing = 0f
        return lineH * plan.lines.size
    }

    private fun resolvedTreatment(mode: String, legacyDesaturated: Boolean): SurfaceColorTreatment {
        val global = SurfaceColorTreatment.fromPreference(
                colorTreatment, default = SurfaceColorTreatment.EXPRESSIVE)
        return SurfaceColorTreatment.fromPreference(mode, legacyDesaturated).resolveAgainst(global)
    }

    /**
     * The full three-colour palette for a surface, derived by the same `common` resolver the watch
     * uses. The preview previously had its own `when` over the treatment enum for the primary and
     * another for the companions; every treatment added since would have needed both to be edited
     * in step with the watch's copy, which is the drift this shared resolver removes.
     */
    private fun surfaceTriad(
            mode: String,
            custom: String,
            legacyDesaturated: Boolean
    ): ColorHarmony.Triad {
        val selected = SurfaceColorTreatment.fromPreference(mode, legacyDesaturated)
        val fixed = (if (selected == SurfaceColorTreatment.FOLLOW) null else parseHexOrNull(custom))
                ?: parseHexOrNull(normalColor)
                ?: ACCENT_NEUTRAL
        return SurfacePaletteResolver.derive(
                resolvedTreatment(mode, legacyDesaturated),
                ColorModifier.fromPreference(colorModifier),
                rawAlbumAccent(),
                rawSecondaryAccent(),
                rawTertiaryAccent(),
                fixed,
                colorHueShift,
                normalColorMulti)
    }

    private fun resolveTint(mode: String, custom: String, legacyDesaturated: Boolean): Int =
            surfaceTriad(mode, custom, legacyDesaturated).primary

    private fun volumeAccent(): Int =
            resolveTint(volumeColorMode, volumeCustomColor, legacyDesaturated = false)

    private fun quickPanelAccent(): Int =
            resolveTint(quickPanelColorMode, quickPanelCustomColor, legacyDesaturated = false)

    private fun resolveSecondaryTint(mode: String, custom: String, legacyDesaturated: Boolean): Int =
            surfaceTriad(mode, custom, legacyDesaturated).secondary

    private fun resolveTertiaryTint(mode: String, custom: String, legacyDesaturated: Boolean): Int =
            surfaceTriad(mode, custom, legacyDesaturated).tertiary

    private fun rawSecondaryAccent(): Int = when {
        usesSyntheticGalleryMedia() -> SAMPLE_ALBUM_SECONDARY
        liveAccent != null -> liveSecondaryAccent ?: sameHueTone(liveAccent!!, .42f)
        else -> SAMPLE_ALBUM_SECONDARY
    }

    private fun rawTertiaryAccent(): Int = when {
        usesSyntheticGalleryMedia() -> SAMPLE_ALBUM_TERTIARY
        liveAccent != null -> liveTertiaryAccent ?: sameHueTone(liveAccent!!, .68f)
        else -> SAMPLE_ALBUM_TERTIARY
    }

    /** The raw (untreated) album accent triple currently driving this preview, with the same
     *  sample-art fallback used when nothing's playing - exposed so the color-treatment picker's
     *  per-option swatches (see ColorTreatmentPreference) match this preview exactly instead of
     *  re-extracting their own, possibly different, palette. */
    fun currentAlbumAccents(): Triple<Int, Int, Int> =
            Triple(rawAlbumAccent(), rawSecondaryAccent(), rawTertiaryAccent())

    private fun volumeSecondaryAccent(): Int =
            resolveSecondaryTint(volumeColorMode, volumeCustomColor, false)

    private fun volumeTertiaryAccent(): Int =
            resolveTertiaryTint(volumeColorMode, volumeCustomColor, false)

    private fun quickPanelSecondaryAccent(): Int =
            resolveSecondaryTint(quickPanelColorMode, quickPanelCustomColor, false)

    private fun quickPanelTertiaryAccent(): Int =
            resolveTertiaryTint(quickPanelColorMode, quickPanelCustomColor, false)

    /** WatchTheme.accentForText: lift lightness so accents read on the dark screen. */
    private fun accentForText(color: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[2] = hsl[2].coerceAtLeast(0.62f)
        return ColorUtils.HSLToColor(hsl)
    }

    /**
     * The artist line's colour, mirroring the watch's `resolvedArtistTextColor`: the resolved tint
     * through the shared lightness floor, then the adaptive correction when the user enabled it.
     *
     * A dedicated funnel rather than folding the correction into [accentForText], which the AOD
     * tint and the queue accent also use - only the artist line has a measured background band.
     */
    private fun artistTextColor(): Int {
        val base = accentForText(resolveTint(artistMode, artistCustom, artistDesaturated))
        if (!artistAdaptiveContrast) return base
        val background = artistBandLuminance() ?: return base
        return AdaptiveTextContrast.adapt(base, background)
    }

    /**
     * The user's chosen title colour, or null to leave every face its own - mirroring the watch's
     * `resolvedTitleTextColor`.
     *
     * "face" resolves no tint at all, which is what keeps an untouched install pixel-identical:
     * each miniature keeps drawing the literal its face was designed with.
     */
    private fun resolvedTitleColor(): Int? {
        if (titleColorMode == MiscPreferences.TITLE_COLOR_FACE_DEFAULT) return null
        val base = accentForText(resolveTint(titleColorMode, titleCustomColor, false))
        if (!titleAdaptiveContrast) return base
        val background = titleBandLuminance() ?: return base
        return AdaptiveTextContrast.adapt(base, background)
    }

    /** The band the title sits on, matching the watch's `titleBandLuminance`. */
    private fun titleBandLuminance(): Float? = sampleBackdropLuminance(0.10f, 0.90f, 0.54f, 0.70f)

    /**
     * Luminance of the artwork behind the artist line. Same band as the watch's
     * `artistBandLuminance` (10%-90% wide, 68%-84% down) so the preview predicts the same
     * correction the watch will apply.
     */
    private fun artistBandLuminance(): Float? = sampleBackdropLuminance(0.10f, 0.90f, 0.68f, 0.84f)

    /** The strip the top-centre clock sits on, matching the watch's `clockBandLuminance`. */
    private fun clockBandLuminance(): Float? = sampleBackdropLuminance(0.35f, 0.65f, 0.02f, 0.15f)

    /**
     * [sampleArtLuminance] against what the miniature is *actually* drawing, mirroring the watch's
     * `sampleBackdropLuminance` through the shared
     * [AdaptiveTextContrast.backdropLuminance] - a background style that hides the artwork paints
     * a black field, and measuring the cover there answers about a picture neither renderer shows.
     */
    private fun sampleBackdropLuminance(
            leftFraction: Float,
            rightFraction: Float,
            topFraction: Float,
            bottomFraction: Float
    ): Float? = AdaptiveTextContrast.backdropLuminance(
            PlayerBackgroundStyle.fromPreference(artStyle)) {
        sampleArtLuminance(leftFraction, rightFraction, topFraction, bottomFraction)
    }

    /**
     * Average luminance of the shown artwork inside a rectangle given as fractions of the bitmap.
     *
     * One sampler for the artist band, the clock band and the clock's light/dark test, mirroring
     * the watch's identically-named helper - these were three near-identical copies, which is
     * exactly how the preview and the watch drift apart on where they measure.
     */
    private fun sampleArtLuminance(
            leftFraction: Float,
            rightFraction: Float,
            topFraction: Float,
            bottomFraction: Float
    ): Float? {
        val art = displayedArt() ?: return null
        val w = art.width
        val h = art.height
        if (w <= 0 || h <= 0) return null
        val left = (w * leftFraction).toInt().coerceIn(0, w - 1)
        val right = (w * rightFraction).toInt().coerceIn(left + 1, w)
        val top = (h * topFraction).toInt().coerceIn(0, h - 1)
        val bottom = (h * bottomFraction).toInt().coerceIn(top + 1, h)
        var sum = 0.0
        var n = 0
        for (cx in 0 until 5) {
            for (cy in 0 until 3) {
                val px = left + (right - left) * cx / 4
                val py = top + (bottom - top) * cy / 2
                sum += ColorUtils.calculateLuminance(
                        art.getPixel(px.coerceIn(0, w - 1), py.coerceIn(0, h - 1)))
                n++
            }
        }
        return if (n > 0) (sum / n).toFloat() else null
    }

    private fun tonal(accent: Int, lightness: Float, minSat: Float, maxSat: Float): Int =
            PaletteTransforms.tonalSurface(accent, lightness, minSat, maxSat)

    /** Mirrors MainActivity.liftedAccent on the watch: raises a near-black album accent to a
     *  lightness that survives being used as text or a hairline rather than as a fill. */
    private fun liftedAccent(color: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        if (hsl[2] >= 0.4f) return color
        hsl[2] = 0.45f
        return ColorUtils.HSLToColor(hsl)
    }

    // --- Drawing ---

    private data class PreviewGeometry(
            val bounds: RectF,
            val cx: Float,
            val cy: Float,
            val radius: Float,
            val dpScale: Float,
            val round: Boolean
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        marqueeActive = false
        transientAnimationActive = false

        val geometry = previewGeometry()
        val dp: (Float) -> Float = { value -> value * geometry.dpScale }

        drawDeviceShadow(canvas, geometry, dp)

        val clip = Path().apply {
            if (geometry.round) {
                addCircle(geometry.cx, geometry.cy, geometry.radius, Path.Direction.CW)
            } else {
                addRoundRect(geometry.bounds, dp(10f), dp(10f), Path.Direction.CW)
            }
        }
        canvas.save()
        canvas.clipPath(clip)

        when (surface) {
            PreviewSurface.PLAYER, PreviewSurface.MINI_BUTTONS ->
                drawPlayerSurface(canvas, geometry, dp)
            PreviewSurface.AOD -> drawAodSurface(canvas, geometry, dp)
            PreviewSurface.VOLUME -> drawVolumeSurface(canvas, geometry, dp)
            PreviewSurface.SEEK -> drawSeekSurface(canvas, geometry, dp)
            PreviewSurface.QUICK_PANEL -> drawQuickPanelSurface(canvas, geometry, dp)
            PreviewSurface.QUEUE -> drawQueueSurface(canvas, geometry, dp)
        }

        canvas.restore()

        if ((marqueeActive || transientAnimationActive) && isShown) {
            postInvalidateOnAnimation()
        }
    }

    private fun previewGeometry(): PreviewGeometry {
        val viewMin = min(width, height).toFloat()
        val margin = viewMin * 0.035f
        val availableWidth = (width - margin * 2f).coerceAtLeast(1f)
        val availableHeight = (height - margin * 2f).coerceAtLeast(1f)
        val scale = min(availableWidth / deviceWidthDp, availableHeight / deviceHeightDp)
        val screenWidth = deviceWidthDp * scale
        val screenHeight = deviceHeightDp * scale
        val left = (width - screenWidth) / 2f
        val top = (height - screenHeight) / 2f
        val bounds = RectF(left, top, left + screenWidth, top + screenHeight)
        return PreviewGeometry(
                bounds = bounds,
                cx = bounds.centerX(),
                cy = bounds.centerY(),
                radius = min(bounds.width(), bounds.height()) / 2f,
                dpScale = scale,
                round = deviceRound != false
        )
    }

    private fun drawDeviceShadow(
            canvas: Canvas,
            geometry: PreviewGeometry,
            dp: (Float) -> Float
    ) {
        val shadowOffset = dp(5f)
        if (geometry.round) {
            val shadowRadius = geometry.radius + dp(7f)
            fillPaint.shader = RadialGradient(
                    geometry.cx,
                    geometry.cy + shadowOffset,
                    shadowRadius,
                    intArrayOf(0x33000000, 0x33000000, 0x00000000),
                    floatArrayOf(0f, geometry.radius / shadowRadius, 1f),
                    Shader.TileMode.CLAMP
            )
            canvas.drawCircle(geometry.cx, geometry.cy + shadowOffset, shadowRadius, fillPaint)
            fillPaint.shader = null
        } else {
            fillPaint.shader = null
            fillPaint.color = 0x22000000
            val shadow = RectF(geometry.bounds).apply {
                inset(-dp(5f), -dp(5f))
                offset(0f, shadowOffset)
            }
            canvas.drawRoundRect(shadow, dp(14f), dp(14f), fillPaint)
        }
    }

    private fun drawPlayerSurface(
            canvas: Canvas,
            geometry: PreviewGeometry,
            dp: (Float) -> Float
    ) {
        // Some controls are intentionally face-specific on the watch. While that preference is
        // focused, use the face that can actually demonstrate it; the normal player remains the
        // user's selected face everywhere else.
        val demonstratedFace = when (focusedPreference) {
            "wear_expressive_seek_mode" -> "expressive"
            "wear_title_text_mode" -> "classic"
            "wear_progress_style" -> face
            else -> face
        }
        drawPlayerBackdrop(canvas, geometry, dp)

        // Immediately after the backdrop and before any face draws, exactly where the watch
        // renders it (inside PlayerBackgroundTreatment). Any face can wear this piece, so the
        // preview must not tie it to one either. Ribbon and Frame ship with a hidden backdrop,
        // but an explicit per-face artwork choice must still be visible in their miniature.
        if (accentFloor.isVisible && demonstratedFace != "split") {
            drawAccentFloor(canvas, geometry, AdaptiveTextContrast.adapt(albumAccent(), 0f))
        }
        when (demonstratedFace) {
            "expressive" -> drawExpressive(
                    canvas, geometry.cx, geometry.cy, geometry.radius, geometry.bounds, dp)
            "carousel" -> drawCarouselPlayer(canvas, geometry, dp)
            "chat" -> drawChatPlayer(canvas, geometry, dp)
            "split" -> drawSplitPlayer(canvas, geometry, dp)
            "note" -> drawNotePlayer(canvas, geometry, dp)
            "verse" -> drawVersePlayer(canvas, geometry, dp)
            "metadata" -> drawMetadataPlayer(canvas, geometry, dp)
            "ribbon" -> drawRibbonPlayer(canvas, geometry, dp)
            "frame" -> drawFramePlayer(canvas, geometry, dp)
            "vinyl", "poster", "studio", "halo", "aurora", "eclipse", "spectrum", "material", "immersive", "depth" ->
                drawCuratedPlayer(canvas, geometry, dp, demonstratedFace)
            else -> {
                drawPlayerShading(canvas, geometry.bounds, geometry.cx, geometry.cy,
                        geometry.radius)
                drawClassic(canvas, geometry, dp)
            }
        }
        if (edgeProgressVisible) {
            drawEdgeSeekRing(canvas, geometry.cx, geometry.cy, geometry.radius, dp)
        }
    }

    private fun drawPlayerBackdrop(
            canvas: Canvas,
            geometry: PreviewGeometry,
            dp: (Float) -> Float
    ) {
        fillPaint.shader = null
        fillPaint.color = Color.BLACK
        canvas.drawRect(geometry.bounds, fillPaint)

        val demonstratingBlur = focusedPreference == "album_art_blur_radius"
        val demonstratingFade = focusedPreference == "wear_album_art_fade"
        val effectiveStyle = when {
            demonstratingBlur -> if (
                PlayerBackgroundStyle.fromPreference(artStyle).usesBlurRadius
            ) artStyle else "blur"
            demonstratingFade &&
                    PlayerBackgroundStyle.fromPreference(artStyle).hidesArtwork -> "cover"
            else -> artStyle
        }
        val backgroundStyle = PlayerBackgroundStyle.fromPreference(effectiveStyle)
        val grayscale = backgroundStyle.grayscaleArtwork
        val blurred = backgroundStyle.blurredArtwork

        if (!backgroundStyle.hidesArtwork) {
            if (demonstratingFade) {
                // Square's inset isn't shown while demonstrating the fade duration specifically -
                // that preview only needs the blurred backdrop, same as plain Blur.
                val first = if (blurred) sampleArtBlurred else sampleArt
                val second = if (blurred) sampleAlternateArtBlurred else sampleAlternateArt
                drawFadeDemonstration(canvas, first, second, geometry.bounds, grayscale)
            } else if (backgroundStyle.squareCornerRadiusFraction != null) {
                drawArtwork(canvas, displayedBlurredArt(), geometry.bounds, 255, grayscale = false)
                // The true original source, not liveArt - that copy is already center-cropped to
                // a square for every other style's use, which would silently defeat the "never
                // crop" point of this one for any source that isn't already square.
                drawSquareInsetArtwork(
                        canvas, displayedSourceArt(), geometry.bounds,
                        backgroundStyle.squareCornerRadiusFraction ?: 0.10f
                )
            } else {
                val art = when {
                    backgroundStyle.frostedEdges -> frostedPreviewArt()
                    blurred -> displayedBlurredArt()
                    else -> displayedArt()
                }
                drawArtwork(canvas, art, geometry.bounds, 255, grayscale)
            }
        }

        drawPlayerBackgroundTreatment(canvas, geometry, backgroundStyle)

    }

    /** Mirrors Wear's Compose/native independent background renderers. */
    private fun drawPlayerBackgroundTreatment(
            canvas: Canvas,
            geometry: PreviewGeometry,
            style: PlayerBackgroundStyle
    ) {
        val bounds = geometry.bounds
        val cx = geometry.cx
        val cy = geometry.cy
        val radius = geometry.radius
        // Authored background styles (Expressive, Poster, ...) own their designed look and must
        // render it regardless of the "Dim album art" toggle - that toggle governs the separate
        // legibility scrim over plain artwork, not a background style's identity. The intensity
        // slider still modulates their depth. Plain treatments have no authored overlay.
        val authoredStrength = if (
            playerShadingStyle == PlayerShadingStyle.FOLLOW && !style.isPlainArtworkTreatment
        ) {
            (playerShadingIntensity / .8f).coerceIn(0f, SHADING_MAX_MULTIPLIER / .8f)
        } else {
            0f
        }
        fun alpha(base: Float): Int =
                (255f * base).toInt().coerceIn(0, 255)
        fun authoredAlpha(base: Float): Int =
                (255f * base * authoredStrength).toInt().coerceIn(0, 255)
        val accent = albumAccent()
        val primary = tunedPreviewColor(accent, .62f, .74f)
        val secondary = tunedPreviewColor(albumSecondaryAccent(), .58f, .70f)
        val tertiary = tunedPreviewColor(albumTertiaryAccent(), .62f, .72f)
        val deep = tunedPreviewColor(accent, .075f, .48f)
        val surfaceColor = tunedPreviewColor(albumSecondaryAccent(), .16f, .42f)

        fillPaint.shader = null
        when (style) {
            PlayerBackgroundStyle.COVER,
            PlayerBackgroundStyle.BLUR,
            // Frosting is baked into the bitmap (FrostedEdges), so nothing is drawn on top.
            PlayerBackgroundStyle.FROSTED,
            PlayerBackgroundStyle.BLACK_AND_WHITE,
            PlayerBackgroundStyle.BLURRED_BLACK_AND_WHITE,
            PlayerBackgroundStyle.SQUARE_SHARP,
            PlayerBackgroundStyle.SQUARE_SOFT,
            PlayerBackgroundStyle.SQUARE -> Unit

            PlayerBackgroundStyle.EXPRESSIVE,
            PlayerBackgroundStyle.EXPRESSIVE_NO_BLUR -> {
                fillPaint.color = ColorUtils.setAlphaComponent(
                        tonal(accent, .30f, .30f, .90f), alpha(.45f))
                canvas.drawRect(bounds, fillPaint)
                fillPaint.color = ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.30f))
                canvas.drawRect(bounds, fillPaint)
                fillPaint.shader = RadialGradient(
                        cx, cy, radius * 1.36f,
                        intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT,
                                ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.88f))),
                        floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(bounds, fillPaint)
            }

            PlayerBackgroundStyle.MATERIAL -> {
                fillPaint.color = Color.BLACK
                canvas.drawRect(bounds, fillPaint)
                val softened = colorTreatment == "desaturated"
                val materialColor = PaletteTransforms.tonalSurface(
                        accent,
                        if (softened) .36f else .26f,
                        if (softened) 0f else .30f,
                        .80f)
                fillPaint.shader = RadialGradient(
                        cx, cy, radius * 1.70f,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(materialColor, 184),
                                ColorUtils.setAlphaComponent(materialColor, 97),
                                ColorUtils.setAlphaComponent(materialColor, 31),
                                Color.TRANSPARENT),
                        floatArrayOf(0f, .50f, .80f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(bounds, fillPaint)
            }

            PlayerBackgroundStyle.POSTER -> {
                fillPaint.color = ColorUtils.setAlphaComponent(primary, alpha(.12f))
                canvas.drawRect(bounds, fillPaint)
                fillPaint.shader = LinearGradient(
                        0f, bounds.top, 0f, bounds.bottom,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.48f)),
                                ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.06f)),
                                ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.25f)),
                                ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.94f))),
                        floatArrayOf(0f, .36f, .68f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(bounds, fillPaint)
                fillPaint.shader = LinearGradient(
                        bounds.left, 0f, bounds.right, 0f,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.36f)),
                                Color.TRANSPARENT,
                                ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.36f))),
                        null, Shader.TileMode.CLAMP)
                canvas.drawRect(bounds, fillPaint)
            }

            PlayerBackgroundStyle.STUDIO -> {
                fillPaint.color = ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.48f))
                canvas.drawRect(bounds, fillPaint)
                fillPaint.shader = LinearGradient(
                        bounds.right, bounds.top, bounds.left, bounds.bottom,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(primary, 112),
                                ColorUtils.setAlphaComponent(secondary, 38),
                                Color.TRANSPARENT),
                        null, Shader.TileMode.CLAMP)
                canvas.drawRect(bounds, fillPaint)
            }

            PlayerBackgroundStyle.VINYL -> {
                fillPaint.color = ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.68f))
                canvas.drawRect(bounds, fillPaint)
                fillPaint.shader = RadialGradient(
                        bounds.left + bounds.width() * .64f,
                        bounds.top + bounds.height() * .38f,
                        radius * 1.38f,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(primary, 82),
                                ColorUtils.setAlphaComponent(deep, 51),
                                Color.TRANSPARENT),
                        floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(bounds, fillPaint)
            }

            PlayerBackgroundStyle.HALO -> {
                fillPaint.color = ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.68f))
                canvas.drawRect(bounds, fillPaint)
                fillPaint.shader = RadialGradient(
                        cx, cy, radius * 1.24f,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(primary, 128),
                                ColorUtils.setAlphaComponent(secondary, 46),
                                Color.TRANSPARENT),
                        floatArrayOf(0f, .48f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(bounds, fillPaint)
            }

            PlayerBackgroundStyle.AURORA -> {
                fillPaint.color = ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(1f))
                canvas.drawRect(bounds, fillPaint)
                fillPaint.shader = RadialGradient(
                        bounds.left + bounds.width() * .18f,
                        bounds.top + bounds.height() * .14f,
                        radius * 1.56f,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(primary, 122),
                                ColorUtils.setAlphaComponent(deep, 77),
                                Color.TRANSPARENT),
                        floatArrayOf(0f, .42f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(bounds, fillPaint)
                fillPaint.shader = RadialGradient(
                        bounds.left + bounds.width() * .88f,
                        bounds.top + bounds.height() * .72f,
                        radius * 1.44f,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(secondary, 97),
                                ColorUtils.setAlphaComponent(tertiary, 46),
                                Color.TRANSPARENT),
                        floatArrayOf(0f, .48f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(bounds, fillPaint)
                listOf(
                        Triple(.30f, .52f, primary),
                        Triple(.43f, .63f, secondary),
                        Triple(.56f, .72f, tertiary)
                ).forEachIndexed { index, (startY, endY, color) ->
                    val ribbon = Path().apply {
                        moveTo(bounds.left - bounds.width() * .14f,
                                bounds.top + bounds.height() * startY)
                        cubicTo(
                                bounds.left + bounds.width() * .18f,
                                bounds.top + bounds.height() * (startY - .24f + index * .025f),
                                bounds.left + bounds.width() * .58f,
                                bounds.top + bounds.height() * (endY + .18f - index * .02f),
                                bounds.left + bounds.width() * 1.14f,
                                bounds.top + bounds.height() * endY)
                    }
                    strokePaint.shader = LinearGradient(
                            bounds.left, cy, bounds.right, cy,
                            intArrayOf(color, primary, secondary), null, Shader.TileMode.CLAMP)
                    strokePaint.strokeWidth = radius * 2f * (.085f - index * .012f)
                    strokePaint.strokeCap = Paint.Cap.ROUND
                    strokePaint.alpha = ((.32f - index * .045f) * 255).toInt()
                    canvas.drawPath(ribbon, strokePaint)
                }
                strokePaint.shader = null
                strokePaint.alpha = 255
                fillPaint.shader = LinearGradient(
                        0f, bounds.top, 0f, bounds.bottom,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.06f)),
                                Color.TRANSPARENT,
                                ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.78f))),
                        floatArrayOf(0f, .62f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(bounds, fillPaint)
            }

            PlayerBackgroundStyle.SPECTRUM -> {
                fillPaint.color = ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.58f))
                canvas.drawRect(bounds, fillPaint)
                fillPaint.shader = LinearGradient(
                        0f, bounds.top, 0f, bounds.bottom,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(surfaceColor, alpha(.78f)),
                                ColorUtils.setAlphaComponent(deep, alpha(.90f)),
                                ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.88f))),
                        null, Shader.TileMode.CLAMP)
                canvas.drawRect(bounds, fillPaint)
            }

            PlayerBackgroundStyle.CORONA -> {
                // Color lives only in a soft ring hugging the rim - a wide stroked circle, not a
                // full-bleed fill - so the cover stays fully legible through its center and only
                // the border picks up the sweep's hues.
                fillPaint.color = ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.16f))
                canvas.drawRect(bounds, fillPaint)
                strokePaint.shader = SweepGradient(
                        cx, cy, intArrayOf(tertiary, primary, secondary, tertiary), null)
                strokePaint.strokeWidth = radius * .48f
                strokePaint.strokeCap = Paint.Cap.ROUND
                strokePaint.alpha = alpha(.58f)
                canvas.drawCircle(cx, cy, radius * .88f, strokePaint)
                strokePaint.shader = null
                strokePaint.alpha = 255
            }

            PlayerBackgroundStyle.DUSK -> {
                // No base fill at all - the fade itself is the only treatment, so the top of the
                // cover stays untouched and only the lower band darkens toward black.
                fillPaint.shader = LinearGradient(
                        0f, bounds.top, 0f, bounds.bottom,
                        intArrayOf(
                                Color.TRANSPARENT,
                                ColorUtils.setAlphaComponent(deep, alpha(.38f)),
                                ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.70f))),
                        floatArrayOf(0f, .60f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(bounds, fillPaint)
            }

            PlayerBackgroundStyle.BLOOM -> {
                fillPaint.color = ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.16f))
                canvas.drawRect(bounds, fillPaint)
                fillPaint.shader = RadialGradient(
                        bounds.left + bounds.width() * .22f, bounds.top + bounds.height() * .26f,
                        radius * 1.04f,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(primary, alpha(.38f)),
                                Color.TRANSPARENT),
                        floatArrayOf(0f, .85f), Shader.TileMode.CLAMP)
                canvas.drawRect(bounds, fillPaint)
                fillPaint.shader = RadialGradient(
                        bounds.left + bounds.width() * .80f, bounds.top + bounds.height() * .22f,
                        radius * .92f,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(secondary, alpha(.32f)),
                                Color.TRANSPARENT),
                        floatArrayOf(0f, .85f), Shader.TileMode.CLAMP)
                canvas.drawRect(bounds, fillPaint)
                fillPaint.shader = RadialGradient(
                        bounds.left + bounds.width() * .50f, bounds.top + bounds.height() * .88f,
                        radius * .96f,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(tertiary, alpha(.28f)),
                                Color.TRANSPARENT),
                        floatArrayOf(0f, .85f), Shader.TileMode.CLAMP)
                canvas.drawRect(bounds, fillPaint)
            }

            PlayerBackgroundStyle.HORIZON -> {
                fillPaint.shader = LinearGradient(
                        0f, bounds.top, 0f, bounds.bottom,
                        intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT,
                                ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.62f))),
                        floatArrayOf(0f, .72f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(bounds, fillPaint)
            }

            PlayerBackgroundStyle.EMBER -> {
                fillPaint.shader = RadialGradient(
                        bounds.left + bounds.width() * .82f, bounds.top + bounds.height() * .84f,
                        radius * .92f,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(primary, alpha(.40f)),
                                ColorUtils.setAlphaComponent(deep, alpha(.22f)),
                                Color.TRANSPARENT),
                        floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(bounds, fillPaint)
            }

            PlayerBackgroundStyle.ECLIPSE,
            PlayerBackgroundStyle.HIDDEN -> {
                fillPaint.shader = null
                fillPaint.color = Color.BLACK
                canvas.drawRect(bounds, fillPaint)
            }
        }
        fillPaint.shader = null
    }

    /** Mirrors PlayerShadingDrawable and the Compose PlayerShadingOverlay. */
    private fun drawPlayerShading(
            canvas: Canvas,
            bounds: RectF,
            cx: Float,
            cy: Float,
            radius: Float
    ) {
        if (!dimArt) return
        val style = if (playerShadingStyle == PlayerShadingStyle.FOLLOW) {
            if (!PlayerBackgroundStyle.fromPreference(artStyle).isPlainArtworkTreatment) return
            PlayerShadingStyle.BOTTOM_FADE
        } else {
            playerShadingStyle
        }
        val strength = playerShadingIntensity.coerceIn(0f, SHADING_MAX_MULTIPLIER)
        // Shading gradient colour (black by default; album/desaturated/custom resolve to a dark
        // tone). Mirrors the watch's PlayerShadingDrawable so a tinted shading previews correctly.
        val shadingRgb = resolvedShadingColor()
        fun shade(maxAlpha: Float) = ColorUtils.setAlphaComponent(
                shadingRgb, (255f * maxAlpha * strength).toInt().coerceIn(0, 255))
        fun darkTone(color: Int) = PaletteTransforms.shadingTone(color)

        fillPaint.shader = when (style) {
            PlayerShadingStyle.EDGE_VIGNETTE,
            PlayerShadingStyle.EDGE_VIGNETTE_STRONG,
            PlayerShadingStyle.EDGE_VIGNETTE_HEAVY -> {
                val baseStop = when (style) {
                    PlayerShadingStyle.EDGE_VIGNETTE_HEAVY -> 0.0f
                    PlayerShadingStyle.EDGE_VIGNETTE_STRONG -> 0.15f
                    else -> 0.46f
                }
                val radiusMult = when (style) {
                    PlayerShadingStyle.EDGE_VIGNETTE_HEAVY -> 0.95f
                    PlayerShadingStyle.EDGE_VIGNETTE_STRONG -> 1.05f
                    else -> 1.34f
                }
                val outerAlpha = when (style) {
                    PlayerShadingStyle.EDGE_VIGNETTE_HEAVY -> 1.0f
                    PlayerShadingStyle.EDGE_VIGNETTE_STRONG -> 1.0f
                    else -> 0.82f
                }
                RadialGradient(
                        cx, cy, radius * radiusMult,
                        intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, shade(outerAlpha)),
                        floatArrayOf(0f, baseStop, 1f), Shader.TileMode.CLAMP)
            }
            PlayerShadingStyle.BOTTOM_CORNER -> LinearGradient(
                    bounds.left, bounds.top, bounds.right, bounds.bottom,
                    intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, shade(.94f)),
                    floatArrayOf(0f, .42f, 1f), Shader.TileMode.CLAMP)
            PlayerShadingStyle.BOTTOM_FADE -> LinearGradient(
                    0f, bounds.top, 0f, bounds.bottom,
                    intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, shade(.94f)),
                    floatArrayOf(0f, .34f, 1f), Shader.TileMode.CLAMP)
            PlayerShadingStyle.FLOOR_CEILING -> LinearGradient(
                    0f, bounds.top, 0f, bounds.bottom,
                    intArrayOf(shade(.55f), Color.TRANSPARENT, Color.TRANSPARENT, shade(.88f)),
                    floatArrayOf(0f, .30f, .60f, 1f), Shader.TileMode.CLAMP)
            PlayerShadingStyle.DUOTONE -> LinearGradient(
                    bounds.left, bounds.top, bounds.right, bounds.bottom,
                    ColorUtils.setAlphaComponent(darkTone(albumAccent()),
                            (255f * .58f * strength).toInt().coerceIn(0, 255)),
                    ColorUtils.setAlphaComponent(darkTone(albumSecondaryAccent()),
                            (255f * .58f * strength).toInt().coerceIn(0, 255)),
                    Shader.TileMode.CLAMP)
            PlayerShadingStyle.SIDE_CURTAINS -> LinearGradient(
                    bounds.left, 0f, bounds.right, 0f,
                    intArrayOf(shade(.72f), Color.TRANSPARENT, Color.TRANSPARENT, shade(.72f)),
                    floatArrayOf(0f, .34f, .66f, 1f), Shader.TileMode.CLAMP)
            else -> null
        }
        fillPaint.color = when (style) {
            PlayerShadingStyle.FULL_FILTER -> shade(.55f)
            PlayerShadingStyle.ALBUM_TINT -> ColorUtils.setAlphaComponent(
                    darkTone(albumAccent()), (255f * .52f * strength).toInt().coerceIn(0, 255))
            else -> Color.WHITE
        }
        canvas.drawRect(bounds, fillPaint)
        fillPaint.shader = null
    }

    private fun drawFadeDemonstration(
            canvas: Canvas,
            first: Bitmap?,
            second: Bitmap?,
            bounds: RectF,
            grayscale: Boolean
    ) {
        val halfCycle = 1800L
        val cycle = previewAnimationTimeMillis() % (halfCycle * 2L)
        val reverse = cycle >= halfCycle
        val local = cycle % halfCycle
        val transition = if (albumArtFade) {
            (local / 550f).coerceIn(0f, 1f)
        } else {
            if (local < 180L) 0f else 1f
        }
        val from = if (reverse) second else first
        val to = if (reverse) first else second
        drawArtwork(canvas, from, bounds, 255, grayscale)
        drawArtwork(canvas, to, bounds, (transition * 255).toInt(), grayscale)
        transientAnimationActive = true
    }

    private fun drawArtwork(
            canvas: Canvas,
            bitmap: Bitmap?,
            bounds: RectF,
            alpha: Int,
            grayscale: Boolean = false
    ) {
        if (bitmap == null || bitmap.isRecycled || alpha <= 0) return
        val scale = max(bounds.width() / bitmap.width, bounds.height() / bitmap.height)
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(
                    bounds.centerX() - bitmap.width * scale / 2f,
                    bounds.centerY() - bitmap.height * scale / 2f
            )
        }
        bitmapPaint.alpha = alpha.coerceIn(0, 255)
        bitmapPaint.colorFilter = if (grayscale) grayscaleFilter else null
        canvas.drawBitmap(bitmap, matrix, bitmapPaint)
        bitmapPaint.alpha = 255
        bitmapPaint.colorFilter = null
    }

    /** Draws one face-owned artwork window. Unlike the player backdrop, this never expands the
     * cover beyond [bounds]: Ribbon's queue capsules and Frame's wide card must crop the same
     * current preview cover into their individual slots. */
    private fun drawClippedFaceArtwork(
            canvas: Canvas,
            bitmap: Bitmap?,
            bounds: RectF,
            cornerRadius: Float,
            alpha: Int = 255,
            grayscale: Boolean = false,
            fallbackColor: Int? = null
    ) {
        val saved = canvas.save()
        canvas.clipPath(Path().apply {
            addRoundRect(bounds, cornerRadius, cornerRadius, Path.Direction.CW)
        })
        if (bitmap != null) {
            drawArtwork(canvas, bitmap, bounds, alpha, grayscale)
        } else if (fallbackColor != null) {
            fillPaint.shader = null
            fillPaint.color = fallbackColor
            canvas.drawRect(bounds, fillPaint)
        }
        canvas.restoreToCount(saved)
    }

    /** A Square variant's sharp inset: the largest square that fits inside the round [bounds]
     *  without a corner being clipped by it (side = min(width, height) / sqrt(2)), contain-fit
     *  (the smaller of the two axis scales, never the larger) and rounded by
     *  [cornerRadiusFraction] of its own side - mirrors the watch's squareInsetOutlineProvider /
     *  applySquareInsetMatrix. Contain, not cover: the whole point of this style is that [bitmap]
     *  is never cropped, only letterboxed within the square if it isn't already one - the blurred
     *  backdrop callers are expected to have already painted via [drawArtwork] shows through any
     *  gap. */
    private fun drawSquareInsetArtwork(
            canvas: Canvas, bitmap: Bitmap?, bounds: RectF, cornerRadiusFraction: Float
    ) {
        if (bitmap == null || bitmap.isRecycled) return
        val side = minOf(bounds.width(), bounds.height()) / sqrt(2f)
        val insetLeft = bounds.centerX() - side / 2f
        val insetTop = bounds.centerY() - side / 2f
        val insetRect = RectF(insetLeft, insetTop, insetLeft + side, insetTop + side)

        val scale = min(insetRect.width() / bitmap.width, insetRect.height() / bitmap.height)
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(
                    insetRect.centerX() - bitmap.width * scale / 2f,
                    insetRect.centerY() - bitmap.height * scale / 2f
            )
        }

        canvas.save()
        val cornerRadius = side * cornerRadiusFraction
        val clipPath = Path().apply {
            addRoundRect(insetRect, cornerRadius, cornerRadius, Path.Direction.CW)
        }
        canvas.clipPath(clipPath)
        canvas.drawBitmap(bitmap, matrix, bitmapPaint)
        canvas.restore()
    }

    // --- Contextual always-on preview ---

    private fun effectiveAodStyle(): String = when (aodStyle) {
        "follow" -> if (face in setOf(
                "expressive", "vinyl", "poster", "studio", "halo", "aurora", "eclipse", "spectrum", "material", "immersive", "depth", "carousel", "chat", "split", "note", "verse", "metadata", "ribbon", "frame"
        )) face else "classic"
        // Removed style; a stored value falls back to classic (matches the watch).
        "minimal" -> "classic"
        else -> aodStyle
    }

    private fun resolvedAodTint(): Int = when (aodColorMode) {
        "album" -> accentForText(albumAccent())
        "custom" -> accentForText(parseHexOrNull(aodCustomColor) ?: Color.WHITE)
        else -> Color.WHITE
    }

    private fun ambientColor(color: Int, multiplier: Float = 1f): Int =
            ColorUtils.setAlphaComponent(
                    color,
                    (255f * (aodIntensity / 100f) * multiplier).toInt().coerceIn(0, 255)
            )

    private fun drawAodSurface(
            canvas: Canvas,
            geometry: PreviewGeometry,
            dp: (Float) -> Float
    ) {
        val style = effectiveAodStyle()
        fillPaint.shader = null
        fillPaint.color = Color.BLACK
        canvas.drawRect(geometry.bounds, fillPaint)

        val artwork = resolveAodArtwork(
                showArtwork = aodShowArt,
                effectiveAodStyle = style,
                treatment = aodArtTreatment,
                playerArtworkStyle = artStyle
        )
        // These faces use the AOD artwork only inside their own cover slots. A full-screen
        // backdrop would erase the black negative space that defines both compositions.
        if (artwork.visible && style !in setOf("ribbon", "frame")) {
            drawArtwork(
                    canvas,
                    if (artwork.blurred) {
                        displayedBlurredArt()
                    } else {
                        displayedArt()
                    },
                    geometry.bounds,
                    ambientArtOpacity * 255 / 100,
                    artwork.monochrome
            )
        }

        when (style) {
            "expressive" -> drawExpressiveAod(canvas, geometry, dp)
            "ribbon" -> drawRibbonAod(canvas, geometry, dp)
            "frame" -> drawFrameAod(canvas, geometry, dp)
            "vinyl", "poster", "studio", "halo", "aurora", "eclipse", "spectrum", "material", "immersive", "depth", "carousel", "chat", "split", "note", "verse", "metadata" ->
                drawCuratedAod(canvas, geometry, dp)
            "chrono" -> drawChronoAod(canvas, geometry, dp)
            else -> drawClassicAod(canvas, geometry, dp, minimal = false)
        }

        // Chrono draws its own big clock; the small top clock is skipped for it.
        if (aodShowClock && style !in setOf("chrono", "ribbon", "frame")) {
            drawAmbientClock(canvas, geometry.cx, geometry.bounds.top + dp(20f), dp)
        }
    }

    /** Ambient Ribbon retains the awake face's rails and cover window, but moves them to the
     * selected low-power tint. The optional art remains confined to the cards rather than becoming
     * a full-screen backdrop. */
    private fun drawRibbonAod(
            canvas: Canvas,
            geometry: PreviewGeometry,
            dp: (Float) -> Float
    ) {
        val screen = geometry.radius * 2f
        val screenLeft = geometry.cx - geometry.radius
        val screenTop = geometry.cy - geometry.radius
        val tint = resolvedAodTint()
        val artwork = resolveAodArtwork(
                showArtwork = aodShowArt,
                effectiveAodStyle = "ribbon",
                treatment = aodArtTreatment,
                playerArtworkStyle = artStyle
        )
        val art = if (artwork.blurred) displayedBlurredArt() else displayedArt()
        val columnWidth = screen * FaceGeometry.Ribbon.COLUMN_WIDTH_FRACTION
        val columnHeight = screen * FaceGeometry.Ribbon.COLUMN_HEIGHT_FRACTION
        val columnTop = screenTop + screen * FaceGeometry.Ribbon.COLUMN_TOP_FRACTION
        val columnCorner = min(columnWidth, columnHeight) *
                FaceGeometry.Ribbon.COLUMN_CORNER_FRACTION
        val outerCenter = screenLeft + screen * FaceGeometry.Ribbon.OUTER_COLUMN_CENTER_X
        val innerCenter = screenLeft + screen * FaceGeometry.Ribbon.INNER_COLUMN_CENTER_X
        val columns = listOf(outerCenter, innerCenter, screenLeft + screen - innerCenter + screenLeft,
                screenLeft + screen - outerCenter + screenLeft).map { centerX ->
            RectF(
                    centerX - columnWidth / 2f,
                    columnTop,
                    centerX + columnWidth / 2f,
                    columnTop + columnHeight
            )
        }

        val coverWidth = screen * FaceGeometry.Ribbon.CENTER_COVER_WIDTH_FRACTION
        val coverHeight = screen * FaceGeometry.Ribbon.CENTER_COVER_HEIGHT_FRACTION
        val coverTop = screenTop + screen * FaceGeometry.Ribbon.CENTER_COVER_TOP_FRACTION
        val cover = RectF(
                geometry.cx - coverWidth / 2f,
                coverTop,
                geometry.cx + coverWidth / 2f,
                coverTop + coverHeight
        )
        val coverCorner = min(coverWidth, coverHeight) *
                FaceGeometry.Ribbon.CENTER_COVER_CORNER_FRACTION

        if (artwork.visible) {
            columns.forEach { column ->
                drawClippedFaceArtwork(
                        canvas, art, column, columnCorner,
                        alpha = (ambientArtOpacity * 255 / 100f * .62f).roundToInt(),
                        grayscale = artwork.monochrome
                )
            }
            drawClippedFaceArtwork(
                    canvas, art, cover, coverCorner,
                    alpha = ambientArtOpacity * 255 / 100,
                    grayscale = artwork.monochrome
            )
        }

        configureAmbientStroke(dp, 1.15f)
        strokePaint.color = ambientColor(tint, .46f)
        columns.forEach { column ->
            canvas.drawRoundRect(column, columnCorner, columnCorner, strokePaint)
        }
        strokePaint.color = ambientColor(tint, .64f)
        canvas.drawRoundRect(cover, coverCorner, coverCorner, strokePaint)

        textPaint.style = Paint.Style.FILL
        textPaint.textAlign = Paint.Align.CENTER
        if (aodShowTrackInfo && (showTrackArtist || !isPlayingShown())) {
            val artist = if (isPlayingShown()) displayArtist()
            else context.getString(R.string.preview_playback_stopped)
            // Match Ribbon's awake metadata: its leading mark is the streaming-app glyph rather
            // than a literal @. The glyph is naturally absent when the player did not provide one.
            drawArtistLine(
                    canvas,
                    artist,
                    geometry.cx,
                    screenTop + screen * FaceGeometry.Ribbon.ARTIST_BASELINE_FRACTION,
                    screen * .76f,
                    ambientColor(tint, .72f),
                    dp(11f),
                    sourceGlyph = isPlayingShown() && sourceGlyphTintable,
                    glyphSize = dp(11f)
            )
        }
        if (aodShowTrackInfo && showTrackTitle) {
            textPaint.typeface = titleTypeface(bold = true)
            textPaint.textSize = titleTypographySpec.scaled(dp(16f))
            drawAmbientOutlinedText(
                    canvas,
                    ellipsize(displayTitle(), screen * .78f),
                    geometry.cx,
                    screenTop + screen * FaceGeometry.Ribbon.TITLE_BASELINE_FRACTION,
                    ambientColor(tint, .86f),
                    dp
            )
        }
        if (aodShowClock) {
            textPaint.typeface = clockTypeface(clockTypographySpec)
            textPaint.textSize = dp(clockTypographySpec.scaled(15f))
            textPaint.letterSpacing = clockTypographySpec.trackingEm
            drawAmbientOutlinedText(
                    canvas,
                    previewClockText(),
                    geometry.cx,
                    screenTop + screen * FaceGeometry.Ribbon.CLOCK_BASELINE_FRACTION,
                    ambientColor(tint, .78f),
                    dp
            )
        }
        textPaint.letterSpacing = 0f
    }

    /** Ambient Frame keeps the card's hierarchy in outline form. Its art toggle affects only the
     * wide image well, exactly as the awake face treats artwork as card content. */
    private fun drawFrameAod(
            canvas: Canvas,
            geometry: PreviewGeometry,
            dp: (Float) -> Float
    ) {
        val screen = geometry.radius * 2f
        val screenTop = geometry.cy - geometry.radius
        val screenLeft = geometry.cx - geometry.radius
        val tint = resolvedAodTint()
        val card = RectF(
                screenLeft + screen * FaceGeometry.Frame.CARD_INSET_FRACTION,
                screenTop + screen * FaceGeometry.Frame.CARD_TOP_FRACTION,
                screenLeft + screen * (1f - FaceGeometry.Frame.CARD_INSET_FRACTION),
                screenTop + screen * FaceGeometry.Frame.CARD_BOTTOM_FRACTION
        )
        val cardCorner = min(card.width(), card.height()) * FaceGeometry.Frame.CARD_CORNER_FRACTION
        val contentInset = screen * FaceGeometry.Frame.CONTENT_INSET_FRACTION
        val art = RectF(
                card.left + contentInset,
                screenTop + screen * FaceGeometry.Frame.ARTWORK_TOP_FRACTION,
                card.right - contentInset,
                screenTop + screen * FaceGeometry.Frame.ARTWORK_BOTTOM_FRACTION
        )
        val artCorner = screen * FaceGeometry.Frame.artworkCornerFraction()
        val artwork = resolveAodArtwork(
                showArtwork = aodShowArt,
                effectiveAodStyle = "frame",
                treatment = aodArtTreatment,
                playerArtworkStyle = artStyle
        )
        if (artwork.visible) {
            drawClippedFaceArtwork(
                    canvas,
                    if (artwork.blurred) displayedBlurredArt() else displayedArt(),
                    art,
                    artCorner,
                    alpha = ambientArtOpacity * 255 / 100,
                    grayscale = artwork.monochrome
            )
        }

        configureAmbientStroke(dp, 1.15f)
        strokePaint.color = ambientColor(tint, .58f)
        canvas.drawRoundRect(card, cardCorner, cardCorner, strokePaint)
        strokePaint.color = ambientColor(tint, .48f)
        canvas.drawRoundRect(art, artCorner, artCorner, strokePaint)

        textPaint.style = Paint.Style.FILL
        textPaint.textAlign = Paint.Align.LEFT
        if (aodShowTrackInfo && (showTrackArtist || !isPlayingShown())) {
            val artist = if (isPlayingShown()) displayArtist()
            else context.getString(R.string.preview_playback_stopped)
            textPaint.typeface = artistTypeface(bold = true)
            textPaint.textSize = artistTypographySpec.scaled(dp(10f))
            textPaint.letterSpacing = artistTypographySpec.trackingEm
            val chipPadding = dp(7f)
            val maxChipWidth = card.width() - contentInset * 2f
            // Ambient only carries a template source glyph, mirroring AmbientSourceIconGlyph on
            // the watch; a full-colour launcher icon stays out of the low-power composition.
            val sourceDiameter = if (isPlayingShown() && showSourceIcon && sourceGlyph != null &&
                    sourceGlyphTintable) {
                dp(10f) * sourceIconTypographySpec.scale
            } else {
                0f
            }
            val sourceGap = if (sourceDiameter > 0f) sourceDiameter * .33f else 0f
            val label = ellipsize(
                    artist,
                    (maxChipWidth - chipPadding * 2f - sourceDiameter - sourceGap)
                            .coerceAtLeast(1f))
            val chipHeight = maxOf(
                    dp(19f),
                    textPaint.fontMetrics.descent - textPaint.fontMetrics.ascent + dp(7f)
            )
            val chipTop = screenTop + screen * FaceGeometry.Frame.ART_TOP_FRACTION
            val chipWidth = (textPaint.measureText(label) + sourceDiameter + sourceGap +
                    chipPadding * 2f)
                    .coerceAtMost(maxChipWidth)
            val chip = RectF(card.left + contentInset, chipTop,
                    card.left + contentInset + chipWidth, chipTop + chipHeight)
            strokePaint.color = ambientColor(tint, .60f)
            canvas.drawRoundRect(chip, chipHeight / 2f, chipHeight / 2f, strokePaint)
            drawArtistLine(
                    canvas,
                    label,
                    chip.left + chipPadding,
                    chip.centerY() - (textPaint.fontMetrics.ascent + textPaint.fontMetrics.descent) / 2f,
                    (chip.width() - chipPadding * 2f).coerceAtLeast(1f),
                    ambientColor(tint, .78f),
                    dp(10f),
                    bold = true,
                    align = Paint.Align.LEFT,
                    sourceGlyph = sourceDiameter > 0f,
                    glyphSize = dp(10f)
            )
        }
        if (aodShowTrackInfo && showTrackTitle) {
            textPaint.typeface = titleTypeface(bold = true)
            val titleSize = titleTypographySpec.scaled(dp(18f))
            textPaint.textSize = titleSize
            val titleTop = screenTop + screen * FaceGeometry.Frame.TITLE_TOP_FRACTION
            drawAmbientOutlinedText(
                    canvas,
                    ellipsize(displayTitle(), card.width() - contentInset * 2f),
                    card.left + contentInset,
                    titleTop - textPaint.fontMetrics.ascent,
                    ambientColor(tint, .88f),
                    dp
            )
        }
        if (aodShowClock) {
            textPaint.typeface = clockTypeface(clockTypographySpec)
            textPaint.textSize = dp(clockTypographySpec.scaled(13f))
            textPaint.letterSpacing = clockTypographySpec.trackingEm
            textPaint.textAlign = Paint.Align.CENTER
            drawAmbientOutlinedText(
                    canvas,
                    previewClockText(),
                    geometry.cx,
                    screenTop + screen * FaceGeometry.Frame.CARD_TOP_FRACTION / 2f,
                    ambientColor(tint, .72f),
                    dp
            )
        }
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.letterSpacing = 0f
    }

    private fun drawAmbientClock(
            canvas: Canvas,
            x: Float,
            baseline: Float,
            dp: (Float) -> Float
    ) {
        val time = previewClockText()
        textPaint.style = Paint.Style.FILL
        textPaint.typeface = fontRegular
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = dp(13f)
        textPaint.color = ambientColor(resolvedAodTint(), .60f)
        canvas.drawText(time, x, baseline, textPaint)
    }

    /** The "chrono" AOD: a large centred clock on black with only the track title beneath it.
     *  Mirrors applyAmbientPresentation's chrono branch on the watch. */
    private fun drawChronoAod(canvas: Canvas, geometry: PreviewGeometry, dp: (Float) -> Float) {
        // Mirrors ChronoAmbientFace on the watch: a large clock hero, then a smaller title, then
        // the artist (with the app glyph), as a centred column on black.
        textPaint.textAlign = Paint.Align.CENTER
        if (aodShowClock) {
            val time = previewClockText()
            textPaint.style = Paint.Style.FILL
            textPaint.typeface = clockTypeface()
            textPaint.textSize = dp(40f)
            textPaint.color = ambientColor(resolvedAodTint(), .92f)
            val fm = textPaint.fontMetrics
            canvas.drawText(time, geometry.cx, geometry.cy - dp(8f) - (fm.ascent + fm.descent) / 2f, textPaint)
        }
        if (aodShowTrackInfo && showTrackTitle) {
            textPaint.typeface = titleTypeface(bold = true)
            textPaint.textSize = dp(13f)
            drawAmbientOutlinedText(
                    canvas,
                    ellipsize(displayTitle(), geometry.radius * 1.5f),
                    geometry.cx,
                    geometry.cy + dp(26f),
                    ambientColor(resolvedAodTint(), .80f),
                    dp
            )
        }
        if (aodShowTrackInfo && showTrackArtist) {
            textPaint.typeface = artistTypeface(bold = false)
            textPaint.textSize = dp(10f)
            drawAmbientOutlinedText(
                    canvas,
                    ellipsize(displayArtist(), geometry.radius * 1.5f),
                    geometry.cx,
                    geometry.cy + dp(42f),
                    ambientColor(resolvedAodTint(), .55f),
                    dp
            )
        }
    }

    private fun drawClassicAod(
            canvas: Canvas,
            geometry: PreviewGeometry,
            dp: (Float) -> Float,
            minimal: Boolean
    ) {
        if (!aodShowTrackInfo) return
        val alphaMultiplier = if (minimal) 0.6f else 1f
        val tint = ambientColor(resolvedAodTint(), alphaMultiplier)
        val artist = ambientColor(resolvedAodTint(), 0.55f * alphaMultiplier)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = artistTypeface(bold = true)
        textPaint.textSize = dp(14f)
        drawAmbientOutlinedText(
                canvas,
                ellipsize(displayArtist(), geometry.radius * 1.55f),
                geometry.cx,
                geometry.cy - dp(17f),
                artist,
                dp
        )
        textPaint.typeface = titleTypeface(bold = true)
        textPaint.textSize = dp(32f)
        drawAmbientOutlinedText(
                canvas,
                ellipsize(displayTitle(), geometry.radius * 1.55f),
                geometry.cx,
                geometry.cy + dp(18f),
                tint,
                dp
        )
    }

    private fun drawAmbientTrackInfo(
            canvas: Canvas,
            geometry: PreviewGeometry,
            dp: (Float) -> Float
    ) {
        if (!aodShowTrackInfo) return
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = titleTypeface(bold = true)
        textPaint.textSize = dp(14f)
        drawAmbientOutlinedText(
                canvas,
                ellipsize(displayTitle(), geometry.radius * 1.45f),
                geometry.cx,
                geometry.bounds.top + dp(43f),
                ambientColor(resolvedAodTint()),
                dp
        )
        textPaint.typeface = artistTypeface(bold = false)
        textPaint.textSize = dp(10f)
        drawAmbientOutlinedText(
                canvas,
                ellipsize(displayArtist(), geometry.radius * 1.45f),
                geometry.cx,
                geometry.bounds.top + dp(57f),
                ambientColor(resolvedAodTint(), 0.55f),
                dp
        )
    }

    private fun drawAmbientOutlinedText(
            canvas: Canvas,
            text: String,
            x: Float,
            baseline: Float,
            color: Int,
            dp: (Float) -> Float
    ) {
        textPaint.style = Paint.Style.STROKE
        textPaint.strokeWidth = dp(1.1f)
        textPaint.color = ColorUtils.setAlphaComponent(Color.BLACK, Color.alpha(color))
        canvas.drawText(text, x, baseline, textPaint)
        textPaint.style = Paint.Style.FILL
        textPaint.color = color
        canvas.drawText(text, x, baseline, textPaint)
    }

    private fun configureAmbientStroke(dp: (Float) -> Float, widthDp: Float = 1.6f) {
        strokePaint.shader = null
        strokePaint.pathEffect = null
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = dp(widthDp)
        strokePaint.strokeCap = Paint.Cap.ROUND
        strokePaint.color = ambientColor(resolvedAodTint())
    }

    /** AOD deliberately reuses the universal progress-style preference. This keeps Material and
     * the curated ambient ring in lockstep with the interactive bezel without adding a second,
     * easily-divergent picker. */
    private fun drawAmbientStyledProgressArc(
            canvas: Canvas,
            arc: RectF,
            startAngle: Float,
            totalSweep: Float,
            progress: Float,
            baseWidth: Float,
            trackColor: Int,
            playedColor: Int
    ) {
        val playedFraction = progress.coerceIn(0f, 1f)
        when (progressStyle) {
            "watch_dots_60" -> {
                drawAmbientWatchMarks(
                        canvas, arc, startAngle, totalSweep, playedFraction,
                        fullCircleCount = 60, kind = "dots", baseWidth, trackColor, playedColor)
                return
            }
            "watch_ticks_60" -> {
                drawAmbientWatchMarks(
                        canvas, arc, startAngle, totalSweep, playedFraction,
                        fullCircleCount = 60, kind = "ticks", baseWidth, trackColor, playedColor)
                return
            }
            "hour_segments_12" -> {
                drawAmbientWatchMarks(
                        canvas, arc, startAngle, totalSweep, playedFraction,
                        fullCircleCount = 12, kind = "hours", baseWidth, trackColor, playedColor)
                return
            }
        }

        strokePaint.shader = null
        strokePaint.pathEffect = when (progressStyle) {
            "dashed" -> DashPathEffect(
                    floatArrayOf(baseWidth * 1.9f, baseWidth * 1.5f), 0f)
            "dots" -> DashPathEffect(
                    floatArrayOf(0.01f, baseWidth * 2.6f), 0f)
            else -> null
        }
        val cap = when (progressStyle) {
            "dashed" -> Paint.Cap.BUTT
            else -> Paint.Cap.ROUND
        }
        val trackWidth = when (progressStyle) {
            "hairline" -> baseWidth * .30f
            "comet" -> baseWidth * .50f
            else -> baseWidth
        }
        val playedWidth = if (progressStyle == "hairline") baseWidth * .45f else baseWidth

        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeCap = cap
        strokePaint.strokeWidth = trackWidth
        strokePaint.color = trackColor
        canvas.drawArc(arc, startAngle, totalSweep, false, strokePaint)

        val playedSweep = totalSweep * playedFraction
        if (playedSweep > 0f) {
            strokePaint.strokeWidth = playedWidth
            strokePaint.color = playedColor
            canvas.drawArc(arc, startAngle, playedSweep, false, strokePaint)
            if (progressStyle == "comet") {
                val angle = Math.toRadians((startAngle + playedSweep).toDouble())
                fillPaint.shader = null
                fillPaint.color = playedColor
                canvas.drawCircle(
                        arc.centerX() + arc.width() / 2f * cos(angle).toFloat(),
                        arc.centerY() + arc.height() / 2f * sin(angle).toFloat(),
                        baseWidth * .9f,
                        fillPaint
                )
            }
        }
        strokePaint.pathEffect = null
    }

    /** Exact dial marks for the watch-inspired styles. Partial rings keep the same density as a
     * full 60/12-mark dial, and mark color itself conveys played vs remaining progress. */
    private fun drawAmbientWatchMarks(
            canvas: Canvas,
            arc: RectF,
            startAngle: Float,
            totalSweep: Float,
            progress: Float,
            fullCircleCount: Int,
            kind: String,
            baseWidth: Float,
            trackColor: Int,
            playedColor: Int
    ) {
        val count = (fullCircleCount * kotlin.math.abs(totalSweep) / 360f)
                .roundToInt().coerceAtLeast(1)
        val closesCircle = kotlin.math.abs(totalSweep) >= 359.5f
        strokePaint.shader = null
        strokePaint.pathEffect = null
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeCap = Paint.Cap.ROUND
        fillPaint.shader = null
        val radius = min(arc.width(), arc.height()) / 2f
        repeat(count) { index ->
            val fraction = if (closesCircle) {
                index.toFloat() / count
            } else if (count == 1) {
                0f
            } else {
                index.toFloat() / (count - 1)
            }
            val angle = Math.toRadians((startAngle + totalSweep * fraction).toDouble())
            val cosAngle = cos(angle).toFloat()
            val sinAngle = sin(angle).toFloat()
            val color = if (fraction <= progress) playedColor else trackColor
            val dialIndex = (fraction * fullCircleCount).roundToInt()
            val major = fullCircleCount == 60 && dialIndex % 5 == 0
            when (kind) {
                "dots" -> {
                    fillPaint.color = color
                    canvas.drawCircle(
                            arc.centerX() + radius * cosAngle,
                            arc.centerY() + radius * sinAngle,
                            baseWidth * if (major) .60f else .38f,
                            fillPaint
                    )
                }
                else -> {
                    val length = when (kind) {
                        "hours" -> baseWidth * 2.2f
                        else -> baseWidth * if (major) 1.7f else .9f
                    }
                    strokePaint.color = color
                    strokePaint.strokeWidth = when (kind) {
                        "hours" -> baseWidth * .70f
                        else -> baseWidth * if (major) .52f else .30f
                    }
                    canvas.drawLine(
                            arc.centerX() + (radius - length) * cosAngle,
                            arc.centerY() + (radius - length) * sinAngle,
                            arc.centerX() + radius * cosAngle,
                            arc.centerY() + radius * sinAngle,
                            strokePaint
                    )
                }
            }
        }
    }

    private fun drawExpressiveAod(
            canvas: Canvas,
            geometry: PreviewGeometry,
            dp: (Float) -> Float
    ) {
        drawAmbientTrackInfo(canvas, geometry, dp)
        if (aodShowTransport) {
            configureAmbientStroke(dp)
            val largeScreen = min(deviceWidthDp, deviceHeightDp) >= 225f
            val sideWidth = dp(if (largeScreen) 48f else 42f)
            val sideHeight = dp(if (largeScreen) 58f else 50f)
            val ringBox = dp(if (largeScreen) 78f else 62f)
            val cookieSize = dp(if (largeScreen) 62f else 48f)
            val sideCorner = sideWidth / 2f
            val sideOffset = ringBox / 2f + dp(4f) + sideWidth / 2f
            for (side in intArrayOf(-1, 1)) {
                val bcx = geometry.cx + side * sideOffset
                canvas.drawRoundRect(bcx - sideWidth / 2f, geometry.cy - sideHeight / 2f,
                        bcx + sideWidth / 2f, geometry.cy + sideHeight / 2f,
                        sideCorner, sideCorner, strokePaint)
            }

            val cookieStroke = dp(1.5f)
            val cookieModulation = if (isPlayingShown()) COOKIE_MODULATION else 0f
            val cookieRadius = (cookieSize / 2f - cookieStroke) / (1f + COOKIE_MODULATION)
            strokePaint.strokeWidth = cookieStroke
            canvas.drawPath(
                    contourPath(
                            geometry.cx, geometry.cy, cookieRadius, cookieModulation, 0f, 360f
                    ).apply { close() },
                    strokePaint
            )
            drawActionIcon(
                    canvas, quadrantIcons[ScreenQuadrant.LEFT],
                    commonR.drawable.action_skip_prev, geometry.cx - sideOffset, geometry.cy,
                    sideHeight * .5f, ambientColor(resolvedAodTint()), forceTint = true
            )
            drawActionIcon(
                    canvas, quadrantIcons[ScreenQuadrant.RIGHT],
                    commonR.drawable.action_skip_next, geometry.cx + sideOffset, geometry.cy,
                    sideHeight * .5f, ambientColor(resolvedAodTint()), forceTint = true
            )
            drawIcon(canvas,
                    if (isPlayingShown()) commonR.drawable.action_pause_expressive
                    else commonR.drawable.action_play,
                    geometry.cx, geometry.cy, cookieSize * .48f,
                    ambientColor(resolvedAodTint()))
            if (aodShowProgress) {
                val ringStroke = dp(2f)
                val ringModulation = if (isPlayingShown()) RING_MODULATION else 0f
                val ringRadius = (ringBox / 2f - ringStroke * 2f) / (1f + RING_MODULATION)
                val sweep = progressFraction() * 360f
                val halfGap = RING_GAP_DEGREES / 2f
                strokePaint.strokeWidth = ringStroke
                if (sweep + halfGap < 360f) {
                    strokePaint.color = ColorUtils.setAlphaComponent(
                            ambientColor(resolvedAodTint()), 72)
                    canvas.drawPath(
                            contourPath(
                                    geometry.cx, geometry.cy, ringRadius, ringModulation,
                                    sweep + halfGap, 360f
                            ),
                            strokePaint
                    )
                }
                if (sweep > halfGap) {
                    strokePaint.color = ambientColor(resolvedAodTint())
                    canvas.drawPath(
                            contourPath(
                                    geometry.cx, geometry.cy, ringRadius, ringModulation,
                                    0f, sweep - halfGap
                            ),
                            strokePaint
                    )
                }
            }
        }
        drawAmbientUpNextPill(canvas, geometry, dp)
    }

    /** Burn-in-conscious AOD shared by the curated collection: static track info and a thin
     * partial progress arc. Poster and Studio intentionally omit the central playback glyph and
     * its button outline; Eclipse remains true black and other layouts may retain dim artwork. */
    private fun drawCuratedAod(
            canvas: Canvas,
            geometry: PreviewGeometry,
            dp: (Float) -> Float
    ) {
        val tint = resolvedAodTint()
        val intensity = aodIntensity / 100f
        val cx = geometry.cx
        val cy = geometry.cy
        val radius = geometry.radius
        val style = effectiveAodStyle()
        // Matches CuratedAmbientFace on the watch: Poster/Studio and the AMOLED-identity
        // Eclipse/Immersive centre their AOD metadata AND skip the centre play/pause glyph.
        val centeredMetadata = style in setOf("poster", "studio", "eclipse", "immersive", "depth")

        if (aodShowTrackInfo) {
            textPaint.typeface = titleTypeface(bold = true)
            // Poster is the one curated layout whose ambient title is set larger (17sp against
            // 15sp everywhere else); the preview used to draw every face at Poster's size.
            textPaint.textSize = dp(if (style == "poster") 17f else 15f)
            textPaint.color = ambientColor(tint, .82f)
            val titleWidth = radius * 1.25f
            var titleBaseline = if (centeredMetadata) cy - dp(3f) else cy - radius + dp(54f)
            var artistBaseline = if (centeredMetadata) cy + dp(14f) else cy - radius + dp(68f)
            // CuratedAmbientFace allows the centred layouts two lines and the top-anchored ones
            // one. Forcing a single line everywhere hid how much of a long title the AOD keeps.
            val titleLines = if (centeredMetadata) {
                splitLines(displayTitle(), titleWidth, 2)
            } else {
                listOf(displayTitle())
            }
            if (titleLines.size > 1) {
                // The metadata column is centred on the watch, so a second line grows the block
                // in both directions rather than pushing the artist off the bottom.
                val fm = textPaint.fontMetrics
                val half = (fm.descent - fm.ascent) / 2f
                titleBaseline -= half
                artistBaseline += half
            }
            titleLines.forEachIndexed { index, line ->
                val fm = textPaint.fontMetrics
                canvas.drawText(
                        ellipsize(line, titleWidth), cx,
                        titleBaseline + index * (fm.descent - fm.ascent), textPaint)
            }
            textPaint.typeface = artistTypeface(bold = false)
            textPaint.textSize = dp(10f)
            textPaint.color = ambientColor(tint, .48f)
            canvas.drawText(ellipsize(
                    if (isPlayingShown()) displayArtist()
                    else context.getString(R.string.preview_playback_stopped),
                    radius * 1.20f
            ), cx, artistBaseline, textPaint)
        }

        if (style == "material") {
            if (aodShowTransport) {
                val skipOffset = radius * 0.54f
                val skipSize = dp(30f)
                drawActionIcon(
                        canvas, quadrantIcons[ScreenQuadrant.LEFT],
                        commonR.drawable.action_skip_prev, cx - skipOffset, cy, skipSize, tint,
                        (128f * intensity).toInt().coerceIn(0, 255), forceTint = true
                )

                val centerCircleR = radius * 0.30f
                val progressRect = RectF(
                        cx - centerCircleR, cy - centerCircleR,
                        cx + centerCircleR, cy + centerCircleR
                )
                if (aodShowProgress) {
                    drawAmbientStyledProgressArc(
                            canvas = canvas,
                            arc = progressRect,
                            startAngle = -90f,
                            totalSweep = 360f,
                            progress = progressFraction(),
                            baseWidth = dp(2f),
                            trackColor = ColorUtils.setAlphaComponent(
                                    tint, (102f * intensity).toInt().coerceIn(0, 255)),
                            playedColor = ColorUtils.setAlphaComponent(
                                    tint, (184f * intensity).toInt().coerceIn(0, 255))
                    )
                } else {
                    strokePaint.pathEffect = null
                    strokePaint.shader = null
                    strokePaint.style = Paint.Style.STROKE
                    strokePaint.strokeCap = Paint.Cap.ROUND
                    strokePaint.color = ColorUtils.setAlphaComponent(
                            tint, (102f * intensity).toInt().coerceIn(0, 255))
                    strokePaint.strokeWidth = dp(2f)
                    canvas.drawCircle(cx, cy, centerCircleR, strokePaint)
                }

                drawIcon(canvas,
                        if (isPlayingShown()) commonR.drawable.action_pause_filled else commonR.drawable.action_play_filled,
                        cx, cy, if (isPlayingShown()) dp(24f) else dp(28f), tint, (168f * intensity).toInt().coerceIn(0, 255))

                drawActionIcon(
                        canvas, quadrantIcons[ScreenQuadrant.RIGHT],
                        commonR.drawable.action_skip_next, cx + skipOffset, cy, skipSize, tint,
                        (128f * intensity).toInt().coerceIn(0, 255), forceTint = true
                )
            }
        } else {
            if (aodShowTransport && aodShowProgress) {
                val inset = radius * .32f
                val arc = RectF(cx - radius + inset, cy - radius + inset,
                        cx + radius - inset, cy + radius - inset)
                drawAmbientStyledProgressArc(
                        canvas = canvas,
                        arc = arc,
                        startAngle = 130f,
                        totalSweep = 280f,
                        progress = progressFraction(),
                        baseWidth = dp(1.5f),
                        trackColor = ColorUtils.setAlphaComponent(
                                tint, (46f * intensity).toInt().coerceIn(0, 255)),
                        playedColor = ColorUtils.setAlphaComponent(
                                tint, (173f * intensity).toInt().coerceIn(0, 255))
                )
            }

            // Eclipse/Immersive are already excluded via centeredMetadata above, matching the
            // watch, so this centre play/pause only draws for the remaining curated faces.
            if (aodShowTransport && !centeredMetadata) {
                strokePaint.color = ColorUtils.setAlphaComponent(tint, (107f * intensity).toInt())
                strokePaint.strokeWidth = dp(1.5f)
                canvas.drawCircle(cx, cy, dp(23f), strokePaint)
                drawIcon(canvas,
                        if (isPlayingShown()) commonR.drawable.action_pause else commonR.drawable.action_play,
                        cx, cy, dp(16f), tint, (168f * intensity).toInt().coerceIn(0, 255))
            }
        }
        // Every curated face offers the pill, matching CuratedPlayerFaces: the non-Material ambient
        // ring stops at 50deg and leaves the bottom of the dial empty, which is where it sits.
        drawAmbientUpNextPill(canvas, geometry, dp)
    }

    /** Static, non-clickable queue preview matching AmbientUpNextPill on the watch. */
    private fun drawAmbientUpNextPill(
            canvas: Canvas,
            geometry: PreviewGeometry,
            dp: (Float) -> Float
    ) {
        if (!aodShowPills) return
        val diameter = geometry.radius * 2f
        val width = diameter * .88f
        val height = (diameter * .25f).coerceIn(dp(44f), dp(52f))
        // Keep the phone preview aligned with the watch: raise the unchanged row into the usable
        // round-screen band while retaining clearance from Material/Expressive center controls.
        val centerY = geometry.bounds.bottom - diameter * .06f - height / 2f
        val bounds = RectF(
                geometry.cx - width / 2f,
                centerY - height / 2f,
                geometry.cx + width / 2f,
                centerY + height / 2f
        )
        configureAmbientStroke(dp, 1.25f)
        strokePaint.color = ambientColor(resolvedAodTint(), .42f)
        canvas.drawRoundRect(bounds, height / 2f, height / 2f, strokePaint)

        val iconX = bounds.left + dp(25f)
        // The watch's AmbientUpNextPill uses the queue-music glyph, not playlist-play.
        drawIcon(
                canvas,
                R.drawable.ic_queue_music,
                iconX,
                centerY,
                dp(22f),
                ambientColor(resolvedAodTint(), .68f)
        )
        val textLeft = iconX + dp(21f)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = fontBold
        textPaint.textSize = dp(11f)
        textPaint.color = ambientColor(resolvedAodTint(), .58f)
        canvas.drawText(context.getString(R.string.quick_panel_default_up_next),
                textLeft, centerY - dp(4f), textPaint)
        textPaint.typeface = titleTypeface(bold = false)
        textPaint.textSize = dp(13f)
        textPaint.color = ambientColor(resolvedAodTint(), .82f)
        val nextDetail = context.getString(R.string.preview_sample_next_title) + " · " +
                context.getString(R.string.preview_sample_artist)
        canvas.drawText(
                ellipsize(nextDetail, width - dp(70f)),
                textLeft,
                centerY + dp(12f),
                textPaint
        )
        textPaint.textAlign = Paint.Align.CENTER
    }

    // --- Contextual volume and seek overlays ---

    private fun seekBackdropStyle(): String = OverlayBackdropResolver.seekContentStyle(seekStyle)

    /** Same independent backdrop resolver and album swatches used by the Wear runtime. */
    private fun drawConfiguredOverlayBackdrop(
            canvas: Canvas,
            geometry: PreviewGeometry,
            contentStyle: String,
            accent: Int = albumAccent(),
            secondary: Int = albumSecondaryAccent(),
            tertiary: Int = albumTertiaryAccent()
    ) {
        val backdrop = OverlayBackdropResolver.resolve(overlayBackdropStyle, contentStyle)
        fillPaint.shader = null
        fillPaint.color = Color.BLACK
        canvas.drawRect(geometry.bounds, fillPaint)

        if (backdrop.usesAlbumBlur) {
            drawArtwork(
                    canvas,
                    displayedOverlayBlurredArt(),
                    geometry.bounds,
                    255
            )
        } else if (backdrop == OverlayBackdrop.EXPRESSIVE_NO_BLUR) {
            // The one backdrop that composes over the *sharp* cover. On the watch this comes for
            // free from the player's album-art View sitting under the overlay group; the preview
            // has no layer stack, so it draws the same bitmap here explicitly.
            drawArtwork(canvas, displayedArt(), geometry.bounds, 255)
        }

        fillPaint.shader = null
        when (backdrop) {
            // Mirror the watch: album-tinted backdrops use the faces' saturation band (.30-.90)
            // so the preview shows the same overlay tint the watch renders over a face.
            OverlayBackdrop.ACRYLIC -> fillPaint.shader = LinearGradient(
                    geometry.bounds.left,
                    geometry.bounds.top,
                    geometry.bounds.right,
                    geometry.bounds.bottom,
                    ColorUtils.setAlphaComponent(
                            tonal(accent, .24f, PaletteTransforms.FACE_MIN_SAT, PaletteTransforms.FACE_MAX_SAT), 0x72),
                    ColorUtils.setAlphaComponent(Color.BLACK, 0x9A),
                    Shader.TileMode.CLAMP
            )
            OverlayBackdrop.SOLID_BLACK -> fillPaint.color = Color.BLACK
            OverlayBackdrop.SOLID_ALBUM ->
                fillPaint.color = tonal(accent, .22f, PaletteTransforms.FACE_MIN_SAT, PaletteTransforms.FACE_MAX_SAT)
            OverlayBackdrop.SOLID_SECONDARY ->
                fillPaint.color = tonal(secondary, .24f, .25f, .68f)
            OverlayBackdrop.SOLID_TERTIARY ->
                fillPaint.color = tonal(tertiary, .24f, .25f, .68f)
            // Mirrors the watch's raised alpha (was 0x2A/0x66) - the old values read as barely
            // more than the plain blurred image underneath.
            OverlayBackdrop.GLASS -> fillPaint.shader = LinearGradient(
                    0f,
                    geometry.bounds.top,
                    0f,
                    geometry.bounds.bottom,
                    0x40FFFFFF,
                    0x99000000.toInt(),
                    Shader.TileMode.CLAMP
            )
            OverlayBackdrop.GRADIENT -> fillPaint.shader = LinearGradient(
                    geometry.bounds.left,
                    geometry.bounds.top,
                    geometry.bounds.right,
                    geometry.bounds.bottom,
                    tonal(accent, .42f, .25f, .60f),
                    tonal(secondary, .18f, .25f, .60f),
                    Shader.TileMode.CLAMP
            )
            OverlayBackdrop.DUOTONE -> fillPaint.shader = LinearGradient(
                    geometry.bounds.left,
                    geometry.cy,
                    geometry.bounds.right,
                    geometry.cy,
                    tonal(accent, .30f, .25f, .60f),
                    tonal(secondary, .30f, .25f, .60f),
                    Shader.TileMode.CLAMP
            )
            OverlayBackdrop.PRISM -> fillPaint.shader = LinearGradient(
                    geometry.bounds.left,
                    geometry.bounds.top,
                    geometry.bounds.right,
                    geometry.bounds.bottom,
                    intArrayOf(
                            ColorUtils.setAlphaComponent(
                                    tonal(tertiary, .25f, .25f, .60f), 0xE8),
                            ColorUtils.setAlphaComponent(
                                    tonal(accent, .42f, .25f, .60f), 0xD8),
                            ColorUtils.setAlphaComponent(
                                    tonal(secondary, .22f, .25f, .60f), 0xEA)
                    ),
                    floatArrayOf(0f, .48f, 1f),
                    Shader.TileMode.CLAMP
            )
            OverlayBackdrop.MESH -> fillPaint.shader = LinearGradient(
                    geometry.bounds.left,
                    geometry.bounds.top,
                    geometry.bounds.right,
                    geometry.bounds.bottom,
                    intArrayOf(
                            tonal(tertiary, .18f, .30f, .78f),
                            tonal(accent, .34f, .30f, .78f),
                            tonal(secondary, .16f, .30f, .78f)
                    ),
                    floatArrayOf(0f, .48f, 1f),
                    Shader.TileMode.CLAMP
            )
            OverlayBackdrop.AURORA -> fillPaint.shader = LinearGradient(
                    geometry.bounds.left,
                    geometry.bounds.bottom,
                    geometry.bounds.right,
                    geometry.bounds.top,
                    intArrayOf(
                            tonal(secondary, .12f, .42f, .92f),
                            tonal(accent, .38f, .42f, .92f),
                            tonal(tertiary, .18f, .42f, .92f),
                            Color.BLACK
                    ),
                    floatArrayOf(0f, .34f, .68f, 1f),
                    Shader.TileMode.CLAMP
            )
            OverlayBackdrop.SPOTLIGHT -> fillPaint.shader = RadialGradient(
                    geometry.cx,
                    geometry.bounds.top + geometry.bounds.height() * .28f,
                    geometry.radius * 1.22f,
                    intArrayOf(
                            tonal(accent, .62f, .18f, .72f),
                            tonal(accent, .20f, .18f, .72f),
                            Color.BLACK
                    ),
                    floatArrayOf(0f, .48f, 1f),
                    Shader.TileMode.CLAMP
            )
            OverlayBackdrop.VIGNETTE -> fillPaint.shader = RadialGradient(
                    geometry.cx,
                    geometry.cy,
                    geometry.radius,
                    intArrayOf(
                            tonal(accent, .30f, .25f, .70f),
                            tonal(accent, .16f, .25f, .70f),
                            Color.BLACK
                    ),
                    floatArrayOf(0f, .54f, 1f),
                    Shader.TileMode.CLAMP
            )
            OverlayBackdrop.SPLIT -> fillPaint.shader = LinearGradient(
                    geometry.bounds.left,
                    geometry.cy,
                    geometry.bounds.right,
                    geometry.cy,
                    intArrayOf(
                            tonal(accent, .36f, .28f, .72f),
                            tonal(accent, .36f, .28f, .72f),
                            tonal(secondary, .20f, .28f, .72f),
                            tonal(secondary, .20f, .28f, .72f)
                    ),
                    floatArrayOf(0f, .495f, .505f, 1f),
                    Shader.TileMode.CLAMP
            )
            OverlayBackdrop.BANDS -> fillPaint.shader = LinearGradient(
                    0f,
                    geometry.bounds.top,
                    0f,
                    geometry.bounds.bottom,
                    intArrayOf(
                            tonal(tertiary, .22f, .34f, .80f),
                            tonal(tertiary, .22f, .34f, .80f),
                            tonal(accent, .38f, .34f, .80f),
                            tonal(accent, .38f, .34f, .80f),
                            tonal(secondary, .16f, .34f, .80f),
                            tonal(secondary, .16f, .34f, .80f)
                    ),
                    floatArrayOf(0f, .32f, .34f, .65f, .67f, 1f),
                    Shader.TileMode.CLAMP
            )
            OverlayBackdrop.MIDNIGHT -> fillPaint.shader = LinearGradient(
                    geometry.bounds.left,
                    geometry.bounds.top,
                    geometry.bounds.right,
                    geometry.bounds.bottom,
                    intArrayOf(
                            0xFF060716.toInt(),
                            tonal(accent, .13f, .18f, .48f),
                            0xFF010106.toInt()
                    ),
                    floatArrayOf(0f, .54f, 1f),
                    Shader.TileMode.CLAMP
            )
            OverlayBackdrop.HALO -> fillPaint.shader = RadialGradient(
                    geometry.cx,
                    geometry.cy,
                    geometry.radius * 1.02f,
                    intArrayOf(
                            Color.BLACK,
                            tonal(accent, .20f, .46f, .94f),
                            tonal(secondary, .42f, .46f, .94f),
                            Color.BLACK
                    ),
                    floatArrayOf(0f, .48f, .76f, 1f),
                    Shader.TileMode.CLAMP
            )
            OverlayBackdrop.SMOKE -> fillPaint.shader = LinearGradient(
                    geometry.bounds.left,
                    geometry.bounds.bottom,
                    geometry.bounds.right,
                    geometry.bounds.top,
                    intArrayOf(
                            ColorUtils.setAlphaComponent(Color.BLACK, 0xD8),
                            ColorUtils.setAlphaComponent(
                                    tonal(secondary, .36f, .08f, .32f), 0xA8),
                            ColorUtils.setAlphaComponent(
                                    tonal(accent, .20f, .08f, .32f), 0xD0)
                    ),
                    floatArrayOf(0f, .46f, 1f),
                    Shader.TileMode.CLAMP
            )
            // Mirrors the watch's LIQUID_GLASS drawable: a low-alpha lifted album tint, a white
            // sheen through the middle and a deeper tail, over the blurred cover this backdrop
            // shares with Acrylic/Glass/Prism.
            OverlayBackdrop.LIQUID_GLASS -> fillPaint.shader = LinearGradient(
                    geometry.bounds.left,
                    geometry.bounds.top,
                    geometry.bounds.right,
                    geometry.bounds.bottom,
                    intArrayOf(
                            ColorUtils.setAlphaComponent(
                                    tonal(accent, .62f, .25f, .60f), 0x3D),
                            ColorUtils.setAlphaComponent(Color.WHITE, 0x14),
                            ColorUtils.setAlphaComponent(
                                    tonal(secondary, .30f, .25f, .60f), 0x66)
                    ),
                    floatArrayOf(0f, .48f, 1f),
                    Shader.TileMode.CLAMP
            )
            // Mirrors the watch's Expressive LayerDrawable: the album wash and the black
            // knock-back collapse into one blended colour here (they are flat fills on the watch
            // too), with the vignette drawn as its own pass below.
            OverlayBackdrop.EXPRESSIVE, OverlayBackdrop.EXPRESSIVE_NO_BLUR ->
                fillPaint.color = ColorUtils.compositeColors(
                        ColorUtils.setAlphaComponent(Color.BLACK, 0x4D),
                        ColorUtils.setAlphaComponent(
                                tonal(accent, .30f, PaletteTransforms.FACE_MIN_SAT, PaletteTransforms.FACE_MAX_SAT),
                                0x73))
            OverlayBackdrop.FOLLOW_STYLE -> fillPaint.color = Color.BLACK
        }
        canvas.drawRect(geometry.bounds, fillPaint)

        // Mesh uses a second independent radial wash; the crossing gradients are what separate it
        // from the ordinary three-stop diagonal backdrop on a watch-sized preview.
        if (backdrop == OverlayBackdrop.MESH) {
            fillPaint.shader = RadialGradient(
                    geometry.bounds.right - geometry.radius * .26f,
                    geometry.bounds.top + geometry.radius * .38f,
                    geometry.radius * .92f,
                    intArrayOf(
                            ColorUtils.setAlphaComponent(
                                    tonal(secondary, .58f, .38f, .86f), 0xB8),
                            ColorUtils.setAlphaComponent(tertiary, 0x40),
                            Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, .48f, 1f),
                    Shader.TileMode.CLAMP
            )
            canvas.drawRect(geometry.bounds, fillPaint)
            fillPaint.shader = null
        }

        if (backdrop == OverlayBackdrop.EXPRESSIVE ||
                backdrop == OverlayBackdrop.EXPRESSIVE_NO_BLUR) {
            fillPaint.shader = RadialGradient(
                    geometry.cx,
                    geometry.cy,
                    maxOf(geometry.bounds.width(), geometry.bounds.height()) * .68f,
                    intArrayOf(
                            Color.TRANSPARENT,
                            Color.TRANSPARENT,
                            ColorUtils.setAlphaComponent(Color.BLACK, 0xE0)),
                    floatArrayOf(0f, .55f, 1f),
                    Shader.TileMode.CLAMP
            )
            canvas.drawRect(geometry.bounds, fillPaint)
            fillPaint.shader = null
        }

        if (backdrop == OverlayBackdrop.PRISM || backdrop == OverlayBackdrop.LIQUID_GLASS) {
            strokePaint.shader = null
            strokePaint.pathEffect = null
            strokePaint.style = Paint.Style.STROKE
            val liquid = backdrop == OverlayBackdrop.LIQUID_GLASS
            // Liquid glass's rim is thicker and brighter - it is the cue that sells the pane
            // as a surface with an edge, where Prism's is only a delineation.
            strokePaint.strokeWidth =
                    (geometry.dpScale * if (liquid) 1.5f else 1f).coerceAtLeast(1f)
            strokePaint.color = if (liquid) 0x8CFFFFFF.toInt() else 0x66FFFFFF
            val inset = strokePaint.strokeWidth / 2f
            if (geometry.round) {
                canvas.drawCircle(geometry.cx, geometry.cy, geometry.radius - inset, strokePaint)
            } else {
                val outline = RectF(geometry.bounds).apply { inset(inset, inset) }
                canvas.drawRoundRect(outline, geometry.dpScale * 10f,
                        geometry.dpScale * 10f, strokePaint)
            }
        }
        fillPaint.shader = null
    }

    private fun drawVolumeSurface(
            canvas: Canvas,
            geometry: PreviewGeometry,
            dp: (Float) -> Float
    ) {
        // Runtime volume always keeps the album blur below its style tint; force the same
        // composition here so the phone preview is faithful.
        drawConfiguredOverlayBackdrop(
                canvas, geometry, volumeStyle,
                volumeAccent(), volumeSecondaryAccent(), volumeTertiaryAccent())
        drawVolumeArc(canvas, geometry, dp)
        val chromeColor = when (volumeStyle) {
            "light" -> 0xFF111111.toInt()
            "terminal" -> TERMINAL_GREEN
            else -> Color.WHITE
        }
        val horizontalMeter = volumeLayout in setOf("meter", "meter_top", "meter_bottom")
        when {
            horizontalMeter -> {
                val iconY = when (volumeLayout) {
                    "meter_top" -> geometry.cy - dp(42f)
                    "meter_bottom" -> geometry.cy + dp(42f)
                    else -> geometry.cy + dp(29f)
                }
                drawIcon(canvas, commonR.drawable.action_volume_down,
                        geometry.cx - dp(72f), iconY, dp(20f), chromeColor)
                drawIcon(canvas, commonR.drawable.action_volume_up,
                        geometry.cx + dp(72f), iconY, dp(20f), chromeColor)
            }
            volumeLayout in setOf("vertical_left", "vertical_right") -> {
                val iconX = if (volumeLayout == "vertical_left") {
                    geometry.bounds.left + dp(28f)
                } else {
                    geometry.bounds.right - dp(28f)
                }
                drawIcon(canvas, commonR.drawable.action_volume_up,
                        iconX, geometry.cy - dp(66f), dp(18f), chromeColor)
                drawIcon(canvas, commonR.drawable.action_volume_down,
                        iconX, geometry.cy + dp(66f), dp(18f), chromeColor)
            }
            volumeLayout in setOf("edge_top", "edge_bottom") -> {
                // The runtime rotates these short edge controls into a horizontal pair.
                val iconY = if (volumeLayout == "edge_top") {
                    geometry.bounds.top + dp(23f)
                } else {
                    geometry.bounds.bottom - dp(23f)
                }
                drawIcon(canvas, commonR.drawable.action_volume_down,
                        geometry.cx - dp(54f), iconY, dp(18f), chromeColor)
                drawIcon(canvas, commonR.drawable.action_volume_up,
                        geometry.cx + dp(54f), iconY, dp(18f), chromeColor)
            }
            volumeLayout == "double_edge" -> {
                drawIcon(canvas, commonR.drawable.action_volume_down,
                        geometry.bounds.left + dp(23f), geometry.cy, dp(18f), chromeColor)
                drawIcon(canvas, commonR.drawable.action_volume_up,
                        geometry.bounds.right - dp(23f), geometry.cy, dp(18f), chromeColor)
            }
            else -> {
                drawIcon(canvas, commonR.drawable.action_volume_up, geometry.cx,
                        geometry.bounds.top + dp(21f), dp(20f), chromeColor)
                drawIcon(canvas, commonR.drawable.action_volume_down, geometry.cx,
                        geometry.bounds.bottom - dp(21f), dp(20f), chromeColor)
            }
        }
        val readoutShiftY = when (volumeLayout) {
            "meter" -> -dp(16f)
            "meter_top" -> dp(18f)
            "meter_bottom" -> -dp(18f)
            else -> 0f
        }
        if (readoutShiftY != 0f) {
            canvas.save()
            canvas.translate(0f, readoutShiftY)
        }
        drawOverlayReadout(
                canvas,
                "65%",
                if (seekStyle == "split") "plain" else seekStyle,
                geometry,
                dp,
                plainColor = chromeColor,
                accent = volumeAccent()
        )
        if (readoutShiftY != 0f) canvas.restore()
    }

    private fun drawSeekSurface(
            canvas: Canvas,
            geometry: PreviewGeometry,
            dp: (Float) -> Float
    ) {
        val progressAccent = resolveTint(progressMode, progressCustom, progressDesaturated)
        drawConfiguredOverlayBackdrop(
                canvas, geometry, seekBackdropStyle(),
                progressAccent, sameHueTone(progressAccent, .42f),
                sameHueTone(progressAccent, .68f))
        // Mirrors applySeekPanelLayout on the watch: the edge family keeps the bezel ring and only
        // scales its stroke, while the remaining layouts author their own meter geometry.
        val edgeRing = seekLayout in setOf("edge", "edge_thin", "edge_thick")
        if (edgeRing) {
            drawEdgeSeekRing(canvas, geometry.cx, geometry.cy, geometry.radius, dp,
                    strokeScale = when (seekLayout) {
                        "edge_thin" -> 0.5f
                        "edge_thick" -> 1.8f
                        else -> 1f
                    })
        } else {
            when (seekLayout) {
                "segments" -> drawPreviewSeekTimeline(
                        canvas, geometry, dp, segmented = true)
                "timeline_top" -> drawPreviewSeekTimeline(
                        canvas, geometry, dp, segmented = false,
                        centerY = geometry.bounds.top + geometry.bounds.height() * .27f)
                "timeline_bottom" -> drawPreviewSeekTimeline(
                        canvas, geometry, dp, segmented = false,
                        centerY = geometry.bounds.top + geometry.bounds.height() * .73f)
                "segments_top" -> drawPreviewSeekTimeline(
                        canvas, geometry, dp, segmented = true,
                        centerY = geometry.bounds.top + geometry.bounds.height() * .27f)
                "vertical_left", "vertical_right" ->
                    drawPreviewVerticalSeekTimeline(canvas, geometry, dp)
                "dial" -> drawPreviewSeekDial(canvas, geometry, dp)
                "twin" -> drawPreviewTwinSeekTimeline(canvas, geometry, dp)
                else -> drawPreviewSeekTimeline(canvas, geometry, dp, segmented = false)
            }
        }
        val readoutShift = when (seekLayout) {
            "timeline", "segments" -> -dp(18f)
            "timeline_top", "segments_top" -> dp(20f)
            "timeline_bottom" -> -dp(18f)
            "dial" -> -dp(42f)
            else -> 0f
        }
        if (readoutShift != 0f) {
            canvas.save()
            canvas.translate(0f, readoutShift)
        }
        drawOverlayReadout(
                canvas, "1:07", seekStyle, geometry, dp, total = "3:12",
                accent = progressAccent)
        if (readoutShift != 0f) canvas.restore()
    }

    private fun drawOverlayReadout(
            canvas: Canvas,
            content: String,
            style: String,
            geometry: PreviewGeometry,
            dp: (Float) -> Float,
            total: String? = null,
            plainColor: Int = Color.WHITE,
            accent: Int = albumAccent()
    ) {
        textPaint.style = Paint.Style.FILL
        textPaint.typeface = fontBold
        textPaint.textAlign = Paint.Align.CENTER
        // Scratch paint shared across surfaces: styles that widen the tracking have to hand it
        // back neutral, or the next readout inherits the spacing.
        textPaint.letterSpacing = 0f
        when (style) {
            "split" -> {
                textPaint.color = plainColor
                textPaint.textSize = dp(30f)
                canvas.drawText(content, geometry.cx, geometry.cy - dp(2f), textPaint)
                textPaint.typeface = fontRegular
                textPaint.color = 0xB3FFFFFF.toInt()
                textPaint.textSize = dp(16.5f)
                canvas.drawText(total ?: "3:12", geometry.cx, geometry.cy + dp(20f), textPaint)
            }
            "giant" -> {
                textPaint.color = plainColor
                textPaint.textSize = dp(52f)
                canvas.drawText(content, geometry.cx, geometry.cy + dp(18f), textPaint)
            }
            "giant_album" -> {
                textPaint.color = liftedAccent(accent)
                textPaint.textSize = dp(52f)
                canvas.drawText(content, geometry.cx, geometry.cy + dp(18f), textPaint)
            }
            "micro" -> {
                textPaint.color = ColorUtils.setAlphaComponent(plainColor, 0xB3)
                textPaint.textSize = dp(15f)
                textPaint.letterSpacing = 0.08f
                canvas.drawText(content, geometry.cx, geometry.cy + dp(5f), textPaint)
                textPaint.letterSpacing = 0f
            }
            "shadow" -> {
                textPaint.textSize = dp(30f)
                textPaint.color = plainColor
                textPaint.setShadowLayer(dp(6f), 0f, dp(1.5f),
                        ColorUtils.setAlphaComponent(Color.BLACK, 0xCC))
                canvas.drawText(content, geometry.cx, geometry.cy + dp(10f), textPaint)
                textPaint.clearShadowLayer()
            }
            "underline" -> {
                textPaint.textSize = dp(28f)
                textPaint.color = plainColor
                val metrics = textPaint.fontMetrics
                val baseline = geometry.cy - (metrics.ascent + metrics.descent) / 2f
                canvas.drawText(content, geometry.cx, baseline, textPaint)
                val ruleWidth = textPaint.measureText(content) + dp(8f)
                val ruleTop = baseline + metrics.descent + dp(4f)
                fillPaint.shader = null
                fillPaint.color = liftedAccent(accent)
                val thickness = dp(2f)
                canvas.drawRoundRect(
                        RectF(geometry.cx - ruleWidth / 2f, ruleTop,
                                geometry.cx + ruleWidth / 2f, ruleTop + thickness),
                        thickness / 2f, thickness / 2f, fillPaint)
            }
            "compact_pill" -> {
                textPaint.textSize = dp(19f)
                val metrics = textPaint.fontMetrics
                val textWidth = textPaint.measureText(content)
                val rect = RectF(
                        geometry.cx - textWidth / 2f - dp(13f),
                        geometry.cy - dp(16f),
                        geometry.cx + textWidth / 2f + dp(13f),
                        geometry.cy + dp(16f))
                fillPaint.shader = null
                fillPaint.color = 0xA6202024.toInt()
                canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, fillPaint)
                textPaint.color = plainColor
                val baseline = geometry.cy - (metrics.ascent + metrics.descent) / 2f
                canvas.drawText(content, geometry.cx, baseline, textPaint)
            }
            "square_album" -> {
                val size = dp(76f)
                val rect = RectF(
                        geometry.cx - size / 2f, geometry.cy - size / 2f,
                        geometry.cx + size / 2f, geometry.cy + size / 2f)
                fillPaint.shader = LinearGradient(
                        rect.left, rect.top, rect.right, rect.bottom,
                        tonal(accent, .62f, .30f, .82f),
                        tonal(accent, .25f, .30f, .82f), Shader.TileMode.CLAMP)
                canvas.drawRoundRect(rect, dp(10f), dp(10f), fillPaint)
                fillPaint.shader = null
                textPaint.textSize = dp(24f)
                textPaint.color = contrastingColor(tonal(accent, .62f, .30f, .82f))
                val metrics = textPaint.fontMetrics
                canvas.drawText(content, geometry.cx,
                        geometry.cy - (metrics.ascent + metrics.descent) / 2f, textPaint)
            }
            "stacked_pill" -> {
                val rect = RectF(
                        geometry.cx - dp(51f), geometry.cy - dp(31f),
                        geometry.cx + dp(51f), geometry.cy + dp(31f))
                fillPaint.shader = null
                fillPaint.color = ColorUtils.setAlphaComponent(
                        tonal(accent, .28f, .25f, .72f), 0xE8)
                canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, fillPaint)
                textPaint.color = Color.WHITE
                textPaint.textSize = dp(24f)
                canvas.drawText(content, geometry.cx, geometry.cy - dp(1f), textPaint)
                textPaint.typeface = fontRegular
                textPaint.textSize = dp(10f)
                textPaint.color = 0xB3FFFFFF.toInt()
                canvas.drawText(total ?: "VOLUME", geometry.cx, geometry.cy + dp(16f), textPaint)
            }
            "badge" -> {
                val radius = dp(39f)
                fillPaint.shader = RadialGradient(
                        geometry.cx - radius * .28f,
                        geometry.cy - radius * .32f,
                        radius * 1.35f,
                        intArrayOf(
                                tonal(accent, .72f, .30f, .86f),
                                tonal(accent, .32f, .30f, .86f),
                                tonal(accent, .18f, .30f, .86f)),
                        floatArrayOf(0f, .58f, 1f), Shader.TileMode.CLAMP)
                canvas.drawCircle(geometry.cx, geometry.cy, radius, fillPaint)
                fillPaint.shader = null
                strokePaint.shader = null
                strokePaint.pathEffect = null
                strokePaint.style = Paint.Style.STROKE
                strokePaint.strokeWidth = dp(1.5f)
                strokePaint.color = 0x70FFFFFF
                canvas.drawCircle(geometry.cx, geometry.cy, radius - dp(1f), strokePaint)
                textPaint.textSize = dp(23f)
                textPaint.color = Color.WHITE
                val metrics = textPaint.fontMetrics
                canvas.drawText(content, geometry.cx,
                        geometry.cy - (metrics.ascent + metrics.descent) / 2f, textPaint)
            }
            "glass_bar" -> {
                val rect = RectF(
                        geometry.cx - dp(72f), geometry.cy - dp(23f),
                        geometry.cx + dp(72f), geometry.cy + dp(23f))
                fillPaint.shader = LinearGradient(
                        0f, rect.top, 0f, rect.bottom,
                        0x52FFFFFF, 0x52101014, Shader.TileMode.CLAMP)
                canvas.drawRoundRect(rect, dp(17f), dp(17f), fillPaint)
                fillPaint.shader = null
                strokePaint.shader = null
                strokePaint.style = Paint.Style.STROKE
                strokePaint.strokeWidth = dp(1f)
                strokePaint.color = 0x66FFFFFF
                canvas.drawRoundRect(rect, dp(17f), dp(17f), strokePaint)
                textPaint.textSize = dp(25f)
                textPaint.color = Color.WHITE
                val metrics = textPaint.fontMetrics
                canvas.drawText(content, geometry.cx,
                        geometry.cy - (metrics.ascent + metrics.descent) / 2f, textPaint)
            }
            "lcd" -> {
                val rect = RectF(
                        geometry.cx - dp(61f), geometry.cy - dp(27f),
                        geometry.cx + dp(61f), geometry.cy + dp(27f))
                fillPaint.shader = null
                fillPaint.color = 0xED06100A.toInt()
                canvas.drawRoundRect(rect, dp(4f), dp(4f), fillPaint)
                strokePaint.shader = null
                strokePaint.pathEffect = null
                strokePaint.style = Paint.Style.STROKE
                strokePaint.strokeWidth = dp(1.5f)
                strokePaint.color = ColorUtils.setAlphaComponent(TERMINAL_GREEN, 0xA8)
                canvas.drawRoundRect(rect, dp(4f), dp(4f), strokePaint)
                textPaint.typeface = Typeface.MONOSPACE
                textPaint.letterSpacing = .13f
                textPaint.textSize = dp(25f)
                textPaint.color = TERMINAL_GREEN
                textPaint.setShadowLayer(dp(4f), 0f, 0f,
                        ColorUtils.setAlphaComponent(TERMINAL_GREEN, 0x70))
                val metrics = textPaint.fontMetrics
                canvas.drawText(content, geometry.cx,
                        geometry.cy - (metrics.ascent + metrics.descent) / 2f, textPaint)
                textPaint.clearShadowLayer()
                textPaint.letterSpacing = 0f
            }
            "outline_square" -> {
                val rect = RectF(
                        geometry.cx - dp(54f), geometry.cy - dp(31f),
                        geometry.cx + dp(54f), geometry.cy + dp(31f))
                fillPaint.shader = null
                fillPaint.color = ColorUtils.setAlphaComponent(Color.BLACK, 0x4D)
                canvas.drawRoundRect(rect, dp(5f), dp(5f), fillPaint)
                strokePaint.shader = null
                strokePaint.pathEffect = null
                strokePaint.style = Paint.Style.STROKE
                strokePaint.strokeWidth = dp(2f)
                strokePaint.color = liftedAccent(accent)
                canvas.drawRoundRect(rect, dp(5f), dp(5f), strokePaint)
                textPaint.textSize = dp(26f)
                textPaint.color = plainColor
                val metrics = textPaint.fontMetrics
                canvas.drawText(content, geometry.cx,
                        geometry.cy - (metrics.ascent + metrics.descent) / 2f, textPaint)
            }
            "ribbon" -> {
                val halfWidth = dp(73f)
                val halfHeight = dp(23f)
                val notch = dp(10f)
                val ribbon = Path().apply {
                    moveTo(geometry.cx - halfWidth, geometry.cy - halfHeight)
                    lineTo(geometry.cx + halfWidth, geometry.cy - halfHeight)
                    lineTo(geometry.cx + halfWidth - notch, geometry.cy)
                    lineTo(geometry.cx + halfWidth, geometry.cy + halfHeight)
                    lineTo(geometry.cx - halfWidth, geometry.cy + halfHeight)
                    lineTo(geometry.cx - halfWidth + notch, geometry.cy)
                    close()
                }
                fillPaint.shader = LinearGradient(
                        geometry.cx - halfWidth, geometry.cy,
                        geometry.cx + halfWidth, geometry.cy,
                        tonal(accent, .28f, .38f, .86f),
                        tonal(accent, .62f, .38f, .86f), Shader.TileMode.CLAMP)
                canvas.drawPath(ribbon, fillPaint)
                fillPaint.shader = null
                textPaint.textSize = dp(25f)
                textPaint.color = Color.WHITE
                val metrics = textPaint.fontMetrics
                canvas.drawText(content, geometry.cx,
                        geometry.cy - (metrics.ascent + metrics.descent) / 2f, textPaint)
            }
            "pill", "expressive", "material", "white", "glass_white", "translucent_album",
            "glow_album", "outline", "solid_theme", "solid_album",
            "mono", "tonal_dark", "terminal", "hairline" -> {
                textPaint.textSize = dp(when (style) {
                    "hairline" -> 22f
                    "terminal" -> 24f
                    else -> 26f
                })
                if (style == "terminal") textPaint.letterSpacing = 0.12f
                val textWidth = textPaint.measureText(content)
                val padH = dp(when (style) {
                    "pill" -> 18f
                    "terminal" -> 16f
                    else -> 20f
                })
                val padV = dp(if (style == "hairline" || style == "terminal") 7f else 8f)
                val fontMetrics = textPaint.fontMetrics
                val textHeight = fontMetrics.descent - fontMetrics.ascent
                val rect = RectF(
                        geometry.cx - textWidth / 2f - padH,
                        geometry.cy - textHeight / 2f - padV,
                        geometry.cx + textWidth / 2f + padH,
                        geometry.cy + textHeight / 2f + padV
                )
                fillPaint.shader = null
                strokePaint.shader = null
                strokePaint.pathEffect = null

                var strokeColor = Color.TRANSPARENT
                var strokeW = 0f
                // Negative means "capsule" - resolved to half the row height once it is known.
                var corner = -1f

                when (style) {
                    "mono" -> {
                        fillPaint.color = MONO_SURFACE
                        textPaint.color = Color.WHITE
                    }
                    "tonal_dark" -> {
                        fillPaint.color = tonal(accent, 0.22f, 0.25f, 0.60f)
                        textPaint.color = liftedAccent(accent)
                    }
                    "terminal" -> {
                        corner = 0f
                        fillPaint.color = ColorUtils.setAlphaComponent(Color.BLACK, 0xCC)
                        textPaint.color = TERMINAL_GREEN
                        strokeColor = ColorUtils.setAlphaComponent(TERMINAL_GREEN, 0x99)
                        strokeW = dp(1f)
                    }
                    "hairline" -> {
                        fillPaint.color = Color.TRANSPARENT
                        textPaint.color = Color.WHITE
                        strokeColor = ColorUtils.setAlphaComponent(Color.WHITE, 0x66)
                        strokeW = dp(1f)
                    }
                    "expressive" -> {
                        fillPaint.color = tonal(accent, 0.82f, 0.25f, 0.60f)
                        textPaint.color = 0xFF202124.toInt()
                    }
                    "material" -> {
                        fillPaint.color = tonal(accent, 0.92f, 0.25f, 0.82f)
                        textPaint.color = 0xFF202124.toInt()
                    }
                    "white" -> {
                        fillPaint.color = Color.WHITE
                        textPaint.color = 0xFF202124.toInt()
                    }
                    "glass_white" -> {
                        fillPaint.color = Color.argb(0x1A, 0xFF, 0xFF, 0xFF)
                        textPaint.color = Color.WHITE
                    }
                    "translucent_album" -> {
                        val baseColor = accent
                        val tintColor = if (face == "expressive") {
                            PaletteTransforms.tonalSurface(baseColor, 0.74f, 0.40f, 0.92f)
                        } else {
                            baseColor
                        }
                        fillPaint.color = ColorUtils.setAlphaComponent(tintColor, 0x4D)
                        textPaint.color = Color.WHITE
                    }
                    "glow_album" -> {
                        fillPaint.color = Color.TRANSPARENT
                        val baseColor = accent
                        val tintColor = if (face == "expressive") {
                            PaletteTransforms.tonalSurface(baseColor, 0.74f, 0.40f, 0.92f)
                        } else {
                            baseColor
                        }
                        val glowColor = liftedAccent(tintColor)
                        textPaint.color = glowColor
                        strokeColor = ColorUtils.setAlphaComponent(glowColor, 0xE0)
                        strokeW = dp(2f)
                    }
                    "outline" -> {
                        fillPaint.color = Color.TRANSPARENT
                        val baseColor = accent
                        textPaint.color = baseColor
                        strokeColor = ColorUtils.setAlphaComponent(baseColor, 0xE0)
                        strokeW = dp(1.5f)
                    }
                    "solid_theme" -> {
                        val baseColor = ACCENT_NEUTRAL
                        val tintColor = if (face == "expressive") {
                            PaletteTransforms.tonalSurface(baseColor, 0.74f, 0.40f, 0.92f)
                        } else {
                            baseColor
                        }
                        fillPaint.color = tintColor
                        textPaint.color = if (ColorUtils.calculateLuminance(tintColor) > .50) Color.BLACK else Color.WHITE
                    }
                    "solid_album" -> {
                        val baseColor = accent
                        val tintColor = if (face == "expressive") {
                            PaletteTransforms.tonalSurface(baseColor, 0.74f, 0.40f, 0.92f)
                        } else {
                            baseColor
                        }
                        fillPaint.color = tintColor
                        textPaint.color = if (ColorUtils.calculateLuminance(tintColor) > .50) Color.BLACK else Color.WHITE
                    }
                    else -> { // pill
                        fillPaint.color = 0xB3161619.toInt()
                        textPaint.color = Color.WHITE
                    }
                }

                val r = if (corner >= 0f) corner else rect.height() / 2f
                if (fillPaint.color != Color.TRANSPARENT) {
                    canvas.drawRoundRect(rect, r, r, fillPaint)
                }
                if (strokeW > 0f) {
                    strokePaint.style = Paint.Style.STROKE
                    strokePaint.strokeWidth = strokeW
                    strokePaint.color = strokeColor
                    canvas.drawRoundRect(rect, r, r, strokePaint)
                }
                val baseline = geometry.cy - (fontMetrics.ascent + fontMetrics.descent) / 2f
                canvas.drawText(content, geometry.cx, baseline, textPaint)
                textPaint.letterSpacing = 0f
            }
            else -> {
                textPaint.color = plainColor
                textPaint.textSize = dp(30f)
                canvas.drawText(content, geometry.cx, geometry.cy + dp(10f), textPaint)
            }
        }
    }

    private fun drawVolumeArc(
            canvas: Canvas,
            geometry: PreviewGeometry,
            dp: (Float) -> Float
    ) {
        if (volumeLayout in setOf("meter", "meter_top", "meter_bottom")) {
            drawPreviewVolumeMeter(canvas, geometry, dp)
            return
        }
        if (volumeLayout in setOf("vertical_left", "vertical_right")) {
            drawPreviewVerticalVolumeMeter(canvas, geometry, dp)
            return
        }
        val baseStroke = dp(6f)
        val maxStroke = baseStroke * 1.7f
        val inset = maxStroke / 2f
        val diameter = when (volumeLayout) {
            "halo" -> min(geometry.radius * 1.44f, dp(138f))
            "dial" -> min(geometry.bounds.width() - dp(76f), dp(104f))
                    .coerceAtLeast(dp(68f))
            else -> geometry.radius * 2f - inset * 2f
        }
        // Mirrors VolumeLayout.activeArcStart/activeArcSweep on the watch, negative sweeps
        // included - those are the arcs that fill counter-clockwise so "up" still means louder.
        previewVolumeArcStart = when (volumeLayout) {
            "halo" -> 135f
            "edge_tall" -> 100f
            "edge_right" -> 50f
            "edge_top" -> 235f
            "edge_bottom" -> 125f
            "ring" -> 270f
            "dial" -> 120f
            else -> 130f
        }
        previewVolumeArcSweep = when (volumeLayout) {
            "halo" -> 270f
            "edge_tall" -> 160f
            "edge_right" -> -100f
            "edge_top" -> 70f
            "edge_bottom" -> -70f
            "ring" -> 360f
            "dial" -> 300f
            else -> 100f
        }
        val bounds = RectF(
                geometry.cx - diameter / 2f,
                geometry.cy - diameter / 2f,
                geometry.cx + diameter / 2f,
                geometry.cy + diameter / 2f
        )
        val accent = volumeAccent()
        val secondary = volumeSecondaryAccent()
        val tertiary = volumeTertiaryAccent()

        when (volumeStyle) {
            "minimal" -> drawVolumeArcPass(canvas, bounds, baseStroke * 0.5f,
                    Paint.Cap.ROUND, 0x22FFFFFF, accent)
            "material" -> {
                drawVolumeArcPass(canvas, bounds, baseStroke * 1.1f,
                        Paint.Cap.BUTT, 0x33FFFFFF, accent)
                drawVolumeThumb(canvas, bounds, baseStroke, accent)
            }
            "tonal" -> drawVolumeArcPass(canvas, bounds, baseStroke * 1.7f,
                    Paint.Cap.ROUND, tonal(accent, 0.22f, 0.25f, 0.60f),
                    tonal(accent, 0.72f, 0.25f, 0.60f))
            "neon" -> drawVolumeArcPass(canvas, bounds, baseStroke * 0.8f,
                    Paint.Cap.ROUND, ColorUtils.setAlphaComponent(accent, 0x40), accent)
            "light" -> drawVolumeArcPass(canvas, bounds, baseStroke,
                    Paint.Cap.ROUND, 0x88CCCCCC.toInt(), accent)
            "gradient" -> drawVolumeArcPass(canvas, bounds, baseStroke * 1.2f,
                    Paint.Cap.ROUND, tonal(accent, 0.18f, 0.25f, 0.60f), 0,
                    LinearGradient(bounds.left, bounds.top, bounds.left, bounds.bottom,
                            tonal(accent, 0.62f, 0.25f, 0.60f),
                            tonal(secondary, 0.30f, 0.25f, 0.60f), Shader.TileMode.CLAMP))
            "mono" -> drawVolumeArcPass(canvas, bounds, baseStroke,
                    Paint.Cap.ROUND, 0x33FFFFFF, MONO_ACTIVE)
            "outline" -> drawVolumeArcPass(canvas, bounds, baseStroke * 1.4f,
                    Paint.Cap.BUTT, 0x55FFFFFF, accent)
            "duotone" -> drawVolumeArcPass(canvas, bounds, baseStroke,
                    Paint.Cap.ROUND, tonal(secondary, 0.30f, 0.25f, 0.60f), accent)
            "prism" -> {
                drawVolumeArcPass(
                        canvas,
                        bounds,
                        baseStroke * 1.7f,
                        Paint.Cap.ROUND,
                        0x22FFFFFF,
                        0,
                        SweepGradient(
                                bounds.centerX(), bounds.centerY(),
                                intArrayOf(tertiary, accent, secondary, tertiary, accent),
                                floatArrayOf(0f, .36f, .50f, .64f, 1f))
                )
                drawVolumeArcPass(canvas, bounds, baseStroke * .35f,
                        Paint.Cap.ROUND, Color.TRANSPARENT, 0xA6FFFFFF.toInt())
            }
            "contrast" -> drawVolumeArcPass(canvas, bounds, baseStroke * 1.3f,
                    Paint.Cap.BUTT, 0x55FFFFFF, Color.WHITE)
            "terminal" -> drawVolumeArcPass(canvas, bounds, baseStroke * 0.9f,
                    Paint.Cap.BUTT, ColorUtils.setAlphaComponent(TERMINAL_GREEN, 0x40), TERMINAL_GREEN)
            "frost" -> drawVolumeArcPass(canvas, bounds, baseStroke,
                    Paint.Cap.ROUND, 0x44FFFFFF, accent)
            "segments" -> {
                strokePaint.pathEffect = DashPathEffect(
                        floatArrayOf(baseStroke * 1.3f, baseStroke * 0.9f), 0f)
                drawVolumeArcPass(canvas, bounds, baseStroke * 1.5f,
                        Paint.Cap.BUTT, ColorUtils.setAlphaComponent(accent, 0x28), accent,
                        preservePathEffect = true)
                strokePaint.pathEffect = null
            }
            "aurora" -> {
                drawVolumeArcPass(canvas, bounds, baseStroke * 1.3f,
                        Paint.Cap.ROUND, 0x22FFFFFF, 0,
                        LinearGradient(bounds.left, bounds.top, bounds.left, bounds.bottom,
                                intArrayOf(
                                        tonal(tertiary, .60f, .25f, .85f),
                                        tonal(accent, .60f, .25f, .85f),
                                        tonal(secondary, .60f, .25f, .85f)
                                ),
                                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP))
            }
            "ink" -> {
                drawVolumeArcPass(canvas, bounds, baseStroke * 2.1f,
                        Paint.Cap.ROUND, Color.TRANSPARENT,
                        ColorUtils.setAlphaComponent(accent, 0x3A))
                drawVolumeArcPass(canvas, bounds, baseStroke * 0.7f,
                        Paint.Cap.ROUND, 0x22FFFFFF, accent)
            }
            "groove" -> {
                drawVolumeArcPass(canvas, bounds, baseStroke * 1.8f,
                        Paint.Cap.ROUND, 0x55000000, Color.TRANSPARENT)
                drawVolumeArcPass(canvas, bounds, baseStroke * 0.6f,
                        Paint.Cap.ROUND, Color.TRANSPARENT, accent)
            }
            "beads" -> drawVolumeBeads(
                    canvas, bounds, baseStroke, accent, volumeSecondaryAccent())
            "dual" -> {
                drawVolumeArcPass(canvas, bounds, baseStroke * 1.45f,
                        Paint.Cap.ROUND, 0x24FFFFFF,
                        ColorUtils.setAlphaComponent(secondary, 0xD8))
                val inner = RectF(bounds).apply { inset(baseStroke * 1.55f, baseStroke * 1.55f) }
                drawVolumeArcPass(canvas, inner, baseStroke * .58f,
                        Paint.Cap.ROUND, 0x1FFFFFFF, accent)
            }
            "pulse" -> {
                drawVolumeArcPass(canvas, bounds, baseStroke * 2.5f,
                        Paint.Cap.ROUND, Color.TRANSPARENT,
                        ColorUtils.setAlphaComponent(accent, 0x2E))
                drawVolumeArcPass(canvas, bounds, baseStroke * .82f,
                        Paint.Cap.ROUND, 0x20FFFFFF, liftedAccent(accent))
                drawVolumeThumb(canvas, bounds, baseStroke * 1.12f, liftedAccent(accent))
            }
            "chrome" -> drawVolumeArcPass(
                    canvas, bounds, baseStroke * 1.28f, Paint.Cap.ROUND,
                    0x30FFFFFF, Color.TRANSPARENT,
                    SweepGradient(
                            bounds.centerX(), bounds.centerY(),
                            intArrayOf(
                                    0xFF62666D.toInt(), Color.WHITE,
                                    0xFF888D96.toInt(), Color.WHITE,
                                    0xFF62666D.toInt()),
                            null))
            "spectrum" -> drawVolumeArcPass(
                    canvas, bounds, baseStroke * 1.32f, Paint.Cap.ROUND,
                    0x24FFFFFF, Color.TRANSPARENT,
                    SweepGradient(
                            bounds.centerX(), bounds.centerY(),
                            intArrayOf(
                                    0xFFFF4D6D.toInt(), 0xFFFFC857.toInt(),
                                    0xFF49D17D.toInt(), 0xFF5BC0EB.toInt(),
                                    0xFF9B5DE5.toInt(), 0xFFFF4D6D.toInt()),
                            null))
            "steps" -> {
                strokePaint.pathEffect = DashPathEffect(
                        floatArrayOf(baseStroke * 2.5f, baseStroke * .72f), 0f)
                drawVolumeArcPass(canvas, bounds, baseStroke * 1.45f,
                        Paint.Cap.BUTT, ColorUtils.setAlphaComponent(accent, 0x24), accent,
                        preservePathEffect = true)
                strokePaint.pathEffect = null
            }
            else -> drawVolumeArcPass(canvas, bounds, baseStroke,
                    Paint.Cap.ROUND, 0x33FFFFFF, accent)
        }

        if (volumeLayout == "dial") {
            val angle = Math.toRadians(
                    (previewVolumeArcStart + SAMPLE_VOLUME * previewVolumeArcSweep).toDouble())
            val cosAngle = cos(angle).toFloat()
            val sinAngle = sin(angle).toFloat()
            val dialRadius = bounds.width() / 2f
            val needleColor = if (volumeStyle == "chrome") Color.WHITE else accent
            strokePaint.pathEffect = null
            strokePaint.shader = null
            strokePaint.strokeCap = Paint.Cap.ROUND
            strokePaint.strokeWidth = baseStroke * .72f
            strokePaint.color = 0x66000000
            canvas.drawLine(
                    bounds.centerX() + dialRadius * .13f * cosAngle + baseStroke * .2f,
                    bounds.centerY() + dialRadius * .13f * sinAngle + baseStroke * .2f,
                    bounds.centerX() + dialRadius * .62f * cosAngle + baseStroke * .2f,
                    bounds.centerY() + dialRadius * .62f * sinAngle + baseStroke * .2f,
                    strokePaint)
            strokePaint.strokeWidth = baseStroke * .42f
            strokePaint.color = needleColor
            canvas.drawLine(
                    bounds.centerX() + dialRadius * .13f * cosAngle,
                    bounds.centerY() + dialRadius * .13f * sinAngle,
                    bounds.centerX() + dialRadius * .62f * cosAngle,
                    bounds.centerY() + dialRadius * .62f * sinAngle,
                    strokePaint)
            fillPaint.shader = null
            fillPaint.color = needleColor
            canvas.drawCircle(bounds.centerX(), bounds.centerY(), baseStroke * .62f, fillPaint)
        }

        if (volumeLayout == "double_edge") {
            // A second counter-facing rail makes the layout legible even for reduced styles.
            val savedStart = previewVolumeArcStart
            val savedSweep = previewVolumeArcSweep
            previewVolumeArcStart = 50f
            previewVolumeArcSweep = -100f
            drawVolumeArcPass(
                    canvas, bounds, baseStroke * .72f, Paint.Cap.ROUND,
                    0x24FFFFFF, volumeSecondaryAccent())
            previewVolumeArcStart = savedStart
            previewVolumeArcSweep = savedSweep
        }
    }

    private fun drawVolumeArcPass(
            canvas: Canvas,
            bounds: RectF,
            strokeWidth: Float,
            cap: Paint.Cap,
            trackColor: Int,
            fillColor: Int,
            fillShader: Shader? = null,
            preservePathEffect: Boolean = false
    ) {
        if (!preservePathEffect) strokePaint.pathEffect = null
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = strokeWidth
        strokePaint.strokeCap = cap
        strokePaint.shader = null
        strokePaint.color = trackColor
        if (Color.alpha(trackColor) > 0) {
            canvas.drawArc(bounds, previewVolumeArcStart, previewVolumeArcSweep, false, strokePaint)
        }
        strokePaint.shader = fillShader
        strokePaint.color = fillColor
        if (fillShader != null || Color.alpha(fillColor) > 0) {
            canvas.drawArc(
                    bounds, previewVolumeArcStart,
                    SAMPLE_VOLUME * previewVolumeArcSweep, false, strokePaint)
        }
        strokePaint.shader = null
        if (!preservePathEffect) strokePaint.pathEffect = null
    }

    private fun drawVolumeThumb(canvas: Canvas, bounds: RectF, baseStroke: Float, color: Int) {
        val angle = Math.toRadians(
                (previewVolumeArcStart + SAMPLE_VOLUME * previewVolumeArcSweep).toDouble())
        fillPaint.shader = null
        fillPaint.color = color
        canvas.drawCircle(
                bounds.centerX() + bounds.width() / 2f * cos(angle).toFloat(),
                bounds.centerY() + bounds.height() / 2f * sin(angle).toFloat(),
                baseStroke * 0.95f,
                fillPaint
        )
    }

    private fun drawVolumeBeads(
            canvas: Canvas,
            bounds: RectF,
            baseStroke: Float,
            playedColor: Int,
            remainingColor: Int
    ) {
        fillPaint.shader = null
        fillPaint.style = Paint.Style.FILL
        val count = when {
            kotlin.math.abs(previewVolumeArcSweep) >= 350f -> 36
            kotlin.math.abs(previewVolumeArcSweep) >= 250f -> 28
            else -> 14
        }
        val radius = bounds.width() / 2f
        repeat(count) { index ->
            val denominator = if (count > 1) count - 1 else 1
            val fraction = index.toFloat() / denominator
            val angle = Math.toRadians(
                    (previewVolumeArcStart + previewVolumeArcSweep * fraction).toDouble())
            fillPaint.color = if (fraction <= SAMPLE_VOLUME) {
                playedColor
            } else {
                ColorUtils.setAlphaComponent(remainingColor, 0x42)
            }
            canvas.drawCircle(
                    bounds.centerX() + radius * cos(angle).toFloat(),
                    bounds.centerY() + radius * sin(angle).toFloat(),
                    baseStroke * if (index % 4 == 0) .5f else .34f,
                    fillPaint)
        }
    }

    private fun drawPreviewVolumeMeter(
            canvas: Canvas,
            geometry: PreviewGeometry,
            dp: (Float) -> Float
    ) {
        val width = if (volumeLayout in setOf("meter_top", "meter_bottom")) {
            min(geometry.bounds.width() * .58f, dp(116f))
        } else {
            min(geometry.bounds.width() * .68f, dp(132f))
        }
        val height = dp(11f)
        val cy = when (volumeLayout) {
            "meter_top" -> geometry.cy - dp(45f)
            "meter_bottom" -> geometry.cy + dp(45f)
            else -> geometry.cy + dp(29f)
        }
        val bounds = RectF(
                geometry.cx - width / 2f, cy - height / 2f,
                geometry.cx + width / 2f, cy + height / 2f)
        drawPreviewLinearVolumeMeter(canvas, bounds, vertical = false, dp = dp)
    }

    private fun drawPreviewVerticalVolumeMeter(
            canvas: Canvas,
            geometry: PreviewGeometry,
            dp: (Float) -> Float
    ) {
        val width = dp(11f)
        val height = min(geometry.bounds.height() * .54f, dp(112f))
        val cx = if (volumeLayout == "vertical_left") {
            geometry.bounds.left + dp(22f)
        } else {
            geometry.bounds.right - dp(22f)
        }
        val bounds = RectF(
                cx - width / 2f, geometry.cy - height / 2f,
                cx + width / 2f, geometry.cy + height / 2f)
        drawPreviewLinearVolumeMeter(canvas, bounds, vertical = true, dp = dp)
    }

    /** Mirrors CircularVolumeBar.drawLinearMeter so styles remain distinct when paired with any
     *  horizontal or vertical meter layout. */
    private fun drawPreviewLinearVolumeMeter(
            canvas: Canvas,
            bounds: RectF,
            vertical: Boolean,
            dp: (Float) -> Float
    ) {
        val accent = volumeAccent()
        val secondary = volumeSecondaryAccent()
        val tertiary = volumeTertiaryAccent()
        val fillColor = when (volumeStyle) {
            "mono" -> MONO_ACTIVE
            "contrast" -> Color.WHITE
            "terminal" -> TERMINAL_GREEN
            "tonal" -> tonal(accent, .72f, .25f, .60f)
            else -> accent
        }
        val trackColor = when (volumeStyle) {
            "light" -> 0x88CCCCCC.toInt()
            "terminal" -> ColorUtils.setAlphaComponent(TERMINAL_GREEN, 0x40)
            "duotone" -> tonal(secondary, .30f, .25f, .60f)
            "tonal" -> tonal(accent, .22f, .25f, .60f)
            else -> 0x35FFFFFF
        }

        fun point(fraction: Float, edgeInset: Float = 0f): Pair<Float, Float> =
                if (vertical) {
                    bounds.centerX() to (bounds.bottom - edgeInset -
                            (bounds.height() - edgeInset * 2f) * fraction.coerceIn(0f, 1f))
                } else {
                    (bounds.left + edgeInset +
                            (bounds.width() - edgeInset * 2f) * fraction.coerceIn(0f, 1f)) to
                            bounds.centerY()
                }

        fun drawBasic(
                rail: RectF,
                railTrack: Int,
                railFill: Int,
                shader: Shader? = null
        ) {
            val radius = min(rail.width(), rail.height()) / 2f
            fillPaint.shader = null
            fillPaint.color = railTrack
            canvas.drawRoundRect(rail, radius, radius, fillPaint)
            if (SAMPLE_VOLUME <= .001f) return
            val played = if (vertical) {
                RectF(
                        rail.left,
                        rail.bottom - rail.height() * SAMPLE_VOLUME,
                        rail.right,
                        rail.bottom)
            } else {
                RectF(
                        rail.left,
                        rail.top,
                        rail.left + rail.width() * SAMPLE_VOLUME,
                        rail.bottom)
            }
            fillPaint.shader = shader
            fillPaint.color = railFill
            canvas.drawRoundRect(played, radius, radius, fillPaint)
            fillPaint.shader = null
        }

        when (volumeStyle) {
            "beads" -> {
                val count = 9
                val beadRadius = min(bounds.width(), bounds.height()) * .48f
                fillPaint.shader = null
                repeat(count) { index ->
                    val fraction = index.toFloat() / (count - 1)
                    val bead = point(fraction, beadRadius)
                    fillPaint.color = if (SAMPLE_VOLUME > .001f &&
                            fraction <= SAMPLE_VOLUME + .001f) {
                        fillColor
                    } else {
                        ColorUtils.setAlphaComponent(fillColor, 0x2C)
                    }
                    val scale = if (index == 0 || index == count - 1) 1f else .78f
                    canvas.drawCircle(bead.first, bead.second, beadRadius * scale, fillPaint)
                }
                return
            }
            "dual" -> {
                val first: RectF
                val second: RectF
                if (vertical) {
                    val railWidth = bounds.width() * .3f
                    first = RectF(bounds.left, bounds.top, bounds.left + railWidth, bounds.bottom)
                    second = RectF(bounds.right - railWidth, bounds.top, bounds.right, bounds.bottom)
                } else {
                    val railHeight = bounds.height() * .3f
                    first = RectF(bounds.left, bounds.top, bounds.right, bounds.top + railHeight)
                    second = RectF(bounds.left, bounds.bottom - railHeight, bounds.right, bounds.bottom)
                }
                drawBasic(first, ColorUtils.setAlphaComponent(accent, 0x28), accent)
                drawBasic(second, ColorUtils.setAlphaComponent(secondary, 0x28), secondary)
                return
            }
            "pulse" -> {
                val slim = RectF(bounds)
                if (vertical) {
                    slim.inset(bounds.width() * .31f, 0f)
                } else {
                    slim.inset(0f, bounds.height() * .31f)
                }
                drawBasic(slim, ColorUtils.setAlphaComponent(accent, 0x22), accent)
                val head = point(SAMPLE_VOLUME)
                val base = min(bounds.width(), bounds.height())
                fillPaint.shader = null
                fillPaint.color = ColorUtils.setAlphaComponent(accent, 0x20)
                canvas.drawCircle(head.first, head.second, base * 1.3f, fillPaint)
                fillPaint.color = ColorUtils.setAlphaComponent(accent, 0x60)
                canvas.drawCircle(head.first, head.second, base * .78f, fillPaint)
                fillPaint.color = accent
                canvas.drawCircle(head.first, head.second, base * .34f, fillPaint)
                return
            }
            "segments", "steps" -> {
                val count = if (volumeStyle == "steps") 9 else 10
                val gap = dp(if (volumeStyle == "steps") 2f else 3f)
                val axisLength = if (vertical) bounds.height() else bounds.width()
                val segmentLength = (axisLength - gap * (count - 1)) / count
                fillPaint.shader = null
                repeat(count) { index ->
                    val fraction = (index + 1f) / count
                    fillPaint.color = if (fraction <= SAMPLE_VOLUME + .001f) {
                        fillColor
                    } else {
                        ColorUtils.setAlphaComponent(fillColor, if (volumeStyle == "steps") 0x2C else 0x28)
                    }
                    val crossScale = if (volumeStyle == "steps") .3f + .7f * fraction else 1f
                    val segment = if (vertical) {
                        val bottom = bounds.bottom - index * (segmentLength + gap)
                        val segmentWidth = bounds.width() * crossScale
                        RectF(
                                bounds.centerX() - segmentWidth / 2f,
                                bottom - segmentLength,
                                bounds.centerX() + segmentWidth / 2f,
                                bottom)
                    } else {
                        val left = bounds.left + index * (segmentLength + gap)
                        val segmentHeight = bounds.height() * crossScale
                        RectF(
                                left,
                                bounds.centerY() - segmentHeight / 2f,
                                left + segmentLength,
                                bounds.centerY() + segmentHeight / 2f)
                    }
                    val radius = if (volumeStyle == "steps") dp(1.08f)
                    else min(segment.width(), segment.height()) / 2f
                    canvas.drawRoundRect(segment, radius, radius, fillPaint)
                }
                return
            }
        }

        val shader = when (volumeStyle) {
            "gradient", "aurora", "prism" -> if (vertical) {
                LinearGradient(
                        bounds.centerX(), bounds.bottom, bounds.centerX(), bounds.top,
                        tertiary, secondary, Shader.TileMode.CLAMP)
            } else {
                LinearGradient(
                        bounds.left, bounds.centerY(), bounds.right, bounds.centerY(),
                        tertiary, secondary, Shader.TileMode.CLAMP)
            }
            "chrome" -> {
                val colors = intArrayOf(
                        0xFF555A60.toInt(), 0xFFF8FAFC.toInt(), 0xFF81868D.toInt(),
                        Color.WHITE, 0xFF4D5258.toInt())
                val stops = floatArrayOf(0f, .2f, .48f, .72f, 1f)
                if (vertical) {
                    LinearGradient(
                            bounds.left, bounds.centerY(), bounds.right, bounds.centerY(),
                            colors, stops, Shader.TileMode.CLAMP)
                } else {
                    LinearGradient(
                            bounds.centerX(), bounds.top, bounds.centerX(), bounds.bottom,
                            colors, stops, Shader.TileMode.CLAMP)
                }
            }
            "spectrum" -> {
                val colors = intArrayOf(
                        0xFFFF3B30.toInt(), 0xFFFFCC00.toInt(), 0xFF34C759.toInt(),
                        0xFF32ADE6.toInt(), 0xFF5856D6.toInt(), 0xFFAF52DE.toInt())
                if (vertical) {
                    LinearGradient(
                            bounds.centerX(), bounds.bottom, bounds.centerX(), bounds.top,
                            colors, null, Shader.TileMode.CLAMP)
                } else {
                    LinearGradient(
                            bounds.left, bounds.centerY(), bounds.right, bounds.centerY(),
                            colors, null, Shader.TileMode.CLAMP)
                }
            }
            else -> null
        }
        drawBasic(bounds, trackColor, fillColor, shader)

        if (volumeStyle == "chrome" && SAMPLE_VOLUME > .001f) {
            val head = point(SAMPLE_VOLUME)
            strokePaint.shader = null
            strokePaint.pathEffect = null
            strokePaint.strokeCap = Paint.Cap.ROUND
            strokePaint.strokeWidth = min(bounds.width(), bounds.height()) * .16f
            strokePaint.color = 0xB3FFFFFF.toInt()
            if (vertical) {
                val x = bounds.centerX() - bounds.width() * .18f
                canvas.drawLine(x, bounds.bottom, x, head.second, strokePaint)
            } else {
                val y = bounds.centerY() - bounds.height() * .18f
                canvas.drawLine(bounds.left, y, head.first, y, strokePaint)
            }
        }

        if (volumeStyle in setOf("material", "glass", "tonal")) {
            val head = point(SAMPLE_VOLUME)
            fillPaint.shader = null
            fillPaint.color = Color.WHITE
            canvas.drawCircle(
                    head.first,
                    head.second,
                    min(bounds.width(), bounds.height()) * .36f,
                    fillPaint)
        }
    }

    private fun drawPreviewSeekTimeline(
            canvas: Canvas,
            geometry: PreviewGeometry,
            dp: (Float) -> Float,
            segmented: Boolean,
            centerY: Float = geometry.cy + dp(25f)
    ) {
        val width = min(geometry.bounds.width() * .68f, dp(132f))
        val height = dp(if (segmented) 9f else 7f)
        val cy = centerY
        val bounds = RectF(
                geometry.cx - width / 2f, cy - height / 2f,
                geometry.cx + width / 2f, cy + height / 2f)
        val accent = resolveTint(progressMode, progressCustom, progressDesaturated)
        if (segmented) {
            val count = 12
            val gap = dp(3f)
            val segmentWidth = (bounds.width() - gap * (count - 1)) / count
            repeat(count) { index ->
                val left = bounds.left + index * (segmentWidth + gap)
                fillPaint.color = if ((index + 1f) / count <= SAMPLE_PROGRESS) {
                    accent
                } else {
                    0x32FFFFFF
                }
                canvas.drawRoundRect(left, bounds.top, left + segmentWidth, bounds.bottom,
                        segmentWidth / 2f, segmentWidth / 2f, fillPaint)
            }
        } else {
            fillPaint.color = 0x38FFFFFF
            canvas.drawRoundRect(bounds, height / 2f, height / 2f, fillPaint)
            val fill = RectF(bounds.left, bounds.top,
                    bounds.left + bounds.width() * SAMPLE_PROGRESS, bounds.bottom)
            fillPaint.color = accent
            canvas.drawRoundRect(fill, height / 2f, height / 2f, fillPaint)
            fillPaint.color = Color.WHITE
            canvas.drawCircle(fill.right, cy, dp(4.5f), fillPaint)
            strokePaint.style = Paint.Style.STROKE
            strokePaint.strokeWidth = dp(1f)
            strokePaint.color = ColorUtils.setAlphaComponent(accent, 0xD8)
            canvas.drawCircle(fill.right, cy, dp(6f), strokePaint)
        }
    }

    private fun drawPreviewVerticalSeekTimeline(
            canvas: Canvas,
            geometry: PreviewGeometry,
            dp: (Float) -> Float
    ) {
        val width = dp(7f)
        val height = min(geometry.bounds.height() * .55f, dp(118f))
        val cx = if (seekLayout == "vertical_left") {
            geometry.bounds.left + geometry.bounds.width() * .22f
        } else {
            geometry.bounds.left + geometry.bounds.width() * .78f
        }
        val bounds = RectF(
                cx - width / 2f, geometry.cy - height / 2f,
                cx + width / 2f, geometry.cy + height / 2f)
        val accent = resolveTint(progressMode, progressCustom, progressDesaturated)
        fillPaint.shader = null
        fillPaint.color = 0x38FFFFFF
        canvas.drawRoundRect(bounds, width / 2f, width / 2f, fillPaint)
        val played = RectF(
                bounds.left,
                bounds.bottom - bounds.height() * SAMPLE_PROGRESS,
                bounds.right,
                bounds.bottom)
        fillPaint.color = accent
        canvas.drawRoundRect(played, width / 2f, width / 2f, fillPaint)
        fillPaint.color = Color.WHITE
        canvas.drawCircle(cx, played.top, dp(4.5f), fillPaint)
    }

    private fun drawPreviewSeekDial(
            canvas: Canvas,
            geometry: PreviewGeometry,
            dp: (Float) -> Float
    ) {
        val accent = resolveTint(progressMode, progressCustom, progressDesaturated)
        val stroke = dp(7f)
        val radius = min(geometry.bounds.width(), geometry.bounds.height()) * .20f
        val centerY = geometry.cy + dp(22f)
        val bounds = RectF(
                geometry.cx - radius, centerY - radius,
                geometry.cx + radius, centerY + radius)
        strokePaint.shader = null
        strokePaint.pathEffect = null
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeCap = Paint.Cap.ROUND
        strokePaint.strokeWidth = stroke
        strokePaint.color = 0x35FFFFFF
        canvas.drawArc(bounds, 135f, 270f, false, strokePaint)
        strokePaint.color = accent
        canvas.drawArc(bounds, 135f, 270f * SAMPLE_PROGRESS, false, strokePaint)
        val head = Math.toRadians((135f + 270f * SAMPLE_PROGRESS).toDouble())
        fillPaint.shader = null
        fillPaint.color = Color.WHITE
        canvas.drawCircle(
                geometry.cx + radius * cos(head).toFloat(),
                centerY + radius * sin(head).toFloat(),
                dp(4.5f), fillPaint)
    }

    private fun drawPreviewTwinSeekTimeline(
            canvas: Canvas,
            geometry: PreviewGeometry,
            dp: (Float) -> Float
    ) {
        val width = min(geometry.bounds.width() * .68f, dp(132f))
        val height = dp(7f)
        val accent = resolveTint(progressMode, progressCustom, progressDesaturated)
        val secondary = resolveSecondaryTint(progressMode, progressCustom, progressDesaturated)
        val left = geometry.cx - width / 2f
        val right = geometry.cx + width / 2f
        listOf(
                geometry.bounds.top + geometry.bounds.height() * .32f,
                geometry.bounds.top + geometry.bounds.height() * .68f
        ).forEachIndexed { index, cy ->
            val bounds = RectF(left, cy - height / 2f, right, cy + height / 2f)
            fillPaint.shader = null
            fillPaint.color = 0x2BFFFFFF
            canvas.drawRoundRect(bounds, height / 2f, height / 2f, fillPaint)
            val fraction = if (index == 0) SAMPLE_PROGRESS else 1f - SAMPLE_PROGRESS
            val fill = if (index == 0) {
                RectF(left, bounds.top, left + width * fraction, bounds.bottom)
            } else {
                RectF(right - width * fraction, bounds.top, right, bounds.bottom)
            }
            fillPaint.color = if (index == 0) accent else secondary
            canvas.drawRoundRect(fill, height / 2f, height / 2f, fillPaint)
        }
    }

    // --- Contextual quick-panel and queue previews ---

    private data class PreviewSkin(
            val fill: Int = Color.TRANSPARENT,
            val onColor: Int = Color.WHITE,
            val cornerDp: Float = 20f,
            val bottomLeftCornerDp: Float? = null,
            val strokeColor: Int = Color.TRANSPARENT,
            val strokeDp: Float = 0f,
            val gradientTop: Int? = null,
            val gradientMiddle: Int? = null,
            val gradientBottom: Int? = null,
            val keylineColor: Int = Color.TRANSPARENT
    )

    private fun drawQuickPanelSurface(
            canvas: Canvas,
            geometry: PreviewGeometry,
            dp: (Float) -> Float
    ) {
        val accent = quickPanelAccent()
        drawConfiguredOverlayBackdrop(
                canvas, geometry, quickPanelStyle,
                accent, quickPanelSecondaryAccent(), quickPanelTertiaryAccent())
        val metadataColor = when (quickPanelStyle) {
            "light" -> 0xFF111111.toInt()
            "terminal" -> TERMINAL_GREEN
            else -> Color.WHITE
        }

        // Dense spatial layouts stand alone and Hero/Column/Split keep one metadata line,
        // MainActivity.applyQuickPanelLayout on the watch.
        val metadataVisible = quickPanelLayout !in setOf("grid", "orbit", "diamond")
        val showTrackTitle = showTrackTitle && metadataVisible
        val renderArtist = showTrackArtist && metadataVisible &&
                quickPanelLayout !in setOf("hero", "column", "split")
        val metadataLines = (if (showTrackTitle) 1 else 0) + (if (renderArtist) 1 else 0)
        val controlsShift = if (quickPanelLayout == "stacked") {
            dp(when (metadataLines) {
                0 -> -20f
                1 -> -8f
                else -> 0f
            })
        } else {
            0f
        }
        // Matches the awake Expressive/Material metadata keyline instead of crowding the
        // upper bezel. Baseline sits around 24% of the common 192dp viewport.
        val titleY = if (renderArtist) geometry.cy - dp(49f) else geometry.cy - dp(40f)
        val artistY = if (showTrackTitle) geometry.cy - dp(31f) else geometry.cy - dp(40f)

        textPaint.style = Paint.Style.FILL
        textPaint.textAlign = Paint.Align.CENTER
        if (showTrackTitle) {
            textPaint.typeface = watchUiTypeface(bold = true)
            textPaint.textSize = dp(18f)
            textPaint.color = metadataColor
            canvas.drawText(
                    ellipsize(displayTitle(), geometry.radius * 1.55f),
                    geometry.cx,
                    titleY,
                    textPaint
            )
        }
        if (renderArtist) {
            textPaint.typeface = watchUiTypeface(bold = true)
            textPaint.textSize = dp(13f)
            textPaint.color = ColorUtils.setAlphaComponent(metadataColor, 0xB3)
            canvas.drawText(
                    ellipsize(displayArtist(), geometry.radius * 1.55f),
                    geometry.cx,
                    artistY,
                    textPaint
            )
        }

        val buttonWidth = dp(54f)
        val buttonHeight = dp(50f)
        val gap = dp(6f)
        val centerY = geometry.cy + dp(12f) + controlsShift
        val defaultIcons = intArrayOf(
                commonR.drawable.action_like,
                commonR.drawable.action_shuffle,
                commonR.drawable.action_repeat
        )
        val previewButtons: List<Triple<PreviewActionIcon?, Int, Boolean>> =
                if (quickPanelSource == "session") {
            // Two buttons deliberately exercise the same collapse/re-centering used by Spotify.
            listOf(
                    Triple<PreviewActionIcon?, Int, Boolean>(
                            null, commonR.drawable.action_replay_10, false),
                    Triple(null, commonR.drawable.action_forward_10, false)
            )
        } else {
            val buttons = ArrayList<Triple<PreviewActionIcon?, Int, Boolean>>(3)
            for (index in QuickPanelButtons.ALL_SLOTS.indices) {
                val slot = QuickPanelButtons.ALL_SLOTS[index]
                val action = quickPanelIcons[slot]
                val key = action?.actionKey.orEmpty()
                if (!key.endsWith(".NullAction")) {
                    buttons.add(Triple(
                            action,
                            defaultIcons[index],
                            key.endsWith(".ShuffleAction") || (action == null && index == 1)
                    ))
                }
            }
            buttons
        }
        // Slot geometry per layout, mirroring QuickActionsRowLayout.Arrangement and
        // applyHeroSlotEmphasis on the watch. "rows" draws no round slots at all - it renders the
        // same actions as labelled full-width rows further below.
        if (quickPanelLayout == "rows") {
            drawPreviewQuickPanelRows(canvas, geometry, dp, previewButtons, centerY, accent)
        } else {
            for ((index, button) in previewButtons.withIndex()) {
                val (action, fallbackIcon, active) = button
                val slotRect = previewQuickSlotRect(
                        index, previewButtons.size, geometry, centerY,
                        buttonWidth, buttonHeight, gap, dp)
                val skin = quickSkin(quickPanelStyle, active, row = false, accent = accent)
                drawSkin(canvas, slotRect, skin, dp)
                if (!active) drawReducedSlotMark(canvas, slotRect, dp, accent)
                val iconSize = when {
                    quickPanelLayout == "hero" && index == 0 -> dp(26f)
                    quickPanelLayout == "carousel" && index == previewButtons.size / 2 -> dp(25f)
                    quickPanelLayout == "split" && index == 0 -> dp(25f)
                    else -> dp(20f)
                }
                drawActionIcon(canvas, action, fallbackIcon,
                        slotRect.centerX(), slotRect.centerY(), iconSize, skin.onColor)
            }
        }

        val longAction = if (quickPanelSource == "session") {
            null
        } else {
            quickPanelIcons[QuickPanelButtons.SLOT_LONG]
        }
        val longRowHidden = longAction?.actionKey.orEmpty().endsWith(".NullAction")
        if (!longRowHidden) {
            val rowWidth = geometry.bounds.width() * .88f
            val rowHeight = dp(54f)
            val rowY = when (quickPanelLayout) {
                "column", "split" -> geometry.bounds.bottom - dp(22f)
                else -> geometry.cy + dp(70f) + controlsShift
            }
            val rowRect = RectF(
                    geometry.cx - rowWidth / 2f,
                    rowY - rowHeight / 2f,
                    geometry.cx + rowWidth / 2f,
                    rowY + rowHeight / 2f
            )
            val rowSkin = upNextRowSkin(accent)
            drawSkin(canvas, rowRect, rowSkin, dp)
            drawActionIcon(
                    canvas,
                    longAction,
                    if (quickPanelSource == "session") R.drawable.ic_playlist_play
                    else R.drawable.ic_playlist_play,
                    rowRect.left + dp(21f),
                    rowY,
                    dp(20f),
                    rowSkin.onColor
            )
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.typeface = watchUiTypeface(bold = true)
            textPaint.textSize = dp(12f)
            textPaint.color = rowSkin.onColor
            val rowTitle = when {
                quickPanelSource == "session" ->
                    context.getString(R.string.quick_panel_default_up_next)
                !longAction?.title.isNullOrBlank() -> longAction.title
                else -> context.getString(R.string.preview_sample_title)
            }
            canvas.drawText(ellipsize(rowTitle,
                    rowWidth - dp(56f)), rowRect.left + dp(37f), rowY - dp(2f), textPaint)
            textPaint.typeface = watchUiTypeface(bold = false)
            textPaint.textSize = dp(9f)
            textPaint.color = ColorUtils.setAlphaComponent(rowSkin.onColor, 0xB3)
            val rowSubtitle = displayTitle()
            canvas.drawText(ellipsize(rowSubtitle, rowWidth - dp(56f)),
                    rowRect.left + dp(37f), rowY + dp(11f), textPaint)
        }
        textPaint.textAlign = Paint.Align.CENTER
    }

    /** Skin for the Up Next row, honouring its dedicated style (mirrors upNextPillBackground /
     *  upNextPillTint on the watch). "follow" defers to the quick-panel row skin. */
    private fun upNextRowSkin(accent: Int): PreviewSkin {
        if (upNextPillStyle == "follow") {
            return quickSkin(quickPanelStyle, active = false, row = true, accent = accent)
        }
        val corner = quickPanelRowCornerDp(quickPanelStyle)
        return when (upNextPillStyle) {
            "transparent" -> PreviewSkin(fill = Color.TRANSPARENT, onColor = Color.WHITE, cornerDp = corner)
            "accent" -> PreviewSkin(fill = accent, onColor = contrastingColor(accent), cornerDp = corner)
            "translucent" -> PreviewSkin(fill = 0x40FFFFFF, onColor = Color.WHITE, cornerDp = corner)
            "white" -> PreviewSkin(fill = 0xF2FFFFFF.toInt(), onColor = LIGHT_ON, cornerDp = corner)
            "white_blur" -> PreviewSkin(fill = 0x73FFFFFF, onColor = LIGHT_ON, cornerDp = corner)
            "black" -> PreviewSkin(fill = 0xCC000000.toInt(), onColor = Color.WHITE, cornerDp = corner)
            "dynamic" -> {
                val fill = tonal(accent, 0.24f, 0.25f, 0.60f)
                PreviewSkin(fill = fill, onColor = contrastingColor(fill), cornerDp = corner)
            }
            else -> quickSkin(quickPanelStyle, active = false, row = true, accent = accent)
        }
    }

    /** The awake player Up Next pill's fill + text, from the shared Up Next pill style (mirrors
     *  MainActivity.upNextPillFillColor / awakeUpNextPillTint). */
    private fun awakePillColors(): Pair<Int, Int> {
        val accent = albumAccent()
        return when (upNextPillStyle) {
            "transparent" -> Color.TRANSPARENT to Color.WHITE
            "accent" -> accent to contrastingColor(accent)
            "translucent" -> 0x40FFFFFF to Color.WHITE
            "white" -> 0xF2FFFFFF.toInt() to LIGHT_ON
            "white_blur" -> 0x73FFFFFF to LIGHT_ON
            "black" -> 0xCC000000.toInt() to Color.WHITE
            "dynamic" -> tonal(accent, 0.24f, 0.25f, 0.60f).let { it to contrastingColor(it) }
            else -> ColorUtils.setAlphaComponent(accent, 0x38) to Color.WHITE
        }
    }

    private fun quickSkin(
            style: String,
            active: Boolean,
            row: Boolean,
            accent: Int
    ): PreviewSkin {
        val secondary = albumSecondaryAccent()
        val tertiary = albumTertiaryAccent()
        // Round slot buttons stay full-stadium; full-width rows use each style's authored
        // silhouette. Terminal keeps its square corners. Mirrors
        // quickPanelRowBackground() / inactiveQuickButtonBackground() on the watch.
        val corner = if (row) {
            quickPanelRowCornerDp(style)
        } else {
            when (style) {
                "terminal" -> 0f
                "slab" -> SLAB_CORNER_DP
                "rail" -> 6f
                else -> 999f
            }
        }
        val bottomLeftCorner = if (style == "bubble") 6f else null

        if (active && !row) {
            when (style) {
                "prism", "holo" -> return PreviewSkin(
                        onColor = Color.WHITE,
                        cornerDp = corner,
                        strokeColor = 0x66FFFFFF,
                        strokeDp = 1.5f,
                        gradientTop = tonal(tertiary, if (style == "holo") .58f else .34f, .25f, .78f),
                        gradientMiddle = tonal(accent, if (style == "holo") .44f else .42f, .25f, .78f),
                        gradientBottom = tonal(secondary, if (style == "holo") .52f else .28f, .25f, .78f))
                "chrome" -> return PreviewSkin(
                        onColor = Color.BLACK,
                        cornerDp = corner,
                        strokeColor = 0x99FFFFFF.toInt(),
                        strokeDp = 1f,
                        gradientTop = 0xFF6E737C.toInt(),
                        gradientMiddle = Color.WHITE,
                        gradientBottom = 0xFF737780.toInt())
                "sunset" -> return PreviewSkin(
                        onColor = Color.WHITE,
                        cornerDp = corner,
                        gradientTop = 0xFFFF7A45.toInt(),
                        gradientMiddle = 0xFFFF4D79.toInt(),
                        gradientBottom = 0xFF8C3BE8.toInt())
                "rail" -> return PreviewSkin(
                        fill = ColorUtils.setAlphaComponent(accent, 0x20),
                        onColor = liftedAccent(accent),
                        cornerDp = corner,
                        strokeColor = liftedAccent(accent),
                        strokeDp = 1.5f,
                        keylineColor = liftedAccent(accent))
                "soft" -> {
                    val fill = tonal(accent, .78f, .22f, .74f)
                    return PreviewSkin(
                            fill = fill, onColor = contrastingColor(fill), cornerDp = corner)
                }
                "bubble" -> return PreviewSkin(
                        fill = ColorUtils.setAlphaComponent(
                                tonal(accent, .56f, .38f, .88f), 0xD8),
                        onColor = Color.WHITE,
                        cornerDp = corner,
                        bottomLeftCornerDp = bottomLeftCorner,
                        strokeColor = 0x70FFFFFF,
                        strokeDp = 1f)
            }
        }

        if (active && !row) {
            val fill = when (style) {
                "contrast" -> Color.WHITE
                "terminal" -> TERMINAL_GREEN
                "mono" -> MONO_ACTIVE
                else -> accent
            }
            return PreviewSkin(
                    fill = fill,
                    onColor = contrastingColor(fill),
                    cornerDp = corner
            )
        }

        return when (style) {
            "glass_white" -> PreviewSkin(
                    fill = 0xB3FFFFFF.toInt(), onColor = LIGHT_ON, cornerDp = corner)
            "glass_tonal" -> {
                val fill = ColorUtils.setAlphaComponent(
                        tonal(accent, .74f, .40f, .92f), 0xB3)
                PreviewSkin(fill = fill, onColor = contrastingColor(
                        tonal(accent, .74f, .40f, .92f)), cornerDp = corner)
            }
            "minimal" -> PreviewSkin(cornerDp = corner,
                    strokeColor = 0x66FFFFFF, strokeDp = 1.5f)
            "material" -> PreviewSkin(fill = MATERIAL_SURFACE, cornerDp = corner)
            "tonal" -> {
                val fill = tonal(accent, .74f, .40f, .92f)
                PreviewSkin(fill = fill, onColor = contrastingColor(fill), cornerDp = corner)
            }
            "neon" -> PreviewSkin(onColor = accent, cornerDp = corner,
                    strokeColor = accent, strokeDp = 2f)
            "light" -> PreviewSkin(fill = LIGHT_SURFACE, onColor = LIGHT_ON, cornerDp = corner)
            "gradient" -> PreviewSkin(cornerDp = corner,
                    gradientTop = tonal(accent, 0.34f, 0.25f, 0.60f),
                    gradientBottom = tonal(secondary, 0.16f, 0.25f, 0.60f))
            "mono" -> PreviewSkin(fill = MONO_SURFACE, cornerDp = corner)
            "outline" -> PreviewSkin(cornerDp = corner,
                    strokeColor = Color.WHITE, strokeDp = 1.25f)
            "outline_glass_white" -> PreviewSkin(
                    fill = 0x80FFFFFF.toInt(), onColor = LIGHT_ON, cornerDp = corner,
                    strokeColor = Color.WHITE, strokeDp = 1.25f)
            "duotone" -> PreviewSkin(
                    fill = tonal(secondary, 0.24f, 0.25f, 0.60f),
                    cornerDp = corner)
            "prism" -> PreviewSkin(
                    onColor = Color.WHITE,
                    cornerDp = corner,
                    strokeColor = 0x66FFFFFF,
                    strokeDp = 1f,
                    gradientTop = tonal(tertiary, .20f, .25f, .60f),
                    gradientMiddle = tonal(accent, .30f, .25f, .60f),
                    gradientBottom = tonal(secondary, .16f, .25f, .60f))
            "contrast" -> PreviewSkin(fill = Color.BLACK, cornerDp = corner,
                    strokeColor = Color.WHITE, strokeDp = 2f)
            "terminal" -> PreviewSkin(onColor = TERMINAL_GREEN, cornerDp = 0f,
                    strokeColor = TERMINAL_GREEN, strokeDp = 1.5f)
            "frost" -> PreviewSkin(fill = 0x33FFFFFF, cornerDp = corner)
            // Reduced styles: the round slots drop their container entirely, but the full-width
            // rows keep a faint surface so a list item's tap area stays visible. Mirrors
            // inactiveQuickButtonBackground() / quickPanelRowBackground() on the watch.
            "ghost", "dot" -> PreviewSkin(
                    fill = if (row) 0x0DFFFFFF else Color.TRANSPARENT, cornerDp = corner)
            "mist" -> PreviewSkin(fill = 0x14FFFFFF, cornerDp = corner)
            "slab" -> PreviewSkin(fill = SLAB_SURFACE, cornerDp = corner)
            "soft" -> {
                val fill = tonal(accent, .27f, .18f, .60f)
                PreviewSkin(
                        fill = fill,
                        onColor = liftedAccent(accent),
                        cornerDp = corner)
            }
            "chrome" -> PreviewSkin(
                    onColor = Color.WHITE,
                    cornerDp = corner,
                    strokeColor = 0x70FFFFFF,
                    strokeDp = 1f,
                    gradientTop = 0xFF4C5058.toInt(),
                    gradientMiddle = 0xFF858A93.toInt(),
                    gradientBottom = 0xFF272A30.toInt())
            "holo" -> PreviewSkin(
                    onColor = Color.WHITE,
                    cornerDp = corner,
                    strokeColor = ColorUtils.setAlphaComponent(liftedAccent(tertiary), 0xA0),
                    strokeDp = 1.25f,
                    gradientTop = ColorUtils.setAlphaComponent(tertiary, 0x8C),
                    gradientMiddle = ColorUtils.setAlphaComponent(accent, 0x70),
                    gradientBottom = ColorUtils.setAlphaComponent(secondary, 0x8C))
            "bubble" -> PreviewSkin(
                    fill = ColorUtils.setAlphaComponent(accent, 0x38),
                    onColor = Color.WHITE,
                    cornerDp = corner,
                    bottomLeftCornerDp = bottomLeftCorner,
                    strokeColor = 0x52FFFFFF,
                    strokeDp = 1f)
            "rail" -> PreviewSkin(
                    fill = if (row) 0x1AFFFFFF else Color.TRANSPARENT,
                    onColor = Color.WHITE,
                    cornerDp = corner,
                    strokeColor = if (row) 0x42FFFFFF else ColorUtils.setAlphaComponent(accent, 0x8C),
                    strokeDp = 1f,
                    keylineColor = if (row) liftedAccent(accent) else Color.TRANSPARENT)
            "sunset" -> PreviewSkin(
                    onColor = Color.WHITE,
                    cornerDp = corner,
                    gradientTop = 0xB8FF7A45.toInt(),
                    gradientMiddle = 0xB8DF3D82.toInt(),
                    gradientBottom = 0xB8783DE8.toInt())
            "ink" -> if (row) {
                PreviewSkin(
                        fill = 0x0DFFFFFF, cornerDp = corner,
                        strokeColor = ColorUtils.setAlphaComponent(liftedAccent(accent), 0xB3),
                        strokeDp = 1.25f)
            } else {
                PreviewSkin(fill = Color.TRANSPARENT, cornerDp = corner)
            }
            else -> PreviewSkin(fill = 0xB3161619.toInt(), cornerDp = corner)
        }
    }

    private fun quickPanelRowCornerDp(style: String): Float = when (style) {
        "terminal" -> 0f
        "slab" -> SLAB_CORNER_DP
        "rail" -> 6f
        "chrome" -> 12f
        "sunset" -> 20f
        "holo" -> 26f
        "bubble" -> 28f
        "soft" -> 30f
        else -> 26f
    }

    /**
     * The bottom-edge mark the chromeless "ink" and "dot" styles put under a round slot in place of
     * a container, matching underlineDrawable/markerDrawable on the watch. Only the round slots
     * need it - their rows keep a real surface.
     */
    private fun drawReducedSlotMark(canvas: Canvas, rect: RectF, dp: (Float) -> Float, accent: Int) {
        val mark = liftedAccent(accent)
        fillPaint.shader = null
        fillPaint.color = mark
        when (quickPanelStyle) {
            "ink" -> {
                val thickness = dp(2f)
                canvas.drawRoundRect(
                        RectF(rect.left, rect.bottom - thickness, rect.right, rect.bottom),
                        thickness / 2f, thickness / 2f, fillPaint)
            }
            "dot" -> {
                val radius = dp(2.5f)
                canvas.drawCircle(rect.centerX(), rect.bottom - radius, radius, fillPaint)
            }
        }
    }

    private fun drawQueueSurface(
            canvas: Canvas,
            geometry: PreviewGeometry,
            dp: (Float) -> Float
    ) {
        fillPaint.shader = null
        fillPaint.color = Color.BLACK
        canvas.drawRect(geometry.bounds, fillPaint)
        drawSmallClock(canvas, geometry.cx, geometry.bounds.top + dp(20f), dp, sizeSp = 16f)

        textPaint.style = Paint.Style.FILL
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = watchUiTypeface(bold = true)
        textPaint.textSize = dp(13f)
        textPaint.color = Color.WHITE
        canvas.drawText(ellipsize(displayTitle(), geometry.radius * 1.45f),
                geometry.cx, geometry.bounds.top + dp(39f), textPaint)
        textPaint.typeface = watchUiTypeface(bold = false)
        textPaint.textSize = dp(9.5f)
        textPaint.color = accentForText(queueAccent())
        canvas.drawText(ellipsize(displayArtist(), geometry.radius * 1.45f),
                geometry.cx, geometry.bounds.top + dp(52f), textPaint)

        val titles = listOf(
                context.getString(R.string.preview_sample_title),
                displayTitle(),
                context.getString(R.string.preview_sample_title)
        )
        val subtitles = listOf(displayArtist(), displayArtist(), displayArtist())
        val availableWidth = min(geometry.bounds.width() - dp(28f), dp(174f))
        // Content height + the style's padding rhythm, mirroring QueueRow on the watch. The
        // preview's reference watch is narrower than a real one, so this scales via dp().
        val rowHeight = dp(when (listRowSize) {
            "compact" -> 31f
            "tall" -> 61f
            "xtall" -> 87f
            else -> 39f
        })
        val spacing = dp(queueSpacingDp(queueStyle))
        val firstCenter = geometry.bounds.top + dp(75f)

        for (index in titles.indices) {
            val active = index == 1
            val y = firstCenter + index * (rowHeight + spacing)
            val rect = RectF(
                    geometry.cx - availableWidth / 2f,
                    y - rowHeight / 2f,
                    geometry.cx + availableWidth / 2f,
                    y + rowHeight / 2f
            )
            val skin = queueSkin(queueStyle, active, queueAccent())
            drawSkin(canvas, rect, skin, dp)
            // Cover style: the row's own art fills the pill under a legibility scrim, matching
            // the watch's QueueStyle.COVER (which falls back to the Glass pill without artwork).
            if (queueStyle in MiscPreferences.COVER_LIST_STYLES) {
                drawQueueCover(canvas, rect, dp(skin.cornerDp), queueStyle)
            }
            if (Color.alpha(skin.keylineColor) > 0) {
                fillPaint.shader = null
                fillPaint.color = skin.keylineColor
                canvas.drawRoundRect(
                        rect.left + dp(4f),
                        rect.top + dp(7f),
                        rect.left + dp(7f),
                        rect.bottom - dp(7f),
                        dp(2f), dp(2f), fillPaint
                )
            }

            val left = rect.left + dp(if (active) 27f else 14f)
            if (active) {
                drawQueueEqualizer(canvas, rect.left + dp(14f), y, skin.onColor, dp)
            }
            textPaint.style = Paint.Style.FILL
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.typeface = watchUiTypeface(bold = true)
            textPaint.textSize = dp(10.5f)
            textPaint.color = skin.onColor
            canvas.drawText(ellipsize(titles[index], rect.right - left - dp(8f)),
                    left, y - dp(1f), textPaint)
            textPaint.typeface = watchUiTypeface(bold = false)
            textPaint.textSize = dp(8f)
            textPaint.color = ColorUtils.setAlphaComponent(skin.onColor, 0xA6)
            canvas.drawText(ellipsize(subtitles[index], rect.right - left - dp(8f)),
                    left, y + dp(11f), textPaint)
        }
        textPaint.textAlign = Paint.Align.CENTER
    }

    /** Center-crops the current cover into [rect], rounded to the row's corner, then lays the
     *  same horizontal scrim the watch uses (see QueueScreen.coverScrim) so the title stays
     *  readable over arbitrary artwork. */
    private fun drawQueueCover(canvas: Canvas, rect: RectF, corner: Float, style: String) {
        val art = if (style == "cover_blur") {
            displayedOverlayBlurredArt() ?: displayedArt()
        } else {
            displayedArt()
        } ?: return
        val save = canvas.save()
        val clip = Path().apply { addRoundRect(rect, corner, corner, Path.Direction.CW) }
        canvas.clipPath(clip)

        // Center-crop: scale so the shorter source axis covers the row, then center the overflow.
        val scale = maxOf(rect.width() / art.width, rect.height() / art.height)
        val drawWidth = art.width * scale
        val drawHeight = art.height * scale
        canvas.drawBitmap(
                art,
                null,
                RectF(
                        rect.centerX() - drawWidth / 2f,
                        rect.centerY() - drawHeight / 2f,
                        rect.centerX() + drawWidth / 2f,
                        rect.centerY() + drawHeight / 2f
                ),
                null
        )

        fillPaint.shader = LinearGradient(
                rect.left, 0f, rect.right, 0f,
                intArrayOf(
                        ColorUtils.setAlphaComponent(Color.BLACK, 0xBD),
                        ColorUtils.setAlphaComponent(Color.BLACK, 0x75),
                        ColorUtils.setAlphaComponent(Color.BLACK, 0x38)
                ),
                floatArrayOf(0f, .55f, 1f),
                Shader.TileMode.CLAMP
        )
        canvas.drawRect(rect, fillPaint)
        fillPaint.shader = null
        if (style == "cover_tonal") {
            fillPaint.color = ColorUtils.setAlphaComponent(queueAccent(), 0x52)
            canvas.drawRect(rect, fillPaint)
        }
        canvas.restoreToCount(save)
    }

    private fun drawQueueEqualizer(
            canvas: Canvas,
            x: Float,
            y: Float,
            color: Int,
            dp: (Float) -> Float
    ) {
        fillPaint.shader = null
        fillPaint.color = color
        val widths = dp(2.2f)
        val heights = floatArrayOf(7f, 12f, 9f)
        for (index in heights.indices) {
            val left = x + dp((index - 1) * 3.8f) - widths / 2f
            val height = dp(heights[index])
            canvas.drawRoundRect(left, y - height / 2f, left + widths, y + height / 2f,
                    widths, widths, fillPaint)
        }
    }

    /** QueueActivity intentionally keeps the extracted album accent even when the player face's
     *  dynamic-accent toggle is off. */
    private fun queueAccent(): Int = liveAccent ?: SAMPLE_ALBUM_ACCENT

    private fun queueSpacingDp(style: String): Float = when (style) {
        "minimal", "terminal", "ink", "rail" -> 2f
        "material", "tonal", "light", "gradient", "duotone", "prism", "frost",
        "soft", "bubble", "sunset", "holo" -> 7f
        else -> 5f
    }

    private fun queueSkin(style: String, active: Boolean, accent: Int): PreviewSkin {
        val lightAccent = accentForSurface(accent)
        val secondary = liveSecondaryAccent ?: if (liveAccent == null) {
            SAMPLE_ALBUM_SECONDARY
        } else {
            sameHueTone(accent, .42f)
        }
        val tertiary = liveTertiaryAccent ?: if (liveAccent == null) {
            SAMPLE_ALBUM_TERTIARY
        } else {
            sameHueTone(accent, .68f)
        }
        return when (style) {
            "minimal" -> PreviewSkin(
                    onColor = if (active) accent else Color.WHITE,
                    cornerDp = 0f,
                    keylineColor = if (active) accent else Color.TRANSPARENT)
            "material" -> PreviewSkin(
                    fill = MATERIAL_SURFACE,
                    onColor = if (active) accent else Color.WHITE,
                    cornerDp = 12f,
                    keylineColor = if (active) accent else Color.TRANSPARENT)
            "tonal" -> PreviewSkin(
                    fill = if (active) lightAccent else tonal(accent, 0.22f, 0.25f, 0.60f),
                    onColor = if (active) Color.BLACK else Color.WHITE,
                    cornerDp = 28f)
            "neon" -> PreviewSkin(
                    onColor = if (active) accent else Color.WHITE,
                    cornerDp = 18f,
                    strokeColor = if (active) accent else ColorUtils.setAlphaComponent(accent, 0x80),
                    strokeDp = if (active) 2f else 1f)
            "light" -> PreviewSkin(
                    fill = if (active) lightAccent else LIGHT_SURFACE,
                    onColor = if (active) Color.BLACK else LIGHT_ON,
                    cornerDp = 20f)
            "gradient" -> PreviewSkin(
                    onColor = if (active) Color.BLACK else Color.WHITE,
                    cornerDp = 22f,
                    gradientTop = if (active) lightAccent else tonal(accent, 0.26f, 0.25f, 0.60f),
                    gradientBottom = if (active) tonal(secondary, 0.55f, 0.25f, 0.60f)
                    else tonal(secondary, 0.13f, 0.25f, 0.60f))
            "mono" -> PreviewSkin(
                    fill = if (active) MONO_ACTIVE else MONO_SURFACE,
                    onColor = if (active) Color.BLACK else Color.WHITE,
                    cornerDp = 14f)
            "outline" -> PreviewSkin(
                    onColor = if (active) accent else Color.WHITE,
                    cornerDp = 16f,
                    strokeColor = if (active) accent else Color.WHITE,
                    strokeDp = if (active) 3f else 2.5f)
            "duotone" -> PreviewSkin(
                    fill = if (active) lightAccent
                    else tonal(secondary, 0.24f, 0.25f, 0.60f),
                    onColor = if (active) Color.BLACK else Color.WHITE,
                    cornerDp = 22f)
            "prism" -> PreviewSkin(
                    onColor = if (active) Color.BLACK else Color.WHITE,
                    cornerDp = 22f,
                    strokeColor = 0x61FFFFFF,
                    strokeDp = 1f,
                    gradientTop = if (active) tonal(tertiary, .52f, .25f, .60f)
                    else tonal(tertiary, .18f, .25f, .60f),
                    gradientMiddle = if (active) lightAccent
                    else tonal(accent, .28f, .25f, .60f),
                    gradientBottom = if (active) tonal(secondary, .46f, .25f, .60f)
                    else tonal(secondary, .14f, .25f, .60f))
            "contrast" -> PreviewSkin(
                    fill = if (active) Color.WHITE else Color.BLACK,
                    onColor = if (active) Color.BLACK else Color.WHITE,
                    cornerDp = 8f,
                    strokeColor = if (active) Color.TRANSPARENT else Color.WHITE,
                    strokeDp = if (active) 0f else 2f)
            "terminal" -> PreviewSkin(
                    onColor = TERMINAL_GREEN,
                    cornerDp = 0f,
                    strokeColor = TERMINAL_GREEN,
                    strokeDp = if (active) 2f else 1f)
            "frost" -> PreviewSkin(
                    fill = if (active) ColorUtils.setAlphaComponent(accent, 0x80) else 0x29FFFFFF,
                    cornerDp = 24f)
            "soft" -> {
                val fill = if (active) {
                    tonal(accent, .78f, .18f, .68f)
                } else {
                    tonal(accent, .24f, .16f, .54f)
                }
                PreviewSkin(
                        fill = fill,
                        onColor = if (active) Color.BLACK else Color.WHITE,
                        cornerDp = 30f)
            }
            "slab" -> PreviewSkin(
                    fill = if (active) accent else SLAB_SURFACE,
                    onColor = Color.WHITE,
                    cornerDp = SLAB_CORNER_DP,
                    keylineColor = if (active) liftedAccent(accent) else Color.TRANSPARENT)
            "ink" -> PreviewSkin(
                    fill = Color.TRANSPARENT,
                    onColor = if (active) liftedAccent(accent) else Color.WHITE,
                    cornerDp = 24f,
                    strokeColor = if (active) ColorUtils.setAlphaComponent(accent, 0xA8)
                    else 0x2EFFFFFF,
                    strokeDp = 1f,
                    keylineColor = if (active) liftedAccent(accent) else Color.TRANSPARENT)
            "rail" -> PreviewSkin(
                    fill = if (active) ColorUtils.setAlphaComponent(accent, 0x24)
                    else 0x0FFFFFFF,
                    onColor = Color.WHITE,
                    cornerDp = 6f,
                    strokeColor = if (active) accent else 0x38FFFFFF,
                    strokeDp = 1f,
                    keylineColor = if (active) accent else Color.TRANSPARENT)
            "sunset" -> PreviewSkin(
                    onColor = if (active) Color.BLACK else Color.WHITE,
                    cornerDp = 20f,
                    strokeColor = if (active) 0x70FFFFFF else Color.TRANSPARENT,
                    strokeDp = if (active) 1f else 0f,
                    gradientTop = if (active) 0xFFFF8A4C.toInt() else 0xA8B53678.toInt(),
                    gradientMiddle = if (active) 0xFFFF4D79.toInt() else 0xA881315F.toInt(),
                    gradientBottom = if (active) 0xFF8C3BE8.toInt() else 0xA84B2C83.toInt())
            "bubble" -> PreviewSkin(
                    fill = if (active) lightAccent
                    else ColorUtils.setAlphaComponent(accent, 0x38),
                    onColor = if (active) Color.BLACK else Color.WHITE,
                    cornerDp = 28f,
                    bottomLeftCornerDp = 6f,
                    strokeColor = 0x52FFFFFF,
                    strokeDp = 1f)
            "chrome" -> PreviewSkin(
                    onColor = if (active) Color.BLACK else Color.WHITE,
                    cornerDp = 12f,
                    strokeColor = 0x78FFFFFF,
                    strokeDp = 1f,
                    gradientTop = if (active) 0xFFF4F5F7.toInt() else 0xFF4D5159.toInt(),
                    gradientMiddle = if (active) Color.WHITE else 0xFF858A94.toInt(),
                    gradientBottom = if (active) 0xFF7E838C.toInt() else 0xFF272A30.toInt())
            "holo" -> PreviewSkin(
                    onColor = if (active) Color.BLACK else Color.WHITE,
                    cornerDp = 26f,
                    strokeColor = 0x7AFFFFFF,
                    strokeDp = 1.25f,
                    gradientTop = ColorUtils.setAlphaComponent(
                            if (active) accentForSurface(tertiary)
                            else tonal(tertiary, .34f, .35f, .84f), 0xD8),
                    gradientMiddle = ColorUtils.setAlphaComponent(
                            if (active) lightAccent
                            else tonal(accent, .24f, .35f, .84f), 0xC8),
                    gradientBottom = ColorUtils.setAlphaComponent(
                            if (active) accentForSurface(secondary)
                            else tonal(secondary, .30f, .35f, .84f), 0xD8))
            // Mirrors QueueStyle.COVER: the Glass fill shows through where a row has no artwork,
            // white text over the scrim, and the now-playing row marked by an accent keyline
            // rather than a light pill that would fight the cover behind it.
            "cover", "cover_blur", "cover_tonal", "cover_square" -> PreviewSkin(
                    fill = 0xFF1E1E20.toInt(),
                    onColor = Color.WHITE,
                    cornerDp = if (style == "cover_square") 7f else 26f,
                    keylineColor = if (active) accent else Color.TRANSPARENT)
            else -> PreviewSkin(
                    fill = if (active) lightAccent else 0xFF1E1E20.toInt(),
                    onColor = if (active) Color.BLACK else Color.WHITE,
                    cornerDp = 26f)
        }
    }

    private fun drawSkin(
            canvas: Canvas,
            rect: RectF,
            skin: PreviewSkin,
            dp: (Float) -> Float
    ) {
        val corner = if (skin.cornerDp >= 900f) rect.height() / 2f else dp(skin.cornerDp)
        val bottomLeftCorner = skin.bottomLeftCornerDp?.let(dp)
        fun asymmetricPath(bounds: RectF, inset: Float = 0f): Path? {
            val bottomLeft = bottomLeftCorner ?: return null
            val maximum = min(bounds.width(), bounds.height()) / 2f
            val mainRadius = (corner - inset).coerceIn(0f, maximum)
            val lowerLeftRadius = (bottomLeft - inset).coerceIn(0f, maximum)
            return Path().apply {
                addRoundRect(
                        bounds,
                        floatArrayOf(
                                mainRadius, mainRadius,
                                mainRadius, mainRadius,
                                mainRadius, mainRadius,
                                lowerLeftRadius, lowerLeftRadius),
                        Path.Direction.CW)
            }
        }
        fillPaint.style = Paint.Style.FILL
        fillPaint.shader = when {
            skin.gradientTop != null && skin.gradientMiddle != null &&
                    skin.gradientBottom != null -> LinearGradient(
                    rect.left, rect.top, rect.right, rect.bottom,
                    intArrayOf(skin.gradientTop, skin.gradientMiddle, skin.gradientBottom),
                    floatArrayOf(0f, .5f, 1f),
                    Shader.TileMode.CLAMP
            )
            skin.gradientTop != null && skin.gradientBottom != null -> LinearGradient(
                    0f, rect.top, 0f, rect.bottom,
                    skin.gradientTop, skin.gradientBottom, Shader.TileMode.CLAMP)
            else -> null
        }
        fillPaint.color = skin.fill
        if (fillPaint.shader != null || Color.alpha(skin.fill) > 0) {
            val path = asymmetricPath(rect)
            if (path != null) {
                canvas.drawPath(path, fillPaint)
            } else {
                canvas.drawRoundRect(rect, corner, corner, fillPaint)
            }
        }
        fillPaint.shader = null
        if (skin.strokeDp > 0f && Color.alpha(skin.strokeColor) > 0) {
            strokePaint.style = Paint.Style.STROKE
            strokePaint.shader = null
            strokePaint.pathEffect = null
            strokePaint.strokeWidth = dp(skin.strokeDp)
            strokePaint.strokeCap = Paint.Cap.ROUND
            strokePaint.color = skin.strokeColor
            val inset = strokePaint.strokeWidth / 2f
            val outline = RectF(rect).apply { inset(inset, inset) }
            val path = asymmetricPath(outline, inset)
            if (path != null) {
                canvas.drawPath(path, strokePaint)
            } else {
                canvas.drawRoundRect(outline, corner, corner, strokePaint)
            }
        }
    }

    /** Mirrors WatchTheme.accentForSurface, sharing its floor so the two cannot drift. */
    private fun accentForSurface(color: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[1] = hsl[1].coerceAtLeast(ColorHarmony.NEUTRAL_ACCENT_SATURATION)
        hsl[2] = hsl[2].coerceIn(0.62f, 0.82f)
        return ColorUtils.HSLToColor(hsl)
    }

    private fun contrastingColor(background: Int): Int =
            if (ColorUtils.calculateLuminance(background) > 0.5) Color.BLACK else Color.WHITE

    private fun drawIcon(canvas: Canvas, resId: Int, centerX: Float, centerY: Float, sizePx: Float, tint: Int, alpha: Int = 255) {
        val drawable = AppCompatResources.getDrawable(context, resId) ?: return
        drawable.mutate()
        drawable.setTint(tint)
        drawable.alpha = alpha
        val half = (sizePx / 2f).toInt()
        drawable.setBounds((centerX - half).toInt(), (centerY - half).toInt(),
                (centerX + half).toInt(), (centerY + half).toInt())
        drawable.draw(canvas)
    }

    private fun drawActionIcon(
            canvas: Canvas,
            icon: PreviewActionIcon?,
            fallbackRes: Int,
            centerX: Float,
            centerY: Float,
            sizePx: Float,
            tint: Int,
            alpha: Int = 255,
            forceTint: Boolean = false
    ) {
        if (icon == null) {
            drawIcon(canvas, fallbackRes, centerX, centerY, sizePx, tint, alpha)
            return
        }
        iconDst.set(
                centerX - sizePx / 2f,
                centerY - sizePx / 2f,
                centerX + sizePx / 2f,
                centerY + sizePx / 2f
        )
        bitmapPaint.alpha = alpha.coerceIn(0, 255)
        bitmapPaint.colorFilter = if (icon.tintable || forceTint) {
            android.graphics.PorterDuffColorFilter(tint, android.graphics.PorterDuff.Mode.SRC_IN)
        } else {
            null
        }
        canvas.drawBitmap(icon.bitmap, null, iconDst, bitmapPaint)
        bitmapPaint.colorFilter = null
        bitmapPaint.alpha = 255
    }

    /**
     * Bezel-hugging progress ring - matches CircularProgressSeekBar: a 6dp band whose outer edge
     * touches the screen edge (inset = strokeWidth/2), track in glass_surface_border with a BUTT
     * cap and the played arc rounded, both starting at 12 o'clock. Used by the classic face and,
     * when the expressive face's seek mode is "edge", by the expressive face too.
     */
    private fun previewMarkIsPlayed(index: Int, count: Int): Boolean =
            progressFraction() > 0f && index.toFloat() / count < progressFraction()

    private fun drawPreviewWatchDots(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            radius: Float,
            baseStroke: Float,
            progressColor: Int,
            count: Int = 60,
            emphasizeEvery: Int = if (count >= 30) 5 else 0,
            normalRadiusScale: Float = .22f,
            emphasizedRadiusScale: Float = .36f,
            startDegrees: Float = -90f,
            sweepDegrees: Float = 360f
    ) {
        val orbit = radius - baseStroke / 2f
        fillPaint.shader = null
        fillPaint.style = Paint.Style.FILL
        repeat(count) { index ->
            val denominator = if (sweepDegrees >= 359f) count else (count - 1).coerceAtLeast(1)
            val angle = Math.toRadians(
                    (startDegrees + index * sweepDegrees / denominator).toDouble())
            val major = emphasizeEvery > 0 && index % emphasizeEvery == 0
            fillPaint.color = if (previewMarkIsPlayed(index, count)) {
                progressColor
            } else {
                0x33FFFFFF
            }
            canvas.drawCircle(
                    cx + orbit * cos(angle).toFloat(),
                    cy + orbit * sin(angle).toFloat(),
                    baseStroke * if (major) emphasizedRadiusScale else normalRadiusScale,
                    fillPaint)
        }
    }

    private fun drawPreviewWatchTicks(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            radius: Float,
            baseStroke: Float,
            progressColor: Int,
            count: Int,
            emphasizeEvery: Int,
            normalLength: Float,
            emphasizedLength: Float,
            normalWidth: Float,
            emphasizedWidth: Float,
            startDegrees: Float = -90f,
            sweepDegrees: Float = 360f
    ) {
        val outerRadius = radius - baseStroke * 0.25f
        strokePaint.shader = null
        strokePaint.pathEffect = null
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeCap = Paint.Cap.ROUND
        repeat(count) { index ->
            val denominator = if (sweepDegrees >= 359f) count else (count - 1).coerceAtLeast(1)
            val angle = Math.toRadians(
                    (startDegrees + index * sweepDegrees / denominator).toDouble())
            val emphasized = emphasizeEvery > 0 && index % emphasizeEvery == 0
            val length = if (emphasized) emphasizedLength else normalLength
            strokePaint.strokeWidth = if (emphasized) emphasizedWidth else normalWidth
            strokePaint.color = if (previewMarkIsPlayed(index, count)) {
                progressColor
            } else {
                0x33FFFFFF
            }
            val cosAngle = cos(angle).toFloat()
            val sinAngle = sin(angle).toFloat()
            canvas.drawLine(
                    cx + (outerRadius - length) * cosAngle,
                    cy + (outerRadius - length) * sinAngle,
                    cx + outerRadius * cosAngle,
                    cy + outerRadius * sinAngle,
                    strokePaint)
        }
    }

    /**
     * Rect for one round quick-panel slot, per the selected layout. Mirrors the watch:
     * [com.svartifoss.snfell.watch.view.QuickActionsRowLayout] for arc/grid and
     * `applyHeroSlotEmphasis` for hero.
     */
    private fun previewQuickSlotRect(
            index: Int,
            count: Int,
            geometry: PreviewGeometry,
            centerY: Float,
            buttonWidth: Float,
            buttonHeight: Float,
            gap: Float,
            dp: (Float) -> Float
    ): RectF = when (quickPanelLayout) {
        "fan" -> {
            val middle = (count - 1) / 2f
            val t = if (middle > 0f) (index - middle) / middle else 0f
            val x = geometry.cx + t * dp(60f)
            val y = centerY + dp(18f) * (1f - kotlin.math.abs(t))
            val size = buttonHeight * (.92f + .08f * (1f - kotlin.math.abs(t)))
            RectF(x - size / 2f, y - size / 2f, x + size / 2f, y + size / 2f)
        }
        "orbit" -> {
            val denominator = (count - 1).coerceAtLeast(1)
            val angle = Math.toRadians((205f + index * 130f / denominator).toDouble())
            val orbit = dp(54f)
            val x = geometry.cx + orbit * cos(angle).toFloat()
            val y = geometry.cy + dp(10f) + orbit * sin(angle).toFloat()
            val size = buttonHeight * .9f
            RectF(x - size / 2f, y - size / 2f, x + size / 2f, y + size / 2f)
        }
        "dock" -> {
            val dockWidth = buttonWidth * .84f
            val dockHeight = buttonHeight * .76f
            val x = geometry.cx + (index - (count - 1) / 2f) * (dockWidth + dp(3f))
            val y = centerY + dp(20f)
            RectF(
                    x - dockWidth / 2f, y - dockHeight / 2f,
                    x + dockWidth / 2f, y + dockHeight / 2f)
        }
        "column" -> {
            val columnWidth = buttonWidth * 1.12f
            val columnHeight = buttonHeight * .7f
            val y = centerY + (index - (count - 1) / 2f) * (columnHeight + dp(5f))
            RectF(
                    geometry.cx - columnWidth / 2f, y - columnHeight / 2f,
                    geometry.cx + columnWidth / 2f, y + columnHeight / 2f)
        }
        "split" -> {
            val heroWidth = buttonWidth * 1.18f
            val heroHeight = buttonHeight * 1.25f
            val smallWidth = buttonWidth * .84f
            val smallHeight = buttonHeight * .58f
            if (index == 0) {
                val x = geometry.cx - dp(34f)
                RectF(
                        x - heroWidth / 2f, centerY - heroHeight / 2f,
                        x + heroWidth / 2f, centerY + heroHeight / 2f)
            } else {
                val x = geometry.cx + dp(41f)
                val secondaryCount = (count - 1).coerceAtLeast(1)
                val y = centerY + (index - (secondaryCount + 1) / 2f) * (smallHeight + dp(5f))
                RectF(
                        x - smallWidth / 2f, y - smallHeight / 2f,
                        x + smallWidth / 2f, y + smallHeight / 2f)
            }
        }
        "diamond" -> {
            val size = buttonHeight * .88f
            val (x, y) = when {
                count == 1 -> geometry.cx to centerY
                count == 2 -> (geometry.cx + if (index == 0) -dp(34f) else dp(34f)) to centerY
                index == 0 -> geometry.cx to (centerY - dp(31f))
                else -> (geometry.cx + if (index == 1) -dp(38f) else dp(38f)) to
                        (centerY + dp(25f))
            }
            RectF(x - size / 2f, y - size / 2f, x + size / 2f, y + size / 2f)
        }
        "carousel" -> {
            val selected = count / 2
            val size = if (index == selected) buttonHeight * 1.28f else buttonHeight * .76f
            val x = geometry.cx + (index - selected) * dp(55f)
            val y = centerY + if (index == selected) 0f else dp(7f)
            RectF(x - size / 2f, y - size / 2f, x + size / 2f, y + size / 2f)
        }
        "grid" -> {
            // Two columns; an odd final row is centred. Cells are wider than in a row because
            // only two of them share the width.
            val cellW = buttonWidth * 1.28f
            val cellH = buttonHeight * 1.16f
            val row = index / 2
            val rowCount = (count + 1) / 2
            val inRow = index % 2
            val itemsInRow = if (row == rowCount - 1 && count % 2 == 1) 1 else 2
            val rowWidth = itemsInRow * cellW + (itemsInRow - 1) * gap
            val x = geometry.cx - rowWidth / 2f + inRow * (cellW + gap) + cellW / 2f
            // Grid grows downwards from a slightly raised first row so both rows stay on screen.
            val y = centerY - dp(14f) + row * (cellH + gap)
            RectF(x - cellW / 2f, y - cellH / 2f, x + cellW / 2f, y + cellH / 2f)
        }
        "hero" -> {
            val heroSize = buttonHeight * 1.5f
            val secondary = buttonHeight * .82f
            val totalWidth = heroSize + (count - 1) * (secondary + gap)
            var x = geometry.cx - totalWidth / 2f
            repeat(index) { x += (if (it == 0) heroSize else secondary) + gap }
            val size = if (index == 0) heroSize else secondary
            RectF(x, centerY - size / 2f, x + size, centerY + size / 2f)
        }
        else -> {
            val x = geometry.cx + (index - (count - 1) / 2f) * (buttonWidth + gap)
            // The arc bows the row downwards, deepest at its centre - the parabolic falloff
            // QuickActionsRowLayout.applyArcOffsets applies.
            val dip = if (quickPanelLayout == "arc" && count > 1) {
                val t = (index - (count - 1) / 2f) / ((count - 1) / 2f)
                dp(16f) * (1f - t * t)
            } else {
                0f
            }
            RectF(
                    x - buttonWidth / 2f,
                    centerY - buttonHeight / 2f + dip,
                    x + buttonWidth / 2f,
                    centerY + buttonHeight / 2f + dip
            )
        }
    }

    /** The "rows" layout: each configured slot as a labelled full-width row. */
    private fun drawPreviewQuickPanelRows(
            canvas: Canvas,
            geometry: PreviewGeometry,
            dp: (Float) -> Float,
            buttons: List<Triple<PreviewActionIcon?, Int, Boolean>>,
            centerY: Float,
            accent: Int
    ) {
        val rowWidth = geometry.bounds.width() * .88f
        val rowHeight = dp(44f)
        val spacing = dp(6f)
        val totalHeight = buttons.size * rowHeight + (buttons.size - 1).coerceAtLeast(0) * spacing
        var y = centerY - totalHeight / 2f + rowHeight / 2f

        buttons.forEach { (action, fallbackIcon, active) ->
            val skin = quickSkin(quickPanelStyle, active, row = true, accent = accent)
            val rect = RectF(
                    geometry.cx - rowWidth / 2f, y - rowHeight / 2f,
                    geometry.cx + rowWidth / 2f, y + rowHeight / 2f)
            if (coverPillFor(action) != null) {
                drawCoverPill(canvas, rect, coverPillFor(action)!!, dp)
            } else {
                drawSkin(canvas, rect, skin, dp)
                val iconX = rect.left + dp(24f)
                drawActionIcon(canvas, action, fallbackIcon, iconX, y, dp(20f), skin.onColor)
            }
            y += rowHeight + spacing
        }
    }

    /**
     * The artwork a quick-panel row fills its whole pill with, or null when it draws an ordinary
     * skin and a leading icon.
     *
     * Three conditions, matching the watch: the row's icon is *genuine* cover art (never a
     * launcher icon - `coverArt` is a different question from `tintable`, and conflating them is
     * what once grew an "open app" row's brand mark to album size), the panel is on a Cover style,
     * and the opt-in toggle is on. The underlying thumbnail fetch is itself opt-in, which is why
     * this defaults off rather than following the style alone.
     */
    private fun coverPillFor(action: PreviewActionIcon?): Bitmap? {
        val icon = action ?: return null
        if (!icon.coverArt || !quickPanelShortcutCover) return null
        if (queueStyle !in MiscPreferences.COVER_LIST_STYLES) return null
        return icon.bitmap
    }

    /** Artwork cropped to the pill, under the same scrim the watch applies so a label stays
     *  readable over it. */
    private fun drawCoverPill(canvas: Canvas, rect: RectF, cover: Bitmap, dp: (Float) -> Float) {
        val corner = dp(14f)
        val saved = canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(rect, corner, corner, Path.Direction.CW) })
        drawArtwork(canvas, cover, rect, 255)
        fillPaint.shader = null
        fillPaint.color = ColorUtils.setAlphaComponent(Color.BLACK, 0x66)
        canvas.drawRect(rect, fillPaint)
        canvas.restoreToCount(saved)
    }

    private fun drawEdgeSeekRing(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            radius: Float,
            dp: (Float) -> Float,
            strokeScale: Float = 1f
    ) {
        val progressColor = resolveTint(progressMode, progressCustom, progressDesaturated)
        val layoutRadius = (radius - when (progressLayout) {
            "inset" -> dp(7f)
            "inner" -> dp(17f)
            "bold", "open_bottom", "open_top", "double" -> dp(1f)
            else -> 0f
        }).coerceAtLeast(dp(28f))
        val layoutStrokeScale = when (progressLayout) {
            "bold" -> 1.8f
            "double" -> .72f
            else -> 1f
        }
        val baseStroke = dp(6f) * strokeScale * layoutStrokeScale
        val layoutStart = when (progressLayout) {
            "open_bottom" -> 135f
            "open_top" -> -45f
            "left_arc" -> 100f
            "right_arc" -> 80f
            else -> -90f
        }
        val layoutSweep = when (progressLayout) {
            "open_bottom", "open_top" -> 270f
            "left_arc" -> 160f
            "right_arc" -> -160f
            else -> 360f
        }
        val ringInset = baseStroke / 2f
        val ringRect = RectF(cx - layoutRadius + ringInset, cy - layoutRadius + ringInset,
                cx + layoutRadius - ringInset, cy + layoutRadius - ringInset)
        val sweep = progressFraction() * layoutSweep

        when (progressStyle) {
            "watch_dots_60" -> {
                drawPreviewWatchDots(
                        canvas, cx, cy, layoutRadius, baseStroke, progressColor,
                        startDegrees = layoutStart, sweepDegrees = layoutSweep)
                if (progressLayout == "double") {
                    drawPreviewWatchDots(
                            canvas, cx, cy, layoutRadius - dp(7f), baseStroke * .76f,
                            resolveSecondaryTint(
                                    progressMode, progressCustom, progressDesaturated),
                            startDegrees = layoutStart, sweepDegrees = layoutSweep)
                }
                return
            }
            "watch_ticks_60" -> {
                drawPreviewWatchTicks(
                        canvas, cx, cy, layoutRadius, baseStroke, progressColor,
                        count = 60,
                        emphasizeEvery = 5,
                        normalLength = baseStroke * 0.58f,
                        emphasizedLength = baseStroke * 0.95f,
                        normalWidth = baseStroke * 0.17f,
                        emphasizedWidth = baseStroke * 0.29f,
                        startDegrees = layoutStart,
                        sweepDegrees = layoutSweep)
                if (progressLayout == "double") {
                    val innerWidth = baseStroke * .76f
                    drawPreviewWatchTicks(
                            canvas, cx, cy, layoutRadius - dp(7f), innerWidth,
                            resolveSecondaryTint(
                                    progressMode, progressCustom, progressDesaturated),
                            count = 60,
                            emphasizeEvery = 5,
                            normalLength = innerWidth * .58f,
                            emphasizedLength = innerWidth * .95f,
                            normalWidth = innerWidth * .17f,
                            emphasizedWidth = innerWidth * .29f,
                            startDegrees = layoutStart,
                            sweepDegrees = layoutSweep)
                }
                return
            }
            "hour_segments_12" -> {
                drawPreviewWatchTicks(
                        canvas, cx, cy, layoutRadius, baseStroke, progressColor,
                        count = 12,
                        emphasizeEvery = 1,
                        normalLength = baseStroke,
                        emphasizedLength = baseStroke,
                        normalWidth = baseStroke * 0.4f,
                        emphasizedWidth = baseStroke * 0.4f,
                        startDegrees = layoutStart,
                        sweepDegrees = layoutSweep)
                if (progressLayout == "double") {
                    val innerWidth = baseStroke * .76f
                    drawPreviewWatchTicks(
                            canvas, cx, cy, layoutRadius - dp(7f), innerWidth,
                            resolveSecondaryTint(
                                    progressMode, progressCustom, progressDesaturated),
                            count = 12,
                            emphasizeEvery = 1,
                            normalLength = innerWidth,
                            emphasizedLength = innerWidth,
                            normalWidth = innerWidth * .4f,
                            emphasizedWidth = innerWidth * .4f,
                            startDegrees = layoutStart,
                            sweepDegrees = layoutSweep)
                }
                return
            }
            "ticks_24" -> {
                drawPreviewWatchTicks(
                        canvas, cx, cy, layoutRadius, baseStroke, progressColor,
                        count = 24,
                        emphasizeEvery = 6,
                        normalLength = baseStroke * .75f,
                        emphasizedLength = baseStroke * 1.35f,
                        normalWidth = baseStroke * .25f,
                        emphasizedWidth = baseStroke * .48f,
                        startDegrees = layoutStart,
                        sweepDegrees = layoutSweep)
                if (progressLayout == "double") {
                    val innerWidth = baseStroke * .76f
                    drawPreviewWatchTicks(
                            canvas, cx, cy, layoutRadius - dp(7f), innerWidth,
                            resolveSecondaryTint(
                                    progressMode, progressCustom, progressDesaturated),
                            count = 24,
                            emphasizeEvery = 6,
                            normalLength = innerWidth * .75f,
                            emphasizedLength = innerWidth * 1.35f,
                            normalWidth = innerWidth * .25f,
                            emphasizedWidth = innerWidth * .48f,
                            startDegrees = layoutStart,
                            sweepDegrees = layoutSweep)
                }
                return
            }
            "beads" -> {
                drawPreviewWatchDots(
                        canvas, cx, cy, layoutRadius, baseStroke, progressColor,
                        count = 24,
                        emphasizeEvery = 6,
                        normalRadiusScale = .34f,
                        emphasizedRadiusScale = .58f,
                        startDegrees = layoutStart,
                        sweepDegrees = layoutSweep)
                if (progressLayout == "double") {
                    drawPreviewWatchDots(
                            canvas, cx, cy, layoutRadius - dp(7f),
                            baseStroke * .76f,
                            resolveSecondaryTint(progressMode, progressCustom, progressDesaturated),
                            count = 24,
                            emphasizeEvery = 6,
                            normalRadiusScale = .34f,
                            emphasizedRadiusScale = .58f,
                            startDegrees = layoutStart,
                            sweepDegrees = layoutSweep)
                }
                return
            }
        }

        // Mirrors the watch's RingStyle branches (CircularProgressSeekBar.onDraw) at preview
        // scale - keep the two in sync when adding ring styles.
        strokePaint.pathEffect = null
        strokePaint.shader = null
        var fillWidth = baseStroke
        var trackWidth = baseStroke
        var fillCap = Paint.Cap.ROUND
        var trackCap = Paint.Cap.BUTT
        when (progressStyle) {
            "dashed" -> {
                strokePaint.pathEffect = DashPathEffect(
                        floatArrayOf(baseStroke * 1.9f, baseStroke * 1.5f), 0f)
                fillCap = Paint.Cap.BUTT
            }
            "dots" -> {
                strokePaint.pathEffect = DashPathEffect(
                        floatArrayOf(0.01f, baseStroke * 2.6f), 0f)
                trackCap = Paint.Cap.ROUND
            }
            "hairline" -> {
                fillWidth = baseStroke * 0.45f
                trackWidth = baseStroke * 0.3f
            }
            "comet" -> {
                trackWidth = baseStroke * 0.5f
            }
            "double" -> {
                fillWidth = baseStroke * .68f
                trackWidth = baseStroke * .5f
            }
            "dash_dot" -> {
                strokePaint.pathEffect = DashPathEffect(
                        floatArrayOf(
                                baseStroke * 2.1f, baseStroke * .82f,
                                .01f, baseStroke * .82f), 0f)
                fillCap = Paint.Cap.ROUND
                trackCap = Paint.Cap.ROUND
            }
            "progress_only" -> trackWidth = 0f
            "glow" -> {
                fillWidth = baseStroke * .78f
                trackWidth = baseStroke * .38f
            }
            "needle" -> {
                fillWidth = baseStroke * .42f
                trackWidth = baseStroke * .26f
            }
        }

        strokePaint.style = Paint.Style.STROKE
        if (trackWidth > 0f) {
            strokePaint.strokeWidth = trackWidth
            strokePaint.strokeCap = trackCap
            strokePaint.color = 0x33FFFFFF
            canvas.drawArc(ringRect, layoutStart, layoutSweep, false, strokePaint)
        }

        if (progressStyle == "glow" && sweep > 1f) {
            strokePaint.pathEffect = null
            strokePaint.strokeWidth = fillWidth * 3.2f
            strokePaint.strokeCap = Paint.Cap.ROUND
            strokePaint.color = ColorUtils.setAlphaComponent(progressColor, 0x2E)
            canvas.drawArc(ringRect, layoutStart, sweep, false, strokePaint)
        }

        strokePaint.strokeWidth = fillWidth
        strokePaint.strokeCap = fillCap
        strokePaint.color = progressColor
        // Angular width of the round stroke cap, as a 0..1 shader position. Mirrors
        // CircularProgressSeekBar.capAngleFraction: an unshifted sweep gradient paints that cap
        // with its LAST stop (the shader wraps just below 1.0), which showed up as a mark of the
        // wrong colour at the very start of the bar.
        val ringRadius = ringRect.width() / 2f
        val capFraction = if (ringRadius > 0f) {
            (Math.toDegrees(atan2(fillWidth / 2f, ringRadius).toDouble()).toFloat() / 360f)
                    .coerceIn(0f, 0.25f)
        } else {
            0f
        }
        if (progressStyle == "comet") {
            if (sweep > 1f) {
                val shader = SweepGradient(cx, cy,
                        intArrayOf(
                                progressColor and 0x00FFFFFF,
                                progressColor and 0x00FFFFFF,
                                progressColor),
                        floatArrayOf(
                                0f,
                                capFraction,
                                (capFraction + sweep / 360f).coerceAtMost(1f)))
                val rotate = Matrix()
                rotate.setRotate(layoutStart - capFraction * 360f, cx, cy)
                shader.setLocalMatrix(rotate)
                strokePaint.shader = shader
                canvas.drawArc(ringRect, layoutStart, sweep, false, strokePaint)
                strokePaint.shader = null
            }
            val headRad = Math.toRadians((layoutStart + sweep).toDouble())
            val r = ringRect.width() / 2f
            fillPaint.shader = null
            fillPaint.color = progressColor
            canvas.drawCircle(cx + r * cos(headRad).toFloat(),
                    cy + r * sin(headRad).toFloat(), baseStroke * 0.5f, fillPaint)
        } else if (progressStyle == "duotone" && sweep > 1f) {
            val secondary = resolveSecondaryTint(
                    progressMode, progressCustom, progressDesaturated)
            val shader = SweepGradient(cx, cy,
                    intArrayOf(progressColor, progressColor, secondary),
                    floatArrayOf(0f, .48f, 1f))
            val rotate = Matrix()
            rotate.setRotate(layoutStart, cx, cy)
            shader.setLocalMatrix(rotate)
            strokePaint.shader = shader
            canvas.drawArc(ringRect, layoutStart, sweep, false, strokePaint)
            strokePaint.shader = null
        } else if (progressStyle == "solid" && sweep > 1f && progressGradientEnabled &&
                (ColorHarmony.hueDistance(progressColor, resolveSecondaryTint(progressMode, progressCustom, progressDesaturated)) >= ColorHarmony.MIN_DUOTONE_HUE_GAP ||
                        ColorHarmony.hueDistance(progressColor, resolveTertiaryTint(progressMode, progressCustom, progressDesaturated)) >= ColorHarmony.MIN_DUOTONE_HUE_GAP)) {
            // Mirrors CircularProgressSeekBar's RingStyle.SOLID gradient: a treatment whose
            // secondary/tertiary sit close to the primary hue draws exactly as before (the `else`
            // below), so this is additive rather than a restyle of Normal/Desaturated/Expressive.
            val shader = SweepGradient(cx, cy,
                    intArrayOf(progressColor,
                            progressColor,
                            resolveSecondaryTint(progressMode, progressCustom, progressDesaturated),
                            resolveTertiaryTint(progressMode, progressCustom, progressDesaturated)),
                    floatArrayOf(
                            0f,
                            capFraction,
                            (capFraction + sweep / 360f * 0.5f).coerceAtMost(1f),
                            (capFraction + sweep / 360f).coerceAtMost(1f)))
            val rotate = Matrix()
            rotate.setRotate(layoutStart - capFraction * 360f, cx, cy)
            shader.setLocalMatrix(rotate)
            strokePaint.shader = shader
            canvas.drawArc(ringRect, layoutStart, sweep, false, strokePaint)
            strokePaint.shader = null
        } else {
            canvas.drawArc(ringRect, layoutStart, sweep, false, strokePaint)
        }

        if (progressStyle == "double") {
            val inner = RectF(ringRect).apply { inset(baseStroke * 1.35f, baseStroke * 1.35f) }
            val innerColor = resolveSecondaryTint(
                    progressMode, progressCustom, progressDesaturated)
            strokePaint.pathEffect = null
            strokePaint.shader = null
            strokePaint.strokeCap = Paint.Cap.ROUND
            strokePaint.strokeWidth = baseStroke * .42f
            strokePaint.color = 0x24FFFFFF
            canvas.drawArc(inner, layoutStart, layoutSweep, false, strokePaint)
            strokePaint.color = innerColor
            canvas.drawArc(inner, layoutStart, sweep, false, strokePaint)
        }

        if (progressLayout == "double") {
            val inner = RectF(ringRect).apply { inset(dp(7f), dp(7f)) }
            val innerColor = resolveSecondaryTint(
                    progressMode, progressCustom, progressDesaturated)
            strokePaint.pathEffect = null
            strokePaint.shader = null
            strokePaint.strokeCap = Paint.Cap.ROUND
            strokePaint.strokeWidth = baseStroke * .76f
            strokePaint.color = 0x24FFFFFF
            canvas.drawArc(inner, layoutStart, layoutSweep, false, strokePaint)
            strokePaint.color = innerColor
            canvas.drawArc(inner, layoutStart, sweep, false, strokePaint)
        }

        if (progressStyle == "needle" && sweep > 0f) {
            val angle = Math.toRadians((layoutStart + sweep).toDouble())
            val outer = ringRect.width() / 2f
            val cosAngle = cos(angle).toFloat()
            val sinAngle = sin(angle).toFloat()
            strokePaint.pathEffect = null
            strokePaint.strokeWidth = baseStroke * .38f
            strokePaint.strokeCap = Paint.Cap.ROUND
            strokePaint.color = progressColor
            canvas.drawLine(
                    cx + (outer - baseStroke * 2.2f) * cosAngle,
                    cy + (outer - baseStroke * 2.2f) * sinAngle,
                    cx + (outer + baseStroke * .35f) * cosAngle,
                    cy + (outer + baseStroke * .35f) * sinAngle,
                    strokePaint)
        }
        strokePaint.pathEffect = null
        strokePaint.shader = null
    }

    private fun drawClassic(
            canvas: Canvas,
            geometry: PreviewGeometry,
            dp: (Float) -> Float
    ) {
        val cx = geometry.cx
        val cy = geometry.cy
        val radius = geometry.radius
        val theme = screenThemeSpec()
        // The seek ring uses the raw resolved accent (like the watch's seekBar.progressColor);
        // only the artist text gets the accentForText lightness lift.
        val artistColor = artistTextColor()

        // Quadrant hints are visual affordances only; the four touch zones remain unchanged on
        // the watch. The visual style theme only changes how these icon glyphs are drawn -
        // opacity and size - never their position, color or the surrounding chrome.
        val iconAlpha = (theme.iconAlpha * 255).toInt().coerceIn(0, 255)
        val iconSize = dp(24f * theme.iconScale)
        val iconColor = Color.WHITE
        val edge = dp(4f)
        fun hint(quadrant: Int, iconRes: Int, x: Float, y: Float) {
            drawActionIcon(
                    canvas, quadrantIcons[quadrant], iconRes,
                    x, y, iconSize, iconColor, iconAlpha
            )
        }
        // Clock: near the top like the watch's ambient_clock (15sp, ~5dp below the very top),
        // shown when Always-show-time is on (which also hides the top quadrant icon), or forced
        // on while a clock preference is being edited so its effect is visible.
        if (alwaysShowTime || clockPreviewForced()) {
            drawFaceClock(canvas, cx, cy - radius, dp)
        } else if (playerControlsVisible) {
            hint(ScreenQuadrant.TOP, commonR.drawable.action_volume_up,
                    cx, cy - radius + edge + iconSize / 2f)
        }
        if (playerControlsVisible) {
            hint(ScreenQuadrant.BOTTOM, commonR.drawable.action_volume_down,
                    cx, cy + radius - edge - iconSize / 2f)
            hint(ScreenQuadrant.LEFT, commonR.drawable.action_skip_prev,
                    cx - radius + edge + iconSize / 2f, cy)
            hint(ScreenQuadrant.RIGHT, commonR.drawable.action_skip_next,
                    cx + radius - edge - iconSize / 2f, cy)
        }

        // The watch uses BoxInsetLayout around the Classic View block. Its 14.6447% round-screen
        // inset yields the central inscribed square; rectangular screens instead retain their
        // 30dp XML margin. The old fixed .80 diameter approximated neither and made the preview's
        // wrap/sizing decision differ before any text was drawn.
        val classicTextWidth = if (geometry.round) {
            geometry.bounds.width() - 2f *
                    maxOf(geometry.bounds.width(), geometry.bounds.height()) *
                    CLASSIC_ROUND_BOX_INSET_FRACTION
        } else {
            geometry.bounds.width() - dp(CLASSIC_SQUARE_TEXT_MARGIN_DP * 2f)
        }.coerceAtLeast(1f)
        drawClassicTextBlock(
                canvas,
                cx,
                cy,
                classicTextWidth,
                artistColor,
                dp)

        drawBottomChrome(canvas, cx, cy, radius, dp)
    }

    /**
     * Draws the [artist, title, time] block exactly like the watch's centered LinearLayout:
     * tight stacking (title directly under artist, time 4dp below the title), the whole block
     * vertically centered on the screen, font padding excluded, at the watch's sp sizes
     * (16 / 40-46 / 13). The title itself follows the synced text mode (see [planTitle]).
     */
    private fun drawClassicTextBlock(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            textWidth: Float,
            artistColor: Int,
            dp: (Float) -> Float
    ) {
        // 46/25 are CLASSIC_TITLE_MAX_SP / CLASSIC_TITLE_MIN_SP, the exact pair the watch hands
        // OutlineTextView.enableSmartWordSizing. That one floor serves both the single-line
        // shrink and the wrap-limit search there, so the preview must not use a second, lower
        // wrap floor of its own - it let the preview shrink past what the wrist will ever draw.
        val plan = planTitle(
                textWidth,
                dp(CLASSIC_TITLE_MAX_SP),
                dp(CLASSIC_TITLE_MIN_SP),
                smartFallsBackToMarquee = true
        )

        val artistVisible = showTrackArtist || !isPlayingShown()
        val classicArtistText = if (isPlayingShown()) {
            displayArtist()
        } else {
            context.getString(R.string.preview_playback_stopped)
        }
        // The View image sibling stays beside the status text too: the source image is the last
        // playing app's icon, not a playback-state indicator. It is absent only when no icon was
        // sent (or the user disabled it), exactly as the watch's ImageView is.
        val hasClassicSourceGlyph = artistVisible && showSourceIcon && sourceGlyph != null
        val artistPlan = if (artistVisible) {
            planClassicArtist(classicArtistText, textWidth, hasClassicSourceGlyph, dp)
        } else {
            null
        }
        // The View layout's identity artist face is bold, just like the title. Passing false
        // made the preview use a regular typeface even when the watch kept the original bold one.
        val classicArtistTypeface = artistTypeface(bold = true)
        textPaint.typeface = classicArtistTypeface
        textPaint.letterSpacing = artistTypographySpec.trackingEm
        textPaint.textSize = artistPlan?.size ?: artistTypographySpec.scaled(dp(CLASSIC_ARTIST_MAX_SP))
        val artistFm = textPaint.fontMetrics
        val artistLineH = artistFm.descent - artistFm.ascent
        val artistH = (artistPlan?.lines?.size ?: 0) * artistLineH

        val classicTitleTypeface = titleTypeface(bold = true)
        textPaint.typeface = classicTitleTypeface
        textPaint.letterSpacing = titleTypographySpec.trackingEm
        textPaint.textSize = plan.size
        val titleFm = textPaint.fontMetrics
        val titleLineH = titleFm.descent - titleFm.ascent
        val titleH = plan.lines.size * titleLineH

        val timeVisible = trackTimeVisible()
        textPaint.typeface = trackTimeTypeface(fontRegular)
        textPaint.letterSpacing = trackTimeTypographySpec.trackingEm
        val timeSize = trackTimeTypographySpec.scaled(dp(CLASSIC_TRACK_TIME_SP))
        textPaint.textSize = timeSize
        val timeFm = textPaint.fontMetrics
        val timeLineH = timeFm.descent - timeFm.ascent
        val timeGap = dp(4f)
        textPaint.letterSpacing = 0f

        val titleVisible = showTrackTitle
        val totalH = artistH +
                (if (titleVisible) titleH else 0f) +
                (if (timeVisible) timeGap + timeLineH else 0f)
        var y = cy - totalH / 2f

        // Artist (or the "Playback Stopped" status in white while paused).
        if (artistPlan != null) {
            textPaint.typeface = classicArtistTypeface
            textPaint.letterSpacing = artistTypographySpec.trackingEm
            textPaint.textSize = artistPlan.size
            textPaint.textAlign = Paint.Align.CENTER
            val rawArtistColor = if (isPlayingShown()) artistColor else Color.WHITE
            // textArtist's alpha remains in effect for status copy on the watch, while its sibling
            // ImageView uses only the independent source-icon opacity. Keep those two axes apart.
            textPaint.color = artistAlpha(rawArtistColor)
            val sourceDiameter = artistPlan.size * CLASSIC_SOURCE_ICON_SIZE_ARTIST_FACTOR *
                    sourceIconTypographySpec.scale
            val sourceGap = artistPlan.size * CLASSIC_SOURCE_ICON_END_MARGIN_ARTIST_FACTOR
            val widestLine = if (artistPlan.marquee || artistPlan.lines.size > 1) {
                artistPlan.labelWidth
            } else {
                artistPlan.lines.maxOfOrNull { textPaint.measureText(it) } ?: 0f
            }
            val groupWidth = widestLine + if (hasClassicSourceGlyph) sourceDiameter + sourceGap else 0f
            val groupLeft = cx - groupWidth / 2f
            val labelCx = if (hasClassicSourceGlyph) {
                groupLeft + sourceDiameter + sourceGap + widestLine / 2f
            } else {
                groupLeft + widestLine / 2f
            }
            if (hasClassicSourceGlyph) {
                drawSourceGlyph(
                        canvas,
                        groupLeft + sourceDiameter / 2f,
                        y + artistH / 2f,
                        sourceDiameter,
                        rawArtistColor)
            }
            if (artistPlan.marquee) {
                drawMarqueeText(
                        canvas,
                        classicArtistText,
                        labelCx,
                        y - artistFm.ascent,
                        artistPlan.labelWidth)
            } else {
                artistPlan.lines.forEachIndexed { index, line ->
                    canvas.drawText(line, labelCx, y - artistFm.ascent + index * artistLineH, textPaint)
                }
            }
            textPaint.letterSpacing = 0f
            y += artistH
        }

        // Title (as many lines as the text mode allows, or scrolling in marquee mode).
        if (titleVisible) {
            textPaint.typeface = classicTitleTypeface
            textPaint.letterSpacing = titleTypographySpec.trackingEm
            textPaint.color = titleAlpha(Color.WHITE)
            textPaint.textSize = plan.size
            if (plan.marquee) {
                drawMarqueeText(canvas, plan.lines.first(), cx, y - titleFm.ascent, textWidth)
                y += titleLineH
            } else {
                plan.lines.forEach { line ->
                    canvas.drawText(line, cx, y - titleFm.ascent, textPaint)
                    y += titleLineH
                }
            }
        }

        // Track time, 4dp below the title.
        if (timeVisible) {
            y += timeGap
            drawTrackTimeText(
                    canvas,
                    timeText(),
                    cx,
                    y - timeFm.ascent,
                    dp(CLASSIC_TRACK_TIME_SP),
                    Color.WHITE,
                    fontRegular)
        }
    }

    /** True while a clock preference is being edited, so the player preview shows the clock even
     *  when Always-show-time is off - the same "force the surface visible while editing it" trick
     *  the mini buttons use. */
    private fun clockPreviewForced(): Boolean = focusedPreference?.startsWith("wear_clock_") == true

    /**
     * The clock's typeface: its own [MiscPreferences.WEAR_CLOCK_FONT] choice when set, otherwise
     * WEAR_FONT, mirroring the watch's `NowPlayingFaceState.clockFont`. The clock is chrome, not
     * track text.
     *
     * Goes through [WatchFontCatalog] rather than keeping its own copy of the key->typeface map:
     * the copy that used to live here had already fallen behind by five fonts (Bebas Neue,
     * Playfair, Space Grotesk, Orbitron, Caveat all rendered as plain Google Sans in the preview
     * while the watch drew them correctly).
     */
    private fun clockTypeface(
            spec: WatchTypography.TextSpec = WatchTypography.IDENTITY_TEXT
    ): Typeface? {
        val key = WatchTypography.clockFontKey(wearClockFontKey, wearFontKey)
        if (WatchTypography.isFlexFont(key)) {
            return flexPreviewTypeface(
                    spec,
                    if (wearClockFontKey == WatchTypography.FLEX_FONT_KEY) {
                        clockFlexAxesSpec
                    } else {
                        flexAxesSpec
                    })
        }
        val base = WatchFontCatalog.typefaceFor(context, key) ?: fontRegular
        // bold = false: the clock's identity weight is normal, unlike the classic title's.
        return styledPreviewTypeface(base, bold = false, spec = spec)
    }

    /**
     * The typeface for song lyrics, mirroring the watch's `NowPlayingFaceState.lyricFont`.
     *
     * Both sides resolve through [WatchTypography.lyricsFontKey], so the preview cannot disagree
     * with the wrist about a choice the user just made - the same class of drift the note on
     * [clockTypeface] records.
     *
     * These are the song's words, not the track's title.
     */
    private fun lyricTypeface(): Typeface? {
        val key = WatchTypography.lyricsFontKey(wearLyricsFontKey, wearFontKey)
        if (WatchTypography.isFlexFont(key)) {
            return flexPreviewTypeface(
                    titleTypographySpec,
                    if (wearLyricsFontKey == WatchTypography.FLEX_FONT_KEY) {
                        lyricsFlexAxesSpec
                    } else {
                        flexAxesSpec
                    })
        }
        return WatchFontCatalog.typefaceFor(context, key) ?: fontRegular
    }

    /**
     * Typeface for an elapsed/total readout. The `follow` default preserves the caller's authored
     * face typeface, while an explicit choice applies the track-time weight/slant and gives Flex
     * its own variable-font instance just as the watch does.
     */
    private fun trackTimeTypeface(
            fallback: Typeface?,
            bold: Boolean = false
    ): Typeface? {
        val key = WatchTypography.trackTimeFontKey(wearTrackTimeFontKey)
        if (key == null && trackTimeTypographySpec.isIdentity) return fallback
        if (WatchTypography.isFlexFont(key)) {
            return flexPreviewTypeface(trackTimeTypographySpec, trackTimeFlexAxesSpec)
        }
        val base = if (key == null) {
            fallback
        } else {
            WatchFontCatalog.typefaceFor(context, key) ?: fallback
        }
        return styledPreviewTypeface(base, bold, trackTimeTypographySpec)
    }

    /** Draws one elapsed/total readout with the same relative typography contract as the watch. */
    private fun drawTrackTimeText(
            canvas: Canvas,
            text: String,
            x: Float,
            y: Float,
            designedTextSize: Float,
            designedColor: Int,
            fallback: Typeface? = fontRegular,
            bold: Boolean = false
    ) {
        textPaint.typeface = trackTimeTypeface(fallback, bold)
        textPaint.textSize = trackTimeTypographySpec.scaled(designedTextSize)
        textPaint.color = ColorUtils.setAlphaComponent(
                designedColor,
                trackTimeTypographySpec.applyAlpha(Color.alpha(designedColor)))
        textPaint.letterSpacing = trackTimeTypographySpec.trackingEm
        canvas.drawText(text, x, y, textPaint)
        // This Paint is shared by all preview drawings. Tracking cannot leak into the next line.
        textPaint.letterSpacing = 0f
    }

    /** Mirrors MainActivity.resolveClockColor: opacity-baked ARGB from the color-mode pref, with
     *  dynamic sampling the top-centre strip of the shown art (not the whole cover). */
    private fun resolveClockColor(): Int {
        val base = when (clockColorMode) {
            // Only the album colour is corrected - it is the one derived rather than chosen.
            // Mirrors MainActivity.adaptedClockAlbumColor.
            "album" -> albumAccent().let { base ->
                val background = clockBandLuminance()
                if (clockAdaptiveContrast && background != null) {
                    AdaptiveTextContrast.adapt(base, background)
                } else {
                    base
                }
            }
            "custom" -> parseHexOrNull(clockCustomColor) ?: Color.WHITE
            "dynamic" -> if (clockAreaIsLight()) Color.BLACK else Color.WHITE
            else -> Color.WHITE
        }
        val alpha = (clockOpacity.coerceIn(10, 100) / 100f * 255f).toInt()
        return ColorUtils.setAlphaComponent(base, alpha)
    }

    /** Whether the artwork region under the top-centre clock is light (→ black clock). Samples the
     *  same top strip the watch does; falls back to dark (→ white clock) with no artwork. */
    private fun clockAreaIsLight(): Boolean = clockBandLuminance()?.let { it > 0.55 } ?: false

    private fun drawSmallClock(
            canvas: Canvas,
            x: Float,
            y: Float,
            dp: (Float) -> Float,
            alpha: Float = 1f,
            sizeSp: Float = CLASSIC_CLOCK_SP
    ) {
        textPaint.style = Paint.Style.FILL
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = clockTypeface(clockTypographySpec)
        textPaint.letterSpacing = clockTypographySpec.trackingEm
        val resolved = resolveClockColor()
        textPaint.color = ColorUtils.setAlphaComponent(
                resolved,
                (Color.alpha(resolved) * alpha).toInt().coerceIn(0, 255)
        )
        // FaceClock and the classic ambient_clock are both authored at 15sp. Queue passes its
        // own 16sp curved-clock size. The miniature used a stale 13dp value for both.
        textPaint.textSize = dp(clockTypographySpec.scaled(sizeSp))
        val time = previewClockText()
        canvas.drawText(time, x, y, textPaint)
        // textPaint is shared across the whole preview; leaving the clock's tracking on it would
        // silently space out whatever draws next.
        textPaint.letterSpacing = 0f
    }

    /**
     * Canvas positions text by baseline while Compose's [FaceClock] positions its Text's layout
     * box. Convert the shared 5dp layout top using the configured clock font metrics instead of
     * passing a guessed baseline (the former 24dp anchor placed the miniature clock too low).
     */
    private fun drawFaceClock(
            canvas: Canvas,
            x: Float,
            screenTop: Float,
            dp: (Float) -> Float
    ) {
        textPaint.typeface = clockTypeface(clockTypographySpec)
        textPaint.textSize = dp(clockTypographySpec.scaled(CLASSIC_CLOCK_SP))
        val baseline = screenTop + dp(CLASSIC_CLOCK_TOP_PADDING_DP) - textPaint.fontMetrics.ascent
        drawSmallClock(canvas, x, baseline, dp)
    }

    /**
     * Uses the real wall clock for an interactive preview, except for the isolated moderation
     * thumbnail where a fixed value prevents personal device state from leaving the phone.
     */
    private fun previewClockText(): String {
        if (moderationPreviewMode) return MODERATION_PREVIEW_CLOCK
        val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
        return java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
                .format(java.util.Date())
    }

    /** The moderation bitmap must not vary with a device's current animation phase. */
    private fun previewAnimationTimeMillis(): Long =
            if (moderationPreviewMode) 0L else SystemClock.uptimeMillis()

    private data class PreviewMiniGeometry(
            val compact: Boolean,
            val pillWidth: Float,
            val pillHeight: Float,
            val gap: Float,
            val iconSize: Float,
            val totalWidth: Float,
            val rowBottom: Float,
            val curved: Boolean
    )

    /** Resolves the same count-aware geometry used by Wear. Classic keeps its metadata centered;
     *  the shortcut row therefore adapts to two/three buttons while retaining readable 40/36dp
     *  controls, and clamps
     *  legacy wide shapes to the usable screen chord instead of forcing the text out of place. */
    private fun previewMiniGeometry(
            iconCount: Int,
            cy: Float,
            radius: Float,
            dp: (Float) -> Float
    ): PreviewMiniGeometry {
        val compact = face != "classic"
        var baseW = if (compact) 46f else 52f
        var baseH = if (compact) 38f else 42f
        when (buttonsShape) {
            "circle", "square", "rounded_square_soft", "rounded_square_medium",
            "drop", "squircle", "leaf" -> {
                baseW = if (compact) 38f else 42f
                baseH = if (compact) 38f else 42f
            }
            "pill_wide_small", "rounded_rect_small" ->
                baseW = if (compact) 56f else 62f
            "pill_wide_medium", "rounded_rect_medium" ->
                baseW = if (compact) 66f else 72f
            "pill_wide_large", "rounded_rect_large" ->
                baseW = if (compact) 76f else 82f
            "pill_wide_xlarge" -> baseW = if (compact) 86f else 92f
        }

        val gapDp: Float
        val iconDp: Float
        if (!compact) {
            val contentWidthDp = radius * 2f / dp(1f).coerceAtLeast(0.01f)
            val equalAspectShape = buttonsShape in setOf(
                    "circle", "square", "rounded_square_soft", "rounded_square_medium",
                    "drop", "squircle", "leaf"
            )
            when {
                iconCount >= 3 -> {
                    baseH = minOf(baseH, 40f)
                    gapDp = 12f
                    val maxRowWidth = minOf(contentWidthDp * 0.84f, 164f)
                            .coerceAtLeast(108f)
                    val maxButtonWidth = ((maxRowWidth - gapDp * 2f) / 3f)
                            .coerceAtLeast(32f)
                    baseW = if (equalAspectShape) baseH else minOf(baseW, maxButtonWidth)
                    iconDp = 26f
                }
                iconCount == 2 -> {
                    baseH = minOf(baseH, 42f)
                    gapDp = 16f
                    val maxRowWidth = minOf(contentWidthDp * 0.78f, 150f)
                            .coerceAtLeast(80f)
                    val maxButtonWidth = ((maxRowWidth - gapDp) / 2f).coerceAtLeast(32f)
                    baseW = if (equalAspectShape) baseH else minOf(baseW, maxButtonWidth)
                    iconDp = 26f
                }
                else -> {
                    gapDp = 12f
                    baseW = minOf(baseW, (contentWidthDp - 12f).coerceAtLeast(30f))
                    iconDp = 24f
                }
            }
        } else {
            gapDp = if (iconCount == 2) 16f else 12f
            iconDp = 24f
        }

        val gap = dp(gapDp)
        val pillW = dp(baseW)
        val pillH = dp(baseH)
        val totalWidth = iconCount * pillW + (iconCount - 1) * gap

        val round = deviceRound != false
        val curved = round && buttonsCurveStyle != "flat"
        // Curved rows determine their resting line from one pill; the side buttons then rise
        // along the bezel with a bounded offset. Classic's row width was already count-clamped.
        val halfWidth = if (curved) pillW / 2f else totalWidth / 2f
        val autoMargin = if (!round) {
            dp(16f)
        } else if (radius > halfWidth) {
            radius - sqrt(radius * radius - halfWidth * halfWidth) + dp(6f)
        } else {
            dp(16f)
        }
        return PreviewMiniGeometry(
                compact = compact,
                pillWidth = pillW,
                pillHeight = pillH,
                gap = gap,
                iconSize = dp(iconDp),
                totalWidth = totalWidth,
                rowBottom = cy + radius - autoMargin,
                curved = curved
        )
    }

    /** The configurable mini-buttons row - only the slots the user actually configured (their
     *  real action icons), re-centered by count, at the user's background/color/curve. */
    private fun clampTimeY(
        desiredY: Float,
        miniConfigured: Boolean,
        radius: Float,
        cy: Float,
        dp: (Float) -> Float
    ): Float {
        if (!miniConfigured) return desiredY
        val icons = if (miniButtonIcons.isNotEmpty()) {
            miniButtonIcons
        } else if (surface == PreviewSurface.MINI_BUTTONS) {
            demoMiniButtonIcons
        } else {
            emptyList()
        }
        if (icons.isEmpty()) return desiredY
        val placement = MiniButtonPlacement.fromPreference(buttonsCurveStyle)
        // A rail sits against the side bezel and blocks nothing at the bottom, so the text keeps
        // the height it asked for - the same thing the watch reports through RAIL_TOP_FRACTION.
        if (placement.isRail) return desiredY
        val geometry = previewMiniGeometry(icons.size, cy, radius, dp)
        val riseScale = placement.riseScale
        val dx = geometry.totalWidth / 2f - geometry.pillWidth / 2f
        val outerDx = if (dx != 0f) dx + geometry.pillWidth / 2f else 0f
        val clamped = outerDx.coerceIn(-radius + 1f, radius - 1f)
        val referenceDx = (geometry.pillWidth / 2f).coerceIn(0f, radius - 1f)
        val referenceClearance = radius - sqrt(radius * radius - referenceDx * referenceDx)
        val outerClearance = radius - sqrt(radius * radius - clamped * clamped)
        val naturalRise = (outerClearance - referenceClearance).coerceAtLeast(0f) * riseScale
        val maxCap = if (buttonsCurveStyle == "curved_extreme") 36f else 18f
        val maxRise = if (!geometry.curved) {
            0f
        } else {
            minOf(naturalRise, dp(maxCap))
        } + if (geometry.curved && icons.size == 3 &&
                buttonsCurveStyle != "curved_extreme") dp(4f) else 0f
        val miniButtonsTop = geometry.rowBottom - geometry.pillHeight - maxRise
        return minOf(desiredY, miniButtonsTop - dp(10f))
    }

    /** Exact preview counterpart of Wear's centeredTransportTrackTimeOffset. Curved outer mini
     * buttons can rise beside the transport ring, but a centered time label must never be pulled
     * through that ring merely because those side pills occupy the same vertical band. */
    private fun centeredTransportTimeY(
            desiredY: Float,
            miniConfigured: Boolean,
            radius: Float,
            cy: Float,
            dp: (Float) -> Float
    ): Float {
        val screenDp = radius * 2f / dp(1f).coerceAtLeast(.01f)
        val ringBottom = dp(if (screenDp >= 225f) 39f else 31f)
        return clampTimeY(desiredY, miniConfigured, radius, cy, dp)
                .coerceAtLeast(cy + ringBottom + dp(10f))
    }

    /** The player's bottom band: the mini-buttons row if it shows, otherwise the awake Up Next
     *  pill when enabled (the watch shows one or the other, never both - see
     *  MainActivity.syncScreenButtonsVisibility). */
    private fun drawBottomChrome(canvas: Canvas, cx: Float, cy: Float, radius: Float, dp: (Float) -> Float) {
        val drewMiniButtons = drawMiniButtons(canvas, cx, cy, radius, dp)
        if (!drewMiniButtons && showUpNextPill && isPlayingShown()) {
            drawAwakeUpNextPill(canvas, cx, cy, radius, dp)
        }
    }

    /** The awake player Up Next pill, honouring the shared Up Next pill style (mirrors the watch's
     *  AwakeUpNextPill, which now reads the resolved colours from the face state). */
    private fun drawAwakeUpNextPill(canvas: Canvas, cx: Float, cy: Float, radius: Float, dp: (Float) -> Float) {
        val (fill, onColor) = awakePillColors()
        val width = radius * 2f * .84f
        val height = (radius * 2f * .25f).coerceIn(dp(44f), dp(52f))
        val centerY = cy + radius - dp(14f) - height / 2f
        val rect = RectF(cx - width / 2f, centerY - height / 2f, cx + width / 2f, centerY + height / 2f)
        if (Color.alpha(fill) > 0) {
            fillPaint.shader = null
            fillPaint.color = fill
            canvas.drawRoundRect(rect, height / 2f, height / 2f, fillPaint)
        }
        drawIcon(canvas, R.drawable.ic_queue_music,
                rect.left + dp(20f), centerY, dp(20f),
                ColorUtils.setAlphaComponent(onColor, 0xD1), 0xD1)
        // Two lines matching the watch's AwakeUpNextPill: a small "Up Next" label over the track.
        val textLeft = rect.left + dp(37f)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = fontBold
        textPaint.textSize = dp(10f)
        textPaint.color = ColorUtils.setAlphaComponent(onColor, 0x99)
        canvas.drawText(context.getString(R.string.quick_panel_default_up_next),
                textLeft, centerY - dp(4f), textPaint)
        textPaint.typeface = titleTypeface(bold = false)
        textPaint.textSize = dp(12f)
        textPaint.color = ColorUtils.setAlphaComponent(onColor, 0xE6)
        val text = ellipsize(
                context.getString(R.string.preview_sample_next_title) + " · " +
                        context.getString(R.string.preview_sample_artist),
                width - dp(52f))
        canvas.drawText(text, textLeft, centerY + dp(12f), textPaint)
    }

    /** The colours the mini-button styles draw from, matching wear's `miniButtonPalette`: the
     *  theme colour is resolved for the selected face here, since Expressive paints "solid_theme"
     *  as a tonal surface of it and every other face uses it raw. */
    private fun miniButtonPalette(): MiniButtonSurfaces.Palette {
        val themeAccent = if (face == "expressive") {
            PaletteTransforms.tonalSurface(ACCENT_NEUTRAL, 0.74f, 0.40f, 0.92f)
        } else {
            ACCENT_NEUTRAL
        }
        return MiniButtonSurfaces.paletteFor(albumAccent(), themeAccent)
    }

    /**
     * The mini buttons the preview should be showing, or empty when the row is not active for the
     * playback state being previewed.
     *
     * Shared by the bottom row and by a face that hosts the buttons itself, so the two can never
     * disagree about whether there are any. Mirrors hasActiveMiniButtons on the watch: a face with
     * mini buttons off for the current playback state never shows them regardless of what is
     * configured, and the preview's shown state (playing vs paused) picks which toggle. Mini
     * buttons themselves are playback-only; paused is deliberately not treated as idle.
     */
    private fun activeMiniButtonIcons(): List<PreviewActionIcon> {
        if (!ActivityVisibility.isActive(miniButtonsMode, isPlayingShown())) return emptyList()
        if (!isPlayingShown()) return emptyList()
        return when {
            miniButtonIcons.isNotEmpty() -> miniButtonIcons
            surface == PreviewSurface.MINI_BUTTONS -> demoMiniButtonIcons
            else -> emptyList()
        }
    }

    private fun drawMiniButtons(canvas: Canvas, cx: Float, cy: Float, radius: Float, dp: (Float) -> Float): Boolean {
        val icons = activeMiniButtonIcons()
        if (icons.isEmpty()) {
            return false
        }
        // A face that hosts the row draws these buttons inside its own composition instead (Chat's
        // circles), so the shared row must not draw them too - the same single-render rule the
        // watch applies. Reported as "no row", which is what reclaims the band.
        if (MiniButtonPlacement.isHostedByFace(face)) {
            return false
        }

        val geometry = previewMiniGeometry(icons.size, cy, radius, dp)
        val compactBezelRow = geometry.compact
        val pillW = geometry.pillWidth
        val pillH = geometry.pillHeight
        val gap = geometry.gap
        val iconSize = geometry.iconSize
        val totalWidth = geometry.totalWidth
        val rowBottom = geometry.rowBottom

        val neutralSkin = neutralMiniButtonSkin(dp)
        // The style's colours are decided by the shared resolver, not by a copy of the watch's
        // `when` kept in step by hand - which is how this preview came to stroke glow_exp with the
        // un-lifted tone and pick its icon colour on a naive luminance split where the watch used
        // the WCAG crossover. "Follow layout" is the one value with no colour of its own, and is
        // where the per-face neutralSkin still applies.
        val buttonSurface = MiniButtonSurfaces.resolve(buttonsBgStyle, miniButtonPalette())
        val pillColor =
                if (buttonSurface.followsFaceNeutral) neutralSkin.fill else buttonSurface.fillArgb
        val pillStroke =
                if (buttonSurface.followsFaceNeutral) neutralSkin.stroke else buttonSurface.strokeArgb
        val pillStrokeWidth = when {
            buttonSurface.followsFaceNeutral -> neutralSkin.strokeWidth
            buttonSurface.strokeArgb == Color.TRANSPARENT -> 0f
            else -> dp(buttonSurface.strokeWidthDp)
        }
        val pillCorner = if (buttonSurface.followsFaceNeutral) neutralSkin.corner else pillH
        val solidIconTint = buttonSurface.iconTintArgb
        val forceGroupIconTint = buttonSurface.forceIconTint

        val placement = MiniButtonPlacement.fromPreference(buttonsCurveStyle)
        val curveFraction = if (deviceRound == false || !placement.followsCurve) {
            null
        } else {
            placement.tiltFraction
        }

        fillPaint.shader = null
        strokePaint.shader = null
        strokePaint.pathEffect = null

        val drawShape = { paint: Paint, cxVal: Float, cyVal: Float ->
            val rect = RectF(cxVal - pillW / 2f, cyVal - pillH / 2f, cxVal + pillW / 2f, cyVal + pillH / 2f)
            when (buttonsShape) {
                "square" -> {
                    canvas.drawRect(rect, paint)
                }
                "rounded_square_soft", "rounded_rect_small" -> {
                    val r = dp(8f)
                    canvas.drawRoundRect(rect, r, r, paint)
                }
                "rounded_square_medium", "rounded_rect_medium", "rounded_rect_large" -> {
                    val r = dp(12f)
                    canvas.drawRoundRect(rect, r, r, paint)
                }
                "squircle" -> {
                    // 15dp, matching MainActivity.applyButtonShape. The two shape tables are
                    // written out by hand on both sides and this was the one value that had drifted.
                    val r = dp(15f)
                    canvas.drawRoundRect(rect, r, r, paint)
                }
                "leaf" -> {
                    val tl = dp(16f)
                    val tr = dp(4f)
                    val br = dp(16f)
                    val bl = dp(4f)
                    val path = Path().apply {
                        addRoundRect(rect, floatArrayOf(tl, tl, tr, tr, br, br, bl, bl), Path.Direction.CW)
                    }
                    canvas.drawPath(path, paint)
                }
                "drop" -> {
                    val c = dp(18f)
                    val path = Path().apply {
                        addRoundRect(rect, floatArrayOf(0f, 0f, 0f, 0f, c, c, c, c), Path.Direction.CW)
                    }
                    canvas.drawPath(path, paint)
                }
                "circle" -> {
                    val r = pillH / 2f
                    canvas.drawRoundRect(rect, r, r, paint)
                }
                else -> {
                    canvas.drawRoundRect(rect, pillCorner, pillCorner, paint)
                }
            }
        }

        val riseScale = when (buttonsCurveStyle) {
            "curved_extreme" -> 1.0f
            "curved" -> 1.2f
            "curved_medium" -> 1.0f
            "curved_soft" -> 0.8f
            "curved_gentle" -> 0.6f
            else -> 1.0f
        }
        val opacityLayer = if (buttonsOpacity < 100) {
            canvas.saveLayerAlpha(
                    RectF(cx - radius, cy - radius, cx + radius, cy + radius),
                    (buttonsOpacity * 255 / 100).coerceIn(0, 255)
            )
        } else {
            null
        }

        for ((i, icon) in icons.withIndex()) {
            var pillCx = cx - totalWidth / 2f + pillW / 2f + i * (pillW + gap)
            var pillCy = rowBottom - pillH / 2f
            var rotation = 0f

            // A rail leaves the bottom band entirely, so it replaces the row geometry rather than
            // adjusting it - and returns before the rise/tilt pass, which has nothing to say about
            // a vertical stack. Mirrors MainActivity.applyScreenButtonsRail; the two are kept in
            // step through the shared MiniButtonPlacement rather than by matching `when` blocks,
            // which is how this preview and the watch drifted apart before.
            if (placement.isRail) {
                val onLeft = when (placement.axis) {
                    MiniButtonPlacement.Axis.LEFT_RAIL -> true
                    MiniButtonPlacement.Axis.RIGHT_RAIL -> false
                    else -> MiniButtonPlacement.splitSideIsLeft(i)
                }
                val group = when (placement.axis) {
                    MiniButtonPlacement.Axis.SPLIT_RAILS ->
                        icons.indices.filter {
                            MiniButtonPlacement.splitSideIsLeft(it) == onLeft
                        }
                    else -> icons.indices.toList()
                }
                val slot = group.indexOf(i).coerceAtLeast(0)
                val step = pillH + dp(8f)
                pillCy = cy + (-(group.size - 1) / 2f + slot) * step
                val dyFromCenter = kotlin.math.abs(pillCy - cy)
                val corner = (dyFromCenter + pillH / 2f).coerceAtMost(radius - 1f)
                val halfExtent = if (deviceRound == false) {
                    radius
                } else {
                    sqrt(radius * radius - corner * corner)
                }
                pillCx = cx + (if (onLeft) -1f else 1f) *
                        (halfExtent - dp(6f) - pillW / 2f)
            } else if (placement.axis == MiniButtonPlacement.Axis.BOTTOM_ROW_SPREAD &&
                    icons.size >= 2 && (i == 0 || i == icons.lastIndex)) {
                // Reach is the chord at the row's depth, not half the screen - see
                // MainActivity.spreadOffsetsFor.
                val dyFromCenter = kotlin.math.abs(pillCy - cy).coerceAtMost(radius - 1f)
                val halfChord = if (deviceRound == false) {
                    radius
                } else {
                    sqrt(radius * radius - dyFromCenter * dyFromCenter)
                }
                val sign = if (i == 0) -1f else 1f
                val target = cx + sign * (halfChord - dp(6f) - pillW / 2f)
                // Never pull a pill inwards, as on the watch.
                pillCx = if (sign < 0f) minOf(target, pillCx) else maxOf(target, pillCx)
            }

            if (curveFraction != null && !placement.isRail) {
                val dx = pillCx - cx
                val clamped = dx.coerceIn(-radius + 1f, radius - 1f)
                // Wear calculates clearance from the outer edge of a side pill, not only its
                // centre. Mirroring that here prevents side buttons from drifting lower/clipping.
                val outerDx = if (dx != 0f) {
                    dx + (if (dx > 0f) 1f else -1f) * pillW / 2f
                } else {
                    clamped
                }.coerceIn(-radius + 1f, radius - 1f)
                val referenceDx = (pillW / 2f).coerceIn(0f, radius - 1f)
                val referenceClearance = radius - sqrt(radius * radius - referenceDx * referenceDx)
                val outerClearance = radius - sqrt(radius * radius - outerDx * outerDx)
                val naturalRise = (outerClearance - referenceClearance)
                        .coerceAtLeast(0f) * riseScale
                val baseRise = minOf(naturalRise, dp(placement.maxRiseDp))
                val isOuterOfThree = icons.size == 3 && (i == 0 || i == icons.lastIndex)
                pillCy -= baseRise + if (isOuterOfThree &&
                        placement != MiniButtonPlacement.CURVED_EXTREME) dp(4f) else 0f
                val tangentRotation = curveFraction *
                        -Math.toDegrees(asin((clamped / radius).toDouble())).toFloat()
                val maxRotation = placement.maxRotationDegrees
                rotation = tangentRotation.coerceIn(-maxRotation, maxRotation)
            }

            canvas.save()
            canvas.rotate(rotation, pillCx, pillCy)
            if (pillColor != Color.TRANSPARENT) {
                fillPaint.style = Paint.Style.FILL
                fillPaint.color = pillColor
                drawShape(fillPaint, pillCx, pillCy)
            }
            if (pillStrokeWidth > 0f) {
                strokePaint.style = Paint.Style.STROKE
                strokePaint.strokeWidth = pillStrokeWidth
                strokePaint.color = pillStroke
                drawShape(strokePaint, pillCx, pillCy)
            }
            iconDst.set(pillCx - iconSize / 2f, pillCy - iconSize / 2f,
                    pillCx + iconSize / 2f, pillCy + iconSize / 2f)
            bitmapPaint.colorFilter = solidIconTint?.takeIf {
                forceGroupIconTint || icon.tintable
            }?.let {
                android.graphics.PorterDuffColorFilter(it, android.graphics.PorterDuff.Mode.SRC_IN)
            }
            bitmapPaint.alpha = ((screenThemeSpec().iconAlpha.takeIf { it > 0f } ?: 1f) * 255)
                    .toInt().coerceIn(0, 255)
            canvas.drawBitmap(icon.bitmap, null, iconDst, bitmapPaint)
            canvas.restore()
        }
        opacityLayer?.let(canvas::restoreToCount)
        bitmapPaint.colorFilter = null
        bitmapPaint.alpha = 255
        return true
    }

    /**
     * The Chat face's circles, drawn as the configured mini buttons.
     *
     * The colours come from the same [MiniButtonSurfaces] resolution the shared row uses, so a
     * button previewed inside this face and one previewed in the row cannot look like two
     * different features. "Follow layout" - the default - has no colour of its own, which is what
     * leaves this face's designed accent circle in place until a style is picked. The circle stays
     * a circle whatever the shape preference says, exactly as on the watch: the shape and curve
     * pickers are hidden for this face rather than silently ignored.
     */
    private fun drawChatMiniButtons(
            canvas: Canvas,
            buttons: List<PreviewActionIcon>,
            centerX: (Int) -> Float,
            centerY: Float,
            diameter: Float,
            faceAccent: Int,
            dp: (Float) -> Float
    ) {
        val surface = MiniButtonSurfaces.resolve(buttonsBgStyle, miniButtonPalette())
        val onAccent = if (AdaptiveTextContrast.prefersDarkText(faceAccent)) {
            ColorUtils.setAlphaComponent(Color.BLACK, 0xD1)
        } else {
            Color.WHITE
        }
        val opacityLayer = if (buttonsOpacity < 100) {
            canvas.saveLayerAlpha(
                    RectF(centerX(0) - diameter, centerY - diameter,
                            centerX(buttons.lastIndex) + diameter, centerY + diameter),
                    (buttonsOpacity * 255 / 100).coerceIn(0, 255))
        } else {
            null
        }
        val iconSize = diameter * .48f
        fillPaint.shader = null
        strokePaint.shader = null
        strokePaint.pathEffect = null
        buttons.forEachIndexed { index, button ->
            val bx = centerX(index)
            val fill = if (surface.followsFaceNeutral) faceAccent else surface.fillArgb
            if (Color.alpha(fill) > 0) {
                fillPaint.color = fill
                canvas.drawCircle(bx, centerY, diameter / 2f, fillPaint)
            }
            if (!surface.followsFaceNeutral && Color.alpha(surface.strokeArgb) > 0 &&
                    surface.strokeWidthDp > 0f) {
                strokePaint.style = Paint.Style.STROKE
                strokePaint.strokeWidth = dp(surface.strokeWidthDp)
                strokePaint.color = surface.strokeArgb
                canvas.drawCircle(bx, centerY, diameter / 2f - strokePaint.strokeWidth / 2f,
                        strokePaint)
            }
            val tint = if (surface.followsFaceNeutral) {
                onAccent.takeIf { button.tintable }
            } else {
                surface.iconTintArgb?.takeIf { surface.forceIconTint || button.tintable }
            }
            iconDst.set(bx - iconSize / 2f, centerY - iconSize / 2f,
                    bx + iconSize / 2f, centerY + iconSize / 2f)
            bitmapPaint.colorFilter = tint?.let {
                android.graphics.PorterDuffColorFilter(it, android.graphics.PorterDuff.Mode.SRC_IN)
            }
            canvas.drawBitmap(button.bitmap, null, iconDst, bitmapPaint)
        }
        opacityLayer?.let(canvas::restoreToCount)
        bitmapPaint.colorFilter = null
    }

    private data class MiniButtonSkin(
            val fill: Int,
            val stroke: Int = Color.TRANSPARENT,
            val strokeWidth: Float = 0f,
            val corner: Float
    )

    /** Mirrors wear's neutralMiniButtonBackground: the "glass" pill is independent of the
     *  selected face (the user's choice, not layout-dictated), adapting only to the explicit
     *  ScreenTheme setting. */
    private fun neutralMiniButtonSkin(dp: (Float) -> Float): MiniButtonSkin {
        val round = dp(38f)
        return when (screenTheme) {
            "minimal", "amoled" -> MiniButtonSkin(
                    Color.TRANSPARENT, 0x66FFFFFF, dp(1f), round)
            "contrast" -> MiniButtonSkin(Color.BLACK, Color.WHITE, dp(2f), round)
            else -> MiniButtonSkin(0x28FFFFFF, corner = round)
        }
    }

    // --- Expressive face (mirrors wear's ExpressiveFace geometry) ---

    private fun cookieProfile(angleRad: Float, modulation: Float): Float {
        // +PI/2 anchors a lobe crest at 12 o'clock for any lobe count - keep in sync with the
        // wear ExpressiveFace's cookieProfile.
        val angleFromTop = angleRad + (Math.PI / 2.0).toFloat()
        val wave = tanh(COOKIE_SOFTNESS * cos(COOKIE_LOBES * angleFromTop)) / tanh(COOKIE_SOFTNESS)
        return 1f + modulation * wave
    }

    private fun contourPath(cx: Float, cy: Float, radius: Float, modulation: Float, fromDeg: Float, toDeg: Float): Path {
        val path = Path()
        var degrees = fromDeg
        var first = true
        while (degrees <= toDeg) {
            val angleRad = Math.toRadians((degrees - 90f).toDouble()).toFloat()
            val r = radius * cookieProfile(angleRad, modulation)
            val x = cx + r * cos(angleRad)
            val y = cy + r * sin(angleRad)
            if (first) {
                path.moveTo(x, y)
                first = false
            } else {
                path.lineTo(x, y)
            }
            degrees += 2f
        }
        return path
    }

    private fun drawExpressive(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            radius: Float,
            screenBounds: RectF,
            dp: (Float) -> Float
    ) {
        val accent = albumAccent()
        val theme = screenThemeSpec()

        drawPlayerShading(canvas, screenBounds, cx, cy, radius)

        val sideContainer = tonal(accent, .74f, .40f, .85f)
        val centerContainer = tonal(accent, .87f, .30f, .70f)
        val onContainer = tonal(accent, .16f, .25f, .60f)
        val artistColor = artistTextColor()
        val progressColor = resolveTint(progressMode, progressCustom, progressDesaturated)

        if (alwaysShowTime || clockPreviewForced()) drawFaceClock(canvas, cx, cy - radius, dp)

        // Title/artist near the top. ExpressiveFace calls AdaptiveTitleText with an explicit
        // 12sp floor against its 16sp size, so the title honours the user's text mode here too -
        // this used to marquee unconditionally on the claim that the watch did, which it does not.
        if (showTrackTitle) {
            drawAdaptiveTitle(
                    canvas,
                    cx,
                    cy - radius + dp(43f),
                    radius * 1.45f,
                    dp(16f),
                    titleAlpha(Color.WHITE),
                    minSize = dp(12f))
        }
        // Artist, or the "Playback Stopped" status in white while paused (the watch's expressive
        // face mirrors the same textArtist line the classic face swaps).
        if (isPlayingShown() && showTrackArtist) {
            drawArtistLine(
                    canvas, displayArtist(), cx, cy - radius + dp(57f), radius * 1.45f,
                    artistColor, dp(12f), sourceGlyph = true, glyphSize = dp(13f))
        } else if (!isPlayingShown()) {
            // Status copy stays neutral white and keeps the mark beside it, as the face does.
            drawArtistLine(
                    canvas, context.getString(R.string.preview_playback_stopped),
                    cx, cy - radius + dp(57f), radius * 1.45f,
                    Color.WHITE, dp(12f), sourceGlyph = true, glyphSize = dp(13f))
        }

        // Keep the same 225dp breakpoint as expressiveMetrics() on Wear.
        val largeScreen = min(deviceWidthDp, deviceHeightDp) >= 225f
        val sideWidth = dp(if (largeScreen) 48f else 42f)
        val sideHeight = dp(if (largeScreen) 58f else 50f)
        val ringBox = dp(if (largeScreen) 78f else 62f)
        val cookieSize = dp(if (largeScreen) 62f else 48f)
        val gap = dp(4f)
        val sideOffset = ringBox / 2f + gap + sideWidth / 2f
        // Every other control-style theme only fades or scales these icons; only Hidden zeroes
        // them out. Expressive's cookie/transport row is its one visual focus, so mirror the
        // watch: always show these icons at full opacity - Hidden becomes a no-op here.
        val expressiveIconAlpha = ((theme.iconAlpha.takeIf { it > 0f } ?: 1f) * 255).toInt().coerceIn(0, 255)
        val expressiveControlsVisible = true
        if (expressiveControlsVisible) {
            fillPaint.color = sideContainer
            // Corner radius = half the shorter (width) side -> rounded top and bottom.
            val sideCorner = sideWidth / 2f
            for (side in intArrayOf(-1, 1)) {
                val bcx = cx + side * sideOffset
                canvas.drawRoundRect(bcx - sideWidth / 2f, cy - sideHeight / 2f,
                        bcx + sideWidth / 2f, cy + sideHeight / 2f,
                        sideCorner, sideCorner, fillPaint)
            }
            drawActionIcon(
                    canvas,
                    quadrantIcons[ScreenQuadrant.LEFT],
                    commonR.drawable.action_skip_prev,
                    cx - sideOffset,
                    cy,
                    sideWidth * 0.5f * theme.iconScale,
                    onContainer,
                    expressiveIconAlpha
            )
            drawActionIcon(
                    canvas,
                    quadrantIcons[ScreenQuadrant.RIGHT],
                    commonR.drawable.action_skip_next,
                    cx + sideOffset,
                    cy,
                    sideWidth * 0.5f * theme.iconScale,
                    onContainer,
                    expressiveIconAlpha
            )
        }

        // Cookie button + contour-following ring. Paused flattens both into plain circles and
        // swaps the icon, matching the watch face's morph.
        val playing = isPlayingShown()
        val cookieModulation = if (playing) COOKIE_MODULATION else 0f
        val ringModulation = if (playing) RING_MODULATION else 0f
        val cookieRadius = cookieSize / 2f / (1f + COOKIE_MODULATION)
        if (expressiveControlsVisible) {
            fillPaint.color = centerContainer
            val cookiePath = contourPath(cx, cy, cookieRadius, cookieModulation, 0f, 360f).apply { close() }
            canvas.drawPath(cookiePath, fillPaint)
            drawIcon(canvas,
                    if (playing) commonR.drawable.action_pause_expressive
                    else commonR.drawable.action_play_filled,
                    cx, cy, cookieSize * 0.48f * theme.iconScale, onContainer,
                    expressiveIconAlpha)
        }

        val stroke = dp(if (largeScreen) 4f else 3f)
        val ringRadius = (ringBox / 2f - stroke) / (1f + RING_MODULATION)
        val sweep = progressFraction() * 360f
        val halfGap = RING_GAP_DEGREES / 2f
        strokePaint.strokeWidth = stroke
        strokePaint.strokeCap = Paint.Cap.ROUND
        // Track wraps back to 12 o'clock (no gap at the start); one gap straddles the playhead.
        strokePaint.color = 0x4DFFFFFF
        if (sweep + halfGap < 360f) {
            canvas.drawPath(contourPath(cx, cy, ringRadius, ringModulation, sweep + halfGap, 360f), strokePaint)
        }
        if (sweep > halfGap) {
            strokePaint.color = progressColor
            canvas.drawPath(contourPath(cx, cy, ringRadius, ringModulation, 0f, sweep - halfGap), strokePaint)
        }
        // No thumb dot - the gap between the played and track segments marks the position,
        // matching the watch face.

        if (trackTimeVisible()) {
            textPaint.typeface = fontRegular
            textPaint.color = 0xB3FFFFFF.toInt()
            textPaint.textSize = dp(10f)
            // Same preferred 45dp offset as Wear, clamped against the measured mini-button row.
            val miniConfigured = isPlayingShown() &&
                    (miniButtonIcons.isNotEmpty() || surface == PreviewSurface.MINI_BUTTONS)
            drawTrackTimeText(
                    canvas,
                    timeText(),
                    cx,
                    centeredTransportTimeY(cy + dp(45f), miniConfigured, radius, cy, dp),
                    dp(10f),
                    0xB3FFFFFF.toInt(),
                    textPaint.typeface)
        }

        // The watch's expressive face has no default queue/volume/overflow trio - that row is
        // left entirely to the user's configured mini buttons.
        drawBottomChrome(canvas, cx, cy, radius, dp)
    }

    /**
     * Carousel's own miniature: the cover rail with its darkened neighbours, the artist band above
     * it and the chord-inset title below.
     *
     * Its own renderer rather than a branch inside [drawCuratedPlayer], because Carousel shares
     * none of that composition - no transport row, no progress ring, and text anchored to the cover
     * rather than to the screen. It used to fall through to the generic curated layout, so the
     * picker showed a face that looked nothing like what the watch actually draws.
     *
     * The geometry constants are the ones CarouselFace.kt derives its layout from, duplicated here
     * because `mobile` cannot depend on `wear` - keep the two in step, as with the cookie/ring
     * geometry the other faces already duplicate. The *inset* math is shared for real, through
     * [RoundScreenText], so the preview cannot disagree about where a title gets cut.
     */
    private fun drawCarouselPlayer(
            canvas: Canvas,
            geometry: PreviewGeometry,
            dp: (Float) -> Float
    ) {
        val cx = geometry.cx
        val cy = geometry.cy
        val radius = geometry.radius
        val screen = radius * 2f
        val screenTop = cy - radius

        drawPlayerShading(canvas, geometry.bounds, cx, cy, radius)

        val art = displayedArt()
        val shape = CoverShape.fromPreference(carouselCardShape)
        val railCenterY = screenTop + screen * CAROUSEL_RAIL_CENTER

        fun card(size: Float, dx: Float, shade: Float) {
            val rect = RectF(
                    cx + dx - size / 2f, railCenterY - size / 2f,
                    cx + dx + size / 2f, railCenterY + size / 2f)
            val corner = size * shape.cornerFraction
            canvas.save()
            canvas.clipPath(Path().apply { addRoundRect(rect, corner, corner, Path.Direction.CW) })
            if (art != null) {
                drawArtwork(canvas, art, rect, 255)
            } else {
                fillPaint.shader = null
                fillPaint.color = ColorUtils.setAlphaComponent(albumAccent(), 0x2E)
                canvas.drawRect(rect, fillPaint)
            }
            if (shade > 0f) {
                fillPaint.shader = null
                fillPaint.color =
                        ColorUtils.setAlphaComponent(Color.BLACK, (shade * 255f).toInt())
                canvas.drawRect(rect, fillPaint)
            }
            canvas.restore()
        }

        // Outermost first so each nearer card overlaps the one behind it - the face's own paint
        // order. Every card shows the same cover because the preview has only one bitmap to work
        // with; the miniature is about the composition, not the queue's real contents.
        card(screen * .34f, -screen * .32f, CAROUSEL_FAR_SHADE)
        card(screen * .34f, screen * .32f, CAROUSEL_FAR_SHADE)
        card(screen * .46f, -screen * .17f, CAROUSEL_NEAR_SHADE)
        card(screen * .46f, screen * .17f, CAROUSEL_NEAR_SHADE)
        card(screen * CAROUSEL_CARD_FRACTION, 0f, 0f)

        textPaint.style = Paint.Style.FILL
        textPaint.textAlign = Paint.Align.CENTER

        val artistVisible = showTrackArtist || !isPlayingShown()
        if (artistVisible) {
            val inset = RoundScreenText.sideInsetFor(
                    CAROUSEL_ARTIST_TOP, CAROUSEL_ARTIST_TOP + CAROUSEL_ARTIST_ROW)
            val available = screen * (1f - 2f * inset)
            val baseline =
                    screenTop + screen * (CAROUSEL_ARTIST_TOP + CAROUSEL_ARTIST_ROW * .72f)
            drawArtistLine(
                    canvas,
                    if (isPlayingShown()) displayArtist().uppercase()
                    else context.getString(R.string.preview_playback_stopped),
                    cx, baseline, available,
                    if (isPlayingShown()) artistTextColor() else Color.WHITE,
                    dp(8f),
                    sourceGlyph = true,
                    glyphSize = dp(CAROUSEL_SOURCE_ICON_SIZE))
        }

        if (showTrackTitle) {
            val title = displayTitle().uppercase()
            val titleSize = dp(11f)
            val lineHeight = dp(12.5f) / screen
            val firstBaseline = screenTop + screen * CAROUSEL_TITLE_TOP + dp(9f)
            fun widthForLines(lines: Int) = screen * (1f - 2f *
                    RoundScreenText.sideInsetForLines(CAROUSEL_TITLE_TOP, lineHeight, lines))
            // The usable chord narrows the deeper the block reaches, and how deep it reaches is
            // decided by the line count the text mode settles on - so plan at the one-line width
            // first and, only if it wrapped, re-plan at the narrower width those lines actually
            // get. This is the settle the face runs through AdaptiveTitleText's onLineCount,
            // whose growth is monotonic for the same reason: a narrower column can add a line,
            // which would narrow it again.
            var plan = planTitle(
                    widthForLines(1), titleSize, titleSize * .62f, title, bold = false)
            val settledWidth = if (plan.lines.size > 1) {
                widthForLines(plan.lines.size).also {
                    plan = planTitle(it, titleSize, titleSize * .62f, title, bold = false)
                }
            } else {
                widthForLines(1)
            }
            // No designedTracking: CarouselFace tracks its *artist* line and its ambient variant,
            // but passes no letterSpacing to the awake title's AdaptiveTitleText.
            drawTitlePlan(
                    canvas, plan, cx, firstBaseline, settledWidth,
                    titleAlpha(Color.WHITE), bold = false, lineHeight = dp(12.5f))
        }

        if (alwaysShowTime || clockPreviewForced()) {
            drawFaceClock(canvas, cx, cy - radius, dp)
        }
        drawBottomChrome(canvas, cx, cy, radius, dp)
    }

    /**
     * Ribbon frames the current cover with the same tall queue-card windows the watch uses. The
     * phone preview has no queue payload, so every capsule deliberately reuses the available
     * current/sample cover rather than inventing coloured placeholders that would misrepresent
     * the wrist layout.
     */
    private fun drawRibbonPlayer(
            canvas: Canvas,
            geometry: PreviewGeometry,
            dp: (Float) -> Float
    ) {
        val cx = geometry.cx
        val radius = geometry.radius
        val screen = radius * 2f
        val screenLeft = cx - radius
        val screenTop = geometry.cy - radius

        val columnWidth = screen * FaceGeometry.Ribbon.COLUMN_WIDTH_FRACTION
        val columnHeight = screen * FaceGeometry.Ribbon.COLUMN_HEIGHT_FRACTION
        val columnTop = screenTop + screen * FaceGeometry.Ribbon.COLUMN_TOP_FRACTION
        val columnCorner = min(columnWidth, columnHeight) *
                FaceGeometry.Ribbon.COLUMN_CORNER_FRACTION
        val outerCenter = screenLeft + screen * FaceGeometry.Ribbon.OUTER_COLUMN_CENTER_X
        val innerCenter = screenLeft + screen * FaceGeometry.Ribbon.INNER_COLUMN_CENTER_X
        val rightInnerCenter = screenLeft + screen * (1f - FaceGeometry.Ribbon.INNER_COLUMN_CENTER_X)
        val rightOuterCenter = screenLeft + screen * (1f - FaceGeometry.Ribbon.OUTER_COLUMN_CENTER_X)
        val art = displayedArt()
        listOf(outerCenter, innerCenter, rightInnerCenter, rightOuterCenter).forEach { centerX ->
            val column = RectF(
                    centerX - columnWidth / 2f,
                    columnTop,
                    centerX + columnWidth / 2f,
                    columnTop + columnHeight
            )
            drawClippedFaceArtwork(
                    canvas,
                    art,
                    column,
                    columnCorner,
                    fallbackColor = ColorUtils.setAlphaComponent(albumAccent(), 0x55)
            )
        }

        val coverWidth = screen * FaceGeometry.Ribbon.CENTER_COVER_WIDTH_FRACTION
        val coverHeight = screen * FaceGeometry.Ribbon.CENTER_COVER_HEIGHT_FRACTION
        val coverTop = screenTop + screen * FaceGeometry.Ribbon.CENTER_COVER_TOP_FRACTION
        val cover = RectF(
                cx - coverWidth / 2f,
                coverTop,
                cx + coverWidth / 2f,
                coverTop + coverHeight
        )
        drawClippedFaceArtwork(
                canvas,
                art,
                cover,
                min(coverWidth, coverHeight) * FaceGeometry.Ribbon.CENTER_COVER_CORNER_FRACTION,
                fallbackColor = 0xFF242426.toInt()
        )

        // The hairline the watch draws in the gap between the rail and the title. Same switch,
        // same resolved progress colour, same band - see RibbonProgress.
        if (internalProgressVisible) {
            val barWidth = screen * FaceGeometry.Ribbon.PROGRESS_WIDTH_FRACTION
            val barHeight = dp(FaceGeometry.Ribbon.PROGRESS_THICKNESS_DP)
            val barCenterY = screenTop + screen * FaceGeometry.Ribbon.PROGRESS_CENTER_FRACTION
            val bar = RectF(
                    cx - barWidth / 2f,
                    barCenterY - barHeight / 2f,
                    cx + barWidth / 2f,
                    barCenterY + barHeight / 2f)
            fillPaint.shader = null
            fillPaint.color = 0x38FFFFFF
            canvas.drawRoundRect(bar, barHeight, barHeight, fillPaint)
            val filled = bar.width() * progressFraction()
            if (filled > 0f) {
                fillPaint.color = resolveTint(progressMode, progressCustom, progressDesaturated)
                canvas.drawRoundRect(
                        RectF(bar.left, bar.top, bar.left + filled, bar.bottom),
                        barHeight, barHeight, fillPaint)
            }
        }

        // Both text bands take their width from the chord at their own depth, exactly as the face
        // does. They used to carry a third and fourth hand-picked inset (.76/.78 here, .13 on the
        // watch), all of them too wide for the title's depth on a round dial.
        val titleLineFraction = dp(FaceGeometry.Ribbon.TITLE_LINE_HEIGHT_DP) / screen
        val titleLines = RoundScreenText.linesThatFit(
                top = FaceGeometry.Ribbon.TITLE_TOP_FRACTION,
                lineHeight = titleLineFraction,
                maxLines = FaceGeometry.Ribbon.TITLE_MAX_LINES)
        val titleInset = RoundScreenText.sideInsetForLines(
                top = FaceGeometry.Ribbon.TITLE_TOP_FRACTION,
                lineHeight = titleLineFraction,
                lines = titleLines)

        textPaint.style = Paint.Style.FILL
        textPaint.textAlign = Paint.Align.CENTER
        if (showTrackArtist || !isPlayingShown()) {
            val artist = if (isPlayingShown()) displayArtist()
            else context.getString(R.string.preview_playback_stopped)
            val artistInset = RoundScreenText.sideInsetFor(
                    top = FaceGeometry.Ribbon.ARTIST_TOP_FRACTION,
                    bottom = FaceGeometry.Ribbon.ARTIST_TOP_FRACTION +
                            dp(FaceGeometry.Ribbon.ARTIST_LINE_HEIGHT_DP) / screen)
            drawArtistLine(
                    canvas,
                    artist,
                    cx,
                    screenTop + screen * FaceGeometry.Ribbon.ARTIST_BASELINE_FRACTION,
                    screen * (1f - artistInset * 2f),
                    if (isPlayingShown()) artistTextColor() else Color.WHITE,
                    dp(12f),
                    bold = true,
                    sourceGlyph = isPlayingShown(),
                    glyphSize = dp(18f)
            )
        }
        if (showTrackTitle) {
            drawAdaptiveTitle(
                    canvas,
                    cx,
                    screenTop + screen * FaceGeometry.Ribbon.TITLE_BASELINE_FRACTION,
                    screen * (1f - titleInset * 2f),
                    dp(18f),
                    titleAlpha(Color.WHITE),
                    bold = true,
                    minSize = dp(11f),
                    maxLines = titleLines
            )
        }
        if (alwaysShowTime || clockPreviewForced()) {
            drawSmallClock(
                    canvas,
                    cx,
                    screenTop + screen * FaceGeometry.Ribbon.CLOCK_BASELINE_FRACTION,
                    dp,
                    sizeSp = 15f
            )
        }
        // Ribbon hosts only its cards and metadata; the generic mini-button rail would cover the
        // title and is intentionally not part of this face's composition.
    }

    /**
     * Frame is a self-contained tonal card. Its surface and chip are derived from the same live
     * player swatches the Wear face receives, so the preview never invents a fixed green theme.
     */
    private fun drawFramePlayer(
            canvas: Canvas,
            geometry: PreviewGeometry,
            dp: (Float) -> Float
    ) {
        val cx = geometry.cx
        val radius = geometry.radius
        val screen = radius * 2f
        val screenLeft = cx - radius
        val screenTop = geometry.cy - radius
        drawPlayerShading(canvas, geometry.bounds, cx, geometry.cy, radius)
        val card = RectF(
                screenLeft + screen * FaceGeometry.Frame.CARD_INSET_FRACTION,
                screenTop + screen * FaceGeometry.Frame.CARD_TOP_FRACTION,
                screenLeft + screen * (1f - FaceGeometry.Frame.CARD_INSET_FRACTION),
                screenTop + screen * FaceGeometry.Frame.CARD_BOTTOM_FRACTION
        )
        val cardCorner = min(card.width(), card.height()) * FaceGeometry.Frame.CARD_CORNER_FRACTION
        val contentInset = screen * FaceGeometry.Frame.CONTENT_INSET_FRACTION
        // Match FrameFace: the card is a dark version of the player's resolved primary accent,
        // and its chip is a readable surface version of that same live colour. Neither is teal.
        val cardColor = tunedPreviewColor(albumAccent(), .16f, .42f)
        val chipColor = accentForSurface(albumAccent())
        val chipTextColor = if (AdaptiveTextContrast.prefersDarkText(chipColor)) {
            Color.BLACK
        } else {
            Color.WHITE
        }

        fillPaint.color = cardColor
        canvas.drawRoundRect(card, cardCorner, cardCorner, fillPaint)

        textPaint.style = Paint.Style.FILL
        textPaint.textAlign = Paint.Align.LEFT
        // The chip decides the header row's height; the time centres against whatever it settles
        // on, and falls back to its own line height when there is no chip to measure.
        var frameHeaderRowHeight = 0f
        if (showTrackArtist || !isPlayingShown()) {
            val artist = if (isPlayingShown()) displayArtist()
            else context.getString(R.string.preview_playback_stopped)
            textPaint.typeface = artistTypeface(bold = true)
            textPaint.textSize = artistTypographySpec.scaled(
                    dp(FaceGeometry.Frame.ARTIST_TEXT_SIZE_SP))
            textPaint.letterSpacing = artistTypographySpec.trackingEm
            val chipPadding = dp(FaceGeometry.Frame.ARTIST_HORIZONTAL_PADDING_DP)
            val chipVerticalPadding = dp(FaceGeometry.Frame.ARTIST_VERTICAL_PADDING_DP)
            val maxChipWidth = card.width() - contentInset * 2f
            val sourceDiameter = if (isPlayingShown() && showSourceIcon && sourceGlyph != null) {
                dp(FaceGeometry.Frame.ARTIST_ICON_SIZE_DP) * sourceIconTypographySpec.scale
            } else {
                0f
            }
            val sourceGap = if (sourceDiameter > 0f) sourceDiameter * .33f else 0f
            val label = ellipsize(
                    artist,
                    (maxChipWidth - chipPadding * 2f - sourceDiameter - sourceGap)
                            .coerceAtLeast(1f))
            val chipHeight = maxOf(
                    sourceDiameter + chipVerticalPadding * 2f,
                    textPaint.fontMetrics.descent - textPaint.fontMetrics.ascent +
                            chipVerticalPadding * 2f
            )
            val chipTop = screenTop + screen * FaceGeometry.Frame.ART_TOP_FRACTION
            val chipWidth = (textPaint.measureText(label) + sourceDiameter + sourceGap +
                    chipPadding * 2f)
                    .coerceAtMost(maxChipWidth)
            val chip = RectF(
                    card.left + contentInset,
                    chipTop,
                    card.left + contentInset + chipWidth,
                    chipTop + chipHeight
            )
            fillPaint.shader = null
            fillPaint.color = chipColor
            canvas.drawRoundRect(chip, chipHeight / 2f, chipHeight / 2f, fillPaint)
            drawArtistLine(
                    canvas,
                    label,
                    chip.left + chipPadding,
                    chip.centerY() - (textPaint.fontMetrics.ascent + textPaint.fontMetrics.descent) / 2f,
                    (chip.width() - chipPadding * 2f).coerceAtLeast(1f),
                    chipTextColor,
                    dp(FaceGeometry.Frame.ARTIST_TEXT_SIZE_SP),
                    bold = true,
                    align = Paint.Align.LEFT,
                    sourceGlyph = isPlayingShown(),
                    glyphSize = dp(FaceGeometry.Frame.ARTIST_ICON_SIZE_DP)
            )
            frameHeaderRowHeight = chipHeight
        }
        // Right-aligned on the chip's own row, as FrameHeaderRow lays it out. Drawn even with the
        // artist line hidden - the row still exists, it just has one occupant.
        if (trackTimeVisible()) {
            textPaint.textAlign = Paint.Align.RIGHT
            val rowTop = screenTop + screen * FaceGeometry.Frame.ART_TOP_FRACTION
            val timeSize = dp(FaceGeometry.Frame.TRACK_TIME_TEXT_SIZE_SP)
            textPaint.textSize = trackTimeTypographySpec.scaled(timeSize)
            val metrics = textPaint.fontMetrics
            val rowHeight = frameHeaderRowHeight.takeIf { it > 0f }
                    ?: (metrics.descent - metrics.ascent)
            drawTrackTimeText(
                    canvas,
                    timeText(),
                    card.right - contentInset,
                    rowTop + rowHeight / 2f - (metrics.ascent + metrics.descent) / 2f,
                    timeSize,
                    0x9EFFFFFF.toInt(),
                    artistTypeface(bold = false)
            )
            textPaint.textAlign = Paint.Align.LEFT
        }
        if (showTrackTitle) {
            val titleSize = dp(FaceGeometry.Frame.TITLE_TEXT_SIZE_SP)
            textPaint.typeface = titleTypeface(bold = true)
            textPaint.textSize = titleTypographySpec.scaled(titleSize)
            val titleTop = screenTop + screen * FaceGeometry.Frame.TITLE_TOP_FRACTION
            drawAdaptiveTitle(
                    canvas,
                    card.left + contentInset,
                    titleTop - textPaint.fontMetrics.ascent,
                    card.width() - contentInset * 2f,
                    titleSize,
                    titleAlpha(Color.WHITE),
                    bold = true,
                    minSize = dp(FaceGeometry.Frame.TITLE_MIN_TEXT_SIZE_SP),
                    align = Paint.Align.LEFT,
                    maxLines = FaceGeometry.Frame.TITLE_MAX_LINES
            )
        }

        val artwork = RectF(
                card.left + contentInset,
                screenTop + screen * FaceGeometry.Frame.ARTWORK_TOP_FRACTION,
                card.right - contentInset,
                screenTop + screen * FaceGeometry.Frame.ARTWORK_BOTTOM_FRACTION
        )
        val artworkCorner = screen * FaceGeometry.Frame.artworkCornerFraction()
        drawClippedFaceArtwork(
                canvas,
                displayedArt(),
                artwork,
                artworkCorner,
                fallbackColor = tunedPreviewColor(albumAccent(), .30f, .56f)
        )
        // FrameProgress: inside the cover's clip, so it takes the same rounded corners.
        if (internalProgressVisible) {
            val barHeight = dp(FaceGeometry.Frame.PROGRESS_THICKNESS_DP)
            canvas.save()
            canvas.clipPath(Path().apply {
                addRoundRect(artwork, artworkCorner, artworkCorner, Path.Direction.CW)
            })
            fillPaint.shader = null
            fillPaint.color = 0x6B000000
            canvas.drawRect(
                    artwork.left, artwork.bottom - barHeight, artwork.right, artwork.bottom,
                    fillPaint)
            val filled = artwork.width() * progressFraction()
            if (filled > 0f) {
                fillPaint.color = resolveTint(progressMode, progressCustom, progressDesaturated)
                canvas.drawRect(
                        artwork.left, artwork.bottom - barHeight, artwork.left + filled,
                        artwork.bottom, fillPaint)
            }
            canvas.restore()
        }

        if (alwaysShowTime || clockPreviewForced()) {
            drawFaceClock(canvas, cx, screenTop, dp)
        }
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.letterSpacing = 0f
        // The card uses every available lower pixel for its wide cover. Generic bottom chrome is
        // deliberately absent, matching the watch face rather than floating buttons over the art.
    }

    /**
     * Miniature of the Chat face: the day chip, one received title/artist pair and the current
     * track's voice bubble.
     *
     * Geometry mirrors `ChatFace.kt` - same side padding, same bubble corners, same waveform bar
     * count - because `mobile` cannot depend on `wear` and the two are kept in step by hand (the
     * standing rule for every face miniature here). The thread shows exactly one history pair
     * regardless of what has played: the preview has one track to work with, and the point of the
     * miniature is the composition, not the session's real contents.
     */
    private fun drawChatPlayer(
            canvas: Canvas,
            geometry: PreviewGeometry,
            dp: (Float) -> Float
    ) {
        val cx = geometry.cx
        val cy = geometry.cy
        val radius = geometry.radius
        val screen = radius * 2f
        val screenTop = cy - radius

        drawPlayerShading(canvas, geometry.bounds, cx, cy, radius)

        val accent = albumAccent()
        val chatPlaying = isPlayingShown()
        // Waveform's filled bars use the independently resolved progress tint, not the face's
        // album accent. That matters whenever the user chose a custom or desaturated progress
        // treatment: ChatFace receives state.progressColor just like every other progress surface.
        val chatProgressColor = resolveTint(progressMode, progressCustom, progressDesaturated)
        val incoming = tonal(accent, CHAT_INCOMING_LIGHTNESS, .25f, .70f)
        val outgoing = tonal(accent, CHAT_OUTGOING_LIGHTNESS, .35f, .70f)
        val sidePad = screen * CHAT_SIDE_PADDING_FRACTION
        val left = cx - radius + sidePad
        val right = cx + radius - sidePad

        fillPaint.shader = null
        textPaint.style = Paint.Style.FILL

        // Bottom-anchored, exactly as the face lays out: the action row sits at the bottom, the
        // voice bubble above it, and history drifts up above that.
        // One circle per configured mini button - this face hosts the row inside its own
        // composition rather than letting the shared row float over the thread - and its own
        // queue + skip pair when nothing is configured. Mirrors ChatActionRow, including the
        // fitting: the designed diameter is kept while it fits and only shrinks for a third
        // circle. It has to be resolved here, before the bubbles, because the thread is laid out
        // against the space the row leaves.
        val chatButtons = activeMiniButtonIcons()
        val chatActionCount = if (chatButtons.isEmpty()) 2 else chatButtons.size
        val actionGap = screen * CHAT_ACTION_GAP_FRACTION
        val actionDiameter = minOf(
                (screen * CHAT_ACTION_DIAMETER_FRACTION).coerceIn(
                        dp(CHAT_ACTION_MIN_DESIGNED_DIAMETER_DP),
                        dp(CHAT_ACTION_MAX_DESIGNED_DIAMETER_DP)),
                (screen * (1f - CHAT_SIDE_PADDING_FRACTION * 2f) -
                        actionGap * (chatActionCount - 1)) / chatActionCount
        ).coerceAtLeast(dp(CHAT_ACTION_MIN_DIAMETER_DP))
        val actionCy = cy + radius - screen * CHAT_BOTTOM_PADDING_FRACTION - actionDiameter / 2f
        val chatTimeVisible = trackTimeVisible()
        val chatTimeTypeface = trackTimeTypeface(artistTypeface(bold = false))
        textPaint.typeface = chatTimeTypeface
        textPaint.textSize = trackTimeTypographySpec.scaled(dp(CHAT_TIME_SP))
        val chatTimeLineHeight = textPaint.fontMetrics.descent - textPaint.fontMetrics.ascent
        // VoiceBubble is a Column: the delivered time/ticks sit *after* its 58dp bubble and only
        // then does the 5dp spacer lead into ChatActionRow. Reserving this row before anchoring the
        // bubble prevents the preview's timestamp from landing underneath the buttons.
        val chatTimeRowHeight = maxOf(chatTimeLineHeight, dp(CHAT_TICK_HEIGHT_DP))
        val chatTimestampHeight = if (chatTimeVisible) {
            dp(CHAT_TIME_TOP_PADDING_DP) + chatTimeRowHeight
        } else {
            0f
        }
        val bubbleHeight = dp(CHAT_VOICE_HEIGHT_DP)
        val bubbleBottom = actionCy - actionDiameter / 2f -
                dp(CHAT_VOICE_TO_ACTION_GAP_DP) - chatTimestampHeight
        val bubbleTop = bubbleBottom - bubbleHeight
        // Full width and right-aligned, as the reference has it.
        val bubbleLeft = left

        fun bubble(l: Float, t: Float, r: Float, b: Float, color: Int, tail: Boolean) {
            val rect = RectF(l, t, r, b)
            val corner = dp(CHAT_BUBBLE_CORNER_DP)
            val tailCorner = dp(CHAT_BUBBLE_TAIL_CORNER_DP)
            val path = Path().apply {
                // Compose's RoundedCornerShape uses one genuinely smaller sender corner. The old
                // preview filled an extra square over an all-large round-rect, which did not have
                // the same curve and made the two bubbles visibly disagree at this scale.
                addRoundRect(
                        rect,
                        if (tail) {
                            floatArrayOf(
                                    tailCorner, tailCorner,
                                    corner, corner,
                                    corner, corner,
                                    corner, corner)
                        } else {
                            floatArrayOf(
                                    corner, corner,
                                    tailCorner, tailCorner,
                                    corner, corner,
                                    corner, corner)
                        },
                        Path.Direction.CW)
            }
            fillPaint.shader = null
            fillPaint.color = color
            canvas.drawPath(path, fillPaint)
        }

        // --- the current track, as one outgoing bubble (ChatFace.CurrentMessageBubble) ---
        // This used to be a pair of *incoming* history bubbles carrying the same track. The face
        // dropped them: on a 192dp screen every past bubble came straight out of the two elements
        // that matter, and looking further back is what the queue button below is for. What
        // replaced them names the current track instead - the voice bubble under it shows a cover
        // and a waveform but says nothing - sent as an outgoing message like that note, same
        // sender, two messages. The preview kept drawing the removed composition.
        val namesTitle = showTrackTitle
        val namesArtist = showTrackArtist || !chatPlaying
        // CurrentMessageBubble returns no layout when both metadata switches are off, but the
        // following Spacer remains in ChatFace's Column. Start above that unconditional spacer so
        // the day chip does not move four dp down only in the preview's metadata-hidden state.
        var currentTop = bubbleTop - dp(CHAT_CURRENT_TO_VOICE_GAP_DP)
        if (namesTitle || namesArtist) {
            val bubblePadH = dp(CHAT_BUBBLE_HORIZONTAL_PADDING_DP)
            val bubblePadV = dp(CHAT_BUBBLE_VERTICAL_PADDING_DP)
            val titleSize = titleTypographySpec.scaled(dp(CHAT_TITLE_SP))
            val artistSize = artistTypographySpec.scaled(dp(CHAT_ARTIST_SP))
            val artistLabel = if (chatPlaying) {
                displayArtist()
            } else {
                context.getString(R.string.preview_playback_stopped)
            }
            // ChatBubble is widthIn(max = 210.dp) inside the column's side padding. Its content
            // width is therefore the smaller of the actual row and that cap, less its horizontal
            // padding. The old .74f cap was narrower than the wrist on normal screens while the
            // uncapped follow-up let the preview grow wider than the wrist on large ones.
            val maxContent = (minOf(right - left, dp(CHAT_CURRENT_BUBBLE_MAX_WIDTH_DP)) -
                    bubblePadH * 2f).coerceAtLeast(1f)

            textPaint.typeface = titleTypeface(bold = true)
            textPaint.textSize = titleSize
            val titleText = ellipsize(displayTitle(), maxContent)
            val titleW = if (namesTitle) textPaint.measureText(titleText) else 0f
            val titleLineH = textPaint.fontMetrics.let { it.descent - it.ascent }

            textPaint.typeface = artistTypeface(bold = false)
            textPaint.textSize = artistSize
            val artistText = ellipsize(artistLabel, maxContent)
            val artistW = if (namesArtist) textPaint.measureText(artistText) else 0f
            val artistLineH = textPaint.fontMetrics.let { it.descent - it.ascent }

            // Wraps its content rather than spanning the row: a bubble sized to the text is what
            // makes the thread read as messages instead of as stacked bars.
            val contentW = maxOf(titleW, artistW)
            val contentH = (if (namesTitle) titleLineH else 0f) +
                    (if (namesArtist) artistLineH else 0f)
            val bubbleW = contentW + bubblePadH * 2f
            val bubbleH = contentH + bubblePadV * 2f
            val currentBottom = bubbleTop - dp(CHAT_CURRENT_TO_VOICE_GAP_DP)
            currentTop = currentBottom - bubbleH
            bubble(right - bubbleW, currentTop, right, currentBottom, outgoing, tail = false)

            var lineY = currentTop + bubblePadV
            val textLeft = right - bubbleW + bubblePadH
            if (namesTitle) {
                textPaint.typeface = titleTypeface(bold = true)
                textPaint.textSize = titleSize
                textPaint.textAlign = Paint.Align.LEFT
                textPaint.color = titleAlpha(Color.WHITE)
                canvas.drawText(titleText, textLeft, lineY - textPaint.fontMetrics.ascent, textPaint)
                lineY += titleLineH
            }
            if (namesArtist) {
                // The face designs this line at .70; drawArtistLine folds the user's own opacity
                // in on top, exactly as ArtistLineText multiplies its own.
                drawArtistLine(
                        canvas, artistText, textLeft,
                        lineY - artistLineH * .22f, maxContent,
                        ColorUtils.setAlphaComponent(artistTextColor(), 0xB3),
                        dp(CHAT_ARTIST_SP), align = Paint.Align.LEFT)
            }
        }

        // --- day chip, above the thread ---
        textPaint.typeface = watchUiTypeface(bold = false)
        textPaint.textSize = dp(CHAT_DAY_TEXT_SP)
        val chipLabel = context.getString(R.string.preview_chat_today)
        val chipW = textPaint.measureText(chipLabel) + dp(CHAT_DAY_HORIZONTAL_PADDING_DP * 2f)
        val chipH = (textPaint.fontMetrics.descent - textPaint.fontMetrics.ascent) +
                dp(CHAT_DAY_VERTICAL_PADDING_DP * 2f)
        val chipTop = currentTop - dp(CHAT_DAY_TO_MESSAGE_GAP_DP) - chipH
        if (chipTop >= screenTop + screen * CHAT_TOP_PADDING_FRACTION) {
            fillPaint.shader = null
            fillPaint.color = ColorUtils.setAlphaComponent(incoming, 0xD9)
            val chipRect = RectF(cx - chipW / 2f, chipTop, cx + chipW / 2f, chipTop + chipH)
            canvas.drawRoundRect(chipRect, chipH / 2f, chipH / 2f, fillPaint)
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.color = ColorUtils.setAlphaComponent(Color.WHITE, 0xBF)
            canvas.drawText(
                    chipLabel,
                    cx, chipTop + dp(CHAT_DAY_VERTICAL_PADDING_DP) - textPaint.fontMetrics.ascent,
                    textPaint)
        }

        // --- voice bubble ---
        bubble(bubbleLeft, bubbleTop, right, bubbleBottom, outgoing, tail = false)

        // Avatar: the cover, or a flat accent disc before any art exists.
        val avatarSize = dp(CHAT_AVATAR_SIZE_DP)
        val avatarRect = RectF(
                bubbleLeft + dp(CHAT_VOICE_HORIZONTAL_PADDING_DP),
                (bubbleTop + bubbleBottom) / 2f - avatarSize / 2f,
                bubbleLeft + dp(CHAT_VOICE_HORIZONTAL_PADDING_DP) + avatarSize,
                (bubbleTop + bubbleBottom) / 2f + avatarSize / 2f)
        val art = displayedArt()
        canvas.save()
        canvas.clipPath(Path().apply { addOval(avatarRect, Path.Direction.CW) })
        if (art != null) {
            drawArtwork(canvas, art, avatarRect, 255)
        } else {
            fillPaint.color = ColorUtils.setAlphaComponent(accent, 0x8C)
            canvas.drawRect(avatarRect, fillPaint)
        }
        canvas.restore()

        // Play/pause disc at the trailing edge.
        val glyphSize = dp(CHAT_GLYPH_SIZE_DP)
        val glyphCx = right - dp(CHAT_VOICE_HORIZONTAL_PADDING_DP) - glyphSize / 2f
        val glyphCy = (bubbleTop + bubbleBottom) / 2f
        fillPaint.color = ColorUtils.setAlphaComponent(Color.BLACK, 0x73)
        canvas.drawCircle(glyphCx, glyphCy, glyphSize / 2f, fillPaint)
        if (playerControlsVisible) {
            fillPaint.color = Color.WHITE
            val g = dp(CHAT_GLYPH_MARK_DP)
            if (chatPlaying) {
                val bar = g * .3f
                canvas.drawRect(
                        RectF(glyphCx - g / 2f, glyphCy - g / 2f, glyphCx - g / 2f + bar, glyphCy + g / 2f),
                        fillPaint)
                canvas.drawRect(
                        RectF(glyphCx + g / 2f - bar, glyphCy - g / 2f, glyphCx + g / 2f, glyphCy + g / 2f),
                        fillPaint)
            } else {
                canvas.drawPath(Path().apply {
                    moveTo(glyphCx - g / 2f, glyphCy - g / 2f)
                    lineTo(glyphCx + g / 2f, glyphCy)
                    lineTo(glyphCx - g / 2f, glyphCy + g / 2f)
                    close()
                }, fillPaint)
            }
        }

        // Waveform between the two, filled up to the current position.
        val waveLeft = avatarRect.right + dp(CHAT_AVATAR_TO_WAVE_GAP_DP)
        val waveRight = glyphCx - glyphSize / 2f - dp(CHAT_WAVE_TO_GLYPH_GAP_DP)
        if (waveRight > waveLeft) {
            val waveH = dp(CHAT_WAVE_HEIGHT_DP)
            // Centred outright: the time moved out from under it, so nothing offsets it now.
            val waveCy = glyphCy
            val slot = (waveRight - waveLeft) / CHAT_WAVE_PATTERN.size
            val barW = (slot * .45f).coerceAtLeast(1f)
            val played = (progressFraction().coerceIn(0f, 1f) * CHAT_WAVE_PATTERN.size).toInt()
            // Match Waveform's 620ms tween/reverse. The Canvas preview must explicitly schedule
            // frames; Compose does that for its infinite transition. Moderation output remains
            // deterministic and therefore does not keep invalidating itself.
            val waveformPulse = if (chatPlaying) {
                if (!moderationPreviewMode) transientAnimationActive = true
                val halfCycle = CHAT_WAVE_PLAYHEAD_PULSE_HALF_CYCLE_MS.toLong()
                val cycle = previewAnimationTimeMillis() % (halfCycle * 2L)
                val rise = if (cycle <= halfCycle) {
                    cycle.toFloat() / halfCycle.toFloat()
                } else {
                    (halfCycle * 2L - cycle).toFloat() / halfCycle.toFloat()
                }
                CHAT_WAVE_PLAYHEAD_PULSE_MIN_SCALE +
                        (1f - CHAT_WAVE_PLAYHEAD_PULSE_MIN_SCALE) * rise
            } else {
                1f
            }
            CHAT_WAVE_PATTERN.forEachIndexed { index, fraction ->
                val scale = if (chatPlaying && index == played) waveformPulse else 1f
                val h = (waveH * fraction * scale).coerceAtLeast(dp(2f))
                fillPaint.color = if (index < played) {
                    chatProgressColor
                } else {
                    ColorUtils.setAlphaComponent(Color.WHITE, 0x59)
                }
                val barLeft = waveLeft + index * slot + (slot - barW) / 2f
                canvas.drawRoundRect(
                        RectF(barLeft, waveCy - h / 2f, barLeft + barW, waveCy + h / 2f),
                        barW / 2f, barW / 2f, fillPaint)
            }
        }
        // Below the bubble and right-aligned, matching the face: the timestamp belongs to the
        // message, not to its contents.
        if (chatTimeVisible) {
            textPaint.typeface = chatTimeTypeface
            textPaint.textSize = trackTimeTypographySpec.scaled(dp(CHAT_TIME_SP))
            val timeTop = bubbleBottom + dp(CHAT_TIME_TOP_PADDING_DP)
            val tickEnd = right - dp(CHAT_TIME_END_PADDING_DP)
            val tickWidth = dp(CHAT_TICK_WIDTH_DP)
            val timeRight = tickEnd - tickWidth - dp(CHAT_TIME_TO_TICKS_GAP_DP)
            val textTop = timeTop + (chatTimeRowHeight - chatTimeLineHeight) / 2f
            // VoiceBubble's enclosing Column aligns this wrap-content Row to the end, so the
            // timestamp's right edge sits immediately before the gap and receipt ticks. The day
            // chip above left Paint in centre alignment; set this explicitly rather than inherit
            // it or the preview shifts the time into the ticks.
            val previousTimeAlign = textPaint.textAlign
            textPaint.textAlign = Paint.Align.RIGHT
            drawTrackTimeText(
                    canvas,
                    chatElapsedTimeText(),
                    timeRight,
                    textTop - textPaint.fontMetrics.ascent,
                    dp(CHAT_TIME_SP),
                    ColorUtils.setAlphaComponent(Color.WHITE, 0x99),
                    chatTimeTypeface)
            textPaint.textAlign = previousTimeAlign
            drawChatDoubleTick(
                    canvas,
                    tickEnd - tickWidth / 2f,
                    timeTop + chatTimeRowHeight / 2f,
                    tickWidth,
                    dp(CHAT_TICK_HEIGHT_DP),
                    if (chatPlaying) accent else ColorUtils.setAlphaComponent(Color.WHITE, 0x59))
        }

        // --- the round actions ---
        val chatRowWidth = actionDiameter * chatActionCount + actionGap * (chatActionCount - 1)
        val chatRowLeft = cx - chatRowWidth / 2f
        fun chatActionCx(index: Int) =
                chatRowLeft + actionDiameter / 2f + index * (actionDiameter + actionGap)

        if (chatButtons.isNotEmpty()) {
            drawChatMiniButtons(canvas, chatButtons, ::chatActionCx, actionCy, actionDiameter,
                    accent, dp)
            if (alwaysShowTime || clockPreviewForced()) {
                drawFaceClock(canvas, cx, cy - radius, dp)
            }
            return
        }

        val leftActionCx = chatActionCx(0)
        val rightActionCx = chatActionCx(1)
        fillPaint.color = accent
        canvas.drawCircle(leftActionCx, actionCy, actionDiameter / 2f, fillPaint)
        canvas.drawCircle(rightActionCx, actionCy, actionDiameter / 2f, fillPaint)
        val onAccent = if (AdaptiveTextContrast.prefersDarkText(accent)) {
            ColorUtils.setAlphaComponent(Color.BLACK, 0xD1)
        } else {
            Color.WHITE
        }
        val actionGlyphSize = actionDiameter * FaceGeometry.Chat.ACTION_GLYPH_FRACTION
        // The same vector assets as ChatFace. The old hand-plotted line/triangle approximation
        // was visibly off-centre and had a different queue glyph from the one on the watch.
        drawIcon(
                canvas,
                commonR.drawable.ic_face_queue_music,
                leftActionCx,
                actionCy,
                actionGlyphSize,
                onAccent)
        drawIcon(
                canvas,
                commonR.drawable.ic_face_skip_next,
                rightActionCx,
                actionCy,
                actionGlyphSize,
                onAccent)

        if (alwaysShowTime || clockPreviewForced()) {
            drawFaceClock(canvas, cx, cy - radius, dp)
        }
        // Deliberately no drawBottomChrome: this face hosts the mini-button row inside its own
        // composition (see MiniButtonPlacement.isHostedByFace) and falls back to its own two
        // actions when none are configured. Drawing the shared row here would preview something
        // the watch will not render.
    }

    /** Canvas equivalent of ChatFace.DoubleTick. Kept as geometry rather than a text glyph: the
     * delivered receipt must retain its two-stroke shape in every selected watch font. */
    private fun drawChatDoubleTick(
            canvas: Canvas,
            centerX: Float,
            centerY: Float,
            width: Float,
            height: Float,
            color: Int
    ) {
        val left = centerX - width / 2f
        val top = centerY - height / 2f
        strokePaint.shader = null
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeCap = Paint.Cap.BUTT
        strokePaint.strokeWidth = height * .18f
        strokePaint.color = color
        fun tick(offsetX: Float) {
            canvas.drawPath(Path().apply {
                moveTo(left + offsetX, top + height * .55f)
                lineTo(left + offsetX + width * .18f, top + height * .85f)
                lineTo(left + offsetX + width * .52f, top + height * .18f)
            }, strokePaint)
        }
        tick(0f)
        tick(width * .33f)
    }

    /**
     * Miniature of the Note face: a small cover disc over one centred `Artist: Title` line, with
     * the track time at the foot.
     *
     * Mirrors `NoteFace.kt` by hand (`mobile` cannot depend on `wear`), including the title-colour
     * decision - both sides ask [AdaptiveTextContrast.prefersDarkText] about the same proxy, so the
     * preview cannot promise white text where the watch draws black.
     */
    /**
     * Verse: three lyric lines over the same black-and-accent floor the lyrics screen uses.
     *
     * The preview has no lyrics to show - they are fetched by the phone for whatever is playing on
     * it, which is not necessarily anything - so the three rows name themselves instead of faking a
     * song. That reads better than placeholder verse anyway: the whole point a user needs to
     * understand before choosing this face is which of the three lines is the one being sung.
     *
     * The floor wash and the accent lift are duplicated from the watch rather than shared, for the
     * usual reason the miniatures are: `mobile` cannot depend on `wear`, and Canvas has no Compose
     * Brush. Keep the four glow constants in step with `accentFloorGlow`.
     */
    private fun drawVersePlayer(
            canvas: Canvas,
            geometry: PreviewGeometry,
            dp: (Float) -> Float
    ) {
        val cx = geometry.cx
        val cy = geometry.cy
        val radius = geometry.radius
        val screen = radius * 2f

        drawPlayerShading(canvas, geometry.bounds, cx, cy, radius)

        // Lifted exactly as VerseFace does: a near-black cover would otherwise render the current
        // line - the only thing marking where the song is - invisible on this face's own backdrop.
        val accent = AdaptiveTextContrast.adapt(albumAccent(), 0f)

        fillPaint.shader = null
        textPaint.style = Paint.Style.FILL
        textPaint.textAlign = Paint.Align.CENTER

        // Running head: artist letterspaced above the title, both small.
        var y = cy - radius * .58f
        if (showTrackArtist || !isPlayingShown()) {
            val artistText = if (isPlayingShown()) {
                displayArtist()
            } else {
                context.getString(R.string.preview_playback_stopped)
            }
            // VerseFace tracks its running head at .18em; that is the face's own design and only
            // gives way when the user moves the artist tracking off zero.
            // VerseFace designs this line at .78 alpha; the preview drew it opaque.
            drawArtistLine(
                    canvas, artistText.uppercase(), cx, y, screen * .74f,
                    ColorUtils.setAlphaComponent(artistTextColor(), 0xC7),
                    dp(8f), designedTracking = 0.18f)
        }
        if (showTrackTitle) {
            y += dp(13f)
            // VerseFace routes this through AdaptiveTitleText, and designs the line at .92 alpha -
            // titleAlpha preserves an incoming alpha exactly as FaceChrome.titleTextColor does.
            drawAdaptiveTitle(
                    canvas,
                    cx,
                    y,
                    screen * .74f,
                    dp(11f),
                    titleAlpha(ColorUtils.setAlphaComponent(Color.WHITE, 0xEB)),
                    bold = false)
        }

        // The three rows. The middle one carries the accent, a larger size and the hairline; the
        // outer two are the same type dimmed, so the reel reads as one continuous thing.
        val inset = RoundScreenText.sideInsetFor(VERSE_BAND_TOP, VERSE_BAND_BOTTOM)
        val available = screen * (1f - 2f * inset)

        // The words themselves, in the lyrics typeface rather than the track one - see
        // lyricTypeface. letterSpacing is reset because titleTypeface above set the title's.
        // The block sits at the band's centre, not the screen's - see VERSE_BAND_CENTER. Applied as
        // one offset to every row and to the hairline so the miniature keeps showing where the
        // words actually land on the wrist.
        val bandShift = screen * (VERSE_BAND_CENTER - 0.5f)

        textPaint.typeface = lyricTypeface()
        textPaint.letterSpacing = 0f
        textPaint.textSize = dp(8.5f)
        textPaint.color = ColorUtils.setAlphaComponent(Color.WHITE, 0x61)
        canvas.drawText(
                ellipsize(context.getString(R.string.preview_verse_previous), available),
                cx, cy - dp(11f) + bandShift, textPaint)

        textPaint.textSize = dp(12f)
        textPaint.color = accent
        canvas.drawText(
                ellipsize(context.getString(R.string.preview_verse_current), available),
                cx, cy + dp(5f) + bandShift, textPaint)

        // Hairline: how far through the *current line* playback is, not through the track. The
        // face's own progress indicator, which is why the edge arc defaults off here.
        if (internalProgressVisible) {
            val ruleWidth = screen * .42f
            val ruleY = cy + dp(11f) + bandShift
            val ruleHeight = dp(1f)
            fillPaint.shader = null
            fillPaint.color = ColorUtils.setAlphaComponent(accent, 0x29)
            canvas.drawRect(
                    cx - ruleWidth / 2f, ruleY,
                    cx + ruleWidth / 2f, ruleY + ruleHeight, fillPaint)
            fillPaint.color = ColorUtils.setAlphaComponent(accent, 0xD9)
            canvas.drawRect(
                    cx - ruleWidth / 2f, ruleY,
                    cx - ruleWidth / 2f + ruleWidth * VERSE_PREVIEW_LINE_PROGRESS,
                    ruleY + ruleHeight,
                    fillPaint)
        }

        textPaint.textSize = dp(8.5f)
        textPaint.color = ColorUtils.setAlphaComponent(Color.WHITE, 0x61)
        canvas.drawText(
                ellipsize(context.getString(R.string.preview_verse_next), available),
                cx, cy + dp(26f) + bandShift, textPaint)

        if (trackTimeVisible()) {
            // Back to the track font: the elapsed time is chrome, and the watch draws it through
            // ArtistLineText rather than with the lyric lines. Set explicitly rather than left to
            // inherit, since the three rows above have just changed the shared paint's typeface.
            textPaint.typeface = artistTypeface(bold = false)
            textPaint.textSize = dp(9f)
            textPaint.color = ColorUtils.setAlphaComponent(Color.WHITE, 0x9E)
            drawTrackTimeText(
                    canvas, timeText(), cx, cy + radius * .70f, dp(9f),
                    ColorUtils.setAlphaComponent(Color.WHITE, 0x9E), textPaint.typeface)
        }
    }

    /**
     * Canvas twin of the watch's `accentFloorGlow`.
     *
     * The shape comes from [AccentFloorStyle], the one place the watch reads it from too, so the
     * two cannot drift. Only the drawing is duplicated, because `mobile` cannot depend on `wear`
     * and Canvas has no Compose Brush.
     */
    private fun drawAccentFloor(canvas: Canvas, geometry: PreviewGeometry, accent: Int) {
        val cx = geometry.cx
        val cy = geometry.cy
        val radius = geometry.radius

        val saved = canvas.saveLayer(geometry.bounds, null)

        fillPaint.xfermode = null
        fillPaint.shader = RadialGradient(
                cx, cy, radius,
                intArrayOf(Color.TRANSPARENT,
                        ColorUtils.setAlphaComponent(accent, (accentFloor.maxAlpha * 255).toInt())),
                floatArrayOf(accentFloor.innerStop, 1f),
                Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, radius, fillPaint)

        // The mask fractions are of screen height; the screen spans cy-radius..cy+radius.
        fillPaint.shader = LinearGradient(
                0f, cy - radius + radius * 2f * accentFloor.maskStart,
                0f, cy - radius + radius * 2f * AccentFloorStyle.MASK_END,
                Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
        fillPaint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
        canvas.drawRect(geometry.bounds, fillPaint)

        fillPaint.xfermode = null
        fillPaint.shader = null
        canvas.restoreToCount(saved)
    }

    /**
     * Metadata: a small cover and identity over a table of the track's own details.
     *
     * Drawn with the *live* track's real values where the preview has them and stand-ins where it
     * does not, which is the same compromise drawVersePlayer makes: the phone has no way to run the
     * watch's request/answer round trip from inside a Canvas, and an empty table would tell the user
     * nothing about what the face looks like. The point of the miniature is the arrangement - how
     * many rows fit, how the labels sit against the values - and that is exactly what it shows.
     */
    private fun drawMetadataPlayer(
            canvas: Canvas,
            geometry: PreviewGeometry,
            dp: (Float) -> Float
    ) {
        val cx = geometry.cx
        val cy = geometry.cy
        val radius = geometry.radius
        val screen = radius * 2f

        drawPlayerShading(canvas, geometry.bounds, cx, cy, radius)

        fillPaint.shader = null
        textPaint.style = Paint.Style.FILL

        var y = cy - radius * .52f

        // Cover, small on purpose: this is the one face where the artwork is the caption and the
        // table is the subject.
        val art = displayedArt()
        if (art != null) {
            val side = screen * .17f
            val rect = RectF(cx - side / 2f, y, cx + side / 2f, y + side)
            canvas.save()
            canvas.clipPath(Path().apply {
                addRoundRect(rect, side * .22f, side * .22f, Path.Direction.CW)
            })
            drawArtwork(canvas, art, rect, 255)
            canvas.restore()
            y += side + dp(4f)
        }

        textPaint.textAlign = Paint.Align.CENTER
        if (showTrackTitle) {
            textPaint.typeface = titleTypeface(bold = false)
            textPaint.textSize = titleTypographySpec.scaled(dp(10f))
            textPaint.color = titleAlpha(Color.WHITE)
            y += dp(9f)
            canvas.drawText(ellipsize(displayTitle(), screen * .70f), cx, y, textPaint)
        }
        if (showTrackArtist) {
            y += dp(9f)
            drawArtistLine(
                    canvas, displayArtist(), cx, y, screen * .70f, artistTextColor(), dp(8f))
        }

        // The table. Label right-aligned against a fixed column, value left of it - the same split
        // MetadataFace draws, so the miniature shows where a long value will be cut.
        val labelRight = cx - dp(3f)
        val valueLeft = cx + dp(3f)
        y += dp(9f)
        // Spent per *line*, not per row, and a row that does not fit is skipped rather than
        // ending the table - the same budget MetadataFace applies, so the miniature shows which
        // rows survive rather than an arbitrary first five. The budget mirrors the watch's
        // TABLE_HEIGHT_FRACTION / ROW_HEIGHT with its 4..12 clamp.
        var remaining = ((screen * METADATA_TABLE_HEIGHT_FRACTION) / dp(METADATA_ROW_HEIGHT_DP))
                .toInt().coerceIn(METADATA_MIN_ROWS, METADATA_MAX_ROWS)
        val lineHeight = dp(9.5f)
        for (row in previewMetadataRows()) {
            if (row.lines > remaining) continue
            remaining -= row.lines
            if (row.wide) {
                // No label column: the value runs the full width and wraps, which is the whole
                // reason this row is shaped differently on the watch.
                textPaint.textSize = dp(6.5f)
                textPaint.color = ColorUtils.setAlphaComponent(Color.WHITE, 0xEB)
                textPaint.textAlign = Paint.Align.CENTER
                val wideWidth = screen * .78f
                splitLines(row.value, wideWidth, row.lines).forEachIndexed { index, line ->
                    canvas.drawText(ellipsize(line, wideWidth), cx, y + index * lineHeight, textPaint)
                }
                y += lineHeight * row.lines
                continue
            }
            textPaint.textSize = dp(6.5f)
            textPaint.color = ColorUtils.setAlphaComponent(Color.WHITE, 0x85)
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(row.label, labelRight, y, textPaint)

            textPaint.textSize = dp(8f)
            textPaint.color = ColorUtils.setAlphaComponent(Color.WHITE, 0xEB)
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(ellipsize(row.value, screen * .34f), valueLeft, y, textPaint)
            y += lineHeight
        }
        textPaint.textAlign = Paint.Align.CENTER
    }

    /** A stand-in row, carrying what the watch's `MetadataEntry` carries: a label, a value, and
     *  what the row costs against the screen's line budget. [wide] drops the label column. */
    private data class PreviewMetadataRow(
            val label: String,
            val value: String,
            val lines: Int = 1,
            val wide: Boolean = false
    )

    /**
     * The rows the miniature stands in with, honouring all six group switches so turning any block
     * off is visible here rather than only on the wrist.
     *
     * Credits and Identifiers used to have no sample rows at all, which made their two switches the
     * only ones on the page that changed nothing - on the one face whose entire argument is how
     * much detail fits. Identifiers is off by default, so its row appears only once it is turned
     * on, exactly as on the watch.
     */
    private fun previewMetadataRows(): List<PreviewMetadataRow> {
        val rows = mutableListOf<PreviewMetadataRow>()
        fun group(group: TrackMetadataFields.Group): Boolean {
            val definition = MiscPreferences.metadataGroupPreference(group)
            return readBoolean(definition.key, definition.defaultValue)
        }
        fun add(label: Int, value: String, lines: Int = 1, wide: Boolean = false) {
            rows.add(PreviewMetadataRow(resources.getString(label), value, lines, wide))
        }
        if (group(TrackMetadataFields.Group.CORE)) {
            add(R.string.preview_metadata_album,
                    resources.getString(R.string.preview_metadata_album_value))
            add(R.string.preview_metadata_track, "7 / 12")
        }
        if (group(TrackMetadataFields.Group.RELEASE)) {
            add(R.string.preview_metadata_genre,
                    resources.getString(R.string.preview_metadata_genre_value))
            add(R.string.preview_metadata_year, "2019")
        }
        if (group(TrackMetadataFields.Group.CREDITS)) {
            // The already-translated generic artist sample rather than a new string: on a real
            // track the composer is very often the performer, and a name needs no invention.
            add(R.string.preview_metadata_composer,
                    resources.getString(R.string.preview_sample_artist))
        }
        if (group(TrackMetadataFields.Group.IDENTIFIERS)) {
            add(R.string.preview_metadata_isrc, "GBAYE0601498")
        }
        if (group(TrackMetadataFields.Group.PLAYBACK)) {
            // A frozen sample rather than a running counter: the miniature redraws on preference
            // changes, not on a ticker, and animating it here would be a second clock to keep in
            // step with the wrist's for no gain. The point of the row in a preview is that it is
            // there and how much width it wants.
            add(R.string.preview_metadata_position, "1:23.456 / 3:45.678")
            add(R.string.preview_metadata_output,
                    resources.getString(R.string.preview_metadata_output_value))
            // The full address, three lines of the whole width and no label column - the one value
            // no 90dp column holds. It is what makes the line budget visible: this row alone
            // displaces three others, which is the trade the Metadata face is asking you to make.
            add(R.string.preview_metadata_url,
                    "https://cdn.example.com/audio/8f3a91c2/track.m4a?token=b7e4", lines = 3,
                    wide = true)
        }
        if (group(TrackMetadataFields.Group.TECHNICAL)) {
            add(R.string.preview_metadata_format, "FLAC")
            add(R.string.preview_metadata_bitrate, "1041 kbps")
        }
        return rows
    }

    private fun drawNotePlayer(
            canvas: Canvas,
            geometry: PreviewGeometry,
            dp: (Float) -> Float
    ) {
        val cx = geometry.cx
        val cy = geometry.cy
        val radius = geometry.radius
        val screen = radius * 2f

        drawPlayerShading(canvas, geometry.bounds, cx, cy, radius)

        val accent = albumAccent()
        val backdropHidesArt = PlayerBackgroundStyle.fromPreference(artStyle).hidesArtwork
        val backdropIsDark = backdropHidesArt || !AdaptiveTextContrast.prefersDarkText(accent)
        // Mirrors NoteFace.noteTitleColor: on a dark backdrop the title takes the palette's
        // tertiary slot so a second colour is visible on a face that paints one line, unless that
        // slot is too close in hue to the artist's colour to read as a second colour. Through
        // accentForText for the same reason the watch does it - Expressive and Desaturated hand
        // over the cover's raw swatches, which on a dark album are near-black.
        val titleColor = when {
            !backdropIsDark -> ColorUtils.setAlphaComponent(Color.BLACK, 0xDE)
            else -> globalTriad().tertiary.takeIf {
                ColorHarmony.hueDistance(it, artistTextColor()) >=
                        ColorHarmony.MIN_DUOTONE_HUE_GAP
            }?.let(::accentForText) ?: Color.WHITE
        }

        fillPaint.shader = null
        textPaint.style = Paint.Style.FILL
        textPaint.textAlign = Paint.Align.CENTER

        val discSize = (screen * NOTE_COVER_FRACTION).coerceIn(dp(34f), dp(60f))
        val blockGap = screen * .05f

        // "Artist: Title", artist bold in the accent. The sentence is this face's *title*, so it
        // follows the user's Title text behaviour exactly as every other face's does - the watch
        // runs it through AdaptiveTitleText, so the miniature plans it with planTitle rather than
        // drawing one fixed ellipsized line. It used to do the latter, which left the setting
        // looking inert here. The plan is measured with the title typeface for the whole sentence
        // while the artist run is drawn bold: a wrap can therefore land a word early on a very
        // long artist, which is the one place this mirror is an approximation rather than a copy.
        val artistText = if (isPlayingShown()) {
            displayArtist()
        } else {
            context.getString(R.string.preview_playback_stopped)
        }
        val showArtist = showTrackArtist || !isPlayingShown()
        val inset = RoundScreenText.sideInsetForLines(.52f, dp(19f) / screen, NOTE_MAX_LINES)
        val available = screen * (1f - 2f * inset)
        // Each half cased by its own typography spec before joining, the same reason NoteFace's
        // NoteLine cases its two spans independently rather than the assembled sentence - the
        // artist and title cases are independent settings, and casing the whole line by only one
        // of them would silently apply it to text it does not own.
        val artistPart =
                if (showArtist) "${artistTypographySpec.case.apply(artistText)}: " else ""
        val titlePart =
                if (showTrackTitle) titleTypographySpec.case.apply(displayTitle()) else ""
        val sentence = artistPart + titlePart
        textPaint.letterSpacing = titleTypographySpec.trackingEm
        val plan = planTitle(
                available,
                dp(16f),
                dp(16f) * .62f,
                textOverride = sentence,
                bold = false,
                maxLines = NOTE_MAX_LINES,
                applyCase = false)
        textPaint.typeface = titleTypeface(bold = false)
        textPaint.textSize = plan.size
        val fm = textPaint.fontMetrics
        val lineHeight = fm.descent - fm.ascent

        // Cover disc, sitting above the line - the block as a whole is centred, so a sentence that
        // wrapped lifts the artwork instead of running off the bottom. With both lines hidden the
        // disc is the whole block, as the face's own Column is when NoteLine draws nothing.
        val lineCount = if (sentence.isEmpty()) 0 else plan.lines.size
        val blockHeight = discSize + if (lineCount == 0) 0f else blockGap + lineHeight * lineCount
        val discCy = cy - blockHeight / 2f + discSize / 2f
        val art = displayedArt()
        val discRect = RectF(
                cx - discSize / 2f, discCy - discSize / 2f,
                cx + discSize / 2f, discCy + discSize / 2f)
        // The same shared silhouette Carousel's cards are cut to, so one vocabulary answers both
        // faces here as it does on the watch. A circle is only this face's default, not its shape.
        val discCorner = discSize * CoverShape.fromPreference(noteCoverShape, CoverShape.CIRCLE)
                .cornerFraction
        canvas.save()
        canvas.clipPath(Path().apply {
            addRoundRect(discRect, discCorner, discCorner, Path.Direction.CW)
        })
        if (art != null) {
            drawArtwork(canvas, art, discRect, 255)
        } else {
            fillPaint.color = ColorUtils.setAlphaComponent(accent, 0x80)
            canvas.drawRect(discRect, fillPaint)
        }
        canvas.restore()

        if (lineCount > 0) drawNoteSentence(
                canvas,
                plan,
                artistPart.length,
                cx,
                discCy + discSize / 2f + blockGap - fm.ascent,
                available,
                lineHeight,
                artistAlpha(artistTextColor()),
                // Through titleAlpha, like every other face's title draw: this one used the literal
                // colour, so the user's chosen title colour and opacity were the one thing the Note
                // miniature would not show - and the tertiary substitution above must lose to an
                // explicit choice here exactly as it does in NoteFace.noteTitleColor.
                titleAlpha(titleColor))

        if (trackTimeVisible()) {
            textPaint.typeface = artistTypeface(bold = false)
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = dp(11f)
            textPaint.color = ColorUtils.setAlphaComponent(titleColor, 0x8C)
            drawTrackTimeText(
                    canvas, timeText(), cx, cy + radius - screen * .07f, dp(11f),
                    ColorUtils.setAlphaComponent(titleColor, 0x8C), textPaint.typeface)
        }

        if (alwaysShowTime || clockPreviewForced()) {
            drawFaceClock(canvas, cx, cy - radius, dp)
        }
        // Mini buttons stay on for this face, so the shared bottom chrome is drawn as usual.
        drawBottomChrome(canvas, cx, cy, radius, dp)
    }

    /**
     * Draws the Note face's `Artist: Title` sentence from an already-resolved [plan].
     *
     * Not [drawTitlePlan], which draws one run in one colour: this line is two - the artist in the
     * accent, the title in whatever reads against the backdrop - flowing as a single sentence, and
     * that is the whole composition. [artistChars] is where the artist run ends in the planned
     * text, which is how each wrapped line works out its own split point.
     */
    private fun drawNoteSentence(
            canvas: Canvas,
            plan: TitlePlan,
            artistChars: Int,
            cx: Float,
            baseline: Float,
            availWidth: Float,
            lineHeight: Float,
            artistColor: Int,
            titleColor: Int
    ) {
        textPaint.textSize = plan.size
        fun runs(line: String, consumed: Int): List<TextRun> {
            val split = (artistChars - consumed).coerceIn(0, line.length)
            return listOf(
                    TextRun(line.take(split), artistColor, artistTypeface(bold = true),
                            artistTypographySpec.trackingEm),
                    TextRun(line.drop(split), titleColor, titleTypeface(bold = false),
                            titleTypographySpec.trackingEm))
                    .filter { it.text.isNotEmpty() }
        }

        if (plan.marquee) {
            drawMarqueeRuns(canvas, runs(plan.lines.first(), 0), cx, baseline, availWidth)
            return
        }
        var consumed = 0
        plan.lines.forEachIndexed { index, line ->
            drawTextRuns(canvas, runs(line, consumed), cx, baseline + index * lineHeight)
            // splitLines rejoins each line's words with a single space, so every line boundary
            // swallowed exactly one character of the sentence.
            consumed += line.length + 1
        }
    }

    /**
     * Miniature of the Split face: cover in the top band, a solid album-coloured panel below it
     * holding artist and title, and the source-app badge straddling the seam right of centre.
     *
     * Mirrors `SplitFace.kt` by hand (`mobile` cannot depend on `wear`), including the text-colour
     * decision: both sides call [AdaptiveTextContrast.prefersDarkText] on the same panel colour, so
     * the preview cannot claim white text where the watch will draw black.
     */
    private fun drawSplitPlayer(
            canvas: Canvas,
            geometry: PreviewGeometry,
            dp: (Float) -> Float
    ) {
        val cx = geometry.cx
        val cy = geometry.cy
        val radius = geometry.radius
        val screen = radius * 2f
        val screenTop = cy - radius
        val seamY = screenTop + screen * SPLIT_SEAM_FRACTION

        val panelColor = tonal(albumAccent(), SPLIT_PANEL_LIGHTNESS, .18f, .62f)
        val darkText = AdaptiveTextContrast.prefersDarkText(panelColor)
        val primary = ColorUtils.setAlphaComponent(
                if (darkText) Color.BLACK else Color.WHITE, if (darkText) 0xDE else 0xFF)
        val secondary = ColorUtils.setAlphaComponent(
                if (darkText) Color.BLACK else Color.WHITE, if (darkText) 0x94 else 0xB3)

        fillPaint.shader = null

        val screenRect = RectF(cx - radius, screenTop, cx + radius, cy + radius)
        val coverRect = RectF(cx - radius, screenTop, cx + radius, seamY)
        val panelRect = RectF(cx - radius, seamY, cx + radius, cy + radius)
        val art = displayedArt()
        val artVisible = art != null && !PlayerBackgroundStyle.fromPreference(artStyle).hidesArtwork

        if (splitPanel == SplitPanelStyle.BLUR && artVisible) {
            // One image across the whole screen, sharp above the seam and blurred below it -
            // mirroring SplitFace.ContinuousBackdrop. Both copies are mapped to the *screen*
            // rectangle and then clipped, never fitted into their own band: fitting the lower one
            // to the panel would crop it differently and the seam would stop being one picture.
            canvas.save()
            canvas.clipRect(coverRect)
            drawArtwork(canvas, art, screenRect, 255)
            canvas.restore()

            canvas.save()
            canvas.clipRect(panelRect)
            // Panel first, artwork over it at a fixed alpha - the same order the face composites
            // in, and for the same reason: the panel colour is what the text contrast was decided
            // against, so it has to be the base rather than a wash.
            fillPaint.color = panelColor
            canvas.drawRect(panelRect, fillPaint)
            drawArtwork(
                    canvas,
                    displayedBlurredArt() ?: art,
                    screenRect,
                    SPLIT_PANEL_ART_ALPHA)
            canvas.restore()
        } else {
            // Cover band. Painted over a panel-tinted base so a transparent or absent cover still
            // reads as the two-tone composition rather than showing the window through.
            fillPaint.color = ColorUtils.setAlphaComponent(panelColor, 0xB3)
            canvas.drawRect(coverRect, fillPaint)
            if (artVisible) {
                canvas.save()
                canvas.clipRect(coverRect)
                drawArtwork(canvas, art, coverRect, 255)
                canvas.restore()
            }

            // Solid panel.
            fillPaint.color = panelColor
            canvas.drawRect(panelRect, fillPaint)
        }

        // Split owns an opaque backdrop, so the shared floor must be placed after it or it is
        // completely covered. Keep it below the card's text and badge, matching the watch face.
        if (accentFloor.isVisible) {
            drawAccentFloor(canvas, geometry, AdaptiveTextContrast.adapt(albumAccent(), 0f))
        }

        // Artist over title, left-aligned, inset for the narrowing round chord.
        val inset = RoundScreenText.sideInsetFor(
                SPLIT_SEAM_FRACTION + .04f, SPLIT_SEAM_FRACTION + .28f)
        val textLeft = cx - radius + screen * inset
        val available = screen * (1f - 2f * inset)
        textPaint.style = Paint.Style.FILL
        textPaint.textAlign = Paint.Align.LEFT
        var baseline = seamY + screen * .04f + dp(11f)
        if (showTrackArtist || !isPlayingShown()) {
            textPaint.typeface = artistTypeface(bold = false)
            textPaint.textSize = artistTypographySpec.scaled(dp(12f))
            textPaint.color = secondary
            val artistText = if (isPlayingShown()) {
                displayArtist()
            } else {
                context.getString(R.string.preview_playback_stopped)
            }
            canvas.drawText(ellipsize(artistText, available), textLeft, baseline, textPaint)
            baseline += dp(16f)
        }
        if (showTrackTitle) {
            // SplitFace uses AdaptiveTitleText here rather than a bare two-line wrap, precisely so
            // the panel never breaks a word badly; the preview has to follow or the two disagree
            // on the one face whose title has a whole panel to itself.
            drawAdaptiveTitle(
                    canvas,
                    textLeft,
                    baseline,
                    available,
                    dp(16f),
                    titleAlpha(primary),
                    align = Paint.Align.LEFT)
        }

        // Source icon on the seam, right of centre. Drawn bare - no disc, no ring - matching
        // SplitFace.SourceBadge, which dropped both so the glyph is the only mark on the seam.
        // SplitFace scales the glyph before applying its 26–52dp safety bounds, then anchors its
        // right edge. Keeping that order makes the preview honest at the 50%/200% extremes.
        val badgeDiameter = (screen * SPLIT_BADGE_FRACTION * sourceIconTypographySpec.scale)
                .coerceIn(dp(26f), dp(52f))
        val badgeCx = cx + radius - screen * .10f - badgeDiameter / 2f
        if (showSourceIcon) {
            // The playing app's real mark, not this app's launcher icon. SourceBadge tints a
            // template to whatever reads on the panel and leaves a launcher-icon fallback alone,
            // circle-clipping only that fallback - a template is already a shape on transparency
            // and clipping it just cuts the glyph.
            val onPanel = if (darkText) {
                ColorUtils.setAlphaComponent(Color.BLACK, 0xD9)
            } else {
                Color.WHITE
            }
            drawSourceGlyph(
                    canvas, badgeCx, seamY, badgeDiameter, onPanel,
                    cornerFraction = if (sourceGlyphTintable) 0f else .5f)
        }

        if (alwaysShowTime || clockPreviewForced()) {
            drawFaceClock(canvas, cx, cy - radius, dp)
        }
        // No drawBottomChrome: like Chat, this face defaults its mini-button row off and composes
        // the whole screen itself, so the shared row would preview something the watch won't draw.
    }

    /** What one curated face designs for its artist line, beyond the size. */
    private data class CuratedArtistStyle(
            val alpha: Int,
            val trackingEm: Float,
            val uppercase: Boolean
    )

    /**
     * The artist treatment each curated layout composes, mirroring its `*Metadata` composable.
     *
     * The preview used to draw one styling for all of them - opaque, untracked, sentence case -
     * because five faces share `header()` here while the watch gives each its own. That flattened
     * exactly the detail these layouts are distinguished by: Eclipse's widely tracked whisper and
     * Vinyl's tight cap-line read as the same row.
     *
     * Tracking is stated in **em** rather than the watch's sp because it has to survive a size
     * change: the faces set it beside an 8sp line, while this miniature draws the same line at its
     * own calibrated size, and only the em form means the same thing at both.
     */
    private fun curatedArtistStyle(kind: String): CuratedArtistStyle = when (kind) {
        // .58 / 2sp of 8sp
        "vinyl" -> CuratedArtistStyle(0x94, .25f, uppercase = true)
        // .76 / 1.55sp of 8sp
        "poster" -> CuratedArtistStyle(0xC2, .194f, uppercase = true)
        // .62 / 1.2sp of 8sp
        "studio" -> CuratedArtistStyle(0x9E, .15f, uppercase = true)
        // .70 / .55sp of 8sp - the one that stays sentence case
        "halo" -> CuratedArtistStyle(0xB3, .069f, uppercase = false)
        // .48 / 2.4sp of 8sp. The face also sets FontWeight.Light, which this preview has no
        // lighter face to draw with - the two available weights are regular and bold.
        "eclipse" -> CuratedArtistStyle(0x7A, .3f, uppercase = true)
        // .70, untracked: Material's line is a plain 13sp row, not a caption.
        "material" -> CuratedArtistStyle(0xB3, 0f, uppercase = false)
        else -> CuratedArtistStyle(0xFF, 0f, uppercase = false)
    }

    /** Shared preview renderer for the curated watch-first layouts. They intentionally expose a
     * single play/pause focus and leave shortcuts to the configured mini-button row. */
    private fun drawCuratedPlayer(
            canvas: Canvas,
            geometry: PreviewGeometry,
            dp: (Float) -> Float,
            kind: String
    ) {
        val cx = geometry.cx
        val cy = geometry.cy
        val radius = geometry.radius
        val bounds = geometry.bounds
        val accent = albumAccent()
        val primary = tunedPreviewColor(accent, .62f, .74f)
        val secondary = tunedPreviewColor(
                albumSecondaryAccent(),
                .58f,
                .70f
        )
        val tertiary = tunedPreviewColor(
                albumTertiaryAccent(),
                .62f,
                .72f
        )
        val deep = tunedPreviewColor(accent, .075f, .48f)
        val surfaceColor = tunedPreviewColor(secondary, .16f, .42f)
        val progressColor = resolveTint(progressMode, progressCustom, progressDesaturated)
        val artistColor = if (isPlayingShown()) {
            artistTextColor()
        } else {
            Color.WHITE
        }
        val theme = screenThemeSpec()
        val essentialTransport = kind == "material"
        val controlsVisible = playerControlsVisible || essentialTransport
        val iconAlpha = ((if (essentialTransport) theme.iconAlpha.takeIf { it > 0f } ?: 1f
                else theme.iconAlpha) * 255).toInt().coerceIn(0, 255)
        val miniConfigured = isPlayingShown() &&
                (miniButtonIcons.isNotEmpty() || surface == PreviewSurface.MINI_BUTTONS)
        val titleVisible = showTrackTitle
        // The stopped/status message remains operational UI even when the artist preference is
        // off, matching the Wear host's metadata visibility policy.
        val artistVisible = showTrackArtist || !isPlayingShown()
        val metadataVisible = titleVisible || artistVisible
        // Mini buttons overlay the layout without switching it to a different compact
        // composition; this mirrors the watch renderer and keeps each theme's identity intact.
        val hasMiniButtons = false
        val screenTop = cy - radius
        val screenDiameter = radius * 2f
        val curveSafety = if (deviceRound == false || buttonsCurveStyle == "flat") 0f else dp(8f)
        val shortcutTop = if (hasMiniButtons) {
            cy + radius - dp(24f) - dp(38f) - curveSafety
        } else {
            cy + radius
        }
        val compactCenterY = minOf(screenTop + screenDiameter * .38f, shortcutTop - dp(24f))
                .coerceAtLeast(screenTop + screenDiameter * .29f)
        val showCompactHeader = metadataVisible &&
                (!hasMiniButtons || shortcutTop >= screenTop + screenDiameter * .50f)

        fillPaint.shader = null
        drawPlayerShading(canvas, bounds, cx, cy, radius)

        fun header(topY: Float, compact: Boolean = false, showArtist: Boolean = true) {
            val titleSize = dp(when (kind) {
                "vinyl" -> 12f
                "halo" -> 13f
                "eclipse" -> if (compact) 12f else 15f
                else -> if (compact) 14f else 18f
            })
            val singleLineAdjustment = if (titleVisible.xor(artistVisible && showArtist)) dp(5f) else 0f
            val adjustedTop = topY + singleLineAdjustment
            val artistGap = dp(if (compact) 12f else 14f)
            var artistBaseline = adjustedTop
            if (titleVisible) {
                val rawTitle = displayTitle()
                val title = if (kind == "vinyl" || kind == "eclipse") rawTitle.uppercase() else rawTitle
                val titleWidth = radius * 1.38f
                // Every curated header goes through AdaptiveTitleText on the watch, so the whole
                // shrink/wrap/scroll cascade applies here too - this used to be a fixed
                // fits-or-scrolls pair that made the Title text behaviour setting inert on ten
                // faces at once.
                val plan = planTitle(titleWidth, titleSize, titleSize * .62f, title)
                val blockHeight = drawTitlePlan(
                        canvas,
                        plan,
                        cx,
                        adjustedTop,
                        titleWidth,
                        titleAlpha(if (kind == "eclipse") 0xE0FFFFFF.toInt() else Color.WHITE))
                // These headers are a Column on the watch, so a wrapped title pushes the artist
                // down rather than being drawn over it. Only the lines past the first add to the
                // designed gap, which keeps the common single-line spacing exactly as tuned.
                artistBaseline = adjustedTop + artistGap +
                        blockHeight / plan.lines.size * (plan.lines.size - 1)
            }
            if (showArtist && artistVisible) {
                val style = curatedArtistStyle(kind)
                val label = if (isPlayingShown()) displayArtist()
                else context.getString(R.string.preview_playback_stopped)
                drawArtistLine(
                        canvas,
                        if (style.uppercase) label.uppercase() else label,
                        cx, artistBaseline, radius * 1.35f,
                        ColorUtils.setAlphaComponent(artistColor, style.alpha),
                        dp(if (compact) 9f else 11f),
                        designedTracking = style.trackingEm,
                        sourceGlyph = true,
                        glyphSize = dp(if (kind == "material") 13f else 10f))
            }
        }

        // Mirrors the watch's AlbumArtwork(): honors ALBUM_ART_STYLE instead of always drawing
        // the sharp cover - "hidden" falls back to the same palette gradient used when no art has
        // arrived yet, "bw"/"blur_bw" desaturate, "blur"/"blur_bw" reuse the already-blurred
        // bitmap this view precomputes for the classic face's own blur style.
        fun artwork(rect: RectF, corner: Float, circle: Boolean = false) {
            val path = Path().apply {
                if (circle) addCircle(rect.centerX(), rect.centerY(), rect.width() / 2f, Path.Direction.CW)
                else addRoundRect(rect, corner, corner, Path.Direction.CW)
            }
            canvas.save()
            canvas.clipPath(path)
            val selectedBackground = PlayerBackgroundStyle.fromPreference(artStyle)
            val hidden = selectedBackground.hidesArtwork
            val blurred = selectedBackground.blurredArtwork
            val grayscale = selectedBackground.grayscaleArtwork
            val art = if (hidden) null else if (blurred) displayedBlurredArt() else displayedArt()
            if (art != null) drawArtwork(canvas, art, rect, 255, grayscale) else {
                fillPaint.shader = LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                        intArrayOf(primary, secondary, tertiary), null, Shader.TileMode.CLAMP)
                canvas.drawRect(rect, fillPaint)
                fillPaint.shader = null
            }
            canvas.restore()
        }

        fun playGlyph(x: Float, y: Float, size: Float, tint: Int = Color.WHITE) {
            if (!controlsVisible) return
            drawIcon(canvas,
                    if (isPlayingShown()) commonR.drawable.action_pause_filled
                    else commonR.drawable.action_play_filled,
                    x, y, size * theme.iconScale, tint, iconAlpha)
        }

        fun progressLine(y: Float, width: Float) {
            if (!internalProgressVisible) return
            val height = dp(4f)
            fillPaint.shader = null
            fillPaint.color = 0x2CFFFFFF
            canvas.drawRoundRect(cx - width / 2f, y - height / 2f,
                    cx + width / 2f, y + height / 2f, height, height, fillPaint)
            val filled = width * progressFraction()
            if (filled > 0f) {
                fillPaint.shader = null
                fillPaint.color = progressColor
                canvas.drawRoundRect(cx - width / 2f, y - height / 2f,
                        cx - width / 2f + filled, y + height / 2f, height, height, fillPaint)
                fillPaint.shader = null
            }
        }

        when (kind) {
            "depth" -> {
                // Mirrors DepthComposition: two off-axis album hazes, the vignette that makes the
                // rim recede, the floor gradient, grounded text and the hairline progress. Those
                // layers are the whole difference from Immersive at a glance, so the preview has
                // to draw all of them or the two faces look identical in the picker.
                fillPaint.shader = RadialGradient(
                        cx - radius * .40f, cy - radius * .44f, radius * 1.56f,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(rawAlbumAccent(), 0x4D),
                                ColorUtils.setAlphaComponent(rawSecondaryAccent(), 0x29),
                                Color.TRANSPARENT),
                        floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
                canvas.drawCircle(cx, cy, radius, fillPaint)
                fillPaint.shader = null

                // Second haze, anchored to the opposite corner at a smaller radius.
                fillPaint.shader = RadialGradient(
                        cx + radius * .56f, cy + radius * .52f, radius * 1.16f,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(rawTertiaryAccent(), 0x38),
                                Color.TRANSPARENT),
                        floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
                canvas.drawCircle(cx, cy, radius, fillPaint)
                fillPaint.shader = null

                // The vignette that replaced the parallax as the depth cue - only the outer
                // third darkens, so the centre of the cover reads as sitting forward.
                fillPaint.shader = RadialGradient(
                        cx, cy, radius * 1.24f,
                        intArrayOf(
                                Color.TRANSPARENT,
                                Color.TRANSPARENT,
                                ColorUtils.setAlphaComponent(Color.BLACK, 0x75)),
                        floatArrayOf(0f, .62f, 1f), Shader.TileMode.CLAMP)
                canvas.drawCircle(cx, cy, radius, fillPaint)
                fillPaint.shader = null

                fillPaint.shader = LinearGradient(cx, cy - radius, cx, cy + radius,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(Color.BLACK, 0x4D),
                                Color.TRANSPARENT,
                                Color.TRANSPARENT,
                                ColorUtils.setAlphaComponent(Color.BLACK, 0xCC)),
                        floatArrayOf(0f, .38f, .62f, 1f), Shader.TileMode.CLAMP)
                canvas.drawCircle(cx, cy, radius, fillPaint)
                fillPaint.shader = null

                var y = cy + radius - radius * 2f * .15f
                if (artistVisible) {
                    drawArtistLine(
                            canvas,
                            if (isPlayingShown()) displayArtist()
                            else context.getString(R.string.preview_playback_stopped),
                            cx, y, radius * 1.45f,
                            ColorUtils.setAlphaComponent(artistColor, 0xC7),
                            dp(11f),
                            sourceGlyph = true, glyphSize = dp(13f))
                    y -= dp(16f)
                }
                if (titleVisible) {
                    textPaint.textAlign = Paint.Align.CENTER
                    drawAdaptiveTitle(
                            canvas, cx, y, radius * 1.45f, dp(14f), titleAlpha(Color.WHITE),
                            bottomAnchored = true)
                }
                if (internalProgressVisible) {
                    val half = radius * .78f
                    val lineY = cy + radius - dp(6f)
                    fillPaint.color = ColorUtils.setAlphaComponent(Color.WHITE, 0x2E)
                    canvas.drawRoundRect(cx - half, lineY - dp(1f), cx + half, lineY + dp(1f),
                            dp(1f), dp(1f), fillPaint)
                    fillPaint.color = ColorUtils.setAlphaComponent(liftedAccent(rawAlbumAccent()), 0xEB)
                    canvas.drawRoundRect(cx - half, lineY - dp(1f),
                            cx - half + half * 2f * progressFraction(), lineY + dp(1f),
                            dp(1f), dp(1f), fillPaint)
                }
            }
            "immersive" -> {
                // Mirrors the watch's ImmersiveComposition: the full-bleed cover is already drawn
                // by the background treatment; the title/artist/time sit *grounded at the bottom*
                // of the screen (not under the top bezel), over a soft dark gradient. No centre
                // play/pause - a tap toggles playback. This branch existed on the watch but was
                // missing from the preview, so Immersive previewed with no text at all.
                // The watch's own bottom scrim (transparent → black .74) for text legibility.
                fillPaint.shader = LinearGradient(cx, cy, cx, cy + radius,
                        intArrayOf(Color.TRANSPARENT, ColorUtils.setAlphaComponent(Color.BLACK, 0xBD)),
                        floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(cx - radius, cy, cx + radius, cy + radius, fillPaint)
                fillPaint.shader = null
                val bottomInset = radius * 2f * IMMERSIVE_BOTTOM_PADDING_FRACTION
                val immersiveTextWidth = radius * 2f *
                        (1f - IMMERSIVE_SIDE_PADDING_FRACTION * 2f)
                var y = cy + radius - bottomInset
                if (trackTimeVisible()) {
                    textPaint.textAlign = Paint.Align.CENTER
                    textPaint.typeface = fontRegular
                    textPaint.textSize = dp(IMMERSIVE_TRACK_TIME_SP)
                    textPaint.color = 0xA8FFFFFF.toInt()
                    drawTrackTimeText(
                            canvas,
                            timeText(),
                            cx,
                            y,
                            dp(IMMERSIVE_TRACK_TIME_SP),
                            0xA8FFFFFF.toInt(),
                            textPaint.typeface
                    )
                    y -= dp(
                            IMMERSIVE_TRACK_TIME_TOP_PADDING_DP +
                                    IMMERSIVE_TRACK_TIME_LINE_HEIGHT_SP)
                }
                if (artistVisible) {
                    drawArtistLine(
                            canvas,
                            if (isPlayingShown()) displayArtist()
                            else context.getString(R.string.preview_playback_stopped),
                            cx, y, immersiveTextWidth,
                            ColorUtils.setAlphaComponent(artistColor, 0xD1),
                            dp(IMMERSIVE_ARTIST_SP),
                            sourceGlyph = true,
                            glyphSize = dp(IMMERSIVE_SOURCE_ICON_SIZE_DP))
                    y -= dp(
                            IMMERSIVE_ARTIST_TOP_PADDING_DP + IMMERSIVE_ARTIST_LINE_HEIGHT_SP)
                }
                if (titleVisible) {
                    drawAdaptiveTitle(
                            canvas,
                            cx,
                            y,
                            immersiveTextWidth,
                            dp(IMMERSIVE_TITLE_SP),
                            titleAlpha(Color.WHITE),
                            bottomAnchored = true,
                            lineHeight = dp(IMMERSIVE_TITLE_LINE_HEIGHT_SP))
                }
            }
            "vinyl" -> {
                if (showCompactHeader) {
                    header(cy - radius + dp(43f), compact = true, showArtist = !hasMiniButtons)
                }
                val discR = dp(if (hasMiniButtons) 28f else 44f)
                val discY = cy
                val discRect = RectF(cx - discR, discY - discR, cx + discR, discY + discR)
                fillPaint.shader = RadialGradient(cx, discY, discR,
                        intArrayOf(deep, 0xFF18191C.toInt(), 0xFF08090B.toInt(), Color.BLACK),
                        floatArrayOf(0f, .34f, .74f, 1f), Shader.TileMode.CLAMP)
                canvas.drawCircle(cx, discY, discR, fillPaint)
                fillPaint.shader = null
                repeat(11) { index ->
                    strokePaint.strokeWidth = dp(if (index % 3 == 0) .75f else .45f)
                    strokePaint.color = if (index % 3 == 0) 0x13FFFFFF else 0x09FFFFFF
                    canvas.drawCircle(cx, discY, discR * (.36f + index * .056f), strokePaint)
                }
                strokePaint.strokeWidth = discR * .15f
                strokePaint.strokeCap = Paint.Cap.ROUND
                strokePaint.color = 0x1FFFFFFF
                canvas.drawArc(
                        RectF(cx - discR * .82f, discY - discR * .82f,
                                cx + discR * .82f, discY + discR * .82f),
                        206f, 76f, false, strokePaint
                )
                val labelR = discR * .38f
                artwork(
                        RectF(cx - labelR, discY - labelR, cx + labelR, discY + labelR),
                        labelR,
                        circle = true
                )
                fillPaint.color = 0x42000000
                canvas.drawCircle(cx, discY, labelR, fillPaint)
                strokePaint.strokeWidth = dp(.7f)
                strokePaint.color = 0x38FFFFFF
                canvas.drawCircle(cx, discY, labelR, strokePaint)
                fillPaint.color = 0xB8000000.toInt()
                canvas.drawCircle(cx, discY, labelR * .14f, fillPaint)
                if (internalProgressVisible) {
                    strokePaint.strokeWidth = dp(3f)
                    strokePaint.strokeCap = Paint.Cap.ROUND
                    strokePaint.color = 0x32FFFFFF
                    canvas.drawArc(discRect, -90f, 360f, false, strokePaint)
                    strokePaint.color = progressColor
                    canvas.drawArc(discRect, -90f, progressFraction() * 360f, false, strokePaint)
                }
                playGlyph(cx, discY, dp(if (hasMiniButtons) 13f else 15f))
                if (trackTimeVisible()) {
                    textPaint.color = 0xA8FFFFFF.toInt(); textPaint.textSize = dp(9f)
                    drawTrackTimeText(
                            canvas, timeText(), cx,
                            clampTimeY(discRect.bottom + dp(17f), miniConfigured, radius, cy, dp),
                            dp(9f), 0xA8FFFFFF.toInt(), textPaint.typeface)
                }
            }
            "poster" -> {
                // The watch's PosterMetadata is a Column with Modifier.align(Alignment.Center) -
                // the whole title+artist block sits vertically centered on the screen, not
                // anchored under the top bezel.
                if (showCompactHeader) {
                    val posterTitleSize = dp(if (hasMiniButtons) 14f else 18f)
                    val posterArtistSize = artistTypographySpec.scaled(dp(10f))
                    val posterGap = dp(6f)
                    val posterArtistVisible = !hasMiniButtons && artistVisible

                    // The watch's PosterMetadata title now follows the user's Title text
                    // behaviour (AdaptiveTitleText), so the preview does too instead of always
                    // wrapping to at most two lines - a short one-line title still takes just
                    // one line, so the artist sits right beneath it rather than floating a whole
                    // empty line below (the "big gap" the preview used to show).
                    val posterTitleWidth = radius * 1.40f
                    val posterFloorSize = posterTitleSize * 0.62f
                    val posterPlan = planTitle(
                            posterTitleWidth, posterTitleSize, posterFloorSize)

                    textPaint.typeface = titleTypeface(bold = true)
                    textPaint.textSize = posterPlan.size
                    val posterTitleFm = textPaint.fontMetrics
                    val posterTitleLineH = posterTitleFm.descent - posterTitleFm.ascent

                    textPaint.typeface = artistTypeface(bold = false)
                    textPaint.textSize = posterArtistSize
                    val posterArtistFm = textPaint.fontMetrics
                    val posterArtistLineH = posterArtistFm.descent - posterArtistFm.ascent

                    val posterTitleBlockH = posterTitleLineH * posterPlan.lines.size
                    val posterTotalH = (if (titleVisible) posterTitleBlockH else 0f) +
                            (if (titleVisible && posterArtistVisible) posterGap else 0f) +
                            (if (posterArtistVisible) posterArtistLineH else 0f)
                    var posterY = cy - posterTotalH / 2f

                    if (titleVisible) {
                        textPaint.typeface = titleTypeface(bold = true)
                        textPaint.textSize = posterPlan.size
                        textPaint.color = Color.WHITE
                        if (posterPlan.marquee) {
                            drawMarqueeText(canvas, posterPlan.lines.first(), cx,
                                    posterY - posterTitleFm.ascent, posterTitleWidth)
                            posterY += posterTitleLineH
                        } else {
                            posterPlan.lines.forEach { line ->
                                canvas.drawText(line, cx, posterY - posterTitleFm.ascent, textPaint)
                                posterY += posterTitleLineH
                            }
                        }
                    }
                    if (posterArtistVisible) {
                        if (titleVisible) posterY += posterGap
                        val posterStyle = curatedArtistStyle("poster")
                        val posterLabel = if (isPlayingShown()) displayArtist()
                        else context.getString(R.string.preview_playback_stopped)
                        drawArtistLine(
                                canvas,
                                if (posterStyle.uppercase) posterLabel.uppercase() else posterLabel,
                                cx, posterY - posterArtistFm.ascent, radius * 1.35f,
                                ColorUtils.setAlphaComponent(artistColor, posterStyle.alpha),
                                dp(10f),
                                designedTracking = posterStyle.trackingEm,
                                sourceGlyph = true, glyphSize = dp(10f))
                    }
                }
                // No play/pause glyph here: the watch's PosterComposition never draws one (tap
                // still toggles playback) - the artwork stays unobscured, matching CHANGELOG.
                if (!miniConfigured) progressLine(cy + dp(56f), dp(104f))
                if (trackTimeVisible()) {
                    textPaint.color = 0xA8FFFFFF.toInt(); textPaint.textSize = dp(9f)
                    // On the floor when there's no progress line above it (mirrors the watch).
                    val posterTimeY = if (internalProgressVisible) dp(73f) else dp(81f)
                    drawTrackTimeText(
                            canvas, timeText(), cx,
                            clampTimeY(cy + posterTimeY, miniConfigured, radius, cy, dp),
                            dp(9f), 0xA8FFFFFF.toInt(), textPaint.typeface)
                }
            }
            "studio" -> {
                // The watch's StudioMetadata is also a Column with Alignment.Center - center the
                // block on screen instead of anchoring it under the top bezel like header() does
                // for the genuinely top-anchored faces (vinyl/halo/eclipse).
                if (showCompactHeader) {
                    val studioTitleSize = dp(if (hasMiniButtons) 14f else 18f)
                    val studioArtistSize =
                            artistTypographySpec.scaled(dp(if (hasMiniButtons) 9f else 11f))
                    val studioGap = dp(if (hasMiniButtons) 5f else 6f)
                    val studioArtistVisible = !hasMiniButtons && artistVisible

                    // Studio's title now follows the user's Title text behaviour, same as Poster
                    // above.
                    val studioTitleWidth = radius * 1.38f
                    val studioFloorSize = studioTitleSize * 0.62f
                    val studioPlan = planTitle(
                            studioTitleWidth, studioTitleSize, studioFloorSize)

                    textPaint.typeface = titleTypeface(bold = true)
                    textPaint.textSize = studioPlan.size
                    val studioTitleFm = textPaint.fontMetrics
                    val studioTitleLineH = studioTitleFm.descent - studioTitleFm.ascent

                    textPaint.typeface = artistTypeface(bold = false)
                    textPaint.textSize = studioArtistSize
                    val studioArtistFm = textPaint.fontMetrics
                    val studioArtistLineH = studioArtistFm.descent - studioArtistFm.ascent

                    val studioTitleBlockH = studioTitleLineH * studioPlan.lines.size
                    val studioTotalH = (if (titleVisible) studioTitleBlockH else 0f) +
                            (if (titleVisible && studioArtistVisible) studioGap else 0f) +
                            (if (studioArtistVisible) studioArtistLineH else 0f)
                    var studioY = cy - studioTotalH / 2f

                    if (titleVisible) {
                        textPaint.typeface = titleTypeface(bold = true)
                        textPaint.textSize = studioPlan.size
                        textPaint.color = Color.WHITE
                        if (studioPlan.marquee) {
                            drawMarqueeText(canvas, studioPlan.lines.first(), cx,
                                    studioY - studioTitleFm.ascent, studioTitleWidth)
                            studioY += studioTitleLineH
                        } else {
                            studioPlan.lines.forEach { line ->
                                canvas.drawText(line, cx, studioY - studioTitleFm.ascent, textPaint)
                                studioY += studioTitleLineH
                            }
                        }
                    }
                    if (studioArtistVisible) {
                        if (titleVisible) studioY += studioGap
                        val studioStyle = curatedArtistStyle("studio")
                        val studioLabel = if (isPlayingShown()) displayArtist()
                        else context.getString(R.string.preview_playback_stopped)
                        drawArtistLine(
                                canvas,
                                if (studioStyle.uppercase) studioLabel.uppercase() else studioLabel,
                                cx, studioY - studioArtistFm.ascent, radius * 1.35f,
                                ColorUtils.setAlphaComponent(artistColor, studioStyle.alpha),
                                dp(if (hasMiniButtons) 9f else 11f),
                                designedTracking = studioStyle.trackingEm,
                                sourceGlyph = true, glyphSize = dp(10f))
                    }
                }
                val orbR = dp(if (hasMiniButtons) 26f else 31f)
                // The watch's StudioComposition anchors this ring near the screen's bottom edge,
                // not its center - no play/pause glyph is drawn on it either (tap still toggles
                // playback; only the progress arc is visible).
                val orbY = cy + radius * .42f
                if (internalProgressVisible) {
                    strokePaint.strokeWidth = dp(4f); strokePaint.strokeCap = Paint.Cap.ROUND
                    strokePaint.color = 0x3CFFFFFF
                    canvas.drawArc(RectF(cx - orbR, orbY - orbR, cx + orbR, orbY + orbR), -90f, 360f, false, strokePaint)
                    strokePaint.color = Color.WHITE
                    canvas.drawArc(RectF(cx - orbR, orbY - orbR, cx + orbR, orbY + orbR), -90f,
                            progressFraction() * 360f, false, strokePaint)
                }
                if (trackTimeVisible()) {
                    textPaint.color = 0xA8FFFFFF.toInt(); textPaint.textSize = dp(9f)
                    // Inside the orb when it's drawn, on the floor when it isn't (mirrors the
                    // watch's TrackFooter anchoring for Studio).
                    val studioTimeY = if (internalProgressVisible) orbY + dp(3f) else cy + dp(81f)
                    drawTrackTimeText(
                            canvas, timeText(), cx,
                            clampTimeY(studioTimeY, miniConfigured, radius, cy, dp),
                            dp(9f), 0xA8FFFFFF.toInt(), textPaint.typeface)
                }
            }
            "halo" -> {
                if (showCompactHeader) {
                    header(cy - radius + dp(43f), compact = true, showArtist = !hasMiniButtons)
                }
                val artR = dp(if (hasMiniButtons) 26f else 41f)
                val artY = cy
                repeat(3) { index ->
                    strokePaint.strokeWidth = dp(7f - index * 1.5f)
                    strokePaint.color = ColorUtils.setAlphaComponent(
                            intArrayOf(primary, secondary, tertiary)[index], 52 - index * 8)
                    canvas.drawCircle(cx, artY, artR * (.74f + index * .255f), strokePaint)
                }
                val rect = RectF(cx - artR, artY - artR, cx + artR, artY + artR)
                artwork(rect, artR, circle = true)
                if (internalProgressVisible) {
                    strokePaint.strokeWidth = dp(4f); strokePaint.strokeCap = Paint.Cap.ROUND
                    strokePaint.color = 0x30FFFFFF
                    canvas.drawArc(rect, -90f, 360f, false, strokePaint)
                    strokePaint.color = progressColor
                    canvas.drawArc(rect, -90f, progressFraction() * 360f, false, strokePaint)
                }
                playGlyph(cx, artY, artR * .48f)
                if (trackTimeVisible()) {
                    textPaint.color = 0xA8FFFFFF.toInt(); textPaint.textSize = dp(9f)
                    drawTrackTimeText(
                            canvas, timeText(), cx,
                            clampTimeY(artY + artR + dp(17f), miniConfigured, radius, cy, dp),
                            dp(9f), 0xA8FFFFFF.toInt(), textPaint.typeface)
                }
            }
            "aurora" -> {
                // Mirrors the watch's AuroraComposition: a centered glass card with the metadata
                // Column inside it (artist eyebrow above an italic title, left-aligned), the play
                // focus offset to the card's lower right and the linear progress to its lower
                // left. The old preview drew none of this - see the user-facing mismatch reports.
                val hasMetadata = metadataVisible && showCompactHeader
                val cardW = radius * 1.44f
                val cardH = radius * if (hasMetadata) 1f else .68f
                val cardRect = RectF(cx - cardW / 2f, cy - cardH / 2f, cx + cardW / 2f, cy + cardH / 2f)
                val cardCorner = radius * .15f
                val cardPath = Path().apply {
                    addRoundRect(cardRect, cardCorner, cardCorner, Path.Direction.CW)
                }
                canvas.save()
                canvas.clipPath(cardPath)
                fillPaint.shader = LinearGradient(cardRect.left, cardRect.top,
                        cardRect.right, cardRect.bottom,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(deep, 245),
                                ColorUtils.setAlphaComponent(primary, 133),
                                ColorUtils.setAlphaComponent(secondary, 87),
                                ColorUtils.setAlphaComponent(surfaceColor, 245)
                        ), null, Shader.TileMode.CLAMP)
                canvas.drawRect(cardRect, fillPaint)
                fillPaint.shader = RadialGradient(
                        cardRect.left + cardW * .12f, cardRect.top + cardH * .05f,
                        cardH * .82f,
                        intArrayOf(ColorUtils.setAlphaComponent(tertiary, 143),
                                ColorUtils.setAlphaComponent(primary, 38), Color.TRANSPARENT),
                        floatArrayOf(0f, .42f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(cardRect, fillPaint)
                fillPaint.shader = null
                val flare = Path().apply {
                    moveTo(cardRect.left - cardW * .12f, cardRect.top + cardH * .78f)
                    cubicTo(cardRect.left + cardW * .28f, cardRect.top + cardH * .30f,
                            cardRect.left + cardW * .64f, cardRect.top + cardH * 1.08f,
                            cardRect.left + cardW * 1.10f, cardRect.top + cardH * .36f)
                }
                strokePaint.shader = LinearGradient(cardRect.left, cy, cardRect.right, cy,
                        intArrayOf(tertiary, secondary, primary), null, Shader.TileMode.CLAMP)
                strokePaint.strokeWidth = cardH * .12f
                strokePaint.strokeCap = Paint.Cap.ROUND
                strokePaint.alpha = 66
                canvas.drawPath(flare, strokePaint)
                strokePaint.shader = null
                strokePaint.alpha = 255
                canvas.restore()
                strokePaint.strokeWidth = dp(.8f)
                strokePaint.color = 0x3DFFFFFF
                canvas.drawRoundRect(cardRect, cardCorner, cardCorner, strokePaint)

                if (hasMetadata) {
                    val columnWidth = cardW * .70f
                    val columnLeft = cx - cardW * .06f - columnWidth / 2f
                    textPaint.textAlign = Paint.Align.LEFT
                    val singleLineAdjustment =
                            if (titleVisible.xor(artistVisible)) dp(5f) else 0f
                    var lineY = cy - cardH * .08f + singleLineAdjustment - dp(6f)
                    if (artistVisible) {
                        val artistLabel = if (isPlayingShown()) displayArtist().uppercase()
                        else context.getString(R.string.preview_playback_stopped).uppercase()
                        drawArtistLine(
                                canvas, artistLabel, columnLeft, lineY, columnWidth,
                                0xB3FFFFFF.toInt(), dp(8f), align = Paint.Align.LEFT,
                                sourceGlyph = true, glyphSize = dp(10f))
                        lineY += dp(12f)
                    }
                    if (titleVisible) {
                        textPaint.typeface = Typeface.create(titleTypeface(bold = true), Typeface.ITALIC)
                        textPaint.textSize = dp(21f)
                        textPaint.color = Color.WHITE
                        canvas.drawText(ellipsize(displayTitle(), columnWidth), columnLeft,
                                lineY + dp(14f), textPaint)
                    }
                    textPaint.textAlign = Paint.Align.CENTER
                }

                if (controlsVisible) {
                    val buttonR = radius * (if (miniConfigured) .19f else .22f)
                    val buttonX = if (hasMetadata) cx + cardW * .29f else cx
                    val buttonY = if (hasMetadata) cy + cardH * .30f else cy
                    fillPaint.color = 0x7A000000
                    canvas.drawCircle(buttonX, buttonY, buttonR, fillPaint)
                    strokePaint.strokeWidth = dp(2f)
                    strokePaint.shader = android.graphics.SweepGradient(buttonX, buttonY,
                            intArrayOf(primary, tertiary, secondary, primary), null)
                    canvas.drawCircle(buttonX, buttonY, buttonR, strokePaint)
                    strokePaint.shader = null
                    fillPaint.color = 0x1AFFFFFF
                    canvas.drawCircle(buttonX, buttonY, buttonR * .76f, fillPaint)
                    playGlyph(buttonX, buttonY, buttonR * .68f)
                }
                if (internalProgressVisible) {
                    val progressX = if (hasMetadata) cx - cardW * .15f else cx
                    val progressY = if (hasMetadata) cy + cardH * .31f else cy + cardH * .26f
                    val progressW = cardW * (if (hasMetadata) .43f else .56f)
                    canvas.save()
                    canvas.translate(progressX - cx, 0f)
                    progressLine(progressY, progressW)
                    canvas.restore()
                }
                if (trackTimeVisible()) {
                    textPaint.color = 0xA8FFFFFF.toInt(); textPaint.textSize = dp(9f)
                    drawTrackTimeText(
                            canvas, timeText(), cx,
                            clampTimeY(cy + radius * .78f, miniConfigured, radius, cy, dp),
                            dp(9f), 0xA8FFFFFF.toInt(), textPaint.typeface)
                }
            }
            "eclipse" -> {
                if (internalProgressVisible) {
                    val outer = radius - dp(14f)
                    val arcRect = RectF(cx - outer, cy - outer, cx + outer, cy + outer)
                    strokePaint.strokeWidth = dp(3f); strokePaint.strokeCap = Paint.Cap.ROUND
                    strokePaint.color = ColorUtils.setAlphaComponent(primary, 48)
                    canvas.drawArc(arcRect, 145f, 250f, false, strokePaint)
                    strokePaint.shader = null
                    strokePaint.color = progressColor
                    canvas.drawArc(arcRect, 145f, progressFraction() * 250f, false, strokePaint)
                    strokePaint.shader = null
                }
                if (showCompactHeader) {
                    header(
                            cy - radius + dp(if (hasMiniButtons) 43f else 49f),
                            compact = hasMiniButtons,
                            showArtist = !hasMiniButtons
                    )
                }
                val coreR = dp(if (hasMiniButtons) 26f else 28f)
                val coreY = cy
                fillPaint.color = Color.BLACK; canvas.drawCircle(cx, coreY, coreR, fillPaint)
                strokePaint.strokeWidth = dp(2f); strokePaint.shader = android.graphics.SweepGradient(
                        cx, coreY, intArrayOf(primary, secondary, tertiary, primary), null)
                canvas.drawCircle(cx, coreY, coreR, strokePaint); strokePaint.shader = null
                fillPaint.color = ColorUtils.setAlphaComponent(surfaceColor, 105)
                canvas.drawCircle(cx, coreY, coreR * .72f, fillPaint)
                playGlyph(cx, coreY, dp(17f))
                if (trackTimeVisible()) {
                    textPaint.color = 0xA8FFFFFF.toInt(); textPaint.textSize = dp(9f)
                    drawTrackTimeText(
                            canvas, timeText(), cx,
                            clampTimeY(coreY + coreR + dp(17f), miniConfigured, radius, cy, dp),
                            dp(9f), 0xA8FFFFFF.toInt(), textPaint.typeface)
                }
            }
            "spectrum" -> {
                if (showCompactHeader) {
                    val singleLineAdjustment = if (titleVisible.xor(artistVisible)) dp(5f) else 0f
                    val spectrumTitleY = cy - radius + dp(43f) + singleLineAdjustment
                    var spectrumArtistY = spectrumTitleY
                    if (titleVisible) {
                        // SpectrumFace's title is an AdaptiveTitleText designed at .92 alpha; it
                        // was the one curated face still truncating instead of following the mode.
                        val titleWidth = radius * 1.36f
                        val plan = planTitle(
                                titleWidth, dp(12f), dp(12f) * .62f, displayTitle().uppercase())
                        val blockHeight = drawTitlePlan(
                                canvas, plan, cx, spectrumTitleY, titleWidth,
                                titleAlpha(0xEAFFFFFF.toInt()))
                        spectrumArtistY = spectrumTitleY + dp(12f) +
                                blockHeight / plan.lines.size * (plan.lines.size - 1)
                    }
                    if (!hasMiniButtons && artistVisible) {
                        drawArtistLine(
                                canvas,
                                if (isPlayingShown()) displayArtist()
                                else context.getString(R.string.preview_playback_stopped),
                                cx, spectrumArtistY, radius * 1.35f,
                                ColorUtils.setAlphaComponent(artistTextColor(), 199),
                                dp(9f),
                                sourceGlyph = true, glyphSize = dp(11f))
                    }
                }
                val groupW = dp(WATCH_DP * .80f)
                val playD = dp(54f)
                val groupGap = dp(8f)
                val fieldW = maxOf(dp(72f), groupW - playD - groupGap)
                val fieldH = dp(if (hasMiniButtons) 52f else 65f)
                val fieldY = if (metadataVisible) cy + dp(10f) else cy
                val playX = cx - groupW / 2f + playD / 2f
                val fieldX = cx + groupW / 2f - fieldW / 2f
                fillPaint.color = ColorUtils.setAlphaComponent(surfaceColor, 71)
                canvas.drawCircle(playX, fieldY, playD / 2f, fillPaint)
                strokePaint.shader = null
                strokePaint.strokeWidth = dp(1f)
                strokePaint.color = 0x4DFFFFFF
                canvas.drawCircle(playX, fieldY, playD / 2f, strokePaint)
                playGlyph(playX, fieldY, dp(21f))
                val bars = 15
                val barW = fieldW / (bars * 1.72f)
                val gap = (fieldW - bars * barW) / (bars - 1)
                val fraction = progressFraction()
                val spectrumTitle = if (themeProfile != null) {
                    context.getString(R.string.preview_sample_title)
                } else {
                    nowPlayingTitle ?: context.getString(R.string.preview_sample_title)
                }
                val spectrumArtist = if (themeProfile != null) {
                    context.getString(R.string.preview_sample_artist)
                } else {
                    nowPlayingArtist ?: context.getString(R.string.preview_sample_artist)
                }
                val spectrumDuration = if (themeProfile != null) {
                    192_000L
                } else {
                    liveDurationMs.takeIf { it > 0L } ?: 192_000L
                }
                val spectrumSeed = spectrumPreviewTrackSeed(
                        spectrumTitle, spectrumArtist, spectrumDuration)
                val spectrumPhase = if (themeProfile == null && isPlayingShown()) {
                    transientAnimationActive = true
                    (previewAnimationTimeMillis() % 120_000L) / 1_000f
                } else {
                    0f
                }
                repeat(bars) { index ->
                    val barProgress = index / (bars - 1f)
                    val wave = spectrumPreviewBarHeight(spectrumSeed, index, spectrumPhase)
                    val h = fieldH * wave
                    fillPaint.shader = null
                    fillPaint.color = if (internalProgressVisible && barProgress <= fraction) {
                        progressColor
                    } else {
                        0x29FFFFFF
                    }
                    val left = fieldX - fieldW / 2f + index * (barW + gap)
                    canvas.drawRoundRect(left, fieldY - h / 2f, left + barW, fieldY + h / 2f,
                            barW / 2f, barW / 2f, fillPaint)
                }
                fillPaint.shader = null
                if (trackTimeVisible()) {
                    textPaint.color = 0xA8FFFFFF.toInt(); textPaint.textSize = dp(9f)
                    drawTrackTimeText(
                            canvas, timeText(), fieldX,
                            clampTimeY(fieldY + fieldH / 2f + dp(17f), miniConfigured, radius, cy, dp),
                            dp(9f), 0xA8FFFFFF.toInt(), textPaint.typeface)
                }
            }
            "material" -> {
                header(cy - radius + dp(51f), compact = false, showArtist = true)

                val centerCircleR = radius * 0.30f
                val skipIconSize = dp(30f)
                val skipOffset = radius * 0.54f
                val prevX = cx - skipOffset
                val nextX = cx + skipOffset

                if (controlsVisible) {
                    drawActionIcon(
                            canvas,
                            quadrantIcons[ScreenQuadrant.LEFT],
                            commonR.drawable.action_skip_prev,
                            prevX,
                            cy,
                            skipIconSize * theme.iconScale,
                            Color.WHITE,
                            iconAlpha
                    )
                }

                val strokeW = dp(4.5f)
                strokePaint.strokeWidth = strokeW
                strokePaint.strokeCap = Paint.Cap.ROUND
                strokePaint.shader = null
                val progressRect = RectF(
                        cx - centerCircleR,
                        cy - centerCircleR,
                        cx + centerCircleR,
                        cy + centerCircleR
                )

                strokePaint.color = 0x2DFFFFFF
                canvas.drawArc(progressRect, -90f, 360f, false, strokePaint)

                strokePaint.color = progressColor
                val sweep = progressFraction() * 360f
                canvas.drawArc(progressRect, -90f, sweep, false, strokePaint)

                if (controlsVisible) {
                    fillPaint.shader = null
                    fillPaint.color = 0x19FFFFFF
                    val innerR = centerCircleR - strokeW
                    canvas.drawCircle(cx, cy, innerR, fillPaint)

                    playGlyph(
                            cx + if (isPlayingShown()) 0f else dp(1f),
                            cy,
                            dp(if (isPlayingShown()) 34f else 38f)
                    )
                }

                if (controlsVisible) {
                    drawActionIcon(
                            canvas,
                            quadrantIcons[ScreenQuadrant.RIGHT],
                            commonR.drawable.action_skip_next,
                            nextX,
                            cy,
                            skipIconSize * theme.iconScale,
                            Color.WHITE,
                            iconAlpha
                    )
                }

                if (trackTimeVisible()) {
                    textPaint.typeface = fontRegular
                    textPaint.color = 0xB3FFFFFF.toInt()
                    textPaint.textSize = dp(10f)
                    // Same preferred baseline and mini-button clearance used by Expressive.
                    drawTrackTimeText(
                            canvas,
                            timeText(),
                            cx,
                            centeredTransportTimeY(cy + dp(45f), miniConfigured, radius, cy, dp),
                            dp(10f),
                            0xB3FFFFFF.toInt(),
                            textPaint.typeface)
                }
            }
        }

        if (alwaysShowTime || clockPreviewForced()) drawFaceClock(canvas, cx, cy - radius, dp)
        drawBottomChrome(canvas, cx, cy, radius, dp)
    }

    private fun tunedPreviewColor(color: Int, lightness: Float, saturation: Float): Int =
            PaletteTransforms.tunedFaceColor(color, lightness, saturation)

    private fun spectrumPreviewTrackSeed(title: String, artist: String, durationMs: Long): Int {
        var result = 17
        result = 31 * result + title.hashCode()
        result = 31 * result + artist.hashCode()
        result = 31 * result + (durationMs xor (durationMs ushr 32)).toInt()
        return result
    }

    private fun spectrumPreviewBarHeight(
            seed: Int,
            index: Int,
            phaseSeconds: Float
    ): Float {
        val rest = spectrumPreviewUnitHash(seed, index, 0x2C1B3C6D)
        val offset = spectrumPreviewUnitHash(seed, index, 0x6A09E667) *
                (Math.PI * 2.0).toFloat()
        val speed = .75f + spectrumPreviewUnitHash(seed, index, 0x3C6EF372) * 1.35f
        val pulse = .5f + .5f * sin((offset + phaseSeconds * speed).toDouble()).toFloat()
        return (.20f + rest * .20f + pulse * (.42f + (1f - rest) * .16f))
                .coerceIn(.20f, .94f)
    }

    private fun spectrumPreviewUnitHash(seed: Int, index: Int, salt: Int): Float {
        var value = seed xor salt xor (index * 0x45D9F3B)
        value = (value xor (value ushr 16)) * 0x45D9F3B
        value = (value xor (value ushr 16)) * 0x45D9F3B
        value = value xor (value ushr 16)
        return (value ushr 8) / 16_777_215f
    }
}
