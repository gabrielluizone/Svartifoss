package com.svartifoss.snfell.actions

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.PersistableBundle
import androidx.appcompat.content.res.AppCompatResources
import com.svartifoss.snfell.R
import com.svartifoss.snfell.music.MusicService
import javax.inject.Inject

/**
 * Starts Deezer Flow, the service's account-wide personalized continuous mix. Unlike a user's
 * favorites playlist, Flow has one stable route for every account, so it can be a built-in action
 * without asking the user to discover and save an account-specific playlist id first.
 *
 * The normal streaming playback ladder still applies: Svartifoss first offers the URI to an active
 * Deezer media session, then its browser service, and finally opens the official Flow page in the
 * installed app and nudges playback.
 */
class PlayDeezerFlowAction : SelectableAction {
    constructor(context: Context) : super(context)
    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle)

    override fun retrieveTitle(): String = context.getString(R.string.action_play_deezer_flow)

    override val defaultIcon: Drawable
        get() = AppCompatResources.getDrawable(
                context,
                com.svartifoss.snfell.common.R.drawable.action_open_playlist
        )!!

    override val remoteUri: String
        get() = PlayPlaylistShortcutAction(context, title, FLOW_LINK).remoteUri

    class Handler @Inject constructor(private val service: MusicService) :
            ActionHandler<PlayDeezerFlowAction> {
        override suspend fun handleAction(action: PlayDeezerFlowAction) {
            service.playDeepLink(FLOW_LINK)
        }
    }

    companion object {
        private const val FLOW_LINK = "https://www.deezer.com/flow"
    }
}
