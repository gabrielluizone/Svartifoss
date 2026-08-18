package com.svartifoss.snfell.common

object CustomLists {
    /** Entry ids are encoded by [QueueEntry] - see MusicService.onCustomMenuItemPresed for why a
     *  queue tap needs more than the position id most players accept. */
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

/**
 * Encoding for [CustomLists.PLAYLIST] entry ids: the queue position id every player accepts via
 * `skipToQueueItem` *when it implements that action at all*, paired with the item's own media id
 * for the one player known not to (Retro Music, on neither of its two sessions - see
 * docs/player-integration-notes.md). Not implementing skip-to-queue-item is not the same as
 * publishing no queue: Retro Music's browser session still hands out real, playable queue items,
 * it just never answers that specific transport command, so the tap needs a second way to reach
 * the same track.
 *
 * `<queueId>|<mediaId>`, split once on the first `|` since the media id itself may contain further
 * `|` characters - the same shape and reasoning as [LibraryEntry]. [mediaId] is empty exactly when
 * the queue item never had one, which some sessions omit.
 */
object QueueEntry {
    fun encode(queueId: Long, mediaId: String?): String = "$queueId|${mediaId.orEmpty()}"

    /** [CustomLists.SPECIAL_ITEM_ERROR]-shaped or otherwise malformed ids decode to
     *  `android.media.session.MediaSession.QueueItem.UNKNOWN_ID`, never a real position - callers
     *  must not act on that value as if it were one. */
    fun queueId(entryId: String): Long = entryId.substringBefore('|').toLongOrNull() ?: -1L

    fun mediaId(entryId: String): String? =
            entryId.substringAfter('|', "").takeIf { it.isNotEmpty() }
}

/**
 * How much of a long playback queue the watch asks for at a time.
 *
 * Shared rather than a phone-side detail because both ends need the same numbers: the watch sizes
 * its "load more" step by them, the phone truncates by them, and a disagreement shows up as a
 * "Load more" row that fetches nothing or one that never appears.
 *
 * The queue is paged at all because every entry carries its own thumbnail asset across Bluetooth -
 * sending a 200-track queue eagerly is a long wait for a list most people never scroll to the end
 * of. Requests are cumulative (each asks for a larger prefix, replacing the previous list) rather
 * than incremental, because the phone publishes the queue as one DataItem it replaces wholesale.
 */
object QueuePaging {
    /** Entries in the first page, and the step each "load more" adds. */
    const val PAGE_SIZE = 20

    /** Ceiling on one request, so a pathological queue cannot be paged into a payload large enough
     *  to fail the transfer. Reached only by someone repeatedly asking for more. */
    const val MAX_ENTRIES = 200

    /** The size of the next request after [loaded] entries, clamped to [MAX_ENTRIES]. */
    fun nextLimit(loaded: Int): Int = (loaded + PAGE_SIZE).coerceAtMost(MAX_ENTRIES)
}