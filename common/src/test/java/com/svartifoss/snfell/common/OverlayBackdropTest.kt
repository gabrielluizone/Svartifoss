package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayBackdropTest {
    @Test
    fun explicitBackgroundNeverDependsOnContentStyle() {
        assertEquals(
                OverlayBackdrop.ACRYLIC,
                OverlayBackdropResolver.resolve("acrylic", "minimal")
        )
        assertEquals(
                OverlayBackdrop.SOLID_BLACK,
                OverlayBackdropResolver.resolve("black", "prism")
        )
        assertEquals(
                OverlayBackdrop.PRISM,
                OverlayBackdropResolver.resolve("prism", "glass")
        )
    }

    @Test
    fun followMapsEveryMulticolorFamilyWithoutInventingOppositeColors() {
        assertEquals(OverlayBackdrop.GRADIENT,
                OverlayBackdropResolver.resolve("follow", "gradient"))
        assertEquals(OverlayBackdrop.DUOTONE,
                OverlayBackdropResolver.resolve("follow", "duotone"))
        assertEquals(OverlayBackdrop.PRISM,
                OverlayBackdropResolver.resolve("follow", "prism"))
        assertEquals(OverlayBackdrop.SOLID_ALBUM,
                OverlayBackdropResolver.resolve("follow", "tonal"))
        assertEquals(OverlayBackdrop.GLASS,
                OverlayBackdropResolver.resolve("follow", "glass_white"))
        assertEquals(OverlayBackdrop.GLASS,
                OverlayBackdropResolver.resolve("follow", "glass_tonal"))
        assertEquals(OverlayBackdrop.GLASS,
                OverlayBackdropResolver.resolve("follow", "outline_glass_white"))
    }

    @Test
    fun onlyCompositedTreatmentsRequestThePrecomputedBlurLayer() {
        assertTrue(OverlayBackdrop.ACRYLIC.usesAlbumBlur)
        assertTrue(OverlayBackdrop.GLASS.usesAlbumBlur)
        assertTrue(OverlayBackdrop.PRISM.usesAlbumBlur)
        assertFalse(OverlayBackdrop.SOLID_BLACK.usesAlbumBlur)
        assertFalse(OverlayBackdrop.GRADIENT.usesAlbumBlur)
    }

    @Test
    fun expressiveBackdropsReuseTheAlbumArtStyleValues() {
        assertEquals(OverlayBackdrop.EXPRESSIVE, OverlayBackdrop.fromPreference("expressive"))
        assertEquals(
                OverlayBackdrop.EXPRESSIVE_NO_BLUR,
                OverlayBackdrop.fromPreference("expressive_no_blur"))
        // The same two keys the player background uses, so the pair cannot drift apart.
        assertEquals(
                PlayerBackgroundStyle.EXPRESSIVE.preferenceValue,
                "expressive")
        assertEquals(
                PlayerBackgroundStyle.EXPRESSIVE_NO_BLUR.preferenceValue,
                "expressive_no_blur")
    }

    /** The blurred/sharp split is the whole difference between the two, so it has to be pinned. */
    @Test
    fun onlyTheBlurredExpressiveVariantAsksForTheBlurLayer() {
        assertTrue(OverlayBackdrop.EXPRESSIVE.usesAlbumBlur)
        assertFalse(OverlayBackdrop.EXPRESSIVE_NO_BLUR.usesAlbumBlur)
    }

    /**
     * "expressive" is also a *content* style (the quick panel's own look). Resolving it there must
     * keep mapping to a solid album surface, not jump to the new backdrop of the same name.
     */
    @Test
    fun expressiveContentStyleStillFollowsToSolidAlbum() {
        assertEquals(
                OverlayBackdrop.SOLID_ALBUM,
                OverlayBackdropResolver.resolve(preference = "follow", contentStyle = "expressive"))
    }

    @Test
    fun seekContentStyleMapsEachSeekStyleAndNeverDependsOnTheControlTheme() {
        assertEquals("tonal", OverlayBackdropResolver.seekContentStyle("expressive"))
        assertEquals("material", OverlayBackdropResolver.seekContentStyle("material"))
        assertEquals("light", OverlayBackdropResolver.seekContentStyle("white"))
        // plain/pill/giant/split and any unknown value all fall back to glass - the control-style
        // theme deliberately no longer influences the seek backdrop.
        assertEquals("glass", OverlayBackdropResolver.seekContentStyle("plain"))
        assertEquals("glass", OverlayBackdropResolver.seekContentStyle("pill"))
        assertEquals("glass", OverlayBackdropResolver.seekContentStyle("giant"))
        assertEquals("glass", OverlayBackdropResolver.seekContentStyle("split"))
        assertEquals("glass", OverlayBackdropResolver.seekContentStyle(null))
    }
}
