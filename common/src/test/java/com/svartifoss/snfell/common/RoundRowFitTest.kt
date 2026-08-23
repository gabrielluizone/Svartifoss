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
        assertEquals(
                preferred,
                RoundRowFit.circleRowDiameter(screen, 2, preferred, gap, inset),
                0f)
    }

    @Test
    fun aThirdCircleIsShrunkUntilItClearsTheBezel() {
        // At the preferred diameter the outer circles of a three-wide row reach past the display
        // edge at that depth - the flat side padding alone does not catch it, because the chord
        // near the bottom of a round screen is much shorter than the screen is wide.
        assertFalse(RoundRowFit.fits(screen, 3, preferred, gap, inset))
        val fitted = RoundRowFit.circleRowDiameter(screen, 3, preferred, gap, inset)
        assertTrue("must actually shrink", fitted < preferred)
        assertTrue("must end up fitting", RoundRowFit.fits(screen, 3, fitted, gap, inset))
        assertTrue("must stay pressable", fitted >= RoundRowFit.DEFAULT_MINIMUM_DIAMETER_DP)
    }

    @Test
    fun itShrinksNoFurtherThanItHasTo() {
        // A row that gave up more than the geometry demanded would look arbitrary next to the
        // two-circle version of the same face.
        val fitted = RoundRowFit.circleRowDiameter(screen, 3, preferred, gap, inset)
        assertFalse(
                "a larger diameter must genuinely not fit",
                RoundRowFit.fits(screen, 3, fitted + 1f, gap, inset))
    }

    @Test
    fun aLargerWatchKeepsTheDesignedSize() {
        // 227dp is the other common Wear width; three circles fit there without help, and being
        // told otherwise would shrink the row on the screens that had room for it.
        val wide = 227f
        val widePreferred = (wide * .215f).coerceIn(38f, 50f)
        assertEquals(
                widePreferred,
                RoundRowFit.circleRowDiameter(
                        wide, 3, widePreferred, wide * .05f, wide * .05f),
                0f)
    }

    @Test
    fun aSquareScreenIsNotFittedAtAll() {
        assertEquals(
                preferred,
                RoundRowFit.circleRowDiameter(screen, 3, preferred, gap, inset, round = false),
                0f)
    }

    @Test
    fun oneControlHasNothingToSolve() {
        assertEquals(
                preferred,
                RoundRowFit.circleRowDiameter(screen, 1, preferred, gap, inset),
                0f)
    }

    @Test
    fun anImpossibleRowStopsAtThePressableMinimumRatherThanVanishing() {
        // Deliberately absurd: a control too small to hit is not a better outcome than one
        // slightly clipped, so the floor holds instead of the loop running the size to nothing.
        val fitted = RoundRowFit.circleRowDiameter(
                screenDp = 120f,
                count = 3,
                preferredDiameterDp = 50f,
                gapDp = 20f,
                bottomInsetDp = 8f)
        assertEquals(RoundRowFit.DEFAULT_MINIMUM_DIAMETER_DP, fitted, 0f)
    }

    @Test
    fun theTestIsRadialRatherThanOnTheRowsBoundingBox() {
        // The point that leaves a round display first is the one furthest along the line from the
        // screen's centre through the control's, not the corner of the row's box. A width-only
        // check passes rows that visibly clip, which is what this guards.
        val diameter = 44f
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
