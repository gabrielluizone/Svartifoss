package com.svartifoss.snfell.common

/**
 * The shape of a shadow cast by the track title or artist line.
 *
 * Five authored presets rather than four numeric controls, for the reason the rest of the
 * appearance surface already follows: a radius, an X offset, a Y offset and an alpha are four
 * questions whose good answers are strongly correlated, and offering them separately mostly
 * produces combinations nobody wants. Intensity stays a number because it is the one axis a person
 * genuinely wants to dial ([TextShadowSpec.strengthPercent]).
 *
 * The geometry is in dp and each renderer converts, so a shadow is the same size on a 1x and a 2x
 * watch — the same rule the rest of `FaceGeometry` follows.
 */
enum class TextShadowStyle(
        val preferenceValue: String,
        /** Gaussian blur radius at 100 % strength. Zero is a hard edge, not "no shadow". */
        val radiusDp: Float,
        /** How far the shadow falls, straight down. */
        val offsetDp: Float,
        /** Opacity at 100 % strength, over whatever the resolved colour already carries. */
        val baseAlpha: Float
) {
    NONE("none", 0f, 0f, 0f),

    /** The one to reach for over artwork: enough separation to read, not enough to notice. */
    SOFT("soft", 3f, 1f, .55f),

    /** No blur at all — a crisp offset copy, the way a poster sets display type. */
    HARD("hard", 0f, 1.5f, .70f),

    /** Centred and wide, so it reads as light coming off the text rather than as a shadow. */
    GLOW("glow", 6f, 0f, .75f),

    /** Long and soft: the text sits above the artwork rather than on it. */
    LIFT("lift", 5f, 2.5f, .60f),

    /** Barely there. For a face whose composition is already dark enough to carry the text. */
    WHISPER("whisper", 2f, .5f, .35f);

    val isNone: Boolean get() = this == NONE

    companion object {
        /**
         * Unknown and absent values resolve to [NONE].
         *
         * Unlike a fallback that picks a *look*, this one picks "no change", which is the only
         * safe answer for a value arriving from a newer build or an imported theme: rendering an
         * unrecognised style as some other style would silently restyle somebody's saved face.
         */
        fun fromPreference(value: String?): TextShadowStyle =
                entries.firstOrNull { it.preferenceValue == value } ?: NONE
    }
}

/** Where a shadow's colour comes from. */
enum class TextShadowColorMode(val preferenceValue: String) {
    /** Black. The default, and the only one that works on every background. */
    BLACK("black"),

    /** The album accent, which turns [TextShadowStyle.GLOW] into a coloured halo. */
    ALBUM("album"),

    /** White — for dark text on a light face, where black would do nothing. */
    WHITE("white"),

    CUSTOM("custom");

    companion object {
        fun fromPreference(value: String?): TextShadowColorMode =
                entries.firstOrNull { it.preferenceValue == value } ?: BLACK
    }
}

/**
 * One element's complete shadow configuration.
 *
 * Deliberately separate from [WatchTypography.TextSpec] rather than six more fields on it.
 * `TextSpec` describes the *glyphs* — weight, slant, size, tracking, case — and every renderer
 * applies it the same way, by handing those values to a text API. A shadow is a second drawing
 * pass with its own colour resolution, and three of the five faces that draw text do not have an
 * accent to resolve it against until album extraction has finished. Keeping them apart is what
 * lets `TextSpec.isIdentity` go on meaning "the glyph styling changes nothing", which is the check
 * every renderer uses to skip its styling path entirely.
 */
data class TextShadowSpec(
        val style: TextShadowStyle,
        val colorMode: TextShadowColorMode,
        /** `#RRGGBB`, honoured only when [colorMode] is [TextShadowColorMode.CUSTOM]. */
        val customColor: String,
        /** 0..[MAX_STRENGTH_PERCENT]; scales radius, offset and alpha together. */
        val strengthPercent: Int
) {
    /** True when this spec draws nothing, letting a renderer skip the whole second pass. */
    val isNone: Boolean get() = style.isNone || strengthPercent <= 0

    private val strength: Float
        get() = (strengthPercent.coerceIn(0, MAX_STRENGTH_PERCENT)) / 100f

    /**
     * Blur radius in dp.
     *
     * Floored just above zero for a blurred style, because a zero radius means something entirely
     * different to the platform: `Paint.setShadowLayer` treats it as "no shadow layer" and Compose
     * treats it as a hard edge. A Soft shadow dialled to 1 % should be a faint soft shadow, not
     * silently a hard one — and not silently nothing.
     */
    val radiusDp: Float
        get() = if (style.radiusDp <= 0f) 0f else (style.radiusDp * strength).coerceAtLeast(MIN_BLUR_DP)

    /** Vertical offset in dp. Always straight down: a light source that moves per element would
     *  make two lines of the same block look like they belong to different pictures. */
    val offsetDp: Float
        get() = style.offsetDp * strength

    /** 0..1 opacity, before the resolved colour's own alpha. */
    val alpha: Float
        get() = (style.baseAlpha * strength).coerceIn(0f, 1f)

    companion object {
        const val MIN_STRENGTH_PERCENT = 0
        const val MAX_STRENGTH_PERCENT = 200

        /** Above 100 % a shadow deepens rather than growing without limit; see [strength]. */
        const val DEFAULT_STRENGTH_PERCENT = 100

        private const val MIN_BLUR_DP = 0.35f

        val NONE = TextShadowSpec(
                TextShadowStyle.NONE,
                TextShadowColorMode.BLACK,
                "",
                DEFAULT_STRENGTH_PERCENT)

        /**
         * The shadow colour as opaque ARGB, before [TextShadowSpec.alpha] is applied.
         *
         * Hand-rolled hex parsing rather than `Color.parseColor`, for the reason
         * `AdaptiveTextContrast` hand-rolls its luminance: this has to be callable from a plain
         * JVM test and from `common`, which the watch, the phone preview and the settings UI all
         * read — three renderers agreeing by construction rather than by three copies.
         *
         * [accent] is the album-derived colour and may be absent (extraction has not finished, or
         * the artwork carried no colour at all). `ALBUM` then falls back to black rather than to
         * a guess: a shadow is a legibility device, and black is the value that works under every
         * face while a real answer is still on its way.
         */
        fun resolveColor(
                colorMode: TextShadowColorMode,
                customColor: String,
                accent: Int?
        ): Int = when (colorMode) {
            TextShadowColorMode.BLACK -> OPAQUE_BLACK
            TextShadowColorMode.WHITE -> OPAQUE_WHITE
            TextShadowColorMode.ALBUM -> accent?.let { it or ALPHA_MASK } ?: OPAQUE_BLACK
            TextShadowColorMode.CUSTOM -> parseHexRgb(customColor) ?: OPAQUE_BLACK
        }

        /** `#RRGGBB` (or a bare `RRGGBB`) to opaque ARGB, or null for anything else. */
        fun parseHexRgb(value: String): Int? {
            val digits = value.trim().removePrefix("#")
            if (digits.length != 6) return null
            var result = 0
            for (character in digits) {
                val digit = when (character) {
                    in '0'..'9' -> character - '0'
                    in 'a'..'f' -> character - 'a' + 10
                    in 'A'..'F' -> character - 'A' + 10
                    else -> return null
                }
                result = (result shl 4) or digit
            }
            return result or ALPHA_MASK
        }

        private const val ALPHA_MASK = 0xFF000000.toInt()
        private const val OPAQUE_BLACK = 0xFF000000.toInt()
        private const val OPAQUE_WHITE = 0xFFFFFFFF.toInt()
    }
}
