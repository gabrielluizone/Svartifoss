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
    fun `the gestures setting shares the page but not the buttons' cards`() {
        assertEquals(
                listOf(MiscPreferences.WEAR_GESTURES_MODE.key),
                MiniButtonEditorModel.specsFor(MiniButtonSlot.GESTURES).map { it.key })
        listOf(MiniButtonSlot.BUTTONS, MiniButtonSlot.APPEARANCE).forEach { slot ->
            assertFalse(
                    MiscPreferences.WEAR_GESTURES_MODE.key in
                            MiniButtonEditorModel.specsFor(slot).map { it.key })
        }
    }

    /**
     * The first card used to hold four different things under one heading: the way out to the
     * Controls tab, when the row appears, and how it looks. Only the last is style.
     */
    @Test
    fun `the cards are the buttons, their appearance and the gestures, in that order`() {
        assertEquals(
                listOf(MiniButtonSlot.BUTTONS, MiniButtonSlot.APPEARANCE, MiniButtonSlot.GESTURES),
                MiniButtonSlot.entries)

        assertEquals(
                listOf(MiniButtonControl.ASSIGN, MiniButtonControl.MODE),
                MiniButtonEditorModel.specsFor(MiniButtonSlot.BUTTONS).map { it.control })
        assertEquals(
                listOf(
                        MiniButtonControl.ARRANGEMENT,
                        MiniButtonControl.SHAPE,
                        MiniButtonControl.BACKGROUND,
                        MiniButtonControl.OPACITY),
                MiniButtonEditorModel.specsFor(MiniButtonSlot.APPEARANCE).map { it.control })
    }

    @Test
    fun `only the gestures card goes without a heading, because its row names itself`() {
        assertNotNull(MiniButtonSlot.BUTTONS.headingRes)
        assertNotNull(MiniButtonSlot.APPEARANCE.headingRes)
        // A heading above a card holding one row titled the same thing would say it twice.
        assertNull(MiniButtonSlot.GESTURES.headingRes)
    }

    @Test
    fun `every control lives in exactly one card`() {
        val controls = MiniButtonEditorModel.specs.map { it.control }
        assertEquals("Each control should be declared once", controls.toSet().size, controls.size)
        assertEquals(
                "Every control should have a spec",
                MiniButtonControl.entries.toSet(),
                controls.toSet())
    }

    @Test
    fun `the way out to the Controls tab is the first thing on the page`() {
        // Assigning what a mini button does happens there, not here, and that is the first
        // question anybody arriving on this page has.
        assertEquals(
                MiniButtonControl.ASSIGN,
                MiniButtonEditorModel.specsFor(MiniButtonSlot.BUTTONS).first().control)
    }

    @Test
    fun `the appearance rows carry a short label and the others use their own title`() {
        // Four of the five preference titles begin "Mini buttons", on a card already headed
        // Appearance. "Show mini buttons" and "Screen gestures" are the exceptions: each is a
        // whole phrase with no such prefix, and "Show" alone would have no subject.
        MiniButtonEditorModel.specsFor(MiniButtonSlot.APPEARANCE).forEach { spec ->
            assertNotNull("${spec.key} needs a short row label", spec.labelRes)
        }
        assertNull(MiniButtonEditorModel.specFor(MiscPreferences.WEAR_MINI_BUTTONS_MODE.key)?.labelRes)
        assertNull(MiniButtonEditorModel.specFor(MiscPreferences.WEAR_GESTURES_MODE.key)?.labelRes)
    }

    /**
     * Two rows cannot be understood from their title and value: the link, which has no value at
     * all, and the gestures, whose title does not say which ones. The style rows are named by their
     * own value and are deliberately left without a sentence.
     */
    @Test
    fun `only the link and the gestures carry a description`() {
        val described = MiniButtonEditorModel.specs
                .filter { it.descriptionRes != null }
                .map { it.control }
                .toSet()
        assertEquals(setOf(MiniButtonControl.ASSIGN, MiniButtonControl.GESTURES_MODE), described)
    }

    @Test
    fun `a hosting face loses the arrangement and the shape but keeps the rest of the appearance card`() {
        val hosting = "chat"
        val offered = MiniButtonEditorModel.visibleIn(MiniButtonSlot.APPEARANCE, hosting)
                .map { it.control }
        assertEquals(listOf(MiniButtonControl.BACKGROUND, MiniButtonControl.OPACITY), offered)
        // ...and every other face is offered all four.
        assertEquals(
                4,
                MiniButtonEditorModel.visibleIn(MiniButtonSlot.APPEARANCE, "classic").size)
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
