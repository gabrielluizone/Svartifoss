package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlbumAccentSelectionTest {

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
    fun theVibrantSourceKeepsATinyDetailTheBalancedSourceRejects() {
        // The whole reason the option exists: this is the cover where the phone preview and the
        // watch disagreed, one taking the small colourful swatch and the other the grey wall.
        val vibrant = SwatchInfo(0xFFD8C3A5.toInt(), population = 40)
        val dominant = SwatchInfo(0xFF4A4A4A.toInt(), population = 1000)
        val swatches = listOf(dominant, vibrant)

        assertEquals(dominant.rgb,
                selectPrimaryAccent(vibrant, swatches, AlbumAccentSource.BALANCED))
        assertEquals(vibrant.rgb,
                selectPrimaryAccent(vibrant, swatches, AlbumAccentSource.VIBRANT))
    }

    @Test
    fun theVibrantSourceStillHasNothingToPickWhenThereIsNoVibrantSwatch() {
        // "Vibrant" is a preference for *which* swatch, not a promise one exists: a cover with no
        // vibrant swatch must still produce an accent rather than dropping to the theme default.
        val dominant = SwatchInfo(0xFF223344.toInt(), population = 900)

        assertEquals(dominant.rgb,
                selectPrimaryAccent(null, listOf(dominant), AlbumAccentSource.VIBRANT))
    }

    @Test
    fun anUnknownStoredValueResolvesToTheBehaviourAlreadyOnScreen() {
        // A value from a newer phone, or a corrupted one, must not silently repaint a watch that
        // never opted into anything - so everything unrecognised lands on the long-standing default.
        assertEquals(AlbumAccentSource.BALANCED, AlbumAccentSource.fromPreference(null))
        assertEquals(AlbumAccentSource.BALANCED, AlbumAccentSource.fromPreference(""))
        assertEquals(AlbumAccentSource.BALANCED, AlbumAccentSource.fromPreference("dominant"))
        assertEquals(AlbumAccentSource.BALANCED, AlbumAccentSource.fromPreference("balanced"))
        assertEquals(AlbumAccentSource.VIBRANT, AlbumAccentSource.fromPreference("vibrant"))
        assertEquals(AlbumAccentSource.VIBRANT, AlbumAccentSource.fromPreference("  VIBRANT "))
    }
}
