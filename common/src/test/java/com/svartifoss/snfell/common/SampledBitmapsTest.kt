package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the property every caller of [SampledBitmaps] relies on: a picture already near the size
 * it is for decodes exactly as it did before sampling was added. The callers pass generous targets
 * (the screen, the largest size a store keeps) precisely so that only an unexpectedly large input
 * is ever sampled - so the rule has to be that nothing up to twice the target changes.
 * The finer cases are pinned through QueueArtworkResolver's delegate in QueueArtworkSampleSizeTest.
 */
class SampledBitmapsTest {

    @Test
    fun aPictureUpToTwiceTheTargetIsDecodedWhole() {
        listOf(96, 144, 454, 466, 480, 512).forEach { target ->
            (1..target * 2 - 1 step 7).forEach { side ->
                assertEquals("side=$side target=$target",
                        1, SampledBitmaps.sampleSizeFor(side, side, target))
            }
        }
    }

    @Test
    fun anOversizedPictureIsSampledButNeverBelowTheTarget() {
        assertEquals(4, SampledBitmaps.sampleSizeFor(4_000, 3_000, 480))
        listOf(144, 466, 512).forEach { target ->
            (target..6_000 step 53).forEach { side ->
                val sample = SampledBitmaps.sampleSizeFor(side, side * 3 / 4, target)
                assertTrue("side=$side target=$target sample=$sample",
                        side * 3 / 4 / sample >= target || sample == 1)
            }
        }
    }

    @Test
    fun unknownBoundsDecodeAtFullSize() {
        assertEquals(1, SampledBitmaps.sampleSizeFor(0, 0, 512))
        assertEquals(1, SampledBitmaps.sampleSizeFor(1_000, 1_000, 0))
    }
}
