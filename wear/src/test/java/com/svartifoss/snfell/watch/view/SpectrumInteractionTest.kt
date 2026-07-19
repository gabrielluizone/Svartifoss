package com.svartifoss.snfell.watch.view

import com.svartifoss.snfell.watch.view.face.spectrumSeekFraction
import com.svartifoss.snfell.watch.view.face.spectrumBarHeight
import com.svartifoss.snfell.watch.view.face.spectrumTrackSeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpectrumInteractionTest {
    @Test
    fun tapPositionMapsAcrossTheEntireTimeline() {
        assertEquals(0f, spectrumSeekFraction(0f, 100f), 0f)
        assertEquals(.5f, spectrumSeekFraction(50f, 100f), 0f)
        assertEquals(1f, spectrumSeekFraction(100f, 100f), 0f)
    }

    @Test
    fun mappingClampsInvalidOrOutOfBoundsCoordinates() {
        assertEquals(0f, spectrumSeekFraction(-20f, 100f), 0f)
        assertEquals(1f, spectrumSeekFraction(120f, 100f), 0f)
        assertEquals(0f, spectrumSeekFraction(20f, 0f), 0f)
    }

    @Test
    fun spectrumProfileIsStableForATrackAndChangesForAnotherTrack() {
        val first = spectrumTrackSeed("First", "Artist", 180_000L)
        assertEquals(first, spectrumTrackSeed("First", "Artist", 180_000L))
        assertNotEquals(first, spectrumTrackSeed("Second", "Artist", 180_000L))
    }

    @Test
    fun spectrumBarsStayBoundedAndMoveWithAnimationPhase() {
        val seed = spectrumTrackSeed("First", "Artist", 180_000L)
        val resting = (0 until 15).map { spectrumBarHeight(seed, it, 0f) }
        val animated = (0 until 15).map { spectrumBarHeight(seed, it, 1.25f) }

        assertTrue(resting.all { it in .20f..94f })
        assertTrue(animated.all { it in .20f..94f })
        assertTrue(resting.zip(animated).any { (before, after) -> before != after })
    }
}
