package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniButtonSurfacesTest {

    private val palette = MiniButtonSurfaces.Palette(
            albumAccent = 0xFF2E5AAC.toInt(),
            themeAccent = 0xFF4A4A4A.toInt(),
            expressiveAlbum = 0xFFBFD3F2.toInt(),
            glowAlbum = 0xFF5C86D6.toInt(),
            glowExpressive = 0xFFBFD3F2.toInt()
    )

    @Test
    fun theDefaultDefersToTheLayoutInsteadOfPaintingAColour() {
        // "glass" is the one value with no colour of its own - the surface drawing the button
        // supplies its neutral skin, which is what lets a face keep its designed button while the
        // user has not chosen a style.
        assertTrue(MiniButtonSurfaces.resolve("glass", palette).followsFaceNeutral)
    }

    @Test
    fun anUnknownStyleDefersToTheLayoutRatherThanDisappearing() {
        // A value can arrive from an imported backup or a newer phone build. Falling through to a
        // blank Surface would draw an invisible button; falling through to the layout's own is the
        // only answer that is right on every face.
        assertTrue(MiniButtonSurfaces.resolve("style_from_a_future_build", palette)
                .followsFaceNeutral)
        assertTrue(MiniButtonSurfaces.resolve(null, palette).followsFaceNeutral)
    }

    @Test
    fun transparentIsAnIconOnItsOwnAndNotTheLayoutDefault() {
        // "Icon only" and "follow the layout" are different answers and used to be one branch.
        val surface = MiniButtonSurfaces.resolve("transparent", palette)
        assertFalse(surface.followsFaceNeutral)
        assertEquals(0, surface.fillArgb)
        assertEquals(0, surface.strokeArgb)
        assertNull(surface.iconTintArgb)
    }

    @Test
    fun outlineStylesStrokeWithoutFilling() {
        val outline = MiniButtonSurfaces.resolve("outline", palette)
        assertEquals(0, outline.fillArgb)
        assertEquals(0xE0, outline.strokeArgb ushr 24)
        assertEquals(1.5f, outline.strokeWidthDp, 0f)
        // The plain outline deliberately leaves the icon alone: it is a keyline around whatever
        // the action's icon already is, including a full-colour app icon or fetched cover.
        assertNull(outline.iconTintArgb)
    }

    @Test
    fun glowUsesTheLiftedColourForBothTheStrokeAndTheIcon() {
        // The phone preview used to stroke glow_exp with the raw expressive tone and tint the icon
        // with it too, while the watch lifted both - so a dark album previewed as a hairline that
        // was not the one the wrist drew.
        val glow = MiniButtonSurfaces.resolve("glow_album", palette)
        assertEquals(palette.glowAlbum and 0x00FFFFFF, glow.strokeArgb and 0x00FFFFFF)
        assertEquals(palette.glowAlbum, glow.iconTintArgb)
        assertEquals(2f, glow.strokeWidthDp, 0f)
        assertTrue(glow.forceIconTint)

        val glowExp = MiniButtonSurfaces.resolve("glow_exp", palette)
        assertEquals(palette.glowExpressive and 0x00FFFFFF, glowExp.strokeArgb and 0x00FFFFFF)
        assertEquals(palette.glowExpressive, glowExp.iconTintArgb)
    }

    @Test
    fun aSolidFillPicksItsIconColourByContrastNotByAHalfWaySplit() {
        // The Expressive tonal surfaces measure around 0.45 luminance and read as light, so the
        // naive split the preview used put white ink on them.
        val light = MiniButtonSurfaces.resolve("solid_exp_album", palette)
        assertEquals(0xFF000000.toInt(), light.iconTintArgb)
        // ...and it forces the tint, because the whole point of that style is one flat colour.
        assertTrue(light.forceIconTint)

        val dark = MiniButtonSurfaces.resolve(
                "solid_album",
                palette.copy(albumAccent = 0xFF101020.toInt()))
        assertEquals(0xFFFFFFFF.toInt(), dark.iconTintArgb)
        // A plain solid does NOT force it: a launcher icon or fetched cover keeps its colours.
        assertFalse(dark.forceIconTint)
    }

    @Test
    fun translucentStylesTintWhiteWithoutFlatteningAColourIcon() {
        for (style in listOf("translucent_album", "translucent_album_exp", "uniform_glass_light")) {
            val surface = MiniButtonSurfaces.resolve(style, palette)
            assertEquals("$style icon tint", 0xFFFFFFFF.toInt(), surface.iconTintArgb)
            assertFalse("$style must not force the tint", surface.forceIconTint)
        }
        assertEquals(
                0x4D,
                MiniButtonSurfaces.resolve("translucent_album", palette).fillArgb ushr 24)
    }

    @Test
    fun solidThemeTakesTheColourTheCallerResolvedForTheFace() {
        // Expressive paints solid_theme as a tonal surface of the theme colour and every other
        // face uses it raw. That choice belongs to the caller, so the table must not second-guess
        // which colour "theme" means.
        val surface = MiniButtonSurfaces.resolve("solid_theme", palette)
        assertEquals(palette.themeAccent and 0x00FFFFFF, surface.fillArgb and 0x00FFFFFF)
    }

    @Test
    fun everyStyleTheSettingsPickerOffersResolvesToSomethingVisible() {
        // The entries in screen_buttons_bg_values. A style that resolved to a blank Surface would
        // be a picker option that renders an invisible button.
        val offered = listOf(
                "glass", "uniform_glass", "uniform_glass_light", "translucent_album",
                "translucent_album_exp", "glow_album", "glow_exp", "solid_theme", "solid_album",
                "solid_exp_album", "outline", "outline_exp", "outline_exp_album", "icon_exp",
                "transparent")
        for (style in offered) {
            val surface = MiniButtonSurfaces.resolve(style, palette)
            val paints = surface.followsFaceNeutral ||
                    surface.fillArgb != 0 ||
                    surface.strokeArgb != 0 ||
                    surface.iconTintArgb != null ||
                    style == "transparent"
            assertTrue("$style draws nothing at all", paints)
        }
    }
}
