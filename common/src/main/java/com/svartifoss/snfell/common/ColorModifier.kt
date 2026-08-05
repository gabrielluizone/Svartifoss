package com.svartifoss.snfell.common

import androidx.core.graphics.ColorUtils

/**
 * A tone filter applied *after* a [SurfaceColorTreatment] has produced its palette.
 *
 * This is deliberately a separate preference rather than more [SurfaceColorTreatment] cases: a
 * modifier is orthogonal to how the palette was derived, so "triadic but pastel" and "duotone but
 * warm" are both reachable without a combinatorial explosion of treatment values. [NONE] is the
 * identity, so an install that never touches the setting keeps rendering exactly as before.
 *
 * Modifiers never change hue *relationships* - a triad stays 120° apart after a pastel pass - they
 * only move saturation and lightness, or bias the hue of every slot by the same small amount.
 */
enum class ColorModifier {
    NONE,
    VIBRANT,
    PASTEL,
    WARM,
    COOL;

    /** Applies this modifier to a single colour. Identity for [NONE]. */
    fun apply(color: Int): Int {
        if (this == NONE) return color
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        when (this) {
            VIBRANT -> {
                // Push chroma up and pull the tone toward the mid range where saturation actually
                // reads; boosting saturation on a near-black colour changes nothing visible.
                hsl[1] = (hsl[1] * 1.45f).coerceAtMost(0.95f)
                hsl[2] = hsl[2].coerceIn(0.38f, 0.62f)
            }
            PASTEL -> {
                // Low chroma, high tone. The lightness floor is what makes it read as pastel rather
                // than simply desaturated - DESATURATED already covers the latter.
                hsl[1] = (hsl[1] * 0.55f).coerceAtMost(0.42f)
                hsl[2] = hsl[2].coerceAtLeast(0.72f)
            }
            WARM -> hsl[0] = biasHue(hsl[0], WARM_ANCHOR)
            COOL -> hsl[0] = biasHue(hsl[0], COOL_ANCHOR)
            NONE -> Unit
        }
        // A neutral source must stay neutral under every modifier: a warm bias on greyscale
        // artwork would tint chrome a colour that appears nowhere in the cover.
        if (hsl[1] < ColorHarmony.CHROMATIC_SATURATION_FLOOR && (this == WARM || this == COOL)) {
            return color
        }
        return ColorUtils.HSLToColor(hsl)
    }

    companion object {
        /** Hue the WARM bias pulls toward (amber) and how far COOL pulls (azure). */
        const val WARM_ANCHOR = 35f
        const val COOL_ANCHOR = 205f

        /** Fraction of the distance to the anchor hue a bias travels. Kept well under half so the
         *  album's own hue still dominates - this is a bias, not a recolour. */
        const val BIAS_STRENGTH = 0.28f

        /**
         * Moves [hue] toward [anchor] along the shorter arc of the hue wheel. Public and pure so
         * `common`'s JVM tests can pin the bias direction and strength without the Android runtime
         * that [apply]'s ColorUtils conversion needs.
         */
        fun biasHue(hue: Float, anchor: Float): Float {
            var delta = anchor - hue
            if (delta > 180f) delta -= 360f
            if (delta < -180f) delta += 360f
            return ((hue + delta * BIAS_STRENGTH) % 360f + 360f) % 360f
        }

        fun fromPreference(value: String?, default: ColorModifier = NONE): ColorModifier =
                when (value) {
                    "none" -> NONE
                    "vibrant" -> VIBRANT
                    "pastel" -> PASTEL
                    "warm" -> WARM
                    "cool" -> COOL
                    else -> default
                }
    }
}
