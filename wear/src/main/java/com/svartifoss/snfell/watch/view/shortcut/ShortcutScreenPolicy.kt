package com.svartifoss.snfell.watch.view.shortcut

import com.svartifoss.snfell.common.CustomLists

/**
 * When picking a streaming shortcut opens its own screen - cover, name, Play beside Shuffle -
 * instead of starting it at once.
 *
 * The rule is "picked from a list", and it is the same rule in every list that holds shortcuts:
 * the shortcut list, the Shortcuts Tile, the actions menu and the quick panel's rows. A button or a
 * gesture assigned to one shortcut never reaches here - it is one deliberate press for one
 * deliberate thing, and a screen in its way would defeat the reason it was assigned.
 *
 * Pure so the fallbacks are pinned: each `false` below is a pick that starts at once exactly as it
 * did before the screen existed, which is the only safe answer whenever the phone has not said
 * enough to make the screen honest.
 */
object ShortcutScreenPolicy {

    /**
     * A row of the streaming-shortcut list, or a chip on the Shortcuts Tile (which lists the same
     * entries).
     *
     * [phoneKnowsPlayModes] is whether the phone sent `shuffleable` with the entry. A phone build
     * from before play modes sends nothing there and ignores the mode it would be asked for, so
     * the screen would cost a tap to offer a choice that changes nothing.
     */
    fun opensForListEntry(
            enabled: Boolean,
            listId: String,
            entryId: String,
            phoneKnowsPlayModes: Boolean
    ): Boolean = enabled &&
            phoneKnowsPlayModes &&
            listId == CustomLists.PLAYLIST_SHORTCUTS &&
            entryId.isNotBlank() &&
            entryId != CustomLists.SPECIAL_ITEM_ERROR

    /**
     * An entry of the actions menu (or the quick panel's rows, which list the same entries).
     *
     * Only an entry the phone described as a streaming destination qualifies - a shortcut
     * subtitle is sent for exactly those - and only one with a link to play, since the screen
     * starts it through the shortcut path with that link.
     */
    fun opensForMenuAction(
            enabled: Boolean,
            remoteUri: String?,
            shortcutSubtitle: String?
    ): Boolean = enabled && !remoteUri.isNullOrBlank() && shortcutSubtitle != null
}
