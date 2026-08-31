package com.svartifoss.snfell.actions

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.PersistableBundle
import com.svartifoss.snfell.actions.appplay.AppPlayPickerAction
import com.svartifoss.snfell.actions.playback.PlaybackActionList
import com.svartifoss.snfell.actions.tasker.TaskerTaskPickerAction
import com.svartifoss.snfell.actions.volume.VolumeActionList
import com.svartifoss.snfell.view.buttonconfig.ActionPickerViewModel
import com.matejdro.wearutils.tasker.TaskerIntent

class RootActionList : PhoneAction {
    private val showNone: Boolean

    constructor(context: Context, showNone: Boolean) : super(context) {
        this.showNone = showNone
    }

    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle) {
        this.showNone = true
    }

    override fun onActionPicked(actionPicker: ActionPickerViewModel) {
        val actions = ArrayList<PhoneAction>(20)

        if (showNone) {
            actions.add(NullAction(context))
        }

        actions.addAll(listOf(PlaybackActionList(context),
                VolumeActionList(context),
                AppPlayPickerAction(context),
                StreamingShortcutActionList(context),
                OpenMenuAction(context),
                OpenQuickActionsPanelAction(context),
                OpenPlaylistAction(context),
                OpenLyricsAction(context),
                OpenVolumeScreenAction(context),
                OpenProgressScreenAction(context),
                SearchAction(context),
                OpenSearchHistoryAction(context),
                OpenLibraryAction(context)
        ))

        if (isTaskerInstalled()) {
            actions.add(TaskerTaskPickerAction(context))
        }

        actionPicker.displayedActions.value = actions
    }

    override fun retrieveTitle(): String {
        throw UnsupportedOperationException()
    }

    override val defaultIcon: Drawable
        get() {
            throw UnsupportedOperationException()
        }

    private fun isTaskerInstalled(): Boolean = TaskerIntent.getInstalledTaskerPackage(context) != null
}
