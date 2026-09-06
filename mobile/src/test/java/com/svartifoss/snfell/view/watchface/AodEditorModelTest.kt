package com.svartifoss.snfell.view.watchface

import com.svartifoss.snfell.common.AppearanceNumericRanges
import com.svartifoss.snfell.common.FaceScopedPreferences
import com.svartifoss.snfell.common.MiscPreferences
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the Always-on page's compact editor against the preferences it is a view over.
 *
 * Same guard as [PanelEditorModelTest] and [PlayerEditorModelTest]: the editor replaces the visible
 * rows but not their storage, so a row the model forgets is unreachable on the page that owns it,
 * and a model entry naming a key the XML no longer declares points search at nothing.
 */
class AodEditorModelTest {

    @Test
    fun `every declared ambient row is owned by the editor`() {
        val declared = aodRowsInXml()
        assertTrue("watch_face_settings.xml should declare ambient rows", declared.size > 8)

        val missing = declared - AodEditorModel.keys
        assertTrue(
                "These Always-on rows have no editor control, so the page cannot reach them: " +
                        "$missing",
                missing.isEmpty())
    }

    @Test
    fun `every editor key is a real preference`() {
        val orphans = AodEditorModel.keys - aodRowsInXml()
        assertTrue(
                "These editor keys are not declared in watch_face_settings.xml: $orphans",
                orphans.isEmpty())
    }

    @Test
    fun `every ambient setting is per-face`() {
        // The Always-on page is an appearance surface, so an unscoped key repaints every face at
        // once - the bug AppearancePreferenceScopingTest describes. Nothing here is exempt.
        val unscoped = AodEditorModel.keys.filterNot(FaceScopedPreferences::isScoped)
        assertTrue("These ambient keys are not face-scoped: $unscoped", unscoped.isEmpty())
    }

    @Test
    fun `follow resolves to the awake face`() {
        // Every style rule is about the style actually drawn: judging the stored "follow" as if it
        // were a face key would hide the transport controls on every install that never changed it.
        assertEquals("carousel", AodEditorModel.effectiveStyle("follow", "carousel"))
        assertEquals("chrono", AodEditorModel.effectiveStyle("chrono", "carousel"))
    }

    @Test
    fun `only a visual style offers transport progress and up next`() {
        listOf(AodControl.SHOW_TRANSPORT, AodControl.SHOW_PROGRESS, AodControl.SHOW_PILLS)
                .forEach { control ->
                    assertTrue("$control", AodEditorModel.appliesToStyle(control, "expressive"))
                    assertFalse("$control", AodEditorModel.appliesToStyle(control, "classic"))
                    assertFalse("$control", AodEditorModel.appliesToStyle(control, "chrono"))
                }
    }

    @Test
    fun `an unknown or removed style falls on the safe classic path`() {
        // The allow-list exists so a value arriving from a backup, a community theme or a newer
        // build - legacy "minimal" is the known one - is treated as Classic rather than as a face
        // whose controls it does not draw.
        assertFalse(AodEditorModel.appliesToStyle(AodControl.SHOW_TRANSPORT, "minimal"))
        assertFalse(AodEditorModel.appliesToStyle(AodControl.SHOW_TRANSPORT, "not_a_style"))
    }

    @Test
    fun `the artless styles hide all three artwork controls`() {
        AodEditorModel.ARTLESS_STYLES.forEach { style ->
            listOf(AodControl.SHOW_ART, AodControl.ART_TREATMENT, AodControl.ART_OPACITY)
                    .forEach { control ->
                        assertFalse(
                                "$control should not apply to $style",
                                AodEditorModel.appliesToStyle(control, style))
                    }
        }
        assertTrue(AodEditorModel.appliesToStyle(AodControl.SHOW_ART, "classic"))
    }

    @Test
    fun `the clock and track info survive every style`() {
        // Both are drawn by the classic ambient screen as well as by every Compose one, so a style
        // gate on either would switch off a control that still has something to hide.
        listOf("classic", "chrono", "eclipse", "expressive").forEach { style ->
            assertTrue(style, AodEditorModel.appliesToStyle(AodControl.SHOW_CLOCK, style))
            assertTrue(style, AodEditorModel.appliesToStyle(AodControl.SHOW_TRACK_INFO, style))
        }
    }

    @Test
    fun `only the style-gated controls are swept by the screen`() {
        // The sweep in updateAodDetailVisibility writes isVisible for exactly this set. The custom
        // tint swatch must stay out of it: its visibility belongs to initAccentColorTarget, and a
        // blanket sweep would reveal it under every colour mode.
        assertFalse(AodControl.CUSTOM_COLOR in AodEditorModel.STYLE_GATED_CONTROLS)
        assertFalse(AodControl.STYLE in AodEditorModel.STYLE_GATED_CONTROLS)
        assertFalse(AodControl.INTENSITY in AodEditorModel.STYLE_GATED_CONTROLS)

        val gateable = AodControl.entries.filter { control ->
            AodEditorModel.VISUAL_STYLES.plus(AodEditorModel.ARTLESS_STYLES).plus("classic")
                    .any { !AodEditorModel.appliesToStyle(control, it) }
        }
        assertEquals(AodEditorModel.STYLE_GATED_CONTROLS, gateable.toSet())
    }

    @Test
    fun `every element renders as a chip and every chip has a short label`() {
        val elements = AodEditorModel.specsFor(AodSlot.ELEMENT)
        assertEquals(6, elements.size)
        elements.forEach { spec ->
            assertTrue("${spec.key} must be a toggle", spec.value is AodValueSpec.Toggle)
            assertNotNull(
                    "${spec.key} needs a short chip label - every ambient title is a sentence " +
                            "ending in \"on always-on display\"",
                    spec.labelRes)
        }
    }

    @Test
    fun `the sliders cannot reach a value the typed field would reject`() {
        // The editor offers a slider where the legacy row offered a free-typing box, so its bounds
        // have to be the registry's rather than a second set invented here.
        listOf(
                MiscPreferences.WEAR_AOD_INTENSITY.key,
                MiscPreferences.AMBIENT_ALBUM_ART_OPACITY.key
        ).forEach { key ->
            val number = AodEditorModel.specFor(key)?.value as? AodValueSpec.Number
            assertNotNull("$key should be a slider", number)
            assertEquals(key, AppearanceNumericRanges.rangeFor(key), number!!.range)
        }
    }

    @Test
    fun `stored defaults match the preference definitions`() {
        // The editor renders a control's default before anything is written, so a drifted default
        // shows one value here while the watch applies another.
        assertEquals(
                AodValueSpec.Choice("follow"),
                AodEditorModel.specFor(MiscPreferences.WEAR_AOD_STYLE.key)?.value)
        assertEquals(
                AodValueSpec.Choice("white"),
                AodEditorModel.specFor(MiscPreferences.WEAR_AOD_COLOR_MODE.key)?.value)
        assertEquals(
                AodValueSpec.Toggle(true),
                AodEditorModel.specFor(MiscPreferences.WEAR_AOD_SHOW_ART.key)?.value)
        assertEquals(
                AodValueSpec.Number(100, 20..100),
                AodEditorModel.specFor(MiscPreferences.WEAR_AOD_INTENSITY.key)?.value)
    }

    /** The rows inside the `cat_wf_aod` category, read straight from the XML. */
    private fun aodRowsInXml(): Set<String> {
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
            if (key == "aod_editor_surface") return@forEach
            if (category == "cat_wf_aod") keys += key
        }
        return keys
    }
}
