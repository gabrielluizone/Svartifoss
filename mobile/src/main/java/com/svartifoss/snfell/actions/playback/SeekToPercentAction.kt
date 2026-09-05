package com.svartifoss.snfell.actions.playback

import android.content.Context
import android.graphics.drawable.Drawable
import android.media.MediaMetadata
import android.os.PersistableBundle
import androidx.appcompat.content.res.AppCompatResources
import com.svartifoss.snfell.R
import com.svartifoss.snfell.actions.ActionHandler
import com.svartifoss.snfell.actions.PhoneAction
import com.svartifoss.snfell.actions.SelectableAction
import com.svartifoss.snfell.music.MusicService
import javax.inject.Inject

internal fun normalizePercent(value: Int): Int = value.coerceIn(0, 100)

internal fun seekPositionForPercent(durationMs: Long, percent: Int): Long? =
        durationMs.takeIf { it > 0L }?.let { duration ->
            duration * normalizePercent(percent) / 100L
        }

/** Jumps to a fixed percentage of the current track. */
class SeekToPercentAction : SelectableAction {
    var percent: Int = 50
        private set

    constructor(context: Context, percent: Int) : super(context) {
        this.percent = normalizePercent(percent)
    }

    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle) {
        percent = normalizePercent(bundle.getInt(KEY_PERCENT, 50))
    }

    override fun retrieveTitle(): String =
            context.getString(R.string.action_seek_to_percent, percent)

    override val defaultIcon: Drawable
        get() = AppCompatResources.getDrawable(
                context, com.svartifoss.snfell.common.R.drawable.action_seek_position)!!

    override fun writeToBundle(bundle: PersistableBundle) {
        super.writeToBundle(bundle)
        bundle.putInt(KEY_PERCENT, percent)
    }

    override fun isEqualToAction(other: PhoneAction): Boolean {
        other as SeekToPercentAction
        return super.isEqualToAction(other) && percent == other.percent
    }

    class Handler @Inject constructor(private val service: MusicService) :
            ActionHandler<SeekToPercentAction> {
        override suspend fun handleAction(action: SeekToPercentAction) {
            val controller = service.currentMediaController ?: return
            val duration = controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
            val target = seekPositionForPercent(duration, action.percent) ?: return
            controller.transportControls.seekTo(target)
        }
    }

    private companion object {
        const val KEY_PERCENT = "SEEK_PERCENT"
    }
}
