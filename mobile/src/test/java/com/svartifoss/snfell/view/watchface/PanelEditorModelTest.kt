package com.svartifoss.snfell.view.watchface

import com.svartifoss.snfell.common.FaceScopedPreferences
import com.svartifoss.snfell.common.MiscPreferences
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the Panel page's compact editor against the preferences it is a view over.
 *
 * Same guard as [ColorEditorModelTest]: the editor replaces the visible rows but not their storage,
 * so a row the model forgets is unreachable on the page that owns it, and a model entry naming a
 * key the XML no longer declares points search at nothing.
 */
class PanelEditorModelTest {

    @Test
    fun `every declared panel row is owned by the editor`() {
        val declared = panelRowsInXml()
        assertTrue("watch_face_settings.xml should declare panel rows", declared.size > 12)

        val missing = declared - PanelEditorModel.keys
        assertTrue(
                "These Panel rows have no editor control, so the Panel page cannot reach them: " +
                        "$missing",
                missing.isEmpty())
    }

    @Test
    fun `every editor key is a real preference`() {
        val orphans = PanelEditorModel.keys - panelRowsInXml()
        assertTrue(
                "These editor keys are not declared in watch_face_settings.xml: $orphans",
                orphans.isEmpty())
    }

    @Test
    fun `only the two documented keys escape face scoping`() {
        // The Panel page is an appearance surface, so an unscoped key repaints every face at once.
        // Two are deliberately global and both are named in the model; anything else appearing
        // here is the bug AppearancePreferenceScopingTest describes.
        val unscoped = PanelEditorModel.keys
                .filter { PanelEditorModel.specFor(it)?.persisted == true }
                .filterNot(FaceScopedPreferences::isScoped)
                .toSet()

        assertEquals(PanelEditorModel.globalKeys, unscoped)
        assertTrue(
                MiscPreferences.WEAR_QUICK_PANEL_SOURCE.key in PanelEditorModel.globalKeys)
        assertTrue("queue_remote_artwork" in PanelEditorModel.globalKeys)
    }

    @Test
    fun `rows that store nothing are not treated as values`() {
        // The open note is explanatory text and the shortcuts row opens another screen. Reading
        // either through the preference store would persist a value no watch has ever read.
        listOf("quick_panel_open_note", "watch_streaming_shortcuts").forEach { key ->
            assertFalse(key, PanelEditorModel.specFor(key)!!.persisted)
        }
    }

    /**
     * The invariant is that no tab opens empty - not that every tab has a *style*.
     *
     * Lyrics is why the two are no longer the same question: it is a full screen with no rows of
     * its own beyond the background it can now be given, so requiring a style control would either
     * exclude it from the rail or demand a picker with nothing behind it.
     */
    @Test
    fun `every surface offers at least one control`() {
        PanelTarget.entries.forEach { target ->
            assertTrue(
                    "$target has no controls at all, so its card would open empty",
                    PanelEditorModel.specsFor(target).isNotEmpty())
        }
    }

    /** Every surface that paints a background can be given one of its own. */
    @Test
    fun `every surface offers a background of its own`() {
        PanelTarget.entries.forEach { target ->
            assertNotNull(
                    "$target cannot be given its own background",
                    PanelEditorModel.keyFor(target, PanelControl.SURFACE_BACKDROP))
        }
    }

    @Test
    fun `the seek tab separates the resting ring from the seek overlay`() {
        // They are two surfaces sharing one settings group: the ring is on screen always, the
        // overlay only during a drag. Collapsing them onto one control would make the Seek tab
        // claim that changing one changes the other.
        assertEquals(
                MiscPreferences.WEAR_PROGRESS_STYLE.key,
                PanelEditorModel.keyFor(PanelTarget.SEEK, PanelControl.RING_STYLE))
        assertEquals(
                MiscPreferences.WEAR_PROGRESS_LAYOUT.key,
                PanelEditorModel.keyFor(PanelTarget.SEEK, PanelControl.RING_LAYOUT))
        assertEquals(
                MiscPreferences.WEAR_SEEK_STYLE.key,
                PanelEditorModel.keyFor(PanelTarget.SEEK, PanelControl.STYLE))
    }

    @Test
    fun `only the queue has a row size and only it downloads covers`() {
        assertEquals(
                listOf(PanelTarget.QUEUE),
                PanelTarget.entries.filter {
                    PanelEditorModel.keyFor(it, PanelControl.ROW_SIZE) != null
                })
        assertEquals(
                listOf(PanelTarget.QUEUE),
                PanelTarget.entries.filter {
                    PanelEditorModel.keyFor(it, PanelControl.REMOTE_ARTWORK) != null
                })
    }

    @Test
    fun `the queue has no layout control`() {
        // Volume, seek and the quick panel each choose where they sit; a list does not.
        assertNull(PanelEditorModel.keyFor(PanelTarget.QUEUE, PanelControl.LAYOUT))
        listOf(PanelTarget.VOLUME, PanelTarget.SEEK, PanelTarget.QUICK_PANEL).forEach {
            assertNotNull("$it", PanelEditorModel.keyFor(it, PanelControl.LAYOUT))
        }
    }

    @Test
    fun `global controls navigate to the first tab`() {
        // Volume is the first button on the rail, so a search result for a page-wide control opens
        // with that rail untouched; its own PanelControl pulses the right button.
        assertEquals(
                PanelSearchTarget(PanelTarget.VOLUME, PanelControl.BACKDROP),
                PanelEditorModel.searchTargetFor(
                        MiscPreferences.WEAR_OVERLAY_BACKDROP_STYLE.key))
        assertEquals(
                PanelSearchTarget(PanelTarget.VOLUME, PanelControl.BLUR),
                PanelEditorModel.searchTargetFor(MiscPreferences.WEAR_OVERLAY_BLUR_RADIUS.key))
    }

    @Test
    fun `surface rows navigate to their own tab`() {
        assertEquals(
                PanelSearchTarget(PanelTarget.QUICK_PANEL, PanelControl.UP_NEXT_STYLE),
                PanelEditorModel.searchTargetFor(MiscPreferences.WEAR_UP_NEXT_PILL_STYLE.key))
        assertEquals(
                PanelSearchTarget(PanelTarget.QUEUE, PanelControl.STYLE),
                PanelEditorModel.searchTargetFor(MiscPreferences.WEAR_QUEUE_STYLE.key))
        assertNull(PanelEditorModel.searchTargetFor("wear_screen_face"))
    }

    @Test
    fun `stored defaults match the preference definitions`() {
        // The editor renders a control's default before anything is written, so a drifted default
        // shows one value here while the watch applies another.
        assertEquals(
                PanelValueSpec.Choice("glass"),
                PanelEditorModel.specFor(MiscPreferences.WEAR_VOLUME_STYLE.key)?.value)
        assertEquals(
                PanelValueSpec.Choice("follow"),
                PanelEditorModel.specFor(MiscPreferences.WEAR_OVERLAY_BACKDROP_STYLE.key)?.value)
        assertEquals(
                PanelValueSpec.Toggle(true),
                PanelEditorModel.specFor(MiscPreferences.WEAR_PROGRESS_GRADIENT.key)?.value)
        assertEquals(
                PanelValueSpec.Toggle(false),
                PanelEditorModel.specFor(MiscPreferences.WEAR_SHOW_UP_NEXT_PILL.key)?.value)
        // On by default - a streaming queue has no other cover source, so off leaves blank rows.
        assertEquals(
                PanelValueSpec.Toggle(true),
                PanelEditorModel.specFor("queue_remote_artwork")?.value)
    }

    /** The rows inside the six `cat_wf_panel*` categories, read straight from the XML. */
    private fun panelRowsInXml(): Set<String> {
        val xml = File("src/main/res/xml/watch_face_settings.xml").takeIf { it.exists() }
                ?: File("mobile/src/main/res/xml/watch_face_settings.xml")
        val text = xml.readText()

        val keys = mutableSetOf<String>()
        var category: String? = null
        Regex("""android:key="([^"]+)"""").findAll(text).forEach { match ->
            val key = match.groupValues[1]
            if (key.startsWith("cat_")) {
                category = key
                return@forEach
            }
            // The editor surface is the view, not one of the values it edits.
            if (key == "panel_editor_surface") return@forEach
            if (category?.startsWith("cat_wf_panel") == true) keys += key
        }
        return keys
    }
}
