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
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.FaceScopedPreferences
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.ThemeAppearance
import com.svartifoss.snfell.watch.theme.LocalWatchUiFontFamily
import com.svartifoss.snfell.watch.theme.watchUiFontFamily
import com.svartifoss.snfell.watch.view.panel.PanelAppearanceResolver
import com.svartifoss.snfell.watch.view.panel.PanelTriad
import com.svartifoss.snfell.watch.view.panel.rememberScreenBackdrop
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
        val appearanceContext = ThemeAppearance.resolve(prefs)
        // Kept as the raw preference value as well: it is the "content style" the *Shared panel
        // appearance* Follow option follows, in the vocabulary OverlayBackdropResolver knows.
        val queueStylePreference = FaceScopedPreferences.getString(
                prefs, MiscPreferences.WEAR_QUEUE_STYLE, appearanceContext)
        val queueStyle = QueueStyle.fromPref(queueStylePreference)
        val accentSource = PanelAppearanceResolver.accentSource(prefs, appearanceContext)
        val themeAccent = getColor(R.color.theme_accent)
        val rowSize = QueueRowSize.fromPref(FaceScopedPreferences.getString(
                prefs,
                MiscPreferences.WEAR_LIST_ROW_SIZE,
                ThemeAppearance.resolve(prefs)
        ))

        setContent {
            // No default value: null means the phone hasn't answered the queue request yet, which
            // QueueScreen renders as a loading spinner instead of a bare black screen.
            val items by viewModel.items.observeAsState()
            val accentTriad by viewModel.accentTriad.observeAsState()
            val nowPlaying by viewModel.nowPlaying.observeAsState()
            val canLoadMore by viewModel.canLoadMore.observeAsState(false)
            val loadingMore by viewModel.loadingMore.observeAsState(false)
            val isHistoryFallback by viewModel.isHistoryFallback.observeAsState(false)
            val albumArt by viewModel.albumArt.observeAsState()

            // The configured ground is accent-dependent. QueueViewModel installs the current
            // cached palette synchronously, so this first loading frame is normally already in
            // the album accent. On a genuinely cold start we still wait rather than flash sage.
            val screenBackdrop = accentTriad?.let { triad ->
                rememberScreenBackdrop(
                        prefs = prefs,
                        appearanceContext = appearanceContext,
                        albumArt = albumArt,
                        accentSource = accentSource,
                        themeAccent = themeAccent,
                        triad = triad,
                        contentStyle = queueStylePreference,
                        backdropStyle = MiscPreferences.WEAR_QUEUE_BACKDROP_STYLE)
            }
            // A cold start has no honest album colour yet. Keep the loading mark neutral there;
            // normal openings use the synchronously cached album triad above.
            val resolvedTriad = accentTriad ?: PanelTriad(
                    0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt())

            CompositionLocalProvider(
                    LocalWatchUiFontFamily provides watchUiFontFamily(
                            PreferenceManager.getDefaultSharedPreferences(this))) {
            QueueScreen(
                    items = items,
                    accentColor = Color(resolvedTriad.primary),
                    secondaryAccentColor = Color(resolvedTriad.secondary),
                    tertiaryAccentColor = Color(resolvedTriad.tertiary),
                    nowPlayingTitle = nowPlaying?.title,
                    nowPlayingArtist = nowPlaying?.artist,
                    onItemClick = { entryId ->
                        viewModel.selectItem(entryId)
                        safeFinish()
                    },
                    onDismiss = { safeFinish() },
                    style = queueStyle,
                    rowSize = rowSize,
                    canLoadMore = canLoadMore,
                    loadingMore = loadingMore,
                    isHistoryFallback = isHistoryFallback,
                    onLoadMore = { viewModel.loadMore() },
                    screenBackdrop = screenBackdrop
            )
            }
        }
    }
}
