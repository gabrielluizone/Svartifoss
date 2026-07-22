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
import com.svartifoss.snfell.common.ActivityVisibility
import com.svartifoss.snfell.common.AodArtTreatment
import com.svartifoss.snfell.common.FaceScopedPreferences
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
import com.svartifoss.snfell.common.SurfaceColorTreatment
import com.svartifoss.snfell.common.ThemeAppearance
import com.svartifoss.snfell.common.resolveAodArtwork
import com.svartifoss.snfell.common.R as commonR
import kotlin.math.asin
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

    private enum class PreviewSurface {
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

        const val TERMINAL_GREEN = 0xFF33FF66.toInt()
        const val MATERIAL_SURFACE = 0xFF2A2A2A.toInt()
        const val LIGHT_SURFACE = 0xFFECECEC.toInt()
        const val LIGHT_ON = 0xFF111111.toInt()
        const val MONO_SURFACE = 0xFF262626.toInt()
        const val MONO_ACTIVE = 0xFFE0E0E0.toInt()
        /** Flat neutral surface + tight corner of the "slab" quick-panel style. */
        const val SLAB_SURFACE = 0xFF1E1E20.toInt()
        const val SLAB_CORNER_DP = 10f

        const val COOKIE_LOBES = 10
        const val COOKIE_SOFTNESS = 0.55f
        const val COOKIE_MODULATION = 0.05f
        const val RING_MODULATION = 0.03f
        const val RING_GAP_DEGREES = 7f
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
    private val fontLoveLetter: Typeface? = ResourcesCompat.getFont(context, R.font.love_letter_typewriter)
    private val fontPoppins: Typeface? = ResourcesCompat.getFont(context, R.font.poppins_regular)
    private val fontMontserrat: Typeface? = ResourcesCompat.getFont(context, R.font.montserrat_regular)
    private val fontMarcellus: Typeface? = ResourcesCompat.getFont(context, R.font.marcellus_regular)

    private var sampleArt: Bitmap? = null
    private var sampleArtBlurred: Bitmap? = null
    private var sampleOverlayArtBlurred: Bitmap? = null
    private var sampleAlternateArt: Bitmap? = null
    private var sampleAlternateArtBlurred: Bitmap? = null

    // --- Live now-playing data (see setNowPlaying()/setPlayback()) ---
    private var nowPlayingSource: Bitmap? = null
    private var nowPlayingTitle: String? = null
    private var nowPlayingArtist: String? = null
    private var liveArt: Bitmap? = null
    private var liveArtBlurred: Bitmap? = null
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
    private var internalProgressVisible = true
    private var edgeProgressVisible = true
    private var edgeSeekEnabled = true
    private var screenTheme = "default"
    private var wearFontKey = "google_sans"
    private var artStyle = "cover"
    private var dimArt = true
    private var dimStrength = 80
    private var playerShadingStyle = PlayerShadingStyle.FOLLOW
    private var playerShadingIntensity = PlayerShadingIntensity.BALANCED.multiplier
    private var shadingColorMode = "black"
    private var shadingCustomColor = ""
    private var colorTreatment = "expressive"
    private var normalColor = ""
    private var artistMode = "follow"
    private var artistCustom = ""
    private var artistDesaturated = false
    private var progressMode = "follow"
    private var progressCustom = ""
    private var progressDesaturated = false
    private var volumeColorMode = "follow"
    private var volumeCustomColor = ""
    private var quickPanelColorMode = "follow"
    private var quickPanelCustomColor = ""
    private var progressStyle = "solid"
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

    /** Mirrors wear's WatchTheme.LAIN_KEYWORDS - mobile cannot depend on wear (see CLAUDE.md), so
     *  this tiny keyword set is deliberately duplicated, same as the geometry/color constants
     *  elsewhere in this class. Keep in sync with WatchTheme.kt's LAIN_KEYWORDS. */
    private val previewLainKeywords = setOf("iwakura", "lain", "wired", "breakcore", "serial experiments")

    private fun previewLainTriggered(): Boolean {
        val title = displayTitle().lowercase()
        val artist = (if (isPlayingShown()) displayArtist() else "").lowercase()
        return previewLainKeywords.any { title.contains(it) || artist.contains(it) }
    }

    /** Base typeface for the user's [wearFontKey] choice, or null for Google Sans (the preloaded
     *  fontBold/fontRegular already cover that case). The Lain keyword easter egg overrides the
     *  preference, mirroring watchFontTypeface/NowPlayingFaceState.titleFont on the watch. */
    private fun titleFontBase(): Typeface? {
        if (previewLainTriggered()) return fontLoveLetter
        return when (wearFontKey) {
            "roboto" -> Typeface.DEFAULT
            "typewriter" -> fontMomsTypewriter
            "love_letter" -> fontLoveLetter
            "poppins" -> fontPoppins
            "montserrat" -> fontMontserrat
            "marcellus" -> fontMarcellus
            "rounded" -> Typeface.create("sans-serif-rounded", Typeface.NORMAL)
            "sans_light" -> Typeface.create("sans-serif-light", Typeface.NORMAL)
            "sans_thin" -> Typeface.create("sans-serif-thin", Typeface.NORMAL)
            "sans_medium" -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
            "sans_black" -> Typeface.create("sans-serif-black", Typeface.NORMAL)
            "small_caps" -> Typeface.create("sans-serif-smallcaps", Typeface.NORMAL)
            "casual" -> Typeface.create("casual", Typeface.NORMAL)
            "serif" -> Typeface.SERIF
            "serif_monospace" -> Typeface.create("serif-monospace", Typeface.NORMAL)
            "monospace" -> Typeface.MONOSPACE
            "cursive" -> Typeface.create("cursive", Typeface.NORMAL)
            "condensed" -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
            "condensed_light" -> Typeface.create("sans-serif-condensed-light", Typeface.NORMAL)
            "condensed_medium" -> Typeface.create("sans-serif-condensed-medium", Typeface.NORMAL)
            else -> null
        }
    }

    /** Title/artist typeface for the current font choice, matching the bold/regular Google Sans
     *  variants this class already preloads. Use at every title/artist draw site instead of the
     *  raw fontBold/fontRegular fields; leave chrome text (readouts, labels) on Google Sans. */
    private fun titleTypeface(bold: Boolean): Typeface? {
        val base = titleFontBase() ?: return if (bold) fontBold else fontRegular
        return Typeface.create(base, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    init {
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
        key == "wear_seek_style" || key == "wear_seek_layout" -> PreviewSurface.SEEK
        key == "wear_quick_panel_style" || key == "wear_quick_panel_layout" ||
                key == "wear_quick_panel_source" || key == "wear_quick_panel_color_mode" ||
                key == "wear_quick_panel_custom_color" || key == "wear_up_next_pill_style" ->
            PreviewSurface.QUICK_PANEL
        key == "wear_queue_style" || key == "wear_list_row_size" -> PreviewSurface.QUEUE
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
        expressiveSeekMode = readString("wear_expressive_seek_mode", "central")
        playerControlsVisible = readBoolean("wear_classic_icons_visible", true)
        showTrackTitle = readBoolean("wear_show_track_title", true)
        showTrackArtist = readBoolean("wear_show_track_artist", true)
        internalProgressVisible = readBoolean("wear_internal_progress_visible", true)
        edgeProgressVisible = readBoolean("wear_edge_progress_visible", true)
        edgeSeekEnabled = readBoolean("wear_edge_seek_enabled", true)
        screenTheme = readString("wear_screen_theme", "default")
        wearFontKey = readString("wear_font", "google_sans")
        artStyle = readString("album_art_style", "cover")
        albumBlurRadius = readInt("album_art_blur_radius", 35).coerceIn(5, 120)
        dimArt = readBoolean("dim_album_art", true)
        dimStrength = readInt("album_art_dim_strength", 80).coerceIn(0, SHADING_MAX_PERCENT)
        playerShadingStyle = PlayerShadingStyle.fromPreference(
                readString("wear_player_shading_style", "follow"))
        // Mirror the watch: numeric percentage is the live source; a legacy named level migrates
        // in only when the user never touched the numeric slider (see resolveShadingMultiplier).
        val hasNumericShading = candidateFor("album_art_dim_strength") != null ||
                prefs.contains(effectiveKey("album_art_dim_strength"))
        val hasNamedShading = candidateFor("wear_player_shading_intensity") != null ||
                prefs.contains(effectiveKey("wear_player_shading_intensity"))
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
        colorTreatment = readString("wear_color_treatment", "expressive")
        normalColor = readString("wear_normal_color", "")
        artistMode = readString("wear_artist_color_mode", "follow")
        artistCustom = readString("wear_artist_custom_color", "")
        artistDesaturated = readBoolean("wear_artist_desaturated", false)
        progressMode = readString("wear_progress_color_mode", "follow")
        progressCustom = readString("wear_progress_custom_color", "")
        progressDesaturated = readBoolean("wear_progress_desaturated", false)
        volumeColorMode = readString("wear_volume_color_mode", "follow")
        volumeCustomColor = readString("wear_volume_custom_color", "")
        quickPanelColorMode = readString("wear_quick_panel_color_mode", "follow")
        quickPanelCustomColor = readString("wear_quick_panel_custom_color", "")
        progressStyle = readString("wear_progress_style", "solid")
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
        invalidate()
    }

    /**
     * Feeds the preview the phone's actual current track (album art, title, artist from the
     * active media session) so it mirrors what the watch is really showing right now. Any null
     * piece falls back to the built-in sample. Passing the same art instance again is cheap -
     * scaling and palette extraction only rerun when the bitmap changes.
     */
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
        return livePlaying ?: true
    }

    private fun progressFraction(): Float = if (
            (surface == PreviewSurface.PLAYER ||
                    surface == PreviewSurface.MINI_BUTTONS ||
                    surface == PreviewSurface.AOD) && liveDurationMs > 0
    ) {
        (livePositionMs.toFloat() / liveDurationMs).coerceIn(0f, 1f)
    } else {
        SAMPLE_PROGRESS
    }

    private fun timeText(): String = if (liveDurationMs > 0) {
        "${formatTime(livePositionMs.coerceAtLeast(0))} / ${formatTime(liveDurationMs)}"
    } else {
        "1:07 / 3:12"
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
        liveArtBlurred = blurApproximation(liveArt!!, albumBlurRadius)
        liveOverlayArtBlurred = blurApproximation(liveArt!!, overlayBlurRadius)
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

    /** Same swatch priority the watch uses to pick its dynamic accent from the album art.
     *  Synchronous rather than [Palette.generate]'s async callback: this preview thumbnail's
     *  art is already small (see [rebuildLiveArt]'s centerCrop, and Palette's own internal
     *  downsampling), so the extraction is fast enough not to jank - and doing it inline avoids
     *  a visible flash of the default/static accent before the real one lands a frame later. */
    private fun extractLiveAccent(art: Bitmap?) {
        liveAccent = null
        liveSecondaryAccent = null
        liveTertiaryAccent = null
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
            val primary = preferredColors.firstOrNull() ?: return@let
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

    /** Appearance keys are stored scoped per face ("<baseKey>@<face>"). Resolves the key actually
     *  present for the current [face]: the scoped entry if set, else the scoped key itself when a
     *  per-face default exists (so [scopedDefault] below wins over any pre-existing *global*
     *  legacy value - see FaceScopedPreferences.getString for why), else the legacy global entry,
     *  else the scoped key again (both absent -> the supplied default is used). Non-scoped keys
     *  pass through untouched. */
    private fun effectiveKey(baseKey: String): String {
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

    private fun readString(key: String, default: String): String {
        candidateFor(key)?.let { return it.toString() }
        val resolvedDefault = scopedDefault(key, default)
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
        val readKey = effectiveKey(key)
        return try {
            prefs.getBoolean(readKey, default)
        } catch (ignored: ClassCastException) {
            prefs.all[readKey]?.toString()?.toBooleanStrictOrNull() ?: default
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
        val readKey = effectiveKey(key)
        return try {
            prefs.getInt(readKey, default)
        } catch (ignored: ClassCastException) {
            prefs.getString(readKey, null)?.toIntOrNull() ?: default
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

    /** Multi-pass filtered blur for the preview. Every pass retains at least half of the source
     *  resolution, avoiding the 8-32px mosaic produced by the old single aggressive downscale.
     *  The result is cached; this work never runs while the user is dragging on the watch. */
    private fun blurApproximation(source: Bitmap, radius: Int): Bitmap {
        // Same downscale curve as the watch's software blur so a given radius blurs to the same
        // strength in the preview as on an older watch (this used to be a much weaker 0.08 slope).
        val scale = PaletteTransforms.legacyBlurScale(radius.toFloat())
        val targetWidth = (source.width * scale).toInt().coerceAtLeast(96).coerceAtMost(source.width)
        val targetHeight = (source.height * scale).toInt().coerceAtLeast(96).coerceAtMost(source.height)
        val passes = (2 + radius / 18).coerceIn(2, 8)
        var current = source
        repeat(passes) {
            val down = Bitmap.createScaledBitmap(current, targetWidth, targetHeight, true)
            val up = Bitmap.createScaledBitmap(down, source.width, source.height, true)
            if (current !== source) current.recycle()
            if (down !== current && down !== up) down.recycle()
            current = up
        }
        return current
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
        sampleArtBlurred = sampleArt?.let { blurApproximation(it, albumBlurRadius) }
        sampleAlternateArtBlurred = sampleAlternateArt?.let { blurApproximation(it, albumBlurRadius) }
        sampleOverlayArtBlurred = sampleArt?.let { blurApproximation(it, overlayRadius) }
        liveArtBlurred = liveArt?.let { blurApproximation(it, albumBlurRadius) }
        liveOverlayArtBlurred = liveArt?.let { blurApproximation(it, overlayRadius) }
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

    private fun rawAlbumAccent(): Int = liveAccent ?: SAMPLE_ALBUM_ACCENT

    private fun albumAccent(): Int = when (colorTreatment) {
        "normal" -> parseHexOrNull(normalColor) ?: ACCENT_NEUTRAL
        "desaturated" -> PaletteTransforms.softenedAlbumAccent(rawAlbumAccent())
        else -> rawAlbumAccent()
    }

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

    /** Real secondary/tertiary cover swatches. A monochromatic live cover falls back to a
     * same-hue tone; the generated sample uses colours actually present in [buildSampleArt]. */
    private fun albumSecondaryAccent(): Int = when {
        // Normal is a single-hue-with-lightness-variant shape - unlike Expressive, it doesn't
        // use the album's actually-distinct secondary swatch.
        colorTreatment == "normal" -> sameHueTone(albumAccent(), .42f)
        colorTreatment == "desaturated" -> PaletteTransforms.softenedAlbumAccent(
                if (liveAccent != null) {
                    liveSecondaryAccent ?: sameHueTone(liveAccent!!, .42f)
                } else {
                    SAMPLE_ALBUM_SECONDARY
                })
        liveAccent != null -> liveSecondaryAccent ?: sameHueTone(liveAccent!!, .42f)
        else -> SAMPLE_ALBUM_SECONDARY
    }

    private fun albumTertiaryAccent(): Int = when {
        colorTreatment == "normal" -> sameHueTone(albumAccent(), .68f)
        colorTreatment == "desaturated" -> PaletteTransforms.softenedAlbumAccent(
                if (liveAccent != null) {
                    liveTertiaryAccent ?: sameHueTone(liveAccent!!, .68f)
                } else {
                    SAMPLE_ALBUM_TERTIARY
                })
        liveAccent != null -> liveTertiaryAccent ?: sameHueTone(liveAccent!!, .68f)
        else -> SAMPLE_ALBUM_TERTIARY
    }

    private fun sameHueTone(color: Int, lightness: Float): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[1] = hsl[1].coerceIn(.25f, .82f)
        hsl[2] = lightness
        return ColorUtils.HSLToColor(hsl)
    }

    private fun displayTitle(): String {
        if (!showTrackTitle) return ""
        val title = nowPlayingTitle ?: context.getString(R.string.preview_sample_title)
        return if (focusedPreference == "wear_title_text_mode") {
            "$title · $title · $title"
        } else {
            title
        }
    }

    private fun displayArtist(): String = if (showTrackArtist) {
        nowPlayingArtist ?: context.getString(R.string.preview_sample_artist)
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
    private fun drawMarqueeText(canvas: Canvas, text: String, cx: Float, baseline: Float, availWidth: Float) {
        val textWidth = textPaint.measureText(text)
        val gap = availWidth * 0.4f
        val period = textWidth + gap
        val speedPxPerSecond = availWidth / 4f
        val phase = (SystemClock.uptimeMillis() % 3_600_000L) / 1000f * speedPxPerSecond % period

        val left = cx - availWidth / 2f
        canvas.save()
        canvas.clipRect(left, baseline - textPaint.textSize * 1.2f,
                left + availWidth, baseline + textPaint.textSize * 0.45f)
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(text, left - phase, baseline, textPaint)
        canvas.drawText(text, left - phase + period, baseline, textPaint)
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
     * Resolves how a title renders per the synced WEAR_TITLE_TEXT_MODE - "smart" (shrink, then
     * wrap, then scroll), "marquee", "wrap"/"wrap3"/"wrap5" (up to two/three/five lines) or
     * "shrink" - mirroring the watch's AdaptiveTitleText (used by every face, not just Classic's
     * own OutlineTextView), without drawing so the caller can lay out the whole text block first.
     * [textOverride] lets curated faces pass their own case-transformed (e.g. uppercased) copy of
     * [displayTitle] instead of the plain version.
     */
    private fun planClassicTitle(
            availWidth: Float,
            maxSize: Float,
            floorSize: Float,
            wrapFloor: Float,
            textOverride: String? = null
    ): TitlePlan {
        val text = textOverride ?: displayTitle()
        textPaint.typeface = titleTypeface(bold = true)

        fun wrapPlan(size: Float, maxLines: Int): TitlePlan {
            textPaint.textSize = size
            val rawLines = splitLines(text, availWidth, maxLines)
            if (rawLines.size <= 1) return TitlePlan(size, rawLines, false)
            val lines = rawLines.dropLast(1) + ellipsize(rawLines.last(), availWidth)
            return TitlePlan(size, lines, false)
        }

        val wrapLines = when (titleTextMode) {
            "static" -> 1
            "wrap" -> 2
            "wrap3" -> 3
            "wrap5" -> 5
            else -> null
        }
        return when {
            titleTextMode == "marquee" -> TitlePlan(maxSize, listOf(text), marquee = true)
            wrapLines != null -> {
                textPaint.textSize = maxSize
                if (textPaint.measureText(text) <= availWidth) TitlePlan(maxSize, listOf(text), false)
                else wrapPlan(
                        findFittingWrapSize(text, availWidth, maxSize, wrapFloor, wrapLines) ?: wrapFloor,
                        wrapLines
                )
            }
            titleTextMode == "shrink" -> {
                val fitted = findFittingTextSize(text, availWidth, maxSize, floorSize) ?: floorSize
                textPaint.textSize = fitted
                TitlePlan(fitted, listOf(ellipsize(text, availWidth)), false)
            }
            else -> {
                // Smart: fit on one line (shrinking to the floor) first; else wrap to two lines
                // at the largest size that fits both; else scroll.
                val fitted = findFittingTextSize(text, availWidth, maxSize, floorSize)
                when {
                    fitted != null -> TitlePlan(fitted, listOf(text), false)
                    else -> findFittingWrapSize(text, availWidth, maxSize, wrapFloor, 2)
                            ?.let { wrapPlan(it, 2) }
                            ?: TitlePlan(maxSize, listOf(text), marquee = true)
                }
            }
        }
    }

    /** Draws a title at [baselineY] honoring the synced text mode via [planClassicTitle] - the
     *  shared entry point every curated/expressive title block uses, so the preview matches the
     *  watch's AdaptiveTitleText (marquee/wrap/shrink/smart) instead of each face only ever
     *  shrinking-or-scrolling with no wrap tier. [color] is applied after the call, so callers
     *  don't need to juggle textPaint state themselves. Returns the total height of every line
     *  drawn, so the caller can push whatever follows (e.g. the artist line) down accordingly. */
    private fun drawAdaptiveTitle(
            canvas: Canvas,
            cx: Float,
            baselineY: Float,
            availWidth: Float,
            maxSize: Float,
            color: Int,
            textOverride: String? = null
    ): Float {
        val floorSize = maxSize * 0.62f
        val plan = planClassicTitle(availWidth, maxSize, floorSize, floorSize, textOverride)
        textPaint.textSize = plan.size
        textPaint.color = color
        val fm = textPaint.fontMetrics
        val lineH = fm.descent - fm.ascent
        if (plan.marquee) {
            drawMarqueeText(canvas, plan.lines.first(), cx, baselineY, availWidth)
        } else {
            plan.lines.forEachIndexed { index, line ->
                canvas.drawText(line, cx, baselineY + index * lineH, textPaint)
            }
        }
        return lineH * plan.lines.size
    }

    private fun resolvedTreatment(mode: String, legacyDesaturated: Boolean): SurfaceColorTreatment {
        val global = SurfaceColorTreatment.fromPreference(
                colorTreatment, default = SurfaceColorTreatment.EXPRESSIVE)
        return SurfaceColorTreatment.fromPreference(mode, legacyDesaturated).resolveAgainst(global)
    }

    private fun resolveTint(mode: String, custom: String, legacyDesaturated: Boolean): Int =
            when (resolvedTreatment(mode, legacyDesaturated)) {
                SurfaceColorTreatment.NORMAL ->
                    (if (SurfaceColorTreatment.fromPreference(mode, legacyDesaturated) ==
                                    SurfaceColorTreatment.FOLLOW) null else parseHexOrNull(custom))
                        ?: parseHexOrNull(normalColor)
                        ?: ACCENT_NEUTRAL
                SurfaceColorTreatment.DESATURATED ->
                    PaletteTransforms.softenedAlbumAccent(rawAlbumAccent())
                SurfaceColorTreatment.EXPRESSIVE -> rawAlbumAccent()
                SurfaceColorTreatment.FOLLOW -> albumAccent() // resolvedAgainst prevents this.
            }

    private fun volumeAccent(): Int =
            resolveTint(volumeColorMode, volumeCustomColor, legacyDesaturated = false)

    private fun quickPanelAccent(): Int =
            resolveTint(quickPanelColorMode, quickPanelCustomColor, legacyDesaturated = false)

    private fun resolveCompanionTint(
            mode: String,
            custom: String,
            legacyDesaturated: Boolean,
            rawCompanion: Int,
            normalLightness: Float
    ): Int = when (resolvedTreatment(mode, legacyDesaturated)) {
        SurfaceColorTreatment.NORMAL -> sameHueTone(
                (if (SurfaceColorTreatment.fromPreference(mode, legacyDesaturated) ==
                                SurfaceColorTreatment.FOLLOW) null else parseHexOrNull(custom))
                        ?: parseHexOrNull(normalColor)
                        ?: ACCENT_NEUTRAL,
                normalLightness)
        SurfaceColorTreatment.DESATURATED -> PaletteTransforms.softenedAlbumAccent(rawCompanion)
        SurfaceColorTreatment.EXPRESSIVE -> rawCompanion
        SurfaceColorTreatment.FOLLOW -> rawCompanion // resolvedAgainst normally prevents this.
    }

    private fun rawSecondaryAccent(): Int = if (liveAccent != null) {
        liveSecondaryAccent ?: sameHueTone(liveAccent!!, .42f)
    } else {
        SAMPLE_ALBUM_SECONDARY
    }

    private fun rawTertiaryAccent(): Int = if (liveAccent != null) {
        liveTertiaryAccent ?: sameHueTone(liveAccent!!, .68f)
    } else {
        SAMPLE_ALBUM_TERTIARY
    }

    /** The raw (untreated) album accent triple currently driving this preview, with the same
     *  sample-art fallback used when nothing's playing - exposed so the color-treatment picker's
     *  per-option swatches (see ColorTreatmentPreference) match this preview exactly instead of
     *  re-extracting their own, possibly different, palette. */
    fun currentAlbumAccents(): Triple<Int, Int, Int> =
            Triple(rawAlbumAccent(), rawSecondaryAccent(), rawTertiaryAccent())

    private fun volumeSecondaryAccent(): Int = resolveCompanionTint(
            volumeColorMode, volumeCustomColor, false, rawSecondaryAccent(), .42f)

    private fun volumeTertiaryAccent(): Int = resolveCompanionTint(
            volumeColorMode, volumeCustomColor, false, rawTertiaryAccent(), .68f)

    private fun quickPanelSecondaryAccent(): Int = resolveCompanionTint(
            quickPanelColorMode, quickPanelCustomColor, false, rawSecondaryAccent(), .42f)

    private fun quickPanelTertiaryAccent(): Int = resolveCompanionTint(
            quickPanelColorMode, quickPanelCustomColor, false, rawTertiaryAccent(), .68f)

    /** WatchTheme.accentForText: lift lightness so accents read on the dark screen. */
    private fun accentForText(color: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[2] = hsl[2].coerceAtLeast(0.62f)
        return ColorUtils.HSLToColor(hsl)
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
        drawPlayerBackdrop(canvas, geometry, dp)

        // Some controls are intentionally face-specific on the watch. While that preference is
        // focused, use the face that can actually demonstrate it; the normal player remains the
        // user's selected face everywhere else.
        val demonstratedFace = when (focusedPreference) {
            "wear_expressive_seek_mode" -> "expressive"
            "wear_title_text_mode" -> "classic"
            "wear_progress_style" -> face
            else -> face
        }
        when (demonstratedFace) {
            "expressive" -> drawExpressive(
                    canvas, geometry.cx, geometry.cy, geometry.radius, geometry.bounds, dp)
            "vinyl", "poster", "studio", "halo", "aurora", "eclipse", "spectrum", "material", "immersive" ->
                drawCuratedPlayer(canvas, geometry, dp, demonstratedFace)
            else -> {
                drawPlayerShading(canvas, geometry.bounds, geometry.cx, geometry.cy,
                        geometry.radius)
                drawClassic(canvas, geometry.cx, geometry.cy, geometry.radius, dp)
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
                drawArtwork(canvas, liveArtBlurred ?: sampleArtBlurred, geometry.bounds, 255, grayscale = false)
                // The true original source, not liveArt - that copy is already center-cropped to
                // a square for every other style's use, which would silently defeat the "never
                // crop" point of this one for any source that isn't already square.
                drawSquareInsetArtwork(
                        canvas, nowPlayingSource ?: sampleArt, geometry.bounds,
                        backgroundStyle.squareCornerRadiusFraction ?: 0.10f
                )
            } else {
                val art = if (blurred) {
                    liveArtBlurred ?: sampleArtBlurred
                } else {
                    liveArt ?: sampleArt
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
        val cycle = SystemClock.uptimeMillis() % (halfCycle * 2L)
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
                "expressive", "vinyl", "poster", "studio", "halo", "aurora", "eclipse", "spectrum", "material", "immersive"
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
        if (artwork.visible) {
            drawArtwork(
                    canvas,
                    if (artwork.blurred) {
                        liveArtBlurred ?: sampleArtBlurred
                    } else {
                        liveArt ?: sampleArt
                    },
                    geometry.bounds,
                    ambientArtOpacity * 255 / 100,
                    artwork.monochrome
            )
        }

        when (style) {
            "expressive" -> drawExpressiveAod(canvas, geometry, dp)
            "vinyl", "poster", "studio", "halo", "aurora", "eclipse", "spectrum", "material", "immersive" ->
                drawCuratedAod(canvas, geometry, dp)
            "chrono" -> drawChronoAod(canvas, geometry, dp)
            else -> drawClassicAod(canvas, geometry, dp, minimal = false)
        }

        // Chrono draws its own big clock; the small top clock is skipped for it.
        if (aodShowClock && style != "chrono") {
            drawAmbientClock(canvas, geometry.cx, geometry.bounds.top + dp(20f), dp)
        }
    }

    private fun drawAmbientClock(
            canvas: Canvas,
            x: Float,
            baseline: Float,
            dp: (Float) -> Float
    ) {
        val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
        val time = java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
                .format(java.util.Date())
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
            val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
            val time = java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
                    .format(java.util.Date())
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
            textPaint.typeface = titleTypeface(bold = false)
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
        textPaint.typeface = titleTypeface(bold = true)
        textPaint.textSize = dp(14f)
        drawAmbientOutlinedText(
                canvas,
                ellipsize(displayArtist(), geometry.radius * 1.55f),
                geometry.cx,
                geometry.cy - dp(17f),
                artist,
                dp
        )
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
        textPaint.typeface = titleTypeface(bold = false)
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
        val centeredMetadata = style in setOf("poster", "studio", "eclipse", "immersive")

        if (aodShowTrackInfo) {
            textPaint.typeface = fontBold
            textPaint.textSize = dp(17f)
            textPaint.color = ambientColor(tint, .82f)
            canvas.drawText(ellipsize(displayTitle(), radius * 1.25f), cx,
                    if (centeredMetadata) cy - dp(3f) else cy - radius + dp(54f), textPaint)
            textPaint.typeface = fontRegular
            textPaint.textSize = dp(10f)
            textPaint.color = ambientColor(tint, .48f)
            canvas.drawText(ellipsize(
                    if (isPlayingShown()) displayArtist()
                    else context.getString(R.string.preview_playback_stopped),
                    radius * 1.20f
            ), cx,
                    if (centeredMetadata) cy + dp(14f) else cy - radius + dp(68f), textPaint)
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

    private fun drawVinylAod(
            canvas: Canvas,
            geometry: PreviewGeometry,
            dp: (Float) -> Float
    ) {
        drawAmbientTrackInfo(canvas, geometry, dp)
        if (aodShowTransport) {
            configureAmbientStroke(dp)
            val recordRadius = dp(43f)
            val centerY = geometry.cy + dp(8f)
            canvas.drawCircle(geometry.cx, centerY, recordRadius, strokePaint)
            canvas.drawCircle(geometry.cx, centerY, recordRadius * 0.48f, strokePaint)
            canvas.drawCircle(geometry.cx, centerY, recordRadius * 0.12f, strokePaint)
            val sideOffset = dp(70f)
            val sideRadius = dp(14f)
            canvas.drawCircle(geometry.cx - sideOffset, centerY, sideRadius, strokePaint)
            canvas.drawCircle(geometry.cx + sideOffset, centerY, sideRadius, strokePaint)
            drawIcon(canvas, commonR.drawable.action_skip_prev, geometry.cx - sideOffset,
                    centerY, dp(13f), ambientColor(resolvedAodTint()))
            drawIcon(canvas, commonR.drawable.action_skip_next, geometry.cx + sideOffset,
                    centerY, dp(13f), ambientColor(resolvedAodTint()))
            if (aodShowProgress) {
                val rim = RectF(
                        geometry.cx - recordRadius - dp(7f),
                        centerY - recordRadius - dp(7f),
                        geometry.cx + recordRadius + dp(7f),
                        centerY + recordRadius + dp(7f)
                )
                strokePaint.strokeWidth = dp(2.5f)
                canvas.drawArc(rim, -90f, progressFraction() * 360f, false, strokePaint)
            }
        }
        drawAmbientBottomPills(canvas, geometry, dp, squared = false)
    }

    private fun drawPosterAod(
            canvas: Canvas,
            geometry: PreviewGeometry,
            dp: (Float) -> Float
    ) {
        drawAmbientTrackInfo(canvas, geometry, dp)
        if (aodShowTransport) {
            configureAmbientStroke(dp)
            val small = dp(36f)
            val big = dp(46f)
            val offset = dp(46f)
            val corner = dp(9f)
            canvas.drawRoundRect(geometry.cx - offset - small / 2f, geometry.cy - small / 2f,
                    geometry.cx - offset + small / 2f, geometry.cy + small / 2f,
                    corner, corner, strokePaint)
            canvas.drawRoundRect(geometry.cx - big / 2f, geometry.cy - big / 2f,
                    geometry.cx + big / 2f, geometry.cy + big / 2f,
                    corner, corner, strokePaint)
            canvas.drawRoundRect(geometry.cx + offset - small / 2f, geometry.cy - small / 2f,
                    geometry.cx + offset + small / 2f, geometry.cy + small / 2f,
                    corner, corner, strokePaint)
            drawIcon(canvas, commonR.drawable.action_skip_prev, geometry.cx - offset,
                    geometry.cy, dp(17f), ambientColor(resolvedAodTint()))
            drawIcon(canvas,
                    if (isPlayingShown()) commonR.drawable.action_pause else commonR.drawable.action_play,
                    geometry.cx, geometry.cy, dp(21f), ambientColor(resolvedAodTint()))
            drawIcon(canvas, commonR.drawable.action_skip_next, geometry.cx + offset,
                    geometry.cy, dp(15f), ambientColor(resolvedAodTint()))
        }
        // Poster keeps its independent straight progress bar even when transport is hidden.
        if (aodShowProgress) {
            configureAmbientStroke(dp)
            val width = dp(100f)
            val y = geometry.cy + dp(36f)
            strokePaint.strokeWidth = dp(2.4f)
            canvas.drawLine(geometry.cx - width / 2f, y,
                    geometry.cx - width / 2f + width * progressFraction(), y, strokePaint)
        }
        drawAmbientBottomPills(canvas, geometry, dp, squared = true)
    }

    private fun drawAmbientBottomPills(
            canvas: Canvas,
            geometry: PreviewGeometry,
            dp: (Float) -> Float,
            squared: Boolean
    ) {
        if (!aodShowPills || miniButtonIcons.isNotEmpty()) return
        configureAmbientStroke(dp, 1.3f)
        val width = geometry.radius * 2f * .235f
        val height = geometry.radius * 2f * .155f
        val centerY = geometry.bounds.bottom - height / 2f - geometry.radius * 2f * .032f
        val sideY = centerY - geometry.radius * 2f * .12f
        val offset = geometry.radius * 2f * .275f
        val corner = if (squared) dp(7f) else height
        for ((x, y) in listOf(
                geometry.cx - offset to sideY,
                geometry.cx to centerY,
                geometry.cx + offset to sideY
        )) {
            canvas.drawRoundRect(x - width / 2f, y - height / 2f,
                    x + width / 2f, y + height / 2f, corner, corner, strokePaint)
        }
        drawIcon(canvas, R.drawable.ic_playlist_play, geometry.cx - offset, sideY,
                dp(12f), ambientColor(resolvedAodTint()))
        drawIcon(canvas, commonR.drawable.action_volume_up, geometry.cx, centerY,
                dp(12f), ambientColor(resolvedAodTint()))
        fillPaint.color = ambientColor(resolvedAodTint())
        for (i in -1..1) {
            canvas.drawCircle(geometry.cx + offset, sideY + dp(i * 3f), dp(1f), fillPaint)
        }
    }

    // --- Contextual volume and seek overlays ---

    private fun seekBackdropStyle(): String = OverlayBackdropResolver.seekContentStyle(seekStyle)

    private fun drawOverlayBackdrop(
            canvas: Canvas,
            geometry: PreviewGeometry,
            style: String,
            forceBlur: Boolean = false
    ) {
        fillPaint.shader = null
        fillPaint.color = Color.BLACK
        canvas.drawRect(geometry.bounds, fillPaint)
        val accent = albumAccent()
        val secondary = albumSecondaryAccent()
        if (forceBlur || style == "glass" || style == "frost") {
            drawArtwork(
                    canvas,
                    liveOverlayArtBlurred ?: sampleOverlayArtBlurred,
                    geometry.bounds,
                    255
            )
        }
        fun backdropAlpha(normal: Int): Int = if (forceBlur) minOf(normal, 0xB8) else normal
        fun backdropColor(color: Int, normalAlpha: Int): Int =
                ColorUtils.setAlphaComponent(color, backdropAlpha(normalAlpha))
        when (style) {
            "frost" -> fillPaint.color = backdropColor(Color.WHITE, 0x38)
            "light" -> fillPaint.color = backdropColor(0xFFF2F2F2.toInt(), 0xE0)
            "material" -> fillPaint.color = backdropColor(0xFF2A2A2A.toInt(), 0xF2)
            "tonal", "ink" -> fillPaint.color =
                    backdropColor(tonal(accent, .22f, .25f, .72f), 0xF2)
            "gradient", "aurora" -> {
                fillPaint.shader = LinearGradient(
                        geometry.bounds.left,
                        geometry.bounds.top,
                        geometry.bounds.right,
                        geometry.bounds.bottom,
                        backdropColor(tonal(accent, .34f, .25f, .72f), 0xF5),
                        backdropColor(tonal(secondary, .15f, .25f, .72f), 0xFA),
                        Shader.TileMode.CLAMP
                )
                fillPaint.color = Color.WHITE
            }
            "duotone" -> fillPaint.color =
                    backdropColor(tonal(secondary, .22f, .25f, .72f), 0xF4)
            "terminal" -> fillPaint.color = backdropColor(0xFF001407.toInt(), 0xFA)
            "neon", "minimal", "contrast", "outline" ->
                fillPaint.color = backdropColor(Color.BLACK, 0xFF)
            "mono", "groove", "segments" ->
                fillPaint.color = backdropColor(0xFF1A1A1A.toInt(), 0xF5)
            else -> fillPaint.color = backdropColor(Color.BLACK, 0x50)
        }
        canvas.drawRect(geometry.bounds, fillPaint)
        fillPaint.shader = null
    }

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
                    liveOverlayArtBlurred ?: sampleOverlayArtBlurred,
                    geometry.bounds,
                    255
            )
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
            OverlayBackdrop.FOLLOW_STYLE -> fillPaint.color = Color.BLACK
        }
        canvas.drawRect(geometry.bounds, fillPaint)

        if (backdrop == OverlayBackdrop.PRISM) {
            strokePaint.shader = null
            strokePaint.pathEffect = null
            strokePaint.style = Paint.Style.STROKE
            strokePaint.strokeWidth = geometry.dpScale.coerceAtLeast(1f)
            strokePaint.color = 0x66FFFFFF
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
        if (volumeLayout == "meter") {
            drawIcon(canvas, commonR.drawable.action_volume_down,
                    geometry.cx - dp(72f), geometry.cy + dp(29f), dp(20f), chromeColor)
            drawIcon(canvas, commonR.drawable.action_volume_up,
                    geometry.cx + dp(72f), geometry.cy + dp(29f), dp(20f), chromeColor)
            canvas.save()
            canvas.translate(0f, -dp(16f))
        } else {
            drawIcon(canvas, commonR.drawable.action_volume_up, geometry.cx,
                    geometry.bounds.top + dp(21f), dp(20f), chromeColor)
            drawIcon(canvas, commonR.drawable.action_volume_down, geometry.cx,
                    geometry.bounds.bottom - dp(21f), dp(20f), chromeColor)
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
        if (volumeLayout == "meter") canvas.restore()
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
        // scales its stroke, everything else replaces it with the timeline meter.
        val edgeRing = seekLayout in setOf("edge", "edge_thin", "edge_thick")
        if (edgeRing) {
            drawEdgeSeekRing(canvas, geometry.cx, geometry.cy, geometry.radius, dp,
                    strokeScale = when (seekLayout) {
                        "edge_thin" -> 0.5f
                        "edge_thick" -> 1.8f
                        else -> 1f
                    })
        } else {
            drawPreviewSeekTimeline(canvas, geometry, dp, segmented = seekLayout == "segments")
            canvas.save()
            canvas.translate(0f, -dp(18f))
        }
        drawOverlayReadout(
                canvas, "1:07", seekStyle, geometry, dp, total = "3:12",
                accent = progressAccent)
        if (!edgeRing) canvas.restore()
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
        if (volumeLayout == "meter") {
            drawPreviewVolumeMeter(canvas, geometry, dp)
            return
        }
        val baseStroke = dp(6f)
        val maxStroke = baseStroke * 1.7f
        val inset = maxStroke / 2f
        val diameter = if (volumeLayout == "halo") {
            min(geometry.radius * 1.44f, dp(138f))
        } else {
            geometry.radius * 2f - inset * 2f
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
            else -> 130f
        }
        previewVolumeArcSweep = when (volumeLayout) {
            "halo" -> 270f
            "edge_tall" -> 160f
            "edge_right" -> -100f
            "edge_top" -> 70f
            "edge_bottom" -> -70f
            "ring" -> 360f
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
            else -> drawVolumeArcPass(canvas, bounds, baseStroke,
                    Paint.Cap.ROUND, 0x33FFFFFF, accent)
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

    private fun drawPreviewVolumeMeter(
            canvas: Canvas,
            geometry: PreviewGeometry,
            dp: (Float) -> Float
    ) {
        val width = min(geometry.bounds.width() * .68f, dp(132f))
        val height = dp(11f)
        val cy = geometry.cy + dp(29f)
        val bounds = RectF(
                geometry.cx - width / 2f, cy - height / 2f,
                geometry.cx + width / 2f, cy + height / 2f)
        val accent = volumeAccent()
        val fillColor = when (volumeStyle) {
            "mono" -> MONO_ACTIVE
            "contrast" -> Color.WHITE
            "terminal" -> TERMINAL_GREEN
            "tonal" -> tonal(accent, .72f, .25f, .60f)
            else -> accent
        }
        fillPaint.shader = null
        fillPaint.color = when (volumeStyle) {
            "light" -> 0x88CCCCCC.toInt()
            "terminal" -> ColorUtils.setAlphaComponent(TERMINAL_GREEN, 0x40)
            "duotone" -> tonal(volumeSecondaryAccent(), .30f, .25f, .60f)
            "tonal" -> tonal(accent, .22f, .25f, .60f)
            else -> 0x35FFFFFF
        }
        canvas.drawRoundRect(bounds, height / 2f, height / 2f, fillPaint)
        if (volumeStyle == "segments") {
            val count = 10
            val gap = dp(3f)
            val segmentWidth = (bounds.width() - gap * (count - 1)) / count
            repeat(count) { index ->
                val left = bounds.left + index * (segmentWidth + gap)
                fillPaint.color = if ((index + 1f) / count <= SAMPLE_VOLUME) {
                    fillColor
                } else {
                    ColorUtils.setAlphaComponent(fillColor, 0x28)
                }
                canvas.drawRoundRect(left, bounds.top, left + segmentWidth, bounds.bottom,
                        segmentWidth / 2f, segmentWidth / 2f, fillPaint)
            }
            return
        }
        val fill = RectF(bounds.left, bounds.top,
                bounds.left + bounds.width() * SAMPLE_VOLUME, bounds.bottom)
        fillPaint.shader = if (volumeStyle in setOf("gradient", "aurora", "prism")) {
            LinearGradient(bounds.left, cy, bounds.right, cy,
                    volumeTertiaryAccent(), volumeSecondaryAccent(), Shader.TileMode.CLAMP)
        } else {
            null
        }
        fillPaint.color = fillColor
        canvas.drawRoundRect(fill, height / 2f, height / 2f, fillPaint)
        fillPaint.shader = null
        if (volumeStyle in setOf("material", "glass", "tonal")) {
            fillPaint.color = Color.WHITE
            canvas.drawCircle(fill.right, cy, height * .36f, fillPaint)
        }
    }

    private fun drawPreviewSeekTimeline(
            canvas: Canvas,
            geometry: PreviewGeometry,
            dp: (Float) -> Float,
            segmented: Boolean
    ) {
        val width = min(geometry.bounds.width() * .68f, dp(132f))
        val height = dp(if (segmented) 9f else 7f)
        val cy = geometry.cy + dp(25f)
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

    // --- Contextual quick-panel and queue previews ---

    private data class PreviewSkin(
            val fill: Int = Color.TRANSPARENT,
            val onColor: Int = Color.WHITE,
            val cornerDp: Float = 20f,
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

        // The grid stands alone as a dense launcher and hero drops to a single line, mirroring
        // MainActivity.applyQuickPanelLayout on the watch.
        val showTrackTitle = showTrackTitle && quickPanelLayout != "grid"
        val renderArtist = showTrackArtist && quickPanelLayout != "grid" && quickPanelLayout != "hero"
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
            textPaint.typeface = fontBold
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
            textPaint.typeface = fontBold
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
                val iconSize = if (quickPanelLayout == "hero" && index == 0) dp(26f) else dp(20f)
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
            val rowY = geometry.cy + dp(70f) + controlsShift
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
            textPaint.typeface = fontBold
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
            textPaint.typeface = fontRegular
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
        val corner = if (quickPanelStyle == "terminal") 0f else if (quickPanelStyle == "slab") SLAB_CORNER_DP else 26f
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
        // Round slot buttons stay full-stadium; the full-width rows use the Menu screen's soft
        // 26dp rounded rectangle. Terminal keeps its authored square corners. Mirrors
        // quickPanelRowBackground() / inactiveQuickButtonBackground() on the watch.
        val corner = when {
            style == "terminal" -> 0f
            // Slab keeps one tight rectangle on both the slots and the rows.
            style == "slab" -> SLAB_CORNER_DP
            row -> 26f
            else -> 999f
        }

        if (active && !row && style == "prism") {
            return PreviewSkin(
                    onColor = Color.WHITE,
                    cornerDp = corner,
                    strokeColor = 0x66FFFFFF,
                    strokeDp = 1.5f,
                    gradientTop = tonal(tertiary, .34f, .25f, .60f),
                    gradientMiddle = tonal(accent, .42f, .25f, .60f),
                    gradientBottom = tonal(secondary, .28f, .25f, .60f)
            )
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
        drawSmallClock(canvas, geometry.cx, geometry.bounds.top + dp(20f), dp)

        textPaint.style = Paint.Style.FILL
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = fontBold
        textPaint.textSize = dp(13f)
        textPaint.color = Color.WHITE
        canvas.drawText(ellipsize(displayTitle(), geometry.radius * 1.45f),
                geometry.cx, geometry.bounds.top + dp(39f), textPaint)
        textPaint.typeface = fontRegular
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
                drawQueueCover(canvas, rect, dp(skin.cornerDp))
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
            textPaint.typeface = fontBold
            textPaint.textSize = dp(10.5f)
            textPaint.color = skin.onColor
            canvas.drawText(ellipsize(titles[index], rect.right - left - dp(8f)),
                    left, y - dp(1f), textPaint)
            textPaint.typeface = fontRegular
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
    private fun drawQueueCover(canvas: Canvas, rect: RectF, corner: Float) {
        val art = liveArt ?: sampleArt ?: return
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
        "minimal", "terminal" -> 2f
        "material", "tonal", "light", "gradient", "duotone", "prism", "frost" -> 7f
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
            // Mirrors QueueStyle.COVER: the Glass fill shows through where a row has no artwork,
            // white text over the scrim, and the now-playing row marked by an accent keyline
            // rather than a light pill that would fight the cover behind it.
            "cover" -> PreviewSkin(
                    fill = 0xFF1E1E20.toInt(),
                    onColor = Color.WHITE,
                    cornerDp = 26f,
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
            canvas.drawRoundRect(rect, corner, corner, fillPaint)
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
            canvas.drawRoundRect(outline, corner, corner, strokePaint)
        }
    }

    private fun accentForSurface(color: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[1] = hsl[1].coerceAtLeast(0.45f)
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
            progressColor: Int
    ) {
        val orbit = radius - baseStroke / 2f
        fillPaint.shader = null
        fillPaint.style = Paint.Style.FILL
        repeat(60) { index ->
            val angle = Math.toRadians((index * 6f - 90f).toDouble())
            val major = index % 5 == 0
            fillPaint.color = if (previewMarkIsPlayed(index, 60)) {
                progressColor
            } else {
                0x33FFFFFF
            }
            canvas.drawCircle(
                    cx + orbit * cos(angle).toFloat(),
                    cy + orbit * sin(angle).toFloat(),
                    baseStroke * if (major) 0.36f else 0.22f,
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
            emphasizedWidth: Float
    ) {
        val outerRadius = radius - baseStroke * 0.25f
        strokePaint.shader = null
        strokePaint.pathEffect = null
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeCap = Paint.Cap.ROUND
        repeat(count) { index ->
            val angle = Math.toRadians((index * 360f / count - 90f).toDouble())
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
            drawSkin(canvas, rect, skin, dp)
            val iconX = rect.left + dp(24f)
            drawActionIcon(canvas, action, fallbackIcon, iconX, y, dp(20f), skin.onColor)
            y += rowHeight + spacing
        }
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
        val baseStroke = dp(6f) * strokeScale
        val ringInset = baseStroke / 2f
        val ringRect = RectF(cx - radius + ringInset, cy - radius + ringInset,
                cx + radius - ringInset, cy + radius - ringInset)
        val sweep = progressFraction() * 360f

        when (progressStyle) {
            "watch_dots_60" -> {
                drawPreviewWatchDots(canvas, cx, cy, radius, baseStroke, progressColor)
                return
            }
            "watch_ticks_60" -> {
                drawPreviewWatchTicks(
                        canvas, cx, cy, radius, baseStroke, progressColor,
                        count = 60,
                        emphasizeEvery = 5,
                        normalLength = baseStroke * 0.58f,
                        emphasizedLength = baseStroke * 0.95f,
                        normalWidth = baseStroke * 0.17f,
                        emphasizedWidth = baseStroke * 0.29f)
                return
            }
            "hour_segments_12" -> {
                drawPreviewWatchTicks(
                        canvas, cx, cy, radius, baseStroke, progressColor,
                        count = 12,
                        emphasizeEvery = 1,
                        normalLength = baseStroke,
                        emphasizedLength = baseStroke,
                        normalWidth = baseStroke * 0.4f,
                        emphasizedWidth = baseStroke * 0.4f)
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
        }

        strokePaint.strokeWidth = trackWidth
        strokePaint.strokeCap = trackCap
        strokePaint.color = 0x33FFFFFF
        val patternedTrackStart = if (progressStyle == "dashed" || progressStyle == "dots") {
            -90f
        } else {
            0f
        }
        canvas.drawArc(ringRect, patternedTrackStart, 360f, false, strokePaint)

        strokePaint.strokeWidth = fillWidth
        strokePaint.strokeCap = fillCap
        strokePaint.color = progressColor
        if (progressStyle == "comet") {
            if (sweep > 1f) {
                val shader = SweepGradient(cx, cy,
                        intArrayOf(progressColor and 0x00FFFFFF, progressColor),
                        floatArrayOf(0f, (sweep / 360f).coerceAtMost(1f)))
                val rotate = Matrix()
                rotate.setRotate(-90f, cx, cy)
                shader.setLocalMatrix(rotate)
                strokePaint.shader = shader
                canvas.drawArc(ringRect, -90f, sweep, false, strokePaint)
                strokePaint.shader = null
            }
            val headRad = Math.toRadians((sweep - 90f).toDouble())
            val r = ringRect.width() / 2f
            fillPaint.color = progressColor
            canvas.drawCircle(cx + r * cos(headRad).toFloat(),
                    cy + r * sin(headRad).toFloat(), baseStroke * 0.5f, fillPaint)
        } else {
            canvas.drawArc(ringRect, -90f, sweep, false, strokePaint)
        }
        strokePaint.pathEffect = null
    }

    private fun drawClassic(canvas: Canvas, cx: Float, cy: Float, radius: Float, dp: (Float) -> Float) {
        val theme = screenThemeSpec()
        // The seek ring uses the raw resolved accent (like the watch's seekBar.progressColor);
        // only the artist text gets the accentForText lightness lift.
        val artistColor = accentForText(resolveTint(artistMode, artistCustom, artistDesaturated))

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
            drawSmallClock(canvas, cx, cy - radius + dp(23f), dp)
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

        drawClassicTextBlock(canvas, cx, cy, radius * 1.6f, artistColor, dp)

        drawBottomChrome(canvas, cx, cy, radius, dp)
    }

    /**
     * Draws the [artist, title, time] block exactly like the watch's centered LinearLayout:
     * tight stacking (title directly under artist, time 4dp below the title), the whole block
     * vertically centered on the screen, font padding excluded, at the watch's sp sizes
     * (16 / 40-46 / 13). The title itself follows the synced text mode (see [planClassicTitle]).
     */
    private fun drawClassicTextBlock(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            textWidth: Float,
            artistColor: Int,
            dp: (Float) -> Float
    ) {
        val plan = planClassicTitle(
                textWidth,
                dp(46f),
                dp(25f),
                dp(17f)
        )

        // 14dp (not the raw 16sp) so the artist reads at the same on-screen size as a real
        // watch, which is physically larger than this preview's 192dp geometry model.
        val artistSize = dp(14f)
        val classicTitleTypeface = titleTypeface(bold = true)
        textPaint.typeface = classicTitleTypeface
        textPaint.textSize = artistSize
        val artistFm = textPaint.fontMetrics
        val artistLineH = artistFm.descent - artistFm.ascent

        textPaint.textSize = plan.size
        val titleFm = textPaint.fontMetrics
        val titleLineH = titleFm.descent - titleFm.ascent
        val titleH = plan.lines.size * titleLineH

        val timeVisible = trackTimeVisible()
        textPaint.typeface = fontRegular
        val timeSize = dp(13f)
        textPaint.textSize = timeSize
        val timeFm = textPaint.fontMetrics
        val timeLineH = timeFm.descent - timeFm.ascent
        val timeGap = dp(4f)

        val artistVisible = showTrackArtist || !isPlayingShown()
        val titleVisible = showTrackTitle
        val totalH = (if (artistVisible) artistLineH else 0f) +
                (if (titleVisible) titleH else 0f) +
                (if (timeVisible) timeGap + timeLineH else 0f)
        var y = cy - totalH / 2f

        // Artist (or the "Playback Stopped" status in white while paused).
        if (artistVisible) {
            textPaint.typeface = classicTitleTypeface
            textPaint.textSize = artistSize
            if (isPlayingShown()) {
                textPaint.color = artistColor
                canvas.drawText(ellipsize(displayArtist(), textWidth), cx, y - artistFm.ascent, textPaint)
            } else {
                textPaint.color = Color.WHITE
                canvas.drawText(context.getString(R.string.preview_playback_stopped), cx, y - artistFm.ascent, textPaint)
            }
            y += artistLineH
        }

        // Title (as many lines as the text mode allows, or scrolling in marquee mode).
        if (titleVisible) {
            textPaint.typeface = classicTitleTypeface
            textPaint.color = Color.WHITE
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
            textPaint.typeface = fontRegular
            textPaint.textSize = timeSize
            textPaint.color = Color.WHITE
            canvas.drawText(timeText(), cx, y - timeFm.ascent, textPaint)
        }
    }

    /** True while a clock preference is being edited, so the player preview shows the clock even
     *  when Always-show-time is off - the same "force the surface visible while editing it" trick
     *  the mini buttons use. */
    private fun clockPreviewForced(): Boolean = focusedPreference?.startsWith("wear_clock_") == true

    /** The clock font follows WEAR_FONT (like the watch's clockFont) but never the Lain override -
     *  it is chrome, not track text. Falls back to the preloaded regular for Google Sans. */
    private fun clockTypeface(): Typeface? = when (wearFontKey) {
        "roboto" -> Typeface.DEFAULT
        "typewriter" -> fontMomsTypewriter
        "love_letter" -> fontLoveLetter
        "poppins" -> fontPoppins
        "montserrat" -> fontMontserrat
        "marcellus" -> fontMarcellus
        "rounded" -> Typeface.create("sans-serif-rounded", Typeface.NORMAL)
        "sans_light" -> Typeface.create("sans-serif-light", Typeface.NORMAL)
        "sans_thin" -> Typeface.create("sans-serif-thin", Typeface.NORMAL)
        "sans_medium" -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
        "sans_black" -> Typeface.create("sans-serif-black", Typeface.NORMAL)
        "small_caps" -> Typeface.create("sans-serif-smallcaps", Typeface.NORMAL)
        "casual" -> Typeface.create("casual", Typeface.NORMAL)
        "serif" -> Typeface.SERIF
        "serif_monospace" -> Typeface.create("serif-monospace", Typeface.NORMAL)
        "monospace" -> Typeface.MONOSPACE
        "cursive" -> Typeface.create("cursive", Typeface.NORMAL)
        "condensed" -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        "condensed_light" -> Typeface.create("sans-serif-condensed-light", Typeface.NORMAL)
        "condensed_medium" -> Typeface.create("sans-serif-condensed-medium", Typeface.NORMAL)
        else -> fontRegular
    }

    /** Mirrors MainActivity.resolveClockColor: opacity-baked ARGB from the color-mode pref, with
     *  dynamic sampling the top-centre strip of the shown art (not the whole cover). */
    private fun resolveClockColor(): Int {
        val base = when (clockColorMode) {
            "album" -> albumAccent()
            "custom" -> parseHexOrNull(clockCustomColor) ?: Color.WHITE
            "dynamic" -> if (clockAreaIsLight()) Color.BLACK else Color.WHITE
            else -> Color.WHITE
        }
        val alpha = (clockOpacity.coerceIn(10, 100) / 100f * 255f).toInt()
        return ColorUtils.setAlphaComponent(base, alpha)
    }

    /** Whether the artwork region under the top-centre clock is light (→ black clock). Samples the
     *  same top strip the watch does; falls back to dark (→ white clock) with no artwork. */
    private fun clockAreaIsLight(): Boolean {
        val art = liveArt ?: sampleArt ?: return false
        val w = art.width
        val h = art.height
        if (w <= 0 || h <= 0) return false
        val left = (w * 0.35f).toInt().coerceIn(0, w - 1)
        val right = (w * 0.65f).toInt().coerceIn(left + 1, w)
        val top = (h * 0.02f).toInt().coerceIn(0, h - 1)
        val bottom = (h * 0.15f).toInt().coerceIn(top + 1, h)
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
        return (if (n > 0) sum / n else 0.0) > 0.55
    }

    private fun drawSmallClock(
            canvas: Canvas,
            x: Float,
            y: Float,
            dp: (Float) -> Float,
            alpha: Float = 1f
    ) {
        textPaint.style = Paint.Style.FILL
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = clockTypeface()
        val resolved = resolveClockColor()
        textPaint.color = ColorUtils.setAlphaComponent(
                resolved,
                (Color.alpha(resolved) * alpha).toInt().coerceIn(0, 255)
        )
        textPaint.textSize = dp(13f)
        // Real wall-clock time, like the watch (which follows the system 12/24h setting and
        // never appends AM/PM). Minute precision is enough - the preview redraws often enough
        // while playing, and a stale minute at rest is harmless.
        val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
        val time = java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
                .format(java.util.Date())
        canvas.drawText(time, x, y, textPaint)
    }

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
        val geometry = previewMiniGeometry(icons.size, cy, radius, dp)
        val riseScale = when (buttonsCurveStyle) {
            "curved_extreme" -> 1.0f
            "curved" -> 1.2f
            "curved_medium" -> 1.0f
            "curved_soft" -> 0.8f
            "curved_gentle" -> 0.6f
            else -> 1.0f
        }
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

    private fun drawMiniButtons(canvas: Canvas, cx: Float, cy: Float, radius: Float, dp: (Float) -> Float): Boolean {
        // Mirrors hasActiveMiniButtons on the watch: a face with mini buttons off for the current
        // playback state never shows the row, regardless of what's configured - checked first so
        // every caller that reflows layout based on this return value reclaims the space exactly
        // like the watch does. The preview's shown state (playing vs paused) picks which toggle.
        if (!ActivityVisibility.isActive(miniButtonsMode, isPlayingShown())) {
            return false
        }
        // The configured stopped-action map remains useful to the preview's other controls, but
        // mini buttons themselves are playback-only. Paused is intentionally not treated as idle.
        if (!isPlayingShown()) {
            return false
        }
        val icons = if (miniButtonIcons.isNotEmpty()) {
            miniButtonIcons
        } else if (surface == PreviewSurface.MINI_BUTTONS) {
            demoMiniButtonIcons
        } else {
            emptyList()
        }
        if (icons.isEmpty()) {
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

        val albumBaseColor = albumAccent()
        val expressiveAlbumColor = PaletteTransforms.tonalSurface(
                albumBaseColor, 0.74f, 0.40f, 0.92f)
        val glowAlbumColor = FloatArray(3).let { hsl ->
            ColorUtils.colorToHSL(albumBaseColor, hsl)
            hsl[2] = hsl[2].coerceAtLeast(0.45f)
            ColorUtils.HSLToColor(hsl)
        }
        val tintColor = when (buttonsBgStyle) {
            "solid_theme" -> {
                val baseColor = ACCENT_NEUTRAL
                if (face == "expressive") {
                    PaletteTransforms.tonalSurface(baseColor, 0.74f, 0.40f, 0.92f)
                } else {
                    baseColor
                }
            }
            "solid_album" -> albumBaseColor
            "solid_exp_album" -> expressiveAlbumColor
            else -> null
        }
        val neutralSkin = neutralMiniButtonSkin(dp)
        // Universal styles render identically on every face (the user's own choice); "glass"
        // (Follow layout) keeps the per-face neutralSkin.
        val uniformGlassFill = 0xB3161619.toInt()
        val outlineStroke = ColorUtils.setAlphaComponent(albumBaseColor, 0xE0)

        val pillColor = when {
            buttonsBgStyle in setOf(
                    "transparent", "outline", "outline_exp_album", "icon_exp",
                    "glow_album", "glow_exp"
            ) -> Color.TRANSPARENT
            buttonsBgStyle == "uniform_glass" -> uniformGlassFill
            buttonsBgStyle == "uniform_glass_light" -> Color.argb(0x1A, 0xFF, 0xFF, 0xFF)
            buttonsBgStyle == "translucent_album" ->
                ColorUtils.setAlphaComponent(albumBaseColor, 0x4D)
            buttonsBgStyle == "translucent_album_exp" ->
                ColorUtils.setAlphaComponent(expressiveAlbumColor, 0x4D)
            tintColor != null -> ColorUtils.setAlphaComponent(
                    tintColor,
                    if (buttonsBgStyle == "solid_exp_album") 0xD0 else 230
            )
            else -> neutralSkin.fill
        }
        val pillStroke = when {
            buttonsBgStyle == "outline" -> outlineStroke
            buttonsBgStyle == "outline_exp_album" ->
                ColorUtils.setAlphaComponent(expressiveAlbumColor, 0xE0)
            buttonsBgStyle == "glow_album" -> ColorUtils.setAlphaComponent(glowAlbumColor, 0xE0)
            buttonsBgStyle == "glow_exp" ->
                ColorUtils.setAlphaComponent(expressiveAlbumColor, 0xE0)
            tintColor == null && buttonsBgStyle == "glass" -> neutralSkin.stroke
            else -> Color.TRANSPARENT
        }
        val pillStrokeWidth = when {
            buttonsBgStyle == "outline" || buttonsBgStyle == "outline_exp_album" -> dp(1.5f)
            buttonsBgStyle == "glow_album" || buttonsBgStyle == "glow_exp" -> dp(2f)
            pillStroke == Color.TRANSPARENT -> 0f
            else -> neutralSkin.strokeWidth
        }
        val pillCorner = if (tintColor == null && buttonsBgStyle == "glass") {
            neutralSkin.corner
        } else {
            pillH
        }
        val solidIconTint = when (buttonsBgStyle) {
            "uniform_glass_light" -> Color.WHITE
            "translucent_album" -> Color.WHITE
            "translucent_album_exp" -> Color.WHITE
            "glow_album" -> glowAlbumColor
            "outline_exp_album", "icon_exp", "glow_exp" -> expressiveAlbumColor
            else -> if (tintColor != null) {
                if (ColorUtils.calculateLuminance(tintColor) > .50) Color.BLACK else Color.WHITE
            } else {
                null
            }
        }
        val forceGroupIconTint = buttonsBgStyle in setOf(
                "glow_album", "outline_exp_album", "icon_exp", "glow_exp", "solid_exp_album"
        )

        val curveFraction = if (deviceRound == false) {
            null
        } else {
            when (buttonsCurveStyle) {
                "arc" -> 0f
                "curved_gentle" -> 0.25f
                "curved_soft" -> 0.5f
                "curved_medium" -> 0.75f
                "curved" -> 1f
                "curved_extreme" -> 2.2f
                else -> null
            }
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
                    val r = dp(17f)
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
            val pillCx = cx - totalWidth / 2f + pillW / 2f + i * (pillW + gap)
            var pillCy = rowBottom - pillH / 2f
            var rotation = 0f

            if (curveFraction != null) {
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
                val maxCap = if (buttonsCurveStyle == "curved_extreme") 36f else 18f
                val baseRise = minOf(naturalRise, dp(maxCap))
                val isOuterOfThree = icons.size == 3 && (i == 0 || i == icons.lastIndex)
                pillCy -= baseRise + if (isOuterOfThree &&
                        buttonsCurveStyle != "curved_extreme") dp(4f) else 0f
                val tangentRotation = curveFraction *
                        -Math.toDegrees(asin((clamped / radius).toDouble())).toFloat()
                val maxRotation = if (buttonsCurveStyle == "curved_extreme") 35f else 15f
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
        val artistColor = accentForText(resolveTint(artistMode, artistCustom, artistDesaturated))
        val progressColor = resolveTint(progressMode, progressCustom, progressDesaturated)

        if (alwaysShowTime || clockPreviewForced()) drawSmallClock(canvas, cx, cy - radius + dp(24f), dp)

        // Title/artist near the top. Overflowing titles always scroll here - the watch's
        // expressive face uses an unconditional marquee, independent of the title text mode.
        // Sizes mirror the watch's expressive face (16sp title / 13sp artist).
        if (showTrackTitle) {
            textPaint.typeface = titleTypeface(bold = true)
            textPaint.color = Color.WHITE
            textPaint.textSize = dp(16f)
            val expressiveTitle = displayTitle()
            if (textPaint.measureText(expressiveTitle) <= radius * 1.45f) {
                canvas.drawText(expressiveTitle, cx, cy - radius + dp(43f), textPaint)
            } else {
                drawMarqueeText(canvas, expressiveTitle, cx, cy - radius + dp(43f), radius * 1.45f)
            }
        }
        // Artist, or the "Playback Stopped" status in white while paused (the watch's expressive
        // face mirrors the same textArtist line the classic face swaps).
        textPaint.typeface = titleTypeface(bold = false)
        textPaint.textSize = dp(12f)
        if (isPlayingShown() && showTrackArtist) {
            textPaint.color = artistColor
            canvas.drawText(ellipsize(displayArtist(), radius * 1.45f), cx, cy - radius + dp(57f), textPaint)
        } else if (!isPlayingShown()) {
            textPaint.color = Color.WHITE
            canvas.drawText(context.getString(R.string.preview_playback_stopped), cx, cy - radius + dp(57f), textPaint)
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
            canvas.drawText(
                    timeText(),
                    cx,
                    centeredTransportTimeY(
                            cy + dp(45f), miniConfigured, radius, cy, dp),
                    textPaint)
        }

        // The watch's expressive face has no default queue/volume/overflow trio - that row is
        // left entirely to the user's configured mini buttons.
        drawBottomChrome(canvas, cx, cy, radius, dp)
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
            accentForText(resolveTint(artistMode, artistCustom, artistDesaturated))
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
            textPaint.typeface = titleTypeface(bold = true)
            textPaint.textSize = dp(when (kind) {
                "vinyl" -> 12f
                "halo" -> 13f
                "eclipse" -> if (compact) 12f else 15f
                else -> if (compact) 14f else 18f
            })
            textPaint.color = if (kind == "eclipse") 0xE0FFFFFF.toInt() else Color.WHITE
            val singleLineAdjustment = if (titleVisible.xor(artistVisible && showArtist)) dp(5f) else 0f
            val adjustedTop = topY + singleLineAdjustment
            if (titleVisible) {
                val rawTitle = displayTitle()
                val title = if (kind == "vinyl" || kind == "eclipse") rawTitle.uppercase() else rawTitle
                val titleWidth = radius * 1.38f
                if (textPaint.measureText(title) <= titleWidth) {
                    canvas.drawText(title, cx, adjustedTop, textPaint)
                } else {
                    drawMarqueeText(canvas, title, cx, adjustedTop, titleWidth)
                }
            }
            if (showArtist && artistVisible) {
                textPaint.typeface = titleTypeface(bold = false)
                textPaint.textSize = dp(if (compact) 9f else 11f)
                textPaint.color = artistColor
                canvas.drawText(ellipsize(if (isPlayingShown()) displayArtist()
                        else context.getString(R.string.preview_playback_stopped), radius * 1.35f),
                        cx, if (titleVisible) adjustedTop + dp(if (compact) 12f else 14f)
                        else adjustedTop, textPaint)
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
            val art = if (hidden) null else if (blurred) (liveArtBlurred ?: sampleArtBlurred) else (liveArt ?: sampleArt)
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
                val bottomInset = radius * 2f * .13f
                var y = cy + radius - bottomInset
                if (trackTimeVisible()) {
                    textPaint.textAlign = Paint.Align.CENTER
                    textPaint.typeface = fontRegular
                    textPaint.textSize = dp(9f)
                    textPaint.color = 0xA8FFFFFF.toInt()
                    canvas.drawText(timeText(), cx, y, textPaint)
                    y -= dp(15f)
                }
                if (artistVisible) {
                    textPaint.typeface = titleTypeface(bold = false)
                    textPaint.textSize = dp(12f)
                    textPaint.color = ColorUtils.setAlphaComponent(artistColor, 0xD1)
                    canvas.drawText(ellipsize(
                            if (isPlayingShown()) displayArtist()
                            else context.getString(R.string.preview_playback_stopped),
                            radius * 1.5f), cx, y, textPaint)
                    y -= dp(17f)
                }
                if (titleVisible) {
                    textPaint.typeface = titleTypeface(bold = true)
                    textPaint.textSize = dp(15f)
                    textPaint.color = Color.WHITE
                    val immersiveTitleWidth = radius * 1.5f
                    val title = displayTitle()
                    if (textPaint.measureText(title) <= immersiveTitleWidth) {
                        canvas.drawText(title, cx, y, textPaint)
                    } else {
                        drawMarqueeText(canvas, title, cx, y, immersiveTitleWidth)
                    }
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
                    canvas.drawText(timeText(), cx, clampTimeY(
                            discRect.bottom + dp(17f), miniConfigured, radius, cy, dp), textPaint)
                }
            }
            "poster" -> {
                // The watch's PosterMetadata is a Column with Modifier.align(Alignment.Center) -
                // the whole title+artist block sits vertically centered on the screen, not
                // anchored under the top bezel.
                if (showCompactHeader) {
                    val posterTitleSize = dp(if (hasMiniButtons) 14f else 18f)
                    val posterArtistSize = dp(10f)
                    val posterGap = dp(6f)
                    val posterArtistVisible = !hasMiniButtons && artistVisible

                    // The watch's PosterMetadata title now follows the user's Title text
                    // behaviour (AdaptiveTitleText), so the preview does too instead of always
                    // wrapping to at most two lines - a short one-line title still takes just
                    // one line, so the artist sits right beneath it rather than floating a whole
                    // empty line below (the "big gap" the preview used to show).
                    val posterTitleWidth = radius * 1.40f
                    val posterFloorSize = posterTitleSize * 0.62f
                    val posterPlan = planClassicTitle(
                            posterTitleWidth, posterTitleSize, posterFloorSize, posterFloorSize)

                    textPaint.typeface = fontBold
                    textPaint.textSize = posterPlan.size
                    val posterTitleFm = textPaint.fontMetrics
                    val posterTitleLineH = posterTitleFm.descent - posterTitleFm.ascent

                    textPaint.typeface = fontRegular
                    textPaint.textSize = posterArtistSize
                    val posterArtistFm = textPaint.fontMetrics
                    val posterArtistLineH = posterArtistFm.descent - posterArtistFm.ascent

                    val posterTitleBlockH = posterTitleLineH * posterPlan.lines.size
                    val posterTotalH = (if (titleVisible) posterTitleBlockH else 0f) +
                            (if (titleVisible && posterArtistVisible) posterGap else 0f) +
                            (if (posterArtistVisible) posterArtistLineH else 0f)
                    var posterY = cy - posterTotalH / 2f

                    if (titleVisible) {
                        textPaint.typeface = fontBold
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
                        textPaint.typeface = fontRegular
                        textPaint.textSize = posterArtistSize
                        textPaint.color = ColorUtils.setAlphaComponent(artistColor, 0xC7)
                        canvas.drawText(ellipsize(
                                if (isPlayingShown()) displayArtist()
                                else context.getString(R.string.preview_playback_stopped),
                                radius * 1.35f
                        ), cx, posterY - posterArtistFm.ascent, textPaint)
                    }
                }
                // No play/pause glyph here: the watch's PosterComposition never draws one (tap
                // still toggles playback) - the artwork stays unobscured, matching CHANGELOG.
                if (!miniConfigured) progressLine(cy + dp(56f), dp(104f))
                if (trackTimeVisible()) {
                    textPaint.color = 0xA8FFFFFF.toInt(); textPaint.textSize = dp(9f)
                    // On the floor when there's no progress line above it (mirrors the watch).
                    val posterTimeY = if (internalProgressVisible) dp(73f) else dp(81f)
                    canvas.drawText(timeText(), cx, clampTimeY(cy + posterTimeY, miniConfigured, radius, cy, dp), textPaint)
                }
            }
            "studio" -> {
                // The watch's StudioMetadata is also a Column with Alignment.Center - center the
                // block on screen instead of anchoring it under the top bezel like header() does
                // for the genuinely top-anchored faces (vinyl/halo/eclipse).
                if (showCompactHeader) {
                    val studioTitleSize = dp(if (hasMiniButtons) 14f else 18f)
                    val studioArtistSize = dp(if (hasMiniButtons) 9f else 11f)
                    val studioGap = dp(if (hasMiniButtons) 5f else 6f)
                    val studioArtistVisible = !hasMiniButtons && artistVisible

                    // Studio's title now follows the user's Title text behaviour, same as Poster
                    // above.
                    val studioTitleWidth = radius * 1.38f
                    val studioFloorSize = studioTitleSize * 0.62f
                    val studioPlan = planClassicTitle(
                            studioTitleWidth, studioTitleSize, studioFloorSize, studioFloorSize)

                    textPaint.typeface = titleTypeface(bold = true)
                    textPaint.textSize = studioPlan.size
                    val studioTitleFm = textPaint.fontMetrics
                    val studioTitleLineH = studioTitleFm.descent - studioTitleFm.ascent

                    textPaint.typeface = titleTypeface(bold = false)
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
                        textPaint.typeface = titleTypeface(bold = false)
                        textPaint.textSize = studioArtistSize
                        textPaint.color = artistColor
                        canvas.drawText(ellipsize(if (isPlayingShown()) displayArtist()
                                else context.getString(R.string.preview_playback_stopped), radius * 1.35f),
                                cx, studioY - studioArtistFm.ascent, textPaint)
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
                    canvas.drawText(timeText(), cx, clampTimeY(studioTimeY, miniConfigured, radius, cy, dp), textPaint)
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
                    canvas.drawText(timeText(), cx, clampTimeY(
                            artY + artR + dp(17f), miniConfigured, radius, cy, dp), textPaint)
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
                        textPaint.typeface = titleTypeface(bold = false)
                        textPaint.textSize = dp(8f)
                        textPaint.color = 0xB3FFFFFF.toInt()
                        val artistLabel = if (isPlayingShown()) displayArtist().uppercase()
                        else context.getString(R.string.preview_playback_stopped).uppercase()
                        canvas.drawText(ellipsize(artistLabel, columnWidth), columnLeft, lineY, textPaint)
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
                    canvas.drawText(timeText(), cx, clampTimeY(
                            cy + radius * .78f, miniConfigured, radius, cy, dp), textPaint)
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
                    canvas.drawText(timeText(), cx, clampTimeY(
                            coreY + coreR + dp(17f), miniConfigured, radius, cy, dp), textPaint)
                }
            }
            "spectrum" -> {
                if (showCompactHeader) {
                    textPaint.typeface = titleTypeface(bold = true)
                    textPaint.textSize = dp(12f)
                    textPaint.color = 0xEAFFFFFF.toInt()
                    val singleLineAdjustment = if (titleVisible.xor(artistVisible)) dp(5f) else 0f
                    val spectrumTitleY = cy - radius + dp(43f) + singleLineAdjustment
                    if (titleVisible) {
                        val spectrumTitle = displayTitle().uppercase()
                        canvas.drawText(
                                ellipsize(spectrumTitle, radius * 1.36f),
                                cx,
                                spectrumTitleY,
                                textPaint
                        )
                    }
                    if (!hasMiniButtons && artistVisible) {
                        textPaint.typeface = titleTypeface(bold = false)
                        textPaint.textSize = dp(9f)
                        textPaint.color = ColorUtils.setAlphaComponent(
                                accentForText(resolveTint(
                                        artistMode,
                                        artistCustom,
                                        artistDesaturated
                                )),
                                199
                        )
                        canvas.drawText(
                                ellipsize(
                                        if (isPlayingShown()) displayArtist()
                                        else context.getString(R.string.preview_playback_stopped),
                                        radius * 1.35f
                                ),
                                cx,
                                if (titleVisible) spectrumTitleY + dp(12f) else spectrumTitleY,
                                textPaint
                        )
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
                val spectrumSeed = spectrumPreviewTrackSeed(
                        nowPlayingTitle ?: context.getString(R.string.preview_sample_title),
                        nowPlayingArtist ?: context.getString(R.string.preview_sample_artist),
                        liveDurationMs.takeIf { it > 0L } ?: 192_000L
                )
                val spectrumPhase = if (isPlayingShown()) {
                    transientAnimationActive = true
                    (SystemClock.uptimeMillis() % 120_000L) / 1_000f
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
                    canvas.drawText(timeText(), fieldX, clampTimeY(
                            fieldY + fieldH / 2f + dp(17f), miniConfigured, radius, cy, dp), textPaint)
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
                    canvas.drawText(
                            timeText(),
                            cx,
                            centeredTransportTimeY(
                                    cy + dp(45f), miniConfigured, radius, cy, dp),
                            textPaint)
                }
            }
        }

        if (alwaysShowTime || clockPreviewForced()) drawSmallClock(canvas, cx, cy - radius + dp(24f), dp)
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
