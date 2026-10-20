package com.svartifoss.snfell.common

/**
 * The sleep timer's presets, and what one press of its chip does.
 *
 * One chip, no dialog: on the wrist a press has to be the whole interaction, so it steps through
 * a short ladder - off, 15, 30, 45, 60 minutes, *end of the track*, off - the way the
 * playback-speed chip steps through its own ([PlaybackSpeeds]). Shared by the watch (which decides
 * what to send) and the tests; the phone only ever receives a length, or [END_OF_TRACK].
 *
 * The last rung is not a length. "Let this song finish" is the commonest thing to want at night,
 * and no number of minutes says it: the song can have forty seconds left or six minutes. It sits
 * after the longest preset rather than before the shortest so the ladder still reads as one
 * direction - longer and longer, then off - and so the presets that existed before it stay one
 * press apart from each other.
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

    /**
     * Sent in place of a length: pause when the track playing now ends.
     *
     * Negative so that a phone build from before it existed reads it the way it reads every
     * length of zero or less - as "cancel" - instead of as a timer of some number of minutes. The
     * watch then shows the timer off, which is what that phone actually did.
     */
    const val END_OF_TRACK: Int = -1

    private const val MS_PER_MINUTE = 60_000L

    /** Whole minutes left, rounded up so a timer that still has time never reads "0 min". */
    fun minutesLeft(remainingMs: Long): Int =
            if (remainingMs <= 0L) 0 else ((remainingMs + MS_PER_MINUTE - 1) / MS_PER_MINUTE).toInt()

    /**
     * The length a press should set while [remainingMs] is left: the first preset longer than the
     * minutes shown, the shortest one when no timer is running, [END_OF_TRACK] after the longest,
     * and 0 - switch it off - when the timer is already waiting for the end of the track
     * ([atTrackEnd]).
     *
     * [atTrackEnd] wins over [remainingMs]: while it waits for the end of the track the phone
     * reports how long the *track* has left, which can be any number of minutes, and reading that
     * as a countdown would step to whichever preset happened to sit above it.
     */
    fun nextMinutes(remainingMs: Long, atTrackEnd: Boolean = false): Int {
        if (atTrackEnd) return 0
        val shown = minutesLeft(remainingMs)
        if (shown == 0) return PRESET_MINUTES.first()
        return PRESET_MINUTES.firstOrNull { it > shown } ?: END_OF_TRACK
    }

    /**
     * Encodes the phone's answer to the watch (see `CommPaths.MESSAGE_SLEEP_TIMER_STATE`): the time
     * left as a big-endian long, then - only when waiting for the end of the track - one byte
     * [STATE_FLAG_TRACK_END].
     *
     * The byte goes *after* the long so that a watch build from before it existed, which reads the
     * first eight bytes and nothing else, still parses the message; it then shows a countdown of the
     * track's remaining time, which is the closest thing it can draw.
     */
    fun encodeState(remainingMs: Long, atTrackEnd: Boolean): ByteArray {
        val buffer = java.nio.ByteBuffer.allocate(if (atTrackEnd) STATE_SIZE_WITH_FLAG else Long.SIZE_BYTES)
        buffer.putLong(remainingMs.coerceAtLeast(0L))
        if (atTrackEnd) buffer.put(STATE_FLAG_TRACK_END)
        return buffer.array()
    }

    /** What [encodeState] wrote: the time left, and whether the timer waits for the track's end. */
    data class State(val remainingMs: Long, val atTrackEnd: Boolean)

    /** Reads [encodeState]'s bytes; a message with no flag byte is an ordinary countdown. */
    fun decodeState(bytes: ByteArray): State {
        val buffer = java.nio.ByteBuffer.wrap(bytes)
        val remaining = buffer.long
        val atTrackEnd = bytes.size >= STATE_SIZE_WITH_FLAG &&
                bytes[Long.SIZE_BYTES] == STATE_FLAG_TRACK_END
        return State(remaining.coerceAtLeast(0L), atTrackEnd)
    }

    private const val STATE_FLAG_TRACK_END: Byte = 1
    private const val STATE_SIZE_WITH_FLAG = Long.SIZE_BYTES + 1
}
