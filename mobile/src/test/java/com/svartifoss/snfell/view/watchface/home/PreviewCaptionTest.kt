package com.svartifoss.snfell.view.watchface.home

import com.svartifoss.snfell.view.watchface.WatchPreviewView.PreviewSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreviewCaptionTest {

    /** A surface added to the preview without a name would make the caption fall silent there. */
    @Test
    fun `every surface has a label`() {
        PreviewSurface.entries.forEach { surface ->
            assertNotEquals("$surface has no label", 0, PreviewCaption.labelRes(surface))
        }
    }

    @Test
    fun `labels are distinct`() {
        val labels = PreviewSurface.entries.map(PreviewCaption::labelRes)
        assertEquals(labels.size, labels.toSet().size)
    }

    /** The player is what the preview shows unless told otherwise, so it adds nothing. */
    @Test
    fun `the player surface adds no suffix`() {
        assertNull(PreviewCaption.suffixRes(PreviewSurface.PLAYER))
    }

    @Test
    fun `every other surface is named after the theme`() {
        PreviewSurface.entries.filter { it != PreviewSurface.PLAYER }.forEach { surface ->
            assertEquals(PreviewCaption.labelRes(surface), PreviewCaption.suffixRes(surface))
        }
    }
}
