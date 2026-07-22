package com.svartifoss.snfell.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.matejdro.wearutils.preferences.definition.Preferences
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.view.mainactivity.MainActivity
import timber.log.Timber

/**
 * Receives and shows developer announcement pushes composed as Firebase Console campaigns
 * targeting the "announcements" topic (see [AnnouncementNotifications]).
 *
 * There is no server-authored notification layout beyond title/body/tap-target: campaigns are
 * composed as *notification* messages (not raw data), so Firebase's own SDK already shows most of
 * them automatically while the app is backgrounded. [onMessageReceived] is only reliably invoked
 * while the app process is alive, matching how every other push-based notifier here behaves. This
 * service still shows the notification itself, rather than relying only on that automatic
 * background path, so backgrounded-but-alive delivery looks identical to the fully-backgrounded
 * case and always respects [MiscPreferences.ANNOUNCEMENTS_ENABLED].
 */
class AnnouncementMessagingService : FirebaseMessagingService() {
    companion object {
        private const val CHANNEL_ID = "announcements"
        private const val NOTIFICATION_ID = 4101
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val notification = message.notification ?: return
        val title = notification.title ?: getString(R.string.announcement_notification_fallback_title)
        val body = notification.body ?: return

        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        if (!Preferences.getBoolean(preferences, MiscPreferences.ANNOUNCEMENTS_ENABLED)) {
            // The topic unsubscribe in AnnouncementNotifications is asynchronous, so a message
            // already in flight when the user opts out can still arrive here; honor the choice at
            // the point of display too rather than relying on the subscription race to win.
            return
        }

        showNotification(title, body, message.data["url"])
    }

    override fun onNewToken(token: String) {
        // No backend to register the token with - delivery is entirely topic-based (see
        // AnnouncementNotifications). Nothing to do; overridden only so the default (a no-op) is
        // an explicit, documented choice rather than an oversight.
    }

    private fun showNotification(title: String, body: String, url: String?) {
        val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.announcement_notification_channel),
                    NotificationManager.IMPORTANCE_DEFAULT
            ))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED) {
            return
        }

        // A url payload key opens that link directly; otherwise falls back to the main activity,
        // same as every other tap-target notification in the app.
        val contentIntent = if (url != null) {
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
        } else {
            Intent(this, MainActivity::class.java)
        }

        val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_brand)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Timber.w(e, "Could not post announcement notification")
        }
    }
}
