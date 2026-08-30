package com.svartifoss.snfell.view.watchface

import com.svartifoss.snfell.common.FaceScopedPreferences
import com.svartifoss.snfell.common.MiscPreferences
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the Color page's compact editor against the preferences it is a view over.
 *
 * The editor replaces the visible rows but not their storage, so the failure this guards is silent
 * in both directions: a colour preference the model forgets is unreachable on the page that owns
 * it, and a model entry naming a key the XML no longer declares points search at nothing.
 */
class ColorEditorModelTest {

    @Test
    fun `every declared colour row is owned by the editor`() {
        val declared = colorRowsInXml()
        assertTrue("watch_face_settings.xml should declare colour rows", declared.size > 15)

        val missing = declared - ColorEditorModel.keys
        assertTrue(
                "These Colors rows have no editor control, so the Color page cannot reach them: " +
                        "$missing",
                missing.isEmpty())
    }

    @Test
    fun `every editor key is a real preference`() {
        val declared = colorRowsInXml()
        val orphans = ColorEditorModel.keys - declared
        assertTrue(
                "These editor keys are not declared in watch_face_settings.xml: $orphans",
                orphans.isEmpty())
    }

    @Test
    fun `every editor key is face-scoped`() {
        // The Color page *is* an appearance surface, so an unscoped key here would repaint every
        // face at once - the rule AppearancePreferenceScopingTest enforces for the XML.
        val unscoped = ColorEditorModel.keys.filterNot(FaceScopedPreferences::isScoped)
        assertTrue("Colour editor keys must be per-face: $unscoped", unscoped.isEmpty())
    }

    @Test
    fun `every target offers a colour mode`() {
        ColorTarget.entries.forEach { target ->
            assertNotNull(
                    "$target has no colour mode, so its card would open empty",
                    ColorEditorModel.keyFor(target, ColorControl.MODE))
        }
    }

    @Test
    fun `only the clock carries an opacity control`() {
        val withOpacity = ColorTarget.entries.filter {
            ColorEditorModel.keyFor(it, ColorControl.OPACITY) != null
        }
        assertEquals(listOf(ColorTarget.CLOCK), withOpacity)
    }

    @Test
    fun `only text elements carry adaptive contrast`() {
        // Progress, volume and the quick panel are surfaces, not text, so there is no line to
        // measure against the artwork - offering the switch beside them would promise nothing.
        val withContrast = ColorTarget.entries.filter {
            ColorEditorModel.keyFor(it, ColorControl.ADAPTIVE_CONTRAST) != null
        }
        assertEquals(
                listOf(ColorTarget.TITLE, ColorTarget.ARTIST, ColorTarget.CLOCK),
                withContrast)
    }

    @Test
    fun `global palette controls navigate to the first tab`() {
        // Title is the first button on the rail, so a search result for a global control opens the
        // page with that rail untouched; its own ColorControl is what pulses the right button.
        listOf(
                MiscPreferences.WEAR_ALBUM_ACCENT_SOURCE.key to ColorControl.ACCENT_SOURCE,
                MiscPreferences.WEAR_COLOR_TREATMENT.key to ColorControl.TREATMENT,
                MiscPreferences.WEAR_COLOR_MODIFIER.key to ColorControl.MODIFIER,
                MiscPreferences.WEAR_COLOR_HUE_SHIFT.key to ColorControl.HUE_SHIFT,
                MiscPreferences.WEAR_NORMAL_COLOR.key to ColorControl.GLOBAL_COLOR,
                MiscPreferences.WEAR_NORMAL_COLOR_MULTI.key to ColorControl.PALETTE
        ).forEach { (key, control) ->
            assertEquals(
                    ColorSearchTarget(ColorTarget.TITLE, control),
                    ColorEditorModel.searchTargetFor(key))
        }
    }

    @Test
    fun `element overrides navigate to their own tab`() {
        assertEquals(
                ColorSearchTarget(ColorTarget.ARTIST, ColorControl.CUSTOM_COLOR),
                ColorEditorModel.searchTargetFor(MiscPreferences.WEAR_ARTIST_CUSTOM_COLOR.key))
        assertEquals(
                ColorSearchTarget(ColorTarget.QUICK_PANEL, ColorControl.MODE),
                ColorEditorModel.searchTargetFor(
                        MiscPreferences.WEAR_QUICK_PANEL_COLOR_MODE.key))
        assertNull(ColorEditorModel.searchTargetFor("wear_screen_face"))
    }

    @Test
    fun `stored defaults match the preference definitions`() {
        // The editor renders a control's default before anything is written, so a drifted default
        // here shows one value while the watch applies another.
        assertEquals(
                ColorValueSpec.Choice(MiscPreferences.TITLE_COLOR_FACE_DEFAULT),
                ColorEditorModel.specFor(MiscPreferences.WEAR_TITLE_COLOR_MODE.key)?.value)
        assertEquals(
                ColorValueSpec.Toggle(true),
                ColorEditorModel.specFor(MiscPreferences.WEAR_NORMAL_COLOR_MULTI.key)?.value)
        assertEquals(
                ColorValueSpec.Number(60, ColorEditorModel.CLOCK_OPACITY_RANGE),
                ColorEditorModel.specFor(MiscPreferences.WEAR_CLOCK_OPACITY.key)?.value)
    }

    @Test
    fun `a full hue turn is not a separate stored value`() {
        // 0 and 360 render identically, so allowing both would give two values for one look.
        assertEquals(0, ColorEditorModel.HUE_SHIFT_RANGE.first)
        assertEquals(359, ColorEditorModel.HUE_SHIFT_RANGE.last)
    }

    /** The rows inside the four `cat_wf_colors*` categories, read straight from the XML. */
    private fun colorRowsInXml(): Set<String> {
        val xml = File(
                "src/main/res/xml/watch_face_settings.xml").takeIf { it.exists() }
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
            if (key == "color_editor_surface") return@forEach
            if (category?.startsWith("cat_wf_colors") == true) keys += key
        }
        return keys
    }
}
