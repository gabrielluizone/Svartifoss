package com.svartifoss.snfell.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.io.File
import java.io.IOException

/**
 * Downloads the phone release APK and hands it to the system package installer, so the sideloaded
 * app can update itself without a manual browser download - the phone-side counterpart to
 * [WatchApkPusher].
 *
 * Installing an APK needs the one-time "install unknown apps" grant (plus REQUEST_INSTALL_PACKAGES
 * in the manifest). When it's missing we open that settings screen and report [Progress.NeedsPermission]
 * instead of failing, so the user can grant it and tap again.
 */
class PhoneApkInstaller(private val context: Context) {

    sealed class Progress {
        class Downloading(val percent: Int) : Progress()
        object Installing : Progress()
        /** The user must grant "install unknown apps" first; its settings screen has been opened. */
        object NeedsPermission : Progress()
    }

    /**
     * @throws IOException on download or validation errors (the caller shows a retry message)
     */
    suspend fun installLatest(release: UpdateChecker.ReleaseInfo, onProgress: (Progress) -> Unit) {
        val apkUrl = release.mobileApkUrl
                ?: throw IOException("Release ${release.tag} has no phone APK asset")

        if (!canInstall()) {
            openInstallPermissionSettings()
            onProgress(Progress.NeedsPermission)
            return
        }

        val apkFile = File(context.cacheDir, APK_FILE_NAME)
        ApkDownloader.download(apkUrl, release.mobileApkSize, apkFile) { percent ->
            onProgress(Progress.Downloading(percent))
        }

        onProgress(Progress.Installing)

        // ApkDownloader already checked the downloaded byte count against the expected size,
        // which catches a truncated transfer (a known HttpURLConnection redirect bug - see
        // ApkDownloader). It can't catch same-length corruption (a re-encoding proxy, bit flips),
        // which would otherwise reach the system installer as a raw, unlocalized "There was a
        // problem parsing the package" failure. Confirm the file is actually a well-formed APK for
        // this exact package first, mirroring the watch's ApkReceiverService.validateApk.
        if (!isValidUpdateApk(apkFile)) {
            apkFile.delete()
            throw IOException("Downloaded file is not a valid Svartifoss update APK")
        }

        commitInstallSession(apkFile)
    }

    private fun canInstall(): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                    context.packageManager.canRequestPackageInstalls()

    private fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                        .setData(Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun isValidUpdateApk(apkFile: File): Boolean {
        val info = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
        return info != null && info.packageName == context.packageName
    }

    /**
     * Commits the downloaded APK through a [PackageInstaller] session instead of firing
     * ACTION_VIEW at the file - the session API validates the archive itself and reports failures
     * through [PhoneInstallResultReceiver] instead of silently handing a possibly-bad file to
     * whatever installer UI the OS provides. Mirrors the watch's ApkReceiverService.startInstall.
     */
    private fun commitInstallSession(apkFile: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        params.setSize(apkFile.length())

        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite(APK_FILE_NAME, 0, apkFile.length()).use { output ->
                apkFile.inputStream().use { input -> input.copyTo(output, 64 * 1024) }
                session.fsync(output)
            }

            val resultIntent = Intent(context, PhoneInstallResultReceiver::class.java)
                    .setAction(PhoneInstallResultReceiver.ACTION_INSTALL_RESULT)
            val resultPendingIntent = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    resultIntent,
                    // Mutable: the installer fills in the status extras.
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            session.commit(resultPendingIntent.intentSender)
        }
    }

    companion object {
        const val APK_FILE_NAME = "phone-update.apk"
    }
}
