package com.svartifoss.snfell.common

/**
 * The single place a [SurfaceColorTreatment] plus a [ColorModifier] turn into the three colours a
 * surface actually paints with.
 *
 * Before the harmony treatments existed, the watch's `MainActivity.resolveSurfacePalette` and the
 * phone's `WatchPreviewView` each had their own `when` over the treatment enum - two short blocks
 * that happened to agree. Adding five more cases to both is precisely the drift this module exists
 * to prevent (the same argument [PaletteTransforms] records), so both sides now call [derive] and
 * keep only their own plumbing: reading preferences, and painting the result.
 */
object SurfacePaletteResolver {

    /**
     * @param treatment the treatment already resolved against the face-wide policy - [derive]
     *   never sees [SurfaceColorTreatment.FOLLOW] from a correct caller, and treats it as
     *   [SurfaceColorTreatment.EXPRESSIVE] if it does.
     * @param rawPrimary the album accent as extracted, before any softening.
     * @param rawSecondary companion swatch; for [SurfaceColorTreatment.DUOTONE] this is the second
     *   real artwork colour, so passing a synthesized value there would defeat the treatment.
     * @param fixedColor the user's Normal colour, already resolved through its fallback chain.
     * @param hueShiftDegrees turns the whole derived palette around the hue wheel, *including the
     *   primary*. Every treatment deliberately anchors its primary to the album's own hue (only the
     *   companion slots rotate), so without this the main accent never changes no matter which
     *   treatment is picked - this is the control that varies it, while keeping the harmony's
     *   internal angles intact because all three slots turn together. Skipped for
     *   [SurfaceColorTreatment.NORMAL]: that colour is one the user picked by hand, so rotating it
     *   would silently render a different colour than the one shown in the picker.
     */
    fun derive(
            treatment: SurfaceColorTreatment,
            modifier: ColorModifier,
            rawPrimary: Int,
            rawSecondary: Int,
            rawTertiary: Int,
            fixedColor: Int,
            hueShiftDegrees: Float = 0f
    ): ColorHarmony.Triad {
        val base = when (treatment) {
            SurfaceColorTreatment.NORMAL -> ColorHarmony.Triad(
                    fixedColor,
                    PaletteTransforms.sameHueTone(fixedColor, .42f),
                    PaletteTransforms.sameHueTone(fixedColor, .68f))

            SurfaceColorTreatment.DESATURATED -> ColorHarmony.Triad(
                    PaletteTransforms.softenedAlbumAccent(rawPrimary),
                    PaletteTransforms.softenedAlbumAccent(rawSecondary),
                    PaletteTransforms.softenedAlbumAccent(rawTertiary))

            SurfaceColorTreatment.EXPRESSIVE,
            SurfaceColorTreatment.FOLLOW ->
                ColorHarmony.Triad(rawPrimary, rawSecondary, rawTertiary)

            SurfaceColorTreatment.COMPLEMENTARY -> ColorHarmony.complementary(rawPrimary)
            SurfaceColorTreatment.TRIADIC -> ColorHarmony.triadic(rawPrimary)
            SurfaceColorTreatment.ANALOGOUS -> ColorHarmony.analogous(rawPrimary)
            SurfaceColorTreatment.MONOCHROME -> ColorHarmony.monochromeTonal(rawPrimary)
            SurfaceColorTreatment.DUOTONE -> ColorHarmony.duotone(rawPrimary, rawSecondary)
        }

        // A hand-picked Normal colour is never turned - see the hueShiftDegrees doc.
        val shift = if (treatment.isAlbumDerived) hueShiftDegrees else 0f
        val shifted = if (shift == 0f) base else ColorHarmony.Triad(
                ColorHarmony.hueShifted(base.primary, shift),
                ColorHarmony.hueShifted(base.secondary, shift),
                ColorHarmony.hueShifted(base.tertiary, shift))

        if (modifier == ColorModifier.NONE) return shifted
        return ColorHarmony.Triad(
                modifier.apply(shifted.primary),
                modifier.apply(shifted.secondary),
                modifier.apply(shifted.tertiary))
    }
}
