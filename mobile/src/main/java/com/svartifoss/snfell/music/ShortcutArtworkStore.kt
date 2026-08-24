package com.svartifoss.snfell.music

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * On-disk cache of streaming-shortcut thumbnails fetched from the internet (see
 * [ShortcutArtworkFetcher]). Each thumbnail is keyed by its shortcut link so it is downloaded at
 * most once and then reused by the phone list, the assigned-action icon and the watch menu. This
 * only ever holds already-downloaded PNG bytes; it never performs any network access itself.
 */
object ShortcutArtworkStore {
    private const val FOLDER = "shortcut_artwork"

    private fun folder(context: Context): File =
            File(context.filesDir, FOLDER).apply { mkdirs() }

    /** Folder included by [com.svartifoss.snfell.config.ConfigBackup] with the shortcut library. */
    internal fun backupDirectory(context: Context): File = File(context.filesDir, FOLDER)

    private fun fileFor(context: Context, link: String): File =
            File(folder(context), hashKey(link) + ".png")

    fun has(context: Context, link: String): Boolean = fileFor(context, link).exists()

    /** Cached PNG bytes for [link], or null when nothing has been downloaded for it. */
    fun get(context: Context, link: String): ByteArray? {
        val file = fileFor(context, link)
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

    fun put(context: Context, link: String, pngBytes: ByteArray) {
        val target = fileFor(context, link)
        val temp = File(target.parentFile, target.name + ".tmp")
        try {
            temp.writeBytes(pngBytes)
            if (!temp.renameTo(target)) {
                target.delete()
                temp.renameTo(target)
            }
        } catch (_: Exception) {
            temp.delete()
        }
    }

    /** Drops cached files for links no longer present, so a deleted shortcut doesn't leak. */
    fun retainOnly(context: Context, links: Collection<String>) {
        val keep = links.mapTo(HashSet()) { hashKey(it) + ".png" }
        folder(context).listFiles()?.forEach { file ->
            if (file.name !in keep) file.delete()
        }
    }

    private fun hashKey(link: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(link.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
