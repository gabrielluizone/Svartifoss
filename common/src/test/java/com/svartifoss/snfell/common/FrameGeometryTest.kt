package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class FrameGeometryTest {
    private val epsilon = .0001f

    @Test
    fun `frame card is centred and contains all of its content`() {
        val frame = FaceGeometry.Frame

        assertEquals(
                .5f,
                (frame.CARD_TOP_FRACTION + frame.CARD_BOTTOM_FRACTION) / 2f,
                epsilon)
        assertTrue(frame.ART_TOP_FRACTION > frame.CARD_TOP_FRACTION)
        assertTrue(frame.TITLE_TOP_FRACTION > frame.ART_TOP_FRACTION)
        assertTrue(frame.ARTWORK_TOP_FRACTION > frame.TITLE_TOP_FRACTION)
        assertTrue(frame.ARTWORK_BOTTOM_FRACTION < frame.CARD_BOTTOM_FRACTION)
    }

    @Test
    fun `frame centre gestures leave the card side bands to quadrant actions`() {
        val frame = FaceGeometry.Frame
        val touchLeft = (1f - frame.CENTER_REGION_FRACTION) / 2f
        val cardLeft = frame.CARD_INSET_FRACTION

        // A side tap within the card must still reach FourWayTouchLayout rather than its invisible
        // centre play/pause region. The remaining 15.5% on each card side is large enough to be
        // intentional on a round watch, while the 46% centre remains an 88 dp target at 192 dp.
        assertTrue(touchLeft > cardLeft)
        assertEquals(.46f, frame.CENTER_REGION_FRACTION, epsilon)
        assertTrue(frame.CENTER_REGION_FRACTION * REFERENCE_WATCH_DP >= 48f)
    }

    /**
     * The reported defect: the cover reached to within half its own side inset of the card's
     * bottom, which on a rounded card means *past* the corner arc. The watch clips its card
     * content and shaved the cover's corners off; the Canvas preview does not clip and drew it
     * outside the card entirely.
     */
    @Test
    fun `the cover keeps the same margin from the card on every side`() {
        val frame = FaceGeometry.Frame

        assertEquals(
                frame.CONTENT_INSET_FRACTION,
                frame.CARD_BOTTOM_FRACTION - frame.ARTWORK_BOTTOM_FRACTION,
                epsilon)
        assertEquals(
                frame.CONTENT_INSET_FRACTION,
                frame.ART_TOP_FRACTION - frame.CARD_TOP_FRACTION,
                epsilon)
    }

    /**
     * The cover's corners have to look parallel to the card's, not merely rounded: an inner box
     * inset by `i` inside a corner of radius `r` is concentric with it at `r - i`. It used to take
     * a fraction of its own shorter side, which on a wide crop came out roughly a third of that.
     */
    @Test
    fun `the cover's corner is concentric with the card's`() {
        val frame = FaceGeometry.Frame
        val cardWidth = 1f - frame.CARD_INSET_FRACTION * 2f
        val cardHeight = frame.CARD_BOTTOM_FRACTION - frame.CARD_TOP_FRACTION
        val cardCorner = minOf(cardWidth, cardHeight) * frame.CARD_CORNER_FRACTION

        assertEquals(
                cardCorner - frame.CONTENT_INSET_FRACTION,
                frame.artworkCornerFraction(),
                epsilon)
    }

    /** A corner radius past half the cover's height would invert it. */
    @Test
    fun `the cover's corner never exceeds half its height`() {
        val frame = FaceGeometry.Frame
        val artHeight = frame.ARTWORK_BOTTOM_FRACTION - frame.ARTWORK_TOP_FRACTION

        assertTrue(frame.artworkCornerFraction() <= artHeight / 2f)
    }

    @Test
    fun `the cover clears the card's rounded corner`() {
        assertTrue(FaceGeometry.Frame.cardCornerClearance() > 0f)
    }

    /** A round dial eats its own corners. The card's furthest drawn point has to stay off the
     *  glass edge, or the composition lands under a device's bezel. */
    @Test
    fun `the card stays inside the round display`() {
        val frame = FaceGeometry.Frame
        val cardWidth = 1f - frame.CARD_INSET_FRACTION * 2f
        val cardHeight = frame.CARD_BOTTOM_FRACTION - frame.CARD_TOP_FRACTION
        val corner = minOf(cardWidth, cardHeight) * frame.CARD_CORNER_FRACTION
        val arcCenterX = frame.CARD_INSET_FRACTION + corner
        val arcCenterY = frame.CARD_TOP_FRACTION + corner
        val reach = hypot(.5f - arcCenterX, .5f - arcCenterY) + corner

        assertTrue("card reaches $reach of the radius", reach < .46f)
    }

    /**
     * Why [FaceGeometry.Frame.TITLE_MAX_LINES] is 1 rather than a taste: the band holds a full
     * design-size line and cannot hold two even at the floor size, so a mode that asks for more
     * has to be capped somewhere. Capping it at the renderer keeps the overflow an ellipsis
     * instead of a second line drawn over the cover.
     */
    @Test
    fun `the title band holds one line and not two`() {
        val frame = FaceGeometry.Frame
        val band = (frame.ARTWORK_TOP_FRACTION - frame.TITLE_TOP_FRACTION) * REFERENCE_WATCH_DP

        assertTrue(
                "band $band dp cannot hold a design-size line",
                band >= frame.TITLE_TEXT_SIZE_SP * LINE_HEIGHT_FACTOR)
        assertTrue(
                "band $band dp holds two floor-size lines, so the cap is arbitrary",
                band < frame.TITLE_MIN_TEXT_SIZE_SP * LINE_HEIGHT_FACTOR * 2f)
        assertEquals(1, frame.TITLE_MAX_LINES)
    }

    private companion object {
        /** The smallest round Wear display the faces are laid out against. */
        const val REFERENCE_WATCH_DP = 192f
        const val LINE_HEIGHT_FACTOR = 1.2f
    }
}
