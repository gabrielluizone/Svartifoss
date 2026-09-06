package com.svartifoss.snfell.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads a release APK to a file, reporting 0..100% progress. Shared by [WatchApkPusher] (wear
 * APK, then streamed to the watch) and [PhoneApkInstaller] (phone APK, then handed to the system
 * installer) so the HTTP/streaming details live in one place.
 */
object ApkDownloader {

    /**
     * @param expectedSize fallback total used for the percentage when the server sends no
     *   Content-Length (0 if unknown); also the size the download is verified against afterwards
     *   (skipped when 0/unknown).
     * @throws IOException when fewer bytes were read than expected - a redirect-then-truncated
     *   response (GitHub release asset URLs 302 from github.com to a different host, and
     *   Android's HttpURLConnection connection-pooling has a known bug silently truncating the
     *   stream across such a redirect) would otherwise hand a corrupt file straight to the
     *   installer, which fails much less clearly ("There was a problem parsing the package").
     *   [target] is deleted on this failure so a retry never sees a stale partial file.
     */
    suspend fun download(
            url: String,
            expectedSize: Long,
            target: File,
            onPercent: (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "Svartifoss-update-download")
            // Defends against a known Android HttpURLConnection bug where a pooled/kept-alive
            // connection can silently truncate the response body when the request was redirected
            // to a different host (exactly GitHub's release-asset redirect: github.com -> a CDN
            // host) - forcing a fresh connection per download avoids the reuse entirely.
            connection.setRequestProperty("Connection", "close")

            // contentLength (not contentLengthLong): minSdk 23, and an APK never nears 2 GB
            val totalSize = connection.contentLength.toLong().takeIf { it > 0 } ?: expectedSize
            val copied = connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    copyWithProgress(input, output, totalSize, onPercent)
                }
            }

            if (expectedSize > 0 && copied != expectedSize) {
                target.delete()
                throw IOException(
                        "Download incomplete: got $copied of $expectedSize bytes for $url"
                )
            }
        } finally {
            connection.disconnect()
        }
    }

    /** @return the total number of bytes copied. */
    inline fun copyWithProgress(
            input: InputStream,
            output: OutputStream,
            totalSize: Long,
            onPercent: (Int) -> Unit
    ): Long {
        val buffer = ByteArray(64 * 1024)
        var copied = 0L
        var lastPercent = -1
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            copied += read
            if (totalSize > 0) {
                val percent = ((copied * 100) / totalSize).toInt().coerceAtMost(100)
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
