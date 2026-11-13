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
 *
 * **What this keeps on the watch, and for how long.** The received APK is written to one fixed
 * file in the cache directory, so each send replaces the last instead of piling up. Once the
 * install session has been committed that file is dropped: the session holds its own staged copy,
 * and this one only sat there (about 25 MB) until the installer reported back - which, for an
 * update left waiting at the confirmation prompt, could be never. The sessions themselves are the
 * other half: every send opens a new [PackageInstaller] session with a full copy of the APK, and
 * the system reaps an abandoned one only after days, so a send while an earlier one was still
 * unconfirmed staged a second copy beside it. The earlier sessions are abandoned first now.
 */
class ApkReceiverService : WearableListenerService() {

    override fun onCreate() {
        super.onCreate()
        removeStaleReceivedApk()
    }

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
            }
            // After the install session has taken its own copy - or, when the file was no APK of
            // ours, instead of it. Either way nothing needs it any more.
            apkFile.delete()
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

        abandonEarlierSessions(installer)
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

    /**
     * Abandons this app's earlier install sessions, so the new one is the only copy staged.
     * `getMySessions` is API 24+; the watch app's minSdk is 26.
     */
    private fun abandonEarlierSessions(installer: PackageInstaller) {
        for (info in installer.mySessions) {
            try {
                installer.abandonSession(info.sessionId)
            } catch (e: RuntimeException) {
                // Already finished or already gone - the point was to have none left.
            }
        }
    }

    /**
     * A received APK the process died holding (killed mid-install, battery out) would otherwise
     * wait for the next send to be overwritten. Anything older than a transfer can last is not
     * being written by a live one.
     */
    private fun removeStaleReceivedApk() {
        val leftover = File(cacheDir, UPDATE_APK_NAME)
        if (leftover.isFile &&
                System.currentTimeMillis() - leftover.lastModified() > TRANSFER_TIMEOUT_MS) {
            leftover.delete()
        }
    }

    companion object {
        const val UPDATE_APK_NAME = "wear-update.apk"
        private val TRANSFER_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(10)
    }
}
