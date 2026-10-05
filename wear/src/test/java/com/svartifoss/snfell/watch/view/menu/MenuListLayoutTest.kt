package com.svartifoss.snfell.watch.view.menu

import com.svartifoss.snfell.common.CustomLists
import com.svartifoss.snfell.common.LibraryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuListLayoutTest {

    @Test
    fun `the centred row maps to the entry below the heading`() {
        // A stem press confirms the centred entry; reading the list position as an entry index
        // would confirm the one below it.
        assertNull(MenuListLayout.entryIndexAt(0))
        assertEquals(0, MenuListLayout.entryIndexAt(1))
        assertEquals(4, MenuListLayout.entryIndexAt(5))
    }

    @Test
    fun `a list holding only its explanation is a message, not rows`() {
        assertTrue(MenuListLayout.isOnlyMessage(listOf(CustomLists.SPECIAL_ITEM_ERROR)))
        assertFalse(MenuListLayout.isOnlyMessage(emptyList()))
        // An empty library folder still has its way back up, so it stays a list.
        assertFalse(MenuListLayout.isOnlyMessage(
                listOf(LibraryEntry.UP, CustomLists.SPECIAL_ITEM_ERROR)))
        assertFalse(MenuListLayout.isOnlyMessage(listOf("spotify:playlist:abc")))
    }

    @Test
    fun `every list the phone sends has a heading, and an unknown one has none`() {
        assertEquals(MenuHeader.SHORTCUTS, MenuHeader.forList(CustomLists.PLAYLIST_SHORTCUTS))
        assertEquals(MenuHeader.SEARCH_RESULTS, MenuHeader.forList(CustomLists.SEARCH_RESULTS))
        assertEquals(MenuHeader.SEARCH_HISTORY, MenuHeader.forList(CustomLists.SEARCH_HISTORY))
        assertEquals(MenuHeader.LIBRARY, MenuHeader.forList(CustomLists.LIBRARY))
        assertEquals(MenuHeader.UP_NEXT, MenuHeader.forList(CustomLists.PLAYLIST))
        assertEquals(MenuHeader.RECENTLY_PLAYED, MenuHeader.forList(CustomLists.HISTORY))
        assertNull(MenuHeader.forList("SomethingANewerPhoneSends"))
    }
}
