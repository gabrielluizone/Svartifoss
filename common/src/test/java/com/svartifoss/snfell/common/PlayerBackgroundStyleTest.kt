package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerBackgroundStyleTest {
    @Test
    fun legacyAndNamedValuesResolve() {
        assertEquals(PlayerBackgroundStyle.BLURRED_BLACK_AND_WHITE,
                PlayerBackgroundStyle.fromPreference("blur_bw"))
        assertEquals(PlayerBackgroundStyle.EXPRESSIVE,
                PlayerBackgroundStyle.fromPreference("expressive"))
        assertEquals(PlayerBackgroundStyle.COVER,
                PlayerBackgroundStyle.fromPreference("unknown"))
    }

    @Test
    fun artworkPipelineFlagsStayCentralized() {
        assertTrue(PlayerBackgroundStyle.EXPRESSIVE.blurredArtwork)
        assertTrue(PlayerBackgroundStyle.BLACK_AND_WHITE.grayscaleArtwork)
        assertTrue(PlayerBackgroundStyle.ECLIPSE.hidesArtwork)
        assertFalse(PlayerBackgroundStyle.MATERIAL.hidesArtwork)
        assertTrue(PlayerBackgroundStyle.BLUR.isPlainArtworkTreatment)
        assertFalse(PlayerBackgroundStyle.POSTER.isPlainArtworkTreatment)
    }

    @Test
    fun builtInFacesKeepTheirAuthoredBackgroundByDefault() {
        assertEquals(PlayerBackgroundStyle.POSTER,
                PlayerBackgroundStyle.defaultForFace("poster"))
        assertEquals(PlayerBackgroundStyle.MATERIAL,
                PlayerBackgroundStyle.defaultForFace("material"))
        assertEquals(PlayerBackgroundStyle.COVER,
                PlayerBackgroundStyle.defaultForFace("classic"))
    }

    @Test
    fun onlyValidNonDefaultLegacyValuesOverridePerFaceDefaults() {
        assertFalse(PlayerBackgroundStyle.isLegacyOverrideValue("cover"))
        assertFalse(PlayerBackgroundStyle.isLegacyOverrideValue(""))
        assertFalse(PlayerBackgroundStyle.isLegacyOverrideValue("corrupt"))
        assertTrue(PlayerBackgroundStyle.isLegacyOverrideValue("blur"))
        assertTrue(PlayerBackgroundStyle.isLegacyOverrideValue("blur_bw"))
        assertTrue(PlayerBackgroundStyle.isLegacyOverrideValue("hidden"))
    }
}
