package com.svartifoss.snfell.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.IntentCompat
import com.svartifoss.snfell.R
import timber.log.Timber
import java.io.File

/**
 * Handles the [PackageInstaller] status callbacks of the phone self-update flow started by
 * [PhoneApkInstaller], plus MY_PACKAGE_REPLACED as the reliable "update finished" signal (the
 * STATUS_SUCCESS callback usually dies with the old process when the app replaces itself).
 * Mirrors the watch's InstallResultReceiver.
 *
 * The interesting status is STATUS_PENDING_USER_ACTION: the system wants its install-confirm
 * dialog shown. UpdateActivity is normally still in the foreground when this arrives (the user
 * just tapped "Update phone"), so a direct launch usually succeeds with zero extra taps; the
 * notification is the fallback for when it doesn't (backgrounded in the meantime).
 */
class PhoneInstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            onUpdateFinished(context)
            return
        }
        if (intent.action != ACTION_INSTALL_RESULT) {
            return
        }

        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmIntent = IntentCompat.getParcelableExtra(
                        intent, Intent.EXTRA_INTENT, Intent::class.java
                ) ?: return
                confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(confirmIntent)
                } catch (e: Exception) {
                    Timber.d(e, "Direct confirm launch not possible, falling back to notification")
                    showConfirmNotification(context, confirmIntent)
                }
            }

            PackageInstaller.STATUS_SUCCESS -> onUpdateFinished(context)

            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Timber.e("Phone update install failed: status %d, %s", status, message)
                cleanUp(context)
                notificationManager(context).cancel(NOTIFICATION_ID)
                // The user aborting the confirm dialog isn't an error worth nagging about.
                if (status != PackageInstaller.STATUS_FAILURE_ABORTED) {
                    showFailureNotification(context)
                }
            }
        }
    }

    private fun showConfirmNotification(context: Context, confirmIntent: Intent) {
        val notification = baseNotification(context)
                .setContentTitle(context.getString(R.string.update_install_notification_title))
                .setContentText(context.getString(R.string.update_install_notification_tap))
                .setContentIntent(PendingIntent.getActivity(
                        context,
                        0,
                        confirmIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                ))
                .setAutoCancel(true)
                .build()
        notificationManager(context).notify(NOTIFICATION_ID, notification)
    }

    private fun showFailureNotification(context: Context) {
        val notification = baseNotification(context)
                .setContentTitle(context.getString(R.string.update_install_notification_title))
                .setContentText(context.getString(R.string.update_install_notification_failed))
                .setAutoCancel(true)
                .build()
        notificationManager(context).notify(NOTIFICATION_ID, notification)
    }

    private fun onUpdateFinished(context: Context) {
        cleanUp(context)
        notificationManager(context).cancel(NOTIFICATION_ID)
    }

    private fun cleanUp(context: Context) {
        File(context.cacheDir, PhoneApkInstaller.APK_FILE_NAME).delete()
    }

    private fun baseNotification(context: Context): NotificationCompat.Builder {
        val manager = notificationManager(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.update_notification_channel),
                    NotificationManager.IMPORTANCE_HIGH
            ))
        }
        return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_brand)
    }

    private fun notificationManager(context: Context): NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val ACTION_INSTALL_RESULT = "com.svartifoss.snfell.INSTALL_RESULT"
        private const val CHANNEL_ID = "app_updates"
        private const val NOTIFICATION_ID = 4003
    }
}
