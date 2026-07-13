package com.svartifoss.snfell.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.svartifoss.snfell.R

/** Posts the "new version available" notification. Tapping it opens [UpdateActivity]. */
object UpdateNotifier {
    private const val CHANNEL_ID = "app_updates"
    private const val NOTIFICATION_ID = 4001

    fun notifyUpdateAvailable(context: Context, release: UpdateChecker.ReleaseInfo) {
        val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.update_notification_channel),
                    NotificationManager.IMPORTANCE_DEFAULT
            ))
        }

        val contentIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, UpdateActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_brand)
                .setContentTitle(context.getString(R.string.update_available_title, release.tag))
                .setContentText(release.title)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)

        // Expandable "What's new": the release notes under the title, so the notification alone
        // says what changed instead of just that *something* did.
        val notes = release.body.takeIf { it.isNotBlank() }
        if (notes != null) {
            builder.setStyle(
                    NotificationCompat.BigTextStyle()
                            .setBigContentTitle(context.getString(R.string.update_available_title, release.tag))
                            .bigText(notes)
            )
        }

        val notification = builder.build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
