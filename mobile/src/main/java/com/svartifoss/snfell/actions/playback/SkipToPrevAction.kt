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

class SkipToPrevAction : SelectableAction {
    constructor(context: Context) : super(context)
    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle)

    override fun retrieveTitle(): String = context.getString(R.string.action_skip_prev)
    override val defaultIcon: Drawable
        get() = AppCompatResources.getDrawable(context, com.svartifoss.snfell.common.R.drawable.action_skip_prev)!!

    class Handler @Inject constructor(private val service: MusicService) : ActionHandler<SkipToPrevAction> {
        override suspend fun handleAction(action: SkipToPrevAction) {
            service.currentMediaController?.transportControls?.skipToPrevious()
        }
    }
}
