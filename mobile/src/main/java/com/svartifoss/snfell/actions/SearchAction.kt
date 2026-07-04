package com.svartifoss.snfell.actions

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.PersistableBundle
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.gms.wearable.Wearable
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.music.MusicService
import com.matejdro.wearutils.messages.sendMessageToNearestClient
import javax.inject.Inject

/**
 * Opens the voice/keyboard search input on the watch; the query then comes back as a
 * [CommPaths.MESSAGE_PLAY_FROM_SEARCH] handled by [MusicService].
 *
 * When assigned to a watch button/gesture the watch intercepts this action locally
 * (see MusicViewModel.executeActionOnWatch) and opens the input without a round trip.
 * This phone-side handler only runs for paths that always execute on the phone, such as
 * the watch's actions menu - it just bounces an "open input" message back to the watch.
 */
class SearchAction : SelectableAction {
    constructor(context: Context) : super(context)
    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle)

    override fun retrieveTitle(): String = context.getString(R.string.action_search)
    override val defaultIcon: Drawable
        get() = AppCompatResources.getDrawable(context, com.svartifoss.snfell.common.R.drawable.action_search)!!

    class Handler @Inject constructor(private val service: MusicService) : ActionHandler<SearchAction> {
        override suspend fun handleAction(action: SearchAction) {
            val messageClient = Wearable.getMessageClient(service)
            val nodeClient = Wearable.getNodeClient(service)
            messageClient.sendMessageToNearestClient(nodeClient, CommPaths.MESSAGE_OPEN_VOICE_SEARCH)
        }
    }
}
