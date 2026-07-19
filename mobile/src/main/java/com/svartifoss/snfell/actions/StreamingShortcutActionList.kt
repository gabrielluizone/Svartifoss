package com.svartifoss.snfell.actions

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.PersistableBundle
import androidx.appcompat.content.res.AppCompatResources
import com.svartifoss.snfell.R
import com.svartifoss.snfell.music.PlaylistShortcutStorage
import com.svartifoss.snfell.view.buttonconfig.ActionPickerViewModel

/**
 * Action-picker category backed by the same library as Streaming shortcuts. Saved tracks,
 * albums, mixes and playlists are exposed directly as assignable actions; no second list or
 * preference is created for the Actions tab.
 */
class StreamingShortcutActionList : PhoneAction {
    constructor(context: Context) : super(context)
    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle)

    override val opensMoreOptions: Boolean
        get() = true

    override fun onActionPicked(actionPicker: ActionPickerViewModel) {
        val shortcuts = PlaylistShortcutStorage.load(context)
        val actions = ArrayList<PhoneAction>(shortcuts.size + 2)

        actions.addAll(shortcuts.map { shortcut ->
            PlayPlaylistShortcutAction(context, shortcut.name, shortcut.link)
        })

        // Pick mode uses PlaylistShortcutStorage itself and also lets an empty/new library be
        // populated without leaving the action assignment flow.
        actions.add(PlaylistShortcutPickerAction(context).apply {
            customTitle = context.getString(R.string.action_choose_or_add_streaming_shortcut)
        })

        if (shortcuts.isNotEmpty()) {
            // This assigns the action that opens the complete saved library on the watch, useful
            // when one Quick Action should provide access to more than a single link.
            actions.add(OpenPlaylistShortcutsAction(context).apply {
                customTitle = context.getString(R.string.action_show_streaming_library)
            })
        }

        actionPicker.updateDisplayedActionsWithBackStack(actions)
    }

    override fun retrieveTitle(): String =
            context.getString(R.string.group_streaming_shortcuts)

    override val defaultIcon: Drawable
        get() = AppCompatResources.getDrawable(
                context,
                com.svartifoss.snfell.common.R.drawable.action_open_playlist
        )!!
}
