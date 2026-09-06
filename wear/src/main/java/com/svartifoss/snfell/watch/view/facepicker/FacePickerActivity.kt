package com.svartifoss.snfell.watch.view.facepicker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.livedata.observeAsState
import androidx.lifecycle.lifecycleScope
import androidx.palette.graphics.Palette
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.svartifoss.snfell.common.AppearanceContext
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.ThemeAppearance
import com.svartifoss.snfell.watch.communication.PhoneConnection
import com.svartifoss.snfell.watch.config.PreferencesBus
import com.svartifoss.snfell.watch.communication.UiOpenServiceConnection
import com.svartifoss.snfell.watch.communication.WatchMusicService
import com.svartifoss.snfell.watch.theme.LocalWatchUiFontFamily
import com.svartifoss.snfell.watch.theme.WatchTheme
import com.svartifoss.snfell.watch.theme.watchUiFontFamily
import com.svartifoss.snfell.watch.util.WatchLanguage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Full-screen host for the on-watch face picker, opened by long-pressing the centre of the player
 * (see `CenterLongPressAction`).
 *
 * A separate Activity rather than an overlay inside MainActivity, which is what makes the
 * right-swipe safe: the gesture closes this window back to the player instead of leaving the app.
 * See [FacePickerScreen] for the rest of that reasoning.
 */
@AndroidEntryPoint
class FacePickerActivity : ComponentActivity() {

    @Inject lateinit var phoneConnection: PhoneConnection

    // ComponentActivity, so AppCompat's pre-33 locale backport would skip this screen - see
    // WatchLanguage, same as the queue and menu screens.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(WatchLanguage.attach(newBase))
    }

    @Volatile private var finishCalled = false

    // This window is opaque, so MainActivity underneath is stopped and unbinds the service - hold
    // it from here too, exactly as QueueActivity does.
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        // Read once on open, like QueueActivity's style: this activity is recreated each time the
        // picker opens (noHistory), so there is nothing to keep in sync while it is up.
        val appearance = ThemeAppearance.resolve(prefs)
        val initialFace = if (appearance is AppearanceContext.Custom) {
            WatchFaceOption.CUSTOM_PREFIX + appearance.themeId
        } else {
            appearance.baseFace
        }
        val customThemesJson = prefs.getString(MiscPreferences.WEAR_AVAILABLE_CUSTOM_THEMES.key, "[]") ?: "[]"
        val phoneConnected = phoneConnection.isPhoneConnected()

        setContent {
            // remember, or every recomposition would reset the highlight back to the stored face
            // and the row the user just tapped would visibly snap back before the screen closes.
            var selected by remember { mutableStateOf(initialFace) }
            val albumArt by phoneConnection.albumArt.observeAsState()
            val accent = rememberAlbumAccent(albumArt)

            CompositionLocalProvider(
                    LocalWatchUiFontFamily provides watchUiFontFamily(prefs)) {
                // Ordered per section, never across them: a saved theme sitting between two
                // built-in faces on the strength of a timestamp is exactly what the two sections
                // exist to prevent. See FaceRecency.
                val builtInOptions = WatchFaceCatalog.builtInOptions(initialFace)
                val customOptions = WatchFaceCatalog.customOptions(customThemesJson)
                FacePickerScreen(
                        builtIn = FaceRecency.ordered(
                                builtInOptions,
                                FaceRecency.timestamps(prefs, builtInOptions.map { it.key })),
                        custom = FaceRecency.ordered(
                                customOptions,
                                FaceRecency.timestamps(prefs, customOptions.map { it.key })),
                        selectedFace = selected,
                        accentColor = accent,
                        phoneConnected = phoneConnected,
                        onSelect = { option ->
                            selected = option.key
                            applyFace(prefs, option)
                            safeFinish()
                        },
                        onDismiss = { safeFinish() }
                )
            }
        }
    }

    /**
     * The current cover's accent, so the picker is tinted like the player it belongs to.
     *
     * Extracted off the main thread and cached per bitmap - Palette on a cover is cheap but not
     * free, and this screen recomposes on every scroll frame. A real cover starts as null so the
     * picker stays neutral until extraction completes instead of flashing the default sage wash.
     * With no artwork, the app accent is the final, intentional fallback.
     */
    @Composable
    private fun rememberAlbumAccent(art: android.graphics.Bitmap?): Color? {
        var accent by remember(art) {
            mutableStateOf(if (art == null) Color(WatchTheme.ACCENT_DEFAULT) else null)
        }
        LaunchedEffect(art) {
            if (art != null) {
                accent = withContext(Dispatchers.Default) {
                    val palette = Palette.from(art).generate()
                    val rgb = palette.vibrantSwatch?.rgb
                            ?: palette.mutedSwatch?.rgb
                            ?: palette.dominantSwatch?.rgb
                            ?: WatchTheme.ACCENT_DEFAULT
                    Color(rgb)
                }
            }
        }
        return accent
    }

    /**
     * Applies [option] locally *and* tells the phone.
     *
     * Both halves are needed and neither is redundant. The local write is what makes the change
     * visible the instant the picker closes - waiting for a Bluetooth round trip would leave the
     * player on the old face for a second or more, which reads as a failed tap. The message is what
     * makes it durable: the phone owns every synced preference and re-publishes the whole snapshot
     * on its next process start, so a watch-only write would eventually be reverted.
     *
     * What the local write *cannot* do is activate a custom theme. A theme is a complete snapshot
     * of scoped appearance values that only the phone holds; `ThemeAppearance.resolve` refuses to
     * activate an incomplete one by design, and `wear_screen_face` only ever accepts a real face
     * key - writing `custom:<id>` into it makes `normalizeBaseFace` fall back to Classic, which is
     * how picking one of your own themes used to visibly switch the watch to the wrong face. So
     * for a theme this applies its *base* face locally, which is the part the watch can honestly
     * render on its own, and lets the phone deliver the rest.
     */
    private fun applyFace(prefs: android.content.SharedPreferences, option: WatchFaceOption) {
        // Recorded against the option's own key, so a saved theme and the built-in face it is based
        // on are remembered separately - they are different picks and appear in different sections.
        FaceRecency.recordUse(prefs, option.key)
        prefs.edit().apply {
            putString(MiscPreferences.WEAR_SCREEN_FACE.key, option.baseFace)
            if (!option.isCustomTheme) {
                // Mirrors MusicService.applyScreenFaceFromWatch. Without it a watch that currently
                // has a theme active keeps resolving to that theme, and picking a built-in face
                // does nothing at all until the phone syncs back.
                remove(MiscPreferences.WEAR_ACTIVE_CUSTOM_THEME_ID.key)
                putBoolean(MiscPreferences.WEAR_CUSTOM_THEME_COMPLETE.key, false)
            }
        }.apply()
        // Writing the file is not enough on its own: MainActivity re-reads its appearance only when
        // PreferencesBus emits (that is how a phone sync applies), and it does not reload on
        // onStart. Without this the player would keep rendering the old face until the phone
        // happened to push a snapshot back - which looks exactly like the picker not working.
        PreferencesBus.postValue(prefs)
        lifecycleScope.launch {
            try {
                phoneConnection.setScreenFace(option.key)
            } catch (e: Exception) {
                // Nothing to recover here: the face is already applied on the watch, and the phone
                // will re-assert its own value when it next syncs. Reported rather than surfaced.
                Timber.w(e, "Could not tell the phone about the face change")
            }
        }
    }
}
