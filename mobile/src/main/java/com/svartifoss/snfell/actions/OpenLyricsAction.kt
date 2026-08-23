package com.svartifoss.snfell.actions

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.PersistableBundle
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.gms.wearable.Wearable
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.CommPaths
import com.matejdro.wearutils.messages.sendMessageToNearestClient
import javax.inject.Inject

/**
 * Opens the watch's synced-lyrics screen.
 *
 * When assigned to a watch button or gesture the watch intercepts this locally (see
 * `MusicViewModel.executeActionOnWatch`) and launches its own screen with no round trip - the same
 * shape as [SearchAction] and [OpenPlaylistAction]. This phone-side handler only runs for the paths
 * that always execute on the phone, where it bounces an "open lyrics" message back to the watch.
 *
 * The lyric text itself is fetched later, by the phone, when that screen asks for it - see
 * [CommPaths.MESSAGE_REQUEST_LYRICS].
 */
class OpenLyricsAction : SelectableAction {
    constructor(context: Context) : super(context)
    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle)

    override fun retrieveTitle(): String = context.getString(R.string.action_open_lyrics)

    override val defaultIcon: Drawable
        get() = AppCompatResources.getDrawable(
                context, com.svartifoss.snfell.common.R.drawable.action_lyrics)!!

    class Handler @Inject constructor(
            private val service: com.svartifoss.snfell.music.MusicService
    ) : ActionHandler<OpenLyricsAction> {
        override suspend fun handleAction(action: OpenLyricsAction) {
            val messageClient = Wearable.getMessageClient(service)
            val nodeClient = Wearable.getNodeClient(service)
            messageClient.sendMessageToNearestClient(nodeClient, CommPaths.MESSAGE_OPEN_LYRICS)
        }
    }
}
