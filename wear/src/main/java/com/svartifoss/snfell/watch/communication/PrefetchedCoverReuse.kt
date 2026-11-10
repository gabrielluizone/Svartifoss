package com.svartifoss.snfell.watch.communication

/**
 * Turns the asset ids of one neighbouring-cover announcement into pictures, decoding as few as it
 * can.
 *
 * A Data Layer asset id is the address of its content, so two entries carrying one id are one
 * picture, and an id this watch already holds decoded needs neither a read nor a decode. Both are
 * the ordinary case rather than the exception: stepping one track along keeps most of the window
 * (and the cover that was playing becomes the neighbour behind), and the tracks of an album share
 * one sleeve. Before this every announcement read and decoded all of its covers - four full-screen
 * bitmaps per track change - and an album kept four identical copies of the same picture in memory.
 *
 * A failed decode is remembered for the rest of the announcement too, so a second entry with the
 * same unreadable asset does not try again.
 */
internal object PrefetchedCoverReuse {

    suspend fun <T : Any> resolve(
            assetIds: List<String?>,
            held: (String) -> T?,
            decode: suspend (String) -> T?
    ): List<T?> {
        val resolved = HashMap<String, T?>()
        return assetIds.map { id ->
            when {
                id == null -> null
                resolved.containsKey(id) -> resolved[id]
                else -> (held(id) ?: decode(id)).also { resolved[id] = it }
            }
        }
    }
}
