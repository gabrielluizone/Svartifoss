package com.svartifoss.snfell.view.watchface.theme

import com.svartifoss.snfell.BuildConfig
import com.svartifoss.snfell.common.ArchivedFaces
import com.svartifoss.snfell.common.FaceScopedPreferences
import com.svartifoss.snfell.common.ThemeAppearance
import com.svartifoss.snfell.update.AppVersionComparison
import java.io.File
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Keeps the static GitHub Pages catalogue and its individual public profiles in lockstep. */
class OnlineThemeCatalogTest {

    @Test
    fun `every indexed public theme is valid and has a matching profile`() {
        val themesDirectory = themesDirectory()
        val index = JSONObject(File(themesDirectory, "index.json").readText())
        assertEquals(1, index.getInt("schemaVersion"))

        val summaries = index.getJSONArray("themes")
        assertTrue("The initial public gallery should offer more than a single sample", summaries.length() >= 4)
        val ids = mutableSetOf<String>()

        for (position in 0 until summaries.length()) {
            val summary = summaries.getJSONObject(position)
            val id = summary.getString("id")
            assertEquals("Catalogue id at $position must be canonical UUID text", UUID.fromString(id).toString(), id)
            assertTrue("Catalogue ids must be unique", ids.add(id))
            assertEquals(WatchThemeRepository.LIBRARY_SCHEMA, summary.getInt("schemaVersion"))

            val baseFace = summary.getString("baseFace")
            assertTrue("$id uses an unknown base face", baseFace in ThemeAppearance.ALLOWED_BASE_FACES)
            assertFalse(
                    "$id must not republish a gallery-excluded face",
                    baseFace in ArchivedFaces.COMMUNITY_GALLERY_EXCLUDED)
            assertFalse(
                    "$id requires a newer app than this catalogue release",
                    AppVersionComparison.isNewer(summary.getString("minimumAppVersion"), BuildConfig.VERSION_NAME))

            val profileFile = File(themesDirectory, "$id.json")
            assertTrue("$id is listed but has no profile file", profileFile.isFile)
            assertProfileMatchesSummary(summary, JSONObject(profileFile.readText()))
        }
    }

    private fun assertProfileMatchesSummary(summary: JSONObject, profile: JSONObject) {
        listOf("id", "name", "author", "baseFace", "revision", "schemaVersion", "minimumAppVersion", "publishedAt")
                .forEach { key -> assertEquals("Profile $key must match its index entry", summary.get(key), profile.get(key)) }

        val settings = profile.getJSONObject("settings")
        assertTrue("A published profile needs at least one intentional setting", settings.length() > 0)
        val keys = settings.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            assertTrue("$key is not an appearance preference and must not be publishable", key in FaceScopedPreferences.SCOPED_KEYS)
            val value = settings.getJSONObject(key)
            assertTrue("$key needs an explicit type tag", value.has("type"))
            assertTrue("$key needs a value", value.has("value"))
            val expectedType = when (val default =
                    FaceScopedPreferences.SCOPED_DEFINITIONS_BY_KEY.getValue(key).defaultValue) {
                is String -> "string"
                is Boolean -> "boolean"
                is Int -> "int"
                else -> fail("$key has unsupported preference type ${default::class.java.name}")
            }
            assertEquals("$key has the wrong explicit profile type", expectedType, value.getString("type"))
        }
    }

    private fun themesDirectory(): File = listOf(
            File("../docs/themes"),
            File("docs/themes")
    ).firstOrNull { it.isDirectory } ?: run {
        fail("Could not find docs/themes from the mobile unit-test working directory")
        error("unreachable")
    }
}
