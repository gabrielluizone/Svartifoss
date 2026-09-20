package com.svartifoss.snfell.actions

import com.svartifoss.snfell.music.PlaylistShortcut
import com.svartifoss.snfell.music.StreamingShortcutLinks

/**
 * The outcome of asking to put one streaming link on the watch menu.
 *
 * Adding a link to the menu used to be two chores in two places - save it in the Streaming
 * shortcuts library, then walk the action picker to assign it - and the library and the menu
 * could drift apart. This is the one decision that makes them a single step: the library entry the
 * menu action points at, whether that entry is new, and whether the menu needs a new row at all.
 */
data class ShortcutMenuAddition(
        /** The library after the addition (unchanged when the link was already saved). */
        val library: List<PlaylistShortcut>,
        /** The saved entry the menu action must be built from - name and link both. */
        val shortcut: PlaylistShortcut,
        val addedToLibrary: Boolean,
        /** False when the menu already carries this link, so a second copy would only be noise. */
        val addedToMenu: Boolean
)

object ShortcutMenuPlanner {

    /**
     * [menuLinks] are the links of the shortcut actions the menu already holds. Links are compared
     * in their canonical form so `open.spotify.com/...` and `https://open.spotify.com/...` are one
     * link, but never with the shuffle flag stripped: a shuffled and a plain YouTube Music playlist
     * are different destinations and each deserves its own row.
     *
     * A link already in the library keeps the name it was saved with. That entry is the single
     * source of truth its assigned copies are kept in step with (see [PlaylistShortcutActionSync]),
     * so a second, differently named copy of it would be a fork the sync could not reconcile.
     */
    fun plan(
            library: List<PlaylistShortcut>,
            menuLinks: Collection<String>,
            name: String,
            link: String
    ): ShortcutMenuAddition {
        val key = keyOf(link)
        val existing = library.firstOrNull { keyOf(it.link) == key }
        val shortcut = existing ?: PlaylistShortcut(name.trim(), link)
        return ShortcutMenuAddition(
                library = if (existing == null) library + shortcut else library,
                shortcut = shortcut,
                addedToLibrary = existing == null,
                addedToMenu = menuLinks.none { keyOf(it) == key }
        )
    }

    /** Whether [link] is already a row of the menu; the same comparison [plan] uses. */
    fun isInMenu(menuLinks: Collection<String>, link: String): Boolean {
        val key = keyOf(link)
        return menuLinks.any { keyOf(it) == key }
    }

    private fun keyOf(link: String): String = StreamingShortcutLinks.canonicalize(link)
}
