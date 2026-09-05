package com.svartifoss.snfell.watch.view.volume

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.preference.PreferenceManager
import com.svartifoss.snfell.common.FaceScopedPreferences
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.watch.view.panel.PanelAppearanceResolver
import com.svartifoss.snfell.watch.view.panel.PanelSurface
import com.svartifoss.snfell.watch.view.panel.rememberPanelPalette
import com.google.android.wearable.input.RotaryEncoderHelper
import com.svartifoss.snfell.R
import com.svartifoss.snfell.watch.communication.UiOpenServiceConnection
import com.svartifoss.snfell.watch.communication.WatchMusicService
import com.svartifoss.snfell.watch.theme.LocalWatchUiFontFamily
import com.svartifoss.snfell.watch.theme.watchUiFontFamily
import com.svartifoss.snfell.watch.theme.watchUiTypeface
import com.svartifoss.snfell.watch.util.WatchLanguage
import dagger.hilt.android.AndroidEntryPoint

/**
 * Full-screen volume control: a draggable ring plus step buttons, reachable only by assigning
 * "Open volume screen" to a button, gesture or quick-panel slot - additional to, never a
 * replacement for, the existing rotary/gesture-triggered transient overlay in `MainActivity`,
 * which this screen does not touch. The crown still adjusts volume here, over the identical
 * [PhoneConnection.sendVolume] path, because it would be a stranger control otherwise on the one
 * screen whose entire purpose is volume.
 */
@AndroidEntryPoint
class VolumeActivity : ComponentActivity() {
    // ComponentActivity, so AppCompat's pre-33 locale backport would skip this screen - see
    // WatchLanguage.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(WatchLanguage.attach(newBase))
    }

    private val viewModel: VolumeViewModel by viewModels()
    @Volatile private var finishCalled = false

    // This window is opaque, so MainActivity underneath is stopped and unbinds the service -
    // bind it from here too so the phone connection stays alive while this screen is on screen.
    private val serviceConnection = UiOpenServiceConnection(lifecycle)

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

    override fun onGenericMotionEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_SCROLL && RotaryEncoderHelper.isFromRotaryEncoder(ev)) {
            val delta = -RotaryEncoderHelper.getRotaryAxisValue(ev) *
                    RotaryEncoderHelper.getScaledScrollFactor(this)
            // Same base scale MainActivity's crown-to-volume mapping uses at default sensitivity.
            viewModel.step(delta * ROTARY_VOLUME_SCALE)
            return true
        }
        return super.onGenericMotionEvent(ev)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keeps volume_screen_title as the Activity's own label. It used to be drawn on the screen
        // as well, where it sat squarely on top of the arc's louder glyph - the panel is already
        // unmistakable from its arc and percentage, so the label reads better as chrome nobody has
        // to look past.
        setTitle(R.string.volume_screen_title)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        // The panel settings are face-scoped, so they are read through the *same* appearance
        // context the player resolves - a saved custom theme's volume panel has to reach this
        // screen too, or opening it would silently drop back to the base face's styling.
        val appearanceContext = PanelAppearanceResolver.appearanceContext(prefs)
        val themeAccent = getColor(R.color.theme_accent)
        val accentSource = PanelAppearanceResolver.accentSource(prefs, appearanceContext)
        // The percentage is a View, so it needs the Typeface twin of the font family the Compose
        // chrome around it uses - otherwise the title and the number render in two different faces.
        val uiTypeface = watchUiTypeface(this, prefs)

        setContent {
            val volume by viewModel.volume.observeAsState(0f)
            val albumArt by viewModel.albumArt.observeAsState()

            // Seeded from AlbumPaletteCache, so a cover the player has already extracted
            // is painted in the album's colours on the first frame - this screen used to
            // open on the fallback accent and snap over. See rememberPanelPalette.
            val palette = rememberPanelPalette(
                    prefs = prefs,
                    appearanceContext = appearanceContext,
                    surface = PanelSurface.VOLUME,
                    albumArt = albumArt,
                    accentSource = accentSource,
                    themeAccent = themeAccent)
            val appearance = palette.appearance
            val backdrop = palette.backdrop

            CompositionLocalProvider(
                    LocalWatchUiFontFamily provides watchUiFontFamily(prefs)) {
                VolumeScreen(
                        volume = volume,
                        appearance = appearance,
                        albumArt = albumArt,
                        backdrop = backdrop,
                        screenFace = appearanceContext.baseFace,
                        themeAccentColor = themeAccent,
                        uiTypeface = uiTypeface,
                        onVolumeChange = viewModel::setVolume,
                        onStep = viewModel::step,
                        onTogglePlayPause = viewModel::togglePlayPause,
                        onDismiss = ::safeFinish)
            }
        }
    }

    private companion object {
        const val ROTARY_VOLUME_SCALE = 0.0011f
    }
}
