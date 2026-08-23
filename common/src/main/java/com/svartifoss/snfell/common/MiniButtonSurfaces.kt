package com.svartifoss.snfell.common

import androidx.core.graphics.ColorUtils

/**
 * The colour half of a mini button's look, decoded from `screen_buttons_bg_style`.
 *
 * Three surfaces draw these buttons and they must agree: the watch's own View row, the Chat face -
 * which hosts them inside its composition rather than under it, so a Compose surface has to paint
 * the same fill from the same preference - and the phone's `WatchPreviewView` miniature. Before
 * this object each had its own `when` over the raw string, and they had already drifted: the
 * preview never applied the lightness lift to `glow_exp`'s stroke or icon, and picked its icon
 * colour on a naive luminance split where the watch used the WCAG crossover, so a medium-light
 * pill previewed with white ink and rendered with black.
 *
 * The split here is deliberate. [resolve] is the decision table and is free of `android.*`, so it
 * is pinned by a plain JVM test. [paletteFor] is the part that genuinely needs colour maths and is
 * shared for the same reason, one step earlier: every caller derives the album's expressive tone
 * and glow the same way or the table below is answering a different question for each of them.
 *
 * What is *not* here is everything about a mini button that is not colour: its shape, its size,
 * where the row sits, and how it curves. Those stay with whoever lays the buttons out, because a
 * face that composes the row itself (see [FaceScopedPreferences] and Chat) owns exactly those and
 * not the fill.
 */
object MiniButtonSurfaces {

    /** "Follow layout": the one value that deliberately has no colour of its own, so the surface
     *  drawing the button supplies its own neutral skin. It is also the default. */
    const val FOLLOW_FACE = "glass"

    /** The uniform glass fill, mirroring wear's `R.color.glass_surface_fill`. Callers with access
     *  to the resource should pass it through [Palette.uniformGlassFill]; this is the value for
     *  the ones that cannot reach wear's resources (the phone preview). */
    const val UNIFORM_GLASS_FILL: Int = 0xB3161619.toInt()

    private const val TRANSPARENT = 0

    /** Alpha applied to a solid fill. The expressive variant sits slightly lower on purpose - its
     *  tonal surface is already light, and at full strength it stopped reading as a surface. */
    private const val SOLID_ALPHA = 230
    private const val SOLID_EXPRESSIVE_ALPHA = 0xD0
    private const val TRANSLUCENT_ALPHA = 0x4D
    private const val EDGE_ALPHA = 0xE0

    /** Colours a style may draw from, derived once by [paletteFor]. */
    data class Palette(
            /** The accent extracted from the current album art. */
            val albumAccent: Int,
            /** What "solid_theme" paints. Face-adjusted by the caller, since the Expressive layout
             *  uses a tonal surface of the theme colour where every other face uses it raw. */
            val themeAccent: Int,
            /** The album accent as an Expressive tonal surface. */
            val expressiveAlbum: Int,
            /** [albumAccent] and [expressiveAlbum] lifted to a legible lightness for the styles
             *  that draw the colour as a hairline rather than as a fill. */
            val glowAlbum: Int,
            val glowExpressive: Int,
            val uniformGlassFill: Int = UNIFORM_GLASS_FILL
    )

    /**
     * How one mini button is painted. Sizes are in dp and colours are ARGB, with a fully
     * transparent value meaning "do not paint this at all" rather than "paint nothing visible" -
     * a distinction that matters to a caller which would otherwise stroke a zero-width outline.
     */
    data class Surface(
            val fillArgb: Int = TRANSPARENT,
            val strokeArgb: Int = TRANSPARENT,
            val strokeWidthDp: Float = 0f,
            /** Colour to draw the icon in, or null to leave the action's own icon untouched. */
            val iconTintArgb: Int? = null,
            /**
             * Whether [iconTintArgb] applies even to an icon that is *not* a tintable template -
             * an app launcher icon or fetched cover art. Only the styles whose whole point is a
             * single-colour treatment force it; elsewhere a full-colour icon keeps its colours,
             * because flattening a cover to one hue destroys the thing it was chosen for.
             */
            val forceIconTint: Boolean = false,
            /** True only for [FOLLOW_FACE]: the caller paints its own neutral skin instead. */
            val followsFaceNeutral: Boolean = false
    )

    fun paletteFor(albumAccent: Int, themeAccent: Int): Palette {
        val expressiveAlbum = PaletteTransforms.tonalSurface(albumAccent, 0.74f, 0.40f, 0.92f)
        return Palette(
                albumAccent = albumAccent,
                themeAccent = themeAccent,
                expressiveAlbum = expressiveAlbum,
                glowAlbum = glow(albumAccent),
                glowExpressive = glow(expressiveAlbum)
        )
    }

    /**
     * The decision table. Unknown values fall to [FOLLOW_FACE]'s behaviour, which is also the
     * default: a style can arrive from an imported backup or a newer phone build, and deferring to
     * the layout is the one answer that is correct on every face.
     */
    fun resolve(style: String?, palette: Palette): Surface = when (style?.trim()) {
        "transparent" -> Surface()

        "uniform_glass" -> Surface(fillArgb = palette.uniformGlassFill)

        "uniform_glass_light" -> Surface(
                fillArgb = argb(0x1A, 0xFF, 0xFF, 0xFF),
                iconTintArgb = WHITE)

        "translucent_album" -> Surface(
                fillArgb = withAlpha(palette.albumAccent, TRANSLUCENT_ALPHA),
                iconTintArgb = WHITE)

        "translucent_album_exp" -> Surface(
                fillArgb = withAlpha(palette.expressiveAlbum, TRANSLUCENT_ALPHA),
                iconTintArgb = WHITE)

        "glow_album" -> glowSurface(palette.glowAlbum)
        "glow_exp" -> glowSurface(palette.glowExpressive)

        // No icon tint: this one is a keyline around whatever the icon already is.
        "outline" -> Surface(
                strokeArgb = withAlpha(palette.albumAccent, EDGE_ALPHA),
                strokeWidthDp = 1.5f)

        "outline_exp", "outline_exp_album" -> Surface(
                strokeArgb = withAlpha(palette.expressiveAlbum, EDGE_ALPHA),
                strokeWidthDp = 1.5f,
                iconTintArgb = palette.expressiveAlbum,
                forceIconTint = true)

        "icon_exp" -> Surface(
                iconTintArgb = palette.expressiveAlbum,
                forceIconTint = true)

        "solid_theme" -> solidSurface(palette.themeAccent, SOLID_ALPHA, force = false)
        "solid_album" -> solidSurface(palette.albumAccent, SOLID_ALPHA, force = false)
        "solid_exp_album" ->
            solidSurface(palette.expressiveAlbum, SOLID_EXPRESSIVE_ALPHA, force = true)

        else -> Surface(followsFaceNeutral = true)
    }

    private fun glowSurface(glow: Int) = Surface(
            strokeArgb = withAlpha(glow, EDGE_ALPHA),
            strokeWidthDp = 2f,
            iconTintArgb = glow,
            forceIconTint = true)

    private fun solidSurface(color: Int, alpha: Int, force: Boolean) = Surface(
            fillArgb = withAlpha(color, alpha),
            iconTintArgb = contrastingIconColor(color),
            forceIconTint = force)

    /**
     * Black or white, by whichever has the higher WCAG contrast ratio against [background]
     * (crossover around 0.179 luminance) rather than by a naive half-way split - the latter left
     * white icons on medium-light pills such as the Expressive tonal surfaces, which measure
     * around 0.45 but read as light.
     */
    fun contrastingIconColor(background: Int): Int {
        val luminance = AdaptiveTextContrast.relativeLuminance(background)
        val againstWhite = 1.05f / (luminance + 0.05f)
        val againstBlack = (luminance + 0.05f) / 0.05f
        return if (againstBlack >= againstWhite) BLACK else WHITE
    }

    /** Lifts a dark colour to a lightness that survives being drawn as a 2dp hairline. */
    private fun glow(base: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(base, hsl)
        if (hsl[2] >= 0.4f) return base
        hsl[2] = 0.45f
        return ColorUtils.HSLToColor(hsl)
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
            (color and 0x00FFFFFF) or ((alpha and 0xFF) shl 24)

    private fun argb(a: Int, r: Int, g: Int, b: Int): Int =
            (a shl 24) or (r shl 16) or (g shl 8) or b

    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val BLACK = 0xFF000000.toInt()
}
