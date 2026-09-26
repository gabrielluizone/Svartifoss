package com.svartifoss.snfell.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins [QueueArtworkResolver.sampleSizeFor].
 *
 * Covers were decoded at their stored size - routinely a thousand pixels or more for art embedded
 * in a track - and a queue decoded every row's at once, so a long local-library queue held
 * hundreds of megabytes of bitmaps for a list of 96px thumbnails. Sampling brings each decode down
 * to near the size it ends up at, and the rule that matters is that it never goes *below* it: a
 * cover sampled under its target would be upscaled afterwards and look soft.
 */
class QueueArtworkSampleSizeTest {

    @Test
    fun `a large cover is sampled down towards the target`() {
        assertEquals(8, QueueArtworkResolver.sampleSizeFor(1_000, 1_000, 96))
        assertEquals(2, QueueArtworkResolver.sampleSizeFor(1_000, 1_000, 320))
    }

    @Test
    fun `a cover already near the target is left alone`() {
        assertEquals(1, QueueArtworkResolver.sampleSizeFor(500, 500, 320))
        assertEquals(1, QueueArtworkResolver.sampleSizeFor(96, 96, 96))
    }

    @Test
    fun `a cover smaller than the target is never sampled`() {
        assertEquals(1, QueueArtworkResolver.sampleSizeFor(60, 60, 320))
    }

    /** The shorter side decides, so a letterboxed thumbnail keeps its height above the target. */
    @Test
    fun `the shorter side decides for a non-square image`() {
        assertEquals(2, QueueArtworkResolver.sampleSizeFor(1_280, 720, 320))
    }

    @Test
    fun `a sampled cover never ends up below the target`() {
        listOf(96, 150, 320, 454).forEach { target ->
            (target..4_000 step 37).forEach { side ->
                val sample = QueueArtworkResolver.sampleSizeFor(side, side, target)
                assertTrue("side=$side target=$target sample=$sample", side / sample >= target)
            }
        }
    }

    @Test
    fun `unknown bounds decode at full size`() {
        assertEquals(1, QueueArtworkResolver.sampleSizeFor(-1, -1, 96))
        assertEquals(1, QueueArtworkResolver.sampleSizeFor(1_000, 1_000, 0))
    }
}
