package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundScreenTextTest {

    @Test
    fun `chord is widest at the centre and empty at the edges`() {
        assertEquals(0.5f, RoundScreenText.halfChordAt(0.5f), 0.0001f)
        assertEquals(0f, RoundScreenText.halfChordAt(0f), 0.0001f)
        assertEquals(0f, RoundScreenText.halfChordAt(1f), 0.0001f)
    }

    @Test
    fun `chord is symmetric about the centre`() {
        assertEquals(
                RoundScreenText.halfChordAt(0.25f),
                RoundScreenText.halfChordAt(0.75f),
                0.0001f)
    }

    /** Out-of-range depths are clamped rather than producing a NaN from a negative square root. */
    @Test
    fun `depths outside the screen clamp instead of returning NaN`() {
        assertEquals(0f, RoundScreenText.halfChordAt(-0.5f), 0.0001f)
        assertEquals(0f, RoundScreenText.halfChordAt(1.5f), 0.0001f)
    }

    /** The whole point of the helper: a second line sits deeper, so it must inset further. */
    @Test
    fun `a wrapped block insets further than a single line at the same top`() {
        val oneLine = RoundScreenText.sideInsetForLines(0.75f, 0.083f, 1)
        val twoLines = RoundScreenText.sideInsetForLines(0.75f, 0.083f, 2)
        assertTrue("two lines should inset more than one: $oneLine vs $twoLines",
                twoLines > oneLine)
    }

    /**
     * A block straddling the centre is narrowest at whichever edge is further out - here the top -
     * so taking the bottom edge alone would under-inset it.
     */
    @Test
    fun `a block reaching higher than it descends is constrained by its top edge`() {
        val inset = RoundScreenText.sideInsetFor(top = 0.20f, bottom = 0.55f, margin = 0f)
        val topInset = 0.5f - RoundScreenText.halfChordAt(0.20f)
        assertEquals(topInset, inset, 0.0001f)
    }

    @Test
    fun `inset never collapses the column even for a block below the glass`() {
        val inset = RoundScreenText.sideInsetFor(top = 0.95f, bottom = 1.2f)
        assertTrue("inset should be clamped, was $inset", inset <= 0.34f)
        assertTrue("column should keep some width", 1f - 2f * inset > 0f)
    }

    @Test
    fun `line budget shrinks as the band starts lower`() {
        val high = RoundScreenText.linesThatFit(top = 0.55f, lineHeight = 0.083f, maxLines = 5)
        val low = RoundScreenText.linesThatFit(top = 0.80f, lineHeight = 0.083f, maxLines = 5)
        assertTrue("a band starting higher should fit at least as many lines: $high vs $low",
                high >= low)
        assertTrue("at least one line is always allowed", low >= 1)
    }

    @Test
    fun `line budget never exceeds the requested maximum`() {
        assertEquals(1, RoundScreenText.linesThatFit(0.4f, 0.083f, maxLines = 1))
        assertTrue(RoundScreenText.linesThatFit(0.4f, 0.083f, maxLines = 2) <= 2)
    }

    @Test
    fun `equivalent insets stop the measure loop`() {
        assertTrue(RoundScreenText.insetsAreEquivalent(0.2000f, 0.2010f))
        assertTrue(!RoundScreenText.insetsAreEquivalent(0.20f, 0.25f))
    }
}
