package com.svartifoss.snfell.music

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.view.KeyEvent
import com.svartifoss.snfell.NotificationService
import com.svartifoss.snfell.actions.PhoneAction
import com.svartifoss.snfell.actions.playback.PauseAction
import com.svartifoss.snfell.actions.playback.PlayAction
import com.svartifoss.snfell.actions.playback.PlayPauseToggleAction
import com.svartifoss.snfell.actions.playback.SkipToNextAction
import com.svartifoss.snfell.actions.playback.SkipToPrevAction
import timber.log.Timber

/**
 * The transport decisions [MusicService] and [DirectTransportControl] must make identically, so a
 * press does the same thing whichever of the two happens to carry it out.
 */
internal object TransportCommands {
    /**
     * Calls the session's transport controls directly rather than dispatching a PLAY_PAUSE key: a
     * key event goes through the media-button dispatcher and the session's own handler before it
     * reaches onPlay/onPause, an extra hop that made the watch button feel slow beside the system
     * media controls. The state is read here, so the toggle is resolved here too.
     */
    fun togglePlayPause(controller: MediaController) {
        if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) {
            controller.transportControls.pause()
        } else {
            controller.transportControls.play()
        }
    }

    /** [fraction] (0..1) of the session's maximum, as the absolute level `setVolumeTo` takes. */
    fun absoluteVolume(controller: MediaController, fraction: Float): Int {
        val maxVolume = controller.playbackInfo?.maxVolume ?: 0
        return (maxVolume * fraction).toInt()
    }
}

/**
 * Carries out a [DirectTransportCommand] against the phone's media sessions with no
 * [MusicService] running.
 *
 * What makes that possible is that `MediaSessionManager.getActiveSessions` asks for notification
 * *access*, not for the listener to be connected: the system checks that the component is an
 * enabled listener, which is a setting. The listener service being bound is not part of it, and by
 * default it is not - [NotificationService] unbinds itself unless auto-start or session-driven
 * quick actions need it. So this works on a phone that has been idle for hours with nothing of the
 * app running except the process that received the message.
 */
internal class DirectTransportControl(context: Context) {
    enum class Outcome {
        /** The command was issued (or there was nothing to aim it at, which is the same answer). */
        DONE,

        /** Something threw part-way, so whether the player heard it is unknown. */
        FAILED,

        /** Notification access is missing, so no session can be seen: the command was not tried. */
        UNAVAILABLE
    }

    private val context = context.applicationContext
    private val listener = ComponentName(this.context, NotificationService::class.java)

    /** Whether any session is playing, or null when notification access is missing. */
    fun isAnythingPlaying(): Boolean? = sessions()?.any { it.isPlaying() }

    /**
     * The package of the session a transport command would be aimed at - see
     * [DirectTransportTarget.pick] - or null with no session or no notification access. What the
     * phone's output switcher is asked to switch, so it moves the app that is playing.
     */
    fun targetPackage(): String? = try {
        sessions()?.let { DirectTransportTarget.pick(it) { session -> session.isPlaying() } }?.packageName
    } catch (e: RuntimeException) {
        null
    }

    fun execute(command: DirectTransportCommand): Outcome {
        val sessions = sessions() ?: return Outcome.UNAVAILABLE
        return try {
            run(command, DirectTransportTarget.pick(sessions) { it.isPlaying() })
            Outcome.DONE
        } catch (e: SecurityException) {
            Outcome.UNAVAILABLE
        } catch (e: RuntimeException) {
            Timber.w(e, "Direct transport command %s failed", command)
            Outcome.FAILED
        }
    }

    private fun run(command: DirectTransportCommand, target: MediaController?) {
        when (command) {
            DirectTransportCommand.TogglePlayPause ->
                if (target != null) TransportCommands.togglePlayPause(target) else pressSystemKey(KeyEvent.KEYCODE_MEDIA_PLAY)
            is DirectTransportCommand.MediaKey -> {
                if (target != null) {
                    target.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_DOWN, command.keyCode))
                    target.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_UP, command.keyCode))
                } else if (command.fallbackToSystem) {
                    pressSystemKey(command.keyCode)
                }
            }
            DirectTransportCommand.SkipNext -> target?.transportControls?.skipToNext()
            DirectTransportCommand.SkipPrevious -> target?.transportControls?.skipToPrevious()
            is DirectTransportCommand.AdjustVolume -> target?.adjustVolume(command.direction, 0)
            is DirectTransportCommand.SetVolume ->
                target?.setVolumeTo(TransportCommands.absoluteVolume(target, command.fraction), 0)
        }
    }

    /** Hands a media key to the audio framework, which routes it to the most recent media app. */
    private fun pressSystemKey(keyCode: Int) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    private fun sessions(): List<MediaController>? {
        val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
                ?: return null
        return try {
            manager.getActiveSessions(listener)
        } catch (e: SecurityException) {
            null
        }
    }

    companion object {
        /**
         * What a button's assigned action asks of the player, if it is one of the transport ones.
         *
         * A watch button reaches the phone as `MESSAGE_EXECUTE_ACTION` carrying only *which button*,
         * so this is the other half of recognising a press: the phone's own configuration says what
         * it means. These are the actions whose handlers do nothing but talk to the session, with
         * the same keys they send. Everything else (the queue, search, shortcuts, Tasker) needs the
         * service and returns null.
         */
        fun fromAction(action: PhoneAction): DirectTransportCommand? = when (action) {
            is PlayPauseToggleAction ->
                DirectTransportCommand.MediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, fallbackToSystem = true)
            is PlayAction ->
                DirectTransportCommand.MediaKey(KeyEvent.KEYCODE_MEDIA_PLAY, fallbackToSystem = true)
            is PauseAction ->
                DirectTransportCommand.MediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE, fallbackToSystem = false)
            is SkipToNextAction -> DirectTransportCommand.SkipNext
            is SkipToPrevAction -> DirectTransportCommand.SkipPrevious
            else -> null
        }
    }
}
