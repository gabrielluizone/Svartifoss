package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    // --- prefersDarkText: the solid-panel decision, see relativeLuminance ---

    @Test
    fun `dark text is chosen on light fills and light text on dark ones`() {
        assertTrue(AdaptiveTextContrast.prefersDarkText(0xFFFFFFFF.toInt()))
        assertTrue(AdaptiveTextContrast.prefersDarkText(0xFFEDEDED.toInt()))
        assertFalse(AdaptiveTextContrast.prefersDarkText(0xFF000000.toInt()))
        assertFalse(AdaptiveTextContrast.prefersDarkText(0xFF202020.toInt()))
    }

    /**
     * Green is far brighter than blue at the same 8-bit value. A naive average would call these two
     * the same and put dark text on the blue one, where it is unreadable.
     */
    @Test
    fun `luminance is weighted per channel, not averaged`() {
        assertTrue(AdaptiveTextContrast.prefersDarkText(0xFF00FF00.toInt()))
        assertFalse(AdaptiveTextContrast.prefersDarkText(0xFF0000FF.toInt()))
    }

    /**
     * The regression the pivot was moved for: `WatchTheme.accentForSurface` produces filled pills
     * in an HSL lightness band, and a saturated blue or purple lands there *looking* light while
     * measuring well under the old 0.34. Those are the fills Frame's artist chip is made of.
     */
    @Test
    fun `a light but low-luminance fill takes dark text`() {
        assertTrue(AdaptiveTextContrast.prefersDarkText(0xFF7A7ADB.toInt()))
        assertTrue(AdaptiveTextContrast.prefersDarkText(0xFFCF6F8F.toInt()))
    }

    /** Solved, not tuned: black and white contrast are equal here, so the constant is checkable. */
    @Test
    fun `the pivot is the luminance where black and white contrast are equal`() {
        val pivot = AdaptiveTextContrast.DARK_TEXT_LUMINANCE_PIVOT
        val againstWhite = 1.05f / (pivot + 0.05f)
        val againstBlack = (pivot + 0.05f) / 0.05f
        assertEquals(againstWhite, againstBlack, 1e-3f)
    }

    @Test
    fun `luminance stays within range for the extremes`() {
        assertEquals(0f, AdaptiveTextContrast.relativeLuminance(0xFF000000.toInt()), 1e-4f)
        assertEquals(1f, AdaptiveTextContrast.relativeLuminance(0xFFFFFFFF.toInt()), 1e-4f)
    }

    /**
     * The bug: Frame defaults to the `HIDDEN` backdrop, so the clock sits on black - and the
     * correction measured the *cover* instead, decided a bright album meant "darken the text", and
     * produced a near-black clock on a black screen. Both artwork-hiding styles must answer for
     * the field they actually paint.
     */
    @Test
    fun `a backdrop that hides the artwork is measured as black, not as the cover`() {
        listOf(PlayerBackgroundStyle.HIDDEN, PlayerBackgroundStyle.ECLIPSE).forEach { style ->
            assertEquals(
                    style.name,
                    AdaptiveTextContrast.HIDDEN_BACKDROP_LUMINANCE,
                    AdaptiveTextContrast.backdropLuminance(style) { 0.82f }!!,
                    1e-6f)
        }
    }

    /** Nothing to sample there, so the pixel reads must not happen at all. */
    @Test
    fun `a hidden backdrop never samples the artwork`() {
        var sampled = false
        AdaptiveTextContrast.backdropLuminance(PlayerBackgroundStyle.HIDDEN) {
            sampled = true
            0.82f
        }
        assertFalse(sampled)
    }

    /** Every treatment that puts artwork on screen still answers from the artwork. */
    @Test
    fun `an artwork treatment reports the measured band`() {
        listOf(
                PlayerBackgroundStyle.COVER,
                PlayerBackgroundStyle.BLUR,
                PlayerBackgroundStyle.SQUARE_SHARP,
                PlayerBackgroundStyle.EXPRESSIVE,
                PlayerBackgroundStyle.POSTER
        ).forEach { style ->
            assertEquals(
                    style.name,
                    0.82f,
                    AdaptiveTextContrast.backdropLuminance(style) { 0.82f }!!,
                    1e-6f)
        }
        assertNull(AdaptiveTextContrast.backdropLuminance(PlayerBackgroundStyle.COVER) { null })
    }

    /**
     * End to end, on the reported case: a pale cover behind an artwork-hiding backdrop must lift
     * the text, where measuring the cover would have darkened it into the black field.
     */
    @Test
    fun `a pale cover behind a hidden backdrop lifts rather than darkens`() {
        val onArtwork = AdaptiveTextContrast.adaptedLightness(
                lightness = 0.55f,
                backgroundLuminance = AdaptiveTextContrast.backdropLuminance(
                        PlayerBackgroundStyle.COVER) { 0.82f }!!)
        val onBlack = AdaptiveTextContrast.adaptedLightness(
                lightness = 0.55f,
                backgroundLuminance = AdaptiveTextContrast.backdropLuminance(
                        PlayerBackgroundStyle.HIDDEN) { 0.82f }!!)
        assertTrue("expected a darkening over pale artwork, got $onArtwork", onArtwork < 0.55f)
        assertTrue("expected no darkening on black, got $onBlack", onBlack >= 0.55f)
        assertTrue(AdaptiveTextContrast.isLegibleAgainst(onBlack, 0f))
    }
}
