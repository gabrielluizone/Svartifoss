package com.svartifoss.snfell.common

/**
 * The sleep timer's presets, and what one press of its chip does.
 *
 * One chip, no dialog: on the wrist a press has to be the whole interaction, so it steps through
 * a short ladder - off, 15, 30, 45, 60 minutes, off - the way the playback-speed chip steps through
 * its own ([PlaybackSpeeds]). Shared by the watch (which decides what to send) and the tests; the
 * phone only ever receives a length.
 *
 * The ladder is stepped from what the **label says**, not from what was last set: a press goes to
 * the first preset longer than the number the person is looking at. A timer started at 30 minutes
 * reads 29 a minute later, so the next press tops it back up to 30 and the one after that goes on
 * to 45 - never straight from 29 to 45, which would skip a rung the label still sits below, and
 * never to a rung already behind it.
 */
object SleepTimerPolicy {
    /** The lengths one press steps through, in minutes, shortest first. */
    val PRESET_MINUTES: List<Int> = listOf(15, 30, 45, 60)

    private const val MS_PER_MINUTE = 60_000L

    /** Whole minutes left, rounded up so a timer that still has time never reads "0 min". */
    fun minutesLeft(remainingMs: Long): Int =
            if (remainingMs <= 0L) 0 else ((remainingMs + MS_PER_MINUTE - 1) / MS_PER_MINUTE).toInt()

    /**
     * The length a press should set while [remainingMs] is left: the first preset longer than the
     * minutes shown, the shortest one when no timer is running, and 0 - switch it off - after the
     * longest.
     */
    fun nextMinutes(remainingMs: Long): Int {
        val shown = minutesLeft(remainingMs)
        if (shown == 0) return PRESET_MINUTES.first()
        return PRESET_MINUTES.firstOrNull { it > shown } ?: 0
    }
}
