package com.svartifoss.snfell.res

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ensures the opt-in gallery never quietly falls back to English in a supported app locale. */
class CommunityThemeTranslationTest {

    private val communityThemeKeys = setOf(
            "watch_theme_browse_community",
            "online_themes_title",
            "online_theme_intro",
            "online_theme_refresh",
            "online_theme_list_description",
            "online_theme_byline",
            "online_theme_row_description",
            "online_theme_apply",
            "online_theme_loading",
            "online_theme_loading_preview",
            "online_theme_empty",
            "online_theme_load_error",
            "online_theme_retry",
            "online_theme_installed",
            "online_theme_apply_success",
            "online_theme_preview_error",
            "online_theme_sync_limit",
            "online_theme_update_available",
            "online_theme_requires_app_version",
            // The "New to me" filter and the empty state it can produce. The hint quotes the chip
            // label, so a locale that translated one and not the other would tell the reader to
            // turn off a control that is not on screen under that name.
            "online_theme_filter_not_installed",
            "online_theme_filter_not_installed_selected",
            "online_theme_filter_not_installed_off",
            "online_theme_search_empty_all_installed",
            "online_theme_search_empty_all_installed_hint"
    )

    @Test
    fun `every supported locale translates the community gallery strings`() {
        val resourceDirectory = resourceDirectory()
        val base = File(resourceDirectory, "values/watch_themes_strings.xml")
        val baseStrings = strings(base)
        assertTrue("Base strings must declare every gallery key", baseStrings.keys.containsAll(communityThemeKeys))

        val localeFiles = resourceDirectory.listFiles()
                ?.filter { it.isDirectory && it.name.startsWith("values-") }
                ?.map { File(it, "watch_themes_strings.xml") }
                ?.filter(File::isFile)
                .orEmpty()
        assertTrue("Expected translated watch-theme resources", localeFiles.size >= 30)

        localeFiles.forEach { file ->
            val translated = strings(file)
            val localeName = file.parentFile?.name ?: file.name
            assertTrue(
                    "$localeName is missing community gallery strings: " +
                            (communityThemeKeys - translated.keys).sorted().joinToString(),
                    translated.keys.containsAll(communityThemeKeys))
            communityThemeKeys.forEach { key ->
                assertEquals(
                        "$localeName: $key has different format placeholders",
                        placeholders(baseStrings.getValue(key)),
                        placeholders(translated.getValue(key)))
            }
        }
    }

    private fun resourceDirectory(): File = listOf(
            File("src/main/res"),
            File("mobile/src/main/res")
    ).firstOrNull(File::isDirectory) ?: error("Could not find mobile resources")

    private fun strings(file: File): Map<String, String> = STRING.findAll(file.readText())
            .associate { match -> match.groupValues[1] to match.groupValues[2] }

    private fun placeholders(value: String): List<String> = PLACEHOLDER.findAll(value)
            .map { it.value }
            .toList()
            .sorted()

    private companion object {
        val STRING = Regex("""<string name="([^"]+)">(.*?)</string>""")
        val PLACEHOLDER = Regex("""%\d+\$[a-zA-Z]""")
    }
}
