package com.svartifoss.snfell.view.settings

import com.svartifoss.snfell.view.watchface.WatchFacePrefsFragment
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Verifies the structural half of settings search against the source XML.
 *
 * [SettingsSearchIndex] indexes a row only when it has a key and title and its enclosing category
 * has a section in [SettingsCatalog]. These checks mirror that contract without an Android context,
 * catching categories that accidentally acquire two destinations as well as rows that silently
 * disappear from search after an information-architecture change.
 */
class SettingsSearchRoutingTest {

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

        val SOURCES = listOf(
                Source("settings.xml", SettingsCatalog.SETTINGS_SECTIONS),
                Source("watch_face_settings.xml", SettingsCatalog.WATCH_SECTIONS))

        // These rows are deliberately managed outside the normal section pages and search index.
        val EXTERNAL_CATEGORIES = setOf("cat_developer")
        val EXTERNAL_TOP_LEVEL_ROWS = setOf("no_watch_banner")
        val NON_SEARCH_ROWS = setOf(
                "typography_editor_surface",
                "color_editor_surface",
                "panel_editor_surface",
                "player_editor_surface",
                "background_editor_surface",
                "aod_editor_surface",
                "mini_button_editor_surface")
    }

    @Test
    fun `every non external preference row has exactly one searchable section`() {
        val problems = mutableListOf<String>()
        var searchableRows = 0

        SOURCES.forEach { source ->
            preferenceRows(source.fileName).forEach rowLoop@ { row ->
                if (row.category?.let { it in EXTERNAL_CATEGORIES } == true) return@rowLoop
                if (row.category == null && row.key in EXTERNAL_TOP_LEVEL_ROWS) return@rowLoop
                if (row.key in NON_SEARCH_ROWS) return@rowLoop

                if (row.category == null) {
                    problems += "${source.fileName}:${row.key} is outside a category, so search " +
                            "cannot assign it to a section."
                    return@rowLoop
                }
                if (row.title.isBlank()) {
                    problems += "${source.fileName}:${row.key} has no title, so search skips it."
                }

                val owners = owners(source, row.category)
                if (owners.size != 1) {
                    problems += "${source.fileName}:${row.key} in ${row.category} has " +
                            "${owners.size} section owners (${owners.joinToString()}); expected one."
                } else {
                    searchableRows++
                }
            }
        }

        assertTrue("The preference XML should contain a substantial searchable index", searchableRows > 150)
        if (problems.isNotEmpty()) {
            fail("Settings search routing is incomplete:\n  " + problems.joinToString("\n  "))
        }
    }

    @Test
    fun `Flex rows are searchable from the Text section`() {
        val source = SOURCES.single { it.fileName == "watch_face_settings.xml" }
        val flexRows = preferenceRows(source.fileName)
                .filter { it.category == "cat_wf_typography_flex" }

        assertEquals(
                setOf(
                        "wear_flex_axes_hint",
                        "wear_font_flex_width",
                        "wear_font_flex_optical_size",
                        "wear_font_flex_grade",
                        "wear_font_flex_roundness") +
                        flexAxisKeys("wear_title_font_flex") +
                        flexAxisKeys("wear_artist_font_flex") +
                        flexAxisKeys("wear_clock_font_flex") +
                        flexAxisKeys("wear_lyrics_font_flex") +
                        flexAxisKeys("wear_track_time_font_flex"),
                flexRows.map { it.key }.toSet())
        flexRows.forEach {
            assertEquals(
                    listOf(WatchFacePrefsFragment.SECTION_TYPOGRAPHY),
                    owners(source, requireNotNull(it.category)))
        }
    }

    @Test
    fun `queue remote artwork is the only intentional duplicate search key`() {
        val occurrences = SOURCES.flatMap { source ->
            preferenceRows(source.fileName)
                    .filterNot { row ->
                        row.category?.let { it in EXTERNAL_CATEGORIES } == true
                    }
                    .filterNot { it.category == null && it.key in EXTERNAL_TOP_LEVEL_ROWS }
                    .filterNot { it.key in NON_SEARCH_ROWS }
                    .map { source to it }
        }
        val duplicates = occurrences.groupBy { it.second.key }.filterValues { it.size > 1 }

        assertEquals(setOf("queue_remote_artwork"), duplicates.keys)

        val queueArtwork = duplicates.getValue("queue_remote_artwork")
        assertEquals(2, queueArtwork.size)
        assertEquals(
                setOf(
                        "settings.xml:${MiscSettingsFragment.SECTION_APPS}",
                        "watch_face_settings.xml:${WatchFacePrefsFragment.SECTION_PANELS}"),
                queueArtwork.map { (source, row) ->
                    "${source.fileName}:${owners(source, requireNotNull(row.category)).single()}"
                }.toSet())
    }

    private fun owners(source: Source, category: String): List<String> =
            source.sections.filterValues { category in it }.keys.toList()

    private fun preferenceRows(fileName: String): List<Row> {
        val file = listOf(
                File("src/main/res/xml/$fileName"),
                File("mobile/src/main/res/xml/$fileName"))
                .first { it.exists() }
        val document = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isIgnoringComments = true
        }.newDocumentBuilder().parse(file)
        val rows = mutableListOf<Row>()

        fun visit(element: Element, enclosingCategory: String?) {
            val isCategory = element.tagName.endsWith("PreferenceCategory")
            val category = if (isCategory) element.androidAttribute("key") else enclosingCategory
            val isRoot = element === document.documentElement

            if (!isRoot && !isCategory) {
                val key = element.androidAttribute("key")
                if (key.isNotBlank()) {
                    rows += Row(
                            key = key,
                            title = element.androidAttribute("title"),
                            category = category)
                }
            }

            for (index in 0 until element.childNodes.length) {
                val child = element.childNodes.item(index)
                if (child.nodeType == Node.ELEMENT_NODE) visit(child as Element, category)
            }
        }

        visit(document.documentElement, null)
        return rows
    }

    private fun Element.androidAttribute(name: String): String = getAttributeNS(ANDROID_NS, name)

    private fun flexAxisKeys(prefix: String): Set<String> = setOf(
            "${prefix}_width",
            "${prefix}_optical_size",
            "${prefix}_grade",
            "${prefix}_roundness")

    private data class Source(
            val fileName: String,
            val sections: Map<String, Set<String>>)

    private data class Row(
            val key: String,
            val title: String,
            val category: String?)
}
