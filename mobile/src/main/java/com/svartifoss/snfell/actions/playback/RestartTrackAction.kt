package com.svartifoss.snfell.actions.playback

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.PersistableBundle
import androidx.appcompat.content.res.AppCompatResources
import com.svartifoss.snfell.R
import com.svartifoss.snfell.actions.ActionHandler
import com.svartifoss.snfell.actions.SelectableAction
import com.svartifoss.snfell.music.MusicService
import javax.inject.Inject

/** Seeks the current track back to its very beginning. */
class RestartTrackAction : SelectableAction {
    constructor(context: Context) : super(context)
    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle)

    override fun retrieveTitle(): String = context.getString(R.string.action_restart)
    override val defaultIcon: Drawable
        get() = AppCompatResources.getDrawable(context, com.svartifoss.snfell.common.R.drawable.action_restart)!!

    class Handler @Inject constructor(private val service: MusicService) : ActionHandler<RestartTrackAction> {
        override suspend fun handleAction(action: RestartTrackAction) {
            service.currentMediaController?.transportControls?.seekTo(0)
        }
    }
}
