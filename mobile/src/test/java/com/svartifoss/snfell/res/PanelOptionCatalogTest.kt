package com.svartifoss.snfell.res

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contract for the additive Panel option arrays and their stable preference tokens. */
class PanelOptionCatalogTest {

    private data class Catalog(
            val key: String,
            val legacyValues: String,
            val extraEntries: String,
            val extraValues: String,
            val total: Int)

    private val catalogs = listOf(
            Catalog("wear_overlay_backdrop_style", "wear_overlay_backdrop_values",
                    "wear_overlay_backdrop_extra_entries", "wear_overlay_backdrop_extra_values", 24),
            Catalog("wear_volume_style", "wear_volume_style_values",
                    "wear_volume_style_extra_entries", "wear_volume_style_extra_values", 26),
            Catalog("wear_volume_layout", "wear_volume_layout_values",
                    "wear_volume_layout_extra_entries", "wear_volume_layout_extra_values", 16),
            Catalog("wear_progress_style", "wear_progress_style_values",
                    "wear_progress_style_extra_entries", "wear_progress_style_extra_values", 18),
            Catalog("wear_seek_style", "wear_seek_style_values",
                    "wear_seek_style_extra_entries", "wear_seek_style_extra_values", 31),
            Catalog("wear_seek_layout", "wear_seek_layout_values",
                    "wear_seek_layout_extra_entries", "wear_seek_layout_extra_values", 14),
            Catalog("wear_quick_panel_style", "wear_quick_panel_style_values",
                    "wear_quick_panel_style_extra_entries", "wear_quick_panel_style_extra_values", 30),
            Catalog("wear_quick_panel_layout", "wear_quick_panel_layout_values",
                    "wear_quick_panel_layout_extra_entries", "wear_quick_panel_layout_extra_values", 14),
            // Queue historically shares the base overlay-style list, then adds its own skins.
            Catalog("wear_queue_style", "wear_overlay_style_values",
                    "wear_queue_style_extra_entries", "wear_queue_style_extra_values", 26))

    @Test
    fun `every additive catalog is aligned unique and wired`() {
        val legacy = resource("values/strings.xml")
        val additions = resource("values/panel_options.xml")
        val catalogSource = repoFile(
                "mobile/src/main/java/com/svartifoss/snfell/view/watchface/PanelOptionCatalog.kt")
                .readText()

        catalogs.forEach { catalog ->
            val oldValues = arrayItems(legacy, catalog.legacyValues)
            val entries = arrayItems(additions, catalog.extraEntries)
            val values = arrayItems(additions, catalog.extraValues)
            assertEquals("${catalog.key} extra entries/values", entries.size, values.size)
            assertEquals("${catalog.key} expanded count", catalog.total, oldValues.size + values.size)
            assertEquals(
                    "${catalog.key} must not expose duplicate stable values",
                    oldValues.size + values.size,
                    (oldValues + values).distinct().size)
            assertTrue("${catalog.key} is not wired into PanelOptionCatalog",
                    catalogSource.contains("\"${catalog.key}\""))
            assertTrue("${catalog.extraEntries} is not wired into PanelOptionCatalog",
                    catalogSource.contains("R.array.${catalog.extraEntries}"))
            assertTrue("${catalog.extraValues} is not wired into PanelOptionCatalog",
                    catalogSource.contains("R.array.${catalog.extraValues}"))
        }
    }

    @Test
    fun `progress layout is a complete independent axis`() {
        val additions = resource("values/panel_options.xml")
        val entries = arrayItems(additions, "wear_progress_layout_entries")
        val values = arrayItems(additions, "wear_progress_layout_values")
        assertEquals(entries.size, values.size)
        assertEquals(11, values.size)
        assertEquals(
                listOf("edge", "inset", "inner", "bold", "open_bottom", "open_top",
                        "left_arc", "right_arc", "double", "open_left", "open_right"),
                values)
    }

    @Test
    fun `Brazilian Portuguese additions keep the same indexes`() {
        val base = resource("values/panel_options.xml")
        val ptBr = resource("values-pt-rBR/panel_options.xml")
        catalogs.forEach { catalog ->
            assertEquals(
                    catalog.extraEntries,
                    arrayItems(base, catalog.extraEntries).size,
                    arrayItems(ptBr, catalog.extraEntries).size)
        }
        assertEquals(
                arrayItems(base, "wear_progress_layout_entries").size,
                arrayItems(ptBr, "wear_progress_layout_entries").size)
    }

    private fun resource(relative: String): File =
            File("src/main/res/$relative").takeIf(File::exists)
                    ?: File("mobile/src/main/res/$relative")

    private fun repoFile(relative: String): File =
            File("../$relative").takeIf(File::exists) ?: File(relative)

    private fun arrayItems(file: File, name: String): List<String> {
        val body = Regex(
                """<string-array[^>]*name="${Regex.escape(name)}"[^>]*>(.*?)</string-array>""",
                RegexOption.DOT_MATCHES_ALL)
                .find(file.readText())
                ?.groupValues?.get(1)
                ?: error("$name missing from ${file.path}")
        return Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL)
                .findAll(body)
                .map { it.groupValues[1].trim() }
                .toList()
    }
}
