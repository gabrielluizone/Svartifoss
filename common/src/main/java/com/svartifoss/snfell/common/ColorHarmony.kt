package com.svartifoss.snfell.common

import androidx.core.graphics.ColorUtils

/**
 * Derives a full three-colour surface palette from the album accent, for the treatments that ask
 * for *related* colours rather than one swatch repeated across the whole UI.
 *
 * Lives in `common` for the same reason as [PaletteTransforms]: the watch renderer and the phone's
 * `WatchPreviewView` both resolve palettes, and a copy in each is exactly how the preview ends up
 * showing a colour the watch never renders. Callers should go through [SurfacePaletteResolver]
 * rather than these functions directly, so treatment selection and the modifier pass stay in one
 * place too.
 *
 * The hue/saturation decisions are split out as pure functions over HSL components ([rotateHue],
 * [shortestHueDistance], [clampSaturation], [degradesToTonal]) with the colour-int entry points as
 * thin wrappers. Only the wrappers touch [ColorUtils], which needs the Android runtime — so the
 * rules that actually define a harmony stay covered by `common`'s JVM tests instead of being
 * verifiable only on a device, the same limitation [PaletteTransformsTest] records.
 *
 * Two invariants hold for every function here:
 *
 *  - **Hue rotation is only meaningful on a chromatic source.** Rotating the hue of a near-grey
 *    cover produces another grey, so a "triadic" palette off a black-and-white album would be three
 *    identical greys - visually indistinguishable from Normal, and a bug report waiting to happen.
 *    Every harmony therefore falls back to a lightness ladder ([monochromeTonal]) below
 *    [CHROMATIC_SATURATION_FLOOR], which is the same instinct [PaletteTransforms.softenedAlbumAccent]
 *    already applies when it refuses to invent a hue for near-neutral artwork.
 *  - **Output stays legible on a dark face.** Saturation and lightness are clamped into the same
 *    bands the existing transforms use, so a harmony can never hand the renderer a colour that
 *    white text and icons cannot sit on.
 */
object ColorHarmony {

    /**
     * Below this HSL saturation an album accent carries no usable hue, so rotating it is a no-op
     * that silently collapses a harmony into a flat palette. Matches the band
     * [PaletteTransforms.softenedAlbumAccent] treats as "near-neutral artwork".
     */
    const val CHROMATIC_SATURATION_FLOOR: Float = 0.10f

    /** Legibility band shared by every derived colour - wide enough to stay album-derived, tight
     *  enough that white chrome keeps contrast on top of it. */
    const val MIN_SAT: Float = 0.28f
    const val MAX_SAT: Float = 0.86f

    /**
     * The saturation a colourless album accent is *promoted* to before anything else touches it.
     *
     * Chosen as `WatchTheme.accentForSurface`'s own floor, which is the middle of the band the
     * filled surfaces were already clamping to - so promotion leaves the pills and buttons looking
     * essentially as they did, and changes the elements that were being left out.
     */
    const val NEUTRAL_ACCENT_SATURATION: Float = 0.45f

    /** Tone steps for the secondary/tertiary slots. The primary keeps the source's own lightness
     *  (lifted into a legible range) so the accent the user already recognises does not shift. */
    const val SECONDARY_LIGHTNESS: Float = 0.46f
    const val TERTIARY_LIGHTNESS: Float = 0.68f

    /** Band a primary's own lightness is lifted into: very dark or blown-out covers would
     *  otherwise produce an accent no chrome can sit on. Matches what the curated faces already
     *  accept from [PaletteTransforms.tunedFaceColor]. */
    const val PRIMARY_MIN_LIGHTNESS: Float = 0.34f
    const val PRIMARY_MAX_LIGHTNESS: Float = 0.78f

    /** Minimum hue separation (degrees) for two swatches to be worth showing as a duotone pair. */
    const val MIN_DUOTONE_HUE_GAP: Float = 18f

    /** Rotation offsets that define each harmony, in degrees around the hue wheel. */
    const val COMPLEMENTARY_ROTATION: Float = 180f
    const val TRIADIC_SECOND_ROTATION: Float = 120f
    const val TRIADIC_THIRD_ROTATION: Float = 240f
    const val ANALOGOUS_ROTATION: Float = 32f

    /** How much the complement's saturation is pulled back. A full-strength opposite hue behind
     *  the primary reads as two competing accents rather than an accent plus a highlight. */
    const val COMPLEMENT_SATURATION_SCALE: Float = 0.72f

    data class Triad(val primary: Int, val secondary: Int, val tertiary: Int)

    // ---------------------------------------------------------------------------------------
    // Pure math. No Android dependency, so `common`'s JVM tests cover the actual harmony rules.
    // ---------------------------------------------------------------------------------------

    /** [hue] moved [degrees] around the wheel, normalised to 0..360. */
    fun rotateHue(hue: Float, degrees: Float): Float = ((hue + degrees) % 360f + 360f) % 360f

    /** Shortest angular distance between two hues, in degrees (0..180). */
    fun shortestHueDistance(hueA: Float, hueB: Float): Float {
        val raw = Math.abs(rotateHue(hueA, 0f) - rotateHue(hueB, 0f))
        return if (raw > 180f) 360f - raw else raw
    }

    /** True when a source at this saturation cannot carry a visible hue rotation. */
    fun degradesToTonal(saturation: Float): Boolean = saturation < CHROMATIC_SATURATION_FLOOR

    /**
     * Clamps saturation into the legible band, except for neutral sources: forcing a grey up to
     * [MIN_SAT] would give a black-and-white cover an arbitrary tint that appears nowhere in the
     * artwork.
     */
    fun clampSaturation(saturation: Float): Float =
            if (degradesToTonal(saturation)) saturation else saturation.coerceIn(MIN_SAT, MAX_SAT)

    /**
     * The saturation a source at [saturation] carries once a colourless accent has been promoted.
     *
     * **This is the one place the "never invent chroma" rule above is deliberately overridden, and
     * it is overridden because leaving it in place is what produced the bug.** A black-and-white
     * cover has no hue; HSL still has to return one and returns the undefined 0°, which is red.
     * The rules here left that alone - correctly, on their own terms - while 97 call sites across
     * the two apps clamped the same accent up to one of *thirteen* different saturation floors
     * before painting it. `tonalSurface` and `tunedFaceColor` also overwrite lightness outright, so
     * on a monochrome cover every album-derived surface was a function of that single undefined
     * number, each drawing it at a different strength. Measured on one screen: the artist line at
     * saturation 0.00 (`accentForText` floors lightness and never touches saturation), the quick
     * panel at 0.33, the transport buttons at 0.38, and the phone's palette dots at 0.45. Four
     * answers, one colour, and no possible way for a swatch to report them all.
     *
     * Promoting once, at the source, is what collapses that: the accent arrives with a real hue and
     * a real saturation, every floor below [NEUTRAL_ACCENT_SATURATION] becomes a no-op, and the
     * remaining per-surface variation is the same variation a colourful album already gets by
     * design. The elements that were being left out - the artist line, the clock, the dots - now
     * carry the same colour as the ones that were not.
     *
     * The invariant at the top of this file is not violated: rotation is still meaningless on a
     * neutral source, and nothing below the floor is rotated. What changed is that a neutral source
     * no longer reaches those functions.
     */
    fun promotedNeutralSaturation(saturation: Float): Float =
            if (degradesToTonal(saturation)) NEUTRAL_ACCENT_SATURATION else saturation

    /** Lifts a primary's own lightness into the band chrome can sit on. */
    fun clampPrimaryLightness(lightness: Float): Float =
            lightness.coerceIn(PRIMARY_MIN_LIGHTNESS, PRIMARY_MAX_LIGHTNESS)

    /**
     * Whether two sources are far enough apart in hue to read as a genuine duotone pair.
     * A near-grey on either side counts as indistinguishable: greys have a hue value but no
     * perceivable hue, so pairing two of them would produce a "duotone" of one colour.
     */
    fun isDuotonePair(saturationA: Float, hueA: Float, saturationB: Float, hueB: Float): Boolean {
        if (degradesToTonal(saturationA) || degradesToTonal(saturationB)) return false
        return shortestHueDistance(hueA, hueB) >= MIN_DUOTONE_HUE_GAP
    }

    // ---------------------------------------------------------------------------------------
    // Colour-int entry points. Thin ColorUtils wrappers over the rules above.
    // ---------------------------------------------------------------------------------------

    /** True when [color] has enough chroma for a hue rotation to produce a visibly different hue. */
    fun isChromatic(color: Int): Boolean = !degradesToTonal(hslOf(color)[1])

    /**
     * [color] with a colourless accent promoted to a real one - see [promotedNeutralSaturation].
     *
     * A no-op for every album that has colour in it, which is the common case and the reason this
     * can sit at the top of the pipeline without restating anything.
     *
     * Saturation only: the source keeps its own hue (whatever HSL reported for it, which *is* the
     * colour the surfaces were already inventing) and its own lightness, so a triad promoted slot
     * by slot still reads as the tonal ladder it was.
     */
    fun promoteNeutralAccent(color: Int): Int {
        val hsl = hslOf(color)
        val saturation = promotedNeutralSaturation(hsl[1])
        if (saturation == hsl[1]) return color
        hsl[1] = saturation
        return ColorUtils.HSLToColor(hsl)
    }

    /**
     * Primary plus its opposite hue. The complement lands on the tertiary slot rather than the
     * secondary: the secondary is used for large fills by several faces, and a full-strength
     * opposite hue there competes with the primary instead of accenting it.
     */
    fun complementary(primary: Int): Triad = harmonyOrTonal(primary) {
        Triad(
                primary = legible(primary, clampPrimaryLightness(hslOf(primary)[2])),
                secondary = rotated(primary, COMPLEMENTARY_ROTATION, SECONDARY_LIGHTNESS,
                        COMPLEMENT_SATURATION_SCALE),
                tertiary = rotated(primary, COMPLEMENTARY_ROTATION, TERTIARY_LIGHTNESS)
        )
    }

    /** Three hues 120° apart - the most colourful treatment, for covers with a strong single hue. */
    fun triadic(primary: Int): Triad = harmonyOrTonal(primary) {
        Triad(
                primary = legible(primary, clampPrimaryLightness(hslOf(primary)[2])),
                secondary = rotated(primary, TRIADIC_SECOND_ROTATION, SECONDARY_LIGHTNESS),
                tertiary = rotated(primary, TRIADIC_THIRD_ROTATION, TERTIARY_LIGHTNESS)
        )
    }

    /**
     * Neighbouring hues (±32°). The subtlest harmony: the palette still reads as "the album's
     * colour" while giving progress/controls enough separation to not look flat.
     */
    fun analogous(primary: Int): Triad = harmonyOrTonal(primary) {
        Triad(
                primary = legible(primary, clampPrimaryLightness(hslOf(primary)[2])),
                secondary = rotated(primary, -ANALOGOUS_ROTATION, SECONDARY_LIGHTNESS),
                tertiary = rotated(primary, ANALOGOUS_ROTATION, TERTIARY_LIGHTNESS)
        )
    }

    /**
     * One hue, three tones. Also the fallback every harmony degrades to on near-neutral artwork,
     * which is why it must never rotate: on a greyscale cover this is the honest result, and on a
     * chromatic one it is a deliberate Material-You-style tonal ladder.
     */
    fun monochromeTonal(primary: Int): Triad = Triad(
            primary = legible(primary, clampPrimaryLightness(hslOf(primary)[2])),
            secondary = legible(primary, SECONDARY_LIGHTNESS),
            tertiary = legible(primary, TERTIARY_LIGHTNESS)
    )

    /**
     * Two *real* album swatches rather than a synthesized hue, so the gradient endpoints are
     * colours that actually appear in the artwork. [secondarySource] is whatever the palette
     * extractor found as a companion colour; when it is null or too close to the primary to read
     * as a second colour, this degrades to a same-hue tonal pair instead of faking a hue - the
     * same rule [PaletteTransforms.sameHueTone] exists for.
     */
    fun duotone(primary: Int, secondarySource: Int?): Triad {
        if (secondarySource == null) return monochromeTonal(primary)
        val a = hslOf(primary)
        val b = hslOf(secondarySource)
        if (!isDuotonePair(a[1], a[0], b[1], b[0])) return monochromeTonal(primary)
        return Triad(
                primary = legible(primary, clampPrimaryLightness(a[2])),
                secondary = legible(secondarySource, SECONDARY_LIGHTNESS),
                tertiary = legible(secondarySource, TERTIARY_LIGHTNESS)
        )
    }

    /** Shortest angular distance between two colours' hues, in degrees (0..180); 0 when either
     *  side is a near-grey, whose numeric hue carries no perceivable colour. */
    fun hueDistance(a: Int, b: Int): Float {
        val hslA = hslOf(a)
        val hslB = hslOf(b)
        if (degradesToTonal(hslA[1]) || degradesToTonal(hslB[1])) return 0f
        return shortestHueDistance(hslA[0], hslB[0])
    }

    /**
     * [color] with its hue turned [degrees] around the wheel, keeping saturation and lightness.
     *
     * Unlike [rotated] this is a plain hue turn with no re-clamping: it runs *after* a treatment
     * has already placed every slot in its legible band, so re-clamping would undo the tonal
     * ladder the treatment just built. A near-neutral colour is returned untouched - rotating a
     * grey produces the same grey, and forcing saturation into it would invent a tint the cover
     * never had (the invariant [clampSaturation] documents).
     */
    fun hueShifted(color: Int, degrees: Float): Int {
        if (degrees == 0f) return color
        val hsl = hslOf(color)
        if (degradesToTonal(hsl[1])) return color
        hsl[0] = rotateHue(hsl[0], degrees)
        return ColorUtils.HSLToColor(hsl)
    }

    /** Runs [build] only when [primary] can carry a hue rotation; otherwise degrades to tones. */
    private inline fun harmonyOrTonal(primary: Int, build: () -> Triad): Triad =
            if (isChromatic(primary)) build() else monochromeTonal(primary)

    private fun hslOf(color: Int): FloatArray {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        return hsl
    }

    /** [color] rotated [degrees] around the hue wheel, then forced into the legible band. */
    private fun rotated(
            color: Int,
            degrees: Float,
            lightness: Float,
            saturationScale: Float = 1f
    ): Int {
        val hsl = hslOf(color)
        hsl[0] = rotateHue(hsl[0], degrees)
        hsl[1] = (hsl[1] * saturationScale).coerceIn(MIN_SAT, MAX_SAT)
        hsl[2] = lightness
        return ColorUtils.HSLToColor(hsl)
    }

    /** [color] at [lightness], with saturation clamped into the legible band and its hue kept. */
    private fun legible(color: Int, lightness: Float): Int {
        val hsl = hslOf(color)
        hsl[1] = clampSaturation(hsl[1])
        hsl[2] = lightness
        return ColorUtils.HSLToColor(hsl)
    }
}
