package com.svartifoss.snfell.watch.update

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.PowerManager
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.svartifoss.snfell.common.CommPaths
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Receives a newer wear APK streamed by the phone over [CommPaths.CHANNEL_WEAR_APK] (see the
 * phone's WatchApkPusher) and hands it to the system [PackageInstaller] - the on-watch half of
 * updating without ADB/Wear Installer. The Data Layer only connects nodes running the same
 * applicationId signed with the same key, so the sender is by construction our own phone app;
 * the received file is still validated to be an APK for this exact package before installing.
 *
 * The install result (including the "confirm on watch" system prompt, which cannot be launched
 * directly from this background service) is handled by [InstallResultReceiver].
 */
class ApkReceiverService : WearableListenerService() {

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        if (channel.path != CommPaths.CHANNEL_WEAR_APK) {
            return
        }

        // A multi-MB Bluetooth transfer takes a while - keep the CPU from suspending mid-read.
        // This callback runs on the service's background thread, so blocking here is fine.
        val wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Svartifoss:ApkReceive")
        wakeLock.acquire(TRANSFER_TIMEOUT_MS)

        val channelClient = Wearable.getChannelClient(this)
        val apkFile = File(cacheDir, UPDATE_APK_NAME)
        try {
            receiveApk(channelClient, channel, apkFile)
            if (validateApk(apkFile)) {
                startInstall(apkFile)
            } else {
                apkFile.delete()
            }
        } catch (e: Exception) {
            Timber.e(e, "Receiving wear update APK failed")
            apkFile.delete()
            try {
                channelClient.close(channel)
            } catch (ignored: Exception) {
            }
        } finally {
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        }
    }

    private fun receiveApk(
            channelClient: ChannelClient,
            channel: ChannelClient.Channel,
            target: File
    ) {
        val inputStream = Tasks.await(
                channelClient.getInputStream(channel),
                30,
                TimeUnit.SECONDS
        )
        inputStream.use { input ->
            target.outputStream().use { output ->
                input.copyTo(output, 64 * 1024)
            }
        }
        channelClient.close(channel)
        Timber.d("Received update APK: %d bytes", target.length())
    }

    private fun validateApk(apkFile: File): Boolean {
        val info = packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
        if (info == null) {
            Timber.e("Received file is not a readable APK")
            return false
        }
        if (info.packageName != packageName) {
            Timber.e("Received APK is for %s, not us", info.packageName)
            return false
        }
        return true
    }

    private fun startInstall(apkFile: File) {
        val installer = packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
        )
        params.setAppPackageName(packageName)
        params.setSize(apkFile.length())

        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite(UPDATE_APK_NAME, 0, apkFile.length()).use { output ->
                apkFile.inputStream().use { input ->
                    input.copyTo(output, 64 * 1024)
                }
                session.fsync(output)
            }

            val resultIntent = Intent(this, InstallResultReceiver::class.java)
                    .setAction(InstallResultReceiver.ACTION_INSTALL_RESULT)
            val resultPendingIntent = PendingIntent.getBroadcast(
                    this,
                    sessionId,
                    resultIntent,
                    // Mutable: the installer fills in the status extras
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            session.commit(resultPendingIntent.intentSender)
        }
        Timber.d("Update install session %d committed", sessionId)
    }

    companion object {
        const val UPDATE_APK_NAME = "wear-update.apk"
        private val TRANSFER_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(10)
    }
}
