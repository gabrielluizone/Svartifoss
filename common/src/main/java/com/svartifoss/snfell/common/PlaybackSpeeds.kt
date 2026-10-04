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
}
