package com.svartifoss.snfell.music

import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * On-disk cache of pictures looked up online fetched by [OnlineArtworkFetcher], keyed by artist name.
 *
 * It lives in `cacheDir` and trims itself, for the reason [RemoteArtworkCache] records at length:
 * a cache written into `filesDir` is not free, because `ConfigBackup` walks everything there and an
 * unbounded folder eventually fails either its own store's asset cap or the whole export. This one
 * grows with the number of distinct artists a person listens to, which has no ceiling.
 *
 * ## A miss is cached too, and it is the point
 *
 * [Entry.bytes] being null is a **recorded absence**: Deezer was asked about this artist and had
 * nothing, or the request failed. It has to be stored, because the alternative is re-querying on
 * every track of an album by an artist who will never have a picture - the case where the request
 * is worth least. [MISS_TTL_MS] is what keeps that from being permanent, since a failure can be a
 * dropped connection rather than a real absence; a hit has no expiry, because an artist's
 * photograph is not something this face needs to keep up with.
 */
object OnlineArtworkCache {
    private const val FOLDER = "online_artwork"

    private const val HIT_SUFFIX = ".jpg"
    private const val MISS_SUFFIX = ".miss"

    /** Comfortably more records than a listening history reaches between cache clears. Covers are
     * keyed per track and artists per performer, so a heavy album-listener fills this far slower
     * than a shuffler does. */
    const val MAX_ENTRIES = 300

    /** How long a recorded absence stands before the artist is worth asking about again. */
    const val MISS_TTL_MS = 7L * 24 * 60 * 60 * 1000

    /** A cached answer. [bytes] is null for a recorded absence - which is an answer, not a gap. */
    data class Entry(val bytes: ByteArray?) {
        override fun equals(other: Any?): Boolean =
                other is Entry && bytes.contentEquals(other.bytes)

        override fun hashCode(): Int = bytes?.contentHashCode() ?: 0
    }

    /** One cached file, as the eviction decision sees it. */
    data class Eviction(val name: String, val lastModifiedMs: Long)

    private fun folder(context: Context): File =
            File(context.cacheDir, FOLDER).apply { mkdirs() }

    private fun hitFile(context: Context, lookupKey: String): File =
            File(folder(context), key(lookupKey) + HIT_SUFFIX)

    private fun missFile(context: Context, lookupKey: String): File =
            File(folder(context), key(lookupKey) + MISS_SUFFIX)

    /**
     * The cached answer for [lookupKey], or null when this artist has never been looked up (or the
     * recorded absence has expired). Note the two nulls are different questions: this one means
     * "ask", while `Entry(null)` means "asked, nothing there".
     */
    fun get(context: Context, lookupKey: String): Entry? {
        val hit = hitFile(context, lookupKey)
        if (hit.exists()) {
            val bytes = try {
                hit.readBytes()
            } catch (_: Exception) {
                null
            }
            if (bytes != null && bytes.isNotEmpty()) return Entry(bytes)
        }
        val miss = missFile(context, lookupKey)
        if (miss.exists()) {
            if (!missExpired(miss.lastModified(), System.currentTimeMillis())) return Entry(null)
            miss.delete()
        }
        return null
    }

    /** Pure so the expiry rule is pinned by a JVM test rather than by the clock. */
    fun missExpired(writtenAtMs: Long, nowMs: Long): Boolean = nowMs - writtenAtMs >= MISS_TTL_MS

    /** Records [bytes] for [lookupKey], or the absence of a picture when it is null. */
    fun put(context: Context, lookupKey: String, bytes: ByteArray?) {
        if (bytes == null || bytes.isEmpty()) {
            writeAtomically(missFile(context, lookupKey), ByteArray(0))
        } else {
            missFile(context, lookupKey).delete()
            writeAtomically(hitFile(context, lookupKey), bytes)
        }
        trim(context)
    }

    private fun writeAtomically(target: File, bytes: ByteArray) {
        val temp = File(target.parentFile, target.name + ".tmp")
        try {
            temp.writeBytes(bytes)
            if (!temp.renameTo(target)) {
                target.delete()
                temp.renameTo(target)
            }
        } catch (_: Exception) {
            temp.delete()
        }
    }

    private fun trim(context: Context) {
        val files = folder(context).listFiles()?.filter {
            it.isFile && (it.name.endsWith(HIT_SUFFIX) || it.name.endsWith(MISS_SUFFIX))
        } ?: return
        if (files.size <= MAX_ENTRIES) return
        val doomed = evictions(files.map { Eviction(it.name, it.lastModified()) }, MAX_ENTRIES)
                .mapTo(HashSet()) { it.name }
        files.forEach { if (it.name in doomed) it.delete() }
    }

    /** Least recently written first, name as tie-break - see [RemoteArtworkCache.evictions]. */
    fun evictions(entries: List<Eviction>, max: Int): List<Eviction> {
        if (entries.size <= max) return emptyList()
        return entries.sortedWith(compareBy({ it.lastModifiedMs }, { it.name }))
                .take(entries.size - max)
    }

    /**
     * The filename for a lookup key (see [OnlineArtworkFetcher.cacheKeyFor]).
     *
     * Case- and whitespace-insensitive, because the same record reaches this from different players
     * spelled differently ("Daft Punk" / "daft punk ") and a second file would mean a second
     * network request for a picture already on disk.
     */
    fun key(lookupKey: String): String {
        val normalized = lookupKey.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
