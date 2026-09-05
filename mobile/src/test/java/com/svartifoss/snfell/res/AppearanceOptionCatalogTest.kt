package com.svartifoss.snfell.res

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class AppearanceOptionCatalogTest {
    private data class Catalog(
            val key: String,
            val entries: String,
            val values: String,
            val valueSet: String,
            val expectedValues: List<String>)

    private val catalogs = listOf(
            Catalog("album_art_style", "album_art_style_extra_entries", "album_art_style_extra_values", "albumArtStyle",
                    listOf("ocean", "sunset", "spotlight", "glass_veil", "velvet", "noir", "ice", "rose",
                            "prismatic", "crescent", "tidal", "paper", "lantern", "mirage", "grid",
                            "nocturne", "cloud", "liquid", "monolith", "split_tone",
                    "gradient", "duotone", "bands", "vignette", "graphite", "cinema",
                            "acrylic", "mesh", "nebula", "bioluminescence",
                            "iridescent", "orbit", "ink_wash", "blossom", "fjord",
                            "dot_matrix", "scanlines", "radar", "contour", "faceted",
                            "album", "secondary", "tertiary", "glass",
                            "midnight", "smoke", "tideline")),
            Catalog("album_art_filter", "album_art_filter_extra_entries", "album_art_filter_extra_values", "albumArtFilter",
                    listOf("moss", "lavender", "cherry", "deep_sea", "dust", "bleach", "moonlight",
                            "dream", "infrared", "forest", "silver", "candy")),
            Catalog("wear_player_shading_style", "player_shading_style_extra_entries",
                    "player_shading_style_extra_values", "playerShadingStyle",
                    listOf("top_fade", "center_spotlight", "diagonal", "left_curtain", "right_curtain", "center_band", "crossfade")),
            Catalog("screen_buttons_shape", "screen_buttons_shape_extra_entries",
                    "screen_buttons_shape_extra_values", "buttonShape",
                    listOf("leaf_reverse", "drop_reverse", "pebble", "arch", "shield", "arch_reverse", "shield_reverse")),
            Catalog("wear_accent_floor", "wear_accent_floor_extra_entries",
                    "wear_accent_floor_extra_values", "accentFloor",
                    listOf("whisper", "radiant", "flood", "glimmer", "deep")),
            Catalog("wear_up_next_pill_style", "wear_up_next_pill_extra_entries",
                    "wear_up_next_pill_extra_values", "upNextPillStyle",
                    listOf("outline_album", "gradient_album", "secondary", "glass_album", "tertiary", "neon_outline")))

    @Test
    fun `additive appearance catalogs are aligned translated and wired`() {
        val base = resource("values/appearance_options.xml")
        val ptBr = resource("values-pt-rBR/appearance_options.xml")
        val source = repoFile(
                "mobile/src/main/java/com/svartifoss/snfell/view/watchface/AppearanceOptionCatalog.kt")
                .readText()
        val constraints = JSONObject(repoFile(
                "common/src/main/assets/community-theme-constraints.json").readText())
                .getJSONObject("valueSets")

        catalogs.forEach { catalog ->
            val entries = arrayItems(base, catalog.entries)
            val values = arrayItems(base, catalog.values)
            assertEquals(catalog.entries, values.size, entries.size)
            assertEquals(catalog.values, catalog.expectedValues, values)
            val accepted = constraints.getJSONArray(catalog.valueSet)
            val acceptedValues = (0 until accepted.length()).map(accepted::getString)
            assertTrue("${catalog.key} additions must be valid in Community themes",
                    acceptedValues.containsAll(values))
            assertEquals("pt-BR ${catalog.entries}", entries.size,
                    arrayItems(ptBr, catalog.entries).size)
            assertTrue(source.contains("\"${catalog.key}\""))
            assertTrue(source.contains("R.array.${catalog.entries}"))
            assertTrue(source.contains("R.array.${catalog.values}"))
        }
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
                .find(file.readText())?.groupValues?.get(1)
                ?: error("$name missing from ${file.path}")
        return Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL)
                .findAll(body).map { it.groupValues[1].trim() }.toList()
    }
}
