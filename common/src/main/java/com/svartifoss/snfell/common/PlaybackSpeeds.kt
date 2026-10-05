package com.svartifoss.snfell.common

import kotlin.math.floor

/**
 * The ladder the watch steps the playback speed along: 0.5x to 2x in quarter steps.
 *
 * One press goes to the next rung and the top wraps back to the bottom, so a single control can
 * reach every speed. It lives here, rather than in either screen that offers it, because the
 * progress screen and the quick panel's speed tool must agree about what "next" means - two
 * ladders would put the same button on two different speeds depending on where it was pressed.
 *
 * A player that reports a speed between rungs (1.1x is not unheard of) is snapped to the rung below
 * it before stepping, so the first press lands on a rung instead of on another odd value.
 */
object PlaybackSpeeds {
    const val MIN = 0.5f
    const val MAX = 2.0f
    const val STEP = 0.25f

    /** Absorbs float noise, so 1.25f stored as 1.2500001f still counts as the 1.25 rung. */
    private const val EPSILON = 0.001f

    /** The speed one press after [current]. */
    fun next(current: Float): Float {
        val rung = floor(current / STEP + EPSILON) * STEP
        val stepped = rung + STEP
        return if (stepped > MAX + EPSILON) MIN else stepped.coerceIn(MIN, MAX)
    }

    /**
     * How long a press is remembered as the speed in force, which is longer than the phone takes
     * to confirm one and shorter than anyone leaves a control alone before pressing it again.
     */
    const val TAP_MEMORY_MS = 3_000L

    /**
     * Steps from what the last press chose when that was recent, and from the speed the phone
     * reports otherwise.
     *
     * The phone is the only source of the speed in force, and it answers a press after a Bluetooth
     * round trip and the player's own delay - and a player that does not support the command never
     * answers it at all. A control that always stepped from the *reported* speed therefore chose the
     * same rung for every press in a quick run, so holding a finger on it looked like a button that
     * did nothing past the first step. Remembering the last press for [TAP_MEMORY_MS] makes a run
     * of presses walk up the ladder, and forgetting it afterwards makes the next press start from
     * what is true - which for a player that ignored the command is where it began.
     *
     * [clock] is a monotonic millisecond clock, a parameter so this stays free of the framework.
     */
    class TapMemory(private val clock: () -> Long) {
        private var lastTapped = 1f
        private var lastTappedAt = 0L
        private var hasTapped = false

        /** The speed this press chooses, given what the phone [reported] last. */
        fun next(reported: Float): Float {
            val now = clock()
            val elapsed = now - lastTappedAt
            val base = if (hasTapped && elapsed in 0 until TAP_MEMORY_MS) lastTapped else reported
            return PlaybackSpeeds.next(base).also {
                lastTapped = it
                lastTappedAt = now
                hasTapped = true
            }
        }
    }
}
