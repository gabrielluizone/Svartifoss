package com.svartifoss.snfell.common

/**
 * A filled box drawn behind the track title or artist line.
 *
 * The vocabulary is deliberately narrow, and the reason is the three renderers rather than taste.
 * Compose draws this through `TextStyle.background`, which paints the *line box* of each line and
 * offers no control over padding, corner radius or width; the classic View and the phone preview
 * could each do more. Offering a rounded pill or a full-width band would therefore mean the watch
 * and its own preview disagreeing about what the setting does — the exact drift
 * `WatchPreviewParityTest` exists to catch, only invisible to it because both sides would be
 * "working". So the set is what all three can draw identically: a per-line box, differing in how
 * much of it is filled in.
 *
 * That is also the effect actually being asked for. A title over a full-bleed cover is unreadable
 * where the artwork happens to be light, and a shadow only separates the glyphs; a plate behind
 * them replaces what is behind the text outright.
 */
enum class TextBackdropStyle(
        val preferenceValue: String,
        /** Opacity of the fill at 100 % of the user's own opacity setting. */
        val baseAlpha: Float
) {
    NONE("none", 0f),

    /** A marker-pen highlight: present, but the artwork still reads through it. */
    HIGHLIGHT("highlight", .45f),

    /** Barely a tint. For a cover that is only occasionally too light. */
    SOFT("soft", .22f),

    /** Nearly opaque — the artwork behind the line is effectively replaced. */
    BLOCK("block", .82f);

    val isNone: Boolean get() = this == NONE

    companion object {
        /** Unknown and absent values resolve to [NONE]; see [TextShadowStyle.fromPreference]. */
        fun fromPreference(value: String?): TextBackdropStyle =
                entries.firstOrNull { it.preferenceValue == value } ?: NONE
    }
}

/**
 * One element's backdrop configuration.
 *
 * Shares [TextShadowColorMode] with the shadow and the outline: the four sources somebody picks a
 * colour from are the same four everywhere, and three parallel enums would be three things for the
 * community-theme contract to keep in step for nothing.
 */
data class TextBackdropSpec(
        val style: TextBackdropStyle,
        val colorMode: TextShadowColorMode,
        /** `#RRGGBB`, honoured only when [colorMode] is [TextShadowColorMode.CUSTOM]. */
        val customColor: String,
        /** 0..100, scaling [TextBackdropStyle.baseAlpha]. */
        val opacityPercent: Int
) {
    val isNone: Boolean get() = style.isNone || opacityPercent <= 0

    /** 0..1 opacity of the fill. */
    val alpha: Float
        get() = (style.baseAlpha * (opacityPercent.coerceIn(0, MAX_OPACITY_PERCENT) / 100f))
                .coerceIn(0f, 1f)

    companion object {
        const val MIN_OPACITY_PERCENT = 0
        const val MAX_OPACITY_PERCENT = 100
        const val DEFAULT_OPACITY_PERCENT = 100

        val NONE = TextBackdropSpec(
                TextBackdropStyle.NONE,
                TextShadowColorMode.BLACK,
                "",
                DEFAULT_OPACITY_PERCENT)

        /**
         * The fill as opaque ARGB, before [alpha].
         *
         * The default here is **black rather than the album colour** for the same reason the
         * shadow's is: this is a legibility device before it is a decoration, and black behind
         * light text works under every face while an accent that has not been extracted yet works
         * under none of them.
         */
        fun resolveColor(
                colorMode: TextShadowColorMode,
                customColor: String,
                accent: Int?
        ): Int = TextShadowSpec.resolveColor(colorMode, customColor, accent)
    }
}
