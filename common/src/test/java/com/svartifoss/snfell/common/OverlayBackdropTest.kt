package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayBackdropTest {
    private val explicitPreferenceMappings = linkedMapOf(
            "acrylic" to OverlayBackdrop.ACRYLIC,
            "blur" to OverlayBackdrop.ACRYLIC,
            "black" to OverlayBackdrop.SOLID_BLACK,
            "album" to OverlayBackdrop.SOLID_ALBUM,
            "secondary" to OverlayBackdrop.SOLID_SECONDARY,
            "tertiary" to OverlayBackdrop.SOLID_TERTIARY,
            "glass" to OverlayBackdrop.GLASS,
            "gradient" to OverlayBackdrop.GRADIENT,
            "duotone" to OverlayBackdrop.DUOTONE,
            "prism" to OverlayBackdrop.PRISM,
            "mesh" to OverlayBackdrop.MESH,
            "aurora" to OverlayBackdrop.AURORA,
            "spotlight" to OverlayBackdrop.SPOTLIGHT,
            "vignette" to OverlayBackdrop.VIGNETTE,
            "split" to OverlayBackdrop.SPLIT,
            "bands" to OverlayBackdrop.BANDS,
            "midnight" to OverlayBackdrop.MIDNIGHT,
            "halo" to OverlayBackdrop.HALO,
            "smoke" to OverlayBackdrop.SMOKE,
            "sunrise" to OverlayBackdrop.SUNRISE,
            "deep_ocean" to OverlayBackdrop.DEEP_OCEAN,
            "liquid_glass" to OverlayBackdrop.LIQUID_GLASS,
            "expressive" to OverlayBackdrop.EXPRESSIVE,
            "expressive_no_blur" to OverlayBackdrop.EXPRESSIVE_NO_BLUR
    )

    @Test
    fun everyExplicitPreferenceTokenMapsToItsBackdrop() {
        explicitPreferenceMappings.forEach { (preference, expected) ->
            assertEquals(
                    "Unexpected mapping for preference '$preference'",
                    expected,
                    OverlayBackdrop.fromPreference(preference)
            )
        }

        assertEquals(
                OverlayBackdrop.values().filterNot { it == OverlayBackdrop.FOLLOW_STYLE }.toSet(),
                explicitPreferenceMappings.values.toSet()
        )
        assertEquals(OverlayBackdrop.FOLLOW_STYLE, OverlayBackdrop.fromPreference("follow"))
        assertEquals(OverlayBackdrop.FOLLOW_STYLE, OverlayBackdrop.fromPreference("unknown"))
        assertEquals(OverlayBackdrop.FOLLOW_STYLE, OverlayBackdrop.fromPreference(null))
    }

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
        assertEquals(OverlayBackdrop.GLASS,
                OverlayBackdropResolver.resolve("follow", "chrome"))
        assertEquals(OverlayBackdrop.SOLID_ALBUM,
                OverlayBackdropResolver.resolve("follow", "soft"))
        assertEquals(OverlayBackdrop.SOLID_ALBUM,
                OverlayBackdropResolver.resolve("follow", "bubble"))
        assertEquals(OverlayBackdrop.GRADIENT,
                OverlayBackdropResolver.resolve("follow", "sunset"))
        assertEquals(OverlayBackdrop.DUOTONE,
                OverlayBackdropResolver.resolve("follow", "dual"))
        assertEquals(OverlayBackdrop.PRISM,
                OverlayBackdropResolver.resolve("follow", "holo"))
        assertEquals(OverlayBackdrop.PRISM,
                OverlayBackdropResolver.resolve("follow", "spectrum"))
    }

    @Test
    fun blurPolicyIsDefinedForEveryBackdrop() {
        val blurredBackdrops = setOf(
                OverlayBackdrop.ACRYLIC,
                OverlayBackdrop.GLASS,
                OverlayBackdrop.PRISM,
                OverlayBackdrop.LIQUID_GLASS,
                OverlayBackdrop.EXPRESSIVE,
                OverlayBackdrop.SMOKE
        )

        OverlayBackdrop.values().forEach { backdrop ->
            assertEquals(
                    "Unexpected album-blur policy for $backdrop",
                    backdrop in blurredBackdrops,
                    backdrop.usesAlbumBlur
            )
        }
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
        assertEquals("tonal", OverlayBackdropResolver.seekContentStyle("square_album"))
        assertEquals("tonal", OverlayBackdropResolver.seekContentStyle("stacked_pill"))
        assertEquals("gradient", OverlayBackdropResolver.seekContentStyle("ribbon"))
        assertEquals("terminal", OverlayBackdropResolver.seekContentStyle("lcd"))
        listOf("compact_pill", "badge", "glass_bar", "outline_square").forEach { style ->
            assertEquals("glass", OverlayBackdropResolver.seekContentStyle(style))
        }
        // plain/pill/giant/split and any unknown value all fall back to glass - the control-style
        // theme deliberately no longer influences the seek backdrop.
        assertEquals("glass", OverlayBackdropResolver.seekContentStyle("plain"))
        assertEquals("glass", OverlayBackdropResolver.seekContentStyle("pill"))
        assertEquals("glass", OverlayBackdropResolver.seekContentStyle("giant"))
        assertEquals("glass", OverlayBackdropResolver.seekContentStyle("split"))
        assertEquals("glass", OverlayBackdropResolver.seekContentStyle(null))
    }
}
