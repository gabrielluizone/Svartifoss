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

        // Pick mode uses PlaylistShortcutStorage itself and also lets an empty/new library be
        // populated without leaving the action assignment flow - the short path that saves the
        // user a round trip through Settings > Apps > Streaming shortcuts and back.
        val addEntry = PlaylistShortcutPickerAction(context).apply {
            customTitle = context.getString(R.string.action_choose_or_add_streaming_shortcut)
        }

        // Position depends on whether there is a library at all. With saved shortcuts the list
        // itself is what the user came for, so "add another" belongs under it, as in any
        // list-with-add-affordance. With an empty library every row below would be nothing but
        // this one, and putting it last framed the category as a dead end - the whole feature is
        // reachable only through this entry, so on an empty library it leads.
        if (shortcuts.isEmpty()) {
            actions.add(addEntry)
        }

        actions.addAll(shortcuts.map { shortcut ->
            PlayPlaylistShortcutAction(context, shortcut.name, shortcut.link)
        })

        if (shortcuts.isNotEmpty()) {
            actions.add(addEntry)

            // This assigns the action that opens the complete saved library on the watch, useful
            // when one Quick Action should provide access to more than a single link.
            actions.add(OpenPlaylistShortcutsAction(context).apply {
                customTitle = context.getString(R.string.action_show_streaming_library)
            })
        }

        actionPicker.updateDisplayedActionsWithBackStack(title, actions)
    }

    override fun retrieveTitle(): String =
            context.getString(R.string.group_streaming_shortcuts)

    override val defaultIcon: Drawable
        get() = AppCompatResources.getDrawable(
                context,
                com.svartifoss.snfell.common.R.drawable.action_open_playlist
        )!!
}
