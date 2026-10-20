package com.svartifoss.snfell.common

import kotlin.math.abs
import kotlin.math.floor

/**
 * What the crown does under the finger while the volume changes.
 *
 * The volume panel has always been silent: the arc moves and nothing else says that a detent was
 * passed, so on a crown with no mechanical click a turn is a smooth, unquantified slide. These are
 * the two things worth adding - a tick as each step goes by, and a firmer buzz at the end of the
 * range - and nothing else, because a vibration motor is the one feedback a wrist cannot ignore and
 * the cost of too much of it is a watch people stop turning.
 *
 * Pure so the rule is pinned by a JVM test: when a tick fires is exactly the part that is easy to
 * get subtly wrong (a tick per event instead of per step, a tick that fires twice at a boundary).
 */
enum class VolumeHaptics(val token: String) {
    /** No extra feedback (the historical behaviour). */
    OFF("off"),

    /** A short tick each time the level crosses a 5 percent step. */
    TICKS("ticks"),

    /** The ticks, plus a firmer buzz when the crown keeps turning at the minimum or maximum. */
    TICKS_AND_LIMITS("ticks_limits");

    /** True when moving from [before] to [after] crossed a step boundary and should tick. */
    fun ticksBetween(before: Float, after: Float): Boolean =
            this != OFF && stepIndex(before) != stepIndex(after)

    /**
     * True when the crown asked for [requested] more but the level could not move, at either end.
     * The caller rate-limits it; this only answers whether the *situation* is a limit.
     */
    fun hitsLimit(before: Float, after: Float, requested: Float): Boolean =
            this == TICKS_AND_LIMITS &&
                    abs(requested) > 0f &&
                    abs(after - before) < 1e-6f &&
                    (after <= 0f || after >= 1f)

    companion object {
        /** Width of one step, as a fraction of the range. */
        const val STEP = 0.05f

        /** The firmest a limit buzz may repeat, so a held crown does not become a drone. */
        const val LIMIT_REPEAT_MS = 280L

        fun fromPref(value: String?): VolumeHaptics =
                entries.firstOrNull { it.token == value } ?: OFF

        /** Which step [level] is in. A small epsilon keeps 0.15 (stored as 0.1499999) in step 3. */
        fun stepIndex(level: Float): Int = floor(level / STEP + 1e-4f).toInt()
    }
}
