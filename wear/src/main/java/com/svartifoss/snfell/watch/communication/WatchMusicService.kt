package com.svartifoss.snfell.watch.communication

import android.annotation.TargetApi
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.asFlow
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.matejdro.wearutils.preferences.definition.Preferences
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.PausedHoldPolicy
import com.svartifoss.snfell.watch.view.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import com.svartifoss.snfell.common.R as commonR

/** Why [WatchMusicService] is currently staying alive - see [resolveServiceHold]. */
internal enum class ServiceHold {
    /** Something needs the connection right now: a screen is open, or music is playing. */
    ACTIVE,

    /** Nothing needs it this instant, but there is a real paused track to come back to. */
    PAUSED_TRACK,

    /** No track at all. */
    IDLE
}

/**
 * Decides how long the service may live for. Splitting "paused with a track" out of plain idle is
 * what keeps an ordinary pause from evicting the app: with the screen off the UI unbinds
 * ([UiOpenServiceConnection] only collects while STARTED) and `playing` is false, so the old
 * `uiOpen || musicPlaying` test dropped straight to idle and killed the foreground notification -
 * taking the watch-face OngoingActivity chip and the proxy [WatchMediaSession] with it - 30
 * seconds after the user pressed pause.
 */
internal fun resolveServiceHold(
        uiOpen: Boolean,
        musicPlaying: Boolean,
        hasTrack: Boolean
): ServiceHold = when {
    uiOpen || musicPlaying -> ServiceHold.ACTIVE
    hasTrack -> ServiceHold.PAUSED_TRACK
    else -> ServiceHold.IDLE
}

@AndroidEntryPoint
class WatchMusicService : LifecycleService() {
    @Inject
    internal lateinit var phoneConnection: PhoneConnection

    private val uiFlow = MutableSharedFlow<Unit>()

    private var serviceTimeoutJob: Job? = null

    private lateinit var mediaSession: WatchMediaSession

    private var lastGlanceableFingerprint: String? = null

    private var foregroundActive = false
    private var lastOngoingTrackText: String? = null

    override fun onCreate() {
        super.onCreate()
        active = true

        // Proxy MediaSession so the phone's playback shows up in the system Media Controls app and
        // the Wear OS media surfaces. State is pushed from PhoneConnection; controls forward back.
        // Created before the first notification so createWearNotification() can reference its token.
        mediaSession = WatchMediaSession(this, phoneConnection, lifecycleScope)
        phoneConnection.musicState.observe(this) { resource ->
            val state = resource?.data
            mediaSession.update(state, phoneConnection.albumArt.value)

            // Recents/launcher shows the OngoingActivity *status text* under the app name - not
            // the media session metadata - so it must be re-posted whenever the track changes.
            refreshOngoingStatus()

            // Keep the glanceable surfaces (Tile, album-art complication) in sync - but only when
            // a field they actually render changed. The phone transmits state on every volume
            // step and seek too; update requests are rate-limited by the system, so spamming one
            // per state change could get the request that carries a new track/cover dropped.
            val fingerprint =
                    "${state?.title}|${state?.artist}|${state?.playing}|${resource?.status}"
            if (fingerprint != lastGlanceableFingerprint) {
                lastGlanceableFingerprint = fingerprint
                GlanceableSurfaces.requestUpdate(this)
            }
        }
        phoneConnection.albumArt.observe(this) { albumArt ->
            mediaSession.update(phoneConnection.musicState.value?.data, albumArt)
            GlanceableSurfaces.requestComplicationUpdate(this)
        }

        createWearNotification()

        lifecycleScope.launch {
            val uiOpenFlow = uiFlow.subscriptionCount
                    .map { it > 0 }

            val musicPlayingFlow = phoneConnection.musicState.asFlow()
                    .map { it.data?.playing == true }

            // A paused track still counts as "there is a session to come back to" - see
            // [resolveServiceHold]. An error state carries its message in the title field, so it
            // is deliberately not a track.
            val hasTrackFlow = phoneConnection.musicState.asFlow()
                    .map { resource ->
                        resource.data?.let { !it.error && it.title.isNotBlank() } == true
                    }

            combine(uiOpenFlow, musicPlayingFlow, hasTrackFlow) { uiOpen, musicPlaying, hasTrack ->
                Timber.d("Service state UI open: %s Music playing: %s Has track: %s",
                        uiOpen, musicPlaying, hasTrack)
                resolveServiceHold(uiOpen, musicPlaying, hasTrack)
            }
                    .distinctUntilChanged()
                    .collect { hold ->
                        serviceTimeoutJob?.cancel()

                        when (hold) {
                            ServiceHold.ACTIVE -> createWearNotification()
                            ServiceHold.PAUSED_TRACK -> {
                                // Deliberately keeps the foreground notification up. The
                                // OngoingActivity chip it carries and the proxy MediaSession are
                                // the only two ways back into a paused session once the screen
                                // has turned off, so tearing them down here is what made the app
                                // disappear from the watch face during an ordinary pause.
                                createWearNotification()
                                when (val hold = pausedHoldMillis()) {
                                    PausedHoldPolicy.FOREVER -> Unit
                                    PausedHoldPolicy.NO_HOLD -> startTimeout(SERVICE_TIMEOUT)
                                    else -> startTimeout(hold)
                                }
                            }
                            ServiceHold.IDLE -> {
                                removeWearNotification()
                                startTimeout(SERVICE_TIMEOUT)
                            }
                        }
                    }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)

        // Every ContextCompat.startForegroundService() aimed at this service (IdleMessageListener,
        // MusicStateListenerService) must be answered with a startForeground() call - even when
        // the service was already running, and especially when it was running *demoted* (after
        // removeWearNotification()). Missing it crashes with
        // "Context.startForegroundService() did not then call Service.startForeground()".
        // createWearNotification() is idempotent, so re-posting is safe; if the service is
        // actually idle, the pending timeout still stops it and removes the notification.
        createWearNotification()

        return result
    }

    override fun onDestroy() {
        active = false
        if (::mediaSession.isInitialized) {
            mediaSession.release()
        }
        super.onDestroy()
    }

    /**
     * The "Title • Artist" line shown in the notification and as the OngoingActivity status
     * (which is what recents/the launcher render under the app name), or null when there is
     * no valid track to show.
     */
    private fun currentTrackText(): String? {
        val state = phoneConnection.musicState.value?.data ?: return null
        if (state.error || state.title.isBlank()) {
            return null
        }
        return if (state.artist.isNotBlank()) {
            "${state.title} • ${state.artist}"
        } else {
            state.title
        }
    }

    /**
     * Re-posts the foreground notification when the displayed track changed. The OngoingActivity
     * status is baked into the posted notification, so a change requires a re-post; skipping
     * unchanged text keeps volume/seek state updates from churning the notification.
     */
    private fun refreshOngoingStatus() {
        if (!foregroundActive || currentTrackText() == lastOngoingTrackText) {
            return
        }
        createWearNotification()
    }

    private fun createWearNotification() {
        val openAppIntent = Intent(this, MainActivity::class.java)

        val openAppPendingIntent = PendingIntent.getActivity(this,
                1,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val trackText = currentTrackText()

        createNotificationChannel()
        val notificationBuilder = NotificationCompat.Builder(this, KEY_NOTIFICATION_CHANNEL)
                .setContentTitle(getString(commonR.string.music_control_active))
                .setContentIntent(openAppPendingIntent)
                .setSmallIcon(R.drawable.ic_notification_bars)
                .setOngoing(true)
                // Tag as a media notification bound to the proxy session so the Wear OS media
                // template/surfaces derive their transport controls from the session state.
                .setStyle(MediaNotificationCompat.MediaStyle().setMediaSession(mediaSession.sessionToken))

        if (trackText != null) {
            notificationBuilder.setContentText(trackText)
        }

        // The animated icon is what the interactive watch face's ongoing-activity indicator
        // shows (faces that don't animate fall back to the static one, as does ambient) - the
        // same 3-bar equalizer the in-app Up Next button plays.
        val ongoingActivityBuilder = OngoingActivity.Builder(this, NOTIFICATION_ID_PERSISTENT, notificationBuilder)
                .setAnimatedIcon(R.drawable.ic_equalizer_bars_animated)
                .setStaticIcon(R.drawable.ic_notification_bars)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setTouchIntent(openAppPendingIntent)

        // Without an explicit Status the launcher falls back to the notification content text,
        // which historically was empty here - that's why recents showed no track name. Set the
        // track as the status so recents always has it (and stays in sync via
        // refreshOngoingStatus()).
        if (trackText != null) {
            ongoingActivityBuilder.setStatus(
                    Status.Builder()
                            .addPart("track", Status.TextPart(trackText))
                            .build()
            )
        }

        ongoingActivityBuilder.build().apply(this)

        foregroundActive = true
        lastOngoingTrackText = trackText

        // ServiceCompat passes the FGS type on API 29+ (required on API 34+) and is a no-op
        // arg on older versions, so this stays correct across the minSdk 25..34 range.
        //
        // This can run while the app is fully backgrounded (e.g. the combine flow flips to active
        // because the phone resumed playback while the UI is closed). On API 31+ starting a
        // foreground service from the background can be refused with
        // ForegroundServiceStartNotAllowedException (an IllegalStateException) - catch it so a
        // refusal doesn't crash the service. It stays a plain bound/started service; the
        // manifest-registered MusicStateListenerService still keeps the Tile/complication fresh.
        try {
            ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID_PERSISTENT,
                    notificationBuilder.build(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } catch (e: IllegalStateException) {
            Timber.w(e, "Could not promote WatchMusicService to foreground from background")
        }
    }

    private fun removeWearNotification() {
        foregroundActive = false
        lastOngoingTrackText = null
        stopForeground(true)
    }

    /**
     * Read at each transition rather than cached: the preference arrives from the phone over the
     * Data Layer at arbitrary times, and a service that latched the value at creation would keep
     * honouring the old setting for its whole (possibly very long) lifetime.
     */
    private fun pausedHoldMillis(): Long = PausedHoldPolicy.holdMillis(
            Preferences.getString(
                    PreferenceManager.getDefaultSharedPreferences(this),
                    MiscPreferences.WEAR_PAUSED_HOLD))

    private fun startTimeout(delayMs: Long) {
        serviceTimeoutJob = lifecycleScope.launch {
            delay(delayMs)

            stopSelf()
        }
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)

        startService(Intent(this, WatchMusicService::class.java))

        return Binder(this)
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager


        val persistentChannel = NotificationChannel(KEY_NOTIFICATION_CHANNEL,
                getString(commonR.string.music_control),
                NotificationManager.IMPORTANCE_MIN)
        notificationManager.createNotificationChannel(persistentChannel)
    }

    class Binder(private val service: WatchMusicService) : android.os.Binder() {
        val uiOpenFlow: Flow<Unit>
            get() = service.uiFlow
    }

    companion object {
        /**
         * Whether an instance is currently running (same-process flag, mirroring the phone-side
         * MusicService.active) - lets MusicStateListenerService skip redundant foreground starts.
         */
        @Volatile
        var active = false
            private set
    }
}


private const val NOTIFICATION_ID_PERSISTENT = 1
private const val KEY_NOTIFICATION_CHANNEL = "Service_Channel"

private const val SERVICE_TIMEOUT = 30_000L

