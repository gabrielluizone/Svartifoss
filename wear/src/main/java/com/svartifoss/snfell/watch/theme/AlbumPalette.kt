package com.svartifoss.snfell.watch.theme

/** A quantized artwork colour and how many pixels it covers. */
internal data class SwatchInfo(
        val rgb: Int,
        val population: Int
)

/** Real secondary/tertiary colours selected from album swatches ranked by pixel population. */
internal data class AlbumCompanionColors(
        val secondary: Int?,
        val tertiary: Int?
)

/**
 * Chooses the accent colour. The most vibrant swatch usually reads best, but a tiny high-contrast
 * detail (a small logo, a bright reflection) can sit far from the cover's overall tone and used to
 * turn a mostly-blue cover's UI red. Trust the vibrant swatch only when it is not a negligible
 * sliver of the artwork - at least [minShare] of the most-populated swatch's pixels - otherwise use
 * the dominant (most-populated) colour, which is also what the blurred background shows. Returns
 * null only when there is no colour information at all.
 */
internal fun selectPrimaryAccent(
        vibrant: SwatchInfo?,
        swatches: List<SwatchInfo>,
        minShare: Float = 0.10f
): Int? {
    val dominant = swatches.maxByOrNull { it.population }
    if (vibrant == null) return dominant?.rgb
    if (dominant == null) return vibrant.rgb
    return if (vibrant.population >= dominant.population * minShare) vibrant.rgb else dominant.rgb
}

/**
 * Selects additional colours without synthesizing a hue. [rankedColors] must contain quantized
 * colours from the artwork in priority order - callers put Palette's named tonal swatches
 * (Vibrant/Muted/LightVibrant/...) ahead of raw population-ranked ones, since two of the
 * most-populous swatches are often near-duplicate shades of the same dominant hue, while the
 * named swatches are chosen by Palette specifically to be tonally distinct from each other. Null
 * tells the renderer to create a same-hue lightness variant when the cover is monochromatic.
 */
internal fun selectAlbumCompanionColors(
        primary: Int,
        rankedColors: List<Int>
): AlbumCompanionColors {
    val distinct = rankedColors.distinct().filter { it != primary }
    return AlbumCompanionColors(
            secondary = distinct.getOrNull(0),
            tertiary = distinct.getOrNull(1)
    )
}
