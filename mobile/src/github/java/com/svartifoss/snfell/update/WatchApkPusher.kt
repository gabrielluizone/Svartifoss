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
 *
 * **The downloaded APK outlives this call.** It used to be deleted in a `finally`, so a push that
 * failed after a complete 25 MB download - no watch in range, the Bluetooth link dropping, the
 * screen being closed - made the next attempt download it all again. It now stays in
 * [ReleaseApkCache] until the watch has the release (or a newer one replaces it), and a download
 * that was interrupted resumes - see [ApkDownloader]. Only the transfer to the watch is repeated.
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

        val apkFile = ReleaseApkCache.of(context).apkFor(ReleaseApkCache.Kind.WATCH, release.tag)

        // Downloaded before the watch is looked for, so a missing watch costs a retry of the
        // connection and nothing else.
        val outcome = ApkDownloader.download(
                url = apkUrl,
                expectedSize = release.wearApkSize,
                target = apkFile,
                sha256 = release.wearApkSha256,
                isUsable = ::isApkOfThisApp
        ) { percent ->
            onProgress(Progress.Downloading(percent))
        }
        Timber.d("Wear update APK %s (%s): %d bytes", release.tag, outcome, apkFile.length())

        onProgress(Progress.Connecting)
        val nodeId = findWatchNode() ?: throw NoWatchException()

        transfer(nodeId, apkFile, onProgress)
        onProgress(Progress.AwaitingWatchConfirmation)
    }

    /** The watch app has this app's package name, so the same check the phone applies to its own
     *  update confirms the file is a well-formed APK of the right app before it crosses to the
     *  watch - which would reject a bad one anyway, after the slow part. */
    private fun isApkOfThisApp(apk: File): Boolean {
        val info = context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
        return info != null && info.packageName == context.packageName
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
