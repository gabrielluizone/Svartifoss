package com.svartifoss.snfell.update

import android.content.Context
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import com.svartifoss.snfell.common.CommPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Ships a wear APK to the paired watch: downloads the release asset to the cache dir, then
 * streams it over a [CommPaths.CHANNEL_WEAR_APK] channel. The watch side (ApkReceiverService)
 * saves it and fires the system package installer - the Data Layer only connects nodes running
 * the same applicationId signed with the same key, so the channel is inherently same-app.
 *
 * Progress is reported via callback so [UpdateActivity] can show download/transfer percentages.
 */
class WatchApkPusher(private val context: Context) {

    sealed class Progress {
        class Downloading(val percent: Int) : Progress()
        object Connecting : Progress()
        class Transferring(val percent: Int) : Progress()
        object AwaitingWatchConfirmation : Progress()
    }

    class NoWatchException : IOException("No reachable watch")

    /**
     * @throws NoWatchException when no reachable watch node runs the app
     * @throws IOException on download/transfer errors
     */
    suspend fun pushToWatch(release: UpdateChecker.ReleaseInfo, onProgress: (Progress) -> Unit) {
        val apkUrl = release.wearApkUrl
                ?: throw IOException("Release ${release.tag} has no wear APK asset")

        val apkFile = File(context.cacheDir, "wear-update.apk")
        try {
            download(apkUrl, release.wearApkSize, apkFile, onProgress)

            onProgress(Progress.Connecting)
            val nodeId = findWatchNode() ?: throw NoWatchException()

            transfer(nodeId, apkFile, onProgress)
            onProgress(Progress.AwaitingWatchConfirmation)
        } finally {
            apkFile.delete()
        }
    }

    private suspend fun download(
            url: String,
            expectedSize: Long,
            target: File,
            onProgress: (Progress) -> Unit
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
                    copyWithProgress(input, output, totalSize) { percent ->
                        onProgress(Progress.Downloading(percent))
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun findWatchNode(): String? {
        val capabilityInfo = Wearable.getCapabilityClient(context)
                .getCapability(CommPaths.WATCH_APP_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                .await()
        return capabilityInfo.nodes.firstOrNull()?.id
    }

    private suspend fun transfer(nodeId: String, apkFile: File, onProgress: (Progress) -> Unit) {
        val channelClient = Wearable.getChannelClient(context)
        val channel = channelClient.openChannel(nodeId, CommPaths.CHANNEL_WEAR_APK).await()

        try {
            val outputStream = channelClient.getOutputStream(channel).await()
            withContext(Dispatchers.IO) {
                apkFile.inputStream().use { input ->
                    outputStream.use { output ->
                        copyWithProgress(input, output, apkFile.length()) { percent ->
                            onProgress(Progress.Transferring(percent))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Wear APK transfer failed")
            channelClient.close(channel)
            throw e
        }
    }

    private inline fun copyWithProgress(
            input: java.io.InputStream,
            output: java.io.OutputStream,
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
