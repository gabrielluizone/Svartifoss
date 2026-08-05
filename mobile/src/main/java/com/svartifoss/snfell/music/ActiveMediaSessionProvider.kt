package com.svartifoss.snfell.music

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import com.svartifoss.snfell.NotificationService
import com.svartifoss.snfell.R
import com.matejdro.wearutils.lifecycle.Resource
import timber.log.Timber
import javax.inject.Inject

class ActiveMediaSessionProvider @Inject constructor(private val context: Context) :
        androidx.lifecycle.LiveData<Resource<MediaController>>(),
        MediaSessionManager.OnActiveSessionsChangedListener {

    private val notificationListenerComponent: ComponentName =
            ComponentName(context, NotificationService::class.java)

    private val mediaSessionManager: MediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    /** The session that is actually *playing*, or null when nothing is. */
    var currentController : MediaController? = null

    /** The session last published to observers, which unlike [currentController] survives a pause
     *  so the watch keeps showing the paused track - see [findPlayingMediaController]. */
    private var lastReportedController: MediaController? = null

    private val idlePlayers: ArrayList<OwnedPlaybackCallback>

    private fun findPlayingMediaController() {
        val activeSessions = getActiveSessions()
        Timber.d("Active Sessions %s", activeSessions.map { "${it.packageName} ${it.playbackState} ${it.playbackInfo}" })

        val newController = activeSessions.firstOrNull { it.isPlaying() }

        // A *paused* player must keep being reported - the watch distinguishes "paused track"
        // (title + "Playback Stopped") from "nothing playing at all" (the idle screen) purely by
        // whether a controller with a title arrives.
        //
        // This deliberately falls back to [lastReportedController] rather than [currentController]:
        // the latter is set to null a few lines below whenever nothing is playing, so on the
        // *second* pass after a pause (any onAudioInfoChanged from an idle player, a session-list
        // change, or a re-activate) there was nothing left to fall back to and the watch dropped to
        // the idle screen mid-pause. Retained only while that session is still live, so a player
        // that actually goes away still ends at null and the idle screen is correct.
        val reportedController = resolveReportedSession(
                playing = newController,
                lastReported = lastReportedController,
                stillActive = { previous ->
                    activeSessions.any { it.sessionToken == previous.sessionToken }
                })

        removeCurrentController()
        currentController = newController
        lastReportedController = reportedController

        idlePlayers.forEach(OwnedPlaybackCallback::unregister)
        idlePlayers.clear()

        if (currentController == null) {
            activeSessions.forEach {
                idlePlayers.add(OwnedPlaybackCallback((it)))
            }
        } else {
            currentController?.registerCallback(mediaCallback)
        }

        Timber.d("Reported session %s", activeSessions.map { "${reportedController?.packageName} ${reportedController?.playbackState} ${reportedController?.playbackInfo}" })
        setReportedController(reportedController)
    }

    private fun getActiveSessions(): List<MediaController> {
        return try {
            mediaSessionManager.getActiveSessions(notificationListenerComponent)
        } catch (e: SecurityException) {
            value = Resource.error(context.getString(R.string.error_notification_access), null)
            emptyList()
        }
    }

    /**
     * Any currently-active MediaController for [packageName], even when it is not the tracked
     * "current" session. Lets a streaming shortcut reach an app (e.g. Spotify) that has a live
     * session while a different app is the foreground one, instead of only ever driving
     * [currentController].
     */
    fun controllerForPackage(packageName: String): MediaController? =
            getActiveSessions().firstOrNull { it.packageName == packageName }

    /**
     * The first non-empty queue published by any *other* live session belonging to [packageName].
     *
     * Several players run more than one `MediaSession` in the same process - Retro Music, for
     * instance, ships a separate `WearBrowserService` session alongside its playback one - and
     * only one of them carries the queue. The tracked controller is chosen by which session is
     * actually playing, which is the right rule for transport control but can land on the sibling
     * that has no queue at all, leaving the watch to fall back to play history as though the app
     * exposed nothing. Returns null when no sibling has one, which is the honest common case.
     */
    fun siblingQueueForPackage(
            packageName: String,
            excluding: MediaController?
    ): List<android.media.session.MediaSession.QueueItem>? =
            getActiveSessions()
                    .asSequence()
                    .filter { it.packageName == packageName }
                    .filter { it.sessionToken != excluding?.sessionToken }
                    .mapNotNull { it.queue?.takeIf { queue -> queue.isNotEmpty() } }
                    .firstOrNull()

    fun activate() {
        try {
            mediaSessionManager.addOnActiveSessionsChangedListener(this, notificationListenerComponent)
        } catch (e: SecurityException) {
            value = Resource.error(context.getString(R.string.error_notification_access), null)
        }

        updateControllerIfNeeded()
    }

    override fun onActiveSessionsChanged(controllers: MutableList<MediaController>?) {
        Timber.d("ActiveSessions changed %s", controllers?.map { it.packageName + " " + it.isPlaying() })
        updateControllerIfNeeded()
    }

    override fun onActive() {
        activate()
    }

    override fun onInactive() {
        mediaSessionManager.removeOnActiveSessionsChangedListener(this)

        // Dropped with the rest of the state: a controller retained across a pause must not
        // outlive the observer that would have shown it.
        lastReportedController = null
        removeCurrentController()
        idlePlayers.forEach(OwnedPlaybackCallback::unregister)
        idlePlayers.clear()
    }

    fun updateControllerIfNeeded() {
        if (!isCurrentControllerActive() || currentController?.isPlaying() != true) {
            findPlayingMediaController()
        }
    }

    private fun isCurrentControllerActive(): Boolean {
        val currentController = currentController ?: return false

        return getActiveSessions().any { it.packageName == currentController.packageName }
    }

    private fun removeCurrentController() {
        currentController?.unregisterCallback(mediaCallback)
        currentController = null
    }

    private val mediaCallback: MediaController.Callback

    inner class OwnedPlaybackCallback(private val controller: MediaController) : MediaController.Callback() {
        init {
            controller.registerCallback(this)
        }

        fun unregister() {
            controller.unregisterCallback(this)
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            if (state?.isPlaying() == true) {
                updateControllerIfNeeded()
            }
        }

        override fun onAudioInfoChanged(info: MediaController.PlaybackInfo) {
            updateControllerIfNeeded()
        }
    }

    private fun setReportedController(mediaController: MediaController?) {
        value = if (mediaController == null) {
            null
        } else {
            Resource.success(mediaController)
        }
    }

    init {
        this.idlePlayers = ArrayList()
        this.mediaCallback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                updateControllerIfNeeded()
            }

            override fun onMetadataChanged(metadata: android.media.MediaMetadata?) {
                setReportedController(currentController)
            }
        }
    }
}

fun PlaybackState.isPlaying() : Boolean {
    val state = this.state
    return state != PlaybackState.STATE_NONE &&
            state != PlaybackState.STATE_PAUSED &&
            state != PlaybackState.STATE_STOPPED &&
            state != PlaybackState.STATE_ERROR
}


fun MediaController.isPlaying() : Boolean {
    return this.playbackState?.isPlaying() == true
}

/**
 * Which media session should be published to observers.
 *
 * [playing] is whichever session is actually playing, or null when none is. [lastReported] is the
 * one published previously, and [stillActive] answers whether that session still exists.
 *
 * The rule is "the playing one, else keep showing the last one we had, as long as it is still
 * alive". Pausing must *not* clear what the watch shows - it distinguishes a paused track (title
 * plus "Playback Stopped") from nothing playing at all (the idle screen) purely by whether a
 * session arrives - while a player that genuinely goes away must resolve to null so the idle
 * screen is still reachable.
 *
 * Extracted as a pure function because the bug it fixes is invisible in the happy path: the first
 * pass after a pause looked correct, and only a *second* pass (an audio-info change from an idle
 * player, a session-list change, a re-activate) dropped to the idle screen, because the fallback
 * used to read a field that had already been nulled.
 */
internal fun <T : Any> resolveReportedSession(
        playing: T?,
        lastReported: T?,
        stillActive: (T) -> Boolean
): T? = playing ?: lastReported?.takeIf(stillActive)
