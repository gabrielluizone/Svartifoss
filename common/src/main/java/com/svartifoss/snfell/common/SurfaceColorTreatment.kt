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
