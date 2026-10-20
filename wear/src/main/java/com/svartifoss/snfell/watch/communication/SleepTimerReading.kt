package com.svartifoss.snfell.watch.communication

import android.os.SystemClock

/**
 * The phone's sleep timer as this watch draws it.
 *
 * [endsAtRealtimeMs] is when it ends on **this** watch's `elapsedRealtime`, 0 when no countdown
 * runs - see [PhoneConnection.sleepTimer] for why an end and not a remainder. [atTrackEnd] says the
 * timer waits for the end of the track instead of counting minutes; [endsAtRealtimeMs] is then the
 * track's own end when its length is known, and is never what the chip shows.
 *
 * One value rather than two `LiveData`s so the two halves can never be observed out of step: a
 * countdown that had just become "end of the track" would otherwise draw one frame as a number.
 */
data class SleepTimerReading(
        val endsAtRealtimeMs: Long = 0L,
        val atTrackEnd: Boolean = false
) {
    /** Whether a timer of either kind is running. */
    val running: Boolean
        get() = atTrackEnd || remainingMs() > 0L

    /** Milliseconds until [endsAtRealtimeMs] on this device's clock; 0 when it is not set. */
    fun remainingMs(nowRealtimeMs: Long = SystemClock.elapsedRealtime()): Long =
            if (endsAtRealtimeMs == 0L) 0L else (endsAtRealtimeMs - nowRealtimeMs).coerceAtLeast(0L)
}
