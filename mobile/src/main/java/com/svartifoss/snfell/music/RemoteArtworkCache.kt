package com.svartifoss.snfell.music

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * On-disk cache of queue covers downloaded from a streaming client's remote artwork URL (see
 * [QueueArtworkResolver]).
 *
 * This used to share [ShortcutArtworkStore], on the reasoning that both are plain URL-keyed PNG
 * caches so a second one would buy nothing. What that missed is that the two have opposite
 * lifetimes, and both halves of the mismatch bite. A shortcut thumbnail is *user data*: one file
 * per saved link, backed up alongside the shortcut library and pruned by
 * [ShortcutArtworkStore.retainOnly] when its shortcut goes. A queue cover is disposable and
 * effectively unbounded - every remote row of every queue ever opened leaves one behind, and the
 * feature is on by default. So the shared folder meant `retainOnly` wiped the whole queue cache
 * whenever the shortcut library was edited, and - the reported failure - a few hundred queue
 * covers pushed the shortcut asset store past [com.svartifoss.snfell.config.ConfigBackup]'s
 * per-store cap, which aborts the *entire* export with "Too many shortcutArtwork assets to back
 * up". A backup that a music queue can break is not a backup.
 *
 * It therefore lives in `cacheDir`, which the backup deliberately never walks (a cache is
 * regenerable, and Android may drop it at any time), and it trims itself to [MAX_ENTRIES] rather
 * than growing forever. Like the store it left, it only ever holds already-downloaded bytes and
 * never performs network access itself.
 */
object RemoteArtworkCache {
    private const val FOLDER = "queue_artwork"

    /**
     * Enough to hold several full queues ([com.svartifoss.snfell.common.QueuePaging] caps one at
     * 200 rows) without re-downloading, at list-thumbnail sizes.
     */
    const val MAX_ENTRIES = 400

    /** One cached file, as the eviction decision sees it. */
    data class Entry(val name: String, val lastModifiedMs: Long)

    private fun folder(context: Context): File =
            File(context.cacheDir, FOLDER).apply { mkdirs() }

    private fun fileFor(context: Context, url: String): File =
            File(folder(context), hashKey(url) + ".png")

    /** Cached PNG bytes for [url], or null when nothing has been downloaded for it. */
    fun get(context: Context, url: String): ByteArray? {
        val file = fileFor(context, url)
        return if (file.exists()) {
            try {
                file.readBytes()
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    fun put(context: Context, url: String, pngBytes: ByteArray) {
        val target = fileFor(context, url)
        val temp = File(target.parentFile, target.name + ".tmp")
        try {
            temp.writeBytes(pngBytes)
            if (!temp.renameTo(target)) {
                target.delete()
                temp.renameTo(target)
            }
        } catch (_: Exception) {
            temp.delete()
            return
        }
        trim(context)
    }

    private fun trim(context: Context) {
        val files = folder(context).listFiles()?.filter { it.isFile && it.name.endsWith(".png") }
                ?: return
        if (files.size <= MAX_ENTRIES) return
        val doomed = evictions(files.map { Entry(it.name, it.lastModified()) }, MAX_ENTRIES)
                .mapTo(HashSet()) { it.name }
        files.forEach { if (it.name in doomed) it.delete() }
    }

    /**
     * Which entries to drop so at most [max] remain: least recently written first, with the name
     * as tie-break because a filesystem may only report whole-second timestamps and an eviction
     * that picks differently on each pass would keep re-downloading the same covers.
     */
    fun evictions(entries: List<Entry>, max: Int): List<Entry> {
        if (entries.size <= max) return emptyList()
        return entries.sortedWith(compareBy({ it.lastModifiedMs }, { it.name }))
                .take(entries.size - max)
    }

    private fun hashKey(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
