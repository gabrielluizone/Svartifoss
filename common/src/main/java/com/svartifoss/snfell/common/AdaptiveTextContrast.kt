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
    fun adapt(color: Int, backgroundLuminance: Float): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        val adapted = adaptedLightness(hsl[2], backgroundLuminance)
        if (adapted == hsl[2]) return color
        hsl[2] = adapted
        return ColorUtils.HSLToColor(hsl)
    }
}
