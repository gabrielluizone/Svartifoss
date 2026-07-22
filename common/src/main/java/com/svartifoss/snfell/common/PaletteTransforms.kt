package com.svartifoss.snfell.common

import androidx.core.graphics.ColorUtils

/**
 * Single source of truth for the album-accent → surface color transforms and the legacy
 * software-blur downscale. These used to be copied verbatim into the wear renderer, each Compose
 * face, and the phone preview, which let them drift apart — the overlay backdrop, the face
 * backdrop and the preview each tinted the same album accent differently. Keeping the math here
 * (consumed identically by `wear` and by `mobile`'s [WatchPreviewView]) is what stops the preview
 * from looking correct while the watch renders a mismatched color.
 *
 * The color functions delegate to [ColorUtils] so their output is bit-for-bit identical to the
 * pre-extraction code. [legacyBlurScale] is pure math with no Android dependency, so the phone
 * preview and the watch cannot diverge on blur strength.
 */
object PaletteTransforms {

    /** Saturation band the Compose faces use for their tonal containers. The album-tinted overlay
     *  backdrops (ACRYLIC/SOLID_ALBUM) reuse it so an overlay opened over a face shares its palette
     *  rather than the narrower, more desaturated chrome band ([tonalSurface]'s default). */
    const val FACE_MIN_SAT: Float = 0.30f
    const val FACE_MAX_SAT: Float = 0.90f

    /**
     * A dark, accent-tinted surface color. Saturation is clamped into [[minSat], [maxSat]] so
     * white icons/text keep contrast; [lightness] sets the tone directly. The default band matches
     * the quick-panel/overlay chrome; the faces pass a wider band for their tonal containers.
     */
    fun tonalSurface(
            accent: Int,
            lightness: Float,
            minSat: Float = 0.25f,
            maxSat: Float = 0.60f
    ): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(accent, hsl)
        hsl[1] = hsl[1].coerceIn(minSat, maxSat)
        hsl[2] = lightness
        return ColorUtils.HSLToColor(hsl)
    }

    /** Dark, still-chromatic tone used to tint the player shading overlay with an album/custom
     *  color instead of plain black. Shared by the watch renderer and the phone preview so a
     *  tinted shading looks identical in both. */
    fun shadingTone(color: Int): Int = tonalSurface(color, lightness = .13f, minSat = .25f, maxSat = .78f)

    /** Same-hue fallback for a monochromatic cover/static accent: varies only lightness, never
     *  inventing a second hue that wasn't in the source. Named spelling of a pattern already
     *  duplicated privately as sameHueTone/albumToneFallback in the wear renderer, the phone
     *  preview and the queue view model - new callers should use this instead of a fourth copy. */
    fun sameHueTone(color: Int, lightness: Float): Int = tonalSurface(color, lightness, .25f, .82f)

    /**
     * Softens an album accent for readable text and progress chrome on a dark watch face.
     *
     * This deliberately works in HSL instead of blending toward RGB gray: the old blend could
     * leave dark blue/green covers just as dark, even though their saturation had fallen enough
     * to make the remaining hue hard to perceive. Chromatic accents keep their original hue,
     * lose roughly half their saturation and are lifted to a legible tone. Near-neutral artwork
     * stays neutral instead of acquiring an arbitrary hue from an HSL saturation floor.
     */
    fun softenedAlbumAccent(accent: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(accent, hsl)
        val originalSaturation = hsl[1]
        hsl[1] = if (originalSaturation <= 0.04f) {
            0f
        } else {
            // Never increase an already-muted cover. More colourful covers retain enough chroma
            // to remain recognisably album-derived after the lightness lift.
            minOf(
                    originalSaturation,
                    (originalSaturation * 0.55f).coerceIn(0.16f, 0.50f)
            )
        }
        hsl[2] = hsl[2].coerceAtLeast(0.64f)
        return ColorUtils.HSLToColor(hsl)
    }

    /**
     * Raises a swatch's saturation to at least [saturation] (clamped to a legible band) and sets
     * its [lightness]. Used to derive the curated faces' palette from real album swatches without
     * inventing a complementary hue.
     */
    fun tunedFaceColor(color: Int, lightness: Float, saturation: Float): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[1] = maxOf(hsl[1], saturation).coerceIn(.22f, .88f)
        hsl[2] = lightness.coerceIn(.04f, .92f)
        return ColorUtils.HSLToColor(hsl)
    }

    /**
     * Downscale factor for the pre-API-31 software blur: shrinks from ~58% of the source at the
     * minimum radius to ~18% at the maximum, so the full slider travel produces a meaningfully
     * different blur strength. The watch's `createBlurredBitmapLegacy` and the phone preview's
     * blur approximation must both use this so a given radius looks the same in both.
     */
    fun legacyBlurScale(radiusPx: Float): Float {
        val strength = radiusPx.coerceIn(0f, 120f) / 120f
        return .58f - strength * .40f
    }
}
