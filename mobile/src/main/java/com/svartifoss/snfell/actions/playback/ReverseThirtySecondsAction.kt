package com.svartifoss.snfell.actions.playback

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.PersistableBundle
import androidx.appcompat.content.res.AppCompatResources
import com.svartifoss.snfell.R
import com.svartifoss.snfell.actions.ActionHandler
import com.svartifoss.snfell.actions.PhoneAction
import com.svartifoss.snfell.actions.SelectableAction
import com.svartifoss.snfell.music.MusicService
import com.svartifoss.snfell.view.actionconfigs.ActionConfigFragment
import com.svartifoss.snfell.view.actionconfigs.ReverseSecondsConfigFragment
import javax.inject.Inject

/**
 * Class name needs to be kept at "thirty seconds" for backwards compatibility
 */
class ReverseThirtySecondsAction : SelectableAction {
    constructor(context: Context) : super(context)
    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle) {
        secondsToReverse = bundle.getInt(KEY_SECONDS_TO_REVERSE, DEFAULT_SECONDS_TO_REVERSE)
    }

    var secondsToReverse: Int = DEFAULT_SECONDS_TO_REVERSE

    override fun retrieveTitle(): String = context.getString(R.string.action_reverse_seconds, secondsToReverse)
    override val defaultIcon: Drawable
        get() = AppCompatResources.getDrawable(context, com.svartifoss.snfell.common.R.drawable.action_reverse_30_seconds)!!
    override val configFragment: Class<out ActionConfigFragment<out PhoneAction>>
        get() = ReverseSecondsConfigFragment::class.java

    override fun writeToBundle(bundle: PersistableBundle) {
        super.writeToBundle(bundle)

        bundle.putInt(KEY_SECONDS_TO_REVERSE, secondsToReverse)
    }

    class Handler @Inject constructor(private val service: MusicService) : ActionHandler<ReverseThirtySecondsAction> {
        override suspend fun handleAction(action: ReverseThirtySecondsAction) {
            val currentPos = service.currentMediaController?.playbackState?.position ?: return
            service.currentMediaController?.transportControls?.seekTo(
                    (currentPos - action.secondsToReverse * 1_000).coerceAtLeast(0)
            )
        }
    }
}

private const val KEY_SECONDS_TO_REVERSE = "SECONDS_TO_REVERSE"
private const val DEFAULT_SECONDS_TO_REVERSE = 30
