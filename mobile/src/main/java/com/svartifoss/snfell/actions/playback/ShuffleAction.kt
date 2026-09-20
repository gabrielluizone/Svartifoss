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
 * Toggles shuffle on/off. Shuffle/repeat were never added to the bare framework
 * `android.media.session` API - they only exist on the AndroidX media-compat layer
 * (`MediaControllerCompat`/`PlaybackStateCompat`), which nearly every modern player app
 * (including YouTube Music) builds its session on internally. The wrapping of the framework token
 * we already hold lives in `MusicService.currentCompatController`, which also explains why this
 * has to use the service's controller rather than making one here.
 *
 * Reading a fresh controller answered -1, which this took for "shuffle is on" - so the button
 * switched shuffle off on every press and could never switch it on. Nobody reported it; the
 * identical fault in the repeat button is what led here. [isShuffleOn] holds the reading now.
 */
class ShuffleAction : SelectableAction {
    constructor(context: Context) : super(context)
    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle)

    override fun retrieveTitle(): String = context.getString(R.string.action_shuffle)
    override val defaultIcon: Drawable
        get() = AppCompatResources.getDrawable(context, com.svartifoss.snfell.common.R.drawable.action_shuffle)!!

    class Handler @Inject constructor(private val service: MusicService) : ActionHandler<ShuffleAction> {
        override suspend fun handleAction(action: ShuffleAction) {
            // The service's own controller - a fresh one reads -1 for the mode, which this used to
            // read as "shuffle is on" and answer by switching it off, every press.
            // See MusicService.currentCompatController.
            val controller = service.currentCompatController ?: return
            controller.transportControls.setShuffleMode(nextShuffleMode(controller.shuffleMode))
        }
    }
}
