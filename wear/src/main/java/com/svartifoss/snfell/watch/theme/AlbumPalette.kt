package com.svartifoss.snfell.watch.theme

/**
 * `SwatchInfo` and `selectPrimaryAccent` used to live here, watch-only, while the phone preview
 * carried its own inlined copy that picked a different swatch - which is how the same cover came
 * out beige in the miniature and grey on the wrist. They now live in
 * `common/.../AlbumAccentSelection.kt` so both sides run the identical selection, and so the choice
 * between them can be a user preference (`MiscPreferences.WEAR_ALBUM_ACCENT_SOURCE`).
 */

/** Real secondary/tertiary colours selected from album swatches ranked by pixel population. */
internal data class AlbumCompanionColors(
        val secondary: Int?,
        val tertiary: Int?
)

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
