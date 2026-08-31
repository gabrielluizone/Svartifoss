package com.svartifoss.snfell.actions

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.PersistableBundle
import androidx.appcompat.content.res.AppCompatResources
import com.svartifoss.snfell.R

/**
 * Opens the watch's dedicated volume screen.
 *
 * Fully watch-local like [OpenMenuAction] - no [Handler], no [di.ActionHandlersModule] binding -
 * because current volume already arrives on every `MusicState`, so there is nothing to fetch
 * before the screen can draw itself. `MusicViewModel.executeActionOnWatch` intercepts this key
 * before it would ever reach a phone-side handler.
 */
class OpenVolumeScreenAction : SelectableAction {
    constructor(context: Context) : super(context)
    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle)

    override fun retrieveTitle(): String = context.getString(R.string.open_volume_screen)
    override val defaultIcon: Drawable
        get() = AppCompatResources.getDrawable(
                context, com.svartifoss.snfell.common.R.drawable.action_volume_up)!!
}
