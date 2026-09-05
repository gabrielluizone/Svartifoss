package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextBackdropTest {

    @Test
    fun `an unrecognised style draws nothing rather than something else`() {
        assertEquals(TextBackdropStyle.NONE, TextBackdropStyle.fromPreference(null))
        assertEquals(TextBackdropStyle.NONE, TextBackdropStyle.fromPreference(""))
        assertEquals(TextBackdropStyle.NONE, TextBackdropStyle.fromPreference("frosted_pill"))
    }

    @Test
    fun `every style round-trips through its preference value`() {
        TextBackdropStyle.entries.forEach {
            assertEquals(it, TextBackdropStyle.fromPreference(it.preferenceValue))
        }
    }

    @Test
    fun `zero opacity draws nothing even with a real style selected`() {
        val spec = spec(TextBackdropStyle.BLOCK, opacity = 0)
        assertTrue(spec.isNone)
        assertEquals(0f, spec.alpha, 0f)
    }

    @Test
    fun `the styles are ordered from faintest to most opaque`() {
        val alphas = listOf(
                TextBackdropStyle.SOFT,
                TextBackdropStyle.HIGHLIGHT,
                TextBackdropStyle.BLOCK
        ).map { spec(it, opacity = 100).alpha }

        assertEquals(alphas.sorted(), alphas)
        // None of them is fully opaque: a plate that hides the artwork completely is a background
        // style, and the Background page already owns that decision.
        assertTrue(alphas.all { it < 1f })
    }

    @Test
    fun `opacity scales the style's own alpha and is clamped`() {
        val full = spec(TextBackdropStyle.HIGHLIGHT, opacity = 100)
        assertEquals(full.alpha / 2f, spec(TextBackdropStyle.HIGHLIGHT, opacity = 50).alpha, 0.001f)
        assertEquals(full.alpha, spec(TextBackdropStyle.HIGHLIGHT, opacity = 10_000).alpha, 0f)
        assertFalse(full.isNone)
    }

    @Test
    fun `a backdrop resolves its colour through exactly the same table as a shadow`() {
        listOf(
                TextShadowColorMode.BLACK,
                TextShadowColorMode.WHITE,
                TextShadowColorMode.ALBUM,
                TextShadowColorMode.CUSTOM
        ).forEach { mode ->
            assertEquals(
                    mode.name,
                    TextShadowSpec.resolveColor(mode, "#204060", 0xFF884422.toInt()),
                    TextBackdropSpec.resolveColor(mode, "#204060", 0xFF884422.toInt()))
        }
    }

    private fun spec(style: TextBackdropStyle, opacity: Int) =
            TextBackdropSpec(style, TextShadowColorMode.BLACK, "", opacity)
}
