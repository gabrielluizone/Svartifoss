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
     * @param multiColor whether the surface gets a palette at all. When false every slot is the
     *   primary, so the screen paints one colour - the album's own under an album-derived
     *   treatment, the picked one under [SurfaceColorTreatment.NORMAL]. Applied *last*, after the
     *   treatment, the hue shift and the modifier, so it collapses whatever those produced rather
     *   than bypassing them: the primary a harmony arrives at is still that harmony's primary, and
     *   turning the palette off does not silently change the main colour as well as removing the
     *   companions. Historical key name (`wear_normal_color_multi`) - it began as a Normal-only
     *   switch, kept so existing values and backups migrate losslessly.
     */
    fun derive(
            treatment: SurfaceColorTreatment,
            modifier: ColorModifier,
            rawPrimary: Int,
            rawSecondary: Int,
            rawTertiary: Int,
            fixedColor: Int,
            hueShiftDegrees: Float = 0f,
            multiColor: Boolean = true
    ): ColorHarmony.Triad {
        val base = when (treatment) {
            SurfaceColorTreatment.NORMAL -> if (multiColor) {
                ColorHarmony.Triad(
                        fixedColor,
                        PaletteTransforms.sameHueTone(fixedColor, .42f),
                        PaletteTransforms.sameHueTone(fixedColor, .68f))
            } else {
                ColorHarmony.Triad(fixedColor, fixedColor, fixedColor)
            }

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

        // A hand-picked Normal colour is never turned - see the hueShiftDegrees doc. The
        // treatment's own rotation rides on the same pass as the user's shift, so a harmony turns
        // as one rigid triad and keeps its internal angles - see
        // SurfaceColorTreatment.primaryRotationDegrees for why the primary turns at all.
        val shift = if (treatment.isAlbumDerived) {
            hueShiftDegrees + treatment.primaryRotationDegrees
        } else {
            0f
        }
        val shifted = if (shift == 0f) base else ColorHarmony.Triad(
                ColorHarmony.hueShifted(base.primary, shift),
                ColorHarmony.hueShifted(base.secondary, shift),
                ColorHarmony.hueShifted(base.tertiary, shift))

        val modified = if (modifier == ColorModifier.NONE) shifted else ColorHarmony.Triad(
                modifier.apply(shifted.primary),
                modifier.apply(shifted.secondary),
                modifier.apply(shifted.tertiary))

        // Last, deliberately - see the multiColor doc.
        return flatten(modified, multiColor)
    }

    /**
     * Drops a triad to its primary when the user has turned the palette off.
     *
     * Split out as a pure function over an already-derived triad so it can be pinned by a JVM test:
     * [derive] itself goes through androidx ColorUtils and is therefore untestable off-device (the
     * constraint `ColorHarmonyTest` documents), while *this* decision - collapse to the primary,
     * never to a companion, and only when asked - is the part that can go quietly wrong.
     *
     * Note it makes the harmony treatments collapse into each other. That is correct: with no
     * companion slots there is nothing left for a harmony to be, and one colour is what was asked
     * for.
     */
    fun flatten(triad: ColorHarmony.Triad, multiColor: Boolean): ColorHarmony.Triad =
            if (multiColor) triad
            else ColorHarmony.Triad(triad.primary, triad.primary, triad.primary)
}
