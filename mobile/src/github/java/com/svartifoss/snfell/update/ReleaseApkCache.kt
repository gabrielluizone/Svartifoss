package com.svartifoss.snfell.update

import android.content.Context
import java.io.File

/**
 * Where downloaded release APKs wait between the download and whatever uses them, and when they go.
 *
 * Both used to be answered the same way: the APK sat at a fixed name in the cache directory and was
 * deleted the moment the step after the download ended, **whatever that step's outcome**. The watch
 * push removed the wear APK in a `finally`, so a push that failed after a complete download (no
 * watch in range, a Bluetooth drop, the screen closed during the transfer) discarded 25 MB the user
 * had just waited for; nothing kept a half-finished download either. A file here is instead kept
 * until it is no longer needed:
 *
 *  - named by release and by which device it is for, so a stale one is never mistaken for the
 *    current release and the phone's and the watch's cannot collide;
 *  - removed once the device it is for is up to date, when a newer release has replaced its tag,
 *    or - for an unfinished download - after [PARTIAL_MAX_AGE_MS], since a release that old has
 *    long since been superseded;
 *  - left to the system otherwise: this is `cacheDir`, which Android clears when it needs the
 *    space, and the downloader treats a missing file as simply not downloaded yet.
 *
 * Context-free on purpose (it takes the directory), so the naming and pruning rules are plain
 * JVM-testable code.
 */
class ReleaseApkCache(private val directory: File) {

    /** Which device an APK is for. */
    enum class Kind(internal val prefix: String) {
        PHONE("phone"),
        WATCH("wear")
    }

    /** The file for [kind] at release [tag]; its partial download sits beside it (`.part`). */
    fun apkFor(kind: Kind, tag: String): File {
        directory.mkdirs()
        return File(directory, "${kind.prefix}-${safeTag(tag)}.apk")
    }

    /**
     * Deletes what is no longer worth keeping.
     *
     * @param keepTag the release the screen is about to offer. Files for any other release are
     *   deleted; null keeps every release (used when the newest release is not known).
     * @param finished the kinds whose device is already on [keepTag] or newer. Everything for them
     *   is deleted, completed or not.
     */
    fun prune(keepTag: String?, finished: Set<Kind> = emptySet(), nowMs: Long = System.currentTimeMillis()) {
        val keep = keepTag?.let(::safeTag)
        for (file in directory.listFiles().orEmpty()) {
            val entry = parse(file.name) ?: continue
            val superseded = keep != null && entry.safeTag != keep
            val expiredPartial = entry.partial && nowMs - file.lastModified() > PARTIAL_MAX_AGE_MS
            if (entry.kind in finished || superseded || expiredPartial) file.delete()
        }
    }

    /** Deletes everything for [kind], completed or not. */
    fun discard(kind: Kind) {
        for (file in directory.listFiles().orEmpty()) {
            if (parse(file.name)?.kind == kind) file.delete()
        }
    }

    /** Deletes the cached APK for [kind] at [tag] and its partial download. */
    fun discard(kind: Kind, tag: String) {
        val apk = apkFor(kind, tag)
        apk.delete()
        ApkDownloader.partialFor(apk).delete()
    }

    internal data class Entry(val kind: Kind, val safeTag: String, val partial: Boolean)

    internal fun parse(name: String): Entry? {
        val partial = name.endsWith(PARTIAL_SUFFIX)
        val withoutPartial = name.removeSuffix(PARTIAL_SUFFIX)
        if (!withoutPartial.endsWith(APK_SUFFIX)) return null
        val stem = withoutPartial.removeSuffix(APK_SUFFIX)
        val kind = Kind.entries.firstOrNull { stem.startsWith(it.prefix + "-") } ?: return null
        val tag = stem.removePrefix(kind.prefix + "-")
        return if (tag.isEmpty()) null else Entry(kind, tag, partial)
    }

    companion object {
        private const val DIRECTORY_NAME = "updates"
        private const val APK_SUFFIX = ".apk"
        private const val PARTIAL_SUFFIX = ".part"

        /** An unfinished download this old belongs to a release that has been replaced. */
        const val PARTIAL_MAX_AGE_MS = 14L * 24 * 60 * 60 * 1000

        /** Names the updater used before the cache existed, directly in the cache directory. */
        private val LEGACY_NAMES = listOf("wear-update.apk", "phone-update.apk")

        fun of(context: Context): ReleaseApkCache {
            removeLegacyFiles(context.cacheDir)
            return ReleaseApkCache(File(context.cacheDir, DIRECTORY_NAME))
        }

        /** A tag as a file-name part: GitHub tags are free text, and a path separator in one must
         *  not reach the filesystem. */
        internal fun safeTag(tag: String): String =
                tag.map { if (it.isLetterOrDigit() || it == '.' || it == '-') it else '_' }
                        .joinToString("")
                        .ifEmpty { "_" }

        private fun removeLegacyFiles(cacheDir: File) {
            for (name in LEGACY_NAMES) File(cacheDir, name).delete()
        }
    }
}
