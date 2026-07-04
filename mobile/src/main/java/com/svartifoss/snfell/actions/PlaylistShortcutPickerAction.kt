package com.svartifoss.snfell.actions

import android.app.Activity
import android.content.Intent
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.PersistableBundle
import androidx.appcompat.content.res.AppCompatResources
import com.svartifoss.snfell.R
import com.svartifoss.snfell.view.ActivityResultReceiver
import com.svartifoss.snfell.view.buttonconfig.ActionPickerViewModel
import com.svartifoss.snfell.view.settings.PlaylistShortcutsActivity

/**
 * "Open specific playlist" entry in the action picker: opens [PlaylistShortcutsActivity] in
 * pick mode (the same screen used to manage the shortcuts, so a missing playlist can be added
 * on the spot) and turns the chosen shortcut into a [PlayPlaylistShortcutAction] - the same
 * pick-then-parameterize flow as [com.svartifoss.snfell.actions.tasker.TaskerTaskPickerAction].
 */
class PlaylistShortcutPickerAction : PhoneAction, ActivityResultReceiver {
    constructor(context: Context) : super(context)
    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle)

    override val opensMoreOptions: Boolean
        get() = true

    private var prevActionPicker: ActionPickerViewModel? = null

    override fun retrieveTitle(): String = context.getString(R.string.action_play_specific_playlist)

    override val defaultIcon: Drawable
        get() = AppCompatResources.getDrawable(context, com.svartifoss.snfell.common.R.drawable.action_open_playlist)!!

    override fun onActionPicked(actionPicker: ActionPickerViewModel) {
        prevActionPicker = actionPicker
        actionPicker.startActivityForResult(PlaylistShortcutsActivity.createPickIntent(context), this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK || data == null) {
            return
        }

        val name = data.getStringExtra(PlaylistShortcutsActivity.EXTRA_PICKED_NAME) ?: return
        val link = data.getStringExtra(PlaylistShortcutsActivity.EXTRA_PICKED_LINK) ?: return

        prevActionPicker?.selectedAction?.value = PlayPlaylistShortcutAction(context, name, link)
    }
}
