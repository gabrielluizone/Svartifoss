package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the rules that define each harmony. The colour-int entry points delegate to androidx
 * ColorUtils (Android runtime, unavailable here - see [PaletteTransformsTest]), which is exactly
 * why the decisions live in pure functions over HSL components: the parts that can silently make a
 * treatment look wrong are the rotation offsets, the neutral-artwork fallback and the legibility
 * clamps, and all three are verifiable on the JVM.
 */
class ColorHarmonyTest {

    @Test
    fun rotateHueWrapsAroundTheWheelInBothDirections() {
        assertEquals(10f, ColorHarmony.rotateHue(370f, 0f), 1e-4f)
        assertEquals(100f, ColorHarmony.rotateHue(340f, 120f), 1e-4f)
        assertEquals(350f, ColorHarmony.rotateHue(20f, -30f), 1e-4f)
        // A full turn is the identity, so an analogous pair is symmetric about the source.
        assertEquals(200f, ColorHarmony.rotateHue(200f, 360f), 1e-4f)
    }

    @Test
    fun triadicOffsetsAreEvenlySpacedAndComplementIsOpposite() {
        // These constants define the treatments; drifting them silently changes every palette.
        assertEquals(120f, ColorHarmony.TRIADIC_SECOND_ROTATION, 1e-4f)
        assertEquals(240f, ColorHarmony.TRIADIC_THIRD_ROTATION, 1e-4f)
        assertEquals(180f, ColorHarmony.COMPLEMENTARY_ROTATION, 1e-4f)

        val source = 30f
        val second = ColorHarmony.rotateHue(source, ColorHarmony.TRIADIC_SECOND_ROTATION)
        val third = ColorHarmony.rotateHue(source, ColorHarmony.TRIADIC_THIRD_ROTATION)
        assertEquals(120f, ColorHarmony.shortestHueDistance(source, second), 1e-4f)
        assertEquals(120f, ColorHarmony.shortestHueDistance(second, third), 1e-4f)
        assertEquals(120f, ColorHarmony.shortestHueDistance(third, source), 1e-4f)
    }

    @Test
    fun shortestHueDistanceTakesTheShorterArc() {
        assertEquals(20f, ColorHarmony.shortestHueDistance(350f, 10f), 1e-4f)
        assertEquals(180f, ColorHarmony.shortestHueDistance(0f, 180f), 1e-4f)
        assertEquals(0f, ColorHarmony.shortestHueDistance(90f, 90f), 1e-4f)
    }

    @Test
    fun nearNeutralSourcesDegradeToTonal() {
        assertTrue(ColorHarmony.degradesToTonal(0f))
        assertTrue(ColorHarmony.degradesToTonal(ColorHarmony.CHROMATIC_SATURATION_FLOOR - 0.01f))
        assertFalse(ColorHarmony.degradesToTonal(ColorHarmony.CHROMATIC_SATURATION_FLOOR))
        assertFalse(ColorHarmony.degradesToTonal(0.8f))
    }

    @Test
    fun clampSaturationLeavesNeutralsNeutral() {
        // The whole point: a greyscale cover must not acquire a tint it never had.
        assertEquals(0f, ColorHarmony.clampSaturation(0f), 1e-4f)
        assertEquals(0.05f, ColorHarmony.clampSaturation(0.05f), 1e-4f)
    }

    @Test
    fun clampSaturationPullsChromaticValuesIntoTheLegibleBand() {
        assertEquals(ColorHarmony.MIN_SAT, ColorHarmony.clampSaturation(0.12f), 1e-4f)
        assertEquals(ColorHarmony.MAX_SAT, ColorHarmony.clampSaturation(1f), 1e-4f)
        assertEquals(0.5f, ColorHarmony.clampSaturation(0.5f), 1e-4f)
    }

    @Test
    fun clampPrimaryLightnessRescuesBlackAndBlownOutCovers() {
        assertEquals(ColorHarmony.PRIMARY_MIN_LIGHTNESS, ColorHarmony.clampPrimaryLightness(0f), 1e-4f)
        assertEquals(ColorHarmony.PRIMARY_MAX_LIGHTNESS, ColorHarmony.clampPrimaryLightness(1f), 1e-4f)
        assertEquals(0.5f, ColorHarmony.clampPrimaryLightness(0.5f), 1e-4f)
    }

    @Test
    fun duotoneNeedsTwoDistinctChromaticSources() {
        // Far enough apart: a real pair.
        assertTrue(ColorHarmony.isDuotonePair(0.6f, 200f, 0.6f, 40f))
        // Same hue: nothing to show as a second colour.
        assertFalse(ColorHarmony.isDuotonePair(0.6f, 200f, 0.6f, 205f))
        // Exactly at the gap threshold still counts.
        assertTrue(ColorHarmony.isDuotonePair(
                0.6f, 200f, 0.6f, 200f + ColorHarmony.MIN_DUOTONE_HUE_GAP))
    }

    @Test
    fun duotoneRejectsGreyPartnersWhoseHueIsNumericOnly() {
        // A grey carries a hue value but no visible colour; pairing it would fake a duotone.
        assertFalse(ColorHarmony.isDuotonePair(0.6f, 200f, 0.01f, 40f))
        assertFalse(ColorHarmony.isDuotonePair(0.01f, 200f, 0.6f, 40f))
    }

    @Test
    fun secondaryIsDarkerThanTertiarySoTheLadderReadsAsHierarchy() {
        assertTrue(ColorHarmony.SECONDARY_LIGHTNESS < ColorHarmony.TERTIARY_LIGHTNESS)
    }

    @Test
    fun aHueShiftPreservesEachHarmonysInternalAngles() {
        // The whole point of turning all three slots together: a triad shifted by any amount is
        // still a triad, so the primary can vary without the palette losing its relationships.
        val source = 30f
        val shift = 75f
        val second = ColorHarmony.rotateHue(source, ColorHarmony.TRIADIC_SECOND_ROTATION)
        val shiftedSource = ColorHarmony.rotateHue(source, shift)
        val shiftedSecond = ColorHarmony.rotateHue(second, shift)
        assertEquals(
                ColorHarmony.shortestHueDistance(source, second),
                ColorHarmony.shortestHueDistance(shiftedSource, shiftedSecond),
                1e-4f)
    }

    @Test
    fun aFullTurnLeavesTheHueWhereItStarted() {
        assertEquals(200f, ColorHarmony.rotateHue(200f, 360f), 1e-4f)
        assertEquals(0f, ColorHarmony.shortestHueDistance(200f, ColorHarmony.rotateHue(200f, 360f)), 1e-4f)
    }
}
