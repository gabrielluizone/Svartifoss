package com.svartifoss.snfell.watch.view.face

import com.svartifoss.snfell.watch.view.face.TransportPressLayout.CENTRE
import com.svartifoss.snfell.watch.view.face.TransportPressLayout.MIN_WIDTH_FRACTION
import com.svartifoss.snfell.watch.view.face.TransportPressLayout.NEXT
import com.svartifoss.snfell.watch.view.face.TransportPressLayout.PREVIOUS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Expressive transport row's press behaves as one group.
 *
 * Two properties carry everything else. The row's total width never changes, which is what the
 * platform's own ButtonGroup guarantees; and a skip button's growth is paid for by the centre
 * alone, which is what makes the *other* skip button perfectly static - in a row laid out left to
 * right, nothing past the centre moves when the two of them cancel out. Both would fail silently
 * on a wrist: as a row that drifts sideways over a few presses, or as a button twitching two
 * places away from the finger.
 */
class TransportPressLayoutTest {

    /** The small-watch metrics from `expressiveMetrics`: side, cookie box, side. */
    private val base = floatArrayOf(42f, 62f, 42f)

    private fun deltas(previous: Float = 0f, centre: Float = 0f, next: Float = 0f) =
            TransportPressLayout.deltas(floatArrayOf(previous, centre, next), base)

    @Test
    fun restingCostsNothing() {
        deltas().forEach { assertEquals(0f, it, 1e-4f) }
    }

    @Test
    fun theRowKeepsItsWidthWhicheverButtonIsHeld() {
        listOf(
                deltas(previous = 1f),
                deltas(centre = 1f),
                deltas(next = 1f),
                deltas(previous = .4f),
                deltas(centre = .63f)
        ).forEach { out ->
            assertEquals(
                    "A press must take its width from the row, not add to it",
                    0f, out.sum(), 1e-3f)
        }
    }

    @Test
    fun aSkipButtonIsPaidForByTheCentreAloneSoTheOtherOneCannotMove() {
        val out = deltas(previous = 1f)
        assertEquals(TransportPressLayout.SIDE_EXPANSION_DP, out[PREVIOUS], 1e-4f)
        assertEquals(-TransportPressLayout.SIDE_EXPANSION_DP, out[CENTRE], 1e-4f)
        assertEquals(
                "The far button must be untouched: these two cancelling is the only reason " +
                        "nothing past the centre moves",
                0f, out[NEXT], 1e-4f)
    }

    @Test
    fun theTwoSideButtonsMirrorEachOther() {
        val left = deltas(previous = 1f)
        val right = deltas(next = 1f)
        assertEquals(left[PREVIOUS], right[NEXT], 1e-4f)
        assertEquals(left[CENTRE], right[CENTRE], 1e-4f)
        assertEquals(left[NEXT], right[PREVIOUS], 1e-4f)
    }

    @Test
    fun theCentreTakesHalfFromEachSideSoItStaysCentred() {
        val out = deltas(centre = 1f)
        assertEquals(TransportPressLayout.CENTRE_EXPANSION_DP, out[CENTRE], 1e-4f)
        assertEquals(
                "An uneven split would slide the progress ring while it stretches",
                out[PREVIOUS], out[NEXT], 1e-4f)
        assertTrue(out[PREVIOUS] < 0f)
    }

    @Test
    fun theSpringIsFollowedProportionally() {
        assertEquals(deltas(centre = 1f)[CENTRE] / 2f, deltas(centre = .5f)[CENTRE], 1e-4f)
    }

    @Test
    fun aValueOutsideTheSpringRangeIsClamped() {
        // A spring settling past its target reports over 1f, which must not be read as a licence
        // to keep growing.
        assertEquals(deltas(centre = 1f)[CENTRE], deltas(centre = 1.2f)[CENTRE], 1e-4f)
        assertEquals(0f, deltas(centre = -.3f)[CENTRE], 1e-4f)
    }

    @Test
    fun nothingIsSqueezedPastTheFloor() {
        // Both side buttons held at once - two fingers, which nothing asks for but nothing
        // prevents. Un-floored the centre would give up 32 of its 62dp and its progress ring
        // would close over the cookie inside it.
        val out = deltas(previous = 1f, next = 1f)
        val squeezed = base[CENTRE] + out[CENTRE]
        assertTrue(
                "The centre was squeezed to $squeezed of ${base[CENTRE]}dp",
                squeezed >= base[CENTRE] * MIN_WIDTH_FRACTION - 1e-3f)
    }

    @Test
    fun theCentreIsSqueezedAndStretchedAlongOneAxisOnly() {
        // A skip press narrows it; its own press widens it. Both are ratios the drawing is scaled
        // by, so the height - and with it the ring's vertical radius - never moves either way.
        val squeezed = TransportPressLayout.horizontalStretch(
                base[CENTRE], deltas(previous = 1f)[CENTRE])
        val stretched = TransportPressLayout.horizontalStretch(
                base[CENTRE], deltas(centre = 1f)[CENTRE])
        assertTrue("A skip press must visibly squeeze it, not leave it alone", squeezed < .9f)
        assertTrue("Its own press must visibly widen it", stretched > 1.1f)
    }

    @Test
    fun theCentreFillsExactlyWhatTheLayoutSettledOn() {
        // The ring and cookie are measured at rest and drawn scaled, so the scale has to be the
        // ratio the layout actually reached - anything else leaves the control either short of
        // the room it took, or drawn past it and into its neighbour.
        listOf(deltas(previous = 1f)[CENTRE], deltas(centre = 1f)[CENTRE], 0f).forEach { delta ->
            val stretch = TransportPressLayout.horizontalStretch(base[CENTRE], delta)
            assertEquals(base[CENTRE] + delta, base[CENTRE] * stretch, 1e-3f)
        }
    }

    @Test
    fun aRestingControlIsNotScaledAtAll() {
        // Exactly 1f, not merely close: the glyph inside the centre is counter-scaled by its
        // reciprocal, so a resting control that was fractionally off would distort its own icon.
        assertEquals(1f, TransportPressLayout.horizontalStretch(base[CENTRE], 0f), 0f)
        // And a zero-width control cannot produce a ratio at all; it must not divide by it.
        assertEquals(1f, TransportPressLayout.horizontalStretch(0f, 12f), 0f)
    }
}
