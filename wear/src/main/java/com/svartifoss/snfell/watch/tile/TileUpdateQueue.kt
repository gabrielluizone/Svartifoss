package com.svartifoss.snfell.watch.tile

/**
 * The bookkeeping half of [TileUpdateRequests]: which Tiles are waiting for a refresh, and whether
 * a connection to the system's update service is already on its way to carry them.
 *
 * Pure so the two rules that matter are pinned by a JVM test (`TileUpdateQueueTest`): a request
 * made while a connection is pending rides on that connection instead of opening a second one, and
 * a connection that ends - sent, refused, timed out - always leaves the queue ready to open the next.
 * The library requester this replaces had no second rule: a bind that failed left its "bind in
 * progress" flag set for good, so the instance could never request again.
 *
 * Not thread-safe; [TileUpdateRequests] touches it only on the main thread.
 */
class TileUpdateQueue<T> {
    private val waiting = LinkedHashSet<T>()

    /** Whether a connection has been started and has not ended yet. */
    var inFlight: Boolean = false
        private set

    /** Queues [tile]; true when the caller has to start a connection for it now. */
    fun add(tile: T): Boolean {
        waiting += tile
        if (inFlight) return false
        inFlight = true
        return true
    }

    /** The connection arrived: everything waiting goes out on it, each Tile once. */
    fun takeForSending(): List<T> {
        val tiles = waiting.toList()
        waiting.clear()
        return tiles
    }

    /**
     * The connection is over, however it ended. Anything still waiting is dropped rather than
     * retried in a loop: the next music state asks again, and a Tile also refreshes on its own
     * freshness interval.
     */
    fun release() {
        waiting.clear()
        inFlight = false
    }
}
