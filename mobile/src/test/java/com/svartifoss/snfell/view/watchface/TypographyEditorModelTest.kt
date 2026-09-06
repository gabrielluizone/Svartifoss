package com.svartifoss.snfell.view.watchface

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class TypographyEditorModelTest {

    @Test
    fun `every legacy Typography row has exactly one editor spec`() {
        val xmlRows = typographyRowsFromXml()

        assertEquals(81, xmlRows.size)
        assertEquals(xmlRows.keys, TypographyEditorModel.keys)
        assertEquals(81, TypographyEditorModel.specs.size)
    }

    @Test
    fun `defaults keep the existing preference contract`() {
        val expectedChoices = mapOf(
                "wear_font" to "google_sans",
                "wear_title_font" to "follow",
                "wear_title_text_mode" to "smart",
                "wear_artist_text_mode" to "static",
                "wear_title_text_case" to "normal",
                "wear_title_shadow_style" to "none",
                "wear_title_shadow_color_mode" to "black",
                "wear_artist_shadow_style" to "none",
                "wear_artist_shadow_color_mode" to "black",
                "wear_title_outline_style" to "none",
                "wear_title_outline_color_mode" to "black",
                "wear_artist_outline_style" to "none",
                "wear_artist_outline_color_mode" to "black",
                "wear_title_text_bg_style" to "none",
                "wear_title_text_bg_color_mode" to "black",
                "wear_artist_text_bg_style" to "none",
                "wear_artist_text_bg_color_mode" to "black",
                "wear_artist_font" to "follow",
                "wear_artist_text_case" to "normal",
                "wear_track_time_font" to "follow",
                "wear_clock_font" to "follow",
                "wear_lyrics_font" to "follow")
        // The picked colours behind the three text effects. A distinct kind from the mode rows
        // beside them: both persist a string, and treating one as the other is what left the
        // editor resolving every colour control to its mode row and no way to reach the hex.
        val expectedHex = hexColorDefaults()
        val expectedToggles = mapOf(
                "wear_font_all_screens" to false,
                "wear_show_track_title" to true,
                "wear_title_font_italic" to false,
                "wear_show_track_artist" to true,
                "wear_artist_font_italic" to false,
                "wear_track_time_font_italic" to false,
                "wear_clock_font_italic" to false)
        val expectedNumbers = mapOf(
                "wear_title_font_weight" to NumberContract(400, 1..1000),
                "wear_title_font_scale" to NumberContract(100, 1..300),
                "wear_title_font_opacity" to NumberContract(100, 20..100),
                "wear_title_font_tracking" to NumberContract(0, -5..20),
                "wear_title_shadow_strength" to NumberContract(100, 0..200),
                "wear_title_text_bg_opacity" to NumberContract(100, 0..100),
                "wear_font_flex_width" to NumberContract(100, 25..151),
                "wear_font_flex_optical_size" to NumberContract(18, 6..144),
                "wear_font_flex_grade" to NumberContract(0, 0..100),
                "wear_font_flex_roundness" to NumberContract(0, 0..100),
                "wear_artist_font_weight" to NumberContract(400, 1..1000),
                "wear_artist_font_scale" to NumberContract(100, 1..300),
                "wear_artist_font_opacity" to NumberContract(100, 20..100),
                "wear_artist_font_tracking" to NumberContract(0, -5..20),
                "wear_artist_shadow_strength" to NumberContract(100, 0..200),
                "wear_artist_text_bg_opacity" to NumberContract(100, 0..100),
                "wear_track_time_font_weight" to NumberContract(400, 1..1000),
                "wear_track_time_font_scale" to NumberContract(100, 1..300),
                "wear_track_time_font_opacity" to NumberContract(100, 20..100),
                "wear_track_time_font_tracking" to NumberContract(0, -5..20),
                "wear_clock_font_weight" to NumberContract(400, 1..1000),
                "wear_clock_font_scale" to NumberContract(100, 1..300),
                "wear_clock_font_tracking" to NumberContract(0, -5..20),
                "wear_source_icon_scale" to NumberContract(100, 1..300),
                "wear_source_icon_opacity" to NumberContract(100, 20..100)) +
                flexAxisContracts("wear_title_font_flex") +
                flexAxisContracts("wear_artist_font_flex") +
                flexAxisContracts("wear_clock_font_flex") +
                flexAxisContracts("wear_lyrics_font_flex") +
                flexAxisContracts("wear_track_time_font_flex")

        expectedChoices.forEach { (key, expected) ->
            assertEquals(
                    "$key choice default",
                    TypographyValueSpec.Choice(expected),
                    TypographyEditorModel.specFor(key)?.value)
        }
        expectedHex.forEach { (key, expected) ->
            assertEquals(
                    "$key hex default",
                    TypographyValueSpec.Hex(expected),
                    TypographyEditorModel.specFor(key)?.value)
        }
        expectedToggles.forEach { (key, expected) ->
            assertEquals(
                    "$key toggle default",
                    TypographyValueSpec.Toggle(expected),
                    TypographyEditorModel.specFor(key)?.value)
        }
        expectedNumbers.forEach { (key, expected) ->
            assertEquals(
                    "$key numeric contract",
                    TypographyValueSpec.Number(expected.defaultValue, expected.range),
                    TypographyEditorModel.specFor(key)?.value)
        }

        val typedKeys = expectedChoices.keys + expectedHex.keys + expectedToggles.keys +
                expectedNumbers.keys
        assertEquals(
                setOf("wear_flex_axes_hint"),
                TypographyEditorModel.keys - typedKeys)
    }

    @Test
    fun `model defaults stay aligned with Typography XML defaults`() {
        val xmlRows = typographyRowsFromXml()

        TypographyEditorModel.specs.forEach { spec ->
            val xmlDefault = xmlRows.getValue(spec.key)
            when (val value = spec.value) {
                is TypographyValueSpec.Choice ->
                    assertEquals("${spec.key} XML default", value.defaultValue, xmlDefault)
                is TypographyValueSpec.Hex ->
                    assertEquals("${spec.key} XML default", value.defaultValue, xmlDefault)
                is TypographyValueSpec.Toggle ->
                    assertEquals("${spec.key} XML default", value.defaultValue.toString(), xmlDefault)
                is TypographyValueSpec.Number ->
                    assertEquals("${spec.key} XML default", value.defaultValue.toString(), xmlDefault)
                TypographyValueSpec.Information ->
                    assertEquals("${spec.key} must not declare a stored default", "", xmlDefault)
            }
        }
    }

    @Test
    fun `search resolves every old row to its editor target and control`() {
        val expected = mapOf(
                "wear_font" to destination(TypographyTarget.TITLE, TypographyControl.FONT),
                "wear_font_all_screens" to destination(
                        TypographyTarget.TITLE, TypographyControl.FONT_SCOPE),
                "wear_title_font" to destination(
                        TypographyTarget.TITLE, TypographyControl.ELEMENT_FONT),
                "wear_show_track_title" to destination(
                        TypographyTarget.TITLE, TypographyControl.VISIBILITY),
                "wear_title_text_mode" to destination(
                        TypographyTarget.TITLE, TypographyControl.TEXT_BEHAVIOR),
                "wear_artist_text_mode" to destination(
                        TypographyTarget.ARTIST, TypographyControl.TEXT_BEHAVIOR),
                "wear_title_text_case" to destination(
                        TypographyTarget.TITLE, TypographyControl.CASE),
                "wear_title_shadow_style" to destination(
                        TypographyTarget.TITLE, TypographyControl.SHADOW),
                // The colour mode and the hex behind it resolve to the same control, the way the
                // four Flex axes do: one decision, two rows of storage.
                "wear_title_shadow_color_mode" to destination(
                        TypographyTarget.TITLE, TypographyControl.SHADOW_COLOR),
                "wear_title_shadow_custom_color" to destination(
                        TypographyTarget.TITLE, TypographyControl.SHADOW_COLOR, hex = true),
                "wear_title_shadow_strength" to destination(
                        TypographyTarget.TITLE, TypographyControl.SHADOW_STRENGTH),
                "wear_title_outline_style" to destination(
                        TypographyTarget.TITLE, TypographyControl.OUTLINE),
                "wear_title_outline_color_mode" to destination(
                        TypographyTarget.TITLE, TypographyControl.OUTLINE_COLOR),
                "wear_title_outline_custom_color" to destination(
                        TypographyTarget.TITLE, TypographyControl.OUTLINE_COLOR, hex = true),
                "wear_title_text_bg_style" to destination(
                        TypographyTarget.TITLE, TypographyControl.BACKDROP),
                "wear_title_text_bg_color_mode" to destination(
                        TypographyTarget.TITLE, TypographyControl.BACKDROP_COLOR),
                "wear_title_text_bg_custom_color" to destination(
                        TypographyTarget.TITLE, TypographyControl.BACKDROP_COLOR, hex = true),
                "wear_title_text_bg_opacity" to destination(
                        TypographyTarget.TITLE, TypographyControl.BACKDROP_OPACITY),
                "wear_title_font_weight" to destination(
                        TypographyTarget.TITLE, TypographyControl.WEIGHT),
                "wear_title_font_italic" to destination(
                        TypographyTarget.TITLE, TypographyControl.ITALIC),
                "wear_title_font_scale" to destination(
                        TypographyTarget.TITLE, TypographyControl.SIZE),
                "wear_title_font_opacity" to destination(
                        TypographyTarget.TITLE, TypographyControl.OPACITY),
                "wear_title_font_tracking" to destination(
                        TypographyTarget.TITLE, TypographyControl.TRACKING),
                "wear_flex_axes_hint" to destination(
                        TypographyTarget.TITLE, TypographyControl.GLOBAL_FLEX),
                "wear_font_flex_width" to destination(
                        TypographyTarget.TITLE, TypographyControl.GLOBAL_FLEX),
                "wear_font_flex_optical_size" to destination(
                        TypographyTarget.TITLE, TypographyControl.GLOBAL_FLEX),
                "wear_font_flex_grade" to destination(
                        TypographyTarget.TITLE, TypographyControl.GLOBAL_FLEX),
                "wear_font_flex_roundness" to destination(
                        TypographyTarget.TITLE, TypographyControl.GLOBAL_FLEX),
                "wear_show_track_artist" to destination(
                        TypographyTarget.ARTIST, TypographyControl.VISIBILITY),
                "wear_artist_font" to destination(
                        TypographyTarget.ARTIST, TypographyControl.ELEMENT_FONT),
                "wear_artist_font_weight" to destination(
                        TypographyTarget.ARTIST, TypographyControl.WEIGHT),
                "wear_artist_font_italic" to destination(
                        TypographyTarget.ARTIST, TypographyControl.ITALIC),
                "wear_artist_font_scale" to destination(
                        TypographyTarget.ARTIST, TypographyControl.SIZE),
                "wear_artist_font_opacity" to destination(
                        TypographyTarget.ARTIST, TypographyControl.OPACITY),
                "wear_artist_font_tracking" to destination(
                        TypographyTarget.ARTIST, TypographyControl.TRACKING),
                "wear_artist_text_case" to destination(
                        TypographyTarget.ARTIST, TypographyControl.CASE),
                "wear_artist_shadow_style" to destination(
                        TypographyTarget.ARTIST, TypographyControl.SHADOW),
                "wear_artist_shadow_color_mode" to destination(
                        TypographyTarget.ARTIST, TypographyControl.SHADOW_COLOR),
                "wear_artist_shadow_custom_color" to destination(
                        TypographyTarget.ARTIST, TypographyControl.SHADOW_COLOR, hex = true),
                "wear_artist_shadow_strength" to destination(
                        TypographyTarget.ARTIST, TypographyControl.SHADOW_STRENGTH),
                "wear_artist_outline_style" to destination(
                        TypographyTarget.ARTIST, TypographyControl.OUTLINE),
                "wear_artist_outline_color_mode" to destination(
                        TypographyTarget.ARTIST, TypographyControl.OUTLINE_COLOR),
                "wear_artist_outline_custom_color" to destination(
                        TypographyTarget.ARTIST, TypographyControl.OUTLINE_COLOR, hex = true),
                "wear_artist_text_bg_style" to destination(
                        TypographyTarget.ARTIST, TypographyControl.BACKDROP),
                "wear_artist_text_bg_color_mode" to destination(
                        TypographyTarget.ARTIST, TypographyControl.BACKDROP_COLOR),
                "wear_artist_text_bg_custom_color" to destination(
                        TypographyTarget.ARTIST, TypographyControl.BACKDROP_COLOR, hex = true),
                "wear_artist_text_bg_opacity" to destination(
                        TypographyTarget.ARTIST, TypographyControl.BACKDROP_OPACITY),
                "wear_track_time_font" to destination(
                        TypographyTarget.TRACK_TIME, TypographyControl.ELEMENT_FONT),
                "wear_track_time_font_weight" to destination(
                        TypographyTarget.TRACK_TIME, TypographyControl.WEIGHT),
                "wear_track_time_font_italic" to destination(
                        TypographyTarget.TRACK_TIME, TypographyControl.ITALIC),
                "wear_track_time_font_scale" to destination(
                        TypographyTarget.TRACK_TIME, TypographyControl.SIZE),
                "wear_track_time_font_opacity" to destination(
                        TypographyTarget.TRACK_TIME, TypographyControl.OPACITY),
                "wear_track_time_font_tracking" to destination(
                        TypographyTarget.TRACK_TIME, TypographyControl.TRACKING),
                "wear_clock_font" to destination(
                        TypographyTarget.CLOCK, TypographyControl.ELEMENT_FONT),
                "wear_clock_font_weight" to destination(
                        TypographyTarget.CLOCK, TypographyControl.WEIGHT),
                "wear_clock_font_italic" to destination(
                        TypographyTarget.CLOCK, TypographyControl.ITALIC),
                "wear_clock_font_scale" to destination(
                        TypographyTarget.CLOCK, TypographyControl.SIZE),
                "wear_clock_font_tracking" to destination(
                        TypographyTarget.CLOCK, TypographyControl.TRACKING),
                "wear_source_icon_scale" to destination(
                        TypographyTarget.ICON, TypographyControl.SIZE),
                "wear_source_icon_opacity" to destination(
                        TypographyTarget.ICON, TypographyControl.OPACITY),
                "wear_lyrics_font" to destination(
                        TypographyTarget.LYRICS, TypographyControl.ELEMENT_FONT)) +
                flexAxisDestinations("wear_title_font_flex", TypographyTarget.TITLE) +
                flexAxisDestinations("wear_artist_font_flex", TypographyTarget.ARTIST) +
                flexAxisDestinations("wear_clock_font_flex", TypographyTarget.CLOCK) +
                flexAxisDestinations("wear_lyrics_font_flex", TypographyTarget.LYRICS) +
                flexAxisDestinations("wear_track_time_font_flex", TypographyTarget.TRACK_TIME)

        assertEquals(TypographyEditorModel.keys, expected.keys)
        expected.forEach { (key, destination) ->
            assertEquals(key, destination, TypographyEditorModel.searchTargetFor(key))
        }
        assertNull(TypographyEditorModel.searchTargetFor("not_a_typography_key"))
    }

    @Test
    fun `only the Flex explanation is non-persistent`() {
        val hint = TypographyEditorModel.specFor("wear_flex_axes_hint")!!

        assertFalse(hint.persisted)
        assertTrue(hint.value is TypographyValueSpec.Information)
        assertEquals(
                setOf("wear_flex_axes_hint"),
                TypographyEditorModel.specs.filterNot { it.persisted }.map { it.key }.toSet())
        assertEquals(80, TypographyEditorModel.specs.count { it.persisted })
    }

    @Test
    fun `target groups keep the compact editor shape`() {
        val expectedCounts = mapOf(
                TypographyTarget.TITLE to 31,
                TypographyTarget.ARTIST to 24,
                TypographyTarget.TRACK_TIME to 10,
                TypographyTarget.CLOCK to 9,
                TypographyTarget.ICON to 2,
                TypographyTarget.LYRICS to 5)

        expectedCounts.forEach { (target, count) ->
            assertEquals(target.name, count, TypographyEditorModel.specsFor(target).size)
        }
    }

    private fun typographyRowsFromXml(): Map<String, String> {
        val file = listOf(
                File("src/main/res/xml/watch_face_settings.xml"),
                File("mobile/src/main/res/xml/watch_face_settings.xml")
        ).first { it.exists() }
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val rows = linkedMapOf<String, String>()
        val categories = document.getElementsByTagName("PreferenceCategory")
        for (categoryIndex in 0 until categories.length) {
            val category = categories.item(categoryIndex) as Element
            if (!category.getAttribute("android:key").startsWith("cat_wf_typography_")) continue
            if (category.getAttribute("android:key") == "cat_wf_typography_editor") continue

            val children = category.childNodes
            for (childIndex in 0 until children.length) {
                val child = children.item(childIndex)
                if (child !is Element) continue
                val key = child.getAttribute("android:key")
                if (key.isNotEmpty()) {
                    rows[key] = child.getAttribute("android:defaultValue")
                }
            }
        }
        return rows
    }

    private fun destination(
            target: TypographyTarget,
            control: TypographyControl,
            hex: Boolean = false
    ) = TypographySearchTarget(target, control, hex)

    /** The six picked-colour rows, all of which default to "no colour chosen". */
    private fun hexColorDefaults(): Map<String, String> = listOf(
            "wear_title_shadow_custom_color",
            "wear_title_outline_custom_color",
            "wear_title_text_bg_custom_color",
            "wear_artist_shadow_custom_color",
            "wear_artist_outline_custom_color",
            "wear_artist_text_bg_custom_color").associateWith { "" }

    private fun flexAxisContracts(prefix: String): Map<String, NumberContract> = mapOf(
            "${prefix}_width" to NumberContract(100, 25..151),
            "${prefix}_optical_size" to NumberContract(18, 6..144),
            "${prefix}_grade" to NumberContract(0, 0..100),
            "${prefix}_roundness" to NumberContract(0, 0..100))

    private fun flexAxisDestinations(
            prefix: String,
            target: TypographyTarget
    ): Map<String, TypographySearchTarget> = listOf(
            "${prefix}_width",
            "${prefix}_optical_size",
            "${prefix}_grade",
            "${prefix}_roundness").associateWith {
        destination(target, TypographyControl.FLEX)
    }

    private data class NumberContract(val defaultValue: Int, val range: IntRange)
}
