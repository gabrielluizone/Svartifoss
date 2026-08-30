package com.svartifoss.snfell.common

/**
 * How a track title that does not fit should behave, decoded from
 * [MiscPreferences.WEAR_TITLE_TEXT_MODE].
 *
 * There are three renderers - the classic face's `OutlineTextView`, Compose's `AdaptiveTitleText`,
 * and the phone preview's own `planTitle` - and each one used to carry its own copy of this table.
 * That is exactly the shape that let `wrap3`, `wrap5` and `static` be implemented in two of the
 * three and silently fall back to "smart" in the classic face, while the preview cheerfully drew
 * the layout the wrist would not produce.
 *
 * Only the wrapping half lives here. The rest of each mode is genuinely renderer-specific - one
 * scrolls with a `TextView` marquee, one with a Compose modifier, one by translating a canvas - but
 * *how many lines a wrapping mode gets* is a plain property of the setting and belongs in one
 * place.
 */
object TitleTextMode {

    const val SMART = "smart"
    const val MARQUEE = "marquee"
    const val SHRINK = "shrink"

    /**
     * The line cap a wrapping mode allows, or null when [value] is not a wrapping mode.
     *
     * Null covers `smart`, `marquee`, `shrink`, an absent value and anything a newer build might
     * write that this one has never heard of - the caller then falls back to its own cascade, which
     * is the safe direction: an unknown value degrades to the default behaviour rather than to a
     * one-line truncation nobody asked for.
     */
    fun wrapLines(value: String?): Int? = when (value) {
        "static" -> 1
        "wrap" -> 2
        "wrap3" -> 3
        "wrap5" -> 5
        else -> null
    }

    /** Whether [value] scrolls a single line rather than shrinking or wrapping. */
    fun isMarquee(value: String?): Boolean = value == MARQUEE

    /** Whether [value] shrinks a single line to a floor and then ellipsizes. */
    fun isShrink(value: String?): Boolean = value == SHRINK
}
