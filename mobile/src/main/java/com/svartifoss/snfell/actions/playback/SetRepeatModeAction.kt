package com.svartifoss.snfell.actions.playback

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.PersistableBundle
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

internal fun repeatModeIcon(mode: Int): Int =
        if (normalizeRepeatMode(mode) == PlaybackStateCompat.REPEAT_MODE_ONE) {
            com.svartifoss.snfell.common.R.drawable.action_repeat_one
        } else {
            com.svartifoss.snfell.common.R.drawable.action_repeat
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
        get() = AppCompatResources.getDrawable(context, repeatModeIcon(mode))!!

    /** Declared because it varies by [mode]: see `needsTransmittedIcon`. */
    override val defaultIconRes: Int
        get() = repeatModeIcon(mode)

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
            // This one names its target outright, so it never needed to read the current mode -
            // which is why it kept working while the cycling button did not.
            val controller = service.currentCompatController ?: return
            controller.transportControls.setRepeatMode(action.mode)
        }
    }

    private companion object {
        const val KEY_MODE = "REPEAT_MODE"
    }
}
