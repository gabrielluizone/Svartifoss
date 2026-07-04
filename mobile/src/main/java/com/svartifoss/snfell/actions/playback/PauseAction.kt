package com.svartifoss.snfell.actions.playback

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.PersistableBundle
import android.view.KeyEvent
import androidx.appcompat.content.res.AppCompatResources
import com.svartifoss.snfell.R
import com.svartifoss.snfell.actions.ActionHandler
import com.svartifoss.snfell.actions.SelectableAction
import com.svartifoss.snfell.music.MusicService
import javax.inject.Inject

class PauseAction : SelectableAction {
    constructor(context: Context) : super(context)
    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle)

    override fun retrieveTitle(): String = context.getString(R.string.action_pause)
    override val defaultIcon: Drawable
        get() = AppCompatResources.getDrawable(context, com.svartifoss.snfell.common.R.drawable.action_pause)!!

    class Handler @Inject constructor(private val service: MusicService) : ActionHandler<PauseAction> {
        override suspend fun handleAction(action: PauseAction) {
            service.currentMediaController?.let {
                it.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
                it.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
            }
        }
    }
}
