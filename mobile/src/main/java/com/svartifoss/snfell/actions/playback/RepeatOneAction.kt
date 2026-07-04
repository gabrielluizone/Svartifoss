package com.svartifoss.snfell.actions.playback

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.PersistableBundle
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.appcompat.content.res.AppCompatResources
import com.svartifoss.snfell.R
import com.svartifoss.snfell.actions.ActionHandler
import com.svartifoss.snfell.actions.SelectableAction
import com.svartifoss.snfell.music.MusicService
import javax.inject.Inject

/**
 * Toggles repeat-one directly (one <-> off), for users who want "loop this track" on a single
 * button without cycling through every mode like [RepeatAction] does. Goes through
 * [MediaControllerCompat] for the same reason as [RepeatAction] - repeat mode doesn't exist on
 * the framework `MediaController` API.
 */
class RepeatOneAction : SelectableAction {
    constructor(context: Context) : super(context)
    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle)

    override fun retrieveTitle(): String = context.getString(R.string.action_repeat_one)
    override val defaultIcon: Drawable
        get() = AppCompatResources.getDrawable(context, com.svartifoss.snfell.common.R.drawable.action_repeat_one)!!

    class Handler @Inject constructor(private val service: MusicService) : ActionHandler<RepeatOneAction> {
        override suspend fun handleAction(action: RepeatOneAction) {
            val controller = service.currentMediaController ?: return
            val compatController = MediaControllerCompat(
                    service,
                    MediaSessionCompat.Token.fromToken(controller.sessionToken)
            )

            val nextMode = if (compatController.repeatMode == PlaybackStateCompat.REPEAT_MODE_ONE) {
                PlaybackStateCompat.REPEAT_MODE_NONE
            } else {
                PlaybackStateCompat.REPEAT_MODE_ONE
            }

            compatController.transportControls.setRepeatMode(nextMode)
        }
    }
}
