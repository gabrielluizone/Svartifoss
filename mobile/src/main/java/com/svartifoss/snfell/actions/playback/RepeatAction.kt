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

/**
 * Cycles repeat mode (off -> all -> one -> off).
 *
 * This is the one repeat action that has to *read* the current mode, which is what made it the one
 * that did not work: reading it needs a `MediaControllerCompat` that has finished its handshake
 * with the session, and this built a fresh one per press. The service owns that controller now -
 * see `MusicService.currentCompatController` - and [nextRepeatMode] owns what a press does when
 * the mode still cannot be read.
 */
class RepeatAction : SelectableAction {
    constructor(context: Context) : super(context)
    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle)

    override fun retrieveTitle(): String = context.getString(R.string.action_repeat)
    override val defaultIcon: Drawable
        get() = AppCompatResources.getDrawable(context, com.svartifoss.snfell.common.R.drawable.action_repeat)!!

    class Handler @Inject constructor(private val service: MusicService) : ActionHandler<RepeatAction> {
        override suspend fun handleAction(action: RepeatAction) {
            // The service's own controller, never a fresh one: a controller built here cannot
            // answer what the current mode is yet. See MusicService.currentCompatController.
            val controller = service.currentCompatController ?: return
            controller.transportControls.setRepeatMode(nextRepeatMode(controller.repeatMode))
        }
    }
}
