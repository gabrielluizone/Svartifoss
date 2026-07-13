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
            ApkDownloader.download(apkUrl, release.wearApkSize, apkFile) { percent ->
                onProgress(Progress.Downloading(percent))
            }

            onProgress(Progress.Connecting)
            val nodeId = findWatchNode() ?: throw NoWatchException()

            transfer(nodeId, apkFile, onProgress)
            onProgress(Progress.AwaitingWatchConfirmation)
        } finally {
            apkFile.delete()
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
                        ApkDownloader.copyWithProgress(input, output, apkFile.length()) { percent ->
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
}
