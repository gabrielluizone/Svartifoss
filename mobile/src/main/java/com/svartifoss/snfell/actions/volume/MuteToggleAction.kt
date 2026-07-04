package com.svartifoss.snfell.actions.volume

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.PersistableBundle
import androidx.appcompat.content.res.AppCompatResources
import com.svartifoss.snfell.R
import com.svartifoss.snfell.actions.ActionHandler
import com.svartifoss.snfell.actions.SelectableAction
import com.svartifoss.snfell.music.MusicService
import javax.inject.Inject

/**
 * Mutes/unmutes the active media session. Unlike volume up/down (handled locally on the watch),
 * this goes to the phone so it can remember the pre-mute level and restore it on the next toggle.
 */
class MuteToggleAction : SelectableAction {
    constructor(context: Context) : super(context)
    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle)

    override fun retrieveTitle(): String = context.getString(R.string.action_mute)
    override val defaultIcon: Drawable
        get() = AppCompatResources.getDrawable(context, com.svartifoss.snfell.common.R.drawable.action_volume_off)!!

    class Handler @Inject constructor(private val service: MusicService) : ActionHandler<MuteToggleAction> {
        override suspend fun handleAction(action: MuteToggleAction) {
            service.toggleMute()
        }
    }
}
