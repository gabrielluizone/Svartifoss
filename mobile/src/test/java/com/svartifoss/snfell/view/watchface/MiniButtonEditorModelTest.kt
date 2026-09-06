package com.svartifoss.snfell.view.watchface

import com.svartifoss.snfell.common.AppearanceNumericRanges
import com.svartifoss.snfell.common.FaceScopedPreferences
import com.svartifoss.snfell.common.MiniButtonPlacement
import com.svartifoss.snfell.common.MiscPreferences
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the Mini buttons page's compact editor against the preferences it is a view over.
 *
 * Same guard as [PanelEditorModelTest] and [PlayerEditorModelTest]: the editor replaces the visible
 * rows but not their storage, so a row the model forgets is unreachable on the page that owns it,
 * and a model entry naming a key the XML no longer declares points search at nothing.
 */
class MiniButtonEditorModelTest {

    @Test
    fun `every declared mini button and gesture row is owned by the editor`() {
        val declared = miniButtonRowsInXml()
        assertTrue("watch_face_settings.xml should declare these rows", declared.size > 5)

        val missing = declared - MiniButtonEditorModel.keys
        assertTrue(
                "These Mini buttons rows have no editor control, so the page cannot reach them: " +
                        "$missing",
                missing.isEmpty())
    }

    @Test
    fun `every editor key is a real preference`() {
        val orphans = MiniButtonEditorModel.keys - miniButtonRowsInXml()
        assertTrue(
                "These editor keys are not declared in watch_face_settings.xml: $orphans",
                orphans.isEmpty())
    }

    @Test
    fun `every stored setting is per-face`() {
        // WEAR_SCREEN_BUTTONS_OFFSET is the family member that stayed global while its four
        // siblings were scoped, which is the failure AppearancePreferenceScopingTest exists for.
        // Nothing this editor stores is exempt.
        val unscoped = MiniButtonEditorModel.keys
                .filter { MiniButtonEditorModel.specFor(it)?.persisted == true }
                .filterNot(FaceScopedPreferences::isScoped)
        assertTrue("These mini-button keys are not face-scoped: $unscoped", unscoped.isEmpty())
    }

    @Test
    fun `the hint row stores nothing`() {
        // It opens the Controls tab. Reading it through the preference store would persist a value
        // no watch has ever read.
        assertFalse(MiniButtonEditorModel.specFor("screen_buttons_hint")!!.persisted)
        assertEquals(
                "screen_buttons_hint",
                MiniButtonEditorModel.keyFor(MiniButtonControl.ASSIGN))
    }

    @Test
    fun `a face that hosts the row places and shapes the buttons itself`() {
        val hosting = "chat"
        assertTrue("Chat is expected to host the row", MiniButtonPlacement.isHostedByFace(hosting))
        listOf(MiniButtonControl.ARRANGEMENT, MiniButtonControl.SHAPE).forEach { control ->
            assertFalse("$control", MiniButtonEditorModel.appliesToFace(control, hosting))
            assertTrue("$control", MiniButtonEditorModel.appliesToFace(control, "classic"))
        }
    }

    @Test
    fun `background and opacity still reach a hosted row`() {
        // Both apply through the shared MiniButtonSurfaces, so hiding them beside the arrangement
        // would take away controls that do work - the opposite of the rule that hid the other two.
        listOf(
                MiniButtonControl.BACKGROUND,
                MiniButtonControl.OPACITY,
                MiniButtonControl.MODE
        ).forEach { control ->
            assertTrue("$control", MiniButtonEditorModel.appliesToFace(control, "chat"))
        }
    }

    @Test
    fun `the gestures switch shares the page but not the row card`() {
        assertEquals(
                listOf(MiscPreferences.WEAR_GESTURES_MODE.key),
                MiniButtonEditorModel.specsFor(MiniButtonSlot.GESTURES).map { it.key })
        assertFalse(
                MiscPreferences.WEAR_GESTURES_MODE.key in
                        MiniButtonEditorModel.specsFor(MiniButtonSlot.ROW).map { it.key })
    }

    @Test
    fun `every row card control has a short label`() {
        // Four of the five preference titles begin "Mini buttons", on a page already called Mini
        // buttons. The gesture row is the exception: its card heading already names it.
        MiniButtonEditorModel.specsFor(MiniButtonSlot.ROW).forEach { spec ->
            assertNotNull("${spec.key} needs a short row label", spec.labelRes)
        }
        assertNull(
                MiniButtonEditorModel.specFor(MiscPreferences.WEAR_GESTURES_MODE.key)?.labelRes)
    }

    @Test
    fun `the opacity slider cannot reach a value the typed field would reject`() {
        val key = MiscPreferences.WEAR_SCREEN_BUTTONS_OPACITY.key
        val number = MiniButtonEditorModel.specFor(key)?.value as? MiniButtonValueSpec.Number
        assertNotNull("$key should be a slider", number)
        assertEquals(AppearanceNumericRanges.rangeFor(key), number!!.range)
    }

    @Test
    fun `stored defaults match the preference definitions`() {
        // The editor renders a control's default before anything is written, so a drifted default
        // shows one value here while the watch applies another.
        assertEquals(
                MiniButtonValueSpec.Choice("always"),
                MiniButtonEditorModel.specFor(MiscPreferences.WEAR_MINI_BUTTONS_MODE.key)?.value)
        assertEquals(
                MiniButtonValueSpec.Choice("flat"),
                MiniButtonEditorModel.specFor(
                        MiscPreferences.WEAR_SCREEN_BUTTONS_CURVE_STYLE.key)?.value)
        assertEquals(
                MiniButtonValueSpec.Choice("glass"),
                MiniButtonEditorModel.specFor(MiscPreferences.WEAR_SCREEN_BUTTONS_BG.key)?.value)
        assertEquals(
                MiniButtonValueSpec.Number(100, 0..100),
                MiniButtonEditorModel.specFor(
                        MiscPreferences.WEAR_SCREEN_BUTTONS_OPACITY.key)?.value)
    }

    /** The rows inside `cat_wf_mini_buttons` and `cat_wf_gestures`, read straight from the XML. */
    private fun miniButtonRowsInXml(): Set<String> {
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
            if (key == "mini_button_editor_surface") return@forEach
            if (category in setOf("cat_wf_mini_buttons", "cat_wf_gestures")) keys += key
        }
        return keys
    }
}
