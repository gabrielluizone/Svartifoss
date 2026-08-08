package com.svartifoss.snfell.common

object CustomLists {
    const val PLAYLIST = "Playlist"

    /**
     * Fallback shown instead of [PLAYLIST] when the playing app doesn't expose its queue
     * (a common restriction on Android 10+ for many apps) - a locally tracked list of
     * recently played tracks rather than the live upcoming queue.
     */
    const val HISTORY = "History"

    /** Results of a [com.svartifoss.snfell.actions.SearchAction] library search. Selecting
     *  an entry plays it via `playFromMediaId` instead of `skipToQueueItem` - see
     *  MusicService.onCustomMenuItemPresed. */
    const val SEARCH_RESULTS = "SearchResults"

    /** User-defined playlist shortcuts (name + deep link) configured in the phone app. The
     *  entry id is the playlist's link; selecting one opens it on the phone - see
     *  MusicService.onCustomMenuItemPresed. */
    const val PLAYLIST_SHORTCUTS = "PlaylistShortcuts"

    /** Recently searched queries, newest first (see SearchHistoryStorage). The entry id IS the
     *  original query text; selecting one re-runs that search, and the watch can delete
     *  individual entries (see CommPaths.MESSAGE_DELETE_CUSTOM_LIST_ITEM). */
    const val SEARCH_HISTORY = "SearchHistory"

    /**
     * One page of the playing app's browsable library (the MediaBrowserService tree Android Auto
     * uses). Entry ids are encoded by [LibraryEntry] because the *watch* has to know whether a row
     * opens a folder or starts playback: selecting a folder must leave the menu open so the next
     * page can swap in, while selecting a track closes it.
     */
    const val LIBRARY = "Library"

    const val SPECIAL_ITEM_ERROR = "ErrorItem"
}

/**
 * Encoding for [CustomLists.LIBRARY] entry ids, shared so the phone and the watch cannot disagree
 * about what a row does.
 *
 * A prefix rather than a new protobuf field: the id is already an opaque token the watch round-trips
 * untouched, and the same `<kind>|<payload>` shape is what action `remoteUri` values use. The media
 * id itself may contain further `|` characters, so decoding splits once and only once.
 */
object LibraryEntry {
    private const val BROWSABLE_PREFIX = "b|"
    private const val PLAYABLE_PREFIX = "p|"

    /** Row that goes back up one level. Carries no media id. */
    const val UP = "^up"

    fun browsable(mediaId: String): String = BROWSABLE_PREFIX + mediaId

    fun playable(mediaId: String): String = PLAYABLE_PREFIX + mediaId

    /** Whether selecting this row opens another page rather than starting playback. */
    fun isBrowsable(entryId: String): Boolean =
            entryId == UP || entryId.startsWith(BROWSABLE_PREFIX)

    /**
     * The underlying media id, or null for [UP] and for anything not produced by this object -
     * an unrecognised id must never be handed to the media browser as if it were a real node.
     */
    fun mediaId(entryId: String): String? = when {
        entryId.startsWith(BROWSABLE_PREFIX) -> entryId.removePrefix(BROWSABLE_PREFIX)
        entryId.startsWith(PLAYABLE_PREFIX) -> entryId.removePrefix(PLAYABLE_PREFIX)
        else -> null
    }
}