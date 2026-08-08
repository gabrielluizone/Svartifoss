package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the pure decision in [AdaptiveTextContrast]. The HSL round-trip in `adapt` delegates to
 * androidx ColorUtils and is exercised by the renderers, matching how [PaletteTransforms] is
 * tested.
 */
class AdaptiveTextContrastTest {

    /** The case the feature exists for: a dark accent on a dark cover was unreadable. */
    @Test
    fun aDarkColourOnADarkBackgroundIsLifted() {
        val adapted = AdaptiveTextContrast.adaptedLightness(lightness = 0.18f, backgroundLuminance = 0.06f)
        assertTrue("expected a lift, got $adapted", adapted > 0.18f)
        assertTrue(AdaptiveTextContrast.isLegibleAgainst(adapted, 0.06f))
    }

    /** The mirror case, which a fixed lightness floor could never handle. */
    @Test
    fun aPaleColourOnAPaleBackgroundIsDarkened() {
        val adapted = AdaptiveTextContrast.adaptedLightness(lightness = 0.88f, backgroundLuminance = 0.92f)
        assertTrue("expected a darkening, got $adapted", adapted < 0.88f)
        assertTrue(AdaptiveTextContrast.isLegibleAgainst(adapted, 0.92f))
    }

    /**
     * The most important guarantee: a colour that already reads is returned untouched, so turning
     * this on does not flatten every well-chosen accent into the same two lightnesses.
     */
    @Test
    fun anAlreadyLegibleColourIsLeftAlone() {
        assertEquals(0.82f, AdaptiveTextContrast.adaptedLightness(0.82f, 0.05f), 1e-6f)
        assertEquals(0.12f, AdaptiveTextContrast.adaptedLightness(0.12f, 0.95f), 1e-6f)
        assertTrue(AdaptiveTextContrast.isLegibleAgainst(0.82f, 0.05f))
        assertTrue(AdaptiveTextContrast.isLegibleAgainst(0.12f, 0.95f))
    }

    @Test
    fun anIllegibleColourIsReportedAsSuch() {
        assertFalse(AdaptiveTextContrast.isLegibleAgainst(0.18f, 0.06f))
        assertFalse(AdaptiveTextContrast.isLegibleAgainst(0.88f, 0.92f))
    }

    /** Never pure black or white - the line has to stay recognisably the album's colour. */
    @Test
    fun theResultStaysInsideTheLegibleBand() {
        val onBlack = AdaptiveTextContrast.adaptedLightness(0f, 0f)
        val onWhite = AdaptiveTextContrast.adaptedLightness(1f, 1f)
        assertTrue(onBlack <= AdaptiveTextContrast.MAX_LIGHTNESS)
        assertTrue(onWhite >= AdaptiveTextContrast.MIN_LIGHTNESS)
        assertTrue(onBlack > 0f)
        assertTrue(onWhite < 1f)
    }

    /** A background luminance from a corrupt/foreign source must not produce a NaN lightness. */
    @Test
    fun outOfRangeInputsClampInsteadOfEscaping() {
        val low = AdaptiveTextContrast.adaptedLightness(-2f, -3f)
        val high = AdaptiveTextContrast.adaptedLightness(5f, 7f)
        assertTrue(low in AdaptiveTextContrast.MIN_LIGHTNESS..AdaptiveTextContrast.MAX_LIGHTNESS)
        assertTrue(high in AdaptiveTextContrast.MIN_LIGHTNESS..AdaptiveTextContrast.MAX_LIGHTNESS)
    }

    /** Monotonic: a darker background never asks for darker text. */
    @Test
    fun aDarkerBackgroundNeverAsksForDarkerText() {
        var previous = AdaptiveTextContrast.adaptedLightness(0.5f, 0f)
        var background = 0.05f
        while (background < PIVOT_LIMIT) {
            val current = AdaptiveTextContrast.adaptedLightness(0.5f, background)
            assertTrue("regressed at background=$background", current >= previous - 1e-6f)
            previous = current
            background += 0.05f
        }
    }

    private companion object {
        const val PIVOT_LIMIT = AdaptiveTextContrast.PIVOT
    }
}
