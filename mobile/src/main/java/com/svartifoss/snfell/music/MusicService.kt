package com.svartifoss.snfell.music

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.preference.PreferenceManager
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.svartifoss.snfell.NotificationService
import com.svartifoss.snfell.R
import com.svartifoss.snfell.view.LyraAccent
import com.svartifoss.snfell.actions.ActionHandler
import com.svartifoss.snfell.actions.DEFAULT_QUEUE_PAGE_SIZE
import com.svartifoss.snfell.actions.OpenPlaylistAction
import com.svartifoss.snfell.actions.PhoneAction
import com.svartifoss.snfell.actions.playback.LikeAction
import com.svartifoss.snfell.actions.playback.RepeatAction
import com.svartifoss.snfell.actions.playback.ShuffleAction
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.common.CustomLists
import com.svartifoss.snfell.common.QueueEntry
import com.svartifoss.snfell.common.LibraryEntry
import com.svartifoss.snfell.common.FaceScopedPreferences
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.LyricsStatus
import com.svartifoss.snfell.common.PlaybackPositionEstimate
import com.svartifoss.snfell.common.PlayerBackgroundStyle
import com.svartifoss.snfell.common.AppearanceContext
import com.svartifoss.snfell.common.ThemeAppearance
import com.svartifoss.snfell.common.buttonconfig.ButtonInfo
import com.svartifoss.snfell.common.util.FloatPacker
import com.svartifoss.snfell.config.ActionConfig
import com.svartifoss.snfell.config.WatchInfoProvider
import com.svartifoss.snfell.config.WatchInfoWithIcons
import com.svartifoss.snfell.view.watchface.theme.WatchThemeRepository
import com.svartifoss.snfell.di.GlobalConfig
import com.svartifoss.snfell.di.MusicServiceSubComponent
import com.svartifoss.snfell.notifications.AppGlyphStore
import com.svartifoss.snfell.notifications.MediaNotificationActions
import com.svartifoss.snfell.notifications.NotificationProvider
import com.svartifoss.snfell.notifications.customActionSnapshotId
import com.svartifoss.snfell.notifications.inferMediaActionSemantic
import com.svartifoss.snfell.notifications.isCustomActionSnapshotId
import com.svartifoss.snfell.proto.LyricsRequest
import com.svartifoss.snfell.proto.LyricsResponse
import com.svartifoss.snfell.proto.CustomList
import com.svartifoss.snfell.proto.CustomListItemAction
import com.svartifoss.snfell.proto.MediaAction
import com.svartifoss.snfell.proto.MusicState
import com.svartifoss.snfell.proto.PlaybackSync
import com.svartifoss.snfell.proto.TrackMetadata
import com.svartifoss.snfell.proto.WatchActions
import com.google.protobuf.ByteString
import com.svartifoss.snfell.update.UpdateChecker
import com.svartifoss.snfell.util.launchWithPlayServicesErrorHandling
import com.matejdro.wearutils.messages.sendMessageToNearestClient
import com.matejdro.wearutils.lifecycle.EmptyObserver
import com.matejdro.wearutils.lifecycle.Resource
import com.matejdro.wearutils.miscutils.BitmapUtils
import com.matejdro.wearutils.preferences.definition.Preferences
import com.matejdro.wearvibrationcenter.notificationprovider.ReceivedNotification
import dagger.android.AndroidInjection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlin.math.max
import com.svartifoss.snfell.common.R as commonR

data class TrackHistoryEntry(val artist: String, val title: String)

class MusicService : LifecycleService(), MessageClient.OnMessageReceivedListener {
    companion object {
        const val ACTION_START_FROM_WATCH = "START_FROM_WATCH"
        const val ACTION_NOTIFICATION_SERVICE_ACTIVATED = "NOTIFICATION_SERVICE_ACTIVATED"

        private const val MESSAGE_STOP_SELF = 0
        private val ACK_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(3)
        private const val SEEK_DETECTION_THRESHOLD_MS = 1500L
        private const val QUEUE_REFRESH_DEBOUNCE_MS = 600L

        /**
         * Grace between cancelling the notification and killing the process on "Force stop".
         *
         * The cancel itself is a synchronous call into the system's notification service, so it has
         * already landed by the time this starts - the delay is for the *user*: it lets the shade
         * finish animating the entry away before the process disappears underneath it, which is the
         * difference between "I tapped it and it closed" and "something vanished".
         */
        private const val FORCE_STOP_KILL_DELAY_MS = 500L

        /**
         * Hard ceiling on how long "Force stop" waits for the watch to be told before killing this
         * process anyway.
         *
         * The send is best-effort by nature - there may be no watch paired, or it may be out of
         * range - and a Force stop that visibly does nothing because Bluetooth hung is a worse
         * failure than a watch that stays up until it next syncs.
         */
        private const val FORCE_STOP_KILL_MAX_DELAY_MS = 2_000L

        /**
         * Outlives the service, on purpose - see [notifyWatchOfShutdown].
         *
         * Never cancelled and never meant to be: the only work it carries is the last message this
         * process sends before it stops or dies, so there is nothing left for a cancellation to
         * protect.
         */
        private val SHUTDOWN_SCOPE = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** MessageClient rejects payloads over 100 KB; stay well under it. */
        private const val MUSIC_STATE_MESSAGE_MAX_BYTES = 60 * 1024

        /**
         * How long a queue jump is given to take visible effect before the media-id route is tried
         * instead - see [onQueueEntrySelected]. Long enough for a player that updates metadata a
         * beat after the transport command (most of them), short enough not to feel like a stall.
         * Overshooting only costs a re-play of the track the user asked for anyway.
         */
        private const val QUEUE_SKIP_VERIFY_MS = 1200L
        private const val MAX_TRACK_HISTORY_SIZE = 20

        /** How long [pressPlayAfterNavigating] keeps watching for the app to finish loading the
         *  content a deep link opened. Generous: this runs only after the URI retries have already
         *  failed, and a cold streaming app can take seconds to publish its first metadata. */
        private const val PRESS_PLAY_ATTEMPTS = 10
        private const val PRESS_PLAY_INTERVAL_MS = 400L

        /** How long a direct `playFromUri` gets to actually start playing before the deep link
         *  falls through to the browser/visible routes. Long enough for a streaming app to buffer,
         *  short enough that a dead command doesn't strand the request - the same trade, and the
         *  same reasoning, as [QUEUE_SKIP_VERIFY_MS]. */
        private const val DEEP_LINK_VERIFY_MS = 1800L

        /**
         * Rows sent per library page. A browse node can legitimately hold thousands of items
         * (an "All songs" folder), and the whole list travels as one DataItem - the same size
         * pressure the queue transmission caps at 20. Deeper levels are how the user narrows down,
         * so a generous-but-bounded page beats paginating a watch menu.
         */
        private const val LIBRARY_PAGE_LIMIT = 50
        private const val SESSION_ACTION_PREFIX = "session:"
        private const val MAX_SESSION_QUICK_ACTIONS = 3

        private const val STOP_SELF_PENDING_INTENT_REQUEST_CODE = 333
        private const val FORCE_STOP_PENDING_INTENT_REQUEST_CODE = 334
        private const val ACTION_STOP_SELF = "STOP_SELF"
        private const val ACTION_FORCE_STOP = "FORCE_STOP"
        // Not private: MiscSettingsFragment links out to this channel's system settings page.
        const val KEY_NOTIFICATION_CHANNEL = "Service_Channel"
        private const val KEY_NOTIFICATION_CHANNEL_ERRORS = "Error notifications"

        private const val NOTIFICATION_ID_PERSISTENT = 1
        private const val NOTIFICATION_ID_SERVICE_ERROR = 2

        var active = false
            private set

        // Monotonic sequence stamped on every DATA_MUSIC_STATE put (MusicState.seq). Wall-clock
        // seeded and process-lifetime so it keeps increasing across MusicService re-creation and
        // even a phone process restart - the watch persists the last seq it applied only in
        // memory, but a restarted phone must still out-number whatever old revision is sitting in
        // the Data Layer store, or the watch would gate the fresh state away as "stale". The watch
        // uses it to drop the older buffered revisions Play Services replays on reconnect.
        private val musicStateSequence = AtomicLong(System.currentTimeMillis())

        private fun nextMusicSeq(): Long =
                musicStateSequence.updateAndGet { max(it + 1L, System.currentTimeMillis()) }
    }

    private lateinit var messageClient: MessageClient
    private lateinit var dataClient: DataClient

    private lateinit var preferences: SharedPreferences

    /** Identity of the track last read from metadata, and when it was first seen on this device's
     *  monotonic clock. Together they let a stale position sample be spotted - see
     *  [PlaybackPositionEstimate.sampleBelongsToTrack]. */
    private var lastSeenTrackKey: String? = null
    private var trackFirstSeenRealtimeMs: Long = 0L

    @Inject
    lateinit var mediaSessionProvider: ActiveMediaSessionProvider

    @Inject
    @GlobalConfig
    lateinit var config: ActionConfig

    @Inject
    lateinit var watchInfoProvider: WatchInfoProvider

    @Inject
    lateinit var notificationProvider: NotificationProvider

    @Inject
    lateinit var musicServiceComponentFactory: MusicServiceSubComponent.Factory

    private lateinit var actionHandlers: Map<Class<*>, ActionHandler<*>>

    private var ackTimeoutHandler = AckTimeoutHandler(WeakReference(this))
    private val queueRefreshHandler = Handler(Looper.getMainLooper())
    private val notificationActionsChanged: () -> Unit = {
        queueRefreshHandler.post {
            // The source icon is taken from the notification too, so a notification update has to
            // re-transmit even when the quick panel is not bound to notification actions -
            // otherwise the icon that arrives just after a track change never reaches the watch.
            if (usesSessionQuickActions() || showSourceIconEnabled()) {
                currentMediaController?.let(::buildMusicStateAndTransmit)
            }
        }
    }
    /**
     * A music app's notification glyph was learned for the first time - see [AppGlyphStore].
     *
     * Action icons are rasterized at *transmit* time, so an assignment made before this phone had
     * ever seen that app's notification carries its launcher icon on the watch and keeps carrying
     * it: nothing else would resend the config, which is why the only way to pick up the new glyph
     * used to be re-picking the action by hand. Re-transmit rather than commit - nothing about the
     * configuration changed, only how it draws.
     *
     * Fired at most once per package per process (the store enforces that), so this is a handful of
     * pushes over the life of an install rather than anything periodic.
     */
    private val appGlyphLearned: (String) -> Unit = {
        queueRefreshHandler.post { retransmitConfigsForGlyphs() }
    }

    /**
     * Re-send the action list and both button configs so their icons are rasterized again.
     *
     * Called both when a glyph is learned live and once at startup when [AppGlyphStore] reports
     * that something was learned while this service was not running - which is the ordinary case,
     * since a service that is stopped is exactly when the user is most likely to have a music app
     * post its first notification.
     */
    private fun retransmitConfigsForGlyphs() {
        try {
            config.getActionList().retransmit()
            config.getPlayingConfig().retransmit()
            config.getStoppedConfig().retransmit()
            AppGlyphStore.markRetransmitted(this)
        } catch (e: Exception) {
            Timber.w(e, "Could not re-transmit configs after learning an app glyph")
        }
    }

    private val quickActionsPreferenceChanged =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == MiscPreferences.WEAR_QUICK_PANEL_SOURCE.key) {
            queueRefreshHandler.post {
                // The notification listener is also used by auto-start, so this central helper
                // decides whether changing either consumer should bind or unbind it.
                NotificationService.updateQuickActionsBinding(this)
                buildMusicStateAndTransmit(currentMediaController)
            }
        } else if (key == MiscPreferences.ENABLE_NOTIFICATION_POPUP.key) {
            // Apply the toggle live instead of only on the next service start. observe/
            // removeObserver must run on the main thread, and queueRefreshHandler is main-looper.
            queueRefreshHandler.post { applyNotificationPopupObserver() }
        }
    }

    /** Binds or unbinds the notification popup source to match the current preference. */
    private fun applyNotificationPopupObserver() {
        notificationProvider.removeObserver(notificationCallback)
        if (Preferences.getBoolean(preferences, MiscPreferences.ENABLE_NOTIFICATION_POPUP)) {
            notificationProvider.observe(this, notificationCallback)
        }
    }

    private var previousMusicState: MusicState? = null
    private var previousAlbumArt: Bitmap? = null

    /**
     * The cover currently shown on the watch's now-playing screen, or null when there is none.
     *
     * Exposed so the queue can reuse it for the entry that is actually playing. Media3-based
     * streaming clients (Echo Music and other YouTube Music front-ends) publish queue covers only
     * as remote URLs - `MediaSessionLegacyStub` attaches a bitmap to a queue item solely when the
     * app embedded raw `artworkData`, while the *current track's* metadata goes through
     * `loadBitmapFromMetadata`, which downloads it. That asymmetry is why such players show art on
     * the player but a queue of blank thumbnails. Handing this already-decoded bitmap to the queue
     * costs no network call and no permission, so the playing row is never blank.
     */
    val currentAlbumArt: Bitmap?
        get() = previousAlbumArt

    var currentMediaController: MediaController? = null
    private var startedFromWatch = false

    // Reference-keyed cache of the last art serialized for the watch. State-only changes
    // (volume, seek, play/pause) reuse the bytes instead of re-encoding the same cover on
    // every transmit.
    private var lastSerializedArtSource: Bitmap? = null
    private var lastSerializedArt: ByteArray? = null
    private var lastSourceIconPackage: String? = null
    private var lastSourceIconBytes: ByteArray? = null
    /** Whether [lastSourceIconBytes] came from the notification (tintable template) or the
     *  launcher (full-colour artwork). Mirrored to the watch as MusicState.sourceIconTemplate. */
    private var lastSourceIconIsTemplate = false
    /** Last icon actually transmitted, so a late-arriving notification glyph defeats the
     *  state dedupe the way changed album art does. */
    private var previousSourceIconBytes: ByteArray? = null
    /** Whether the current package has already had one state update pass without its media
     *  notification. Gates the launcher fallback so it never flashes ahead of the real glyph. */
    private var sourceIconAwaitedOnce = false

    // Guards against a transmit that suspended for art encoding finishing after a newer
    // transmit already shipped fresher state - see transmitToWear.
    private var transmitSequence = 0L

    // Cache of the last album art decoded from a content URI, keyed by the URI string. Apps
    // that publish art as a URI would otherwise get a fresh Bitmap object on every state
    // callback, defeating the reference-identity "did the art change" check below.
    private var lastArtUriString: String? = null
    private var lastArtUriBitmap: Bitmap? = null

    private var lastTrackArtist = ""
    private var lastTrackTitle = ""

    /** Most recently played tracks, newest first. Used as a fallback when [MediaController.getQueue] is unavailable.
     *  Persisted via [TrackHistoryStorage] so it survives this service being torn down and recreated. */
    val recentTrackHistory = ArrayDeque<TrackHistoryEntry>()

    /**
     * A queue together with the session that published it.
     *
     * The pairing is the point: a `queueId` is an index into one specific session's queue and means
     * nothing to any other session, so whoever acts on a queue entry has to send the command back
     * to the same controller the entry came from.
     */
    data class QueueSource(
            val controller: MediaController,
            val items: List<android.media.session.MediaSession.QueueItem>
    )

    /**
     * The playing app's queue and its owning session, or null when the app genuinely publishes none
     * (the caller then falls back to [recentTrackHistory]).
     *
     * Prefers the tracked controller's own queue and only then looks at the app's other live
     * sessions - see [ActiveMediaSessionProvider.siblingQueueSourceForPackage] for why an app can
     * have a queue on a session that is not the one playing.
     */
    fun resolveQueueSource(): QueueSource? {
        val controller = currentMediaController ?: return null
        controller.queue?.takeIf { it.isNotEmpty() }?.let { return QueueSource(controller, it) }
        return mediaSessionProvider.siblingQueueSourceForPackage(controller.packageName, controller)
    }

    /** [resolveQueueSource] for callers that only render the entries. */
    fun resolvePlaybackQueue(): List<android.media.session.MediaSession.QueueItem>? =
            resolveQueueSource()?.items

    private var currentVolume = 0

    @SuppressLint("LaunchActivityFromNotification")
    override fun onCreate() {
        super.onCreate()

        AndroidInjection.inject(this)
        actionHandlers = musicServiceComponentFactory.build(this).getActionHandlers()

        recentTrackHistory.addAll(TrackHistoryStorage.load(this))

        // This service starts whenever media plays, making it the app's most reliable
        // background heartbeat for the sideload update check (throttled to once a day inside).
        lifecycleScope.launch {
            UpdateChecker.maybeCheckInBackground(this@MusicService)
        }

        messageClient = Wearable.getMessageClient(applicationContext)
        dataClient = Wearable.getDataClient(applicationContext)
        PlaylistShortcutStorage.syncToWatch(this)

        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        preferences.registerOnSharedPreferenceChangeListener(quickActionsPreferenceChanged)

        MediaNotificationActions.addListener(notificationActionsChanged)
        AppGlyphStore.addListener(appGlyphLearned)
        // Catches up on glyphs learned while this service was down - see needsRetransmit.
        if (AppGlyphStore.needsRetransmit(this)) {
            queueRefreshHandler.post { retransmitConfigsForGlyphs() }
        }
        NotificationService.updateQuickActionsBinding(this)

        try {
            messageClient.addListener(this, Uri.parse(CommPaths.MESSAGES_PREFIX), MessageClient.FILTER_PREFIX)
        } catch (e: Exception) {
            Timber.w(e, "Failed to register Wearable message client listener")
        }

        mediaSessionProvider = ActiveMediaSessionProvider(this)
        mediaSessionProvider.observe(this, mediaCallback)

        watchInfoProvider.observe(this, EmptyObserver<WatchInfoWithIcons>())

        applyNotificationPopupObserver()

        if (!promoteToForeground()) {
            return
        }

        active = true
        Timber.d("Service started")
    }


    /**
     * Builds the persistent notification and promotes the service to the foreground.
     *
     * Must be called for **every** `startForegroundService()` aimed at this service, not only on
     * creation. A start aimed at an already-created service does not run [onCreate], so nothing
     * answered the promotion contract and the system killed the process with
     * ForegroundServiceDidNotStartInTimeException - reported from both background callers
     * (WatchListenerService on a watch command, NotificationService on listener rebind at boot).
     * The wear-side service guards the identical pattern in its own onStartCommand.
     *
     * Idempotent: re-posting the same notification id is free, and the pending intents use
     * FLAG_UPDATE_CURRENT. Returns false when the OS refused the promotion, in which case the
     * service has already stopped itself.
     */
    private fun promoteToForeground(): Boolean {
        val stopSelfIntent = Intent(this, MusicService::class.java)
        stopSelfIntent.action = ACTION_STOP_SELF

        val stopSelfPendingIntent = PendingIntent.getService(this,
                STOP_SELF_PENDING_INTENT_REQUEST_CODE,
                stopSelfIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val forceStopIntent = Intent(this, MusicService::class.java)
        forceStopIntent.action = ACTION_FORCE_STOP
        val forceStopPendingIntent = PendingIntent.getService(this,
                FORCE_STOP_PENDING_INTENT_REQUEST_CODE,
                forceStopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        createNotificationChannel()
        val notificationBuilder = NotificationCompat.Builder(this, KEY_NOTIFICATION_CHANNEL)
                .setContentTitle(getString(commonR.string.music_control_active))
                .setContentText(getString(R.string.tap_to_force_stop))
                .setContentIntent(stopSelfPendingIntent)
                // Tapping the body keeps today's behavior (stop the service); the two actions
                // make the choice explicit - "stop" is the same, "force stop" additionally
                // unbinds the notification listener and kills the process outright.
                .addAction(R.drawable.ic_nav_stopped,
                        getString(R.string.notification_action_stop), stopSelfPendingIntent)
                .addAction(R.drawable.ic_music_off,
                        getString(R.string.notification_action_force_stop), forceStopPendingIntent)
                // ic_notification_brand, not ic_app_brand: same logo, but re-padded to the
                // standard notification-glyph fill - the raw brand asset has so much built-in
                // padding it rendered visibly smaller than other apps' status icons.
                .setSmallIcon(R.drawable.ic_notification_brand)
                // Brand accent instead of the system default grey - matches the color used
                // everywhere else outside MainActivity (LyraAccent is the single source of truth
                // there too), rather than mixing in a separate hardcoded value here.
                .setColor(LyraAccent.resolve(this))

        contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, volumeContentObserver)


        // This is still needed for Pre-O versions, so it must be used, even if it is deprecated.
        @Suppress("DEPRECATION")
        notificationBuilder.priority = Notification.PRIORITY_MIN

        // ServiceCompat passes the FGS type on API 29+ (required on API 34+) and is a no-op arg
        // on older versions, so this stays correct across the minSdk 23.. range.
        //
        // onCreate can run after a background startForegroundService() (WatchListenerService /
        // NotificationService). On API 31+ the promotion to foreground can still be refused with
        // ForegroundServiceStartNotAllowedException (an IllegalStateException) when the OS considers
        // us fully backgrounded. Catch it and stop the service rather than crash: letting onCreate
        // throw kills the process, and swallowing it while staying alive gets us killed later for
        // "did not call startForeground()". stopSelf() satisfies the start-timeout contract cleanly;
        // control resumes the next time the app is foregrounded or the phone starts playing.
        try {
            ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID_PERSISTENT,
                    notificationBuilder.build(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } catch (e: IllegalStateException) {
            Timber.w(e, "Could not promote MusicService to foreground from background; stopping")
            stopSelf()
            return false
        }
        return true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        // The two stop actions are handled before promoteToForeground(), and that order is the
        // point: promoting re-posts the very notification the user just tapped Stop on, so the
        // entry visibly blinks back into the shade and only leaves once the service finishes
        // tearing down (or, for Force stop, once the process dies). Taking it down first makes the
        // tap read the way it should - the notification goes, then the app does.
        //
        // Skipping the promotion is safe for exactly these two and nothing else: they arrive only
        // from this notification's own PendingIntents, which are plain startService calls and only
        // reachable while the notification - and therefore the foreground service - already exists.
        // There is no outstanding startForegroundService() contract for them to answer, and
        // stopSelf() below satisfies the start-timeout contract anyway.
        if (action == ACTION_STOP_SELF || action == ACTION_FORCE_STOP) {
            // The watch is a separate app on a separate device and nothing else would ever tell it.
            // Both taps mean "stop the app", and until now they stopped only the phone half, which
            // left the watch holding an ongoing-activity chip and a proxy media session pointing at
            // a service that no longer existed. Sent before the teardown below because Force stop
            // kills this process outright.
            val watchNotified = notifyWatchOfShutdown(force = action == ACTION_FORCE_STOP)
            removeServiceNotifications()
            if (action == ACTION_FORCE_STOP) {
                // "Force stop" ends the whole app, not just this service. Unbind the notification
                // listener first so the system doesn't restart the process for it right away
                // (it stays unbound until reboot or a listener-access toggle).
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    // Straight at the connected listener instance: it lives in this same process,
                    // so this takes effect immediately. The startService route only reaches it if
                    // our own onStartCommand runs before the kill, a race this path regularly
                    // loses - and losing it means the system rebinds the listener, revives the
                    // process and posts the persistent notification all over again.
                    try {
                        if (!NotificationService.requestUnbindNow()) {
                            startService(Intent(this, NotificationService::class.java)
                                    .setAction(NotificationService.ACTION_UNBIND_SERVICE))
                        }
                    } catch (e: RuntimeException) {
                        Timber.w(e, "Listener unbind failed")
                    }
                }
                stopSelf()
                // Kill once the watch has actually been told, rather than on a fixed delay: a
                // Bluetooth hand-off regularly takes longer than the 500 ms that used to be
                // allowed, and this process dying is what cancels the send. The unconditional
                // backstop is what keeps that from turning into "Force stop sometimes doesn't
                // stop" when the watch is unreachable and the send never completes at all.
                val kill = Runnable {
                    android.os.Process.killProcess(android.os.Process.myPid())
                }
                val handler = Handler(Looper.getMainLooper())
                SHUTDOWN_SCOPE.launch {
                    watchNotified.join()
                    handler.removeCallbacks(kill)
                    handler.postDelayed(kill, FORCE_STOP_KILL_DELAY_MS)
                }
                handler.postDelayed(kill, FORCE_STOP_KILL_MAX_DELAY_MS)
            } else {
                // Ordinary "Stop". onDestroy would clear the notification too, but only whenever
                // teardown actually runs; doing it here means the shade is clear on the tap.
                stopSelf()
            }
            return Service.START_NOT_STICKY
        }

        // Before any branch below, because several of them call stopSelf(). A start delivered by
        // startForegroundService() must be answered with startForeground() even when the answer is
        // "and now stop" - otherwise the process is killed for missing the deadline, which is
        // exactly the crash this guards. onCreate only covers the run that created the service.
        promoteToForeground()

        if (action == ACTION_START_FROM_WATCH) {
            startedFromWatch = true
        } else if (!startedFromWatch) {
            // ACTION_STOP_SELF used to share this branch; it returns above now, so what is left is
            // the real case here - a start that is not from the watch and has no business keeping
            // a service alive that only exists to serve one.
            stopSelf()
            return Service.START_NOT_STICKY
        } else if (action == ACTION_NOTIFICATION_SERVICE_ACTIVATED) {
            mediaSessionProvider.activate()
            NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID_SERVICE_ERROR)
        }

        super.onStartCommand(intent, flags, startId)
        return Service.START_STICKY
    }

    /**
     * Detaches this service from the foreground and clears its persistent notification.
     *
     * Explicit rather than implicit: teardown normally removes a foreground notification for free,
     * but only when the service is actually destroyed in an orderly way. The force-stop path kills
     * the process outright, and `stopForeground` alone is not enough either - it detaches the
     * notification from the service without necessarily cancelling it, which is precisely how a
     * "Music control active" entry outlives the app that posted it. Cancelling by id afterwards is
     * unconditional and idempotent, so calling this when nothing is posted costs nothing.
     *
     * Deliberately leaves [NOTIFICATION_ID_SERVICE_ERROR] alone. That one is not a status readout
     * but a standing prompt to grant notification access, and this service stopping is the *normal*
     * consequence of not having it - clearing it here would delete the only route back to the
     * setting at exactly the moment it is needed. It has its own lifecycle: it is cancelled when
     * access is actually granted (see ACTION_NOTIFICATION_SERVICE_ACTIVATED).
     */
    /**
     * Tells the watch to shut down too, returning the job so "Force stop" can wait for it.
     *
     * Deliberately **not** on [lifecycleScope]. Both callers call `stopSelf()` immediately after,
     * and that scope is cancelled at `onDestroy` - which lands within milliseconds and would
     * routinely cancel the very send this exists to make. [SHUTDOWN_SCOPE] is process-scoped, so
     * the only thing that can cut it short is the process itself going away, which for Force stop
     * is precisely the event being waited on.
     */
    private fun notifyWatchOfShutdown(force: Boolean): Job {
        val path = if (force) {
            CommPaths.MESSAGE_FORCE_STOP_WATCH_APP
        } else {
            CommPaths.MESSAGE_STOP_WATCH_APP
        }
        return SHUTDOWN_SCOPE.launch {
            try {
                Wearable.getMessageClient(applicationContext).sendMessageToNearestClient(
                        Wearable.getNodeClient(applicationContext), path)
            } catch (e: Exception) {
                // No watch paired, out of range, or Play Services unavailable. Nothing to recover:
                // the phone half stops regardless, which is what the user actually tapped.
                Timber.w(e, "Could not tell the watch to stop")
            }
        }
    }

    private fun removeServiceNotifications() {
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID_PERSISTENT)
        } catch (e: Exception) {
            Timber.w(e, "Failed to remove service notifications")
        }
    }

    override fun onDestroy() {
        Timber.d("Service stopped")

        // The ordinary stop path ("Stop", an idle timeout, the system reclaiming us) goes through
        // here rather than through the force-stop branch, and gets the same guarantee: nothing this
        // service posted is left in the shade once it is gone.
        removeServiceNotifications()

        try {
            messageClient.removeListener(this)
        } catch (e: Exception) {
            Timber.w(e, "Failed to remove Wearable message client listener")
        }

        ackTimeoutHandler.removeCallbacksAndMessages(null)
        queueRefreshHandler.removeCallbacksAndMessages(null)
        MediaNotificationActions.removeListener(notificationActionsChanged)
        AppGlyphStore.removeListener(appGlyphLearned)
        preferences.unregisterOnSharedPreferenceChangeListener(quickActionsPreferenceChanged)
        contentResolver.unregisterContentObserver(volumeContentObserver)

        active = false

        super.onDestroy()
    }

    private val mediaCallback = Observer<Resource<MediaController>?> {
        when {
            it == null -> {
                currentMediaController = null
                buildMusicStateAndTransmit(null)
            }
            it.status == Resource.Status.ERROR -> {
                transmitError(it.message ?: "")

                if (it.message == getString(R.string.error_notification_access)) {
                    showNotificationServiceErrorNotification()
                }
            }
            else -> {
                currentMediaController = it.data
                buildMusicStateAndTransmit(currentMediaController)
            }
        }
    }

    private val notificationCallback = Observer<ReceivedNotification?> {
        if (it == null) {
            return@Observer
        }

        val putDataRequest = PutDataRequest.create(CommPaths.DATA_NOTIFICATION)

        val protoNotification = com.svartifoss.snfell.proto.Notification.newBuilder()
                .setTitle(it.title.trim())
                .setDescription(it.description.trim())
                .setTime(System.currentTimeMillis().toInt())
                .build()

        it.imageDataPng?.let { imageData ->
            val albumArtAsset = Asset.createFromBytes(imageData)
            putDataRequest.putAsset(CommPaths.ASSET_NOTIFICATION_BACKGROUND, albumArtAsset)
        }

        putDataRequest.data = protoNotification.toByteArray()
        putDataRequest.setUrgent()

        lifecycleScope.launchWithPlayServicesErrorHandling(this) {
            dataClient.putDataItem(putDataRequest).await()
        }

        startTimeout()
    }

    private fun updateVolume(newVolume: Float) {
        val previousMediaController = currentMediaController ?: return

        val maxVolume = previousMediaController.playbackInfo?.maxVolume ?: 0
        val newAbsoluteVolume = (maxVolume * newVolume).toInt()
        currentVolume = newAbsoluteVolume
        previousMediaController.setVolumeTo(newAbsoluteVolume, 0)
    }

    private fun seekTo(positionMs: Long) {
        currentMediaController?.transportControls?.seekTo(positionMs)
    }

    /** Seeks by [deltaMs] relative to the session's LIVE position. Senders like the Tile only
     *  hold a snapshot that may be many seconds stale (30s refresh), so the phone - not the
     *  sender - resolves the actual target position. */
    private fun seekRelative(deltaMs: Long) {
        val controller = currentMediaController ?: return
        val position = controller.playbackState?.position ?: return
        val duration = controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val target = (position + deltaMs).coerceAtLeast(0L)
                .let { if (duration > 0) it.coerceAtMost(duration) else it }
        controller.transportControls.seekTo(target)
    }

    private var preMuteVolume = 0

    /** Toggles mute on the active media session, restoring the previous level when unmuting. */
    fun toggleMute() {
        val mediaController = currentMediaController ?: return
        val playbackInfo = mediaController.playbackInfo ?: return
        val maxVolume = playbackInfo.maxVolume

        if (playbackInfo.currentVolume > 0) {
            preMuteVolume = playbackInfo.currentVolume
            mediaController.setVolumeTo(0, 0)
        } else {
            val restored = if (preMuteVolume > 0) preMuteVolume else maxVolume / 2
            mediaController.setVolumeTo(restored, 0)
        }
    }

    private fun togglePlayPause() {
        val controller = currentMediaController
        if (controller != null) {
            // Call the session's transport controls directly (play()/pause()) instead of
            // dispatching a PLAY_PAUSE key event. A key event is routed through the media-button
            // dispatcher and the session's onMediaButtonEvent before reaching onPlay/onPause - an
            // extra hop that made the watch button feel slow next to the system media controls,
            // which call the transport controls straight away. We already track playback state, so
            // resolve the toggle here.
            val isPlaying = controller.playbackState?.state == PlaybackState.STATE_PLAYING
            if (isPlaying) {
                controller.transportControls.pause()
            } else {
                controller.transportControls.play()
            }
        } else {
            // Nothing is playing (the watch's "Nothing playing" screen): route a PLAY key through
            // the audio framework so the most recent media app resumes - the same thing the
            // phone's own play button or a Bluetooth remote does.
            resumeLastMediaSession()
        }
    }

    /** Resumes the last-active media session when there is no current one, by dispatching a media
     *  PLAY key through [AudioManager] (framework-routed to the most recent media app). */
    private fun resumeLastMediaSession() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
    }

    private fun usesSessionQuickActions(): Boolean =
            Preferences.getString(preferences, MiscPreferences.WEAR_QUICK_PANEL_SOURCE) == "session"

    private fun customActionSemantic(action: PlaybackState.CustomAction): String =
            inferMediaActionSemantic(action.action, action.name?.toString())

    private fun customActionCommandId(action: PlaybackState.CustomAction): String =
            customActionSnapshotId(
                    sourceId = action.action,
                    label = action.name?.toString().orEmpty(),
                    semantic = customActionSemantic(action),
                    iconResourceId = action.icon
            )

    /** Drives the watch's quick-actions panel. Notification actions resolve to the original
     * phone-side PendingIntent; MediaSession custom actions are looked up again because a player
     * can replace them between the watch receiving the panel and the user tapping it. */
    private fun executeQuickAction(name: String) {
        if (name.startsWith(SESSION_ACTION_PREFIX)) {
            if (!usesSessionQuickActions()) return
            val id = name.removePrefix(SESSION_ACTION_PREFIX)
            if (MediaNotificationActions.isNotificationAction(id)) {
                val controller = currentMediaController ?: return
                if (MediaNotificationActions.execute(
                                actionId = id,
                                packageName = controller.packageName,
                                sessionToken = controller.sessionToken
                        )) {
                    scheduleStateRefresh()
                }
                return
            }
            val customActions = currentMediaController?.playbackState?.customActions.orEmpty()
            val action = if (isCustomActionSnapshotId(id)) {
                // Snapshot ids include the source id, label, semantic meaning and icon resource.
                // If an app repurposes an id before this tap arrives, it deliberately will not
                // match and no unrelated function is executed under the old icon.
                customActions.firstOrNull { customActionCommandId(it) == id }
            } else {
                // Compatibility with a watch that cached a state from an older phone build.
                customActions.firstOrNull { it.action == id }
            }
                    ?: return
            currentMediaController?.transportControls?.sendCustomAction(action.action, action.extras)
            scheduleStateRefresh()
            return
        }

        // The dedicated Like button: try the MediaSession custom action, then the app's like/save
        // notification action (Spotify exposes "like" only there, in its expanded actions).
        if (name == "like" && executeLikeCommand()) return

        val action: PhoneAction = when (name) {
            "like" -> LikeAction(this)
            "shuffle" -> ShuffleAction(this)
            "repeat" -> RepeatAction(this)
            else -> return
        }

        executeAction(action)
    }

    private fun executeLikeCommand(): Boolean {
        val controller = currentMediaController ?: return false
        controller.playbackState?.let { state ->
            LikeAction.findLikeCustomAction(state)?.let { custom ->
                controller.transportControls.sendCustomAction(custom.action, custom.extras)
                scheduleStateRefresh()
                return true
            }
        }
        if (MediaNotificationActions.executeLike(controller.packageName, controller.sessionToken)) {
            scheduleStateRefresh()
            return true
        }
        return false
    }

    private fun executeAction(buttonInfo: ButtonInfo) {
        val playing = currentMediaController?.isPlaying() == true

        val config = if (playing)
            config.getPlayingConfig()
        else
            config.getStoppedConfig()

        val buttonAction = config.getScreenAction(buttonInfo) ?: return
        executeAction(buttonAction)
    }

    private fun executeMenuAction(index: Int) {
        val config = config.getActionList()
        val list = config.actions

        if (index < 0 || index >= list.size) {
            Timber.e("Action out of bounds: %d", index)
            return
        }

        executeAction(list[index])
    }

    private fun executeAction(action: PhoneAction) {
        lifecycleScope.launchWithPlayServicesErrorHandling(this) {
            @Suppress("UNCHECKED_CAST")
            val handler = actionHandlers[action.javaClass] as ActionHandler<PhoneAction>?
                    ?: throw IllegalStateException("Action handler for $action missing")

            handler.handleAction(action)
        }
    }

    private fun buildMusicStateAndTransmit(mediaController: MediaController?) {
        val musicStateBuilder = MusicState.newBuilder()
        var albumArt: Bitmap? = null

        musicStateBuilder.time = System.currentTimeMillis().toInt()

        if (mediaController == null) {
            musicStateBuilder.playing = false
        } else {
            val playbackState = mediaController.playbackState

            musicStateBuilder.playing = playbackState?.isPlaying() == true

            val meta = mediaController.metadata
            if (meta != null) {
                val newArtist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                val newTitle = meta.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""

                if (newArtist.isNotEmpty()) {
                    musicStateBuilder.artist = newArtist
                }
                if (newTitle.isNotEmpty()) {
                    musicStateBuilder.title = newTitle
                }

                recordTrackHistoryIfChanged(newArtist, newTitle)

                // When this track was first read, so a position sample that predates it can be
                // recognised as belonging to the previous one - see the playback-state block
                // below and PlaybackPositionEstimate.sampleBelongsToTrack.
                // Artist and title only, deliberately not the duration: streaming players
                // routinely publish metadata with a duration of 0 and fill it in a moment later,
                // which would read as a second track change and reset the marker below a second
                // time - rejecting a position sample that was perfectly valid.
                val trackKey = "$newArtist|$newTitle"
                if (trackKey != lastSeenTrackKey) {
                    lastSeenTrackKey = trackKey
                    trackFirstSeenRealtimeMs = android.os.SystemClock.elapsedRealtime()
                }

                val artUriString = meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                    ?: meta.getString(MediaMetadata.METADATA_KEY_ART_URI)
                    ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)

                albumArt = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
                    ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
                    // Many apps on Android 10+ provide art as a content:// URI instead of a raw
                    // Bitmap to reduce memory pressure. The system notification resolver handles
                    // these automatically; we need an explicit fallback to match.
                    ?: loadBitmapFromUriCached(artUriString)

                // A streaming client's bitmap is often a thumbnail sized for its own notification -
                // SoundCloud's is 100px square - while transmitToWear scales whatever it gets up to
                // the watch's display. Prefer a larger copy fetched from the address the metadata
                // also carries, when the app published one and the user allows remote covers.
                albumArt = higherResolutionArtOrSame(albumArt, artUriString)

                val duration = meta.getLong(MediaMetadata.METADATA_KEY_DURATION)
                if (duration > 0) {
                    musicStateBuilder.durationMs = duration
                }
            }

            if (playbackState != null) {
                val elapsedRealtimeNow = android.os.SystemClock.elapsedRealtime()

                // Metadata and playback state arrive through separate MediaSession callbacks with
                // no guaranteed order, so right after a track change the new track's title and
                // duration are readable while the position still describes the one that just
                // ended. Attaching them to each other is what made a 2:30 track ending into a 4:00
                // one leave the watch counting 2:31 upwards to 4:00.
                val sampleIsThisTrack = PlaybackPositionEstimate.sampleBelongsToTrack(
                        playbackState.lastPositionUpdateTime, trackFirstSeenRealtimeMs)

                musicStateBuilder.positionMs =
                        if (sampleIsThisTrack) playbackState.position else 0L

                // PlaybackState.lastPositionUpdateTime is in SystemClock.elapsedRealtime() time,
                // not wall-clock time, and the watch has no way to relate its own elapsedRealtime
                // (different device, different boot time) to ours.
                //
                // positionUpdateTime converts it to an epoch timestamp, which only pre-3.2 watch
                // builds still read: having the watch subtract that from its own wall clock is the
                // skew bug positionAgeMs exists to end (see music.proto and
                // PlaybackPositionEstimate). It is still sent so an older watch keeps behaving as
                // it always has rather than losing its progress display entirely.
                musicStateBuilder.positionUpdateTime = if (sampleIsThisTrack) {
                    System.currentTimeMillis() - (elapsedRealtimeNow - playbackState.lastPositionUpdateTime)
                } else {
                    System.currentTimeMillis()
                }

                // The same figure as a plain duration, which is what a current watch uses. Both
                // ends of the subtraction come from this device's monotonic clock, so no foreign
                // clock enters the calculation at any point.
                //
                // A session that has never published a position update time reports 0, which would
                // otherwise be read as "sampled at boot" and hand the watch an age of hours - the
                // guard reports it as current instead, which is the only useful reading available.
                // A rejected sample is reported as "position zero, measured just now" rather than
                // as a very old zero, which the watch would otherwise extrapolate forward again.
                musicStateBuilder.positionAgeMs = if (sampleIsThisTrack) {
                    (elapsedRealtimeNow - playbackState.lastPositionUpdateTime).coerceAtLeast(0L)
                } else {
                    0L
                }

                musicStateBuilder.playbackSpeed = playbackState.playbackSpeed
                musicStateBuilder.seekable = (playbackState.actions and PlaybackState.ACTION_SEEK_TO) != 0L
                // A custom action is authoritative when present; only apps with none at all (e.g.
                // SoundCloud, whose "like" is solely a notification action) fall back to the
                // notification-label guess - see MediaNotificationActions.likedStateForSession.
                musicStateBuilder.liked = if (LikeAction.findLikeCustomAction(playbackState) != null) {
                    LikeAction.isCurrentlyLiked(playbackState)
                } else {
                    MediaNotificationActions.likedStateForSession(
                            mediaController.packageName, mediaController.sessionToken)
                }
                if (usesSessionQuickActions()) {
                    val notificationActions = MediaNotificationActions.actionsForSession(
                            packageName = mediaController.packageName,
                            sessionToken = mediaController.sessionToken
                    )
                    if (notificationActions.isNotEmpty()) {
                        notificationActions.take(MAX_SESSION_QUICK_ACTIONS).forEach { action ->
                            val builder = MediaAction.newBuilder()
                                    .setId(action.id)
                                    .setLabel(action.label)
                                    .setSemantic(action.semantic)
                            action.iconPng?.takeIf { it.isNotEmpty() }?.let {
                                builder.iconPng = ByteString.copyFrom(it)
                            }
                            musicStateBuilder.addMediaActions(builder.build())
                        }
                    } else {
                        // MediaSession custom actions are the fallback for apps whose media
                        // notification does not expose buttons (or while listener access
                        // reconnects). If neither source supplies anything, the watch presents
                        // an explicit unavailable state instead of silently using manual slots.
                        playbackState.customActions.orEmpty()
                                .asSequence()
                                .filter { it.action.isNotBlank() }
                                .filter { customActionSemantic(it) != "dislike" }
                                .distinctBy { it.action }
                                .take(MAX_SESSION_QUICK_ACTIONS)
                                .forEach { customAction ->
                                    val label = customAction.name?.toString().orEmpty()
                                    val semantic = customActionSemantic(customAction)
                                    val iconPng = MediaNotificationActions.loadRemoteActionIcon(
                                            context = this,
                                            packageName = mediaController.packageName,
                                            resourceId = customAction.icon
                                    )
                                    val builder = MediaAction.newBuilder()
                                            .setId(customActionCommandId(customAction))
                                            .setLabel(label)
                                            .setSemantic(semantic)
                                    iconPng?.takeIf { it.isNotEmpty() }?.let {
                                        builder.iconPng = ByteString.copyFrom(it)
                                    }
                                    musicStateBuilder.addMediaActions(builder.build())
                                }
                    }
                }
            }

            // Shuffle/repeat only exist on the AndroidX media-compat layer, not the framework
            // MediaController API - see ShuffleAction/RepeatAction for why this wrapping works.
            val compatController = MediaControllerCompat(
                    this,
                    MediaSessionCompat.Token.fromToken(mediaController.sessionToken)
            )
            // Only treat shuffle as ON when explicitly set to ALL or GROUP.
            // SHUFFLE_MODE_INVALID (-1) is returned by apps that never set shuffle mode, and
            // (-1 != NONE) would have made the button always appear selected. :contentReference[oaicite:0]{index=0}
            musicStateBuilder.shuffleEnabled =
                    compatController.shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_ALL ||
                    compatController.shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_GROUP
            musicStateBuilder.repeatMode = when (compatController.repeatMode) {
                PlaybackStateCompat.REPEAT_MODE_ALL, PlaybackStateCompat.REPEAT_MODE_GROUP -> 1
                PlaybackStateCompat.REPEAT_MODE_ONE -> 2
                else -> 0
            }

            currentVolume = mediaController.playbackInfo?.currentVolume ?: 0

            // Guard the divide-by-zero: a null playbackInfo (or a session reporting maxVolume 0)
            // otherwise made this NaN, which propagated to the watch's volume bar.
            val maxVolume = mediaController.playbackInfo?.maxVolume ?: 0
            musicStateBuilder.volume =
                    if (maxVolume > 0) currentVolume.toFloat() / maxVolume else 0f
        }


        // Resolved before the state is built: the state carries whether this icon is a tintable
        // notification template, and a late-arriving icon has to defeat the dedupe below.
        val sourceIconBytes = resolveSourceIconBytes(mediaController)
        musicStateBuilder.sourceIconTemplate = lastSourceIconIsTemplate
        val sourceIconChanged = !sourceIconBytes.contentEqualsNullable(previousSourceIconBytes)

        val musicState = musicStateBuilder.build()

        // MediaMetadata is immutable, so as long as the source app hasn't published a new
        // MediaMetadata object, repeated getBitmap() calls return the same Bitmap reference -
        // making reference equality a cheap, reliable signal for "is this actually new art".
        // This must be checked in addition to equalsIgnoringTime(): some apps publish metadata
        // in two steps (text fields, then artwork moments later); without this, the artwork-only
        // update would look "equal" on every field that comparison checks and get silently
        // dropped, leaving the watch stuck on stale (or missing) album art until some other
        // unrelated change (e.g. pause) forced a retransmit.
        val albumArtChanged = albumArt !== previousAlbumArt

        // Do not waste BT bandwitch and re-transmit equal music state
        if (!albumArtChanged && !sourceIconChanged &&
                musicState.equalsIgnoringTime(previousMusicState)) {
            return
        }

        Timber.d("TransmittingToWear %s", musicState)
        val trackChanged = previousMusicState?.title != musicState.title ||
                previousMusicState?.artist != musicState.artist
        previousMusicState = musicState
        previousAlbumArt = albumArt
        previousSourceIconBytes = sourceIconBytes
        transmitToWear(musicState, albumArt, sourceIconBytes)

        // Keep the watch's queue data (QueueActivity + the quick panel's "Up Next" preview)
        // in step with playback. It used to be pushed only when explicitly requested, so the
        // preview kept showing "next" relative to whatever old snapshot it had - often the
        // previous or even the current track.
        if (trackChanged) {
            scheduleQueueRefresh()
        }
    }

    /**
     * Debounced re-push of the playback queue custom list. The delay lets the player app settle
     * activeQueueItemId after a track change (many update metadata first, queue position a
     * moment later), and collapses rapid skip-skip-skip into one transmission.
     */
    private fun scheduleQueueRefresh() {
        queueRefreshHandler.removeCallbacksAndMessages(null)
        queueRefreshHandler.postDelayed({
            // At the size the watch last asked for, not the default page. This refresh fires on
            // every track change, so sending a default-sized list here would silently truncate a
            // queue the user had just paged through - the rows would vanish under them mid-scroll.
            openPlaybackQueueOnWatch(lastRequestedQueueLimit)
        }, QUEUE_REFRESH_DEBOUNCE_MS)
    }

    /** PNG bytes of the source-icon face element. Prefers the *media notification's* small icon -
     *  the branded glyph the status bar shows, which is what the element is meant to echo - and
     *  falls back to the launcher icon only when no notification can ever arrive. Skipped
     *  entirely when the user turned the element off, so its bytes never cross Bluetooth then.
     *
     *  The listener stores a new notification slightly *after* the media session publishes the
     *  state change, so on a track or app switch there is a window where this app has no stored
     *  notification yet. Falling back to the launcher icon during that window is what made the
     *  original icon flash before the real one replaced it. With notification access granted the
     *  fallback is therefore suppressed: the last known glyph for the *same* package is held
     *  (silent refresh), and a genuinely new package simply shows no icon for that brief moment
     *  rather than the wrong one. Only without notification access - where the small icon will
     *  never come - is the launcher icon used, and then it is cached per package. */
    /** The element is scoped per now-playing face ([FaceScopedPreferences.SCOPED_KEYS]), so
     *  whether its bytes are worth sending depends on whichever face is actually active - not a
     *  single global on/off. */
    private fun showSourceIconEnabled(): Boolean = FaceScopedPreferences.getBoolean(
            preferences,
            MiscPreferences.WEAR_SHOW_SOURCE_ICON,
            ThemeAppearance.resolve(preferences)
    )

    /** Whether the active face's album art style is one of the Square variants - scoped per face
     *  the same way [showSourceIconEnabled] is, since the style can differ from one face to the
     *  next. Square's entire point is showing the cover uncropped (see [PlayerBackgroundStyle]),
     *  so the pre-transmit resize below must not center-crop it like every other style does. */
    private fun isSquareAlbumArtStyle(): Boolean = PlayerBackgroundStyle.fromPreference(
            FaceScopedPreferences.getString(
                    preferences,
                    MiscPreferences.ALBUM_ART_STYLE,
                    ThemeAppearance.resolve(preferences)
            )
    ).squareCornerRadiusFraction != null

    private fun resolveSourceIconBytes(controller: MediaController?): ByteArray? {
        if (!showSourceIconEnabled()) {
            lastSourceIconPackage = null
            lastSourceIconBytes = null
            lastSourceIconIsTemplate = false
            sourceIconAwaitedOnce = false
            return null
        }
        val packageName = controller?.packageName ?: return null
        if (packageName != lastSourceIconPackage) {
            lastSourceIconPackage = packageName
            lastSourceIconBytes = null
            lastSourceIconIsTemplate = false
            sourceIconAwaitedOnce = false
        }

        MediaNotificationActions.smallIconForSession(packageName, controller.sessionToken)
                ?.let { notificationIcon ->
                    lastSourceIconBytes = notificationIcon
                    lastSourceIconIsTemplate = true
                    return notificationIcon
                }

        // Access granted and this is the *first* state for the app: its notification is almost
        // certainly a beat behind the session. Show nothing for that beat rather than the launcher
        // icon, which is what produced the visible flash. From the next update on, a still-missing
        // notification means this player simply does not post a readable one, so the launcher
        // fallback below applies and those apps keep an icon.
        if (NotificationService.isEnabled(this) && !sourceIconAwaitedOnce) {
            sourceIconAwaitedOnce = true
            return lastSourceIconBytes
        }

        // The glyph this app posted the last time it played, before giving up and using its
        // launcher icon. A player that is running but whose notification this process has not seen
        // yet - a cold start, a listener rebind - would otherwise show a full-colour launcher icon
        // on the seam for the first track and the monochrome glyph from the second on, which reads
        // as the face changing its mind. See AppGlyphStore.
        if (lastSourceIconBytes == null) {
            AppGlyphStore.glyph(this, packageName)?.let { remembered ->
                lastSourceIconBytes = remembered
                lastSourceIconIsTemplate = true
                return remembered
            }
        }

        if (lastSourceIconBytes == null) {
            lastSourceIconBytes = try {
                rasterizeSourceIcon(packageManager.getApplicationIcon(packageName))
            } catch (_: Exception) {
                null
            }
            lastSourceIconIsTemplate = false
        }
        return lastSourceIconBytes
    }

    private fun rasterizeSourceIcon(drawable: android.graphics.drawable.Drawable): ByteArray? {
        // Matches MediaNotificationActions.SOURCE_ICON_SIZE_PX: the Split face draws this as its
        // seam mark at up to 52dp, so a 48px source came out visibly pixelated on a high-density
        // round watch. This is one icon per playing app, not per state, so the extra bytes are
        // paid about as rarely as an icon can be.
        val size = 144
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(android.graphics.Canvas(bitmap))
        return ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        }
    }

    private fun transmitToWear(
            musicState: MusicState,
            originalAlbumArt: Bitmap?,
            sourceIconBytes: ByteArray?
    ) {
        val mySequence = ++transmitSequence

        lifecycleScope.launchWithPlayServicesErrorHandling(this) {
            // Before any art work: this is the copy that decides how quickly a track change shows
            // up on the wrist, and everything below it can suspend for hundreds of milliseconds
            // encoding a cover.
            sendMusicStateMessage(musicState)

            val previousArtSource = lastSerializedArtSource
            val artChanged = when {
                originalAlbumArt === previousArtSource -> false
                originalAlbumArt == null || previousArtSource == null -> true
                // Same pixels behind a new object: players often republish MediaMetadata on
                // play/pause, handing us a fresh Bitmap for unchanged art. Treating that as
                // "new art" shipped a state-only put first, which blanked the cover on the
                // watch for a moment on every pause. Pixel-compare and keep the cached bytes.
                originalAlbumArt.sameAs(previousArtSource) -> {
                    lastSerializedArtSource = originalAlbumArt
                    false
                }
                else -> true
            }

            // The Data Layer only delivers a DataItem to the watch after all attached assets
            // finished crossing Bluetooth - bundling new art with the state made the new track's
            // title/artist wait for the cover transfer. When art has to travel, ship the state
            // alone first (delivered near-instantly) and attach the cover in a second put.
            if (originalAlbumArt != null && artChanged) {
                val stateOnlyRequest = PutDataRequest.create(CommPaths.DATA_MUSIC_STATE)
                // albumArtPending tells the watch "the cover for this state is in transit" so it
                // keeps the previous cover up (smooth crossfade once the new one lands) instead
                // of blanking - as opposed to a final state that genuinely has no art.
                stateOnlyRequest.data = musicState.toBuilder()
                        .setAlbumArtPending(true)
                        .setSeq(nextMusicSeq())
                        .build().toByteArray()
                stateOnlyRequest.setUrgent()
                dataClient.putDataItem(stateOnlyRequest).await()
            }

            val artBytes = when {
                originalAlbumArt == null -> null
                !artChanged -> lastSerializedArt
                else -> withContext(Dispatchers.Default) {
                    // Off the main thread (this used to PNG-encode on main for every transmit)
                    // and as JPEG: album art has no alpha, encodes much faster, and comes out
                    // several times smaller than PNG - directly cutting the Bluetooth transfer
                    // that delays the cover on the watch.
                    var albumArt = originalAlbumArt
                    val watchInfo = watchInfoProvider.value?.watchInfo
                    if (watchInfo != null) {
                        // Square styles show the cover uncropped, letterboxed inside a square
                        // inset - the watch already renders that correctly, but only if the
                        // bitmap it receives still has its original aspect ratio. Center-cropping
                        // it to the watch's (square) display here, like every other style wants,
                        // would destroy exactly what Square is supposed to preserve before the
                        // watch ever sees it - shrinkPreservingRatio keeps the whole image instead,
                        // just scaled down for the transfer.
                        albumArt = if (isSquareAlbumArtStyle()) {
                            BitmapUtils.shrinkPreservingRatio(albumArt,
                                    watchInfo.displayWidth,
                                    watchInfo.displayHeight)
                        } else {
                            BitmapUtils.resizeAndCrop(albumArt,
                                    watchInfo.displayWidth,
                                    watchInfo.displayHeight,
                                    true)
                        }
                    }

                    ByteArrayOutputStream().use { stream ->
                        albumArt.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                        stream.toByteArray()
                    }
                }.also {
                    lastSerializedArtSource = originalAlbumArt
                    lastSerializedArt = it
                }
            }

            // While this coroutine was suspended encoding art, a newer state may already have
            // been transmitted - putting ours now would overwrite fresh state with stale.
            if (transmitSequence != mySequence) {
                return@launchWithPlayServicesErrorHandling
            }

            val putDataRequest = PutDataRequest.create(CommPaths.DATA_MUSIC_STATE)
            if (artBytes != null) {
                putDataRequest.putAsset(CommPaths.ASSET_ALBUM_ART, Asset.createFromBytes(artBytes))
            }
            if (sourceIconBytes != null) {
                putDataRequest.putAsset(
                        CommPaths.ASSET_SOURCE_ICON, Asset.createFromBytes(sourceIconBytes))
            }
            // Stamp the sequence at the actual put (not on the early stateBytes snapshot) so it
            // reflects true send order relative to the state-only put above.
            putDataRequest.data = musicState.toBuilder().setSeq(nextMusicSeq()).build().toByteArray()
            putDataRequest.setUrgent()

            dataClient.putDataItem(putDataRequest).await()
            startTimeout()
        }
    }

    private fun transmitError(error: String) = lifecycleScope.launchWithPlayServicesErrorHandling(this) {
        // Invalidate any in-flight transmitToWear still encoding art, so it can't finish after
        // this and overwrite the error with stale "playing" state.
        ++transmitSequence

        val musicStateBuilder = MusicState.newBuilder()

        // Add time to the first message to make sure it gets transmitted even if it is
        // identical to the previous one
        musicStateBuilder.time = System.currentTimeMillis().toInt()

        musicStateBuilder.error = true
        musicStateBuilder.title = error
        musicStateBuilder.playing = false

        val musicState = musicStateBuilder.build()

        // Record the error as the last-transmitted state (buildMusicStateAndTransmit bypasses this
        // path entirely, so it wouldn't otherwise know an error went out). Without this, if
        // playback recovers to exactly the pre-error state, that recovered state compares equal to
        // the stale previousMusicState and gets deduped away - leaving the watch stuck on the error
        // screen. The error field differs, so comparing against the error state forces a re-send.
        previousMusicState = musicState
        previousAlbumArt = null

        sendMusicStateMessage(musicState)

        val putDataRequest = PutDataRequest.create(CommPaths.DATA_MUSIC_STATE)

        putDataRequest.data = musicState.toBuilder().setSeq(nextMusicSeq()).build().toByteArray()
        putDataRequest.setUrgent()

        dataClient.putDataItem(putDataRequest).await()
    }

    /**
     * Sends [musicState] to the watch over MessageClient, alongside the DataItem put that follows.
     *
     * See [CommPaths.MESSAGE_MUSIC_STATE]. The seq is taken here so it orders correctly against the
     * puts below - the watch keeps whichever arrives first and drops the rest by content, so the
     * duplicate costs one comparison rather than a second pass over every observer.
     *
     * Skipped when the payload is too large. A MusicState is normally a few hundred bytes, but it
     * carries the media notification's rasterized action icons inline, and a player with many
     * actions could in principle push it past what a message may hold. Falling back to the DataItem
     * alone is exactly the behaviour that shipped before this existed, so the failure mode is
     * "as slow as it used to be" rather than "no state at all".
     */
    private suspend fun sendMusicStateMessage(musicState: MusicState) {
        val bytes = musicState.toBuilder().setSeq(nextMusicSeq()).build().toByteArray()
        if (bytes.size > MUSIC_STATE_MESSAGE_MAX_BYTES) {
            Timber.d("Music state too large for a message (%d bytes) - DataItem only", bytes.size)
            return
        }
        try {
            Wearable.getMessageClient(applicationContext).sendMessageToNearestClient(
                    Wearable.getNodeClient(applicationContext),
                    CommPaths.MESSAGE_MUSIC_STATE,
                    bytes)
        } catch (e: Exception) {
            // No watch in range, or Play Services unavailable. The DataItem below is the durable
            // path and still carries this state whenever the watch comes back.
            Timber.d(e, "Could not send the music state as a message")
        }
    }

    private fun showNotificationServiceErrorNotification() {
        val notificationManagerIntent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")

        val notificationManagerPendingIntent = PendingIntent.getActivity(this,
                STOP_SELF_PENDING_INTENT_REQUEST_CODE,
                notificationManagerIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        createNotificationChannel()
        val notificationBuilder = NotificationCompat.Builder(this, KEY_NOTIFICATION_CHANNEL_ERRORS)
                .setContentTitle(getString(R.string.notification_access_notification_title))
                .setContentText(getString(R.string.notification_access_notification_title_description))
                .setContentIntent(notificationManagerPendingIntent)
                // ic_notification_brand, not ic_app_brand: same logo, but re-padded to the
                // standard notification-glyph fill - the raw brand asset has so much built-in
                // padding it rendered visibly smaller than other apps' status icons.
                .setSmallIcon(R.drawable.ic_notification_brand)


        // targetSdk 33+ (Android 13) gates notifications behind POST_NOTIFICATIONS. If the user
        // denied it (see MainActivity.maybeRequestNotificationPermission), this notification is
        // simply skipped - the notification-access error is still logged/handled elsewhere, only
        // this visible nudge is suppressed, same as any other manually-denied notification.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID_SERVICE_ERROR,
                    notificationBuilder.build())
        }
    }

    private fun onWatchSwipeExited() {
        if (!Preferences.getBoolean(preferences, MiscPreferences.PAUSE_ON_SWIPE_EXIT)) {
            return
        }

        if (currentMediaController?.isPlaying() == true) {
            currentMediaController?.transportControls?.pause()
        }
    }

    /**
     * Loads a [Bitmap] from a local content:// URI. Skips http/https URIs to avoid blocking the
     * main thread on network I/O. Returns null on any error.
     */
    /**
     * [loadBitmapFromUri] with a one-entry cache keyed by the URI string. Besides skipping a
     * redundant decode on every state callback, this keeps the returned Bitmap
     * *reference-stable* while the URI doesn't change - which is what the identity-based
     * "did the art change" checks in [buildMusicStateAndTransmit]/[transmitToWear] need.
     * A fresh decode per call made every state change look like it carried new art.
     */
    /** Remote player covers already downloaded this process, keyed by the metadata address. Small
     *  because it only ever holds the current track's cover and the one before it. */
    private val remotePlayerArt = object : LinkedHashMap<String, Bitmap>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean =
                size > 2
    }
    private var remotePlayerArtInFlight: String? = null

    /** Last line [reportArtUpgrade] emitted, so a decision that repeats every state tick is
     *  reported once instead of once per second. */
    private var lastArtUpgradeReport: String? = null

    /**
     * Traces why the now-playing cover was or was not upgraded - see [higherResolutionArtOrSame].
     *
     * Every branch of that function is a silent `return current`, and which one fires decides
     * whether a player's soft cover is fixable at all. Deduplicated by message so the ~1/s state
     * tick reports a steady situation once; a changed situation logs again immediately.
     */
    private fun reportArtUpgrade(reason: String) {
        if (reason == lastArtUpgradeReport) return
        lastArtUpgradeReport = reason
        Timber.i("Player cover: %s", reason)
    }

    /**
     * [current], or a larger copy of the same cover downloaded from [artUriString].
     *
     * Why this exists: `transmitToWear` scales the cover it is given up to the watch's display
     * (~450px square), so a source smaller than that is *stretched* and lands visibly soft. Several
     * streaming clients publish a bitmap sized for their own phone notification - SoundCloud's is
     * 100px - while the same metadata carries an address the full-size cover can be fetched from.
     * `loadBitmapFromUri` deliberately refuses http(s), so that address was previously unreachable
     * and the small bitmap was all the watch ever saw.
     *
     * Never blocks the state build: a miss returns what we already have and downloads in the
     * background, then [scheduleStateRefresh] rebuilds the state so the next pass finds the cache
     * warm. That matters because this runs on every state tick, roughly once a second while a track
     * plays. `remotePlayerArtInFlight` keeps those ticks from starting the same download repeatedly.
     *
     * **This is a network path**, gated on the same `queue_remote_artwork` preference (and disk
     * cache) as the queue's covers - keep `docs/privacy-policy.md` and the Data Safety draft in step
     * with it, as that one already is.
     */
    private fun higherResolutionArtOrSame(current: Bitmap?, artUriString: String?): Bitmap? {
        val have = current?.let { "${it.width}x${it.height}" } ?: "no bitmap"
        val wanted = watchInfoProvider.value?.watchInfo?.displayWidth
        if (wanted == null || wanted <= 0) {
            reportArtUpgrade("no watch display width reported yet (have $have)")
            return current
        }
        // Nothing to gain once the source already covers the watch.
        if (current != null && current.width >= wanted && current.height >= wanted) {
            reportArtUpgrade("have $have, watch wants ${wanted}px - already big enough")
            return current
        }

        val uriString = artUriString?.takeIf { it.isNotEmpty() }
        if (uriString == null) {
            reportArtUpgrade(
                    "have $have and the metadata carries NO artwork address - cannot upgrade")
            return current
        }
        val uri = try {
            Uri.parse(uriString)
        } catch (_: RuntimeException) {
            reportArtUpgrade("unparseable artwork address: $uriString")
            return current
        }
        if (uri.scheme != "http" && uri.scheme != "https") {
            reportArtUpgrade("artwork address is not http(s), so unreachable: $uriString")
            return current
        }
        if (!QueueArtworkResolver.remoteArtworkEnabled(this)) {
            reportArtUpgrade("remote covers are switched off (queue_remote_artwork)")
            return current
        }

        remotePlayerArt[uriString]?.let { cached ->
            // Only an improvement counts; a host that ignored the size request must not push the
            // app into replacing a better bitmap with a worse one on every track.
            return if (current == null || cached.width > current.width) {
                reportArtUpgrade("serving the cached ${cached.width}px cover (had $have)")
                cached
            } else {
                reportArtUpgrade(
                        "cached cover is ${cached.width}px, no better than $have - keeping ours")
                current
            }
        }

        if (remotePlayerArtInFlight != uriString) {
            remotePlayerArtInFlight = uriString
            reportArtUpgrade("have $have, watch wants ${wanted}px - fetching $uriString")
            lifecycleScope.launch {
                // Logged separately from the address above: when the two differ, the size-rewrite
                // rules fired; when they are identical, they did not recognise this host.
                val requested = QueueArtworkResolver.sizedArtworkUrl(uriString, wanted)
                Timber.i("Player cover: requesting %s", requested)
                val fetched = try {
                    QueueArtworkResolver.remoteArtworkForUri(this@MusicService, uri, wanted)
                } catch (e: Exception) {
                    Timber.w(e, "Player cover: fetch threw for %s", requested)
                    null
                }
                if (fetched == null) {
                    Timber.w("Player cover: download produced nothing for %s", requested)
                } else {
                    remotePlayerArt[uriString] = fetched
                    Timber.i("Player cover: got %dpx from %s", fetched.width, requested)
                    // Only worth a rebuild if it actually beats what we sent.
                    if (fetched.width > (current?.width ?: 0)) {
                        scheduleStateRefresh(0L)
                    } else {
                        Timber.w(
                                "Player cover: %dpx is no better than %s - not retransmitting",
                                fetched.width, have)
                    }
                }
                if (remotePlayerArtInFlight == uriString) remotePlayerArtInFlight = null
            }
        }
        return current
    }

    private fun loadBitmapFromUriCached(uriString: String?): Bitmap? {
        if (uriString.isNullOrEmpty()) return null
        if (uriString == lastArtUriString) return lastArtUriBitmap

        val bitmap = loadBitmapFromUri(uriString)
        // Negative results are cached too - retrying a broken URI on every callback would
        // just repeat the failed content-resolver round trip.
        lastArtUriString = uriString
        lastArtUriBitmap = bitmap
        return bitmap
    }

    private fun loadBitmapFromUri(uriString: String?): Bitmap? {
        if (uriString.isNullOrEmpty()) return null
        val uri = Uri.parse(uriString)
        if (uri.scheme == "http" || uri.scheme == "https") return null
        return try {
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            Timber.w(e, "Could not load album art from URI: %s", uriString)
            null
        }
    }

    /**
     * Schedules a forced re-read and retransmit of the current music state after [delayMs].
     *
     * Some apps (e.g. those that toggle a "like" favorite) don't immediately call
     * [android.media.session.MediaSession.setPlaybackState] after updating their internal state,
     * so [onPlaybackStateChanged] never fires. Calling this after executing such an action ensures
     * the watch button reflects the new state within half a second.
     */
    fun scheduleStateRefresh(delayMs: Long = 500L) {
        Handler(Looper.getMainLooper()).postDelayed({
            buildMusicStateAndTransmit(currentMediaController)
        }, delayMs)
    }

    private fun onCustomMenuItemPresed(customListItemAction: CustomListItemAction) {
        if (customListItemAction.entryId == CustomLists.SPECIAL_ITEM_ERROR) {
            return
        }

        when (customListItemAction.listId) {
            CustomLists.PLAYLIST -> onQueueEntrySelected(customListItemAction.entryId)
            CustomLists.SEARCH_RESULTS -> {
                // Shares the library's selection path: browsable rows walk in, playable rows go
                // through MediaBrowserPlayback. The old code issued playFromMediaId on the tracked
                // controller, which does nothing at all when no session is live yet - the usual
                // state when a search is started from the wrist - and is ignored by several apps
                // even when one is.
                lifecycleScope.launchWithPlayServicesErrorHandling(this) {
                    onLibraryEntrySelected(customListItemAction.entryId, startNewWalk = true)
                }
            }
            CustomLists.PLAYLIST_SHORTCUTS -> {
                // The entry id IS the playlist's deep link - see OpenPlaylistShortcutsAction.
                //
                // The saved name has to travel with it: playDeepLink can only play an *artist* by
                // issuing playFromSearch for that name, because an artist URI merely navigates.
                // Without it (as was the case here) picking an artist shortcut from the watch menu
                // just opened the artist page and never started playback, while the very same
                // shortcut assigned to a button worked - that path passes the name.
                val link = customListItemAction.entryId
                val savedName = PlaylistShortcutStorage.load(this)
                        .firstOrNull { it.link == link }
                        ?.name
                playDeepLink(link, savedName)
            }
            CustomLists.HISTORY -> {
                // Past-played entries have no mediaId to resume from - they're just remembered
                // artist/title text (see recordTrackHistoryIfChanged) - so "replay" means
                // searching for that exact track again, same as manually typing it in.
                val entry = recentTrackHistory.getOrNull(customListItemAction.entryId.toIntOrNull() ?: -1)
                        ?: return
                playFromSearch("${entry.artist} ${entry.title}".trim())
            }
            CustomLists.SEARCH_HISTORY -> {
                // The entry id IS the original query text - see OpenSearchHistoryAction.
                playFromSearch(customListItemAction.entryId)
            }
            CustomLists.LIBRARY -> {
                lifecycleScope.launchWithPlayServicesErrorHandling(this) {
                    onLibraryEntrySelected(customListItemAction.entryId)
                }
            }
        }
    }

    /**
     * Plays the queue row the user tapped on the watch.
     *
     * `skipToQueueItem` is issued **unconditionally**, which is deliberate and hard-won: gating it
     * on `ACTION_SKIP_TO_QUEUE_ITEM` broke players that implement the command without advertising
     * it, which is common - the advertised bitmask is a hint, not a contract, and taking it as one
     * diverted a working call into a fallback that then did nothing. An unsupported transport
     * command is a harmless no-op, so the cost of always trying is nothing and the cost of guessing
     * wrong is a dead queue.
     *
     * It goes to the playing session *and*, when the entries came from a different one, to the
     * session that published them (see [resolveQueueSource] - a `queueId` indexes the queue of
     * whoever published it). Sending to both cannot land on the wrong track: the sibling is only
     * consulted when the playing session has no queue at all, where the command is a no-op.
     *
     * Only once that demonstrably changed nothing does the media-id route run, which is the Retro
     * Music case (neither of its sessions implements the command). Verified rather than predicted,
     * for the same reason the gate had to go.
     */
    private fun onQueueEntrySelected(entryId: String) {
        val queueId = QueueEntry.queueId(entryId)
        val mediaId = QueueEntry.mediaId(entryId)
        val playing = currentMediaController
        val owner = resolveQueueSource()?.controller

        val known = queueId != android.media.session.MediaSession.QueueItem.UNKNOWN_ID.toLong()

        // Both read *before* anything is issued. A player that reacts to the skip synchronously
        // would otherwise have already moved by the time these are sampled, making a jump that
        // worked perfectly look like a no-op and earning the track a pointless second start.
        val before = playbackIdentity(playing)
        val alreadyOnThisRow = known && playing?.playbackState?.activeQueueItemId == queueId

        if (known) {
            Timber.d("Queue tap: skipToQueueItem(%d) on %s", queueId, playing?.packageName)
            playing?.transportControls?.skipToQueueItem(queueId)
            if (owner != null && owner.sessionToken != playing?.sessionToken) {
                Timber.d("Queue tap: also on queue owner %s", owner.packageName)
                owner.transportControls.skipToQueueItem(queueId)
            }
        }

        if (mediaId == null) return
        // Tapping the row that is already playing is the one case where "nothing changed" is the
        // correct outcome rather than a failure, so it must not trigger the fallback.
        if (alreadyOnThisRow) return

        val packageName = playing?.packageName ?: owner?.packageName ?: return
        lifecycleScope.launchWithPlayServicesErrorHandling(this) {
            delay(QUEUE_SKIP_VERIFY_MS)
            if (playbackIdentity(currentMediaController) != before) return@launchWithPlayServicesErrorHandling
            Timber.d("Queue tap: skip had no effect on %s; playing media id %s",
                    packageName, mediaId)
            MediaBrowserPlayback.playMediaId(this@MusicService, packageName, mediaId)
        }
    }

    /**
     * What is playing right now, reduced to the fields that must change if a queue jump worked.
     *
     * Title is in there because plenty of sessions never maintain `activeQueueItemId` at all, and a
     * media id because plenty of others publish no title in metadata. Two adjacent queue rows with
     * an identical identity are indistinguishable here - the fallback then replays the track the
     * user asked for, which is the right track either way.
     */
    private fun playbackIdentity(controller: MediaController?): Triple<Long?, String?, String?> =
            Triple(
                    controller?.playbackState?.activeQueueItemId,
                    controller?.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
                    controller?.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE))

    /** Removes one entry from a watch-managed deletable custom list (currently just search
     *  history) and re-pushes the updated list so the watch's menu updates immediately. */
    private fun onCustomMenuItemDeleted(customListItemAction: CustomListItemAction) {
        when (customListItemAction.listId) {
            CustomLists.SEARCH_HISTORY -> {
                SearchHistoryStorage.remove(this, customListItemAction.entryId)
                lifecycleScope.launchWithPlayServicesErrorHandling(this) {
                    sendSearchHistoryToWatch()
                }
            }
        }
    }

    /** Pushes the current search-history list (see [SearchHistoryStorage]) to the watch, e.g.
     *  after a new search is recorded or an entry is deleted from the watch's menu. */
    suspend fun sendSearchHistoryToWatch() {
        val queries = SearchHistoryStorage.load(this)

        val entries = if (queries.isEmpty()) {
            listOf(
                    CustomList.ListEntry.newBuilder()
                            .setEntryId(CustomLists.SPECIAL_ITEM_ERROR)
                            .setEntryTitle(getString(R.string.search_history_empty))
                            .build()
            )
        } else {
            queries.map { query ->
                CustomList.ListEntry.newBuilder()
                        .setEntryId(query)
                        .setEntryTitle(query)
                        .build()
            }
        }

        val protoData = CustomList.newBuilder()
                .addAllActions(entries)
                .setListId(CustomLists.SEARCH_HISTORY)
                .setListTimestamp(System.currentTimeMillis())
                .build()

        val putDataRequest = PutDataRequest.create(CommPaths.DATA_CUSTOM_LIST)
        putDataRequest.data = protoData.toByteArray()

        Wearable.getDataClient(this).putDataItem(putDataRequest).await()
    }

    private fun recordTrackHistoryIfChanged(newArtist: String, newTitle: String) {
        if (newTitle.isEmpty() || (newArtist == lastTrackArtist && newTitle == lastTrackTitle)) {
            return
        }

        if (lastTrackTitle.isNotEmpty()) {
            recentTrackHistory.addFirst(TrackHistoryEntry(lastTrackArtist, lastTrackTitle))
            while (recentTrackHistory.size > MAX_TRACK_HISTORY_SIZE) {
                recentTrackHistory.removeLast()
            }
            TrackHistoryStorage.save(this, recentTrackHistory)
        }

        lastTrackArtist = newArtist
        lastTrackTitle = newTitle
    }

    /**
     * How many queue entries the watch last asked for.
     *
     * Held here because the phone also pushes the queue on its own (see [scheduleQueueRefresh]) and
     * those pushes have no request to take a size from - without remembering it, every track change
     * would reset a paged-through queue to the first page. Reset to the default whenever the watch
     * opens the queue afresh, which it does by asking for exactly one page.
     */
    private var lastRequestedQueueLimit = DEFAULT_QUEUE_PAGE_SIZE

    private fun openPlaybackQueueOnWatch(entryLimit: Int = DEFAULT_QUEUE_PAGE_SIZE) {
        lastRequestedQueueLimit = entryLimit
        executeAction(OpenPlaylistAction(this).apply { this.entryLimit = entryLimit })
    }

    /**
     * Persists a face the user picked from the on-watch picker.
     *
     * The phone is the owner of every synced preference, so this write - not the watch's own - is
     * what makes the choice stick: `WatchPreferenceSyncCoordinator` sees the change and pushes it
     * back, which also updates the phone's picker and live preview with no extra plumbing.
     *
     * Picking a built-in face additionally **deactivates any active custom theme**, for exactly the
     * reason `ConfigBackup.import` does the same: a custom theme owns its own base layout, so
     * letting a bare `wear_screen_face` write land underneath one would silently rewrite a saved
     * profile rather than switch away from it (see ThemeAppearance.resolve). Switching faces is an
     * unambiguous "show me this instead", so leaving the theme active is never the intent.
     */
    private fun applyScreenFaceFromWatch(rawFace: String) {
        val face = rawFace.trim()
        if (face.isEmpty()) return
        
        if (face.startsWith("custom:")) {
            val themeId = face.removePrefix("custom:")
            val repository = WatchThemeRepository(this)
            val profile = repository.profiles.find { it.id == themeId }
            if (profile != null) {
                repository.applyProfile(preferences, profile)
                Timber.i("Face set to custom theme '%s' from the watch", profile.name)
            } else {
                Timber.w("Watch asked for unknown custom theme ID '%s'; ignoring", themeId)
            }
            return
        }

        if (face !in ThemeAppearance.ALLOWED_BASE_FACES) {
            Timber.w("Watch asked for unknown face '%s'; ignoring", rawFace)
            return
        }
        val hadCustomTheme = ThemeAppearance.resolve(preferences) is AppearanceContext.Custom
        preferences.edit().apply {
            putString(MiscPreferences.WEAR_SCREEN_FACE.key, face)
            if (hadCustomTheme) {
                remove(MiscPreferences.WEAR_ACTIVE_CUSTOM_THEME_ID.key)
                putBoolean(MiscPreferences.WEAR_CUSTOM_THEME_COMPLETE.key, false)
            }
        }.apply()
        Timber.i("Face set to '%s' from the watch (custom theme cleared: %b)", face, hadCustomTheme)
    }

    private fun playFromSearch(query: String) {
        if (query.isBlank()) {
            return
        }

        SearchHistoryStorage.record(this, query)

        val controller = currentMediaController
        if (controller == null) {
            // No known music app to target a library search at. Fall back to the generic
            // "play from search" intent, which the user's default music app handles. Best-effort:
            // background activity start restrictions (Android 10+) may swallow this unless the
            // app is exempted (e.g. battery optimization off / companion app).
            launchPlayFromSearchIntent(query)
            return
        }

        lifecycleScope.launchWithPlayServicesErrorHandling(this) {
            // Library search (MediaBrowserService) gives a pickable result list, but several big
            // apps allowlist who may connect - YouTube Music both rejects unlisted callers
            // outright (search() returns null) AND, for some accounts/queries, connects fine but
            // answers with zero results (search() returns an empty, non-null list) - either way
            // there's nothing useful to show the user, so both cases fall through the same way.
            val results = MediaBrowserSearch.search(this@MusicService, controller.packageName, query)

            if (results.isNullOrEmpty()) {
                // Those same apps DO handle the Assistant's voice-search activity intent, which
                // plays the best match directly. Session-level playFromSearch stays as the very
                // last resort - many apps (YouTube Music included) silently ignore it.
                if (!launchPlayFromSearchIntent(query, controller.packageName)) {
                    // Routed through the browser connection rather than fired at the tracked
                    // controller. Retro Music is the case that forced this: its play capabilities
                    // live on the browser service's session, so the old call went to a session that
                    // never implemented the command. MediaBrowserPlayback also checks whether the
                    // action is advertised at all before spending the connection on it.
                    if (!MediaBrowserPlayback.playSearch(
                                    this@MusicService, controller.packageName, query)) {
                        // Last resort, unchanged: some apps honour this on the playing session even
                        // though they expose no browser service to route it through.
                        controller.transportControls.playFromSearch(query, null)
                    }
                }
                return@launchWithPlayServicesErrorHandling
            }

            sendSearchResultsToWatch(results)
        }
    }

    /**
     * Fires the same voice-search activity intent Google Assistant uses to start playback.
     * [packageName] targets a specific player; null lets the user's default music app take it.
     * Returns false when no matching activity exists (e.g. the app has no voice-search entry).
     */
    private fun launchPlayFromSearchIntent(query: String, packageName: String? = null): Boolean {
        val searchIntent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            putExtra(SearchManager.QUERY, query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            setPackage(packageName)
        }

        return try {
            startActivity(searchIntent)
            true
        } catch (e: ActivityNotFoundException) {
            Timber.e("No app handles play-from-search (package=%s)", packageName)
            false
        }
    }

    /** Opens a user-configured streaming link on the phone. This intentionally remains a link
     * hand-off rather than pretending to be an account/API integration. When requested, a known
    * installed streaming app is targeted; every targeted launch has an ACTION_VIEW fallback. */
    /**
     * @param searchQuery the shortcut's name, used as a `playFromSearch` fallback. It is the only
     *   command that reliably plays an *artist* (whose page URL only navigates) and that Spotify
     *   honors from an external controller, so it materially improves both cases.
     */
    /**
     * Tells the watch how a streaming shortcut ended: null means playback started and nothing
     * should be opened, otherwise [openUri] is what the watch should open on this phone.
     *
     * The watch cannot open it up front - see [CommPaths.MESSAGE_DEEP_LINK_VERDICT]. It used to,
     * which brought the target app to the foreground while the silent routes below were still
     * being tried, so a locked phone lit up and switched apps even when the MediaBrowser route
     * went on to work. Sending a verdict on *every* terminal path, success included, is what lets
     * the watch stop waiting instead of falling back on its timeout.
     *
     * Harmless when the shortcut was started from the phone's own UI: a watch with nothing
     * outstanding ignores the verdict.
     */
    private fun sendDeepLinkVerdict(openUri: String?) {
        val payload = (openUri ?: "").toByteArray(Charsets.UTF_8)
        lifecycleScope.launch {
            try {
                Wearable.getMessageClient(applicationContext).sendMessageToNearestClient(
                        Wearable.getNodeClient(applicationContext),
                        CommPaths.MESSAGE_DEEP_LINK_VERDICT,
                        payload)
            } catch (e: Exception) {
                // No watch paired, out of range, or Play Services down. The watch's own backstop
                // covers the case where it was waiting for this.
                Timber.w(e, "Could not send the deep-link verdict to the watch")
            }
        }
    }

    /**
     * Answers the watch's lyrics request for the track it names.
     *
     * The request carries the track rather than the phone reading its own session, because the two
     * sides drift by a track whenever a skip is in flight and lyrics for the wrong song are worse
     * than none - see [CommPaths.MESSAGE_REQUEST_LYRICS]. The request's fields are echoed back so
     * the watch can drop an answer it has already moved past.
     *
     * A reply is sent on **every** path, including the disabled and failed ones. The watch shows a
     * spinner until it hears something, so a silently dropped lookup is a screen that spins
     * forever - the same reason [sendDeepLinkVerdict] answers at every terminal point.
     */
    private fun sendLyricsToWatch(request: LyricsRequest) {
        lifecycleScope.launch {
            val answer = if (!Preferences.getBoolean(preferences, MiscPreferences.LYRICS_ENABLED)) {
                LyricsAnswer(LyricsStatus.DISABLED)
            } else {
                try {
                    LyricsRepository.lyricsFor(
                            request.title, request.artist, request.durationMs)
                } catch (e: Exception) {
                    // The repository already maps the lookup failures it knows about; this catches
                    // whatever it did not, so the watch is never left waiting on a crashed
                    // coroutine.
                    Timber.w(e, "Lyrics lookup failed unexpectedly")
                    LyricsAnswer(LyricsStatus.FAILED)
                }
            }

            val response = LyricsResponse.newBuilder()
                    .setTitle(request.title.orEmpty())
                    .setArtist(request.artist.orEmpty())
                    .setDurationMs(request.durationMs)
                    .setStatus(answer.status)
                    .apply {
                        answer.lrc?.let { setLrc(it) }
                        answer.plain?.let { setPlain(it) }
                    }
                    .build()

            try {
                Wearable.getMessageClient(applicationContext).sendMessageToNearestClient(
                        Wearable.getNodeClient(applicationContext),
                        CommPaths.MESSAGE_LYRICS_RESULT,
                        response.toByteArray())
            } catch (e: Exception) {
                // Watch went out of range while we were fetching. Its own screen times out.
                Timber.w(e, "Could not send lyrics to the watch")
            }
        }
    }

    /**
     * Answers the watch's "where is playback actually at?" with a live reading.
     *
     * Read from the session here and now, deliberately not from [previousMusicState]: that is the
     * last state *transmitted*, and the whole reason this path exists is that the phone suppresses
     * position-only retransmissions, so it can be a whole track old. Reusing it would answer the
     * question with the very number the watch is already extrapolating from.
     *
     * [token] is the watch's own monotonic clock reading, echoed back untouched. It is opaque here
     * and must stay that way - it is what lets the watch measure the round trip without either side
     * reading the other's clock, the same rule [PlaybackPositionEstimate] enforces for the sample
     * itself.
     *
     * An answer goes out on every path, including "nothing is playing", for the reason
     * [sendLyricsToWatch] and [sendDeepLinkVerdict] both document: the requester is waiting, and a
     * silently dropped reply is indistinguishable from a lost one.
     */
    private fun sendPlaybackSyncToWatch(token: Long) {
        val builder = PlaybackSync.newBuilder().setToken(token)

        val controller = currentMediaController
        val playbackState = controller?.playbackState
        if (controller != null && playbackState != null) {
            val elapsedRealtimeNow = android.os.SystemClock.elapsedRealtime()
            // The same guard buildMusicStateAndTransmit applies: metadata and playback state arrive
            // through separate callbacks, so right after a track change the position can still
            // describe the track that just ended. Reporting zero is the honest answer; reporting
            // the stale sample would have the watch correct itself to the previous song.
            val sampleIsThisTrack = PlaybackPositionEstimate.sampleBelongsToTrack(
                    playbackState.lastPositionUpdateTime, trackFirstSeenRealtimeMs)
            val meta = controller.metadata

            builder.hasSession = true
            builder.positionMs = if (sampleIsThisTrack) playbackState.position else 0L
            builder.positionAgeMs = if (sampleIsThisTrack) {
                (elapsedRealtimeNow - playbackState.lastPositionUpdateTime).coerceAtLeast(0L)
            } else {
                0L
            }
            builder.playing = playbackState.isPlaying()
            builder.playbackSpeed = playbackState.playbackSpeed
            meta?.getLong(MediaMetadata.METADATA_KEY_DURATION)
                    ?.takeIf { it > 0 }
                    ?.let { builder.durationMs = it }
            builder.title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
            builder.artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
        } else {
            builder.hasSession = false
        }

        val reply = builder.build()
        lifecycleScope.launch {
            try {
                Wearable.getMessageClient(applicationContext).sendMessageToNearestClient(
                        Wearable.getNodeClient(applicationContext),
                        CommPaths.MESSAGE_PLAYBACK_SYNC,
                        reply.toByteArray())
            } catch (e: Exception) {
                // Watch went out of range. Its own request simply goes unanswered, which its next
                // check retries - nothing here needs to recover.
                Timber.v(e, "Could not send the playback sync reply")
            }
        }
    }

    /**
     * Answers the watch's request for everything known about the playing track.
     *
     * Answers **twice** when the optional online lookup is switched on, and the order is the whole
     * design: the local reply goes out first with everything the phone already had, and the
     * enriched one follows if and when MusicBrainz returns. The watch therefore draws its table
     * from the player's own tags immediately and never waits on a network call - the rule this
     * screen was specified around.
     *
     * The request carries the track it is for and the reply echoes it back, for the reason
     * [sendLyricsToWatch] documents: the two sides are a track apart whenever a skip is in flight,
     * and a confident table describing the previous song is worse than none.
     */
    private fun sendTrackMetadataToWatch(request: TrackMetadata) {
        lifecycleScope.launch {
            val probeFile = QueueArtworkResolver.hasMediaPermission(this@MusicService)
            val local = TrackMetadataReader.read(
                    this@MusicService, currentMediaController, probeFile)

            // Only answer for the track that was asked about. Reading the session directly means
            // this can already have moved on, and the watch would have no way to tell.
            if (!local.describesSameTrackAs(request)) {
                Timber.v("Ignoring a metadata request for a track that is no longer playing")
                return@launch
            }
            sendTrackMetadata(local)

            if (!Preferences.getBoolean(preferences, MiscPreferences.METADATA_LOOKUP_ENABLED)) {
                return@launch
            }
            val facts = MusicBrainzMetadata.lookup(local.title, local.artist) ?: return@launch
            // The track can have changed while the lookup was out - the same discard the first
            // reply makes, applied again at the point the second one would be sent.
            if (!TrackMetadataReader.read(this@MusicService, currentMediaController, probeFile = false)
                            .describesSameTrackAs(request)) {
                return@launch
            }
            sendTrackMetadata(local.toBuilder()
                    .apply {
                        enriched = true
                        // Catalogue facts no player publishes: always taken.
                        facts.isrc?.let { isrc = it }
                        facts.label?.let { label = it }
                        facts.releaseDate?.let { releaseDate = it }
                        facts.releaseCountry?.let { releaseCountry = it }
                        facts.recordingMbid?.let { recordingMbid = it }
                        facts.releaseMbid?.let { releaseMbid = it }

                        // The rest fill a *gap* and never overwrite. What the playing app published
                        // describes the thing actually coming out of the speaker; MusicBrainz
                        // describes a recording it matched by name, and on a disagreement the
                        // player is right by definition - it is the one playing the file. This is
                        // also what makes the enrichment worth having for a service like SoundCloud
                        // that publishes almost no tags: every row it adds is a row that was blank.
                        if (!hasAlbum()) facts.album?.let { album = it }
                        if (!hasGenre()) facts.genre?.let { genre = it }
                        if (!hasTrackCount()) facts.trackCount?.let { trackCount = it }
                        if (!hasDurationMs()) facts.durationMs?.let { durationMs = it }
                    }
                    .build())
        }
    }

    /** Title and artist only, trimmed and case-insensitive: the two fields both sides are certain
     *  to express the same way, and the same key `LyricsFeed` matches its answers on. */
    private fun TrackMetadata.describesSameTrackAs(other: TrackMetadata): Boolean =
            title.orEmpty().trim().equals(other.title.orEmpty().trim(), ignoreCase = true) &&
                    artist.orEmpty().trim().equals(other.artist.orEmpty().trim(), ignoreCase = true)

    private suspend fun sendTrackMetadata(metadata: TrackMetadata) {
        try {
            Wearable.getMessageClient(applicationContext).sendMessageToNearestClient(
                    Wearable.getNodeClient(applicationContext),
                    CommPaths.MESSAGE_TRACK_METADATA,
                    metadata.toByteArray())
        } catch (e: Exception) {
            // Watch out of range. Its own screen keeps whatever it already had.
            Timber.v(e, "Could not send track metadata to the watch")
        }
    }

    fun playDeepLink(link: String, searchQuery: String? = null) {
        if (!StreamingShortcutLinks.isSafeLink(link)) {
            Timber.e("Refusing unsafe or invalid streaming link")
            // Refusing is still a terminal outcome, so it owes the watch a verdict like every
            // other one. Without it the watch sat on the request until its 30s backstop expired
            // and then fell back to opening the link itself - which its own scheme check refuses
            // in turn, so the whole half-minute produced nothing. "Do not open" is the honest
            // answer here: this phone declined the link, so there is nothing for the watch to
            // salvage by opening it.
            sendDeepLinkVerdict(null)
            return
        }
        val service = StreamingShortcutLinks.detect(link)
        val contentType = StreamingShortcutLinks.inspect(link).contentType
        val query = searchQuery?.trim()?.takeIf { it.isNotEmpty() }
        // An artist URI is not honored by playFromUri; its named search is what actually plays.
        val preferSearch = contentType == StreamingContentType.ARTIST && query != null
        val openMode = preferences.getString(StreamingShortcutLinks.OPEN_MODE_KEY, null)
                ?: if (preferences.getBoolean(
                                StreamingShortcutLinks.PREFER_INSTALLED_APP_KEY,
                                true)) {
                    StreamingShortcutLinks.OPEN_MODE_APP
                } else {
                    StreamingShortcutLinks.OPEN_MODE_DEFAULT
                }
        val targetPackage = service.packageName?.takeIf { packageName ->
            openMode == StreamingShortcutLinks.OPEN_MODE_APP && isPackageInstalled(packageName)
        }
        val browserLink = StreamingShortcutLinks.forBrowser(link)
        if (openMode == StreamingShortcutLinks.OPEN_MODE_CHOOSER &&
                startStreamingLinkChooser(browserLink)) {
            // The chooser is already on screen; the watch must not open the link a second time.
            sendDeepLinkVerdict(null)
            return
        }

        // Everything after the direct command, factored out so the verification below can fall
        // through to it instead of duplicating it.
        val continueWithBrowserThenVisibleOpen = {
            if (targetPackage != null && (contentType.isPlayable || preferSearch)) {
                startBrowserThenVisibleOpen(
                        link, service, targetPackage, browserLink, query, preferSearch)
            } else {
                startStreamingLinkWithPlaybackNudge(
                        link, service, targetPackage, browserLink, query, preferSearch)
            }
        }

        // ACTION_VIEW only navigates to Spotify content; it does not promise playback. If that
        // app already has a MediaSession, use the Android media contract first (playFromUri, or
        // playFromSearch for artists) so tracks, albums, playlists and artists actually start.
        if (targetPackage != null &&
                requestStreamingPlayback(link, service, targetPackage, query, preferSearch)) {
            // "Accepted" is not "playing". That return value comes from the advertised
            // ACTION_PLAY_FROM_URI bit, and an app can advertise the command, accept the call and
            // do nothing with it - SoundCloud takes a track URI happily and silently ignores a
            // collection one, which is why a saved playlist and the Likes button both looked dead:
            // this branch reported success, so neither the browser route nor the visible open ever
            // ran. Verify the same way a queue tap does rather than trusting the bitmask.
            val controllerBefore = mediaSessionProvider.controllerForPackage(targetPackage)
            val before = playbackIdentity(controllerBefore)
            val wasPlayingBefore = controllerBefore?.isPlaying() == true
            lifecycleScope.launch {
                delay(DEEP_LINK_VERIFY_MS)
                val after = mediaSessionProvider.controllerForPackage(targetPackage)
                // Playing a *different* item, or playing at all where nothing was: either way the
                // command landed. The one case that means it was swallowed is the app carrying on
                // with exactly what it was already playing - which is what a collection URI does to
                // SoundCloud. Note "same item but now playing" counts as success: a link pointing
                // at the paused track is legitimately satisfied by resuming it.
                val movedOn = playbackIdentity(after) != before
                if (after?.isPlaying() == true && (movedOn || !wasPlayingBefore)) {
                    sendDeepLinkVerdict(null)
                    return@launch
                }
                Timber.d("%s accepted the URI but did not start playing; continuing", targetPackage)
                continueWithBrowserThenVisibleOpen()
            }
            return
        }

        continueWithBrowserThenVisibleOpen()
    }

    /** The browser route, falling back to the visible deep-link open - see [playDeepLink]. */
    private fun startBrowserThenVisibleOpen(
            link: String,
            service: StreamingService,
            targetPackage: String,
            browserLink: String,
            query: String?,
            preferSearch: Boolean
    ) {
        // Ask the app's MediaBrowserService to play (the Android Auto/Assistant path). This
        // wakes the app in the background with no Activity launch, so it works with the
        // screen off/locked - where an ACTION_VIEW deep link merely queues navigation that
        // e.g. YouTube Music only acts on once its UI reaches the foreground. Falls back to
        // the visible deep-link flow when the app has no browser service, rejects the
        // connection, or never starts playing.
        lifecycleScope.launch {
            val played = MediaBrowserPlayback.play(
                    this@MusicService,
                    targetPackage,
                    StreamingShortcutLinks.forPlayback(link),
                    query,
                    preferSearch)
            if (played) {
                sendDeepLinkVerdict(null)
                scheduleStateRefresh()
            } else {
                startStreamingLinkWithPlaybackNudge(
                        link, service, targetPackage, browserLink, query, preferSearch)
            }
        }
    }

    /** The visible fallback: resolve the deep link into the target app (or a browser), then keep
     *  nudging its media session for a few seconds because opening content does not start it. */
    private fun startStreamingLinkWithPlaybackNudge(
            link: String,
            service: StreamingService,
            targetPackage: String?,
            browserLink: String,
            searchQuery: String? = null,
            preferSearch: Boolean = false
    ) {
        val primaryLink = if (targetPackage != null) {
            StreamingShortcutLinks.forInstalledApp(link)
        } else browserLink

        // Every silent route is spent, so the link has to be opened visibly - and only the watch
        // can do that reliably, since startStreamingLink below is subject to the background
        // activity-start rules this whole ladder exists to work around. Same `targetPackage|uri`
        // form PlayPlaylistShortcutAction.remoteUri produces.
        sendDeepLinkVerdict(
                if (targetPackage != null) "$targetPackage|$primaryLink" else primaryLink)

        // Sampled before the link opens, so the press-play step below can tell "the app loaded the
        // thing we asked for" apart from "the app is sitting on whatever it had before".
        val identityBeforeOpen = playbackIdentity(
                targetPackage?.let { mediaSessionProvider.controllerForPackage(it) })
        val hadTrackBeforeOpen = identityBeforeOpen.second != null || identityBeforeOpen.third != null

        if (startStreamingLink(primaryLink, targetPackage)) {
            if (targetPackage != null) {
                lifecycleScope.launch {
                    for (i in 0..15) {
                        kotlinx.coroutines.delay(200)
                        if (requestStreamingPlayback(
                                        link, service, targetPackage, searchQuery, preferSearch)) {
                            return@launch
                        }
                    }
                    pressPlayAfterNavigating(targetPackage, identityBeforeOpen, hadTrackBeforeOpen)
                }
            }
            return
        }
        if (targetPackage != null && startStreamingLink(browserLink, null)) return

        Timber.e("No app handles %s streaming link", service.name)
    }

    /**
     * Last resort after a deep link has been opened: press play.
     *
     * The retry loop above re-issues `playFromUri`, which is the right command for a *track* and
     * the wrong one for a collection - SoundCloud honours it for a track and merely navigates for a
     * playlist or a personal collection like Likes, so every retry re-opened the page and nothing
     * ever started. The app had been taken to the content and then never asked to play it.
     *
     * Deliberately conditional, because a bare `play()` is a resume and could just as easily start
     * something the user never asked for. It is only issued when the session is safe to resume into:
     * either the app had no track at all before the link opened (nothing to hijack, and a resume is
     * exactly what was wanted), or its metadata has since *changed*, which is the app telling us it
     * loaded what we sent it. An app still sitting on the same track it had before is left alone -
     * there the deep link only navigated, and pressing play would resume the wrong thing.
     */
    private suspend fun pressPlayAfterNavigating(
            targetPackage: String,
            identityBeforeOpen: Triple<Long?, String?, String?>,
            hadTrackBeforeOpen: Boolean
    ) {
        for (attempt in 0 until PRESS_PLAY_ATTEMPTS) {
            val controller = mediaSessionProvider.controllerForPackage(targetPackage)
            if (controller == null) {
                kotlinx.coroutines.delay(PRESS_PLAY_INTERVAL_MS)
                continue
            }
            if (controller.isPlaying()) return

            val loadedSomethingNew = playbackIdentity(controller) != identityBeforeOpen
            if (!hadTrackBeforeOpen || loadedSomethingNew) {
                Timber.d("Deep link navigated without playing; pressing play on %s", targetPackage)
                try {
                    controller.transportControls.play()
                } catch (e: RuntimeException) {
                    Timber.w(e, "Play command rejected by %s", targetPackage)
                }
                scheduleStateRefresh()
                return
            }
            kotlinx.coroutines.delay(PRESS_PLAY_INTERVAL_MS)
        }
    }

    private fun requestStreamingPlayback(
            link: String,
            service: StreamingService,
            targetPackage: String,
            searchQuery: String? = null,
            preferSearch: Boolean = false
    ): Boolean {
        val info = StreamingShortcutLinks.inspect(link)
        if (info.service != service || !info.contentType.isPlayable) return false
        // Prefer the tracked session when it is already the target app, but fall back to any live
        // session that app has - it may be playing/paused without being the foreground one we
        // mirror, and we can still hand it a command (this is what lets Spotify respond while it
        // is running even though another app owns the current session).
        val controller = currentMediaController?.takeIf { it.packageName == targetPackage }
                ?: mediaSessionProvider.controllerForPackage(targetPackage)
                ?: return false
        val actions = controller.playbackState?.actions ?: 0L
        val advertisesPlayFromUri = actions and PlaybackState.ACTION_PLAY_FROM_URI != 0L
        val advertisesPlayFromMediaId =
                actions and PlaybackState.ACTION_PLAY_FROM_MEDIA_ID != 0L
        val playbackLink = StreamingShortcutLinks.forPlayback(link)

        return try {
            // Artists (and anything else where URI playback is not honored) play via a named
            // search - the one command Spotify and YouTube Music both accept from outside.
            if (preferSearch && searchQuery != null) {
                controller.transportControls.playFromSearch(searchQuery, Bundle.EMPTY)
                scheduleStateRefresh()
                return true
            }
            // For a precise entity (track/album/playlist) the URI is authoritative; a named search
            // could match a different item, so it is NOT used as a substitute here - only artists
            // (preferSearch, handled above) or the visible deep-link open below cover the rest.
            when {
                advertisesPlayFromUri -> controller.transportControls.playFromUri(
                        Uri.parse(playbackLink),
                        Bundle.EMPTY
                )
                advertisesPlayFromMediaId -> controller.transportControls.playFromMediaId(
                        playbackLink,
                        Bundle.EMPTY
                )
                // Some player sessions accept URI playback but fail to publish the capability.
                // Try it, then still execute the visible deep-link fallback below.
                else -> controller.transportControls.playFromUri(
                        Uri.parse(playbackLink),
                        Bundle.EMPTY
                )
            }
            scheduleStateRefresh()
            // Only playFromUri has an unambiguous contract for this input. Media-id and
            // unadvertised fallbacks are best effort, so still foreground the same deep link:
            // there is no acknowledgement channel and app-defined media ids may use another form.
            advertisesPlayFromUri
        } catch (error: RuntimeException) {
            Timber.w(error, "Player rejected %s playback command", service.name)
            false
        }
    }

    private fun startStreamingLinkChooser(link: String): Boolean {
        if (link.isBlank()) return false
        val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
        val chooser = Intent.createChooser(
                viewIntent,
                getString(R.string.streaming_shortcut_choose_app)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            startActivity(chooser)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private fun startStreamingLink(link: String, targetPackage: String?): Boolean {
        if (link.isBlank()) return false
        val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(link)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            setPackage(targetPackage)
        }
        return try {
            startActivity(viewIntent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean = try {
        packageManager.getPackageInfo(packageName, 0)
        true
    } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
        false
    }

    /**
     * Where the watch currently is in the browsable library, innermost last. Empty means the root.
     *
     * Process-local and deliberately not persisted: it is navigation state for a menu that is open
     * right now, in the same spirit as [MediaNotificationActions]' registry. If this service is
     * restarted mid-browse the watch simply reopens at the root, which is a far better failure than
     * restoring a path into a library that may have changed underneath it.
     */
    private val libraryPath = mutableListOf<String>()

    /**
     * Loads one library page and pushes it to the watch as a [CustomLists.LIBRARY] custom list.
     *
     * [parentId] null browses the root. The watch decides per row whether selecting it navigates or
     * plays, which is why every entry id goes through [LibraryEntry].
     */
    private suspend fun sendLibraryPageToWatch(parentId: String?) {
        // currentMediaController is already the *reported* session (resolveReportedSession keeps
        // the last live one across a pause), so there is no separate fallback to consult here.
        val packageName = currentMediaController?.packageName
        if (packageName == null) {
            sendLibraryErrorToWatch(getString(R.string.error_library_no_player))
            return
        }

        val page = MediaBrowserLibrary.browse(this, packageName, parentId)
        if (page == null) {
            // No MediaBrowserService, or it never answered. Both mean this app cannot be browsed,
            // which is a real and common case (several popular players expose no library at all).
            sendLibraryErrorToWatch(getString(R.string.error_library_unavailable))
            return
        }

        val entries = mutableListOf<CustomList.ListEntry>()
        if (libraryPath.isNotEmpty()) {
            entries += CustomList.ListEntry.newBuilder()
                    .setEntryId(LibraryEntry.UP)
                    .setEntryTitle(getString(R.string.library_up))
                    .build()
        }
        entries += page.children.take(LIBRARY_PAGE_LIMIT)
                .filter { it.mediaId != null }
                .map { item ->
                    CustomList.ListEntry.newBuilder()
                            .setEntryId(
                                    if (item.isBrowsable) LibraryEntry.browsable(item.mediaId!!)
                                    else LibraryEntry.playable(item.mediaId!!))
                            .setEntryTitle(item.description.title?.toString() ?: "")
                            .setEntrySubtitle(item.description.subtitle?.toString() ?: "")
                            .build()
                }

        if (entries.none { it.entryId != LibraryEntry.UP }) {
            entries += CustomList.ListEntry.newBuilder()
                    .setEntryId(CustomLists.SPECIAL_ITEM_ERROR)
                    .setEntryTitle(getString(R.string.error_library_empty))
                    .build()
        }

        transmitCustomList(CustomLists.LIBRARY, entries)
    }

    private suspend fun sendLibraryErrorToWatch(message: String) {
        transmitCustomList(CustomLists.LIBRARY, listOf(
                CustomList.ListEntry.newBuilder()
                        .setEntryId(CustomLists.SPECIAL_ITEM_ERROR)
                        .setEntryTitle(message)
                        .build()))
    }

    /** Opens the playing app's library at the root, resetting any previous walk. */
    suspend fun openLibraryOnWatch() {
        libraryPath.clear()
        sendLibraryPageToWatch(null)
    }

    /**
     * Handles a row picked from a library page: folders walk deeper (or back up), tracks play.
     *
     * Playback goes through [MediaBrowserPlayback] rather than `playFromMediaId` on the tracked
     * controller, for the reason that class documents - it is the only route that works when the
     * player has no live session yet, which is exactly the state a user browsing from the wrist is
     * often in.
     */
    private suspend fun onLibraryEntrySelected(entryId: String, startNewWalk: Boolean = false) {
        // A row picked out of search results starts its own walk: the previous path (if any) leads
        // somewhere unrelated, so "Back" from an artist reached by searching should return to the
        // library root, never to whatever folder was last browsed.
        if (startNewWalk) {
            libraryPath.clear()
        }
        if (!LibraryEntry.isBrowsable(entryId)) {
            val mediaId = LibraryEntry.mediaId(entryId) ?: return
            val packageName = currentMediaController?.packageName ?: return
            MediaBrowserPlayback.playMediaId(this, packageName, mediaId)
            return
        }

        if (entryId == LibraryEntry.UP) {
            libraryPath.removeLastOrNull()
        } else {
            LibraryEntry.mediaId(entryId)?.let { libraryPath += it }
        }
        sendLibraryPageToWatch(libraryPath.lastOrNull())
    }

    private suspend fun sendSearchResultsToWatch(results: List<android.support.v4.media.MediaBrowserCompat.MediaItem>) {
        // Encoded exactly like a library page, and handled by the same selection path. A search
        // result is frequently *browsable* rather than playable - an artist or an album row - and
        // sending those as playable was why picking an artist from a watch search did nothing:
        // playFromMediaId on a folder node has nothing to play. Marked browsable, the same row now
        // walks into that artist's albums instead.
        val entries = results.take(20)
                .filter { it.mediaId != null }
                .map { item ->
                    CustomList.ListEntry.newBuilder()
                            .setEntryId(
                                    if (item.isBrowsable) LibraryEntry.browsable(item.mediaId!!)
                                    else LibraryEntry.playable(item.mediaId!!))
                            .setEntryTitle(item.description.title?.toString() ?: "")
                            .setEntrySubtitle(item.description.subtitle?.toString() ?: "")
                            .build()
                }

        val listEntries = entries.ifEmpty {
            listOf(
                    CustomList.ListEntry.newBuilder()
                            .setEntryId(CustomLists.SPECIAL_ITEM_ERROR)
                            .setEntryTitle(getString(R.string.error_search_no_results))
                            .build()
            )
        }

        transmitCustomList(CustomLists.SEARCH_RESULTS, listEntries)
    }

    /**
     * Publishes one custom list to the watch.
     *
     * The timestamp is what makes the watch treat this as a *new* list worth opening the menu for
     * (see MainActivity.customListListener), so every page of a browse walk gets its own - an
     * unchanged timestamp would leave an open menu showing the previous level.
     */
    private suspend fun transmitCustomList(listId: String, entries: List<CustomList.ListEntry>) {
        val protoData = CustomList.newBuilder()
                .addAllActions(entries)
                .setListId(listId)
                .setListTimestamp(System.currentTimeMillis())
                .build()

        val putDataRequest = PutDataRequest.create(CommPaths.DATA_CUSTOM_LIST)
        putDataRequest.data = protoData.toByteArray()

        Wearable.getDataClient(this).putDataItem(putDataRequest).await()
    }

    override fun onMessageReceived(event: MessageEvent) {
        Timber.d("Message %s", event.path)

        when (event.path) {
            CommPaths.MESSAGE_WATCH_CLOSED -> {
                stopSelf()
            }
            CommPaths.MESSAGE_ACK -> {
                ackTimeoutHandler.removeMessages(MESSAGE_STOP_SELF)
            }
            CommPaths.MESSAGE_CHANGE_VOLUME -> {
                updateVolume(FloatPacker.unpackFloat(event.data))
            }
            CommPaths.MESSAGE_SEEK_TO -> {
                seekTo(ByteBuffer.wrap(event.data).long)
            }
            CommPaths.MESSAGE_SEEK_RELATIVE -> {
                seekRelative(ByteBuffer.wrap(event.data).long)
            }
            CommPaths.MESSAGE_TOGGLE_PLAY_PAUSE -> {
                togglePlayPause()
            }
            CommPaths.MESSAGE_SKIP_NEXT -> {
                currentMediaController?.transportControls?.skipToNext()
            }
            CommPaths.MESSAGE_SKIP_PREVIOUS -> {
                currentMediaController?.transportControls?.skipToPrevious()
            }
            CommPaths.MESSAGE_QUICK_ACTION -> {
                executeQuickAction(String(event.data, Charsets.UTF_8))
            }
            CommPaths.MESSAGE_EXECUTE_ACTION -> {
                executeAction(ButtonInfo(WatchActions.ProtoButtonInfo.parseFrom(event.data)))
            }
            CommPaths.MESSAGE_EXECUTE_MENU_ACTION -> {
                executeMenuAction(ByteBuffer.wrap(event.data).int)
            }
            CommPaths.MESSAGE_WATCH_OPENED -> {

                ackTimeoutHandler.removeMessages(MESSAGE_STOP_SELF)
                buildMusicStateAndTransmit(currentMediaController)
            }
            CommPaths.MESSAGE_WATCH_CLOSED_MANUALLY -> {
                onWatchSwipeExited()
            }
            CommPaths.MESSAGE_CUSTOM_LIST_ITEM_SELECTED -> {
                onCustomMenuItemPresed(CustomListItemAction.parseFrom(event.data))
            }
            CommPaths.MESSAGE_OPEN_PLAYBACK_QUEUE -> {
                // Payload is the number of entries the watch wants, added for "load more". Older
                // watch builds send none at all, which reads back as the default first page.
                val requested = event.data
                        ?.takeIf { it.size >= 4 }
                        ?.let { ByteBuffer.wrap(it).int }
                        ?: DEFAULT_QUEUE_PAGE_SIZE
                openPlaybackQueueOnWatch(requested)
            }
            CommPaths.MESSAGE_PLAY_FROM_SEARCH -> {
                playFromSearch(String(event.data, Charsets.UTF_8))
            }
            CommPaths.MESSAGE_SET_SCREEN_FACE -> {
                applyScreenFaceFromWatch(String(event.data, Charsets.UTF_8))
            }
            CommPaths.MESSAGE_DELETE_CUSTOM_LIST_ITEM -> {
                onCustomMenuItemDeleted(CustomListItemAction.parseFrom(event.data))
            }
            CommPaths.MESSAGE_REQUEST_LYRICS -> {
                sendLyricsToWatch(LyricsRequest.parseFrom(event.data))
            }
            CommPaths.MESSAGE_REQUEST_PLAYBACK_SYNC -> {
                sendPlaybackSyncToWatch(ByteBuffer.wrap(event.data).long)
            }
            CommPaths.MESSAGE_REQUEST_TRACK_METADATA -> {
                sendTrackMetadataToWatch(TrackMetadata.parseFrom(event.data))
            }
        }
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

        val errorChannel = NotificationChannel(KEY_NOTIFICATION_CHANNEL_ERRORS,
                getString(R.string.error_notifications),
                NotificationManager.IMPORTANCE_DEFAULT)
        notificationManager.createNotificationChannel(errorChannel)
    }

    private fun startTimeout() {
        ackTimeoutHandler.removeMessages(MESSAGE_STOP_SELF)
    }

    private class AckTimeoutHandler(val service: WeakReference<MusicService>) : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what == MESSAGE_STOP_SELF) {
                Timber.d("TIMEOUT!")
                service.get()?.stopSelf()
            }
        }
    }

    /** ByteArray equality by content - the source-icon PNGs are fresh arrays each rasterization. */
    private fun ByteArray?.contentEqualsNullable(other: ByteArray?): Boolean = when {
        this === other -> true
        this == null || other == null -> false
        else -> contentEquals(other)
    }

    private fun MusicState.equalsIgnoringTime(other: MusicState?): Boolean {
        if (other == null ||
                other.playing != playing ||
                other.artist != artist ||
                other.title != title ||
                other.volume != volume ||
                other.error != error ||
                other.durationMs != durationMs ||
                other.seekable != seekable ||
                // Without these three the watch's quick-actions panel state rings stayed frozen:
                // a state change that only flipped shuffle/repeat/like looked "equal" here and was
                // never retransmitted (LikeAction.scheduleStateRefresh hit the same dedupe).
                other.shuffleEnabled != shuffleEnabled ||
                other.repeatMode != repeatMode ||
                other.liked != liked
                || other.mediaActionsList != mediaActionsList
        ) {
            return false
        }

        // positionMs naturally drifts forward every time playback is polled, so comparing it
        // directly would defeat the point of this check. Instead, extrapolate where playback
        // "should" be based on the previous state and only treat a bigger-than-expected jump
        // (a real seek, or a track restart) as a change worth re-transmitting for.
        val elapsedSincePrevious = positionUpdateTime - other.positionUpdateTime
        val expectedPosition = if (other.playing) {
            other.positionMs + (elapsedSincePrevious * other.playbackSpeed).toLong()
        } else {
            other.positionMs
        }

        return Math.abs(positionMs - expectedPosition) <= SEEK_DETECTION_THRESHOLD_MS
    }

    private val volumeContentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            val newVolume = currentMediaController?.playbackInfo?.currentVolume
            if (newVolume != currentVolume) {
                buildMusicStateAndTransmit(currentMediaController)
            }
        }
    }
}
