package com.svartifoss.snfell.view.buttonconfig

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionPickerLayoutTest {
    private fun section(id: String, vararg items: PickerItem<String>) =
            PickerSection(id, title = id.uppercase(), chipLabel = id, iconRes = 0, items = items.toList())

    private val playback = section(
            "playback",
            PickerItem.Choice("Play"),
            PickerItem.Choice("Next"),
            PickerItem.Group("speed", "Playback speed") { listOf("0.5x", "1x", "2x") })
    private val volume = section("volume", PickerItem.Choice("Louder"), PickerItem.Choice("Quieter"))

    @Test
    fun everySectionGetsAHeaderAndItsRowsInOrder() {
        val layout = ActionPickerLayout.flatten(listOf(playback, volume), emptySet())

        assertEquals(listOf(
                PickerRow.Header("playback"),
                PickerRow.Choice("Play"),
                PickerRow.Choice("Next"),
                PickerRow.Group("speed", "Playback speed", expanded = false),
                PickerRow.Header("volume"),
                PickerRow.Choice("Louder"),
                PickerRow.Choice("Quieter")
        ), layout.rows)
        assertEquals(listOf(0, 4), layout.sectionStarts)
        assertEquals(listOf("playback", "volume"), layout.sectionIds)
    }

    @Test
    fun aGroupExpandsInPlaceAndPushesLaterSectionsDown() {
        val layout = ActionPickerLayout.flatten(listOf(playback, volume), setOf("speed"))

        assertEquals(PickerRow.Group("speed", "Playback speed", expanded = true), layout.rows[3])
        // Children sit directly under their group and are marked as children.
        assertEquals(PickerRow.Choice("0.5x", indent = true), layout.rows[4])
        assertEquals(PickerRow.Choice("2x", indent = true), layout.rows[6])
        // The next section moved down by the three children.
        assertEquals(listOf(0, 7), layout.sectionStarts)
    }

    @Test
    fun aCollapsedGroupNeverBuildsItsChildren() {
        var built = false
        val lazy = section("apps", PickerItem.Group("apps", "Play with an app") {
            built = true
            listOf("Spotify")
        })

        ActionPickerLayout.flatten(listOf(lazy), emptySet())
        assertFalse("collapsing must not pay for the children", built)

        ActionPickerLayout.flatten(listOf(lazy), setOf("apps"))
        assertTrue(built)
    }

    @Test
    fun aSectionWithNothingInItIsLeftOutWithItsShortcut() {
        val empty = section("automation")
        val layout = ActionPickerLayout.flatten(listOf(playback, empty, volume), emptySet())

        assertEquals(listOf("playback", "volume"), layout.sectionIds)
        assertFalse(layout.rows.contains(PickerRow.Header("automation")))
    }

    @Test
    fun leadingRowsComeBeforeTheFirstHeaderAndBelongToNoSection() {
        val layout = ActionPickerLayout.flatten(listOf(volume), emptySet(), leading = listOf("None"))

        assertEquals(PickerRow.Choice("None"), layout.rows[0])
        assertEquals(PickerRow.Header("volume"), layout.rows[1])
        assertEquals(listOf(1), layout.sectionStarts)
    }

    @Test
    fun theAddLinkRowIsDrawnWhereItWasPlaced() {
        val streaming = section("streaming", PickerItem.AddLink, PickerItem.Choice("Liked songs"))
        val layout = ActionPickerLayout.flatten(listOf(streaming), emptySet())

        assertEquals(listOf(
                PickerRow.Header("streaming"),
                PickerRow.AddLink,
                PickerRow.Choice("Liked songs")
        ), layout.rows)
    }

    // region sectionAt

    @Test
    fun theSectionAtAPositionIsTheLastHeaderAtOrAboveIt() {
        val starts = listOf(1, 6, 12)

        assertEquals(0, ActionPickerLayout.sectionAt(starts, 1))
        assertEquals(0, ActionPickerLayout.sectionAt(starts, 5))
        assertEquals(1, ActionPickerLayout.sectionAt(starts, 6))
        assertEquals(1, ActionPickerLayout.sectionAt(starts, 11))
        assertEquals(2, ActionPickerLayout.sectionAt(starts, 12))
        assertEquals(2, ActionPickerLayout.sectionAt(starts, 99))
    }

    @Test
    fun rowsAboveTheFirstHeaderCountAsTheFirstSection() {
        // The "None" row: scrolled to the very top, the first shortcut is still the lit one.
        assertEquals(0, ActionPickerLayout.sectionAt(listOf(1, 6), 0))
    }

    @Test
    fun noSectionsMeansNoneIsLit() {
        assertEquals(-1, ActionPickerLayout.sectionAt(emptyList(), 0))
    }

    // endregion

    // region search

    private fun titles(rows: List<PickerRow.Choice<String>>) = rows.map { it.action }

    @Test
    fun searchFindsAGroupChildWithoutOpeningTheGroup() {
        val rows = ActionPickerLayout.searchRows(listOf(playback, volume), { it }, "0.5")

        assertEquals(listOf("0.5x"), titles(rows))
        assertEquals("PLAYBACK › Playback speed", rows.single().breadcrumb)
    }

    @Test
    fun searchCanFindAChoiceByTheSectionItIsIn() {
        // "volume" is in no action title but is the section of both: breadcrumb matches count.
        val rows = ActionPickerLayout.searchRows(listOf(playback, volume), { it }, "volume")

        assertEquals(listOf("Louder", "Quieter"), titles(rows))
    }

    @Test
    fun aTitleMatchOutranksASectionMatch() {
        val sections = listOf(
                section("volume", PickerItem.Choice("Mute")),
                section("playback", PickerItem.Choice("Volume up")))
        val rows = ActionPickerLayout.searchRows(sections, { it }, "volume")

        assertEquals(listOf("Volume up", "Mute"), titles(rows))
    }

    @Test
    fun theAddLinkRowIsNotASearchResult() {
        val streaming = section("streaming", PickerItem.AddLink, PickerItem.Choice("Liked songs"))

        assertEquals(listOf("Liked songs"),
                titles(ActionPickerLayout.searchRows(listOf(streaming), { it }, "s")))
    }

    @Test
    fun theNoneRowIsSearchableAndHasNoBreadcrumb() {
        val rows = ActionPickerLayout.searchRows(
                listOf(volume), { it }, "none", leading = listOf("None"))

        assertEquals(listOf("None"), titles(rows))
        assertEquals(null, rows.single().breadcrumb)
    }

    @Test
    fun aQueryThatMatchesNothingIsEmpty() {
        assertTrue(ActionPickerLayout.searchRows(listOf(playback), { it }, "zzz").isEmpty())
    }

    @Test
    fun searchResultsAreNotIndentedEvenWhenTheyCameFromAGroup() {
        val rows = ActionPickerLayout.searchRows(listOf(playback), { it }, "2x")

        assertFalse(rows.single().indent)
    }

    // endregion
}
