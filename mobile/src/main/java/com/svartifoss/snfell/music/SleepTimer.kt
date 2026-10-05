package com.svartifoss.snfell.music

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * The phone's sleep timer: counts down, then calls [onEnded] so the music can be paused.
 *
 * It lives on the phone, in [MusicService], for the reason `CommPaths.MESSAGE_SET_SLEEP_TIMER`
 * gives: the phone is what pauses the music, and it is the device that is certain to be running
 * when someone has fallen asleep. It is deliberately **in memory only** - a timer that outlived
 * the service that could act on it would fire into nothing, and one restored after a restart would
 * pause music the person started after waking up.
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

    private val end = Runnable {
        endsAtRealtimeMs = 0L
        onEnded()
        onChanged()
    }

    /** Whether a timer is counting down. */
    val isRunning: Boolean
        get() = endsAtRealtimeMs != 0L

    /** Milliseconds left, or 0 when none is running. */
    fun remainingMs(): Long =
            if (endsAtRealtimeMs == 0L) 0L
            else (endsAtRealtimeMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)

    /** Starts a timer of [minutes], replacing any running one; 0 or less cancels it. */
    fun set(minutes: Int) {
        handler.removeCallbacks(end)
        if (minutes <= 0) {
            endsAtRealtimeMs = 0L
        } else {
            val lengthMs = minutes * MS_PER_MINUTE
            endsAtRealtimeMs = SystemClock.elapsedRealtime() + lengthMs
            handler.postDelayed(end, lengthMs)
        }
        onChanged()
    }

    /** Stops the countdown without telling anyone: for the service going away. */
    fun release() {
        handler.removeCallbacks(end)
        endsAtRealtimeMs = 0L
    }

    private companion object {
        const val MS_PER_MINUTE = 60_000L
    }
}
