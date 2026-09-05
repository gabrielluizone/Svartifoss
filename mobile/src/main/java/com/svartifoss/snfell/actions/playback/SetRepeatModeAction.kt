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

internal fun normalizeRepeatMode(value: Int): Int = when (value) {
    PlaybackStateCompat.REPEAT_MODE_NONE,
    PlaybackStateCompat.REPEAT_MODE_ALL,
    PlaybackStateCompat.REPEAT_MODE_ONE -> value
    else -> PlaybackStateCompat.REPEAT_MODE_NONE
}

/** Sets repeat off/all/one directly, without depending on the current cycle position. */
class SetRepeatModeAction : SelectableAction {
    var mode: Int = PlaybackStateCompat.REPEAT_MODE_NONE
        private set

    constructor(context: Context, mode: Int) : super(context) {
        this.mode = normalizeRepeatMode(mode)
    }

    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle) {
        mode = normalizeRepeatMode(bundle.getInt(KEY_MODE, PlaybackStateCompat.REPEAT_MODE_NONE))
    }

    override fun retrieveTitle(): String = context.getString(when (mode) {
        PlaybackStateCompat.REPEAT_MODE_ALL -> R.string.action_set_repeat_all
        PlaybackStateCompat.REPEAT_MODE_ONE -> R.string.action_set_repeat_one
        else -> R.string.action_set_repeat_off
    })

    override val defaultIcon: Drawable
        get() = AppCompatResources.getDrawable(
                context,
                com.svartifoss.snfell.common.R.drawable.action_repeat)!!

    override fun writeToBundle(bundle: PersistableBundle) {
        super.writeToBundle(bundle)
        bundle.putInt(KEY_MODE, mode)
    }

    override fun isEqualToAction(other: PhoneAction): Boolean {
        other as SetRepeatModeAction
        return super.isEqualToAction(other) && mode == other.mode
    }

    class Handler @Inject constructor(private val service: MusicService) :
            ActionHandler<SetRepeatModeAction> {
        override suspend fun handleAction(action: SetRepeatModeAction) {
            val controller = service.currentMediaController ?: return
            val compatController = MediaControllerCompat(
                    service,
                    MediaSessionCompat.Token.fromToken(controller.sessionToken))
            compatController.transportControls.setRepeatMode(action.mode)
        }
    }

    private companion object {
        const val KEY_MODE = "REPEAT_MODE"
    }
}
