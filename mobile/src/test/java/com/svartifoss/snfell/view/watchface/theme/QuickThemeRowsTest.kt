package com.svartifoss.snfell.view.watchface.theme

import com.svartifoss.snfell.common.AppearanceContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickThemeRowsTest {

    private val faces = listOf(
            QuickThemeEntry("classic", "Classic"),
            QuickThemeEntry("poster", "Poster"),
            QuickThemeEntry("verse", "Verse"))

    private val mine = listOf(
            QuickThemeEntry("custom:a", "Night drive", "Layout: Poster"),
            QuickThemeEntry("custom:b", "Gym", "Layout: Classic"))

    private fun rows(custom: List<QuickThemeEntry> = mine, active: String = "classic") =
            buildQuickThemeRows(custom, faces, active, customHeader = "My themes", builtInHeader = "Built-in")

    private fun List<QuickThemeRow>.labels() = map {
        when (it) {
            is QuickThemeRow.Header -> "# ${it.title}"
            is QuickThemeRow.Choice -> it.title
        }
    }

    @Test
    fun `saved themes lead, then the built-in ones, each under its heading`() {
        assertEquals(
                listOf("# My themes", "Night drive", "Gym", "# Built-in", "Classic", "Poster", "Verse"),
                rows().labels())
    }

    /** A heading above the only section names nothing. */
    @Test
    fun `with no saved themes the window is one plain list`() {
        val rows = rows(custom = emptyList())
        assertEquals(listOf("Classic", "Poster", "Verse"), rows.labels())
        assertTrue(rows.none { it is QuickThemeRow.Header })
    }

    @Test
    fun `only the theme in use is marked`() {
        val active = rows(active = "poster").filterIsInstance<QuickThemeRow.Choice>()
                .filter { it.active }.map { it.key }
        assertEquals(listOf("poster"), active)
    }

    @Test
    fun `a saved theme can be the one in use, and then no built-in one is`() {
        val choices = rows(active = "custom:b").filterIsInstance<QuickThemeRow.Choice>()
        assertEquals(listOf("custom:b"), choices.filter { it.active }.map { it.key })
    }

    @Test
    fun `a theme that is no longer in the library marks nothing`() {
        val choices = rows(active = "custom:gone").filterIsInstance<QuickThemeRow.Choice>()
        assertTrue(choices.none { it.active })
    }

    @Test
    fun `a saved theme and a face never share a key`() {
        val keys = rows().filterIsInstance<QuickThemeRow.Choice>().map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(keys.filter { it.startsWith(QuickThemeSwitch.CUSTOM_PREFIX) }.size == 2)
    }

    @Test
    fun `the subtitle travels with the row`() {
        val night = rows().filterIsInstance<QuickThemeRow.Choice>().first { it.key == "custom:a" }
        assertEquals("Layout: Poster", night.subtitle)
        assertFalse(rows().filterIsInstance<QuickThemeRow.Choice>().first { it.key == "poster" }.subtitle != null)
    }

    // ---- which key the active appearance is listed under ---------------------------------------

    @Test
    fun `a built-in appearance is listed under its face`() {
        assertEquals("verse", activeQuickThemeKey(AppearanceContext.BuiltIn("verse")))
    }

    @Test
    fun `a custom appearance is listed under its theme id, not its base face`() {
        val appearance = AppearanceContext.Custom(
                themeId = "b", baseFace = "classic", schema = 1, revision = 3)
        assertEquals("custom:b", activeQuickThemeKey(appearance))
    }
}
