package com.svartifoss.snfell.music

/**
 * Which row of a queue is the track playing now, or -1 when it cannot be identified. The watch's
 * queue (through [MusicService.activeQueueIndex]) and the phone's queue window both ask this, so
 * the two mark the same row.
 *
 * Queue id first, title second, in that order for the reason `QueueScrollPolicy.activeRowIndex`
 * documents: the id is exact where it exists, and the title is the only thing left for the many
 * players that never publish one. Pure, so `ActiveQueueRowTest` pins it.
 */
object ActiveQueueRow {
    /** `MediaSession.QueueItem.UNKNOWN_ID`, written out so this stays free of Android. */
    const val UNKNOWN_ID = -1L

    fun index(
            queueIds: List<Long>,
            titles: List<String?>,
            activeQueueId: Long?,
            playingTitle: String?
    ): Int {
        val byId = activeQueueId
                ?.takeIf { it != UNKNOWN_ID }
                ?.let { id -> queueIds.indexOf(id) }
                ?: -1
        if (byId >= 0) return byId
        val title = playingTitle?.trim()?.takeIf { it.isNotEmpty() } ?: return -1
        return titles.indexOfFirst { it?.trim().equals(title, ignoreCase = true) }
    }
}
