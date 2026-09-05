package com.svartifoss.snfell.actions

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.PersistableBundle
import androidx.appcompat.content.res.AppCompatResources
import com.svartifoss.snfell.R

/** Destinations rendered locally on the watch. */
class WatchScreenActionList : PickerActionGroup {
    constructor(context: Context) : super(context)
    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle)

    override fun pickerChildren(): List<PhoneAction> = listOf(
            OpenMenuAction(context),
            OpenQuickActionsPanelAction(context),
            OpenPlaylistAction(context),
            OpenLyricsAction(context),
            OpenVolumeScreenAction(context),
            OpenProgressScreenAction(context),
            OpenFacePickerAction(context),
            CloseWatchAppAction(context))

    override fun retrieveTitle(): String = context.getString(R.string.group_watch_screens)
    override val defaultIcon: Drawable
        get() = AppCompatResources.getDrawable(
                context, com.svartifoss.snfell.common.R.drawable.action_open_menu)!!
}
