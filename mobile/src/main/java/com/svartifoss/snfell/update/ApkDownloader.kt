package com.svartifoss.snfell.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
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
     *   Content-Length (0 if unknown).
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

            // contentLength (not contentLengthLong): minSdk 23, and an APK never nears 2 GB
            val totalSize = connection.contentLength.toLong().takeIf { it > 0 } ?: expectedSize
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    copyWithProgress(input, output, totalSize, onPercent)
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    inline fun copyWithProgress(
            input: InputStream,
            output: OutputStream,
            totalSize: Long,
            onPercent: (Int) -> Unit
    ) {
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
    }
}
