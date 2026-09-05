package com.svartifoss.snfell.view.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Everything subtle about the recent-colours list is in what it accepts, so that is what is
 * pinned. The list itself is a stack of strings; the ways a colour can be written are not.
 */
class ColorHistoryTest {

    @Test
    fun `a pasted colour is accepted however it was written`() {
        assertEquals("#FF0000", ColorHistory.normalize("#ff0000"))
        assertEquals("#FF0000", ColorHistory.normalize("FF0000"))
        assertEquals("#FF0000", ColorHistory.normalize("  #FF0000  "))
        // CSS shorthand, which is what a lot of palette tools put on the clipboard.
        assertEquals("#FF0000", ColorHistory.normalize("#f00"))
        assertEquals("#AABBCC", ColorHistory.normalize("#abc"))
    }

    /** Every preference behind this picker stores an opaque colour, so alpha is dropped, not
     *  refused - the person pasting an Android literal meant the colour in it. */
    @Test
    fun `an eight digit colour keeps its rgb and loses its alpha`() {
        assertEquals("#112233", ColorHistory.normalize("#80112233"))
        assertEquals("#112233", ColorHistory.normalize("FF112233"))
    }

    @Test
    fun `anything that is not a colour is refused rather than guessed at`() {
        assertNull(ColorHistory.normalize(null))
        assertNull(ColorHistory.normalize(""))
        assertNull(ColorHistory.normalize("#"))
        assertNull(ColorHistory.normalize("#12345"))
        assertNull(ColorHistory.normalize("#GGGGGG"))
        assertNull(ColorHistory.normalize("rgb(1,2,3)"))
    }

    @Test
    fun `the list reads newest first and drops what it cannot parse`() {
        assertEquals(
                listOf("#FF0000", "#00FF00"),
                ColorHistory.parse("#ff0000,not-a-colour,#00FF00,"))
        assertEquals(emptyList<String>(), ColorHistory.parse(null))
        assertEquals(emptyList<String>(), ColorHistory.parse(""))
    }

    @Test
    fun `applying a colour puts it in front`() {
        assertEquals("#0000FF,#FF0000", ColorHistory.remember("#FF0000", "#0000ff"))
    }

    /** The reason `remember` dedupes: re-applying the colour you have been working in would
     *  otherwise evict the rest of the palette one entry at a time. */
    @Test
    fun `re-applying a remembered colour moves it instead of repeating it`() {
        assertEquals(
                "#00FF00,#FF0000,#0000FF",
                ColorHistory.remember("#FF0000,#00FF00,#0000FF", "#00FF00"))
    }

    @Test
    fun `the list stops at the cap, dropping the oldest`() {
        val stored = (1..ColorHistory.MAX_ENTRIES).joinToString(",") { "#0000%02X".format(it) }
        val remembered = ColorHistory.remember(stored, "#FFFFFF")
        val entries = remembered.split(",")
        assertEquals(ColorHistory.MAX_ENTRIES, entries.size)
        assertEquals("#FFFFFF", entries.first())
        assertEquals("#000001", entries[1])
    }

    /** An unusable new value must not corrupt what is already there. */
    @Test
    fun `remembering nothing leaves the list intact`() {
        assertEquals("#FF0000,#00FF00", ColorHistory.remember("#ff0000,#00ff00", null))
        assertEquals("#FF0000", ColorHistory.remember("#FF0000", "nope"))
    }
}
