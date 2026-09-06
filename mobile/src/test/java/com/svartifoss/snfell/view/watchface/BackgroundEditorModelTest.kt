package com.svartifoss.snfell.view.watchface

import com.svartifoss.snfell.common.FaceScopedPreferences
import com.svartifoss.snfell.common.MiscPreferences
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the Background page's editor against the preferences it is a view over.
 *
 * The guard the other four contextual pages already had, and the one page that lacked it - which
 * is precisely where the failure it describes then happened. The editor *replaces* the visible
 * rows without replacing their storage: the rows stay inflated behind it, owning persistence,
 * validation, their dialogs and their search metadata, and are hidden. So a key the model does not
 * claim is in the worst possible state - findable in settings search, and invisible on the only
 * page that owns it. `wear_album_art_source` shipped exactly that way.
 *
 * The reverse direction matters too: a model entry naming a key the XML no longer declares points
 * search and the editor at nothing.
 */
class BackgroundEditorModelTest {

    @Test
    fun `every declared background row is owned by the editor`() {
        val declared = backgroundRowsInXml()
        assertTrue("watch_face_settings.xml should declare background rows", declared.size > 8)

        val missing = declared - BackgroundEditorModel.keys
        assertTrue(
                "These Background rows have no editor control, so they are searchable but " +
                        "unreachable on the page that owns them: $missing",
                missing.isEmpty())
    }

    @Test
    fun `every editor key is a real preference`() {
        val orphans = BackgroundEditorModel.keys - backgroundRowsInXml()
        assertTrue(
                "These editor keys are not declared in watch_face_settings.xml: $orphans",
                orphans.isEmpty())
    }

    @Test
    fun `every owned key resolves to a control`() {
        BackgroundEditorModel.keys.forEach { key ->
            assertNotNull(
                    "$key is listed by the model but resolves to no control, so search cannot " +
                            "pulse anything for it",
                    BackgroundEditorModel.controlFor(key))
        }
    }

    @Test
    fun `the artwork controls are ordered source first`() {
        // Which picture, before how it is treated: the source is the only one of these that can
        // send the phone looking something up, and the rest merely compose whatever arrived.
        assertTrue(
                "the source must lead the artwork controls",
                BackgroundEditorModel.artworkKeys.first() ==
                        MiscPreferences.WEAR_ALBUM_ART_SOURCE.key)
    }

    @Test
    fun `the page edits appearance, so only the documented keys escape face scoping`() {
        // An unscoped key on an appearance surface repaints every face at once - the bug
        // AppearancePreferenceScopingTest describes. The only exceptions are the two that hold a
        // file this phone owns rather than a look, and they are named in the model so the
        // exemption lives in production code instead of in this assertion.
        val unscoped = BackgroundEditorModel.keys
                .filterNot(FaceScopedPreferences::isScoped)
                .toSet()

        assertEquals(BackgroundEditorModel.globalKeys, unscoped)
        assertTrue(
                MiscPreferences.CUSTOM_ALBUM_ART_IMAGE.key in BackgroundEditorModel.globalKeys)
        assertTrue(
                MiscPreferences.CUSTOM_ALBUM_ART_FOLDER.key in BackgroundEditorModel.globalKeys)
    }

    /** The rows declared under the Background categories, minus the editor surface itself. */
    private fun backgroundRowsInXml(): Set<String> {
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
            if (key == "background_editor_surface") return@forEach
            if (category == "cat_wf_background") keys += key
        }
        return keys
    }
}
