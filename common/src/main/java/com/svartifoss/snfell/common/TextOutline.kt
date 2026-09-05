package com.svartifoss.snfell.common

/**
 * A stroke drawn around the track title or artist line.
 *
 * An outline is **two drawing passes**, not a paint setting: no text API on either platform can
 * stroke and fill in one go. That constraint is the reason the width is a *fraction of the text
 * size* rather than a dp value — the stroke grows from the glyph's centre outward, so a fixed
 * width that looks like a keyline on a 30sp title closes up the counters of a 12sp artist line.
 * A dp floor keeps the thinnest setting from vanishing entirely on a low-density watch.
 *
 * Deliberately not folded into [TextShadowSpec]: a shadow is drawn *behind* the glyphs and a
 * stroke is drawn *on* them, they are chosen independently, and combining them into one "effects"
 * value would make the community-theme contract validate two unrelated vocabularies as one string.
 */
enum class TextOutlineStyle(
        val preferenceValue: String,
        /** Stroke width as a fraction of the text size. See the class note for why not dp. */
        val widthFraction: Float
) {
    NONE("none", 0f),

    /** Just enough to separate the glyphs from what is behind them. */
    HAIRLINE("hairline", .022f),

    THIN("thin", .045f),
    MEDIUM("medium", .075f),

    /** Heavy enough to read as a deliberate treatment rather than as separation. */
    BOLD("bold", .115f);

    val isNone: Boolean get() = this == NONE

    companion object {
        /** The narrowest stroke worth drawing, in dp. Below this a stroke aliases into nothing on
         *  a 1x watch, which reads as the setting having no effect rather than as a fine line. */
        const val MIN_WIDTH_DP = 0.5f

        /**
         * Unknown and absent values resolve to [NONE], for [TextShadowStyle.fromPreference]'s
         * reason: rendering an unrecognised style as some *other* style would silently restyle a
         * saved face rather than leave it alone.
         */
        fun fromPreference(value: String?): TextOutlineStyle =
                entries.firstOrNull { it.preferenceValue == value } ?: NONE
    }
}

/**
 * One element's outline configuration.
 *
 * Reuses [TextShadowColorMode] rather than declaring a parallel enum: the four sources a person
 * can pick from are the same four (black, the album's colour, white, or one they choose), and two
 * identical vocabularies under different names would be two things for the community-theme
 * contract to keep in step for no gain.
 */
data class TextOutlineSpec(
        val style: TextOutlineStyle,
        val colorMode: TextShadowColorMode,
        /** `#RRGGBB`, honoured only when [colorMode] is [TextShadowColorMode.CUSTOM]. */
        val customColor: String
) {
    val isNone: Boolean get() = style.isNone

    /**
     * Stroke width in pixels for text drawn at [textSizePx] on a screen of [density].
     *
     * Both arguments are needed and neither is redundant: the fraction scales the stroke with the
     * type, and the floor is the one thing that has to be expressed in real screen units.
     */
    fun strokeWidthPx(textSizePx: Float, density: Float): Float =
            if (isNone) 0f
            else (textSizePx * style.widthFraction)
                    .coerceAtLeast(TextOutlineStyle.MIN_WIDTH_DP * density)

    companion object {
        val NONE = TextOutlineSpec(TextOutlineStyle.NONE, TextShadowColorMode.BLACK, "")

        /**
         * The stroke colour as opaque ARGB.
         *
         * Shares [TextShadowSpec.resolveColor], so an "album" outline and an "album" shadow on the
         * same line are provably the same colour rather than two implementations that agree today.
         */
        fun resolveColor(
                colorMode: TextShadowColorMode,
                customColor: String,
                accent: Int?
        ): Int = TextShadowSpec.resolveColor(colorMode, customColor, accent)
    }
}
