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

    const val SPECIAL_ITEM_ERROR = "ErrorItem"
}