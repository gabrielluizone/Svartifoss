package com.svartifoss.snfell.music

/**
 * Queue thumbnails already encoded in this process, and the rows already found to have none.
 *
 * The queue is published again on every track change, and almost every row in it is one that was
 * published the time before - the window only moves by one. Without this, each publication ran the
 * whole artwork chain again for every row (a disk read or a MediaStore decode, a border trim, a
 * resize and a JPEG encode apiece) to produce bytes identical to the ones it produced a few minutes
 * earlier. With it, a track change resolves the one row that entered the window.
 *
 * A row that resolved to nothing is remembered as well, for [missTtlMs]: otherwise a streaming
 * queue whose covers cannot be fetched would retry every one of those downloads, each with its own
 * timeout, on every track change. The TTL is what lets a switch turned on in the meantime (remote
 * covers, the media permission) take effect without a restart.
 *
 * Bounded by the encoded bytes it holds, not by entry count, since a cover-style thumbnail is
 * several times the size of a list one. Pure and free of `android.*`, so the eviction and expiry
 * rules are pinned by a plain JVM test; the clock is injected for the same reason.
 */
class QueueThumbnailCache(
        private val maxBytes: Int,
        private val missTtlMs: Long,
        private val clock: () -> Long
) {
    /** What is known about one key. */
    sealed interface Lookup {
        /** Never resolved, or forgotten since - resolve it. */
        object Unknown : Lookup

        /** Resolved before, to these bytes. */
        class Hit(val bytes: ByteArray) : Lookup

        /** Resolved recently and nothing was found - do not resolve it again yet. */
        object KnownMiss : Lookup
    }

    private class Entry(val bytes: ByteArray?, val storedAtMs: Long) {
        /** What the entry costs against [maxBytes]. A miss holds no bytes but still takes a slot,
         *  so a queue full of coverless rows cannot grow the map without bound. */
        val cost: Int get() = (bytes?.size ?: 0) + ENTRY_OVERHEAD_BYTES
    }

    private val entries = LinkedHashMap<String, Entry>(16, 0.75f, true)
    private var usedBytes = 0

    @Synchronized
    fun lookup(key: String): Lookup {
        val entry = entries[key] ?: return Lookup.Unknown
        val bytes = entry.bytes
        if (bytes != null) return Lookup.Hit(bytes)
        if (clock() - entry.storedAtMs < missTtlMs) return Lookup.KnownMiss
        remove(key)
        return Lookup.Unknown
    }

    /** Remembers that [key] encodes to [bytes]. Anything larger than the whole budget is skipped. */
    @Synchronized
    fun putHit(key: String, bytes: ByteArray) = store(key, Entry(bytes, clock()))

    /** Remembers that [key] resolved to no artwork, for [missTtlMs]. */
    @Synchronized
    fun putMiss(key: String) = store(key, Entry(null, clock()))

    @Synchronized
    fun sizeBytes(): Int = usedBytes

    private fun store(key: String, entry: Entry) {
        remove(key)
        if (entry.cost > maxBytes) return
        entries[key] = entry
        usedBytes += entry.cost
        val eldest = entries.entries.iterator()
        while (usedBytes > maxBytes && eldest.hasNext()) {
            val oldest = eldest.next()
            eldest.remove()
            usedBytes -= oldest.value.cost
        }
    }

    private fun remove(key: String) {
        entries.remove(key)?.let { usedBytes -= it.cost }
    }

    private companion object {
        const val ENTRY_OVERHEAD_BYTES = 64
    }
}
