package com.svartifoss.snfell

import android.annotation.TargetApi
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.session.MediaController
import android.os.Build
import android.preference.PreferenceManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.Wearable
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.model.AutoStartMode
import com.svartifoss.snfell.music.ActiveMediaSessionProvider
import com.svartifoss.snfell.music.MusicService
import com.svartifoss.snfell.music.isPlaying
import com.svartifoss.snfell.notifications.MediaNotificationActions
import com.matejdro.wearutils.lifecycle.Resource
import com.matejdro.wearutils.messages.sendMessageToNearestClient
import com.matejdro.wearutils.preferences.definition.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

class NotificationService : NotificationListenerService() {
    private lateinit var preferences: SharedPreferences
    private var bound = false
    private var mediaObserverRegistered = false

    private var activeMediaProvider: ActiveMediaSessionProvider? = null

    private val consumerPreferenceListener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == MiscPreferences.AUTO_START.key ||
                key == MiscPreferences.AUTO_START_MODE.key ||
                key == MiscPreferences.WEAR_QUICK_PANEL_SOURCE.key) {
            updateConnectedConsumers()
        }
    }

    private val coroutineScope = CoroutineScope(Job())
    private lateinit var messageClient: MessageClient
    private lateinit var nodeClient: NodeClient

    override fun onCreate() {
        super.onCreate()

        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        preferences.registerOnSharedPreferenceChangeListener(consumerPreferenceListener)

        nodeClient = Wearable.getNodeClient(applicationContext)
        messageClient = Wearable.getMessageClient(applicationContext)

        Timber.d("Service started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (ACTION_UNBIND_SERVICE == intent?.action &&
                bound &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requestUnbindSafe()
            Timber.d("Unbind on command")
        }

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()

        Timber.d("Listener connected")

        val musicServiceNotifyIntent = Intent(this, MusicService::class.java)
        musicServiceNotifyIntent.action = MusicService.ACTION_NOTIFICATION_SERVICE_ACTIVATED
        // MusicService calls startForeground() shortly after being started, and this callback
        // fires from the system (no foreground UI) - startService() alone can throw
        // ForegroundServiceStartNotAllowedException in that case on API 31+.
        ContextCompat.startForegroundService(this, musicServiceNotifyIntent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !shouldRun()) {
            //Running notification service is not needed for this app to run, it only needs to be enabled.

            Timber.d("Unbind on start")

            // On N+ we can turn off the service
            requestUnbindSafe()
            return
        }

        activeMediaProvider = ActiveMediaSessionProvider(this)

        // Seed the registry immediately; waiting for the next onNotificationPosted callback left
        // an already-playing app's quick actions empty until its notification happened to update.
        try {
            activeNotifications.orEmpty().forEach { MediaNotificationActions.update(this, it) }
        } catch (e: SecurityException) {
            // A few OEMs briefly revoke access while reconnecting the listener. The next posted
            // media notification will populate the registry, so this must not kill the service.
            Timber.w(e, "Unable to seed media notification actions")
        }

        bound = true
        updateConnectedConsumers()
    }

    override fun onListenerDisconnected() {
        Timber.d("Listener disconnected")
        stopObservingMedia()
        activeMediaProvider = null
        bound = false
        MediaNotificationActions.clear()

        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        Timber.d("Service destroyed")

        preferences.unregisterOnSharedPreferenceChangeListener(consumerPreferenceListener)
        stopObservingMedia()
        activeMediaProvider = null
        bound = false
        MediaNotificationActions.clear()
        coroutineScope.cancel()

        super.onDestroy()
    }

    private fun shouldRun(): Boolean {
        return MiscPreferences.isAnyKindOfAutoStartEnabled(preferences) ||
                Preferences.getString(preferences, MiscPreferences.WEAR_QUICK_PANEL_SOURCE) == "session"
    }

    /** Keeps auto-start observation independent from the quick-actions reason for binding. */
    private fun updateConnectedConsumers() {
        val provider = activeMediaProvider
        val needsAutoStart = MiscPreferences.isAnyKindOfAutoStartEnabled(preferences)
        if (provider != null && needsAutoStart && !mediaObserverRegistered) {
            provider.observeForever(mediaObserver)
            mediaObserverRegistered = true
        } else if (!needsAutoStart) {
            stopObservingMedia()
        }

        if (bound && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !shouldRun()) {
            requestUnbindSafe()
        }
    }

    private fun stopObservingMedia() {
        if (mediaObserverRegistered) {
            activeMediaProvider?.removeObserver(mediaObserver)
            mediaObserverRegistered = false
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let { MediaNotificationActions.update(this, it) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.key?.let(MediaNotificationActions::remove)
    }

    private fun startAppOnWatch() {
        Timber.d("AttemptToStartApp")
        coroutineScope.launch {
            try {
                val legacySetting = Preferences.getBoolean(preferences, MiscPreferences.AUTO_START)
                val openType = if (legacySetting) {
                    AutoStartMode.OPEN_APP
                } else {
                    Preferences.getEnum(preferences, MiscPreferences.AUTO_START_MODE)
                }

                val message = when (openType) {
                    AutoStartMode.OFF, null -> return@launch
                    AutoStartMode.SHOW_ICON -> CommPaths.MESSAGE_START_SERVICE
                    AutoStartMode.OPEN_APP -> CommPaths.MESSAGE_OPEN_APP
                }

                messageClient.sendMessageToNearestClient(nodeClient, message)
                Timber.d("Start success")
            } catch (e: Exception) {
                Timber.e(e, "Start Fail")
            }
        }
    }

    private val mediaObserver = Observer<Resource<MediaController>?> {
        Timber.d("Playback update %b %s", MusicService.active, it?.data?.playbackState?.state)

        if (!MusicService.active && it?.data?.playbackState?.isPlaying() == true) {
            val autoStartBlacklist = Preferences.getStringSet(preferences, MiscPreferences.AUTO_START_APP_BLACKLIST)
            if (!autoStartBlacklist.contains(it.data?.packageName)) {
                startAppOnWatch()
            }
        }
    }

    companion object {
        fun isEnabled(context: Context): Boolean {
            val component = ComponentName(context, NotificationService::class.java)
            val enabledListeners = Settings.Secure.getString(context.contentResolver,
                    "enabled_notification_listeners")

            return enabledListeners != null && enabledListeners.contains(component.flattenToString())
        }

        const val ACTION_UNBIND_SERVICE = "UNBIND"

        /**
         * Re-evaluates the notification listener whenever either of its consumers changes.
         * [autoStartEnabledOverride] is used by the ListPreference callback, which runs just
         * before the new value is persisted; all other callers should leave it null.
         */
        fun updateQuickActionsBinding(
                context: Context,
                autoStartEnabledOverride: Boolean? = null
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || !isEnabled(context)) return
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val shouldBind = (autoStartEnabledOverride
                    ?: MiscPreferences.isAnyKindOfAutoStartEnabled(prefs)) ||
                    Preferences.getString(prefs, MiscPreferences.WEAR_QUICK_PANEL_SOURCE) == "session"
            val component = ComponentName(context, NotificationService::class.java)
            if (shouldBind) {
                requestRebind(component)
            } else {
                try {
                    context.startService(
                            Intent(context, NotificationService::class.java)
                                    .setAction(ACTION_UNBIND_SERVICE)
                    )
                } catch (e: RuntimeException) {
                    // A background-start restriction can race a settings/import lifecycle.
                    // The persisted values are still authoritative and onListenerConnected()
                    // will unbind immediately the next time the system connects the listener.
                    Timber.w(e, "Unable to request notification-listener unbind")
                }
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.N)
    private fun requestUnbindSafe() {
        try {
            requestUnbind()
        } catch (e: SecurityException) {
            // Sometimes notification service may be unbound before we can unbind safely.
            // just stop self.
            stopSelf()
        }
    }
}
