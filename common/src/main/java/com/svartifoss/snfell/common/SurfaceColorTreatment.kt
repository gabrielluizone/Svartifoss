package com.svartifoss.snfell.common

/**
 * Color-treatment vocabulary shared by the phone preview and the watch renderer.
 *
 * Component-specific controls can inherit the face-wide treatment or override it. The parser
 * deliberately understands the previous Colors-page values so a watch can render a setting sent
 * by an older phone before that phone has had a chance to run its migration.
 *
 * The harmony cases ([COMPLEMENTARY]..[DUOTONE]) derive a *related* set of colours instead of
 * repeating one album swatch; their math lives in [ColorHarmony] and is applied for both sides by
 * [SurfacePaletteResolver]. An older watch that receives one of these values from a newer phone
 * parses it as the [default] (Expressive for the face-wide setting), which is the closest
 * album-derived look - so a mixed-version pair degrades instead of failing.
 */
enum class SurfaceColorTreatment {
    FOLLOW,
    NORMAL,
    DESATURATED,
    EXPRESSIVE,
    COMPLEMENTARY,
    TRIADIC,
    ANALOGOUS,
    MONOCHROME,
    DUOTONE;

    /** True when this treatment builds its palette from the album art rather than a fixed color. */
    val isAlbumDerived: Boolean
        get() = this != NORMAL && this != FOLLOW

    /**
     * How far this treatment turns the *whole* triad, primary included, in degrees.
     *
     * The harmonies used to anchor the primary to the album's own hue and rotate only the companion
     * slots. That is invisible on the many surfaces which paint the primary and nothing else - the
     * Note face paints exactly one colour - so Triadic, Complementary and Analogous rendered
     * pixel-identical to Expressive there, while the phone's picker showed three distinct swatches
     * for each. Turning all three slots together keeps every harmony's internal angles intact (the
     * same trick `wear_color_hue_shift` uses) while making the choice legible on the accent itself.
     *
     * Two treatments stay at zero, for reasons that are not oversights:
     *  - [MONOCHROME] is also the fallback every harmony degrades to on near-neutral artwork, where
     *    rotation is meaningless - see [ColorHarmony.monochromeTonal].
     *  - [DUOTONE]'s slots are two colours that genuinely appear in the cover; rotating them would
     *    substitute invented hues and defeat the one thing that treatment is for. It reads as
     *    distinct through its companion slots instead.
     *
     * Near-grey artwork is unaffected throughout: the rotation runs through
     * [ColorHarmony.hueShifted], which returns a neutral source untouched rather than inventing a
     * tint the cover never had.
     */
    val primaryRotationDegrees: Float
        get() = when (this) {
            COMPLEMENTARY -> ColorHarmony.COMPLEMENTARY_ROTATION
            TRIADIC -> ColorHarmony.TRIADIC_SECOND_ROTATION
            ANALOGOUS -> ColorHarmony.ANALOGOUS_ROTATION
            else -> 0f
        }

    fun resolveAgainst(global: SurfaceColorTreatment): SurfaceColorTreatment =
            if (this == FOLLOW) global.takeUnless { it == FOLLOW } ?: EXPRESSIVE else this

    companion object {
        fun fromPreference(
                value: String?,
                legacyDesaturated: Boolean = false,
                default: SurfaceColorTreatment = FOLLOW
        ): SurfaceColorTreatment = when (value) {
            "follow" -> FOLLOW
            "normal", "neutral", "custom" -> NORMAL
            "desaturated" -> DESATURATED
            "expressive" -> EXPRESSIVE
            "complementary" -> COMPLEMENTARY
            "triadic" -> TRIADIC
            "analogous" -> ANALOGOUS
            "monochrome" -> MONOCHROME
            "duotone" -> DUOTONE
            "album" -> if (legacyDesaturated) DESATURATED else EXPRESSIVE
            else -> default
        }
    }
}
