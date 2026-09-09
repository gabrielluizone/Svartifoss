package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule: a face picked on the wrist never leaves another theme active.
 *
 * Pinned as a pure decision because the bug it replaced was invisible in review and specific on the
 * wrist. The picker cleared the theme metadata only when a *built-in* face was chosen, so choosing
 * one saved theme while another was active moved `wear_screen_face` to the new base face and left
 * the old theme's id marked complete. [ThemeAppearance.resolve] therefore kept returning that
 * theme, and a Custom context reads only the `custom_active` scope - so the new layout rendered in
 * the previous theme's colours. Edit a colour on the phone, switch theme on the watch, and the
 * colour followed you across, which reads exactly like colours not being per-theme at all.
 */
class WatchFacePicksTest {

    @Test
    fun `picking a built-in face clears any active theme`() {
        val pick = WatchFacePicks.localWriteFor("poster", isCustomTheme = false)

        assertEquals("poster", pick.baseFace)
        assertNull(pick.activeCustomThemeId)
        assertFalse(pick.customComplete)
    }

    @Test
    fun `picking a saved theme never leaves the previous one active`() {
        val pick = WatchFacePicks.localWriteFor(
                "poster", isCustomTheme = true, customThemeId = "theme-y")

        // The awaited theme is named, so nothing is left holding an id nobody chose - but it is
        // never marked complete, which is what stops it (or its predecessor) from activating
        // before the phone has actually delivered a snapshot.
        assertEquals("theme-y", pick.activeCustomThemeId)
        assertFalse(pick.customComplete)
    }

    @Test
    fun `no pick of either kind can resolve to a theme`() {
        listOf(
                WatchFacePicks.localWriteFor("poster", isCustomTheme = false),
                WatchFacePicks.localWriteFor("poster", isCustomTheme = true, customThemeId = "theme-y"),
                WatchFacePicks.localWriteFor("classic", isCustomTheme = true, customThemeId = "theme-x")
        ).forEach { pick ->
            val resolved = ThemeAppearance.resolve(
                    baseFace = pick.baseFace,
                    customThemeId = pick.activeCustomThemeId,
                    customComplete = pick.customComplete,
                    customSchema = ThemeAppearance.CURRENT_SCHEMA)
            assertTrue(
                    "A watch-local pick resolved to $resolved - the watch would render the " +
                            "layout wearing a theme whose snapshot it does not have",
                    resolved is AppearanceContext.BuiltIn)
            assertEquals(pick.baseFace, resolved.baseFace)
        }
    }

    @Test
    fun `the base face is normalized, since the option can name one this build does not know`() {
        val pick = WatchFacePicks.localWriteFor("no_such_face", isCustomTheme = false)

        assertEquals(ThemeAppearance.DEFAULT_FACE, pick.baseFace)
    }

    @Test
    fun `a blank theme id is treated as no theme rather than written through`() {
        listOf("", "   ").forEach { id ->
            assertNull(
                    "A blank id would be written as an active theme nothing can resolve",
                    WatchFacePicks.localWriteFor("poster", isCustomTheme = true, customThemeId = id)
                            .activeCustomThemeId)
        }
    }
}
