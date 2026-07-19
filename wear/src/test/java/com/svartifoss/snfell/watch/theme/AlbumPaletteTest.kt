package com.svartifoss.snfell.watch.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlbumPaletteTest {
    @Test
    fun aSignificantVibrantSwatchIsPreferredAsAccent() {
        val vibrant = SwatchInfo(0xFFE53935.toInt(), population = 400)
        val dominant = SwatchInfo(0xFF1E3A5F.toInt(), population = 1000)

        assertEquals(vibrant.rgb, selectPrimaryAccent(vibrant, listOf(dominant, vibrant)))
    }

    @Test
    fun aTinyVibrantDetailFallsBackToTheDominantColor() {
        // A small bright detail (a logo, a reflection) below the population share must not turn a
        // mostly-blue cover's accent red - the dominant colour also matches the blurred background.
        val vibrant = SwatchInfo(0xFFE53935.toInt(), population = 40)
        val dominant = SwatchInfo(0xFF1E3A5F.toInt(), population = 1000)

        assertEquals(dominant.rgb, selectPrimaryAccent(vibrant, listOf(dominant, vibrant)))
    }

    @Test
    fun withoutAVibrantSwatchTheDominantColorIsUsed() {
        val a = SwatchInfo(0xFF445566.toInt(), population = 300)
        val b = SwatchInfo(0xFF223344.toInt(), population = 900)

        assertEquals(b.rgb, selectPrimaryAccent(null, listOf(a, b)))
    }

    @Test
    fun withNoColorInformationThereIsNoAccent() {
        assertNull(selectPrimaryAccent(null, emptyList()))
    }


    @Test
    fun companionColorsComeOnlyFromRankedAlbumSwatches() {
        val primary = 0xFF1769AA.toInt()
        val coverBrown = 0xFF6E3B33.toInt()
        val coverPlum = 0xFF241B2F.toInt()

        val result = selectAlbumCompanionColors(
                primary,
                listOf(primary, coverBrown, coverBrown, coverPlum)
        )

        assertEquals(coverBrown, result.secondary)
        assertEquals(coverPlum, result.tertiary)
    }

    @Test
    fun monochromaticArtworkRequestsSameHueFallbackFromRenderer() {
        val primary = 0xFF1769AA.toInt()

        val result = selectAlbumCompanionColors(primary, listOf(primary, primary))

        assertNull(result.secondary)
        assertNull(result.tertiary)
    }
}
