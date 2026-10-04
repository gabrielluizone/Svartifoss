package com.svartifoss.snfell.watch.view.menu

import com.svartifoss.snfell.common.CustomLists

/**
 * What heads a list in the menu, named by what the list *is* rather than by how it was reached:
 * the actions menu, the saved shortcuts, a search's results, recent searches, a page of the
 * playing app's library, or the queue and its recently-played fallback (which have a screen of
 * their own, but can still land here when published while the menu is open).
 *
 * It used to have no heading at all. The rows began directly under the clock, so a library page,
 * a search's results and the shortcut list - three lists that look alike, reached in three
 * different ways - were told apart only by reading their rows, and nothing on screen said which
 * one the user had landed in.
 */
enum class MenuHeader {
    ACTIONS,
    SHORTCUTS,
    SEARCH_RESULTS,
    SEARCH_HISTORY,
    LIBRARY,
    UP_NEXT,
    RECENTLY_PLAYED;

    companion object {
        /** Null for a list this build does not know - a newer phone's - which then shows no
         *  heading rather than a wrong one. */
        fun forList(listId: String): MenuHeader? = when (listId) {
            CustomLists.PLAYLIST_SHORTCUTS -> SHORTCUTS
            CustomLists.SEARCH_RESULTS -> SEARCH_RESULTS
            CustomLists.SEARCH_HISTORY -> SEARCH_HISTORY
            CustomLists.LIBRARY -> LIBRARY
            CustomLists.PLAYLIST -> UP_NEXT
            CustomLists.HISTORY -> RECENTLY_PLAYED
            else -> null
        }
    }
}

/**
 * The menu's rows as positions in its list, which is not the same as positions in its content.
 *
 * The heading occupies the first slot, so the row the crown has centred is one place further down
 * the list than the entry it shows. A stem press confirms "the centred entry", and reading the list
 * position as an entry index would confirm the entry *below* the one on screen - the kind of
 * off-by-one nothing else would catch, which is why it lives in one tested place.
 */
object MenuListLayout {

    /** Slots above the first entry: the heading. */
    const val LEADING_ROWS = 1

    /** The entry centred at list position [centerItemIndex], or null while the heading is. */
    fun entryIndexAt(centerItemIndex: Int): Int? =
            (centerItemIndex - LEADING_ROWS).takeIf { it >= 0 }

    /**
     * Whether a list from the phone carries no entries, only its explanation - "No shortcuts yet",
     * "This app has no library", "No results". Those arrive as rows with the reserved error id, and
     * drawn as rows they looked like something to tap: a pill that did nothing, or (for shortcuts)
     * one that sent the phone a selection it then had to ignore.
     */
    fun isOnlyMessage(entryIds: List<String>): Boolean =
            entryIds.isNotEmpty() && entryIds.all { it == CustomLists.SPECIAL_ITEM_ERROR }
}
