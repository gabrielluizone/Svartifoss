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
import com.svartifoss.snfell.actions.OpenPlaylistAction
import com.svartifoss.snfell.actions.PhoneAction
import com.svartifoss.snfell.actions.playback.LikeAction
import com.svartifoss.snfell.actions.playback.RepeatAction
import com.svartifoss.snfell.actions.playback.ShuffleAction
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.common.CustomLists
import com.svartifoss.snfell.common.FaceScopedPreferences
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.PlayerBackgroundStyle
import com.svartifoss.snfell.common.ThemeAppearance
import com.svartifoss.snfell.common.buttonconfig.ButtonInfo
import com.svartifoss.snfell.common.util.FloatPacker
import com.svartifoss.snfell.config.ActionConfig
import com.svartifoss.snfell.config.WatchInfoProvider
import com.svartifoss.snfell.config.WatchInfoWithIcons
import com.svartifoss.snfell.di.GlobalConfig
import com.svartifoss.snfell.di.MusicServiceSubComponent
import com.svartifoss.snfell.notifications.MediaNotificationActions
import com.svartifoss.snfell.notifications.NotificationProvider
import com.svartifoss.snfell.notifications.customActionSnapshotId
import com.svartifoss.snfell.notifications.inferMediaActionSemantic
import com.svartifoss.snfell.notifications.isCustomActionSnapshotId
import com.svartifoss.snfell.proto.CustomList
import com.svartifoss.snfell.proto.CustomListItemAction
import com.svartifoss.snfell.proto.MediaAction
import com.svartifoss.snfell.proto.MusicState
import com.svartifoss.snfell.proto.WatchActions
import com.google.protobuf.ByteString
import com.svartifoss.snfell.update.UpdateChecker
import com.svartifoss.snfell.util.launchWithPlayServicesErrorHandling
import com.matejdro.wearutils.lifecycle.EmptyObserver
import com.matejdro.wearutils.lifecycle.Resource
import com.matejdro.wearutils.miscutils.BitmapUtils
import com.matejdro.wearutils.preferences.definition.Preferences
import com.matejdro.wearvibrationcenter.notificationprovider.ReceivedNotification
import dagger.android.AndroidInjection
import kotlinx.coroutines.Dispatchers
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
        private const val MAX_TRACK_HISTORY_SIZE = 20
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
            return
        }

        active = true
        Timber.d("Service started")
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == ACTION_FORCE_STOP) {
            // "Force stop" from the notification: actually end the whole app, not just this
            // service. Unbind the notification listener first so the system doesn't restart
            // the process for it right away (stays unbound until reboot or a listener-access
            // toggle), then die for real once stopSelf has been processed.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                startService(Intent(this, NotificationService::class.java)
                        .setAction(NotificationService.ACTION_UNBIND_SERVICE))
            }
            stopSelf()
            Handler(Looper.getMainLooper()).postDelayed({
                android.os.Process.killProcess(android.os.Process.myPid())
            }, 300)
            return Service.START_NOT_STICKY
        } else if (action == ACTION_START_FROM_WATCH) {
            startedFromWatch = true
        } else if (action == ACTION_STOP_SELF || !startedFromWatch) {
            stopSelf()
            return Service.START_NOT_STICKY
        } else if (action == ACTION_NOTIFICATION_SERVICE_ACTIVATED) {
            mediaSessionProvider.activate()
            NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID_SERVICE_ERROR)
        }

        super.onStartCommand(intent, flags, startId)
        return Service.START_STICKY
    }

    override fun onDestroy() {
        Timber.d("Service stopped")

        try {
            messageClient.removeListener(this)
        } catch (e: Exception) {
            Timber.w(e, "Failed to remove Wearable message client listener")
        }

        ackTimeoutHandler.removeCallbacksAndMessages(null)
        queueRefreshHandler.removeCallbacksAndMessages(null)
        MediaNotificationActions.removeListener(notificationActionsChanged)
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

                albumArt = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
                    ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
                    // Many apps on Android 10+ provide art as a content:// URI instead of a raw
                    // Bitmap to reduce memory pressure. The system notification resolver handles
                    // these automatically; we need an explicit fallback to match.
                    ?: loadBitmapFromUriCached(
                        meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                            ?: meta.getString(MediaMetadata.METADATA_KEY_ART_URI)
                            ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
                    )

                val duration = meta.getLong(MediaMetadata.METADATA_KEY_DURATION)
                if (duration > 0) {
                    musicStateBuilder.durationMs = duration
                }
            }

            if (playbackState != null) {
                musicStateBuilder.positionMs = playbackState.position

                // PlaybackState.lastPositionUpdateTime is in SystemClock.elapsedRealtime() time,
                // not wall-clock time, and the watch has no way to relate its own elapsedRealtime
                // (different device, different boot time) to ours. Convert it to an epoch
                // timestamp here so the watch can extrapolate using its own currentTimeMillis().
                val elapsedRealtimeNow = android.os.SystemClock.elapsedRealtime()
                musicStateBuilder.positionUpdateTime =
                        System.currentTimeMillis() - (elapsedRealtimeNow - playbackState.lastPositionUpdateTime)

                musicStateBuilder.playbackSpeed = playbackState.playbackSpeed
                musicStateBuilder.seekable = (playbackState.actions and PlaybackState.ACTION_SEEK_TO) != 0L
                musicStateBuilder.liked = LikeAction.isCurrentlyLiked(playbackState)
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
            executeAction(OpenPlaylistAction(this))
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
        val size = 48
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

        val putDataRequest = PutDataRequest.create(CommPaths.DATA_MUSIC_STATE)

        putDataRequest.data = musicState.toBuilder().setSeq(nextMusicSeq()).build().toByteArray()
        putDataRequest.setUrgent()

        dataClient.putDataItem(putDataRequest).await()
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
            CustomLists.PLAYLIST -> {
                currentMediaController?.transportControls?.skipToQueueItem(
                        customListItemAction.entryId.toLong()
                )
            }
            CustomLists.SEARCH_RESULTS -> {
                currentMediaController?.transportControls?.playFromMediaId(
                        customListItemAction.entryId, null
                )
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
        }
    }

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

    private fun openPlaybackQueueOnWatch() {
        executeAction(OpenPlaylistAction(this))
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
                    controller.transportControls.playFromSearch(query, null)
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
    fun playDeepLink(link: String, searchQuery: String? = null) {
        if (!StreamingShortcutLinks.isSafeLink(link)) {
            Timber.e("Refusing unsafe or invalid streaming link")
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
                startStreamingLinkChooser(browserLink)) return

        // ACTION_VIEW only navigates to Spotify content; it does not promise playback. If that
        // app already has a MediaSession, use the Android media contract first (playFromUri, or
        // playFromSearch for artists) so tracks, albums, playlists and artists actually start.
        if (targetPackage != null &&
                requestStreamingPlayback(link, service, targetPackage, query, preferSearch)) return

        if (targetPackage != null && (contentType.isPlayable || preferSearch)) {
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
                    scheduleStateRefresh()
                } else {
                    startStreamingLinkWithPlaybackNudge(
                            link, service, targetPackage, browserLink, query, preferSearch)
                }
            }
            return
        }

        startStreamingLinkWithPlaybackNudge(
                link, service, targetPackage, browserLink, query, preferSearch)
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
                }
            }
            return
        }
        if (targetPackage != null && startStreamingLink(browserLink, null)) return

        Timber.e("No app handles %s streaming link", service.name)
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

    private suspend fun sendSearchResultsToWatch(results: List<android.support.v4.media.MediaBrowserCompat.MediaItem>) {
        val entries = results.take(20)
                .filter { it.mediaId != null }
                .map { item ->
                    CustomList.ListEntry.newBuilder()
                            .setEntryId(item.mediaId!!)
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

        val protoData = CustomList.newBuilder()
                .addAllActions(listEntries)
                .setListId(CustomLists.SEARCH_RESULTS)
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
                openPlaybackQueueOnWatch()
            }
            CommPaths.MESSAGE_PLAY_FROM_SEARCH -> {
                playFromSearch(String(event.data, Charsets.UTF_8))
            }
            CommPaths.MESSAGE_DELETE_CUSTOM_LIST_ITEM -> {
                onCustomMenuItemDeleted(CustomListItemAction.parseFrom(event.data))
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
