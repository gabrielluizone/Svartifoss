package com.svartifoss.snfell.watch.view.queue

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.graphics.Color
import androidx.preference.PreferenceManager
import com.svartifoss.snfell.common.FaceScopedPreferences
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.ThemeAppearance
import com.svartifoss.snfell.watch.theme.LocalWatchUiFontFamily
import com.svartifoss.snfell.watch.theme.watchUiFontFamily
import com.svartifoss.snfell.watch.communication.UiOpenServiceConnection
import com.svartifoss.snfell.watch.communication.WatchMusicService
import com.svartifoss.snfell.watch.util.WatchLanguage
import dagger.hilt.android.AndroidEntryPoint

/**
 * Full-screen Compose host for the playback queue. Requests the queue on open, renders
 * [QueueScreen], plays the tapped entry, and finishes on swipe-to-dismiss (which returns to the
 * player instead of exiting the app).
 */
@AndroidEntryPoint
class QueueActivity : ComponentActivity() {
    // ComponentActivity, so AppCompat's pre-33 locale backport would skip this screen - see
    // WatchLanguage.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(WatchLanguage.attach(newBase))
    }

    private val viewModel: QueueViewModel by viewModels()
    // Guard against finish() being called more than once (Compose dismiss + noHistory + system
    // swipe can all fire close events; only the first one should take effect).
    @Volatile private var finishCalled = false

    // This window is opaque, so MainActivity underneath is stopped and unbinds the service -
    // bind it from here too so the phone connection stays alive while the queue is on screen.
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
            // Make the window invisible before calling finish() so there is no flash from the
            // emptied window being briefly visible while the system transitions back to
            // MainActivity.
            window.decorView.visibility = View.INVISIBLE
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.requestQueue()

        // Read once on open: the style is a synced phone preference that rarely changes, and this
        // activity is recreated each time the queue opens (noHistory).
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val queueStyle = QueueStyle.fromPref(FaceScopedPreferences.getString(
                prefs,
                MiscPreferences.WEAR_QUEUE_STYLE,
                ThemeAppearance.resolve(prefs)
        ))
        val rowSize = QueueRowSize.fromPref(FaceScopedPreferences.getString(
                prefs,
                MiscPreferences.WEAR_LIST_ROW_SIZE,
                ThemeAppearance.resolve(prefs)
        ))

        setContent {
            // No default value: null means the phone hasn't answered the queue request yet, which
            // QueueScreen renders as a loading spinner instead of a bare black screen.
            val items by viewModel.items.observeAsState()
            val accent by viewModel.accentColor.observeAsState(DEFAULT_QUEUE_ACCENT)
            val secondaryAccent by viewModel.secondaryAccentColor.observeAsState(DEFAULT_QUEUE_ACCENT)
            val tertiaryAccent by viewModel.tertiaryAccentColor.observeAsState(DEFAULT_QUEUE_ACCENT)
            val nowPlaying by viewModel.nowPlaying.observeAsState()

            CompositionLocalProvider(
                    LocalWatchUiFontFamily provides watchUiFontFamily(
                            PreferenceManager.getDefaultSharedPreferences(this))) {
            QueueScreen(
                    items = items,
                    accentColor = Color(accent),
                    secondaryAccentColor = Color(secondaryAccent),
                    tertiaryAccentColor = Color(tertiaryAccent),
                    nowPlayingTitle = nowPlaying?.title,
                    nowPlayingArtist = nowPlaying?.artist,
                    onItemClick = { entryId ->
                        viewModel.selectItem(entryId)
                        safeFinish()
                    },
                    onDismiss = { safeFinish() },
                    style = queueStyle,
                    rowSize = rowSize
            )
            }
        }
    }
}
