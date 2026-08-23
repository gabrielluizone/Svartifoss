package com.svartifoss.snfell.watch.view

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.graphics.drawable.Animatable
import androidx.compose.ui.unit.dp
import com.svartifoss.snfell.watch.view.queue.QUEUE_ARTWORK_INSET
import com.svartifoss.snfell.watch.view.queue.QueueRowSize
import com.svartifoss.snfell.watch.view.queue.listRowArtworkSize
import com.svartifoss.snfell.watch.view.queue.QueueStyle
import com.svartifoss.snfell.watch.view.queue.blurredCover
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.TransitionDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Vibrator
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.app.ActivityManager
import android.app.RemoteInput
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.palette.graphics.Palette
import androidx.preference.PreferenceManager
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.wear.input.RemoteInputIntentHelper
import androidx.wear.widget.SwipeDismissFrameLayout
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.wearable.input.RotaryEncoderHelper
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.CenterButton
import com.svartifoss.snfell.common.AdaptiveTextContrast
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.common.CustomLists
import com.svartifoss.snfell.common.AodArtTreatment
import com.svartifoss.snfell.common.AppearanceContext
import com.svartifoss.snfell.common.FaceScopedPreferences
import com.svartifoss.snfell.common.FrostedEdges
import com.svartifoss.snfell.common.BitmapBlur
import com.svartifoss.snfell.common.ActivityVisibility
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
import com.svartifoss.snfell.common.ScreenQuadrant
import com.svartifoss.snfell.common.QuickPanelButtons
import com.svartifoss.snfell.common.CarouselCardShape
import com.svartifoss.snfell.common.IdleScreenAction
import com.svartifoss.snfell.common.R as commonR
import com.svartifoss.snfell.common.RotaryAction
import com.svartifoss.snfell.common.ScreenButtons
import com.svartifoss.snfell.common.AccentFloorStyle
import com.svartifoss.snfell.common.SplitPanelStyle
import com.svartifoss.snfell.common.AlbumAccentSource
import com.svartifoss.snfell.common.SwatchInfo
import com.svartifoss.snfell.common.selectPrimaryAccent
import com.svartifoss.snfell.common.ColorModifier
import com.svartifoss.snfell.common.SurfaceColorTreatment
import com.svartifoss.snfell.common.SurfacePaletteResolver
import com.svartifoss.snfell.common.SwipeGesture
import com.svartifoss.snfell.common.TrackMetadataFields
import com.svartifoss.snfell.common.WatchTypography
import com.svartifoss.snfell.common.ThemeAppearance
import com.svartifoss.snfell.common.resolveAodArtwork
import com.svartifoss.snfell.common.buttonconfig.ButtonInfo
import com.svartifoss.snfell.common.buttonconfig.GESTURE_DOUBLE_TAP
import com.svartifoss.snfell.common.buttonconfig.GESTURE_LONG_TAP
import com.svartifoss.snfell.common.buttonconfig.GESTURE_SINGLE_TAP
import com.svartifoss.snfell.common.buttonconfig.SpecialButtonCodes
import com.svartifoss.snfell.common.view.FourWayTouchLayout
import com.svartifoss.snfell.common.view.SwipeTrailDrawable
import com.svartifoss.snfell.common.view.TapPulseDrawable
import com.svartifoss.snfell.databinding.ActivityMainBinding
import com.svartifoss.snfell.proto.MediaAction
import com.svartifoss.snfell.proto.MusicState
import com.svartifoss.snfell.watch.communication.WatchAppShutdown
import com.svartifoss.snfell.watch.communication.CustomListWithBitmaps
import com.svartifoss.snfell.watch.communication.UiOpenServiceConnection
import com.svartifoss.snfell.watch.communication.WatchInfoSender
import com.svartifoss.snfell.watch.communication.WatchMusicService
import com.svartifoss.snfell.watch.view.menu.MenuActivity
import com.svartifoss.snfell.watch.view.queue.QueueActivity
import com.svartifoss.snfell.watch.view.lyrics.LyricsActivity
import com.svartifoss.snfell.watch.config.ButtonAction
import com.svartifoss.snfell.watch.config.WatchActionConfigProvider
import com.svartifoss.snfell.watch.model.Notification
import com.svartifoss.snfell.watch.theme.WatchTheme
import com.svartifoss.snfell.watch.theme.lainFont
import com.svartifoss.snfell.watch.theme.watchFontTypeface
import com.svartifoss.snfell.watch.theme.flexTypeface
import com.svartifoss.snfell.watch.theme.selectAlbumCompanionColors
import com.svartifoss.snfell.common.AppLocales
import com.svartifoss.snfell.watch.util.StandardActionTitles
import com.svartifoss.snfell.watch.util.applyKeepScreenOnPreference
import com.svartifoss.snfell.watch.util.WatchLanguage
import com.svartifoss.snfell.watch.view.face.AuroraFace
import com.svartifoss.snfell.watch.view.face.EclipseFace
import com.svartifoss.snfell.watch.view.face.ExpressiveFace
import com.svartifoss.snfell.watch.view.face.HaloFace
import com.svartifoss.snfell.watch.view.face.PosterFace
import com.svartifoss.snfell.watch.view.face.SpectrumFace
import com.svartifoss.snfell.watch.view.face.StudioFace
import com.svartifoss.snfell.watch.view.face.VinylFace
import com.svartifoss.snfell.watch.view.face.MaterialFace
import com.svartifoss.snfell.watch.view.face.ChronoAmbientFace
import com.svartifoss.snfell.common.CenterLongPressAction
import com.svartifoss.snfell.watch.view.facepicker.FacePickerActivity
import com.svartifoss.snfell.watch.view.face.CarouselFace
import com.svartifoss.snfell.watch.view.face.ChatFace
import com.svartifoss.snfell.watch.view.face.NoteFace
import com.svartifoss.snfell.watch.view.face.VerseFace
import com.svartifoss.snfell.watch.view.lyrics.LyricsUiState
import com.svartifoss.snfell.watch.view.face.SplitFace
import com.svartifoss.snfell.watch.view.face.QueueCard
import com.svartifoss.snfell.watch.view.face.DepthFace
import com.svartifoss.snfell.watch.view.face.ImmersiveFace
import com.svartifoss.snfell.watch.view.face.FaceMiniButton
import com.svartifoss.snfell.watch.view.face.NowPlayingFaceListener
import com.svartifoss.snfell.watch.view.face.MetadataFace
import com.svartifoss.snfell.watch.view.face.NowPlayingFaceState
import com.svartifoss.snfell.watch.view.face.ScreenTheme
import com.svartifoss.snfell.watch.view.face.resolveMetadataVisibility
import com.svartifoss.snfell.watch.view.face.shouldEnableCentralSeek
import com.svartifoss.snfell.watch.view.face.shouldKeepEdgeSeekView
import com.matejdro.wearutils.companionnotice.WearCompanionWatchActivity
import com.matejdro.wearutils.lifecycle.Resource
import com.matejdro.wearutils.miscutils.VibratorCompat
import com.matejdro.wearutils.preferences.definition.Preferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import timber.log.Timber
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Mini buttons follow their config, including on the empty "nothing playing" screen.
 *
 * They used to be suppressed while idle, on the reasoning that they belong to a loaded track. That
 * left the idle screen with no visible control whatsoever - and it is precisely the screen where a
 * user most needs one, because the slots there come from the *stopped* config, which is where
 * playlist shortcuts and "resume" style actions are assigned. [idle] is kept as a parameter so the
 * ambient/overlay rules below can still reason about the state.
 *
 * [enabledForFace] is [MiscPreferences.WEAR_MINI_BUTTONS_MODE] resolved for the active face and
 * playback state - independent from [configured], which reflects the (global) slot assignments.
 */
@Suppress("UNUSED_PARAMETER")
internal fun hasActiveMiniButtons(configured: Boolean, idle: Boolean, enabledForFace: Boolean): Boolean =
        configured && enabledForFace


internal fun shouldShowMiniButtons(
        configured: Boolean,
        idle: Boolean,
        ambient: Boolean,
        overlayActive: Boolean,
        enabledForFace: Boolean
): Boolean = hasActiveMiniButtons(configured, idle, enabledForFace) && !ambient && !overlayActive

@AndroidEntryPoint
class MainActivity : WearCompanionWatchActivity(),
        FourWayTouchLayout.UserActionListener {

    companion object {
        const val EXTRA_OPEN_VOICE_SEARCH = "OpenVoiceSearch"
        const val EXTRA_OPEN_LYRICS = "OpenLyrics"

        /** The one face that renders lyrics; see applyScreenFaceNow. */
        private const val FACE_VERSE = "verse"

        /** The one face that renders the track's full metadata; see applyScreenFaceNow. */
        private const val FACE_METADATA = "metadata"
        private const val KEY_SEARCH_QUERY = "search_query"

        /** Leading size for a full-colour app-launcher icon in a quick-panel row. Kept in step
         *  with `MenuScreen`'s APP_ICON_SIZE - the two surfaces list the same actions, so an app
         *  icon that differed between them would read as a bug in one of them. */
        private const val APP_ICON_DP = 26f

        /** What a rail reports as the top of the mini-button band: nothing, in practice. The
         *  bottom-row value is clamped to .95 at most, so this is "the whole screen is yours". */
        private const val RAIL_TOP_FRACTION = .95f

        /** The classic face's designed text sizes, which the user's size scale multiplies. Named
         *  so the initial setup and applyClassicTypography cannot drift out of step. */
        private const val CLASSIC_TITLE_MAX_SP = 46f
        private const val CLASSIC_TITLE_MIN_SP = 25f
        private const val CLASSIC_ARTIST_MAX_SP = 16f
        private const val CLASSIC_ARTIST_MIN_SP = 9f

        /** The awake clock's designed size - must match activity_main.xml's ambient_clock
         *  textSize, which the clock size percentage scales from. */
        private const val CLASSIC_CLOCK_SP = 15f

        private const val MESSAGE_HIDE_VOLUME = 10
        private const val MESSAGE_UPDATE_CLOCK = 11
        private const val MESSAGE_DISMISS_NOTIFICATION = 12
        private const val REQUEST_CODE_POST_NOTIFICATIONS = 1001

        // Local-only prefs file. The default SharedPreferences can't be used for watch-side UI
        // state: viewModel.preferences swaps it for the phone-synced copy, which would wipe
        // anything written locally on the next sync.
        private const val PREFS_LOCAL_UI = "local_ui_state"
        // Versioned so an expanded guide can be presented once to people who completed the old
        // one-screen hint. A version is persisted only after the final page is completed.
        private const val KEY_GESTURE_GUIDE_VERSION = "gesture_guide_version"
        private const val GESTURE_GUIDE_VERSION = 2

        /** Top edge of the awake Up Next pill as a fraction of screen height: it sits at
         *  BottomCenter with ~.07 bottom padding and ~.25 height, so its top is ~1 - .07 - .25.
         *  Fed to the faces as miniButtonsTopFraction so the track time clears it. */
        private const val AWAKE_PILL_TOP_FRACTION = 0.66f

        private const val ROTARY_SEEK_COMMIT_DELAY_MS = 400L
        private const val OVERLAY_FADE_OUT_MS = 150L
        private const val OVERLAY_FADE_IN_MS = 90L
        private const val ALBUM_ART_CROSSFADE_MS = 300

        /** How far two covers' aspect ratios may differ and still cross-fade directly - see
         *  [sameAspectRatio]. Wide enough to absorb a rounding difference of a pixel or two, far
         *  narrower than any real shape mismatch. */
        private const val ALBUM_ART_ASPECT_TOLERANCE = 0.01f
        /** Consistent readable ink for every light pill surface. */
        private const val PILL_ON_LIGHT = 0xFF202124.toInt()

        /** The Up Next pill's padding while it shows a glyph and text rather than a cover; see
         *  [applyUpNextTextPadding]. Matches activity_main.xml's own start/end values. */
        private const val UP_NEXT_TEXT_PADDING_START_DP = 18f
        private const val UP_NEXT_TEXT_PADDING_END_DP = 20f
        private const val UP_NEXT_TEXT_PADDING_VERTICAL_DP = 11f
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var vibrator: Vibrator
    private lateinit var ambientObserver: AmbientLifecycleObserver

    /** Our own ambient flag, flipped at the very start of onEnter/onExitAmbient. More reliable than
     *  [AmbientLifecycleObserver.isAmbient], which is not guaranteed set yet inside the enter
     *  callback on every watch - see the source-icon-in-AOD note there. */
    private var inAmbient = false
    private lateinit var stemButtonsManager: StemButtonsManager
    private val handler = TimeoutsHandler(WeakReference(this))

    private var notificationDismissDeadline: Long = Long.MAX_VALUE
    // True while the lesson is open; also brings it back after ambient temporarily hides it.
    private var firstRunHintsPending = false
    private var firstRunHintsPage = 0
    private var dimAlbumArt: Boolean = false
    private var albumArtStyle: String = "cover"
    /** Carousel's card outline, re-read with the other face-scoped appearance values. */
    private val carouselCardShape = mutableStateOf(CarouselCardShape.ROUNDED)
    private var playerBackgroundStyle: PlayerBackgroundStyle = PlayerBackgroundStyle.COVER
    private var blurAlbumArtBackground: Boolean = false
    private var albumArtGrayscale: Boolean = false
    private var albumArtHidden: Boolean = false
    private var blurRadiusPx: Float = 35f
    private var overlayBlurRadiusPx: Float = 35f
    /** Frosted-rim composition and the exact bitmap it was built from - see [frostArtworkIfSelected]. */
    private var cachedFrostedArt: Bitmap? = null
    private var cachedFrostedSource: Bitmap? = null
    private var overlayBackdropStyle: String = "follow"
    private var playerShadingStyle: PlayerShadingStyle = PlayerShadingStyle.FOLLOW
    private var playerShadingIntensity: Float = PlayerShadingIntensity.BALANCED.multiplier
    private var shadingColorMode: String = "black"
    private var shadingCustomColor: String = ""
    private var volumeBarTimeoutMs: Long = 1000L
    private var rotaryDeadzone: Float = 6f
    private var ambientAlbumArtAlpha: Float = 0.55f

    /** Always-on display configuration, all synced from the phone (see MiscPreferences):
     *  [aodStyle] picks the AOD presentation ("follow" resolves against [screenFace] - see
     *  [effectiveAodStyle]), the rest toggle individual elements. */
    private var aodStyle: String = "follow"
    private var aodShowArt = true
    private var aodArtTreatment = AodArtTreatment.BLUR
    private var aodShowClock = true
    private var aodShowTrackInfo = true
    private var aodColorMode: String = "white"
    private var aodCustomColor: String = ""
    private var aodShowTransport = true
    private var aodShowProgress = true
    private var aodShowPills = true
    private var aodIntensity: Float = 1f

    /** Awake-clock appearance (MiscPreferences.WEAR_CLOCK_*). Resolved into a single ARGB colour
     *  by [resolveClockColor]; the "dynamic" mode also samples [latestAlbumArt]. */
    private var clockColorMode: String = "white"
    private var clockCustomColor: String = ""
    private var clockOpacity: Int = 60

    /** When the mini-buttons row / screen gestures apply on the active face, as an
     *  [ActivityVisibility] value (MiscPreferences.WEAR_MINI_BUTTONS_MODE / WEAR_GESTURES_MODE).
     *  Independent from the configured slots/assignments; resolved for the current playback state
     *  by [miniButtonsEnabledNow] / [gesturesEnabledNow]. */
    private var miniButtonsMode: String = ActivityVisibility.ALWAYS
    private var gesturesMode: String = ActivityVisibility.ALWAYS

    /** MiscPreferences.WEAR_SHOW_UP_NEXT_PILL for the active face. The pill only actually draws
     *  while the mini-buttons row is not (resolved in syncScreenButtonsVisibility). */
    private var showUpNextPillPref = false

    /** The modes resolved against actual playback right now. "playing" is true only during real
     *  playback; a paused session and true idle both count as not-playing (matching the button
     *  configs' split). */
    private fun miniButtonsEnabledNow(): Boolean =
            ActivityVisibility.isActive(miniButtonsMode, isMusicPlaying)

    private fun gesturesEnabledNow(): Boolean =
            ActivityVisibility.isActive(gesturesMode, isMusicPlaying)

    private var centerLongPressQueueEnabled = false

    /** Resolved from the three-way preference, falling back to the legacy boolean above - see
     *  [CenterLongPressAction]. Re-read alongside it in the preference pass. */
    private var centerLongPressAction = CenterLongPressAction.FACES

    /**
     * The one place the centre long-press is acted on, shared by the Compose faces' listener and
     * the classic face's own GestureDetector. Both used to carry their own copy of the "is the
     * queue option on" check, which is exactly the kind of duplication that leaves one of them
     * behind when the behaviour grows a third case.
     */
    private fun performCenterLongPress() {
        val target = when (centerLongPressAction) {
            CenterLongPressAction.NONE -> return
            CenterLongPressAction.QUEUE -> QueueActivity::class.java
            CenterLongPressAction.FACES -> FacePickerActivity::class.java
        }
        buzz()
        startActivity(Intent(this@MainActivity, target))
    }
    private var wearDynamicAccentEnabled = true
    private var albumArtFadeEnabled = true
    private var screenTheme: ScreenTheme = ScreenTheme.DEFAULT
    /** Classic-only: whether a quadrant's icon flashes to full opacity and back on its own tap -
     *  see [pulseQuadrantIcon]. */
    private var quadrantTapFlashEnabled: Boolean = false

    /** Selected now-playing face (see [MiscPreferences.WEAR_SCREEN_FACE] and NowPlayingFace.kt):
     *  "classic" is the original View presentation, "expressive" the Compose face. */
    private var screenFace: String = "classic"
    /** Separates the structural renderer ([screenFace]) from the appearance namespace. Built-in
     * themes keep their historical `key@face` scope; a saved custom theme renders its validated
     * [AppearanceContext.baseFace] while reading the isolated active-theme snapshot. */
    private var appearanceContext: AppearanceContext = AppearanceContext.BuiltIn("classic")
    /** Per-element typography, resolved once per preference read and shared by the classic View
     *  face ([applyClassicTypography]) and every Compose face (through the face state). */
    private var titleTypography = WatchTypography.IDENTITY_TEXT
    private var artistTypography = WatchTypography.IDENTITY_TEXT
    private var clockTypography = WatchTypography.IDENTITY_TEXT
    /** Google Sans Flex's shared width/optical-size/grade/roundness axes - see
     *  [WatchTypography.flexAxes]. Only consulted when the active font is actually Flex. */
    private var flexAxes = WatchTypography.IDENTITY_FLEX_AXES
    private var sourceIconTypography = WatchTypography.IDENTITY_ICON
    private var showTrackTitle = true
    private var showTrackArtist = true
    private var playerControlsVisible = true
    private var internalProgressVisible = true
    private var edgeProgressVisible = true
    private var edgeSeekEnabled = true
    private var playbackSeekable = false
    /** True from the first overlay frame until its fade-out completes. The bezel seek view sits
     * above the dim/blur views in z-order, so alpha alone cannot prevent it intercepting touch. */
    private var overlayActive = false
    private var titleLineIsStatus = false

    /** How the expressive face exposes drag-to-seek (see [MiscPreferences.WEAR_EXPRESSIVE_SEEK_MODE]):
     *  "central" makes the expressive ring draggable, "edge" keeps the classic bezel seek ring
     *  visible on the expressive face, "none" leaves seeking to the rotary crown. */
    private var expressiveSeekMode: String = "central"

    /** Title/artist typeface choice (MiscPreferences.WEAR_FONT), applied to every layout -
     *  including the View-based classic face, which reads it directly (see applyWearFont). */
    private var wearFontKey: String = "google_sans"
    /** Raw MiscPreferences.WEAR_CLOCK_FONT value - "follow" until the user picks a clock typeface.
     *  Resolved against [wearFontKey] through WatchTypography.clockFontKey at the point of use. */
    private var wearClockFontKey: String = WatchTypography.CLOCK_FONT_FOLLOW
    /** Raw MiscPreferences.WEAR_LYRICS_FONT value - "follow" until the user picks a typeface for
     *  song lyrics, which keeps the serif the Verse face was designed around. Resolved by
     *  NowPlayingFaceState.lyricFont at the point of use. */
    private var wearLyricsFontKey: String = WatchTypography.LYRICS_FONT_FOLLOW
    /** Which blocks of the Metadata face are switched on - see TrackMetadataFields.Group. */
    private var metadataGroups: Set<TrackMetadataFields.Group> = emptySet()

    /** Single state snapshot driving the Compose face. Kept up to date by the same observers
     *  that update the classic views, so switching faces is purely a visibility change. */
    private val faceState = mutableStateOf(NowPlayingFaceState())

    /** Which Compose face the shared ComposeView renders ("expressive"/"vinyl"/"poster"/...).
     *  Tracks [screenFace] while interactive and the effective AOD style while ambient. */
    private val composeFaceKind = mutableStateOf("expressive")

    /** Face keys rendered by the Compose view (everything except the View-based classic). */
    private val composeFaces = setOf(
            "expressive", "vinyl", "poster", "studio", "halo", "aurora", "eclipse", "spectrum",
            "material", "immersive", "depth", "carousel", "chat", "split", "note", "verse",
            "metadata"
    )

    /** Mirrors [FourWayTouchLayout]'s own tap-feedback pulse at a higher z-order, drawn into
     *  R.id.compose_tap_pulse (see that view's doc comment in activity_main.xml). That layout's
     *  ripple is a child of itself, so it's invisible whenever a Compose face's opaque backdrop
     *  is layered on top - this parallel drawable is driven by onTouchDown/onTouchUp below, only
     *  while a Compose face is showing (Classic already has a visible ripple and doesn't need a
     *  second one). */
    private val composeTapPulse by lazy {
        TapPulseDrawable(ResourcesCompat.getColor(resources, com.svartifoss.snfell.common.R.color.music_screen_ripple, null))
    }

    /** Drawn into R.id.swipe_trail_overlay, the topmost view in content_frame - so unlike
     *  [composeTapPulse] it doesn't need a face-conditional, the same instance renders above
     *  Classic and every Compose face alike. Fed live points via [onTouchMove] while a touch is
     *  unclassified, revealed only once a swipe actually fires (see [onUpwardsSwipe] and
     *  siblings), gated by [swipeTrailEnabled]. */
    private val swipeTrail by lazy {
        SwipeTrailDrawable(ResourcesCompat.getColor(resources, com.svartifoss.snfell.common.R.color.music_screen_ripple, null))
    }

    /** Whether the *currently active* config (playing or stopped, whichever is live) has at
     *  least one swipe gesture assigned - recomputed by [buttonConfigObserver] on every config
     *  swap, exactly the condition the user asked for: a screen with nothing assigned to swipes
     *  never shows the trail, one with at least one assignment always does, regardless of which
     *  direction was actually performed. */
    private var swipeTrailEnabled = false

    /** Set once a swipe is actually recognized during the current touch sequence, so
     *  [onTouchUp] knows whether to [SwipeTrailDrawable.discard] the just-recorded path (a tap,
     *  long-press, or quadrant gesture shouldn't leave a stray trail) or leave the just-revealed
     *  fade alone. */
    private var swipeTrailRevealedThisTouch = false

    private var paletteGeneration = 0
    private var lastPaletteArt: Bitmap? = null

    /**
     * The accent source the cached [lastPaletteArt] extraction was run with.
     *
     * Without this the cache is keyed on the bitmap alone, and changing
     * [MiscPreferences.WEAR_ALBUM_ACCENT_SOURCE] does nothing until the track changes: the
     * preference observer does re-run [updateDynamicAccentFromArt], but the artwork is the same
     * object, so it returns early and the raw accent stays whatever the previous source picked.
     * The treatment/modifier/hue-shift settings are unaffected by that cache because they are
     * applied *after* extraction, over the stored raw accent - this one decides the raw accent
     * itself, so it has to invalidate.
     */
    private var lastPaletteAccentSource: AlbumAccentSource? = null
    private var lastKnownDurationMs: Long = 0L
    private var lastKnownPositionMs: Long = 0L
    private var pendingRotarySeekFraction: Float? = null
    private var latestAlbumArt: Bitmap? = null
    private var latestSourceIcon: Bitmap? = null
    private var latestSourceIconTemplate = false

    /** Last playback rate pushed into the face state, so an unchanged one costs no state copy -
     *  this observer runs on every playback change and the rate almost never moves. */
    private var latestPlaybackSpeed = 1f
    private var rawAccentColor: Int = 0
    private var rawSecondaryAccentColor: Int = 0
    private var rawTertiaryAccentColor: Int = 0
    private var currentAccentColor: Int = 0
    /** Additional quantized colours taken from real album-art pixels. Multi-colour surfaces use
     * these instead of manufacturing a complementary/opposite hue from [currentAccentColor]. */
    private var currentSecondaryAccentColor: Int = 0
    private var currentTertiaryAccentColor: Int = 0
    private var shuffleEnabled: Boolean = false
    private var repeatMode: Int = 0
    private var liked: Boolean = false
    /** One policy drives every interactive accent consumer. Legacy per-target fields are read
     * only by [resolveLegacyColorTreatment] when a phone has not migrated them yet. */
    private var colorTreatment = "expressive"
    private var normalColor = ""
    private var normalColorMulti = true
    private var colorModifier = ColorModifier.NONE
    /** Degrees the whole album-derived palette is turned by - see [MiscPreferences.WEAR_COLOR_HUE_SHIFT]. */
    private var colorHueShift = 0f

    /** Which cover swatch becomes the accent - see [MiscPreferences.WEAR_ALBUM_ACCENT_SOURCE]. */
    private var albumAccentSource = AlbumAccentSource.BALANCED
    private var titleColorMode = MiscPreferences.TITLE_COLOR_FACE_DEFAULT
    private var titleCustomColor = ""
    private var titleAdaptiveContrast = false
    /** Resolved title colour, or 0 while the title keeps the face's own. */
    private var titleAccentColor = 0
    private var artistColorMode = "follow"
    private var artistCustomColor = ""
    private var artistLegacyDesaturated = false
    /** MiscPreferences.WEAR_ARTIST_ADAPTIVE_CONTRAST - see [resolvedArtistTextColor]. */
    private var artistAdaptiveContrast = false
    private var clockAdaptiveContrast = false
    /** Per-face MiscPreferences.ALWAYS_SHOW_TIME. A field rather than a local because the
     *  clock ticker in the Handler below has to consult the same resolved value. */
    private var alwaysDisplayClock = false
    private var progressColorMode = "follow"
    private var progressCustomColor = ""
    private var progressLegacyDesaturated = false
    private var volumeColorMode = "follow"
    private var volumeCustomColor = ""
    private var quickPanelColorMode = "follow"
    private var quickPanelCustomColor = ""

    private var artistAccentColor = 0
    private var progressAccentColor = 0
    private var progressSecondaryAccentColor = 0
    private var progressTertiaryAccentColor = 0
    private var volumeAccentColor = 0
    private var volumeSecondaryAccentColor = 0
    private var volumeTertiaryAccentColor = 0
    private var quickPanelAccentColor = 0
    private var quickPanelSecondaryAccentColor = 0
    private var quickPanelTertiaryAccentColor = 0

    /** Synced from the phone (see [MiscPreferences.WEAR_TRACK_TIME_MODE]): "always", "playing",
     *  "paused" or "never". Combined with [isMusicPlaying]/[hasPlaybackPosition] in
     *  [updatePlaybackTimeVisibility] - the single place that decides whether the track time
     *  ("1:23 / 3:45") line shows. */
    private var trackTimeMode: String = "always"
    private var isMusicPlaying: Boolean = false
    private var hasPlaybackPosition: Boolean = false

    private val defaultSeekBarColor by lazy { getColor(R.color.theme_accent) }

    private lateinit var preferences: SharedPreferences

    /** Language this instance's Resources were built with; a sync that changes it forces a
     *  recreate. Captured in [attachBaseContext], which runs before anything else. */
    private var createdLanguageTag: String = AppLocales.SYSTEM

    override fun attachBaseContext(newBase: Context) {
        createdLanguageTag = WatchLanguage.storedTag(newBase)
        super.attachBaseContext(WatchLanguage.attach(newBase))
    }

    private val viewModel: MusicViewModel by viewModels()

    private var rotatingInputDisabledUntil = 0L

    /** Whether any mini-button slot currently has an action, so ambient exit knows whether to
     *  bring the row back. */
    private var screenButtonsConfigured = false

    /** Mini-button appearance, all synced from the phone (see MiscPreferences): continuous tilt
     *  (0-100, [applyScreenButtonsCurvature]), pill background and color source
     *  ([styleScreenButtons]). */
    private var screenButtonsCurveStyle = "flat"
    private var screenButtonsBgStyle = "glass"
    private var screenButtonsShape = "pill"
    private var screenButtonsOpacity = 1f
    /** Tint policy received with each visible mini-button icon. Gallery/app artwork remains
     * full-colour; shared vectors and picker glyphs adapt to the selected pill surface. */
    private val screenButtonIconTintable = HashMap<Int, Boolean>(ScreenButtons.ALL_SLOTS.size)
    /** The configured mini buttons in on-screen order, ready for a face that hosts the row (see
     *  [MiniButtonPlacement.isHostedByFace]). Rebuilt whenever the button config changes; whether
     *  it actually reaches the face is decided by [syncScreenButtonsVisibility], the one gate. */
    private var configuredMiniButtons: List<FaceMiniButton> = emptyList()
    /** Automatic round-safe resting margin (px) for the mini-button row, recomputed in
     *  [configureScreenButtonsGeometry]. There is no user position preference. */
    private var autoBottomMarginPx = 0

    /** What each of the three quick-panel button positions does. An unset [QuickPanelButtons]
     *  slot keeps the position's classic default (like/shuffle/repeat with its state ring);
     *  Like/Shuffle/Repeat assignments keep the ring in whatever slot they land; NullAction
     *  hides the slot; anything else is a plain trigger for that action. */
    private enum class QuickSlotMode { LIKE, SHUFFLE, REPEAT, CUSTOM, SESSION, HIDDEN }

    private val quickSlotModes = arrayOf(QuickSlotMode.LIKE, QuickSlotMode.SHUFFLE, QuickSlotMode.REPEAT)
    /** Whether each round slot currently shows a real, already-colored icon (a rasterized
     *  notification bitmap or a user-picked custom action icon) rather than one of this app's own
     *  single-color glyphs. Real icons must never be re-tinted - see [setQuickActionButtonActive]. */
    private val quickSlotUsesRealIcon = BooleanArray(quickSlotModes.size)
    /** Whether each round slot is currently showing the *playing app's own* rasterized icon, as
     *  opposed to one of this app's semantic fallback glyphs. Deliberately separate from
     *  [quickSlotUsesRealIcon]: these icons are white templates and do want the panel's tint, but
     *  they must not be painted over. The repeat slot used to be overwritten with this app's own
     *  repeat glyph unconditionally, purely so repeat-one could be shown - which replaced the
     *  player's own artwork on the one surface whose whole promise is that it mirrors the player.
     *  A player publishes its repeat button per state anyway, so its icon already says which. */
    private val sessionSlotShowsAppIcon = BooleanArray(quickSlotModes.size)
    /** Same distinction as [quickSlotUsesRealIcon], for the full-width row's icon. */
    private var quickActionUpNextUsesRealIcon = false
    /** Selected quick-actions panel style (see [MiscPreferences.WEAR_QUICK_PANEL_STYLE]):
     *  "glass"/"minimal"/"material"/"tonal". Themes the round slot buttons and the long row. */
    private var quickPanelStyle: String = "glass"
    /** Dedicated Up Next pill background style (MiscPreferences.WEAR_UP_NEXT_PILL_STYLE); "follow"
     *  defers to [quickPanelStyle]. See [upNextPillBackground]. */
    private var upNextPillStyle: String = "follow"
    /** Whether pills that have their own cover art should be filled with it (see
     *  QueueStyle.COVER). Driven by the queue style, which acts as the shared list-style choice. */
    private var coverPillsActive = false
    /** Which cover variation is active, so the View-side pills match the queue's rows. */
    private var coverPillStyle: QueueStyle = QueueStyle.COVER
    /** Whether an eligible quick-panel action row (genuine fetched cover art, not just any
     *  non-tintable icon) is also allowed to fill its pill with it - opt-in, on top of
     *  [coverPillsActive], since not every user wants shortcut rows turned into cover art. */
    private var quickPanelShortcutCoverEnabled = false
    private var listRowSize: QueueRowSize = QueueRowSize.NORMAL
    private var quickPanelLayout: String = "stacked"
    /** "manual" uses assigned slots; "session" mirrors actions exposed by the active player. */
    private var quickPanelSource: String = "manual"
    private var seekOverlayStyle: String = "plain"
    private var seekPanelLayout: String = "edge"

    /** At most three notification/MediaSession actions received from the active phone player. */
    private var sessionQuickActions: List<MediaAction> = emptyList()
    /** The ranked subset currently painted into the three fixed session buttons. Click dispatch
     *  and active-state tinting must use this exact order, never the publisher's original list. */
    private var displayedSessionQuickActions: List<MediaAction> = emptyList()
    private data class CachedSessionQuickIcon(val png: ByteArray, val bitmap: Bitmap)
    private val sessionQuickIconBitmaps = HashMap<String, CachedSessionQuickIcon>()
    private var quickPanelSlots: Array<ButtonAction?> = arrayOfNulls(QuickPanelButtons.ALL_SLOTS.size)

    /** The panel's long row (see [QuickPanelButtons.SLOT_LONG]): default Up Next when unset,
     *  hidden on NullAction, otherwise a full-width trigger for the assigned action. */
    private enum class QuickLongMode { UP_NEXT, CUSTOM, SESSION, HIDDEN }

    private var quickPanelLongSlot: ButtonAction? = null
    private var quickPanelLongMode = QuickLongMode.UP_NEXT
    /** The phone's configurable Actions-menu entries, repeated as large rows below Up Next. */
    private var quickPanelExtraActions: List<ButtonAction> = emptyList()

    private val serviceConnection = UiOpenServiceConnection(lifecycle)

    private fun updateFaceState(transform: (NowPlayingFaceState) -> NowPlayingFaceState) {
        faceState.value = transform(faceState.value)
    }

    /** Face events route into the exact same pipelines the classic face's inputs use, so both
     *  faces behave identically (haptics, optimistic state, quick panel, queue long-press). */
    private val expressiveFaceListener = object : NowPlayingFaceListener {
        override fun onPlayPauseTap() {
            buzz()
            // Follow the user's Controls config for the center tap if one is set, otherwise fall
            // back to the default play/pause toggle (same idiom as onSkipPreviousTap/onSkipNextTap).
            if (!viewModel.executeAction(ButtonInfo(false, CenterButton.TAP, GESTURE_SINGLE_TAP))) {
                viewModel.togglePlayPause()
            }
        }

        override fun onCenterDoubleTap() {
            buzz()
            if (isQuickActionsPanelShowing()) {
                hideOverlay()
            } else {
                showQuickActionsPanel()
            }
        }

        override fun onCenterLongPress() {
            performCenterLongPress()
        }

        override fun onSkipPreviousTap() {
            buzz()
            // Follow the user's Controls config: run the LEFT quadrant's action if one is set,
            // otherwise fall back to the default previous-track behaviour.
            if (!viewModel.executeAction(ButtonInfo(false, ScreenQuadrant.LEFT, GESTURE_SINGLE_TAP))) {
                viewModel.skipPrevious()
            }
        }

        override fun onSkipNextTap() {
            buzz()
            if (!viewModel.executeAction(ButtonInfo(false, ScreenQuadrant.RIGHT, GESTURE_SINGLE_TAP))) {
                viewModel.skipNext()
            }
        }

        override fun onMiniButtonTap(slotCode: Int) {
            // Identical to the View row's own click listener: the face resolved nothing, it only
            // said which slot was pressed.
            if (viewModel.executeAction(ButtonInfo(false, slotCode, GESTURE_SINGLE_TAP))) {
                buzz()
            }
        }

        override fun onMiniButtonLongPress(slotCode: Int) {
            if (viewModel.executeAction(ButtonInfo(false, slotCode, GESTURE_LONG_TAP))) {
                buzz()
            }
        }

        override fun onQueueTap() {
            buzz()
            startActivity(Intent(this@MainActivity, QueueActivity::class.java))
        }

        override fun onVolumeTap() {
            buzz()
            showVolumeBar()
        }

        override fun onOverflowTap() {
            buzz()
            startMenu(showCustomList = false)
        }

        override fun onSwipeUp() = this@MainActivity.onUpwardsSwipe()

        override fun onSwipeDown() = this@MainActivity.onDownwardsSwipe()

        override fun onSwipeLeft() = this@MainActivity.onSwipeLeft()

        override fun onSeek(fraction: Float) {
            viewModel.seekTo(fraction)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // "Stop"/"Force stop" on the phone's notification now ends the watch app too; the player
        // is the bottom of the task, so closing here takes the queue/menu/picker with it.
        WatchAppShutdown.closeOn(this, this)

        binding.fourWayTouch.listener = this
        binding.composeTapPulse.setImageDrawable(composeTapPulse)
        binding.swipeTrailOverlay.setImageDrawable(swipeTrail)
        binding.seekBar.onSeekPreview = { fraction -> showSeekOverlay(fraction) }
        binding.seekBar.onSeekFinished = { fraction ->
            viewModel.seekTo(fraction)
            hideOverlay()
        }
        binding.volumeBar.onVolumeChanged = { fraction ->
            viewModel.updateVolume(fraction)
            showVolumeBar()
        }
        binding.seekBar.excludedTouchViews = listOf(
                binding.iconTop,
                binding.iconBottom,
                binding.iconLeft,
                binding.iconRight,
                binding.screenButton1,
                binding.screenButton2,
                binding.screenButton3
        )

        binding.expressiveFace.setContent {
            // One ComposeView hosts every Compose face; composeFaceKind picks which one renders
            // (the interactive screenFace normally, or the AOD style's face while ambient - see
            // applyScreenFaceNow/applyAmbientPresentation).
            when (composeFaceKind.value) {
                "vinyl" -> VinylFace(state = faceState.value, listener = expressiveFaceListener)
                "poster" -> PosterFace(state = faceState.value, listener = expressiveFaceListener)
                "studio" -> StudioFace(state = faceState.value, listener = expressiveFaceListener)
                "halo" -> HaloFace(state = faceState.value, listener = expressiveFaceListener)
                "aurora" -> AuroraFace(state = faceState.value, listener = expressiveFaceListener)
                "eclipse" -> EclipseFace(state = faceState.value, listener = expressiveFaceListener)
                "spectrum" -> SpectrumFace(state = faceState.value, listener = expressiveFaceListener)
                "material" -> MaterialFace(state = faceState.value, listener = expressiveFaceListener)
                "immersive" -> ImmersiveFace(state = faceState.value, listener = expressiveFaceListener)
                "depth" -> DepthFace(state = faceState.value, listener = expressiveFaceListener)
                "chat" -> ChatFace(state = faceState.value, listener = expressiveFaceListener)
                "split" -> SplitFace(state = faceState.value, listener = expressiveFaceListener)
                "note" -> NoteFace(state = faceState.value, listener = expressiveFaceListener)
                "verse" -> VerseFace(state = faceState.value, listener = expressiveFaceListener)
                "metadata" ->
                        MetadataFace(state = faceState.value, listener = expressiveFaceListener)
                "carousel" -> CarouselFace(
                        state = faceState.value,
                        listener = expressiveFaceListener,
                        cardShape = carouselCardShape.value)
                // Chrono is an AOD-only face (the awake screen never selects it), rendered in the
                // same ComposeView while ambient - see applyAmbientPresentation.
                "chrono" -> ChronoAmbientFace(state = faceState.value)
                else -> ExpressiveFace(state = faceState.value, listener = expressiveFaceListener)
            }
        }

        val centerTapGestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            // Without this, the detector's default onDown() (false) would make onTouchEvent()
            // return false for ACTION_DOWN, so the view never sees the rest of the gesture -
            // the touch would fall through to FourWayTouchLayout underneath instead.
            override fun onDown(e: MotionEvent): Boolean {
                pulseCenterTapFeedback()
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                buzz()
                if (!viewModel.executeAction(ButtonInfo(false, CenterButton.TAP, GESTURE_SINGLE_TAP))) {
                    viewModel.togglePlayPause()
                }
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                buzz()
                if (isQuickActionsPanelShowing()) {
                    hideOverlay()
                } else {
                    showQuickActionsPanel()
                }
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                performCenterLongPress()
            }

            // The zone consumes the whole touch stream from onDown() on, so a swipe that happens
            // to start on it would otherwise just die here instead of reaching FourWayTouchLayout
            // underneath - mirror its fling handling (same thresholds, same left-only horizontal
            // rule: rightward swipes stay reserved for the system dismiss gesture).
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (abs(velocityX) > abs(velocityY)) {
                    if (velocityX < 0 && abs(velocityX) > FourWayTouchLayout.SWIPE_MIN_VELOCITY) {
                        onSwipeLeft()
                        return true
                    }
                } else if (abs(velocityY) > FourWayTouchLayout.SWIPE_MIN_VELOCITY) {
                    if (velocityY < 0) {
                        onUpwardsSwipe()
                    } else {
                        onDownwardsSwipe()
                    }
                    return true
                }
                return false
            }
        })
        binding.centerTapZone.setOnTouchListener { _, event -> centerTapGestureDetector.onTouchEvent(event) }

        // Tapping outside the panel or swiping down both remain convenient secondary dismiss
        // paths; the standard rightward back gesture is handled by quickActionsDismissFrame.
        val overlayDismissGestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (isQuickActionsPanelShowing()) {
                    hideOverlay()
                }
                return true
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (isQuickActionsPanelShowing() && velocityY > 400f && velocityY > abs(velocityX)) {
                    hideOverlay()
                    return true
                }
                return false
            }
        })
        binding.overlayBackdrop.setOnTouchListener { _, event ->
            overlayDismissGestureDetector.onTouchEvent(event)
        }

        // Consume the standard rightward Wear gesture inside Quick Actions and dismiss only the
        // overlay. Without a swipe host the gesture escaped the ScrollView and closed MainActivity.
        // Enable it explicitly: the widget otherwise inherits the Activity/window navigation
        // policy, which varies between Wear versions and left some devices dismissing the app.
        binding.quickActionsDismissFrame.isSwipeable = true
        binding.quickActionsDismissFrame.addCallback(
                object : SwipeDismissFrameLayout.Callback() {
                    override fun onDismissed(layout: SwipeDismissFrameLayout) {
                        if (isQuickActionsPanelShowing()) hideOverlay()
                        // SwipeDismissFrameLayout normally hosts an Activity that is destroyed.
                        // This one is reusable, so restore its transformed surface for next open.
                        layout.post {
                            layout.translationX = 0f
                            layout.alpha = 1f
                        }
                    }
                }
        )
        binding.quickActionsPanel.setOnDismissRequestListener {
            if (isQuickActionsPanelShowing()) hideOverlay()
        }

        binding.quickActionLike.setOnTouchListener(quickActionPressFeedback)
        binding.quickActionShuffle.setOnTouchListener(quickActionPressFeedback)
        binding.quickActionRepeat.setOnTouchListener(quickActionPressFeedback)

        quickPanelViews().forEachIndexed { index, panelButton ->
            panelButton.setOnClickListener { onQuickPanelButtonClicked(index) }
        }
        binding.quickActionUpNext.setOnClickListener {
            when (quickPanelLongMode) {
                QuickLongMode.UP_NEXT -> {
                    buzz()
                    hideOverlay()
                    // Opens the new Compose queue screen (swipe-to-dismiss closes just it).
                    // QueueActivity requests the queue itself, so no need to prime the old
                    // drawer list here.
                    startActivity(Intent(this, QueueActivity::class.java))
                }
                QuickLongMode.CUSTOM -> {
                    buzz()
                    hideOverlay()
                    viewModel.executeAction(
                            ButtonInfo(false, QuickPanelButtons.SLOT_LONG, GESTURE_SINGLE_TAP))
                }
                QuickLongMode.SESSION -> sessionQuickActions.getOrNull(3)?.let {
                    buzz()
                    viewModel.sendQuickAction("session:${it.id}")
                }
                QuickLongMode.HIDDEN -> Unit
            }
        }

        for ((slotCode, slotView) in screenButtonViews()) {
            slotView.setOnTouchListener(quickActionPressFeedback)
            slotView.setOnClickListener {
                if (viewModel.executeAction(ButtonInfo(false, slotCode, GESTURE_SINGLE_TAP))) {
                    buzz()
                }
            }
            slotView.setOnLongClickListener {
                if (viewModel.executeAction(ButtonInfo(false, slotCode, GESTURE_LONG_TAP))) {
                    buzz()
                    true
                } else {
                    false
                }
            }
        }

        // Anything that changes the vertical space available to the mini buttons - the title
        // wrapping to two lines, the playback time appearing, the bottom quadrant icon
        // toggling, the row's own size/margin changing - re-runs the smart placement.
        val screenButtonsLayoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            repositionScreenButtonsRow()
        }
        listOf(binding.textArtist, binding.textTitle, binding.textPlaybackTime,
                binding.iconBottom, binding.screenButtonsRow).forEach {
            it.addOnLayoutChangeListener(screenButtonsLayoutListener)
        }

        // Title's floor (22sp) is kept comfortably above artist's ceiling (16sp) so the title
        // stays visually dominant even when a long title has to shrink to fit two lines.
        // applyClassicTypography re-applies these with the user's size scale once preferences load.
        binding.textArtist.enableSmartWordSizing(
                maxSizeSp = CLASSIC_ARTIST_MAX_SP, minSizeSp = CLASSIC_ARTIST_MIN_SP)
        binding.textTitle.enableSmartWordSizing(
                maxSizeSp = CLASSIC_TITLE_MAX_SP, minSizeSp = CLASSIC_TITLE_MIN_SP)
        // Same idea for the quick-actions panel's copy of the title/artist - without this a long
        // title just sat there clipped instead of shrinking a bit and then scrolling.
        binding.quickActionPanelTitle.enableSmartWordSizing(maxSizeSp = 18f, minSizeSp = 15f)
        binding.quickActionPanelArtist.enableSmartWordSizing(maxSizeSp = 13f, minSizeSp = 11f)

        binding.notificationPopup.clickableFrame.setOnClickListener { onNotificationTapped() }

        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        appearanceContext = ThemeAppearance.resolve(preferences)
        screenFace = appearanceContext.baseFace
        ambientObserver = AmbientLifecycleObserver(this, ambientCallback)
        lifecycle.addObserver(ambientObserver)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        maybeRequestNotificationPermission()

        stemButtonsManager = StemButtonsManager(WatchInfoSender.getAvailableButtonsOnWatch(this), stemButtonListener, lifecycleScope)

        viewModel.albumArt.observe(this, albumArtObserver)
        viewModel.sourceIcon.observe(this, sourceIconObserver)
        viewModel.currentButtonConfig.observe(this, buttonConfigObserver)
        // Preference delivery must remain active while the activity is in ambient/paused state.
        // A lifecycle-bound observer can be suspended there, making an already-received phone
        // edit appear only after the next touch wakes the activity back to STARTED. The observer
        // is explicitly removed in onDestroy, so it cannot retain a dead Activity.
        viewModel.preferences.observeForever(preferencesChangeObserver)
        viewModel.volume.observe(this, phoneVolumeListener)
        viewModel.popupVolumeBar.observe(this, volumeBarPopupListener)
        viewModel.openActionsMenu.observe(this, openActionsMenuListener)
        viewModel.openPlaybackQueueScreen.observe(this, openPlaybackQueueScreenListener)
        viewModel.openLyricsScreen.observe(this, openLyricsScreenListener)
        viewModel.lyricsState.observe(this, lyricsStateObserver)
        viewModel.trackMetadata.observe(this, trackMetadataObserver)
        viewModel.openStreamingShortcutsMenu.observe(this, openStreamingShortcutsMenuListener)
        viewModel.openVoiceSearch.observe(this, openVoiceSearchListener)
        viewModel.closeApp.observe(this, closeAppListener)
        viewModel.notification.observe(this, notificationObserver)
        viewModel.customList.observe(this, customListListener)
        viewModel.playbackPosition.observe(this, playbackPositionObserver)
        viewModel.actionsMenuConfig.config.observe(this) { actions ->
            quickPanelExtraActions = actions.orEmpty()
            if (isQuickActionsPanelShowing()) renderQuickPanelExtraActions()
        }

        onBackPressedDispatcher.addCallback(this, backButtonOverrideCallback)
        // Registered after backButtonOverrideCallback so it takes priority while enabled - the
        // back gesture should close the quick-actions panel instead of exiting the app.
        onBackPressedDispatcher.addCallback(this, quickActionsPanelBackCallback)
        // Last registered = highest priority: Back moves through the lesson before it can exit.
        onBackPressedDispatcher.addCallback(this, firstRunHintsBackCallback)

        setupFirstRunHints()

        handleVoiceSearchIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // A fresh launch of an existing instance (this activity is singleTask, so reopening from
        // the launcher lands here rather than in onCreate) is exactly the "user opened the app"
        // moment WEAR_IDLE_AUTO_OPEN is about. Resetting in onStart instead would loop: backing
        // out of the destination returns here, sees idle, and would open it straight back up.
        idleAutoOpenConsumed = false
        // Back from the first page closes without completing. Reopening the app should offer the
        // lesson again rather than silently treating that close as completion.
        setupFirstRunHints()
        handleVoiceSearchIntent(intent)
        handleOpenLyricsIntent(intent)
    }

    /** Set by IdleMessageListener when the phone asks to open search (e.g. Search picked from
     *  the actions menu, which always executes phone-side). */
    private fun handleVoiceSearchIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_VOICE_SEARCH, false) == true) {
            intent.removeExtra(EXTRA_OPEN_VOICE_SEARCH)
            openVoiceSearchInput()
        }
    }

    /** Set by IdleMessageListener when the phone asks to open lyrics - the same phone-side paths
     *  that reach [handleVoiceSearchIntent]. */
    private fun handleOpenLyricsIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_LYRICS, false) == true) {
            intent.removeExtra(EXTRA_OPEN_LYRICS)
            openLyricsScreen()
        }
    }

    private val voiceSearchLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val query = result.data
                ?.let { RemoteInput.getResultsFromIntent(it)?.getCharSequence(KEY_SEARCH_QUERY) }
                ?.toString()

        if (!query.isNullOrBlank()) {
            viewModel.playFromSearch(query)
        }
    }

    private val openVoiceSearchListener = Observer<Unit?> {
        openVoiceSearchInput()
    }

    private fun openVoiceSearchInput() {
        val remoteInput = RemoteInput.Builder(KEY_SEARCH_QUERY)
                .setLabel(getString(R.string.voice_search_prompt))
                .build()

        val inputIntent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        RemoteInputIntentHelper.putRemoteInputsExtra(inputIntent, listOf(remoteInput))

        voiceSearchLauncher.launch(inputIntent)
    }

    private fun maybeRequestNotificationPermission() {
        // targetSdk 33+ (Android 13) gates notifications behind POST_NOTIFICATIONS. The foreground
        // WatchMusicService posts an OngoingActivity notification, so request it once to preserve
        // the pre-33 behavior of that notification simply showing. If the user denies it, the
        // service still runs - only the notification is suppressed, same as a manual denial.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_CODE_POST_NOTIFICATIONS
            )
        }
    }

    override fun onStart() {
        super.onStart()

        applyKeepScreenOnPreference()

        if (faceBool(MiscPreferences.ALWAYS_SHOW_TIME)) {
            handler.sendEmptyMessage(MESSAGE_UPDATE_CLOCK)
        }

        val crownDisableTime =
                Preferences.getInt(preferences, MiscPreferences.ROTATING_CROWN_OFF_PERIOD)
        if (crownDisableTime > 0) {
            rotatingInputDisabledUntil = System.currentTimeMillis() + crownDisableTime
        }

        viewModel.updateTimers()
        hideNotificationIfOverdue()

        bindService(Intent(this, WatchMusicService::class.java), serviceConnection, BIND_AUTO_CREATE)
    }

    override fun onStop() {
        if (isFinishing) {
            viewModel.sendManualCloseMessage()
        }

        super.onStop()

        handler.removeMessages(MESSAGE_UPDATE_CLOCK)
        unbindService(serviceConnection)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        // onStop will trigger when screen turns off (But app stays in foreground)
        // and thus disable data transmission

        // Keep one observer alive as long as app has focus.

        if (hasFocus) {
            viewModel.musicState.observeForever(musicStateObserver)
        } else {
            viewModel.musicState.removeObserver(musicStateObserver)
        }

        // While anything else is on top (the queue/menu during their open transition, system
        // dialogs, ...) there's no point animating here: the title marquee and the 500ms
        // position ticker driving the progress ring would just burn battery - and if the window
        // above is see-through, force the compositor to re-blend both layers each frame. Freeze
        // while unfocused; ambient mode manages its own pause/resume via the ambient callbacks.
        if (!ambientObserver.isAmbient) {
            binding.textArtist.setMarqueePaused(!hasFocus)
            binding.textTitle.setMarqueePaused(!hasFocus)
            viewModel.setContinuousPositionTicking(hasFocus)
        }
    }

    override fun onDestroy() {
        viewModel.preferences.removeObserver(preferencesChangeObserver)
        super.onDestroy()

        viewModel.musicState.removeObserver(musicStateObserver)
    }

    /** Follows the system 12/24h setting, but never appends AM/PM - the suffix just adds
     *  clutter without information on a watch-sized clock. */
    private fun updateClock() {
        val pattern = if (android.text.format.DateFormat.is24HourFormat(this)) "HH:mm" else "h:mm"
        val time = java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
                .format(java.util.Date())
        binding.ambientClock.text = time
        // Keep the Chrono ambient face's large Compose clock in sync with the per-minute update.
        if (faceState.value.clockText != time) {
            updateFaceState { it.copy(clockText = time) }
        }
    }

    private fun updateDeveloperOverlay() {
        val showBounds = Preferences.getBoolean(
                preferences, MiscPreferences.WEAR_DEV_SHOW_LAYOUT_BOUNDS)
        val showInfo = Preferences.getBoolean(
                preferences, MiscPreferences.WEAR_DEV_SHOW_PLAYER_INFO)
        binding.developerOverlay.apply {
            showLayoutBounds = showBounds
            showPlayerInfo = showInfo
            visibility = if (showBounds || showInfo) View.VISIBLE else View.GONE
            if (showInfo) {
                val percent = if (lastKnownDurationMs > 0L) {
                    (lastKnownPositionMs * 100L / lastKnownDurationMs).coerceIn(0L, 100L)
                } else {
                    0L
                }
                playerInfo = buildString {
                    append("face=").append(screenFace)
                    append("  theme=").append(screenTheme.name.lowercase())
                    append('\n').append("playing=").append(isMusicPlaying)
                    append("  ambient=").append(ambientObserver.isAmbient)
                    append('\n').append("position=")
                            .append(formatPlaybackTime(lastKnownPositionMs))
                            .append('/').append(formatPlaybackTime(lastKnownDurationMs))
                            .append("  ").append(percent).append('%')
                    append('\n').append("seekable=").append(playbackSeekable)
                    append("  overlay=").append(activeOverlayKind?.name?.lowercase() ?: "none")
                    append('\n').append("colors=").append(colorTreatment)
                    append("  actions=").append(sessionQuickActions.size)
                }
            }
        }
    }

    private val musicStateObserver = Observer<Resource<MusicState>?> {
        val previousFaceTitle = faceState.value.title
        Timber.d("GUI Music State %s %s", it?.status, it?.data)
        if (it == null || it.status == Resource.Status.LOADING) {
            binding.loadingIndicator.visibility = View.VISIBLE
            return@Observer
        }

        binding.loadingIndicator.visibility = View.GONE

        isMusicPlaying = it.status == Resource.Status.SUCCESS && it.data?.playing == true
        sessionQuickActions = if (it.status == Resource.Status.SUCCESS) {
            it.data?.mediaActionsList.orEmpty()
        } else {
            emptyList()
        }
        sessionQuickIconBitmaps.keys.retainAll(sessionQuickActions.mapTo(HashSet()) { action -> action.id })
        if (isQuickActionsPanelShowing()) {
            configureQuickPanelButtons()
        }
        updatePlaybackTimeVisibility()
        syncUpNextEqualizerAnimation()

        // "Idle": connected fine, but there is no track at all (as opposed to a *paused* track,
        // which keeps the normal title + "Playback Stopped" presentation). Shows the branded
        // equalizer + hint instead of a bare status line on an empty screen.
        val idle = it.status == Resource.Status.SUCCESS &&
                (it.data == null || (it.data?.playing != true && it.data?.title.isNullOrBlank()))

        // Whether the phone's source icon is a tintable monochrome template (notification small
        // icon) or full-colour launcher artwork. Drives the glyph's tint on every face.
        (it.data as? MusicState)?.let { state ->
            if (state.sourceIconTemplate != latestSourceIconTemplate) {
                latestSourceIconTemplate = state.sourceIconTemplate
                updateFaceState { face -> face.copy(sourceIconTemplate = state.sourceIconTemplate) }
            }
            if (state.playbackSpeed != latestPlaybackSpeed) {
                latestPlaybackSpeed = state.playbackSpeed
                updateFaceState { face -> face.copy(playbackSpeed = state.playbackSpeed) }
            }
        }

        if (it.status == Resource.Status.SUCCESS && it.data != null && !idle) {
            titleLineIsStatus = false
            if ((it.data as MusicState).playing) {
                // Restores the dynamic (palette-extracted) color after a stopped/error message
                // may have forced it to plain white below.
                binding.textArtist.setTextColor(resolvedArtistTextColor())
                binding.textArtist.text = it.data?.artist
            } else {
                setStatusMessageOnArtistLine(getString(R.string.playback_stopped))
            }
            // After the artist colour is set: a template source icon is tinted to whatever colour
            // the artist line now shows, so it follows the per-track palette instead of a stale
            // colour. (It used to be applied only on the source-icon-template *flag* change, which
            // ran before this and left the glyph the wrong colour.)
            applyClassicSourceIcon()

            binding.textTitle.text = it.data?.title
            updateRecentsLabel((it.data as MusicState).title)

            shuffleEnabled = it.data?.shuffleEnabled == true
            repeatMode = it.data?.repeatMode ?: 0
            liked = it.data?.liked == true
            updateQuickActionButtonStates()
        } else if (it.status == Resource.Status.ERROR) {
            titleLineIsStatus = true
            setStatusMessageOnArtistLine(getString(R.string.error))
            binding.textTitle.text = it.message
            updateRecentsLabel(null)

            val errorData = it.errorData
            if (errorData is GooglePlayServicesRepairableException) {
                GoogleApiAvailability.getInstance().getErrorDialog(this, errorData.connectionStatusCode, 1)?.show()
            }
        } else {
            titleLineIsStatus = false
            binding.textArtist.text = ""
            binding.textTitle.text = ""
            updateRecentsLabel(null)
        }

        setIdleStateVisible(idle)

        applyMetadataVisibility(idle)
        val trackChanged = !idle && faceState.value.title != previousFaceTitle
        if (trackChanged) {
            // Advance immediately from the cached queue (the title matcher handles players whose
            // activeQueueItemId lags behind), then refresh in the background. Queue data is now
            // warm before either AOD or Quick Actions is opened instead of being fetched only as
            // a side effect of showing that panel.
            viewModel.customList.value?.let(::updateUpNextPreview)
            viewModel.refreshPlaybackQueueSilently()
        }
        if (ambientObserver.isAmbient) {
            // Playback/status updates can write the interactive white/artist colors above while
            // the device is ambient. Reassert the AOD palette after the metadata text changes.
            applyAmbientViewColors()
        }
        updateDeveloperOverlay()
    }

    /** Applies the independent title/artist choices without suppressing playback/error status. */
    private fun applyMetadataVisibility(idle: Boolean = faceState.value.idle) {
        val title = binding.textTitle.text?.toString().orEmpty()
        val artist = binding.textArtist.text?.toString().orEmpty()
        val artistLineIsStatus = !isMusicPlaying && artist.isNotEmpty()
        val visibility = resolveMetadataVisibility(
                title = title,
                artist = artist,
                showTitle = showTrackTitle,
                showArtist = showTrackArtist,
                titleIsStatus = titleLineIsStatus,
                artistIsStatus = artistLineIsStatus
        )

        binding.textTitle.visibility = if (visibility.title) View.VISIBLE else View.GONE
        binding.textArtist.visibility = if (visibility.artist) View.VISIBLE else View.GONE
        applyClassicFont(title, artist)

        updateFaceState { face ->
            face.copy(
                    title = title,
                    artist = artist,
                    showTitle = visibility.title,
                    showArtist = visibility.artist,
                    artistColor = binding.textArtist.currentTextColor,
                    titleColor = resolvedTitleTextColor(),
                    playing = isMusicPlaying,
                    idle = idle,
                    titleIsStatus = titleLineIsStatus,
                    artistIsStatus = artistLineIsStatus
            )
        }
        syncScreenButtonsVisibility()
    }

    /** Mirrors [NowPlayingFaceState.titleFont] for the View-based classic face: the Lain keyword
     *  easter egg wins over the user's [wearFontKey] choice, matching every Compose face exactly. */
    private fun applyClassicFont(title: String, artist: String) {
        val effectiveKey = if (lainFont(title) != null || lainFont(artist) != null) {
            "love_letter"
        } else {
            wearFontKey
        }
        if (WatchTypography.isFlexFont(effectiveKey)) {
            // Flex needs its own real variable-font instance per element (wght/slnt from that
            // element's spec, plus the shared wdth/opsz/GRAD/ROND) rather than the generic
            // weight-matching styledClassicTypeface applies to every other font - see flexTypeface.
            binding.textTitle.typeface = flexTypeface(this, titleTypography, flexAxes)
            binding.textArtist.typeface = flexTypeface(this, artistTypography, flexAxes)
            return
        }
        val base = watchFontTypeface(this, effectiveKey)
        binding.textTitle.typeface = styledClassicTypeface(base, titleTypography)
        binding.textArtist.typeface = styledClassicTypeface(base, artistTypography)
        applyQuickPanelFont()
    }

    /**
     * Extends the font choice to the quick-actions panel when "Use font everywhere" is on.
     *
     * The panel is a View overlay, not one of the Compose screens, so [LocalWatchUiFontFamily] never
     * reached it - which is why turning that switch on restyled the menu and the queue but left this
     * one surface on Google Sans. Its labels carry no per-element typography of their own, so the
     * plain family is applied without the title/artist weight and slant specs.
     */
    /** Null means "leave the layout's own typeface", i.e. the switch is off. */
    private fun quickPanelTypeface(): android.graphics.Typeface? =
            if (Preferences.getBoolean(preferences, MiscPreferences.WEAR_FONT_ALL_SCREENS)) {
                watchFontTypeface(this, wearFontKey)
            } else {
                null
            }

    private fun applyQuickPanelFont() {
        val typeface = quickPanelTypeface()
        binding.quickActionPanelTitle.typeface = typeface
        binding.quickActionPanelArtist.typeface = typeface
        binding.quickActionUpNextLabel.typeface = typeface
        binding.quickActionUpNextTrack.typeface = typeface
    }

    /**
     * [base] with the user's weight/slant applied, for the View-based classic face.
     *
     * The classic face has always drawn both lines bold, so the identity weight (400) must keep
     * producing exactly [Typeface.BOLD] - resolving it to a "normal" 400 face instead would visibly
     * lighten every existing install the moment these controls shipped. Any other weight is a
     * deliberate choice and uses the real numeric-weight API where the platform has it (API 28+),
     * falling back to the coarse bold/normal flags on older watches, which is the most those can
     * express.
     */
    private fun styledClassicTypeface(
            base: Typeface,
            spec: WatchTypography.TextSpec
    ): Typeface {
        if (spec.weight == 400) {
            return Typeface.create(base, if (spec.italic) Typeface.BOLD_ITALIC else Typeface.BOLD)
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

    /**
     * [styledClassicTypeface] for the clock, differing in exactly one place: weight 400 resolves to
     * NORMAL, not BOLD.
     *
     * Both follow the project's "a default value means keep what this element was designed as"
     * rule - the classic title and artist are drawn bold by design, the clock is not, so the same
     * identity weight has to land on a different style for each.
     */
    private fun styledClockTypeface(
            base: Typeface,
            spec: WatchTypography.TextSpec
    ): Typeface {
        if (spec.weight == 400) {
            return Typeface.create(base, if (spec.italic) Typeface.ITALIC else Typeface.NORMAL)
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

    /**
     * Applies the size/opacity/tracking half of the typography preferences to the classic face.
     * Weight and slant travel with the typeface instead ([applyClassicFont]), which also has to run
     * on every track change for the Lain easter egg.
     *
     * Opacity goes on the View's own alpha rather than into the text colour: the artist line's
     * colour is recomputed from the album palette on every metadata update
     * ([resolvedArtistTextColor]), so baking alpha into it would be overwritten on the next track.
     */
    private fun applyClassicTypography() {
        binding.textTitle.enableSmartWordSizing(
                maxSizeSp = titleTypography.scaled(CLASSIC_TITLE_MAX_SP),
                minSizeSp = titleTypography.scaled(CLASSIC_TITLE_MIN_SP))
        binding.textArtist.enableSmartWordSizing(
                maxSizeSp = artistTypography.scaled(CLASSIC_ARTIST_MAX_SP),
                minSizeSp = artistTypography.scaled(CLASSIC_ARTIST_MIN_SP))
        binding.textTitle.letterSpacing = titleTypography.trackingEm
        binding.textArtist.letterSpacing = artistTypography.trackingEm
        binding.textTitle.alpha = titleTypography.alpha
        binding.textArtist.alpha = artistTypography.alpha
        applyClassicFont(
                binding.textTitle.text?.toString().orEmpty(),
                binding.textArtist.text?.toString().orEmpty())
        applyClassicSourceIcon()
    }

    /**
     * Shows/hides the idle ("nothing playing") group and runs its equalizer animation only
     * while it is actually on screen and the display is interactive - ambient stops it (see
     * ambientCallback), both to respect the low-power mode and because AVDs don't animate
     * there anyway.
     */
    private fun setIdleStateVisible(visible: Boolean) {
        binding.idleStateGroup.visibility = if (visible) View.VISIBLE else View.GONE
        binding.playerBackground.visibility = if (
            visible || ambientObserver.isAmbient || screenFace in composeFaces
        ) View.GONE else View.VISIBLE
        if (visible) {
            applyIdleScreenConfiguration()
            maybeAutoOpenIdleDestination()
        }
        // The resume button replaced the equalizer glyph as the idle screen's focus, so the AVD
        // only runs if something has explicitly put the glyph back on screen - animating a GONE
        // view would be pure battery cost with nothing to show for it.
        val animation = binding.idleStateIcon.drawable as? Animatable ?: return
        if (visible && !ambientObserver.isAmbient &&
                binding.idleStateIcon.visibility == View.VISIBLE) {
            if (!animation.isRunning) animation.start()
        } else {
            animation.stop()
        }
    }

    /**
     * Whether this activity instance has already honoured [MiscPreferences.WEAR_IDLE_AUTO_OPEN].
     *
     * Consumed once per instance on purpose: the destination it opens (menu, shortcuts, queue)
     * comes back to an idle screen when dismissed, so re-firing on every idle state would trap the
     * user in a screen they just backed out of.
     */
    private var idleAutoOpenConsumed = false

    /**
     * Decides what the idle ("nothing playing") state looks like.
     *
     * On a Compose face the face draws it: those layouts already degrade to no-artwork - Carousel
     * to an accent card, the curated set to their own backdrops - and the status line reads
     * "playback stopped" through the shared artistOrStatus helper. Showing a separate black screen
     * over them meant selecting a face had no effect at all whenever music was not playing.
     *
     * Classic keeps the dedicated group, because Classic's own idle presentation *is* that group.
     */
    private fun applyIdleScreenConfiguration() {
        val composeFace = screenFace in composeFaces
        binding.idleStateGroup.visibility =
                if (composeFace) View.GONE else View.VISIBLE
        binding.idleStateHint.setText(R.string.idle_hint)
    }

    /** Runs the configured idle destination through the same events the button and menu use. */
    private fun runIdleScreenAction(action: IdleScreenAction) {
        when (action) {
            IdleScreenAction.RESUME -> viewModel.togglePlayPause()
            IdleScreenAction.SHORTCUTS -> viewModel.openStreamingShortcutsMenu.call()
            IdleScreenAction.MENU -> viewModel.openActionsMenu.call()
            IdleScreenAction.SEARCH -> viewModel.openVoiceSearch.call()
            IdleScreenAction.QUEUE -> viewModel.openPlaybackQueueScreen.call()
            IdleScreenAction.NONE -> Unit
        }
    }

    /**
     * Opens the user's chosen "nothing playing" destination straight away, so the app can be a
     * launcher rather than a dead end for people who always start from the same place. Skipped in
     * ambient: the screen the user is looking at then is a glance, not an interaction.
     */
    private fun maybeAutoOpenIdleDestination() {
        if (idleAutoOpenConsumed || ambientObserver.isAmbient) {
            return
        }
        val destination = IdleScreenAction.forAutoOpen(
                Preferences.getString(preferences, MiscPreferences.WEAR_IDLE_AUTO_OPEN))
        if (destination == IdleScreenAction.NONE) {
            return
        }
        idleAutoOpenConsumed = true
        runIdleScreenAction(destination)
    }

    /** The app label to fall back to in the recents/app-switcher card when nothing is playing. */
    private val defaultRecentsLabel: String by lazy {
        applicationInfo.loadLabel(packageManager).toString()
    }
    private var currentRecentsLabel: String? = null

    /**
     * Sets the task label shown for this app in the Wear OS recents (app-switcher) card to the
     * current track, so the switcher shows the song name instead of just "Svartifoss". A null/blank
     * [trackTitle] restores the app name. This is the actual lever for that surface's text - the
     * media-session metadata drives the system media control chip, not the launcher's recents card.
     */
    private fun updateRecentsLabel(trackTitle: String?) {
        val label = trackTitle?.takeIf { it.isNotBlank() } ?: defaultRecentsLabel
        if (label == currentRecentsLabel) return
        currentRecentsLabel = label
        val description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityManager.TaskDescription.Builder().setLabel(label).build()
        } else {
            // TaskDescription.Builder only exists since API 33 - the old >= P guard crashed
            // Wear OS 3 (API 30) watches with NoClassDefFoundError on every track change.
            @Suppress("DEPRECATION")
            ActivityManager.TaskDescription(label)
        }
        setTaskDescription(description)
    }

    private val sourceIconObserver = Observer<Bitmap?> { bitmap ->
        latestSourceIcon = bitmap
        updateFaceState { it.copy(sourceIcon = bitmap?.asImageBitmap()) }
        applyClassicSourceIcon()
    }

    /** Draws the source icon inline with the Classic face's artist line. A real sibling
     *  [ImageView] in the centred row (see activity_main.xml), not a compound drawable on the
     *  TextView - a start-compound drawable on a match_parent centred TextView anchors to the
     *  view's own edge rather than next to the (shorter, centred) text, which is what put it far
     *  from the artist name. Cleared in ambient for the same reason the Compose glyph
     *  ([SourceIconGlyph]) is skipped there: the AOD contract is outline-only, with no filled
     *  artwork - [inAmbient] is the reliable signal for that. */
    private fun applyClassicSourceIcon() {
        val iconView = binding.sourceIconClassic
        val icon = latestSourceIcon.takeUnless { inAmbient }
        if (icon == null) {
            iconView.visibility = View.GONE
            iconView.setImageDrawable(null)
            return
        }

        // A square box sized off the artist line's own text size, with FIT_CENTER below keeping
        // the icon's own aspect ratio inside it - forcing non-square source art into a square
        // stretched it, which made the glyph look skewed next to the artist name.
        // The box already tracks the artist line's size; the icon's own scale multiplies that, so
        // resizing the icon stays independent of resizing the text next to it.
        val box = (binding.textArtist.textSize * 1.1f * sourceIconTypography.scale)
                .toInt().coerceAtLeast(1)
        val params = iconView.layoutParams as LinearLayout.LayoutParams
        params.width = box
        params.height = box
        params.marginEnd = (binding.textArtist.textSize * .28f).toInt()
        iconView.layoutParams = params

        iconView.alpha = sourceIconTypography.alpha
        iconView.scaleType = ImageView.ScaleType.FIT_CENTER
        iconView.setImageBitmap(icon)
        // A notification small icon is a flat white template, so it has to be tinted to the
        // artist line's own colour or it reads as a foreign white blob next to accent-coloured
        // text. Launcher artwork is full-colour and must stay untinted.
        iconView.imageTintList = if (latestSourceIconTemplate) {
            ColorStateList.valueOf(binding.textArtist.currentTextColor)
        } else {
            null
        }
        iconView.visibility = View.VISIBLE
    }

    private val albumArtObserver = Observer<Bitmap?> { bitmap ->
        val previous = latestAlbumArt
        latestAlbumArt = bitmap
        // Play/pause re-syncs re-deliver the same art as a fresh Bitmap instance - compare
        // pixels so those don't re-trigger the transition (the art used to blink on every
        // pause even though nothing visually changed).
        val samePixels = previous != null && bitmap != null &&
                (previous === bitmap || previous.sameAs(bitmap))
        if (!ambientObserver.isAmbient) {
            if (albumArtFadeEnabled && previous != null && bitmap != null && !samePixels) {
                fadeToAlbumArt(bitmap)
            } else {
                applyMainAlbumArtDisplay(bitmap, forceBlur = blurAlbumArtBackground)
            }
        } else {
            applyAmbientAlbumArt()
        }
        applyBlurredAlbumArt(bitmap)
        // Faces that draw the art themselves (vinyl disc) read it from the face state. The art is
        // published together with its resolved accent inside updateDynamicAccentFromArt so the two
        // land atomically - see A4 in the blur/color coherence work.
        updateDynamicAccentFromArt(bitmap)
        // The dynamic clock colour is derived from the region under the clock, so a new cover can
        // flip it; album mode also follows the accent, refreshed just above. Cheap enough to run
        // unconditionally rather than branch on the mode.
        applyClockAppearance()
    }

    private fun updateDynamicAccentFromArt(art: Bitmap?) {
        if (!wearDynamicAccentEnabled) {
            paletteGeneration++
            lastPaletteArt = null
            applyAccentColor(
                    defaultSeekBarColor,
                    albumToneFallback(defaultSeekBarColor, .42f),
                    albumToneFallback(defaultSeekBarColor, .68f),
                    art = art,
                    publishArt = true
            )
            return
        }
        if (art == null) {
            paletteGeneration++
            lastPaletteArt = null
            applyAccentColor(
                    defaultSeekBarColor,
                    albumToneFallback(defaultSeekBarColor, .42f),
                    albumToneFallback(defaultSeekBarColor, .68f),
                    art = null,
                    publishArt = true
            )
            return
        }
        if (art === lastPaletteArt && albumAccentSource == lastPaletteAccentSource) {
            return
        }
        lastPaletteArt = art
        lastPaletteAccentSource = albumAccentSource

        val generation = ++paletteGeneration
        Palette.from(art).generate { palette ->
            if (generation != paletteGeneration || art !== lastPaletteArt) {
                return@generate
            }
            val preferredColors = palette?.let { p ->
                listOfNotNull(
                        p.getVibrantSwatch(),
                        p.getMutedSwatch(),
                        p.getLightVibrantSwatch(),
                        p.getDarkVibrantSwatch(),
                        p.getLightMutedSwatch(),
                        p.getDarkMutedSwatch(),
                        p.dominantSwatch
                ).map { it.rgb }.distinct()
            }.orEmpty()
            // Palette swatches are quantized pixels from the cover. Only trust the vibrant swatch
            // when it covers a meaningful share of the artwork, so a tiny high-contrast detail does
            // not turn a mostly-blue cover's UI red while the blurred background stays blue.
            val swatchInfos = palette?.swatches.orEmpty()
                    .map { SwatchInfo(it.rgb, it.population) }
            val primary = selectPrimaryAccent(
                    palette?.getVibrantSwatch()?.let { SwatchInfo(it.rgb, it.population) },
                    swatchInfos,
                    albumAccentSource
            ) ?: preferredColors.firstOrNull() ?: defaultSeekBarColor
            val realAlbumColors = swatchInfos
                    .sortedByDescending { it.population }
                    .map { it.rgb }
            // Named tonal swatches first (they're chosen by Palette specifically to be distinct
            // from each other), population-ranked raw swatches only as a fallback - two of the
            // most-populous swatches are often near-duplicate shades of the same dominant hue,
            // which used to make Expressive collapse into Original's single-hue look far more
            // often than a genuinely monochromatic cover would justify.
            val companions = selectAlbumCompanionColors(primary, preferredColors + realAlbumColors)
            val secondary = companions.secondary ?: albumToneFallback(primary, .42f)
            val tertiary = companions.tertiary ?: albumToneFallback(primary, .68f)

            applyAccentColor(primary, secondary, tertiary, art = art, publishArt = true)
        }
    }

    /** Same-hue fallback for monochromatic covers/static accents. It varies only luminance and
     * saturation, never inventing a second hue that was absent from the artwork. */
    private fun albumToneFallback(color: Int, lightness: Float): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[1] = hsl[1].coerceIn(.25f, .82f)
        hsl[2] = lightness
        return ColorUtils.HSLToColor(hsl)
    }

    /** parseColor throws StringIndexOutOfBounds (not IllegalArgument) on an empty string - and
     *  empty is every custom-color preference's "not picked yet" default. */
    private fun parseHexColorOrNull(hex: String): Int? = if (hex.isBlank()) null else try {
        Color.parseColor(hex)
    } catch (ignored: Exception) {
        null
    }

    private data class SurfacePalette(
            val primary: Int,
            val secondary: Int,
            val tertiary: Int,
            val treatment: SurfaceColorTreatment
    )

    private fun resolvedGlobalColorTreatment(): SurfaceColorTreatment =
            SurfaceColorTreatment.fromPreference(
                    colorTreatment,
                    default = SurfaceColorTreatment.EXPRESSIVE)

    /** Resolves an individual surface against the global watch policy. A component's saved Normal
     * color wins only when that component explicitly selected Normal; Follow uses the global
     * Normal color, so an old hidden custom value cannot unexpectedly leak back into the UI. */
    private fun resolveSurfacePalette(
            mode: String,
            customColor: String,
            legacyDesaturated: Boolean,
            rawPrimary: Int,
            rawSecondary: Int,
            rawTertiary: Int
    ): SurfacePalette {
        val selected = SurfaceColorTreatment.fromPreference(mode, legacyDesaturated)
        val treatment = selected.resolveAgainst(resolvedGlobalColorTreatment())
        val fixed = (if (selected == SurfaceColorTreatment.FOLLOW) null
                else parseHexColorOrNull(customColor))
                ?: parseHexColorOrNull(normalColor)
                ?: defaultSeekBarColor
        val triad = SurfacePaletteResolver.derive(
                treatment, colorModifier, rawPrimary, rawSecondary, rawTertiary, fixed,
                colorHueShift, normalColorMulti)
        return SurfacePalette(
                triad.primary,
                triad.secondary,
                triad.tertiary,
                // FOLLOW can only survive resolveAgainst when the global is FOLLOW too; report the
                // treatment the resolver actually applied so downstream `== NORMAL` checks (which
                // gate palette extraction and the Material surface softening) stay correct.
                if (treatment == SurfaceColorTreatment.FOLLOW) SurfaceColorTreatment.EXPRESSIVE
                else treatment)
    }

    /**
     * The artist line's colour, after the shared lightness floor and - when the user asked for it -
     * the adaptive correction against the artwork actually behind the line.
     *
     * The adaptation runs *last*, on whatever the colour treatment produced, so it composes with
     * every treatment and with a hand-picked custom colour rather than replacing any of them.
     */
    private fun resolvedArtistTextColor(): Int {
        val base = WatchTheme.accentForText(
                artistAccentColor.takeIf { it != 0 } ?: defaultSeekBarColor)
        if (!artistAdaptiveContrast) return base
        val background = artistBandLuminance() ?: return base
        return AdaptiveTextContrast.adapt(base, background)
    }

    /**
     * The user's chosen title colour, or null to leave every face its own.
     *
     * Null is the untouched default: "face" mode resolves no palette at all (see applyAccentColor),
     * so nothing here has to know what any individual face draws - each keeps its own literal, and
     * its own alpha, through FaceChrome's titleTextColor.
     */
    private fun resolvedTitleTextColor(): Int? {
        val base = titleAccentColor.takeIf { it != 0 } ?: return null
        val lifted = WatchTheme.accentForText(base)
        if (!titleAdaptiveContrast) return lifted
        val background = titleBandLuminance() ?: return lifted
        return AdaptiveTextContrast.adapt(lifted, background)
    }

    private fun applyAccentColor(
            color: Int,
            secondaryColor: Int? = null,
            tertiaryColor: Int? = null,
            art: Bitmap? = null,
            publishArt: Boolean = false
    ) {
        rawAccentColor = color
        secondaryColor?.let { rawSecondaryAccentColor = it }
        tertiaryColor?.let { rawTertiaryAccentColor = it }
        val sourceSecondary = rawSecondaryAccentColor.takeIf { it != 0 }
                ?: albumToneFallback(color, .42f)
        val sourceTertiary = rawTertiaryAccentColor.takeIf { it != 0 }
                ?: albumToneFallback(color, .68f)
        // Routed through the shared resolver rather than a local `when` over the raw preference
        // string: that older form treated every unrecognised value as Expressive, so each harmony
        // treatment added later would have silently rendered as the plain album accent here while
        // the per-surface palettes below applied it correctly.
        val globalTriad = SurfacePaletteResolver.derive(
                resolvedGlobalColorTreatment(),
                colorModifier,
                color,
                sourceSecondary,
                sourceTertiary,
                parseHexColorOrNull(normalColor) ?: defaultSeekBarColor,
                colorHueShift,
                normalColorMulti)
        currentAccentColor = globalTriad.primary
        currentSecondaryAccentColor = globalTriad.secondary
        currentTertiaryAccentColor = globalTriad.tertiary
        // "face" means the title keeps whatever colour each face designed, so there is nothing to
        // derive - the state's titleColor stays null and every face falls back to its own.
        val titlePalette = if (titleColorMode == MiscPreferences.TITLE_COLOR_FACE_DEFAULT) {
            null
        } else {
            resolveSurfacePalette(
                    titleColorMode, titleCustomColor, legacyDesaturated = false,
                    color, sourceSecondary, sourceTertiary)
        }
        val artistPalette = resolveSurfacePalette(
                artistColorMode, artistCustomColor, artistLegacyDesaturated,
                color, sourceSecondary, sourceTertiary)
        val progressPalette = resolveSurfacePalette(
                progressColorMode, progressCustomColor, progressLegacyDesaturated,
                color, sourceSecondary, sourceTertiary)
        val volumePalette = resolveSurfacePalette(
                volumeColorMode, volumeCustomColor, legacyDesaturated = false,
                color, sourceSecondary, sourceTertiary)
        val quickPalette = resolveSurfacePalette(
                quickPanelColorMode, quickPanelCustomColor, legacyDesaturated = false,
                color, sourceSecondary, sourceTertiary)
        titleAccentColor = titlePalette?.primary ?: 0
        artistAccentColor = artistPalette.primary
        progressAccentColor = progressPalette.primary
        progressSecondaryAccentColor = progressPalette.secondary
        progressTertiaryAccentColor = progressPalette.tertiary
        volumeAccentColor = volumePalette.primary
        volumeSecondaryAccentColor = volumePalette.secondary
        volumeTertiaryAccentColor = volumePalette.tertiary
        quickPanelAccentColor = quickPalette.primary
        quickPanelSecondaryAccentColor = quickPalette.secondary
        quickPanelTertiaryAccentColor = quickPalette.tertiary

        binding.seekBar.setPaletteColors(
                progressAccentColor, progressSecondaryAccentColor, progressTertiaryAccentColor)
        binding.seekOverlayMeter.accentColor = progressAccentColor
        binding.volumeBar.progressColor = volumeAccentColor
        binding.volumeBar.secondaryColor = volumeSecondaryAccentColor
        binding.volumeBar.tertiaryColor = volumeTertiaryAccentColor
        binding.fourWayTouch.setTapFeedbackColor(currentAccentColor)
        composeTapPulse.accentColor = currentAccentColor
        swipeTrail.accentColor = currentAccentColor
        // Artist name uses the same dark-theme-adapted (lightened) accent as the queue's now-playing row.
        binding.textArtist.setTextColor(resolvedArtistTextColor())

        if (screenButtonsBgStyle in setOf(
                        "solid_album", "solid_exp_album",
                        "outline", "outline_exp", "outline_exp_album",
                        "icon_exp", "glow_album", "glow_exp",
                        "translucent_album", "translucent_album_exp")) {
            styleScreenButtons()
        }

        if (isQuickActionsPanelShowing()) {
            binding.quickActionUpNext.background = upNextPillBackground()
            renderQuickPanelExtraActions()
            updateQuickActionButtonStates()
        }

        updateFaceState {
            val materialSurfaceSoftened = colorTreatment == "desaturated"
            val base = it.copy(
                    accentColor = currentAccentColor,
                    materialSurfaceColor = if (materialSurfaceSoftened) {
                        currentAccentColor
                    } else {
                        currentAccentColor
                    },
                    materialSurfaceSoftened = materialSurfaceSoftened,
                    secondaryAccentColor = resolvedSecondaryAccent(),
                    tertiaryAccentColor = resolvedTertiaryAccent(),
                    // Album/desaturated/custom shading tones follow the current accent.
                    backdropShadingColor = resolvedShadingColor(),
                    progressColor = binding.seekBar.progressColor,
                    artistColor = binding.textArtist.currentTextColor,
                    titleColor = resolvedTitleTextColor(),
                    // The awake Up Next pill's colours follow the accent, so refresh them here too.
                    upNextPillFill = upNextPillFillColor(),
                    upNextPillTextColor = awakeUpNextPillTint(),
                    // Track changes can land mid-ambient - keep the AOD's album-mode tint
                    // following the new art's accent.
                    ambientTint = resolvedAodTint()
            )
            // Publish the cover and its accent in the same state update so faces that draw the
            // art themselves never render a new cover with the previous track's tint for a frame.
            // Frosted here as well as in applyMainAlbumArtDisplay: Compose faces draw the artwork
            // themselves from face state, so publishing the raw bitmap would leave Classic frosted
            // and every Compose face sharp. Palette extraction upstream still reads the *raw*
            // cover - frosting the rim must not shift the accent the whole screen is tinted with.
            // Both call sites share one cache, keyed on bitmap identity.
            if (publishArt) {
                base.copy(albumArt = frostArtworkIfSelected(art)?.asImageBitmap())
            } else {
                base
            }
        }
        // Album-based and duotone shading must follow the same palette update as the controls.
        applyAlbumArtScrim()
        applyPlayerBackground()
        if (ambientObserver.isAmbient) {
            // Keep all host-rendered AOD metadata (including the clock) following a newly
            // extracted album tint, rather than updating only the title line.
            applyAmbientViewColors()
        } else {
            // The awake clock is the one album-derived element resolved *outside* this function
            // (resolveClockColor reads currentAccentColor, which is only assigned above), and the
            // art-change path calls applyClockAppearance immediately - while Palette.generate is
            // still running. So the clock was painted from the *previous* cover's accent and
            // nothing brought it back: it stayed one track behind until some unrelated preference
            // change happened to refresh it. AOD already had this covered by the branch above.
            applyClockAppearance()
        }
    }

    private fun resolvedSecondaryAccent(): Int = currentSecondaryAccentColor.takeIf { it != 0 }
            ?: albumToneFallback(currentAccentColor.takeIf { it != 0 } ?: defaultSeekBarColor, .42f)

    private fun resolvedTertiaryAccent(): Int = currentTertiaryAccentColor.takeIf { it != 0 }
            ?: albumToneFallback(currentAccentColor.takeIf { it != 0 } ?: defaultSeekBarColor, .68f)

    private fun resolvedProgressAccent(): Int =
            progressAccentColor.takeIf { it != 0 } ?: currentAccentColor.takeIf { it != 0 }
            ?: defaultSeekBarColor

    private fun resolvedProgressSecondaryAccent(): Int =
            progressSecondaryAccentColor.takeIf { it != 0 }
            ?: albumToneFallback(resolvedProgressAccent(), .42f)

    private fun resolvedProgressTertiaryAccent(): Int =
            progressTertiaryAccentColor.takeIf { it != 0 }
            ?: albumToneFallback(resolvedProgressAccent(), .68f)

    private fun resolvedVolumeAccent(): Int =
            volumeAccentColor.takeIf { it != 0 } ?: currentAccentColor.takeIf { it != 0 }
            ?: defaultSeekBarColor

    private fun resolvedVolumeSecondaryAccent(): Int =
            volumeSecondaryAccentColor.takeIf { it != 0 }
            ?: albumToneFallback(resolvedVolumeAccent(), .42f)

    private fun resolvedVolumeTertiaryAccent(): Int =
            volumeTertiaryAccentColor.takeIf { it != 0 }
            ?: albumToneFallback(resolvedVolumeAccent(), .68f)

    private fun resolvedQuickPanelAccent(): Int =
            quickPanelAccentColor.takeIf { it != 0 } ?: currentAccentColor.takeIf { it != 0 }
            ?: defaultSeekBarColor

    private fun resolvedQuickPanelSecondaryAccent(): Int =
            quickPanelSecondaryAccentColor.takeIf { it != 0 }
            ?: albumToneFallback(resolvedQuickPanelAccent(), .42f)

    private fun resolvedQuickPanelTertiaryAccent(): Int =
            quickPanelTertiaryAccentColor.takeIf { it != 0 }
            ?: albumToneFallback(resolvedQuickPanelAccent(), .68f)

    /**
     * "Playback Stopped"/"Error" reuse the artist line, but they're status messages, not an
     * artist name - they should always read in plain white, never the dynamic accent color.
     */
    private fun setStatusMessageOnArtistLine(message: String) {
        binding.textArtist.setTextColor(getColor(android.R.color.white))
        binding.textArtist.text = message
    }

    /** Pending "settle back to the plain cover drawable" callback from the last [fadeToAlbumArt]
     *  cross-fade, so a rapid follow-up (quick track skips) cancels it instead of letting it
     *  restore a drawable that is no longer the current one. */
    private var albumArtSettleRunnable: Runnable? = null

    private fun fadeToAlbumArt(bitmap: Bitmap?) {
        // Cross-fade instead of fade-out-then-in: the old art stays visible underneath while
        // the new one fades in over it, so the artwork never blinks away mid-transition.
        binding.albumArt.animate().cancel()
        albumArtSettleRunnable?.let { binding.albumArt.removeCallbacks(it) }
        albumArtSettleRunnable = null

        // Unwrap a still-running transition so rapid track skips don't nest layers endlessly.
        val oldDrawable = when (val current = binding.albumArt.drawable) {
            is TransitionDrawable -> current.getDrawable(1)
            else -> current
        }

        applyMainAlbumArtDisplay(bitmap, forceBlur = blurAlbumArtBackground)
        val newDrawable = binding.albumArt.drawable
        if (oldDrawable == null || newDrawable == null || oldDrawable === newDrawable) {
            return
        }

        // Covers of matching shape can be handed to a TransitionDrawable directly; covers of
        // differing shape cannot, and the failure is the stretched artwork this guards against -
        // see [sameAspectRatio].
        if (sameAspectRatio(oldDrawable, newDrawable)) {
            val transition = TransitionDrawable(arrayOf(oldDrawable, newDrawable))
            binding.albumArt.setImageDrawable(transition)
            transition.startTransition(ALBUM_ART_CROSSFADE_MS)
            return
        }

        val oldFrame = composeCoverFrame(binding.albumArt, oldDrawable)
        val newFrame = composeCoverFrame(binding.albumArt, newDrawable)
        if (oldFrame == null || newFrame == null) {
            // Not laid out yet, or a drawable with no intrinsic size. The new cover is already
            // applied above; skipping the cross-fade is the honest outcome.
            return
        }

        val transition = TransitionDrawable(
                arrayOf(BitmapDrawable(resources, oldFrame), BitmapDrawable(resources, newFrame)))
        binding.albumArt.setImageDrawable(transition)
        transition.startTransition(ALBUM_ART_CROSSFADE_MS)

        // Back to the real drawable once the fade is over, so the view returns to its own
        // centerCrop of the true cover rather than staying on a baked, view-resolution frame.
        //
        // Guarded on the transition still being what is on screen: several paths re-render the
        // artwork outside a track change (a preference edit, an ambient exit, a face swap), and one
        // landing inside the fade's 300 ms would otherwise be reverted here to a drawable composed
        // before it.
        val settle = Runnable {
            if (binding.albumArt.drawable === transition) {
                binding.albumArt.setImageDrawable(newDrawable)
            }
            albumArtSettleRunnable = null
        }
        albumArtSettleRunnable = settle
        binding.albumArt.postDelayed(settle, ALBUM_ART_CROSSFADE_MS.toLong())
    }

    /**
     * Whether two cover drawables can share one [TransitionDrawable] without being distorted.
     *
     * They can only when their aspect ratios agree, and the reason is not obvious.
     * `TransitionDrawable` is a `LayerDrawable`, whose intrinsic size is the maximum of its layers'
     * **per axis, computed independently** - so a 640x360 cover fading into a 450x450 one yields an
     * intrinsic size of 640x450, a shape belonging to neither. `BitmapDrawable`'s default gravity is
     * `FILL`, so each layer is then stretched into that composite box, and the host ImageView's
     * `centerCrop` cannot undo it: it scales the composite, uniformly, against the wrong shape.
     *
     * Covers of differing shape do reach the watch. `BitmapUtils.resizeAndCrop` (wearutils, called
     * from `MusicService.transmitToWear`) returns the source *uncropped* when it is smaller than the
     * aspect-adjusted target, so a 640x360 "art track" thumbnail arrives in 16:9 while an ordinary
     * cover arrives square - which is why the stretch was real but rare, and why it corrected itself
     * on the next track.
     *
     * Sizes may differ freely as long as the ratio holds: that is a uniform scale, which is exactly
     * what the ImageView is for.
     */
    private fun sameAspectRatio(first: Drawable, second: Drawable): Boolean {
        val fw = first.intrinsicWidth
        val fh = first.intrinsicHeight
        val sw = second.intrinsicWidth
        val sh = second.intrinsicHeight
        if (fw <= 0 || fh <= 0 || sw <= 0 || sh <= 0) {
            return false
        }
        return abs(fw.toFloat() / fh - sw.toFloat() / sh) <= ALBUM_ART_ASPECT_TOLERANCE
    }

    /**
     * Renders [drawable] into a [view]-sized frame with the view's own centerCrop fit already
     * baked in.
     *
     * The same trick [composeSquareInsetFrame] uses, for the same reason: two frames of identical
     * dimensions can be cross-faded by a `TransitionDrawable` without its per-axis intrinsic
     * arithmetic having anything left to get wrong, because each frame is already exactly the size
     * the layer will be drawn at.
     *
     * Only used on the shape-mismatch path - it allocates a screen-sized bitmap per layer, which is
     * not worth spending on every track change when the shapes agree and a plain cross-fade is
     * already correct.
     */
    private fun composeCoverFrame(view: ImageView, drawable: Drawable): Bitmap? {
        val viewWidth = view.width
        val viewHeight = view.height
        val intrinsicWidth = drawable.intrinsicWidth
        val intrinsicHeight = drawable.intrinsicHeight
        if (viewWidth <= 0 || viewHeight <= 0 || intrinsicWidth <= 0 || intrinsicHeight <= 0) {
            return null
        }

        val scale = max(
                viewWidth / intrinsicWidth.toFloat(),
                viewHeight / intrinsicHeight.toFloat())
        val width = (intrinsicWidth * scale).roundToInt()
        val height = (intrinsicHeight * scale).roundToInt()
        val left = (viewWidth - width) / 2
        val top = (viewHeight - height) / 2

        val frame = Bitmap.createBitmap(viewWidth, viewHeight, Bitmap.Config.ARGB_8888)
        // The drawable is live - it is (or was) the ImageView's own - so its bounds are borrowed
        // and put back. setImageDrawable reconfigures them anyway, but not before this returns.
        val previousBounds = Rect(drawable.bounds)
        drawable.setBounds(left, top, left + width, top + height)
        drawable.draw(Canvas(frame))
        drawable.bounds = previousBounds
        return frame
    }

    /** Grayscale for the "Black & white" album art styles - a plain saturation-0 color filter
     *  works on every API level and stacks with the blur paths untouched. */
    private val grayscaleFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })

    /** Renders the main background album art — sharp cover, full-screen blur, black & white
     *  variants, Square (an uncropped inset over a blurred backdrop), or hidden entirely, per
     *  user setting. */
    private fun applyMainAlbumArtDisplay(source: Bitmap?, forceBlur: Boolean) {
        applyAlbumArtDisplay(
                source = frostArtworkIfSelected(source),
                blurred = forceBlur,
                grayscale = albumArtGrayscale,
                hidden = albumArtHidden,
                square = playerBackgroundStyle.squareCornerRadiusFraction != null
        )
    }

    /**
     * Composes the frosted-rim artwork when that background style is selected, caching the result
     * against the exact bitmap it was built from.
     *
     * Cached because this runs on every artwork *display* pass - preference changes, ambient exits,
     * face swaps - not only on a track change, and the composition costs several bitmap allocations
     * plus the blur passes. The cache key is bitmap identity, so a new cover invalidates it without
     * needing a separate signal.
     */
    private fun frostArtworkIfSelected(source: Bitmap?): Bitmap? {
        if (source == null || !playerBackgroundStyle.frostedEdges) return source
        cachedFrostedSource?.let { cached ->
            if (cached === source) {
                cachedFrostedArt?.takeIf { !it.isRecycled }?.let { return it }
            }
        }
        val frosted = FrostedEdges.compose(source, blurRadiusPx)
        // Deliberately NOT recycling the previous composition. It is handed to two independent
        // consumers - the classic ImageView and, via face state, whichever Compose face is drawing
        // - and neither tells us when it has stopped using it. Recycling here crashed the watch
        // ("trying to use a recycled bitmap") whenever one of them still held the old frame, which
        // is exactly what happens on a style change. Since minSdk 26 the pixels live on the native
        // heap tied to the object, so dropping the reference is enough.
        cachedFrostedSource = source
        cachedFrostedArt = frosted
        return frosted
    }

    /** Low-level artwork renderer shared by interactive and ambient modes. Ambient treatment is
     * independent from the interactive artwork style, so blur/monochrome/visibility - and
     * [square], which only the interactive caller ever passes true - must be supplied explicitly
     * instead of reading the interactive preference fields directly. */
    private fun applyAlbumArtDisplay(
            source: Bitmap?,
            blurred: Boolean,
            grayscale: Boolean,
            hidden: Boolean,
            square: Boolean = false
    ) {
        binding.albumArt.colorFilter = if (grayscale) grayscaleFilter else null
        updateSquareInset(if (square && !hidden) source else null)

        if (source == null || hidden) {
            binding.albumArt.setImageBitmap(null)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                binding.albumArt.setRenderEffect(null)
            }
            return
        }

        if (!blurred) {
            binding.albumArt.setImageBitmap(source)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                binding.albumArt.setRenderEffect(null)
            }
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            binding.albumArt.setImageBitmap(source)
            binding.albumArt.setRenderEffect(
                    // CLAMP, not DECAL: the art fills the whole (centerCrop) view, so the blur
                    // kernel samples past the bitmap edges. DECAL treats those samples as
                    // transparent, fading the blur out at the bezel and letting the black backdrop
                    // bleed in as a dark ring; CLAMP extends the edge pixels so the blur stays
                    // opaque edge-to-edge like the reference composition.
                    RenderEffect.createBlurEffect(blurRadiusPx, blurRadiusPx, Shader.TileMode.CLAMP)
            )
        } else {
            // Deliberately NOT recycled. fadeToAlbumArt captures the *current* drawable before
            // calling in here and then puts it into a TransitionDrawable as the outgoing layer, so
            // the previous blur is still being drawn for the length of the cross-fade. Recycling it
            // the moment the new one was set is what produced "Canvas: trying to use a recycled
            // bitmap" on every faded track change on pre-31 watches, which is exactly where this
            // legacy path runs. Bitmap memory has been reclaimed by the GC since API 26, so
            // dropping the reference is all that is needed - and all that is safe.
            binding.albumArt.setImageBitmap(createBlurredBitmapLegacy(source, blurRadiusPx))
        }
    }

    /**
     * Shows a blurred copy of the current album art behind the volume/seek rings.
     *
     * On API 31+ this is a real GPU Gaussian blur via [android.graphics.RenderEffect] - sharp,
     * cheap, no quality loss. Older watches fall back to [createBlurredBitmapLegacy], a software
     * approximation.
     */
    private fun applyBlurredAlbumArt(source: Bitmap?) {
        if (source == null) {
            binding.overlayBlurImage.setImageBitmap(null)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                binding.overlayBlurImage.setRenderEffect(null)
            }
            return
        }

        // When the background is already blurred (album_art_style = blur), match its radius so the
        // overlay blur is pixel-identical to what is already on screen and revealing it never makes
        // the blur "jump" to a different strength. Otherwise use the overlay's own radius.
        val radius = if (blurAlbumArtBackground) blurRadiusPx else overlayBlurRadiusPx
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            binding.overlayBlurImage.setImageBitmap(source)
            binding.overlayBlurImage.setRenderEffect(
                    // CLAMP so the overlay blur stays opaque to the bezel (see applyMainAlbumArtDisplay).
                    RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
            )
        } else {
            // Not recycled, for the same reason as the main blur above.
            binding.overlayBlurImage.setImageBitmap(createBlurredBitmapLegacy(source, radius))
        }
    }

    /** Applies the resolved AOD artwork policy. Minimal and Eclipse resolve to hidden before this
     * renderer is reached, so entering either style never allocates a legacy blurred bitmap or a
     * GPU blur effect. */
    private fun applyAmbientAlbumArt() {
        val spec = resolveAodArtwork(
                showArtwork = aodShowArt,
                effectiveAodStyle = effectiveAodStyle(),
                treatment = aodArtTreatment,
                playerArtworkStyle = albumArtStyle
        )
        binding.albumArt.alpha = if (spec.visible) ambientAlbumArtAlpha else 0f
        applyAlbumArtDisplay(
                source = latestAlbumArt,
                blurred = spec.blurred,
                grayscale = spec.monochrome,
                hidden = !spec.visible
        )
    }

    /**
     * Multi-pass bilinear blur for pre-Android 12 watches. Each pass retains at least roughly half
     * of the source resolution and returns to full size before the next pass. This is smoother
     * than collapsing the cover to 8–16 pixels and, because the result is cached when art or
     * preferences change, no bitmap work occurs during a seek/volume gesture.
     */
    /** Delegates to the shared implementation so the watch, the cover pills and the phone
     *  preview all blur identically for a given radius. */
    private fun createBlurredBitmapLegacy(source: Bitmap, radiusPx: Float): Bitmap =
            BitmapBlur.blur(source, radiusPx)

    /** Pending "settle back to a plain matrix-scaled bitmap" callback from the last
     *  [updateSquareInset] cross-fade, so a rapid follow-up call (quick track skips) cancels it
     *  instead of letting it overwrite a newer bitmap with a stale one. */
    private var squareInsetSettleRunnable: Runnable? = null

    /** Shows/hides and fills the Square style's sharp inset (`album_art_square_inset` in
     *  activity_main.xml) - null hides it. The blurred backdrop stays entirely on
     *  `binding.albumArt`, handled by the ordinary blurred/plain paths in [applyAlbumArtDisplay]
     *  and already cross-fades there (see [fadeToAlbumArt]); this only ever swapped its own
     *  Bitmap outright, so the sharp inset popped instantly on every track change while the
     *  backdrop behind it eased in - this cross-fades the inset too, to match.
     *
     *  Reworked from a straight setImageBitmap: the view's `scaleType="matrix"` means a single
     *  [applySquareInsetMatrix] only ever positions *one* bitmap at a time, so a
     *  [TransitionDrawable] of the raw old/new bitmaps can't be used directly (both would be
     *  forced through whichever bitmap's matrix is current). Instead each frame is pre-composed
     *  into a view-sized [Bitmap] with its own contain-fit already baked in via
     *  [composeSquareInsetFrame], so the two frames the TransitionDrawable cross-fades between
     *  are already positioned identically regardless of the two source bitmaps' own dimensions -
     *  the outgoing frame is a live snapshot ([snapshotView]) of whatever was on screen, so it's
     *  correct even if that was mid cross-fade itself. Settles back to a plain matrix-scaled
     *  bitmap once the transition finishes, ready for the next call. */
    private fun updateSquareInset(source: Bitmap?) {
        val insetView = binding.albumArtSquareInset
        squareInsetSettleRunnable?.let { insetView.removeCallbacks(it) }
        squareInsetSettleRunnable = null

        if (source == null) {
            insetView.visibility = View.GONE
            insetView.setImageBitmap(null)
            return
        }

        insetView.clipToOutline = true
        insetView.outlineProvider = squareInsetOutlineProvider

        val previousFrame = if (insetView.visibility == View.VISIBLE &&
                insetView.width > 0 && insetView.height > 0) {
            snapshotView(insetView)
        } else {
            null
        }
        insetView.visibility = View.VISIBLE

        fun settle() {
            insetView.scaleType = ImageView.ScaleType.MATRIX
            insetView.setImageBitmap(source)
            applySquareInsetMatrix(insetView, source)
            insetView.invalidateOutline()
        }

        val newFrame = previousFrame?.let { composeSquareInsetFrame(insetView, source) }
        if (previousFrame == null || newFrame == null) {
            // First appearance, not laid out yet, or the style just switched on - nothing sane
            // to cross-fade from.
            settle()
            return
        }

        val transition = TransitionDrawable(
                arrayOf(BitmapDrawable(resources, previousFrame), BitmapDrawable(resources, newFrame)))
        insetView.scaleType = ImageView.ScaleType.FIT_XY
        insetView.setImageDrawable(transition)
        insetView.invalidateOutline()
        transition.startTransition(ALBUM_ART_CROSSFADE_MS)

        val runnable = Runnable {
            settle()
            squareInsetSettleRunnable = null
        }
        squareInsetSettleRunnable = runnable
        insetView.postDelayed(runnable, ALBUM_ART_CROSSFADE_MS.toLong())
    }

    /** Renders [view]'s current on-screen content (post-clip, post-transform) into a same-sized
     *  [Bitmap] - used to grab a stable "outgoing frame" for [updateSquareInset]'s cross-fade
     *  that's correct even mid-transition, since it captures whatever is actually drawn rather
     *  than assuming a single static bitmap is showing. */
    private fun snapshotView(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    /** Composes [source] into a [view]-sized frame with the same contain-fit
     *  [squareInsetFitMatrix] gives [applySquareInsetMatrix], so it can stand in as one half of a
     *  [TransitionDrawable] cross-fade without the shared `imageMatrix` a plain Bitmap would need
     *  (and which only ever fits one source bitmap's own dimensions at a time). Null when the
     *  view isn't laid out yet. */
    private fun composeSquareInsetFrame(view: ImageView, source: Bitmap): Bitmap? {
        val bounds = squareInsetBounds(view) ?: return null
        val frame = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        Canvas(frame).drawBitmap(source, squareInsetFitMatrix(bounds, source), null)
        return frame
    }

    /** The largest square that fits inside the round bezel without a corner being cut off (side
     *  = min(width, height) / sqrt(2)) - the target region for the Square style's sharp inset,
     *  shared by [squareInsetOutlineProvider] (which clips the view to it) and
     *  [applySquareInsetMatrix] (which fits the bitmap into it), so the two can never drift
     *  apart into a clip region and a scale that disagree. */
    private fun squareInsetBounds(view: View): RectF? {
        val side = minOf(view.width, view.height) / sqrt(2f)
        if (side <= 0f) return null
        val left = (view.width - side) / 2f
        val top = (view.height - side) / 2f
        return RectF(left, top, left + side, top + side)
    }

    /** Fits the *entire* bitmap into [squareInsetBounds] with contain scaling - the smaller of
     *  the two axis scales, never the larger - so a source that isn't exactly square is
     *  letterboxed inside the inset square rather than cropped; the blurred backdrop underneath
     *  shows through any letterbox gap. The view stays `match_parent` and `scaleType="matrix"`
     *  (see activity_main.xml), so without this the platform's own centerCrop math would scale
     *  against the full screen instead of the much smaller inset, zooming into the art's center
     *  well past what the outline clip below reveals - that was the original bug. Deferred to the
     *  next layout pass if the view hasn't been measured yet (only possible on the very first
     *  call, before content_frame's first layout). */
    private fun applySquareInsetMatrix(view: ImageView, source: Bitmap) {
        if (view.width == 0 || view.height == 0) {
            view.doOnLayout { applySquareInsetMatrix(view, source) }
            return
        }
        val bounds = squareInsetBounds(view) ?: return
        view.imageMatrix = squareInsetFitMatrix(bounds, source)
    }

    /** The contain-fit transform shared by [applySquareInsetMatrix] (applied live as an
     *  ImageView's `imageMatrix`) and [composeSquareInsetFrame] (baked into an offscreen
     *  Bitmap) - kept as one function so the two can never drift into disagreeing about where
     *  the art sits. */
    private fun squareInsetFitMatrix(bounds: RectF, source: Bitmap): Matrix {
        val scale = minOf(bounds.width() / source.width, bounds.height() / source.height)
        return Matrix().apply {
            setScale(scale, scale)
            postTranslate(
                    bounds.centerX() - source.width * scale / 2f,
                    bounds.centerY() - source.height * scale / 2f
            )
        }
    }

    /** Clips the sharp inset to [squareInsetBounds], rounded per the active Square variant's
     *  [PlayerBackgroundStyle.squareCornerRadiusFraction]. Reads [playerBackgroundStyle] fresh on
     *  every call, so switching between Square variants just needs [updateSquareInset] to
     *  invalidate the outline, not a new provider instance. */
    private val squareInsetOutlineProvider = object : android.view.ViewOutlineProvider() {
        override fun getOutline(view: View, outline: android.graphics.Outline) {
            val bounds = squareInsetBounds(view) ?: return
            val radiusFraction = playerBackgroundStyle.squareCornerRadiusFraction ?: 0.10f
            outline.setRoundRect(
                    bounds.left.roundToInt(), bounds.top.roundToInt(),
                    bounds.right.roundToInt(), bounds.bottom.roundToInt(),
                    bounds.width() * radiusFraction
            )
        }
    }

    private val buttonConfigObserver = Observer<WatchActionConfigProvider?> { config ->
        if (config == null) {
            return@Observer
        }

        val topSingle = config.getAction(ButtonInfo(false, ScreenQuadrant.TOP, GESTURE_SINGLE_TAP))
        val bottomSingle =
                config.getAction(ButtonInfo(false, ScreenQuadrant.BOTTOM, GESTURE_SINGLE_TAP))
        val leftSingle =
                config.getAction(ButtonInfo(false, ScreenQuadrant.LEFT, GESTURE_SINGLE_TAP))
        val rightSingle =
                config.getAction(ButtonInfo(false, ScreenQuadrant.RIGHT, GESTURE_SINGLE_TAP))

        binding.iconTop.setImageDrawable(topSingle?.icon)
        binding.iconBottom.setImageDrawable(bottomSingle?.icon)
        binding.iconLeft.setImageDrawable(leftSingle?.icon)
        binding.iconRight.setImageDrawable(rightSingle?.icon)

        // The expressive face's side transport buttons mirror the LEFT/RIGHT quadrant actions:
        // show the configured action's icon (null falls back to the default skip glyph). The tap
        // itself is routed to the same quadrant action in the expressive listener.
        updateFaceState {
            it.copy(
                    leftActionIcon = leftSingle?.icon?.toFaceIcon(),
                    rightActionIcon = rightSingle?.icon?.toFaceIcon(),
                    leftActionIconTintable = leftSingle?.iconTintable ?: true,
                    rightActionIconTintable = rightSingle?.iconTintable ?: true,
                    leftActionDescription = leftSingle?.title
                            ?: StandardActionTitles.get(this, leftSingle?.key),
                    rightActionDescription = rightSingle?.title
                            ?: StandardActionTitles.get(this, rightSingle?.key)
            )
        }

        // These icons are the only visible hint of what each quadrant does - name them after
        // their configured action so the main screen isn't silent under a screen reader.
        binding.iconTop.contentDescription = topSingle?.title
                ?: StandardActionTitles.get(this, topSingle?.key)
        binding.iconBottom.contentDescription = bottomSingle?.title
                ?: StandardActionTitles.get(this, bottomSingle?.key)
        binding.iconLeft.contentDescription = leftSingle?.title
                ?: StandardActionTitles.get(this, leftSingle?.key)
        binding.iconRight.contentDescription = rightSingle?.title
                ?: StandardActionTitles.get(this, rightSingle?.key)

        for (i in 0 until 4) {
            binding.fourWayTouch.enabledDoubleTaps[i] =
                    config.isActionActive(ButtonInfo(false, i, GESTURE_DOUBLE_TAP))
            binding.fourWayTouch.enabledLongTaps[i] =
                    config.isActionActive(ButtonInfo(false, i, GESTURE_LONG_TAP))
        }

        with(stemButtonsManager) {
            for (button in WatchInfoSender.getAvailableButtonsOnWatch(this@MainActivity)) {
                enabledDoublePressActions[button] =
                        config.isActionActive(ButtonInfo(true, button, GESTURE_DOUBLE_TAP))
                enabledLongPressActions[button] =
                        config.isActionActive(ButtonInfo(true, button, GESTURE_LONG_TAP))
            }
        }

        backButtonOverrideCallback.isEnabled = config.isActionActive(ButtonInfo(true, KeyEvent.KEYCODE_BACK, GESTURE_SINGLE_TAP))

        // Gates the swipe comet trail (see swipeTrail/onTouchMove/onUpwardsSwipe and siblings):
        // only shown when this specific screen's config - playing or stopped, whichever is
        // currently active - has at least one swipe direction actually assigned.
        swipeTrailEnabled = config.isActionActive(ButtonInfo(false, SwipeGesture.UP, GESTURE_SINGLE_TAP)) ||
                config.isActionActive(ButtonInfo(false, SwipeGesture.DOWN, GESTURE_SINGLE_TAP)) ||
                config.isActionActive(ButtonInfo(false, SwipeGesture.LEFT, GESTURE_SINGLE_TAP))

        updateScreenButtons(config)

        quickPanelSlots = Array(QuickPanelButtons.ALL_SLOTS.size) {
            config.getAction(ButtonInfo(false, QuickPanelButtons.ALL_SLOTS[it], GESTURE_SINGLE_TAP))
        }
        quickPanelLongSlot =
                config.getAction(ButtonInfo(false, QuickPanelButtons.SLOT_LONG, GESTURE_SINGLE_TAP))
        if (isQuickActionsPanelShowing()) {
            configureQuickPanelButtons()
        }
    }

    private fun quickPanelViews() = listOf(
            binding.quickActionLike,
            binding.quickActionShuffle,
            binding.quickActionRepeat
    )

    /** Resolves each quick-panel position's [QuickSlotMode] from the synced slot config and
     *  applies visibility + icon. See [quickSlotModes]'s kdoc for the slot semantics. */
    private fun configureQuickPanelButtons() {
        if (quickPanelSource == "session") {
            if (sessionQuickActions.isEmpty()) {
                configureUnavailableSessionQuickPanel()
            } else {
                configureSessionQuickPanelButtons()
            }
            return
        }

        // Undo any session-mode shrink / overflow buttons before the manual layout.
        resetFixedQuickButtonSizes()

        binding.quickActionUpNext.isEnabled = true
        binding.quickActionUpNext.isClickable = true
        clearQuickUpNextArtwork()

        val defaultModes = arrayOf(QuickSlotMode.LIKE, QuickSlotMode.SHUFFLE, QuickSlotMode.REPEAT)
        for ((index, panelButton) in quickPanelViews().withIndex()) {
            setQuickActionIconPadding(panelButton, remoteTemplate = false)
            val assigned = quickPanelSlots[index]
            val key = assigned?.key.orEmpty()

            val mode = when {
                assigned == null -> defaultModes[index]
                key.endsWith(".NullAction") -> QuickSlotMode.HIDDEN
                key.endsWith(".LikeAction") -> QuickSlotMode.LIKE
                key.endsWith(".ShuffleAction") -> QuickSlotMode.SHUFFLE
                key.endsWith(".RepeatAction") -> QuickSlotMode.REPEAT
                else -> QuickSlotMode.CUSTOM
            }
            quickSlotModes[index] = mode

            when (mode) {
                QuickSlotMode.HIDDEN -> {
                    panelButton.visibility = View.GONE
                    quickSlotUsesRealIcon[index] = false
                    panelButton.contentDescription = null
                }
                QuickSlotMode.LIKE -> {
                    panelButton.visibility = View.VISIBLE
                    panelButton.setImageResource(com.svartifoss.snfell.common.R.drawable.action_like)
                    quickSlotUsesRealIcon[index] = false
                    panelButton.contentDescription = getString(R.string.quick_action_like)
                }
                QuickSlotMode.SHUFFLE -> {
                    panelButton.visibility = View.VISIBLE
                    panelButton.setImageResource(com.svartifoss.snfell.common.R.drawable.action_shuffle)
                    quickSlotUsesRealIcon[index] = false
                    panelButton.contentDescription = getString(R.string.quick_action_shuffle)
                }
                QuickSlotMode.REPEAT -> {
                    panelButton.visibility = View.VISIBLE
                    panelButton.setImageResource(com.svartifoss.snfell.common.R.drawable.action_repeat)
                    quickSlotUsesRealIcon[index] = false
                    panelButton.contentDescription = getString(R.string.quick_action_repeat)
                }
                QuickSlotMode.CUSTOM -> {
                    panelButton.visibility = View.VISIBLE
                    if (assigned?.icon != null) {
                        panelButton.setImageDrawable(assigned.icon)
                        quickSlotUsesRealIcon[index] = !assigned.iconTintable
                    } else {
                        // A missing icon must never inherit Like/Shuffle/Repeat merely because of
                        // its slot position: that promises a different action than the tap runs.
                        panelButton.setImageResource(
                                com.svartifoss.snfell.common.R.drawable.action_custom)
                        quickSlotUsesRealIcon[index] = false
                    }
                    panelButton.contentDescription = assigned?.title
                            ?: StandardActionTitles.get(this, key)
                }
                QuickSlotMode.SESSION -> {
                    panelButton.visibility = View.VISIBLE
                    quickSlotUsesRealIcon[index] = false
                    sessionSlotShowsAppIcon[index] = false
                    displayedSessionQuickActions.getOrNull(index)?.let {
                        quickSlotUsesRealIcon[index] = applySessionQuickIcon(panelButton, it)
                        sessionSlotShowsAppIcon[index] = quickSlotUsesRealIcon[index]
                        panelButton.contentDescription = sessionActionDescription(it)
                    }
                }
            }
        }

        val longKey = quickPanelLongSlot?.key.orEmpty()
        quickPanelLongMode = when {
            quickPanelLongSlot == null -> QuickLongMode.UP_NEXT
            longKey.endsWith(".NullAction") -> QuickLongMode.HIDDEN
            else -> QuickLongMode.CUSTOM
        }
        // Stop the equalizer before it's potentially replaced below - repeatCount="infinite"
        // means it never stops on its own once started, so leaving UP_NEXT mode without this
        // would keep it ticking (and holding the drawable alive) in the background indefinitely.
        if (quickPanelLongMode != QuickLongMode.UP_NEXT) {
            (binding.quickActionUpNextIcon.drawable as? Animatable)?.stop()
        }
        when (quickPanelLongMode) {
            QuickLongMode.UP_NEXT -> {
                binding.quickActionUpNext.visibility = View.VISIBLE
                binding.quickActionUpNextIcon.setImageResource(R.drawable.ic_equalizer_bars_animated)
                quickActionUpNextUsesRealIcon = false
                syncUpNextEqualizerAnimation()
                binding.quickActionUpNextLabel.setText(R.string.quick_action_up_next)
                binding.quickActionUpNext.contentDescription = getString(R.string.quick_action_up_next)
                viewModel.customList.value?.let { updateUpNextPreview(it) }
            }
            QuickLongMode.CUSTOM -> {
                binding.quickActionUpNext.visibility = View.VISIBLE
                val icon = quickPanelLongSlot?.icon
                if (icon != null) {
                    binding.quickActionUpNextIcon.setImageDrawable(icon)
                    quickActionUpNextUsesRealIcon = !(quickPanelLongSlot?.iconTintable ?: true)
                } else {
                    binding.quickActionUpNextIcon.setImageResource(
                            com.svartifoss.snfell.common.R.drawable.action_custom)
                    quickActionUpNextUsesRealIcon = false
                }
                binding.quickActionUpNextLabel.text =
                        quickPanelLongSlot?.title
                                ?: StandardActionTitles.get(this, longKey)
                                ?: getString(R.string.action_name_custom)
                binding.quickActionUpNext.contentDescription = binding.quickActionUpNextLabel.text
                binding.quickActionUpNextTrack.visibility = View.GONE
            }
            QuickLongMode.SESSION -> {
                val action = sessionQuickActions.getOrNull(3)
                if (action == null) {
                    binding.quickActionUpNext.visibility = View.GONE
                    quickActionUpNextUsesRealIcon = false
                } else {
                    binding.quickActionUpNext.visibility = View.VISIBLE
                    quickActionUpNextUsesRealIcon = applySessionQuickIcon(binding.quickActionUpNextIcon, action)
                    binding.quickActionUpNextLabel.text = sessionActionDescription(action)
                    binding.quickActionUpNext.contentDescription = binding.quickActionUpNextLabel.text
                    binding.quickActionUpNextTrack.visibility = View.GONE
                }
            }
            QuickLongMode.HIDDEN -> {
                binding.quickActionUpNext.visibility = View.GONE
                quickActionUpNextUsesRealIcon = false
                binding.quickActionUpNext.contentDescription = null
            }
        }

        updateQuickActionButtonStates()
    }

    /** Notification actions use their exact rasterized icons; MediaSession fallback actions use
     * a recognizable local icon because their drawable resources live in the remote app. */
    private fun configureSessionQuickPanelButtons() {
        // Three fixed slots preserve a proper Wear touch target. The phone already caps the
        // payload and discards dislike; this watch-side filter is defensive for cached/older
        // states. Transport actions rank after app-specific toggles because the face already owns
        // play/skip, while a player exposing only transports still fills the available slots.
        val actions = sessionQuickActions
                .filterNot { it.semantic == "dislike" }
                .distinctBy { it.id }
                .sortedBy { quickActionDisplayRank(it.semantic) }
                .take(quickPanelViews().size)
        displayedSessionQuickActions = actions
        val (roundW, roundH, margin) =
                fittedRoundQuickSizes(actions.size.coerceIn(1, quickPanelViews().size))

        for ((index, panelButton) in quickPanelViews().withIndex()) {
            sizeRoundQuickButton(panelButton, roundW, roundH, margin, first = index == 0)
            val action = actions.getOrNull(index)
            if (action == null) {
                quickSlotModes[index] = QuickSlotMode.HIDDEN
                panelButton.setImageDrawable(null)
                panelButton.visibility = View.GONE
                quickSlotUsesRealIcon[index] = false
                sessionSlotShowsAppIcon[index] = false
                panelButton.contentDescription = null
            } else {
                quickSlotModes[index] = QuickSlotMode.SESSION
                panelButton.visibility = View.VISIBLE
                sessionSlotShowsAppIcon[index] = applySessionQuickIcon(panelButton, action)
                // Session icons are white templates now (see MediaNotificationActions), so let the
                // panel tint them to its chrome colour (dark on the light tonal panel) like the
                // rest of the glyphs, instead of leaving them raw white on a light surface.
                quickSlotUsesRealIcon[index] = false
                panelButton.contentDescription = sessionActionDescription(action)
            }
        }

        // Wide row: the queue shortcut, protected from being replaced by a media action.
        binding.quickActionUpNext.isEnabled = true
        binding.quickActionUpNext.isClickable = true
        binding.quickActionUpNext.visibility = View.VISIBLE
        clearQuickUpNextArtwork()
        quickPanelLongMode = QuickLongMode.UP_NEXT
        binding.quickActionUpNextIcon.setImageResource(R.drawable.ic_equalizer_bars_animated)
        quickActionUpNextUsesRealIcon = false
        syncUpNextEqualizerAnimation()
        binding.quickActionUpNextLabel.setText(R.string.quick_action_up_next)
        binding.quickActionUpNext.contentDescription = getString(R.string.quick_action_up_next)
        viewModel.customList.value?.let { updateUpNextPreview(it) }
        updateQuickActionButtonStates()
    }

    /** Display priority for a session quick-action slot: genuinely extra actions first, then
     * transport controls already represented by the face. Dislike has already been discarded. */
    private fun quickActionDisplayRank(semantic: String): Int = when (semantic) {
        "play", "pause", "previous", "next", "stop" -> 2
        else -> 0
    }

    /** Button width / height / inter-button margin such that [count] round quick-panel buttons
     *  (with their margins - see [sizeRoundQuickButton]'s first/end structure) fit the panel's
     *  usable width. The row used to always take the fixed XML size (58dp buttons, 8dp margins),
     *  which is wider than a 192dp screen minus the panel's 12dp side margins - the row overflowed
     *  and the first button drew clipped by the screen edge. Margins shrink before buttons do;
     *  whenever the screen has room, both dimensions retain the Wear OS 48dp minimum target. */
    private fun fittedRoundQuickSizes(count: Int): Triple<Int, Int, Int> {
        val density = resources.displayMetrics.density
        val xmlW = resources.getDimensionPixelSize(R.dimen.quick_action_button_width)
        val xmlH = resources.getDimensionPixelSize(R.dimen.quick_action_button_height)
        val defaultW = xmlW
        val defaultH = xmlH
        val minimumTarget = (48 * density).roundToInt()
        val available = (binding.contentFrame.width.takeIf { it > 0 }
                ?: resources.displayMetrics.widthPixels) - (16 * density).toInt()
        var margin = (8 * density).toInt()
        var width = defaultW
        if (count == 3) {
            // Match the measured width occupied by Up Next on every display size. Only the
            // horizontal dimension grows; height stays the fixed 52dp touch target below.
            if (count * width + margin * (count - 1) > available) {
                margin = (4 * density).toInt()
            }
            width = ((available - margin * (count - 1)) / count)
                    .coerceAtLeast((40 * density).toInt())
        } else if (count * width + margin * (count - 1) > available) {
            margin = (4 * density).toInt()
            width = (available - margin * (count - 1)) / count
            if (width < minimumTarget && available >= count * minimumTarget) {
                margin = if (count > 1) {
                    ((available - count * minimumTarget) / (count - 1)).coerceAtLeast(0)
                } else {
                    0
                }
                width = minimumTarget
            }
            width = width.coerceIn((40 * density).toInt(), defaultW)
        }
        // Width adapts to the round viewport, but height is a deliberate, stable touch target.
        // Scaling both axes made the top pills look shorter/smaller than Up Next on narrow
        // watches even though the user only asked for horizontal fitting.
        val height = maxOf(minimumTarget, defaultH)
        return Triple(width, height, margin)
    }

    private fun sizeRoundQuickButton(button: ImageView, width: Int, height: Int, margin: Int, first: Boolean) {
        val params = (button.layoutParams as? LinearLayout.LayoutParams)
                ?: LinearLayout.LayoutParams(width, height)
        params.width = width
        params.height = height
        params.marginStart = if (first) 0 else margin
        params.marginEnd = 0
        button.layoutParams = params
    }

    /** Sizes the three fixed quick-panel slots for the manual layout (fitted to the panel width -
     *  see [fittedRoundQuickSizes]) and removes any session-mode dynamic buttons. */
    private fun resetFixedQuickButtonSizes() {
        displayedSessionQuickActions = emptyList()
        val (w, h, margin) = fittedRoundQuickSizes(quickPanelViews().size)
        quickPanelViews().forEachIndexed { index, button ->
            sizeRoundQuickButton(button, w, h, margin, first = index == 0)
        }
    }

    /**
     * The user explicitly selected actions from the current media app. Showing the manual
     * defaults here would be a deceptive fallback, so keep one inert explanatory row until the
     * phone reports notification/MediaSession actions.
     */
    private fun configureUnavailableSessionQuickPanel() {
        displayedSessionQuickActions = emptyList()
        quickPanelViews().forEachIndexed { index, panelButton ->
            quickSlotModes[index] = QuickSlotMode.HIDDEN
            panelButton.setImageDrawable(null)
            panelButton.visibility = View.GONE
            quickSlotUsesRealIcon[index] = false
            sessionSlotShowsAppIcon[index] = false
        }
        (binding.quickActionUpNextIcon.drawable as? Animatable)?.stop()
        quickPanelLongMode = QuickLongMode.HIDDEN
        quickActionUpNextUsesRealIcon = false
        clearQuickUpNextArtwork()
        binding.quickActionUpNext.visibility = View.VISIBLE
        binding.quickActionUpNext.isEnabled = false
        binding.quickActionUpNext.isClickable = false
        binding.quickActionUpNextIcon.setImageResource(R.drawable.ic_queue_music)
        binding.quickActionUpNextLabel.setText(R.string.quick_actions_unavailable)
        binding.quickActionUpNextTrack.visibility = View.GONE
        binding.quickActionUpNext.contentDescription = getString(R.string.quick_actions_unavailable)
    }

    /** Uses the exact icon rasterized from the phone's media notification when available.
     *  Returns true when a real, already-colored bitmap was set (must not be re-tinted by
     *  [setQuickActionButtonActive]); false when the local monochrome fallback glyph was used
     *  instead (should be tinted like the rest of the panel chrome). */
    private fun applySessionQuickIcon(view: ImageView, action: MediaAction): Boolean {
        // Session mode is "From current media app": the user explicitly asked for the app's own
        // controls, so the app's rasterized notification icon wins whenever one exists. The local
        // semantic glyph (sessionQuickFallbackIcon) is only the fallback for actions that arrive
        // without a raster (MediaSession custom actions, whose drawables live in the remote app).
        val bitmap = if (action.hasIconPng() && !action.iconPng.isEmpty) {
            val bytes = action.iconPng.toByteArray()
            val cached = sessionQuickIconBitmaps[action.id]
            // A cached entry was already checked for visible pixels when it was decoded, and its
            // bytes are compared here, so it cannot have become blank. Re-scanning it was cheap
            // at 48px and is not at 128 - this runs for every session slot on every state change
            // while the panel is open.
            if (cached != null && cached.png.contentEquals(bytes)) {
                cached.bitmap
            } else {
                val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (decoded != null && hasVisiblePixels(decoded)) {
                    sessionQuickIconBitmaps[action.id] = CachedSessionQuickIcon(bytes, decoded)
                    decoded
                } else {
                    decoded?.recycle()
                    sessionQuickIconBitmaps.remove(action.id)
                    null
                }
            }
        } else {
            sessionQuickIconBitmaps.remove(action.id)
            null
        }

        return if (bitmap != null) {
            view.setImageBitmap(bitmap)
            setQuickActionIconPadding(view, remoteTemplate = true)
            true
        } else {
            view.setImageResource(sessionQuickFallbackIcon(action))
            setQuickActionIconPadding(view, remoteTemplate = false)
            false
        }
    }

    /** Defensive counterpart of the phone-side raster check for cached payloads produced by an
     * older app version. Transparent PNG bytes are valid data but not a visible action icon.
     * One getPixels pass rather than per-pixel reads: the icons grew from 48px to 128px, and this
     * runs for every session action whenever the panel is rebuilt. */
    private fun hasVisiblePixels(bitmap: Bitmap): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return false
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        return pixels.any { Color.alpha(it) > 12 }
    }

    /**
     * A rasterized player icon is a bitmap and this app's own glyphs are vectors, so they need
     * different fitting to come out the same size.
     *
     * CENTER_INSIDE never scales a bitmap *up*: the phone's raster was drawn at whatever pixel
     * size it arrived at, which put the player's own icons a quarter smaller than the system's
     * media controls show them, and smaller on a denser watch. FIT_CENTER fills the padded box in
     * both directions, which is also why the bitmap branch can afford more padding than the
     * vectors: 10dp of a 52dp button leaves the glyph at the 24dp the system draws.
     */
    private fun setQuickActionIconPadding(view: ImageView, remoteTemplate: Boolean) {
        val paddingDp = if (remoteTemplate) 10f else 13f
        val padding = (paddingDp * resources.displayMetrics.density).roundToInt()
        view.scaleType = if (remoteTemplate) {
            ImageView.ScaleType.FIT_CENTER
        } else {
            ImageView.ScaleType.CENTER_INSIDE
        }
        view.setPadding(padding, padding, padding, padding)
    }

    /** Our own glyph for a known action semantic, or null when the meaning is unknown. Drawing
     *  these instead of each app's rasterized icon is what keeps a given action (shuffle, like,
     *  ...) looking identical across every music app and both delivery paths. */
    private fun localGlyphForSemantic(semantic: String): Int? = when (semantic) {
        "like" -> com.svartifoss.snfell.common.R.drawable.action_like
        "shuffle" -> com.svartifoss.snfell.common.R.drawable.action_shuffle
        "repeat" -> com.svartifoss.snfell.common.R.drawable.action_repeat
        "previous" -> com.svartifoss.snfell.common.R.drawable.action_skip_prev
        "next" -> com.svartifoss.snfell.common.R.drawable.action_skip_next
        "seek_backward" -> com.svartifoss.snfell.common.R.drawable.action_replay_10
        "seek_forward" -> com.svartifoss.snfell.common.R.drawable.action_forward_10
        "pause" -> com.svartifoss.snfell.common.R.drawable.action_pause
        "play" -> com.svartifoss.snfell.common.R.drawable.action_play
        "stop" -> com.svartifoss.snfell.common.R.drawable.action_stop
        "queue" -> R.drawable.ic_queue_music
        else -> null
    }

    /** Never expose publisher-internal action ids (often hashes/UUIDs) to accessibility. */
    private fun sessionActionDescription(action: MediaAction): String {
        action.label.takeIf { it.isNotBlank() }?.let { return it }
        return getString(
                when (action.semantic) {
                    "like" -> R.string.quick_action_like
                    "shuffle" -> R.string.quick_action_shuffle
                    "repeat" -> R.string.quick_action_repeat
                    "previous" -> R.string.action_name_skip_prev
                    "next" -> R.string.action_name_skip_next
                    "pause" -> R.string.action_name_pause
                    "play" -> R.string.action_name_play
                    "stop" -> R.string.action_name_stop
                    "queue" -> R.string.quick_action_up_next
                    else -> R.string.action_name_custom
                }
        )
    }

    private fun sessionQuickFallbackIcon(action: MediaAction): Int {
        localGlyphForSemantic(action.semantic)?.let { return it }
        val hint = "${action.id} ${action.label}".lowercase()
        return when {
            listOf("like", "favorite", "favourite", "heart", "thumb", "curtir", "gostei")
                    .any { hint.contains(it) } ->
                com.svartifoss.snfell.common.R.drawable.action_like
            hint.contains("shuffle") || hint.contains("random") ||
                    hint.contains("aleat") || hint.contains("embaralh") ->
                com.svartifoss.snfell.common.R.drawable.action_shuffle
            hint.contains("repeat") || hint.contains("loop") || hint.contains("repet") ->
                com.svartifoss.snfell.common.R.drawable.action_repeat
            hint.contains("previous") || hint.contains("prev") || hint.contains("back") ||
                    hint.contains("anterior") ->
                com.svartifoss.snfell.common.R.drawable.action_skip_prev
            hint.contains("next") || hint.contains("skip") || hint.contains("forward") ||
                    hint.contains("próxim") || hint.contains("proxim") ->
                com.svartifoss.snfell.common.R.drawable.action_skip_next
            hint.contains("pause") || hint.contains("pausar") ->
                com.svartifoss.snfell.common.R.drawable.action_pause
            hint.contains("play") || hint.contains("tocar") || hint.contains("reproduzir") ->
                com.svartifoss.snfell.common.R.drawable.action_play
            else -> com.svartifoss.snfell.common.R.drawable.action_custom
        }
    }

    /** Starts/stops the Up Next equalizer (see ic_equalizer_bars_animated.xml) to match playback
     *  - a no-op unless the icon is actually showing that drawable right now, so this is safe to
     *  call from the music-state observer on every tick without checking the panel mode first. */
    private fun syncUpNextEqualizerAnimation() {
        val drawable = binding.quickActionUpNextIcon.drawable as? Animatable ?: return
        if (isMusicPlaying) {
            if (!drawable.isRunning) drawable.start()
        } else {
            drawable.stop()
        }
    }

    private fun clearQuickUpNextArtwork() {
        binding.quickActionUpNextArtwork.setImageDrawable(null)
        binding.quickActionUpNextArtwork.visibility = View.GONE
        binding.quickActionUpNextIcon.visibility = View.VISIBLE
        // applyListRowArtworkSize replaces the pill's padding with the cover inset on every side;
        // the glyph shown here wants the text keyline back, or it sits further left and tighter
        // than every other row's icon on the next pass.
        applyUpNextTextPadding()
    }

    /**
     * The Up Next pill's padding when it is showing text and a glyph rather than a cover: a text
     * keyline at the start and enough vertical room for the label/track pair.
     *
     * One function because two places set it - the panel layout pass and [clearQuickUpNextArtwork] -
     * and because [applyListRowArtworkSize] deliberately overrides it when a cover is present. Two
     * copies of these numbers would drift the moment one of them was tuned.
     */
    private fun applyUpNextTextPadding() {
        val d = resources.displayMetrics.density
        binding.quickActionUpNext.setPadding(
                (UP_NEXT_TEXT_PADDING_START_DP * d).roundToInt(),
                (UP_NEXT_TEXT_PADDING_VERTICAL_DP * d).roundToInt(),
                (UP_NEXT_TEXT_PADDING_END_DP * d).roundToInt(),
                (UP_NEXT_TEXT_PADDING_VERTICAL_DP * d).roundToInt())
    }

    private fun onQuickPanelButtonClicked(index: Int) {
        buzz()
        when (quickSlotModes[index]) {
            QuickSlotMode.LIKE -> viewModel.sendQuickAction("like")
            QuickSlotMode.SHUFFLE -> viewModel.sendQuickAction("shuffle")
            QuickSlotMode.REPEAT -> viewModel.sendQuickAction("repeat")
            QuickSlotMode.SESSION -> displayedSessionQuickActions.getOrNull(index)?.let {
                viewModel.sendQuickAction("session:${it.id}")
            }
            QuickSlotMode.CUSTOM -> {
                // Custom actions usually navigate somewhere (playlist, menu, queue...) -
                // close the panel first so the result isn't hidden behind it.
                hideOverlay()
                viewModel.executeAction(
                        ButtonInfo(false, QuickPanelButtons.ALL_SLOTS[index], GESTURE_SINGLE_TAP))
            }
            QuickSlotMode.HIDDEN -> Unit
        }
    }

    private fun screenButtonViews(): List<Pair<Int, ImageView>> = listOf(
            ScreenButtons.SLOT_1 to binding.screenButton1,
            ScreenButtons.SLOT_2 to binding.screenButton2,
            ScreenButtons.SLOT_3 to binding.screenButton3
    )

    /** Compose layouts reserve their lower chrome for shortcuts, so their mini buttons use a
     * compact bezel row instead of the Classic face's larger, user-positioned overlay. This
     * keeps the row away from play controls without shrinking artwork or changing typography. */
    private fun configureScreenButtonsGeometry() {
        val density = resources.displayMetrics.density
        val compact = screenFace in composeFaces
        var baseW = if (compact) 46f else 52f
        var baseH = if (compact) 38f else 42f

        when (screenButtonsShape) {
            "circle", "square", "rounded_square_soft", "rounded_square_medium", "drop", "squircle", "leaf" -> {
                baseW = if (compact) 38f else 42f
                baseH = if (compact) 38f else 42f
            }
            "pill_wide_small", "rounded_rect_small" -> {
                baseW = if (compact) 56f else 62f
                baseH = if (compact) 38f else 42f
            }
            "pill_wide_medium", "rounded_rect_medium" -> {
                baseW = if (compact) 66f else 72f
                baseH = if (compact) 38f else 42f
            }
            "pill_wide_large", "rounded_rect_large" -> {
                baseW = if (compact) 76f else 82f
                baseH = if (compact) 38f else 42f
            }
            "pill_wide_xlarge" -> {
                baseW = if (compact) 86f else 92f
                baseH = if (compact) 38f else 42f
            }
        }
        val visibleButtons = screenButtonViews().map { it.second }
                .filter { it.visibility == View.VISIBLE }
        val visibleCount = visibleButtons.size

        // Classic has a large, centered metadata block and therefore cannot use the same
        // one-size-fits-all row as the lower-chrome Compose faces. Make the controls themselves
        // adapt to the number of configured actions: one keeps the requested shape, two use a
        // 40dp band, and three use a 36dp band. Wide/legacy shapes remain supported, but
        // their width is clamped instead of allowing a 210-276dp row to overflow a 192dp watch.
        // Compose geometry deliberately remains unchanged.
        var gapDp = if (visibleCount == 2) 16f else 12f
        if (!compact && visibleCount > 0) {
            val contentWidthPx = binding.contentFrame.width.takeIf { it > 0 }
                    ?: resources.displayMetrics.widthPixels
            val contentWidthDp = contentWidthPx / density
            val equalAspectShape = screenButtonsShape in setOf(
                    "circle", "square", "rounded_square_soft", "rounded_square_medium",
                    "drop", "squircle", "leaf")

            when (visibleCount) {
                2 -> {
                    baseH = minOf(baseH, 42f)
                    gapDp = 16f
                    val maxRowWidth = minOf(contentWidthDp * 0.78f, 150f)
                            .coerceAtLeast(80f)
                    val maxButtonWidth = ((maxRowWidth - gapDp) / 2f).coerceAtLeast(32f)
                    baseW = if (equalAspectShape) baseH else minOf(baseW, maxButtonWidth)
                }
                3 -> {
                    baseH = minOf(baseH, 40f)
                    gapDp = 12f
                    val maxRowWidth = minOf(contentWidthDp * 0.84f, 164f)
                            .coerceAtLeast(108f)
                    val maxButtonWidth = ((maxRowWidth - gapDp * 2f) / 3f)
                            .coerceAtLeast(32f)
                    baseW = if (equalAspectShape) baseH else minOf(baseW, maxButtonWidth)
                }
                else -> {
                    // A single archived extra-wide shape is harmless while it fits; constrain it
                    // only on unusually narrow displays rather than silently changing its look.
                    baseW = minOf(baseW, (contentWidthDp - 12f).coerceAtLeast(30f))
                }
            }
        }

        val buttonWidth = (baseW * density).roundToInt()
        val buttonHeight = (baseH * density).roundToInt()
        // This is the complete visual gap between adjacent buttons. Previously both the end of
        // one button and the start of the next received the same margin, doubling the intended
        // spacing (16dp for three buttons and 26dp for two). A single start margin keeps the row
        // compact and makes the geometry below describe what is actually drawn.
        val gap = (gapDp * density).roundToInt()

        for ((_, button) in screenButtonViews()) {
            val params = button.layoutParams as ViewGroup.MarginLayoutParams
            val visibleIndex = visibleButtons.indexOf(button)
            params.width = buttonWidth
            params.height = buttonHeight
            params.marginStart = if (visibleIndex > 0) gap else 0
            params.marginEnd = 0
            button.layoutParams = params
        }

        val rowParams = binding.screenButtonsRow.layoutParams as ViewGroup.MarginLayoutParams
        // Position is fully automatic (there is no user offset preference): sit the row as low as
        // possible while keeping the whole row inside a round screen, so it hugs the bottom of the
        // player's space instead of floating high. repositionScreenButtonsRow then slides it up
        // only as far as it must to clear the track text.
        val placement = MiniButtonPlacement.fromPreference(screenButtonsCurveStyle)
        val isCurved = resources.configuration.isScreenRound && placement.followsCurve
        val effectiveCount = if (isCurved) 1 else visibleButtons.size
        autoBottomMarginPx = autoRowBottomMarginPx(effectiveCount, buttonWidth, gap)
        // A rail is centred on the screen and owns its own bounds - applying the bottom-row margin
        // here would shove it off centre on the next size pass, undoing the placement.
        if (!placement.isRail) {
            rowParams.bottomMargin = autoBottomMarginPx
            binding.screenButtonsRow.layoutParams = rowParams
        }
    }

    /** Lowest round-safe resting margin for the mini-button row: the smallest gap from the bottom
     *  edge at which the full row (its widest, lowest corners) still fits inside the circular
     *  display, plus a small breathing inset. Square screens just use a flat inset. */
    private fun autoRowBottomMarginPx(visibleCount: Int, buttonWidthPx: Int, gapPx: Int): Int {
        val density = resources.displayMetrics.density
        if (visibleCount <= 0 || !resources.configuration.isScreenRound) {
            return (16 * density).roundToInt()
        }
        val rowWidthPx = visibleCount * buttonWidthPx + (visibleCount - 1) * gapPx
        val content = binding.contentFrame
        val radius = minOf(
                content.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels,
                content.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels) / 2f
        val halfWidth = rowWidthPx / 2f
        if (radius <= halfWidth) {
            return (16 * density).roundToInt()
        }
        // Bottom corners at (±rowWidth/2, radius - m) must stay within the circle:
        // (rowWidth/2)^2 + (radius - m)^2 <= radius^2  ->  m >= radius - sqrt(radius^2 - halfWidth^2)
        // Keep a small optical inset beyond the bare round-safe minimum. Six dp leaves the row
        // close to the bezel without letting antialiased/rotated corners touch the screen edge.
        val minMargin = radius - kotlin.math.sqrt(radius * radius - halfWidth * halfWidth)
        return (minMargin + 6 * density).roundToInt()
    }

    /** Round-safe bottom margin for an already measured/scaled visual width. Unlike the legacy
     *  count helper above, an over-wide value is clamped to the circle instead of falling back to
     *  16dp and placing its corners outside the display. */
    private fun autoRowBottomMarginForWidthPx(rowWidthPx: Float): Int {
        val density = resources.displayMetrics.density
        if (!resources.configuration.isScreenRound) {
            return (16f * density).roundToInt()
        }
        val content = binding.contentFrame
        val radius = minOf(
                content.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels,
                content.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels) / 2f
        if (radius <= 1f) return (16f * density).roundToInt()

        val halfWidth = (rowWidthPx / 2f).coerceIn(0f, radius - 1f)
        val minMargin = radius - kotlin.math.sqrt(radius * radius - halfWidth * halfWidth)
        return (minMargin + 6f * density).roundToInt()
    }

    /** Shows each configured mini-button slot with its action's icon and hides the rest; the
     *  whole row collapses when nothing is configured, so the screen looks exactly like before
     *  this feature existed. A slot with only a long-press action still shows that icon. */
    private fun updateScreenButtons(config: WatchActionConfigProvider) {
        val visibleButtons = ArrayList<ImageView>(3)
        val faceButtons = ArrayList<FaceMiniButton>(ScreenButtons.ALL_SLOTS.size)

        for ((slotCode, slotView) in screenButtonViews()) {
            val tapAction = config.getAction(ButtonInfo(false, slotCode, GESTURE_SINGLE_TAP))
            val longAction = config.getAction(ButtonInfo(false, slotCode, GESTURE_LONG_TAP))
            val displayedAction = tapAction ?: longAction

            if (displayedAction == null) {
                slotView.visibility = View.GONE
                screenButtonIconTintable.remove(slotCode)
            } else {
                val description = displayedAction.title
                        ?: StandardActionTitles.get(this, displayedAction.key)
                slotView.visibility = View.VISIBLE
                slotView.setImageDrawable(displayedAction.icon)
                screenButtonIconTintable[slotCode] = displayedAction.iconTintable
                slotView.contentDescription = description
                visibleButtons.add(slotView)
                // The same slot, its same icon and its same description, in a form a Compose face
                // can draw. Built here rather than in the face so both paths read one config.
                faceButtons += FaceMiniButton(
                        slotCode = slotCode,
                        icon = displayedAction.icon?.toFaceIcon(),
                        iconTintable = displayedAction.iconTintable,
                        description = description)
            }
        }

        configuredMiniButtons = faceButtons
        screenButtonsConfigured = visibleButtons.isNotEmpty()
        configureScreenButtonsGeometry()
        styleScreenButtons()
        syncScreenButtonsVisibility()
    }

    /** Central visibility contract for every playback/AOD/overlay transition. Paused-track actions
     * stay visible, while the truly empty idle screen remains uncluttered. [forceInteractive] is
     * used while leaving ambient because
     * AmbientLifecycleObserver may still report ambient from inside its exit callback. */
    private fun syncScreenButtonsVisibility(forceInteractive: Boolean = false) {
        val miniButtonsEnabled = miniButtonsEnabledNow()
        val active = hasActiveMiniButtons(
                screenButtonsConfigured, faceState.value.idle, miniButtonsEnabled)
        val visible = if (forceInteractive) {
            active && !overlayActive
        } else {
            shouldShowMiniButtons(
                    configured = screenButtonsConfigured,
                    idle = faceState.value.idle,
                    ambient = ambientObserver.isAmbient,
                    overlayActive = overlayActive,
                    enabledForFace = miniButtonsEnabled)
        }
        // A face that hosts the row draws these buttons itself, so the shared View row must not
        // also draw them - that double render is the overlap this exists to fix. Everything else
        // about the row's activation is untouched: the Up Next pill and the faces' lower-chrome
        // reservation still read `active`, because the buttons are on screen either way.
        val hostedByFace = MiniButtonPlacement.isHostedByFace(screenFace)
        publishHostedMiniButtons(active && !ambientObserver.isAmbient && hostedByFace)
        val wasVisible = binding.screenButtonsRow.visibility == View.VISIBLE
        // Newly shown: keep it transparent until repositionScreenButtonsRow has placed it. The row
        // is laid out at its default XML position first and only nudged into place in a later post,
        // so drawing it in between flashed the outer icons clipped by the round bezel. Alpha 0 lets
        // it measure without being seen; the reposition sets alpha back to 1.
        if (visible && !wasVisible) {
            binding.screenButtonsRow.alpha = 0f
        }
        binding.screenButtonsRow.visibility =
                if (visible && !hostedByFace) View.VISIBLE else View.GONE

        // Curated/Expressive faces reserve their lower chrome only for a row that can actually
        // appear. Only the empty idle state restores their default lower composition.
        // The awake Up Next pill fills that same bottom band, so it shows only when the row does
        // not (and never on the empty idle screen).
        val pillVisible = showUpNextPillPref && !active && !faceState.value.idle
        updateFaceState { state ->
            state.copy(
                    showDefaultBottomPills = !active,
                    // The faces read miniButtonsTopFraction to keep the track time / lower content
                    // clear of whatever occupies the bottom band. The pill takes the same band the
                    // mini-button row would (BottomCenter, padding .07, height ~.25 of the screen),
                    // so reserve its top edge (~.66) - otherwise the track time drew behind it.
                    miniButtonsTopFraction = when {
                        active -> state.miniButtonsTopFraction
                        pillVisible -> AWAKE_PILL_TOP_FRACTION
                        else -> 1f
                    },
                    showUpNextPill = pillVisible)
        }
        if (visible) {
            binding.screenButtonsRow.post { repositionScreenButtonsRow() }
        }
    }

    /** Smart vertical placement for the mini buttons. Classic's title/artist/time block is an
     *  immutable centered layer: this method measures it only as an obstacle and adapts the row,
     *  never the metadata. The row rests as low as the bezel and bottom quadrant allow, then
     *  scales down around its bottom edge when a tall text block needs more room. */
    private fun repositionScreenButtonsRow() {
        val row = binding.screenButtonsRow
        val content = binding.contentFrame
        if (row.visibility != View.VISIBLE || row.height == 0 || content.height == 0) {
            return
        }

        if (MiniButtonPlacement.fromPreference(screenButtonsCurveStyle).isRail) {
            // Nothing to dodge and nothing to lift: a rail is placed absolutely against the bezel
            // and occupies no bottom band. Report the band as free so the faces underneath use the
            // full height instead of reserving room for a row that is no longer there.
            applyScreenButtonsCurvature()
            if (row.alpha != screenButtonsOpacity) {
                row.alpha = screenButtonsOpacity
            }
            updateFaceState { state ->
                if (abs(state.miniButtonsTopFraction - RAIL_TOP_FRACTION) < .002f) {
                    state
                } else {
                    state.copy(miniButtonsTopFraction = RAIL_TOP_FRACTION)
                }
            }
            return
        }

        val density = resources.displayMetrics.density
        val gapPx = 6 * density

        val contentLoc = IntArray(2).also { content.getLocationInWindow(it) }
        val viewLoc = IntArray(2)

        // Bottom of the lowest visible line of the centered text block - only meaningful for
        // Classic's static View text, which the row must dodge. Compose faces have no equivalent
        // fixed block to measure (the View-based text block is GONE, and reading its last
        // measured geometry only made the row jump according to stale Classic text); their own
        // content instead reads the resulting miniButtonsTopFraction below and keeps itself clear
        // of wherever the row ends up (see NowPlayingFaceState.miniButtonsTopFraction and
        // CuratedPlayerFaces.TrackFooter), so the row here settles at its preferred offset
        // without chasing a synthetic measurement.
        val textBottom = if (screenFace in composeFaces) {
            null
        } else {
            listOf(binding.textArtist, binding.textTitle, binding.textPlaybackTime)
                    .filter { it.visibility == View.VISIBLE && it.height > 0 }
                    .maxOfOrNull {
                        it.getLocationInWindow(viewLoc)
                        (viewLoc[1] + it.height - contentLoc[1]).toFloat()
                    }
        }

        // Clear the bottom quadrant only when it is really drawn. Compose covers the old View
        // chrome, Hidden has alpha 0, and an unconfigured quadrant has no drawable.
        val bottomIconDrawn = screenFace !in composeFaces &&
                binding.iconBottom.visibility == View.VISIBLE &&
                binding.iconBottom.alpha > 0f &&
                binding.iconBottom.drawable != null
        val iconBottomLimit = if (bottomIconDrawn) {
            binding.iconBottom.getLocationInWindow(viewLoc)
            viewLoc[1] - contentLoc[1] - gapPx
        } else {
            Float.POSITIVE_INFINITY
        }

        // Where the static layout put the row (its automatic bottom margin).
        val baseBottom = (content.height -
                (row.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin).toFloat()

        val visibleButtons = screenButtonViews().map { it.second }
                .filter { it.visibility == View.VISIBLE && it.width > 0 && it.height > 0 }
        if (visibleButtons.isEmpty()) return

        // Curvature is established before calculating the visible extent. Its padding is only a
        // touch-bounds reserve; maxRise is the part that is actually painted above the buttons.
        val maxRise = applyScreenButtonsCurvature()
        val buttonHeight = visibleButtons.maxOf { it.height }.toFloat()
        val buttonWidth = visibleButtons.maxOf { it.width }.toFloat()
        val rowWidth = row.width.toFloat().coerceAtLeast(buttonWidth)
        val isCurved = resources.configuration.isScreenRound && screenButtonsCurveStyle != "flat"

        // Guard against old extra-wide persisted shapes and very small displays even before the
        // vertical collision calculation. Classic already receives count-aware LayoutParams in
        // configureScreenButtonsGeometry; this is the final measured-size safety net.
        val horizontalInset = 6f * density
        val minimumScale = if (screenFace in composeFaces) 0.45f else 0.78f
        val horizontalScale = ((content.width - horizontalInset * 2f) / rowWidth)
                .coerceIn(minimumScale, 1f)
        var scale = horizontalScale
        var bottom = baseBottom

        // Scaling makes a row narrower and therefore lets it rest lower on a round bezel. Iterate
        // a few times because that lower resting line in turn gives Classic more vertical room.
        repeat(4) {
            val safeWidth = (if (isCurved) buttonWidth else rowWidth) * scale
            val safeBottom = (content.height - autoRowBottomMarginForWidthPx(safeWidth)).toFloat()
            bottom = minOf(safeBottom, iconBottomLimit)

            val requiredTop = textBottom?.plus(gapPx)
            if (requiredTop != null) {
                val unscaledVisualExtent = buttonHeight + maxRise
                val availableHeight = (bottom - requiredTop).coerceIn(0f, Float.POSITIVE_INFINITY)
                scale = minOf(scale, availableHeight / unscaledVisualExtent)
                        .coerceIn(minimumScale, 1f)
            }
        }

        // Re-evaluate the resting line once with the final scale from the loop.
        val safeWidth = (if (isCurved) buttonWidth else rowWidth) * scale
        bottom = minOf(
                (content.height - autoRowBottomMarginForWidthPx(safeWidth)).toFloat(),
                iconBottomLimit)

        row.translationY = bottom - baseBottom
        row.pivotX = row.width / 2f
        row.pivotY = row.height.toFloat()
        row.scaleX = scale
        row.scaleY = scale
        // Now placed: reveal it (syncScreenButtonsVisibility hid it at alpha 0 to avoid a flash of
        // the un-positioned row). Restore the user's configured opacity, not a hardcoded 1.
        if (row.alpha != screenButtonsOpacity) {
            row.alpha = screenButtonsOpacity
        }

        val effectiveTop = bottom - (buttonHeight + maxRise) * scale
        val topFraction = (effectiveTop / content.height).coerceIn(.20f, .95f)
        updateFaceState { state ->
            if (abs(state.miniButtonsTopFraction - topFraction) < .002f) {
                state
            } else {
                state.copy(miniButtonsTopFraction = topFraction)
            }
        }
    }

    /**
     * Optional curved arrangement (the "Mini buttons curve" phone setting): each side button
     * is raised by exactly how much the round screen's edge rises at its horizontal position
     * (R - sqrt(R² - dx²), i.e. constant vertical distance to the bezel at any row depth), and
     * depending on the style also rotated toward the circle's tangent - "arc" keeps the pills
     * upright, "curved_soft" applies half the tangent angle, "curved" the full angle. The row
     * gets top padding for the raised buttons so they stay inside its touch bounds (parents
     * only dispatch touches within child bounds), and clipChildren is off so rotated pill
     * corners aren't shaved. No-op (flat row) when set to "flat" or on square screens.
     */
    private fun applyScreenButtonsCurvature(): Float {
        val row = binding.screenButtonsRow
        val buttons = screenButtonViews().map { it.second }
        val visibleButtons = buttons.filter { it.visibility == View.VISIBLE }

        val requested = MiniButtonPlacement.fromPreference(screenButtonsCurveStyle)
        applyScreenButtonsRowBounds(requested)
        if (requested.isRail) {
            return applyScreenButtonsRail(requested, visibleButtons)
        }
        // Leaving a rail: the pills carry horizontal offsets no bottom-row pass would clear.
        for (button in buttons) {
            button.translationX = 0f
        }

        val placement = MiniButtonPlacement.fromPreference(screenButtonsCurveStyle)
        val tiltFraction = placement.tiltFraction

        if (!placement.followsCurve || !resources.configuration.isScreenRound) {
            for (button in buttons) {
                button.translationX = 0f
                button.translationY = 0f
                button.rotation = 0f
            }
            if (row.paddingTop != 0) {
                row.setPadding(0, 0, 0, 0)
            }
            return 0f
        }

        val content = binding.contentFrame
        val radius = minOf(content.width, content.height) / 2f
        if (radius <= 0 || row.width == 0) {
            return 0f
        }

        row.clipChildren = false
        row.clipToPadding = false
        content.clipChildren = false
        content.clipToPadding = false

        // Two passes: first work out every button's rise/rotation WITHOUT touching the views, so
        // maxRise (and therefore the row's top padding) is known and applied *before* any button
        // is actually moved. Applying translationY per-button inside the same pass that was still
        // accumulating maxRise used to move the raised (elevated) buttons into a top area whose
        // padding wasn't reserved yet on that pass - on a round screen with clipToPadding already
        // false this could render a button poking above the row's still-too-short bounds, reading
        // as a wrongly elevated middle button and clipped-looking side buttons until a later pass
        // (if any) caught up.
        data class Placement(val button: ImageView, val rise: Float, val rotation: Float)
        val placements = ArrayList<Placement>(buttons.size)
        var maxRise = 0f
        for (button in buttons) {
            if (button.visibility != View.VISIBLE || button.width == 0) {
                continue
            }

            val buttonCenterX = row.left + button.left + button.width / 2f
            val dx = buttonCenterX - content.width / 2f
            val clampedDx = dx.coerceIn(-radius + 1f, radius - 1f)

            // For side buttons, calculate the natural rise at the outer edge of the button
            // to ensure it clears the circular bezel.
            val buttonWidth = button.width.toFloat()
            val outerDx = if (dx != 0f) {
                val sign = if (dx > 0f) 1f else -1f
                (dx + sign * buttonWidth / 2f).coerceIn(-radius + 1f, radius - 1f)
            } else {
                clampedDx
            }

            // Extreme changes the tangent angle, not the bezel clearance - see the enum's note.
            val riseScale = placement.riseScale
            val referenceDx = (buttonWidth / 2f).coerceIn(0f, radius - 1f)
            val referenceClearance = radius - kotlin.math.sqrt(
                    radius * radius - referenceDx * referenceDx)
            val outerClearance = radius - kotlin.math.sqrt(
                    radius * radius - outerDx * outerDx)
            // The row's automatic bottom margin already includes the centered button's clearance.
            // Raise a side button only by the additional clearance its outer edge needs.
            val naturalRise = (outerClearance - referenceClearance)
                    .coerceAtLeast(0f) * riseScale
            // Curving a compact bezel row by the full circle equation can lift its side buttons
            // back over the face controls. Keep just enough rise to read as an arc, but enough to avoid clipping.
            // Preserve the arc without floating the outer buttons back into the player. The old
            // 32/64dp caps visually detached a compact row from the lower bezel.
            val maxCap = placement.maxRiseDp
            val baseRise = minOf(naturalRise, maxCap * resources.displayMetrics.density)
            // With three configured buttons, non-Extreme curves lift the two outer pills a final
            // 4dp. Extreme already follows the exact bezel delta, so adding it there would make
            // the controls float inward again.
            val isOuterOfThree = visibleButtons.size == 3 &&
                    (button === visibleButtons.first() || button === visibleButtons.last())
            val rise = baseRise + if (isOuterOfThree &&
                    placement != MiniButtonPlacement.CURVED_EXTREME) {
                4f * resources.displayMetrics.density
            } else {
                0f
            }
            // Tangent to the circle: the outer end of each side pill tips up along the bezel.
            val tangentRotation = tiltFraction *
                    -Math.toDegrees(kotlin.math.asin((clampedDx / radius).toDouble())).toFloat()
            val maxRotation = placement.maxRotationDegrees
            val rotation = tangentRotation.coerceIn(-maxRotation, maxRotation)

            placements.add(Placement(button, rise, rotation))
            maxRise = maxOf(maxRise, rise)
        }

        // Rotated pills stick out past their own height a little; pad enough for both. Committed
        // (and, if it actually changes, laid out) BEFORE any button is moved into that space.
        val paddingDp = 4
        val neededPadding = (maxRise + paddingDp * resources.displayMetrics.density).toInt()
        if (row.paddingTop != neededPadding) {
            row.setPadding(0, neededPadding, 0, 0)
            row.requestLayout()
        }

        val spreadOffsets = if (placement.axis == MiniButtonPlacement.Axis.BOTTOM_ROW_SPREAD) {
            spreadOffsetsFor(visibleButtons, row, content, radius, maxRise)
        } else {
            emptyMap()
        }

        for ((button, rise, rotation) in placements) {
            button.translationX = spreadOffsets[button] ?: 0f
            button.translationY = -rise
            button.rotation = rotation
        }
        return maxRise
    }

    /**
     * Sizes the row itself to whatever the placement needs to stay tappable.
     *
     * A pill is only touchable where its *parent* also receives the event: a `ViewGroup` never
     * dispatches a touch that lands outside its own bounds, however far a child has been
     * translated. The default `wrap_content` row is therefore only correct while the pills stay
     * inside it - the moment one is pushed out to a wall or an edge it would draw in the right
     * place and do nothing when tapped, the least debuggable kind of broken.
     *
     * A full-frame row is safe to leave over the player: the row is not itself clickable, so when
     * no pill is hit it declines the event and the parent carries on down to the gesture layers
     * beneath it.
     */
    private fun applyScreenButtonsRowBounds(placement: MiniButtonPlacement) {
        val row = binding.screenButtonsRow
        val params = row.layoutParams as? FrameLayout.LayoutParams ?: return
        val wantsFullWidth = placement.isRail ||
                placement.axis == MiniButtonPlacement.Axis.BOTTOM_ROW_SPREAD
        val targetWidth = if (wantsFullWidth) {
            FrameLayout.LayoutParams.MATCH_PARENT
        } else {
            FrameLayout.LayoutParams.WRAP_CONTENT
        }
        val targetHeight = if (placement.isRail) {
            FrameLayout.LayoutParams.MATCH_PARENT
        } else {
            FrameLayout.LayoutParams.WRAP_CONTENT
        }
        val targetGravity = if (placement.isRail) {
            Gravity.CENTER
        } else {
            Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
        }
        // A rail is centred on the screen, so the auto bottom margin that lifts a bottom row clear
        // of the bezel would only push it off-centre.
        val targetBottomMargin = if (placement.isRail) 0 else autoBottomMarginPx
        if (params.width == targetWidth && params.height == targetHeight &&
                params.gravity == targetGravity && params.bottomMargin == targetBottomMargin) {
            return
        }
        params.width = targetWidth
        params.height = targetHeight
        params.gravity = targetGravity
        params.bottomMargin = targetBottomMargin
        row.layoutParams = params
        if (placement.isRail) {
            // The bottom-row pass owns these; a rail places every pill absolutely and must start
            // from an untransformed row or the offsets compound.
            row.translationY = 0f
            row.scaleX = 1f
            row.scaleY = 1f
        }
    }

    /**
     * Horizontal offsets that push a spread row's outer pills out to the screen's edges.
     *
     * The reach is the *chord* at the row's own depth, not the screen width: near the bottom of a
     * round display the usable width has already collapsed well inside the diameter, and spreading
     * to half the width would put the outer pills behind the bezel. Everything is measured from the
     * pill's outer edge for the same reason.
     *
     * The row itself is widened to the full frame by [applyScreenButtonsRowBounds] before this
     * runs - a `wrap_content` row would keep its original narrow touch bounds and the moved pills,
     * drawn correctly, would simply not be tappable (a parent only dispatches touches that land
     * inside a child).
     */
    private fun spreadOffsetsFor(
            visibleButtons: List<ImageView>,
            row: View,
            content: View,
            radius: Float,
            maxRise: Float
    ): Map<ImageView, Float> {
        if (visibleButtons.size < 2) return emptyMap()
        val centerX = content.width / 2f
        // Depth of the highest point the row reaches, so the chord is measured where the pills
        // actually are rather than at their resting line.
        val rowCenterY = row.top + row.height / 2f - maxRise
        val dy = abs(rowCenterY - content.height / 2f).coerceAtMost(radius - 1f)
        val halfChord = kotlin.math.sqrt(radius * radius - dy * dy)
        val inset = 6f * resources.displayMetrics.density

        val offsets = HashMap<ImageView, Float>()
        val first = visibleButtons.first()
        val last = visibleButtons.last()
        for (button in visibleButtons) {
            if (button !== first && button !== last) continue
            val sign = if (button === first) -1f else 1f
            val currentCenterX = row.left + button.left + button.width / 2f
            val targetCenterX = centerX + sign * (halfChord - inset - button.width / 2f)
            // Never pull a pill inwards: on a narrow screen the chord can sit inside where the row
            // already had it, and "spread" that squeezes is worse than one that does nothing.
            val delta = targetCenterX - currentCenterX
            offsets[button] = if (sign < 0f) minOf(delta, 0f) else maxOf(delta, 0f)
        }
        return offsets
    }

    /**
     * Lays the pills out down a wall instead of along the bottom.
     *
     * Each pill is placed at its own depth and then pushed out to the chord at *that* depth, so the
     * rail hugs the bezel rather than running down a straight line that only touches it once. The
     * pills stay upright: a rail is read top-to-bottom and rotating each one to the tangent turned
     * the labels into a fan.
     *
     * Returns 0 for the row's rise because a rail occupies no bottom band at all - which is also
     * why [repositionScreenButtonsRow] reports the full height as free for the faces underneath.
     */
    private fun applyScreenButtonsRail(
            placement: MiniButtonPlacement,
            visibleButtons: List<ImageView>
    ): Float {
        val row = binding.screenButtonsRow
        val content = binding.contentFrame
        if (content.width == 0 || row.width == 0 || visibleButtons.isEmpty()) return 0f

        row.clipChildren = false
        row.clipToPadding = false
        content.clipChildren = false
        content.clipToPadding = false
        if (row.paddingTop != 0) {
            row.setPadding(0, 0, 0, 0)
        }

        val density = resources.displayMetrics.density
        val radius = minOf(content.width, content.height) / 2f
        val centerX = content.width / 2f
        val centerY = content.height / 2f
        val gap = 8f * density
        val inset = 6f * density
        val round = resources.configuration.isScreenRound

        fun place(button: ImageView, targetCenterX: Float, targetCenterY: Float) {
            val currentCenterX = row.left + button.left + button.width / 2f
            val currentCenterY = row.top + button.top + button.height / 2f
            button.translationX = targetCenterX - currentCenterX
            button.translationY = targetCenterY - currentCenterY
            button.rotation = 0f
        }

        /** Stacks [group] down one wall, centred vertically on the screen. */
        fun rail(group: List<ImageView>, onLeft: Boolean) {
            if (group.isEmpty()) return
            val step = group.first().height + gap
            val firstOffset = -(group.size - 1) / 2f
            group.forEachIndexed { index, button ->
                val targetCenterY = centerY + (firstOffset + index) * step
                val dy = abs(targetCenterY - centerY).coerceAtMost(radius - 1f)
                val halfExtent = if (round) {
                    // Clear the bezel at the pill's own corners, not at its centre line, or a tall
                    // pill's top and bottom edges cross the glass while its middle looks fine.
                    val corner = (dy + button.height / 2f).coerceAtMost(radius - 1f)
                    kotlin.math.sqrt(radius * radius - corner * corner)
                } else {
                    centerX
                }
                val sign = if (onLeft) -1f else 1f
                place(button,
                        centerX + sign * (halfExtent - inset - button.width / 2f),
                        targetCenterY)
            }
        }

        when (placement.axis) {
            MiniButtonPlacement.Axis.LEFT_RAIL -> rail(visibleButtons, onLeft = true)
            MiniButtonPlacement.Axis.RIGHT_RAIL -> rail(visibleButtons, onLeft = false)
            MiniButtonPlacement.Axis.SPLIT_RAILS -> {
                val left = visibleButtons.filterIndexed { i, _ ->
                    MiniButtonPlacement.splitSideIsLeft(i)
                }
                val right = visibleButtons.filterIndexed { i, _ ->
                    !MiniButtonPlacement.splitSideIsLeft(i)
                }
                rail(left, onLeft = true)
                rail(right, onLeft = false)
            }
            else -> Unit
        }
        return 0f
    }

    /**
     * Applies the user's mini-button appearance: pill background style (glass / solid /
     * transparent) and color source (neutral theme glass, live album accent - optionally
     * desaturated - or a fixed custom color). Re-run whenever the preferences or the dynamic
     * accent change, and to restore a button after the press-feedback highlight.
     */
    private fun styleScreenButtons() {
        binding.screenButtonsRow.alpha = screenButtonsOpacity
        for ((slotCode, button) in screenButtonViews()) {
            styleScreenButton(button, screenButtonIconTintable[slotCode] ?: true)
        }
        publishMiniButtonAppearance()
    }

    /** Hands a hosting face the same surface the row above just painted itself with. Called from
     *  [styleScreenButtons], which [applyAccentColor] already runs, so an album-derived style
     *  follows the artwork instead of lagging a track behind it. */
    private fun publishMiniButtonAppearance() {
        val surface = MiniButtonSurfaces.resolve(screenButtonsBgStyle, miniButtonPalette())
        updateFaceState { state ->
            if (state.miniButtonSurface == surface &&
                    state.miniButtonsAlpha == screenButtonsOpacity) {
                state
            } else {
                state.copy(
                        miniButtonSurface = surface,
                        miniButtonsAlpha = screenButtonsOpacity)
            }
        }
    }

    /** The one gate on whether a face draws the mini buttons, so the row and the face can never
     *  both be showing them (or neither). */
    private fun publishHostedMiniButtons(show: Boolean) {
        val buttons = if (show) configuredMiniButtons else emptyList()
        updateFaceState { state ->
            if (state.miniButtons == buttons) state else state.copy(miniButtons = buttons)
        }
    }

    private fun styleScreenButton(
            button: ImageView,
            iconTintable: Boolean = screenButtonViews()
                    .firstOrNull { it.second === button }
                    ?.let { screenButtonIconTintable[it.first] }
                    ?: true
    ) {
        val density = resources.displayMetrics.density
        val compactRow = screenFace in composeFaces
        val visibleCount = screenButtonViews().count { it.second.visibility == View.VISIBLE }
        val paddingDp = when {
            !compactRow && visibleCount >= 3 -> 5
            !compactRow && visibleCount == 2 -> 7
            compactRow && screenTheme == ScreenTheme.COMPACT -> 8
            compactRow && screenFace == "spectrum" -> 8
            compactRow -> 7
            screenTheme == ScreenTheme.COMPACT -> 11
            else -> 9
        }
        val padding = (paddingDp * density).roundToInt()
        button.setPadding(padding, padding, padding, padding)
        // Unlike the theme's decorative quadrant icons, mini buttons are the user's own
        // explicitly configured controls - "Hidden" mutes ambient chrome, not actions the user
        // deliberately placed here (same exemption as Expressive's transport icons, see
        // expressiveIconAlpha in ExpressiveFace.kt).
        val miniButtonIconAlpha = screenTheme.tokens.iconAlpha.takeIf { it > 0f } ?: 1f
        button.imageAlpha = (miniButtonIconAlpha * 255).roundToInt().coerceIn(0, 255)
        button.clearColorFilter()

        // The colour of a mini button is decided in common, not here: the Chat face draws these
        // same buttons inside its own composition with Compose, and the phone previews them with
        // Canvas. Three surfaces answering "what does screen_buttons_bg_style look like?" from
        // three copies of one `when` is what let the preview and the wrist disagree.
        val surface = MiniButtonSurfaces.resolve(screenButtonsBgStyle, miniButtonPalette())
        button.background = when {
            surface.followsFaceNeutral -> neutralMiniButtonBackground()
            // Neither a fill nor a stroke: an icon on its own, with no shape to paint at all.
            // Distinct from a transparent fill, which still carries the button's outline.
            surface.fillArgb == 0 && surface.strokeArgb == 0 -> null
            else -> GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(surface.fillArgb)
                if (surface.strokeArgb != 0 && surface.strokeWidthDp > 0f) {
                    setStroke(
                            (surface.strokeWidthDp * density).roundToInt(),
                            surface.strokeArgb)
                }
            }
        }
        surface.iconTintArgb?.let { tint ->
            // A style that forces its tint means it: the treatment is a single flat colour, so it
            // applies even to an app icon or fetched cover. Everything else leaves a full-colour
            // icon alone, since flattening a cover to one hue destroys what it was chosen for.
            if (surface.forceIconTint || iconTintable) button.setColorFilter(tint)
        }

        val background = button.background
        if (background is GradientDrawable) {
            // layoutParams.height first: it holds the TARGET height configureScreenButtonsGeometry
            // just set, while button.height is the last MEASURED height - stale within the same
            // pass of a shape change (e.g. a "circle" radius computed from the previous shape's
            // height rendered as a lopsided capsule until the next relayout).
            val paramsHeight = (button.layoutParams?.height ?: 0).takeIf { it > 0 }
            val heightPx = paramsHeight?.toFloat()
                    ?: if (button.height > 0) button.height.toFloat()
                    else (if (screenFace in composeFaces) 38f else 42f) * density
            applyButtonShape(background, heightPx, density)
        }
    }

    private fun applyButtonShape(drawable: GradientDrawable, heightPx: Float, density: Float) {
        drawable.shape = GradientDrawable.RECTANGLE
        when (screenButtonsShape) {
            "square" -> {
                drawable.cornerRadius = 0f
            }
            "rounded_square_soft", "rounded_rect_small" -> {
                drawable.cornerRadius = 8f * density
            }
            "rounded_square_medium", "rounded_rect_medium", "rounded_rect_large" -> {
                drawable.cornerRadius = 12f * density
            }
            "squircle" -> {
                drawable.cornerRadius = 15f * density
            }
            "leaf" -> {
                val c1 = 16f * density
                val c2 = 4f * density
                drawable.cornerRadii = floatArrayOf(c1, c1, c2, c2, c1, c1, c2, c2)
            }
            "drop" -> {
                val c = 18f * density
                drawable.cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, c, c, c, c)
            }
            "circle" -> {
                drawable.cornerRadius = heightPx / 2f
            }
            else -> { // "pill" and wide pills
                drawable.cornerRadius = 999f
            }
        }
    }

    /** The colours [MiniButtonSurfaces] draws its styles from. "solid_theme" is the one that is
     *  face-dependent - Expressive paints it as a tonal surface of the theme colour where every
     *  other face uses it raw - so that choice is made here and handed over resolved. */
    private fun miniButtonPalette(): MiniButtonSurfaces.Palette {
        val albumAccent = currentAccentColor.takeIf { it != 0 } ?: defaultSeekBarColor
        val themeAccent = if (screenFace == "expressive") {
            PaletteTransforms.tonalSurface(defaultSeekBarColor, 0.74f, 0.40f, 0.92f)
        } else {
            defaultSeekBarColor
        }
        return MiniButtonSurfaces.paletteFor(albumAccent, themeAccent)
                .copy(uniformGlassFill = getColor(R.color.glass_surface_fill))
    }

    /** The "glass" (Follow layout) mini-button pill. Deliberately independent of the selected
     *  now-playing face - the appearance is the user's own choice, not something the layout
     *  dictates (a face used to force e.g. square Studio pills). It still adapts to the explicit
     *  ScreenTheme choice (minimal/amoled/contrast) since that is a separate user setting. */
    private fun neutralMiniButtonBackground(): android.graphics.drawable.Drawable {
        val density = resources.displayMetrics.density
        return when (screenTheme) {
            ScreenTheme.MINIMAL, ScreenTheme.AMOLED -> capsule(
                    Color.TRANSPARENT,
                    (1f * density).roundToInt(),
                    0x66FFFFFF)
            ScreenTheme.CONTRAST -> capsule(
                    Color.BLACK,
                    (2f * density).roundToInt(),
                    Color.WHITE)
            // mutate(): applyButtonShape mutates the drawable's corner radius, and without a
            // private constant state that mutation leaked into every other user of
            // glass_pill_background (the quick panel's pills picked up the mini buttons' shape).
            else -> AppCompatResources.getDrawable(this, R.drawable.glass_pill_background)!!.mutate()
        }
    }

    // --- Appearance-scoped reads. The active context deliberately separates the structural face
    // from the storage namespace: presets keep `key@face`, while a custom theme uses its isolated
    // active snapshot without modifying (or inheriting mutable values from) the base preset.
    private fun faceString(def: com.matejdro.wearutils.preferences.definition.PreferenceDefinition<String>): String =
            FaceScopedPreferences.getString(preferences, def, appearanceContext)

    private fun faceBool(def: com.matejdro.wearutils.preferences.definition.PreferenceDefinition<Boolean>): Boolean =
            FaceScopedPreferences.getBoolean(preferences, def, appearanceContext)

    private fun faceInt(def: com.matejdro.wearutils.preferences.definition.PreferenceDefinition<Int>): Int =
            FaceScopedPreferences.getInt(preferences, def, appearanceContext)

    private fun hasFacePreference(baseKey: String): Boolean =
            FaceScopedPreferences.containsExplicitValue(preferences, baseKey, appearanceContext)

    /** Runtime migration for a watch paired to an older phone or a config restored before the
     * rewritten Colors page has been opened. */
    private fun resolveColorTreatmentPreference(): String {
        if (hasFacePreference(MiscPreferences.WEAR_COLOR_TREATMENT.key)) {
            return faceString(MiscPreferences.WEAR_COLOR_TREATMENT)
        }
        // Artist/progress preferences are independent targets again; never let one legacy custom
        // target silently recolor the whole watch when the unified key is absent.
        return if (faceBool(MiscPreferences.WEAR_DYNAMIC_ACCENT)) "expressive" else "normal"
    }

    private fun resolveNormalColorPreference(): String {
        if (hasFacePreference(MiscPreferences.WEAR_NORMAL_COLOR.key)) {
            return faceString(MiscPreferences.WEAR_NORMAL_COLOR)
        }
        return ""
    }

    /** Rasterizes a configured action's icon (BitmapDrawable or vector) to an ImageBitmap so a
     *  Compose face can render it. Null on failure -> the face keeps its default glyph. */
    private fun android.graphics.drawable.Drawable.toFaceIcon(): androidx.compose.ui.graphics.ImageBitmap? =
            try {
                val fallback = (24 * resources.displayMetrics.density).toInt().coerceAtLeast(1)
                val w = intrinsicWidth.takeIf { it > 0 } ?: fallback
                val h = intrinsicHeight.takeIf { it > 0 } ?: fallback
                toBitmap(w, h).asImageBitmap()
            } catch (e: Exception) {
                null
            }

    private val preferencesChangeObserver = Observer<SharedPreferences?> {
        if (it == null) {
            return@Observer
        }

        preferences = it

        // A language change cannot be applied in place: every already-inflated string, and the
        // Resources the activity was created against, are bound to the old locale. Recreating is
        // the only way, and it must happen before any of the appearance work below - that work
        // would just be redone by the new instance.
        if (WatchLanguage.tagFrom(it) != createdLanguageTag) {
            recreate()
            return@Observer
        }

        // Resolve the active theme up front. screenFace remains the validated structural renderer
        // used by Compose dispatch and AOD follow; appearanceContext owns all scoped reads.
        appearanceContext = ThemeAppearance.resolve(preferences)
        screenFace = appearanceContext.baseFace

        stemButtonsManager.enableDoublePressInAmbient = !Preferences.getBoolean(
                preferences,
                MiscPreferences.DISABLE_PHYSICAL_DOUBLE_CLICK_IN_AMBIENT
        )

        // Re-applied here as well as in onStart: this preference is phone-owned, so it can flip
        // while the player is already on screen and would otherwise only take effect on the next
        // open.
        applyKeepScreenOnPreference()

        // Only restyles a screen that is already up; deliberately does not run the auto-open side
        // of the idle config, which would yank the user into another screen just because a
        // preference landed from the phone while they were looking at this one.
        if (binding.idleStateGroup.visibility == View.VISIBLE) {
            applyIdleScreenConfiguration()
        }

        alwaysDisplayClock = faceBool(MiscPreferences.ALWAYS_SHOW_TIME)
        if (!ambientObserver.isAmbient) {
            if (screenFace in composeFaces) {
                // Compose faces render their own FaceClock. Keep the classic View clock (and the
                // top quadrant icon) hidden so the two clocks never stack - they use slightly
                // different top offsets and briefly read as a doubled clock otherwise.
                binding.ambientClock.visibility = View.GONE
                binding.iconTop.visibility = View.GONE
            } else if (alwaysDisplayClock) {
                binding.ambientClock.visibility = View.VISIBLE
                binding.iconTop.visibility = View.GONE
                handler.sendEmptyMessage(MESSAGE_UPDATE_CLOCK)
            } else {
                binding.iconTop.visibility = View.VISIBLE
                binding.ambientClock.visibility = View.GONE
            }
        }

        dimAlbumArt = faceBool(MiscPreferences.DIM_ALBUM_ART)
        albumArtStyle = faceString(MiscPreferences.ALBUM_ART_STYLE)
        carouselCardShape.value = CarouselCardShape.fromPreference(
                faceString(MiscPreferences.WEAR_CAROUSEL_CARD_SHAPE))
        playerBackgroundStyle = PlayerBackgroundStyle.fromPreference(albumArtStyle)
        blurAlbumArtBackground = playerBackgroundStyle.blurredArtwork
        albumArtGrayscale = playerBackgroundStyle.grayscaleArtwork
        albumArtHidden = playerBackgroundStyle.hidesArtwork
        blurRadiusPx = faceInt(MiscPreferences.ALBUM_ART_BLUR_RADIUS)
                .coerceIn(5, 120).toFloat()
        // The classic host ImageView applies these itself (applyMainAlbumArtDisplay); the Compose
        // faces read the same choice through the shared state instead of ignoring it.
        updateFaceState {
            it.copy(
                    backgroundStyle = playerBackgroundStyle,
                    // A shared piece rendered by the background layer, so it is resolved here with
                    // the rest of the backdrop rather than by whichever face happens to want it.
                    accentFloor = AccentFloorStyle.fromPreference(
                            faceString(MiscPreferences.WEAR_ACCENT_FLOOR)),
                    // Resolved here with the rest of the backdrop for the same reason: Split's
                    // panel *is* its background, it just happens to be the one the shared layer
                    // cannot draw.
                    splitPanelStyle = SplitPanelStyle.fromPref(
                            faceString(MiscPreferences.WEAR_SPLIT_PANEL)),
                    albumArtHidden = albumArtHidden,
                    albumArtGrayscale = albumArtGrayscale,
                    albumArtBlurred = blurAlbumArtBackground,
                    albumArtBlurRadiusPx = blurRadiusPx
            )
        }
        playerShadingStyle = PlayerShadingStyle.fromPreference(
                faceString(MiscPreferences.WEAR_PLAYER_SHADING_STYLE))
        playerShadingIntensity = resolveShadingMultiplier()
        shadingColorMode = faceString(MiscPreferences.WEAR_SHADING_COLOR_MODE)
                ?: MiscPreferences.WEAR_SHADING_COLOR_MODE.defaultValue
        shadingCustomColor = faceString(MiscPreferences.WEAR_SHADING_CUSTOM_COLOR).orEmpty()
        updateFaceState {
            it.copy(
                    backdropDimEnabled = dimAlbumArt,
                    backdropDimStrength = playerShadingIntensity,
                    backdropShadingStyle = playerShadingStyle,
                    backdropShadingColor = resolvedShadingColor()
            )
        }
        volumeBarTimeoutMs = Preferences.getInt(preferences, MiscPreferences.VOLUME_OVERLAY_TIMEOUT)
                .coerceIn(300, 5000).toLong()
        rotaryDeadzone = Preferences.getInt(preferences, MiscPreferences.ROTARY_DEADZONE)
                .coerceIn(0, 30).toFloat()
        ambientAlbumArtAlpha = faceInt(MiscPreferences.AMBIENT_ALBUM_ART_OPACITY)
                .coerceIn(20, 100) / 100f

        aodStyle = faceString(MiscPreferences.WEAR_AOD_STYLE)
        aodShowArt = faceBool(MiscPreferences.WEAR_AOD_SHOW_ART)
        aodArtTreatment = AodArtTreatment.fromPreference(
                faceString(MiscPreferences.WEAR_AOD_ART_TREATMENT))
        aodShowClock = faceBool(MiscPreferences.WEAR_AOD_SHOW_CLOCK)
        aodShowTrackInfo = faceBool(MiscPreferences.WEAR_AOD_SHOW_TRACK_INFO)
        aodColorMode = faceString(MiscPreferences.WEAR_AOD_COLOR_MODE)
        aodCustomColor = faceString(MiscPreferences.WEAR_AOD_CUSTOM_COLOR)
        aodShowTransport = faceBool(MiscPreferences.WEAR_AOD_SHOW_TRANSPORT)
        aodShowProgress = faceBool(MiscPreferences.WEAR_AOD_SHOW_PROGRESS)
        aodShowPills = faceBool(MiscPreferences.WEAR_AOD_SHOW_PILLS)
        aodIntensity = faceInt(MiscPreferences.WEAR_AOD_INTENSITY)
                .coerceIn(20, 100) / 100f
        updateFaceState {
            it.copy(
                    ambientShowTrackInfo = aodShowTrackInfo,
                    ambientTint = resolvedAodTint(),
                    ambientIntensity = aodIntensity,
                    ambientShowTransport = aodShowTransport,
                    ambientShowProgress = aodShowProgress,
                    ambientShowPills = aodShowPills
            )
        }

        clockColorMode = faceString(MiscPreferences.WEAR_CLOCK_COLOR_MODE)
        clockCustomColor = faceString(MiscPreferences.WEAR_CLOCK_CUSTOM_COLOR).orEmpty()
        clockOpacity = faceInt(MiscPreferences.WEAR_CLOCK_OPACITY)
        // applyClockAppearance() is deferred until after wearFontKey and the face state's fontKey
        // are refreshed below, so the View clock's typeface uses the just-read font on this pass.

        screenButtonsCurveStyle = faceString(MiscPreferences.WEAR_SCREEN_BUTTONS_CURVE_STYLE)
        screenButtonsBgStyle = faceString(MiscPreferences.WEAR_SCREEN_BUTTONS_BG)
        screenButtonsShape = faceString(MiscPreferences.WEAR_SCREEN_BUTTONS_SHAPE)
        screenButtonsOpacity = faceInt(MiscPreferences.WEAR_SCREEN_BUTTONS_OPACITY)
                .coerceIn(0, 100) / 100f
        miniButtonsMode = faceString(MiscPreferences.WEAR_MINI_BUTTONS_MODE)
        gesturesMode = faceString(MiscPreferences.WEAR_GESTURES_MODE)
        showUpNextPillPref = faceBool(MiscPreferences.WEAR_SHOW_UP_NEXT_PILL)
        // The shape also drives per-button WIDTH/HEIGHT (circle -> square box, wide pills -> wider
        // boxes - see configureScreenButtonsGeometry). Restyling without re-running the geometry
        // left a shape change applying only its corner radius until the next full config sync,
        // which is why shape switches looked half-applied on the watch.
        configureScreenButtonsGeometry()
        styleScreenButtons()
        // The enabled flag doesn't change screenButtonsConfigured itself, so it needs its own
        // visibility pass - configureScreenButtonsGeometry/styleScreenButtons only restyle a row
        // that's already showing, they don't decide whether it should be.
        syncScreenButtonsVisibility()
        binding.screenButtonsRow.post { repositionScreenButtonsRow() }

        overlayBlurRadiusPx = faceInt(MiscPreferences.WEAR_OVERLAY_BLUR_RADIUS)
                .coerceIn(5, 120).toFloat()
        applyBlurredAlbumArt(latestAlbumArt)
        overlayBackdropStyle = faceString(MiscPreferences.WEAR_OVERLAY_BACKDROP_STYLE)

        binding.volumeBar.barStyle = VolumeStyle.fromPref(
                faceString(MiscPreferences.WEAR_VOLUME_STYLE))
        binding.volumeBar.barLayout = VolumeLayout.fromPref(
                faceString(MiscPreferences.WEAR_VOLUME_LAYOUT))
        val progressRingStyle = faceString(MiscPreferences.WEAR_PROGRESS_STYLE)
        binding.seekBar.ringStyle = RingStyle.fromPref(progressRingStyle)
        binding.seekBar.gradientEnabled = faceBool(MiscPreferences.WEAR_PROGRESS_GRADIENT)
        updateFaceState { it.copy(progressRingStyle = progressRingStyle) }
        seekOverlayStyle = faceString(MiscPreferences.WEAR_SEEK_STYLE)
        seekPanelLayout = faceString(MiscPreferences.WEAR_SEEK_LAYOUT)
        quickPanelStyle = faceString(MiscPreferences.WEAR_QUICK_PANEL_STYLE)
        upNextPillStyle = faceString(MiscPreferences.WEAR_UP_NEXT_PILL_STYLE)
        // The awake player pill shares the quick-panel Up Next style; refresh its resolved colours
        // so a style change updates the player pill too, not only the quick panel.
        updateFaceState {
            it.copy(upNextPillFill = upNextPillFillColor(), upNextPillTextColor = awakeUpNextPillTint())
        }
        quickPanelLayout = faceString(MiscPreferences.WEAR_QUICK_PANEL_LAYOUT)
        // One switch drives every cover-capable pill (queue, menu, quick panel) rather than a
        // separate picker per surface, so "use the cover" is a single choice for the user.
        coverPillStyle = QueueStyle.fromPref(faceString(MiscPreferences.WEAR_QUEUE_STYLE))
        coverPillsActive = coverPillStyle.isCover
        quickPanelShortcutCoverEnabled = faceBool(MiscPreferences.WEAR_QUICK_PANEL_SHORTCUT_COVER)
        // Legacy cover_compact / cover_tall still name their own size; otherwise the standalone
        // preference owns it, so the quick panel's pills match the queue's rows.
        listRowSize = coverPillStyle.legacyRowSize
                ?: QueueRowSize.fromPref(faceString(MiscPreferences.WEAR_LIST_ROW_SIZE))
        applyListRowHeight()
        // Source controls a phone-side MediaSession binding and is intentionally global; it is
        // not part of a saved appearance snapshot (see FaceScopedPreferences.SCOPED_KEYS).
        quickPanelSource = Preferences.getString(
                preferences, MiscPreferences.WEAR_QUICK_PANEL_SOURCE)
        if (isQuickActionsPanelShowing()) {
            configureQuickPanelButtons()
            renderQuickPanelExtraActions()
            applyQuickPanelLayout()
        }
        centerLongPressQueueEnabled = Preferences.getBoolean(
                preferences, MiscPreferences.WEAR_CENTER_LONG_PRESS_QUEUE
        )
        centerLongPressAction = CenterLongPressAction.resolve(
                Preferences.getString(preferences, MiscPreferences.WEAR_CENTER_LONG_PRESS))
        colorTreatment = resolveColorTreatmentPreference()
        normalColor = resolveNormalColorPreference()
        // Plain faceBool now that the key is in SCOPED_KEYS: the four-step resolution ends at the
        // definition default (true), which is what the hand-rolled presence check was standing in
        // for while the key was unscoped and faceBool could not see it.
        normalColorMulti = faceBool(MiscPreferences.WEAR_NORMAL_COLOR_MULTI)
        colorModifier = ColorModifier.fromPreference(faceString(MiscPreferences.WEAR_COLOR_MODIFIER))
        colorHueShift = faceInt(MiscPreferences.WEAR_COLOR_HUE_SHIFT).toFloat()
        albumAccentSource = AlbumAccentSource.fromPreference(
                faceString(MiscPreferences.WEAR_ALBUM_ACCENT_SOURCE))
        titleColorMode = faceString(MiscPreferences.WEAR_TITLE_COLOR_MODE)
        titleCustomColor = faceString(MiscPreferences.WEAR_TITLE_CUSTOM_COLOR)
        titleAdaptiveContrast = faceBool(MiscPreferences.WEAR_TITLE_ADAPTIVE_CONTRAST)
        artistColorMode = faceString(MiscPreferences.WEAR_ARTIST_COLOR_MODE)
        artistCustomColor = faceString(MiscPreferences.WEAR_ARTIST_CUSTOM_COLOR)
        artistLegacyDesaturated = faceBool(MiscPreferences.WEAR_ARTIST_DESATURATED)
        artistAdaptiveContrast = faceBool(MiscPreferences.WEAR_ARTIST_ADAPTIVE_CONTRAST)
        clockAdaptiveContrast = faceBool(MiscPreferences.WEAR_CLOCK_ADAPTIVE_CONTRAST)
        progressColorMode = faceString(MiscPreferences.WEAR_PROGRESS_COLOR_MODE)
        progressCustomColor = faceString(MiscPreferences.WEAR_PROGRESS_CUSTOM_COLOR)
        progressLegacyDesaturated = faceBool(MiscPreferences.WEAR_PROGRESS_DESATURATED)
        volumeColorMode = faceString(MiscPreferences.WEAR_VOLUME_COLOR_MODE)
        volumeCustomColor = faceString(MiscPreferences.WEAR_VOLUME_CUSTOM_COLOR)
        quickPanelColorMode = faceString(MiscPreferences.WEAR_QUICK_PANEL_COLOR_MODE)
        quickPanelCustomColor = faceString(MiscPreferences.WEAR_QUICK_PANEL_CUSTOM_COLOR)
        // Palette extraction remains enabled when any independently colored surface needs album
        // swatches, even if the face-wide treatment itself is fixed Normal.
        val globalTreatment = resolvedGlobalColorTreatment()
        wearDynamicAccentEnabled = listOf(
                SurfaceColorTreatment.fromPreference(
                        artistColorMode, artistLegacyDesaturated).resolveAgainst(globalTreatment),
                SurfaceColorTreatment.fromPreference(
                        progressColorMode, progressLegacyDesaturated).resolveAgainst(globalTreatment),
                SurfaceColorTreatment.fromPreference(volumeColorMode).resolveAgainst(globalTreatment),
                SurfaceColorTreatment.fromPreference(quickPanelColorMode).resolveAgainst(globalTreatment),
                globalTreatment
        ).any { treatment -> treatment.isAlbumDerived }
        albumArtFadeEnabled = faceBool(MiscPreferences.WEAR_ALBUM_ART_FADE)
        // Faces that draw the cover themselves (Poster & co.) honor the fade through their own
        // Crossfade; the host ImageView keeps handling it for classic/expressive backgrounds.
        updateFaceState { it.copy(albumArtFade = albumArtFadeEnabled) }
        screenTheme = ScreenTheme.fromPreference(
                faceString(MiscPreferences.WEAR_SCREEN_THEME)
        )
        quadrantTapFlashEnabled = faceBool(MiscPreferences.WEAR_QUADRANT_TAP_FLASH)
        showTrackTitle = faceBool(MiscPreferences.WEAR_SHOW_TRACK_TITLE)
        showTrackArtist = faceBool(MiscPreferences.WEAR_SHOW_TRACK_ARTIST)
        playerControlsVisible = faceBool(MiscPreferences.WEAR_PLAYER_CONTROLS_VISIBLE)
        internalProgressVisible = faceBool(MiscPreferences.WEAR_INTERNAL_PROGRESS_VISIBLE)
        edgeProgressVisible = faceBool(MiscPreferences.WEAR_EDGE_PROGRESS_VISIBLE)
        edgeSeekEnabled = faceBool(MiscPreferences.WEAR_EDGE_SEEK_ENABLED)
        updateEdgeSeekTouchState()
        expressiveSeekMode = faceString(MiscPreferences.WEAR_EXPRESSIVE_SEEK_MODE)
        wearFontKey = faceString(MiscPreferences.WEAR_FONT)
        wearClockFontKey = faceString(MiscPreferences.WEAR_CLOCK_FONT)
        wearLyricsFontKey = faceString(MiscPreferences.WEAR_LYRICS_FONT)
        metadataGroups = TrackMetadataFields.Group.entries
                .filterTo(mutableSetOf()) { group ->
                    faceBool(MiscPreferences.metadataGroupPreference(group))
                }
        val keepsEssentialTransport = screenFace == "expressive" || screenFace == "material"
        // Expressive and Material use their center transport as the composition's only obvious
        // playback affordance. Hidden/Show controls off therefore become a deliberate no-op for
        // those two faces, while Poster, Studio, Classic and every other curated face continue to
        // honor both control visibility and style.
        val effectiveFaceTheme = if (keepsEssentialTransport && screenTheme == ScreenTheme.HIDDEN) {
            ScreenTheme.DEFAULT
        } else {
            screenTheme
        }
        updateFaceState {
            it.copy(
                    screenTheme = effectiveFaceTheme,
                    showControls = playerControlsVisible || keepsEssentialTransport,
                    showInternalProgress = internalProgressVisible,
                    // Cookie and bezel are disjoint targets; neither disables the other.
                    centralSeekEnabled = shouldEnableCentralSeek(expressiveSeekMode),
                    showClock = alwaysDisplayClock,
                    fontKey = wearFontKey,
                    clockFontKey = wearClockFontKey,
                    lyricsFontKey = wearLyricsFontKey,
                    metadataGroups = metadataGroups
            )
        }
        // Now that wearFontKey and the state's fontKey are current, resolve the awake clock colour
        // + typeface (see the deferred note where the clock prefs are read above).
        applyClockAppearance()
        applyMetadataVisibility()

        trackTimeMode = faceString(MiscPreferences.WEAR_TRACK_TIME_MODE)
        updatePlaybackTimeVisibility()

        val titleTextModePref = faceString(MiscPreferences.WEAR_TITLE_TEXT_MODE)
        val titleTextMode = when (titleTextModePref) {
            "marquee" -> TextSizingMode.MARQUEE
            "wrap" -> TextSizingMode.WRAP
            "shrink" -> TextSizingMode.SHRINK
            else -> TextSizingMode.SMART
        }
        binding.textTitle.setSizingMode(titleTextMode)
        // Every Compose face reads the same raw preference value through AdaptiveTitleText now,
        // instead of each hardcoding one fixed overflow strategy the way they used to.
        updateFaceState { it.copy(titleTextMode = titleTextModePref ?: "smart") }

        titleTypography = WatchTypography.titleSpec(preferences, appearanceContext)
        artistTypography = WatchTypography.artistSpec(preferences, appearanceContext)
        sourceIconTypography = WatchTypography.sourceIconSpec(preferences, appearanceContext)
        clockTypography = WatchTypography.clockSpec(preferences, appearanceContext)
        flexAxes = WatchTypography.flexAxes(preferences, appearanceContext)
        updateFaceState {
            it.copy(
                    titleTypography = titleTypography,
                    artistTypography = artistTypography,
                    sourceIconTypography = sourceIconTypography,
                    clockTypography = clockTypography,
                    flexAxes = flexAxes)
        }
        applyClassicTypography()

        // Re-apply the last raw palette so changing Normal/Desaturated/Expressive updates every
        // consumer immediately, without waiting for a new album-art callback.
        applyAccentColor(rawAccentColor.takeIf { it != 0 } ?: defaultSeekBarColor)
        activeOverlayKind?.let { kind ->
            activeOverlayUsesBlur = applyOverlayBackdrop(kind)
            binding.overlayBlurImage.visibility =
                    if (activeOverlayUsesBlur) View.VISIBLE else View.GONE
            when (kind) {
                OverlayKind.VOLUME -> {
                    applyVolumeOverlayStyle()
                    applyVolumePanelLayout()
                }
                OverlayKind.SEEK -> {
                    applySeekOverlayStyle(binding.seekBar.progress)
                    applySeekPanelLayout(binding.seekBar.progress)
                }
                OverlayKind.QUICK_ACTIONS -> applyQuickPanelLayout()
            }
        }

        applyAlbumArtScrim()
        applyPlayerBackground()
        applyScreenFace()

        updateDynamicAccentFromArt(latestAlbumArt)

        if (!ambientObserver.isAmbient) {
            binding.albumArtScrim.visibility =
                    if (shouldShowInteractiveScrim()) View.VISIBLE else View.INVISIBLE
            applyMainAlbumArtDisplay(latestAlbumArt, forceBlur = blurAlbumArtBackground)
        } else {
            // A config edit can arrive mid-ambient (ConfigListenerService delivers while idle) -
            // re-style the already-showing AOD instead of waiting for the next round-trip. Last,
            // after every field it depends on (aod*, screenFace) has been re-read above.
            applyAmbientPresentation()
        }
        updateDeveloperOverlay()
    }

    /** Shading strength as a 0..[SHADING_MAX_MULTIPLIER] multiplier from the numeric percentage
     *  preference. A user who set a legacy named level but never touched the numeric slider keeps
     *  that level's equivalent percentage until they do, so the migration is loss-free. */
    private fun resolveShadingMultiplier(): Float {
        val hasNumeric = FaceScopedPreferences.containsExplicitValue(
                preferences, MiscPreferences.ALBUM_ART_DIM_STRENGTH.key, appearanceContext)
        val hasNamed = FaceScopedPreferences.containsExplicitValue(
                preferences, MiscPreferences.WEAR_PLAYER_SHADING_INTENSITY.key, appearanceContext)
        val percent = if (!hasNumeric && hasNamed) {
            PlayerShadingIntensity.percentFor(
                    faceString(MiscPreferences.WEAR_PLAYER_SHADING_INTENSITY))
        } else {
            faceInt(MiscPreferences.ALBUM_ART_DIM_STRENGTH)
        }
        return percent.coerceIn(0, SHADING_MAX_PERCENT) / 100f
    }

    /** Colour that tints the shading gradient. Black by default; album/desaturated/custom resolve
     *  to a dark, still-chromatic tone so the overlay keeps darkening while carrying the hue. */
    private fun resolvedShadingColor(): Int {
        val accent = currentAccentColor.takeIf { it != 0 } ?: defaultSeekBarColor
        return when (shadingColorMode) {
            "album" -> PaletteTransforms.shadingTone(accent)
            "desaturated" ->
                PaletteTransforms.shadingTone(PaletteTransforms.softenedAlbumAccent(accent))
            "custom" -> parseHexColorOrNull(shadingCustomColor)
                    ?.let { PaletteTransforms.shadingTone(it) } ?: Color.BLACK
            else -> Color.BLACK
        }
    }

    private fun applyAlbumArtScrim() {
        val usesSharedLayer = playerShadingStyle != PlayerShadingStyle.FOLLOW ||
                playerBackgroundStyle.isPlainArtworkTreatment
        binding.albumArtScrim.background = PlayerShadingDrawable(
                style = playerShadingStyle,
                intensity = if (dimAlbumArt && usesSharedLayer) playerShadingIntensity else 0f,
                primary = currentAccentColor.takeIf { it != 0 } ?: defaultSeekBarColor,
                secondary = resolvedSecondaryAccent(),
                shadingColor = resolvedShadingColor()
        )
    }

    /** Native background renderer used only by Classic; Compose faces draw the same treatment
     * inside their own canvas so layout and background remain independently selectable. */
    private fun applyPlayerBackground() {
        // Authored background styles (Expressive, Poster, ...) own their designed look and must
        // render it regardless of the "Dim album art" toggle - that toggle governs the separate
        // legibility scrim over plain artwork, not a background style's identity. The intensity
        // slider still modulates their depth. Plain treatments have no authored overlay.
        val authoredStrength = if (
            playerShadingStyle == PlayerShadingStyle.FOLLOW &&
            !playerBackgroundStyle.isPlainArtworkTreatment
        ) {
            (playerShadingIntensity / .8f).coerceIn(0f, SHADING_MAX_MULTIPLIER / .8f)
        } else {
            0f
        }
        binding.playerBackground.background = PlayerBackgroundDrawable(
                style = playerBackgroundStyle,
                authoredStrength = authoredStrength,
                primary = currentAccentColor.takeIf { it != 0 } ?: defaultSeekBarColor,
                secondary = resolvedSecondaryAccent(),
                tertiary = resolvedTertiaryAccent(),
                materialSurface = currentAccentColor.takeIf { it != 0 } ?: defaultSeekBarColor,
                materialSurfaceSoftened = colorTreatment == "desaturated")
    }

    /** Compose faces own the same layer inside their composition; the host View is Classic-only. */
    private fun shouldShowInteractiveScrim(): Boolean =
            dimAlbumArt && screenFace == "classic" &&
                    (playerShadingStyle != PlayerShadingStyle.FOLLOW ||
                            playerBackgroundStyle.isPlainArtworkTreatment)

    /** Keep the bezel scrubber inert whenever another full-screen surface owns input. Merely
     * fading the ring to alpha=0 is insufficient because an invisible View remains hit-testable. */
    private fun updateEdgeSeekTouchState() {
        binding.seekBar.touchSeekingEnabled =
                edgeSeekEnabled && playbackSeekable && !overlayActive
    }

    /**
     * Applies the selected now-playing face (see NowPlayingFace.kt): "classic" keeps the
     * original View presentation; "expressive" swaps the central rendering - text block,
     * bezel seek ring, center tap zone and quadrant hint icons - for the Compose face. The
     * classic views keep being updated while hidden (ambient mode and the quick panel read
     * from them), and every input layer stays shared between faces. Ambient always reverts
     * to the classic presentation - see [ambientCallback].
     */
    private fun applyScreenFace() {
        if (ambientObserver.isAmbient) {
            return
        }
        applyScreenFaceNow()
    }

    /** [applyScreenFace] without the ambient guard - onExitAmbient MUST use this variant:
     *  inside the exit callback [AmbientLifecycleObserver.isAmbient] can still report true,
     *  which made the guarded call a silent no-op and left ambient-only visibilities (e.g. the
     *  force-shown edge seek ring) leaking into the interactive screen. */
    private fun applyScreenFaceNow() {
        val composeFace = screenFace in composeFaces
        if (composeFace) {
            composeFaceKind.value = screenFace
        }
        // Verse is the only face that shows the song's words, and looking them up costs a network
        // request on the phone per track - so the feed follows the face rather than running for
        // everyone. Driven from here because this is the one place the applied face is settled,
        // including when the phone pushes a new choice while the screen is already up.
        viewModel.setLyricsEnabled(screenFace == FACE_VERSE)
        // Same rule, same reason: the phone reads a file (and, if the user switched it on,
        // queries a service) to answer this, so nobody who has not selected the face pays.
        viewModel.setMetadataEnabled(screenFace == FACE_METADATA)
        binding.expressiveFace.visibility = if (composeFace) View.VISIBLE else View.GONE
        binding.playerBackground.visibility = if (
            composeFace || binding.idleStateGroup.visibility == View.VISIBLE
        ) View.GONE else View.VISIBLE
        binding.classicTextBlock.visibility = if (composeFace) View.GONE else View.VISIBLE
        // Keep the bezel View/hit target present when only the arc is hidden. onTouchEvent passes
        // non-bezel touches through, so this does not steal the layouts' central controls.
        binding.seekBar.drawProgress = edgeProgressVisible
        binding.seekBar.visibility =
                if (shouldKeepEdgeSeekView(edgeProgressVisible, edgeSeekEnabled)) View.VISIBLE else View.GONE
        updateEdgeSeekTouchState()
        binding.centerTapZone.visibility = if (composeFace) View.GONE else View.VISIBLE
        configureScreenButtonsGeometry()
        styleScreenButtons()
        syncScreenButtonsVisibility()
        applyScreenThemeNow()
    }

    private fun applyScreenTheme() {
        if (ambientObserver.isAmbient) {
            return
        }
        applyScreenThemeNow()
    }

    /** [applyScreenTheme] without the ambient guard - see [applyScreenFaceNow]. */
    private fun applyScreenThemeNow() {
        val icons = listOf(binding.iconTop, binding.iconBottom, binding.iconLeft, binding.iconRight)
        applyAlbumArtScrim()
        val alwaysShowTime = faceBool(MiscPreferences.ALWAYS_SHOW_TIME)
        val tokens = screenTheme.tokens

        // The backdrop scrim is never theme-owned. Gesture regions, the center tap zone, seek
        // ring, text and the user-configured mini buttons keep their existing geometry for every
        // theme - only the quadrant hint icons' opacity and size change.
        binding.albumArtScrim.visibility =
                if (shouldShowInteractiveScrim()) View.VISIBLE else View.INVISIBLE

        if (screenFace in composeFaces) {
            // Compose faces resolve the same tokens from NowPlayingFaceState. The host still owns
            // the shared art/scrim, while Vinyl and Poster deliberately paint their own backdrop.
            icons.forEach { it.visibility = View.GONE }
            binding.ambientClock.visibility = View.GONE
            return
        }

        val iconSizeDefault = resources.getDimensionPixelSize(R.dimen.music_screen_icon_size)
        val iconSize = (iconSizeDefault * tokens.iconScale).roundToInt()
        icons.forEach { icon ->
            val hideTop = icon === binding.iconTop && alwaysShowTime
            icon.visibility = if (playerControlsVisible && !hideTop) View.VISIBLE else View.GONE
            icon.alpha = tokens.iconAlpha
            val params = icon.layoutParams
            params.width = iconSize
            params.height = iconSize
            icon.layoutParams = params
        }
        binding.ambientClock.visibility = if (alwaysShowTime) View.VISIBLE else View.GONE
    }

    private val notificationObserver = Observer<Notification?> {
        if (it == null) {
            return@Observer
        }

        val notificationPopup = binding.notificationPopup

        notificationPopup.title.text = it.title
        notificationPopup.body.text = it.description
        notificationPopup.backgroundImage.setImageBitmap(it.background)

        showNotification(it)
    }

    private val phoneVolumeListener = Observer<Float> {
        binding.volumeBar.volume = it
    }

    private val playbackPositionObserver = Observer<PlaybackPosition?> { position ->
        // Mid-rotary-scrub the ring shows the pending seek target - don't let the live position
        // ticker yank it back to the playing position between crown detents. The commit runnable
        // clears the pending fraction and seekTo() re-anchors the position, so ticks resume from
        // the scrubbed point right after.
        if (pendingRotarySeekFraction != null) {
            return@Observer
        }

        if (position == null || position.durationMs <= 0) {
            binding.seekBar.seekable = false
            playbackSeekable = false
            updateEdgeSeekTouchState()
            hasPlaybackPosition = false
            updatePlaybackTimeVisibility()
            updateFaceState { it.copy(seekable = false) }
            lastKnownPositionMs = 0L
            lastKnownDurationMs = 0L
            updateDeveloperOverlay()
            return@Observer
        }

        lastKnownPositionMs = position.positionMs
        lastKnownDurationMs = position.durationMs
        playbackSeekable = position.seekable
        // Duration is enough to draw progress; session seek capability only gates interaction.
        binding.seekBar.seekable = true
        updateEdgeSeekTouchState()
        binding.seekBar.progress = position.positionMs.toFloat() / position.durationMs

        updateFaceState {
            it.copy(
                    progress = position.positionMs.toFloat() / position.durationMs,
                    seekable = position.seekable,
                    positionMs = position.positionMs,
                    durationMs = position.durationMs
            )
        }

        hasPlaybackPosition = true
        updatePlaybackTimeVisibility()
        binding.textPlaybackTime.text = getString(
                R.string.playback_time_format,
                formatPlaybackTime(position.positionMs),
                formatPlaybackTime(position.durationMs)
        )
        updateDeveloperOverlay()
    }

    /** Applies [MiscPreferences.WEAR_TRACK_TIME_MODE] on top of the base requirement that a
     *  usable position exists. Ambient mode is left alone: onEnterAmbient force-hides the line
     *  (a once-a-minute position would just look frozen) and onExitAmbient calls back here. */
    private fun updatePlaybackTimeVisibility() {
        if (ambientObserver.isAmbient) {
            return
        }
        val allowedByMode = when (trackTimeMode) {
            "never" -> false
            "playing" -> isMusicPlaying
            "paused" -> !isMusicPlaying
            else -> true
        }
        binding.textPlaybackTime.visibility =
                if (hasPlaybackPosition && allowedByMode) View.VISIBLE else View.GONE
        updateFaceState { it.copy(showTrackTime = hasPlaybackPosition && allowedByMode) }
    }

    private fun formatPlaybackTime(timeMs: Long): String {
        val totalSeconds = timeMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(java.util.Locale.getDefault(), "%d:%02d", minutes, seconds)
    }

    private val volumeBarPopupListener = Observer<Unit?> {
        showVolumeBar()
    }

    private val openActionsMenuListener = Observer<Unit?> {
        startMenu(showCustomList = false)
    }

    private val openPlaybackQueueScreenListener = Observer<Unit?> {
        startActivity(Intent(this, QueueActivity::class.java))
    }

    private val openLyricsScreenListener = Observer<Unit?> {
        openLyricsScreen()
    }

    /**
     * Feeds the Verse face its words.
     *
     * Only the synced case produces lines. Unsynced lyrics are deliberately dropped rather than
     * shown as a block: this face's whole composition is "the line being sung", and a wall of
     * untimed text has no line being sung - the face's title card is the honest rendering of that
     * and looks like a design rather than a failure.
     *
     * `pending` is kept distinct from "no lines" so the face can hold its title card back at full
     * strength while an answer is still on its way, instead of asserting a track has no lyrics a
     * moment before its lyrics arrive.
     */
    /** The Metadata face's table. Null until the phone answers - which is that face's own empty
     *  state, not a spinner: the point of the screen is that it draws from what is already here. */
    private val trackMetadataObserver = Observer<com.svartifoss.snfell.proto.TrackMetadata?> { meta ->
        updateFaceState { face -> face.copy(metadata = meta) }
    }

    private val lyricsStateObserver = Observer<LyricsUiState?> { state ->
        val lines = (state as? LyricsUiState.Synced)?.lines.orEmpty()
        updateFaceState { face ->
            face.copy(lyricLines = lines, lyricsPending = state is LyricsUiState.Loading)
        }
    }

    /**
     * Opens the lyrics screen, handing it the accent already resolved here.
     *
     * Passing the colour rather than letting that screen work it out is what stops it opening on
     * the default accent and correcting itself a moment later: album colour is extracted
     * asynchronously (see applyAccentColor), so a screen starting from scratch has nothing to draw
     * with for the first frames. It re-derives its own once the artwork changes under it.
     */
    private fun openLyricsScreen() {
        startActivity(Intent(this, LyricsActivity::class.java)
                .putExtra(LyricsActivity.EXTRA_ACCENT_COLOR, currentAccentColor))
    }

    private val openStreamingShortcutsMenuListener = Observer<Unit?> {
        startMenu(
                showCustomList = true,
                customListId = CustomLists.PLAYLIST_SHORTCUTS
        )
    }

    private val closeAppListener = Observer<Unit?> {
        finish()
    }

    /**
     * MenuActivity is a pure picker - the chosen entry comes back here and is executed on THIS
     * activity's view model, so watch-executed actions (volume, search, open menu) raise their
     * events (volume popup, voice input, ...) where they can actually be shown.
     */
    private val menuLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            return@registerForActivityResult
        }
        val data = result.data ?: return@registerForActivityResult

        val actionIndex = data.getIntExtra(MenuActivity.RESULT_EXTRA_ACTION_INDEX, -1)
        if (actionIndex >= 0) {
            viewModel.executeActionFromMenu(actionIndex)
            return@registerForActivityResult
        }

        val listId = data.getStringExtra(MenuActivity.RESULT_EXTRA_LIST_ID)
        val entryId = data.getStringExtra(MenuActivity.RESULT_EXTRA_ENTRY_ID)
        if (listId != null && entryId != null) {
            viewModel.executeItemFromCustomMenu(listId, entryId)
        }
    }

    private fun startMenu(showCustomList: Boolean, customListId: String? = null) {
        menuLauncher.launch(
                Intent(this, MenuActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra(MenuActivity.EXTRA_SHOW_CUSTOM_LIST, showCustomList)
                        .putExtra(MenuActivity.EXTRA_CUSTOM_LIST_ID, customListId)
        )
    }

    private val customListListener = Observer<CustomListWithBitmaps?> {
        if (it == null) {
            return@Observer
        }

        updateUpNextPreview(it)

        // The quick-actions panel only asked for this to populate its "Up Next" preview text -
        // it hasn't asked to see the full list, so don't yank the user into the menu for it.
        if (isQuickActionsPanelShowing()) {
            return@Observer
        }

        // Playback queue (PLAYLIST) and its history fallback (HISTORY) belong to QueueActivity
        // exclusively. Block both by listId, not only by activeEntryId — some apps never set
        // activeQueueItemId, which previously left activeEntryId null and let the menu open.
        if (!it.activeEntryId.isNullOrEmpty() ||
                it.listId == CustomLists.PLAYLIST ||
                it.listId == CustomLists.HISTORY) {
            return@Observer
        }

        val lastListDisplayed = Preferences.getString(
                preferences,
                MiscPreferences.LAST_MENU_DISPLAYED
        ).toLong()

        // MenuActivity observes the custom list itself, so an already-open menu updates in
        // place - only a genuinely new list needs a launch.
        if (lastListDisplayed != it.listTimestamp) {
            startMenu(showCustomList = true)

            val editor = preferences.edit()
            Preferences.putString(
                    editor,
                    MiscPreferences.LAST_MENU_DISPLAYED,
                    it.listTimestamp.toString()
            )
            editor.apply()
        }
    }


    private val stemButtonListener = { buttonKeyCode: Int, gesture: Int ->
        if (gesture == GESTURE_DOUBLE_TAP) {
            handler.postDelayed(this::buzz, ViewConfiguration.getDoubleTapTimeout().toLong())
        } else if (buttonKeyCode != SpecialButtonCodes.TURN_ROTARY_CW && buttonKeyCode != SpecialButtonCodes.TURN_ROTARY_CCW) {
            buzz()
        }

        viewModel.executeAction(ButtonInfo(true, buttonKeyCode, gesture))
    }

    val backButtonOverrideCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            stemButtonsManager.simulateKeyPress(KeyEvent.KEYCODE_BACK)
        }
    }

    private val quickActionsPanelBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            hideOverlay()
        }
    }

    private val firstRunHintsBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (firstRunHintsPage > 0) {
                showFirstRunHintsPage(firstRunHintsPage - 1)
            } else {
                closeFirstRunHints(completed = false)
            }
        }
    }

    /** Shows the current short, paged player lesson once per guide version. */
    private fun setupFirstRunHints() {
        if (firstRunHintsPending ||
                localUiPrefs().getInt(KEY_GESTURE_GUIDE_VERSION, 0) >= GESTURE_GUIDE_VERSION) {
            return
        }

        firstRunHintsPending = true
        firstRunHintsPage = 0
        firstRunHintsBackCallback.isEnabled = true

        val hints = binding.firstRunHints.root
        // The root remains clickable to consume touches, but deliberately has no dismiss action:
        // an exploratory tap must not permanently skip the lesson.
        hints.setOnClickListener(null)
        binding.firstRunHints.firstRunHintsBack.setOnClickListener {
            if (firstRunHintsPage > 0) showFirstRunHintsPage(firstRunHintsPage - 1)
        }
        binding.firstRunHints.firstRunHintsNext.setOnClickListener {
            val lastPage = binding.firstRunHints.firstRunHintsPages.childCount - 1
            if (firstRunHintsPage == lastPage) {
                closeFirstRunHints(completed = true)
            } else {
                showFirstRunHintsPage(firstRunHintsPage + 1)
            }
        }
        showFirstRunHintsPage(0)

        // Slight delay so the sheet fades in over a settled screen instead of fighting the
        // activity's own entrance transition. It blocks input from the moment it turns VISIBLE.
        hints.alpha = 0f
        hints.visibility = View.VISIBLE
        hints.animate().alpha(1f).setStartDelay(400).setDuration(300).start()
    }

    private fun showFirstRunHintsPage(page: Int) {
        val pages = binding.firstRunHints.firstRunHintsPages
        firstRunHintsPage = page.coerceIn(0, pages.childCount - 1)
        pages.displayedChild = firstRunHintsPage
        binding.firstRunHints.firstRunHintsProgress.text =
                "${firstRunHintsPage + 1} / ${pages.childCount}"
        binding.firstRunHints.firstRunHintsBack.visibility =
                if (firstRunHintsPage == 0) View.INVISIBLE else View.VISIBLE
        binding.firstRunHints.firstRunHintsNext.setText(
                if (firstRunHintsPage == pages.childCount - 1) R.string.hint_dismiss
                else R.string.hint_next
        )
    }

    /** Closes the lesson. Only reaching the explicit final action persists completion. */
    private fun closeFirstRunHints(completed: Boolean) {
        if (!firstRunHintsPending) {
            return
        }
        firstRunHintsPending = false
        firstRunHintsBackCallback.isEnabled = false
        if (completed) {
            localUiPrefs().edit()
                    .putInt(KEY_GESTURE_GUIDE_VERSION, GESTURE_GUIDE_VERSION)
                    .apply()
        }

        val hints = binding.firstRunHints.root
        hints.animate().cancel()
        hints.animate()
                .alpha(0f)
                .setStartDelay(0)
                .setDuration(200)
                .withEndAction {
                    // A singleTask relaunch can reopen the lesson while this close animation is
                    // finishing; never let the stale callback hide the newly opened lesson.
                    if (!firstRunHintsPending) hints.visibility = View.GONE
                }
                .start()
    }

    private fun localUiPrefs(): SharedPreferences =
            getSharedPreferences(PREFS_LOCAL_UI, Context.MODE_PRIVATE)

    /** Resolves [MiscPreferences.WEAR_AOD_STYLE]'s "follow" against the selected face; every
     *  other value picks its AOD presentation directly. */
    private fun effectiveAodStyle(): String = when (aodStyle) {
        // Compose faces each bring their own AOD variant; the classic face keeps classic AOD.
        "follow" -> if (screenFace in composeFaces) screenFace else "classic"
        // "minimal" was removed (it read almost identically to classic); a stored value falls back
        // to classic so existing installs keep a working AOD.
        "minimal" -> "classic"
        else -> aodStyle
    }

    /** Resolves [MiscPreferences.WEAR_AOD_COLOR_MODE] into the color the expressive AOD strokes
     *  its outlines/glyphs with. Album/custom colors get their lightness floored so a dark
     *  accent doesn't vanish into the pure-black AOD background. */
    private fun resolvedAodTint(): Int = when (aodColorMode) {
        "album" -> liftForAodLegibility(currentAccentColor.takeIf { it != 0 } ?: defaultSeekBarColor)
        "custom" -> parseHexColorOrNull(aodCustomColor)?.let(::liftForAodLegibility) ?: Color.WHITE
        else -> Color.WHITE
    }

    private fun liftForAodLegibility(color: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[2] = hsl[2].coerceAtLeast(0.60f)
        return ColorUtils.HSLToColor(hsl)
    }

    private fun aodTintWithAlpha(multiplier: Float): Int = ColorUtils.setAlphaComponent(
            resolvedAodTint(),
            (255f * multiplier.coerceIn(0f, 1f)).roundToInt()
    )

    /**
     * The single ARGB colour (opacity baked in) for the awake clock, resolved from the
     * WEAR_CLOCK_* prefs. Consumed by the Classic View clock and, via the face state, by the
     * Compose [FaceClock] - so both always agree.
     *
     * "dynamic" samples only the small artwork region *under* the clock (top-centre), never the
     * whole cover, and picks white on a dark region or black on a light one. It is a best-effort
     * proxy: it reads the raw album bitmap, not the on-screen (blurred/shaded) background, so a
     * heavily dimmed light cover can still read as "light". The fixed colour modes are there for
     * anyone who wants a guaranteed result.
     */
    private fun resolveClockColor(): Int {
        val base = when (clockColorMode) {
            // The album colour is the only one derived rather than chosen, so it is the only one
            // the contrast correction is allowed to move - the same rule the artist line follows.
            // "dynamic" already resolves legibility its own way (black or white by the artwork
            // beneath), and correcting "custom"/"white" would override an explicit decision.
            "album" -> adaptedClockAlbumColor(
                    currentAccentColor.takeIf { it != 0 } ?: defaultSeekBarColor)
            "custom" -> parseHexColorOrNull(clockCustomColor) ?: Color.WHITE
            "dynamic" -> if (clockAreaIsLight()) Color.BLACK else Color.WHITE
            else -> Color.WHITE
        }
        val alpha = (clockOpacity.coerceIn(10, 100) / 100f * 255f).roundToInt()
        return ColorUtils.setAlphaComponent(base, alpha)
    }

    /**
     * Whether the artwork region directly under the top-centre clock is light enough that black
     * text would read better than white. Samples a small horizontal strip near the top of
     * [latestAlbumArt] (roughly where the clock sits on a round screen) and thresholds its average
     * luminance. Falls back to "dark" (→ white clock) when there is no artwork.
     */
    private fun clockAreaIsLight(): Boolean =
            clockBandLuminance()?.let { it > 0.55f } ?: false

    /** The strip of artwork the top-centre clock sits on - the same band [clockAreaIsLight]
     *  thresholds, kept as a raw value so the contrast correction can use it too. */
    private fun clockBandLuminance(): Float? = sampleArtLuminance(0.35f, 0.65f, 0.02f, 0.15f)

    /** The album-derived clock colour, lifted or darkened away from the artwork under the clock
     *  when the user asked for it. Hue and saturation are untouched, so it still reads as the
     *  album's colour rather than as white - see [AdaptiveTextContrast]. */
    private fun adaptedClockAlbumColor(base: Int): Int {
        if (!clockAdaptiveContrast) return base
        val background = clockBandLuminance() ?: return base
        return AdaptiveTextContrast.adapt(base, background)
    }

    /**
     * Average luminance of the artwork inside a rectangle given as fractions of the bitmap, or null
     * when there is no artwork to measure.
     *
     * Samples a 5x3 grid rather than every pixel - plenty for an average, and cheap enough to run
     * on every art change. Extracted from [clockAreaIsLight] so the artist line can measure its own
     * band: the whole point of both is that a *local* region decides legibility, and averaging the
     * full cover would wash out exactly the contrast being tested for.
     */
    private fun sampleArtLuminance(
            leftFraction: Float,
            rightFraction: Float,
            topFraction: Float,
            bottomFraction: Float
    ): Float? {
        val art = latestAlbumArt ?: return null
        val w = art.width
        val h = art.height
        if (w <= 0 || h <= 0) return null
        val left = (w * leftFraction).toInt().coerceIn(0, w - 1)
        val right = (w * rightFraction).toInt().coerceIn(left + 1, w)
        val top = (h * topFraction).toInt().coerceIn(0, h - 1)
        val bottom = (h * bottomFraction).toInt().coerceIn(top + 1, h)
        var luminanceSum = 0.0
        var samples = 0
        val cols = 5
        val rows = 3
        for (cx in 0 until cols) {
            for (cy in 0 until rows) {
                val x = left + (right - left) * cx / (cols - 1).coerceAtLeast(1)
                val y = top + (bottom - top) * cy / (rows - 1).coerceAtLeast(1)
                luminanceSum += ColorUtils.calculateLuminance(
                        art.getPixel(x.coerceIn(0, w - 1), y.coerceIn(0, h - 1)))
                samples++
            }
        }
        return if (samples > 0) (luminanceSum / samples).toFloat() else null
    }

    /**
     * Luminance behind the artist line, for [MiscPreferences.WEAR_ARTIST_ADAPTIVE_CONTRAST].
     *
     * The band is wide (10%-90%) and sits low (68%-84%), which is where every face puts the artist
     * relative to a full-bleed cover. It is an approximation - each face places the line slightly
     * differently and the shading/background styles alter what is actually behind it - but it is a
     * far better predictor than the cover's overall accent, which is what the line used before.
     */
    private fun artistBandLuminance(): Float? =
            sampleArtLuminance(0.10f, 0.90f, 0.68f, 0.84f)

    /** The band the title sits on - directly above the artist's, since the title is the taller
     *  line of the pair on every face. */
    private fun titleBandLuminance(): Float? = sampleArtLuminance(0.10f, 0.90f, 0.54f, 0.70f)

    /** Applies the resolved awake-clock colour + font to the Classic View clock and pushes the
     *  colour into the face state for the Compose clock. Called on preference and artwork changes. */
    private fun applyClockAppearance() {
        val color = resolveClockColor()
        binding.ambientClock.setTextColor(color)
        val clockKey = WatchTypography.clockFontKey(wearClockFontKey, wearFontKey)
        binding.ambientClock.typeface = if (WatchTypography.isFlexFont(clockKey)) {
            // Flex carries wght/slnt as real axes, so it gets an instance rather than a synthesized
            // bold - the same split applyClassicFont makes for the title and artist.
            flexTypeface(this, clockTypography, flexAxes)
        } else {
            styledClockTypeface(watchFontTypeface(this, clockKey), clockTypography)
        }
        binding.ambientClock.setTextSize(
                TypedValue.COMPLEX_UNIT_SP, clockTypography.scaled(CLASSIC_CLOCK_SP))
        binding.ambientClock.letterSpacing = clockTypography.trackingEm
        if (faceState.value.clockColor != color) {
            updateFaceState { it.copy(clockColor = color) }
        }
    }

    /** Classic uses Android Views while every other face paints AOD in Compose. Keep the View
     * metadata on the same tint/intensity contract: the parent block supplies global intensity,
     * while secondary metadata retains a quieter relative alpha. The clock sits outside that
     * block, so it receives the intensity directly. */
    private fun applyAmbientViewColors() {
        binding.textTitle.setTextColor(resolvedAodTint())
        binding.textArtist.setTextColor(aodTintWithAlpha(.55f))
        binding.ambientClock.setTextColor(aodTintWithAlpha(.60f))
    }

    /**
     * The style- and toggle-dependent part of the always-on display, split out of
     * [ambientCallback]'s onEnterAmbient so a config edit arriving mid-ambient (via
     * ConfigListenerService) restyles the already-showing AOD immediately. Every variant stays
     * burn-in-audited and battery-lean: outlined/dim rendering only, no animations, and
     * onUpdateAmbient's pixel jiggle moves all of it.
     *
     *  - "classic": the original AOD - dimmed blurred art behind the outlined classic text block.
     *  - "expressive": [ExpressiveFace] with [NowPlayingFaceState.ambient] set - the same layout
     *    reduced to hairline outlines, so the face no longer snaps to the classic look in AOD.
     *  - "chrono": a large centred clock on pure black with just the track title beneath - the
     *    clock-forward AOD that replaced the near-classic "minimal" style.
     */
    private fun applyAmbientPresentation() {
        val style = effectiveAodStyle()

        // Interactive themes never leak into AOD. Ambient has its own intensity, color and
        // element toggles, and keeping those independent also preserves its burn-in budget.
        binding.classicTextBlock.scaleX = 1f
        binding.classicTextBlock.scaleY = 1f
        binding.textTitle.alpha = 1f
        binding.textArtist.alpha = 1f
        binding.textPlaybackTime.alpha = 1f
        binding.ambientClock.alpha = aodIntensity
        // Chrono draws its own large clock in Compose, so the small View clock is hidden for it.
        binding.ambientClock.visibility =
                if (aodShowClock && style != "chrono") View.VISIBLE else View.GONE

        applyAmbientAlbumArt()
        // Ambient has its own artwork contract and Compose treatment; never leak the interactive
        // Classic background layer into AOD.
        binding.playerBackground.visibility = View.GONE

        // Chrono is Compose-rendered like the face AODs, just not one of the interactive faces.
        val composeAod = style in composeFaces || style == "chrono"
        if (composeAod) {
            composeFaceKind.value = style
            updateFaceState { it.copy(clockText = binding.ambientClock.text.toString()) }
        }
        updateFaceState {
            it.copy(
                    ambient = true,
                    ambientShowTrackInfo = aodShowTrackInfo,
                    ambientTint = resolvedAodTint(),
                    ambientIntensity = aodIntensity,
                    ambientShowTransport = aodShowTransport,
                    ambientShowProgress = aodShowProgress,
                    ambientShowPills = aodShowPills
            )
        }
        // Drops the filled source-icon glyph from the Classic artist line for ambient.
        applyClassicSourceIcon()
        binding.expressiveFace.visibility = if (composeAod) View.VISIBLE else View.GONE
        binding.classicTextBlock.visibility =
                if (!composeAod && aodShowTrackInfo) View.VISIBLE else View.GONE
        binding.classicTextBlock.alpha = aodIntensity
        if (!composeAod) {
            binding.textArtist.visibility =
                    if (aodShowTrackInfo && showTrackArtist) View.VISIBLE else View.GONE
        }

        // The title follows the AOD color mode like the outlines do (resolvedAodTint() is plain
        // white in the default mode, and album/custom tints arrive lightness-lifted for
        // legibility on black). onExitAmbient restores the layout's white.
        applyAmbientViewColors()
        binding.textTitle.displayTextOutline = true
        binding.textPlaybackTime.displayTextOutline = true
    }

    private val ambientCallback = object : AmbientLifecycleObserver.AmbientLifecycleCallback {
                override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
                    // Own ambient flag, set before anything else runs. AmbientLifecycleObserver's
                    // isAmbient is not reliably true yet inside this callback on every watch, which
                    // let the Classic source icon survive into AOD (accent-tinted, far from the
                    // centred artist because it rides as a start compound drawable on a
                    // match_parent view) - applyClassicSourceIcon now reads this instead.
                    inAmbient = true
                    // Order matters: disable first so the refresh below is a true one-shot rather
                    // than restarting the 500ms loop we are trying to stop.
                    viewModel.setContinuousPositionTicking(false)
                    viewModel.refreshPositionOnce()
                    stemButtonsManager.onEnterAmbient()
                    overlayActive = false
                    activeOverlayKind = null

                    handler.removeMessages(MESSAGE_UPDATE_CLOCK)
                    updateClock()
                    hideNotificationIfOverdue()

                    binding.iconTop.visibility = View.GONE
                    binding.iconBottom.visibility = View.GONE
                    binding.iconLeft.visibility = View.GONE
                    binding.iconRight.visibility = View.GONE
                    binding.screenButtonsRow.visibility = View.GONE

                    applyAmbientPresentation()

                    // Every Compose AOD can show the Up Next pill, not just those two - the pill
                    // was generalised but this refresh was not, so the rest were left drawing
                    // whatever queue data happened to be lying around, or none. Now that any face
                    // can be chosen as the AOD, that gap would be visible on nine more of them.
                    if (effectiveAodStyle() in composeFaces || effectiveAodStyle() == "chrono") {
                        // Keep the cached row visible while refreshing. Clearing it here made AOD
                        // look empty until Quick Actions happened to request the queue later.
                        viewModel.customList.value?.let(::updateUpNextPreview)
                        viewModel.refreshPlaybackQueueSilently()
                    }

                    binding.albumArtScrim.visibility = View.GONE
                    binding.volumeBar.visibility = View.GONE
                    binding.seekBar.visibility = View.GONE
                    binding.overlayBackdrop.animate().cancel()
                    binding.overlayBackdrop.visibility = View.GONE
                    binding.overlayBackdrop.alpha = 0f
                    binding.textVolumePercent.visibility = View.GONE
                    binding.textSeekTime.visibility = View.GONE
                    binding.seekOverlayMeter.visibility = View.GONE
                    binding.volumeIconTop.visibility = View.GONE
                    binding.volumeIconBottom.visibility = View.GONE
                    binding.quickActionsDismissFrame.visibility = View.GONE
                    quickActionsPanelBackCallback.isEnabled = false
                    binding.loadingIndicator.visibility = View.GONE
                    // Hidden (not dismissed) in ambient: bright overlay text is a burn-in risk.
                    // onExitAmbient brings it back as long as the user never dismissed it.
                    binding.firstRunHints.root.visibility = View.GONE

                    // A continuously scrolling marquee would be both a burn-in risk and a
                    // pointless battery drain on an always-on display - freeze it in place.
                    binding.textArtist.setMarqueePaused(true)
                    binding.textTitle.setMarqueePaused(true)

                    // The system only calls onUpdateAmbient roughly once a minute, so a
                    // playback position display would just look frozen/stale here. The
                    // always-visible ambientClock above already covers "what time is it".
                    binding.textPlaybackTime.visibility = View.GONE

                    viewModel.setContinuousPositionTicking(false)

                    // AVDs don't animate in ambient; also a live equalizer would defeat the
                    // low-power mode. setIdleStateVisible restarts it on ambient exit.
                    (binding.idleStateIcon.drawable as? Animatable)?.stop()

                    // The root is already opaque black from the layout (AMOLED + it covers the
                    // branded splash windowBackground); nothing to override for ambient.

                    binding.notificationPopup.backgroundImage.visibility = View.GONE
                    binding.notificationPopup.solidBackground.background = ColorDrawable(Color.BLACK)
                    updateDeveloperOverlay()

                }

                override fun onUpdateAmbient() {
                    updateClock()
                    // Once per ambient update - the cadence the platform already wakes us at, so
                    // it costs nothing extra. Anything reading the position (ambient progress, the
                    // ambient track time, Verse's lyric line) is then correct exactly when the
                    // panel redraws, and untouched in between.
                    viewModel.refreshPositionOnce()
                    viewModel.updateTimers()
                    hideNotificationIfOverdue()

                    binding.contentFrame.translationX = Random.nextInt(-5, 6).toFloat()
                    binding.contentFrame.translationY = Random.nextInt(-5, 6).toFloat()
                    updateDeveloperOverlay()
                }

                override fun onExitAmbient() {
                    inAmbient = false
                    viewModel.setContinuousPositionTicking(true)
                    stemButtonsManager.onExitAmbient()

                    updateFaceState { it.copy(ambient = false) }
                    // Restores the source-icon glyph the ambient pass cleared.
                    applyClassicSourceIcon()
                    binding.classicTextBlock.alpha = 1f
                    binding.ambientClock.alpha = 1f
                    // Restore the user's awake-clock colour/font, not the raw layout default -
                    // the ambient pass overwrote the colour with the AOD tint.
                    applyClockAppearance()

                    if (faceBool(MiscPreferences.ALWAYS_SHOW_TIME)) {
                        binding.ambientClock.visibility = View.VISIBLE
                        handler.sendEmptyMessage(MESSAGE_UPDATE_CLOCK)
                    } else {
                        binding.ambientClock.visibility = View.GONE
                    }

                    applyScreenThemeNow()

                    binding.albumArt.alpha = 1f
                    applyMainAlbumArtDisplay(latestAlbumArt, forceBlur = blurAlbumArtBackground)
                    binding.albumArtScrim.visibility = if (dimAlbumArt) View.VISIBLE else View.INVISIBLE
                    // Seek ring visibility is face-dependent - applyScreenFaceNow() below owns it
                    // (the old unconditional VISIBLE here leaked the edge ring onto the
                    // expressive face after every ambient round-trip).
                    binding.seekBar.alpha = 1f
                    binding.volumeBar.visibility = View.GONE
                    binding.volumeBar.alpha = 1f
                    binding.overlayBackdrop.animate().cancel()
                    binding.overlayBackdrop.visibility = View.GONE
                    binding.overlayBackdrop.alpha = 0f
                    binding.textVolumePercent.visibility = View.GONE
                    binding.textSeekTime.visibility = View.GONE
                    binding.seekOverlayMeter.visibility = View.GONE

                    binding.textArtist.setMarqueePaused(false)
                    binding.textTitle.setMarqueePaused(false)

                    viewModel.setContinuousPositionTicking(true)

                    // Root deliberately keeps its opaque black layout background (never null -
                    // that would let the splash windowBackground glyph bleed through wherever
                    // no album art covers the screen).
                    setIdleStateVisible(binding.idleStateGroup.visibility == View.VISIBLE)

                    binding.notificationPopup.backgroundImage.visibility = View.VISIBLE
                    binding.notificationPopup.solidBackground.background =
                            AppCompatResources.getDrawable(
                                    this@MainActivity,
                                    R.drawable.notification_popup_background
                            )

                    if (viewModel.musicState.value == null || (viewModel.musicState.value as Resource<MusicState>).status == Resource.Status.LOADING) {
                        binding.loadingIndicator.visibility = View.VISIBLE
                    }

                    val crownDisableTime =
                            Preferences.getInt(preferences, MiscPreferences.ROTATING_CROWN_OFF_PERIOD)
                    if (crownDisableTime > 0) {
                        rotatingInputDisabledUntil = System.currentTimeMillis() + crownDisableTime
                    }

                    // White is the classic face's own designed title colour, so it is what the
                    // "face default" mode resolves to here - same contract as the Compose faces,
                    // which each keep their own literal.
                    binding.textTitle.setTextColor(resolvedTitleTextColor() ?: Color.WHITE)
                    binding.textArtist.setTextColor(
                            if (!isMusicPlaying && binding.textArtist.text?.isNotEmpty() == true) {
                                Color.WHITE
                            } else {
                                resolvedArtistTextColor()
                            }
                    )
                    binding.textTitle.displayTextOutline = false
                    binding.textPlaybackTime.displayTextOutline = false

                    // onEnterAmbient force-hid the track time; the position ticker would bring
                    // it back on its own only while playing, so restore it explicitly.
                    updatePlaybackTimeVisibility()

                    if (firstRunHintsPending) {
                        binding.firstRunHints.root.alpha = 1f
                        binding.firstRunHints.root.visibility = View.VISIBLE
                    }

                    // onUpdateAmbient() offsets contentFrame (not root) for burn-in protection -
                    // resetting the wrong view here left it permanently shifted after ambient.
                    binding.contentFrame.translationX = 0f
                    binding.contentFrame.translationY = 0f

                    // Last, so it wins over the classic-view restores above. The unguarded
                    // variant is essential: isAmbient can still be true inside this callback,
                    // and the guarded applyScreenFace() silently no-oped - leaving the edge
                    // seek ring visible on the expressive face after every wake-up.
                    applyScreenFaceNow()
                    syncScreenButtonsVisibility(forceInteractive = true)
                    updateDeveloperOverlay()
                }

            }

    override fun onGenericMotionEvent(ev: android.view.MotionEvent): Boolean {
        // Quick Actions is a real scrollable list now. Route rotary input into it before the
        // normal volume/seek mapping, including on watches with a discrete crown.
        if (isQuickActionsPanelShowing()) {
            if (ev.action == android.view.MotionEvent.ACTION_SCROLL &&
                    RotaryEncoderHelper.isFromRotaryEncoder(ev)) {
                val delta = -RotaryEncoderHelper.getRotaryAxisValue(ev) *
                        RotaryEncoderHelper.getScaledScrollFactor(this)
                binding.quickActionsPanel.smoothScrollByRotary(delta.roundToInt())
                return true
            }
            return false
        }

        if (rotatingInputDisabledUntil > System.currentTimeMillis()) {
            return false
        }

        // A touch-bezel watch (every non-Classic Galaxy Watch) reports a finger sliding around the
        // rim as rotary scroll. That is the same physical gesture as an edge-seek drag on the ring,
        // so while a drag is live the rotary mapping below must stay out of the way - otherwise
        // dragging to seek popped the volume overlay and the seek never happened.
        if (binding.seekBar.isSeekDragging) {
            return true
        }

        if (ev.action == android.view.MotionEvent.ACTION_SCROLL && RotaryEncoderHelper.isFromRotaryEncoder(
                        ev
                )
        ) {
            val delta =
                    -RotaryEncoderHelper.getRotaryAxisValue(ev) * RotaryEncoderHelper.getScaledScrollFactor(
                            this
                    )

            // Deliberately ahead of the WEAR_ROTARY_ACTION check below: on a discrete-rotary watch
            // a turn is a *configured button press*, which belongs to the button config rather
            // than to this preference. "Do nothing" turns off the volume/seek mapping this
            // preference owns - it is not a master switch over the user's own crown assignments.
            if (WatchInfoSender.hasDiscreteRotaryInput()) {
                val keyCode = if (delta > 0) {
                    SpecialButtonCodes.TURN_ROTARY_CW
                } else {
                    SpecialButtonCodes.TURN_ROTARY_CCW
                }

                return stemButtonsManager.simulateKeyPress(keyCode)
            }

            // getScaledScrollFactor() is tuned for scrolling lists by a full screen-ish amount
            // per detent, so even a tiny/accidental crown nudge produces a surprisingly large
            // delta for fine-grained volume control - a deadzone filters out that noise, and the
            // base factor is well below the old list-scrolling-derived value on top of it.
            if (abs(delta) < rotaryDeadzone) {
                return true
            }

            val multipler =
                    Preferences.getInt(preferences, MiscPreferences.ROTATING_CROWN_SENSITIVITY) / 100f

            val rotaryAction = RotaryAction.resolve(
                    Preferences.getString(preferences, MiscPreferences.WEAR_ROTARY_ACTION),
                    Preferences.getBoolean(preferences, MiscPreferences.ROTARY_SEEK))

            // Swallowed rather than passed on: on a touch-bezel watch the rim gesture would
            // otherwise reach whatever is underneath, which is the opposite of "off".
            if (rotaryAction == RotaryAction.OFF) {
                return true
            }

            // Optional: scrub the playback timeline with the crown instead of changing volume.
            if (rotaryAction == RotaryAction.SEEK && playbackSeekable) {
                // Accumulate on the pending scrub target, not on seekBar.progress: while music
                // plays, the position ticker keeps rewriting seekBar.progress with the live
                // position between detents, so using the bar as the base made each turn snap
                // back and fight the ticker (the bug only showed during playback - paused had
                // no ticks). The observer below also holds ticker writes off mid-scrub.
                val base = pendingRotarySeekFraction ?: binding.seekBar.progress
                val newFraction = (base + delta * 0.0011f * multipler).coerceIn(0f, 1f)
                binding.seekBar.progress = newFraction
                showSeekOverlay(newFraction)
                scheduleRotarySeekCommit(newFraction)
                return true
            }

            binding.volumeBar.incrementVolume(delta * 0.0011f * multipler)
            viewModel.updateVolume(binding.volumeBar.volume)
            showVolumeBar()


            return true
        }

        return super.onGenericMotionEvent(ev)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // Back button is handled through onBackPressedDispatcher
            return super.onKeyDown(keyCode, event)
        }

        if (stemButtonsManager.onKeyDown(keyCode, event)) {
            return true
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // Back button is handled through onBackPressedDispatcher. It's super.onKeyUp (not
            // onKeyDown) that fires onBackPressed for a tracked back event - calling onKeyDown
            // here instead swallowed the release, so the configured back action, the quick-panel
            // dismiss and the hints dismiss never ran on watches that deliver BACK as a KeyEvent.
            return super.onKeyUp(keyCode, event)
        }

        if (stemButtonsManager.onKeyUp(keyCode)) {
            return true
        }

        return super.onKeyUp(keyCode, event)
    }

    private fun onNotificationTapped() {
        hideNotification()
    }

    private fun hideNotificationIfOverdue() {
        if (notificationDismissDeadline < System.currentTimeMillis()) {
            hideNotification()
        }
    }

    private fun hideNotification() {
        val card = binding.notificationPopup.notificationCard
        card.animate().scaleX(0f).scaleY(0f).setDuration(200).withEndAction {
            card.visibility = View.GONE
        }.start()
        handler.removeMessages(MESSAGE_DISMISS_NOTIFICATION)
        notificationDismissDeadline = Long.MAX_VALUE
    }

    private fun showNotification(notification: Notification) {
        val timeout = Preferences.getInt(preferences, MiscPreferences.NOTIFICATION_TIMEOUT)
        val deadlineMs = timeout * 1000

        notificationDismissDeadline = notification.time + deadlineMs
        if (notificationDismissDeadline < System.currentTimeMillis()) {
            return
        }

        val card = binding.notificationPopup.notificationCard
        card.animate().scaleX(1f).scaleY(1f).setDuration(200).withStartAction {
            card.visibility = View.VISIBLE
        }.start()

        handler.removeMessages(MESSAGE_DISMISS_NOTIFICATION)
        if (timeout > 0) {
            handler.sendEmptyMessageDelayed(MESSAGE_DISMISS_NOTIFICATION, deadlineMs.toLong())
        }
    }

    /**
     * Fades in the full-screen acrylic scrim that backs both the volume and seek overlays - it
     * sits below them in the layout but above everything else, so showing it alone is enough to
     * visually hide the rest of the screen without touching every other view's visibility.
     */
    private enum class OverlayKind { VOLUME, SEEK, QUICK_ACTIONS }
    private var activeOverlayKind: OverlayKind? = null
    private var activeOverlayUsesBlur = false

    /** Applies the independently selected full-screen background. The content style is consulted
     * only when the compatibility "follow" option is selected. */
    private fun applyOverlayBackdrop(kind: OverlayKind): Boolean {
        val contentStyle = when (kind) {
            OverlayKind.VOLUME -> binding.volumeBar.barStyle.name.lowercase()
            OverlayKind.QUICK_ACTIONS -> quickPanelStyle
            OverlayKind.SEEK -> OverlayBackdropResolver.seekContentStyle(seekOverlayStyle)
        }
        val backdrop = OverlayBackdropResolver.resolve(overlayBackdropStyle, contentStyle)
        val density = resources.displayMetrics.density
        val (accent, secondary, tertiary) = when (kind) {
            OverlayKind.VOLUME -> Triple(
                    resolvedVolumeAccent(),
                    resolvedVolumeSecondaryAccent(),
                    resolvedVolumeTertiaryAccent())
            OverlayKind.SEEK -> Triple(
                    resolvedProgressAccent(),
                    resolvedProgressSecondaryAccent(),
                    resolvedProgressTertiaryAccent())
            OverlayKind.QUICK_ACTIONS -> Triple(
                    resolvedQuickPanelAccent(),
                    resolvedQuickPanelSecondaryAccent(),
                    resolvedQuickPanelTertiaryAccent())
        }
        fun withAlpha(color: Int, alpha: Int) = ColorUtils.setAlphaComponent(color, alpha)
        binding.overlayDim.background = when (backdrop) {
            // The album-tinted backdrops use the faces' wider saturation band (.30-.90) instead of
            // the chrome default (.25-.60) so an overlay opened over a face shares its palette
            // instead of showing a desaturated, mismatched tint.
            // The bleed-through the user saw was mostly the always-on clock and mini-button row
            // drawing OVER this backdrop (fixed separately in showOverlay by hiding them), not the
            // backdrop's own opacity - a first attempt raised these to near-opaque and over-shot
            // ("quase totalmente opaco"). Settled at a middle ground: clearly reads as a covering
            // surface without losing the frosted/acrylic look these styles are meant to have.
            OverlayBackdrop.ACRYLIC -> GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(
                            withAlpha(PaletteTransforms.tonalSurface(accent, .22f, PaletteTransforms.FACE_MIN_SAT, PaletteTransforms.FACE_MAX_SAT), 0xAE),
                            withAlpha(Color.BLACK, 0xD2)
                    )
            )
            OverlayBackdrop.SOLID_BLACK -> ColorDrawable(Color.BLACK)
            OverlayBackdrop.SOLID_ALBUM -> ColorDrawable(PaletteTransforms.tonalSurface(accent, .22f, PaletteTransforms.FACE_MIN_SAT, PaletteTransforms.FACE_MAX_SAT))
            OverlayBackdrop.GLASS -> GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(0x2EFFFFFF, 0xB8000000.toInt())
            )
            OverlayBackdrop.GRADIENT -> GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(tonalSurface(accent, .42f), tonalSurface(secondary, .18f))
            )
            OverlayBackdrop.DUOTONE -> GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(tonalSurface(accent, .30f), tonalSurface(secondary, .30f))
            )
            OverlayBackdrop.PRISM -> GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(
                            withAlpha(tonalSurface(tertiary, .25f), 0xE8),
                            withAlpha(tonalSurface(accent, .42f), 0xD8),
                            withAlpha(tonalSurface(secondary, .22f), 0xEA)
                    )
            ).apply {
                setStroke((1f * density).roundToInt().coerceAtLeast(1), 0x66FFFFFF)
            }
            // Deliberately the lightest wash of the set: the blurred cover underneath is the
            // material, and covering it would defeat the effect. The album tint is kept low-alpha
            // and lifted (.62 lightness) so it reads as coloured glass rather than as a filter,
            // and the near-white stroke is the pane's edge - the single cue that turns a
            // translucent wash into a surface with thickness.
            OverlayBackdrop.LIQUID_GLASS -> GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(
                            withAlpha(PaletteTransforms.tonalSurface(accent, .62f, PaletteTransforms.FACE_MIN_SAT, PaletteTransforms.FACE_MAX_SAT), 0x3D),
                            withAlpha(Color.WHITE, 0x14),
                            withAlpha(PaletteTransforms.tonalSurface(secondary, .30f, PaletteTransforms.FACE_MIN_SAT, PaletteTransforms.FACE_MAX_SAT), 0x66)
                    )
            ).apply {
                setStroke((1.5f * density).roundToInt().coerceAtLeast(1), 0x8CFFFFFF.toInt())
            }
            // The authored Expressive background, rebuilt as an overlay: album wash, black
            // knock-back, then the vignette that makes the rim recede. Three stacked layers rather
            // than one gradient because that is what the face's own PlayerBackgroundDrawable draws,
            // and an overlay that only approximated it would read as a different treatment sharing
            // a name. The blurred/sharp pair differ *only* in what shows through underneath
            // (usesAlbumBlur), exactly as the album-art styles of the same name do.
            OverlayBackdrop.EXPRESSIVE, OverlayBackdrop.EXPRESSIVE_NO_BLUR -> LayerDrawable(
                    arrayOf(
                            ColorDrawable(withAlpha(
                                    PaletteTransforms.tonalSurface(accent, .30f, .30f, .90f), 0x73)),
                            ColorDrawable(withAlpha(Color.BLACK, 0x4D)),
                            GradientDrawable().apply {
                                gradientType = GradientDrawable.RADIAL_GRADIENT
                                colors = intArrayOf(
                                        Color.TRANSPARENT,
                                        Color.TRANSPARENT,
                                        withAlpha(Color.BLACK, 0xE0))
                                setGradientCenter(.5f, .5f)
                                // Full-screen overlay, so the face's own maxDimension * .68f is
                                // just the screen width; px because GradientDrawable's programmatic
                                // radius has no fraction form (that exists only in XML).
                                gradientRadius =
                                        resources.displayMetrics.widthPixels * .68f
                            }))
            OverlayBackdrop.FOLLOW_STYLE -> ColorDrawable(Color.BLACK) // resolved above
        }
        return backdrop.usesAlbumBlur
    }

    private fun showOverlay(kind: OverlayKind) {
        val continuingSameOverlay = overlayActive && activeOverlayKind == kind &&
                binding.overlayBackdrop.visibility == View.VISIBLE
        val backdropAlreadyEnteringOrVisible = overlayActive &&
                binding.overlayBackdrop.visibility == View.VISIBLE
        overlayActive = true
        activeOverlayKind = kind
        updateDeveloperOverlay()
        updateEdgeSeekTouchState()
        // Persistent chrome that otherwise shows through a translucent backdrop near the edges:
        // the always-on clock (top) and the mini-button row (bottom). Hidden for the overlay's
        // duration so the backdrop reads as a full-screen surface; hideOverlay restores them.
        binding.ambientClock.visibility = View.GONE
        binding.screenButtonsRow.visibility = View.GONE
        val useBlur = if (continuingSameOverlay) {
            activeOverlayUsesBlur
        } else {
            applyOverlayBackdrop(kind).also { activeOverlayUsesBlur = it }
        }
        binding.overlayBlurImage.alpha = 1f
        binding.overlayBlurImage.visibility = if (useBlur) View.VISIBLE else View.GONE
        binding.overlayDim.alpha = 1f
        binding.overlayDim.visibility = View.VISIBLE

        // Blur and gradient are children of the same hardware-composited group. Fading the parent
        // guarantees that neither can become visible one frame before the other, which previously
        // looked like two different blur/gradient effects being applied in sequence.
        if (!backdropAlreadyEnteringOrVisible) {
            val wasStillVisible = binding.overlayBackdrop.visibility == View.VISIBLE
            binding.overlayBackdrop.animate().cancel()
            if (!wasStillVisible) binding.overlayBackdrop.alpha = 0f
            binding.overlayBackdrop.visibility = View.VISIBLE
            binding.overlayBackdrop.animate()
                    .alpha(1f)
                    .setDuration(OVERLAY_FADE_IN_MS)
                    .start()

            // A Compose face (ComposeView) can draw over its later sibling Views, so once an
            // overlay owns the screen the face must end hidden or its clock/controls paint over
            // the backdrop. It used to be hidden with an instant GONE, which uncovered the sharp
            // classic album art beneath it for the backdrop's fade-in - read as the cover
            // flashing before the blur appeared. Crossfade it out over the same duration instead:
            // the opaque face keeps occluding the album art until the backdrop has faded fully in.
            if (screenFace in composeFaces) {
                binding.expressiveFace.animate().cancel()
                binding.expressiveFace.animate()
                        .alpha(0f)
                        .setDuration(OVERLAY_FADE_IN_MS)
                        .withEndAction {
                            if (overlayActive) binding.expressiveFace.visibility = View.GONE
                            binding.expressiveFace.alpha = 1f
                        }
                        .start()
            }
        }
    }

    private fun hideOverlay() {
        overlayActive = false
        activeOverlayKind = null
        updateDeveloperOverlay()
        // Bring the Compose face and the persistent chrome back (hidden in showOverlay). Ambient
        // handles its own visibility, so only restore when interactive.
        if (!ambientObserver.isAmbient) {
            if (screenFace in composeFaces) {
                // Cancel a still-running show crossfade so its end action can't hide the face
                // again after we bring it back, and reset the alpha it was mid-fading.
                binding.expressiveFace.animate().cancel()
                binding.expressiveFace.alpha = 1f
                binding.expressiveFace.visibility = View.VISIBLE
                // The Compose face owns its own clock; restoring the classic View clock here would
                // stack a second clock on top of it once the overlay finishes closing.
                binding.ambientClock.visibility = View.GONE
            } else {
                binding.ambientClock.visibility =
                        if (faceBool(MiscPreferences.ALWAYS_SHOW_TIME)) View.VISIBLE else View.GONE
            }
            syncScreenButtonsVisibility()
        }
        binding.overlayBackdrop.animate().cancel()
        binding.overlayBackdrop.animate()
                .alpha(0f)
                .setDuration(OVERLAY_FADE_OUT_MS)
                .withEndAction {
                    if (!overlayActive) {
                        binding.overlayBackdrop.visibility = View.GONE
                        updateEdgeSeekTouchState()
                    }
                }
                .start()

        binding.volumeBar.animate().cancel()
        binding.volumeBar.animate()
                .alpha(0f)
                .setDuration(OVERLAY_FADE_OUT_MS)
                .withEndAction {
                    if (!overlayActive) binding.volumeBar.visibility = View.GONE
                }
                .start()

        binding.seekBar.animate().cancel()
        binding.seekBar.animate().alpha(1f).setDuration(OVERLAY_FADE_OUT_MS).start()

        binding.textVolumePercent.visibility = View.GONE
        binding.textSeekTime.visibility = View.GONE
        binding.seekOverlayMeter.visibility = View.GONE
        binding.volumeIconTop.visibility = View.GONE
        binding.volumeIconBottom.visibility = View.GONE
        binding.quickActionsDismissFrame.visibility = View.GONE
        binding.quickActionsDismissFrame.translationX = 0f
        binding.quickActionsDismissFrame.alpha = 1f
        quickActionsPanelBackCallback.isEnabled = false

        handler.removeMessages(MESSAGE_HIDE_VOLUME)
    }

    /** Opened by double-tapping center_tap_zone - like/shuffle/repeat shortcuts plus a way into
     *  the queue, on top of the same blur/dim scrim the volume and seek previews use. Stays open
     *  until the user taps outside it, taps Up Next, or presses back - it does not auto-hide. */
    private fun showQuickActionsPanel() {
        showOverlay(OverlayKind.QUICK_ACTIONS)

        binding.seekBar.animate().cancel()
        binding.seekBar.animate().alpha(0f).setDuration(OVERLAY_FADE_IN_MS).start()

        binding.textVolumePercent.visibility = View.GONE
        binding.textSeekTime.visibility = View.GONE
        binding.seekOverlayMeter.visibility = View.GONE
        binding.volumeBar.visibility = View.GONE
        binding.quickActionsDismissFrame.translationX = 0f
        binding.quickActionsDismissFrame.alpha = 1f
        binding.quickActionsDismissFrame.visibility = View.VISIBLE
        quickActionsPanelBackCallback.isEnabled = true

        // Manual mode uses the configured slots; session mode mirrors controls supplied by the
        // media app itself. Hidden elements collapse and the rest re-center on their own.
        configureQuickPanelButtons()
        binding.quickActionUpNext.background = upNextPillBackground()
        // The Up Next row is the one panel element with a coloured surface behind its own text, so
        // its label/subtitle/icon follow its pill tint (dark on light, white on black/accent, or
        // the quick-panel tint when the pill style is "follow").
        val upNextTint = upNextPillTint()
        binding.quickActionUpNextLabel.setTextColor(upNextTint)
        binding.quickActionUpNextTrack.setTextColor(ColorUtils.setAlphaComponent(upNextTint, 0xB3))
        // A real notification/custom icon must keep its own colours - see setQuickActionButtonActive.
        if (quickActionUpNextUsesRealIcon) {
            binding.quickActionUpNextIcon.clearColorFilter()
        } else {
            binding.quickActionUpNextIcon.setColorFilter(upNextTint)
        }

        binding.quickActionPanelTitle.text = binding.textTitle.text
        binding.quickActionPanelArtist.text = binding.textArtist.text
        // Unlike the Up Next row and the round buttons, the title/artist sit directly on the
        // overlay BACKDROP (dark blur/tonal/black), not on a per-style capsule - so their colour
        // must contrast with the backdrop. quickPanelInactiveTint() is the capsule chrome colour,
        // which on the "tonal" style is dark ink (for the light capsules) and was unreadable
        // against the dark backdrop behind the text.
        val panelMetadataTint = contrastingIconColor(quickPanelBackdropColor())
        binding.quickActionPanelTitle.setTextColor(panelMetadataTint)
        binding.quickActionPanelTitle.visibility =
                if (faceState.value.showTitle) View.VISIBLE else View.GONE
        binding.quickActionPanelArtist.setTextColor(
                ColorUtils.setAlphaComponent(panelMetadataTint, 0xB3))
        binding.quickActionPanelArtist.visibility =
                if (faceState.value.showArtist &&
                        !binding.quickActionPanelArtist.text.isNullOrEmpty()) View.VISIBLE else View.GONE

        renderQuickPanelExtraActions()
        applyQuickPanelLayout()
        binding.quickActionsPanel.scrollTo(0, 0)

        updateQuickActionButtonStates()

        // Show whatever was cached from a previous fetch immediately, then ask the phone for a
        // fresh queue snapshot in the background - customListListener() will update the preview
        // text in place without yanking the user into the full drawer while this panel is open.
        if (quickPanelLongMode == QuickLongMode.UP_NEXT) {
            viewModel.customList.value?.let { updateUpNextPreview(it) }
            viewModel.openPlaybackQueue()
        }
    }

    private fun isQuickActionsPanelShowing() =
            binding.quickActionsDismissFrame.visibility == View.VISIBLE

    /** Mirrors the user-configured Actions menu below the primary media controls. This turns the
     * panel into the scrollable launcher requested by the design without inventing a second set
     * of configuration slots: playlist shortcuts, search, library actions, and future actions
     * appear here automatically in the same order the user already chose. */
    private fun renderQuickPanelExtraActions() {
        val container = binding.quickActionExtraList
        container.removeAllViews()
        if (quickPanelExtraActions.isEmpty()) {
            container.visibility = View.GONE
            return
        }

        val tint = quickPanelInactiveTint()
        quickPanelExtraActions.forEachIndexed { sourceIndex, action ->
            val row = layoutInflater.inflate(
                    R.layout.item_quick_action_row,
                    container,
                    false
            )
            row.layoutParams = row.layoutParams?.apply { height = listRowHeightPx() }
            val icon = row.findViewById<ImageView>(R.id.quick_extra_icon)
            val title = row.findViewById<TextView>(R.id.quick_extra_title)
            // Inflated rows are created here rather than existing in the layout, so applyQuickPanelFont
            // (which walks fixed binding views) never reached them - the panel's own labels followed
            // the chosen font while its shortcut rows stayed on Google Sans.
            title.typeface = quickPanelTypeface()
            if (action.icon != null) {
                icon.setImageDrawable(action.icon)
            } else {
                icon.setImageResource(com.svartifoss.snfell.common.R.drawable.action_custom)
            }
            // Genuine cover art (e.g. an online shortcut thumbnail) fills a circle and crops
            // rectangles; everything else stays centered at icon size.
            //
            // Reads isCoverArt, not !iconTintable, which is what it used to do and got wrong the
            // same way MenuScreen.leadsWithArtwork did: an app-launcher icon is full-colour and so
            // untintable too, so an "open app" row grew its icon to album-cover size and had it
            // cropped into a circle - a brand mark rendered larger than the actual album art
            // beside it, with the corners of its squircle sliced off. Note the coverBitmap check
            // below already used isCoverArt, so the pill background was right while the leading
            // icon was not.
            val isArtwork = action.icon != null && action.isCoverArt
            // Full-colour but not artwork: an app icon. Kept smaller than a cover and smaller than
            // the layout's glyph size, the way a launcher list draws them - the row is read by its
            // label, and the icon is there to confirm the choice rather than to be it.
            val isAppIcon = action.icon != null && !action.isCoverArt && !action.iconTintable
            // Cover style + the opt-in shortcut-cover toggle: only a row backed by genuine cover
            // art (action.isCoverArt - never a plain app-launcher icon) fills the whole pill with
            // it, the same treatment the Up Next row gets for the actual track artwork.
            val coverBitmap = if (action.isCoverArt && coverPillsActive && quickPanelShortcutCoverEnabled) {
                (action.icon as? BitmapDrawable)?.bitmap
            } else {
                null
            }
            if (coverBitmap != null) {
                icon.visibility = View.GONE
            } else if (isArtwork) {
                icon.visibility = View.VISIBLE
                icon.clearColorFilter()
                icon.scaleType = ImageView.ScaleType.CENTER_CROP
                icon.outlineProvider = circularOutlineProvider
                icon.clipToOutline = true
                applyListRowArtworkSize(row, icon)
            } else {
                icon.visibility = View.VISIBLE
                if (action.iconTintable) icon.setColorFilter(tint) else icon.clearColorFilter()
                icon.scaleType = ImageView.ScaleType.FIT_CENTER
                icon.clipToOutline = false
                if (isAppIcon) {
                    // No circular clip either: an app icon carries its own shape and its own
                    // background, so cropping it to a circle cuts the corners off a squircle
                    // rather than tidying it up.
                    val sizePx = (APP_ICON_DP * resources.displayMetrics.density).roundToInt()
                    icon.layoutParams = icon.layoutParams?.apply {
                        width = sizePx
                        height = sizePx
                    }
                }
            }
            title.text = action.title
                    ?: StandardActionTitles.get(this, action.key)
                    ?: getString(R.string.action_name_custom)
            // White over the scrim rather than the panel tint, which is picked to read against
            // the flat pill and can vanish against artwork.
            title.setTextColor(if (coverBitmap != null) Color.WHITE else tint)
            if (coverBitmap != null) {
                applyCoverPill(row, coverBitmap)
            } else {
                row.background = quickPanelRowBackground()
            }
            row.contentDescription = title.text
            row.setOnClickListener {
                buzz()
                hideOverlay()
                viewModel.executeActionFromMenu(sourceIndex)
            }
            container.addView(row)
        }
        container.visibility = View.VISIBLE
    }

    /** Changes the panel's actual information hierarchy independently from its surface paint.
     * Hidden app actions collapse before sizing, so two Spotify actions remain full-size and are
     * centered while three-action players occupy a balanced row. */
    private fun applyQuickPanelLayout() {
        val panelRoot = binding.quickActionsPanel
        val panel = binding.quickActionsContent
        val title = binding.quickActionPanelTitle
        val artist = binding.quickActionPanelArtist
        val actions = binding.quickActionRoundRow
        val upNext = binding.quickActionUpNext
        val extras = binding.quickActionExtraList
        val density = resources.displayMetrics.density

        val primaryList = binding.quickActionPrimaryList

        // "rows" is the only layout that does not use the round slots at all: it mirrors them as
        // labelled full-width rows instead. Everything else keeps the round row and varies its
        // arrangement.
        val asRows = quickPanelLayout == "rows"
        actions.visibility = if (asRows) View.GONE else View.VISIBLE
        primaryList.visibility = if (asRows) View.VISIBLE else View.GONE
        if (asRows) renderQuickPanelPrimaryRows() else primaryList.removeAllViews()

        actions.arrangement = when (quickPanelLayout) {
            "arc" -> QuickActionsRowLayout.Arrangement.ARC
            "grid" -> QuickActionsRowLayout.Arrangement.GRID
            else -> QuickActionsRowLayout.Arrangement.ROW
        }

        // The grid stands on its own as a dense launcher, so metadata would only cost it the room
        // it needs; every other layout keeps both lines.
        val metadataVisible = quickPanelLayout != "grid"
        title.visibility = if (metadataVisible && title.text.isNotBlank()) View.VISIBLE else View.GONE
        // Hero deliberately drops to a single metadata line - its point is one dominant action.
        artist.visibility = if (metadataVisible && quickPanelLayout != "hero" &&
                artist.text.isNotBlank()) View.VISIBLE else View.GONE

        // Every layout now keeps the natural reading order and expresses itself through the
        // arrangement of the slots instead. The old "actions first" / "compact deck" options were
        // only reorderings of this same stack, which is why they never looked meaningfully
        // different from it.
        val desiredOrder = listOf(title, artist, actions, primaryList, upNext)
        val params = desiredOrder.associateWith { it.layoutParams }
        desiredOrder.forEach(panel::removeView)
        panel.removeView(extras)
        desiredOrder.forEach { panel.addView(it, params.getValue(it)) }
        panel.addView(extras)

        val panelParams = panelRoot.layoutParams as FrameLayout.LayoutParams
        panelParams.marginStart = 0
        panelParams.marginEnd = 0
        panelRoot.layoutParams = panelParams
        // Keep the cards at the original safe inset while letting the ScrollView itself reach the
        // physical viewport edge. Its curved indicator can now hug the same bezel as QueueScreen.
        val sideInset = (8f * density).roundToInt()
        panelRoot.setPadding(sideInset, panelRoot.paddingTop, sideInset, panelRoot.paddingBottom)
        val viewportHeight = panelRoot.height.takeIf { it > 0 }
                ?: resources.displayMetrics.heightPixels
        panel.setPadding(
                panel.paddingLeft,
                (viewportHeight * .17f).roundToInt(),
                panel.paddingRight,
                panel.paddingBottom
        )

        // No layout shrinks text or hides metadata to make room any more - the old compact
        // variant's tiny bubbles were the core legibility regression. Hero is the one exception,
        // and it drops a whole line rather than shrinking one.
        title.textSize = if (quickPanelLayout == "hero") 16f else 18f
        artist.textSize = 13f

        val titleParams = title.layoutParams as LinearLayout.LayoutParams
        titleParams.bottomMargin = (5f * density).roundToInt()
        title.layoutParams = titleParams
        val artistParams = artist.layoutParams as LinearLayout.LayoutParams
        artistParams.bottomMargin = (14f * density).roundToInt()
        artist.layoutParams = artistParams
        val actionParams = actions.layoutParams as LinearLayout.LayoutParams
        // The arc's dipped centre slot already reserves its depth inside the row's measured
        // height, so only the grid needs to be held off the Up Next row below it.
        actionParams.bottomMargin = if (quickPanelLayout == "grid") {
            (10f * density).roundToInt()
        } else {
            0
        }
        actions.layoutParams = actionParams
        val upNextParams = upNext.layoutParams as LinearLayout.LayoutParams
        upNextParams.topMargin = (6f * density).roundToInt()
        upNextParams.width = ViewGroup.LayoutParams.MATCH_PARENT
        upNextParams.gravity = Gravity.CENTER_HORIZONTAL
        upNext.layoutParams = upNextParams
        upNext.minimumHeight = (58f * density).roundToInt()
        // The text padding squeezes a cover out of round, and this runs every time the panel is
        // rebuilt - after the artwork update as often as before it. Hand the row back to the
        // artwork ruler whenever there is artwork, so whichever of the two ran last, the cover
        // still ends up circular.
        if (binding.quickActionUpNextArtwork.visibility == View.VISIBLE) {
            applyListRowArtworkSize(upNext, binding.quickActionUpNextArtwork)
        } else {
            applyUpNextTextPadding()
        }

        val visible = quickPanelViews().filter { it.visibility == View.VISIBLE }
        if (visible.isNotEmpty()) {
            // The grid puts at most two slots on a line, so they are fitted for two rather than for
            // the whole row - which is what lets them come out noticeably larger than in a row.
            val fitCount = if (quickPanelLayout == "grid") {
                minOf(visible.size, 2)
            } else {
                visible.size
            }
            val (width, height, gap) = fittedRoundQuickSizes(fitCount)
            visible.forEachIndexed { index, button ->
                sizeRoundQuickButton(button, width, height, gap, first = index == 0)
            }
            if (quickPanelLayout == "hero") applyHeroSlotEmphasis(visible, height)
        }
    }

    /**
     * Hero layout: the first visible slot becomes the panel's single focus and the rest step back.
     * Sizes are derived from the fitted row height so the emphasised slot still fits the viewport
     * on small watches, and every slot stays at or above the 48dp Wear OS touch target.
     */
    private fun applyHeroSlotEmphasis(visible: List<ImageView>, fittedHeight: Int) {
        val density = resources.displayMetrics.density
        val minimumTarget = (48 * density).roundToInt()
        val available = (binding.contentFrame.width.takeIf { it > 0 }
                ?: resources.displayMetrics.widthPixels) - (16 * density).toInt()
        val gap = (8 * density).roundToInt()

        val heroSize = (fittedHeight * 1.5f).roundToInt()
        val secondaryCount = visible.size - 1
        // Whatever the hero does not take is shared by the remaining slots.
        val secondaryWidth = if (secondaryCount > 0) {
            ((available - heroSize - gap * secondaryCount) / secondaryCount)
                    .coerceIn(minimumTarget, fittedHeight)
        } else {
            fittedHeight
        }

        visible.forEachIndexed { index, button ->
            if (index == 0) {
                sizeRoundQuickButton(button, heroSize, heroSize, gap, first = true)
            } else {
                sizeRoundQuickButton(button, secondaryWidth, secondaryWidth, gap, first = false)
            }
        }
    }

    /**
     * Rebuilds the round slots as labelled full-width rows for the "rows" layout.
     *
     * It reads the already-resolved round buttons rather than re-deriving the slot config: their
     * drawable is the icon the user configured and their content description is the label already
     * computed for accessibility, and the click is forwarded to the button itself. So slot
     * semantics (defaults, NullAction hiding, session mode) stay defined in exactly one place.
     */
    private fun renderQuickPanelPrimaryRows() {
        val container = binding.quickActionPrimaryList
        container.removeAllViews()
        val tint = quickPanelInactiveTint()

        quickPanelViews().filter { it.visibility == View.VISIBLE }.forEach { button ->
            val row = layoutInflater.inflate(R.layout.item_quick_action_row, container, false)
            row.layoutParams = row.layoutParams?.apply { height = listRowHeightPx() }
            val icon = row.findViewById<ImageView>(R.id.quick_extra_icon)
            val title = row.findViewById<TextView>(R.id.quick_extra_title)

            icon.setImageDrawable(button.drawable)
            icon.setColorFilter(tint)
            title.text = button.contentDescription ?: getString(R.string.action_name_custom)
            title.setTextColor(tint)
            row.background = quickPanelRowBackground()
            row.setOnClickListener { button.performClick() }
            container.addView(row)
        }
    }

    // Same stadium/capsule shape as glass_pill_background.xml (the inactive state) - this used
    // to be a plain oval, which made the active button look like a different shape from the
    // other two instead of just a different color.
    private fun accentCircleDrawable(): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 999f
        setColor(resolvedQuickPanelAccent())
    }

    /** Material Design 2 surface grey shared by the material quick-panel chrome. */
    private val materialSurfaceColor = 0xFF2A2A2A.toInt()
    private val LIGHT_PANEL_SURFACE = 0xFFECECEC.toInt()
    private val LIGHT_PANEL_ON = 0xFF111111.toInt()
    private val MONO_PANEL_SURFACE = 0xFF262626.toInt()
    private val MONO_PANEL_ACTIVE = 0xFFE0E0E0.toInt()
    private val TERMINAL_GREEN = 0xFF33FF66.toInt()
    /** Soft rounded-rectangle corner shared by the quick-panel rows, matching the Menu screen. */
    private val QUICK_PANEL_ROW_CORNER_DP = 26f

    /** Circular clip for a full-colour thumbnail shown in a quick-panel row. */
    private val circularOutlineProvider = object : android.view.ViewOutlineProvider() {
        override fun getOutline(view: View, outline: android.graphics.Outline) {
            outline.setOval(0, 0, view.width, view.height)
        }
    }

    /** A dark, accent-tinted surface for the tonal/gradient quick-panel chrome (saturation clamped
     *  so the white icons/text keep enough contrast). Delegates to the shared transform so the
     *  phone preview and the watch tint the same album accent identically. */
    private fun tonalSurface(accent: Int, lightness: Float = 0.28f): Int =
            PaletteTransforms.tonalSurface(accent, lightness)

    /** The exact light tonal container the Expressive face paints on its transport buttons
     *  (tonalSurface(accent, .74, .40, .92)). Overlay surfaces reuse it so the quick panel /
     *  volume / seek read as the same shade the player shows, not a darker, stronger accent. */
    private fun expressiveSurface(accent: Int): Int =
            PaletteTransforms.tonalSurface(accent, 0.74f, 0.40f, 0.92f)

    private fun capsule(fill: Int, strokePx: Int = 0, strokeColor: Int = 0, radiusPx: Float = 999f) =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = radiusPx
                setColor(fill)
                if (strokePx > 0) setStroke(strokePx, strokeColor)
            }

    private fun gradientCapsule(topColor: Int, bottomColor: Int, radiusPx: Float = 999f) =
            GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(topColor, bottomColor)).apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = radiusPx
            }

    /** Three real album swatches, with a thin glass keyline instead of a synthetic opposite hue. */
    private fun prismCapsule(radiusPx: Float = 999f, active: Boolean = false): GradientDrawable {
        val density = resources.displayMetrics.density
        val colors = if (active) {
            intArrayOf(
                    tonalSurface(resolvedQuickPanelTertiaryAccent(), .34f),
                    tonalSurface(resolvedQuickPanelAccent(), .42f),
                    tonalSurface(resolvedQuickPanelSecondaryAccent(), .28f))
        } else {
            intArrayOf(
                    tonalSurface(resolvedQuickPanelTertiaryAccent(), .20f),
                    tonalSurface(resolvedQuickPanelAccent(), .30f),
                    tonalSurface(resolvedQuickPanelSecondaryAccent(), .16f))
        }
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            setStroke((if (active) 1.5f else 1f).times(density).roundToInt(), 0x66FFFFFF)
        }
    }

    /** Inactive background of a round quick-panel slot button, per [quickPanelStyle]. The active
     *  (accent-filled) look stays shared across styles. */
    private fun inactiveQuickButtonBackground(): android.graphics.drawable.Drawable {
        val d = resources.displayMetrics.density
        val hairline = (1.25f * d).roundToInt().coerceAtLeast(1)
        return when (quickPanelStyle) {
            "glass_white" -> capsule(0xB3FFFFFF.toInt())
            "glass_tonal" -> capsule(ColorUtils.setAlphaComponent(
                    expressiveSurface(resolvedQuickPanelAccent()), 0xB3))
            "minimal" -> capsule(Color.TRANSPARENT, hairline, 0x66FFFFFF)
            "material" -> capsule(materialSurfaceColor)
            "tonal" -> capsule(expressiveSurface(resolvedQuickPanelAccent()))
            "neon" -> capsule(Color.TRANSPARENT, (2f * resources.displayMetrics.density).toInt(), resolvedQuickPanelAccent())
            "light" -> capsule(LIGHT_PANEL_SURFACE)
            "gradient" -> gradientCapsule(tonalSurface(resolvedQuickPanelAccent(), 0.34f), tonalSurface(resolvedQuickPanelSecondaryAccent(), 0.16f))
            "mono" -> capsule(MONO_PANEL_SURFACE)
            "outline" -> capsule(Color.TRANSPARENT, hairline, Color.WHITE)
            "outline_glass_white" -> capsule(0x80FFFFFF.toInt(), hairline, Color.WHITE)
            "prism" -> prismCapsule()
            "duotone" -> capsule(tonalSurface(resolvedQuickPanelSecondaryAccent()))
            "contrast" -> capsule(Color.BLACK, (2f * resources.displayMetrics.density).toInt(), Color.WHITE)
            "terminal" -> capsule(Color.TRANSPARENT, hairline, TERMINAL_GREEN, radiusPx = 0f)
            "frost" -> capsule(0x33FFFFFF)
            // --- Reduced styles. These drop the container rather than restyling it, so the icon
            // itself becomes the control; the touch target is unchanged in every case.
            "ghost" -> capsule(Color.TRANSPARENT)
            "mist" -> capsule(0x14FFFFFF)
            "slab" -> capsule(SLAB_SURFACE, radiusPx = SLAB_CORNER_DP * d)
            "ink" -> underlineDrawable(
                    liftedAccent(resolvedQuickPanelAccent()), (2f * d).roundToInt())
            "dot" -> markerDrawable(
                    liftedAccent(resolvedQuickPanelAccent()), (5f * d).roundToInt())
            else -> AppCompatResources.getDrawable(this, R.drawable.glass_pill_background)!!
        }
    }

    /** Neutral flat surface for the "slab" style - a single tone, no stroke, no gradient. */
    private val SLAB_SURFACE = 0xFF1E1E20.toInt()
    private val SLAB_CORNER_DP = 10f

    /**
     * A small centred dot along the bottom edge, used by the "dot" quick-panel style: the slot has
     * no container at all, and this marks it as a control instead. Same [LayerDrawable] technique
     * as [underlineDrawable] - a fixed-size, bottom-gravity layer, since the view height is not
     * known when the background is assigned.
     */
    private fun markerDrawable(color: Int, sizePx: Int): LayerDrawable =
            LayerDrawable(arrayOf<android.graphics.drawable.Drawable>(
                    ColorDrawable(Color.TRANSPARENT),
                    GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(color)
                    }
            )).apply {
                setLayerGravity(1, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
                setLayerWidth(1, sizePx)
                setLayerHeight(1, sizePx)
            }

    /** Background of the full-width Up Next / long-slot / action rows, per [quickPanelStyle].
     *
     *  Rows use the Menu screen's soft 26dp rounded-rectangle corners (not a full stadium) so the
     *  quick panel's scrollable list reads as the same interface as the rest of the app. Styles
     *  differ in *surface*, not shape. "terminal" stays square: sharp corners are its identity. */
    /** Height of a full-width quick-panel pill, in px: the user's chosen content height plus the
     *  panel's own 12dp padding rhythm, matching QueueRow's arithmetic on the queue screen. */
    private fun listRowHeightPx(): Int {
        val d = resources.displayMetrics.density
        return ((listRowSize.contentHeight.value + 24f) * d).roundToInt()
    }

    /**
     * Grows a quick-panel row's leading cover to fill the pill, matching the queue and the menu.
     *
     * Only called for genuine artwork (`isArtwork`): a monochrome template glyph stays at the
     * layout's 30dp, because scaling a glyph to the pill's height reads as a rendering fault rather
     * than as a bigger icon. The row's own start padding drops to the shared inset at the same time
     * so the cover sits in an even frame instead of behind a text keyline it does not need.
     */
    private fun applyListRowArtworkSize(row: View, icon: ImageView) {
        val density = resources.displayMetrics.density
        val inset = (QUEUE_ARTWORK_INSET.value * density).roundToInt()
        row.layoutParams = row.layoutParams?.apply { height = listRowHeightPx() }
        // The vertical inset is *set*, not preserved. [listRowArtworkSize] derives the cover from
        // `rowHeight - QUEUE_ARTWORK_INSET * 2`, so the row has to actually use that inset for the
        // result to fit. applyQuickPanelLayout gives the Up Next pill 11dp top and bottom for its
        // two lines of text; carrying that forward left the cover 22dp less height than it had been
        // sized for, and a LinearLayout clamps a child's height while honouring its width - so the
        // circular outline came out an oval with centerCrop slicing the artwork's top and bottom
        // off. The inflated extra rows never hit this because nothing gives them vertical padding.
        row.setPaddingRelative(inset, inset, row.paddingEnd, inset)
        // Belt and braces: never ask for more height than the row can actually give, so any future
        // change to the row's padding or minimum height degrades to a smaller circle rather than
        // back to an oval.
        val available = (listRowHeightPx() - inset * 2).coerceAtLeast(1)
        val sizePx = minOf(
                (listRowArtworkSize(listRowSize.contentHeight + 24.dp).value * density).roundToInt(),
                available)
        icon.layoutParams = icon.layoutParams?.apply {
            width = sizePx
            height = sizePx
        }
    }

    /** Re-applies the pill height to the Up Next row. The inflated extra rows pick it up as they
     *  are created in [renderQuickPanelExtraActions]. */
    private fun applyListRowHeight() {
        binding.quickActionUpNext.layoutParams =
                binding.quickActionUpNext.layoutParams?.apply { height = listRowHeightPx() }
    }

    /** Fills [view] with [artwork] under the cover scrim, honouring the active cover variation
     *  (blur/tonal/square). Keeps the quick panel's pills identical to the queue's rows, which
     *  render the same variations through QueueScreen. */
    private fun applyCoverPill(view: View, artwork: Bitmap) {
        val style = coverPillStyle
        val bitmap = if (style == QueueStyle.COVER_BLUR) blurredCover(artwork) else artwork
        val corner = if (style == QueueStyle.COVER_SQUARE) {
            10f * resources.displayMetrics.density
        } else {
            quickPanelRowCornerPx()
        }
        val scrimTint = if (style == QueueStyle.COVER_TONAL) {
            resolvedQuickPanelAccent()
        } else {
            Color.BLACK
        }
        view.background = CoverPillDrawable(
                bitmap,
                corner,
                scrimTint = scrimTint,
                softScrim = style == QueueStyle.COVER_BLUR
        )
    }

    /** Corner radius the quick-panel pills are drawn with, in px - shared by the flat backgrounds
     *  and the cover-filled ones so both round identically. */
    private fun quickPanelRowCornerPx(): Float = when (quickPanelStyle) {
        "terminal" -> 0f
        // Slab's identity is a tight, flat rectangle rather than the soft list-item corner.
        "slab" -> SLAB_CORNER_DP * resources.displayMetrics.density
        else -> QUICK_PANEL_ROW_CORNER_DP * resources.displayMetrics.density
    }

    private fun quickPanelRowBackground(): android.graphics.drawable.Drawable {
        val d = resources.displayMetrics.density
        val hairline = (1.25f * d).roundToInt().coerceAtLeast(1)
        val r = if (quickPanelStyle == "terminal") 0f else QUICK_PANEL_ROW_CORNER_DP * d
        return when (quickPanelStyle) {
            "glass_white" -> capsule(0xB3FFFFFF.toInt(), radiusPx = r)
            "glass_tonal" -> capsule(
                    ColorUtils.setAlphaComponent(expressiveSurface(resolvedQuickPanelAccent()), 0xB3),
                    radiusPx = r)
            "minimal" -> capsule(Color.TRANSPARENT, hairline, 0x66FFFFFF, radiusPx = r)
            "material" -> capsule(materialSurfaceColor, radiusPx = r)
            "tonal" -> capsule(expressiveSurface(resolvedQuickPanelAccent()), radiusPx = r)
            "neon" -> capsule(Color.TRANSPARENT, (2f * d).toInt(), resolvedQuickPanelAccent(), radiusPx = r)
            "light" -> capsule(LIGHT_PANEL_SURFACE, radiusPx = r)
            "gradient" -> gradientCapsule(tonalSurface(resolvedQuickPanelAccent(), 0.34f), tonalSurface(resolvedQuickPanelSecondaryAccent(), 0.16f), radiusPx = r)
            "mono" -> capsule(MONO_PANEL_SURFACE, radiusPx = r)
            "outline" -> capsule(Color.TRANSPARENT, hairline, Color.WHITE, radiusPx = r)
            "outline_glass_white" -> capsule(0x80FFFFFF.toInt(), hairline, Color.WHITE, radiusPx = r)
            "duotone" -> capsule(tonalSurface(resolvedQuickPanelSecondaryAccent()), radiusPx = r)
            "contrast" -> capsule(Color.BLACK, (2f * d).toInt(), Color.WHITE, radiusPx = r)
            "prism" -> prismCapsule(radiusPx = r)
            "terminal" -> capsule(Color.TRANSPARENT, hairline, TERMINAL_GREEN, radiusPx = 0f)
            "frost" -> capsule(0x33FFFFFF, radiusPx = r)
            // The reduced styles keep a faint surface on the full-width rows even where the round
            // slots have none: a row is a list item, and with zero chrome its tap area would be
            // completely invisible against the backdrop.
            "ghost" -> capsule(0x0DFFFFFF, radiusPx = r)
            "mist" -> capsule(0x14FFFFFF, radiusPx = r)
            "slab" -> capsule(SLAB_SURFACE, radiusPx = r)
            "ink" -> capsule(0x0DFFFFFF, hairline,
                    ColorUtils.setAlphaComponent(liftedAccent(resolvedQuickPanelAccent()), 0xB3),
                    radiusPx = r)
            "dot" -> capsule(0x0DFFFFFF, radiusPx = r)
            else -> capsule(ContextCompat.getColor(this, R.color.glass_surface_fill), radiusPx = r)
        }
    }

    /**
     * Background for the awake Up Next pill, honouring its own [upNextPillStyle] independent of the
     * quick-panel style. "follow" keeps the historical behaviour (the pill follows the panel), so
     * existing installs are unchanged. Paired with [upNextPillTint] for legible text/icon colour.
     *
     * "white_blur" is a *frosted* white rather than a real per-pill GPU blur: the panel already
     * sits over its own (optionally blurred) overlay backdrop, so a translucent white capsule on
     * top reads as frosted glass without the cost of blurring a bitmap for this one row.
     */
    private fun upNextPillBackground(): android.graphics.drawable.Drawable {
        if (upNextPillStyle == "follow") return quickPanelRowBackground()
        val r = quickPanelRowCornerPx()
        return capsule(upNextPillFillColor(), radiusPx = r)
    }

    /** The Up Next pill's fill colour for a colour-based style (everything except "follow", which
     *  uses the quick-panel row background). Shared by the quick-panel pill and the awake player
     *  pill so both look identical for a given style. */
    private fun upNextPillFillColor(): Int {
        val accent = resolvedQuickPanelAccent()
        return when (upNextPillStyle) {
            "accent" -> accent
            "translucent" -> 0x40FFFFFF
            "white" -> 0xF2FFFFFF.toInt()
            "white_blur" -> 0x73FFFFFF
            "black" -> 0xCC000000.toInt()
            "dynamic" -> tonalSurface(accent, lightness = 0.24f)
            "transparent" -> Color.TRANSPARENT
            // "follow": the awake pill has no quick-panel context, so it uses the tonal accent.
            else -> ColorUtils.setAlphaComponent(accent, 0x38)
        }
    }

    /** Text/icon colour for the Up Next pill, contrasting with [upNextPillBackground]. Delegates to
     *  the quick-panel tint in "follow" mode. */
    private fun upNextPillTint(): Int = when (upNextPillStyle) {
        "follow" -> quickPanelInactiveTint()
        "white", "white_blur" -> LIGHT_PANEL_ON
        "translucent", "transparent" -> Color.WHITE
        "accent" -> contrastingIconColor(resolvedQuickPanelAccent())
        "black" -> Color.WHITE
        "dynamic" -> contrastingIconColor(tonalSurface(resolvedQuickPanelAccent(), lightness = 0.24f))
        else -> quickPanelInactiveTint()
    }

    /** The awake player pill's text colour, resolved from the same style as the quick-panel pill.
     *  "follow" uses white here (the pill sits over the player art, not the panel backdrop). */
    private fun awakeUpNextPillTint(): Int =
            if (upNextPillStyle == "follow") Color.WHITE else upNextPillTint()

    /** Representative colour of the surface the quick panel's free-floating title/artist text sits
     *  on - the full-screen overlay backdrop, NOT the button capsules. Mirrors the drawables built
     *  in [applyOverlayBackdrop] (for gradients, the accent-toned stop, which is what sits behind
     *  the centered text). Used for contrast decisions so the metadata text auto-flips light/dark
     *  with the backdrop instead of following the capsule chrome tint. */
    private fun quickPanelBackdropColor(): Int {
        val accent = resolvedQuickPanelAccent()
        return when (OverlayBackdropResolver.resolve(overlayBackdropStyle, quickPanelStyle)) {
            OverlayBackdrop.ACRYLIC, OverlayBackdrop.SOLID_ALBUM ->
                PaletteTransforms.tonalSurface(
                        accent, .22f, PaletteTransforms.FACE_MIN_SAT, PaletteTransforms.FACE_MAX_SAT)
            OverlayBackdrop.GRADIENT -> tonalSurface(accent, .42f)
            OverlayBackdrop.DUOTONE -> tonalSurface(accent, .30f)
            OverlayBackdrop.PRISM -> tonalSurface(accent, .42f)
            // The centre of the Expressive wash, where this text sits, is well inside the vignette
            // and reads as a dark album tone rather than as black.
            OverlayBackdrop.EXPRESSIVE, OverlayBackdrop.EXPRESSIVE_NO_BLUR ->
                PaletteTransforms.tonalSurface(accent, .30f, .30f, .90f)
            // Black, like the other translucent panes: this is the colour blended *behind* the
            // backdrop, and tinting it would double up with the pane's own album tint.
            OverlayBackdrop.LIQUID_GLASS,
            OverlayBackdrop.GLASS, OverlayBackdrop.SOLID_BLACK, OverlayBackdrop.FOLLOW_STYLE ->
                Color.BLACK
        }
    }

    /** Icon/text colour for the inactive quick-panel chrome, per [quickPanelStyle]. */
    private fun quickPanelInactiveTint(): Int = when (quickPanelStyle) {
        "light", "glass_white", "outline_glass_white" -> LIGHT_PANEL_ON
        "neon" -> resolvedQuickPanelAccent()
        "terminal" -> TERMINAL_GREEN
        // The tonal chrome now uses the Expressive face's light container, so its glyphs must be
        // dark to stay legible (matching the face's dark-on-light transport icons).
        "tonal", "glass_tonal" -> contrastingIconColor(
                expressiveSurface(resolvedQuickPanelAccent()))
        else -> Color.WHITE
    }

    /** Fill colour of an *active* quick-panel button, per [quickPanelStyle]. Most styles use the
     *  album accent; the monochrome styles keep their own palette so the accent never leaks in. */
    private fun activeQuickFillColor(): Int = when (quickPanelStyle) {
        "contrast" -> Color.WHITE
        "terminal" -> TERMINAL_GREEN
        "mono" -> MONO_PANEL_ACTIVE
        else -> resolvedQuickPanelAccent()
    }

    private fun activeQuickButtonBackground(): android.graphics.drawable.Drawable =
            if (quickPanelStyle == "prism") {
                prismCapsule(active = true)
            } else {
                // The reduced styles are chromeless only while inactive - an active slot always
                // gets a real accent fill, which is what makes the state readable at a glance.
                capsule(activeQuickFillColor(), radiusPx = when (quickPanelStyle) {
                    "terminal" -> 0f
                    "slab" -> SLAB_CORNER_DP * resources.displayMetrics.density
                    else -> 999f
                })
            }

    /** White icons can disappear against a light album-art accent color, so the icon itself
     *  flips to black/white depending on how light or dark [backgroundColor] is. */
    private fun contrastingIconColor(backgroundColor: Int): Int {
        // Pick black or white by whichever gives the higher WCAG contrast ratio against the
        // background (crossover ~0.179 luminance), not a naive 0.5 split - the latter left white
        // icons on medium-light pills (e.g. the expressive face's tonal surfaces, ~0.45 luminance
        // but visually light) where dark ink is clearly more legible.
        val lum = ColorUtils.calculateLuminance(backgroundColor)
        val contrastWithWhite = 1.05 / (lum + 0.05)
        val contrastWithBlack = (lum + 0.05) / 0.05
        return if (contrastWithBlack >= contrastWithWhite) Color.BLACK else Color.WHITE
    }

    /** [tintable] must be false for a real, already-colored icon (a rasterized notification
     *  bitmap or a user-picked custom action icon) - forcing a flat colour filter over one
     *  discards all of its original detail and reads as a garbled/blank glyph. The active/inactive
     *  capsule background still applies either way; only the icon's own colour is affected. */
    private fun setQuickActionButtonActive(view: ImageView, active: Boolean, tintable: Boolean = true) {
        if (active) {
            view.background = activeQuickButtonBackground()
            if (tintable) {
                view.setColorFilter(if (quickPanelStyle == "prism") Color.WHITE
                else contrastingIconColor(activeQuickFillColor()))
            } else {
                view.clearColorFilter()
            }
        } else {
            view.background = inactiveQuickButtonBackground()
            if (tintable) {
                view.setColorFilter(quickPanelInactiveTint())
            } else {
                view.clearColorFilter()
            }
        }
    }

    /** Reflects confirmed shuffle/repeat/like state (from the phone) on the panel buttons that
     *  currently host those toggles - wherever the user placed them. Shuffle/repeat are
     *  reliable (real MediaSession state); "liked" is a best-effort guess since there's no
     *  generic cross-app API for it - see LikeAction.isCurrentlyLiked(). Custom-action slots
     *  have no state and stay in the inactive glass look. */
    private fun updateQuickActionButtonStates() {
        for ((index, panelButton) in quickPanelViews().withIndex()) {
            val tintable = !quickSlotUsesRealIcon[index]
            when (quickSlotModes[index]) {
                QuickSlotMode.LIKE -> {
                    setQuickActionButtonActive(panelButton, liked)
                    setToggleSemantics(panelButton, liked)
                }
                QuickSlotMode.SHUFFLE -> {
                    setQuickActionButtonActive(panelButton, shuffleEnabled)
                    setToggleSemantics(panelButton, shuffleEnabled)
                }
                QuickSlotMode.REPEAT -> {
                    panelButton.setImageResource(
                            if (repeatMode == 2) {
                                com.svartifoss.snfell.common.R.drawable.action_repeat_one
                            } else {
                                com.svartifoss.snfell.common.R.drawable.action_repeat
                            }
                    )
                    panelButton.contentDescription = getString(
                            if (repeatMode == 2) R.string.quick_action_repeat_one
                            else R.string.quick_action_repeat
                    )
                    setQuickActionButtonActive(panelButton, repeatMode != 0)
                    panelButton.isSelected = repeatMode != 0
                    ViewCompat.setStateDescription(
                            panelButton,
                            getString(
                                    when (repeatMode) {
                                        1 -> R.string.quick_action_repeat_all
                                        2 -> R.string.quick_action_repeat_one
                                        else -> R.string.state_off
                                    }
                            )
                    )
                }
                QuickSlotMode.CUSTOM -> {
                    setQuickActionButtonActive(panelButton, false, tintable)
                    panelButton.isSelected = false
                    ViewCompat.setStateDescription(panelButton, null)
                }
                QuickSlotMode.SESSION -> {
                    val semantic = displayedSessionQuickActions.getOrNull(index)?.semantic
                    // Repeat is the one session slot whose glyph carries three states rather than
                    // two, so the fallback has to be swapped for the repeat-one variant. Only the
                    // fallback: while the player's own icon is on screen it already says which
                    // state it is in - that is what it is drawn per state for - and painting over
                    // it replaced the player's artwork on a panel that promises to mirror it.
                    if (semantic == "repeat" && !sessionSlotShowsAppIcon[index]) {
                        panelButton.setImageResource(
                                if (repeatMode == 2) {
                                    com.svartifoss.snfell.common.R.drawable.action_repeat_one
                                } else {
                                    com.svartifoss.snfell.common.R.drawable.action_repeat
                                }
                        )
                        panelButton.contentDescription = getString(
                                if (repeatMode == 2) R.string.quick_action_repeat_one
                                else R.string.quick_action_repeat
                        )
                    }
                    val active = when (semantic) {
                        "like" -> liked
                        "shuffle" -> shuffleEnabled
                        "repeat" -> repeatMode != 0
                        else -> false
                    }
                    setQuickActionButtonActive(panelButton, active, tintable)
                    if (semantic == "like" || semantic == "shuffle" || semantic == "repeat") {
                        if (semantic == "repeat") {
                            panelButton.isSelected = active
                            ViewCompat.setStateDescription(
                                    panelButton,
                                    getString(
                                            when (repeatMode) {
                                                1 -> R.string.quick_action_repeat_all
                                                2 -> R.string.quick_action_repeat_one
                                                else -> R.string.state_off
                                            }
                                    )
                            )
                        } else {
                            setToggleSemantics(panelButton, active)
                        }
                    } else {
                        panelButton.isSelected = false
                        ViewCompat.setStateDescription(panelButton, null)
                    }
                }
                QuickSlotMode.HIDDEN -> {
                    panelButton.isSelected = false
                    ViewCompat.setStateDescription(panelButton, null)
                }
            }
        }
    }

    private fun setToggleSemantics(view: View, active: Boolean) {
        view.isSelected = active
        ViewCompat.setStateDescription(
                view,
                getString(if (active) R.string.state_on else R.string.state_off)
        )
    }

    private val quickActionPressFeedback = View.OnTouchListener { v, event ->
        val imageView = v as ImageView
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                imageView.background = accentCircleDrawable()
                val quickIndex = quickPanelViews().indexOf(imageView)
                val miniSlot = screenButtonViews().firstOrNull { it.second === imageView }?.first
                val tintable = when {
                    quickIndex >= 0 -> !quickSlotUsesRealIcon[quickIndex]
                    miniSlot != null -> screenButtonIconTintable[miniSlot] ?: true
                    else -> true
                }
                val forceMonochromeMini = miniSlot != null && screenButtonsBgStyle in setOf(
                        "glow_album", "glow_exp", "outline_exp", "outline_exp_album", "icon_exp",
                        "solid_exp_album")
                if (tintable || forceMonochromeMini) {
                    imageView.setColorFilter(contrastingIconColor(currentAccentColor))
                } else {
                    imageView.clearColorFilter()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> when (imageView) {
                // Panel buttons restore per their current slot mode (state ring or inactive
                // glass) - which toggle lives where is user-configurable now.
                binding.quickActionLike, binding.quickActionShuffle, binding.quickActionRepeat ->
                    updateQuickActionButtonStates()
                // Mini buttons carry user-styled backgrounds (color/opacity) - restore those,
                // not the quick-action glass default.
                binding.screenButton1, binding.screenButton2, binding.screenButton3 -> {
                    imageView.clearColorFilter()
                    styleScreenButton(imageView)
                }
                else -> setQuickActionButtonActive(imageView, false)
            }
        }
        false
    }

    private fun updateUpNextPreview(data: CustomListWithBitmaps) {
        // Search results and streaming-shortcut lists reuse the same DataItem. They must not wipe a
        // still-valid queue preview while the phone is preparing a fresh PLAYLIST response.
        if (data.listId != CustomLists.PLAYLIST && data.listId != CustomLists.HISTORY) {
            return
        }

        // History (the fallback shown when the playing app exposes no real queue) is backward
        // looking - there's no "next" track to preview in that case.
        val nextItem = if (data.listId == CustomLists.PLAYLIST) {
            // The queue list is the full queue with activeEntryId marking the current track -
            // "Up Next" is the entry AFTER it. Taking items.first() showed whatever happened to
            // sit at the top of the queue (usually the current or even an older track). Without
            // an active id (some apps never set activeQueueItemId), fall back to the first entry.
            val items = data.items
            val activeIndexFromId = data.activeEntryId
                    ?.let { id -> items.indexOfFirst { item -> item.listItem.entryId == id } }
                    ?: -1
            // Several players omit activeQueueItemId even though their queue still starts with (or
            // contains) the current song. Match the visible title before falling back to item 0 so
            // the pill does not label the current track as "Up Next".
            val activeIndexFromTitle = items.indexOfFirst { item ->
                item.listItem.entryTitle.equals(faceState.value.title, ignoreCase = true)
            }
            // The controller often advances metadata before activeQueueItemId. Prefer the title
            // match so the cached preview can advance synchronously on a skip.
            val activeIndex = activeIndexFromTitle.takeIf { it >= 0 } ?: activeIndexFromId
            if (activeIndex >= 0) {
                items.getOrNull(activeIndex + 1)
            } else {
                items.firstOrNull()
            }
        } else {
            null
        }?.takeUnless { it.listItem.entryId == CustomLists.SPECIAL_ITEM_ERROR }

        val nextEntry = nextItem?.listItem
        val nextTrack = nextEntry?.entryTitle?.takeIf(String::isNotBlank)
        // The full queue, for faces that draw the queue itself. Built from the same list the pill
        // above reads so the two can never disagree about what is coming next. History is excluded:
        // it is backward-looking, and a carousel of already-played tracks would read as upcoming.
        val cards = if (data.listId == CustomLists.PLAYLIST) {
            data.items
                    .filter { it.listItem.entryId != CustomLists.SPECIAL_ITEM_ERROR }
                    .map { item ->
                        QueueCard(
                                entryId = item.listItem.entryId,
                                title = item.listItem.entryTitle,
                                artist = item.listItem.entrySubtitle.orEmpty(),
                                art = item.icon?.asImageBitmap())
                    }
        } else {
            emptyList()
        }
        updateFaceState { state ->
            state.copy(
                    upNextTitle = nextTrack.orEmpty(),
                    upNextArtist = nextEntry?.entrySubtitle?.takeIf(String::isNotBlank).orEmpty(),
                    queueCards = cards
            )
        }

        // A custom long-row action shows its own title instead of queue data. The AOD state above
        // still gets refreshed because its Up Next pill is a separate, display-only surface.
        if (quickPanelLongMode != QuickLongMode.UP_NEXT) return

        val artwork = nextItem?.icon
        if (artwork != null && coverPillsActive) {
            // Cover style: the art fills the whole pill instead of sitting in the 30dp slot, and
            // the label goes white because the panel tint is chosen against the flat pill and can
            // disappear over artwork.
            applyCoverPill(binding.quickActionUpNext, artwork)
            binding.quickActionUpNextLabel.setTextColor(Color.WHITE)
            binding.quickActionUpNextTrack.setTextColor(
                    ColorUtils.setAlphaComponent(Color.WHITE, 0xB3))
            binding.quickActionUpNextArtwork.visibility = View.GONE
            binding.quickActionUpNextIcon.visibility = View.GONE
            // The art is the pill's background here, so the text wants its own keyline back - the
            // cover inset applyListRowArtworkSize leaves behind is for a cover in the row, and
            // switching styles would otherwise keep it until the panel was next rebuilt.
            applyUpNextTextPadding()
        } else {
            // No cover to show (this entry has none, or the style is off): stay on the themed
            // pill and its own tint. Forcing the static glass drawable here made a light
            // quick-panel style render dark-on-dark, since the tint is picked for the theme.
            binding.quickActionUpNext.background = upNextPillBackground()
            val upNextTint = upNextPillTint()
            binding.quickActionUpNextLabel.setTextColor(upNextTint)
            binding.quickActionUpNextTrack.setTextColor(
                    ColorUtils.setAlphaComponent(upNextTint, 0xB3))
            if (artwork != null) {
                binding.quickActionUpNextArtwork.setImageBitmap(artwork)
                binding.quickActionUpNextArtwork.visibility = View.VISIBLE
                binding.quickActionUpNextIcon.visibility = View.GONE
                // Same growth the inflated extra rows get in renderQuickPanelExtraActions: this
                // row is a sibling of theirs, so leaving it on the layout's fixed 30dp made the
                // one row showing the *actual* next track carry the smallest cover in the panel.
                applyListRowArtworkSize(
                        binding.quickActionUpNext, binding.quickActionUpNextArtwork)
            } else {
                clearQuickUpNextArtwork()
            }
        }

        binding.quickActionUpNextTrack.apply {
            if (nextTrack == null) {
                visibility = View.GONE
            } else {
                visibility = View.VISIBLE
                text = nextTrack
            }
        }
    }

    private fun showVolumeBar() {
        val continuingVolumeOverlay = overlayActive && activeOverlayKind == OverlayKind.VOLUME
        showOverlay(OverlayKind.VOLUME)

        binding.seekOverlayMeter.visibility = View.GONE

        if (!continuingVolumeOverlay || binding.volumeBar.visibility != View.VISIBLE) {
            val wasVisible = binding.volumeBar.visibility == View.VISIBLE
            binding.volumeBar.animate().cancel()
            if (!wasVisible) binding.volumeBar.alpha = 0f
            binding.volumeBar.visibility = View.VISIBLE
            binding.volumeBar.animate().alpha(1f).setDuration(OVERLAY_FADE_OUT_MS).start()
        }

        // The two rings share the same accent color and would otherwise both be visible at
        // once now that they're drawn on top of the blur overlay - only one should show at a time.
        if (!continuingVolumeOverlay) {
            binding.seekBar.animate().cancel()
            binding.seekBar.animate().alpha(0f).setDuration(OVERLAY_FADE_IN_MS).start()
        }

        binding.textSeekTime.visibility = View.GONE
        binding.textVolumePercent.visibility = View.VISIBLE
        applyVolumeOverlayStyle()
        applyVolumePanelLayout()
        val volumeChromeTint = when (binding.volumeBar.barStyle) {
            VolumeStyle.LIGHT -> LIGHT_PANEL_ON
            VolumeStyle.TERMINAL -> TERMINAL_GREEN
            else -> Color.WHITE
        }
        binding.volumeIconTop.setColorFilter(volumeChromeTint)
        binding.volumeIconBottom.setColorFilter(volumeChromeTint)
        binding.volumeIconTop.visibility = View.VISIBLE
        binding.volumeIconBottom.visibility = View.VISIBLE

        handler.removeMessages(MESSAGE_HIDE_VOLUME)
        handler.sendEmptyMessageDelayed(MESSAGE_HIDE_VOLUME, volumeBarTimeoutMs)
    }

    private fun showSeekOverlay(fraction: Float) {
        showOverlay(OverlayKind.SEEK)

        binding.volumeBar.visibility = View.GONE
        binding.volumeIconTop.visibility = View.GONE
        binding.volumeIconBottom.visibility = View.GONE
        binding.textVolumePercent.visibility = View.GONE
        binding.textSeekTime.visibility = View.VISIBLE
        applySeekOverlayStyle(fraction)
        applySeekPanelLayout(fraction)

        // Auto-hide the seek overlay just like the volume overlay does.
        handler.removeMessages(MESSAGE_HIDE_VOLUME)
        handler.sendEmptyMessageDelayed(MESSAGE_HIDE_VOLUME, volumeBarTimeoutMs)
    }

    /**
     * Styles the scrub-time readout per [MiscPreferences.WEAR_SEEK_STYLE]: "plain" is the
     * original bare centered time, "pill"/"expressive" wrap it in a capsule (see
     * [applyPillReadoutStyle]), "giant" blows it up for at-a-glance reading mid-drag, and "split"
     * stacks the target position over the track's total length (seek-only - there's no
     * equivalent second line for a plain volume percentage).
     */
    private fun applySeekOverlayStyle(fraction: Float) {
        val position = formatPlaybackTime((fraction * lastKnownDurationMs).toLong())
        val text = binding.textSeekTime

        if (seekOverlayStyle == "split") {
            text.textSize = 30f
            text.background = null
            text.setPadding(0, 0, 0, 0)
            val total = formatPlaybackTime(lastKnownDurationMs)
            val stacked = SpannableString("$position\n$total")
            stacked.setSpan(RelativeSizeSpan(0.55f), position.length + 1, stacked.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            stacked.setSpan(ForegroundColorSpan(0xB3FFFFFF.toInt()), position.length + 1,
                    stacked.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            text.text = stacked
            return
        }

        applyPillReadoutStyle(text, seekOverlayStyle, position, resolvedProgressAccent())
    }

    /** Same [MiscPreferences.WEAR_SEEK_STYLE] options, applied to the volume-percentage readout
     *  (there's no "split" equivalent for a single percentage, so it falls back to plain). */
    private fun applyVolumeOverlayStyle() {
        val percentText = getString(
                R.string.volume_percent_format,
                (binding.volumeBar.volume * 100).roundToInt()
        )
        applyPillReadoutStyle(
                binding.textVolumePercent, seekOverlayStyle, percentText, resolvedVolumeAccent())
        // Chromeless readouts draw white numerals straight onto the backdrop, which the LIGHT arc
        // style makes pale; they get flipped to dark text. Styles carrying their own filled
        // container already guarantee their own contrast and are left alone.
        if (binding.volumeBar.barStyle == VolumeStyle.LIGHT &&
                seekOverlayStyle !in setOf("pill", "expressive", "material", "white", "glass_white",
                        "translucent_album", "glow_album", "outline", "solid_theme", "solid_album",
                        "mono", "tonal_dark", "terminal")) {
            binding.textVolumePercent.setTextColor(LIGHT_PANEL_ON)
        }
    }

    private fun applyVolumePanelLayout() {
        val density = resources.displayMetrics.density
        val top = binding.volumeIconTop.layoutParams as FrameLayout.LayoutParams
        val bottom = binding.volumeIconBottom.layoutParams as FrameLayout.LayoutParams
        top.marginStart = 0
        top.marginEnd = 0
        top.topMargin = resources.getDimensionPixelSize(R.dimen.music_screen_icon_offset)
        top.bottomMargin = 0
        bottom.marginStart = 0
        bottom.marginEnd = 0
        bottom.topMargin = 0
        bottom.bottomMargin = resources.getDimensionPixelSize(R.dimen.music_screen_icon_offset)
        binding.volumeIconTop.translationY = 0f
        binding.volumeIconBottom.translationY = 0f
        binding.textVolumePercent.translationY = 0f

        when (binding.volumeBar.barLayout) {
            // Every arc that fills bottom-to-top keeps the louder/quieter glyphs on the vertical
            // axis, matching the direction the fill actually travels.
            VolumeLayout.EDGE,
            VolumeLayout.EDGE_TALL,
            VolumeLayout.EDGE_RIGHT,
            VolumeLayout.RING,
            VolumeLayout.HALO -> {
                top.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                bottom.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            }
            // The top/bottom arcs fill left-to-right, so the glyphs move onto the horizontal axis.
            // They sit at mid-height (as in METER) rather than beside the arc's ends: near the top
            // or bottom of a round screen there is no width left to place them without clipping.
            VolumeLayout.EDGE_TOP,
            VolumeLayout.EDGE_BOTTOM -> {
                val side = (8f * density).roundToInt()
                top.gravity = Gravity.END or Gravity.CENTER_VERTICAL
                top.marginEnd = side
                top.topMargin = 0
                bottom.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                bottom.marginStart = side
                bottom.bottomMargin = 0
                bottom.topMargin = 0
            }
            VolumeLayout.METER -> {
                val side = (8f * density).roundToInt()
                val vertical = (29f * density).roundToInt()
                top.gravity = Gravity.END or Gravity.CENTER_VERTICAL
                top.marginEnd = side
                top.topMargin = 0
                bottom.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                bottom.marginStart = side
                bottom.bottomMargin = 0
                bottom.topMargin = 0
                binding.volumeIconTop.translationY = vertical.toFloat()
                binding.volumeIconBottom.translationY = vertical.toFloat()
                binding.textVolumePercent.translationY = -16f * density
            }
        }
        binding.volumeIconTop.layoutParams = top
        binding.volumeIconBottom.layoutParams = bottom
    }

    private fun applySeekPanelLayout(fraction: Float) {
        val density = resources.displayMetrics.density
        // The edge family keeps the bezel ring and only varies its weight; everything else swaps
        // the ring out for the meter. Thickness is orthogonal to WEAR_PROGRESS_STYLE, so each
        // variant works with all eight ring appearances.
        val edgeStrokeScale = when (seekPanelLayout) {
            "edge_thin" -> 0.5f
            "edge_thick" -> 1.8f
            else -> 1f
        }
        val customMeter = seekPanelLayout !in setOf("edge", "edge_thin", "edge_thick")
        binding.seekBar.edgeStrokeScale = edgeStrokeScale
        binding.seekBar.animate().cancel()
        binding.seekBar.alpha = if (customMeter) 0f else 1f
        binding.textSeekTime.translationY = if (customMeter) -18f * density else 0f
        binding.seekOverlayMeter.apply {
            progress = fraction
            accentColor = binding.seekBar.progressColor
            mode = if (seekPanelLayout == "segments") {
                OverlayProgressMeter.Mode.SEGMENTS
            } else {
                OverlayProgressMeter.Mode.TIMELINE
            }
            visibility = if (customMeter) View.VISIBLE else View.GONE
        }
    }

    /**
     * Shared "plain" / "pill" (glass capsule) / "giant" / expressive, Material and white
     * pill rendering for a centered overlay readout - used by both the
     * seek-time and volume-percentage text views so they always share one visual language.
     *
     * "expressive" mirrors the system media player's tonal-pair look: a light, saturated tint of
     * the album accent as the pill fill with a dark tone of the *same* hue as the text - rather
     * than white-on-dark-glass, so the accent color itself reads as the decoration.
     */
    private fun applyPillReadoutStyle(
            text: TextView,
            style: String,
            content: String,
            accentColor: Int
    ) {
        // One TextView is restyled in place every time the style changes, so any property only some
        // branches set has to be cleared here or it leaks into the next style. Background, colour,
        // size and padding are set by every branch; these two are not.
        text.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        text.letterSpacing = 0f

        when (style) {
            "pill" -> {
                text.textSize = 26f
                text.setTextColor(Color.WHITE)
                text.setBackgroundResource(R.drawable.glass_pill_background)
                val padH = (18 * resources.displayMetrics.density).toInt()
                val padV = (8 * resources.displayMetrics.density).toInt()
                text.setPadding(padH, padV, padH, padV)
                text.text = content
            }
            "glass_white" -> {
                text.textSize = 26f
                text.setTextColor(Color.WHITE)
                text.background = capsule(Color.argb(0x1A, 0xFF, 0xFF, 0xFF))
                val padH = (20 * resources.displayMetrics.density).toInt()
                val padV = (8 * resources.displayMetrics.density).toInt()
                text.setPadding(padH, padV, padH, padV)
                text.text = content
            }
            "translucent_album" -> {
                text.textSize = 26f
                val accent = accentColor
                val tintColor = if (screenFace == "expressive") {
                    PaletteTransforms.tonalSurface(accent, 0.74f, 0.40f, 0.92f)
                } else {
                    accent
                }
                text.setTextColor(Color.WHITE)
                text.background = capsule(ColorUtils.setAlphaComponent(tintColor, 0x4D))
                val padH = (20 * resources.displayMetrics.density).toInt()
                val padV = (8 * resources.displayMetrics.density).toInt()
                text.setPadding(padH, padV, padH, padV)
                text.text = content
            }
            "glow_album" -> {
                text.textSize = 26f
                val accent = accentColor
                val tintColor = if (screenFace == "expressive") {
                    PaletteTransforms.tonalSurface(accent, 0.74f, 0.40f, 0.92f)
                } else {
                    accent
                }
                val glowColor = liftedAccent(tintColor)
                text.setTextColor(glowColor)
                text.background = capsule(
                        Color.TRANSPARENT,
                        (2f * resources.displayMetrics.density).roundToInt(),
                        ColorUtils.setAlphaComponent(glowColor, 0xE0)
                )
                val padH = (20 * resources.displayMetrics.density).toInt()
                val padV = (8 * resources.displayMetrics.density).toInt()
                text.setPadding(padH, padV, padH, padV)
                text.text = content
            }
            "outline" -> {
                text.textSize = 26f
                val accent = accentColor
                text.setTextColor(accent)
                text.background = capsule(
                        Color.TRANSPARENT,
                        (1.5f * resources.displayMetrics.density).roundToInt(),
                        ColorUtils.setAlphaComponent(accent, 0xE0)
                )
                val padH = (20 * resources.displayMetrics.density).toInt()
                val padV = (8 * resources.displayMetrics.density).toInt()
                text.setPadding(padH, padV, padH, padV)
                text.text = content
            }
            "solid_theme" -> {
                text.textSize = 26f
                val accent = defaultSeekBarColor
                val tintColor = if (screenFace == "expressive") {
                    PaletteTransforms.tonalSurface(accent, 0.74f, 0.40f, 0.92f)
                } else {
                    accent
                }
                text.setTextColor(contrastingIconColor(tintColor))
                text.background = capsule(tintColor)
                val padH = (20 * resources.displayMetrics.density).toInt()
                val padV = (8 * resources.displayMetrics.density).toInt()
                text.setPadding(padH, padV, padH, padV)
                text.text = content
            }
            "solid_album" -> {
                text.textSize = 26f
                val accent = accentColor
                val tintColor = if (screenFace == "expressive") {
                    PaletteTransforms.tonalSurface(accent, 0.74f, 0.40f, 0.92f)
                } else {
                    accent
                }
                text.setTextColor(contrastingIconColor(tintColor))
                text.background = capsule(tintColor)
                val padH = (20 * resources.displayMetrics.density).toInt()
                val padV = (8 * resources.displayMetrics.density).toInt()
                text.setPadding(padH, padV, padH, padV)
                text.text = content
            }
            "expressive" -> {
                text.textSize = 26f
                text.setTextColor(PILL_ON_LIGHT)
                text.background = capsule(expressivePillFillColor(accentColor))
                val padH = (20 * resources.displayMetrics.density).toInt()
                val padV = (8 * resources.displayMetrics.density).toInt()
                text.setPadding(padH, padV, padH, padV)
                text.text = content
            }
            "material" -> {
                text.textSize = 26f
                text.setTextColor(PILL_ON_LIGHT)
                text.background = capsule(tonalSurface(accentColor, lightness = 0.92f))
                val padH = (20 * resources.displayMetrics.density).toInt()
                val padV = (8 * resources.displayMetrics.density).toInt()
                text.setPadding(padH, padV, padH, padV)
                text.text = content
            }
            "white" -> {
                text.textSize = 26f
                text.setTextColor(PILL_ON_LIGHT)
                text.background = capsule(Color.WHITE)
                val padH = (20 * resources.displayMetrics.density).toInt()
                val padV = (8 * resources.displayMetrics.density).toInt()
                text.setPadding(padH, padV, padH, padV)
                text.text = content
            }
            "giant" -> {
                text.textSize = 52f
                text.setTextColor(Color.WHITE)
                text.background = null
                text.setPadding(0, 0, 0, 0)
                text.text = content
            }
            // --- Minimal readouts: no filled container, the numerals themselves carry the style.
            "micro" -> {
                // Deliberately the smallest readout offered: on a scrub the ring already shows the
                // position, so the numerals only need to confirm it, not announce it.
                text.textSize = 15f
                text.setTextColor(ColorUtils.setAlphaComponent(Color.WHITE, 0xB3))
                text.letterSpacing = 0.08f
                text.background = null
                text.setPadding(0, 0, 0, 0)
                text.text = content
            }
            "shadow" -> {
                // Chromeless but readable over bright artwork: the drop shadow does the work a
                // capsule would, without covering the art.
                text.textSize = 30f
                text.setTextColor(Color.WHITE)
                text.setShadowLayer(
                        6f * resources.displayMetrics.density, 0f,
                        1.5f * resources.displayMetrics.density,
                        ColorUtils.setAlphaComponent(Color.BLACK, 0xCC))
                text.background = null
                text.setPadding(0, 0, 0, 0)
                text.text = content
            }
            "underline" -> {
                val accent = liftedAccent(accentColor)
                text.textSize = 28f
                text.setTextColor(Color.WHITE)
                text.background = underlineDrawable(
                        accent, (2f * resources.displayMetrics.density).roundToInt())
                val padH = (4 * resources.displayMetrics.density).toInt()
                val padV = (6 * resources.displayMetrics.density).toInt()
                text.setPadding(padH, padV, padH, padV)
                text.text = content
            }
            "hairline" -> {
                text.textSize = 22f
                text.setTextColor(Color.WHITE)
                text.background = capsule(
                        Color.TRANSPARENT,
                        (1f * resources.displayMetrics.density).roundToInt(),
                        ColorUtils.setAlphaComponent(Color.WHITE, 0x66))
                val padH = (20 * resources.displayMetrics.density).toInt()
                val padV = (7 * resources.displayMetrics.density).toInt()
                text.setPadding(padH, padV, padH, padV)
                text.text = content
            }
            "giant_album" -> {
                text.textSize = 52f
                text.setTextColor(liftedAccent(accentColor))
                text.background = null
                text.setPadding(0, 0, 0, 0)
                text.text = content
            }
            // --- Filled counterparts that stay off the album accent entirely, or invert it.
            "mono" -> {
                text.textSize = 26f
                text.setTextColor(Color.WHITE)
                text.background = capsule(MONO_PANEL_SURFACE)
                val padH = (20 * resources.displayMetrics.density).toInt()
                val padV = (8 * resources.displayMetrics.density).toInt()
                text.setPadding(padH, padV, padH, padV)
                text.text = content
            }
            "tonal_dark" -> {
                // The inverse of "material": a dark tonal container with light accent numerals,
                // for users who find the light pill too bright at night.
                val container = tonalSurface(accentColor, lightness = 0.22f)
                text.textSize = 26f
                text.setTextColor(liftedAccent(accentColor))
                text.background = capsule(container)
                val padH = (20 * resources.displayMetrics.density).toInt()
                val padV = (8 * resources.displayMetrics.density).toInt()
                text.setPadding(padH, padV, padH, padV)
                text.text = content
            }
            "terminal" -> {
                // Sharp corners and forced green, matching the "terminal" vocabulary the arc and
                // quick panel already speak - the album accent is ignored on purpose.
                text.textSize = 24f
                text.setTextColor(TERMINAL_GREEN)
                text.letterSpacing = 0.12f
                text.background = capsule(
                        ColorUtils.setAlphaComponent(Color.BLACK, 0xCC),
                        (1f * resources.displayMetrics.density).roundToInt(),
                        ColorUtils.setAlphaComponent(TERMINAL_GREEN, 0x99),
                        radiusPx = 0f)
                val padH = (16 * resources.displayMetrics.density).toInt()
                val padV = (7 * resources.displayMetrics.density).toInt()
                text.setPadding(padH, padV, padH, padV)
                text.text = content
            }
            else -> {
                text.textSize = 30f
                text.setTextColor(Color.WHITE)
                text.background = null
                text.setPadding(0, 0, 0, 0)
                text.text = content
            }
        }
    }

    /** Light, saturated tint of the album accent - the "expressive" pill style's fill. Lightness
     *  is high enough that dark text stays legible regardless of the accent's own hue. */
    private fun expressivePillFillColor(accentColor: Int): Int =
            tonalSurface(accentColor, lightness = 0.82f)

    /**
     * The album accent raised to a lightness that stays readable when it is used as *text* or a
     * hairline rather than as a fill. Dark album art routinely yields an accent near-black, which
     * is invisible on the overlay backdrop; anything already light is passed through untouched.
     */
    private fun liftedAccent(color: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        if (hsl[2] >= 0.4f) return color
        hsl[2] = 0.45f
        return ColorUtils.HSLToColor(hsl)
    }

    /**
     * A rule drawn along the bottom edge of whatever it backs, used by the "underline" readout.
     * A [LayerDrawable] with a bottom-gravity fixed-height layer rather than an inset drawable,
     * because the inset would have to be computed from a view height that isn't known when the
     * background is assigned.
     */
    private fun underlineDrawable(color: Int, thicknessPx: Int): LayerDrawable =
            LayerDrawable(arrayOf<android.graphics.drawable.Drawable>(
                    ColorDrawable(Color.TRANSPARENT),
                    GradientDrawable().apply { setColor(color); cornerRadius = thicknessPx / 2f }
            )).apply {
                setLayerGravity(1, Gravity.BOTTOM)
                setLayerHeight(1, thicknessPx)
            }

    private val rotarySeekCommitRunnable = Runnable {
        // Clear the pending fraction *before* seeking: seekTo() posts the re-anchored position
        // synchronously, and the observer ignores position updates while a scrub is pending.
        val fraction = pendingRotarySeekFraction
        pendingRotarySeekFraction = null
        fraction?.let { viewModel.seekTo(it) }
    }

    /**
     * Rotary seeking fires many events per turn; update the ring instantly but only send the
     * actual seek to the phone once the crown settles, to avoid flooding the Data Layer.
     */
    private fun scheduleRotarySeekCommit(fraction: Float) {
        pendingRotarySeekFraction = fraction
        handler.removeCallbacks(rotarySeekCommitRunnable)
        handler.postDelayed(rotarySeekCommitRunnable, ROTARY_SEEK_COMMIT_DELAY_MS)
    }

    fun buzz() {
        if (!Preferences.getBoolean(preferences, MiscPreferences.HAPTIC_FEEDBACK)) {
            return
        }

        VibratorCompat.vibrate(vibrator, 50)
    }

    /** Expanding-ring flash confirming a center tap the moment the finger lands (see the
     *  center_tap_pulse comment in activity_main.xml for why the action itself can't be
     *  this immediate). */
    private fun pulseCenterTapFeedback() {
        val pulse = binding.centerTapPulse
        pulse.animate().cancel()
        pulse.alpha = 0.9f
        pulse.scaleX = 0.6f
        pulse.scaleY = 0.6f
        pulse.animate()
                .alpha(0f)
                .scaleX(1.25f)
                .scaleY(1.25f)
                .setDuration(300)
                .start()
    }

    // Up/down/left are configurable (same ButtonInfo/action pipeline as a quadrant tap - see
    // SwipeGesture), so any of them can be assigned any phone action. Only swipe-up falls back
    // to a hardcoded default (the actions menu) when nothing's configured, matching its
    // long-standing unconditional behavior; the other two simply do nothing until assigned.
    // Swipe-right has no case here at all - see SwipeGesture's kdoc for why.
    override fun onUpwardsSwipe() {
        if (!gesturesEnabledNow()) return
        Timber.d("UpwardsSwipe")
        buzz()
        revealSwipeTrailIfEnabled()
        if (!viewModel.executeAction(ButtonInfo(false, SwipeGesture.UP, GESTURE_SINGLE_TAP))) {
            startMenu(showCustomList = false)
        }
    }

    override fun onDownwardsSwipe() {
        if (!gesturesEnabledNow()) return
        buzz()
        revealSwipeTrailIfEnabled()
        viewModel.executeAction(ButtonInfo(false, SwipeGesture.DOWN, GESTURE_SINGLE_TAP))
    }

    override fun onSwipeLeft() {
        if (!gesturesEnabledNow()) return
        buzz()
        revealSwipeTrailIfEnabled()
        viewModel.executeAction(ButtonInfo(false, SwipeGesture.LEFT, GESTURE_SINGLE_TAP))
    }

    /** Freezes and fades the path recorded by [onTouchMove] since the finger went down - see
     *  [swipeTrailEnabled]'s doc for the gating rule. Marks the touch sequence so [onTouchUp]
     *  doesn't also [SwipeTrailDrawable.discard] what was just revealed. */
    private fun revealSwipeTrailIfEnabled() {
        if (!swipeTrailEnabled) return
        swipeTrail.reveal()
        swipeTrailRevealedThisTouch = true
    }

    override fun onSingleTap(quadrant: Int) {
        if (!gesturesEnabledNow()) return
        buzz()
        pulseQuadrantIcon(quadrant)

        viewModel.executeAction(ButtonInfo(false, quadrant, GESTURE_SINGLE_TAP))
    }

    override fun onDoubleTap(quadrant: Int) {
        if (!gesturesEnabledNow()) return
        // Single tap vibration has delay, because it needs to wait to see if user presses
        // for the second time.
        // Introduce similar delay to double tap vibration to make it more apparent to the user
        // that he double pressed
        handler.postDelayed(this::buzz, ViewConfiguration.getDoubleTapTimeout().toLong())
        pulseQuadrantIcon(quadrant)
        viewModel.executeAction(ButtonInfo(false, quadrant, GESTURE_DOUBLE_TAP))
    }

    override fun onLongTap(quadrant: Int) {
        if (!gesturesEnabledNow()) return
        buzz()
        pulseQuadrantIcon(quadrant)
        viewModel.executeAction(ButtonInfo(false, quadrant, GESTURE_LONG_TAP))
    }

    /** Only Compose faces need the higher-z-order mirror pulse - Classic's own ripple (drawn by
     *  [binding.fourWayTouch] itself) is already visible since nothing opaque sits above it.
     *  Boosted when "Flash icon on tap" is on - these faces have no persistent quadrant icon to
     *  flash (see [pulseQuadrantIcon]), so the ripple itself has to carry that confirmation.
     *  Also starts recording this touch sequence's path for the swipe trail (see
     *  [swipeTrailEnabled]) - captured here rather than only from [onTouchMove] so the trail
     *  doesn't miss the actual finger-down origin. */
    override fun onTouchDown(x: Float, y: Float) {
        swipeTrailRevealedThisTouch = false
        if (swipeTrailEnabled) {
            swipeTrail.record(x, y)
        }
        if (screenFace in composeFaces) {
            composeTapPulse.press(x, y, boosted = quadrantTapFlashEnabled)
        }
    }

    override fun onTouchMove(x: Float, y: Float) {
        if (swipeTrailEnabled) {
            swipeTrail.record(x, y)
        }
    }

    override fun onTouchUp() {
        composeTapPulse.release()
        // A tap, long-press, or quadrant gesture never reveals the trail - drop what was
        // recorded so it doesn't leave a stray mark for a touch that wasn't a swipe.
        if (!swipeTrailRevealedThisTouch) {
            swipeTrail.discard()
        }
    }

    /** Briefly scales the tapped quadrant's icon up and back, visually tying "I tapped here"
     *  to "that action ran" - the quadrant ripple alone doesn't point at the icon. */
    private fun pulseQuadrantIcon(quadrant: Int) {
        if (screenFace in composeFaces) return // no persistent icon here - see composeTapPulse.
        val icon = when (quadrant) {
            ScreenQuadrant.TOP -> binding.iconTop
            ScreenQuadrant.BOTTOM -> binding.iconBottom
            ScreenQuadrant.LEFT -> binding.iconLeft
            ScreenQuadrant.RIGHT -> binding.iconRight
            else -> return
        }

        // Persistently View.GONE (not just alpha 0) whenever "Show player controls" is off, or
        // for the always-shown-time top icon - the same condition applyScreenThemeNow() uses.
        // Resolved from the *setting*, not the icon's current (possibly already forced-visible)
        // visibility, so a rapid re-tap mid-flash still lands on the same answer both times. The
        // flash toggle overrides this just long enough to confirm which action fired, then hides
        // the icon again.
        val alwaysShowTime = faceBool(MiscPreferences.ALWAYS_SHOW_TIME)
        val persistentlyHidden = !playerControlsVisible || (icon === binding.iconTop && alwaysShowTime)
        if (persistentlyHidden && !quadrantTapFlashEnabled) return

        if (persistentlyHidden) {
            icon.visibility = View.VISIBLE
            icon.alpha = 0f
        }

        // Timed to the quadrant ring pulse (~350ms total, decelerating) so ring + icon read as
        // one gesture; the old 110ms bounce was over before the eye caught it.
        icon.animate().cancel()
        icon.scaleX = 1f
        icon.scaleY = 1f
        icon.animate()
                .scaleX(1.25f)
                .scaleY(1.25f)
                .setDuration(150)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    icon.animate().scaleX(1f).scaleY(1f)
                            .setDuration(250)
                            .setInterpolator(DecelerateInterpolator())
                            .start()
                }
                .start()

        if (quadrantTapFlashEnabled) {
            // A normally-hidden icon flashes down to fully invisible again (then is re-hidden via
            // View.GONE once the fade completes), not the Screen Theme's resting alpha - that
            // only applies to an icon that's actually meant to stay on screen.
            val restingAlpha = if (persistentlyHidden) 0f else screenTheme.tokens.iconAlpha
            flashQuadrantIconAlpha(icon, restingAlpha) {
                if (persistentlyHidden) icon.visibility = View.GONE
            }
        }
    }

    private var quadrantFlashAnimator: AnimatorSet? = null

    /** Flashes [icon] to full opacity, holds briefly, then fades back down to [restingAlpha] -
     *  a separate property from [pulseQuadrantIcon]'s scale bounce above, so the two don't fight
     *  over the same ViewPropertyAnimator. Makes the tap register visibly even when the icon is
     *  normally invisible (Hidden theme, or forced temporarily visible while otherwise GONE),
     *  where there would otherwise be no confirmation at all of which action fired. [onNaturalEnd]
     *  only fires when this flash runs to completion undisturbed - a rapid re-tap cancels it to
     *  start a fresh one instead, and must not also fire the superseded flash's end action (which
     *  could hide the icon while the new flash is still using it). */
    private fun flashQuadrantIconAlpha(
            icon: ImageView, restingAlpha: Float, onNaturalEnd: (() -> Unit)? = null
    ) {
        quadrantFlashAnimator?.cancel()
        val flashIn = ObjectAnimator.ofFloat(icon, View.ALPHA, 1f).apply {
            duration = 100
        }
        val flashOut = ObjectAnimator.ofFloat(icon, View.ALPHA, restingAlpha).apply {
            duration = 250
            startDelay = 240
        }
        val set = AnimatorSet().apply { playTogether(flashIn, flashOut) }
        if (onNaturalEnd != null) {
            set.addListener(object : android.animation.AnimatorListenerAdapter() {
                private var wasCancelled = false
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    wasCancelled = true
                }
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (!wasCancelled) onNaturalEnd()
                }
            })
        }
        quadrantFlashAnimator = set
        set.start()
    }

    private class TimeoutsHandler(val activity: WeakReference<MainActivity>) :
            Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MESSAGE_HIDE_VOLUME -> {
                    activity.get()?.hideOverlay()
                }
                MESSAGE_UPDATE_CLOCK -> {
                    removeMessages(MESSAGE_UPDATE_CLOCK)

                    val activity = activity.get() ?: return

                    activity.updateClock()

                    // Through the face scope, not the raw global: "always show time" is a per-face
                    // setting, and reading it globally here meant the clock could be visible on
                    // this face (scoped on) while the ticker stopped after a minute because the
                    // legacy global said off - a clock frozen at the time it appeared.
                    if (!activity.ambientObserver.isAmbient &&
                            (activity.isQuickActionsPanelShowing() ||
                                    activity.alwaysDisplayClock)
                    ) {
                        sendEmptyMessageDelayed(MESSAGE_UPDATE_CLOCK, 60_000)
                    }
                }
                MESSAGE_DISMISS_NOTIFICATION -> {
                    activity.get()?.hideNotification()
                }
                else -> super.handleMessage(msg)
            }

        }
    }

    override fun getPhoneAppPresenceCapability(): String = CommPaths.PHONE_APP_CAPABILITY
}
