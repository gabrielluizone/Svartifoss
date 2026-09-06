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

    /**
     * The staircase: each element measured at its own depth, not at the block's worst one.
     *
     * This is what separates a moved block from an organised one. Below the centre the chord
     * narrows with every line, so an artist line sitting under a title has to stop short of where
     * the title may reach - and the elapsed readout under it shorter again.
     */
    @Test
    fun `each element of a low block is inset further than the one above it`() {
        val insets = RoundScreenText.lineSideInsets(
                top = 0.66f,
                elementHeights = listOf(0.09f, 0.06f, 0.05f))
        assertEquals(3, insets.size)
        assertTrue("title vs artist: ${insets[0]} vs ${insets[1]}", insets[1] > insets[0])
        assertTrue("artist vs time: ${insets[1]} vs ${insets[2]}", insets[2] > insets[1])
    }

    @Test
    fun `a block straddling the centre is bound by whichever edge reaches further out`() {
        // The top element reaches higher than the bottom one descends, so it is the narrower of
        // the two - the same rule sideInsetFor documents, applied per element.
        val insets = RoundScreenText.lineSideInsets(
                top = 0.30f,
                elementHeights = listOf(0.12f, 0.12f))
        assertTrue(insets[0] > insets[1])
    }

    @Test
    fun `one element answers exactly as the whole-block helper does`() {
        assertEquals(
                RoundScreenText.sideInsetFor(0.70f, 0.80f),
                RoundScreenText.lineSideInsets(0.70f, listOf(0.10f)).single(),
                0.0001f)
    }

    @Test
    fun `no elements means no insets rather than an exception`() {
        assertEquals(emptyList<Float>(), RoundScreenText.lineSideInsets(0.5f, emptyList()))
    }
}
