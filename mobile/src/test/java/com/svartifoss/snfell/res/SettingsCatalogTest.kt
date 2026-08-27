package com.svartifoss.snfell.res

import com.svartifoss.snfell.view.settings.SettingsCatalog
import com.svartifoss.snfell.view.watchface.WatchFacePrefsFragment
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Keeps [SettingsCatalog] honest against the preference XML it describes.
 *
 * Both settings screens are one big `PreferenceScreen` whose pages are produced by *hiding* the
 * categories the current section does not want. That makes the category lists load-bearing in a way
 * that is invisible when reading either file alone:
 *
 *  - a category in the XML but missing from the hide-list is never assigned a visibility, so it
 *    shows on **every** page (this is how `cat_idle` ended up on all five Settings pages, and how
 *    the clock's type controls appeared on Style as well as Text);
 *  - a category in the hide-list but in no section is hidden on every page, so its settings become
 *    unreachable;
 *  - a stale name in either list silently does nothing at all.
 *
 * None of the three fails visibly at build time, and the second and third do not fail visibly at
 * runtime either - which is what makes them worth a test rather than a comment.
 */
class SettingsCatalogTest {

    private companion object {
        /**
         * Categories whose visibility is decided by something other than the section, so they are
         * deliberately absent from the section maps and the hide-lists.
         *
         * Adding to this set means claiming a separate rule owns the category completely.
         */
        val EXTERNALLY_MANAGED = setOf(
                // Shown only once developer mode is unlocked - MiscSettingsFragment.updateDevModeVisibility.
                "cat_developer"
        )
    }

    @Test
    fun everySettingsCategoryIsAccountedFor() =
            assertCatalogMatchesXml(
                    xml = "settings.xml",
                    declared = declaredCategories("settings.xml"),
                    listed = SettingsCatalog.SETTINGS_CATEGORIES,
                    sections = SettingsCatalog.SETTINGS_SECTIONS)

    @Test
    fun everyWatchFaceCategoryIsAccountedFor() =
            assertCatalogMatchesXml(
                    xml = "watch_face_settings.xml",
                    declared = declaredCategories("watch_face_settings.xml"),
                    listed = SettingsCatalog.WATCH_CATEGORIES,
                    sections = SettingsCatalog.WATCH_SECTIONS)

    @Test
    fun musicColorsLeadTheColorsPage() {
        val colors = SettingsCatalog.WATCH_SECTIONS
                .getValue(WatchFacePrefsFragment.SECTION_COLORS)
        val visibleOrder = declaredCategoryOrder("watch_face_settings.xml")
                .filter { it in colors }

        assertEquals(
                listOf(
                        "cat_wf_colors",
                        "cat_wf_colors_title",
                        "cat_wf_colors_artist",
                        "cat_wf_colors_clock"),
                visibleOrder)
    }

    private fun assertCatalogMatchesXml(
            xml: String,
            declared: Set<String>,
            listed: List<String>,
            sections: Map<String, Set<String>>
    ) {
        assertTrue("$xml should declare categories", declared.size > 5)

        val problems = mutableListOf<String>()

        (declared - listed.toSet() - EXTERNALLY_MANAGED).sorted().forEach {
            problems += "$it is a category in $xml but is not in the hide-list, so it stays " +
                    "visible on every page. Add it to the catalog's category list."
        }

        (listed.toSet() - declared).sorted().forEach {
            problems += "$it is in the catalog's category list but no longer exists in $xml. " +
                    "Remove it - a stale name hides nothing."
        }

        val reachable = sections.values.flatten().toSet()
        (listed.toSet() - reachable).sorted().forEach {
            problems += "$it is hidden on every page: it is in the hide-list but in no section, " +
                    "so its settings cannot be reached. Add it to a section."
        }

        (reachable - declared).sorted().forEach {
            problems += "$it is assigned to a section but does not exist in $xml."
        }

        if (problems.isNotEmpty()) {
            fail("SettingsCatalog and $xml disagree:\n  " + problems.joinToString("\n  "))
        }
    }

    /** Category keys as the XML actually declares them, read from the file like the sibling
     *  resource tests rather than through the Android resource system. */
    private fun declaredCategories(fileName: String): Set<String> {
        return declaredCategoryOrder(fileName).toSet()
    }

    private fun declaredCategoryOrder(fileName: String): List<String> {
        val file = listOf(
                File("src/main/res/xml/$fileName"),
                File("mobile/src/main/res/xml/$fileName")
        ).first { it.exists() }
        return Regex("""android:key="(cat_[a-z_0-9]+)"""")
                .findAll(file.readText())
                .map { it.groupValues[1] }
                .toList()
    }
}
