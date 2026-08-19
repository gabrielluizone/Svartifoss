package com.svartifoss.snfell.watch.tile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.wearable.Wearable
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.common.CustomLists
import com.svartifoss.snfell.proto.CustomListItemAction
import com.svartifoss.snfell.watch.communication.PhoneUriOpener
import com.matejdro.wearutils.messages.sendMessageToNearestClient
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Invisible bridge activity launched by a [ShortcutsTileService] chip. A Tile click can only launch
 * an Activity (not send Data Layer messages), so this performs the same two steps the now-playing
 * menu does when a shortcut is picked (MusicViewModel.executeItemFromCustomMenu):
 *
 *  1. Register the shortcut's URI with [PhoneUriOpener], which opens it on the phone only if the
 *     phone reports back that it could not start playback silently.
 *  2. Ask the phone to actually play it via [CommPaths.MESSAGE_CUSTOM_LIST_ITEM_SELECTED].
 *
 * Registering before sending matters: the verdict can arrive before this activity has finished,
 * and a verdict with nothing outstanding is deliberately ignored.
 *
 * It shows no UI and finishes as soon as both are dispatched. The pending open outlives it -
 * [PhoneUriOpener] is process-scoped precisely because every caller closes itself immediately.
 */
class ShortcutLaunchActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val entryId = intent?.getStringExtra(EXTRA_ENTRY_ID)?.takeIf { it.isNotBlank() }
        if (entryId == null) {
            finish()
            return
        }

        PhoneUriOpener.requestOpenAfterPhoneTries(this, entryId)
        lifecycleScope.launch {
            try {
                playOnPhone(entryId)
            } catch (e: Exception) {
                Timber.e(e, "Could not launch streaming shortcut from the Tile")
            } finally {
                finish()
            }
        }
    }

    private suspend fun playOnPhone(entryId: String) {
        val payload = CustomListItemAction.newBuilder()
            .setListId(CustomLists.PLAYLIST_SHORTCUTS)
            .setEntryId(entryId)
            .build()
            .toByteArray()
        Wearable.getMessageClient(this).sendMessageToNearestClient(
            Wearable.getNodeClient(this),
            CommPaths.MESSAGE_CUSTOM_LIST_ITEM_SELECTED,
            payload
        )
    }

    companion object {
        const val EXTRA_ENTRY_ID = "com.svartifoss.snfell.watch.tile.EXTRA_ENTRY_ID"
    }
}
