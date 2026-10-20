package com.svartifoss.snfell.music

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.svartifoss.snfell.common.SleepTimerPolicy
import timber.log.Timber

/**
 * The phone's sleep timer: counts down, then calls [onEnded] so the music can be paused.
 *
 * It lives on the phone, in [MusicService], for the reason `CommPaths.MESSAGE_SET_SLEEP_TIMER`
 * gives: the phone is what pauses the music, and it is the device that is certain to be running
 * when someone has fallen asleep. It is deliberately **in memory only** - a timer that outlived
 * the service that could act on it would fire into nothing, and one restored after a restart would
 * pause music the person started after waking up.
 *
 * Two kinds: a countdown of some minutes, and [SleepTimerPolicy.END_OF_TRACK] - pause when the
 * track playing now ends. The second has no deadline of its own; it is told about the session by
 * [observe] and decides with [EndOfTrackSleep] when the end is.
 *
 * Main-thread only, like every other handler in the service.
 *
 * The remaining time is read from the monotonic clock, so a change to the wall clock (a time-zone
 * hop, an NTP correction) cannot lengthen or shorten a timer; the callback itself rides a
 * [Handler], which counts uptime rather than elapsed time and so can only run *late* if the phone
 * slept - playing music keeps it awake, which is the only case that matters.
 */
class SleepTimer(
        private val onEnded: () -> Unit,
        private val onChanged: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var endsAtRealtimeMs = 0L

    /** Whether the timer waits for the end of the track rather than counting minutes down. */
    var atTrackEnd: Boolean = false
        private set

    /** The last reading of the session while waiting for the end of the track. */
    private var lastPlayback: EndOfTrackSleep.Playback? = null

    private val end = Runnable {
        endsAtRealtimeMs = 0L
        atTrackEnd = false
        lastPlayback = null
        onEnded()
        onChanged()
    }

    /** Whether a timer is counting down or waiting for the end of the track. */
    val isRunning: Boolean
        get() = endsAtRealtimeMs != 0L || atTrackEnd

    /**
     * Milliseconds left, or 0 when none is running. While waiting for the end of the track, it is
     * the time the track has left - 0 when its length is not known.
     */
    fun remainingMs(): Long {
        val now = SystemClock.elapsedRealtime()
        if (atTrackEnd) {
            if (endsAtRealtimeMs != 0L) {
                return (endsAtRealtimeMs - now + EndOfTrackSleep.END_MARGIN_MS).coerceAtLeast(0L)
            }
            return lastPlayback?.timeLeftMs(now) ?: 0L
        }
        return if (endsAtRealtimeMs == 0L) 0L else (endsAtRealtimeMs - now).coerceAtLeast(0L)
    }

    /**
     * Starts a timer of [minutes], replacing any running one; [SleepTimerPolicy.END_OF_TRACK] waits
     * for the end of the track instead, and anything else of 0 or less cancels it.
     */
    fun set(minutes: Int) {
        handler.removeCallbacks(end)
        endsAtRealtimeMs = 0L
        lastPlayback = null
        atTrackEnd = minutes == SleepTimerPolicy.END_OF_TRACK
        Timber.i("Sleep timer: %s", when {
            atTrackEnd -> "at the end of the track"
            minutes > 0 -> "$minutes min"
            else -> "off"
        })
        if (!atTrackEnd && minutes > 0) {
            val lengthMs = minutes * MS_PER_MINUTE
            endsAtRealtimeMs = SystemClock.elapsedRealtime() + lengthMs
            handler.postDelayed(end, lengthMs)
        }
        onChanged()
    }

    /**
     * What the session says now, while waiting for the end of the track; ignored otherwise.
     * [playback] is null when no track is readable (no session, or one without metadata), which
     * changes nothing - a session vanishing is not the track ending.
     */
    fun observe(playback: EndOfTrackSleep.Playback?) {
        if (!atTrackEnd || playback == null) return
        val previous = lastPlayback
        lastPlayback = playback
        val previouslyScheduled = endsAtRealtimeMs
        when (val decision = EndOfTrackSleep.decide(previous, playback)) {
            EndOfTrackSleep.Decision.PauseNow -> {
                Timber.i("Sleep timer: %s ended as the track changed", previous?.trackKey)
                handler.removeCallbacks(end)
                end.run()
                return
            }
            is EndOfTrackSleep.Decision.PauseIn -> {
                handler.removeCallbacks(end)
                endsAtRealtimeMs = playback.atRealtimeMs + decision.delayMs
                handler.postDelayed(end,
                        (endsAtRealtimeMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
            }
            EndOfTrackSleep.Decision.Wait -> {
                handler.removeCallbacks(end)
                endsAtRealtimeMs = 0L
            }
        }
        // The watch counts the track's time left down on its own clock, so it only needs telling
        // when that moved by more than the drift a countdown has anyway - a seek, a pause, a new
        // track - and not on every state the player publishes.
        if (previous == null || previous.trackKey != playback.trackKey ||
                kotlin.math.abs(endsAtRealtimeMs - previouslyScheduled) > RESEND_DRIFT_MS) {
            Timber.d("Sleep timer: %s for %s", if (endsAtRealtimeMs == 0L) "waiting" else
                "pausing in ${endsAtRealtimeMs - SystemClock.elapsedRealtime()} ms", playback.trackKey)
            onChanged()
        }
    }

    /** Stops the countdown without telling anyone: for the service going away. */
    fun release() {
        handler.removeCallbacks(end)
        endsAtRealtimeMs = 0L
        atTrackEnd = false
        lastPlayback = null
    }

    private companion object {
        const val MS_PER_MINUTE = 60_000L

        /** How far the scheduled end may move before the watch is told again. */
        const val RESEND_DRIFT_MS = 2_000L
    }
}
