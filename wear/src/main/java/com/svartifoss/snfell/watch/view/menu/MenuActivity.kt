package com.svartifoss.snfell.watch.view.menu

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Vibrator
import android.view.KeyEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import com.svartifoss.snfell.watch.theme.LocalWatchUiFontFamily
import com.svartifoss.snfell.watch.theme.watchUiFontFamily
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.preference.PreferenceManager
import androidx.wear.input.WearableButtons
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.AlbumAccentSource
import com.svartifoss.snfell.common.CustomLists
import com.svartifoss.snfell.common.LibraryEntry
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.proto.ShortcutPlayMode
import com.svartifoss.snfell.watch.communication.ShortcutPlayRequest
import com.svartifoss.snfell.watch.communication.UiOpenServiceConnection
import com.svartifoss.snfell.watch.communication.WatchMusicService
import com.svartifoss.snfell.watch.util.WatchLanguage
import com.svartifoss.snfell.watch.view.panel.AlbumPaletteCache
import com.svartifoss.snfell.watch.view.panel.PanelAppearanceResolver
import com.svartifoss.snfell.watch.view.shortcut.ShortcutDetailActivity
import com.svartifoss.snfell.watch.view.shortcut.ShortcutDetailUi
import com.svartifoss.snfell.watch.view.shortcut.ShortcutScreenPolicy
import com.svartifoss.snfell.watch.view.shortcut.showContinueOnPhone
import com.svartifoss.snfell.watch.view.shortcut.toShortcutDetail
import com.matejdro.wearutils.miscutils.VibratorCompat
import com.matejdro.wearutils.preferences.definition.Preferences
import dagger.hilt.android.AndroidEntryPoint
import com.svartifoss.snfell.common.ThemeAppearance
import com.svartifoss.snfell.common.FaceScopedPreferences
import com.svartifoss.snfell.watch.view.queue.QueueStyle

/**
 * Full-screen Compose menu replacing the old bottom drawer: shows either the configurable
 * actions menu or a phone-pushed custom list (playlists, search results, ...), per
 * [EXTRA_SHOW_CUSTOM_LIST].
 *
 * A pure picker: the selection is returned as an activity result and executed by MainActivity's
 * MusicViewModel, so watch-executed actions (volume, open menu, search) keep raising their
 * events - volume popup, voice input - where MainActivity can actually show them.
 *
 * A streaming shortcut is the one pick that does not close the menu at once: it opens the
 * shortcut's own screen inside the menu first, and the Play or Shuffle chosen there is what goes
 * back - see [ShortcutScreenPolicy].
 *
 * Physical stem buttons mirror the old drawer: the button physically furthest from the wearer
 * closes the menu, any other stem button confirms the row currently in the center.
 */
@AndroidEntryPoint
class MenuActivity : ComponentActivity() {
    // ComponentActivity, so AppCompat's pre-33 locale backport would skip this screen - see
    // WatchLanguage.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(WatchLanguage.attach(newBase))
    }

    companion object {
        const val EXTRA_SHOW_CUSTOM_LIST = "ShowCustomList"
        const val EXTRA_CUSTOM_LIST_ID = "CustomListId"

        const val RESULT_EXTRA_ACTION_INDEX = "ActionIndex"
        const val RESULT_EXTRA_LIST_ID = "ListId"
        const val RESULT_EXTRA_ENTRY_ID = "EntryId"
    }

    private val viewModel: MenuViewModel by viewModels()

    private var showCustomList by mutableStateOf(false)
    private var requestedCustomListId by mutableStateOf<String?>(null)
    /** The shortcut whose own screen is showing over the list, or null for the list itself. */
    private var shortcutDetail by mutableStateOf<ShortcutDetailUi?>(null)
    private var centerItemIndex = 0
    private var closeKeycode = -1
    @Volatile private var finishCalled = false

    // This window is opaque, so MainActivity underneath is stopped and unbinds the service -
    // bind it from here too so the phone connection stays alive while the menu is on screen.
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

        showCustomList = intent.getBooleanExtra(EXTRA_SHOW_CUSTOM_LIST, false)
        requestedCustomListId = intent.getStringExtra(EXTRA_CUSTOM_LIST_ID)
        findCloseButton()

        setContent {
            val actions by viewModel.actions.observeAsState()
            val customList by viewModel.customList.observeAsState()
            val streamingShortcuts by viewModel.streamingShortcuts.observeAsState()
            val preferences by viewModel.preferences.observeAsState()
            val albumArt by viewModel.albumArt.observeAsState()

            val content = if (showCustomList) {
                val requestedList = if (requestedCustomListId == CustomLists.PLAYLIST_SHORTCUTS) {
                    streamingShortcuts
                } else {
                    customList
                }
                requestedList?.let { MenuContent.Custom(it) }
            } else {
                actions?.let { MenuContent.Actions(it) }
            }

            val alwaysPickCenter = preferences?.let {
                Preferences.getBoolean(it, MiscPreferences.ALWAYS_SELECT_CENTER_ACTION)
            } ?: false

            // The menu has no style picker of its own; it follows the queue's, so the two
            // browse-style lists stay visually consistent. Only the cover treatment is read -
            // every other queue style leaves these rows on their standard pill.
            val coverStyle = preferences?.let {
                QueueStyle.fromPref(FaceScopedPreferences.getString(
                        it, MiscPreferences.WEAR_QUEUE_STYLE, ThemeAppearance.resolve(it)
                ))
            } ?: QueueStyle.GLASS
            val accentSource = preferences?.let {
                PanelAppearanceResolver.accentSource(it, ThemeAppearance.resolve(it))
            } ?: AlbumAccentSource.BALANCED
            val accent = rememberPlayingAccent(albumArt, accentSource)

            CompositionLocalProvider(
                    LocalWatchUiFontFamily provides watchUiFontFamily(preferences)) {
            MenuScreen(
                    content = content,
                    alwaysPickCenter = alwaysPickCenter,
                    coverStyle = coverStyle,
                    accentColor = accent,
                    detail = shortcutDetail,
                    accentSource = accentSource,
                    onActionClick = { index -> returnAction(index) },
                    onEntryClick = { listId, entryId -> returnCustomEntry(listId, entryId) },
                    onEntryLongClick = { listId, entryId -> deleteEntry(listId, entryId) },
                    onCenterItemChanged = { centerItemIndex = it },
                    onCenterConfirm = { confirmCenterItem() },
                    onDetailPlay = { mode -> playShortcut(mode) },
                    onDetailOpenOnPhone = { openShortcutOnPhone() },
                    onDetailDismiss = { shortcutDetail = null },
                    onDismiss = { safeFinish() }
            )
            }
        }
    }

    /**
     * The playing album's colour, seeded from [AlbumPaletteCache] so a menu opened over a cover the
     * player has already read is in that colour from its first frame, as the panel screens are.
     * Null only while an uncached cover is being read; with no cover, the app accent.
     */
    @Composable
    private fun rememberPlayingAccent(art: Bitmap?, source: AlbumAccentSource): Color? {
        val themeAccent = getColor(R.color.theme_accent)
        val seed = AlbumPaletteCache.get(art, source)
        var accent by remember(art, source) {
            mutableStateOf(seed?.let { Color(it.primary) }
                    ?: if (art == null) Color(themeAccent) else null)
        }
        LaunchedEffect(art, source) {
            if (seed != null || art == null) return@LaunchedEffect
            PanelAppearanceResolver.albumTriad(art, source, themeAccent) { triad ->
                accent = Color(triad.primary)
            }
        }
        return accent
    }

    /** singleTop: a custom list arriving while the actions menu is open swaps the content in
     *  place instead of stacking a second menu. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        showCustomList = intent.getBooleanExtra(EXTRA_SHOW_CUSTOM_LIST, false)
        requestedCustomListId = intent.getStringExtra(EXTRA_CUSTOM_LIST_ID)
        // A different list was asked for: whatever shortcut was open belonged to the old one.
        shortcutDetail = null
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val isStemKey = keyCode == KeyEvent.KEYCODE_STEM_PRIMARY ||
                keyCode in KeyEvent.KEYCODE_STEM_1..KeyEvent.KEYCODE_STEM_3
        if (!isStemKey) {
            return super.onKeyDown(keyCode, event)
        }

        if (keyCode == closeKeycode) {
            // From a shortcut's screen, one step back to its list - the same thing a swipe does.
            if (shortcutDetail != null) shortcutDetail = null else safeFinish()
        } else {
            confirmCenterItem()
        }
        return true
    }

    private fun confirmCenterItem() {
        if (shortcutDetail != null) {
            // The screen's primary action, as the stem confirms a list's centred row.
            playShortcut(ShortcutPlayMode.IN_ORDER)
            return
        }
        if (showCustomList) {
            val list = if (requestedCustomListId == CustomLists.PLAYLIST_SHORTCUTS) {
                viewModel.streamingShortcuts.value
            } else {
                viewModel.customList.value
            } ?: return
            val entry = list.items.getOrNull(centerItemIndex)?.listItem ?: return
            returnCustomEntry(list.listId, entry.entryId)
        } else {
            val actions = viewModel.actions.value ?: return
            if (centerItemIndex !in actions.indices) {
                return
            }
            returnAction(centerItemIndex)
        }
    }

    private fun shortcutScreenEnabled(): Boolean {
        val preferences = viewModel.preferences.value
                ?: PreferenceManager.getDefaultSharedPreferences(this)
        return Preferences.getBoolean(preferences, MiscPreferences.WEAR_SHORTCUT_DETAILS)
    }

    private fun returnAction(index: Int) {
        val action = viewModel.actions.value?.getOrNull(index)
        val remoteUri = action?.remoteUri
        if (action != null && remoteUri != null && ShortcutScreenPolicy.opensForMenuAction(
                        shortcutScreenEnabled(), remoteUri, action.shortcutSubtitle)) {
            buzz()
            shortcutDetail = action.toShortcutDetail(remoteUri)
            return
        }

        buzz()
        setResult(RESULT_OK, Intent().putExtra(RESULT_EXTRA_ACTION_INDEX, index))
        safeFinish()
    }

    private fun returnCustomEntry(listId: String, entryId: String) {
        // A list's own explanation ("No shortcuts yet") is not something to pick.
        if (entryId == CustomLists.SPECIAL_ITEM_ERROR) return
        buzz()

        if (listId == CustomLists.PLAYLIST_SHORTCUTS) {
            val item = viewModel.streamingShortcuts.value?.items
                    ?.firstOrNull { it.listItem.entryId == entryId }
            if (item != null && ShortcutScreenPolicy.opensForListEntry(
                            shortcutScreenEnabled(), listId, entryId,
                            phoneKnowsPlayModes = item.listItem.hasShuffleable())) {
                shortcutDetail = item.toShortcutDetail()
                return
            }
        }

        // Walking into a library folder must not close the menu: the phone answers with the next
        // page as a fresh custom list, and MenuViewModel.customList swaps it in underneath the user
        // (the same in-place update deleting a search-history row relies on). Finishing here would
        // dismiss the menu and immediately relaunch it for every level, flashing the player screen
        // between each tap.
        // Search results carry the same encoding: picking an artist there walks into their albums
        // rather than trying (and failing) to play a folder, so it must keep the menu open too.
        val navigable = listId == CustomLists.LIBRARY || listId == CustomLists.SEARCH_RESULTS
        if (navigable && LibraryEntry.isBrowsable(entryId)) {
            viewModel.selectCustomListEntry(listId, entryId)
            return
        }

        setResult(
                RESULT_OK,
                Intent()
                        .putExtra(RESULT_EXTRA_LIST_ID, listId)
                        .putExtra(RESULT_EXTRA_ENTRY_ID, entryId)
        )
        safeFinish()
    }

    /**
     * Play or Shuffle on the open shortcut's screen: handed back like any other pick, through the
     * shortcut path whatever list it came from - a menu entry's remote URI is played the same way
     * as a row of the shortcut list, and that path is the one that carries a play mode.
     */
    private fun playShortcut(mode: ShortcutPlayMode) {
        val detail = shortcutDetail ?: return
        buzz()
        setResult(
                RESULT_OK,
                Intent()
                        .putExtra(RESULT_EXTRA_LIST_ID, CustomLists.PLAYLIST_SHORTCUTS)
                        .putExtra(RESULT_EXTRA_ENTRY_ID, detail.entryId)
                        .putExtra(ShortcutDetailActivity.RESULT_EXTRA_PLAY_MODE, mode.number)
        )
        safeFinish()
    }

    private fun openShortcutOnPhone() {
        val detail = shortcutDetail ?: return
        buzz()
        ShortcutPlayRequest.openOnPhone(this, detail.entryId)
        showContinueOnPhone(this)
        safeFinish()
    }

    /** Deletes an entry in place - unlike [returnCustomEntry] this doesn't close the menu, since
     *  the point is to keep managing the list (the phone re-pushes it afterwards, updating
     *  [MenuViewModel.customList] live). */
    private fun deleteEntry(listId: String, entryId: String) {
        buzz()
        viewModel.deleteCustomListEntry(listId, entryId)
    }

    private fun buzz() {
        val preferences = viewModel.preferences.value
                ?: PreferenceManager.getDefaultSharedPreferences(this)
        if (!Preferences.getBoolean(preferences, MiscPreferences.HAPTIC_FEEDBACK)) {
            return
        }
        VibratorCompat.vibrate(getSystemService(Context.VIBRATOR_SERVICE) as Vibrator, 50)
    }

    /** Same lowest-Y heuristic as the old drawer: the stem button physically furthest from the
     *  wearer's hand acts as "close"; with 0-1 buttons every stem press confirms instead. */
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
            // Hide the window before finish() so the emptied window doesn't flash while the
            // system transitions back to MainActivity (same trick as QueueActivity).
            window.decorView.visibility = View.INVISIBLE
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}
