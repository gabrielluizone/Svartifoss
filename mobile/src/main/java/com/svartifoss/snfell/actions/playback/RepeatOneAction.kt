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
 * Toggles repeat-one directly (one <-> off), for users who want "loop this track" on a single
 * button without cycling through every mode like [RepeatAction] does.
 *
 * It reads the current mode, so it depends on the same settled `MediaControllerCompat`
 * [RepeatAction] does - see `MusicService.currentCompatController`. Reading a fresh one answered
 * "not repeat-one" every time, which turned repeat-one on and could never turn it back off; the
 * half that worked was the more visible one, which is why this was reported as a cycle bug alone.
 */
class RepeatOneAction : SelectableAction {
    constructor(context: Context) : super(context)
    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle)

    override fun retrieveTitle(): String = context.getString(R.string.action_repeat_one)
    override val defaultIcon: Drawable
        get() = AppCompatResources.getDrawable(context, com.svartifoss.snfell.common.R.drawable.action_repeat_one)!!

    class Handler @Inject constructor(private val service: MusicService) : ActionHandler<RepeatOneAction> {
        override suspend fun handleAction(action: RepeatOneAction) {
            // The service's own controller - a fresh one reads -1 for the mode, which made this
            // toggle a one-way switch. See MusicService.currentCompatController.
            val controller = service.currentCompatController ?: return
            controller.transportControls.setRepeatMode(toggledRepeatOneMode(controller.repeatMode))
        }
    }
}
