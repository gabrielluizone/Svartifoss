package com.svartifoss.snfell.actions

import com.svartifoss.snfell.music.PlaylistShortcut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutMenuPlannerTest {
    private val spotify = PlaylistShortcut("Happy Hits", "https://open.spotify.com/playlist/abc")
    private val ytm = PlaylistShortcut("Mix", "https://music.youtube.com/playlist?list=PL1")

    @Test
    fun aNewLinkGoesIntoBothTheLibraryAndTheMenu() {
        val plan = ShortcutMenuPlanner.plan(
                library = listOf(spotify),
                menuLinks = emptyList(),
                name = "  Mix  ",
                link = ytm.link)

        assertTrue(plan.addedToLibrary)
        assertTrue(plan.addedToMenu)
        // Appended, and the name is trimmed the way the editor trims it.
        assertEquals(listOf(spotify, ytm), plan.library)
        assertEquals(ytm, plan.shortcut)
    }

    @Test
    fun aLinkAlreadySavedIsNotSavedAgainAndKeepsItsSavedName() {
        val plan = ShortcutMenuPlanner.plan(
                library = listOf(spotify),
                menuLinks = emptyList(),
                name = "Something typed later",
                link = spotify.link)

        assertFalse(plan.addedToLibrary)
        assertTrue(plan.addedToMenu)
        assertEquals(listOf(spotify), plan.library)
        // The library entry is the one its assigned copies are kept in step with.
        assertEquals("Happy Hits", plan.shortcut.name)
    }

    @Test
    fun aLinkAlreadyOnTheMenuIsNotAddedTwice() {
        val plan = ShortcutMenuPlanner.plan(
                library = listOf(spotify),
                menuLinks = listOf(spotify.link),
                name = "Happy Hits",
                link = spotify.link)

        assertFalse(plan.addedToLibrary)
        assertFalse(plan.addedToMenu)
    }

    @Test
    fun aLinkOnTheMenuButNotInTheLibraryIsStillSavedButNotDuplicatedOnTheMenu() {
        // A shortcut deleted from the library keeps its assigned copies working, so the menu can
        // hold a link the library has forgotten. Re-adding it restores the library entry only.
        val plan = ShortcutMenuPlanner.plan(
                library = emptyList(),
                menuLinks = listOf(ytm.link),
                name = "Mix",
                link = ytm.link)

        assertTrue(plan.addedToLibrary)
        assertFalse(plan.addedToMenu)
    }

    @Test
    fun linksAreComparedWithoutRegardToTheScheme() {
        val bare = "open.spotify.com/playlist/abc"

        assertTrue(ShortcutMenuPlanner.isInMenu(listOf(spotify.link), bare))
        assertFalse(ShortcutMenuPlanner.plan(listOf(spotify), listOf(spotify.link), "x", bare).addedToLibrary)
    }

    @Test
    fun aShuffledPlaylistIsAnotherDestinationFromThePlainOne() {
        val shuffled = ytm.link + "&shuffle=true"

        assertFalse(ShortcutMenuPlanner.isInMenu(listOf(ytm.link), shuffled))
        val plan = ShortcutMenuPlanner.plan(listOf(ytm), listOf(ytm.link), "Mix (shuffled)", shuffled)

        assertTrue(plan.addedToLibrary)
        assertTrue(plan.addedToMenu)
    }

    @Test
    fun isInMenuIsFalseForAnEmptyMenu() {
        assertFalse(ShortcutMenuPlanner.isInMenu(emptyList(), spotify.link))
    }
}
