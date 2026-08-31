package com.svartifoss.snfell.actions

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.PersistableBundle
import androidx.appcompat.content.res.AppCompatResources
import com.svartifoss.snfell.R

/**
 * Opens the watch's dedicated progress screen (seek, skip, playback speed).
 *
 * Fully watch-local like [OpenMenuAction] - no [Handler], no [di.ActionHandlersModule] binding -
 * position, duration and speed already arrive on every `MusicState`, so there is nothing to fetch
 * before the screen can draw itself.
 */
class OpenProgressScreenAction : SelectableAction {
    constructor(context: Context) : super(context)
    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle)

    override fun retrieveTitle(): String = context.getString(R.string.open_progress_screen)
    override val defaultIcon: Drawable
        get() = AppCompatResources.getDrawable(
                context, com.svartifoss.snfell.common.R.drawable.action_progress)!!
}
