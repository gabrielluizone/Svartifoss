package com.svartifoss.snfell.actions.volume

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.PersistableBundle
import androidx.appcompat.content.res.AppCompatResources
import com.svartifoss.snfell.R
import com.svartifoss.snfell.actions.ActionHandler
import com.svartifoss.snfell.actions.PhoneAction
import com.svartifoss.snfell.actions.SelectableAction
import com.svartifoss.snfell.actions.playback.normalizePercent
import com.svartifoss.snfell.music.MusicService
import javax.inject.Inject

internal fun absoluteVolumeForPercent(maxVolume: Int, percent: Int): Int =
        if (maxVolume <= 0) 0 else (maxVolume * normalizePercent(percent) / 100f).toInt()

/** Sets the active session to a fixed volume level. */
class SetVolumePercentAction : SelectableAction {
    var percent: Int = 50
        private set

    constructor(context: Context, percent: Int) : super(context) {
        this.percent = normalizePercent(percent)
    }

    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle) {
        percent = normalizePercent(bundle.getInt(KEY_PERCENT, 50))
    }

    override fun retrieveTitle(): String =
            context.getString(R.string.action_set_volume_percent, percent)

    override val defaultIcon: Drawable
        get() = AppCompatResources.getDrawable(
                context,
                com.svartifoss.snfell.common.R.drawable.action_volume_up)!!

    override fun writeToBundle(bundle: PersistableBundle) {
        super.writeToBundle(bundle)
        bundle.putInt(KEY_PERCENT, percent)
    }

    override fun isEqualToAction(other: PhoneAction): Boolean {
        other as SetVolumePercentAction
        return super.isEqualToAction(other) && percent == other.percent
    }

    class Handler @Inject constructor(private val service: MusicService) :
            ActionHandler<SetVolumePercentAction> {
        override suspend fun handleAction(action: SetVolumePercentAction) {
            val controller = service.currentMediaController ?: return
            val maxVolume = controller.playbackInfo?.maxVolume ?: return
            controller.setVolumeTo(absoluteVolumeForPercent(maxVolume, action.percent), 0)
        }
    }

    private companion object {
        const val KEY_PERCENT = "VOLUME_PERCENT"
    }
}
