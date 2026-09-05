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
import com.svartifoss.snfell.actions.PhoneAction
import com.svartifoss.snfell.actions.SelectableAction
import com.svartifoss.snfell.music.MusicService
import javax.inject.Inject

/** Sets shuffle deterministically instead of toggling an unknown current state. */
class SetShuffleModeAction : SelectableAction {
    var enabled: Boolean = false
        private set

    constructor(context: Context, enabled: Boolean) : super(context) {
        this.enabled = enabled
    }

    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle) {
        enabled = bundle.getBoolean(KEY_ENABLED, false)
    }

    override fun retrieveTitle(): String = context.getString(
            if (enabled) R.string.action_set_shuffle_on else R.string.action_set_shuffle_off)

    override val defaultIcon: Drawable
        get() = AppCompatResources.getDrawable(
                context, com.svartifoss.snfell.common.R.drawable.action_shuffle)!!

    override fun writeToBundle(bundle: PersistableBundle) {
        super.writeToBundle(bundle)
        bundle.putBoolean(KEY_ENABLED, enabled)
    }

    override fun isEqualToAction(other: PhoneAction): Boolean {
        other as SetShuffleModeAction
        return super.isEqualToAction(other) && enabled == other.enabled
    }

    class Handler @Inject constructor(private val service: MusicService) :
            ActionHandler<SetShuffleModeAction> {
        override suspend fun handleAction(action: SetShuffleModeAction) {
            val controller = service.currentMediaController ?: return
            val compatController = MediaControllerCompat(
                    service,
                    MediaSessionCompat.Token.fromToken(controller.sessionToken))
            compatController.transportControls.setShuffleMode(
                    if (action.enabled) PlaybackStateCompat.SHUFFLE_MODE_ALL
                    else PlaybackStateCompat.SHUFFLE_MODE_NONE)
        }
    }

    private companion object {
        const val KEY_ENABLED = "SHUFFLE_ENABLED"
    }
}
