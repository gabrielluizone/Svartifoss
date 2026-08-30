package com.svartifoss.snfell.view

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyraContrastTest {

    @Test
    fun `medium light accent chooses black instead of low contrast white`() {
        val accent = 0xffd98e76.toInt()

        assertEquals(LyraContrast.BLACK, LyraContrast.foregroundFor(accent))
        assertTrue(
                LyraContrast.contrastRatio(LyraContrast.BLACK, accent) >
                        LyraContrast.contrastRatio(LyraContrast.WHITE, accent))
        assertTrue(LyraContrast.contrastRatio(LyraContrast.BLACK, accent) >= 4.5)
    }

    @Test
    fun `night sage filled button chooses a readable dark foreground`() {
        val nightSage = 0xff87a89f.toInt()
        val foreground = LyraContrast.foregroundFor(nightSage)

        assertEquals(LyraContrast.BLACK, foreground)
        assertTrue(LyraContrast.contrastRatio(foreground, nightSage) >= 4.5)
        assertTrue(LyraContrast.contrastRatio(LyraContrast.WHITE, nightSage) < 4.5)
    }

    @Test
    fun `accent text is adjusted to four point five on light surface`() {
        val surface = 0xfff2f2f2.toInt()
        val washedAccent = 0xffe4b7a8.toInt()

        val resolved = LyraContrast.contrastSafe(washedAccent, surface, 4.5)

        assertTrue(LyraContrast.contrastRatio(resolved, surface) >= 4.5)
        assertEquals(LyraContrast.BLACK, LyraContrast.foregroundFor(surface))
    }

    @Test
    fun `accent text is adjusted to four point five on dark surface`() {
        val surface = 0xff141414.toInt()
        val dimAccent = 0xff344744.toInt()

        val resolved = LyraContrast.contrastSafe(dimAccent, surface, 4.5)

        assertTrue(LyraContrast.contrastRatio(resolved, surface) >= 4.5)
        assertEquals(LyraContrast.WHITE, LyraContrast.foregroundFor(surface))
    }

    @Test
    fun `already legible accent is preserved exactly`() {
        val surface = 0xffffffff.toInt()
        val accent = 0xff55776f.toInt()

        assertEquals(accent, LyraContrast.contrastSafe(accent, surface, 4.5))
    }
}
