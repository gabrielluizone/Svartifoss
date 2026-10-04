package com.svartifoss.snfell.watch.view.shortcut

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.os.Vibrator
import android.view.KeyEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.drawable.toBitmap
import androidx.preference.PreferenceManager
import androidx.wear.activity.ConfirmationActivity
import androidx.wear.compose.material3.SwipeToDismissBox
import androidx.wear.input.WearableButtons
import com.matejdro.wearutils.miscutils.VibratorCompat
import com.matejdro.wearutils.preferences.definition.Preferences
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.ThemeAppearance
import com.svartifoss.snfell.proto.ShortcutPlayMode
import com.svartifoss.snfell.watch.communication.CustomListItemWithIcon
import com.svartifoss.snfell.watch.communication.PhoneConnection
import com.svartifoss.snfell.watch.communication.ShortcutPlayRequest
import com.svartifoss.snfell.watch.communication.UiOpenServiceConnection
import com.svartifoss.snfell.watch.communication.WatchMusicService
import com.svartifoss.snfell.watch.config.ButtonAction
import com.svartifoss.snfell.watch.theme.LocalWatchUiFontFamily
import com.svartifoss.snfell.watch.theme.WatchTheme
import com.svartifoss.snfell.watch.theme.watchUiFontFamily
import com.svartifoss.snfell.watch.util.WatchLanguage
import com.svartifoss.snfell.watch.view.panel.AlbumPaletteCache
import com.svartifoss.snfell.watch.view.panel.PanelAppearanceResolver
import dagger.hilt.android.AndroidEntryPoint
import java.lang.ref.WeakReference
import javax.inject.Inject

/**
 * A streaming shortcut's screen on its own, for the two places that have no list underneath to
 * return to: the Shortcuts Tile and the quick panel's rows. The menu shows the same content inside
 * itself instead (see `MenuScreen`), so a swipe there goes back to the list rather than out.
 *
 * Two ways to finish, chosen by the caller. Opened from the player ([EXTRA_EXECUTE] false) it is a
 * picker: the choice goes back as a result and the player's view model starts it, the way every
 * other pick does, with its error handling. Opened from the Tile there is no player to hand it to,
 * so it starts the shortcut itself through [ShortcutPlayRequest].
 */
@AndroidEntryPoint
class ShortcutDetailActivity : ComponentActivity() {

    @Inject lateinit var phoneConnection: PhoneConnection

    // ComponentActivity, so AppCompat's pre-33 locale backport would skip this screen - see
    // WatchLanguage, same as the queue and menu screens.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(WatchLanguage.attach(newBase))
    }

    @Volatile private var finishCalled = false
    private var closeKeycode = -1
    private var detail by mutableStateOf<ShortcutDetailUi?>(null)

    // Opaque window, so whatever was underneath is stopped - hold the service from here too, the
    // way the queue, menu and face picker do, so the phone connection stays up while it is open.
    private val serviceConnection = UiOpenServiceConnection(lifecycle)

    override fun onStart() {
        super.onStart()
        bindService(Intent(this, WatchMusicService::class.java), serviceConnection, BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        unbindService(serviceConnection)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val entryId = intent.getStringExtra(EXTRA_ENTRY_ID)?.takeIf { it.isNotBlank() }
        if (entryId == null) {
            safeFinish()
            return
        }
        val execute = intent.getBooleanExtra(EXTRA_EXECUTE, false)
        detail = ShortcutDetailUi(
                entryId = entryId,
                title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                subtitle = intent.getStringExtra(EXTRA_SUBTITLE),
                shuffleable = intent.getBooleanExtra(EXTRA_SHUFFLEABLE, false))
                .let { CoverHandoff.take(it) }
        findCloseButton()

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val appearance = ThemeAppearance.resolve(prefs)
        val accentSource = PanelAppearanceResolver.accentSource(prefs, appearance)
        // With no cover to take a colour from, the colour of what is playing - the one the player
        // underneath was just showing - rather than one fixed colour.
        val fallbackAccent = AlbumPaletteCache.get(phoneConnection.albumArt.value, accentSource)
                ?.primary ?: WatchTheme.ACCENT_DEFAULT

        setContent {
            // The saved list fills in whatever the caller could not pass: the Tile knows a
            // shortcut's name but not its cover, and the cover can also land after the screen
            // opened (the phone fetches it in the background when the opt-in lookup is on).
            val shortcuts by phoneConnection.streamingShortcuts.observeAsState()
            val listed = shortcuts?.items?.firstOrNull { it.listItem.entryId == entryId }
            val current = detail ?: ShortcutDetailUi(entryId, "", null, false)
            val shown = if (listed == null) current else current.copy(
                    title = current.title.ifBlank { listed.listItem.entryTitle },
                    subtitle = current.subtitle ?: listed.listItem.entrySubtitle
                            .takeIf { listed.listItem.hasEntrySubtitle() },
                    shuffleable = current.shuffleable ||
                            (listed.listItem.hasShuffleable() && listed.listItem.shuffleable),
                    cover = current.cover ?: listed.icon)

            var dismissed by remember { mutableStateOf(false) }
            CompositionLocalProvider(LocalWatchUiFontFamily provides watchUiFontFamily(prefs)) {
                SwipeToDismissBox(onDismissed = {
                    if (!dismissed) {
                        dismissed = true
                        safeFinish()
                    }
                }) { isBackground ->
                    if (!isBackground) {
                        ShortcutDetailContent(
                                detail = shown,
                                accentSource = accentSource,
                                fallbackAccent = Color(fallbackAccent),
                                onPlay = { mode -> choose(shown.entryId, mode, execute) },
                                onOpenOnPhone = { openOnPhone(shown.entryId) })
                    }
                }
            }
        }
    }

    private fun choose(entryId: String, mode: ShortcutPlayMode, execute: Boolean) {
        buzz()
        if (execute) {
            ShortcutPlayRequest.start(this, entryId, mode)
        }
        setResult(RESULT_OK, Intent()
                .putExtra(RESULT_EXTRA_ENTRY_ID, entryId)
                .putExtra(RESULT_EXTRA_PLAY_MODE, mode.number))
        safeFinish()
    }

    private fun openOnPhone(entryId: String) {
        buzz()
        ShortcutPlayRequest.openOnPhone(this, entryId)
        showContinueOnPhone(this)
        safeFinish()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val isStemKey = keyCode == KeyEvent.KEYCODE_STEM_PRIMARY ||
                keyCode in KeyEvent.KEYCODE_STEM_1..KeyEvent.KEYCODE_STEM_3
        if (!isStemKey) {
            return super.onKeyDown(keyCode, event)
        }
        // The same split the menu makes: the button furthest from the hand closes, any other one
        // takes the primary action - here, Play.
        val shown = detail
        if (keyCode == closeKeycode || shown == null) {
            safeFinish()
        } else {
            choose(shown.entryId, ShortcutPlayMode.IN_ORDER,
                    intent.getBooleanExtra(EXTRA_EXECUTE, false))
        }
        return true
    }

    private fun buzz() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        if (!Preferences.getBoolean(preferences, MiscPreferences.HAPTIC_FEEDBACK)) {
            return
        }
        VibratorCompat.vibrate(getSystemService(Context.VIBRATOR_SERVICE) as Vibrator, 50)
    }

    /** See MenuActivity.findCloseButton - the same lowest-Y heuristic. */
    private fun findCloseButton() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N) {
            return
        }
        if (WearableButtons.getButtonCount(this) <= 1) {
            return
        }
        closeKeycode = (KeyEvent.KEYCODE_STEM_1..KeyEvent.KEYCODE_STEM_3)
                .mapNotNull { WearableButtons.getButtonInfo(this, it) }
                .minByOrNull { it.y }?.keycode ?: -1
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

    /**
     * The cover and the player's mark, handed across the activity boundary by reference.
     *
     * A Bitmap does not belong in an Intent - a 480px cover is most of the Binder transaction
     * limit - and both callers already hold it decoded. Weak, so a screen that is never opened
     * cannot keep a cover alive; when it has gone, the saved list supplies it instead.
     */
    private object CoverHandoff {
        private var entryId: String? = null
        private var cover: WeakReference<Bitmap>? = null
        private var mark: WeakReference<Bitmap>? = null
        private var markTintable = false

        fun put(detail: ShortcutDetailUi) {
            entryId = detail.entryId
            cover = detail.cover?.let(::WeakReference)
            mark = detail.mark?.let(::WeakReference)
            markTintable = detail.markTintable
        }

        /** [detail] with whatever pictures were handed over for it. */
        fun take(detail: ShortcutDetailUi): ShortcutDetailUi {
            val matches = entryId == detail.entryId
            val picture = cover?.get()?.takeIf { matches && !it.isRecycled }
            val appMark = mark?.get()?.takeIf { matches && !it.isRecycled }
            val tintable = markTintable
            entryId = null
            cover = null
            mark = null
            return detail.copy(cover = picture, mark = appMark, markTintable = tintable)
        }
    }

    companion object {
        const val EXTRA_ENTRY_ID = "com.svartifoss.snfell.watch.shortcut.ENTRY_ID"
        const val EXTRA_TITLE = "com.svartifoss.snfell.watch.shortcut.TITLE"
        const val EXTRA_SUBTITLE = "com.svartifoss.snfell.watch.shortcut.SUBTITLE"
        const val EXTRA_SHUFFLEABLE = "com.svartifoss.snfell.watch.shortcut.SHUFFLEABLE"
        const val EXTRA_EXECUTE = "com.svartifoss.snfell.watch.shortcut.EXECUTE"

        const val RESULT_EXTRA_ENTRY_ID = "EntryId"
        const val RESULT_EXTRA_PLAY_MODE = "PlayMode"

        /** The intent that opens [detail]'s screen; [execute] makes it start the pick itself. */
        fun intentFor(context: Context, detail: ShortcutDetailUi, execute: Boolean): Intent {
            CoverHandoff.put(detail)
            return Intent(context, ShortcutDetailActivity::class.java)
                    .putExtra(EXTRA_ENTRY_ID, detail.entryId)
                    .putExtra(EXTRA_TITLE, detail.title)
                    .putExtra(EXTRA_SUBTITLE, detail.subtitle)
                    .putExtra(EXTRA_SHUFFLEABLE, detail.shuffleable)
                    .putExtra(EXTRA_EXECUTE, execute)
        }

        /** The play mode a result carries, [ShortcutPlayMode.AS_SAVED] for anything unreadable. */
        fun playModeOf(data: Intent?): ShortcutPlayMode =
                ShortcutPlayMode.forNumber(
                        data?.getIntExtra(RESULT_EXTRA_PLAY_MODE, ShortcutPlayMode.AS_SAVED.number)
                                ?: ShortcutPlayMode.AS_SAVED.number)
                        ?: ShortcutPlayMode.AS_SAVED
    }
}

/**
 * The system's "continue on your phone" confirmation, shown after a shortcut was sent to the
 * phone to be opened there - the standard Wear OS answer to a tap whose result appears on another
 * device, so the wrist does not look as though nothing happened.
 */
fun showContinueOnPhone(context: Context) {
    // Started from the screen that is closing, so it lands in that screen's task: from the Tile,
    // the trampoline's own excluded task, rather than bringing the player's task forward.
    context.startActivity(Intent(context, ConfirmationActivity::class.java)
            .putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE,
                    ConfirmationActivity.OPEN_ON_PHONE_ANIMATION)
            .putExtra(ConfirmationActivity.EXTRA_MESSAGE,
                    context.getString(R.string.shortcut_continue_on_phone)))
}

/**
 * This menu entry as its shortcut screen shows it, started through [entryId] - the entry's remote
 * URI, which the phone's shortcut path plays the same way it plays a row of the shortcut list.
 *
 * Cover art becomes the cover. Anything else the entry carries is its player's mark (a launcher
 * icon or a notification glyph): not a cover - grown to cover size it would read as a smeared
 * logo - but the screen shows it on its placeholder tile, so a cover that never arrived still
 * leaves the player recognisable.
 */
fun ButtonAction.toShortcutDetail(entryId: String): ShortcutDetailUi = ShortcutDetailUi(
        entryId = entryId,
        title = title.orEmpty(),
        subtitle = shortcutSubtitle,
        shuffleable = shortcutShuffleable,
        cover = if (isCoverArt) (icon as? BitmapDrawable)?.bitmap else null,
        mark = if (isCoverArt) null else icon?.let(::markBitmap),
        markTintable = iconTintable)

/** An entry icon as a bitmap big enough for the placeholder tile - a vector at its own 24dp
 *  intrinsic size would be drawn blurred at several times that. */
private fun markBitmap(icon: android.graphics.drawable.Drawable): Bitmap? =
        (icon as? BitmapDrawable)?.bitmap ?: try {
            icon.toBitmap(MARK_BITMAP_PX, MARK_BITMAP_PX)
        } catch (_: IllegalArgumentException) {
            null
        }

private const val MARK_BITMAP_PX = 128

/** A row of the shortcut list as its screen shows it. */
fun CustomListItemWithIcon.toShortcutDetail(): ShortcutDetailUi = ShortcutDetailUi(
        entryId = listItem.entryId,
        title = listItem.entryTitle,
        subtitle = listItem.entrySubtitle.takeIf { listItem.hasEntrySubtitle() && it.isNotBlank() },
        shuffleable = listItem.hasShuffleable() && listItem.shuffleable,
        cover = icon)
