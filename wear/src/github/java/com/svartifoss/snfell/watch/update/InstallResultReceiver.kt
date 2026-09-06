package com.svartifoss.snfell.watch.update

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
import com.svartifoss.snfell.watch.util.WatchLanguage
import timber.log.Timber
import java.io.File

/**
 * Handles the [PackageInstaller] status callbacks of the watch self-update flow started by
 * [ApkReceiverService], plus MY_PACKAGE_REPLACED as the reliable "update finished" signal
 * (the STATUS_SUCCESS callback usually dies with the old process when the app replaces itself).
 *
 * The interesting status is STATUS_PENDING_USER_ACTION: the system wants its install-confirm
 * dialog shown, but a background receiver may not start activities - so the confirm intent is
 * wrapped in a notification the user taps ("tap to install"), which is allowed to launch it.
 */
class InstallResultReceiver : BroadcastReceiver() {

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
                // Guaranteed path first: tapping the notification may always launch the dialog.
                showConfirmNotification(context, confirmIntent)
                // Best effort on top: when our app happens to be in the foreground a direct
                // launch is allowed and pops the dialog with zero extra taps (in the background
                // the system silently drops it and the notification remains the way in).
                try {
                    context.startActivity(confirmIntent)
                } catch (e: Exception) {
                    Timber.d(e, "Direct confirm launch not possible")
                }
            }

            PackageInstaller.STATUS_SUCCESS -> onUpdateFinished(context)

            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Timber.e("Update install failed: status %d, %s", status, message)
                cleanUp(context)
                notificationManager(context).cancel(NOTIFICATION_ID)
                // The user aborting the confirm dialog isn't an error worth nagging about
                if (status != PackageInstaller.STATUS_FAILURE_ABORTED) {
                    showFailureNotification(context)
                }
            }
        }
    }

    private fun showConfirmNotification(context: Context, confirmIntent: Intent) {
        val strings = WatchLanguage.localized(context)
        val notification = baseNotification(context)
                .setContentTitle(strings.getString(R.string.update_install_title))
                .setContentText(strings.getString(R.string.update_install_tap))
                .setContentIntent(PendingIntent.getActivity(
                        context,
                        0,
                        confirmIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                ))
                .setAutoCancel(true)
                .setOngoing(false)
                .build()
        notificationManager(context).notify(NOTIFICATION_ID, notification)
    }

    private fun showFailureNotification(context: Context) {
        val strings = WatchLanguage.localized(context)
        val notification = baseNotification(context)
                .setContentTitle(strings.getString(R.string.update_install_title))
                .setContentText(strings.getString(R.string.update_install_failed))
                .setAutoCancel(true)
                .build()
        notificationManager(context).notify(NOTIFICATION_ID, notification)
    }

    private fun onUpdateFinished(context: Context) {
        cleanUp(context)
        notificationManager(context).cancel(NOTIFICATION_ID)
    }

    private fun cleanUp(context: Context) {
        File(context.cacheDir, ApkReceiverService.UPDATE_APK_NAME).delete()
    }

    /** A BroadcastReceiver gets no attachBaseContext, so its notification text used to resolve
     *  against the watch's own system locale instead of the language chosen on the phone. */
    private fun baseNotification(rawContext: Context): NotificationCompat.Builder {
        val context = WatchLanguage.localized(rawContext)
        val manager = notificationManager(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.update_notification_channel),
                    NotificationManager.IMPORTANCE_HIGH
            ))
        }
        return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_bars)
    }

    private fun notificationManager(context: Context): NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val ACTION_INSTALL_RESULT = "com.svartifoss.snfell.watch.INSTALL_RESULT"
        private const val CHANNEL_ID = "app_updates"
        private const val NOTIFICATION_ID = 4002
    }
}
