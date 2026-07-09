package com.svartifoss.snfell.watch.view

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.Animatable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.TransitionDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Vibrator
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
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
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.palette.graphics.Palette
import androidx.preference.PreferenceManager
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.wear.input.RemoteInputIntentHelper
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.wearable.input.RotaryEncoderHelper
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.common.CustomLists
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.PremiumStyles
import com.svartifoss.snfell.common.ScreenQuadrant
import com.svartifoss.snfell.common.QuickPanelButtons
import com.svartifoss.snfell.common.ScreenButtons
import com.svartifoss.snfell.common.SwipeGesture
import com.svartifoss.snfell.common.buttonconfig.ButtonInfo
import com.svartifoss.snfell.common.buttonconfig.GESTURE_DOUBLE_TAP
import com.svartifoss.snfell.common.buttonconfig.GESTURE_LONG_TAP
import com.svartifoss.snfell.common.buttonconfig.GESTURE_SINGLE_TAP
import com.svartifoss.snfell.common.buttonconfig.SpecialButtonCodes
import com.svartifoss.snfell.common.view.FourWayTouchLayout
import com.svartifoss.snfell.databinding.ActivityMainBinding
import com.svartifoss.snfell.proto.MusicState
import com.svartifoss.snfell.watch.communication.CustomListWithBitmaps
import com.svartifoss.snfell.watch.communication.UiOpenServiceConnection
import com.svartifoss.snfell.watch.communication.WatchInfoSender
import com.svartifoss.snfell.watch.communication.WatchMusicService
import com.svartifoss.snfell.watch.view.menu.MenuActivity
import com.svartifoss.snfell.watch.view.queue.QueueActivity
import com.svartifoss.snfell.watch.config.ButtonAction
import com.svartifoss.snfell.watch.config.WatchActionConfigProvider
import com.svartifoss.snfell.watch.model.Notification
import com.svartifoss.snfell.watch.theme.WatchTheme
import com.svartifoss.snfell.watch.util.StandardActionTitles
import com.svartifoss.snfell.watch.view.face.ExpressiveFace
import com.svartifoss.snfell.watch.view.face.NowPlayingFaceListener
import com.svartifoss.snfell.watch.view.face.NowPlayingFaceState
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
import kotlin.math.roundToInt
import kotlin.random.Random

@AndroidEntryPoint
class MainActivity : WearCompanionWatchActivity(),
        FourWayTouchLayout.UserActionListener {

    companion object {
        const val EXTRA_OPEN_VOICE_SEARCH = "OpenVoiceSearch"
        private const val KEY_SEARCH_QUERY = "search_query"

        private const val MESSAGE_HIDE_VOLUME = 10
        private const val MESSAGE_UPDATE_CLOCK = 11
        private const val MESSAGE_DISMISS_NOTIFICATION = 12
        private const val REQUEST_CODE_POST_NOTIFICATIONS = 1001

        // Local-only prefs file. The default SharedPreferences can't be used for watch-side UI
        // state: viewModel.preferences swaps it for the phone-synced copy, which would wipe
        // anything written locally on the next sync.
        private const val PREFS_LOCAL_UI = "local_ui_state"
        private const val KEY_GESTURE_HINTS_SHOWN = "gesture_hints_shown"

        private const val ROTARY_SEEK_COMMIT_DELAY_MS = 400L
        private const val OVERLAY_FADE_OUT_MS = 150L
        private const val OVERLAY_FADE_IN_MS = 80L
        private const val ALBUM_ART_CROSSFADE_MS = 300
        private const val MIN_LEGACY_BLUR_DIMENSION_PX = 16
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var vibrator: Vibrator
    private lateinit var ambientObserver: AmbientLifecycleObserver
    private lateinit var stemButtonsManager: StemButtonsManager
    private val handler = TimeoutsHandler(WeakReference(this))

    private var notificationDismissDeadline: Long = Long.MAX_VALUE
    // True from first launch until the user explicitly dismisses the gesture hints overlay -
    // also used to bring the overlay back after an ambient round-trip hides it.
    private var firstRunHintsPending = false
    private var dimAlbumArt: Boolean = false
    private var blurAlbumArtBackground: Boolean = false
    private var albumArtGrayscale: Boolean = false
    private var albumArtHidden: Boolean = false
    private var blurRadiusPx: Float = 35f
    private var overlayBlurRadiusPx: Float = 35f
    private var dimStrengthPercent: Int = 80
    private var volumeBarTimeoutMs: Long = 1000L
    private var rotaryDeadzone: Float = 6f
    private var ambientAlbumArtAlpha: Float = 0.55f
    private var centerLongPressQueueEnabled = false
    private var wearDynamicAccentEnabled = true
    private var albumArtFadeEnabled = true
    private var screenTheme: String = "default"

    /** Selected now-playing face (see [MiscPreferences.WEAR_SCREEN_FACE] and NowPlayingFace.kt):
     *  "classic" is the original View presentation, "expressive" the Compose face. */
    private var screenFace: String = "classic"

    /** How the expressive face exposes drag-to-seek (see [MiscPreferences.WEAR_EXPRESSIVE_SEEK_MODE]):
     *  "central" makes the expressive ring draggable, "edge" keeps the classic bezel seek ring
     *  visible on the expressive face, "none" leaves seeking to the rotary crown. */
    private var expressiveSeekMode: String = "central"

    /** Single state snapshot driving the Compose face. Kept up to date by the same observers
     *  that update the classic views, so switching faces is purely a visibility change. */
    private val faceState = mutableStateOf(NowPlayingFaceState())

    private var paletteGeneration = 0
    private var lastPaletteArt: Bitmap? = null
    private var lastKnownDurationMs: Long = 0L
    private var pendingRotarySeekFraction: Float? = null
    private var latestAlbumArt: Bitmap? = null
    private var currentAccentColor: Int = 0
    private var shuffleEnabled: Boolean = false
    private var repeatMode: Int = 0
    private var liked: Boolean = false

    /** Independent color sources for the artist text and the seek bar - each "neutral" (static
     *  theme accent), "album" (the [currentAccentColor] the icons/buttons already track,
     *  optionally desaturated) or "custom" (a fixed hex color). See [resolveAccentTint]. */
    private var artistColorMode = "album"
    private var artistCustomColor = ""
    private var artistDesaturated = false
    private var progressColorMode = "album"
    private var progressCustomColor = ""
    private var progressDesaturated = false

    /** Synced from the phone (see [MiscPreferences.WEAR_TRACK_TIME_MODE]): "always", "playing",
     *  "paused" or "never". Combined with [isMusicPlaying]/[hasPlaybackPosition] in
     *  [updatePlaybackTimeVisibility] - the single place that decides whether the track time
     *  ("1:23 / 3:45") line shows. */
    private var trackTimeMode: String = "always"
    private var isMusicPlaying: Boolean = false
    private var hasPlaybackPosition: Boolean = false

    private val defaultSeekBarColor by lazy { getColor(R.color.theme_accent) }

    private lateinit var preferences: SharedPreferences

    private val viewModel: MusicViewModel by viewModels()

    private var rotatingInputDisabledUntil = 0L

    /** Whether any mini-button slot currently has an action, so ambient exit knows whether to
     *  bring the row back. */
    private var screenButtonsConfigured = false

    /** Mini-button appearance, all synced from the phone (see MiscPreferences): curvature
     *  style ([applyScreenButtonsCurvature]), pill background and color source
     *  ([styleScreenButtons]). */
    private var screenButtonsCurveStyle = "flat"
    private var screenButtonsBgStyle = "glass"
    private var screenButtonsColorMode = "neutral"
    private var screenButtonsCustomColor = ""
    private var screenButtonsDesaturated = false

    /** What each of the three quick-panel button positions does. An unset [QuickPanelButtons]
     *  slot keeps the position's classic default (like/shuffle/repeat with its state ring);
     *  Like/Shuffle/Repeat assignments keep the ring in whatever slot they land; NullAction
     *  hides the slot; anything else is a plain trigger for that action. */
    private enum class QuickSlotMode { LIKE, SHUFFLE, REPEAT, CUSTOM, HIDDEN }

    private val quickSlotModes = arrayOf(QuickSlotMode.LIKE, QuickSlotMode.SHUFFLE, QuickSlotMode.REPEAT)
    /** Selected quick-actions panel style (see [MiscPreferences.WEAR_QUICK_PANEL_STYLE]):
     *  "glass"/"minimal"/"material"/"tonal". Themes the round slot buttons and the long row. */
    private var quickPanelStyle: String = "glass"

    private var quickPanelSlots: Array<ButtonAction?> = arrayOfNulls(QuickPanelButtons.ALL_SLOTS.size)

    /** The panel's long row (see [QuickPanelButtons.SLOT_LONG]): default Up Next when unset,
     *  hidden on NullAction, otherwise a full-width trigger for the assigned action. */
    private enum class QuickLongMode { UP_NEXT, CUSTOM, HIDDEN }

    private var quickPanelLongSlot: ButtonAction? = null
    private var quickPanelLongMode = QuickLongMode.UP_NEXT

    private val serviceConnection = UiOpenServiceConnection(lifecycle)

    private fun updateFaceState(transform: (NowPlayingFaceState) -> NowPlayingFaceState) {
        faceState.value = transform(faceState.value)
    }

    /** Face events route into the exact same pipelines the classic face's inputs use, so both
     *  faces behave identically (haptics, optimistic state, quick panel, queue long-press). */
    private val expressiveFaceListener = object : NowPlayingFaceListener {
        override fun onPlayPauseTap() {
            buzz()
            viewModel.togglePlayPause()
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
            if (!centerLongPressQueueEnabled) {
                return
            }
            buzz()
            startActivity(Intent(this@MainActivity, QueueActivity::class.java))
        }

        override fun onSkipPreviousTap() {
            buzz()
            viewModel.skipPrevious()
        }

        override fun onSkipNextTap() {
            buzz()
            viewModel.skipNext()
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

        override fun onSeek(fraction: Float) {
            viewModel.seekTo(fraction)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.fourWayTouch.listener = this
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
            ExpressiveFace(state = faceState.value, listener = expressiveFaceListener)
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
                viewModel.togglePlayPause()
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
                if (!centerLongPressQueueEnabled) {
                    return
                }
                buzz()
                startActivity(Intent(this@MainActivity, QueueActivity::class.java))
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

        // Tapping outside the panel, or swiping down on it, both dismiss it - the system back
        // gesture (left-edge swipe) closes the whole app instead of just this overlay on some
        // Wear OS builds, so a swipe-down is offered as a reliable alternative.
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
        binding.overlayBlurImage.setOnTouchListener { _, event ->
            overlayDismissGestureDetector.onTouchEvent(event)
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
        binding.textArtist.enableSmartWordSizing(maxSizeSp = 16f, minSizeSp = 9f)
        binding.textTitle.enableSmartWordSizing(maxSizeSp = 46f, minSizeSp = 25f)
        // Same idea for the quick-actions panel's copy of the title/artist - without this a long
        // title just sat there clipped instead of shrinking a bit and then scrolling.
        binding.quickActionPanelTitle.enableSmartWordSizing(maxSizeSp = 18f, minSizeSp = 15f)
        binding.quickActionPanelArtist.enableSmartWordSizing(maxSizeSp = 13f, minSizeSp = 11f)

        binding.notificationPopup.clickableFrame.setOnClickListener { onNotificationTapped() }

        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        ambientObserver = AmbientLifecycleObserver(this, ambientCallback)
        lifecycle.addObserver(ambientObserver)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        maybeRequestNotificationPermission()

        viewModel.albumArt.observe(this, albumArtObserver)
        viewModel.currentButtonConfig.observe(this, buttonConfigObserver)
        viewModel.preferences.observe(this, preferencesChangeObserver)
        viewModel.volume.observe(this, phoneVolumeListener)
        viewModel.popupVolumeBar.observe(this, volumeBarPopupListener)
        viewModel.openActionsMenu.observe(this, openActionsMenuListener)
        viewModel.openPlaybackQueueScreen.observe(this, openPlaybackQueueScreenListener)
        viewModel.openVoiceSearch.observe(this, openVoiceSearchListener)
        viewModel.closeApp.observe(this, closeAppListener)
        viewModel.notification.observe(this, notificationObserver)
        viewModel.customList.observe(this, customListListener)
        viewModel.playbackPosition.observe(this, playbackPositionObserver)


        stemButtonsManager = StemButtonsManager(WatchInfoSender.getAvailableButtonsOnWatch(this), stemButtonListener, lifecycleScope)

        onBackPressedDispatcher.addCallback(this, backButtonOverrideCallback)
        // Registered after backButtonOverrideCallback so it takes priority while enabled - the
        // back gesture should close the quick-actions panel instead of exiting the app.
        onBackPressedDispatcher.addCallback(this, quickActionsPanelBackCallback)
        // Last registered = highest priority: while the hints overlay is up, back dismisses it.
        onBackPressedDispatcher.addCallback(this, firstRunHintsBackCallback)

        setupFirstRunHints()

        handleVoiceSearchIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleVoiceSearchIntent(intent)
    }

    /** Set by IdleMessageListener when the phone asks to open search (e.g. Search picked from
     *  the actions menu, which always executes phone-side). */
    private fun handleVoiceSearchIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_VOICE_SEARCH, false) == true) {
            intent.removeExtra(EXTRA_OPEN_VOICE_SEARCH)
            openVoiceSearchInput()
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

        if (Preferences.getBoolean(preferences, MiscPreferences.ALWAYS_SHOW_TIME)) {
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
        super.onDestroy()

        viewModel.musicState.removeObserver(musicStateObserver)
    }

    /** Follows the system 12/24h setting, but never appends AM/PM - the suffix just adds
     *  clutter without information on a watch-sized clock. */
    private fun updateClock() {
        val pattern = if (android.text.format.DateFormat.is24HourFormat(this)) "HH:mm" else "h:mm"
        binding.ambientClock.text =
                java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
                        .format(java.util.Date())
    }

    private val musicStateObserver = Observer<Resource<MusicState>?> {
        Timber.d("GUI Music State %s %s", it?.status, it?.data)
        if (it == null || it.status == Resource.Status.LOADING) {
            binding.loadingIndicator.visibility = View.VISIBLE
            return@Observer
        }

        binding.loadingIndicator.visibility = View.GONE

        isMusicPlaying = it.status == Resource.Status.SUCCESS && it.data?.playing == true
        updatePlaybackTimeVisibility()
        syncUpNextEqualizerAnimation()

        // "Idle": connected fine, but there is no track at all (as opposed to a *paused* track,
        // which keeps the normal title + "Playback Stopped" presentation). Shows the branded
        // equalizer + hint instead of a bare status line on an empty screen.
        val idle = it.status == Resource.Status.SUCCESS &&
                (it.data == null || (it.data?.playing != true && it.data?.title.isNullOrBlank()))

        if (it.status == Resource.Status.SUCCESS && it.data != null && !idle) {
            if ((it.data as MusicState).playing) {
                // Restores the dynamic (palette-extracted) color after a stopped/error message
                // may have forced it to plain white below.
                binding.textArtist.setTextColor(resolvedArtistTextColor())
                binding.textArtist.text = it.data?.artist
            } else {
                setStatusMessageOnArtistLine(getString(R.string.playback_stopped))
            }

            binding.textTitle.text = it.data?.title
            updateRecentsLabel((it.data as MusicState).title)

            shuffleEnabled = it.data?.shuffleEnabled == true
            repeatMode = it.data?.repeatMode ?: 0
            liked = it.data?.liked == true
            updateQuickActionButtonStates()
        } else if (it.status == Resource.Status.ERROR) {
            setStatusMessageOnArtistLine(getString(R.string.error))
            binding.textTitle.text = it.message
            updateRecentsLabel(null)

            val errorData = it.errorData
            if (errorData is GooglePlayServicesRepairableException) {
                GoogleApiAvailability.getInstance().getErrorDialog(this, errorData.connectionStatusCode, 1)?.show()
            }
        } else {
            binding.textArtist.text = ""
            binding.textTitle.text = ""
            updateRecentsLabel(null)
        }

        setIdleStateVisible(idle)

        binding.textArtist.visibility =
                if (binding.textArtist.text.isEmpty()) View.GONE else View.VISIBLE

        // The classic text views above stay the single source of truth (including the
        // status-message-in-white override on the artist line) - the face just mirrors them.
        updateFaceState { face ->
            face.copy(
                    title = binding.textTitle.text?.toString().orEmpty(),
                    artist = binding.textArtist.text?.toString().orEmpty(),
                    artistColor = binding.textArtist.currentTextColor,
                    playing = isMusicPlaying,
                    idle = idle
            )
        }
    }

    /**
     * Shows/hides the idle ("nothing playing") group and runs its equalizer animation only
     * while it is actually on screen and the display is interactive - ambient stops it (see
     * ambientCallback), both to respect the low-power mode and because AVDs don't animate
     * there anyway.
     */
    private fun setIdleStateVisible(visible: Boolean) {
        binding.idleStateGroup.visibility = if (visible) View.VISIBLE else View.GONE
        val animation = binding.idleStateIcon.drawable as? Animatable ?: return
        if (visible && !ambientObserver.isAmbient) {
            if (!animation.isRunning) animation.start()
        } else {
            animation.stop()
        }
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
        }
        applyBlurredAlbumArt(bitmap)
        updateDynamicAccentFromArt(bitmap)
    }

    private fun updateDynamicAccentFromArt(art: Bitmap?) {
        if (!wearDynamicAccentEnabled) {
            lastPaletteArt = art
            applyAccentColor(defaultSeekBarColor)
            return
        }
        if (art == null) {
            lastPaletteArt = null
            applyAccentColor(defaultSeekBarColor)
            return
        }
        if (art === lastPaletteArt) {
            return
        }
        lastPaletteArt = art

        val generation = ++paletteGeneration
        Palette.from(art).generate { palette ->
            if (generation != paletteGeneration || art !== lastPaletteArt) {
                return@generate
            }
            val color = palette?.let { p ->
                listOf(
                        p.getVibrantSwatch(),
                        p.getMutedSwatch(),
                        p.getLightVibrantSwatch(),
                        p.getDarkVibrantSwatch(),
                        p.getLightMutedSwatch(),
                        p.getDarkMutedSwatch(),
                        p.dominantSwatch
                ).firstNotNullOfOrNull { swatch -> swatch?.rgb }
            } ?: defaultSeekBarColor

            applyAccentColor(color)
        }
    }

    /** parseColor throws StringIndexOutOfBounds (not IllegalArgument) on an empty string - and
     *  empty is every custom-color preference's "not picked yet" default. */
    private fun parseHexColorOrNull(hex: String): Int? = if (hex.isBlank()) null else try {
        Color.parseColor(hex)
    } catch (ignored: Exception) {
        null
    }

    /**
     * Resolves one of the independent artist/progress-bar color sources against the current
     * album accent: "neutral" always uses the static [defaultSeekBarColor] (ignores the album
     * entirely, even if dynamic accent is on elsewhere), "album" uses [albumColor] (optionally
     * desaturated toward gray), "custom" uses the picked hex (falling back to [albumColor] if
     * unset/invalid).
     */
    private fun resolveAccentTint(mode: String, customColor: String, desaturated: Boolean, albumColor: Int): Int {
        return when (mode) {
            "album" -> if (desaturated) ColorUtils.blendARGB(albumColor, Color.GRAY, 0.45f) else albumColor
            "custom" -> parseHexColorOrNull(customColor) ?: albumColor
            else -> defaultSeekBarColor
        }
    }

    private fun resolvedArtistTextColor(): Int =
            WatchTheme.accentForText(resolveAccentTint(artistColorMode, artistCustomColor, artistDesaturated, currentAccentColor))

    private fun applyAccentColor(color: Int) {
        currentAccentColor = color
        binding.seekBar.progressColor = resolveAccentTint(progressColorMode, progressCustomColor, progressDesaturated, color)
        binding.volumeBar.progressColor = color
        binding.fourWayTouch.setTapFeedbackColor(color)
        // Artist name uses the same dark-theme-adapted (lightened) accent as the queue's now-playing row.
        binding.textArtist.setTextColor(resolvedArtistTextColor())

        if (screenButtonsColorMode == "album") {
            styleScreenButtons()
        }

        if (isQuickActionsPanelShowing()) {
            binding.quickActionPanelArtist.setTextColor(binding.textArtist.currentTextColor)
            updateQuickActionButtonStates()
        }

        updateFaceState {
            it.copy(
                    accentColor = color,
                    progressColor = binding.seekBar.progressColor,
                    artistColor = binding.textArtist.currentTextColor
            )
        }
    }

    /**
     * "Playback Stopped"/"Error" reuse the artist line, but they're status messages, not an
     * artist name - they should always read in plain white, never the dynamic accent color.
     */
    private fun setStatusMessageOnArtistLine(message: String) {
        binding.textArtist.setTextColor(getColor(android.R.color.white))
        binding.textArtist.text = message
    }

    private fun fadeToAlbumArt(bitmap: Bitmap?) {
        // Cross-fade instead of fade-out-then-in: the old art stays visible underneath while
        // the new one fades in over it, so the artwork never blinks away mid-transition.
        binding.albumArt.animate().cancel()
        binding.albumArt.alpha = 1f

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

        val transition = TransitionDrawable(arrayOf(oldDrawable, newDrawable))
        binding.albumArt.setImageDrawable(transition)
        transition.startTransition(ALBUM_ART_CROSSFADE_MS)
    }

    /** Grayscale for the "Black & white" album art styles - a plain saturation-0 color filter
     *  works on every API level and stacks with the blur paths untouched. */
    private val grayscaleFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })

    /** Renders the main background album art — sharp cover, full-screen blur, black & white
     *  variants, or hidden entirely, per user setting. */
    private fun applyMainAlbumArtDisplay(source: Bitmap?, forceBlur: Boolean) {
        binding.albumArt.colorFilter = if (albumArtGrayscale) grayscaleFilter else null

        if (source == null || albumArtHidden) {
            binding.albumArt.setImageBitmap(null)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                binding.albumArt.setRenderEffect(null)
            }
            return
        }

        if (!forceBlur) {
            binding.albumArt.setImageBitmap(source)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                binding.albumArt.setRenderEffect(null)
            }
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            binding.albumArt.setImageBitmap(source)
            binding.albumArt.setRenderEffect(
                    RenderEffect.createBlurEffect(blurRadiusPx, blurRadiusPx, Shader.TileMode.CLAMP)
            )
        } else {
            binding.albumArt.setImageBitmap(createBlurredBitmapLegacy(source))
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            binding.overlayBlurImage.setImageBitmap(source)
            binding.overlayBlurImage.setRenderEffect(
                    RenderEffect.createBlurEffect(overlayBlurRadiusPx, overlayBlurRadiusPx, Shader.TileMode.CLAMP)
            )
        } else {
            binding.overlayBlurImage.setImageBitmap(createBlurredBitmapLegacy(source))
        }
    }

    /**
     * Approximates a blur without RenderEffect by halving the bitmap's size repeatedly (each
     * halving step is naturally box-filtered by the bilinear downscale) before scaling back up
     * in one go - a single aggressive downscale jump looks blocky/pixelated instead, since
     * bilinear interpolation between too few source pixels can't approximate a smooth blur.
     */
    /** Blurs (or un-blurs) the main album art view — used for ambient mode and the blur style setting. */
    private fun setAmbientAlbumArtBlur(enabled: Boolean) {
        applyMainAlbumArtDisplay(latestAlbumArt, forceBlur = enabled || blurAlbumArtBackground)
    }

    private fun createBlurredBitmapLegacy(source: Bitmap): Bitmap {
        var current = source
        while (current.width > MIN_LEGACY_BLUR_DIMENSION_PX && current.height > MIN_LEGACY_BLUR_DIMENSION_PX) {
            val next = Bitmap.createScaledBitmap(current, current.width / 2, current.height / 2, true)
            if (current !== source) {
                current.recycle()
            }
            current = next
        }

        val result = Bitmap.createScaledBitmap(current, source.width, source.height, true)
        if (current !== source) {
            current.recycle()
        }
        return result
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

        // These icons are the only visible hint of what each quadrant does - name them after
        // their configured action so the main screen isn't silent under a screen reader.
        binding.iconTop.contentDescription = StandardActionTitles.get(this, topSingle?.key)
        binding.iconBottom.contentDescription = StandardActionTitles.get(this, bottomSingle?.key)
        binding.iconLeft.contentDescription = StandardActionTitles.get(this, leftSingle?.key)
        binding.iconRight.contentDescription = StandardActionTitles.get(this, rightSingle?.key)

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
        val defaultModes = arrayOf(QuickSlotMode.LIKE, QuickSlotMode.SHUFFLE, QuickSlotMode.REPEAT)
        val defaultIcons = intArrayOf(
                R.drawable.action_like_outline,
                com.svartifoss.snfell.common.R.drawable.action_shuffle,
                com.svartifoss.snfell.common.R.drawable.action_repeat
        )

        for ((index, panelButton) in quickPanelViews().withIndex()) {
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
                QuickSlotMode.HIDDEN -> panelButton.visibility = View.GONE
                QuickSlotMode.LIKE -> {
                    panelButton.visibility = View.VISIBLE
                    panelButton.setImageResource(R.drawable.action_like_outline)
                }
                QuickSlotMode.SHUFFLE -> {
                    panelButton.visibility = View.VISIBLE
                    panelButton.setImageResource(com.svartifoss.snfell.common.R.drawable.action_shuffle)
                }
                QuickSlotMode.REPEAT -> {
                    panelButton.visibility = View.VISIBLE
                    panelButton.setImageResource(com.svartifoss.snfell.common.R.drawable.action_repeat)
                }
                QuickSlotMode.CUSTOM -> {
                    panelButton.visibility = View.VISIBLE
                    if (assigned?.icon != null) {
                        panelButton.setImageDrawable(assigned.icon)
                    } else {
                        panelButton.setImageResource(defaultIcons[index])
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
                syncUpNextEqualizerAnimation()
                binding.quickActionUpNextLabel.setText(R.string.quick_action_up_next)
                viewModel.customList.value?.let { updateUpNextPreview(it) }
            }
            QuickLongMode.CUSTOM -> {
                binding.quickActionUpNext.visibility = View.VISIBLE
                val icon = quickPanelLongSlot?.icon
                if (icon != null) {
                    binding.quickActionUpNextIcon.setImageDrawable(icon)
                } else {
                    binding.quickActionUpNextIcon.setImageResource(R.drawable.ic_queue_music)
                }
                binding.quickActionUpNextLabel.text =
                        StandardActionTitles.get(this, longKey) ?: getString(R.string.quick_action_up_next)
                binding.quickActionUpNextTrack.visibility = View.GONE
            }
            QuickLongMode.HIDDEN -> binding.quickActionUpNext.visibility = View.GONE
        }

        updateQuickActionButtonStates()
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

    private fun onQuickPanelButtonClicked(index: Int) {
        buzz()
        when (quickSlotModes[index]) {
            QuickSlotMode.LIKE -> viewModel.sendQuickAction("like")
            QuickSlotMode.SHUFFLE -> viewModel.sendQuickAction("shuffle")
            QuickSlotMode.REPEAT -> viewModel.sendQuickAction("repeat")
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

    /** Shows each configured mini-button slot with its action's icon and hides the rest; the
     *  whole row collapses when nothing is configured, so the screen looks exactly like before
     *  this feature existed. A slot with only a long-press action still shows that icon. */
    private fun updateScreenButtons(config: WatchActionConfigProvider) {
        val visibleButtons = ArrayList<ImageView>(3)

        for ((slotCode, slotView) in screenButtonViews()) {
            val tapAction = config.getAction(ButtonInfo(false, slotCode, GESTURE_SINGLE_TAP))
            val longAction = config.getAction(ButtonInfo(false, slotCode, GESTURE_LONG_TAP))
            val displayedAction = tapAction ?: longAction

            if (displayedAction == null) {
                slotView.visibility = View.GONE
            } else {
                slotView.visibility = View.VISIBLE
                slotView.setImageDrawable(displayedAction.icon)
                slotView.contentDescription = StandardActionTitles.get(this, displayedAction.key)
                visibleButtons.add(slotView)
            }
        }

        // Re-space the survivors so 1/2/3 visible buttons always sit balanced regardless of
        // *which* slots are configured: a lone button gets no margins, a pair gets a wider
        // gap than the full row of three.
        val edgePx = ((if (visibleButtons.size == 2) 8 else 5) * resources.displayMetrics.density).toInt()
        for ((index, button) in visibleButtons.withIndex()) {
            val params = button.layoutParams as ViewGroup.MarginLayoutParams
            params.marginStart = if (index == 0) 0 else edgePx
            params.marginEnd = if (index == visibleButtons.lastIndex) 0 else edgePx
            button.layoutParams = params
        }

        screenButtonsConfigured = visibleButtons.isNotEmpty()
        binding.screenButtonsRow.visibility =
                if (screenButtonsConfigured && !ambientObserver.isAmbient) View.VISIBLE else View.GONE
        styleScreenButtons()
        binding.screenButtonsRow.post { repositionScreenButtonsRow() }

        // The expressive face's default queue/volume/overflow trio yields to configured mini
        // buttons - they occupy the same part of the screen.
        updateFaceState { it.copy(showDefaultBottomPills = !screenButtonsConfigured) }
    }

    /** Smart vertical placement for the mini buttons: the row rests at the user's preferred
     *  bottom offset, but when the centered text block grows (2-line titles) it slides further
     *  down to keep clear of the track time - and if there's still not enough room, shrinks
     *  slightly (anchored at its bottom edge) to absorb the difference. Recomputed whenever
     *  the text block, the bottom quadrant icon, or the row itself changes layout. */
    private fun repositionScreenButtonsRow() {
        val row = binding.screenButtonsRow
        val content = binding.contentFrame
        if (row.visibility != View.VISIBLE || row.height == 0 || content.height == 0) {
            return
        }

        val density = resources.displayMetrics.density
        val gapPx = 6 * density

        val contentLoc = IntArray(2).also { content.getLocationInWindow(it) }
        val viewLoc = IntArray(2)

        // Bottom of the lowest visible line of the centered text block.
        val lowestText = listOf(binding.textPlaybackTime, binding.textTitle, binding.textArtist)
                .firstOrNull { it.visibility == View.VISIBLE && it.height > 0 }
        val textBottom = lowestText?.let {
            it.getLocationInWindow(viewLoc)
            (viewLoc[1] + it.height - contentLoc[1]).toFloat()
        } ?: content.height / 2f

        // Lowest allowed row bottom: clear the bottom quadrant icon when it's shown, else just
        // keep a small inset so the pills stay inside a round screen's circle.
        val maxBottom = if (binding.iconBottom.visibility == View.VISIBLE) {
            binding.iconBottom.getLocationInWindow(viewLoc)
            viewLoc[1] - contentLoc[1] - gapPx
        } else {
            content.height - 14 * density
        }

        // Where the static layout put the row (bottom margin = the offset preference).
        val baseBottom = (content.height -
                (row.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin).toFloat()

        var bottom = minOf(baseBottom, maxBottom)
        val requiredTop = textBottom + gapPx
        if (bottom - row.height < requiredTop) {
            bottom = minOf(maxBottom, requiredTop + row.height)
        }

        // Whatever overlap is left after sliding down gets absorbed by shrinking, anchored at
        // the bottom edge so the freed space all goes toward the text.
        val overlap = requiredTop - (bottom - row.height)
        val scale = if (overlap > 0) {
            ((row.height - overlap) / row.height).coerceIn(0.75f, 1f)
        } else {
            1f
        }

        row.translationY = bottom - baseBottom
        row.pivotX = row.width / 2f
        row.pivotY = row.height.toFloat()
        row.scaleX = scale
        row.scaleY = scale

        applyScreenButtonsCurvature()
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
    private fun applyScreenButtonsCurvature() {
        val row = binding.screenButtonsRow
        val buttons = screenButtonViews().map { it.second }

        val tiltFraction = when (screenButtonsCurveStyle) {
            "arc" -> 0f
            "curved_soft" -> 0.5f
            "curved" -> 1f
            else -> null
        }

        if (tiltFraction == null || !resources.configuration.isScreenRound) {
            for (button in buttons) {
                button.translationY = 0f
                button.rotation = 0f
            }
            if (row.paddingTop != 0) {
                row.setPadding(0, 0, 0, 0)
            }
            return
        }

        val content = binding.contentFrame
        val radius = minOf(content.width, content.height) / 2f
        if (radius <= 0 || row.width == 0) {
            return
        }

        row.clipChildren = false
        row.clipToPadding = false

        var maxRise = 0f
        for (button in buttons) {
            if (button.visibility != View.VISIBLE || button.width == 0) {
                continue
            }

            val buttonCenterX = row.left + button.left + button.width / 2f
            val dx = buttonCenterX - content.width / 2f
            val clampedDx = dx.coerceIn(-radius + 1f, radius - 1f)

            val rise = radius - kotlin.math.sqrt(radius * radius - clampedDx * clampedDx)
            button.translationY = -rise
            // Tangent to the circle: the outer end of each side pill tips up along the bezel.
            button.rotation = tiltFraction *
                    -Math.toDegrees(kotlin.math.asin((clampedDx / radius).toDouble())).toFloat()

            maxRise = maxOf(maxRise, rise)
        }

        // Rotated pills stick out past their own height a little; pad enough for both.
        val neededPadding = (maxRise + 10 * resources.displayMetrics.density).toInt()
        if (row.paddingTop != neededPadding) {
            row.setPadding(0, neededPadding, 0, 0)
        }
    }

    /**
     * Applies the user's mini-button appearance: pill background style (glass / solid /
     * transparent) and color source (neutral theme glass, live album accent - optionally
     * desaturated - or a fixed custom color). Re-run whenever the preferences or the dynamic
     * accent change, and to restore a button after the press-feedback highlight.
     */
    private fun styleScreenButtons() {
        for ((_, button) in screenButtonViews()) {
            styleScreenButton(button)
        }
    }

    private fun styleScreenButton(button: ImageView) {
        if (screenButtonsBgStyle == "transparent") {
            button.background = null
            return
        }

        val tintColor = when (screenButtonsColorMode) {
            "album" -> {
                val accent = if (currentAccentColor != 0) currentAccentColor else null
                if (accent != null && screenButtonsDesaturated) {
                    ColorUtils.blendARGB(accent, Color.GRAY, 0.45f)
                } else {
                    accent
                }
            }
            "custom" -> parseHexColorOrNull(screenButtonsCustomColor)
            else -> null
        }

        if (tintColor == null) {
            // Neutral: the shared glass pill, or an opaque dark pill for the solid style.
            button.background = if (screenButtonsBgStyle == "solid") {
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 999f
                    setColor(Color.argb(230, 22, 24, 26))
                }
            } else {
                AppCompatResources.getDrawable(this, R.drawable.glass_pill_background)
            }
        } else {
            val alpha = if (screenButtonsBgStyle == "solid") 230 else 96
            button.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 999f
                setColor(ColorUtils.setAlphaComponent(tintColor, alpha))
            }
        }
    }

    private val preferencesChangeObserver = Observer<SharedPreferences?> {
        if (it == null) {
            return@Observer
        }

        preferences = it

        stemButtonsManager.enableDoublePressInAmbient = !Preferences.getBoolean(
                preferences,
                MiscPreferences.DISABLE_PHYSICAL_DOUBLE_CLICK_IN_AMBIENT
        )

        if (!ambientObserver.isAmbient) {
            val alwaysDisplayClock =
                    Preferences.getBoolean(preferences, MiscPreferences.ALWAYS_SHOW_TIME)

            if (alwaysDisplayClock) {
                binding.ambientClock.visibility = View.VISIBLE
                binding.iconTop.visibility = View.GONE
                handler.sendEmptyMessage(MESSAGE_UPDATE_CLOCK)
            } else {
                binding.iconTop.visibility = View.VISIBLE
                binding.ambientClock.visibility = View.GONE
            }
        }

        dimAlbumArt = Preferences.getBoolean(
            preferences,
            MiscPreferences.DIM_ALBUM_ART
        )
        val albumArtStyle = Preferences.getString(preferences, MiscPreferences.ALBUM_ART_STYLE)
        blurAlbumArtBackground = albumArtStyle == "blur" || albumArtStyle == "blur_bw"
        albumArtGrayscale = albumArtStyle == "bw" || albumArtStyle == "blur_bw"
        albumArtHidden = albumArtStyle == "hidden"
        blurRadiusPx = Preferences.getInt(preferences, MiscPreferences.ALBUM_ART_BLUR_RADIUS)
                .coerceIn(10, 80).toFloat()
        dimStrengthPercent = Preferences.getInt(preferences, MiscPreferences.ALBUM_ART_DIM_STRENGTH)
                .coerceIn(0, 100)
        volumeBarTimeoutMs = Preferences.getInt(preferences, MiscPreferences.VOLUME_OVERLAY_TIMEOUT)
                .coerceIn(300, 5000).toLong()
        rotaryDeadzone = Preferences.getInt(preferences, MiscPreferences.ROTARY_DEADZONE)
                .coerceIn(0, 30).toFloat()
        ambientAlbumArtAlpha = Preferences.getInt(preferences, MiscPreferences.AMBIENT_ALBUM_ART_OPACITY)
                .coerceIn(20, 100) / 100f

        val screenButtonsOffsetPx = (Preferences.getInt(preferences, MiscPreferences.WEAR_SCREEN_BUTTONS_OFFSET)
                .coerceIn(0, 120) * resources.displayMetrics.density).toInt()
        val rowParams = binding.screenButtonsRow.layoutParams as ViewGroup.MarginLayoutParams
        if (rowParams.bottomMargin != screenButtonsOffsetPx) {
            rowParams.bottomMargin = screenButtonsOffsetPx
            binding.screenButtonsRow.layoutParams = rowParams
        }

        screenButtonsCurveStyle = Preferences.getString(preferences, MiscPreferences.WEAR_SCREEN_BUTTONS_CURVE_STYLE)
        screenButtonsBgStyle = Preferences.getString(preferences, MiscPreferences.WEAR_SCREEN_BUTTONS_BG)
        screenButtonsColorMode = Preferences.getString(preferences, MiscPreferences.WEAR_SCREEN_BUTTONS_COLOR_MODE)
        screenButtonsCustomColor = Preferences.getString(preferences, MiscPreferences.WEAR_SCREEN_BUTTONS_CUSTOM_COLOR)
        screenButtonsDesaturated = Preferences.getBoolean(preferences, MiscPreferences.WEAR_SCREEN_BUTTONS_DESATURATED)
        styleScreenButtons()
        binding.screenButtonsRow.post { repositionScreenButtonsRow() }

        overlayBlurRadiusPx = Preferences.getInt(preferences, MiscPreferences.WEAR_OVERLAY_BLUR_RADIUS)
                .coerceIn(5, 120).toFloat()

        // Defensive: the phone already blocks selecting a premium style without the unlock, but a
        // watch that never lost a stale value (e.g. after a refund) should still fall back cleanly.
        val premiumUnlocked = Preferences.getBoolean(preferences, MiscPreferences.PREMIUM_UNLOCKED)
        binding.volumeBar.barStyle = VolumeStyle.fromPref(
                PremiumStyles.sanitize(
                        Preferences.getString(preferences, MiscPreferences.WEAR_VOLUME_STYLE), premiumUnlocked))
        quickPanelStyle = PremiumStyles.sanitize(
                Preferences.getString(preferences, MiscPreferences.WEAR_QUICK_PANEL_STYLE), premiumUnlocked)

        centerLongPressQueueEnabled = Preferences.getBoolean(
                preferences, MiscPreferences.WEAR_CENTER_LONG_PRESS_QUEUE
        )
        wearDynamicAccentEnabled = Preferences.getBoolean(
                preferences, MiscPreferences.WEAR_DYNAMIC_ACCENT
        )
        albumArtFadeEnabled = Preferences.getBoolean(
                preferences, MiscPreferences.WEAR_ALBUM_ART_FADE
        )
        screenTheme = Preferences.getString(preferences, MiscPreferences.WEAR_SCREEN_THEME)
        screenFace = Preferences.getString(preferences, MiscPreferences.WEAR_SCREEN_FACE)
        expressiveSeekMode = Preferences.getString(preferences, MiscPreferences.WEAR_EXPRESSIVE_SEEK_MODE)
        updateFaceState { it.copy(centralSeekEnabled = expressiveSeekMode == "central") }

        trackTimeMode = Preferences.getString(preferences, MiscPreferences.WEAR_TRACK_TIME_MODE)
        updatePlaybackTimeVisibility()

        val titleTextMode = when (Preferences.getString(preferences, MiscPreferences.WEAR_TITLE_TEXT_MODE)) {
            "marquee" -> TextSizingMode.MARQUEE
            "wrap" -> TextSizingMode.WRAP
            "shrink" -> TextSizingMode.SHRINK
            else -> TextSizingMode.SMART
        }
        binding.textTitle.setSizingMode(titleTextMode)

        artistColorMode = Preferences.getString(preferences, MiscPreferences.WEAR_ARTIST_COLOR_MODE)
        artistCustomColor = Preferences.getString(preferences, MiscPreferences.WEAR_ARTIST_CUSTOM_COLOR)
        artistDesaturated = Preferences.getBoolean(preferences, MiscPreferences.WEAR_ARTIST_DESATURATED)
        progressColorMode = Preferences.getString(preferences, MiscPreferences.WEAR_PROGRESS_COLOR_MODE)
        progressCustomColor = Preferences.getString(preferences, MiscPreferences.WEAR_PROGRESS_CUSTOM_COLOR)
        progressDesaturated = Preferences.getBoolean(preferences, MiscPreferences.WEAR_PROGRESS_DESATURATED)
        // Re-applies with the (possibly unchanged) album color so a mode/custom-color/desaturate
        // change re-tints the artist text and seek bar immediately, without needing new art.
        applyAccentColor(currentAccentColor)

        applyAlbumArtScrim()
        applyScreenFace()

        if (!wearDynamicAccentEnabled) {
            updateDynamicAccentFromArt(latestAlbumArt)
        }

        if (!ambientObserver.isAmbient) {
            binding.albumArtScrim.visibility = if (dimAlbumArt) View.VISIBLE else View.INVISIBLE
            applyMainAlbumArtDisplay(latestAlbumArt, forceBlur = blurAlbumArtBackground)
        }
    }

    private fun applyAlbumArtScrim() {
        var strength = dimStrengthPercent.coerceIn(0, 100)
        if (screenTheme == "cinema") {
            strength = (strength + 20).coerceAtMost(100)
        }
        val bottomAlpha = strength * 255 / 100
        binding.albumArtScrim.background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.TRANSPARENT, Color.argb(bottomAlpha, 0, 0, 0))
        )
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

        val expressive = screenFace == "expressive"
        binding.expressiveFace.visibility = if (expressive) View.VISIBLE else View.GONE
        binding.classicTextBlock.visibility = if (expressive) View.GONE else View.VISIBLE
        // The bezel seek ring belongs to the classic face; on the expressive face it appears only
        // when the user picks the "edge" seek mode (it draws on top of the ComposeView - see the
        // z-order in activity_main.xml - so it stays touchable). "central"/"none" hide it.
        val showEdgeSeekRing = !expressive || expressiveSeekMode == "edge"
        binding.seekBar.visibility = if (showEdgeSeekRing) View.VISIBLE else View.GONE
        binding.centerTapZone.visibility = if (expressive) View.GONE else View.VISIBLE
        applyScreenTheme()
    }

    private fun applyScreenTheme() {
        if (ambientObserver.isAmbient) {
            return
        }

        val icons = listOf(binding.iconTop, binding.iconBottom, binding.iconLeft, binding.iconRight)
        val iconSizeDefault = resources.getDimensionPixelSize(R.dimen.music_screen_icon_size)
        val iconSizeCompact = (18 * resources.displayMetrics.density).toInt()
        val alwaysShowTime = Preferences.getBoolean(preferences, MiscPreferences.ALWAYS_SHOW_TIME)

        fun setIconSize(icon: ImageView, size: Int) {
            val params = icon.layoutParams
            params.width = size
            params.height = size
            icon.layoutParams = params
        }

        if (screenFace == "expressive") {
            // The expressive face draws its own transport buttons and curved clock - the
            // quadrant hint icons and the always-on clock belong to the classic face only.
            // (Quadrant taps themselves keep working; only their icons are hidden.) The
            // cinema theme's extra scrim still applies on top of any face.
            icons.forEach { it.visibility = View.GONE }
            binding.ambientClock.visibility = View.GONE
            if (screenTheme == "cinema" && dimAlbumArt) {
                binding.albumArtScrim.visibility = View.VISIBLE
            }
            return
        }

        when (screenTheme) {
            "minimal" -> {
                icons.forEach { it.visibility = View.GONE }
                if (alwaysShowTime) {
                    binding.ambientClock.visibility = View.VISIBLE
                }
            }
            "compact" -> {
                icons.forEach { icon ->
                    val hideTop = icon === binding.iconTop && alwaysShowTime
                    icon.visibility = if (hideTop) View.GONE else View.VISIBLE
                    icon.alpha = 1f
                    setIconSize(icon, iconSizeCompact)
                }
            }
            "cinema" -> {
                icons.forEach { icon ->
                    val hideTop = icon === binding.iconTop && alwaysShowTime
                    icon.visibility = if (hideTop) View.GONE else View.VISIBLE
                    icon.alpha = 0.35f
                    setIconSize(icon, iconSizeDefault)
                }
                if (dimAlbumArt) {
                    binding.albumArtScrim.visibility = View.VISIBLE
                }
            }
            else -> {
                icons.forEach { icon ->
                    val hideTop = icon === binding.iconTop && alwaysShowTime
                    icon.visibility = if (hideTop) View.GONE else View.VISIBLE
                    icon.alpha = 1f
                    setIconSize(icon, iconSizeDefault)
                }
            }
        }
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
            hasPlaybackPosition = false
            updatePlaybackTimeVisibility()
            updateFaceState { it.copy(seekable = false) }
            return@Observer
        }

        lastKnownDurationMs = position.durationMs
        binding.seekBar.seekable = position.seekable
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

    private fun startMenu(showCustomList: Boolean) {
        menuLauncher.launch(
                Intent(this, MenuActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra(MenuActivity.EXTRA_SHOW_CUSTOM_LIST, showCustomList)
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
            dismissFirstRunHints()
        }
    }

    /** Shows the one-time gesture cheat sheet on the very first launch. Dismissing it (tap
     *  anywhere, the pill button, or back) writes the flag so it never comes back. */
    private fun setupFirstRunHints() {
        if (localUiPrefs().getBoolean(KEY_GESTURE_HINTS_SHOWN, false)) {
            return
        }

        firstRunHintsPending = true
        firstRunHintsBackCallback.isEnabled = true

        val hints = binding.firstRunHints.root
        hints.setOnClickListener { dismissFirstRunHints() }
        binding.firstRunHints.firstRunHintsDismiss.setOnClickListener { dismissFirstRunHints() }

        // Slight delay so the sheet fades in over a settled screen instead of fighting the
        // activity's own entrance transition. It blocks input from the moment it turns VISIBLE.
        hints.alpha = 0f
        hints.visibility = View.VISIBLE
        hints.animate().alpha(1f).setStartDelay(400).setDuration(300).start()
    }

    private fun dismissFirstRunHints() {
        if (!firstRunHintsPending) {
            return
        }
        firstRunHintsPending = false
        firstRunHintsBackCallback.isEnabled = false
        localUiPrefs().edit().putBoolean(KEY_GESTURE_HINTS_SHOWN, true).apply()

        val hints = binding.firstRunHints.root
        hints.animate().cancel()
        hints.animate()
                .alpha(0f)
                .setStartDelay(0)
                .setDuration(200)
                .withEndAction { hints.visibility = View.GONE }
                .start()
    }

    private fun localUiPrefs(): SharedPreferences =
            getSharedPreferences(PREFS_LOCAL_UI, Context.MODE_PRIVATE)

    private val ambientCallback = object : AmbientLifecycleObserver.AmbientLifecycleCallback {
                override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
                    stemButtonsManager.onEnterAmbient()
                    binding.ambientClock.visibility = View.VISIBLE

                    handler.removeMessages(MESSAGE_UPDATE_CLOCK)
                    updateClock()
                    hideNotificationIfOverdue()

                    binding.iconTop.visibility = View.GONE
                    binding.iconBottom.visibility = View.GONE
                    binding.iconLeft.visibility = View.GONE
                    binding.iconRight.visibility = View.GONE
                    binding.screenButtonsRow.visibility = View.GONE

                    // Ambient always shows the classic presentation regardless of the selected
                    // face - it is burn-in-audited (outlined text, pixel jiggle) and animation
                    // free. onExitAmbient re-applies the user's face.
                    binding.expressiveFace.visibility = View.GONE
                    binding.classicTextBlock.visibility = View.VISIBLE

                    binding.albumArt.alpha = ambientAlbumArtAlpha
                    setAmbientAlbumArtBlur(true)
                    binding.albumArtScrim.visibility = View.GONE
                    binding.volumeBar.visibility = View.GONE
                    binding.seekBar.visibility = View.GONE
                    binding.overlayBlurImage.visibility = View.GONE
                    binding.overlayDim.visibility = View.GONE
                    binding.textVolumePercent.visibility = View.GONE
                    binding.textSeekTime.visibility = View.GONE
                    binding.volumeIconTop.visibility = View.GONE
                    binding.volumeIconBottom.visibility = View.GONE
                    binding.quickActionsPanel.visibility = View.GONE
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

                    // Artist stays plain bold (no outline effect) - only the title mimics the
                    // stock look of an outlined, "etched" headline.
                    binding.textTitle.displayTextOutline = true
                    binding.textPlaybackTime.displayTextOutline = true
                }

                override fun onUpdateAmbient() {
                    updateClock()
                    viewModel.updateTimers()
                    hideNotificationIfOverdue()

                    binding.contentFrame.translationX = Random.nextInt(-5, 6).toFloat()
                    binding.contentFrame.translationY = Random.nextInt(-5, 6).toFloat()
                }

                override fun onExitAmbient() {
                    stemButtonsManager.onExitAmbient()

                    if (Preferences.getBoolean(preferences, MiscPreferences.ALWAYS_SHOW_TIME)) {
                        handler.sendEmptyMessage(MESSAGE_UPDATE_CLOCK)
                    } else {
                        binding.ambientClock.visibility = View.GONE
                    }

                    applyScreenTheme()

                    binding.screenButtonsRow.visibility =
                            if (screenButtonsConfigured) View.VISIBLE else View.GONE

                    binding.albumArt.alpha = 1f
                    setAmbientAlbumArtBlur(false)
                    binding.albumArtScrim.visibility = if (dimAlbumArt) View.VISIBLE else View.INVISIBLE
                    binding.seekBar.visibility = View.VISIBLE
                    binding.seekBar.alpha = 1f
                    binding.volumeBar.visibility = View.GONE
                    binding.volumeBar.alpha = 1f
                    binding.overlayBlurImage.visibility = View.GONE
                    binding.overlayDim.visibility = View.GONE
                    binding.textVolumePercent.visibility = View.GONE
                    binding.textSeekTime.visibility = View.GONE

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

                    // Last, so it wins over the classic-view restores above (e.g. the seek ring
                    // set VISIBLE earlier) when the expressive face is selected.
                    applyScreenFace()
                }

            }

    override fun onGenericMotionEvent(ev: android.view.MotionEvent): Boolean {
        // The quick-actions panel sits on top of the volume bar's overlay - rotating the crown
        // while it's open shouldn't pop the volume UI over the like/shuffle/repeat buttons.
        if (isQuickActionsPanelShowing()) {
            return false
        }

        if (rotatingInputDisabledUntil > System.currentTimeMillis()) {
            return false
        }

        if (ev.action == android.view.MotionEvent.ACTION_SCROLL && RotaryEncoderHelper.isFromRotaryEncoder(
                        ev
                )
        ) {
            val delta =
                    -RotaryEncoderHelper.getRotaryAxisValue(ev) * RotaryEncoderHelper.getScaledScrollFactor(
                            this
                    )

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

            // Optional: scrub the playback timeline with the crown instead of changing volume.
            if (Preferences.getBoolean(preferences, MiscPreferences.ROTARY_SEEK) &&
                    binding.seekBar.seekable) {
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
    private fun showOverlay() {
        if (binding.overlayBlurImage.visibility != View.VISIBLE) {
            binding.overlayBlurImage.alpha = 0f
            binding.overlayBlurImage.visibility = View.VISIBLE
            binding.overlayBlurImage.animate().cancel()
            binding.overlayBlurImage.animate().alpha(1f).setDuration(OVERLAY_FADE_IN_MS).start()

            binding.overlayDim.alpha = 0f
            binding.overlayDim.visibility = View.VISIBLE
            binding.overlayDim.animate().cancel()
            binding.overlayDim.animate().alpha(1f).setDuration(OVERLAY_FADE_IN_MS).start()
        }
    }

    private fun hideOverlay() {
        binding.overlayBlurImage.animate().cancel()
        binding.overlayBlurImage.animate()
                .alpha(0f)
                .setDuration(OVERLAY_FADE_OUT_MS)
                .withEndAction { binding.overlayBlurImage.visibility = View.GONE }
                .start()

        binding.overlayDim.animate().cancel()
        binding.overlayDim.animate()
                .alpha(0f)
                .setDuration(OVERLAY_FADE_OUT_MS)
                .withEndAction { binding.overlayDim.visibility = View.GONE }
                .start()

        binding.volumeBar.animate().cancel()
        binding.volumeBar.animate()
                .alpha(0f)
                .setDuration(OVERLAY_FADE_OUT_MS)
                .withEndAction { binding.volumeBar.visibility = View.GONE }
                .start()

        binding.seekBar.animate().cancel()
        binding.seekBar.animate().alpha(1f).setDuration(OVERLAY_FADE_OUT_MS).start()

        binding.textVolumePercent.visibility = View.GONE
        binding.textSeekTime.visibility = View.GONE
        binding.volumeIconTop.visibility = View.GONE
        binding.volumeIconBottom.visibility = View.GONE
        binding.quickActionsPanel.visibility = View.GONE
        quickActionsPanelBackCallback.isEnabled = false

        handler.removeMessages(MESSAGE_HIDE_VOLUME)
    }

    /** Opened by double-tapping center_tap_zone - like/shuffle/repeat shortcuts plus a way into
     *  the queue, on top of the same blur/dim scrim the volume and seek previews use. Stays open
     *  until the user taps outside it, taps Up Next, or presses back - it does not auto-hide. */
    private fun showQuickActionsPanel() {
        showOverlay()

        binding.seekBar.animate().cancel()
        binding.seekBar.animate().alpha(0f).setDuration(OVERLAY_FADE_IN_MS).start()

        binding.textVolumePercent.visibility = View.GONE
        binding.textSeekTime.visibility = View.GONE
        binding.quickActionsPanel.visibility = View.VISIBLE
        quickActionsPanelBackCallback.isEnabled = true

        // All four panel elements are user-configurable slots (see configureQuickPanelButtons);
        // hidden ones collapse and the remaining elements re-center on their own.
        configureQuickPanelButtons()
        binding.quickActionUpNext.background = quickPanelRowBackground()
        // The Up Next row is the one panel element with a coloured surface behind its own text, so
        // its label/subtitle/icon follow the same per-style tint as the round buttons (dark on
        // light, green on terminal, accent on neon, white otherwise).
        val upNextTint = quickPanelInactiveTint()
        binding.quickActionUpNextLabel.setTextColor(upNextTint)
        binding.quickActionUpNextTrack.setTextColor(ColorUtils.setAlphaComponent(upNextTint, 0xB3))
        binding.quickActionUpNextIcon.setColorFilter(upNextTint)

        binding.quickActionPanelTitle.text = binding.textTitle.text
        binding.quickActionPanelArtist.text = binding.textArtist.text
        binding.quickActionPanelArtist.setTextColor(binding.textArtist.currentTextColor)
        binding.quickActionPanelArtist.visibility =
                if (binding.quickActionPanelArtist.text.isNullOrEmpty()) View.GONE else View.VISIBLE

        updateQuickActionButtonStates()

        // Show whatever was cached from a previous fetch immediately, then ask the phone for a
        // fresh queue snapshot in the background - customListListener() will update the preview
        // text in place without yanking the user into the full drawer while this panel is open.
        viewModel.customList.value?.let { updateUpNextPreview(it) }
        viewModel.openPlaybackQueue()
    }

    private fun isQuickActionsPanelShowing() = binding.quickActionsPanel.visibility == View.VISIBLE

    // Same stadium/capsule shape as glass_pill_background.xml (the inactive state) - this used
    // to be a plain oval, which made the active button look like a different shape from the
    // other two instead of just a different color.
    private fun accentCircleDrawable(): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 999f
        setColor(currentAccentColor)
    }

    /** Material Design 2 surface grey shared by the material quick-panel chrome. */
    private val materialSurfaceColor = 0xFF2A2A2A.toInt()
    private val LIGHT_PANEL_SURFACE = 0xFFECECEC.toInt()
    private val LIGHT_PANEL_ON = 0xFF111111.toInt()
    private val MONO_PANEL_SURFACE = 0xFF262626.toInt()
    private val MONO_PANEL_ACTIVE = 0xFFE0E0E0.toInt()
    private val TERMINAL_GREEN = 0xFF33FF66.toInt()

    /** The album accent's complementary hue (used by the duotone quick-panel style). */
    private fun complementary(accent: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(accent, hsl)
        hsl[0] = (hsl[0] + 180f) % 360f
        return ColorUtils.HSLToColor(hsl)
    }

    /** A dark, accent-tinted surface for the tonal/gradient quick-panel chrome (saturation clamped
     *  so the white icons/text keep enough contrast). */
    private fun tonalSurface(accent: Int, lightness: Float = 0.28f): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(accent, hsl)
        hsl[1] = hsl[1].coerceIn(0.25f, 0.60f)
        hsl[2] = lightness
        return ColorUtils.HSLToColor(hsl)
    }

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

    /** Inactive background of a round quick-panel slot button, per [quickPanelStyle]. The active
     *  (accent-filled) look stays shared across styles. */
    private fun inactiveQuickButtonBackground(): android.graphics.drawable.Drawable {
        val hairline = (1.5f * resources.displayMetrics.density).toInt()
        return when (quickPanelStyle) {
            "minimal" -> capsule(Color.TRANSPARENT, hairline, 0x66FFFFFF)
            "material" -> capsule(materialSurfaceColor)
            "tonal" -> capsule(tonalSurface(currentAccentColor))
            "neon" -> capsule(Color.TRANSPARENT, (2f * resources.displayMetrics.density).toInt(), currentAccentColor)
            "light" -> capsule(LIGHT_PANEL_SURFACE)
            "gradient" -> gradientCapsule(tonalSurface(currentAccentColor, 0.34f), tonalSurface(currentAccentColor, 0.16f))
            "mono" -> capsule(MONO_PANEL_SURFACE)
            "outline" -> capsule(Color.TRANSPARENT, (3f * resources.displayMetrics.density).toInt(), Color.WHITE)
            "duotone" -> capsule(tonalSurface(complementary(currentAccentColor)))
            "contrast" -> capsule(Color.BLACK, (2f * resources.displayMetrics.density).toInt(), Color.WHITE)
            "terminal" -> capsule(Color.TRANSPARENT, hairline, TERMINAL_GREEN, radiusPx = 0f)
            "frost" -> capsule(0x33FFFFFF)
            else -> AppCompatResources.getDrawable(this, R.drawable.glass_pill_background)!!
        }
    }

    /** Background of the full-width Up Next / long-slot row, per [quickPanelStyle]. */
    private fun quickPanelRowBackground(): android.graphics.drawable.Drawable {
        val d = resources.displayMetrics.density
        val hairline = (1.5f * d).toInt()
        return when (quickPanelStyle) {
            "minimal" -> capsule(Color.TRANSPARENT, hairline, 0x66FFFFFF, radiusPx = 24f * d)
            "material" -> capsule(materialSurfaceColor, radiusPx = 16f * d)
            "tonal" -> capsule(tonalSurface(currentAccentColor), radiusPx = 28f * d)
            "neon" -> capsule(Color.TRANSPARENT, (2f * d).toInt(), currentAccentColor, radiusPx = 22f * d)
            "light" -> capsule(LIGHT_PANEL_SURFACE, radiusPx = 22f * d)
            "gradient" -> gradientCapsule(tonalSurface(currentAccentColor, 0.34f), tonalSurface(currentAccentColor, 0.16f), radiusPx = 24f * d)
            "mono" -> capsule(MONO_PANEL_SURFACE, radiusPx = 18f * d)
            "outline" -> capsule(Color.TRANSPARENT, (3f * d).toInt(), Color.WHITE, radiusPx = 20f * d)
            "duotone" -> capsule(tonalSurface(complementary(currentAccentColor)), radiusPx = 24f * d)
            "contrast" -> capsule(Color.BLACK, (2f * d).toInt(), Color.WHITE, radiusPx = 16f * d)
            "terminal" -> capsule(Color.TRANSPARENT, hairline, TERMINAL_GREEN, radiusPx = 0f)
            "frost" -> capsule(0x33FFFFFF, radiusPx = 22f * d)
            else -> AppCompatResources.getDrawable(this, R.drawable.up_next_pill_background)!!
        }
    }

    /** Icon/text colour for the inactive quick-panel chrome, per [quickPanelStyle]. */
    private fun quickPanelInactiveTint(): Int = when (quickPanelStyle) {
        "light" -> LIGHT_PANEL_ON
        "neon" -> currentAccentColor
        "terminal" -> TERMINAL_GREEN
        else -> Color.WHITE
    }

    /** Fill colour of an *active* quick-panel button, per [quickPanelStyle]. Most styles use the
     *  album accent; the monochrome styles keep their own palette so the accent never leaks in. */
    private fun activeQuickFillColor(): Int = when (quickPanelStyle) {
        "contrast" -> Color.WHITE
        "terminal" -> TERMINAL_GREEN
        "mono" -> MONO_PANEL_ACTIVE
        else -> currentAccentColor
    }

    private fun activeQuickButtonBackground(): android.graphics.drawable.Drawable =
            capsule(activeQuickFillColor(), radiusPx = if (quickPanelStyle == "terminal") 0f else 999f)

    /** White icons can disappear against a light album-art accent color, so the icon itself
     *  flips to black/white depending on how light or dark [backgroundColor] is. */
    private fun contrastingIconColor(backgroundColor: Int): Int =
            if (ColorUtils.calculateLuminance(backgroundColor) > 0.5) Color.BLACK else Color.WHITE

    private fun setQuickActionButtonActive(view: ImageView, active: Boolean) {
        if (active) {
            view.background = activeQuickButtonBackground()
            view.setColorFilter(contrastingIconColor(activeQuickFillColor()))
        } else {
            view.background = inactiveQuickButtonBackground()
            view.setColorFilter(quickPanelInactiveTint())
        }
    }

    /** Reflects confirmed shuffle/repeat/like state (from the phone) on the panel buttons that
     *  currently host those toggles - wherever the user placed them. Shuffle/repeat are
     *  reliable (real MediaSession state); "liked" is a best-effort guess since there's no
     *  generic cross-app API for it - see LikeAction.isCurrentlyLiked(). Custom-action slots
     *  have no state and stay in the inactive glass look. */
    private fun updateQuickActionButtonStates() {
        for ((index, panelButton) in quickPanelViews().withIndex()) {
            when (quickSlotModes[index]) {
                QuickSlotMode.LIKE -> setQuickActionButtonActive(panelButton, liked)
                QuickSlotMode.SHUFFLE -> setQuickActionButtonActive(panelButton, shuffleEnabled)
                QuickSlotMode.REPEAT -> setQuickActionButtonActive(panelButton, repeatMode != 0)
                QuickSlotMode.CUSTOM -> setQuickActionButtonActive(panelButton, false)
                QuickSlotMode.HIDDEN -> Unit
            }
        }
    }

    private val quickActionPressFeedback = View.OnTouchListener { v, event ->
        val imageView = v as ImageView
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                imageView.background = accentCircleDrawable()
                imageView.setColorFilter(contrastingIconColor(currentAccentColor))
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
        // The preview only exists in the classic Up Next mode - a custom long-row action shows
        // its own title instead of queue data.
        if (quickPanelLongMode != QuickLongMode.UP_NEXT) {
            return
        }

        // History (the fallback shown when the playing app exposes no real queue) is backward
        // looking - there's no "next" track to preview in that case.
        val nextTrack = if (data.listId == CustomLists.PLAYLIST) {
            // The queue list is the full queue with activeEntryId marking the current track -
            // "Up Next" is the entry AFTER it. Taking items.first() showed whatever happened to
            // sit at the top of the queue (usually the current or even an older track). Without
            // an active id (some apps never set activeQueueItemId), fall back to the first entry.
            val items = data.items
            val activeIndex = data.activeEntryId
                    ?.let { id -> items.indexOfFirst { item -> item.listItem.entryId == id } }
                    ?: -1
            if (activeIndex >= 0) {
                items.getOrNull(activeIndex + 1)?.listItem?.entryTitle
            } else {
                items.firstOrNull()?.listItem?.entryTitle
            }
        } else {
            null
        }

        binding.quickActionUpNextTrack.apply {
            if (nextTrack.isNullOrEmpty()) {
                visibility = View.GONE
            } else {
                visibility = View.VISIBLE
                text = nextTrack
            }
        }
    }

    private fun showVolumeBar() {
        showOverlay()

        if (binding.volumeBar.visibility != View.VISIBLE) {
            binding.volumeBar.alpha = 0f
            binding.volumeBar.visibility = View.VISIBLE
            binding.volumeBar.animate().cancel()
            binding.volumeBar.animate().alpha(1f).setDuration(OVERLAY_FADE_OUT_MS).start()
        }

        // The two rings share the same accent color and would otherwise both be visible at
        // once now that they're drawn on top of the blur overlay - only one should show at a time.
        binding.seekBar.animate().cancel()
        binding.seekBar.animate().alpha(0f).setDuration(OVERLAY_FADE_IN_MS).start()

        binding.textSeekTime.visibility = View.GONE
        binding.textVolumePercent.visibility = View.VISIBLE
        binding.textVolumePercent.text = getString(
                R.string.volume_percent_format,
                (binding.volumeBar.volume * 100).roundToInt()
        )
        binding.volumeIconTop.visibility = View.VISIBLE
        binding.volumeIconBottom.visibility = View.VISIBLE

        handler.removeMessages(MESSAGE_HIDE_VOLUME)
        handler.sendEmptyMessageDelayed(MESSAGE_HIDE_VOLUME, volumeBarTimeoutMs)
    }

    private fun showSeekOverlay(fraction: Float) {
        showOverlay()

        binding.textVolumePercent.visibility = View.GONE
        binding.textSeekTime.visibility = View.VISIBLE
        binding.textSeekTime.text = formatPlaybackTime((fraction * lastKnownDurationMs).toLong())

        // Auto-hide the seek overlay just like the volume overlay does.
        handler.removeMessages(MESSAGE_HIDE_VOLUME)
        handler.sendEmptyMessageDelayed(MESSAGE_HIDE_VOLUME, volumeBarTimeoutMs)
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
        Timber.d("UpwardsSwipe")
        buzz()
        if (!viewModel.executeAction(ButtonInfo(false, SwipeGesture.UP, GESTURE_SINGLE_TAP))) {
            startMenu(showCustomList = false)
        }
    }

    override fun onDownwardsSwipe() {
        buzz()
        viewModel.executeAction(ButtonInfo(false, SwipeGesture.DOWN, GESTURE_SINGLE_TAP))
    }

    override fun onSwipeLeft() {
        buzz()
        viewModel.executeAction(ButtonInfo(false, SwipeGesture.LEFT, GESTURE_SINGLE_TAP))
    }

    override fun onSingleTap(quadrant: Int) {
        buzz()
        pulseQuadrantIcon(quadrant)

        viewModel.executeAction(ButtonInfo(false, quadrant, GESTURE_SINGLE_TAP))
    }

    override fun onDoubleTap(quadrant: Int) {
        // Single tap vibration has delay, because it needs to wait to see if user presses
        // for the second time.
        // Introduce similar delay to double tap vibration to make it more apparent to the user
        // that he double pressed
        handler.postDelayed(this::buzz, ViewConfiguration.getDoubleTapTimeout().toLong())
        pulseQuadrantIcon(quadrant)
        viewModel.executeAction(ButtonInfo(false, quadrant, GESTURE_DOUBLE_TAP))
    }

    override fun onLongTap(quadrant: Int) {
        buzz()
        pulseQuadrantIcon(quadrant)
        viewModel.executeAction(ButtonInfo(false, quadrant, GESTURE_LONG_TAP))
    }

    /** Briefly scales the tapped quadrant's icon up and back, visually tying "I tapped here"
     *  to "that action ran" - the quadrant ripple alone doesn't point at the icon. */
    private fun pulseQuadrantIcon(quadrant: Int) {
        val icon = when (quadrant) {
            ScreenQuadrant.TOP -> binding.iconTop
            ScreenQuadrant.BOTTOM -> binding.iconBottom
            ScreenQuadrant.LEFT -> binding.iconLeft
            ScreenQuadrant.RIGHT -> binding.iconRight
            else -> return
        }
        if (icon.visibility != View.VISIBLE) {
            return
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

                    if (!activity.ambientObserver.isAmbient &&
                            Preferences.getBoolean(
                                    activity.preferences,
                                    MiscPreferences.ALWAYS_SHOW_TIME
                            )
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
