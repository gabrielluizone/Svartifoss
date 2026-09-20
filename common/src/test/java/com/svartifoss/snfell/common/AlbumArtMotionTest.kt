package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumArtMotionTest {

    @Test
    fun fadeDisabledMeansNoAnimationAtAll() {
        val motion = AlbumArtMotion.resolve(
                fadeEnabled = false, direction = CoverChangeDirection.FORWARD)
        assertFalse(motion.animates)
        assertEquals(1f, motion.enterScale, 0f)
        assertEquals(0f, motion.enterShiftFraction, 0f)
    }

    @Test
    fun forwardEntersFromTheRightAndBackwardFromTheLeft() {
        val forward = AlbumArtMotion.resolve(true, CoverChangeDirection.FORWARD)
        val backward = AlbumArtMotion.resolve(true, CoverChangeDirection.BACKWARD)
        assertTrue(forward.enterShiftFraction > 0f)
        assertTrue(backward.enterShiftFraction < 0f)
        assertEquals(forward.enterShiftFraction, -backward.enterShiftFraction, 0f)
    }

    /**
     * The overscale is the only margin the drift has. Let the shift exceed it and the far edge of
     * the incoming cover walks into the frame, which on a round display is a bright crescent at
     * the bezel - the single most visible way this can fail.
     */
    @Test
    fun theDriftStaysInsideTheMarginTheOverscaleBuys() {
        val motion = AlbumArtMotion.resolve(true, CoverChangeDirection.FORWARD)
        val marginPerSide = (motion.enterScale - 1f) / 2f
        assertTrue(
                "Shift ${motion.enterShiftFraction} exceeds the $marginPerSide margin " +
                        "that scale ${motion.enterScale} leaves",
                motion.enterShiftFraction < marginPerSide)
    }

    @Test
    fun theOverscaleNeverShrinksTheCover() {
        assertTrue(AlbumArtMotion.resolve(true, CoverChangeDirection.FORWARD).enterScale >= 1f)
    }

    @Test
    fun settleOnlyKeepsTheFadeAndDropsTheTravel() {
        val motion = AlbumArtMotion.resolve(true, CoverChangeDirection.FORWARD).settleOnly()
        assertTrue(motion.animates)
        assertEquals(AlbumArtMotion.ENTER_SCALE, motion.enterScale, 0f)
        assertEquals(0f, motion.enterShiftFraction, 0f)
    }

    @Test
    fun aRecentPreviousPressOwnsTheDirection() {
        assertEquals(
                CoverChangeDirection.BACKWARD,
                AlbumArtMotion.directionFor(lastSkipWasBackward = true, msSinceSkip = 250))
    }

    @Test
    fun anOldPreviousPressDoesNot() {
        assertEquals(
                CoverChangeDirection.FORWARD,
                AlbumArtMotion.directionFor(
                        lastSkipWasBackward = true,
                        msSinceSkip = AlbumArtMotion.BACKWARD_WINDOW_MS + 1))
    }

    /** An uninitialised timestamp must not read as "pressed just now" - see [directionFor]. */
    @Test
    fun aNegativeAgeIsNotAFreshPress() {
        assertEquals(
                CoverChangeDirection.FORWARD,
                AlbumArtMotion.directionFor(lastSkipWasBackward = true, msSinceSkip = -1))
    }

    @Test
    fun aTrackThatEndsOnItsOwnGoesForward() {
        assertEquals(
                CoverChangeDirection.FORWARD,
                AlbumArtMotion.directionFor(lastSkipWasBackward = false, msSinceSkip = 10))
    }
}
