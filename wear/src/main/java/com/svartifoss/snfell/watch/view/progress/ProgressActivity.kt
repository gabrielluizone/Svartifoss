package com.svartifoss.snfell.watch.view.progress

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.preference.PreferenceManager
import com.svartifoss.snfell.common.FaceScopedPreferences
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.watch.view.panel.PanelAppearanceResolver
import com.svartifoss.snfell.watch.view.panel.PanelSurface
import com.svartifoss.snfell.watch.view.panel.rememberPanelPalette
import com.svartifoss.snfell.watch.communication.UiOpenServiceConnection
import com.svartifoss.snfell.watch.communication.WatchMusicService
import com.svartifoss.snfell.watch.theme.LocalWatchUiFontFamily
import com.svartifoss.snfell.watch.theme.watchUiFontFamily
import com.svartifoss.snfell.watch.theme.watchUiTypeface
import com.svartifoss.snfell.R
import com.svartifoss.snfell.watch.util.WatchLanguage
import com.google.android.wearable.input.RotaryEncoderHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay

/** How often the displayed position is re-read from `PlaybackClock` while playing - a readout,
 *  not a stopwatch, so this can be far coarser than the millisecond ticker `MetadataFace` uses. */
private const val POSITION_REFRESH_MS = 500L

/** Same base scale `MainActivity`'s crown-to-seek mapping uses at default sensitivity, and the
 *  same settle delay before one turn becomes one seek. */
private const val ROTARY_SEEK_SCALE = 0.0011f
private const val ROTARY_SEEK_COMMIT_DELAY_MS = 400L

/**
 * Full-screen playback-progress control: seek ring and symmetric 5/10/30-second skip controls,
 * reachable by assigning "Open progress screen" to a button, gesture or quick-panel slot.
 */
@AndroidEntryPoint
class ProgressActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(WatchLanguage.attach(newBase))
    }

    private val viewModel: ProgressViewModel by viewModels()
    @Volatile private var finishCalled = false
    private val serviceConnection = UiOpenServiceConnection(lifecycle)

    private val handler = Handler(Looper.getMainLooper())

    /**
     * Crown scrubbing. The ring on this screen is display-only (see [ProgressScreen]), so the
     * crown is what replaces dragging it - and it is the more precise of the two on a wrist
     * anyway.
     *
     * The target is accumulated here rather than read back off the state: while music plays the
     * position keeps arriving between detents, so using the live position as the base would make
     * each turn snap back and fight the ticker. `MainActivity`'s rotary seek carries the same
     * pending-target field for exactly that reason, and commits on the same delay so a turn sends
     * one seek rather than one per detent.
     */
    private var pendingSeekFraction: Float? = null

    /** Compose state, not a plain field: the readout has to follow the crown as it turns, and a
     *  field would only be picked up on the next 500ms position tick - a scrub that lags half a
     *  second behind the wrist reads as the crown not working. */
    private val previewSeekFraction = mutableStateOf<Float?>(null)

    private val commitSeekRunnable = Runnable {
        // Cleared before seeking: seekToFraction re-anchors the clock synchronously, and the live
        // readout must stop preferring the pending target from that moment on.
        val fraction = pendingSeekFraction
        pendingSeekFraction = null
        previewSeekFraction.value = null
        fraction?.let { viewModel.seekToFraction(it) }
    }

    override fun onGenericMotionEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_SCROLL && RotaryEncoderHelper.isFromRotaryEncoder(ev)) {
            val duration = viewModel.state.value?.durationMs ?: 0L
            if (duration <= 0L) return true
            val delta = -RotaryEncoderHelper.getRotaryAxisValue(ev) *
                    RotaryEncoderHelper.getScaledScrollFactor(this)
            val base = pendingSeekFraction
                    ?: (viewModel.livePositionMs().toFloat() / duration).coerceIn(0f, 1f)
            val next = (base + delta * ROTARY_SEEK_SCALE).coerceIn(0f, 1f)
            pendingSeekFraction = next
            previewSeekFraction.value = next
            handler.removeCallbacks(commitSeekRunnable)
            handler.postDelayed(commitSeekRunnable, ROTARY_SEEK_COMMIT_DELAY_MS)
            return true
        }
        return super.onGenericMotionEvent(ev)
    }

    override fun onDestroy() {
        handler.removeCallbacks(commitSeekRunnable)
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        bindService(Intent(this, WatchMusicService::class.java), serviceConnection, BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        unbindService(serviceConnection)
    }

    private fun safeFinish() {
        if (!finishCalled) {
            finishCalled = true
            window.decorView.visibility = View.INVISIBLE
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle(R.string.progress_screen_title)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        // See VolumeActivity for why the panel settings are read through the resolved appearance
        // context rather than as flat preferences.
        val appearanceContext = PanelAppearanceResolver.appearanceContext(prefs)
        val themeAccent = getColor(R.color.theme_accent)
        val accentSource = PanelAppearanceResolver.accentSource(prefs, appearanceContext)
        // See VolumeActivity: the elapsed/total readout is a View and needs the Typeface twin of
        // the Compose chrome's font family.
        val uiTypeface = watchUiTypeface(this, prefs)

        setContent {
            val state by viewModel.state.observeAsState(ProgressUiState())
            val albumArt by viewModel.albumArt.observeAsState()
            var livePositionMs by remember { mutableLongStateOf(state.positionMs) }

            var cancelArmed by remember { mutableStateOf(false) }
            // A finger on the ring; the crown's own scrub is tracked separately by
            // pendingSeekFraction, and unlike a drag it does want the ring to follow it.
            var ringDragging by remember { mutableStateOf(false) }

            // Seeded from AlbumPaletteCache, so a cover the player has already extracted
            // is painted in the album's colours on the first frame - this screen used to
            // open on the fallback accent and snap over. See rememberPanelPalette.
            val palette = rememberPanelPalette(
                    prefs = prefs,
                    appearanceContext = appearanceContext,
                    surface = PanelSurface.SEEK,
                    albumArt = albumArt,
                    accentSource = accentSource,
                    themeAccent = themeAccent)
            val appearance = palette.appearance
            val backdrop = palette.backdrop

            // Re-anchors on every real sample (including a seek's optimistic echo once the phone
            // confirms it) and only runs while playing - a paused position is not moving, so
            // there is nothing to extrapolate, the same rule PlaybackClock itself follows.
            // A crown scrub in flight wins over both: the whole point of the pending target is
            // that the readout shows where the turn is heading, not where playback still is.
            val scrub by previewSeekFraction
            LaunchedEffect(state.positionMs, state.playing) {
                livePositionMs = state.positionMs
                if (!state.playing) return@LaunchedEffect
                while (true) {
                    delay(POSITION_REFRESH_MS)
                    livePositionMs = viewModel.livePositionMs()
                }
            }

            // Two positions, not one. The readout follows whatever is being chosen - a crown turn
            // or a finger on the ring - while the ring itself keeps being fed the *real* playback
            // position, because that is what its live marker draws. Feeding it the preview instead
            // made the marker chase the finger, which is the one thing it must never do: it exists
            // to say where the track is while the finger is somewhere else.
            val scrubPositionMs = scrub?.let { (it * state.durationMs).toLong() }
            val readoutPositionMs = scrubPositionMs ?: livePositionMs
            val ringPositionMs = if (ringDragging) livePositionMs else readoutPositionMs

            CompositionLocalProvider(
                    LocalWatchUiFontFamily provides watchUiFontFamily(prefs)) {
                ProgressScreen(
                        state = state,
                        livePositionMs = readoutPositionMs,
                        ringPositionMs = ringPositionMs,
                        appearance = appearance,
                        albumArt = albumArt,
                        backdrop = backdrop,
                        showBackdrop = palette.isResolved,
                        screenFace = appearanceContext.baseFace,
                        themeAccentColor = themeAccent,
                        uiTypeface = uiTypeface,
                        // A drag on the ring previews locally and commits on release; releasing in
                        // the middle of the screen cancels, exactly as on the player.
                        onSeekPreview = {
                            // A finger on the ring supersedes a crown turn that has not committed
                            // yet - otherwise the pending target would land moments later and undo
                            // the drag the user had just finished.
                            pendingSeekFraction = null
                            handler.removeCallbacks(commitSeekRunnable)
                            ringDragging = true
                            previewSeekFraction.value = it
                        },
                        onSeekFinished = {
                            ringDragging = false
                            previewSeekFraction.value = null
                            viewModel.seekToFraction(it)
                        },
                        onSeekCancelled = {
                            ringDragging = false
                            previewSeekFraction.value = null
                        },
                        cancelArmed = cancelArmed,
                        onCancelArmedChanged = { cancelArmed = it },
                        onTogglePlayPause = viewModel::togglePlayPause,
                        onSkipBy = { deltaMs ->
                            // A button tap is a complete seek of its own. Do not let an older crown
                            // target commit afterwards and jump the freshly updated bar elsewhere.
                            pendingSeekFraction = null
                            handler.removeCallbacks(commitSeekRunnable)
                            previewSeekFraction.value = null
                            ringDragging = false
                            viewModel.skipBy(deltaMs)
                        },
                        onCycleSpeed = viewModel::cycleSpeed,
                        onDismiss = ::safeFinish)
            }
        }
    }
}
