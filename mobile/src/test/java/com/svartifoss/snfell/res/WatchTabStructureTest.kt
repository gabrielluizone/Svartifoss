package com.svartifoss.snfell.res

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * The Watch tab's structure, as it lives in XML: the two orientations of its layout, the order of
 * its categories, and the house rule its Home layouts follow.
 *
 * WatchFaceFragment reaches both orientations of `fragment_watch_face.xml` through one binding, so
 * an id that exists in only one of them is a view the code silently does not find on rotation.
 */
class WatchTabStructureTest {

    private companion object {
        /** The rule between the preview and the pages, which only the side-by-side layout draws. */
        val LANDSCAPE_ONLY_IDS = setOf("watch_preview_divider")

        val HOME_LAYOUTS = listOf("pref_home_themes.xml", "pref_home_quick.xml", "pref_home_areas.xml", "item_watch_look.xml",
                "item_watch_area.xml")
    }

    private fun res(path: String): File =
            File("src/main/res/$path").takeIf { it.exists() } ?: File("mobile/src/main/res/$path")

    private fun ids(file: File): Set<String> =
            Regex("""android:id="@\+id/([^"]+)"""").findAll(file.readText())
                    .map { it.groupValues[1] }
                    .toSet()

    @Test
    fun `both orientations of the Watch tab declare the same ids`() {
        val portrait = ids(res("layout/fragment_watch_face.xml"))
        val landscape = ids(res("layout-land/fragment_watch_face.xml")) - LANDSCAPE_ONLY_IDS
        assertTrue("fragment_watch_face.xml should declare ids", portrait.size > 5)
        if (portrait != landscape) {
            fail("fragment_watch_face.xml and its layout-land twin disagree.\n" +
                    "  Only in portrait: ${(portrait - landscape).sorted()}\n" +
                    "  Only in landscape: ${(landscape - portrait).sorted()}")
        }
    }

    /** Home is the first thing on screen, so it is the first thing in the file. */
    @Test
    fun `the home category is the first category of the watch settings`() {
        val first = Regex("""<PreferenceCategory\s+[^>]*?android:key="([^"]+)"""")
                .find(res("xml/watch_face_settings.xml").readText())
                ?.groupValues?.get(1)
        assertEquals("cat_wf_home_editor", first)
    }

    /** The house rule: an indeterminate wait is MusicLoadingBarsView, never a stock ProgressBar. */
    @Test
    fun `no home layout uses a stock progress bar`() {
        HOME_LAYOUTS.forEach { name ->
            val text = res("layout/$name").readText()
            assertTrue("$name declares a ProgressBar", "<ProgressBar" !in text)
        }
    }
}
