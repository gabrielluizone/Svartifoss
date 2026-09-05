package com.svartifoss.snfell.common

import androidx.core.graphics.ColorUtils

/**
 * Keeps album-derived text legible against whatever the artwork actually puts behind it.
 *
 * The artist line takes its colour from the album, and a fixed lightness floor (WatchTheme's
 * `accentForText`) is only ever right for one assumed background. A dark cover with a dark accent
 * leaves the line barely visible; a pale cover with a pale accent does the same in the other
 * direction. The floor cannot fix both because it does not know what is behind the text.
 *
 * This does: given the measured luminance of the artwork *in the band the line occupies* - not the
 * whole cover, which averages away exactly the local contrast that matters - it pushes the colour's
 * lightness away from the background until the two are far enough apart, keeping hue and saturation
 * so the line still reads as the album's colour rather than as white.
 *
 * Opt-in per element (`MiscPreferences.WEAR_ARTIST_ADAPTIVE_CONTRAST`): a user who tuned a palette
 * by hand should not have it silently corrected.
 */
object AdaptiveTextContrast {

    /**
     * How far the text's lightness must sit from the background's luminance, on the 0..1 scale.
     *
     * Not a WCAG contrast ratio: those are defined on relative luminance, and HSL lightness is what
     * can actually be adjusted here without shifting hue. The value is tuned so a mid-grey backdrop
     * still yields text that reads, while staying small enough that a colour already clearly
     * separated from its background is left completely alone.
     */
    const val MIN_SEPARATION = 0.42f

    /** Below this the background counts as dark and text is lifted; above it, text is darkened. */
    const val PIVOT = 0.5f

    /** Never pure black or pure white - the point is to keep the album's colour, not to replace it. */
    const val MIN_LIGHTNESS = 0.10f
    const val MAX_LIGHTNESS = 0.95f

    /**
     * The lightness text should take against a background of [backgroundLuminance].
     *
     * Pure, so the fallback behaviour is pinned by tests: a colour that already clears
     * [MIN_SEPARATION] is returned untouched, which is what stops this from flattening every
     * carefully chosen accent into the same two values.
     */
    fun adaptedLightness(lightness: Float, backgroundLuminance: Float): Float {
        val background = backgroundLuminance.coerceIn(0f, 1f)
        val current = lightness.coerceIn(0f, 1f)
        val target = if (background < PIVOT) {
            // Dark background: lift, but only if it is not already light enough.
            maxOf(current, background + MIN_SEPARATION)
        } else {
            minOf(current, background - MIN_SEPARATION)
        }
        return target.coerceIn(MIN_LIGHTNESS, MAX_LIGHTNESS)
    }

    /**
     * Whether [lightness] already clears [MIN_SEPARATION] against [backgroundLuminance], i.e.
     * whether [adaptedLightness] would leave it alone. Exposed so callers can skip the HSL
     * round-trip entirely in the common case.
     */
    fun isLegibleAgainst(lightness: Float, backgroundLuminance: Float): Boolean {
        val background = backgroundLuminance.coerceIn(0f, 1f)
        val current = lightness.coerceIn(0f, 1f)
        return if (background < PIVOT) {
            current >= background + MIN_SEPARATION
        } else {
            current <= background - MIN_SEPARATION
        }
    }

    /**
     * [color] with its lightness adapted for [backgroundLuminance], keeping hue and saturation.
     *
     * Delegates to [ColorUtils] like the rest of the shared colour math, so the watch and the phone
     * preview produce bit-for-bit identical results; the decision itself is [adaptedLightness],
     * which is where the tests live.
     */
    /**
     * sRGB relative luminance of [color] (0..1), gamma-corrected per WCAG.
     *
     * Deliberately hand-rolled rather than `ColorUtils.calculateLuminance`: this is the input to a
     * decision the phone preview and the watch both make, and keeping it free of `android.*` means
     * the decision below is pinned by a plain JVM test instead of only being exercised on a device.
     */
    fun relativeLuminance(color: Int): Float {
        fun channel(shift: Int): Float {
            val raw = ((color shr shift) and 0xFF) / 255f
            return if (raw <= 0.03928f) raw / 12.92f else
                Math.pow(((raw + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
        }
        return 0.2126f * channel(16) + 0.7152f * channel(8) + 0.0722f * channel(0)
    }

    /**
     * Whether dark text reads better than light text on a solid [background].
     *
     * For a *known* flat fill this is the honest question, and it is a different one from [adapt]:
     * that keeps the album's hue and only nudges lightness, which is right for text sitting over
     * arbitrary artwork. A face that paints its own solid panel already controls the background
     * exactly, so it wants the maximally legible answer, not a tinted approximation of one.
     */
    fun prefersDarkText(background: Int): Boolean =
            relativeLuminance(background) > DARK_TEXT_LUMINANCE_PIVOT

    /**
     * The luminance at which black and white text are exactly equally legible, solved rather than
     * estimated: WCAG contrast against white is `1.05 / (L + 0.05)` and against black is
     * `(L + 0.05) / 0.05`, and the two are equal at `L = sqrt(0.0525) - 0.05`.
     *
     * It was 0.34, carrying the same "about equally legible" claim - which is a full band too high.
     * Every colour between the real crossover and that value took white text when black read
     * better, and the band is not obscure: it is where saturated blues, purples and reds land once
     * `WatchTheme.accentForSurface` lifts them into its filled-surface lightness range. Frame's
     * artist chip is the visible case - a light periwinkle pill carrying white text - and Split's
     * panel is the same decision on a larger surface.
     */
    const val DARK_TEXT_LUMINANCE_PIVOT = 0.17913f

    /**
     * The luminance of the ground a face paints where no artwork reaches: black.
     *
     * Both artwork-hiding treatments (`HIDDEN` and Eclipse's true-black AMOLED field) paint the
     * screen black, so this is a measurement rather than a placeholder.
     */
    const val HIDDEN_BACKDROP_LUMINANCE = 0f

    /**
     * The luminance actually behind a line of on-screen chrome, given the selected background
     * treatment.
     *
     * Everything here measures *the artwork* to decide whether text will read - which is only the
     * right question when artwork is on screen. A background style that hides it paints a black
     * field instead, and sampling the cover then answers about a picture nobody can see: a bright
     * album under Frame's default `HIDDEN` backdrop darkened the clock into near-invisibility
     * against the black it was actually sitting on, which is the exact inverse of what the
     * "Adapt to background" switch promises. The band sampler is passed lazily because in that
     * case there is nothing to sample and the pixel reads are wasted.
     *
     * It stays an approximation for the artwork case, and deliberately so: the dim scrim and the
     * authored treatments both darken what is drawn, and each does it unevenly across the screen,
     * so folding a single number in would trade one wrong answer for a subtler one. Every one of
     * those errs in the same direction - the screen is *darker* than the cover measured - so the
     * correction under-lifts rather than inverting, which is the survivable half.
     */
    fun backdropLuminance(
            style: PlayerBackgroundStyle,
            /**
             * The luminance of the flat field a style paints in place of the artwork, for the
             * styles that hide it behind something other than black.
             *
             * Returning null keeps the historical answer, which is what HIDDEN and Eclipse want -
             * they really do paint black. The flat *album* fills do not, and reporting black for
             * them would tell adaptive contrast to darken text sitting on a light album tone.
             *
             * Declared *before* [artworkBandLuminance] on purpose: that one is the trailing lambda
             * every existing caller passes without naming it, and moving it would silently rebind
             * those call sites to this parameter instead.
             */
            flatFillLuminance: (AlbumFillSlot) -> Float? = { null },
            artworkBandLuminance: () -> Float?
    ): Float? = when {
        !style.hidesArtwork -> artworkBandLuminance()
        else -> style.flatAlbumFill?.let(flatFillLuminance) ?: HIDDEN_BACKDROP_LUMINANCE
    }

    fun adapt(color: Int, backgroundLuminance: Float): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        val adapted = adaptedLightness(hsl[2], backgroundLuminance)
        if (adapted == hsl[2]) return color
        hsl[2] = adapted
        return ColorUtils.HSLToColor(hsl)
    }
}
