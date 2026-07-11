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
import com.svartifoss.snfell.R
import com.svartifoss.snfell.actions.ActionHandler
import com.svartifoss.snfell.actions.OpenPlaylistAction
import com.svartifoss.snfell.actions.PhoneAction
import com.svartifoss.snfell.actions.playback.LikeAction
import com.svartifoss.snfell.actions.playback.RepeatAction
import com.svartifoss.snfell.actions.playback.ShuffleAction
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.common.CustomLists
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.buttonconfig.ButtonInfo
import com.svartifoss.snfell.common.util.FloatPacker
import com.svartifoss.snfell.config.ActionConfig
import com.svartifoss.snfell.config.WatchInfoProvider
import com.svartifoss.snfell.config.WatchInfoWithIcons
import com.svartifoss.snfell.di.GlobalConfig
import com.svartifoss.snfell.di.MusicServiceSubComponent
import com.svartifoss.snfell.notifications.NotificationProvider
import com.svartifoss.snfell.proto.CustomList
import com.svartifoss.snfell.proto.CustomListItemAction
import com.svartifoss.snfell.proto.MusicState
import com.svartifoss.snfell.proto.WatchActions
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
import javax.inject.Inject
import com.svartifoss.snfell.common.R as commonR

data class TrackHistoryEntry(val artist: String, val title: String)

private const val YOUTUBE_MUSIC_PACKAGE = "com.google.android.apps.youtube.music"

class MusicService : LifecycleService(), MessageClient.OnMessageReceivedListener {
    companion object {
        const val ACTION_START_FROM_WATCH = "START_FROM_WATCH"
        const val ACTION_NOTIFICATION_SERVICE_ACTIVATED = "NOTIFICATION_SERVICE_ACTIVATED"

        private const val MESSAGE_STOP_SELF = 0
        private val ACK_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(3)
        private const val SEEK_DETECTION_THRESHOLD_MS = 1500L
        private const val QUEUE_REFRESH_DEBOUNCE_MS = 600L
        private const val MAX_TRACK_HISTORY_SIZE = 20

        private const val STOP_SELF_PENDING_INTENT_REQUEST_CODE = 333
        private const val ACTION_STOP_SELF = "STOP_SELF"
        private const val KEY_NOTIFICATION_CHANNEL = "Service_Channel"
        private const val KEY_NOTIFICATION_CHANNEL_ERRORS = "Error notifications"

        private const val NOTIFICATION_ID_PERSISTENT = 1
        private const val NOTIFICATION_ID_SERVICE_ERROR = 2

        var active = false
            private set
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

    private var previousMusicState: MusicState? = null
    private var previousAlbumArt: Bitmap? = null
    var currentMediaController: MediaController? = null
    private var startedFromWatch = false

    // Reference-keyed cache of the last art serialized for the watch. State-only changes
    // (volume, seek, play/pause) reuse the bytes instead of re-encoding the same cover on
    // every transmit.
    private var lastSerializedArtSource: Bitmap? = null
    private var lastSerializedArt: ByteArray? = null

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

        preferences = PreferenceManager.getDefaultSharedPreferences(this)

        messageClient.addListener(this, Uri.parse(CommPaths.MESSAGES_PREFIX), MessageClient.FILTER_PREFIX)

        mediaSessionProvider = ActiveMediaSessionProvider(this)
        mediaSessionProvider.observe(this, mediaCallback)

        watchInfoProvider.observe(this, EmptyObserver<WatchInfoWithIcons>())

        if (Preferences.getBoolean(preferences, MiscPreferences.ENABLE_NOTIFICATION_POPUP)) {
            notificationProvider.observe(this, notificationCallback)
        }

        val stopSelfIntent = Intent(this, MusicService::class.java)
        stopSelfIntent.action = ACTION_STOP_SELF

        val stopSelfPendingIntent = PendingIntent.getService(this,
                STOP_SELF_PENDING_INTENT_REQUEST_CODE,
                stopSelfIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        createNotificationChannel()
        val notificationBuilder = NotificationCompat.Builder(this, KEY_NOTIFICATION_CHANNEL)
                .setContentTitle(getString(commonR.string.music_control_active))
                .setContentText(getString(R.string.tap_to_force_stop))
                .setContentIntent(stopSelfPendingIntent)
                // ic_notification_brand, not ic_app_brand: same logo, but re-padded to the
                // standard notification-glyph fill - the raw brand asset has so much built-in
                // padding it rendered visibly smaller than other apps' status icons.
                .setSmallIcon(R.drawable.ic_notification_brand)

        contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, volumeContentObserver)


        // This is still needed for Pre-O versions, so it must be used, even if it is deprecated.
        @Suppress("DEPRECATION")
        notificationBuilder.priority = Notification.PRIORITY_MIN

        // ServiceCompat passes the FGS type on API 29+ (required on API 34+) and is a no-op arg
        // on older versions, so this stays correct across the minSdk 23.. range.
        ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID_PERSISTENT,
                notificationBuilder.build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )

        active = true
        Timber.d("Service started")
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == ACTION_START_FROM_WATCH) {
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

        messageClient.removeListener(this)

        ackTimeoutHandler.removeCallbacksAndMessages(null)
        queueRefreshHandler.removeCallbacksAndMessages(null)
        contentResolver.unregisterContentObserver(volumeContentObserver)

        active = false

        super.onDestroy()
    }

    private val mediaCallback = Observer<Resource<MediaController>?> {
        when {
            it == null -> {
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
        currentMediaController?.let {
            it.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
            it.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        }
    }

    /** Drives the watch's quick-actions panel - these always work, regardless of button config. */
    private fun executeQuickAction(name: String) {
        val action: PhoneAction = when (name) {
            "like" -> LikeAction(this)
            "shuffle" -> ShuffleAction(this)
            "repeat" -> RepeatAction(this)
            else -> return
        }

        executeAction(action)
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
        if (!albumArtChanged && musicState.equalsIgnoringTime(previousMusicState)) {
            return
        }

        Timber.d("TransmittingToWear %s", musicState)
        val trackChanged = previousMusicState?.title != musicState.title ||
                previousMusicState?.artist != musicState.artist
        previousMusicState = musicState
        previousAlbumArt = albumArt
        transmitToWear(musicState, albumArt)

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

    private fun transmitToWear(musicState: MusicState, originalAlbumArt: Bitmap?) {
        val mySequence = ++transmitSequence

        lifecycleScope.launchWithPlayServicesErrorHandling(this) {
            val stateBytes = musicState.toByteArray()

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
                stateOnlyRequest.data = musicState.toBuilder().setAlbumArtPending(true).build().toByteArray()
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
                        albumArt = BitmapUtils.resizeAndCrop(albumArt,
                                watchInfo.displayWidth,
                                watchInfo.displayHeight,
                                true)
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
            putDataRequest.data = stateBytes
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

        putDataRequest.data = musicState.toByteArray()
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
                playDeepLink(customListItemAction.entryId)
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

    /**
     * Opens a music deep link (e.g. a music.youtube.com playlist) on the phone. YouTube links
     * are targeted straight at the YouTube Music app when it's installed, so they start playing
     * there instead of opening a browser/chooser.
     */
    fun playDeepLink(link: String) {
        val uri = normalizeYoutubeMusicLink(Uri.parse(link))
        val targetPackage = if (uri.host?.contains("youtube") == true &&
                isPackageInstalled(YOUTUBE_MUSIC_PACKAGE)) {
            YOUTUBE_MUSIC_PACKAGE
        } else {
            null
        }

        val viewIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            setPackage(targetPackage)
        }

        try {
            startActivity(viewIntent)
        } catch (e: ActivityNotFoundException) {
            if (targetPackage == null) {
                Timber.e("No app handles music link %s", link)
                return
            }
            viewIntent.setPackage(null)
            try {
                startActivity(viewIntent)
            } catch (e2: ActivityNotFoundException) {
                Timber.e("No app handles music link %s", link)
            }
        }
    }

    /**
     * A YT Music playlist share link is `.../playlist?list=PLxxx` - that path only opens the
     * playlist's page, it does not start playback. `.../watch?list=PLxxx` is the endpoint that
     * actually starts playing the playlist immediately (the same one "Liked Music" already used).
     * Any other link (a plain video/watch link, a non-YouTube link, ...) passes through untouched.
     */
    private fun normalizeYoutubeMusicLink(uri: Uri): Uri {
        if (uri.host?.contains("youtube") != true || uri.path != "/playlist") {
            return uri
        }
        return uri.buildUpon().path("/watch").build()
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
