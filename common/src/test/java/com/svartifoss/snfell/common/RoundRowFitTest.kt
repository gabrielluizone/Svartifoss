package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundRowFitTest {

    // Chat's own numbers on a 192dp watch: .215 of the screen for a circle, .05 for the gap and
    // for the bottom inset.
    private val screen = 192f
    private val gap = screen * .05f
    private val inset = screen * .05f
    private val preferred = (screen * .215f).coerceIn(38f, 50f)

    @Test
    fun thePairThisFaceAlreadyHadIsLeftExactlyAsItWas() {
        // Two circles fitted comfortably before any of this existed, so the fitting must be a
        // no-op there - otherwise every existing Chat screen quietly changes size.
        val fitted = RoundRowFit.fitRow(screen, 2, preferred, gap, inset)
        assertEquals(preferred, fitted.diameterDp, 0f)
        assertEquals(inset, fitted.bottomInsetDp, 0f)
        assertFalse(fitted.clipped)
    }

    @Test
    fun aThirdCircleIsRaisedUntilItClearsTheBezelWhenThereIsRoom() {
        // At the preferred diameter the outer circles of a three-wide row reach past the display
        // edge at that depth - the flat side padding alone does not catch it, because the chord
        // near the bottom of a round screen is much shorter than the screen is wide.
        assertFalse(RoundRowFit.fits(screen, 3, preferred, gap, inset))
        val fitted = RoundRowFit.fitRow(
                screen, 3, preferred, gap, inset, maxBottomInsetDp = 32f)
        assertEquals("must preserve the designed size", preferred, fitted.diameterDp, 0f)
        assertTrue("must move up", fitted.bottomInsetDp > inset)
        assertTrue(
                "must end up fitting",
                RoundRowFit.fits(
                        screen, 3, fitted.diameterDp, gap, fitted.bottomInsetDp))
        assertFalse(fitted.clipped)
    }

    @Test
    fun limitedVerticalRoomShrinksNoFurtherThanItHasTo() {
        // A row that gave up more than the geometry demanded would look arbitrary next to the
        // two-circle version of the same face.
        val maxInset = 18f
        val fitted = RoundRowFit.fitRow(
                screen, 3, preferred, gap, inset, maxBottomInsetDp = maxInset)
        assertTrue("must actually shrink", fitted.diameterDp < preferred)
        assertTrue(
                "must stay pressable",
                fitted.diameterDp >= RoundRowFit.DEFAULT_MINIMUM_DIAMETER_DP)
        assertTrue(
                "must end up fitting",
                RoundRowFit.fits(
                        screen, 3, fitted.diameterDp, gap, fitted.bottomInsetDp))
        assertFalse(
                "a larger diameter must genuinely not fit",
                RoundRowFit.fits(screen, 3, fitted.diameterDp + 0.5f, gap, maxInset))
        assertFalse(fitted.clipped)
    }

    @Test
    fun aLargerWatchKeepsTheDesignedSizeWhenVerticalRoomIsAvailable() {
        // 227dp is the other common Wear width. Its row can retain the designed diameter when the
        // composition gives the fitter enough vertical room.
        val wide = 227f
        val widePreferred = (wide * .215f).coerceIn(38f, 50f)
        val fitted = RoundRowFit.fitRow(
                wide,
                3,
                widePreferred,
                wide * .05f,
                wide * .05f,
                maxBottomInsetDp = 32f)
        assertEquals(
                widePreferred,
                fitted.diameterDp,
                0f)
        assertFalse(fitted.clipped)
    }

    @Test
    fun aSquareScreenIsNotFittedAtAll() {
        val fitted = RoundRowFit.fitRow(
                screen, 3, preferred, gap, inset, round = false)
        assertEquals(
                preferred,
                fitted.diameterDp,
                0f)
        assertEquals(inset, fitted.bottomInsetDp, 0f)
        assertFalse(fitted.clipped)
    }

    @Test
    fun oneControlHasNothingToSolve() {
        val fitted = RoundRowFit.fitRow(screen, 1, preferred, gap, inset)
        assertEquals(
                preferred,
                fitted.diameterDp,
                0f)
        assertEquals(inset, fitted.bottomInsetDp, 0f)
        assertFalse(fitted.clipped)
    }

    @Test
    fun anImpossibleRowStopsAtThePressableMinimumRatherThanVanishing() {
        // Deliberately absurd: a control too small to hit is not a better outcome than one
        // slightly clipped, so the floor holds instead of the loop running the size to nothing.
        val fitted = RoundRowFit.fitRow(
                screenDp = 120f,
                count = 3,
                preferredDiameterDp = 50f,
                gapDp = 20f,
                preferredBottomInsetDp = 8f)
        assertEquals(RoundRowFit.DEFAULT_MINIMUM_DIAMETER_DP, fitted.diameterDp, 0f)
        assertEquals(8f, fitted.bottomInsetDp, 0f)
        assertTrue(fitted.clipped)
    }

    @Test
    fun theTestIsRadialRatherThanOnTheRowsBoundingBox() {
        // The point that leaves a round display first is the one furthest along the line from the
        // screen's centre through the control's, not the corner of the row's box. A width-only
        // check passes rows that visibly clip, which is what this guards.
        val diameter = 38f
        val count = 3
        val dx = (count - 1) * (diameter + gap) / 2f
        val halfChordAtRowDepth = run {
            val r = screen / 2f
            val dy = r - (inset + diameter / 2f)
            kotlin.math.sqrt(r * r - dy * dy)
        }
        // Fits by width alone...
        assertTrue(dx + diameter / 2f <= halfChordAtRowDepth)
        // ...and still does not fit radially, which is the answer that matters.
        assertFalse(RoundRowFit.fits(screen, count, diameter, gap, inset))
    }
}
