package com.svartifoss.snfell.watch.view.shortcut

import com.svartifoss.snfell.common.AdaptiveTextContrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Pins the shortcut screen's colours: one colour from the cover, in two tones, with a neutral
 * answer for a cover that has no colour worth carrying - black, white, grey, a washed-out
 * gradient - and the service line flipping to the dark tone over a light ground.
 */
class ShortcutPaletteTest {

    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private fun hueDistance(a: Float, b: Float): Float {
        val d = abs(a - b) % 360f
        return if (d > 180f) 360f - d else d
    }

    @Test
    fun `a cover with no colour gives none`() {
        listOf(
                rgb(0, 0, 0),          // black
                rgb(255, 255, 255),    // white
                rgb(128, 128, 128),    // grey
                rgb(12, 4, 8),         // near black: saturated in HSL terms, but only noise
                rgb(250, 246, 248),    // near white with a hint of pink
                rgb(140, 123, 107),    // sepia - a monochrome photograph
                rgb(244, 217, 224)     // the pale end of a soft pastel gradient
        ).forEach { assertNull("%08x".format(it), ShortcutPalette.usableAccent(it)) }
        assertNull(ShortcutPalette.usableAccent(null))
    }

    @Test
    fun `a colour worth carrying is kept as it is`() {
        listOf(
                rgb(140, 112, 246),    // Liked Music's violet
                rgb(10, 20, 80),       // a deep navy: dark, but plainly blue
                rgb(232, 180, 200),    // a pastel pink with some body
                rgb(30, 215, 96)       // Spotify green
        ).forEach { assertEquals("%08x".format(it), it, ShortcutPalette.usableAccent(it)) }
    }

    @Test
    fun `both tones are the colour, light and dark`() {
        val accent = rgb(140, 112, 246)
        val hue = ShortcutPalette.hsl(accent)[0]
        listOf(ShortcutPalette.lightTone(accent) to ShortcutPalette.LIGHT_TONE,
                ShortcutPalette.darkTone(accent) to ShortcutPalette.DARK_TONE).forEach { (tone, lightness) ->
            val hsl = ShortcutPalette.hsl(tone)
            assertTrue("hue ${hsl[0]} vs $hue", hueDistance(hsl[0], hue) < 3f)
            assertEquals(lightness, hsl[2], .01f)
            assertTrue("saturation ${hsl[1]}", hsl[1] in .29f..0.86f)
        }
        // A deep navy still gives a light tone light enough to read on black.
        val light = ShortcutPalette.lightTone(rgb(10, 20, 80))
        assertTrue(AdaptiveTextContrast.relativeLuminance(light) > .4f)
    }

    @Test
    fun `hsl round trips`() {
        listOf(rgb(140, 112, 246), rgb(255, 0, 0), rgb(0, 128, 255), rgb(30, 215, 96), rgb(200, 200, 40))
                .forEach { color ->
                    val (h, s, l) = ShortcutPalette.hsl(color)
                    val back = ShortcutPalette.fromHsl(h, s, l)
                    listOf(16, 8, 0).forEach { shift ->
                        assertTrue("%08x".format(color),
                                abs(((back shr shift) and 0xFF) - ((color shr shift) and 0xFF)) <= 1)
                    }
                }
    }

    @Test
    fun `without a colour the screen is neutral`() {
        val colors = ShortcutPalette.resolve(accent = null, subtitleGround = null, neutralPlay = false)
        assertEquals(ShortcutPalette.NEUTRAL_LIGHT_TEXT, colors.subtitle)
        assertEquals(rgb(255, 255, 255), colors.playContainer)
        assertEquals(rgb(0, 0, 0), colors.playContent)
        // Over a light ground, even the neutral service line turns dark.
        assertEquals(ShortcutPalette.NEUTRAL_DARK_TEXT, ShortcutPalette.resolve(
                accent = null, subtitleGround = rgb(230, 230, 230), neutralPlay = false).subtitle)
    }

    @Test
    fun `one colour runs through the service line and the Play button`() {
        val accent = rgb(140, 112, 246)
        val dark = ShortcutPalette.resolve(accent, subtitleGround = rgb(30, 30, 50), neutralPlay = false)
        assertEquals(ShortcutPalette.lightTone(accent), dark.subtitle)
        assertEquals(dark.subtitle, dark.playContainer)
        assertEquals(ShortcutPalette.darkTone(accent), dark.playContent)
        // Not measured yet counts as dark: the ground is black until the glow lands.
        assertEquals(dark, ShortcutPalette.resolve(accent, subtitleGround = null, neutralPlay = false))
    }

    @Test
    fun `over a light ground the service line takes the dark tone`() {
        val accent = rgb(140, 112, 246)
        val light = ShortcutPalette.resolve(accent, subtitleGround = rgb(200, 195, 235), neutralPlay = false)
        assertEquals(ShortcutPalette.darkTone(accent), light.subtitle)
        // The Play button keeps its light fill: it is a shape, not a line of text.
        assertEquals(ShortcutPalette.lightTone(accent), light.playContainer)
    }

    @Test
    fun `Liked Music keeps a white Play button whatever its cover`() {
        val accent = rgb(140, 112, 246)
        val colors = ShortcutPalette.resolve(accent, subtitleGround = null, neutralPlay = true)
        assertEquals(rgb(255, 255, 255), colors.playContainer)
        assertEquals(rgb(0, 0, 0), colors.playContent)
        // The service line still carries the cover's colour.
        assertEquals(ShortcutPalette.lightTone(accent), colors.subtitle)
    }

    @Test
    fun `every answer is legible where it is drawn`() {
        listOf(rgb(140, 112, 246), rgb(10, 20, 80), rgb(232, 180, 200), rgb(200, 30, 30), rgb(250, 220, 40))
                .forEach { accent ->
                    val onDark = ShortcutPalette.resolve(accent, rgb(20, 20, 24), neutralPlay = false)
                    assertTrue(AdaptiveTextContrast.relativeLuminance(onDark.subtitle) > .4f)
                    val onLight = ShortcutPalette.resolve(accent, rgb(225, 225, 230), neutralPlay = false)
                    assertTrue(AdaptiveTextContrast.relativeLuminance(onLight.subtitle) < .10f)
                    assertNotNull(onLight.playContent)
                    // The glyph on the Play button stands apart from its fill.
                    assertTrue(AdaptiveTextContrast.relativeLuminance(onDark.playContainer) -
                            AdaptiveTextContrast.relativeLuminance(onDark.playContent) > .35f)
                }
    }
}
