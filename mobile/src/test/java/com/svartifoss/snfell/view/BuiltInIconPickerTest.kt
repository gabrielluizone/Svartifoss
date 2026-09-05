package com.svartifoss.snfell.view

import com.svartifoss.snfell.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the one mistake a long, hand-edited resource-id array invites: listing the same drawable
 * twice. Existence of every id is already guaranteed by the Kotlin compiler (a typo'd `R.drawable`
 * reference is a build failure, not a runtime one), so a duplicate is the only thing worth a test
 * here - it silently shows the same icon in two grid cells rather than adding the one that was
 * meant to replace it.
 */
class BuiltInIconPickerTest {

    @Test
    fun `the built-in icon set has no duplicates`() {
        val ids = BuiltInIconPicker.BUILT_IN_ICONS.toList()
        val duplicates = ids.groupBy { it }.filterValues { it.size > 1 }.keys
        assertTrue("Duplicate resource ids in BUILT_IN_ICONS: $duplicates", duplicates.isEmpty())
    }

    @Test
    fun `the built-in icon set is not empty and stays well above the pre-redesign count`() {
        // 163 was the exact size right before the lyrics/quick-panel/app-mark/icons_3 additions -
        // a floor rather than an exact match, so the test does not need editing every time a new
        // icon is added.
        assertTrue(BuiltInIconPicker.BUILT_IN_ICONS.size > 163)
    }

    @Test
    fun `the app's own mark is offered, and offered first`() {
        assertEquals(R.drawable.ic_app_brand, BuiltInIconPicker.BUILT_IN_ICONS.first())
    }

    /**
     * The duplicate check above is worth as much across the two arrays as within either one: an
     * archived icon that is also an ordinary one shows twice the moment the switch is turned on,
     * which is exactly the silent outcome the first test exists to prevent.
     */
    @Test
    fun `archived icons are additional, never repeats of the ordinary set`() {
        val ordinary = BuiltInIconPicker.BUILT_IN_ICONS.toSet()
        val repeated = BuiltInIconPicker.ARCHIVED_ICONS.filter { it in ordinary }
        assertTrue("Archived ids already in BUILT_IN_ICONS: $repeated", repeated.isEmpty())

        val archived = BuiltInIconPicker.ARCHIVED_ICONS.toList()
        val duplicates = archived.groupBy { it }.filterValues { it.size > 1 }.keys
        assertTrue("Duplicate resource ids in ARCHIVED_ICONS: $duplicates", duplicates.isEmpty())
    }

    /**
     * Pins the reason this second array exists at all. Haibane Renmei is not a retired option, it
     * is a mark belonging to something other than this app, so it must not appear beside the
     * transport glyphs unless the developer switch has asked for exactly that.
     */
    @Test
    fun `the Haibane Renmei mark is offered only behind the archived switch`() {
        assertTrue(R.drawable.ic_haibane_renmei in BuiltInIconPicker.ARCHIVED_ICONS)
        assertTrue(R.drawable.ic_haibane_renmei !in BuiltInIconPicker.BUILT_IN_ICONS)
    }
}
