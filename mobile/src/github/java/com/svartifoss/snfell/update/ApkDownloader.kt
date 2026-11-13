package com.svartifoss.snfell.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * Downloads a release APK to a file, reporting 0..100% progress. Shared by [WatchApkPusher] (wear
 * APK, then streamed to the watch) and [PhoneApkInstaller] (phone APK, then handed to the system
 * installer) so the HTTP/streaming details live in one place.
 *
 * **A download is never thrown away.** These APKs are 25 to 36 MB, over a phone connection, and
 * the user is in the middle of an update screen they can leave at any moment, so every way a
 * download can stop short used to cost the whole thing:
 *
 *  - Bytes land in `<target>.part` and only become [target] once the file is whole and checked.
 *    A stop at any point - leaving the screen, a dropped connection, the process dying - keeps the
 *    prefix, and the next call asks the server for the rest (`Range`) instead of starting again.
 *    GitHub's release-asset host answers `206` with a `Content-Range`, so this is the ordinary case
 *    and not a hope; a server that ignores `Range` just gets the file from the start, as before.
 *  - A [target] that is already there and still good is used as it is, with no network at all, so
 *    a download that succeeded but whose next step failed (no watch in range, a Bluetooth drop, the
 *    install prompt dismissed) is not repeated.
 *
 * "Still good" is judged against what the release published: its size, its SHA-256 when the API
 * gave one, and [isUsable] (the caller's own check that it is an APK for this app). A file that
 * fails any of them is deleted rather than trusted, so a bad copy costs one more download and never
 * a wedged update.
 */
object ApkDownloader {

    /** What [download] did, for the log and for tests. */
    enum class Outcome {
        /** A complete, verified copy was already on disk; the network was not touched. */
        REUSED,

        /** The file was downloaded now, whole or finished from an earlier partial. */
        DOWNLOADED
    }

    /**
     * How many times one call tries before giving up. A stream that ends short or breaks mid-way is
     * resumed from the bytes already kept, but only while each attempt gets somewhere, so a dead
     * connection cannot spin: attempts that add nothing stop at once.
     */
    private const val MAX_ATTEMPTS = 3

    /** The partial file for [target]: the same name plus `.part`, in the same directory so that
     *  promoting it is a rename on one filesystem. */
    fun partialFor(target: File): File = File(target.parentFile, target.name + ".part")

    /**
     * @param expectedSize the published size: the fallback total for the percentage when the
     *   server sends no Content-Length (0 if unknown), and what the finished file is checked
     *   against (skipped when 0).
     * @param sha256 the published SHA-256 digest of the asset, hex, optionally prefixed with
     *   `sha256:` as GitHub writes it. Null when the release did not carry one.
     * @param isUsable the caller's check that [target] is a file it can use - for the updater, an
     *   APK of this very package. Applied to a file that was already there and to a finished
     *   download alike.
     * @throws IOException when the file could not be completed. The partial bytes are kept for the
     *   next call unless they are what failed verification, in which case they are deleted.
     */
    suspend fun download(
            url: String,
            expectedSize: Long,
            target: File,
            sha256: String? = null,
            isUsable: (File) -> Boolean = { true },
            onPercent: (Int) -> Unit
    ): Outcome = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()

        if (target.isFile) {
            if (isComplete(target, expectedSize, sha256, isUsable)) {
                onPercent(100)
                return@withContext Outcome.REUSED
            }
            // Present but not the file this release published: older bytes, or damaged.
            target.delete()
        }

        val partial = partialFor(target)
        var attempt = 0
        while (true) {
            attempt++
            val before = partial.takeIf { it.isFile }?.length() ?: 0L
            try {
                fetchInto(url, expectedSize, partial, onPercent)
            } catch (e: IOException) {
                val gained = (partial.takeIf { it.isFile }?.length() ?: 0L) - before
                if (attempt >= MAX_ATTEMPTS || gained <= 0L) throw e
                continue
            }

            val have = partial.length()
            if (expectedSize <= 0 || have == expectedSize) break
            // A body that ends short without an error: the HttpURLConnection redirect truncation
            // this class has always guarded against. Reported only if it will not resume.
            if (attempt >= MAX_ATTEMPTS || have <= before) {
                throw IOException("Download incomplete: got $have of $expectedSize bytes for $url")
            }
        }

        if (!verify(partial, expectedSize, sha256)) {
            // The kept prefix is what is wrong, so keeping it would wedge every later attempt.
            partial.delete()
            throw IOException("Downloaded file does not match the published release: $url")
        }
        if (!partial.renameTo(target)) {
            partial.copyTo(target, overwrite = true)
            partial.delete()
        }
        if (!isUsable(target)) {
            target.delete()
            throw IOException("Downloaded file is not a usable APK: $url")
        }
        Outcome.DOWNLOADED
    }

    private fun isComplete(
            file: File,
            expectedSize: Long,
            sha256: String?,
            isUsable: (File) -> Boolean
    ): Boolean = verify(file, expectedSize, sha256) && isUsable(file)

    private fun verify(file: File, expectedSize: Long, sha256: String?): Boolean {
        if (!file.isFile) return false
        if (expectedSize > 0 && file.length() != expectedSize) return false
        val expectedDigest = normalizeSha256(sha256) ?: return true
        return sha256Hex(file) == expectedDigest
    }

    /**
     * One request: asks for the rest of [partial] and appends it, or - when the server does not
     * honour the range - replaces it. Returns normally when the stream ended, whole or not; the
     * caller compares the length. Throws on an HTTP error or a broken connection, leaving whatever
     * was already written in [partial].
     */
    private suspend fun fetchInto(
            url: String,
            expectedSize: Long,
            partial: File,
            onPercent: (Int) -> Unit
    ) {
        var existing = partial.takeIf { it.isFile }?.length() ?: 0L
        if (expectedSize > 0 && existing > expectedSize) {
            // Longer than the release: not a prefix of it.
            partial.delete()
            existing = 0L
        }
        if (expectedSize > 0 && existing == expectedSize) {
            onPercent(100)
            return
        }

        var connection = open(url, rangeFrom = existing)
        try {
            var code = connection.responseCode
            if (code == HttpURLConnection.HTTP_PARTIAL && existing > 0 &&
                    contentRangeStart(connection.getHeaderField("Content-Range")) != existing) {
                // A range other than the one asked for cannot be appended to what is kept.
                connection.disconnect()
                partial.delete()
                existing = 0L
                connection = open(url, rangeFrom = 0L)
                code = connection.responseCode
            } else if (code == HTTP_RANGE_NOT_SATISFIABLE && existing > 0) {
                // The server disagrees about how long the file is: what is kept is not a prefix.
                connection.disconnect()
                partial.delete()
                existing = 0L
                connection = open(url, rangeFrom = 0L)
                code = connection.responseCode
            }

            val append = when (code) {
                HttpURLConnection.HTTP_PARTIAL -> existing > 0
                HttpURLConnection.HTTP_OK -> false
                else -> throw IOException("HTTP $code for $url")
            }
            val startAt = if (append) existing else 0L

            // contentLength (not contentLengthLong): minSdk 23, and an APK never nears 2 GB
            val bodyLength = connection.contentLength.toLong().takeIf { it > 0 }
            val totalSize = when {
                expectedSize > 0 -> expectedSize
                bodyLength != null -> startAt + bodyLength
                else -> 0L
            }

            val job = coroutineContext[Job]
            connection.inputStream.use { input ->
                FileOutputStream(partial, append).use { output ->
                    copyWithProgress(
                            input, output, totalSize,
                            alreadyCopied = startAt,
                            checkActive = { job?.ensureActive() },
                            onPercent = onPercent)
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String, rangeFrom: Long): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "Svartifoss-update-download")
        // Defends against a known Android HttpURLConnection bug where a pooled/kept-alive
        // connection can silently truncate the response body when the request was redirected
        // to a different host (exactly GitHub's release-asset redirect: github.com -> a CDN
        // host) - forcing a fresh connection per download avoids the reuse entirely.
        connection.setRequestProperty("Connection", "close")
        // Not asked for a gzip body: a range into a compressed stream is not a range of the file.
        connection.setRequestProperty("Accept-Encoding", "identity")
        if (rangeFrom > 0) connection.setRequestProperty("Range", "bytes=$rangeFrom-")
        return connection
    }

    /** The first byte a `Content-Range: bytes 100-199/1000` header says the body starts at, or
     *  null when the header is missing or is not that shape. */
    internal fun contentRangeStart(header: String?): Long? {
        val value = header?.trim() ?: return null
        if (!value.startsWith("bytes", ignoreCase = true)) return null
        val range = value.substring(5).trim().substringBefore('/')
        return range.substringBefore('-').trim().toLongOrNull()
    }

    /** The digest in lower-case hex, from `sha256:ABC...` or a bare hex string; null when absent or
     *  not a SHA-256 (64 hex digits), in which case the file is simply not checked against it. */
    internal fun normalizeSha256(raw: String?): String? {
        val value = raw?.trim()?.removePrefix("sha256:")?.removePrefix("SHA256:")?.lowercase()
                ?: return null
        return value.takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
    }

    internal fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private const val HTTP_RANGE_NOT_SATISFIABLE = 416

    /**
     * @param alreadyCopied bytes of the file that were there before this stream started, so the
     *   percentage of a resumed download starts where the earlier one stopped.
     * @param checkActive called once per chunk; throws when the caller has been cancelled, so
     *   leaving the screen stops the copy at the next chunk instead of finishing it unseen.
     * @return the number of bytes copied from [input] (not counting [alreadyCopied]).
     */
    inline fun copyWithProgress(
            input: InputStream,
            output: OutputStream,
            totalSize: Long,
            alreadyCopied: Long = 0L,
            checkActive: () -> Unit = {},
            onPercent: (Int) -> Unit
    ): Long {
        val buffer = ByteArray(64 * 1024)
        var copied = 0L
        var lastPercent = -1
        while (true) {
            checkActive()
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            copied += read
            if (totalSize > 0) {
                val percent = (((alreadyCopied + copied) * 100) / totalSize).toInt().coerceIn(0, 100)
                if (percent != lastPercent) {
                    lastPercent = percent
                    onPercent(percent)
                }
            }
        }
        output.flush()
        return copied
    }
}
