package com.svartifoss.snfell.watch.view.menu

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Vibrator
import android.view.KeyEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.preference.PreferenceManager
import androidx.wear.input.WearableButtons
import com.svartifoss.snfell.common.CustomLists
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.watch.communication.UiOpenServiceConnection
import com.svartifoss.snfell.watch.communication.WatchMusicService
import com.matejdro.wearutils.miscutils.VibratorCompat
import com.matejdro.wearutils.preferences.definition.Preferences
import dagger.hilt.android.AndroidEntryPoint

/**
 * Full-screen Compose menu replacing the old bottom drawer: shows either the configurable
 * actions menu or a phone-pushed custom list (playlists, search results, ...), per
 * [EXTRA_SHOW_CUSTOM_LIST].
 *
 * A pure picker: the selection is returned as an activity result and executed by MainActivity's
 * MusicViewModel, so watch-executed actions (volume, open menu, search) keep raising their
 * events - volume popup, voice input - where MainActivity can actually show them.
 *
 * Physical stem buttons mirror the old drawer: the button physically furthest from the wearer
 * closes the menu, any other stem button confirms the row currently in the center.
 */
@AndroidEntryPoint
class MenuActivity : ComponentActivity() {
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

            MenuScreen(
                    content = content,
                    alwaysPickCenter = alwaysPickCenter,
                    onActionClick = { index -> returnAction(index) },
                    onEntryClick = { listId, entryId -> returnCustomEntry(listId, entryId) },
                    onEntryLongClick = { listId, entryId -> deleteEntry(listId, entryId) },
                    onCenterItemChanged = { centerItemIndex = it },
                    onCenterConfirm = { confirmCenterItem() },
                    onDismiss = { safeFinish() }
            )
        }
    }

    /** singleTop: a custom list arriving while the actions menu is open swaps the content in
     *  place instead of stacking a second menu. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        showCustomList = intent.getBooleanExtra(EXTRA_SHOW_CUSTOM_LIST, false)
        requestedCustomListId = intent.getStringExtra(EXTRA_CUSTOM_LIST_ID)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val isStemKey = keyCode == KeyEvent.KEYCODE_STEM_PRIMARY ||
                keyCode in KeyEvent.KEYCODE_STEM_1..KeyEvent.KEYCODE_STEM_3
        if (!isStemKey) {
            return super.onKeyDown(keyCode, event)
        }

        if (keyCode == closeKeycode) {
            safeFinish()
        } else {
            confirmCenterItem()
        }
        return true
    }

    private fun confirmCenterItem() {
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

    private fun returnAction(index: Int) {
        buzz()
        setResult(RESULT_OK, Intent().putExtra(RESULT_EXTRA_ACTION_INDEX, index))
        safeFinish()
    }

    private fun returnCustomEntry(listId: String, entryId: String) {
        buzz()
        setResult(
                RESULT_OK,
                Intent()
                        .putExtra(RESULT_EXTRA_LIST_ID, listId)
                        .putExtra(RESULT_EXTRA_ENTRY_ID, entryId)
        )
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
