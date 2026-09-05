package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextOutlineTest {

    @Test
    fun `an unrecognised style draws nothing rather than something else`() {
        assertEquals(TextOutlineStyle.NONE, TextOutlineStyle.fromPreference(null))
        assertEquals(TextOutlineStyle.NONE, TextOutlineStyle.fromPreference(""))
        assertEquals(TextOutlineStyle.NONE, TextOutlineStyle.fromPreference("chiselled"))
    }

    @Test
    fun `every style round-trips through its preference value`() {
        TextOutlineStyle.entries.forEach {
            assertEquals(it, TextOutlineStyle.fromPreference(it.preferenceValue))
        }
    }

    @Test
    fun `none has no width at any size`() {
        val none = TextOutlineSpec.NONE
        assertTrue(none.isNone)
        assertEquals(0f, none.strokeWidthPx(textSizePx = 90f, density = 3f), 0f)
    }

    @Test
    fun `the stroke scales with the type rather than being a fixed width`() {
        // The whole reason the width is a fraction: a stroke that reads as a keyline on a large
        // title closes up the counters of a small artist line.
        val spec = spec(TextOutlineStyle.MEDIUM)
        val large = spec.strokeWidthPx(textSizePx = 90f, density = 2f)
        val small = spec.strokeWidthPx(textSizePx = 45f, density = 2f)

        assertEquals(large / 2f, small, 0.001f)
        assertTrue(large > small)
    }

    @Test
    fun `the thinnest stroke never aliases away on a small line`() {
        // At 20px text on a 1x screen the fraction alone would ask for under half a pixel, which
        // draws as nothing at all - indistinguishable from the setting not working.
        val hairline = spec(TextOutlineStyle.HAIRLINE)
        val width = hairline.strokeWidthPx(textSizePx = 20f, density = 1f)

        assertFalse(width <= 0f)
        assertEquals(TextOutlineStyle.MIN_WIDTH_DP, width, 0.001f)
        // The floor is in dp, so a denser screen gets proportionally more pixels for it.
        assertEquals(
                TextOutlineStyle.MIN_WIDTH_DP * 3f,
                hairline.strokeWidthPx(textSizePx = 20f, density = 3f),
                0.001f)
    }

    @Test
    fun `the styles are ordered from thinnest to heaviest`() {
        val widths = listOf(
                TextOutlineStyle.HAIRLINE,
                TextOutlineStyle.THIN,
                TextOutlineStyle.MEDIUM,
                TextOutlineStyle.BOLD
        ).map { spec(it).strokeWidthPx(textSizePx = 200f, density = 1f) }

        assertEquals(widths.sorted(), widths)
    }

    @Test
    fun `an outline resolves its colour through exactly the same table as a shadow`() {
        // Two implementations that agree today are two implementations that can stop agreeing.
        listOf(
                TextShadowColorMode.BLACK,
                TextShadowColorMode.WHITE,
                TextShadowColorMode.ALBUM,
                TextShadowColorMode.CUSTOM
        ).forEach { mode ->
            assertEquals(
                    mode.name,
                    TextShadowSpec.resolveColor(mode, "#123456", 0xFF00FF00.toInt()),
                    TextOutlineSpec.resolveColor(mode, "#123456", 0xFF00FF00.toInt()))
        }
    }

    private fun spec(style: TextOutlineStyle) =
            TextOutlineSpec(style, TextShadowColorMode.BLACK, "")
}
