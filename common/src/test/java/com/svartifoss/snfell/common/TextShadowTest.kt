package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextShadowTest {

    @Test
    fun `an unrecognised style draws nothing rather than something else`() {
        // A value can arrive from a newer build or an imported community theme. Falling back to a
        // real style would silently restyle a saved face into a look nobody chose.
        assertEquals(TextShadowStyle.NONE, TextShadowStyle.fromPreference(null))
        assertEquals(TextShadowStyle.NONE, TextShadowStyle.fromPreference(""))
        assertEquals(TextShadowStyle.NONE, TextShadowStyle.fromPreference("neon_bevel"))
    }

    @Test
    fun `every style and colour mode round-trips through its preference value`() {
        TextShadowStyle.entries.forEach {
            assertEquals(it, TextShadowStyle.fromPreference(it.preferenceValue))
        }
        TextShadowColorMode.entries.forEach {
            assertEquals(it, TextShadowColorMode.fromPreference(it.preferenceValue))
        }
    }

    @Test
    fun `zero strength draws nothing even with a real style selected`() {
        val spec = spec(TextShadowStyle.LIFT, strength = 0)
        assertTrue(spec.isNone)
        assertEquals(0f, spec.alpha, 0f)
    }

    @Test
    fun `a blurred style dialled almost to nothing stays blurred rather than turning hard`() {
        // A zero radius is not "a very small blur" to either platform: Paint.setShadowLayer reads
        // it as no shadow layer at all and Compose reads it as a hard edge. Soft at 1% has to stay
        // a faint soft shadow.
        val faint = spec(TextShadowStyle.SOFT, strength = 1)
        assertFalse(faint.isNone)
        assertTrue("a blurred style must keep a non-zero radius", faint.radiusDp > 0f)

        // Hard is the style that genuinely has no blur, and must not gain one.
        assertEquals(0f, spec(TextShadowStyle.HARD, strength = 100).radiusDp, 0f)
    }

    @Test
    fun `strength scales radius, offset and alpha together`() {
        val full = spec(TextShadowStyle.LIFT, strength = 100)
        val half = spec(TextShadowStyle.LIFT, strength = 50)

        assertEquals(full.radiusDp / 2f, half.radiusDp, 0.001f)
        assertEquals(full.offsetDp / 2f, half.offsetDp, 0.001f)
        assertEquals(full.alpha / 2f, half.alpha, 0.001f)
    }

    @Test
    fun `alpha is clamped even past the strength ceiling`() {
        val overdriven = spec(TextShadowStyle.GLOW, strength = TextShadowSpec.MAX_STRENGTH_PERCENT)
        assertTrue(overdriven.alpha <= 1f)
        // Out-of-range values are clamped rather than refused: a profile written before the bound
        // existed is still drawn as its author intended, the way AppearanceNumericRanges does it.
        assertEquals(
                overdriven.alpha,
                spec(TextShadowStyle.GLOW, strength = 10_000).alpha,
                0f)
    }

    @Test
    fun `album falls back to black while extraction is still on its way`() {
        // Palette.generate is a callback, so on a track change the accent genuinely is not there
        // yet. A shadow is a legibility device; black is the value that works under every face.
        assertEquals(
                0xFF000000.toInt(),
                TextShadowSpec.resolveColor(TextShadowColorMode.ALBUM, "", null))
        assertEquals(
                0xFF3366CC.toInt(),
                TextShadowSpec.resolveColor(TextShadowColorMode.ALBUM, "", 0x003366CC))
    }

    @Test
    fun `the fixed colour modes ignore everything else about the spec`() {
        assertEquals(
                0xFF000000.toInt(),
                TextShadowSpec.resolveColor(TextShadowColorMode.BLACK, "#FF0000", 0xFF00FF00.toInt()))
        assertEquals(
                0xFFFFFFFF.toInt(),
                TextShadowSpec.resolveColor(TextShadowColorMode.WHITE, "#FF0000", 0xFF00FF00.toInt()))
    }

    @Test
    fun `a custom colour that cannot be read falls back to black instead of throwing`() {
        assertEquals(
                0xFFFF8800.toInt(),
                TextShadowSpec.resolveColor(TextShadowColorMode.CUSTOM, "#FF8800", null))
        assertEquals(
                0xFFFF8800.toInt(),
                TextShadowSpec.resolveColor(TextShadowColorMode.CUSTOM, "ff8800", null))
        listOf("", "#FFF", "#GGGGGG", "#FF88000", "not a colour").forEach { broken ->
            assertEquals(
                    "\"$broken\" must fall back to black",
                    0xFF000000.toInt(),
                    TextShadowSpec.resolveColor(TextShadowColorMode.CUSTOM, broken, null))
        }
    }

    @Test
    fun `hex parsing accepts both spellings and refuses everything else`() {
        assertEquals(0xFF123456.toInt(), TextShadowSpec.parseHexRgb("#123456"))
        assertEquals(0xFFABCDEF.toInt(), TextShadowSpec.parseHexRgb("abcdef"))
        assertEquals(0xFFABCDEF.toInt(), TextShadowSpec.parseHexRgb("  #ABCDEF  "))
        listOf("#12345", "#1234567", "#12345g", "").forEach {
            assertNull("\"$it\" is not a colour", TextShadowSpec.parseHexRgb(it))
        }
    }

    private fun spec(style: TextShadowStyle, strength: Int) = TextShadowSpec(
            style = style,
            colorMode = TextShadowColorMode.BLACK,
            customColor = "",
            strengthPercent = strength)
}
