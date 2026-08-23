package com.svartifoss.snfell.watch.view.lyrics

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Vibrator
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import androidx.wear.ambient.AmbientLifecycleObserver
import com.svartifoss.snfell.watch.communication.UiOpenServiceConnection
import com.svartifoss.snfell.watch.communication.WatchAppShutdown
import com.svartifoss.snfell.watch.communication.WatchMusicService
import com.svartifoss.snfell.common.FaceScopedPreferences
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.ThemeAppearance
import com.svartifoss.snfell.common.WatchTypography
import com.svartifoss.snfell.watch.theme.LocalWatchUiFontFamily
import com.svartifoss.snfell.watch.theme.watchFontFamily
import com.svartifoss.snfell.watch.theme.watchUiFontFamily
import com.svartifoss.snfell.watch.util.WatchLanguage
import com.matejdro.wearutils.miscutils.VibratorCompat
import dagger.hilt.android.AndroidEntryPoint

/**
 * The synced-lyrics screen.
 *
 * Its own Activity, like the queue, the menu and the face picker - that is what makes a right swipe
 * close *this* screen back to the player instead of exiting the app.
 *
 * Two things set it apart from those three, and both come from it being a screen you *read* rather
 * than a chooser you pass through:
 *
 *  - it holds the screen on unconditionally ([WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON]),
 *    ignoring [MiscPreferences.WEAR_KEEP_SCREEN_ON]. Following a lyric involves touching nothing,
 *    so the inactivity timeout would blank the display every few seconds mid-verse - the one case
 *    where that timeout is guaranteed to be wrong.
 *  - it supports **ambient**, and is therefore deliberately *not* `noHistory` like the other three.
 *    They dismiss on screen-off on purpose; this one has to survive it, because going ambient is
 *    exactly what happens when you lower your wrist mid-song and the whole point is that the words
 *    are still there when you raise it again.
 *
 * What neither of those can do is worth stating plainly, since the difference is invisible until
 * you try it: lowering your wrist is a system gesture and no window flag overrides it. The watch
 * still goes ambient. What keeps the lyric visible at that point is the ambient variant below, not
 * the keep-screen-on flag.
 */
@AndroidEntryPoint
class LyricsActivity : ComponentActivity() {

    companion object {
        /**
         * The player's already-resolved accent, so the first frame matches the screen the user just
         * came from instead of flashing the default and correcting itself once Palette finishes.
         */
        const val EXTRA_ACCENT_COLOR = "accent_color"

        /** Burn-in offsets cycled through on each ambient update. */
        private val JIGGLE_STEPS = listOf(0 to 0, 2 to 1, 0 to 2, -2 to 1)
    }

    // ComponentActivity, so AppCompat's pre-33 locale backport would skip this screen - see
    // WatchLanguage.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(WatchLanguage.attach(newBase))
    }

    private val viewModel: LyricsViewModel by viewModels()

    // This window is opaque, so MainActivity underneath is stopped and unbinds the service - bind
    // it here too, or the phone connection dies while the lyrics are on screen.
    private val serviceConnection = UiOpenServiceConnection(lifecycle)

    private lateinit var ambientObserver: AmbientLifecycleObserver

    private var ambient by mutableStateOf(false)
    private var jiggleStep by mutableIntStateOf(0)

    @Volatile private var finishCalled = false

    private val ambientCallback = object : AmbientLifecycleObserver.AmbientLifecycleCallback {
        override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
            ambient = true
            viewModel.setAmbient(true)
        }

        override fun onUpdateAmbient() {
            // Fires about once a minute. That cadence is the reason the ambient variant does not
            // pretend to follow the song: the highlight moves when the system lets it, not when
            // the verse does. Recompute the position right here so the line drawn is the one
            // playing now, rather than whatever the slow ambient ticker last left behind.
            viewModel.refreshPosition()
            jiggleStep = (jiggleStep + 1) % JIGGLE_STEPS.size
        }

        override fun onExitAmbient() {
            ambient = false
            viewModel.setAmbient(false)
            jiggleStep = 0
        }
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
            // Hide the window before finishing so the emptied window is not briefly visible while
            // the system transitions back to MainActivity.
            window.decorView.visibility = View.INVISIBLE
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        ambientObserver = AmbientLifecycleObserver(this, ambientCallback)
        lifecycle.addObserver(ambientObserver)

        // The other full-screen screens get this for free from noHistory - they are destroyed on
        // screen-off, so a "Stop" from the phone can never find one still up. This one survives
        // screen-off by design, so without this it would sit there showing a frozen lyric for a
        // service that no longer exists.
        WatchAppShutdown.closeOn(this, this)

        viewModel.seedAccent(intent.getIntExtra(EXTRA_ACCENT_COLOR, 0))

        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val fontFamily = watchUiFontFamily(preferences)

        // The words' own typeface, a replaceable piece of the theme rather than something this
        // screen decides - see MiscPreferences.WEAR_LYRICS_FONT. Both halves are read through the
        // face scope like every appearance key: this screen belongs to no face, so it follows
        // whichever face is active, exactly as watchUiFontFamily above already does.
        //
        // "follow" resolves to the theme's own typeface (WEAR_FONT), not to this screen's previous
        // UI font - see WatchTypography.lyricsFontKey for why keeping the old look as the default
        // was the bug rather than the compatibility guarantee it looked like.
        val appearance = ThemeAppearance.resolve(preferences)
        val lyricsFontKey = WatchTypography.lyricsFontKey(
                FaceScopedPreferences.getString(
                        preferences, MiscPreferences.WEAR_LYRICS_FONT, appearance),
                trackFontKey = FaceScopedPreferences.getString(
                        preferences, MiscPreferences.WEAR_FONT, appearance))
        val lyricsFontFamily = lyricsFontKey?.let(::watchFontFamily)

        setContent {
            val state by viewModel.state.observeAsState(LyricsUiState.Loading)
            val position by viewModel.positionMs.observeAsState(0L)
            val accent by viewModel.accentColor.observeAsState(0)
            // Only the last line needs it: its span runs to the end of the track rather than to a
            // next line that does not exist.
            val track by viewModel.track.observeAsState()

            val (dx, dy) = JIGGLE_STEPS[jiggleStep]

            CompositionLocalProvider(LocalWatchUiFontFamily provides fontFamily) {
                LyricsScreen(
                        state = state,
                        positionMs = position,
                        durationMs = track?.durationMs ?: 0L,
                        accentColor = Color(accent),
                        ambient = ambient,
                        // Burn-in protection. Only ever a couple of pixels, and zero while awake,
                        // so it costs nothing visually and keeps a static wall of text from
                        // etching itself into the panel over a long album.
                        modifier = Modifier.offset(x = dx.dp, y = dy.dp),
                        onDismiss = { safeFinish() },
                        onSeekToLine = { line ->
                            // The app's own 50ms confirmation buzz, the same one the menu and the
                            // player use for "your input registered" - not Compose's haptic
                            // feedback, which would be a second vocabulary for the same event on
                            // the same wrist.
                            VibratorCompat.vibrate(
                                    getSystemService(Context.VIBRATOR_SERVICE) as Vibrator, 50)
                            viewModel.seekToLine(line)
                        },
                        lyricsFontFamily = lyricsFontFamily)
            }
        }
    }
}
