package com.svartifoss.snfell.actions

import android.content.Context
import com.svartifoss.snfell.config.ActionConfig
import com.svartifoss.snfell.config.buttons.ButtonConfig

/**
 * Keeps assigned [PlayPlaylistShortcutAction]s in sync with edits made to the saved-shortcut
 * library. Because an assignment bakes the shortcut's name and link into the action (so it can be
 * bound to a button, gesture, quick-panel slot or the action list independently), editing the
 * library entry would otherwise leave those copies pointing at the old name/link. This rewrites
 * every copy that targets the edited shortcut, through the normal commit path (disk + watch).
 */
object PlaylistShortcutActionSync {

    /** Rewrites every assigned shortcut action pointing at [oldLink] to [newName]/[newLink]. */
    fun propagateEdit(
            context: Context,
            config: ActionConfig,
            oldLink: String,
            newName: String,
            newLink: String
    ) {
        updateActionList(context, config, oldLink, newName, newLink)
        updateButtonConfig(context, config.getPlayingConfig(), oldLink, newName, newLink)
        updateButtonConfig(context, config.getStoppedConfig(), oldLink, newName, newLink)
    }

    /** Removes nothing, but stops orphaned copies from silently launching a deleted shortcut is
     *  intentionally NOT done here: a deleted library entry leaves its bound copies working, which
     *  matches how deleting the library never used to disturb existing button assignments. */

    private fun updateActionList(
            context: Context,
            config: ActionConfig,
            oldLink: String,
            newName: String,
            newLink: String
    ) {
        val actionList = config.getActionList()
        var changed = false
        val updated = actionList.actions.map { action ->
            if (action is PlayPlaylistShortcutAction && action.link == oldLink) {
                changed = true
                rewrite(context, action, newName, newLink)
            } else {
                action
            }
        }
        if (changed) {
            actionList.actions = updated
            actionList.commit()
        }
    }

    private fun updateButtonConfig(
            context: Context,
            config: ButtonConfig,
            oldLink: String,
            newName: String,
            newLink: String
    ) {
        // Collect first: saveButtonAction mutates the backing map, so we must not iterate it live.
        val matches = config.getAllActions()
                .filter { it.value is PlayPlaylistShortcutAction &&
                        (it.value as PlayPlaylistShortcutAction).link == oldLink }
                .map { it.key to (it.value as PlayPlaylistShortcutAction) }
        if (matches.isEmpty()) return
        matches.forEach { (buttonInfo, action) ->
            config.saveButtonAction(buttonInfo, rewrite(context, action, newName, newLink))
        }
        config.commit()
    }

    /** A fresh action with the new name/link, preserving any per-assignment custom title/icon the
     *  user set on that specific button (those are deliberate overrides, not the shortcut's own
     *  identity). Replacing the instance also refreshes the link-derived destination icon. */
    private fun rewrite(
            context: Context,
            action: PlayPlaylistShortcutAction,
            newName: String,
            newLink: String
    ): PlayPlaylistShortcutAction = PlayPlaylistShortcutAction(context, newName, newLink).also {
        it.customTitle = action.customTitle
        it.customIconUri = action.customIconUri
    }
}
