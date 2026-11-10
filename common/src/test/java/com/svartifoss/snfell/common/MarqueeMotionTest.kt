package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarqueeMotionTest {

    private val speed = 60f // px per second
    private val loop = 120f // px, so a pass takes exactly 2000 ms
    private val fade = 18f

    @Test
    fun aLineThatFitsDoesNotScroll() {
        assertFalse(MarqueeMotion.overflows(100f, 100f))
        assertFalse(MarqueeMotion.overflows(80f, 100f))
        assertFalse(MarqueeMotion.overflows(80f, 0f))
        assertTrue(MarqueeMotion.overflows(101f, 100f))
    }

    @Test
    fun theLoopIsTheLinePlusAThirdOfTheViewport() {
        assertEquals(240f, MarqueeMotion.loopPx(200f, 120f), 0.001f)
    }

    @Test
    fun theLineRestsAtItsStartBeforeTheFirstPass() {
        for (t in listOf(0L, 1L, 600L, MarqueeMotion.REST_MILLIS - 1)) {
            assertEquals("t=$t", 0f, MarqueeMotion.offsetAt(t, loop, speed), 0f)
            assertEquals("t=$t", 0f, MarqueeMotion.fadeStrength(
                    MarqueeMotion.offsetAt(t, loop, speed), loop, fade), 0f)
        }
    }

    @Test
    fun theLineMovesAtConstantSpeedDuringAPass() {
        val rest = MarqueeMotion.REST_MILLIS
        assertEquals(0f, MarqueeMotion.offsetAt(rest, loop, speed), 0.001f)
        assertEquals(30f, MarqueeMotion.offsetAt(rest + 500, loop, speed), 0.001f)
        assertEquals(60f, MarqueeMotion.offsetAt(rest + 1_000, loop, speed), 0.001f)
    }

    @Test
    fun theEndOfAPassAndTheNextRestAreTheSamePicture() {
        val period = MarqueeMotion.REST_MILLIS + MarqueeMotion.passMillis(loop, speed)
        // At the end of the pass the second copy has reached the start: an offset of a whole loop.
        assertEquals(loop, MarqueeMotion.offsetAt(period - 1, loop, speed), 0.1f)
        // ...and the next period begins at rest at zero, which draws the same thing.
        assertEquals(0f, MarqueeMotion.offsetAt(period, loop, speed), 0f)
        assertEquals(0f, MarqueeMotion.offsetAt(period + 1_000, loop, speed), 0f)
    }

    @Test
    fun nothingIsFadedWhileTheLineRests() {
        val period = MarqueeMotion.REST_MILLIS + MarqueeMotion.passMillis(loop, speed)
        for (t in listOf(0L, 500L, period, period + 700, 3 * period + 1_100)) {
            val offset = MarqueeMotion.offsetAt(t, loop, speed)
            assertEquals("t=$t", 0f, MarqueeMotion.fadeStrength(offset, loop, fade), 0f)
        }
        // The last frame of a pass is (almost) back at rest, so (almost) nothing is faded either.
        val last = MarqueeMotion.offsetAt(period - 1, loop, speed)
        assertTrue(MarqueeMotion.fadeStrength(last, loop, fade) < 0.01f)
    }

    @Test
    fun theFadeGrowsWithTheDistanceFromEitherRest() {
        assertEquals(0f, MarqueeMotion.fadeStrength(0f, loop, fade), 0f)
        assertEquals(0.5f, MarqueeMotion.fadeStrength(9f, loop, fade), 0.001f)
        assertEquals(1f, MarqueeMotion.fadeStrength(18f, loop, fade), 0f)
        assertEquals(1f, MarqueeMotion.fadeStrength(60f, loop, fade), 0f)
        assertEquals(0.5f, MarqueeMotion.fadeStrength(loop - 9f, loop, fade), 0.001f)
        assertEquals(0f, MarqueeMotion.fadeStrength(loop, loop, fade), 0f)
    }

    @Test
    fun aRendererCanSleepThroughARest() {
        val rest = MarqueeMotion.REST_MILLIS
        val period = rest + MarqueeMotion.passMillis(loop, speed)
        assertEquals(rest, MarqueeMotion.millisUntilMoving(0L, loop, speed))
        assertEquals(200L, MarqueeMotion.millisUntilMoving(rest - 200, loop, speed))
        assertEquals(0L, MarqueeMotion.millisUntilMoving(rest + 10, loop, speed))
        assertEquals(rest, MarqueeMotion.millisUntilMoving(period, loop, speed))
    }

    @Test
    fun aLoopThatCannotMoveNeverMoves() {
        assertEquals(0f, MarqueeMotion.offsetAt(5_000L, 0f, speed), 0f)
        assertEquals(0f, MarqueeMotion.offsetAt(5_000L, loop, 0f), 0f)
        assertEquals(Long.MAX_VALUE, MarqueeMotion.millisUntilMoving(5_000L, 0f, speed))
        assertEquals(0f, MarqueeMotion.fadeStrength(10f, loop, 0f), 0f)
    }

    @Test
    fun aNarrowViewportFadesAtMostAThirdOfItsWidthPerEnd() {
        assertEquals(18f, MarqueeMotion.fadeWidthPx(18f, 200f), 0f)
        assertEquals(10f, MarqueeMotion.fadeWidthPx(18f, 30f), 0.001f)
        assertEquals(0f, MarqueeMotion.fadeWidthPx(18f, 0f), 0f)
    }
}
