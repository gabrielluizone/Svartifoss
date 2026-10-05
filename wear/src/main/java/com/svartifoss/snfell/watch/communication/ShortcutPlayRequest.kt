package com.svartifoss.snfell.watch.communication

import android.content.Context
import com.google.android.gms.wearable.Wearable
import com.matejdro.wearutils.messages.sendMessageToNearestClient
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.common.CustomLists
import com.svartifoss.snfell.proto.CustomListItemAction
import com.svartifoss.snfell.proto.ShortcutPlayMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * The selection message for one custom-list entry.
 *
 * Shared by every sender - [PhoneConnection] and the screens that run without it - so they cannot
 * drift apart. [ShortcutPlayMode.AS_SAVED] is left unset rather than written, which keeps the bytes
 * identical to what every selection sent before play modes existed.
 */
fun customListItemPayload(
        listId: String,
        entryId: String,
        playMode: ShortcutPlayMode = ShortcutPlayMode.AS_SAVED,
        requestId: String? = null,
        searchQuery: String? = null
): ByteArray = CustomListItemAction.newBuilder()
        .setListId(listId)
        .setEntryId(entryId)
        .apply { if (playMode != ShortcutPlayMode.AS_SAVED) setPlayMode(playMode) }
        .apply { requestId?.let { setRequestId(it) }; searchQuery?.let { setSearchQuery(it) } }
        .build()
        .toByteArray()

/**
 * Starts a streaming shortcut from a screen that has no player behind it to do it - the
 * shortcut screen opened from the Shortcuts Tile, and the Tile's own one-tap trampoline.
 *
 * The same two steps the player performs (see `MusicViewModel.executeItemFromCustomMenu`):
 * register the link with [PhoneUriOpener], which opens it on the phone only if the phone reports
 * that it could not start playback silently, then ask the phone to play it. Registering first
 * matters - the verdict can arrive before the send has even returned, and a verdict with nothing
 * outstanding is deliberately ignored.
 *
 * The send runs on a process-scoped scope because both callers close themselves in the same
 * gesture: a send tied to the screen would be cancelled exactly on the path where it was asked for.
 */
object ShortcutPlayRequest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun start(context: Context, entryId: String, playMode: ShortcutPlayMode,
              searchQuery: String? = null) {
        val appContext = context.applicationContext
        val requestId = PhoneUriOpener.requestOpenAfterPhoneTries(appContext, entryId)
        scope.launch {
            try {
                Wearable.getMessageClient(appContext).sendMessageToNearestClient(
                        Wearable.getNodeClient(appContext),
                        CommPaths.MESSAGE_CUSTOM_LIST_ITEM_SELECTED,
                        customListItemPayload(CustomLists.PLAYLIST_SHORTCUTS, entryId, playMode,
                                requestId, searchQuery))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Phone out of range or Play Services down. The opener's own backstop still covers
                // a phone that receives this late; there is no screen left to report it on.
                Timber.e(e, "Could not ask the phone to play a streaming shortcut")
            }
        }
    }

    /**
     * Opens [entryId] in the service's app on the phone, at once - the shortcut screen's
     * "Open on phone", for seeing what the playlist holds or starting it there by hand.
     *
     * The phone resolves the current app selection without starting playback. This avoids opening
     * an uninstalled app from the cached watch entry. The reply uses the same correlated bridge
     * as playback; an older/unreachable phone gets a bounded cached-link fallback.
     */
    fun openOnPhone(context: Context, entryId: String) {
        val appContext = context.applicationContext
        val requestId = PhoneUriOpener.requestOpenAfterPhoneTries(appContext, entryId, openOnly = true)
        scope.launch {
            try {
                Wearable.getMessageClient(appContext).sendMessageToNearestClient(
                        Wearable.getNodeClient(appContext),
                        CommPaths.MESSAGE_RESOLVE_STREAMING_SHORTCUT,
                        customListItemPayload(CustomLists.PLAYLIST_SHORTCUTS, entryId,
                                requestId = requestId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Could not resolve the current streaming app; using cached fallback")
            }
        }
    }
}
