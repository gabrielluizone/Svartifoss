package com.svartifoss.snfell.common

import androidx.annotation.DrawableRes

/**
 * The glyph of a seek chip: a circular arrow with the number of seconds drawn inside it.
 *
 * The quick panel's seek block used to label its chips "−30 −10 +10 +30", which reads as arithmetic
 * and not as an instruction, and as text it was set on whatever the album behind it happened to be.
 * These are the Material "replay" and "forward" arrows, the glyphs every media player has taught
 * people to read as "back" and "forward" by this many seconds, so one shape says both the
 * direction and the amount.
 *
 * Material draws them for 5, 10 and 30 seconds and no other amount, which is why
 * [QuickPanelStack.SEEK_STEP_VOCABULARY] stops there: a value with no glyph here has no honest
 * chip. Both lookups return null for one rather than a near miss, because a chip that draws ten
 * seconds while jumping by fifteen is worse than no chip.
 */
object SeekGlyphs {

    /** The arrow for jumping back by [seconds], or null when there is none for that amount. */
    @DrawableRes
    fun back(seconds: Int): Int? = when (seconds) {
        5 -> R.drawable.action_replay_5
        10 -> R.drawable.action_replay_10
        30 -> R.drawable.action_replay_30
        else -> null
    }

    /** The arrow for jumping forward by [seconds], or null when there is none for that amount. */
    @DrawableRes
    fun forward(seconds: Int): Int? = when (seconds) {
        5 -> R.drawable.action_forward_5
        10 -> R.drawable.action_forward_10
        30 -> R.drawable.action_forward_30
        else -> null
    }

    /** The arrow for a signed jump: negative goes back. */
    @DrawableRes
    fun forOffset(seconds: Int): Int? =
            if (seconds < 0) back(-seconds) else forward(seconds)
}
