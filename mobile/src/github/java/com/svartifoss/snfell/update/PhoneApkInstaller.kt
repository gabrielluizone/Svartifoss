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

        // Kept in the cache after this call, like the watch's: the install ends in a system
        // confirmation the user can dismiss, and a dismissed prompt used to mean downloading the
        // 36 MB again. The result receiver removes it once the update is done.
        val apkFile = ReleaseApkCache.of(context).apkFor(ReleaseApkCache.Kind.PHONE, release.tag)

        // ApkDownloader checks the byte count against the published size (a truncated transfer is
        // a known HttpURLConnection redirect bug - see ApkDownloader), the published SHA-256 when
        // there is one, and then [isValidUpdateApk]. Size alone can't catch same-length corruption
        // (a re-encoding proxy, bit flips), which would otherwise reach the system installer as a
        // raw, unlocalized "There was a problem parsing the package" failure; confirming the file
        // is a well-formed APK for this exact package first mirrors the watch's
        // ApkReceiverService.validateApk. A file that fails is deleted by the downloader.
        ApkDownloader.download(
                url = apkUrl,
                expectedSize = release.mobileApkSize,
                target = apkFile,
                sha256 = release.mobileApkSha256,
                isUsable = ::isValidUpdateApk
        ) { percent ->
            onProgress(Progress.Downloading(percent))
        }

        onProgress(Progress.Installing)
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

        abandonEarlierSessions(installer)
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

    /**
     * Drops this app's earlier install sessions before opening another.
     *
     * Each session holds its own full copy of the APK until it is committed *and* confirmed, or
     * abandoned, and the system only reaps an abandoned one after days. An update the user walked
     * away from at the confirmation prompt left one behind per attempt, so pressing the button
     * three times staged three copies. `getMySessions` is API 24+.
     */
    private fun abandonEarlierSessions(installer: PackageInstaller) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        for (info in installer.mySessions) {
            try {
                installer.abandonSession(info.sessionId)
            } catch (e: RuntimeException) {
                // Already finished or already gone - the point was to have none left.
            }
        }
    }

    companion object {
        /** The name inside the install session; the file itself is in [ReleaseApkCache]. */
        const val APK_FILE_NAME = "phone-update.apk"
    }
}
