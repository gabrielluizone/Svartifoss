package com.svartifoss.snfell.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
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
     * @throws IOException on download errors (the caller shows a retry message)
     */
    suspend fun installLatest(release: UpdateChecker.ReleaseInfo, onProgress: (Progress) -> Unit) {
        val apkUrl = release.mobileApkUrl
                ?: throw IOException("Release ${release.tag} has no phone APK asset")

        if (!canInstall()) {
            openInstallPermissionSettings()
            onProgress(Progress.NeedsPermission)
            return
        }

        val apkFile = File(context.cacheDir, "phone-update.apk")
        ApkDownloader.download(apkUrl, release.mobileApkSize, apkFile) { percent ->
            onProgress(Progress.Downloading(percent))
        }

        onProgress(Progress.Installing)
        launchInstaller(apkFile)
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

    private fun launchInstaller(apkFile: File) {
        val uri = FileProvider.getUriForFile(context, APK_AUTHORITY, apkFile)
        context.startActivity(
                Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                        Intent.FLAG_ACTIVITY_NEW_TASK
                        )
        )
    }

    companion object {
        /** Must match the FileProvider authority declared in the manifest. */
        const val APK_AUTHORITY = "com.svartifoss.snfell.apkprovider"
    }
}
